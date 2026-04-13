package com.example.atmosfera.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.atmosfera.data.MixTrack

/**
 * Audio engine for Mix Studio — manages multiple simultaneous ExoPlayer
 * instances (pads & custom audio) plus a SoundPool-based click track.
 */
class MixAudioEngine(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val fadeHandler = Handler(Looper.getMainLooper())

    // ExoPlayer instances keyed by track ID (for pad and custom tracks)
    private val players = mutableMapOf<Long, ExoPlayer>()
    private val mixers = mutableMapOf<Long, ChannelMixingAudioProcessor>()
    private val padTrackIds = mutableSetOf<Long>()

    // Fade settings (set from Settings via MainActivity)
    @Volatile var fadeInMs = 2000L
    @Volatile var fadeOutMs = 1500L
    private val fadeSteps = 30

    // Click track state (only one click track at a time)
    private var clickTrackId: Long? = null
    private var clickRunnable: Runnable? = null
    private var isClickRunning = false
    private lateinit var soundPool: SoundPool
    private var clickSoundId: Int = 0
    private var accentSoundId: Int = 0
    @Volatile private var soundPoolReady = false

    // Live-updatable click volume (updated by setTrackVolume)
    @Volatile private var clickVolLeft: Float = 0.5f
    @Volatile private var clickVolRight: Float = 0.5f
    @Volatile private var clickChannel: String = "mono"

    /** Observable playing state per track: trackId → isPlaying */
    val trackPlaying = mutableStateMapOf<Long, Boolean>()

    /** Muted tracks: trackId → muted — track keeps running but silenced */
    val mutedTracks = mutableStateMapOf<Long, Boolean>()

    /** Stored volume for muted tracks so we can restore on unmute */
    private val mutedVolumes = mutableMapOf<Long, Float>()

    /** Whether playback is paused (custom tracks hold position) */
    @Volatile var isPaused = false
        private set

    fun init(clickResId: Int, accentResId: Int) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()
        clickSoundId = soundPool.load(context, clickResId, 1)
        accentSoundId = soundPool.load(context, accentResId, 1)
        soundPoolReady = true
    }

    // ── Start / Stop individual tracks ──────────────────────────────────

    @OptIn(UnstableApi::class)
    fun startTrack(track: MixTrack) {
        val started = when (track.trackType) {
            "pad" -> startPadTrack(track)
            "custom" -> startCustomTrack(track)
            "click" -> startClickTrack(track)
            else -> false
        }
        if (started) {
            trackPlaying[track.id] = true
            // Re-apply mute if track was muted before restart
            if (mutedTracks[track.id] == true) {
                players[track.id]?.let { player ->
                    if (!mutedVolumes.containsKey(track.id)) {
                        mutedVolumes[track.id] = track.volume
                    }
                    player.volume = 0f
                }
            }
        }
    }

    fun stopTrack(trackId: Long) {
        // Check if it's the click track
        if (clickTrackId == trackId) {
            stopClickInternal()
        }
        // Check if it's an ExoPlayer track
        players.remove(trackId)?.let { player ->
            mixers.remove(trackId)
            if (trackId in padTrackIds) {
                padTrackIds.remove(trackId)
                // Cancel any pending fades for this track
                try { player.stop(); player.release() } catch (_: Exception) {}
            } else {
                try { player.stop(); player.release() } catch (_: Exception) {}
            }
        }
        trackPlaying[trackId] = false
    }

    private fun fadeOutPlayer(trackId: Long, player: ExoPlayer) {
        val targetVolume = player.volume
        val stepDelay = fadeOutMs / fadeSteps
        for (i in 1..fadeSteps) {
            fadeHandler.postDelayed({
                try {
                    val fraction = 1f - i.toFloat() / fadeSteps
                    player.volume = fraction * fraction * targetVolume
                } catch (_: Exception) {}
            }, i * stepDelay)
        }
        fadeHandler.postDelayed({
            try { player.stop(); player.release() } catch (_: Exception) {}
            players.remove(trackId)
            mixers.remove(trackId)
            padTrackIds.remove(trackId)
        }, fadeOutMs + 50)
    }

    fun startAll(tracks: List<MixTrack>) {
        isPaused = false
        tracks.forEach { startTrack(it) }
    }

    fun stopAll() {
        isPaused = false
        fadeHandler.removeCallbacksAndMessages(null)
        val ids = players.keys.toList()
        ids.forEach { stopTrack(it) }
        clickTrackId?.let { stopTrack(it) }
        trackPlaying.clear()
    }

    /** Pause custom tracks (hold position), stop pads & clicks */
    fun pauseAll() {
        isPaused = true
        // Pause custom ExoPlayers (keep position)
        players.forEach { (id, player) ->
            if (id !in padTrackIds) {
                player.pause()
                trackPlaying[id] = false
            }
        }
        // Stop pads (they loop, no position to hold)
        val padIds = padTrackIds.toList()
        padIds.forEach { stopTrack(it) }
        // Stop click
        clickTrackId?.let { stopTrack(it) }
    }

    /** Resume paused custom tracks, restart pads & clicks */
    fun resumeAll(allTracks: List<MixTrack>) {
        isPaused = false
        // Resume custom ExoPlayers that are still in the map
        val customIds = players.keys.filter { it !in padTrackIds }.toList()
        customIds.forEach { id ->
            players[id]?.let { player ->
                try {
                    player.play()
                    trackPlaying[id] = true
                } catch (_: Exception) {
                    // Player was released, remove it
                    players.remove(id)
                    mixers.remove(id)
                }
            }
        }
        // Restart pads & clicks
        allTracks.filter { it.trackType == "pad" || it.trackType == "click" }.forEach {
            startTrack(it)
        }
    }

    /** Seek all custom tracks to the given position */
    fun seekAllCustom(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0)
        players.forEach { (id, player) ->
            if (id !in padTrackIds) {
                try { player.seekTo(safePos) } catch (_: Exception) {}
            }
        }
    }

    /** Get the max duration among all custom tracks (ms), or 0 */
    fun getCustomDurationMs(): Long {
        return players.filter { it.key !in padTrackIds }
            .values.maxOfOrNull { it.duration.coerceAtLeast(0) } ?: 0L
    }

    /** Get the current position of the first custom track (ms), or 0 */
    fun getCustomPositionMs(): Long {
        return players.filter { it.key !in padTrackIds }
            .values.firstOrNull()?.currentPosition?.coerceAtLeast(0) ?: 0L
    }

    /** Check if there are any active custom track players */
    fun hasCustomTracks(): Boolean {
        return players.any { it.key !in padTrackIds }
    }

    fun setTrackVolume(trackId: Long, volume: Float) {
        if (trackId == clickTrackId) {
            val (l, r) = channelVolumes(clickChannel, volume)
            clickVolLeft = l
            clickVolRight = r
            if (trackId in mutedTracks) mutedVolumes[trackId] = volume
            return
        }
        if (trackId in mutedTracks) {
            mutedVolumes[trackId] = volume
        } else {
            players[trackId]?.volume = volume
        }
    }

    fun muteTrack(trackId: Long) {
        if (mutedTracks[trackId] == true) return
        mutedTracks[trackId] = true
        // ExoPlayer tracks: save current volume, set to 0
        players[trackId]?.let { player ->
            mutedVolumes[trackId] = player.volume
            player.volume = 0f
        }
        // Click track: volume is applied per-tick via isMuted check
    }

    fun unmuteTrack(trackId: Long) {
        if (mutedTracks[trackId] != true) return
        mutedTracks.remove(trackId)
        // ExoPlayer tracks: restore volume
        players[trackId]?.let { player ->
            player.volume = mutedVolumes.remove(trackId) ?: 0.5f
        }
        mutedVolumes.remove(trackId)
    }

    // ── Pad track (ExoPlayer, looping) ──────────────────────────────────

    @OptIn(UnstableApi::class)
    private fun startPadTrack(track: MixTrack): Boolean {
        stopTrack(track.id)

        val uri = if (track.soundPackId == null || track.soundPackId <= 0L) {
            val note = track.note ?: "c"
            val mode = track.padMode ?: "neu"
            val resName = "pad_${note}_${mode}"
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            if (resId == 0) return false
            "android.resource://${context.packageName}/$resId"
        } else {
            track.filePath?.let { "file://$it" } ?: return false
        }

        val (mixer, player) = createPlayer(uri, track)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.volume = 0f
        player.prepare()
        player.play()

        players[track.id] = player
        mixers[track.id] = mixer
        padTrackIds.add(track.id)

        // Fade in (skip if muted)
        val targetVolume = track.volume
        if (mutedTracks[track.id] != true) {
            val stepDelay = fadeInMs / fadeSteps
            for (i in 1..fadeSteps) {
                fadeHandler.postDelayed({
                    if (players[track.id] == player && mutedTracks[track.id] != true) {
                        val fraction = i.toFloat() / fadeSteps
                        player.volume = fraction * fraction * targetVolume
                    }
                }, i * stepDelay)
            }
        }
        return true
    }

    // ── Custom audio track (ExoPlayer, plays once) ──────────────────────

    @OptIn(UnstableApi::class)
    private fun startCustomTrack(track: MixTrack): Boolean {
        stopTrack(track.id)

        val path = track.filePath ?: return false
        val uri = "file://$path"

        val (mixer, player) = createPlayer(uri, track)
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    trackPlaying[track.id] = false
                    players.remove(track.id)?.let {
                        try { it.release() } catch (_: Exception) {}
                    }
                    mixers.remove(track.id)
                }
            }
        })
        player.prepare()
        player.play()

        players[track.id] = player
        mixers[track.id] = mixer
        return true
    }

    // ── Click track (SoundPool + Handler, looping) ──────────────────────

    private fun startClickTrack(track: MixTrack): Boolean {
        stopTrack(track.id)
        if (!soundPoolReady) return false

        clickTrackId = track.id
        isClickRunning = true
        val bpm = (track.bpm ?: 120).coerceIn(30, 240)
        val accents = track.accents?.split(",")?.map {
            it.toIntOrNull() ?: 0
        } ?: listOf(1, 0, 0, 0)
        val interval = 60000L / bpm
        var beatIndex = 0

        val (initLeft, initRight) = channelVolumes(track.channel, track.volume)
        clickVolLeft = initLeft
        clickVolRight = initRight
        clickChannel = track.channel ?: "mono"

        clickRunnable = object : Runnable {
            override fun run() {
                if (!isClickRunning) return
                val beatState = accents[beatIndex % accents.size]
                val isMuted = clickTrackId != null && clickTrackId in mutedTracks
                if (beatState != 2 && !isMuted) {
                    val soundId = if (beatState == 1) accentSoundId else clickSoundId
                    soundPool.play(soundId, clickVolLeft, clickVolRight, 1, 0, 1f)
                }
                beatIndex = (beatIndex + 1) % accents.size
                handler.postDelayed(this, interval)
            }
        }
        handler.post(clickRunnable!!)
        return true
    }

    private fun stopClickInternal() {
        isClickRunning = false
        clickRunnable?.let { handler.removeCallbacks(it) }
        clickRunnable = null
        val id = clickTrackId
        clickTrackId = null
        if (id != null) trackPlaying[id] = false
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    private fun createPlayer(uri: String, track: MixTrack): Pair<ChannelMixingAudioProcessor, ExoPlayer> {
        val mixer = ChannelMixingAudioProcessor()
        applyPanning(mixer, track.channel)

        val renderersFactory = object : DefaultRenderersFactory(context) {
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

        val player = ExoPlayer.Builder(context, renderersFactory).build().apply {
            val exoAttrs = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            setAudioAttributes(exoAttrs, false)
            setMediaItem(MediaItem.fromUri(uri))
            volume = track.volume
        }

        return mixer to player
    }

    private fun applyPanning(mixer: ChannelMixingAudioProcessor, channel: String) {
        val (l, r) = when (channel) {
            "left" -> 1f to 0f
            "right" -> 0f to 1f
            else -> 1f to 1f
        }
        mixer.putChannelMixingMatrix(
            ChannelMixingMatrix(1, 2, floatArrayOf(l, r))
        )
        mixer.putChannelMixingMatrix(
            ChannelMixingMatrix(2, 2, floatArrayOf(l, 0f, 0f, r))
        )
    }

    private fun channelVolumes(channel: String, volume: Float): Pair<Float, Float> = when (channel) {
        "left" -> volume to 0f
        "right" -> 0f to volume
        else -> volume to volume
    }

    fun isPlaying(trackId: Long): Boolean = trackPlaying[trackId] == true

    fun isAnyPlaying(): Boolean = trackPlaying.values.any { it }

    fun release() {
        fadeHandler.removeCallbacksAndMessages(null)
        stopAll()
        if (soundPoolReady) soundPool.release()
    }
}
