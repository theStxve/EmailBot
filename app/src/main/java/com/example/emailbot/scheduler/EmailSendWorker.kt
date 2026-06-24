package com.example.emailbot.scheduler

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.emailbot.data.EmailLog
import com.example.emailbot.data.EmailStatus
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.EmailTaskRepository
import com.example.emailbot.data.ScheduleType
import com.example.emailbot.data.SecurityType
import com.example.emailbot.data.SmtpSettings
import com.example.emailbot.data.SmtpSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class EmailSendWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val tag = "EmailSendWorker"

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channelId = "emailbot_worker"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "E-Mail Versand",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("EmailBot")
            .setContentText("E-Mail wird gesendet...")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setOngoing(true)
            .build()

        return ForegroundInfo(2002, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getInt("task_id", -1)
        if (taskId == -1) return@withContext Result.failure()

        val repo = EmailTaskRepository(applicationContext)
        val task = repo.getById(taskId) ?: return@withContext Result.failure()

        if (!task.isEnabled) return@withContext Result.success()

        // Guard: if a one-time task has already been sent, don't send it again.
        // This protects against double-execution from boot restores or WorkManager retries.
        if (task.scheduleType == ScheduleType.ONCE && task.status == EmailStatus.SENT) {
            Log.i(tag, "Task $taskId already sent (ONCE), skipping.")
            return@withContext Result.success()
        }

        val smtpRepo = SmtpSettingsRepository(applicationContext)
        val smtp = smtpRepo.getById(task.smtpAccountId) ?: smtpRepo.getDefault()

        // --- Smart Trigger Checks ---
        // Check Wi-Fi requirement
        if (task.requireWifi && !isWifiConnected(applicationContext)) {
            val msg = "Verschoben: Warte auf WLAN-Verbindung..."
            Log.i(tag, "Task $taskId deferred: $msg")
            repo.updateStatus(taskId, EmailStatus.PENDING, System.currentTimeMillis(), msg)
            return@withContext Result.retry()
        }

        // Check charging requirement
        val (batteryLevel, isCharging) = getBatteryInfo(applicationContext)
        if (task.requireCharging && !isCharging) {
            val msg = "Verschoben: Warte auf Stromquelle (Gerät nicht geladen)..."
            Log.i(tag, "Task $taskId deferred: $msg")
            repo.updateStatus(taskId, EmailStatus.PENDING, System.currentTimeMillis(), msg)
            return@withContext Result.retry()
        }

        // Check minimum battery level
        if (task.requireMinBattery > 0 && batteryLevel < task.requireMinBattery) {
            val msg = "Verschoben: Warte auf Akkustand (aktuell $batteryLevel% < ${task.requireMinBattery}%)..."
            Log.i(tag, "Task $taskId deferred: $msg")
            repo.updateStatus(taskId, EmailStatus.PENDING, System.currentTimeMillis(), msg)
            return@withContext Result.retry()
        }

        // Mark task as currently executing so it is clear that the worker has started
        repo.updateStatus(taskId, EmailStatus.PENDING, System.currentTimeMillis(), "Verbindung zum SMTP-Server wird hergestellt...")

        return@withContext try {
            sendEmail(task, smtp)

            val now = System.currentTimeMillis()
            val newStatus = if (task.scheduleType == ScheduleType.ONCE) EmailStatus.SENT else EmailStatus.PENDING
            repo.updateStatus(taskId, newStatus, now, "")

            // Disable ONCE tasks after successful send so they can never fire again
            // (e.g. via boot restore or a stale WorkManager retry)
            if (task.scheduleType == ScheduleType.ONCE) {
                repo.save(task.copy(status = EmailStatus.SENT, isEnabled = false, lastSentTimeMs = now))
                EmailScheduler.cancel(applicationContext, taskId)
            }

            // Log success event
            repo.addLog(
                EmailLog(
                    taskId = task.id,
                    taskLabel = task.label,
                    recipient = task.recipientTo,
                    subject = task.subject,
                    sentTimeMs = now,
                    status = EmailStatus.SENT
                )
            )

            // Re-schedule next occurrence for PERIODIC and CRON tasks
            if ((task.scheduleType == ScheduleType.PERIODIC || task.scheduleType == ScheduleType.CRON) && task.isEnabled) {
                val updated = task.copy(lastSentTimeMs = now)
                repo.save(updated)
                EmailScheduler.schedule(applicationContext, updated)
            }

            Log.i(tag, "Email sent for task $taskId")
            com.example.emailbot.widget.EmailBotWidgetUpdater.update(applicationContext)
            Result.success()
        } catch (e: MessagingException) {
            Log.e(tag, "SMTP error for task $taskId: ${e.message}", e)
            if (runAttemptCount < 3) {
                val nextAttempt = runAttemptCount + 1
                repo.updateStatus(taskId, EmailStatus.PENDING, System.currentTimeMillis(), "Verbindung fehlgeschlagen, erneuter Versuch ($nextAttempt/3): ${e.message}")
                Result.retry()
            } else {
                val errorMsg = e.message ?: "SMTP-Fehler nach 3 Versuchen"
                repo.updateStatus(taskId, EmailStatus.FAILED, System.currentTimeMillis(), errorMsg)
                repo.addLog(
                    EmailLog(
                        taskId = task.id,
                        taskLabel = task.label,
                        recipient = task.recipientTo,
                        subject = task.subject,
                        sentTimeMs = System.currentTimeMillis(),
                        status = EmailStatus.FAILED,
                        errorMessage = errorMsg
                    )
                )
                com.example.emailbot.widget.EmailBotWidgetUpdater.update(applicationContext)
                Result.failure()
            }
        } catch (e: Throwable) {
            Log.e(tag, "Error for task $taskId: ${e.message}", e)
            val errorMsg = e.toString()
            repo.updateStatus(taskId, EmailStatus.FAILED, System.currentTimeMillis(), errorMsg)
            repo.addLog(
                EmailLog(
                    taskId = task.id,
                    taskLabel = task.label,
                    recipient = task.recipientTo,
                    subject = task.subject,
                    sentTimeMs = System.currentTimeMillis(),
                    status = EmailStatus.FAILED,
                    errorMessage = errorMsg
                )
            )
            com.example.emailbot.widget.EmailBotWidgetUpdater.update(applicationContext)
            Result.failure()
        }
    }

    private fun sendEmail(task: EmailTask, smtp: SmtpSettings) {
        val props = Properties().apply {
            put("mail.smtp.auth",              "true")
            put("mail.smtp.host",              smtp.host)
            put("mail.smtp.port",              smtp.port.toString())
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout",           "15000")
            put("mail.smtp.writetimeout",      "15000")
            when (smtp.security) {
                SecurityType.STARTTLS -> {
                    put("mail.smtp.starttls.enable",   "true")
                    put("mail.smtp.starttls.required", "true")
                }
                SecurityType.SSL_TLS -> {
                    put("mail.smtp.ssl.enable",              "true")
                    put("mail.smtp.ssl.checkserveridentity", "true")
                    put("mail.smtp.socketFactory.port",      smtp.port.toString())
                    put("mail.smtp.socketFactory.class",     "javax.net.ssl.SSLSocketFactory")
                }
                SecurityType.NONE -> Unit
            }
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(smtp.username, smtp.password)
        })

        val fromAddr = if (smtp.fromName.isNotBlank())
            InternetAddress(smtp.username, smtp.fromName)
        else InternetAddress(smtp.username)

        val message = MimeMessage(session).apply {
            setFrom(fromAddr)
            setSubject(task.subject, "UTF-8")
            setRecipients(Message.RecipientType.TO,  InternetAddress.parse(task.recipientTo))
            if (task.recipientCc.isNotBlank())
                setRecipients(Message.RecipientType.CC,  InternetAddress.parse(task.recipientCc))
            if (task.recipientBcc.isNotBlank())
                setRecipients(Message.RecipientType.BCC, InternetAddress.parse(task.recipientBcc))
            sentDate = Date()
        }

        // Build multipart if there is an attachment, otherwise send plain text
        val attachmentFile = if (task.attachmentPath.isNotBlank()) File(task.attachmentPath) else null
        if (attachmentFile != null && attachmentFile.exists()) {
            val multipart: Multipart = MimeMultipart()

            // Text body part
            val textPart = MimeBodyPart().apply {
                setText(task.body, "UTF-8")
            }
            multipart.addBodyPart(textPart)

            // Attachment body part
            val attachPart = MimeBodyPart().apply {
                val source = FileDataSource(attachmentFile)
                dataHandler = DataHandler(source)
                fileName = task.attachmentName.ifBlank { attachmentFile.name }
            }
            multipart.addBodyPart(attachPart)

            message.setContent(multipart)
        } else {
            message.setText(task.body, "UTF-8")
        }

        Transport.send(message)
    }

    // --- Helper: Wi-Fi connectivity check ---
    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    // --- Helper: Battery level and charging status ---
    // Returns Pair(level: Int 0-100, isCharging: Boolean)
    private fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
        val intent: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let {
            context.registerReceiver(null, it)
        }
        val level  = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL,  -1) ?: -1
        val scale  = intent?.getIntExtra(BatteryManager.EXTRA_SCALE,  100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS,  -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL
        return Pair(batteryPct, charging)
    }
}
