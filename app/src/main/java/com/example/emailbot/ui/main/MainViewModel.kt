package com.example.emailbot.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.emailbot.data.EmailLog
import com.example.emailbot.data.EmailStatus
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.EmailTaskRepository
import com.example.emailbot.data.ScheduleType
import com.example.emailbot.data.SmtpSettings
import com.example.emailbot.data.SmtpSettingsRepository
import com.example.emailbot.scheduler.EmailScheduler
import com.example.emailbot.scheduler.EmailSchedulerService
import com.example.emailbot.util.AttachmentHelper
import com.example.emailbot.util.ImportExportHelper
import com.example.emailbot.widget.EmailBotWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private val repo = EmailTaskRepository(application)
    private val smtpRepo = SmtpSettingsRepository(application)

    /** Null while loading, so the UI doesn't flash the empty state. */
    val tasks: StateFlow<List<EmailTask>?> = repo.tasks
        .map { list -> list.sortedWith(taskOrder) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val logs: StateFlow<List<EmailLog>> = repo.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<SmtpSettings>?> = smtpRepo.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            smtpRepo.encryptLegacyPasswords()
            EmailSchedulerService.sync(app)
        }
    }

    private fun afterChange() {
        viewModelScope.launch(Dispatchers.IO) {
            EmailSchedulerService.sync(app)
            EmailBotWidgetUpdater.update(app)
        }
    }

    fun saveTask(task: EmailTask) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = repo.save(task)
            EmailScheduler.schedule(app, saved)
            afterChange()
            _messages.send(if (task.id == 0) "E-Mail geplant" else "Änderungen gespeichert")
        }
    }

    fun toggleEnabled(task: EmailTask) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repo.update(task.id) {
                val enable = !it.isEnabled
                // Re-enabling a failed task gives it a fresh start.
                it.copy(
                    isEnabled = enable,
                    status = if (enable && it.status == EmailStatus.FAILED) EmailStatus.PENDING else it.status,
                    lastErrorMessage = if (enable) "" else it.lastErrorMessage
                )
            } ?: return@launch
            EmailScheduler.schedule(app, updated)
            if (!updated.isEnabled) WorkManager.getInstance(app).cancelUniqueWork(EmailScheduler.workName(task.id))
            afterChange()
        }
    }

    fun deleteTask(task: EmailTask) {
        viewModelScope.launch(Dispatchers.IO) {
            EmailScheduler.cancel(app, task.id)
            WorkManager.getInstance(app).cancelUniqueWork(EmailScheduler.workName(task.id))
            AttachmentHelper.deleteAttachment(task.attachmentPath)
            repo.delete(task.id)
            afterChange()
            _messages.send("„${task.label.ifBlank { task.subject }}“ gelöscht")
        }
    }

    /** Sends right away, ignoring Wi-Fi/charging/battery conditions. Also used for "retry". */
    fun sendNow(task: EmailTask) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.update(task.id) {
                it.copy(lastErrorMessage = "", status = if (it.status == EmailStatus.FAILED) EmailStatus.PENDING else it.status)
            }
            val started = EmailScheduler.enqueueSend(app, task, manual = true)
            _messages.send(if (started) "Wird gesendet …" else "Diese E-Mail wird gerade schon gesendet")
        }
    }

    fun clearLogs() {
        viewModelScope.launch { repo.clearLogs() }
    }

    fun exportTasks(onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { ImportExportHelper.exportTasks(app, repo.getAll()) }
            if (uri != null) onReady(uri) else _messages.send("Export fehlgeschlagen")
        }
    }

    /**
     * Imports tasks from a backup. To avoid surprise sends, one-time emails that were already
     * sent are skipped and those whose time has passed are imported switched off.
     */
    fun importTasks(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = ImportExportHelper.importTasks(app, uri)
            if (imported == null) {
                _messages.send("Import fehlgeschlagen: keine gültige EmailBot-Sicherung")
                return@launch
            }
            val now = System.currentTimeMillis()
            var added = 0
            var missingAttachments = 0
            imported.forEach { task ->
                if (task.scheduleType == ScheduleType.ONCE && task.status == EmailStatus.SENT) return@forEach
                val attachmentOk = task.attachmentPath.isBlank() || File(task.attachmentPath).exists()
                if (!attachmentOk) missingAttachments++
                val pastDue = task.scheduleType == ScheduleType.ONCE && task.scheduledTimeMs < now
                val saved = repo.save(
                    task.copy(
                        id = 0,
                        status = EmailStatus.PENDING,
                        isEnabled = task.isEnabled && !pastDue,
                        lastErrorMessage = "",
                        lastSentTimeMs = 0L,
                        smtpAccountId = 0, // account ids from another device mean nothing here
                        attachmentPath = if (attachmentOk) task.attachmentPath else "",
                        attachmentName = if (attachmentOk) task.attachmentName else ""
                    )
                )
                EmailScheduler.schedule(app, saved)
                added++
            }
            afterChange()
            _messages.send(
                buildString {
                    append(if (added == 1) "1 E-Mail importiert" else "$added E-Mails importiert")
                    if (missingAttachments > 0) append(" · $missingAttachments Anhänge fehlen")
                }
            )
        }
    }

    private companion object {
        /** Active tasks first, by next send time; then paused; then finished one-time tasks. */
        val taskOrder = compareBy<EmailTask>(
            { if (it.isEnabled) 0 else if (it.status == EmailStatus.SENT) 2 else 1 },
            { if (it.isEnabled) EmailScheduler.nextTriggerMs(it) else -it.lastSentTimeMs }
        )
    }
}
