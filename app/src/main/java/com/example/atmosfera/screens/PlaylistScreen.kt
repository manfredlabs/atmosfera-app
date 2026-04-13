package com.example.atmosfera.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.atmosfera.data.Song
import com.example.atmosfera.data.SongDao
import com.example.atmosfera.data.MixProject
import com.example.atmosfera.data.MixProjectDao
import com.example.atmosfera.data.MixTrack
import com.example.atmosfera.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// Mapa de nome interno → label visual
private val NOTE_LABELS = mapOf(
    "c" to "C", "cs" to "C#", "d" to "D", "ds" to "D#",
    "e" to "E", "f" to "F", "fs" to "F#", "g" to "G",
    "gs" to "G#", "a" to "A", "as" to "A#", "b" to "B"
)

private val NOTE_NAMES = listOf("c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b")

/** Unified wrapper so Songs and MixProjects share one sorted list. */
sealed class PlaylistItem(val sortKey: Int, val createdAt: Long) {
    abstract val uid: String
    class SongItem(val song: Song) : PlaylistItem(song.sortOrder, song.createdAt) {
        override val uid = "song_${song.id}"
    }
    class MixItem(val project: MixProject) : PlaylistItem(project.sortOrder, project.createdAt) {
        override val uid = "mix_${project.id}"
    }
}

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
    isPadPlaying: Boolean = true,
    isClickPlaying: Boolean = true,
    onTogglePad: () -> Unit = {},
    onToggleClick: () -> Unit = {},
    onNavigateToAddSong: () -> Unit,
    onNavigateToEditSong: (Long) -> Unit,
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    allPacks: List<com.example.atmosfera.data.SoundPack> = emptyList(),
    // Mix Studio integration
    mixProjectDao: MixProjectDao? = null,
    playingMixId: Long? = null,
    onPlayMix: (MixProject, List<MixTrack>) -> Unit = { _, _ -> },
    onStopMix: () -> Unit = {},
    onPauseMix: () -> Unit = {},
    onResumeMix: (List<MixTrack>) -> Unit = {},
    onSeekMix: (Long) -> Unit = {},
    getMixPositionMs: () -> Long = { 0L },
    getMixDurationMs: () -> Long = { 0L },
    isMixPaused: Boolean = false,
    mixTrackPlayingState: Map<Long, Boolean> = emptyMap(),
    onMixTrackVolumeChange: (MixTrack, Float) -> Unit = { _, _ -> },
    onMuteMixTrack: (Long) -> Unit = {},
    onUnmuteMixTrack: (Long) -> Unit = {},
    mutedMixTrackIds: Map<Long, Boolean> = emptyMap(),
    onDeleteMixProject: (MixProject) -> Unit = {}
) {
    val songs by songDao.getAll().collectAsState(initial = emptyList())
    val mixProjects by mixProjectDao?.getAllInPlaylist()?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val scope = rememberCoroutineScope()

    // Single expanded card — null means all collapsed
    var expandedCardId by remember { mutableStateOf<String?>(null) }

    // Collapse all cards when unlocking
    LaunchedEffect(isLocked) {
        if (!isLocked) expandedCardId = null
    }

    // Build unified list
    val items = remember(songs, mixProjects) {
        val songItems = songs.map { PlaylistItem.SongItem(it) }
        val mixItems = mixProjects.map { PlaylistItem.MixItem(it) }
        (songItems + mixItems).sortedWith(compareBy<PlaylistItem> { it.sortKey }.thenByDescending { it.createdAt })
    }

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

            if (items.isEmpty()) {
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
                val lazyListState = rememberLazyListState()

                // Source index — set on long press, stays constant throughout the drag.
                var draggingIndex by remember { mutableStateOf<Int?>(null) }
                // Cumulative Y pixels moved since drag started.
                var dragOffsetY by remember { mutableFloatStateOf(0f) }
                // Measured height of each card in pixels — keyed by uid so heights survive reorders.
                val itemHeights = remember { mutableStateMapOf<String, Float>() }

                // Stable ordered list, not recreated on every DB emission.
                val localOrder = remember { mutableStateListOf<PlaylistItem>() }

                // Coroutine scope NOT bound to this composition so saves survive navigation.
                val dbScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
                DisposableEffect(Unit) { onDispose { dbScope.cancel() } }

                val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }

                // Re-sync localOrder only when items are added or removed, never on reorder.
                val itemUidSet = remember(items) { items.map { it.uid }.sorted().joinToString() }
                LaunchedEffect(itemUidSet) {
                    if (draggingIndex != null) return@LaunchedEffect
                    val currentUids = localOrder.map { it.uid }.toSet()
                    val newUids = items.map { it.uid }.toSet()
                    if (currentUids != newUids) {
                        val idToItem = items.associateBy { it.uid }
                        val preserved = localOrder.mapNotNull { idToItem[it.uid] }
                        val added = items.filter { it.uid !in currentUids }
                        localOrder.clear()
                        localOrder.addAll(preserved + added)
                    } else {
                        // Update data in place (name edits, etc.) without touching order.
                        val idToItem = items.associateBy { it.uid }
                        for (i in localOrder.indices) {
                            idToItem[localOrder[i].uid]?.let { localOrder[i] = it }
                        }
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    userScrollEnabled = draggingIndex == null,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.graphicsLayer { clip = false }
                ) {
                    itemsIndexed(localOrder, key = { _, item -> item.uid }) { index, item ->
                        val isDraggingThis = draggingIndex == index

                        // Target displacement for this card (0 when nothing is being dragged,
                        // ±cardHeight when this card needs to slide out of the way).
                        // derivedStateOf avoids recomposing all cards on every drag pixel —
                        // only recomposes when the computed value actually changes.
                        val targetDisplacement by remember {
                            derivedStateOf {
                                val di = draggingIndex
                                val avgH = itemHeights.values.average().toFloat().takeIf { it > 0f }
                                if (isDraggingThis || di == null || avgH == null) return@derivedStateOf 0f
                                val steps = (dragOffsetY / avgH).roundToInt()
                                val ti = (di + steps).coerceIn(0, localOrder.size - 1)
                                val draggedH = itemHeights[localOrder.getOrNull(di)?.uid] ?: avgH
                                val shift = draggedH + spacingPx
                                when {
                                    di < ti && index in (di + 1)..ti -> -shift
                                    di > ti && index in ti until di -> shift
                                    else -> 0f
                                }
                            }
                        }

                        // Spring-animated displacement: neighbor cards bounce into position.
                        val animatedDisplacement by animateFloatAsState(
                            targetValue = targetDisplacement,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "displacement"
                        )

                        // animatedScale / animatedAlpha for smooth lift-on-press feel.
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isDraggingThis) 1.04f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "scale"
                        )
                        val animatedAlpha by animateFloatAsState(
                            targetValue = if (isDraggingThis) 0.93f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "alpha"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // animateItem handles the placement animation when the list
                                // physically reorders on drag-end (skip for the dragging card
                                // to avoid fighting the gesture).
                                .then(if (!isDraggingThis) Modifier.animateItem(
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) else Modifier)
                                .zIndex(if (isDraggingThis) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDraggingThis) dragOffsetY else animatedDisplacement
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    alpha = animatedAlpha
                                }
                                .onGloballyPositioned {
                                    itemHeights[item.uid] = it.size.height.toFloat()
                                }
                                .then(
                                    if (!isLocked) {
                                        Modifier.pointerInput(index) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingIndex = index
                                                    dragOffsetY = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val di = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                                    val avgH = itemHeights.values.average().toFloat().takeIf { it > 0f } ?: 0f
                                                    val minOffset = -(di * (avgH + spacingPx))
                                                    val maxOffset = ((localOrder.size - 1 - di) * (avgH + spacingPx))
                                                    dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(minOffset, maxOffset)
                                                },
                                                onDragEnd = {
                                                    val di = draggingIndex
                                                    if (di != null) {
                                                        val avgH = itemHeights.values.average()
                                                            .toFloat().takeIf { it > 0f } ?: 0f
                                                        val steps = (dragOffsetY / avgH).roundToInt()
                                                        val ti = (di + steps).coerceIn(0, localOrder.size - 1)
                                                        draggingIndex = null
                                                        dragOffsetY = 0f
                                                        if (di != ti) {
                                                            localOrder.add(ti, localOrder.removeAt(di))
                                                        }
                                                    } else {
                                                        draggingIndex = null
                                                        dragOffsetY = 0f
                                                    }
                                                    val snapshot = localOrder.toList()
                                                    dbScope.launch {
                                                        snapshot.forEachIndexed { i, it ->
                                                            when (it) {
                                                                is PlaylistItem.SongItem ->
                                                                    songDao.update(it.song.copy(sortOrder = i))
                                                                is PlaylistItem.MixItem ->
                                                                    mixProjectDao?.update(it.project.copy(sortOrder = i))
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    draggingIndex = null
                                                    dragOffsetY = 0f
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                            when (item) {
                                is PlaylistItem.SongItem -> {
                                    val song = item.song
                                    val isSongPlaying = playingSongId == song.id
                                    val isThisExpanded = expandedCardId == item.uid
                                    SongCard(
                                        song = song,
                                        isPlaying = isSongPlaying,
                                        isExpanded = isThisExpanded,
                                        onHeaderClick = {
                                            if (!isLocked) return@SongCard
                                            if (isThisExpanded && isSongPlaying) {
                                                onPauseSong()
                                                expandedCardId = null
                                            } else if (isThisExpanded) {
                                                expandedCardId = null
                                            } else {
                                                if (expandedCardId != null) {
                                                    if (playingSongId != null) onPauseSong()
                                                    if (playingMixId != null) onStopMix()
                                                }
                                                expandedCardId = item.uid
                                            }
                                        },
                                        padVolume = padVolume,
                                        clickVolume = clickVolume,
                                        onPlay = { onPlaySong(song) },
                                        onStop = { onPauseSong() },
                                        onDelete = { scope.launch { songDao.delete(song) } },
                                        onEdit = { s -> onNavigateToEditSong(s.id) },
                                        onPadVolumeChange = onPadVolumeChange,
                                        onClickVolumeChange = onClickVolumeChange,
                                        isPadPlaying = isPadPlaying,
                                        isClickPlaying = isClickPlaying && song.clickEnabled,
                                        onTogglePad = onTogglePad,
                                        onToggleClick = onToggleClick,
                                        isLocked = isLocked,
                                        packName = allPacks.find { it.id == song.soundPackId }?.name ?: "Atmos"
                                    )
                                }
                                is PlaylistItem.MixItem -> {
                                    val project = item.project
                                    val isMixPlaying = playingMixId == project.id
                                    val isThisExpanded = expandedCardId == item.uid
                                    MixProjectCard(
                                        project = project,
                                        mixDao = mixProjectDao!!,
                                        isPlaying = isMixPlaying,
                                        isPaused = isMixPaused,
                                        isExpanded = isThisExpanded,
                                        onHeaderClick = {
                                            if (!isLocked) return@MixProjectCard
                                            if (isThisExpanded && (isMixPlaying || isMixPaused)) {
                                                onStopMix()
                                                expandedCardId = null
                                            } else if (isThisExpanded) {
                                                expandedCardId = null
                                            } else {
                                                if (expandedCardId != null) {
                                                    if (playingSongId != null) onPauseSong()
                                                    if (playingMixId != null) onStopMix()
                                                }
                                                expandedCardId = item.uid
                                            }
                                        },
                                        onPlay = {
                                            scope.launch {
                                                val tracks = mixProjectDao.getTracksForProjectOnce(project.id)
                                                onPlayMix(project, tracks)
                                            }
                                        },
                                        onResume = {
                                            scope.launch {
                                                val tracks = mixProjectDao.getTracksForProjectOnce(project.id)
                                                onResumeMix(tracks)
                                            }
                                        },
                                        onPause = { onPauseMix() },
                                        onStop = { onStopMix() },
                                        onDelete = { onDeleteMixProject(project) },
                                        isLocked = isLocked,
                                        trackPlayingState = mixTrackPlayingState,
                                        onTrackVolumeChange = onMixTrackVolumeChange,
                                        mutedTrackIds = mutedMixTrackIds,
                                        onMuteTrack = onMuteMixTrack,
                                        onUnmuteTrack = onUnmuteMixTrack,
                                        onSeek = onSeekMix,
                                        getPositionMs = getMixPositionMs,
                                        getDurationMs = getMixDurationMs
                                    )
                                }
                            }
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
private fun SongCard(
    song: Song,
    isPlaying: Boolean,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    padVolume: Float,
    clickVolume: Float,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (Song) -> Unit,
    onPadVolumeChange: (Float) -> Unit,
    onClickVolumeChange: (Float) -> Unit,
    isPadPlaying: Boolean = true,
    isClickPlaying: Boolean = true,
    onTogglePad: () -> Unit = {},
    onToggleClick: () -> Unit = {},
    isLocked: Boolean = false,
    packName: String = "Atmos"
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val noteLabel = NOTE_LABELS[song.note] ?: song.note.uppercase()
    val modeLabel = song.padMode.uppercase()
    val scope = rememberCoroutineScope()

    // Swipe state - use density to convert dp to px
    val density = androidx.compose.ui.platform.LocalDensity.current
    val revealWidthPx = with(density) { 140.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isPlaying) LedAmber.copy(alpha = 0.7f) else PadBorder.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
    ) {
        // Background action buttons (revealed on swipe)
        if (offsetX.value < -1f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(DarkBg),
                horizontalArrangement = Arrangement.End
            ) {
                // Edit button
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(70.dp)
                        .background(PadIdle)
                        .clickable {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                            onEdit(song)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = LedAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Delete button
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(70.dp)
                        .background(PadIdle)
                        .clickable {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                            showDeleteConfirm = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Foreground card (swipeable)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(if (isPlaying) LedAmber.copy(alpha = 0.07f) else PadIdle)
                .then(
                    if (!isLocked) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        val target = if (offsetX.value < -revealWidthPx / 2) -revealWidthPx else 0f
                                        offsetX.animateTo(target, tween(200))
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        val newValue = (offsetX.value + dragAmount).coerceIn(-revealWidthPx, 0f)
                                        offsetX.snapTo(newValue)
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeaderClick() }
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Note badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .background(LedAmber.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = noteLabel,
                        fontSize = 20.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        color = LedAmber
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.name,
                        fontSize = 18.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Normal,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val metaParts = mutableListOf(modeLabel)
                    if (song.clickEnabled) metaParts.add("${song.bpm} BPM")
                    metaParts.add(packName)
                    Text(
                        text = metaParts.joinToString(" • "),
                        fontSize = 13.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary.copy(alpha = 0.6f)
                    )
                }
            }

            // Expanded controls
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = PadBorder.copy(alpha = 0.3f), thickness = 1.dp)

                    // Transport controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onPlay() }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = if (!isPlaying) LedAmber else LedAmber.copy(alpha = 0.3f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(
                            onClick = { onStop() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = if (isPlaying) Color(0xFFFF6B6B) else TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Piano,
                            contentDescription = "Toggle Pad",
                            tint = if (isPadPlaying) LedAmber else LedAmber.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp).clickable { onTogglePad() }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PAD",
                            fontSize = 11.sp,
                            fontFamily = SpaceGrotesk,
                            color = TextSecondary,
                            modifier = Modifier.width(34.dp)
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
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Toggle Click",
                                tint = if (isClickPlaying) ClickTeal else ClickTeal.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp).clickable { onToggleClick() }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CLICK",
                                fontSize = 11.sp,
                                fontFamily = SpaceGrotesk,
                                color = TextSecondary,
                                modifier = Modifier.width(34.dp)
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

// ═══════════════════════════════════════════════════════════════════════
//  Mix Project Card (playlist integration)
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MixProjectCard(
    project: MixProject,
    mixDao: MixProjectDao,
    isPlaying: Boolean,
    isPaused: Boolean,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    isLocked: Boolean,
    trackPlayingState: Map<Long, Boolean>,
    onTrackVolumeChange: (MixTrack, Float) -> Unit,
    mutedTrackIds: Map<Long, Boolean>,
    onMuteTrack: (Long) -> Unit,
    onUnmuteTrack: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    getPositionMs: () -> Long,
    getDurationMs: () -> Long
) {
    val tracks by mixDao.getTracksForProject(project.id).collectAsState(initial = emptyList())
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasCustom = tracks.any { it.trackType == "custom" }
    val anyTrackPlaying = trackPlayingState.values.any { it }

    // Swipe state
    val density = androidx.compose.ui.platform.LocalDensity.current
    val revealWidthPx = with(density) { 70.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isPlaying || isPaused) LabsPurple.copy(alpha = 0.8f) else PadBorder.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
    ) {
        // Background delete button (swipe)
        if (offsetX.value < -1f) {
            Row(
                modifier = Modifier.matchParentSize().background(DarkBg),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(70.dp)
                        .background(PadIdle)
                        .clickable {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                            showDeleteConfirm = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Foreground card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(if (isPlaying || isPaused) LabsPurple.copy(alpha = 0.07f) else PadIdle)
                .then(
                    if (!isLocked) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        val target = if (offsetX.value < -revealWidthPx / 2) -revealWidthPx else 0f
                                        offsetX.animateTo(target, tween(200))
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        val newValue = (offsetX.value + dragAmount).coerceIn(-revealWidthPx, 0f)
                                        offsetX.snapTo(newValue)
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeaderClick() }
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mix icon badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .background(LabsPurple.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = LabsPurple,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        fontSize = 18.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Normal,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${tracks.size} tracks · Mix",
                        fontSize = 13.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary.copy(alpha = 0.6f)
                    )
                }
            }

            // Expanded per-track controls
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = PadBorder.copy(alpha = 0.3f), thickness = 1.dp)

                    // Seek bar (only when custom tracks exist)
                    if (hasCustom) {
                        var positionMs by remember { mutableStateOf(0L) }
                        var durationMs by remember { mutableStateOf(0L) }
                        var isSeeking by remember { mutableStateOf(false) }
                        var seekValue by remember { mutableStateOf(0f) }

                        LaunchedEffect(anyTrackPlaying, isPaused) {
                            while (anyTrackPlaying || isPaused) {
                                if (!isSeeking) {
                                    positionMs = getPositionMs()
                                    durationMs = getDurationMs()
                                }
                                kotlinx.coroutines.delay(250)
                            }
                        }

                        fun formatTime(ms: Long): String {
                            val totalSec = (ms / 1000).coerceAtLeast(0)
                            val min = totalSec / 60
                            val sec = totalSec % 60
                            return "%d:%02d".format(min, sec)
                        }

                        val progress = if (durationMs > 0) {
                            if (isSeeking) seekValue else positionMs.toFloat() / durationMs
                        } else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(if (isSeeking) (seekValue * durationMs).toLong() else positionMs),
                                fontSize = 10.sp,
                                fontFamily = SpaceGrotesk,
                                color = TextSecondary,
                                modifier = Modifier.width(32.dp)
                            )
                            Slider(
                                value = progress.coerceIn(0f, 1f),
                                onValueChange = { v ->
                                    isSeeking = true
                                    seekValue = v
                                },
                                onValueChangeFinished = {
                                    val targetMs = (seekValue * durationMs).toLong()
                                    onSeek(targetMs)
                                    positionMs = targetMs
                                    isSeeking = false
                                },
                                modifier = Modifier.weight(1f).height(20.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = LabsPurple,
                                    activeTrackColor = LabsPurple,
                                    inactiveTrackColor = PadBorder
                                )
                            )
                            Text(
                                text = formatTime(durationMs),
                                fontSize = 10.sp,
                                fontFamily = SpaceGrotesk,
                                color = TextSecondary,
                                modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    // Transport controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play
                        IconButton(
                            onClick = {
                                if (isPaused) onResume()
                                else if (!anyTrackPlaying) onPlay()
                            },
                            enabled = !anyTrackPlaying || isPaused,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = if (!anyTrackPlaying || isPaused) LabsPurple else LabsPurple.copy(alpha = 0.3f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        // Pause (only with custom tracks)
                        if (hasCustom) {
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(
                                onClick = { onPause() },
                                enabled = anyTrackPlaying && !isPaused,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = if (anyTrackPlaying && !isPaused) LabsPurple else TextSecondary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        // Stop
                        IconButton(
                            onClick = { onStop() },
                            enabled = anyTrackPlaying || isPaused,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = if (anyTrackPlaying || isPaused) Color(0xFFFF6B6B) else TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    tracks.forEach { track ->
                        MixTrackSlider(
                            track = track,
                            isTrackPlaying = trackPlayingState[track.id] == true,
                            isMuted = mutedTrackIds[track.id] == true,
                            onVolumeChange = { vol -> onTrackVolumeChange(track, vol) },
                            onMuteToggle = {
                                if (mutedTrackIds[track.id] == true) onUnmuteTrack(track.id)
                                else onMuteTrack(track.id)
                            }
                        )
                    }
                }
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
                Text("Remove \"${project.name}\" from playlist?", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("The mix will still be available in Mix Studio.", fontFamily = SpaceGrotesk, fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TextSecondary)
                    ) { Text("CANCEL", fontFamily = SpaceGrotesk, fontSize = 13.sp, color = TextSecondary) }
                    Button(
                        onClick = { onDelete(); showDeleteConfirm = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                    ) { Text("REMOVE", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun MixTrackSlider(
    track: MixTrack,
    isTrackPlaying: Boolean,
    isMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit
) {
    var localVol by remember(track.id, track.volume) { mutableStateOf(track.volume) }

    val icon = when (track.trackType) {
        "pad" -> Icons.Default.Piano
        "click" -> Icons.Default.Timer
        else -> Icons.Default.AudioFile
    }
    val color = when (track.trackType) {
        "pad" -> LedAmber
        "click" -> ClickTeal
        else -> LabsPurple
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .then(if (isMuted) Modifier.alpha(0.45f) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon = mute toggle
        Icon(
            if (isMuted) Icons.Default.VolumeOff else icon,
            contentDescription = if (isMuted) "Unmute" else "Mute",
            tint = if (isMuted) TextSecondary.copy(alpha = 0.4f)
                   else if (isTrackPlaying) color
                   else color.copy(alpha = 0.3f),
            modifier = Modifier
                .size(18.dp)
                .clickable { onMuteToggle() }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = track.label,
            fontSize = 11.sp,
            fontFamily = SpaceGrotesk,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier.width(56.dp),
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(4.dp))
        Slider(
            value = localVol,
            onValueChange = { localVol = it },
            onValueChangeFinished = { onVolumeChange(localVol) },
            modifier = Modifier.weight(1f).height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.4f),
                inactiveTrackColor = PadBorder
            )
        )
    }
}