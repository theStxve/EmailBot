package com.example.emailbot.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.emailbot.MainActivity
import com.example.emailbot.R

/** Notification channels and the notifications the background parts of the app post. */
object Notifications {

    const val CHANNEL_SCHEDULER = "emailbot_scheduler"
    const val CHANNEL_SENDING   = "emailbot_worker"
    const val CHANNEL_FAILURES  = "emailbot_failures"

    const val ID_SCHEDULER = 1001
    const val ID_SENDING   = 2002
    private const val ID_FAILURE_BASE = 3000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SCHEDULER, "Hintergrund-Status", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Zeigt an, wie viele Zeitpläne aktiv sind"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_SENDING, "Versand läuft", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Wird kurz angezeigt, während eine E-Mail gesendet wird"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_FAILURES, "Versandfehler", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Meldet E-Mails, die nicht gesendet werden konnten"
                }
            )
        )
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun scheduler(context: Context, activeCount: Int) =
        NotificationCompat.Builder(context, CHANNEL_SCHEDULER)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setContentTitle("EmailBot ist bereit")
            .setContentText(if (activeCount == 1) "1 Zeitplan aktiv" else "$activeCount Zeitpläne aktiv")
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun sending(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_SENDING)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setContentTitle("E-Mail wird gesendet …")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun showFailure(context: Context, taskId: Int, title: String, error: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL_FAILURES)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setContentTitle("Nicht gesendet: $title")
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_FAILURE_BASE + taskId, notification)
    }
}
