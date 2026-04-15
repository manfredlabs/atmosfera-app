package com.manfredlabs.atmosfera.model

data class Song(
    val id: Long = 0,
    val name: String,
    val note: String,
    val isMajor: Boolean = true,
    val bpm: Int,
    val accents: String = "1,0,0,0",
    val padEnabled: Boolean = true,
    val clickEnabled: Boolean = true,
    val createdAt: Long = 0,
    val sortOrder: Int = 0,
    val padMode: String = "maj",
    val soundPackId: Long = -1,
    val padVolume: Float = 0.5f,
    val padChannel: String = "mono",
    val clickVolume: Float = 0.5f,
    val clickChannel: String = "mono"
) {
    fun accentList(): List<Int> =
        if (accents.isBlank()) listOf(1, 0, 0, 0)
        else accents.split(",").map { it.toIntOrNull() ?: if (it == "true") 1 else 0 }

    companion object {
        fun accentsToString(list: List<Int>): String = list.joinToString(",")
    }
}

data class SoundPack(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = 0
)

data class SoundPad(
    val id: Long = 0,
    val packId: Long,
    val note: String,
    val mode: String,
    val filePath: String,
    val createdAt: Long = 0
)

data class MixProject(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = 0,
    val sortOrder: Int = 0,
    val inPlaylist: Boolean = false,
    val padVolume: Float = 0.5f,
    val padChannel: String = "mono",
    val clickVolume: Float = 0.5f,
    val clickChannel: String = "mono"
)

data class MixTrack(
    val id: Long = 0,
    val projectId: Long,
    val trackType: String,
    val label: String,
    val volume: Float = 0.5f,
    val channel: String = "mono",
    val sortOrder: Int = 0,
    val note: String? = null,
    val padMode: String? = null,
    val soundPackId: Long? = null,
    val bpm: Int? = null,
    val accents: String? = null,
    val filePath: String? = null,
    val fileName: String? = null
)
