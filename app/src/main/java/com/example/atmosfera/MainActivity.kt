package com.example.atmosfera

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.atmosfera.audio.AudioEngine
import com.example.atmosfera.audio.PadProcessor
import com.example.atmosfera.data.AppDatabase
import com.example.atmosfera.data.SoundPack
import com.example.atmosfera.data.SoundPad
import com.example.atmosfera.model.ALL_NOTES
import com.example.atmosfera.model.ClickChannel
import com.example.atmosfera.model.PadChannel
import com.example.atmosfera.model.availablePadsSet
import com.example.atmosfera.screens.AddSongScreen
import com.example.atmosfera.screens.HomeScreen
import com.example.atmosfera.screens.PlaylistScreen
import com.example.atmosfera.screens.SettingsScreen
import com.example.atmosfera.screens.SoundPackListScreen
import com.example.atmosfera.screens.SoundPackScreen
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
        val savedFadeIn = prefs.getLong("fadeInMs", 2000L)
        val savedFadeOut = prefs.getLong("fadeOutMs", 1500L)
        audio.padTargetVolume = savedPadVolume
        audio.fadeInMs = savedFadeIn
        audio.fadeOutMs = savedFadeOut

        setContent {
            AtmosferaTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: "home"
                val showBottomBar = currentRoute in listOf("home", "playlist", "settings", "pack_selector")
                val db = remember { AppDatabase.getInstance(this@MainActivity) }
                val songDao = remember { db.songDao() }
                val soundPackDao = remember { db.soundPackDao() }
                val padProcessor = remember { PadProcessor(this@MainActivity) }
                val soundPackDir = remember { java.io.File(filesDir, "soundpacks").also { it.mkdirs() } }

                // Sound pack state
                val allPacks by soundPackDao.getAll().collectAsState(initial = emptyList())
                var currentPackId by remember { mutableStateOf(prefs.getLong("currentPackId", -1L)) }

                // Ensure default pack exists (runs once)
                LaunchedEffect(Unit) {
                    val existing = soundPackDao.getDefault()
                    if (existing == null) {
                        soundPackDao.insert(SoundPack(name = "Atmos", isDefault = true))
                    }
                }

                // Auto-select default pack if none selected
                LaunchedEffect(allPacks) {
                    if (currentPackId == -1L && allPacks.isNotEmpty()) {
                        val defaultPack = allPacks.find { it.isDefault } ?: allPacks.first()
                        currentPackId = defaultPack.id
                    }
                }
                val currentPack = allPacks.find { it.id == currentPackId }
                val isDefaultPack = currentPack?.isDefault ?: true
                val currentPackName = currentPack?.name ?: "Atmos"
                val currentPads by soundPackDao.getPadsForPack(currentPackId).collectAsState(initial = emptyList())
                val availablePads = remember(currentPads) { availablePadsSet(currentPads) }

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
                var fadeInMs by remember { mutableStateOf(savedFadeIn) }
                var fadeOutMs by remember { mutableStateOf(savedFadeOut) }
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

                // Keep screen on in Live and locked Playlist
                val keepScreenOn = currentRoute == "home" || (currentRoute == "playlist" && playlistLocked)
                DisposableEffect(keepScreenOn) {
                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            inclusive = false
                                                            saveState = false
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = false
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
                    val tabOrder = mapOf("settings" to 0, "home" to 1, "playlist" to 2)
                    fun routeIndex(route: String?): Int = tabOrder[route] ?: 99

                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding),
                        enterTransition = {
                            val fromIdx = routeIndex(initialState.destination.route)
                            val toIdx = routeIndex(targetState.destination.route)
                            if (toIdx >= fromIdx) slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                            else slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                        },
                        exitTransition = {
                            val fromIdx = routeIndex(initialState.destination.route)
                            val toIdx = routeIndex(targetState.destination.route)
                            if (toIdx >= fromIdx) slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                            else slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            val fromIdx = routeIndex(initialState.destination.route)
                            val toIdx = routeIndex(targetState.destination.route)
                            if (fromIdx < 99 && toIdx < 99) {
                                // Tab-to-tab pop: respect direction
                                if (toIdx >= fromIdx) slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                                else slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                            } else {
                                // Sub-page pop: always from left
                                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                            }
                        },
                        popExitTransition = {
                            val fromIdx = routeIndex(initialState.destination.route)
                            val toIdx = routeIndex(targetState.destination.route)
                            if (fromIdx < 99 && toIdx < 99) {
                                if (toIdx >= fromIdx) slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                                else slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                            } else {
                                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                            }
                        }
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
                                currentPackName = currentPackName,
                                currentPackId = currentPackId,
                                isDefaultPack = isDefaultPack,
                                availablePads = availablePads,
                                allPacks = allPacks,
                                onPadTap = { note ->
                                    if (playingNote == note.label) {
                                        audio.stopPad { }
                                        playingNote = null
                                    } else {
                                        if (isDefaultPack) {
                                            audio.startPad(note.resNameForMode(padMode), padChannel)
                                        } else {
                                            val pad = currentPads.find { it.note == note.name && it.mode == padMode }
                                            pad?.let { audio.startPadFromFile(it.filePath, padChannel) }
                                        }
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
                                    if (playingNote != null) {
                                        audio.stopPad { }
                                        playingNote = null
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
                                },
                                onSelectPack = { packId ->
                                    if (currentPackId != packId) {
                                        audio.stopPad { }
                                        playingNote = null
                                        currentPackId = packId
                                        prefs.edit().putLong("currentPackId", packId).apply()
                                    }
                                },
                                onManagePacks = {
                                    navController.navigate("pack_selector")
                                }
                            )
                        }

                        composable("pack_selector") {
                            SoundPackListScreen(
                                packs = allPacks,
                                currentPackId = currentPackId,
                                onSelectPack = { packId ->
                                    if (currentPackId != packId) {
                                        audio.stopPad { }
                                        playingNote = null
                                        currentPackId = packId
                                        prefs.edit().putLong("currentPackId", packId).apply()
                                    }
                                    navController.popBackStack()
                                },
                                onCreatePack = {
                                    navController.navigate("sound_pack/new")
                                },
                                onEditPack = { packId ->
                                    navController.navigate("sound_pack/$packId")
                                },
                                onDeletePack = { pack ->
                                    scope.launch {
                                        if (currentPackId == pack.id) {
                                            val defaultPack = allPacks.find { it.isDefault }
                                            currentPackId = defaultPack?.id ?: -1L
                                            prefs.edit().putLong("currentPackId", currentPackId).apply()
                                            audio.stopPad { }
                                            playingNote = null
                                        }
                                        java.io.File(soundPackDir, pack.id.toString()).deleteRecursively()
                                        soundPackDao.delete(pack)
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("sound_pack/{packId}") { backStackEntry ->
                            val packIdStr = backStackEntry.arguments?.getString("packId") ?: return@composable
                            val existingPackId = if (packIdStr == "new") null else packIdStr.toLongOrNull()
                            SoundPackScreen(
                                packId = existingPackId,
                                soundPackDao = soundPackDao,
                                padProcessor = padProcessor,
                                soundPackDir = soundPackDir,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("playlist") {
                            BackHandler(enabled = playlistLocked) {
                                Toast.makeText(this@MainActivity, "Unlock playlist to go back", Toast.LENGTH_SHORT).show()
                            }
                            PlaylistScreen(
                                songDao = songDao,
                                playingSongId = playingSongId,
                                padVolume = padVolume,
                                clickVolume = clickVolume,
                                onPlaySong = { song ->
                                    scope.launch {
                                        val note = ALL_NOTES.find { it.name == song.note }
                                        audio.stopClick()

                                        padMode = song.padMode
                                        bpm = song.bpm
                                        accents = song.accentList()
                                        audio.currentAccents = accents

                                        val songPack = if (song.soundPackId > 0) song.soundPackId else currentPackId
                                        if (songPack != currentPackId) {
                                            currentPackId = songPack
                                            prefs.edit().putLong("currentPackId", songPack).apply()
                                        }

                                        if (note != null) {
                                            val defaultPack = allPacks.find { it.isDefault }
                                            if (songPack == defaultPack?.id || songPack == -1L) {
                                                audio.startPad(note.resNameForMode(song.padMode), padChannel)
                                            } else {
                                                val pad = soundPackDao.getPad(songPack, note.name, song.padMode)
                                                if (pad != null) {
                                                    audio.startPadFromFile(pad.filePath, padChannel)
                                                } else {
                                                    audio.startPad(note.resNameForMode(song.padMode), padChannel)
                                                }
                                            }
                                            playingNote = note.label
                                        }

                                        playingSongId = song.id

                                        if (song.clickEnabled) {
                                            clickEnabled = true
                                            audio.startClick(bpm, clickChannel, clickVolume, accents)
                                        } else {
                                            clickEnabled = false
                                        }
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
                                    } else {
                                        // Locking: stop any live pad/click playing
                                        if (playingNote != null) {
                                            audio.stopPad { }
                                            audio.stopClick()
                                            playingNote = null
                                            clickEnabled = false
                                        }
                                    }
                                    playlistLocked = !playlistLocked
                                },
                                allPacks = allPacks
                            )
                        }

                        composable("add_song") {
                            AddSongScreen(
                                songDao = songDao,
                                onBack = { navController.popBackStack() },
                                allPacks = allPacks,
                                currentPackId = currentPackId,
                                soundPackDao = soundPackDao,
                                liveBpm = bpm,
                                liveAccents = accents,
                                liveClickEnabled = clickEnabled,
                                livePadMode = padMode,
                                liveNote = playingNote?.let { label ->
                                    ALL_NOTES.find { it.label == label }?.name
                                } ?: "c"
                            )
                        }

                        composable("edit_song/{songId}") { backStackEntry ->
                            val songId = backStackEntry.arguments?.getString("songId")?.toLongOrNull()
                            AddSongScreen(
                                songDao = songDao,
                                onBack = { navController.popBackStack() },
                                editSongId = songId,
                                allPacks = allPacks,
                                currentPackId = currentPackId,
                                soundPackDao = soundPackDao
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
                                currentPackName = currentPackName,
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
                                },
                                fadeInMs = fadeInMs,
                                fadeOutMs = fadeOutMs,
                                onFadeInChange = {
                                    fadeInMs = it
                                    audio.fadeInMs = it
                                    prefs.edit().putLong("fadeInMs", it).apply()
                                },
                                onFadeOutChange = {
                                    fadeOutMs = it
                                    audio.fadeOutMs = it
                                    prefs.edit().putLong("fadeOutMs", it).apply()
                                },
                                onManagePacks = {
                                    navController.navigate("pack_selector")
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
