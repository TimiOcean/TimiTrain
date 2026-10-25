package de.timitrain.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object ExercisePhotos {
    private const val DIRECTORY = "exercise-photos"

    fun file(context: Context, name: String): File {
        return File(directory(context), name)
    }

    fun copyIn(context: Context, source: Uri): String? {
        val name = "${UUID.randomUUID()}.jpg"
        val dest = file(context, name)
        val copied = context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (copied == null || copied <= 0L) {
            dest.delete()
            return null
        }
        return name
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        file(context, name).delete()
    }

    fun cameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun directory(context: Context): File {
        return File(context.filesDir, DIRECTORY).apply { mkdirs() }
    }
}
