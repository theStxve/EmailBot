package com.example.emailbot.data

import kotlinx.serialization.Serializable

enum class ScheduleType { ONCE, PERIODIC, CRON }
enum class EmailStatus  { PENDING, SENT, FAILED, PAUSED }
enum class SecurityType { SSL_TLS, STARTTLS, NONE }

@Serializable
data class EmailTask(
    val id: Int = 0,

    val recipientTo: String  = "",
    val recipientCc: String  = "",
    val recipientBcc: String = "",

    val subject: String = "",
    val body: String    = "",

    val scheduleType: ScheduleType = ScheduleType.ONCE,
    val scheduledTimeMs: Long      = 0L,
    val intervalMinutes: Double    = 60.0,

    // Cron fields (-1 = every day, 0=Mon … 6=Sun)
    val cronDayOfWeek: Int = -1,
    val cronHour: Int      = 9,
    val cronMinute: Int    = 0,

    val isEnabled: Boolean       = true,
    val status: EmailStatus      = EmailStatus.PENDING,
    val lastSentTimeMs: Long     = 0L,
    val lastErrorMessage: String = "",
    val label: String            = "",

    val smtpAccountId: Int = 0,

    val attachmentPath: String = "",
    val attachmentName: String = "",

    val requireWifi: Boolean     = false,
    val requireCharging: Boolean = false,
    val requireMinBattery: Int   = 0
)
