package com.example.atmosfera.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.atmosfera.data.Song
import com.example.atmosfera.data.SongDao
import com.example.atmosfera.ui.theme.*
import kotlinx.coroutines.launch

private val NOTE_LABELS = mapOf(
    "c" to "C", "cs" to "C#", "d" to "D", "ds" to "D#",
    "e" to "E", "f" to "F", "fs" to "F#", "g" to "G",
    "gs" to "G#", "a" to "A", "as" to "A#", "b" to "B"
)

private val NOTE_NAMES = listOf("c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongScreen(
    songDao: SongDao,
    onBack: () -> Unit,
    editSongId: Long? = null,
    allPacks: List<com.example.atmosfera.data.SoundPack> = emptyList(),
    currentPackId: Long = -1L,
    soundPackDao: com.example.atmosfera.data.SoundPackDao? = null
) {
    val songs by songDao.getAll().collectAsState(initial = null)
    var editSong by remember { mutableStateOf<Song?>(null) }
    val isEditMode = editSongId != null
    val scope = rememberCoroutineScope()

    var nameField by remember { mutableStateOf(TextFieldValue("")) }
    var nameInitialized by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var selectedNote by remember { mutableStateOf("c") }
    var padMode by remember { mutableStateOf("maj") }
    var bpm by remember { mutableIntStateOf(90) }
    var accents by remember { mutableStateOf(listOf(true, false, false, false)) }
    var clickEnabled by remember { mutableStateOf(true) }
    var selectedPackId by remember { mutableStateOf(currentPackId) }
    var showPackSheet by remember { mutableStateOf(false) }

    val selectedPack = allPacks.find { it.id == selectedPackId }
    val isDefaultPack = selectedPack?.isDefault == true || selectedPackId == -1L

    val packPads by (soundPackDao?.getPadsForPack(selectedPackId)
        ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList())

    val availableModes = if (isDefaultPack) listOf("neu", "maj", "min")
        else packPads.map { it.mode }.distinct()
    val availableNotes: (String) -> Set<String> = { mode ->
        if (isDefaultPack) NOTE_NAMES.toSet()
        else packPads.filter { it.mode == mode }.map { it.note }.toSet()
    }

    // Reset selection when pack changes and mode/note not available
    LaunchedEffect(selectedPackId, availableModes) {
        if (!isDefaultPack && padMode !in availableModes && availableModes.isNotEmpty()) {
            padMode = availableModes.first()
        }
    }
    LaunchedEffect(selectedPackId, padMode, packPads) {
        if (!isDefaultPack) {
            val notes = availableNotes(padMode)
            if (selectedNote !in notes && notes.isNotEmpty()) {
                selectedNote = notes.first()
            }
        }
    }

    if (showPackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPackSheet = false },
            containerColor = DarkBg,
            dragHandle = {
                Box(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .background(TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SOUND PACK",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                allPacks.forEach { pack ->
                    val isSelected = selectedPackId == pack.id
                    Surface(
                        onClick = {
                            selectedPackId = pack.id
                            showPackSheet = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) LedAmber.copy(alpha = 0.15f) else PadIdle,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) LedAmber else PadBorder.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = pack.name,
                                fontSize = 15.sp,
                                fontFamily = SpaceGrotesk,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) LedAmber else TextSecondary
                            )
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    fontSize = 14.sp,
                                    color = LedAmber,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Load song for edit mode
    LaunchedEffect(editSongId) {
        if (editSongId != null) {
            val s = songDao.getById(editSongId)
            if (s != null) {
                editSong = s
                nameField = TextFieldValue(
                    text = s.name
                )
                selectedNote = s.note
                padMode = s.padMode
                bpm = s.bpm
                accents = s.accentList()
                clickEnabled = s.clickEnabled
                selectedPackId = if (s.soundPackId == -1L) currentPackId else s.soundPackId
                nameInitialized = true
            }
        }
    }

    // Initialize for new song (wait for songs to load from DB)
    LaunchedEffect(songs) {
        val loadedSongs = songs
        if (loadedSongs != null && !nameInitialized && !isEditMode) {
            val maxNum = loadedSongs
                .mapNotNull { it.name.removePrefix("MySong#").toIntOrNull() }
                .maxOrNull() ?: 0
            val defaultName = "MySong#${maxNum + 1}"
            nameField = TextFieldValue(
                text = defaultName,
                selection = TextRange(0, defaultName.length)
            )
            nameInitialized = true
        }
    }

    LaunchedEffect(nameInitialized) {
        if (nameInitialized && !isEditMode) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header with back
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
                text = if (isEditMode) "EDIT SONG" else "NEW SONG",
                fontSize = 22.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                color = TextPrimary
            )
        }

        // ─── Nome ───
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "NAME",
                fontSize = 12.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = TextSecondary
            )
            OutlinedTextField(
                value = nameField,
                onValueChange = { nameField = it },
                placeholder = {
                    Text(
                        "e.g. How Great Is Our God",
                        fontFamily = SpaceGrotesk,
                        fontSize = 16.sp,
                        color = TextSecondary.copy(alpha = 0.4f)
                    )
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp,
                    fontFamily = SpaceGrotesk
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = LedAmber,
                    unfocusedBorderColor = PadBorder.copy(alpha = 0.5f),
                    focusedContainerColor = PadIdle,
                    unfocusedContainerColor = PadIdle,
                    cursorColor = LedAmber
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        }

        // ─── Sound Pack ───
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "SOUND PACK",
                fontSize = 12.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = TextSecondary
            )
            val selectedPackName = allPacks.find { it.id == selectedPackId }?.name ?: "Atmos"
            Surface(
                onClick = { showPackSheet = true },
                shape = RoundedCornerShape(8.dp),
                color = PadIdle,
                border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = selectedPackName,
                        fontSize = 15.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Normal,
                        color = TextPrimary
                    )
                    Text(
                        text = "▼",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }

        // ─── Tom ───
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "KEY",
                fontSize = 12.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = TextSecondary
            )

            // NEU / MAJ / MIN
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("neu" to "NEU", "maj" to "MAJ", "min" to "MIN").forEach { (mode, label) ->
                    val isSelected = padMode == mode
                    val isAvailable = mode in availableModes
                    Surface(
                        onClick = { if (isAvailable) padMode = mode },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) LedAmber.copy(alpha = 0.15f) else PadIdle,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) LedAmber else PadBorder.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f).height(48.dp)
                            .then(if (!isAvailable) Modifier.alpha(0.3f) else Modifier)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                label,
                                fontSize = 15.sp,
                                fontFamily = SpaceGrotesk,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) LedAmber else TextSecondary
                            )
                        }
                    }
                }
            }

            // Note grid 4x3
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val notesForMode = availableNotes(padMode)
                NOTE_NAMES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { note ->
                            val isSelected = selectedNote == note
                            val isAvailable = note in notesForMode
                            Surface(
                                onClick = { if (isAvailable) selectedNote = note },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) LedAmber.copy(alpha = 0.15f) else PadIdle,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) LedAmber else PadBorder.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f).height(54.dp)
                                    .then(if (!isAvailable) Modifier.alpha(0.3f) else Modifier)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        (NOTE_LABELS[note] ?: note) + if (padMode == "min") "m" else "",
                                        fontSize = 15.sp,
                                        fontFamily = SpaceGrotesk,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) LedAmber else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── Click ───
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "CLICK",
                fontSize = 12.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = TextSecondary
            )

            // CLICK ON/OFF toggle
            Surface(
                onClick = { clickEnabled = !clickEnabled },
                shape = RoundedCornerShape(8.dp),
                color = if (clickEnabled) ClickTeal.copy(alpha = 0.15f) else PadIdle,
                border = BorderStroke(
                    1.dp,
                    if (clickEnabled) ClickTeal else PadBorder.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        if (clickEnabled) "CLICK ON" else "CLICK OFF",
                        fontSize = 15.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        color = if (clickEnabled) ClickTeal else TextSecondary
                    )
                }
            }

            // BPM + Accents
            AnimatedVisibility(visible = clickEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // BPM
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "◀",
                            fontSize = 24.sp,
                            color = TextSecondary,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { bpm = (bpm - 1).coerceIn(30, 240) },
                                    onLongPress = { bpm = (bpm - 10).coerceIn(30, 240) }
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        Text(
                            "$bpm",
                            fontSize = 28.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            color = ClickTeal
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        Text(
                            "▶",
                            fontSize = 24.sp,
                            color = TextSecondary,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { bpm = (bpm + 1).coerceIn(30, 240) },
                                    onLongPress = { bpm = (bpm + 10).coerceIn(30, 240) }
                                )
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
                            if (index > 0) Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (isAccent) ClickTeal.copy(alpha = 0.2f) else Color.Transparent,
                                        CircleShape
                                    )
                                    .border(
                                        1.5.dp,
                                        if (isAccent) ClickTeal else PadBorder.copy(alpha = 0.4f),
                                        CircleShape
                                    )
                                    .clickable {
                                        accents = accents
                                            .toMutableList()
                                            .also { it[index] = !it[index] }
                                    }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ─── Buttons ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    "CANCEL",
                    fontSize = 14.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
            Button(
                onClick = {
                    if (nameField.text.isNotBlank()) {
                        scope.launch {
                            if (isEditMode && editSong != null) {
                                songDao.update(
                                    editSong!!.copy(
                                        name = nameField.text.trim(),
                                        note = selectedNote,
                                        isMajor = padMode == "maj",
                                        bpm = bpm,
                                        accents = Song.accentsToString(accents),
                                        clickEnabled = clickEnabled,
                                        padMode = padMode,
                                        soundPackId = selectedPackId
                                    )
                                )
                            } else {
                                songDao.insert(
                                    Song(
                                        name = nameField.text.trim(),
                                        note = selectedNote,
                                        isMajor = padMode == "maj",
                                        bpm = bpm,
                                        accents = Song.accentsToString(accents),
                                        clickEnabled = clickEnabled,
                                        padMode = padMode,
                                        soundPackId = selectedPackId
                                    )
                                )
                            }
                        }
                        onBack()
                    }
                },
                enabled = nameField.text.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LedAmber,
                    contentColor = DarkBg,
                    disabledContainerColor = PadActive,
                    disabledContentColor = TextSecondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    "SAVE",
                    fontSize = 14.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
