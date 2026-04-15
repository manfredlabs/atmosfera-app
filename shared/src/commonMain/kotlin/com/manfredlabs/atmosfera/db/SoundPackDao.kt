package com.manfredlabs.atmosfera.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.manfredlabs.atmosfera.model.SoundPack
import com.manfredlabs.atmosfera.model.SoundPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface SoundPackDao {
    fun getAll(): Flow<List<SoundPack>>
    suspend fun getById(id: Long): SoundPack?
    suspend fun getDefault(): SoundPack?
    suspend fun insert(pack: SoundPack): Long
    suspend fun update(pack: SoundPack)
    suspend fun delete(pack: SoundPack)
    fun getPadsForPack(packId: Long): Flow<List<SoundPad>>
    suspend fun getPad(packId: Long, note: String, mode: String): SoundPad?
    suspend fun insertPad(pad: SoundPad): Long
    suspend fun deletePad(pad: SoundPad)
    suspend fun removePad(packId: Long, note: String, mode: String)
}

internal fun Sound_packs.toModel() = SoundPack(
    id = id,
    name = name,
    description = description,
    isDefault = isDefault != 0L,
    createdAt = createdAt
)

internal fun Sound_pads.toModel() = SoundPad(
    id = id,
    packId = packId,
    note = note,
    mode = mode,
    filePath = filePath,
    createdAt = createdAt
)

class SQLDelightSoundPackDao(
    private val packQueries: SoundPackQueries,
    private val padQueries: SoundPadQueries
) : SoundPackDao {

    override fun getAll(): Flow<List<SoundPack>> =
        packQueries.getAll().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { it.toModel() }
        }

    override suspend fun getById(id: Long): SoundPack? = withContext(Dispatchers.Default) {
        packQueries.getById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun getDefault(): SoundPack? = withContext(Dispatchers.Default) {
        packQueries.getDefault().executeAsOneOrNull()?.toModel()
    }

    override suspend fun insert(pack: SoundPack): Long = withContext(Dispatchers.Default) {
        var newId = 0L
        packQueries.transaction {
            packQueries.insert(
                name = pack.name,
                description = pack.description,
                isDefault = if (pack.isDefault) 1L else 0L,
                createdAt = if (pack.createdAt == 0L) currentTimeMs() else pack.createdAt
            )
            newId = packQueries.lastInsertId().executeAsOne()
        }
        newId
    }

    override suspend fun update(pack: SoundPack) = withContext(Dispatchers.Default) {
        packQueries.update(
            name = pack.name,
            description = pack.description,
            isDefault = if (pack.isDefault) 1L else 0L,
            id = pack.id
        )
    }

    override suspend fun delete(pack: SoundPack) = withContext(Dispatchers.Default) {
        packQueries.deleteById(pack.id)
    }

    override fun getPadsForPack(packId: Long): Flow<List<SoundPad>> =
        padQueries.getPadsForPack(packId).asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { it.toModel() }
        }

    override suspend fun getPad(packId: Long, note: String, mode: String): SoundPad? =
        withContext(Dispatchers.Default) {
            padQueries.getPad(packId, note, mode).executeAsOneOrNull()?.toModel()
        }

    override suspend fun insertPad(pad: SoundPad): Long = withContext(Dispatchers.Default) {
        var newId = 0L
        padQueries.transaction {
            padQueries.insert(
                packId = pad.packId,
                note = pad.note,
                mode = pad.mode,
                filePath = pad.filePath,
                createdAt = if (pad.createdAt == 0L) currentTimeMs() else pad.createdAt
            )
            newId = padQueries.lastInsertId().executeAsOne()
        }
        newId
    }

    override suspend fun deletePad(pad: SoundPad) = withContext(Dispatchers.Default) {
        padQueries.deleteById(pad.id)
    }

    override suspend fun removePad(packId: Long, note: String, mode: String) =
        withContext(Dispatchers.Default) {
            padQueries.removePad(packId, note, mode)
        }
}
