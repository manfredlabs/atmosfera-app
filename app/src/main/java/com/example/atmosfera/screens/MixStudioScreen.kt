package com.example.atmosfera.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.atmosfera.data.MixProject
import com.example.atmosfera.data.MixProjectDao
import com.example.atmosfera.data.MixTrack
import com.example.atmosfera.data.SoundPack
import com.example.atmosfera.ui.theme.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
//  Mix Studio List (projects overview)
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun MixStudioListScreen(
    mixDao: MixProjectDao,
    onNavigateToEditor: (Long) -> Unit,
    onCreateNew: () -> Unit,
    onBack: () -> Unit
) {
    val projects by mixDao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header with back button
            Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
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
                    text = "MIX STUDIO",
                    fontSize = 22.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (projects.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No projects yet",
                            fontSize = 16.sp,
                            fontFamily = SpaceGrotesk,
                            color = TextSecondary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap + to create your first mix",
                            fontSize = 13.sp,
                            fontFamily = SpaceGrotesk,
                            color = TextSecondary.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    projects.forEach { project ->
                        MixProjectCard(
                            project = project,
                            mixDao = mixDao,
                            onClick = { onNavigateToEditor(project.id) },
                            onDelete = { scope.launch { mixDao.delete(project) } }
                        )
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = onCreateNew,
            containerColor = ClickTeal,
            contentColor = TextPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "New project")
        }
    }
}

