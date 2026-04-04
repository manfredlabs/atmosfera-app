package com.example.atmosfera

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.atmosfera.data.AppDatabase
import com.example.atmosfera.data.Song
import com.example.atmosfera.screens.AddSongScreen
import com.example.atmosfera.screens.PlaylistScreen
import com.example.atmosfera.screens.SettingsScreen
import com.example.atmosfera.ui.theme.*

data class Note(val name: String, val label: String, val neuResName: String, val majResName: String, val minResName: String) {
    fun resNameForMode(mode: String): String = when (mode) {
        "neu" -> neuResName
        "min" -> minResName
        else -> majResName
    }
}

enum class ClickChannel(val label: String) { LEFT("L"), MONO("M"), RIGHT("R") }
enum class PadChannel(val label: String) { LEFT("L"), MONO("M"), RIGHT("R") }

class MainActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var padTargetVolume = 0.5f
    private val fadeInMs = 2000L
    private val fadeOutMs = 1500L
    private val fadeSteps = 30

    private lateinit var soundPool: SoundPool
    private var clickSoundId: Int = 0
    private var accentSoundId: Int = 0
    private var isClickRunning = false
    private var clickRunnable: Runnable? = null
    private var currentClickVolume = 0.5f
    private var currentClickChannel = ClickChannel.MONO
    private var currentAccents = listOf(true, false, false, false)

    // Beat visual state
    private val _beatOn = mutableStateOf(false)
    private val _currentBeat = mutableIntStateOf(0)

    // Pad stereo panning
    private var channelMixer: ChannelMixingAudioProcessor? = null

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()
        clickSoundId = soundPool.load(this, R.raw.click, 1)
        accentSoundId = soundPool.load(this, R.raw.click_accent, 1)

        val prefs = getSharedPreferences("atmosfera_settings", MODE_PRIVATE)
        val savedPadVolume = prefs.getFloat("padVolume", 0.5f)
        val savedClickVolume = prefs.getFloat("clickVolume", 0.5f)
        val savedPadChannel = PadChannel.entries.find { it.name == prefs.getString("padChannel", "MONO") } ?: PadChannel.MONO
        val savedClickChannel = ClickChannel.entries.find { it.name == prefs.getString("clickChannel", "MONO") } ?: ClickChannel.MONO
        padTargetVolume = savedPadVolume

        val notes = listOf(
            Note("c", "C", "pad_c_neu", "pad_c_maj", "pad_c_min"),
            Note("cs", "C#", "pad_cs_neu", "pad_cs_maj", "pad_cs_min"),
            Note("d", "D", "pad_d_neu", "pad_d_maj", "pad_d_min"),
            Note("ds", "D#", "pad_ds_neu", "pad_ds_maj", "pad_ds_min"),
            Note("e", "E", "pad_e_neu", "pad_e_maj", "pad_e_min"),
            Note("f", "F", "pad_f_neu", "pad_f_maj", "pad_f_min"),
            Note("fs", "F#", "pad_fs_neu", "pad_fs_maj", "pad_fs_min"),
            Note("g", "G", "pad_g_neu", "pad_g_maj", "pad_g_min"),
            Note("gs", "G#", "pad_gs_neu", "pad_gs_maj", "pad_gs_min"),
            Note("a", "A", "pad_a_neu", "pad_a_maj", "pad_a_min"),
            Note("as", "A#", "pad_as_neu", "pad_as_maj", "pad_as_min"),
            Note("b", "B", "pad_b_neu", "pad_b_maj", "pad_b_min"),
        )

        setContent {
            AtmosferaTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: "home"
                val showBottomBar = currentRoute in listOf("home", "playlist", "settings")
                val db = remember { AppDatabase.getInstance(this@MainActivity) }
                val songDao = remember { db.songDao() }

                var playingNote by remember { mutableStateOf<String?>(null) }
                var playingSongId by remember { mutableStateOf<Long?>(null) }

                // Live screen state (persisted)
                var liveBpm by remember { mutableIntStateOf(prefs.getInt("liveBpm", 90)) }
                var liveClickEnabled by remember { mutableStateOf(false) }
                var liveAccents by remember { mutableStateOf(
                    prefs.getString("liveAccents", "1,0,0,0")!!.split(",").map { it == "1" }
                ) }
                var livePadMode by remember { mutableStateOf(prefs.getString("livePadMode", "maj")!!) }

                // Active playback state (what's actually playing right now)
                var bpm by remember { mutableIntStateOf(liveBpm) }
                var clickEnabled by remember { mutableStateOf(false) }
                var accents by remember { mutableStateOf(liveAccents) }
                var padMode by remember { mutableStateOf(livePadMode) }
                var clickChannel by remember { mutableStateOf(savedClickChannel) }
                var clickVolume by remember { mutableFloatStateOf(savedClickVolume) }
                var padChannel by remember { mutableStateOf(savedPadChannel) }
                var padVolume by remember { mutableFloatStateOf(savedPadVolume) }
                var playlistLocked by remember { mutableStateOf(false) }

                // Restore live values when navigating to home
                LaunchedEffect(currentRoute) {
                    if (currentRoute == "home") {
                        bpm = liveBpm
                        accents = liveAccents
                        padMode = livePadMode
                        currentAccents = liveAccents
                    }
                }

                Scaffold(
                    containerColor = DarkBg,
                    bottomBar = {
                        if (showBottomBar) {
                        androidx.compose.animation.Crossfade(
                            targetState = playlistLocked,
                            animationSpec = androidx.compose.animation.core.tween(300)
                        ) { locked ->
                            NavigationBar(
                                containerColor = PadIdle,
                                contentColor = TextSecondary,
                                tonalElevation = 0.dp
                            ) {
                                val tabs = if (locked) {
                                    listOf(Triple("playlist", "Playlist", Icons.AutoMirrored.Filled.QueueMusic))
                                } else {
                                    listOf(
                                        Triple("settings", "Settings", Icons.Default.Settings),
                                        Triple("home", "Live", Icons.Default.MusicNote),
                                        Triple("playlist", "Playlist", Icons.AutoMirrored.Filled.QueueMusic),
                                    )
                                }
                                tabs.forEach { (route, label, icon) ->
                                    NavigationBarItem(
                                        selected = currentRoute == route,
                                        onClick = {
                                            if (currentRoute != route) {
                                                navController.navigate(route) {
                                                    popUpTo("home") { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = {
                                            Text(
                                                label,
                                                fontFamily = SpaceGrotesk,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = TextPrimary,
                                            selectedTextColor = TextPrimary,
                                            unselectedIconColor = TextSecondary.copy(alpha = 0.5f),
                                            unselectedTextColor = TextSecondary.copy(alpha = 0.5f),
                                            indicatorColor = PadActive
                                        )
                                    )
                                }
                            }
                        }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // NEU/MAJ/MIN toggle (centered)
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .height(32.dp)
                            .background(PadIdle, RoundedCornerShape(8.dp))
                            .border(1.dp, PadBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("neu" to "NEU", "maj" to "MAJ", "min" to "MIN").forEach { (mode, label) ->
                            val isSelected = padMode == mode
                            Surface(
                                onClick = {
                                    if (padMode != mode) {
                                        padMode = mode
                                        livePadMode = mode
                                        prefs.edit().putString("livePadMode", mode).apply()
                                        playingNote?.let { noteName ->
                                            val note = notes.find { it.label == noteName }
                                            if (note != null) {
                                                startPad(note.resNameForMode(mode), padChannel)
                                            }
                                        }
                                    }
                                },
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
                                            .background(bgColor, RoundedCornerShape(12.dp))
                                            .border(
                                                width = 1.dp,
                                                color = if (isActive) LedAmber.copy(alpha = 0.5f) else PadBorder.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .pointerInput(note.label, playingNote, padMode) {
                                                detectTapGestures(
                                                    onTap = {
                                                        if (isActive) {
                                                            stopPad { }
                                                            playingNote = null
                                                        } else {
                                                            startPad(note.resNameForMode(padMode), padChannel)
                                                            playingNote = note.label
                                                        }
                                                    },
                                                    onLongPress = {
                                                        stopPad { }
                                                        playingNote = null
                                                        playingSongId = null
                                                        stopClick()
                                                        clickEnabled = false
                                                    }
                                                )
                                            }
                                    ) {
                                        val displayLabel = when (padMode) {
                                            "min" -> "${note.label}m"
                                            "neu" -> note.label
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

                    // ─── CLICK controls (vertical layout) ───
                    val beatOn by _beatOn
                    val currentBeat by _currentBeat

                    // CLICK ON/OFF toggle (full width)
                    Surface(
                        onClick = {
                            clickEnabled = !clickEnabled
                            if (clickEnabled) {
                                startClick(bpm, clickChannel, clickVolume, accents)
                            } else {
                                stopClick()
                            }
                        },
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
                                detectTapGestures(
                                    onTap = {
                                        if (bpm > 30) {
                                            bpm--
                                            liveBpm = bpm
                                            prefs.edit().putInt("liveBpm", bpm).apply()
                                            if (clickEnabled) restartClick(bpm, clickChannel, clickVolume, accents)
                                        }
                                    }
                                )
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
                                detectTapGestures(
                                    onTap = {
                                        if (bpm < 240) {
                                            bpm++
                                            liveBpm = bpm
                                            prefs.edit().putInt("liveBpm", bpm).apply()
                                            if (clickEnabled) restartClick(bpm, clickChannel, clickVolume, accents)
                                        }
                                    }
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
                                        detectTapGestures(
                                            onTap = {
                                                accents = accents.toMutableList().also {
                                                    it[index] = !it[index]
                                                }
                                                liveAccents = accents
                                                currentAccents = accents
                                                prefs.edit().putString("liveAccents", accents.joinToString(",") { if (it) "1" else "0" }).apply()
                                            }
                                        )
                                    }
                            )
                        }
                    }

                    // Spacer final pra equilibrar espaçamento com o menu
                    Spacer(modifier = Modifier.height(0.dp))
                }
                        }

                        composable("playlist") {
                            PlaylistScreen(
                                songDao = songDao,
                                playingSongId = playingSongId,
                                padVolume = padVolume,
                                clickVolume = clickVolume,
                                onPlaySong = { song ->
                                    val note = notes.find { it.name == song.note }
                                    stopClick()
                                    
                                    padMode = song.padMode
                                    bpm = song.bpm
                                    accents = song.accentList()
                                    currentAccents = accents
                                    
                                    if (note != null) {
                                        val resName = note.resNameForMode(song.padMode)
                                        startPad(resName, padChannel)
                                        playingNote = note.label
                                    }
                                    
                                    playingSongId = song.id
                                    
                                    if (song.clickEnabled) {
                                        clickEnabled = true
                                        startClick(bpm, clickChannel, clickVolume, accents)
                                    } else {
                                        clickEnabled = false
                                    }
                                },
                                onPauseSong = {
                                    stopPad { }
                                    stopClick()
                                    playingNote = null
                                    playingSongId = null
                                    clickEnabled = false
                                },
                                onPadVolumeChange = { vol ->
                                    padVolume = vol
                                    padTargetVolume = vol
                                    player?.volume = vol
                                    prefs.edit().putFloat("padVolume", vol).apply()
                                },
                                onClickVolumeChange = { vol ->
                                    clickVolume = vol
                                    currentClickVolume = vol
                                    prefs.edit().putFloat("clickVolume", vol).apply()
                                },
                                onNavigateToAddSong = {
                                    navController.navigate("add_song")
                                },
                                onNavigateToEditSong = { songId ->
                                    navController.navigate("edit_song/$songId")
                                },
                                isLocked = playlistLocked,
                                onToggleLock = {
                                    if (playlistLocked) {
                                        // Unlocking — auto-pause
                                        stopPad { }
                                        stopClick()
                                        playingNote = null
                                        playingSongId = null
                                        clickEnabled = false
                                    }
                                    playlistLocked = !playlistLocked
                                }
                            )
                        }

                        composable("add_song") {
                            AddSongScreen(
                                songDao = songDao,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("edit_song/{songId}") { backStackEntry ->
                            val songId = backStackEntry.arguments?.getString("songId")?.toLongOrNull()
                            AddSongScreen(
                                songDao = songDao,
                                onBack = { navController.popBackStack() },
                                editSongId = songId
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                padVolume = padVolume,
                                clickVolume = clickVolume,
                                padChannel = padChannel,
                                clickChannel = clickChannel,
                                playingNote = playingNote,
                                clickEnabled = clickEnabled,
                                onPadVolumeChange = {
                                    padVolume = it
                                    padTargetVolume = it
                                    player?.volume = it
                                    prefs.edit().putFloat("padVolume", it).apply()
                                },
                                onClickVolumeChange = {
                                    clickVolume = it
                                    currentClickVolume = it
                                    prefs.edit().putFloat("clickVolume", it).apply()
                                },
                                onPadChannelChange = { channel ->
                                    padChannel = channel
                                    updatePadPanning(channel)
                                    val linkedClick = when (channel) {
                                        PadChannel.LEFT -> ClickChannel.RIGHT
                                        PadChannel.RIGHT -> ClickChannel.LEFT
                                        PadChannel.MONO -> ClickChannel.MONO
                                    }
                                    clickChannel = linkedClick
                                    currentClickChannel = linkedClick
                                    prefs.edit().putString("padChannel", channel.name).putString("clickChannel", linkedClick.name).apply()
                                },
                                onClickChannelChange = { channel ->
                                    clickChannel = channel
                                    currentClickChannel = channel
                                    val linkedPad = when (channel) {
                                        ClickChannel.LEFT -> PadChannel.RIGHT
                                        ClickChannel.RIGHT -> PadChannel.LEFT
                                        ClickChannel.MONO -> PadChannel.MONO
                                    }
                                    padChannel = linkedPad
                                    updatePadPanning(linkedPad)
                                    prefs.edit().putString("clickChannel", channel.name).putString("padChannel", linkedPad.name).apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startPad(rawResName: String, padCh: PadChannel) {
        // Fade out o player anterior enquanto o novo entra
        val old = player
        if (old != null) {
            val stepDelay = fadeOutMs / fadeSteps
            for (i in 1..fadeSteps) {
                handler.postDelayed({
                    val progress = 1f - (i.toFloat() / fadeSteps)
                    old.volume = (progress * progress) * padTargetVolume
                }, i * stepDelay)
            }
            handler.postDelayed({ old.release() }, fadeOutMs + 50)
        }

        val resId = resources.getIdentifier(rawResName, "raw", packageName)
        val uri = "android.resource://$packageName/$resId"

        // Cria audio processor pra controle de pan
        val mixer = ChannelMixingAudioProcessor()
        applyPadPanning(mixer, padCh)
        channelMixer = mixer

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(mixer))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        val newPlayer = ExoPlayer.Builder(this, renderersFactory).build().apply {
            val exoAudioAttrs = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            setAudioAttributes(exoAudioAttrs, false)
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            play()
        }
        player = newPlayer

        // Fade in o novo
        val stepDelay = fadeInMs / fadeSteps
        for (i in 1..fadeSteps) {
            handler.postDelayed({
                val progress = i.toFloat() / fadeSteps
                newPlayer.volume = (progress * progress) * padTargetVolume
            }, i * stepDelay)
        }
    }

    private fun stopPad(onComplete: (() -> Unit)? = null) {
        val current = player ?: run {
            onComplete?.invoke()
            return
        }

        // Fade out com curva quadrática (saída suave)
        val stepDelay = fadeOutMs / fadeSteps
        for (i in 1..fadeSteps) {
            handler.postDelayed({
                val progress = 1f - (i.toFloat() / fadeSteps)
                current.volume = (progress * progress) * padTargetVolume
            }, i * stepDelay)
        }

        handler.postDelayed({
            current.release()
            if (player == current) player = null
            onComplete?.invoke()
        }, fadeOutMs + 50)
    }

    private fun stopPadImmediate() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        // Re-start click if it was running (handler was cleared)
        // Click will be restarted by the caller if needed
    }

    private fun startClick(bpm: Int, channel: ClickChannel, volume: Float, accents: List<Boolean>) {
        isClickRunning = true
        currentClickVolume = volume
        currentClickChannel = channel
        currentAccents = accents
        val interval = (60000L / bpm)
        val beatFlashMs = 80L
        var beatIndex = 0

        clickRunnable = object : Runnable {
            override fun run() {
                if (!isClickRunning) return
                val (leftVol, rightVol) = when (currentClickChannel) {
                    ClickChannel.LEFT -> currentClickVolume to 0f
                    ClickChannel.RIGHT -> 0f to currentClickVolume
                    ClickChannel.MONO -> currentClickVolume to currentClickVolume
                }
                val isAccent = currentAccents[beatIndex]
                val soundId = if (isAccent) accentSoundId else clickSoundId
                soundPool.play(soundId, leftVol, rightVol, 1, 0, 1f)
                _currentBeat.intValue = beatIndex
                _beatOn.value = true
                handler.postDelayed({ _beatOn.value = false }, beatFlashMs)
                beatIndex = (beatIndex + 1) % currentAccents.size
                handler.postDelayed(this, interval)
            }
        }
        handler.post(clickRunnable!!)
    }

    private fun stopClick() {
        isClickRunning = false
        _beatOn.value = false
        _currentBeat.intValue = 0
        clickRunnable?.let { handler.removeCallbacks(it) }
        clickRunnable = null
    }

    private fun restartClick(bpm: Int, channel: ClickChannel, volume: Float, accents: List<Boolean>) {
        stopClick()
        startClick(bpm, channel, volume, accents)
    }

    private fun applyPadPanning(mixer: ChannelMixingAudioProcessor, channel: PadChannel) {
        val (leftGain, rightGain) = when (channel) {
            PadChannel.LEFT -> 1f to 0f
            PadChannel.MONO -> 1f to 1f
            PadChannel.RIGHT -> 0f to 1f
        }
        mixer.putChannelMixingMatrix(
            ChannelMixingMatrix(1, 2, floatArrayOf(leftGain, rightGain))
        )
        mixer.putChannelMixingMatrix(
            ChannelMixingMatrix(2, 2, floatArrayOf(leftGain, 0f, 0f, rightGain))
        )
    }

    private fun updatePadPanning(channel: PadChannel) {
        channelMixer?.let { applyPadPanning(it, channel) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClick()
        stopPadImmediate()
        soundPool.release()
    }
}
