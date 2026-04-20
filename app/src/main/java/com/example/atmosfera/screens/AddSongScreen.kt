package com.example.atmosfera.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manfredlabs.atmosfera.model.Song
import com.manfredlabs.atmosfera.db.SongDao
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
    allPacks: List<com.manfredlabs.atmosfera.model.SoundPack> = emptyList(),
    currentPackId: Long = -1L,
    soundPackDao: com.manfredlabs.atmosfera.db.SoundPackDao? = null,
    liveBpm: Int = 90,
    liveAccents: List<Int> = listOf(1, 0, 0, 0),
    liveClickEnabled: Boolean = true,
    livePadMode: String = "maj",
    liveNote: String = "c",
    livePadVolume: Float = 0.5f,
    livePadChannel: String = "mono",
    liveClickVolume: Float = 0.5f,
    liveClickChannel: String = "mono"
) {
    val songs by songDao.getAll().collectAsState(initial = null)
    var editSong by remember { mutableStateOf<Song?>(null) }
    val isEditMode = editSongId != null
    val scope = rememberCoroutineScope()

    var nameField by remember { mutableStateOf(TextFieldValue("")) }
    var nameInitialized by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var selectedNote by remember { mutableStateOf(liveNote) }
    var padMode by remember { mutableStateOf(livePadMode) }
    var bpm by remember { mutableIntStateOf(liveBpm) }
    var accents by remember { mutableStateOf(liveAccents) }
    var timeSignature by remember {
        val beats = liveAccents.size
        val sig = when (beats) {
            2 -> "2/4"; 3 -> "3/4"; 5 -> "5/4"; 6 -> "6/4"; 7 -> "7/4"; else -> "4/4"
        }
        mutableStateOf(sig)
    }
    var clickEnabled by remember { mutableStateOf(if (editSongId != null) liveClickEnabled else false) }
    var padVolume by remember { mutableFloatStateOf(livePadVolume) }
    var padChannel by remember { mutableStateOf(livePadChannel) }
    var clickVolume by remember { mutableFloatStateOf(liveClickVolume) }
    var clickChannel by remember { mutableStateOf(liveClickChannel) }
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
                timeSignature = when (accents.size) {
                    2 -> "2/4"; 3 -> "3/4"; 5 -> "5/4"; 6 -> "6/4"; 7 -> "7/4"; else -> "4/4"
                }
                clickEnabled = s.clickEnabled
                padVolume = s.padVolume
                padChannel = s.padChannel
                clickVolume = s.clickVolume
                clickChannel = s.clickChannel
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

    Box(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 600.dp)
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
                onValueChange = { if (it.text.length <= 30) nameField = it },
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

        // ─── PAD Card ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, LedAmber.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .background(PadIdle)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PAD",
                    fontSize = 13.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = LedAmber
                )

                // Channel bar [L][M][R]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(PadIdle, RoundedCornerShape(8.dp))
                        .border(1.dp, PadBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("left" to "L", "mono" to "M", "right" to "R").forEach { (ch, label) ->
                        val isSelected = padChannel == ch
                        Surface(
                            onClick = { padChannel = ch },
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

                // Volume row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Volume", fontSize = 13.sp, fontFamily = SpaceGrotesk, color = TextSecondary,
                        modifier = Modifier.width(60.dp))
                    Slider(
                        value = padVolume,
                        onValueChange = { padVolume = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = LedAmber,
                            activeTrackColor = LedAmberDim,
                            inactiveTrackColor = PadBorder.copy(alpha = 0.3f)
                        )
                    )
                }

                // Pack selector
                val selectedPackName = allPacks.find { it.id == selectedPackId }?.name ?: "Atmos"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pack", fontSize = 13.sp, fontFamily = SpaceGrotesk, color = TextSecondary,
                        modifier = Modifier.width(60.dp))
                    Surface(
                        onClick = { showPackSheet = true },
                        shape = RoundedCornerShape(8.dp),
                        color = PadIdle,
                        border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                            Text(selectedPackName, fontSize = 14.sp, fontFamily = SpaceGrotesk, color = TextPrimary)
                            Text("▼", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.align(Alignment.CenterEnd))
                        }
                    }
                }

                // Mode selector (NEU / MAJ / MIN)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(PadIdle, RoundedCornerShape(8.dp))
                        .border(1.dp, PadBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("neu" to "NEU", "maj" to "MAJ", "min" to "MIN").forEach { (mode, label) ->
                        val isSelected = padMode == mode
                        val isAvailable = mode in availableModes
                        Surface(
                            onClick = { if (isAvailable) padMode = mode },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) LedAmber.copy(alpha = 0.15f) else PadIdle,
                            modifier = Modifier.padding(3.dp).weight(1f).fillMaxHeight()
                                .then(if (!isAvailable) Modifier.alpha(0.3f) else Modifier)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(label, fontSize = 14.sp, fontFamily = SpaceGrotesk,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) LedAmber else TextSecondary)
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
                                            NOTE_LABELS[note] ?: note,
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
        }

        // ─── CLICK Card ───
        val clickAccentColor = if (clickEnabled) ClickTeal else TextSecondary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, clickAccentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .background(PadIdle)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CLICK",
                    fontSize = 13.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = clickAccentColor
                )

                // CLICK toggle
                val clickBtnHeight = 36.dp
                AnimatedContent(
                    targetState = clickEnabled,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "clickToggle"
                ) { enabled ->
                    if (!enabled) {
                        Surface(
                            onClick = { clickEnabled = true },
                            shape = RoundedCornerShape(8.dp),
                            color = PadIdle,
                            border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().height(clickBtnHeight)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    "CLICK OFF  —  TAP TO ENABLE",
                                    fontSize = 13.sp,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { clickEnabled = false },
                                    shape = RoundedCornerShape(8.dp),
                                    color = ClickTeal.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, ClickTeal),
                                    modifier = Modifier.weight(1f).height(clickBtnHeight)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text("CLICK ON", fontSize = 13.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = ClickTeal)
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).height(clickBtnHeight), contentAlignment = Alignment.CenterStart) {
                                    val atMin = bpm <= 30
                                    Surface(
                                        onClick = { if (!atMin) bpm = bpm - 1 },
                                        shape = RoundedCornerShape(8.dp),
                                        color = PadIdle,
                                        border = BorderStroke(1.dp, PadBorder.copy(alpha = if (atMin) 0.15f else 0.3f)),
                                        modifier = Modifier.width(44.dp).height(clickBtnHeight)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text("−", fontSize = 20.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = if (atMin) TextSecondary.copy(alpha = 0.2f) else TextSecondary)
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).height(clickBtnHeight), contentAlignment = Alignment.CenterEnd) {
                                    val atMax = bpm >= 240
                                    Surface(
                                        onClick = { if (!atMax) bpm = bpm + 1 },
                                        shape = RoundedCornerShape(8.dp),
                                        color = PadIdle,
                                        border = BorderStroke(1.dp, PadBorder.copy(alpha = if (atMax) 0.15f else 0.3f)),
                                        modifier = Modifier.width(44.dp).height(clickBtnHeight)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text("+", fontSize = 20.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = if (atMax) TextSecondary.copy(alpha = 0.2f) else TextSecondary)
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(2f / 3f)
                                    .height(clickBtnHeight)
                                    .align(Alignment.CenterEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("$bpm", fontSize = 24.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = ClickTeal)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("BPM", fontSize = 10.sp, fontFamily = SpaceGrotesk, color = ClickTeal.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 3.dp))
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = clickEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        // Channel bar [L][M][R]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(PadIdle, RoundedCornerShape(8.dp))
                                .border(1.dp, PadBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("left" to "L", "mono" to "M", "right" to "R").forEach { (ch, label) ->
                                val isSelected = clickChannel == ch
                                Surface(
                                    onClick = { clickChannel = ch },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) ClickTeal.copy(alpha = 0.15f) else PadIdle,
                                    modifier = Modifier.padding(3.dp).weight(1f).fillMaxHeight()
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(label, fontSize = 14.sp, fontFamily = SpaceGrotesk,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) ClickTeal else TextSecondary)
                                    }
                                }
                            }
                        }

                        // Volume row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Volume", fontSize = 13.sp, fontFamily = SpaceGrotesk, color = TextSecondary,
                                modifier = Modifier.width(60.dp))
                            Slider(
                                value = clickVolume,
                                onValueChange = { clickVolume = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f).height(28.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = ClickTeal,
                                    activeTrackColor = ClickTealDim,
                                    inactiveTrackColor = PadBorder.copy(alpha = 0.3f)
                                )
                            )
                        }

                        // Time Signature selector
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "TIME SIGNATURE",
                                fontSize = 12.sp,
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = TextSecondary
                            )
                            val signatures = listOf("2/4", "3/4", "4/4", "5/4", "6/4", "6/8", "7/4", "7/8")
                            signatures.chunked(4).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    row.forEach { sig ->
                                        val isSelected = timeSignature == sig
                                        Surface(
                                            onClick = {
                                                timeSignature = sig
                                                val beats = sig.substringBefore("/").toInt()
                                                accents = List(beats) { if (it == 0) 1 else 0 }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = when {
                                                isSelected && clickEnabled -> ClickTeal.copy(alpha = 0.15f)
                                                isSelected -> PadActive
                                                else -> PadIdle
                                            },
                                            border = BorderStroke(
                                                1.dp,
                                                when {
                                                    isSelected && clickEnabled -> ClickTeal.copy(alpha = 0.5f)
                                                    isSelected -> PadBorder.copy(alpha = 0.8f)
                                                    else -> PadBorder.copy(alpha = 0.3f)
                                                }
                                            ),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Text(
                                                    text = sig,
                                                    fontSize = 14.sp,
                                                    fontFamily = SpaceGrotesk,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = when {
                                                        isSelected && clickEnabled -> ClickTeal
                                                        isSelected -> TextPrimary
                                                        else -> TextSecondary.copy(alpha = 0.7f)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Accent circles (numbered)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            accents.forEachIndexed { index, beatState ->
                                if (index > 0) Spacer(modifier = Modifier.width(10.dp))
                                val isAccent = beatState == 1
                                val isMuted = beatState == 2
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            when {
                                                isMuted -> Color.Transparent
                                                isAccent -> ClickTeal.copy(alpha = 0.2f)
                                                else -> Color.Transparent
                                            },
                                            CircleShape
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                isMuted -> PadBorder.copy(alpha = 0.15f)
                                                isAccent -> ClickTeal
                                                else -> PadBorder.copy(alpha = 0.4f)
                                            },
                                            CircleShape
                                        )
                                        .clickable {
                                            accents = accents
                                                .toMutableList()
                                                .also { it[index] = when (it[index]) { 1 -> 0; 0 -> 2; else -> 1 } }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isMuted) "×" else "${index + 1}",
                                        fontSize = 16.sp,
                                        fontFamily = SpaceGrotesk,
                                        fontWeight = if (isAccent) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isMuted -> TextSecondary.copy(alpha = 0.2f)
                                            isAccent -> ClickTeal
                                            else -> TextSecondary.copy(alpha = 0.4f)
                                        }
                                    )
                                }
                            }
                        }
                    } // end AnimatedVisibility Column
                } // end AnimatedVisibility
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
                                        soundPackId = selectedPackId,
                                        padVolume = padVolume,
                                        padChannel = padChannel,
                                        clickVolume = clickVolume,
                                        clickChannel = clickChannel
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
                                        soundPackId = selectedPackId,
                                        padVolume = padVolume,
                                        padChannel = padChannel,
                                        clickVolume = clickVolume,
                                        clickChannel = clickChannel
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
    } // Box
}