@Composable
private fun MixProjectCard(
    project: MixProject,
    mixDao: MixProjectDao,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val tracks by mixDao.getTracksForProject(project.id).collectAsState(initial = emptyList())

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = PadIdle,
        border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = ClickTeal,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    fontSize = 17.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${tracks.size}/6 tracks",
                    fontSize = 13.sp,
                    fontFamily = SpaceGrotesk,
                    color = TextSecondary.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Mix Studio Editor
// ═══════════════════════════════════════════════════════════════════════

private val NOTE_LABELS_MIX = mapOf(
    "c" to "C", "cs" to "C#", "d" to "D", "ds" to "D#",
    "e" to "E", "f" to "F", "fs" to "F#", "g" to "G",
    "gs" to "G#", "a" to "A", "as" to "A#", "b" to "B"
)
private val NOTE_NAMES_MIX = listOf("c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b")
private val TIME_SIGS = listOf("2/4", "3/4", "4/4", "5/4", "6/4", "6/8", "7/4", "7/8")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixStudioEditorScreen(
    projectId: Long?,
    mixDao: MixProjectDao,
    allPacks: List<SoundPack>,
    defaultPadVolume: Float,
    defaultPadChannel: String,
    defaultClickVolume: Float,
    defaultClickChannel: String,
    defaultBpm: Int,
    defaultAccents: List<Int>,
    onBack: () -> Unit,
    onPickAudioFile: () -> Unit,
    pendingAudioUri: String?,
    pendingAudioName: String?,
    onAudioConsumed: () -> Unit,
    onStartTrack: (MixTrack) -> Unit,
    onStopTrack: (Long) -> Unit,
    onStartAll: (List<MixTrack>) -> Unit,
    onStopAll: () -> Unit,
    trackPlayingState: Map<Long, Boolean>
) {
    val scope = rememberCoroutineScope()

    var project by remember { mutableStateOf<MixProject?>(null) }
    val tracks by if (projectId != null && projectId > 0) {
        mixDao.getTracksForProject(projectId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<MixTrack>()) }
    }

    var nameField by remember { mutableStateOf(TextFieldValue("")) }
    var initialized by remember { mutableStateOf(false) }

    // Sheet states
    var showAddTrackSheet by remember { mutableStateOf(false) }
    var showPadConfig by remember { mutableStateOf(false) }
    var showClickConfig by remember { mutableStateOf(false) }

    // Pad config state
    var padNote by remember { mutableStateOf("c") }
    var padMode by remember { mutableStateOf("neu") }
    var padPackId by remember { mutableStateOf(-1L) }

    // Click config state
    var clickBpm by remember { mutableIntStateOf(defaultBpm) }
    var clickTimeSig by remember { mutableStateOf("4/4") }
    var clickAccents by remember { mutableStateOf(defaultAccents) }

    // Load or create project
    LaunchedEffect(projectId) {
        if (projectId != null && projectId > 0) {
            val existing = mixDao.getById(projectId)
            if (existing != null) {
                project = existing
                nameField = TextFieldValue(existing.name, TextRange(existing.name.length))
                initialized = true
            }
        }
        if (!initialized) {
            val newId = mixDao.insert(MixProject(name = "My Mix"))
            project = mixDao.getById(newId)
            nameField = TextFieldValue("My Mix", TextRange(7))
            initialized = true
        }
    }

    // Handle incoming audio file
    LaunchedEffect(pendingAudioUri) {
        if (pendingAudioUri != null && project != null) {
            val p = project!!
            val count = mixDao.getTrackCount(p.id)
            if (count < 6) {
                val track = MixTrack(
                    projectId = p.id,
                    trackType = "custom",
                    label = pendingAudioName ?: "Audio",
                    volume = 0.5f,
                    channel = "mono",
                    sortOrder = count,
                    filePath = pendingAudioUri,
                    fileName = pendingAudioName
                )
                mixDao.insertTrack(track)
            }
            onAudioConsumed()
        }
    }

    val currentProject = project ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header with back
        Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            IconButton(
                onClick = {
                    scope.launch {
                        if (nameField.text.isNotBlank()) {
                            mixDao.update(currentProject.copy(name = nameField.text.trim()))
                        }
                    }
                    onBack()
                },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary
                )
            }
            Text(
                text = "MIX STUDIO",
                fontSize = 22.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Project name
        OutlinedTextField(
            value = nameField,
            onValueChange = {
                if (it.text.length <= 30) {
                    nameField = it
                    scope.launch { mixDao.update(currentProject.copy(name = it.text.trim().ifBlank { "My Mix" })) }
                }
            },
            label = { Text("PROJECT NAME", fontFamily = SpaceGrotesk, fontSize = 11.sp, letterSpacing = 2.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = ClickTeal,
                unfocusedBorderColor = PadBorder,
                focusedLabelColor = ClickTeal,
                unfocusedLabelColor = TextSecondary,
                cursorColor = ClickTeal
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = SpaceGrotesk,
                fontSize = 16.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Track count header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRACKS",
                fontSize = 12.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                color = TextSecondary
            )
            Text(
                text = "${tracks.size}/6",
                fontSize = 12.sp,
                fontFamily = SpaceGrotesk,
                color = if (tracks.size >= 6) LedAmber else TextSecondary.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tracks list
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add tracks to start mixing",
                        fontSize = 14.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary.copy(alpha = 0.4f)
                    )
                }
            }

            tracks.forEach { track ->
                TrackCard(
                    track = track,
                    isPlaying = trackPlayingState[track.id] == true,
                    onPlayStop = {
                        if (trackPlayingState[track.id] == true) onStopTrack(track.id)
                        else onStartTrack(track)
                    },
                    onVolumeChange = { vol ->
                        scope.launch { mixDao.updateTrack(track.copy(volume = vol)) }
                    },
                    onChannelChange = { ch ->
                        scope.launch { mixDao.updateTrack(track.copy(channel = ch)) }
                    },
                    onDelete = { scope.launch {
                        onStopTrack(track.id)
                        mixDao.deleteTrack(track)
                    }}
                )
            }

            // Add track button
            if (tracks.size < 6) {
                Surface(
                    onClick = { showAddTrackSheet = true },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = ClickTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Track",
                            fontSize = 14.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Medium,
                            color = ClickTeal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom controls: Play All / Stop All
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val anyPlaying = trackPlayingState.values.any { it }

            Surface(
                onClick = {
                    if (anyPlaying) onStopAll()
                    else if (tracks.isNotEmpty()) onStartAll(tracks)
                },
                shape = RoundedCornerShape(10.dp),
                color = if (anyPlaying) LedAmber else ClickTeal,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (anyPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = DarkBg,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (anyPlaying) "Stop All" else "Play All",
                            fontSize = 15.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            color = DarkBg
                        )
                    }
                }
            }
        }
    }

    // ── Add Track Type Picker Sheet ────────────────────────────────────
    if (showAddTrackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddTrackSheet = false },
            containerColor = PadIdle,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ADD TRACK",
                    fontSize = 14.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Pad option
                val hasPad = tracks.any { it.trackType == "pad" }
                TrackTypeOption(
                    icon = Icons.Default.Piano,
                    title = "Pad",
                    subtitle = if (hasPad) "Already added" else "Loop a pad sound",
                    enabled = !hasPad,
                    onClick = {
                        showAddTrackSheet = false
                        showPadConfig = true
                    }
                )

                // Click option
                val hasClick = tracks.any { it.trackType == "click" }
                TrackTypeOption(
                    icon = Icons.Default.Timer,
                    title = "Click",
                    subtitle = if (hasClick) "Already added" else "Metronome track",
                    enabled = !hasClick,
                    onClick = {
                        showAddTrackSheet = false
                        clickBpm = defaultBpm
                        clickTimeSig = "4/4"
                        clickAccents = defaultAccents
                        showClickConfig = true
                    }
                )

                // Custom audio option
                TrackTypeOption(
                    icon = Icons.Default.AudioFile,
                    title = "Audio File",
                    subtitle = "Import MP3, WAV, OGG...",
                    enabled = true,
                    onClick = {
                        showAddTrackSheet = false
                        onPickAudioFile()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ── Pad Config Sheet ───────────────────────────────────────────────
    if (showPadConfig) {
        ModalBottomSheet(
            onDismissRequest = { showPadConfig = false },
            containerColor = PadIdle,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PAD TRACK",
                    fontSize = 14.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    color = TextSecondary
                )

                // Mode selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("neu" to "NEU", "maj" to "MAJ", "min" to "MIN").forEach { (mode, label) ->
                        Surface(
                            onClick = { padMode = mode },
                            shape = RoundedCornerShape(8.dp),
                            color = if (padMode == mode) ClickTeal.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (padMode == mode) ClickTeal else PadBorder.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Medium,
                                    color = if (padMode == mode) ClickTeal else TextSecondary
                                )
                            }
                        }
                    }
                }

                // Note grid 4x3
                val rows = NOTE_NAMES_MIX.chunked(4)
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { note ->
                            val selected = padNote == note
                            Surface(
                                onClick = { padNote = note },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) LedAmber.copy(alpha = 0.15f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) LedAmber else PadBorder.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = NOTE_LABELS_MIX[note] ?: note,
                                        fontSize = 15.sp,
                                        fontFamily = SpaceGrotesk,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selected) LedAmber else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Pack selector
                if (allPacks.size > 1) {
                    Text(
                        text = "SOUND PACK",
                        fontSize = 11.sp,
                        fontFamily = SpaceGrotesk,
                        letterSpacing = 2.sp,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allPacks.take(4).forEach { pack ->
                            val selected = padPackId == pack.id || (padPackId == -1L && pack.isDefault)
                            Surface(
                                onClick = { padPackId = pack.id },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) ClickTeal.copy(alpha = 0.15f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) ClickTeal else PadBorder.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = pack.name,
                                        fontSize = 12.sp,
                                        fontFamily = SpaceGrotesk,
                                        color = if (selected) ClickTeal else TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Save button
                Surface(
                    onClick = {
                        scope.launch {
                            val label = "Pad ${NOTE_LABELS_MIX[padNote] ?: padNote} ${padMode.uppercase()}"
                            val count = mixDao.getTrackCount(currentProject.id)
                            val effectivePackId = if (padPackId == -1L) {
                                allPacks.find { it.isDefault }?.id ?: -1L
                            } else padPackId
                            mixDao.insertTrack(
                                MixTrack(
                                    projectId = currentProject.id,
                                    trackType = "pad",
                                    label = label,
                                    volume = defaultPadVolume,
                                    channel = defaultPadChannel,
                                    sortOrder = count,
                                    note = padNote,
                                    padMode = padMode,
                                    soundPackId = effectivePackId
                                )
                            )
                        }
                        showPadConfig = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = ClickTeal,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Add Pad Track",
                            fontSize = 15.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            color = DarkBg
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ── Click Config Sheet ─────────────────────────────────────────────
    if (showClickConfig) {
        ModalBottomSheet(
            onDismissRequest = { showClickConfig = false },
            containerColor = PadIdle,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CLICK TRACK",
                    fontSize = 14.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    color = TextSecondary
                )

                // BPM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        onClick = { if (clickBpm > 30) clickBpm-- },
                        shape = RoundedCornerShape(8.dp),
                        color = PadIdle,
                        border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("−", fontSize = 20.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$clickBpm",
                            fontSize = 32.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            color = ClickTeal
                        )
                        Text(
                            text = "BPM",
                            fontSize = 11.sp,
                            fontFamily = SpaceGrotesk,
                            color = ClickTeal.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Surface(
                        onClick = { if (clickBpm < 240) clickBpm++ },
                        shape = RoundedCornerShape(8.dp),
                        color = PadIdle,
                        border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("+", fontSize = 20.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = TextSecondary)
                        }
                    }
                }

                // Time signature
                Text(
                    text = "TIME SIGNATURE",
                    fontSize = 11.sp,
                    fontFamily = SpaceGrotesk,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TIME_SIGS.forEach { sig ->
                        val sel = clickTimeSig == sig
                        Surface(
                            onClick = {
                                clickTimeSig = sig
                                val beats = sig.substringBefore("/").toInt()
                                clickAccents = List(beats) { if (it == 0) 1 else 0 }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (sel) ClickTeal.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (sel) ClickTeal else PadBorder.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = sig,
                                    fontSize = 13.sp,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) ClickTeal else TextSecondary
                                )
                            }
                        }
                    }
                }

                // Accent dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    clickAccents.forEachIndexed { index, state ->
                        if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                val next = when (state) { 1 -> 0; 0 -> 2; else -> 1 }
                                clickAccents = clickAccents.toMutableList().also { it[index] = next }
                            },
                            shape = CircleShape,
                            color = when (state) {
                                1 -> ClickTeal.copy(alpha = 0.2f)
                                2 -> Color.Transparent
                                else -> Color.Transparent
                            },
                            border = BorderStroke(
                                width = if (state == 1) 2.dp else 1.dp,
                                color = when (state) {
                                    1 -> ClickTeal
                                    2 -> PadBorder.copy(alpha = 0.15f)
                                    else -> PadBorder.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                if (state == 2) {
                                    Text("×", fontSize = 14.sp, fontFamily = SpaceGrotesk, color = TextSecondary.copy(alpha = 0.3f))
                                } else {
                                    Text(
                                        "${index + 1}",
                                        fontSize = 13.sp,
                                        fontFamily = SpaceGrotesk,
                                        fontWeight = if (state == 1) FontWeight.Bold else FontWeight.Normal,
                                        color = if (state == 1) ClickTeal else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Save button
                Surface(
                    onClick = {
                        scope.launch {
                            val count = mixDao.getTrackCount(currentProject.id)
                            val label = "Click ${clickBpm}bpm ${clickTimeSig}"
                            mixDao.insertTrack(
                                MixTrack(
                                    projectId = currentProject.id,
                                    trackType = "click",
                                    label = label,
                                    volume = defaultClickVolume,
                                    channel = defaultClickChannel,
                                    sortOrder = count,
                                    bpm = clickBpm,
                                    accents = clickAccents.joinToString(",")
                                )
                            )
                        }
                        showClickConfig = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = ClickTeal,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Add Click Track",
                            fontSize = 15.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            color = DarkBg
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Track Card
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun TrackCard(
    track: MixTrack,
    isPlaying: Boolean,
    onPlayStop: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onChannelChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var localVolume by remember(track.id, track.volume) { mutableStateOf(track.volume) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = PadIdle,
        border = BorderStroke(
            1.dp,
            if (isPlaying) ClickTeal.copy(alpha = 0.4f) else PadBorder.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Row 1: type icon, label, play/delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type icon
                val icon = when (track.trackType) {
                    "pad" -> Icons.Default.Piano
                    "click" -> Icons.Default.Timer
                    else -> Icons.Default.AudioFile
                }
                val tint = when (track.trackType) {
                    "pad" -> LedAmber
                    "click" -> ClickTeal
                    else -> TextPrimary
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))

                // Label
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.label,
                        fontSize = 15.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = track.trackType.replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary.copy(alpha = 0.5f)
                    )
                }

                // Play/stop button
                IconButton(onClick = onPlayStop, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isPlaying) LedAmber else ClickTeal,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Delete
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: volume slider + channel
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Volume slider
                Slider(
                    value = localVolume,
                    onValueChange = { localVolume = it },
                    onValueChangeFinished = { onVolumeChange(localVolume) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = when (track.trackType) {
                            "pad" -> LedAmber; "click" -> ClickTeal; else -> TextPrimary
                        },
                        activeTrackColor = when (track.trackType) {
                            "pad" -> LedAmber; "click" -> ClickTeal; else -> TextPrimary
                        },
                        inactiveTrackColor = PadBorder
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Channel selector (L / M / R)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("left" to "L", "mono" to "M", "right" to "R").forEach { (ch, label) ->
                        val sel = track.channel == ch
                        Surface(
                            onClick = { onChannelChange(ch) },
                            shape = RoundedCornerShape(4.dp),
                            color = if (sel) ClickTeal.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (sel) ClickTeal else PadBorder.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) ClickTeal else TextSecondary.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackTypeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) PadIdle else PadIdle.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().height(64.dp).alpha(if (enabled) 1f else 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ClickTeal, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    color = TextSecondary.copy(alpha = 0.5f)
                )
            }
            if (enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
