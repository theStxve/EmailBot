package com.example.emailbot.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
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

private const val MAX_LOGS = 200

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults   = true
}

private val taskListSerializer = ListSerializer(EmailTask.serializer())
private val logListSerializer  = ListSerializer(EmailLog.serializer())

private fun decodeTasks(raw: String?): List<EmailTask> =
    raw?.let { runCatching { json.decodeFromString(taskListSerializer, it) }.getOrNull() } ?: emptyList()

private fun decodeLogs(raw: String?): List<EmailLog> =
    raw?.let { runCatching { json.decodeFromString(logListSerializer, it) }.getOrNull() } ?: emptyList()

/**
 * Stores tasks and the send log as JSON in a Preferences DataStore.
 *
 * Every write goes through [editTasks], which reads and writes inside one DataStore
 * transaction. That way the worker and the UI can't overwrite each other's changes.
 */
class EmailTaskRepository(context: Context) {

    private val dataStore = context.applicationContext.taskDataStore

    val tasks: Flow<List<EmailTask>> = dataStore.data.map { decodeTasks(it[TASKS_KEY]) }

    /** Newest first. */
    val logs: Flow<List<EmailLog>> = dataStore.data.map { decodeLogs(it[LOGS_KEY]) }

    suspend fun getAll(): List<EmailTask> = tasks.first()

    suspend fun getById(id: Int): EmailTask? = getAll().find { it.id == id }

    suspend fun getActiveTasks(): List<EmailTask> = getAll().filter { it.isActive }

    private suspend fun editTasks(block: (List<EmailTask>) -> List<EmailTask>) {
        dataStore.edit { prefs -> prefs.writeTasks(block(decodeTasks(prefs[TASKS_KEY]))) }
    }

    private fun MutablePreferences.writeTasks(list: List<EmailTask>) {
        this[TASKS_KEY] = json.encodeToString(taskListSerializer, list)
    }

    /** Inserts (id == 0) or replaces a task. Returns the task with its final id. */
    suspend fun save(task: EmailTask): EmailTask {
        var saved = task
        editTasks { current ->
            if (task.id == 0) {
                saved = task.copy(id = (current.maxOfOrNull { it.id } ?: 0) + 1)
                current + saved
            } else {
                current.map { if (it.id == task.id) task else it }
            }
        }
        return saved
    }

    /** Atomically applies [transform] to the stored task. Returns the updated task, or null if it no longer exists. */
    suspend fun update(id: Int, transform: (EmailTask) -> EmailTask): EmailTask? {
        var result: EmailTask? = null
        editTasks { current ->
            current.map { if (it.id == id) transform(it).also { t -> result = t } else it }
        }
        return result
    }

    suspend fun delete(id: Int) = editTasks { current -> current.filter { it.id != id } }

    /** Points every task that uses [accountId] to the default account instead. Returns how many were changed. */
    suspend fun reassignAccount(accountId: Int): Int {
        var count = 0
        editTasks { current ->
            current.map {
                if (it.smtpAccountId == accountId) { count++; it.copy(smtpAccountId = 0) } else it
            }
        }
        return count
    }

    suspend fun addLog(log: EmailLog) {
        dataStore.edit { prefs ->
            val newList = (listOf(log) + decodeLogs(prefs[LOGS_KEY])).take(MAX_LOGS)
            prefs[LOGS_KEY] = json.encodeToString(logListSerializer, newList)
        }
    }

    suspend fun clearLogs() {
        dataStore.edit { it.remove(LOGS_KEY) }
    }
}
