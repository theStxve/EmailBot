package com.example.emailbot.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Receives AlarmManager triggers and hands off to WorkManager for reliable
 * background execution (survives Doze mode, low memory pressure, etc.)
 */
class EmailAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("task_id", -1)
        if (taskId == -1) return

        val data = workDataOf("task_id" to taskId)

        val workRequest = OneTimeWorkRequestBuilder<EmailSendWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            // Retry up to 3 times with exponential backoff for network failures
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag("email_send_$taskId")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "email_send_$taskId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }
}
