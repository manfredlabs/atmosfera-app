package com.manfredlabs.atmosfera.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.manfredlabs.atmosfera.model.MixProject
import com.manfredlabs.atmosfera.model.MixTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface MixProjectDao {
    fun getAll(): Flow<List<MixProject>>
    fun getAllInPlaylist(): Flow<List<MixProject>>
    suspend fun getAllOnce(): List<MixProject>
    suspend fun getById(id: Long): MixProject?
    suspend fun insert(project: MixProject): Long
    suspend fun update(project: MixProject)
    suspend fun delete(project: MixProject)
    suspend fun updateAll(projects: List<MixProject>)
    fun getTracksForProject(projectId: Long): Flow<List<MixTrack>>
    suspend fun getTracksForProjectOnce(projectId: Long): List<MixTrack>
    suspend fun getTrackCount(projectId: Long): Int
    suspend fun insertTrack(track: MixTrack): Long
    suspend fun updateTrack(track: MixTrack)
    suspend fun deleteTrack(track: MixTrack)
    suspend fun deleteTracksForProject(projectId: Long)
}

internal fun Mix_projects.toModel() = MixProject(
    id = id,
    name = name,
    createdAt = createdAt,
    sortOrder = sortOrder.toInt(),
    inPlaylist = inPlaylist != 0L,
    padVolume = padVolume.toFloat(),
    padChannel = padChannel,
    clickVolume = clickVolume.toFloat(),
    clickChannel = clickChannel
)

internal fun Mix_tracks.toModel() = MixTrack(
    id = id,
    projectId = projectId,
    trackType = trackType,
    label = label,
    volume = volume.toFloat(),
    channel = channel,
    sortOrder = sortOrder.toInt(),
    note = note,
    padMode = padMode,
    soundPackId = soundPackId,
    bpm = bpm?.toInt(),
    accents = accents,
    filePath = filePath,
    fileName = fileName
)

class SQLDelightMixProjectDao(
    private val projectQueries: MixProjectQueries,
    private val trackQueries: MixTrackQueries
) : MixProjectDao {

    override fun getAll(): Flow<List<MixProject>> =
        projectQueries.getAll().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { it.toModel() }
        }

    override fun getAllInPlaylist(): Flow<List<MixProject>> =
        projectQueries.getAllInPlaylist().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { it.toModel() }
        }

    override suspend fun getAllOnce(): List<MixProject> = withContext(Dispatchers.Default) {
        projectQueries.getAll().executeAsList().map { it.toModel() }
    }

    override suspend fun getById(id: Long): MixProject? = withContext(Dispatchers.Default) {
        projectQueries.getById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun insert(project: MixProject): Long = withContext(Dispatchers.Default) {
        var newId = 0L
        projectQueries.transaction {
            projectQueries.insert(
                name = project.name,
                createdAt = if (project.createdAt == 0L) currentTimeMs() else project.createdAt,
                sortOrder = project.sortOrder.toLong(),
                inPlaylist = if (project.inPlaylist) 1L else 0L,
                padVolume = project.padVolume.toDouble(),
                padChannel = project.padChannel,
                clickVolume = project.clickVolume.toDouble(),
                clickChannel = project.clickChannel
            )
            newId = projectQueries.lastInsertId().executeAsOne()
        }
        newId
    }

    override suspend fun update(project: MixProject) = withContext(Dispatchers.Default) {
        projectQueries.update(
            name = project.name,
            sortOrder = project.sortOrder.toLong(),
            inPlaylist = if (project.inPlaylist) 1L else 0L,
            padVolume = project.padVolume.toDouble(),
            padChannel = project.padChannel,
            clickVolume = project.clickVolume.toDouble(),
            clickChannel = project.clickChannel,
            id = project.id
        )
    }

    override suspend fun delete(project: MixProject) = withContext(Dispatchers.Default) {
        projectQueries.deleteById(project.id)
    }

    override suspend fun updateAll(projects: List<MixProject>) = withContext(Dispatchers.Default) {
        projectQueries.transaction {
            projects.forEach { p ->
                projectQueries.update(
                    name = p.name,
                    sortOrder = p.sortOrder.toLong(),
                    inPlaylist = if (p.inPlaylist) 1L else 0L,
                    padVolume = p.padVolume.toDouble(),
                    padChannel = p.padChannel,
                    clickVolume = p.clickVolume.toDouble(),
                    clickChannel = p.clickChannel,
                    id = p.id
                )
            }
        }
    }

    override fun getTracksForProject(projectId: Long): Flow<List<MixTrack>> =
        trackQueries.getTracksForProject(projectId).asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { it.toModel() }
        }

    override suspend fun getTracksForProjectOnce(projectId: Long): List<MixTrack> =
        withContext(Dispatchers.Default) {
            trackQueries.getTracksForProject(projectId).executeAsList().map { it.toModel() }
        }

    override suspend fun getTrackCount(projectId: Long): Int = withContext(Dispatchers.Default) {
        trackQueries.getTrackCount(projectId).executeAsOne().toInt()
    }

    override suspend fun insertTrack(track: MixTrack): Long = withContext(Dispatchers.Default) {
        var newId = 0L
        trackQueries.transaction {
            trackQueries.insert(
                projectId = track.projectId,
                trackType = track.trackType,
                label = track.label,
                volume = track.volume.toDouble(),
                channel = track.channel,
                sortOrder = track.sortOrder.toLong(),
                note = track.note,
                padMode = track.padMode,
                soundPackId = track.soundPackId,
                bpm = track.bpm?.toLong(),
                accents = track.accents,
                filePath = track.filePath,
                fileName = track.fileName
            )
            newId = trackQueries.lastInsertId().executeAsOne()
        }
        newId
    }

    override suspend fun updateTrack(track: MixTrack) = withContext(Dispatchers.Default) {
        trackQueries.update(
            trackType = track.trackType,
            label = track.label,
            volume = track.volume.toDouble(),
            channel = track.channel,
            sortOrder = track.sortOrder.toLong(),
            note = track.note,
            padMode = track.padMode,
            soundPackId = track.soundPackId,
            bpm = track.bpm?.toLong(),
            accents = track.accents,
            filePath = track.filePath,
            fileName = track.fileName,
            id = track.id
        )
    }

    override suspend fun deleteTrack(track: MixTrack) = withContext(Dispatchers.Default) {
        trackQueries.deleteById(track.id)
    }

    override suspend fun deleteTracksForProject(projectId: Long) = withContext(Dispatchers.Default) {
        trackQueries.deleteTracksForProject(projectId)
    }
}
