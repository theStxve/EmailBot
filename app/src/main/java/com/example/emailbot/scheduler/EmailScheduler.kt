package com.example.emailbot.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.ScheduleType
import java.util.Calendar

object EmailScheduler {

    private const val REQ_CODE_BASE = 10000

    fun schedule(context: Context, task: EmailTask) {
        if (!task.isEnabled) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = buildPendingIntent(context, task.id)
        val triggerAt = nextTriggerMs(task)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, taskId))
    }

    fun nextTriggerMs(task: EmailTask): Long {
        val now = System.currentTimeMillis()
        return when (task.scheduleType) {
            ScheduleType.ONCE -> task.scheduledTimeMs

            ScheduleType.PERIODIC -> {
                val intervalMs = (task.intervalMinutes * 60_000.0).toLong().coerceAtLeast(1000L)
                var next = task.scheduledTimeMs
                while (next <= now) next += intervalMs
                next
            }

            ScheduleType.CRON -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, task.cronHour)
                    set(Calendar.MINUTE,      task.cronMinute)
                    set(Calendar.SECOND,      0)
                    set(Calendar.MILLISECOND, 0)
                }
                // If daily (cronDayOfWeek == -1) or today is the right weekday, move to today's time.
                // Otherwise advance to the next matching weekday.
                if (task.cronDayOfWeek != -1) {
                    // Calendar.DAY_OF_WEEK: 1=Sun,2=Mon,...,7=Sat
                    // Our cronDayOfWeek: 0=Mon,...,6=Sun
                    val targetCalDay = ((task.cronDayOfWeek + 1) % 7) + 1
                    val todayCalDay = cal.get(Calendar.DAY_OF_WEEK)
                    var daysToAdd = (targetCalDay - todayCalDay + 7) % 7
                    if (daysToAdd == 0 && cal.timeInMillis <= now) daysToAdd = 7
                    cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                } else {
                    if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }
        }
    }

    private fun buildPendingIntent(context: Context, taskId: Int): PendingIntent {
        val intent = Intent(context, EmailAlarmReceiver::class.java).apply {
            putExtra("task_id", taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_CODE_BASE + taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
