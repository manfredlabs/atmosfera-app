package com.example.atmosfera.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String,       // "c", "cs", "d", etc.
    val isMajor: Boolean,   // legacy — use padMode instead
    val bpm: Int,
    val accents: String = "1,0,0,0",
    val padEnabled: Boolean = true,
    val clickEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val padMode: String = "maj",
    val soundPackId: Long = -1  // -1 = use current/default pack
) {
    fun accentList(): List<Boolean> = accents.split(",").map { it == "1" }
    companion object {
        fun accentsToString(list: List<Boolean>): String = list.joinToString(",") { if (it) "1" else "0" }
    }
}

@Entity(tableName = "sound_packs")
data class SoundPack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sound_pads",
    foreignKeys = [ForeignKey(
        entity = SoundPack::class,
        parentColumns = ["id"],
        childColumns = ["packId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("packId")]
)
data class SoundPad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packId: Long,
    val note: String,       // "c", "cs", "d", etc.
    val mode: String,       // "neu", "maj", "min"
    val filePath: String,   // internal storage path to processed WAV
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY sortOrder ASC, createdAt DESC")
    fun getAll(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): Song?

    @Insert
    suspend fun insert(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Update
    suspend fun update(song: Song)

    @Update
    suspend fun updateAll(songs: List<Song>)
}

@Dao
interface SoundPackDao {
    @Query("SELECT * FROM sound_packs ORDER BY isDefault DESC, name ASC")
    fun getAll(): Flow<List<SoundPack>>

    @Query("SELECT * FROM sound_packs WHERE id = :id")
    suspend fun getById(id: Long): SoundPack?

    @Query("SELECT * FROM sound_packs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): SoundPack?

    @Insert
    suspend fun insert(pack: SoundPack): Long

    @Update
    suspend fun update(pack: SoundPack)

    @Delete
    suspend fun delete(pack: SoundPack)

    @Query("SELECT * FROM sound_pads WHERE packId = :packId")
    fun getPadsForPack(packId: Long): Flow<List<SoundPad>>

    @Query("SELECT * FROM sound_pads WHERE packId = :packId AND note = :note AND mode = :mode LIMIT 1")
    suspend fun getPad(packId: Long, note: String, mode: String): SoundPad?

    @Insert
    suspend fun insertPad(pad: SoundPad): Long

    @Delete
    suspend fun deletePad(pad: SoundPad)

    @Query("DELETE FROM sound_pads WHERE packId = :packId AND note = :note AND mode = :mode")
    suspend fun removePad(packId: Long, note: String, mode: String)
}

@Database(entities = [Song::class, SoundPack::class, SoundPad::class], version = 10)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun soundPackDao(): SoundPackDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN accents TEXT NOT NULL DEFAULT '1,0,0,0'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN padEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN clickEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN padMode TEXT NOT NULL DEFAULT 'maj'")
                db.execSQL("UPDATE songs SET padMode = CASE WHEN isMajor = 1 THEN 'maj' ELSE 'min' END")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sound_packs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sound_pads (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packId INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (packId) REFERENCES sound_packs(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sound_pads_packId ON sound_pads(packId)")
                db.execSQL("INSERT INTO sound_packs (name, isDefault, createdAt) VALUES ('Atmosfera', 1, ${System.currentTimeMillis()})")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE sound_packs SET name = 'Atmos' WHERE isDefault = 1")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN soundPackId INTEGER NOT NULL DEFAULT -1")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sound_packs ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "atmosfera_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
