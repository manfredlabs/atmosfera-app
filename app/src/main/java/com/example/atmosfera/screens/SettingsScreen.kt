package com.example.atmosfera.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manfredlabs.atmosfera.model.ClickChannel
import com.manfredlabs.atmosfera.model.PadChannel
import com.example.atmosfera.ui.theme.*

@Composable
fun SettingsScreen(
    padVolume: Float,
    clickVolume: Float,
    padChannel: PadChannel,
    clickChannel: ClickChannel,
    playingNote: String?,
    clickEnabled: Boolean,
    currentPackName: String = "Atmos",
    onPadVolumeChange: (Float) -> Unit,
    onClickVolumeChange: (Float) -> Unit,
    onPadChannelChange: (PadChannel) -> Unit,
    onClickChannelChange: (ClickChannel) -> Unit,
    onManagePacks: () -> Unit = {},
    timeSignature: String = "4/4",
    onTimeSignatureChange: (String) -> Unit = {}
){
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SETTINGS",
                    fontSize = 22.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 6.sp,
                    color = TextSecondary
                )
            }

            // ─── PAD Section ───
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "PAD",
                    fontSize = 13.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = if (playingNote != null) LedAmber else TextSecondary
                )

                // Pad channel [L][M][R]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(PadIdle, RoundedCornerShape(8.dp))
                        .border(1.dp, PadBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PadChannel.entries.forEach { channel ->
                        val isSelected = padChannel == channel
                        Surface(
                            onClick = { onPadChannelChange(channel) },
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isSelected && playingNote != null -> LedAmber.copy(alpha = 0.15f)
                                isSelected -> PadActive
                                else -> PadIdle
                            },
                            modifier = Modifier.padding(3.dp).weight(1f).fillMaxHeight()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = channel.label,
                                    fontSize = 14.sp,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected && playingNote != null -> LedAmber
                                        isSelected -> TextPrimary
                                        else -> TextSecondary
                                    }
                                )
                            }
                        }
                    }
                }

                // Pad volume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Volume",
                        fontSize = 13.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary,
                        modifier = Modifier.width(60.dp)
                    )
                    Slider(
                        value = padVolume,
                        onValueChange = onPadVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = if (playingNote != null) LedAmber else TextSecondary,
                            activeTrackColor = if (playingNote != null) LedAmberDim else PadActive,
                            inactiveTrackColor = PadBorder.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            HorizontalDivider(color = PadBorder.copy(alpha = 0.3f), thickness = 1.dp)

            // ─── CLICK Section ───
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "CLICK",
                    fontSize = 13.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = if (clickEnabled) ClickTeal else TextSecondary
                )

                // Click channel [L][M][R]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(PadIdle, RoundedCornerShape(8.dp))
                        .border(1.dp, PadBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClickChannel.entries.forEach { channel ->
                        val isSelected = clickChannel == channel
                        Surface(
                            onClick = { onClickChannelChange(channel) },
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isSelected && clickEnabled -> ClickTeal.copy(alpha = 0.15f)
                                isSelected -> PadActive
                                else -> PadIdle
                            },
                            modifier = Modifier.padding(3.dp).weight(1f).fillMaxHeight()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = channel.label,
                                    fontSize = 14.sp,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected && clickEnabled -> ClickTeal
                                        isSelected -> TextPrimary
                                        else -> TextSecondary
                                    }
                                )
                            }
                        }
                    }
                }

                // Click volume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Volume",
                        fontSize = 13.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary,
                        modifier = Modifier.width(60.dp)
                    )
                    Slider(
                        value = clickVolume,
                        onValueChange = onClickVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = if (clickEnabled) ClickTeal else TextSecondary,
                            activeTrackColor = if (clickEnabled) ClickTealDim else PadActive,
                            inactiveTrackColor = PadBorder.copy(alpha = 0.3f)
                        )
                    )
                }

                // Time Signature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Signature",
                        fontSize = 13.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary,
                        modifier = Modifier.width(60.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val signatures = listOf("2/4", "3/4", "4/4", "5/4", "6/4", "6/8", "7/4", "7/8")
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { expanded = true },
                            shape = CircleShape,
                            color = if (clickEnabled) ClickTeal.copy(alpha = 0.15f) else PadActive,
                            border = BorderStroke(
                                1.dp,
                                if (clickEnabled) ClickTeal.copy(alpha = 0.5f) else PadBorder.copy(alpha = 0.8f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = timeSignature,
                                    fontSize = 14.sp,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    color = if (clickEnabled) ClickTeal else TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = if (clickEnabled) ClickTeal.copy(alpha = 0.7f) else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            containerColor = PadIdle
                        ) {
                            signatures.forEach { sig ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = sig,
                                            fontSize = 14.sp,
                                            fontFamily = SpaceGrotesk,
                                            fontWeight = if (timeSignature == sig) FontWeight.Bold else FontWeight.Normal,
                                            color = if (timeSignature == sig && clickEnabled) ClickTeal
                                                    else if (timeSignature == sig) TextPrimary
                                                    else TextSecondary
                                        )
                                    },
                                    onClick = {
                                        onTimeSignatureChange(sig)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = PadBorder.copy(alpha = 0.3f), thickness = 1.dp)

            // ─── SOUND PACKS Section ───
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "SOUND PACKS",
                    fontSize = 13.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )

                Surface(
                    onClick = onManagePacks,
                    shape = RoundedCornerShape(8.dp),
                    color = PadIdle,
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sound Packs",
                            fontSize = 14.sp,
                            fontFamily = SpaceGrotesk,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Manage",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
}