package com.manfredlabs.atmosfera.audio

import com.manfredlabs.atmosfera.model.ClickChannel
import com.manfredlabs.atmosfera.model.MixTrack
import com.manfredlabs.atmosfera.model.PadChannel

/**
 * Contract for the live-screen audio engine (pad + click playback).
 * Android: implemented by AudioEngine (ExoPlayer + SoundPool).
 * iOS: implemented by LiveAudioPlayerIos (AVAudioEngine + AVAudioPlayer).
 */
interface LiveAudioPlayer {
    var fadeInMs: Long
    var fadeOutMs: Long
    var padTargetVolume: Float
    var padVolume: Float
    var currentClickVolume: Float
    var currentClickChannel: ClickChannel
    var currentAccents: List<Int>

    /** Play a built-in pad resource (e.g. "pad_c_maj"). */
    fun startPad(resName: String, padCh: PadChannel)

    /** Play a pad from a custom file path. */
    fun startPadFromFile(filePath: String, padCh: PadChannel)

    /** Fade-out and stop pad. Optional callback when fully stopped. */
    fun stopPad(onComplete: (() -> Unit)? = null)

    /** Immediate (no fade) pad stop. */
    fun stopPadImmediate()

    fun startClick(bpm: Int, channel: ClickChannel, volume: Float, accents: List<Int>)
    fun stopClick()
    fun restartClick(bpm: Int, channel: ClickChannel, volume: Float, accents: List<Int>)

    /** Update stereo panning of the currently-playing pad. */
    fun updatePadPanning(channel: PadChannel)

    fun release()
}

/**
 * Contract for the Mix Studio audio engine (multi-track playback).
 * Android: implemented by MixAudioEngine.
 * iOS: implemented by MixAudioPlayerIos.
 */
interface MixAudioPlayer {
    var fadeInMs: Long
    var fadeOutMs: Long
    val isPaused: Boolean

    fun startTrack(track: MixTrack)
    fun stopTrack(trackId: Long)
    fun startAll(tracks: List<MixTrack>)
    fun stopAll()
    fun pauseAll()
    fun resumeAll(allTracks: List<MixTrack>)

    fun seekAllCustom(positionMs: Long)
    fun getCustomDurationMs(): Long
    fun getCustomPositionMs(): Long
    fun hasCustomTracks(): Boolean

    fun setTrackVolume(trackId: Long, volume: Float)
    fun muteTrack(trackId: Long)
    fun unmuteTrack(trackId: Long)
    fun isPlaying(trackId: Long): Boolean
    fun isAnyPlaying(): Boolean

    fun release()
}

/**
 * Contract for audio file storage (copy from picker URI to internal storage).
 * Android: implemented by a wrapper around MixFileManager + PadProcessor.
 * iOS: implemented using FileManager.
 */
interface AudioFileStorage {
    /**
     * Copy audio from a picker URI string to internal mix storage.
     * @return absolute path of the stored file.
     */
    fun copyAudioToMixStorage(projectId: Long, trackId: Long, sourceUri: String, displayName: String): String

    fun deleteTrackFile(filePath: String)
    fun deleteProjectFiles(projectId: Long)

    /**
     * Copy a pad audio file from picker URI to the given destination path.
     * @return true on success.
     */
    fun copyPadAudio(sourceUri: String, destPath: String): Boolean
}
