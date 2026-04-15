package com.manfredlabs.atmosfera.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.manfredlabs.atmosfera.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface SongDao {
    fun getAll(): Flow<List<Song>>
    suspend fun getById(id: Long): Song?
    suspend fun insert(song: Song)
    suspend fun delete(song: Song)
    suspend fun update(song: Song)
    suspend fun updateAll(songs: List<Song>)
}

internal fun com.manfredlabs.atmosfera.db.Songs.toModel() = Song(
    id = id,
    name = name,
    note = note,
    isMajor = isMajor != 0L,
    bpm = bpm.toInt(),
    accents = accents,
    padEnabled = padEnabled != 0L,
    clickEnabled = clickEnabled != 0L,
    createdAt = createdAt,
    sortOrder = sortOrder.toInt(),
    padMode = padMode,
    soundPackId = soundPackId,
    padVolume = padVolume.toFloat(),
    padChannel = padChannel,
    clickVolume = clickVolume.toFloat(),
    clickChannel = clickChannel
)

class SQLDelightSongDao(private val queries: SongQueries) : SongDao {

    override fun getAll(): Flow<List<Song>> =
        queries.getAll().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { it.toModel() }
        }

    override suspend fun getById(id: Long): Song? = withContext(Dispatchers.Default) {
        queries.getById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun insert(song: Song) = withContext(Dispatchers.Default) {
        queries.insert(
            name = song.name,
            note = song.note,
            isMajor = if (song.isMajor) 1L else 0L,
            bpm = song.bpm.toLong(),
            accents = song.accents,
            padEnabled = if (song.padEnabled) 1L else 0L,
            clickEnabled = if (song.clickEnabled) 1L else 0L,
            createdAt = if (song.createdAt == 0L) currentTimeMs() else song.createdAt,
            sortOrder = song.sortOrder.toLong(),
            padMode = song.padMode,
            soundPackId = song.soundPackId,
            padVolume = song.padVolume.toDouble(),
            padChannel = song.padChannel,
            clickVolume = song.clickVolume.toDouble(),
            clickChannel = song.clickChannel
        )
    }

    override suspend fun delete(song: Song) = withContext(Dispatchers.Default) {
        queries.deleteById(song.id)
    }

    override suspend fun update(song: Song) = withContext(Dispatchers.Default) {
        queries.update(
            name = song.name,
            note = song.note,
            isMajor = if (song.isMajor) 1L else 0L,
            bpm = song.bpm.toLong(),
            accents = song.accents,
            padEnabled = if (song.padEnabled) 1L else 0L,
            clickEnabled = if (song.clickEnabled) 1L else 0L,
            sortOrder = song.sortOrder.toLong(),
            padMode = song.padMode,
            soundPackId = song.soundPackId,
            padVolume = song.padVolume.toDouble(),
            padChannel = song.padChannel,
            clickVolume = song.clickVolume.toDouble(),
            clickChannel = song.clickChannel,
            id = song.id
        )
    }

    override suspend fun updateAll(songs: List<Song>) = withContext(Dispatchers.Default) {
        queries.transaction {
            songs.forEach { song ->
                queries.update(
                    name = song.name,
                    note = song.note,
                    isMajor = if (song.isMajor) 1L else 0L,
                    bpm = song.bpm.toLong(),
                    accents = song.accents,
                    padEnabled = if (song.padEnabled) 1L else 0L,
                    clickEnabled = if (song.clickEnabled) 1L else 0L,
                    sortOrder = song.sortOrder.toLong(),
                    padMode = song.padMode,
                    soundPackId = song.soundPackId,
                    padVolume = song.padVolume.toDouble(),
                    padChannel = song.padChannel,
                    clickVolume = song.clickVolume.toDouble(),
                    clickChannel = song.clickChannel,
                    id = song.id
                )
            }
        }
    }
}
