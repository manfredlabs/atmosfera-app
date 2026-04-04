package com.example.atmosfera.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.atmosfera.data.Song
import com.example.atmosfera.data.SongDao
import com.example.atmosfera.ui.theme.*
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// Mapa de nome interno → label visual
private val NOTE_LABELS = mapOf(
    "c" to "C", "cs" to "C#", "d" to "D", "ds" to "D#",
    "e" to "E", "f" to "F", "fs" to "F#", "g" to "G",
    "gs" to "G#", "a" to "A", "as" to "A#", "b" to "B"
)

private val NOTE_NAMES = listOf("c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b")

@Composable
fun PlaylistScreen(
    songDao: SongDao,
    playingSongId: Long?,
    padVolume: Float,
    clickVolume: Float,
    onPlaySong: (Song) -> Unit,
    onPauseSong: () -> Unit,
    onPadVolumeChange: (Float) -> Unit,
    onClickVolumeChange: (Float) -> Unit,
    onNavigateToAddSong: () -> Unit,
    onNavigateToEditSong: (Long) -> Unit,
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {}
) {
    val songs by songDao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PLAYLIST",
                    fontSize = 22.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 6.sp,
                    color = TextSecondary
                )
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Unlock" else "Lock",
                        tint = if (isLocked) LedAmber else TextSecondary.copy(alpha = 0.5f)
                    )
                }
            }

            if (songs.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No songs saved",
                            fontSize = 16.sp,
                            fontFamily = SpaceGrotesk,
                            color = TextSecondary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap + to add",
                            fontSize = 13.sp,
                            fontFamily = SpaceGrotesk,
                            color = TextSecondary.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                var localSongs by remember(songs) { mutableStateOf(songs) }
                val lazyListState = rememberLazyListState()
                val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    localSongs = localSongs.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                }

                // Save order when drag ends
                LaunchedEffect(localSongs) {
                    if (localSongs != songs && localSongs.size == songs.size) {
                        val updated = localSongs.mapIndexed { index, song ->
                            song.copy(sortOrder = index)
                        }
                        songDao.updateAll(updated)
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(localSongs, key = { it.id }) { song ->
                        ReorderableItem(reorderableLazyListState, key = song.id) { isDragging ->
                            val isPlaying = playingSongId == song.id
                            SongItem(
                                song = song,
                                isPlaying = isPlaying,
                                padVolume = padVolume,
                                clickVolume = clickVolume,
                                onTap = {
                                    if (!isLocked) return@SongItem
                                    if (isPlaying) onPauseSong() else onPlaySong(song)
                                },
                                onDelete = { scope.launch { songDao.delete(song) } },
                                onEdit = { s -> onNavigateToEditSong(s.id) },
                                onPadVolumeChange = onPadVolumeChange,
                                onClickVolumeChange = onClickVolumeChange,
                                isLocked = isLocked,
                                dragModifier = Modifier.draggableHandle()
                            )
                        }
                    }
                }
            }
        }

        // FAB overlay (hidden when locked)
        if (!isLocked) {
            FloatingActionButton(
            onClick = onNavigateToAddSong,
            containerColor = PadActive,
            contentColor = TextPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SongItem(
    song: Song,
    isPlaying: Boolean,
    padVolume: Float,
    clickVolume: Float,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (Song) -> Unit,
    onPadVolumeChange: (Float) -> Unit,
    onClickVolumeChange: (Float) -> Unit,
    isLocked: Boolean = false,
    dragModifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val noteLabel = NOTE_LABELS[song.note] ?: song.note.uppercase()
    val chordLabel = when (song.padMode) {
        "min" -> "${noteLabel}m"
        else -> noteLabel
    }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PadIdle, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isPlaying) PadBorder.copy(alpha = 0.5f) else PadBorder.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = { onTap() },
                onLongClick = {
                    if (!isLocked) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showBottomSheet = true
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = dragModifier
                    .size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = song.name,
                    fontSize = 17.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = chordLabel,
                        fontSize = 13.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        color = LedAmber
                    )
                    if (song.clickEnabled) {
                        Text(
                            text = "  •  ",
                            fontSize = 13.sp,
                            color = TextSecondary.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "${song.bpm} BPM",
                            fontSize = 13.sp,
                            fontFamily = SpaceGrotesk,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Expanded controls when playing
        androidx.compose.animation.AnimatedVisibility(visible = isPlaying) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = PadBorder.copy(alpha = 0.3f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PAD",
                        fontSize = 11.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary,
                        modifier = Modifier.width(40.dp)
                    )
                    Slider(
                        value = padVolume,
                        onValueChange = onPadVolumeChange,
                        modifier = Modifier.weight(1f).height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = LedAmber,
                            activeTrackColor = LedAmberDim,
                            inactiveTrackColor = PadBorder
                        )
                    )
                }

                if (song.clickEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CLICK",
                            fontSize = 11.sp,
                            fontFamily = SpaceGrotesk,
                            color = TextSecondary,
                            modifier = Modifier.width(40.dp)
                        )
                        Slider(
                            value = clickVolume,
                            onValueChange = onClickVolumeChange,
                            modifier = Modifier.weight(1f).height(24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = ClickTeal,
                                activeTrackColor = ClickTealDim,
                                inactiveTrackColor = PadBorder
                            )
                        )
                    }
                }
            }
        }
    }

    // Bottom sheet for actions
    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = PadIdle,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(32.dp)
                        .height(4.dp)
                        .background(PadBorder.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = song.name,
                    fontSize = 16.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                HorizontalDivider(color = PadBorder.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Edit",
                    fontSize = 16.sp,
                    fontFamily = SpaceGrotesk,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showBottomSheet = false
                            onEdit(song)
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )

                Text(
                    text = "Delete",
                    fontSize = 16.sp,
                    fontFamily = SpaceGrotesk,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showBottomSheet = false
                            showDeleteConfirm = true
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        val deleteSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showDeleteConfirm = false },
            sheetState = deleteSheetState,
            containerColor = PadIdle,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Delete \"${song.name}\"?",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This action cannot be undone.",
                    fontFamily = SpaceGrotesk,
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TextSecondary)
                    ) {
                        Text(
                            "CANCEL",
                            fontFamily = SpaceGrotesk,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = {
                            onDelete()
                            showDeleteConfirm = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B)
                        )
                    ) {
                        Text(
                            "DELETE",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}