package com.example.emailbot.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.MIN_INTERVAL_MINUTES
import com.example.emailbot.data.ScheduleType
import com.example.emailbot.data.isActive
import java.util.Calendar
import java.util.concurrent.TimeUnit

object EmailScheduler {

    private const val REQ_CODE_BASE = 10000
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_MANUAL  = "manual"

    fun workName(taskId: Int) = "email_send_$taskId"

    /** Registers the alarm for the next run, or removes it if the task has nothing left to send. */
    fun schedule(context: Context, task: EmailTask) {
        if (!task.isActive) {
            cancel(context, task.id)
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = buildPendingIntent(context, task.id)
        val triggerAt = nextTriggerMs(task)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Without the permission we can only ask for an inexact alarm. It may fire a few minutes late.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, taskId: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(buildPendingIntent(context, taskId))
    }

    /**
     * Next time the task should fire, strictly after [now] for recurring tasks.
     * One-time tasks return their fixed time even if it's in the past (they fire right away).
     */
    fun nextTriggerMs(task: EmailTask, now: Long = System.currentTimeMillis()): Long =
        when (task.scheduleType) {
            ScheduleType.ONCE -> task.scheduledTimeMs

            ScheduleType.PERIODIC -> {
                val intervalMs = (task.intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES) * 60_000.0).toLong()
                val start = task.scheduledTimeMs
                if (start > now) start
                else start + ((now - start) / intervalMs + 1) * intervalMs
            }

            ScheduleType.CRON -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, task.cronHour)
                    set(Calendar.MINUTE, task.cronMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (task.cronDayOfWeek == -1) {
                    if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
                } else {
                    // Calendar.DAY_OF_WEEK: 1=Sun … 7=Sat. Ours: 0=Mon … 6=Sun.
                    val targetCalDay = (task.cronDayOfWeek + 1) % 7 + 1
                    var daysToAdd = (targetCalDay - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    if (daysToAdd == 0 && cal.timeInMillis <= now) daysToAdd = 7
                    cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                }
                cal.timeInMillis
            }
        }

    /**
     * Hands the send over to WorkManager.
     *
     * Scheduled sends honour the task's Wi-Fi/charging conditions as WorkManager constraints,
     * so the job simply waits until they are met. A [manual] send ("send now") ignores them.
     *
     * Returns false if a send for this task is already running.
     */
    fun enqueueSend(context: Context, task: EmailTask, manual: Boolean): Boolean {
        val wm = WorkManager.getInstance(context)
        val name = workName(task.id)

        val requireCharging = !manual && task.requireCharging
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (!manual && task.requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(requireCharging)
            .build()

        val request = OneTimeWorkRequestBuilder<EmailSendWorker>()
            .setInputData(workDataOf(EXTRA_TASK_ID to task.id, EXTRA_MANUAL to manual))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(name)
            .apply {
                // Expedited jobs only support network constraints.
                if (!requireCharging) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            .build()

        return if (manual) {
            val running = runCatching {
                wm.getWorkInfosForUniqueWork(name).get().any { it.state == WorkInfo.State.RUNNING }
            }.getOrDefault(false)
            if (running) return false
            wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
            true
        } else {
            // KEEP: never interrupt a send in progress or a send waiting for its conditions.
            wm.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
            true
        }
    }

    private fun buildPendingIntent(context: Context, taskId: Int): PendingIntent {
        val intent = Intent(context, EmailAlarmReceiver::class.java).putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            REQ_CODE_BASE + taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
