package com.example.emailbot.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.emailbot.data.EmailTask
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup as a JSON list of tasks. SMTP accounts and passwords are deliberately left out.
 * Attachments are too: on import, tasks whose attachment file is missing lose the attachment.
 */
object ImportExportHelper {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportTasks(context: Context, tasks: List<EmailTask>): Uri? = runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val file = File(dir, "emailbot-backup-$date.json")
        file.writeText(json.encodeToString(ListSerializer(EmailTask.serializer()), tasks), Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    fun importTasks(context: Context, uri: Uri): List<EmailTask>? = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return null
        json.decodeFromString(ListSerializer(EmailTask.serializer()), text)
    }.getOrNull()
}
