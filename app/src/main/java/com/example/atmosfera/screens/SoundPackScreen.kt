package com.example.atmosfera.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.atmosfera.audio.PadProcessor
import com.example.atmosfera.data.SoundPad
import com.example.atmosfera.data.SoundPackDao
import com.example.atmosfera.data.SoundPack
import com.example.atmosfera.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val NOTE_LABELS = mapOf(
    "c" to "C", "cs" to "C#", "d" to "D", "ds" to "D#",
    "e" to "E", "f" to "F", "fs" to "F#", "g" to "G",
    "gs" to "G#", "a" to "A", "as" to "A#", "b" to "B"
)
private val NOTE_NAMES = listOf("c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b")

@Composable
fun SoundPackScreen(
    packId: Long?,
    soundPackDao: SoundPackDao,
    padProcessor: PadProcessor,
    soundPackDir: File,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isNewPack = packId == null

    var createdPackId by remember { mutableStateOf(packId) }
    val resolvedId = createdPackId ?: -1L
    val allPacks by soundPackDao.getAll().collectAsState(initial = emptyList())
    val currentPack = allPacks.find { it.id == resolvedId }
    val pads by soundPackDao.getPadsForPack(resolvedId).collectAsState(initial = emptyList())

    var nameField by remember { mutableStateOf(TextFieldValue("")) }
    var nameInitialized by remember { mutableStateOf(false) }
    var padMode by remember { mutableStateOf("maj") }
    var isProcessing by remember { mutableStateOf(false) }

    // Multi-select notes + per-note file
    var selectedNotes by remember { mutableStateOf(setOf<String>()) }
    var noteFiles by remember { mutableStateOf(mapOf<String, Pair<Uri, String>>()) }
    var pendingFileNote by remember { mutableStateOf<String?>(null) }

    // Init name from DB for existing packs
    LaunchedEffect(currentPack) {
        if (!nameInitialized && currentPack != null) {
            nameField = TextFieldValue(currentPack.name)
            nameInitialized = true
        }
    }

    // File picker — result goes to pendingFileNote
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val note = pendingFileNote
        if (uri != null && note != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
            noteFiles = noteFiles + (note to Pair(uri, fileName))
        }
        pendingFileNote = null
    }

    val handleBack: () -> Unit = {
        if (!isNewPack) {
            scope.launch {
                currentPack?.let {
                    if (nameField.text.trim() != it.name && nameField.text.isNotBlank()) {
                        soundPackDao.update(it.copy(name = nameField.text.trim()))
                    }
                }
            }
        }
        onBack()
    }

    val allFilesSelected = selectedNotes.isNotEmpty() && selectedNotes.all { it in noteFiles }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(onClick = handleBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
            }
            Text("SOUND PACK", fontSize = 22.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Light, letterSpacing = 2.sp, color = TextPrimary)
        }

        // ─── Name ───
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NAME", fontSize = 12.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = TextSecondary)
            OutlinedTextField(
                value = nameField,
                onValueChange = { nameField = it },
                placeholder = { Text("e.g. Troposphere", fontFamily = SpaceGrotesk, fontSize = 16.sp, color = TextSecondary.copy(alpha = 0.4f)) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontFamily = SpaceGrotesk),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedBorderColor = LedAmber, unfocusedBorderColor = PadBorder.copy(alpha = 0.5f),
                    focusedContainerColor = PadIdle, unfocusedContainerColor = PadIdle,
                    cursorColor = LedAmber
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ─── Key ───
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("KEY", fontSize = 12.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = TextSecondary)

            // NEU / MAJ / MIN — slide selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(PadIdle, RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, PadBorder.copy(alpha = 0.5f)), RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("neu" to "NEU", "maj" to "MAJ", "min" to "MIN").forEach { (mode, label) ->
                    val isSelected = padMode == mode
                    Surface(
                        onClick = {
                            if (padMode != mode) {
                                padMode = mode
                                selectedNotes = emptySet()
                                noteFiles = emptyMap()
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) LedAmber.copy(alpha = 0.15f) else PadIdle,
                        modifier = Modifier.padding(3.dp).weight(1f).fillMaxHeight()
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(label, fontSize = 14.sp, fontFamily = SpaceGrotesk,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) LedAmber else TextSecondary)
                        }
                    }
                }
            }

            // Note grid 4x3 — multi-select toggle
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NOTE_NAMES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { note ->
                            val isSelected = note in selectedNotes
                            val hasPad = pads.any { it.note == note && it.mode == padMode }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .alpha(if (hasPad && !isSelected) 0.4f else 1f)
                                    .background(
                                        when {
                                            isSelected -> LedAmber.copy(alpha = 0.15f)
                                            hasPad -> PadActive.copy(alpha = 0.15f)
                                            else -> PadIdle
                                        },
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isSelected -> LedAmber
                                            hasPad -> PadActive.copy(alpha = 0.5f)
                                            else -> PadBorder.copy(alpha = 0.3f)
                                        },
                                        RoundedCornerShape(8.dp)
                                    )
                                    .pointerInput(note, hasPad, isSelected, padMode) {
                                        detectTapGestures(
                                            onTap = {
                                                if (!hasPad) {
                                                    selectedNotes = if (isSelected) {
                                                        noteFiles = noteFiles - note
                                                        selectedNotes - note
                                                    } else {
                                                        selectedNotes + note
                                                    }
                                                }
                                            },
                                            onLongPress = {
                                                if (hasPad) {
                                                    // Re-enable for editing — add to selection with existing file info
                                                    selectedNotes = selectedNotes + note
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        (NOTE_LABELS[note] ?: note) + if (padMode == "min") "m" else "",
                                        fontSize = 15.sp, fontFamily = SpaceGrotesk,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> LedAmber
                                            hasPad -> PadActive
                                            else -> TextSecondary
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── File assignments ───
        if (selectedNotes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("FILES", fontSize = 12.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = TextSecondary)

                val orderedNotes = NOTE_NAMES.filter { it in selectedNotes }
                orderedNotes.forEach { note ->
                    val fileInfo = noteFiles[note]
                    val noteLabel = (NOTE_LABELS[note] ?: note) + if (padMode == "min") "m" else ""

                    Row(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(noteLabel, fontSize = 15.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = LedAmber, modifier = Modifier.width(36.dp))

                        Surface(
                            onClick = {
                                pendingFileNote = note
                                filePicker.launch(arrayOf("audio/*"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = PadIdle,
                            border = BorderStroke(1.dp, if (fileInfo != null) PadActive.copy(alpha = 0.5f) else PadBorder.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                                Text(
                                    text = fileInfo?.second ?: "Select file...",
                                    fontSize = 13.sp, fontFamily = SpaceGrotesk,
                                    color = if (fileInfo != null) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                                    maxLines = 1
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                noteFiles = noteFiles - note
                                selectedNotes = selectedNotes - note
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, "Remove", tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Processing indicator
        if (isProcessing) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = PadActive, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Importing...", fontFamily = SpaceGrotesk, color = TextSecondary, fontSize = 13.sp)
                }
            }
        }

        // ─── Buttons ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = handleBack,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("BACK", fontSize = 14.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Button(
                onClick = {
                    if (!allFilesSelected || nameField.text.isBlank()) return@Button
                    isProcessing = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val actualPackId = createdPackId ?: run {
                                val id = soundPackDao.insert(SoundPack(name = nameField.text.trim()))
                                createdPackId = id
                                id
                            }

                            val pack = soundPackDao.getById(actualPackId)
                            if (pack != null && nameField.text.trim() != pack.name) {
                                soundPackDao.update(pack.copy(name = nameField.text.trim()))
                            }

                            val packDir = File(soundPackDir, actualPackId.toString())
                            for ((note, filePair) in noteFiles) {
                                val (uri, _) = filePair
                                val outputFile = File(packDir, "pad_${note}_${padMode}")
                                val success = padProcessor.process(uri, outputFile)
                                if (success) {
                                    soundPackDao.removePad(actualPackId, note, padMode)
                                    soundPackDao.insertPad(
                                        SoundPad(packId = actualPackId, note = note, mode = padMode, filePath = outputFile.absolutePath)
                                    )
                                }
                            }
                        }
                        isProcessing = false
                        selectedNotes = emptySet()
                        noteFiles = emptyMap()
                    }
                },
                enabled = nameField.text.isNotBlank() && allFilesSelected && !isProcessing,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LedAmber, contentColor = DarkBg,
                    disabledContainerColor = PadActive, disabledContentColor = TextSecondary
                ),
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("SAVE", fontSize = 14.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
    }
}
