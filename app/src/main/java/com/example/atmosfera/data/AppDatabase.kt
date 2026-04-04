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
    val accents: String = "1,0,0,0",  // comma-separated: "1,0,0,0" = first beat accented
    val padEnabled: Boolean = true,
    val clickEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val padMode: String = "maj"  // "neu", "maj", "min"
) {
    fun accentList(): List<Boolean> = accents.split(",").map { it == "1" }
    companion object {
        fun accentsToString(list: List<Boolean>): String = list.joinToString(",") { if (it) "1" else "0" }
    }
}

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

@Database(entities = [Song::class], version = 6)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

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

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "atmosfera_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
