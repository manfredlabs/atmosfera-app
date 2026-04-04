package com.example.atmosfera.model

data class Note(
    val name: String,
    val label: String,
    val neuResName: String,
    val majResName: String,
    val minResName: String
) {
    fun resNameForMode(mode: String): String = when (mode) {
        "neu" -> neuResName
        "min" -> minResName
        else -> majResName
    }
}

enum class ClickChannel(val label: String) { LEFT("L"), MONO("M"), RIGHT("R") }
enum class PadChannel(val label: String) { LEFT("L"), MONO("M"), RIGHT("R") }

val ALL_NOTES = listOf(
    Note("c", "C", "pad_c_neu", "pad_c_maj", "pad_c_min"),
    Note("cs", "C#", "pad_cs_neu", "pad_cs_maj", "pad_cs_min"),
    Note("d", "D", "pad_d_neu", "pad_d_maj", "pad_d_min"),
    Note("ds", "D#", "pad_ds_neu", "pad_ds_maj", "pad_ds_min"),
    Note("e", "E", "pad_e_neu", "pad_e_maj", "pad_e_min"),
    Note("f", "F", "pad_f_neu", "pad_f_maj", "pad_f_min"),
    Note("fs", "F#", "pad_fs_neu", "pad_fs_maj", "pad_fs_min"),
    Note("g", "G", "pad_g_neu", "pad_g_maj", "pad_g_min"),
    Note("gs", "G#", "pad_gs_neu", "pad_gs_maj", "pad_gs_min"),
    Note("a", "A", "pad_a_neu", "pad_a_maj", "pad_a_min"),
    Note("as", "A#", "pad_as_neu", "pad_as_maj", "pad_as_min"),
    Note("b", "B", "pad_b_neu", "pad_b_maj", "pad_b_min"),
)
