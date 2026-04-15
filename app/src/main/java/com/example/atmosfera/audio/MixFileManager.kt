package com.example.atmosfera.audio

import android.content.Context
import android.net.Uri
import com.manfredlabs.atmosfera.audio.AudioFileStorage
import java.io.File

class MixFileManager(private val context: Context) : AudioFileStorage {

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

    override fun copyAudioToMixStorage(projectId: Long, trackId: Long, sourceUri: String, displayName: String): String =
        copyAudioToStorage(projectId, trackId, Uri.parse(sourceUri), displayName)

    override fun deleteTrackFile(filePath: String) {
        val file = File(filePath)
        if (file.exists()) file.delete()
    }

    override fun deleteProjectFiles(projectId: Long) {
        val dir = projectDir(projectId)
        if (dir.exists()) dir.deleteRecursively()
    }

    override fun copyPadAudio(sourceUri: String, destPath: String): Boolean {
        val dest = File(destPath)
        return try {
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.exists() && dest.length() > 0
        } catch (e: Exception) {
            false
        }
    }
}
