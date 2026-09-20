package com.sb.attendance.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves selfies and enrolment shots to app-internal storage. Internal storage keeps the
 * images private to the app and needs no storage permission on any API level.
 */
class SelfieStorage(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "selfies").apply { if (!exists()) mkdirs() }

    suspend fun save(bitmap: Bitmap, prefix: String): String = withContext(Dispatchers.IO) {
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        file.absolutePath
    }
}
