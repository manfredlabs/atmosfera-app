package com.example.atmosfera.audio

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies user-uploaded audio files to internal storage for playback.
 * ExoPlayer handles looping natively — no processing needed.
 */
class PadProcessor(private val context: Context) {

    /**
     * Copy an audio file to internal storage.
     * @param sourceUri URI of the source audio file
     * @param outputFile destination file
     * @return true if successful
     */
    fun process(sourceUri: Uri, outputFile: File): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
