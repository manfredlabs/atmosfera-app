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
    fun accentList(): List<Int> = if (accents.isBlank()) listOf(1,0,0,0) else accents.split(",").map { it.toIntOrNull() ?: if (it == "true") 1 else 0 }
    companion object {
        fun accentsToString(list: List<Int>): String = list.joinToString(",")
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

@Entity(tableName = "sound_pads",
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

@Entity(tableName = "mix_projects")
data class MixProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

@Entity(
    tableName = "mix_tracks",
    foreignKeys = [ForeignKey(
        entity = MixProject::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class MixTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val trackType: String,      // "pad", "click", "custom"
    val label: String,
    val volume: Float = 0.5f,
    val channel: String = "mono",   // "left", "mono", "right"
    val sortOrder: Int = 0,
    // Pad-specific
    val note: String? = null,
    val padMode: String? = null,
    val soundPackId: Long? = null,
    // Click-specific
    val bpm: Int? = null,
    val accents: String? = null,
    // Custom-specific
    val filePath: String? = null,
    val fileName: String? = null
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

@Dao
interface MixProjectDao {
    @Query("SELECT * FROM mix_projects ORDER BY sortOrder ASC, createdAt DESC")
    fun getAll(): Flow<List<MixProject>>

    @Query("SELECT * FROM mix_projects WHERE id = :id")
    suspend fun getById(id: Long): MixProject?

    @Insert
    suspend fun insert(project: MixProject): Long

    @Update
    suspend fun update(project: MixProject)

    @Delete
    suspend fun delete(project: MixProject)

    @Update
    suspend fun updateAll(projects: List<MixProject>)

    @Query("SELECT * FROM mix_tracks WHERE projectId = :projectId ORDER BY sortOrder ASC")
    fun getTracksForProject(projectId: Long): Flow<List<MixTrack>>

    @Query("SELECT * FROM mix_tracks WHERE projectId = :projectId ORDER BY sortOrder ASC")
    suspend fun getTracksForProjectOnce(projectId: Long): List<MixTrack>

    @Query("SELECT COUNT(*) FROM mix_tracks WHERE projectId = :projectId")
    suspend fun getTrackCount(projectId: Long): Int

    @Insert
    suspend fun insertTrack(track: MixTrack): Long

    @Update
    suspend fun updateTrack(track: MixTrack)

    @Delete
    suspend fun deleteTrack(track: MixTrack)
}

@Database(entities = [Song::class, SoundPack::class, SoundPad::class, MixProject::class, MixTrack::class], version = 11)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun soundPackDao(): SoundPackDao
    abstract fun mixProjectDao(): MixProjectDao

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

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mix_projects (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mix_tracks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        trackType TEXT NOT NULL,
                        label TEXT NOT NULL,
                        volume REAL NOT NULL DEFAULT 0.5,
                        channel TEXT NOT NULL DEFAULT 'mono',
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        note TEXT,
                        padMode TEXT,
                        soundPackId INTEGER,
                        bpm INTEGER,
                        accents TEXT,
                        filePath TEXT,
                        fileName TEXT,
                        FOREIGN KEY (projectId) REFERENCES mix_projects(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mix_tracks_projectId ON mix_tracks(projectId)")
            }
        }

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val prefs = context.applicationContext.getSharedPreferences("atmosfera_settings", android.content.Context.MODE_PRIVATE)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "atmosfera_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        if (!prefs.getBoolean("default_pack_checked", false)) {
                            db.execSQL("""
                                DELETE FROM sound_packs
                                WHERE isDefault = 1
                                AND id != (SELECT MIN(id) FROM sound_packs WHERE isDefault = 1)
                            """)
                            db.execSQL("""
                                INSERT INTO sound_packs (name, description, isDefault, createdAt)
                                SELECT 'Atmos', '', 1, ${System.currentTimeMillis()}
                                WHERE NOT EXISTS (SELECT 1 FROM sound_packs WHERE isDefault = 1)
                            """)
                            prefs.edit().putBoolean("default_pack_checked", true).apply()
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
