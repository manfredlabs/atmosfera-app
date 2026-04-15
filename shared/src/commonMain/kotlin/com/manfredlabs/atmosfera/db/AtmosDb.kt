package com.manfredlabs.atmosfera.db

class AtmosDb(driverFactory: DatabaseDriverFactory) {
    private val database = AtmosDatabase(driverFactory.createDriver())

    val songDao: SongDao = SQLDelightSongDao(database.songQueries)
    val soundPackDao: SoundPackDao = SQLDelightSoundPackDao(
        database.soundPackQueries,
        database.soundPadQueries
    )
    val mixProjectDao: MixProjectDao = SQLDelightMixProjectDao(
        database.mixProjectQueries,
        database.mixTrackQueries
    )
}
