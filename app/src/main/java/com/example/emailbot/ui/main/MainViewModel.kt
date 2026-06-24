package com.example.emailbot.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.Constraints
import androidx.work.OutOfQuotaPolicy
import com.example.emailbot.data.EmailStatus
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.EmailTaskRepository
import com.example.emailbot.data.EmailLog
import com.example.emailbot.scheduler.EmailScheduler
import com.example.emailbot.scheduler.EmailSendWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.emailbot.util.AttachmentHelper

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = EmailTaskRepository(application)

    val tasks: StateFlow<List<EmailTask>> = repo.tasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<EmailLog>> = repo.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearLogs() {
        viewModelScope.launch {
            repo.clearLogs()
        }
    }

    fun addOrUpdateTask(task: EmailTask) {
        viewModelScope.launch {
            val saved = repo.save(task)
            if (saved.isEnabled) EmailScheduler.schedule(getApplication(), saved)
            com.example.emailbot.widget.EmailBotWidgetUpdater.update(getApplication())
        }
    }

    fun toggleEnabled(task: EmailTask) {
        viewModelScope.launch {
            val newEnabled = !task.isEnabled
            repo.setEnabled(task.id, newEnabled)
            if (newEnabled) {
                EmailScheduler.schedule(getApplication(), task.copy(isEnabled = true))
            } else {
                EmailScheduler.cancel(getApplication(), task.id)
            }
            com.example.emailbot.widget.EmailBotWidgetUpdater.update(getApplication())
        }
    }

    fun deleteTask(task: EmailTask) {
        viewModelScope.launch {
            EmailScheduler.cancel(getApplication(), task.id)
            if (task.attachmentPath.isNotBlank()) {
                AttachmentHelper.deleteAttachment(task.attachmentPath)
            }
            repo.delete(task.id)
            com.example.emailbot.widget.EmailBotWidgetUpdater.update(getApplication())
        }
    }

    fun retryTask(task: EmailTask) {
        viewModelScope.launch {
            val reset = task.copy(status = EmailStatus.PENDING, lastErrorMessage = "")
            repo.save(reset)
            EmailScheduler.schedule(getApplication(), reset)
            com.example.emailbot.widget.EmailBotWidgetUpdater.update(getApplication())
        }
    }

    /** Bypass AlarmManager and send immediately — useful for testing SMTP settings. */
    fun sendNow(task: EmailTask) {
        viewModelScope.launch {
            // Reset to PENDING so the worker picks it up
            val reset = task.copy(status = EmailStatus.PENDING, lastErrorMessage = "")
            repo.save(reset)

            val data = workDataOf("task_id" to reset.id)
            val request = OneTimeWorkRequestBuilder<EmailSendWorker>()
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("send_now_${reset.id}")
                .build()

            WorkManager.getInstance(getApplication())
                .enqueueUniqueWork(
                    "send_now_${reset.id}",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            com.example.emailbot.widget.EmailBotWidgetUpdater.update(getApplication())
        }
    }

    fun importTasks(imported: List<EmailTask>) {
        viewModelScope.launch {
            imported.forEach { task ->
                val cleanTask = task.copy(
                    id = 0,
                    status = EmailStatus.PENDING,
                    lastErrorMessage = "",
                    lastSentTimeMs = 0L
                )
                val saved = repo.save(cleanTask)
                if (saved.isEnabled) {
                    EmailScheduler.schedule(getApplication(), saved)
                }
            }
            com.example.emailbot.widget.EmailBotWidgetUpdater.update(getApplication())
        }
    }
}
