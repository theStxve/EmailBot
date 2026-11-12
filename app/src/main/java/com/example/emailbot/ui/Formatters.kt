package com.example.emailbot.ui

import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.ScheduleType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val GERMAN = Locale.GERMANY

val WEEKDAYS_SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val WEEKDAYS_LONG = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

private fun dayDiff(ms: Long, now: Long): Int {
    fun Calendar.startOfDay() = apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val a = Calendar.getInstance().apply { timeInMillis = now }.startOfDay()
    val b = Calendar.getInstance().apply { timeInMillis = ms }.startOfDay()
    return Math.round((b - a) / 86_400_000.0).toInt()
}

fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", GERMAN).format(Date(ms))

fun formatTime(hour: Int, minute: Int): String = String.format(GERMAN, "%02d:%02d", hour, minute)

/** "Heute, 14:30" · "Morgen, 09:00" · "Gestern, 18:12" · "Mo, 16.11., 09:00" · "16.11.2027, 09:00" */
fun formatRelative(ms: Long, now: Long = System.currentTimeMillis()): String {
    val time = formatTime(ms)
    return when (dayDiff(ms, now)) {
        0 -> "Heute, $time"
        1 -> "Morgen, $time"
        -1 -> "Gestern, $time"
        else -> {
            val sameYear = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR) ==
                Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
            val pattern = if (sameYear) "EE, dd.MM., HH:mm" else "dd.MM.yyyy, HH:mm"
            SimpleDateFormat(pattern, GERMAN).format(Date(ms))
        }
    }
}

fun formatDateLong(ms: Long): String = SimpleDateFormat("EEEE, d. MMMM yyyy", GERMAN).format(Date(ms))

/** "45 Min." · "2 Std." · "1 Std. 30 Min." · "1 Tag" */
fun formatInterval(minutes: Double): String {
    val total = minutes.toLong()
    if (minutes != total.toDouble()) return "${minutes.toString().replace('.', ',')} Min."
    val days = total / 1440
    val hours = (total % 1440) / 60
    val mins = total % 60
    return buildList {
        if (days > 0) add(if (days == 1L) "1 Tag" else "$days Tage")
        if (hours > 0) add("$hours Std.")
        if (mins > 0 || isEmpty()) add("$mins Min.")
    }.joinToString(" ")
}

/** Short human description of when a task runs, e.g. "Jeden Montag, 09:00". */
fun scheduleSummary(task: EmailTask): String = when (task.scheduleType) {
    ScheduleType.ONCE -> "Einmalig"
    ScheduleType.PERIODIC -> "Alle ${formatInterval(task.intervalMinutes)}"
    ScheduleType.CRON -> {
        val time = formatTime(task.cronHour, task.cronMinute)
        if (task.cronDayOfWeek == -1) "Täglich, $time"
        else "Jeden ${WEEKDAYS_LONG.getOrElse(task.cronDayOfWeek) { "?" }}, $time"
    }
}
