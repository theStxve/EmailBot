package com.example.emailbot.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.taskDataStore by preferencesDataStore(name = "email_tasks")
private val TASKS_KEY = stringPreferencesKey("tasks_json")
private val LOGS_KEY  = stringPreferencesKey("logs_json")

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults   = true
}

class EmailTaskRepository(private val context: Context) {

    /** Live Flow of all tasks, newest-scheduled first */
    val tasks: Flow<List<EmailTask>> = context.taskDataStore.data.map { prefs ->
        val raw = prefs[TASKS_KEY] ?: return@map emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(EmailTask.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    /** Live Flow of all audit logs, newest first */
    val logs: Flow<List<EmailLog>> = context.taskDataStore.data.map { prefs ->
        val raw = prefs[LOGS_KEY] ?: return@map emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(EmailLog.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    /** Snapshot for one-shot reads (e.g. from a Worker) */
    suspend fun getAll(): List<EmailTask> = tasks.first()

    suspend fun getById(id: Int): EmailTask? = getAll().find { it.id == id }

    /** Returns the saved task with its final ID */
    suspend fun save(task: EmailTask): EmailTask {
        var saved = task
        context.taskDataStore.edit { prefs ->
            val current = prefs[TASKS_KEY]
                ?.let { runCatching { json.decodeFromString(ListSerializer(EmailTask.serializer()), it) }.getOrDefault(emptyList()) }
                ?: emptyList()

            val newList = if (task.id == 0) {
                // New task — assign next ID
                val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
                saved = task.copy(id = nextId)
                current + saved
            } else {
                // Update existing
                current.map { if (it.id == task.id) task else it }
            }
            prefs[TASKS_KEY] = json.encodeToString(ListSerializer(EmailTask.serializer()), newList)
        }
        return saved
    }

    suspend fun delete(id: Int) {
        context.taskDataStore.edit { prefs ->
            val current = prefs[TASKS_KEY]
                ?.let { runCatching { json.decodeFromString(ListSerializer(EmailTask.serializer()), it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            prefs[TASKS_KEY] = json.encodeToString(ListSerializer(EmailTask.serializer()), current.filter { it.id != id })
        }
    }

    suspend fun updateStatus(id: Int, status: EmailStatus, timeMs: Long, error: String) {
        val task = getById(id) ?: return
        save(task.copy(status = status, lastSentTimeMs = timeMs, lastErrorMessage = error))
    }

    suspend fun setEnabled(id: Int, enabled: Boolean) {
        val task = getById(id) ?: return
        save(task.copy(isEnabled = enabled))
    }

    suspend fun getActiveTasks(): List<EmailTask> =
        getAll().filter { it.isEnabled && it.status != EmailStatus.PAUSED && it.status != EmailStatus.SENT }

    suspend fun addLog(log: EmailLog) {
        context.taskDataStore.edit { prefs ->
            val current = prefs[LOGS_KEY]
                ?.let { runCatching { json.decodeFromString(ListSerializer(EmailLog.serializer()), it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            // Keep up to 200 logs to prevent infinite storage growth
            val newList = (listOf(log) + current).take(200)
            prefs[LOGS_KEY] = json.encodeToString(ListSerializer(EmailLog.serializer()), newList)
        }
    }

    suspend fun clearLogs() {
        context.taskDataStore.edit { prefs ->
            prefs.remove(LOGS_KEY)
        }
    }
}
