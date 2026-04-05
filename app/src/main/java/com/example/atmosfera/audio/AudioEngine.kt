package com.example.atmosfera.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.atmosfera.model.ClickChannel
import com.example.atmosfera.model.PadChannel

class AudioEngine(private val context: Context) {

    private var player: ExoPlayer? = null
    private val padHandler = Handler(Looper.getMainLooper())
    private val retireHandler = Handler(Looper.getMainLooper())
    private val clickHandler = Handler(Looper.getMainLooper())
    var padTargetVolume = 0.5f

    @Volatile var fadeInMs = 2000L
    @Volatile var fadeOutMs = 1500L
    private val fadeSteps = 30

    private lateinit var soundPool: SoundPool
    private var clickSoundId: Int = 0
    private var accentSoundId: Int = 0
    private var isClickRunning = false
    private var clickRunnable: Runnable? = null
    var currentClickVolume = 0.5f
    var currentClickChannel = ClickChannel.MONO
    var currentAccents = listOf(true, false, false, false)

    val beatOn = mutableStateOf(false)
    val currentBeat = mutableIntStateOf(0)

    private var channelMixer: ChannelMixingAudioProcessor? = null

    // Track the single player being faded out (max 1 at a time)
    private var retiringPlayer: ExoPlayer? = null

    /** Current pad volume (for external adjustments) */
    var padVolume: Float
        get() = player?.volume ?: 0f
        set(value) { player?.volume = value }

    fun init(clickResId: Int, accentResId: Int) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()
        clickSoundId = soundPool.load(context, clickResId, 1)
        accentSoundId = soundPool.load(context, accentResId, 1)
    }

    /** Fade out and release a player. Uses retireHandler (independent of padHandler). */
    private fun retirePlayer(old: ExoPlayer) {
        val stepDelay = fadeOutMs / fadeSteps
        for (i in 1..fadeSteps) {
            retireHandler.postDelayed({
                try { old.volume = (1f - i.toFloat() / fadeSteps).let { it * it } * padTargetVolume }
                catch (_: Exception) {}
            }, i * stepDelay)
        }
        retireHandler.postDelayed({
            if (retiringPlayer == old) retiringPlayer = null
            try { old.release() } catch (_: Exception) {}
        }, fadeOutMs + 50)
    }

    /** Immediately kill any currently-retiring player */
    private fun killRetiring() {
        retireHandler.removeCallbacksAndMessages(null)
        retiringPlayer?.let {
            try { it.release() } catch (_: Exception) {}
        }
        retiringPlayer = null
    }

    @OptIn(UnstableApi::class)
    fun startPad(rawResName: String, padCh: PadChannel) {
        val resId = context.resources.getIdentifier(rawResName, "raw", context.packageName)
        val uri = "android.resource://${context.packageName}/$resId"
        startPadFromUri(uri, padCh)
    }

    /** Play a pad from a file path (custom sound packs). */
    @OptIn(UnstableApi::class)
    fun startPadFromFile(filePath: String, padCh: PadChannel) {
        val uri = android.net.Uri.fromFile(java.io.File(filePath)).toString()
        startPadFromUri(uri, padCh)
    }

    @OptIn(UnstableApi::class)
    private fun startPadFromUri(uri: String, padCh: PadChannel) {
        // Cancel any pending fade-in
        padHandler.removeCallbacksAndMessages(null)

        // Kill any previously-retiring player immediately (max 1 fading out at a time)
        killRetiring()

        // Fade out current player
        val old = player
        player = null
        if (old != null) {
            retiringPlayer = old
            retirePlayer(old)
        }

        val mixer = ChannelMixingAudioProcessor()
        applyPadPanning(mixer, padCh)
        channelMixer = mixer

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

        val newPlayer = ExoPlayer.Builder(context, renderersFactory).build().apply {
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

        val stepDelay = fadeInMs / fadeSteps
        for (i in 1..fadeSteps) {
            padHandler.postDelayed({
                if (player == newPlayer) {
                    newPlayer.volume = (i.toFloat() / fadeSteps).let { it * it } * padTargetVolume
                }
            }, i * stepDelay)
        }
    }

    fun stopPad(onComplete: (() -> Unit)? = null) {
        padHandler.removeCallbacksAndMessages(null)

        val current = player
        player = null

        if (current == null) {
            onComplete?.invoke()
            return
        }

        // Kill any previous retiring player, then fade this one
        killRetiring()
        retiringPlayer = current
        retirePlayer(current)

        if (onComplete != null) {
            retireHandler.postDelayed({ onComplete() }, fadeOutMs + 100)
        }
    }

    fun stopPadImmediate() {
        padHandler.removeCallbacksAndMessages(null)
        killRetiring()
        player?.let { try { it.release() } catch (_: Exception) {} }
        player = null
    }

    fun startClick(bpm: Int, channel: ClickChannel, volume: Float, accents: List<Boolean>) {
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
                currentBeat.intValue = beatIndex
                beatOn.value = true
                clickHandler.postDelayed({ beatOn.value = false }, beatFlashMs)
                beatIndex = (beatIndex + 1) % currentAccents.size
                clickHandler.postDelayed(this, interval)
            }
        }
        clickHandler.post(clickRunnable!!)
    }

    fun stopClick() {
        isClickRunning = false
        beatOn.value = false
        currentBeat.intValue = 0
        clickRunnable?.let { clickHandler.removeCallbacks(it) }
        clickRunnable = null
    }

    fun restartClick(bpm: Int, channel: ClickChannel, volume: Float, accents: List<Boolean>) {
        stopClick()
        startClick(bpm, channel, volume, accents)
    }

    fun updatePadPanning(channel: PadChannel) {
        channelMixer?.let { applyPadPanning(it, channel) }
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

    fun release() {
        stopClick()
        stopPadImmediate()
        soundPool.release()
    }
}
