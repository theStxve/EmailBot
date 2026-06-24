package com.example.emailbot.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object AttachmentHelper {

    /**
     * Copies the content of a Uri to a secure app-private file under filesDir/attachments/
     * and returns a Pair of (absolutePath, originalFilename).
     */
    fun copyToInternalStorage(context: Context, uri: Uri): Pair<String, String>? {
        return try {
            val contentResolver = context.contentResolver
            val originalName = getFileName(context, uri) ?: "attachment_${UUID.randomUUID()}"

            // Create attachments directory if it doesn't exist
            val dir = File(context.filesDir, "attachments")
            if (!dir.exists()) dir.mkdirs()

            // Create a unique file on disk
            val fileExtension = File(originalName).extension
            val suffix = if (fileExtension.isNotBlank()) ".$fileExtension" else ""
            val destFile = File(dir, "attach_${UUID.randomUUID()}$suffix")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            Pair(destFile.absolutePath, originalName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a local attachment file if it exists.
     */
    fun deleteAttachment(path: String) {
        if (path.isBlank()) return
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }
}
