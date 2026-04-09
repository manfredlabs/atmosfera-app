package com.example.atmosfera.audio

import android.content.Context
import android.net.Uri
import java.io.File

class MixFileManager(private val context: Context) {

    private fun projectDir(projectId: Long): File =
        File(context.filesDir, "mixstudio/$projectId").also { it.mkdirs() }

    /** Copy audio from SAF URI to internal storage. Returns the internal file path. */
    fun copyAudioToStorage(projectId: Long, trackId: Long, uri: Uri, displayName: String): String {
        val dir = projectDir(projectId)
        val safeFileName = "${trackId}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
        val dest = File(dir, safeFileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open URI: $uri")

        return dest.absolutePath
    }

    /** Delete a single track's audio file. */
    fun deleteTrackFile(filePath: String) {
        val file = File(filePath)
        if (file.exists()) file.delete()
    }

    /** Delete all files for a project. */
    fun deleteProjectFiles(projectId: Long) {
        val dir = projectDir(projectId)
        if (dir.exists()) dir.deleteRecursively()
    }
}
