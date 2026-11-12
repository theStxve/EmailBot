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

    /** 0 = use the default (first) SMTP account. */
    val smtpAccountId: Int = 0,

    val attachmentPath: String = "",
    val attachmentName: String = "",

    val requireWifi: Boolean     = false,
    val requireCharging: Boolean = false,
    val requireMinBattery: Int   = 0
)

/** Smallest interval a periodic task may use. Shorter intervals get throttled by Android anyway. */
const val MIN_INTERVAL_MINUTES = 1.0

val EmailTask.isRecurring: Boolean
    get() = scheduleType != ScheduleType.ONCE

/**
 * A task is "active" when it still has an upcoming send.
 * A failed one-time task is not active: it is only sent again when the user retries it.
 */
val EmailTask.isActive: Boolean
    get() = isEnabled && when (status) {
        EmailStatus.PENDING -> true
        EmailStatus.FAILED  -> isRecurring
        EmailStatus.SENT, EmailStatus.PAUSED -> false
    }

val EmailTask.displayTitle: String
    get() = label.ifBlank { subject.ifBlank { "(kein Betreff)" } }
