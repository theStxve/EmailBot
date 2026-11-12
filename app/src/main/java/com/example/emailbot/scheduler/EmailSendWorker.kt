package com.example.emailbot.scheduler

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.emailbot.data.EmailLog
import com.example.emailbot.data.EmailStatus
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.EmailTaskRepository
import com.example.emailbot.data.ScheduleType
import com.example.emailbot.data.SmtpSettingsRepository
import com.example.emailbot.data.displayTitle
import com.example.emailbot.mail.PermanentSendException
import com.example.emailbot.mail.SmtpClient
import com.example.emailbot.widget.EmailBotWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends one email. Wi-Fi and charging conditions are handled by WorkManager constraints
 * (see [EmailScheduler.enqueueSend]); the minimum battery level is checked here.
 *
 * Network errors are retried [MAX_RETRIES] times. Errors that a retry can't fix
 * (wrong password, invalid address, missing attachment) fail immediately.
 */
class EmailSendWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private companion object {
        const val TAG = "EmailSendWorker"
        const val MAX_RETRIES = 3
    }

    private val repo = EmailTaskRepository(applicationContext)

    /** Only used for expedited work on Android 11 and older. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        Notifications.createChannels(applicationContext)
        return ForegroundInfo(Notifications.ID_SENDING, Notifications.sending(applicationContext))
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getInt(EmailScheduler.EXTRA_TASK_ID, -1)
        val manual = inputData.getBoolean(EmailScheduler.EXTRA_MANUAL, false)
        val task = repo.getById(taskId) ?: return@withContext Result.failure()

        if (!manual) {
            if (!task.isEnabled) return@withContext Result.success()
            // A one-time task that was already sent (e.g. via "send now") must never go out twice.
            if (task.scheduleType == ScheduleType.ONCE && task.status == EmailStatus.SENT) {
                return@withContext Result.success()
            }
            if (task.requireMinBattery > 0) {
                val level = batteryLevel()
                if (level < task.requireMinBattery) {
                    repo.update(taskId) {
                        it.copy(lastErrorMessage = "Wartet auf Akkustand ${task.requireMinBattery} % (aktuell $level %)")
                    }
                    return@withContext Result.retry()
                }
            }
        }

        val smtp = SmtpSettingsRepository(applicationContext).resolve(task.smtpAccountId)
            ?: return@withContext fail(
                task,
                if (task.smtpAccountId == 0) "Kein SMTP-Konto eingerichtet."
                else "Das SMTP-Konto dieser E-Mail wurde gelöscht. Bitte ein anderes auswählen."
            )

        try {
            SmtpClient.send(task, smtp)
        } catch (e: PermanentSendException) {
            return@withContext fail(task, SmtpClient.describe(e))
        } catch (e: Exception) {
            Log.w(TAG, "Send failed for task $taskId (attempt ${runAttemptCount + 1})", e)
            val reason = SmtpClient.describe(e)
            if (runAttemptCount < MAX_RETRIES) {
                repo.update(taskId) {
                    it.copy(lastErrorMessage = "Versuch ${runAttemptCount + 1} fehlgeschlagen, neuer Versuch folgt: $reason")
                }
                return@withContext Result.retry()
            }
            return@withContext fail(task, reason)
        }

        succeed(task)
        Result.success()
    }

    private suspend fun succeed(task: EmailTask) {
        val now = System.currentTimeMillis()
        val once = task.scheduleType == ScheduleType.ONCE
        repo.update(task.id) {
            it.copy(
                status = if (once) EmailStatus.SENT else EmailStatus.PENDING,
                isEnabled = if (once) false else it.isEnabled,
                lastSentTimeMs = now,
                lastErrorMessage = ""
            )
        }
        if (once) EmailScheduler.cancel(applicationContext, task.id)
        repo.addLog(task.toLog(EmailStatus.SENT, now))
        EmailBotWidgetUpdater.update(applicationContext)
        Log.i(TAG, "Email sent for task ${task.id}")
    }

    private suspend fun fail(task: EmailTask, error: String): Result {
        val now = System.currentTimeMillis()
        repo.update(task.id) { it.copy(status = EmailStatus.FAILED, lastErrorMessage = error) }
        repo.addLog(task.toLog(EmailStatus.FAILED, now, error))
        Notifications.createChannels(applicationContext)
        Notifications.showFailure(applicationContext, task.id, task.displayTitle, error)
        EmailBotWidgetUpdater.update(applicationContext)
        return Result.failure()
    }

    private fun EmailTask.toLog(status: EmailStatus, time: Long, error: String = "") = EmailLog(
        taskId = id,
        taskLabel = label,
        recipient = recipientTo,
        subject = subject,
        sentTimeMs = time,
        status = status,
        errorMessage = error
    )

    private fun batteryLevel(): Int {
        val intent = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) level * 100 / scale else 100
    }
}
