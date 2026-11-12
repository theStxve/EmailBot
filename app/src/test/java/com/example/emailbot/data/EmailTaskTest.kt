package com.example.emailbot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailTaskTest {

    @Test fun pendingEnabledTask_isActive() {
        assertTrue(EmailTask(status = EmailStatus.PENDING).isActive)
    }

    @Test fun disabledTask_isNotActive() {
        assertFalse(EmailTask(isEnabled = false).isActive)
    }

    @Test fun sentOneTimeTask_isNotActive() {
        assertFalse(EmailTask(scheduleType = ScheduleType.ONCE, status = EmailStatus.SENT).isActive)
    }

    @Test fun failedOneTimeTask_isNotActive_soItIsNotResentOnReboot() {
        assertFalse(EmailTask(scheduleType = ScheduleType.ONCE, status = EmailStatus.FAILED).isActive)
    }

    @Test fun failedRecurringTask_staysActive() {
        assertTrue(EmailTask(scheduleType = ScheduleType.PERIODIC, status = EmailStatus.FAILED).isActive)
        assertTrue(EmailTask(scheduleType = ScheduleType.CRON, status = EmailStatus.FAILED).isActive)
    }

    @Test fun displayTitle_prefersLabel_thenSubject() {
        assertEquals("Label", EmailTask(label = "Label", subject = "Betreff").displayTitle)
        assertEquals("Betreff", EmailTask(subject = "Betreff").displayTitle)
        assertEquals("(kein Betreff)", EmailTask().displayTitle)
    }
}
