package com.example.atmosfera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.atmosfera.audio.AudioEngine
import com.example.atmosfera.data.AppDatabase
import com.example.atmosfera.model.ALL_NOTES
import com.example.atmosfera.model.ClickChannel
import com.example.atmosfera.model.PadChannel
import com.example.atmosfera.screens.AddSongScreen
import com.example.atmosfera.screens.HomeScreen
import com.example.atmosfera.screens.PlaylistScreen
import com.example.atmosfera.screens.SettingsScreen
import com.example.atmosfera.ui.theme.*

class MainActivity : ComponentActivity() {

    private lateinit var audio: AudioEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        audio = AudioEngine(this)
        audio.init(R.raw.click, R.raw.click_accent)

        val prefs = getSharedPreferences("atmosfera_settings", MODE_PRIVATE)
        val savedPadVolume = prefs.getFloat("padVolume", 0.5f)
        val savedClickVolume = prefs.getFloat("clickVolume", 0.5f)
        val savedPadChannel = PadChannel.entries.find { it.name == prefs.getString("padChannel", "MONO") } ?: PadChannel.MONO
        val savedClickChannel = ClickChannel.entries.find { it.name == prefs.getString("clickChannel", "MONO") } ?: ClickChannel.MONO
        audio.padTargetVolume = savedPadVolume

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
                var liveAccents by remember { mutableStateOf(
                    prefs.getString("liveAccents", "1,0,0,0")!!.split(",").map { it == "1" }
                ) }
                var livePadMode by remember { mutableStateOf(prefs.getString("livePadMode", "maj")!!) }

                // Active playback state
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
                        audio.currentAccents = liveAccents
                    }
                }

                Scaffold(
                    containerColor = DarkBg,
                    bottomBar = {
                        if (showBottomBar) {
                            Crossfade(
                                targetState = playlistLocked,
                                animationSpec = tween(300)
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
                            val beatOn by audio.beatOn
                            val currentBeat by audio.currentBeat

                            HomeScreen(
                                notes = ALL_NOTES,
                                playingNote = playingNote,
                                padMode = padMode,
                                clickEnabled = clickEnabled,
                                bpm = bpm,
                                accents = accents,
                                beatOn = beatOn,
                                currentBeat = currentBeat,
                                onPadTap = { note ->
                                    if (playingNote == note.label) {
                                        audio.stopPad { }
                                        playingNote = null
                                    } else {
                                        audio.startPad(note.resNameForMode(padMode), padChannel)
                                        playingNote = note.label
                                    }
                                },
                                onPadLongPress = {
                                    audio.stopPad { }
                                    playingNote = null
                                    playingSongId = null
                                    audio.stopClick()
                                    clickEnabled = false
                                },
                                onPadModeChange = { mode ->
                                    padMode = mode
                                    livePadMode = mode
                                    prefs.edit().putString("livePadMode", mode).apply()
                                    playingNote?.let { noteName ->
                                        val note = ALL_NOTES.find { it.label == noteName }
                                        if (note != null) {
                                            audio.startPad(note.resNameForMode(mode), padChannel)
                                        }
                                    }
                                },
                                onClickToggle = {
                                    clickEnabled = !clickEnabled
                                    if (clickEnabled) {
                                        audio.startClick(bpm, clickChannel, clickVolume, accents)
                                    } else {
                                        audio.stopClick()
                                    }
                                },
                                onBpmChange = { delta ->
                                    val newBpm = (bpm + delta).coerceIn(30, 240)
                                    if (newBpm != bpm) {
                                        bpm = newBpm
                                        liveBpm = bpm
                                        prefs.edit().putInt("liveBpm", bpm).apply()
                                        if (clickEnabled) audio.restartClick(bpm, clickChannel, clickVolume, accents)
                                    }
                                },
                                onAccentToggle = { index ->
                                    accents = accents.toMutableList().also { it[index] = !it[index] }
                                    liveAccents = accents
                                    audio.currentAccents = accents
                                    prefs.edit().putString("liveAccents", accents.joinToString(",") { if (it) "1" else "0" }).apply()
                                }
                            )
                        }

                        composable("playlist") {
                            PlaylistScreen(
                                songDao = songDao,
                                playingSongId = playingSongId,
                                padVolume = padVolume,
                                clickVolume = clickVolume,
                                onPlaySong = { song ->
                                    val note = ALL_NOTES.find { it.name == song.note }
                                    audio.stopClick()

                                    padMode = song.padMode
                                    bpm = song.bpm
                                    accents = song.accentList()
                                    audio.currentAccents = accents

                                    if (note != null) {
                                        audio.startPad(note.resNameForMode(song.padMode), padChannel)
                                        playingNote = note.label
                                    }

                                    playingSongId = song.id

                                    if (song.clickEnabled) {
                                        clickEnabled = true
                                        audio.startClick(bpm, clickChannel, clickVolume, accents)
                                    } else {
                                        clickEnabled = false
                                    }
                                },
                                onPauseSong = {
                                    audio.stopPad { }
                                    audio.stopClick()
                                    playingNote = null
                                    playingSongId = null
                                    clickEnabled = false
                                },
                                onPadVolumeChange = { vol ->
                                    padVolume = vol
                                    audio.padTargetVolume = vol
                                    audio.padVolume = vol
                                    prefs.edit().putFloat("padVolume", vol).apply()
                                },
                                onClickVolumeChange = { vol ->
                                    clickVolume = vol
                                    audio.currentClickVolume = vol
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
                                        audio.stopPad { }
                                        audio.stopClick()
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
                                    audio.padTargetVolume = it
                                    audio.padVolume = it
                                    prefs.edit().putFloat("padVolume", it).apply()
                                },
                                onClickVolumeChange = {
                                    clickVolume = it
                                    audio.currentClickVolume = it
                                    prefs.edit().putFloat("clickVolume", it).apply()
                                },
                                onPadChannelChange = { channel ->
                                    padChannel = channel
                                    audio.updatePadPanning(channel)
                                    val linkedClick = when (channel) {
                                        PadChannel.LEFT -> ClickChannel.RIGHT
                                        PadChannel.RIGHT -> ClickChannel.LEFT
                                        PadChannel.MONO -> ClickChannel.MONO
                                    }
                                    clickChannel = linkedClick
                                    audio.currentClickChannel = linkedClick
                                    prefs.edit().putString("padChannel", channel.name).putString("clickChannel", linkedClick.name).apply()
                                },
                                onClickChannelChange = { channel ->
                                    clickChannel = channel
                                    audio.currentClickChannel = channel
                                    val linkedPad = when (channel) {
                                        ClickChannel.LEFT -> PadChannel.RIGHT
                                        ClickChannel.RIGHT -> PadChannel.LEFT
                                        ClickChannel.MONO -> PadChannel.MONO
                                    }
                                    padChannel = linkedPad
                                    audio.updatePadPanning(linkedPad)
                                    prefs.edit().putString("clickChannel", channel.name).putString("padChannel", linkedPad.name).apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.release()
    }
}
