package com.example.atmosfera.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.atmosfera.data.SoundPack
import com.example.atmosfera.ui.theme.*

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
    var showDeleteDialog by remember { mutableStateOf<SoundPack?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header matching AddSongScreen style
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
            IconButton(
                onClick = { onCreatePack() },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Add, "Create Pack", tint = PadActive)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(packs) { pack ->
                val isSelected = pack.id == currentPackId
                Surface(
                    onClick = { onSelectPack(pack.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) PadActive.copy(alpha = 0.15f) else PadIdle,
                    border = if (isSelected) {
                        androidx.compose.foundation.BorderStroke(1.dp, PadActive.copy(alpha = 0.5f))
                    } else {
                        androidx.compose.foundation.BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pack.name,
                                fontFamily = SpaceGrotesk,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                            if (pack.isDefault) {
                                Text(
                                    text = "Built-in",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 11.sp,
                                    color = TextSecondary.copy(alpha = 0.6f)
                                )
                            }
                        }

                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = PadActive,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        if (!pack.isDefault) {
                            IconButton(onClick = { onEditPack(pack.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, "Edit", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { showDeleteDialog = pack }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Delete", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { pack ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Pack", fontFamily = SpaceGrotesk, color = TextPrimary) },
            text = {
                Text(
                    "Delete \"${pack.name}\" and all its pads?",
                    fontFamily = SpaceGrotesk,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePack(pack)
                    showDeleteDialog = null
                }) {
                    Text("DELETE", fontFamily = SpaceGrotesk, color = LedAmber)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("CANCEL", fontFamily = SpaceGrotesk, color = TextSecondary)
                }
            },
            containerColor = DarkBg,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
