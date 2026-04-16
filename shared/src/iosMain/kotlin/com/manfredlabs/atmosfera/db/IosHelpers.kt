package com.manfredlabs.atmosfera.db

import com.manfredlabs.atmosfera.model.*
import kotlinx.coroutines.flow.first

/**
 * iOS bridge helper: exposes one-shot reads and primitive-typed CRUD operations,
 * avoiding the need to construct Kotlin data class instances in Swift.
 */
class IosDbHelper(private val db: AtmosDb) {

    // ── One-shot reads (collects first emission from Flow-based DAOs) ──────────

    suspend fun getAllSongs(): List<Song> = db.songDao.getAll().first()
    suspend fun getAllPacks(): List<SoundPack> = db.soundPackDao.getAll().first()
    suspend fun getMixProjects(): List<MixProject> = db.mixProjectDao.getAllOnce()
    suspend fun getPadsForPack(packId: Long): List<SoundPad> =
        db.soundPackDao.getPadsForPack(packId).first()
    suspend fun getTracksForProject(projectId: Long): List<MixTrack> =
        db.mixProjectDao.getTracksForProjectOnce(projectId)

    // ── Song CRUD ──────────────────────────────────────────────────────────────

    suspend fun insertSong(
        name: String, note: String, isMajor: Boolean, bpm: Int, accents: String,
        padEnabled: Boolean, clickEnabled: Boolean, sortOrder: Int, padMode: String,
        soundPackId: Long, padVolume: Float, padChannel: String,
        clickVolume: Float, clickChannel: String
    ) {
        db.songDao.insert(
            Song(
                name = name, note = note, isMajor = isMajor, bpm = bpm,
                accents = accents, padEnabled = padEnabled, clickEnabled = clickEnabled,
                createdAt = currentTimeMs(), sortOrder = sortOrder,
                padMode = padMode, soundPackId = soundPackId, padVolume = padVolume,
                padChannel = padChannel, clickVolume = clickVolume, clickChannel = clickChannel
            )
        )
    }

    suspend fun updateSong(
        id: Long, name: String, note: String, isMajor: Boolean, bpm: Int, accents: String,
        padEnabled: Boolean, clickEnabled: Boolean, createdAt: Long, sortOrder: Int,
        padMode: String, soundPackId: Long, padVolume: Float, padChannel: String,
        clickVolume: Float, clickChannel: String
    ) {
        db.songDao.update(
            Song(
                id = id, name = name, note = note, isMajor = isMajor, bpm = bpm,
                accents = accents, padEnabled = padEnabled, clickEnabled = clickEnabled,
                createdAt = createdAt, sortOrder = sortOrder, padMode = padMode,
                soundPackId = soundPackId, padVolume = padVolume, padChannel = padChannel,
                clickVolume = clickVolume, clickChannel = clickChannel
            )
        )
    }

    suspend fun deleteSongById(id: Long) {
        val song = db.songDao.getById(id) ?: return
        db.songDao.delete(song)
    }

    // ── SoundPack CRUD ─────────────────────────────────────────────────────────

    suspend fun insertSoundPack(name: String, description: String, isDefault: Boolean): Long =
        db.soundPackDao.insert(
            SoundPack(
                name = name, description = description, isDefault = isDefault,
                createdAt = currentTimeMs()
            )
        )

    suspend fun deleteSoundPackById(id: Long) {
        val pack = db.soundPackDao.getById(id) ?: return
        db.soundPackDao.delete(pack)
    }

    suspend fun renameSoundPack(id: Long, name: String) {
        val pack = db.soundPackDao.getById(id) ?: return
        db.soundPackDao.update(pack.copy(name = name))
    }

    suspend fun updateSoundPackDescription(id: Long, description: String) {
        val pack = db.soundPackDao.getById(id) ?: return
        db.soundPackDao.update(pack.copy(description = description))
    }

    suspend fun setDefaultPack(id: Long) {
        getAllPacks().forEach { pack ->
            db.soundPackDao.update(pack.copy(isDefault = pack.id == id))
        }
    }

    suspend fun assignPad(packId: Long, note: String, mode: String, filePath: String): Long {
        db.soundPackDao.removePad(packId, note, mode)
        return db.soundPackDao.insertPad(
            SoundPad(
                packId = packId, note = note, mode = mode, filePath = filePath,
                createdAt = currentTimeMs()
            )
        )
    }

    suspend fun removePadAssignment(packId: Long, note: String, mode: String) =
        db.soundPackDao.removePad(packId, note, mode)

    // ── MixProject CRUD ────────────────────────────────────────────────────────

    suspend fun insertMixProject(name: String): Long =
        db.mixProjectDao.insert(
            MixProject(
                name = name, createdAt = currentTimeMs(),
                sortOrder = getMixProjects().size
            )
        )

    suspend fun deleteMixProjectById(id: Long) {
        val project = db.mixProjectDao.getById(id) ?: return
        db.mixProjectDao.delete(project)
    }

    suspend fun toggleMixProjectInPlaylist(id: Long) {
        val project = db.mixProjectDao.getById(id) ?: return
        db.mixProjectDao.update(project.copy(inPlaylist = !project.inPlaylist))
    }

    suspend fun renameMixProject(id: Long, name: String) {
        val project = db.mixProjectDao.getById(id) ?: return
        db.mixProjectDao.update(project.copy(name = name))
    }

    suspend fun updateMixProjectSortOrder(id: Long, sortOrder: Int) {
        val project = db.mixProjectDao.getById(id) ?: return
        db.mixProjectDao.update(project.copy(sortOrder = sortOrder))
    }

    // ── MixTrack CRUD ──────────────────────────────────────────────────────────

    suspend fun insertMixTrack(
        projectId: Long, trackType: String, label: String, volume: Float, channel: String,
        sortOrder: Int, note: String?, padMode: String?, soundPackId: Long?,
        bpm: Int?, accents: String?, filePath: String?, fileName: String?
    ): Long = db.mixProjectDao.insertTrack(
        MixTrack(
            projectId = projectId, trackType = trackType, label = label, volume = volume,
            channel = channel, sortOrder = sortOrder, note = note, padMode = padMode,
            soundPackId = soundPackId, bpm = bpm, accents = accents,
            filePath = filePath, fileName = fileName
        )
    )

    suspend fun deleteMixTrackById(trackId: Long, projectId: Long) {
        val track = getTracksForProject(projectId).firstOrNull { it.id == trackId } ?: return
        db.mixProjectDao.deleteTrack(track)
    }

    suspend fun updateMixTrackVolume(trackId: Long, projectId: Long, volume: Float) {
        val track = getTracksForProject(projectId).firstOrNull { it.id == trackId } ?: return
        db.mixProjectDao.updateTrack(track.copy(volume = volume))
    }

    suspend fun updateMixTrackChannel(trackId: Long, projectId: Long, channel: String) {
        val track = getTracksForProject(projectId).firstOrNull { it.id == trackId } ?: return
        db.mixProjectDao.updateTrack(track.copy(channel = channel))
    }
}
