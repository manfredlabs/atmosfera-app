package com.example.atmosfera.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.manfredlabs.atmosfera.model.MixProject
import com.manfredlabs.atmosfera.db.MixProjectDao
import com.manfredlabs.atmosfera.model.MixTrack
import com.manfredlabs.atmosfera.model.SoundPack
import com.manfredlabs.atmosfera.db.SoundPackDao
import com.example.atmosfera.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════
//  Mix Studio List (projects overview)
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun MixStudioListScreen(
    mixDao: MixProjectDao,
    onNavigateToEditor: (Long) -> Unit,
    onCreateNew: () -> Unit,
    onBack: () -> Unit,
    onDeleteProject: (MixProject) -> Unit = {}
) {
    val projects by mixDao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 600.dp)
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
                            onDelete = { onDeleteProject(project) }
                        )
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = onCreateNew,
            containerColor = LabsPurple,
            contentColor = TextPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "New project")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MixProjectCard(
    project: MixProject,
    mixDao: MixProjectDao,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val tracks by mixDao.getTracksForProject(project.id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
        if (offsetX.value < -1f) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(PadIdle)
                .pointerInput(Unit) {
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
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(LabsPurple.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = LabsPurple,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    fontSize = 18.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Normal,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${tracks.size}/6 tracks",
                    fontSize = 13.sp,
                    fontFamily = SpaceGrotesk,
                    color = TextSecondary.copy(alpha = 0.6f)
                )
            }
            if (project.inPlaylist) {
                Icon(
                    Icons.Default.PlaylistAddCheck,
                    contentDescription = "In Playlist",
                    tint = LabsPurple.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // Delete confirmation bottom sheet
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
                    "Delete \"${project.name}\"?",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This action cannot be undone.",
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
                    ) { Text("CANCEL", fontFamily = SpaceGrotesk, fontSize = 13.sp, color = TextSecondary) }
                    Button(
                        onClick = { onDelete(); showDeleteConfirm = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                    ) { Text("DELETE", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White) }
                }
            }
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

data class MixEditorDefaults(
    val padVolume: Float,
    val padChannel: String,
    val clickVolume: Float,
    val clickChannel: String,
    val bpm: Int,
    val accents: List<Int>
)

data class MixEditorCallbacks(
    val onBack: () -> Unit,
    val onPickAudioFile: () -> Unit,
    val onAudioConsumed: () -> Unit,
    val onStartTrack: (MixTrack) -> Unit,
    val onStopTrack: (Long) -> Unit,
    val onMuteTrack: (Long) -> Unit = {},
    val onUnmuteTrack: (Long) -> Unit = {},
    val onSetTrackVolume: (Long, Float) -> Unit = { _, _ -> },
    val onStartAll: (List<MixTrack>) -> Unit,
    val onStopAll: () -> Unit,
    val onPauseAll: () -> Unit = {},
    val onResumeAll: (List<MixTrack>) -> Unit = {},
    val onSeekAll: (Long) -> Unit = {},
    val getPositionMs: () -> Long = { 0L },
    val getDurationMs: () -> Long = { 0L },
    val hasCustomTracks: () -> Boolean = { false },
    val isEnginePaused: () -> Boolean = { false },
    val onDeleteTrackFile: (String?) -> Unit = {},
    val onProjectReady: (Long) -> Unit = {},
    val onGoToPlaylist: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixStudioEditorScreen(
    projectId: Long?,
    mixDao: MixProjectDao,
    soundPackDao: SoundPackDao,
    allPacks: List<SoundPack>,
    defaults: MixEditorDefaults,
    callbacks: MixEditorCallbacks,
    pendingAudioUri: String?,
    pendingAudioName: String?,
    trackPlayingState: Map<Long, Boolean>,
    mutedTrackIds: Map<Long, Boolean> = emptyMap()
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val isNewProject = projectId == null || projectId <= 0

    var project by remember { mutableStateOf<MixProject?>(null) }
    val effectiveProjectId = projectId?.takeIf { it > 0 } ?: project?.id
    val tracks by remember(effectiveProjectId) {
        if (effectiveProjectId != null && effectiveProjectId > 0)
            mixDao.getTracksForProject(effectiveProjectId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    var nameField by remember { mutableStateOf(TextFieldValue("")) }
    var initialized by remember { mutableStateOf(false) }

    // Sheet states
    var showAddTrackSheet by remember { mutableStateOf(false) }
    var showPadConfig by remember { mutableStateOf(false) }
    var showPadPackSheet by remember { mutableStateOf(false) }
    var showClickConfig by remember { mutableStateOf(false) }
    var showCustomConfig by remember { mutableStateOf(false) }

    // Custom track config state
    var customConfigTrack by remember { mutableStateOf<MixTrack?>(null) }
    var customName by remember { mutableStateOf("") }
    var customFilePath by remember { mutableStateOf<String?>(null) }
    var customFileName by remember { mutableStateOf<String?>(null) }

    // Pad config state
    var editingPadTrack by remember { mutableStateOf<MixTrack?>(null) }
    var padNote by remember { mutableStateOf("c") }
    var padMode by remember { mutableStateOf("neu") }
    var padPackId by remember { mutableStateOf(-1L) }

    // Resolve available modes/notes based on selected pack
    val selectedPadPack = allPacks.find { it.id == padPackId }
    val isPadDefaultPack = selectedPadPack?.isDefault == true || padPackId == -1L
    val packPads by (if (padPackId > 0) soundPackDao.getPadsForPack(padPackId)
        else kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList())
    val padAvailableModes = if (isPadDefaultPack) listOf("neu", "maj", "min")
        else packPads.map { it.mode }.distinct()
    val padAvailableNotes: (String) -> Set<String> = { mode ->
        if (isPadDefaultPack) NOTE_NAMES_MIX.toSet()
        else packPads.filter { it.mode == mode }.map { it.note }.toSet()
    }

    LaunchedEffect(padPackId, padAvailableModes) {
        if (!isPadDefaultPack && padMode !in padAvailableModes && padAvailableModes.isNotEmpty()) {
            padMode = padAvailableModes.first()
        }
    }
    LaunchedEffect(padPackId, padMode, packPads) {
        if (!isPadDefaultPack) {
            val notes = padAvailableNotes(padMode)
            if (padNote !in notes && notes.isNotEmpty()) {
                padNote = notes.first()
            }
        }
    }

    // Click config state
    var editingClickTrack by remember { mutableStateOf<MixTrack?>(null) }
    var clickBpm by remember { mutableIntStateOf(defaults.bpm) }
    var clickTimeSig by remember { mutableStateOf("4/4") }
    var clickAccents by remember { mutableStateOf(defaults.accents) }

    // Mix-level pad/click routing
    var mixPadVolume by remember { mutableFloatStateOf(defaults.padVolume) }
    var mixPadChannel by remember { mutableStateOf(defaults.padChannel) }
    var mixClickVolume by remember { mutableFloatStateOf(defaults.clickVolume) }
    var mixClickChannel by remember { mutableStateOf(defaults.clickChannel) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Load or create project
    LaunchedEffect(projectId) {
        if (projectId != null && projectId > 0) {
            val existing = mixDao.getById(projectId)
            if (existing != null) {
                project = existing
                nameField = TextFieldValue(existing.name, TextRange(existing.name.length))
                mixPadVolume = existing.padVolume
                mixPadChannel = existing.padChannel
                mixClickVolume = existing.clickVolume
                mixClickChannel = existing.clickChannel
                initialized = true
                callbacks.onProjectReady(existing.id)
            }
        }
        if (!initialized) {
            val allProjects = mixDao.getAllOnce()
            val maxNum = allProjects
                .mapNotNull { it.name.removePrefix("MyMix#").toIntOrNull() }
                .maxOrNull() ?: 0
            val defaultName = "MyMix#${maxNum + 1}"
            val newId = mixDao.insert(MixProject(name = defaultName, padVolume = defaults.padVolume, padChannel = defaults.padChannel, clickVolume = defaults.clickVolume, clickChannel = defaults.clickChannel))
            project = mixDao.getById(newId)
            nameField = TextFieldValue(defaultName, TextRange(0, defaultName.length))
            initialized = true
            callbacks.onProjectReady(newId)
        }
    }

    // Auto-focus name field for new projects
    LaunchedEffect(initialized) {
        if (initialized && isNewProject) {
            focusRequester.requestFocus()
        }
    }

    // Handle incoming audio file
    LaunchedEffect(pendingAudioUri) {
        if (pendingAudioUri != null && project != null) {
            if (showCustomConfig) {
                // Capture file info for config modal
                customFilePath = pendingAudioUri
                customFileName = pendingAudioName
                if (customName.isBlank()) customName = pendingAudioName ?: "Audio"
                callbacks.onAudioConsumed()
            } else {
                // Direct creation fallback (shouldn't happen with new flow)
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
                callbacks.onAudioConsumed()
            }
        }
    }

    val currentProject = project

    val discardAndGoBack: () -> Unit = {
        scope.launch {
            val p = currentProject
            if (p != null && isNewProject) {
                callbacks.onStopAll()
                tracks.filter { it.trackType == "custom" }.forEach {
                    callbacks.onDeleteTrackFile(it.filePath)
                }
                mixDao.deleteTracksForProject(p.id)
                mixDao.delete(p)
            }
            callbacks.onBack()
        }
    }

    val handleBack: () -> Unit = {
        if (isNewProject && tracks.isEmpty()) {
            // New empty project — silently discard
            discardAndGoBack()
        } else {
            // Auto-save name and go back
            scope.launch {
                val p = currentProject
                if (p != null) {
                    val updatedName = nameField.text.trim().ifBlank { p.name }
                    if (updatedName != p.name) {
                        mixDao.update(p.copy(name = updatedName))
                    }
                }
                callbacks.onStopAll()
                callbacks.onBack()
            }
        }
    }

    // Intercept system back (gesture / hardware button)
    BackHandler { handleBack() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 600.dp)
                .background(DarkBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
        // Header with back + save
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { handleBack() },
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

        // ─── Name ───
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
                onValueChange = {
                    if (it.text.length <= 30) {
                        nameField = it
                        scope.launch { currentProject?.let { p -> mixDao.update(p.copy(name = it.text.trim().ifBlank { "My Mix" })) } }
                    }
                },
                placeholder = {
                    Text(
                        "e.g. Worship Set 1",
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
                    focusedBorderColor = LabsPurple,
                    unfocusedBorderColor = PadBorder.copy(alpha = 0.5f),
                    focusedContainerColor = PadIdle,
                    unfocusedContainerColor = PadIdle,
                    cursorColor = LabsPurple
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        }

        // ─── Tracks ───
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRACKS",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )
                Text(
                    text = "${tracks.size}/6",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    color = if (tracks.size >= 6) LabsPurple else TextSecondary.copy(alpha = 0.6f)
                )
            }

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

            // Reorderable track list — new smooth drag-and-drop system
            // localOrder preserves user drag order; syncs data (not order) on every DB emission.
            val localOrder = remember { mutableStateListOf<MixTrack>() }

            // Scope that outlives navigation (rememberCoroutineScope() is cancelled on back nav).
            val dbScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
            DisposableEffect(Unit) { onDispose { dbScope.cancel() } }

            var draggingIndex by remember { mutableStateOf<Int?>(null) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            val itemHeight = remember { mutableStateMapOf<Long, Float>() }

            val spacingPx = with(LocalDensity.current) { 10.dp.toPx() }

            // Re-sync on every DB emission: handles add/remove AND in-place data changes (volume, channel).
            LaunchedEffect(tracks) {
                if (draggingIndex != null) return@LaunchedEffect
                val currentIds = localOrder.map { it.id }.toSet()
                val newIds = tracks.map { it.id }.toSet()
                val idToTrack = tracks.associateBy { it.id }
                if (currentIds != newIds) {
                    val preserved = localOrder.mapNotNull { idToTrack[it.id] }
                    val added = tracks.filter { it.id !in currentIds }
                    localOrder.clear()
                    localOrder.addAll(preserved + added)
                } else {
                    for (i in localOrder.indices) {
                        idToTrack[localOrder[i].id]?.let { localOrder[i] = it }
                    }
                }
            }

            localOrder.forEachIndexed { index, track ->
                key(track.id) {
                val isDragging = draggingIndex == index
                val isLocked = false
                val displayTrack = track
                val trackSubtitle= when (track.trackType) {
                    "pad" -> {
                        val noteLabel = NOTE_LABELS_MIX[track.note] ?: track.note ?: "C"
                        val modeLabel = (track.padMode ?: "neu").uppercase()
                        val packName = if (track.soundPackId == null)
                            allPacks.find { it.isDefault }?.name ?: "Default"
                        else allPacks.find { it.id == track.soundPackId }?.name ?: "Custom"
                        "$noteLabel $modeLabel · $packName"
                    }
                    "click" -> {
                        val bpm = "${track.bpm ?: 120} BPM"
                        val timeSig = track.fileName ?: "4/4"
                        "$bpm · $timeSig"
                    }
                    "custom" -> {
                        val originalName = track.fileName ?: "Audio"
                        "$originalName · Custom"
                    }
                    else -> null
                }

                // Target displacement for neighbor cards — computed only when target slot changes.
                val targetDisplacement by remember {
                    derivedStateOf {
                        val di = draggingIndex
                        val avgH = itemHeight.values.average().toFloat().takeIf { it > 0f }
                        if (isDragging || di == null || avgH == null) return@derivedStateOf 0f
                        val steps = (dragOffsetY / avgH).roundToInt()
                        val ti = (di + steps).coerceIn(0, localOrder.size - 1)
                        val draggedH = itemHeight[localOrder.getOrNull(di)?.id] ?: avgH
                        val shift = draggedH + spacingPx
                        when {
                            di < ti && index in (di + 1)..ti -> -shift
                            di > ti && index in ti until di -> shift
                            else -> 0f
                        }
                    }
                }

                val animatedDisplacement by animateFloatAsState(
                    targetValue = targetDisplacement,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "displacement"
                )
                val animatedScale by animateFloatAsState(
                    targetValue = if (isDragging) 1.04f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "scale"
                )
                val animatedAlpha by animateFloatAsState(
                    targetValue = if (isDragging) 0.93f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "alpha"
                )
                val animatedShadow by animateFloatAsState(
                    targetValue = if (isDragging) 16f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "shadow"
                )

                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else animatedDisplacement
                            scaleX = animatedScale
                            scaleY = animatedScale
                            shadowElevation = animatedShadow
                            alpha = animatedAlpha
                        }
                        .onGloballyPositioned { coords ->
                            itemHeight[track.id] = coords.size.height.toFloat()
                        }
                        .pointerInput(index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val di = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                    val avgH = itemHeight.values.average().toFloat().takeIf { it > 0f } ?: 0f
                                    val minOffset = -(di * (avgH + spacingPx))
                                    val maxOffset = (localOrder.size - 1 - di) * (avgH + spacingPx)
                                    dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(minOffset, maxOffset)
                                },
                                onDragEnd = {
                                    val di = draggingIndex
                                    if (di != null) {
                                        val avgH = itemHeight.values.average()
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
                                        snapshot.forEachIndexed { i, t ->
                                            if (t.sortOrder != i) {
                                                mixDao.updateTrack(t.copy(sortOrder = i))
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
                ) {
                    TrackCard(
                        track = displayTrack,
                        isPlaying = trackPlayingState[track.id] == true,
                        isMuted = mutedTrackIds[track.id] == true,
                        locked = isLocked,
                        subtitle = trackSubtitle,
                        onMuteToggle = {
                            if (mutedTrackIds[track.id] == true) callbacks.onUnmuteTrack(track.id)
                            else callbacks.onMuteTrack(track.id)
                        },
                        onVolumeChange = { vol ->
                            callbacks.onSetTrackVolume(track.id, vol)
                            scope.launch {
                                mixDao.updateTrack(track.copy(volume = vol))
                                val proj = project ?: return@launch
                                when (track.trackType) {
                                    "pad" -> mixDao.update(proj.copy(padVolume = vol))
                                    "click" -> mixDao.update(proj.copy(clickVolume = vol))
                                }
                            }
                        },
                        onChannelChange = { ch ->
                            scope.launch {
                                mixDao.updateTrack(track.copy(channel = ch))
                                val proj = project ?: return@launch
                                when (track.trackType) {
                                    "pad" -> mixDao.update(proj.copy(padChannel = ch))
                                    "click" -> mixDao.update(proj.copy(clickChannel = ch))
                                }
                            }
                        },
                        onConfigure = when (track.trackType) {
                            "pad" -> { {
                                editingPadTrack = track
                                padNote = track.note ?: "c"
                                padMode = track.padMode ?: "neu"
                                padPackId = track.soundPackId ?: -1L
                                showPadConfig = true
                            } }
                            "click" -> { {
                                editingClickTrack = track
                                clickBpm = track.bpm ?: defaults.bpm
                                clickTimeSig = track.fileName ?: "4/4"
                                val accentStr = track.accents
                                clickAccents = if (!accentStr.isNullOrBlank())
                                    accentStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                                else defaults.accents
                                showClickConfig = true
                            } }
                            "custom" -> { {
                                customConfigTrack = track
                                customName = track.label
                                customFilePath = track.filePath
                                customFileName = track.fileName
                                showCustomConfig = true
                            } }
                            else -> null
                        },
                        onDelete = { scope.launch {
                            callbacks.onStopTrack(track.id)
                            if (track.trackType == "custom") callbacks.onDeleteTrackFile(track.filePath)
                            mixDao.deleteTrack(track)
                        }}
                    )
                }
                }
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
                            tint = LabsPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Track",
                            fontSize = 14.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Medium,
                            color = LabsPurple
                        )
                    }
                }
            }
        }

        // ─── Transport Controls ───
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val anyPlaying = trackPlayingState.values.any { it }
            val enginePaused = callbacks.isEnginePaused()
            val hasCustom = tracks.any { it.trackType == "custom" }

            // Seek bar (visible whenever custom tracks exist)
            if (hasCustom) {
                var positionMs by remember { mutableStateOf(0L) }
                var durationMs by remember { mutableStateOf(0L) }
                var isSeeking by remember { mutableStateOf(false) }
                var seekValue by remember { mutableStateOf(0f) }

                LaunchedEffect(anyPlaying, enginePaused) {
                    while (anyPlaying || enginePaused) {
                        if (!isSeeking) {
                            positionMs = callbacks.getPositionMs()
                            durationMs = callbacks.getDurationMs()
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
                        fontSize = 11.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary,
                        modifier = Modifier.width(36.dp)
                    )
                    Slider(
                        value = progress.coerceIn(0f, 1f),
                        onValueChange = { v ->
                            isSeeking = true
                            seekValue = v
                        },
                        onValueChangeFinished = {
                            val targetMs = (seekValue * durationMs).toLong()
                            callbacks.onSeekAll(targetMs)
                            positionMs = targetMs
                            isSeeking = false
                        },
                        modifier = Modifier.weight(1f).height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = LabsPurple,
                            activeTrackColor = LabsPurple,
                            inactiveTrackColor = PadBorder
                        )
                    )
                    Text(
                        text = formatTime(durationMs),
                        fontSize = 11.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            // Transport buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Play
                Button(
                    onClick = {
                        if (enginePaused) callbacks.onResumeAll(tracks)
                        else if (!anyPlaying && tracks.isNotEmpty()) callbacks.onStartAll(tracks)
                    },
                    enabled = !anyPlaying || enginePaused,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LabsPurple,
                        contentColor = Color.White,
                        disabledContainerColor = LabsPurple.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(22.dp))
                }

                // Pause (only useful when custom tracks exist)
                if (tracks.any { it.trackType == "custom" }) {
                    Button(
                        onClick = { callbacks.onPauseAll() },
                        enabled = anyPlaying && !enginePaused,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PadIdle,
                            contentColor = LabsPurple,
                            disabledContainerColor = PadIdle.copy(alpha = 0.5f),
                            disabledContentColor = TextSecondary.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, LabsPurple.copy(alpha = if (anyPlaying && !enginePaused) 0.4f else 0.15f)),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(22.dp))
                    }
                }

                // Stop
                Button(
                    onClick = { callbacks.onStopAll() },
                    enabled = anyPlaying || enginePaused,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PadIdle,
                        contentColor = Color(0xFFFF6B6B),
                        disabledContainerColor = PadIdle.copy(alpha = 0.5f),
                        disabledContentColor = TextSecondary.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, if (anyPlaying || enginePaused) Color(0xFFFF6B6B).copy(alpha = 0.4f) else PadBorder.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(22.dp))
                }
            }
        }

        // ─── Send to Playlist ───
        val isInPlaylist = currentProject?.inPlaylist == true
        Button(
            onClick = {
                scope.launch {
                    val p = currentProject ?: return@launch
                    val updatedName = nameField.text.trim().ifBlank { p.name }
                    if (!p.inPlaylist) {
                        // Send to playlist: save + navigate
                        val updated = p.copy(name = updatedName, inPlaylist = true)
                        mixDao.update(updated)
                        project = updated
                        callbacks.onStopAll()
                        callbacks.onGoToPlaylist()
                    } else {
                        // Remove from playlist: just toggle
                        val updated = p.copy(name = updatedName, inPlaylist = false)
                        mixDao.update(updated)
                        project = updated
                    }
                }
            },
            enabled = tracks.isNotEmpty(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isInPlaylist) PadIdle else LabsPurple.copy(alpha = 0.15f),
                contentColor = if (isInPlaylist) LabsPurple else LabsPurple,
                disabledContainerColor = PadIdle.copy(alpha = 0.5f),
                disabledContentColor = TextSecondary.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, if (isInPlaylist) LabsPurple else LabsPurple.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(
                if (isInPlaylist) Icons.Default.PlaylistRemove else Icons.Default.PlaylistAdd,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isInPlaylist) "REMOVE FROM PLAYLIST" else "SEND TO PLAYLIST",
                fontSize = 14.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = PadIdle,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(10.dp)
            )
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
                        editingPadTrack = null
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
                        editingClickTrack = null
                        clickBpm = defaults.bpm
                        clickTimeSig = "4/4"
                        clickAccents = defaults.accents
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
                        customConfigTrack = null
                        customName = ""
                        customFilePath = null
                        customFileName = null
                        showCustomConfig = true
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ── Pad Config Sheet ───────────────────────────────────────────────
    if (showPadConfig) {
        val padSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPadConfig = false },
            sheetState = padSheetState,
            containerColor = PadIdle,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "PAD TRACK",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )

                // NEU / MAJ / MIN — slide selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(DarkBg, RoundedCornerShape(8.dp))
                        .border(1.dp, PadBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("neu" to "NEU", "maj" to "MAJ", "min" to "MIN").forEach { (mode, label) ->
                        val isSelected = padMode == mode
                        val isAvailable = mode in padAvailableModes
                        Surface(
                            onClick = { if (isAvailable) padMode = mode },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) LabsPurple.copy(alpha = 0.15f) else DarkBg,
                            modifier = Modifier.padding(3.dp).weight(1f).fillMaxHeight()
                                .alpha(if (isAvailable) 1f else 0.3f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    label,
                                    fontSize = 14.sp,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) LabsPurple else TextSecondary
                                )
                            }
                        }
                    }
                }

                // Note grid 4x3
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val notesForMode = padAvailableNotes(padMode)
                    val rows = NOTE_NAMES_MIX.chunked(4)
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { note ->
                                val selected = padNote == note
                                val isAvailable = note in notesForMode
                                Surface(
                                    onClick = { if (isAvailable) padNote = note },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) LabsPurple.copy(alpha = 0.15f) else DarkBg,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) LabsPurple else PadBorder.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.weight(1f).height(54.dp)
                                        .alpha(if (isAvailable) 1f else 0.3f)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = NOTE_LABELS_MIX[note] ?: note,
                                            fontSize = 15.sp,
                                            fontFamily = SpaceGrotesk,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) LabsPurple else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Sound Pack — compact selector
                Text(
                    text = "SOUND PACK",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )
                val selectedPack = allPacks.find { it.id == padPackId }
                    ?: allPacks.find { it.isDefault }
                Surface(
                    onClick = { showPadPackSheet = true },
                    shape = RoundedCornerShape(10.dp),
                    color = DarkBg,
                    border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedPack?.name ?: "Default",
                            fontSize = 15.sp,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Save button
                Button(
                    onClick = {
                        scope.launch {
                            val p = currentProject ?: return@launch
                            val label = "PAD"
                            val selectedPk = if (padPackId == -1L) allPacks.find { it.isDefault } else allPacks.find { it.id == padPackId }
                            val isDefaultPack = selectedPk?.isDefault == true
                            var resolvedFilePath: String? = null
                            var resolvedPackId: Long? = null
                            if (!isDefaultPack && selectedPk != null) {
                                val soundPad = soundPackDao.getPad(selectedPk.id, padNote, padMode)
                                resolvedFilePath = soundPad?.filePath
                                resolvedPackId = selectedPk.id
                            }
                            if (editingPadTrack != null) {
                                val existing = editingPadTrack!!
                                val wasPlaying = trackPlayingState[existing.id] == true
                                callbacks.onStopTrack(existing.id)
                                mixDao.updateTrack(
                                    existing.copy(
                                        label = label,
                                        note = padNote,
                                        padMode = padMode,
                                        soundPackId = resolvedPackId,
                                        filePath = resolvedFilePath
                                    )
                                )
                                if (wasPlaying) snackbarHostState.showSnackbar("Track updated. Restart playback to apply.")
                            } else {
                                val count = mixDao.getTrackCount(p.id)
                                mixDao.insertTrack(
                                    MixTrack(
                                        projectId = p.id,
                                        trackType = "pad",
                                        label = label,
                                        volume = mixPadVolume,
                                        channel = mixPadChannel,
                                        sortOrder = count,
                                        note = padNote,
                                        padMode = padMode,
                                        soundPackId = resolvedPackId,
                                        filePath = resolvedFilePath
                                    )
                                )
                            }
                        }
                        showPadConfig = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LabsPurple,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(
                        text = if (editingPadTrack != null) "SAVE CHANGES" else "ADD PAD TRACK",
                        fontSize = 14.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ── Pad Pack Selection Sheet ─────────────────────────────────────────
    if (showPadPackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPadPackSheet = false },
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
                    val isSelected = padPackId == pack.id || (padPackId == -1L && pack.isDefault)
                    Surface(
                        onClick = {
                            padPackId = pack.id
                            showPadPackSheet = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) LabsPurple.copy(alpha = 0.15f) else PadIdle,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) LabsPurple else PadBorder.copy(alpha = 0.3f)
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
                                color = if (isSelected) LabsPurple else TextSecondary
                            )
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    fontSize = 14.sp,
                                    color = LabsPurple,
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

    // ── Click Config Sheet ─────────────────────────────────────────────
    if (showClickConfig) {
        val clickSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showClickConfig = false },
            sheetState = clickSheetState,
            containerColor = PadIdle,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "CLICK TRACK",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
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
                        color = DarkBg,
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
                            color = LabsPurple
                        )
                        Text(
                            text = "BPM",
                            fontSize = 11.sp,
                            fontFamily = SpaceGrotesk,
                            color = LabsPurple.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Surface(
                        onClick = { if (clickBpm < 240) clickBpm++ },
                        shape = RoundedCornerShape(8.dp),
                        color = DarkBg,
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
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TIME_SIGS.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { sig ->
                                val sel = clickTimeSig == sig
                                Surface(
                                    onClick = {
                                        clickTimeSig = sig
                                        val beats = sig.substringBefore("/").toInt()
                                        clickAccents = List(beats) { if (it == 0) 1 else 0 }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (sel) LabsPurple.copy(alpha = 0.15f) else DarkBg,
                                    border = BorderStroke(
                                        1.dp,
                                        if (sel) LabsPurple else PadBorder.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = sig,
                                            fontSize = 14.sp,
                                            fontFamily = SpaceGrotesk,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sel) LabsPurple else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Accent dots
                Text(
                    text = "BEATS",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )
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
                                1 -> LabsPurple.copy(alpha = 0.2f)
                                2 -> Color.Transparent
                                else -> Color.Transparent
                            },
                            border = BorderStroke(
                                width = if (state == 1) 2.dp else 1.dp,
                                color = when (state) {
                                    1 -> LabsPurple
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
                                        color = if (state == 1) LabsPurple else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Save button
                Button(
                    onClick = {
                        scope.launch {
                            val p = currentProject ?: return@launch
                            val label = "CLICK"
                            val clickSubLabel = clickTimeSig
                            if (editingClickTrack != null) {
                                val existing = editingClickTrack!!
                                val wasPlaying = trackPlayingState[existing.id] == true
                                callbacks.onStopTrack(existing.id)
                                mixDao.updateTrack(
                                    existing.copy(
                                        label = label,
                                        bpm = clickBpm,
                                        accents = clickAccents.joinToString(","),
                                        fileName = clickSubLabel
                                    )
                                )
                                if (wasPlaying) snackbarHostState.showSnackbar("Track updated. Restart playback to apply.")
                            } else {
                                val count = mixDao.getTrackCount(p.id)
                                mixDao.insertTrack(
                                    MixTrack(
                                        projectId = p.id,
                                        trackType = "click",
                                        label = label,
                                        volume = mixClickVolume,
                                        channel = mixClickChannel,
                                        sortOrder = count,
                                        bpm = clickBpm,
                                        accents = clickAccents.joinToString(","),
                                        fileName = clickSubLabel
                                    )
                                )
                            }
                        }
                        showClickConfig = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LabsPurple,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(
                        text = if (editingClickTrack != null) "SAVE CHANGES" else "ADD CLICK TRACK",
                        fontSize = 14.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ── Custom Audio Config Sheet ─────────────────────────────────────
    if (showCustomConfig) {
        val isEditing = customConfigTrack != null
        val customSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCustomConfig = false },
            sheetState = customSheetState,
            containerColor = PadIdle,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "AUDIO TRACK",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )

                // ── Name ──
                Text(
                    text = "NAME",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    placeholder = {
                        Text(
                            text = customFileName ?: "Track name",
                            fontFamily = SpaceGrotesk,
                            color = TextSecondary.copy(alpha = 0.3f)
                        )
                    },
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextPrimary
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LabsPurple,
                        unfocusedBorderColor = PadBorder.copy(alpha = 0.3f),
                        cursorColor = LabsPurple
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // ── File ──
                Text(
                    text = "FILE",
                    fontSize = 12.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )
                Surface(
                    onClick = { callbacks.onPickAudioFile() },
                    shape = RoundedCornerShape(10.dp),
                    color = DarkBg,
                    border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (customFilePath != null) Icons.Default.AudioFile else Icons.Default.FileOpen,
                            contentDescription = null,
                            tint = if (customFilePath != null) LabsPurple else TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = customFileName ?: "Choose audio file...",
                            fontSize = 14.sp,
                            fontFamily = SpaceGrotesk,
                            color = if (customFilePath != null) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (customFilePath != null) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = LabsPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Save ──
                Button(
                    onClick = {
                        val p = project ?: return@Button
                        val filePath = customFilePath ?: return@Button
                        val finalName = customName.trim().ifBlank { customFileName ?: "Audio" }
                        scope.launch {
                            if (isEditing) {
                                val existing = customConfigTrack!!
                                val wasPlaying = trackPlayingState[existing.id] == true
                                callbacks.onStopTrack(existing.id)
                                val updatedTrack = if (filePath != existing.filePath) {
                                    callbacks.onDeleteTrackFile(existing.filePath)
                                    existing.copy(
                                        label = finalName,
                                        filePath = filePath,
                                        fileName = customFileName
                                    )
                                } else {
                                    existing.copy(label = finalName)
                                }
                                mixDao.updateTrack(updatedTrack)
                                if (wasPlaying) snackbarHostState.showSnackbar("Track updated. Restart playback to apply.")
                            } else {
                                val count = mixDao.getTrackCount(p.id)
                                if (count < 6) {
                                    mixDao.insertTrack(
                                        MixTrack(
                                            projectId = p.id,
                                            trackType = "custom",
                                            label = finalName,
                                            volume = 0.5f,
                                            channel = "mono",
                                            sortOrder = count,
                                            filePath = filePath,
                                            fileName = customFileName
                                        )
                                    )
                                }
                            }
                            showCustomConfig = false
                        }
                    },
                    enabled = customFilePath != null,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LabsPurple,
                        contentColor = Color.White,
                        disabledContainerColor = LabsPurple.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(
                        text = if (isEditing) "SAVE CHANGES" else "ADD AUDIO TRACK",
                        fontSize = 14.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ── Discard Confirmation Sheet ─────────────────────────────────────
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackCard(
    track: MixTrack,
    isPlaying: Boolean,
    isMuted: Boolean = false,
    locked: Boolean = false,
    subtitle: String? = null,
    onMuteToggle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onChannelChange: (String) -> Unit,
    onConfigure: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    var localVolume by remember(track.id, track.volume) { mutableStateOf(track.volume) }
    var localChannel by remember(track.id, track.channel) { mutableStateOf(track.channel ?: "mono") }
    val trackColor = when (track.trackType) {
        "pad" -> LedAmber
        "click" -> ClickTeal
        else -> LabsPurple
    }
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val hasEditAction = onConfigure != null
    val revealWidthDp = if (hasEditAction) 120.dp else 60.dp
    val revealWidthPx = with(density) { revealWidthDp.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isMuted) TextSecondary.copy(alpha = 0.15f)
                else if (isPlaying) trackColor.copy(alpha = 0.4f)
                else PadBorder.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
    ) {
        // Background icons (revealed on swipe)
        if (offsetX.value < -1f) {
            Row(
                modifier = Modifier.matchParentSize().background(DarkBg),
                horizontalArrangement = Arrangement.End
            ) {
                if (hasEditAction) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(60.dp)
                            .background(PadIdle)
                            .clickable {
                                scope.launch { offsetX.animateTo(0f, tween(200)) }
                                onConfigure?.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = trackColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(60.dp)
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
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Foreground card (swipeable)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(PadIdle)
                .pointerInput(Unit) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .then(if (isMuted) Modifier.alpha(0.45f) else Modifier)
        ) {
            // Row 1: type icon, label, play
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (track.trackType) {
                    "pad" -> Icons.Default.Piano
                    "click" -> Icons.Default.Timer
                    else -> Icons.Default.AudioFile
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(trackColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                ) {
                    Icon(icon, contentDescription = null, tint = trackColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))

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
                        text = subtitle ?: track.trackType.replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        fontFamily = SpaceGrotesk,
                        color = TextSecondary.copy(alpha = 0.5f)
                    )
                }

                IconButton(onClick = onMuteToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) TextSecondary.copy(alpha = 0.4f) else trackColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: volume slider + channel
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = localVolume,
                    onValueChange = { if (!locked) localVolume = it },
                    onValueChangeFinished = { if (!locked) onVolumeChange(localVolume) },
                    enabled = !locked,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = if (locked) trackColor.copy(alpha = 0.4f) else trackColor,
                        activeTrackColor = if (locked) trackColor.copy(alpha = 0.4f) else trackColor,
                        inactiveTrackColor = PadBorder,
                        disabledThumbColor = trackColor.copy(alpha = 0.4f),
                        disabledActiveTrackColor = trackColor.copy(alpha = 0.4f),
                        disabledInactiveTrackColor = PadBorder
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("left" to "L", "mono" to "M", "right" to "R").forEach { (ch, label) ->
                        val sel = localChannel == ch
                        Surface(
                            onClick = {
                                if (!locked) {
                                    localChannel = ch
                                    onChannelChange(ch)
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            color = if (sel) trackColor.copy(alpha = if (locked) 0.1f else 0.2f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (sel) trackColor.copy(alpha = if (locked) 0.3f else 1f) else PadBorder.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) trackColor.copy(alpha = if (locked) 0.4f else 1f) else TextSecondary.copy(alpha = if (locked) 0.3f else 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation bottom sheet
    if (showDeleteConfirm) {
        ModalBottomSheet(
            onDismissRequest = { showDeleteConfirm = false },
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Remove \"${track.label}\"?",
                    fontSize = 16.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = "This track will be removed from the mix.",
                    fontSize = 13.sp,
                    fontFamily = SpaceGrotesk,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = { showDeleteConfirm = false },
                        shape = RoundedCornerShape(10.dp),
                        color = PadIdle,
                        border = BorderStroke(1.dp, PadBorder.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("Cancel", fontSize = 14.sp, fontFamily = SpaceGrotesk, color = TextSecondary)
                        }
                    }
                    Surface(
                        onClick = {
                            showDeleteConfirm = false
                            onDelete()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("Remove", fontSize = 14.sp, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
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
            Icon(icon, contentDescription = null, tint = LabsPurple, modifier = Modifier.size(24.dp))
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
