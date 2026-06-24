package com.example.emailbot.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.emailbot.R
import com.example.emailbot.data.EmailTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the scheduling engine alive even on aggressive
 * battery-saving devices (Samsung, Xiaomi, Huawei etc.).
 *
 * It runs a lightweight heartbeat loop every 60 seconds to:
 * 1. Re-verify all active alarms are still registered (AlarmManager can lose
 *    alarms on some OEM ROMs after power-save mode kicks in).
 * 2. Provide a persistent notification so Android never kills this process.
 */
class EmailSchedulerService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        const val CHANNEL_ID  = "emailbot_scheduler"
        const val NOTIF_ID    = 1001

        fun start(context: Context) {
            val intent = Intent(context, EmailSchedulerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EmailSchedulerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("E-Mail Scheduler aktiv"))
        startHeartbeat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart automatically if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Every 15 seconds: update the notification with the current number of active schedules.
     * We do NOT reschedule alarms here to avoid hitting Android's rate limits for exact alarms.
     */
    private fun startHeartbeat() {
        scope.launch {
            val repo = EmailTaskRepository(applicationContext)
            while (true) {
                try {
                    val active = repo.getActiveTasks()
                    updateNotification("${active.size} E-Mail-Zeitplan(e) aktiv")
                } catch (_: Exception) { }
                delay(15_000L)
            }
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EmailBot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "E-Mail Scheduler",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hält den E-Mail-Zeitplan im Hintergrund aktiv"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
