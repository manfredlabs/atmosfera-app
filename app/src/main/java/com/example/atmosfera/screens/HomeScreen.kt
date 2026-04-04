package com.example.atmosfera.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.atmosfera.data.SoundPack
import com.example.atmosfera.model.Note
import com.example.atmosfera.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    notes: List<Note>,
    playingNote: String?,
    padMode: String,
    clickEnabled: Boolean,
    bpm: Int,
    accents: List<Boolean>,
    beatOn: Boolean,
    currentBeat: Int,
    currentPackName: String = "Atmos",
    currentPackId: Long = -1L,
    isDefaultPack: Boolean = true,
    availablePads: Set<String> = emptySet(),
    allPacks: List<SoundPack> = emptyList(),
    onPadTap: (Note) -> Unit,
    onPadLongPress: () -> Unit,
    onPadModeChange: (String) -> Unit,
    onClickToggle: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onAccentToggle: (Int) -> Unit,
    onSelectPack: (Long) -> Unit = {},
    onManagePacks: () -> Unit = {},
) {
    var showPackSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Bottom sheet for pack selection
    if (showPackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPackSheet = false },
            sheetState = sheetState,
            containerColor = PadIdle,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(32.dp)
                        .height(4.dp)
                        .background(PadBorder, RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SOUND PACKS",
                        fontSize = 13.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = TextSecondary
                    )
                    IconButton(
                        onClick = {
                            showPackSheet = false
                            onManagePacks()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Settings, "Manage", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                allPacks.forEach { pack ->
                    val isSelected = pack.id == currentPackId
                    Surface(
                        onClick = {
                            onSelectPack(pack.id)
                            showPackSheet = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) PadActive.copy(alpha = 0.15f) else DarkBg,
                        border = if (isSelected) {
                            BorderStroke(1.dp, PadActive.copy(alpha = 0.5f))
                        } else {
                            BorderStroke(1.dp, PadBorder.copy(alpha = 0.2f))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pack.name,
                                fontFamily = SpaceGrotesk,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = PadActive,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top row: NEU/MAJ/MIN (left) + Pack chip (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // NEU/MAJ/MIN toggle
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .background(PadIdle, RoundedCornerShape(8.dp))
                    .border(1.dp, PadBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("neu" to "NEU", "maj" to "MAJ", "min" to "MIN").forEach { (mode, label) ->
                    val isSelected = padMode == mode
                    Surface(
                        onClick = { if (padMode != mode) onPadModeChange(mode) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) PadActive else PadIdle,
                        modifier = Modifier.padding(3.dp).width(48.dp).fillMaxHeight()
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontFamily = SpaceGrotesk,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            // Pack selector chip
            Surface(
                onClick = { showPackSheet = true },
                shape = RoundedCornerShape(8.dp),
                color = PadIdle,
                border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.5f)),
                modifier = Modifier.height(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        text = currentPackName,
                        fontSize = 12.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary
                    )
                }
            }
        }

        // Pad grid 3x4
        val rows = notes.chunked(3)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { note ->
                        val isActive = playingNote == note.label
                        val padAvailable = isDefaultPack || "${note.name}:${padMode}" in availablePads

                        val bgColor by animateColorAsState(
                            targetValue = if (isActive) LedAmber.copy(alpha = 0.15f) else PadIdle,
                            animationSpec = tween(200),
                            label = "padColor"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .alpha(if (padAvailable) 1f else 0.3f)
                                .background(bgColor, RoundedCornerShape(12.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) LedAmber.copy(alpha = 0.5f) else PadBorder.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .then(
                                    if (padAvailable) {
                                        Modifier.pointerInput(note.label, playingNote, padMode) {
                                            detectTapGestures(
                                                onTap = { onPadTap(note) },
                                                onLongPress = { onPadLongPress() }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                            val displayLabel = when (padMode) {
                                "min" -> "${note.label}m"
                                else -> note.label
                            }
                            Text(
                                text = displayLabel,
                                fontFamily = SpaceGrotesk,
                                fontSize = if (padMode == "min") 27.sp else 29.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isActive) LedAmber else TextOnPad,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // ─── CLICK controls ───

        // CLICK ON/OFF toggle
        Surface(
            onClick = { onClickToggle() },
            shape = RoundedCornerShape(8.dp),
            color = if (clickEnabled) ClickTealDim else PadIdle,
            border = BorderStroke(1.dp, if (clickEnabled) ClickTeal.copy(alpha = 0.4f) else PadBorder.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().height(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = if (clickEnabled) "CLICK ON" else "CLICK OFF",
                    fontSize = 14.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    color = if (clickEnabled) ClickTeal else TextSecondary
                )
            }
        }

        // ◀ BPM ▶
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◀",
                fontSize = 22.sp,
                color = TextSecondary,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { onBpmChange(-1) })
                }
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = "$bpm",
                fontSize = 24.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                color = if (clickEnabled) ClickTeal else TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = "▶",
                fontSize = 22.sp,
                color = TextSecondary,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { onBpmChange(1) })
                }
            )
        }

        // Accent circles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            accents.forEachIndexed { index, isAccent ->
                if (index > 0) Spacer(modifier = Modifier.width(14.dp))
                val isCurrent = clickEnabled && beatOn && currentBeat == index
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = when {
                                isCurrent && isAccent -> ClickTeal
                                isCurrent -> ClickTeal.copy(alpha = 0.7f)
                                isAccent && clickEnabled -> ClickTealDim
                                isAccent -> PadActive
                                else -> PadIdle
                            },
                            shape = CircleShape
                        )
                        .border(
                            width = 1.5f.dp,
                            color = when {
                                isCurrent -> ClickTeal
                                isAccent && clickEnabled -> ClickTeal.copy(alpha = 0.5f)
                                isAccent -> PadBorder.copy(alpha = 0.8f)
                                else -> PadBorder
                            },
                            shape = CircleShape
                        )
                        .pointerInput(index) {
                            detectTapGestures(onTap = { onAccentToggle(index) })
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(0.dp))
    }
}
