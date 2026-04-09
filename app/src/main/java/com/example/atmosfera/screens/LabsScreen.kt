package com.example.atmosfera.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.atmosfera.ui.theme.*

@Composable
fun LabsScreen(
    onApplyBpm: (Int) -> Unit = {}
) {
    val tapTimes = remember { mutableStateListOf<Long>() }
    val tapBpm = remember { mutableIntStateOf(0) }
    val tapCount = remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Text(
            text = "LABS",
            fontSize = 13.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = TextSecondary
        )

        // Tap Tempo Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PadIdle,
            border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "TAP TEMPO",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )

                // BPM display
                Text(
                    text = if (tapBpm.intValue > 0) "${tapBpm.intValue}" else "—",
                    fontSize = 56.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    color = if (tapBpm.intValue > 0) ClickTeal else TextSecondary.copy(alpha = 0.3f)
                )

                Text(
                    text = when {
                        tapBpm.intValue > 0 -> "BPM  ·  ${tapCount.intValue} taps"
                        else -> "Tap the button to start"
                    },
                    fontSize = 13.sp,
                    fontFamily = SpaceGrotesk,
                    color = TextSecondary.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Tap button
                Surface(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2000) {
                            tapTimes.clear()
                        }
                        tapTimes.add(now)
                        if (tapTimes.size > 8) tapTimes.removeAt(0)
                        tapCount.intValue = tapTimes.size
                        if (tapTimes.size >= 2) {
                            val intervals = tapTimes.zipWithNext { a, b -> b - a }
                            val avgMs = intervals.average()
                            tapBpm.intValue = (60000.0 / avgMs).toInt().coerceIn(30, 240)
                        }
                    },
                    shape = CircleShape,
                    color = ClickTealDim,
                    border = BorderStroke(2.dp, ClickTeal.copy(alpha = 0.5f)),
                    modifier = Modifier.size(120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "TAP",
                            fontSize = 22.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            color = ClickTeal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reset
                    Surface(
                        onClick = {
                            tapTimes.clear()
                            tapBpm.intValue = 0
                            tapCount.intValue = 0
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = PadIdle,
                        border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("Reset", fontSize = 14.sp, fontFamily = SpaceGrotesk, color = TextSecondary)
                        }
                    }

                    // Apply → sets BPM and navigates to Live
                    Surface(
                        onClick = {
                            if (tapBpm.intValue > 0) {
                                onApplyBpm(tapBpm.intValue)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (tapBpm.intValue > 0) ClickTealDim else PadIdle,
                        border = BorderStroke(
                            1.dp,
                            if (tapBpm.intValue > 0) ClickTeal.copy(alpha = 0.4f)
                            else PadBorder.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                "Apply & Go Live",
                                fontSize = 14.sp,
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                color = if (tapBpm.intValue > 0) ClickTeal else TextSecondary.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
