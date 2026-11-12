package com.example.emailbot.ui

import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.ScheduleType
import com.example.emailbot.mail.MailProviders
import com.example.emailbot.mail.SmtpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FormattersTest {

    @Test fun formatInterval_variants() {
        assertEquals("45 Min.", formatInterval(45.0))
        assertEquals("1 Std.", formatInterval(60.0))
        assertEquals("1 Std. 30 Min.", formatInterval(90.0))
        assertEquals("1 Tag", formatInterval(1440.0))
        assertEquals("2 Tage 3 Std.", formatInterval(3060.0))
        assertEquals("1,5 Min.", formatInterval(1.5))
    }

    @Test fun formatRelative_todayTomorrowYesterday() {
        val now = Calendar.getInstance().apply { set(2026, 10, 11, 12, 0, 0) }.timeInMillis
        val h = 3_600_000L
        assertEquals("Heute, 14:00", formatRelative(now + 2 * h, now))
        assertEquals("Morgen, 12:00", formatRelative(now + 24 * h, now))
        assertEquals("Gestern, 12:00", formatRelative(now - 24 * h, now))
    }

    @Test fun scheduleSummary_cron() {
        assertEquals("Täglich, 07:05", scheduleSummary(EmailTask(scheduleType = ScheduleType.CRON, cronDayOfWeek = -1, cronHour = 7, cronMinute = 5)))
        assertEquals("Jeden Freitag, 16:30", scheduleSummary(EmailTask(scheduleType = ScheduleType.CRON, cronDayOfWeek = 4, cronHour = 16, cronMinute = 30)))
        assertEquals("Alle 1 Std.", scheduleSummary(EmailTask(scheduleType = ScheduleType.PERIODIC, intervalMinutes = 60.0)))
    }

    @Test fun addressValidation() {
        assertTrue(SmtpClient.isValidAddressList("anna@example.com"))
        assertTrue(SmtpClient.isValidAddressList("anna@example.com, max@example.org"))
        assertTrue(SmtpClient.isValidAddressList("anna@example.com; max@example.org"))
        assertFalse(SmtpClient.isValidAddressList(""))
        assertFalse(SmtpClient.isValidAddressList("anna"))
        assertFalse(SmtpClient.isValidAddressList("anna@example.com, kaputt@"))
    }

    @Test fun providerDetection() {
        assertEquals("smtp.gmail.com", MailProviders.forAddress("someone@gmail.com")?.host)
        assertEquals("mail.gmx.net", MailProviders.forAddress("Someone@GMX.de")?.host)
        assertNull(MailProviders.forAddress("someone@firma.de"))
        assertNull(MailProviders.forAddress("kein-at-zeichen"))
    }
}
