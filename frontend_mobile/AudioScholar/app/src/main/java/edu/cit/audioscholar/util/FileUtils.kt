package edu.cit.audioscholar.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            if (fileName == null) {
                fileName = uri.path?.substringAfterLast('/')
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "Error getting file name from URI: $uri", e)
            fileName = null
        }
        return fileName?.replace("/", "_")?.replace("\\", "_")
    }

    fun getFileNameWithoutExtension(fullFileName: String?): String {
        if (fullFileName.isNullOrBlank()) return "Recording"
        val lastDotIndex = fullFileName.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            fullFileName.substring(0, lastDotIndex)
        } else {
            fullFileName
        }
    }
} 