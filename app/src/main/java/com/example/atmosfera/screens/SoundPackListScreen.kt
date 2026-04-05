package com.example.atmosfera.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.atmosfera.data.SoundPack
import com.example.atmosfera.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPackListScreen(
    packs: List<SoundPack>,
    currentPackId: Long,
    onSelectPack: (Long) -> Unit,
    onCreatePack: () -> Unit,
    onEditPack: (Long) -> Unit,
    onDeletePack: (SoundPack) -> Unit,
    onBack: () -> Unit
) {
    var packToDelete by remember { mutableStateOf<SoundPack?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextSecondary
                    )
                }
                Text(
                    text = "SOUND PACKS",
                    fontSize = 22.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                    color = TextPrimary
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(packs, key = { it.id }) { pack ->
                    PackItem(
                        pack = pack,
                        onClick = { if (!pack.isDefault) onEditPack(pack.id) },
                        onDelete = { packToDelete = pack }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { onCreatePack() },
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

    // Delete confirmation bottom sheet
    packToDelete?.let { pack ->
        ModalBottomSheet(
            onDismissRequest = { packToDelete = null },
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
                    text = "Delete \"${pack.name}\"?",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "All pads in this pack will be removed.",
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
                        onClick = { packToDelete = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TextSecondary)
                    ) {
                        Text("CANCEL", fontFamily = SpaceGrotesk, fontSize = 13.sp, color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            onDeletePack(pack)
                            packToDelete = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                    ) {
                        Text("DELETE", fontFamily = SpaceGrotesk, fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PackItem(
    pack: SoundPack,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val revealWidthPx = with(density) { 70.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, PadBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        // Background delete button (revealed on swipe)
        if (offsetX.value < -1f && !pack.isDefault) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(DarkBg),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(70.dp)
                        .background(PadIdle)
                        .clickable {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                            onDelete()
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
                .background(PadIdle)
                .then(
                    if (!pack.isDefault) {
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
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pack.name,
                        fontFamily = SpaceGrotesk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextPrimary
                    )
                    val subtitle = if (pack.isDefault) "Built-in" else pack.description
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontFamily = SpaceGrotesk,
                            fontSize = 11.sp,
                            color = TextSecondary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
