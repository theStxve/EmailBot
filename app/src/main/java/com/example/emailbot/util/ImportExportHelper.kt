package com.example.emailbot.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.emailbot.data.EmailTask
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object ImportExportHelper {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportTasks(context: Context, tasks: List<EmailTask>): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "exports")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            cacheDir.listFiles()?.forEach { it.delete() }

            val file = File(cacheDir, "emailbot_backup.json")
            val jsonString = json.encodeToString(ListSerializer(EmailTask.serializer()), tasks)
            file.writeText(jsonString, Charsets.UTF_8)

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun importTasks(context: Context, uri: Uri): List<EmailTask>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader()?.use { it.readText() } ?: return null
            json.decodeFromString(ListSerializer(EmailTask.serializer()), jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
