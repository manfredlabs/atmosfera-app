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

    // ExoPlayer instances keyed by track ID (for pad and custom tracks)
    private val players = mutableMapOf<Long, ExoPlayer>()
    private val mixers = mutableMapOf<Long, ChannelMixingAudioProcessor>()

    // Click track state (only one click track at a time)
    private var clickTrackId: Long? = null
    private var clickRunnable: Runnable? = null
    private var isClickRunning = false
    private lateinit var soundPool: SoundPool
    private var clickSoundId: Int = 0
    private var accentSoundId: Int = 0
    private var soundPoolReady = false

    /** Observable playing state per track: trackId → isPlaying */
    val trackPlaying = mutableStateMapOf<Long, Boolean>()

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
        when (track.trackType) {
            "pad" -> startPadTrack(track)
            "custom" -> startCustomTrack(track)
            "click" -> startClickTrack(track)
        }
        trackPlaying[track.id] = true
    }

    fun stopTrack(trackId: Long) {
        // Check if it's the click track
        if (clickTrackId == trackId) {
            stopClickInternal()
        }
        // Check if it's an ExoPlayer track
        players[trackId]?.let {
            try { it.stop(); it.release() } catch (_: Exception) {}
            players.remove(trackId)
            mixers.remove(trackId)
        }
        trackPlaying[trackId] = false
    }

    fun startAll(tracks: List<MixTrack>) {
        tracks.forEach { startTrack(it) }
    }

    fun stopAll() {
        val ids = players.keys.toList()
        ids.forEach { stopTrack(it) }
        clickTrackId?.let { stopTrack(it) }
    }

    fun setTrackVolume(trackId: Long, volume: Float) {
        players[trackId]?.volume = volume
    }

    // ── Pad track (ExoPlayer, looping) ──────────────────────────────────

    @OptIn(UnstableApi::class)
    private fun startPadTrack(track: MixTrack) {
        stopTrack(track.id)

        val uri = if (track.soundPackId == null || track.soundPackId <= 0L) {
            // Built-in pad from resources
            val note = track.note ?: "c"
            val mode = track.padMode ?: "neu"
            val resName = "pad_${note}_${mode}"
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            "android.resource://${context.packageName}/$resId"
        } else {
            // Custom sound pack — filePath should be set by caller
            track.filePath?.let { "file://$it" } ?: return
        }

        val (mixer, player) = createPlayer(uri, track)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
        player.play()

        players[track.id] = player
        mixers[track.id] = mixer
    }

    // ── Custom audio track (ExoPlayer, plays once) ──────────────────────

    @OptIn(UnstableApi::class)
    private fun startCustomTrack(track: MixTrack) {
        stopTrack(track.id)

        val path = track.filePath ?: return
        val uri = "file://$path"

        val (mixer, player) = createPlayer(uri, track)
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    trackPlaying[track.id] = false
                }
            }
        })
        player.prepare()
        player.play()

        players[track.id] = player
        mixers[track.id] = mixer
    }

    // ── Click track (SoundPool + Handler, looping) ──────────────────────

    private fun startClickTrack(track: MixTrack) {
        stopTrack(track.id)
        if (!soundPoolReady) return

        clickTrackId = track.id
        isClickRunning = true
        val bpm = track.bpm ?: 120
        val accents = track.accents?.split(",")?.map {
            it.toIntOrNull() ?: 0
        } ?: listOf(1, 0, 0, 0)
        val volume = track.volume
        val interval = 60000L / bpm
        var beatIndex = 0

        val (leftVol, rightVol) = channelVolumes(track.channel, volume)

        clickRunnable = object : Runnable {
            override fun run() {
                if (!isClickRunning) return
                val beatState = accents[beatIndex % accents.size]
                if (beatState != 2) {
                    val soundId = if (beatState == 1) accentSoundId else clickSoundId
                    soundPool.play(soundId, leftVol, rightVol, 1, 0, 1f)
                }
                beatIndex = (beatIndex + 1) % accents.size
                handler.postDelayed(this, interval)
            }
        }
        handler.post(clickRunnable!!)
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
        stopAll()
        if (soundPoolReady) soundPool.release()
    }
}
