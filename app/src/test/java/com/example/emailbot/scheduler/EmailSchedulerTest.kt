package com.example.emailbot.scheduler

import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.ScheduleType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EmailSchedulerTest {

    private lateinit var originalTz: TimeZone

    @Before fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After fun tearDown() = TimeZone.setDefault(originalTz)

    /** Local time in Berlin. Month is 1-based. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private val minute = 60_000L

    // 2026-11-11 is a Wednesday.
    private val wednesdayNoon = at(2026, 11, 11, 12, 0)

    @Test fun once_returnsFixedTime_evenIfPast() {
        val past = wednesdayNoon - 5 * minute
        val task = EmailTask(scheduleType = ScheduleType.ONCE, scheduledTimeMs = past)
        assertEquals(past, EmailScheduler.nextTriggerMs(task, wednesdayNoon))
    }

    @Test fun periodic_startInFuture_returnsStart() {
        val start = wednesdayNoon + 30 * minute
        val task = EmailTask(scheduleType = ScheduleType.PERIODIC, scheduledTimeMs = start, intervalMinutes = 60.0)
        assertEquals(start, EmailScheduler.nextTriggerMs(task, wednesdayNoon))
    }

    @Test fun periodic_startInPast_returnsNextSlotOnTheGrid() {
        val start = wednesdayNoon - 150 * minute // 09:30
        val task = EmailTask(scheduleType = ScheduleType.PERIODIC, scheduledTimeMs = start, intervalMinutes = 60.0)
        assertEquals(at(2026, 11, 11, 12, 30), EmailScheduler.nextTriggerMs(task, wednesdayNoon))
    }

    @Test fun periodic_exactlyOnSlot_returnsFollowingSlot() {
        val start = wednesdayNoon - 120 * minute
        val task = EmailTask(scheduleType = ScheduleType.PERIODIC, scheduledTimeMs = start, intervalMinutes = 60.0)
        assertEquals(wednesdayNoon + 60 * minute, EmailScheduler.nextTriggerMs(task, wednesdayNoon))
    }

    @Test fun periodic_veryOldStartAndTinyInterval_isFastAndInFuture() {
        val task = EmailTask(scheduleType = ScheduleType.PERIODIC, scheduledTimeMs = 0L, intervalMinutes = 0.01)
        val next = EmailScheduler.nextTriggerMs(task, wednesdayNoon)
        assertTrue(next > wednesdayNoon)
        assertTrue(next - wednesdayNoon <= minute) // interval is clamped to 1 minute
    }

    @Test fun cron_daily_laterToday() {
        val task = EmailTask(scheduleType = ScheduleType.CRON, cronDayOfWeek = -1, cronHour = 18, cronMinute = 15)
        assertEquals(at(2026, 11, 11, 18, 15), EmailScheduler.nextTriggerMs(task, wednesdayNoon))
    }

    @Test fun cron_daily_alreadyPassed_isTomorrow() {
        val task = EmailTask(scheduleType = ScheduleType.CRON, cronDayOfWeek = -1, cronHour = 9, cronMinute = 0)
        assertEquals(at(2026, 11, 12, 9, 0), EmailScheduler.nextTriggerMs(task, wednesdayNoon))
    }

    @Test fun cron_weekly_mondayFromWednesday() {
        val task = EmailTask(scheduleType = ScheduleType.CRON, cronDayOfWeek = 0, cronHour = 9, cronMinute = 0)
        assertEquals(at(2026, 11, 16, 9, 0), EmailScheduler.nextTriggerMs(task, wednesdayNoon))
    }

    @Test fun cron_weekly_sunday() {
        val task = EmailTask(scheduleType = ScheduleType.CRON, cronDayOfWeek = 6, cronHour = 20, cronMinute = 0)
        assertEquals(at(2026, 11, 15, 20, 0), EmailScheduler.nextTriggerMs(task, wednesdayNoon))
    }

    @Test fun cron_weekly_sameDayLater_isToday() {
        val task = EmailTask(scheduleType = ScheduleType.CRON, cronDayOfWeek = 2, cronHour = 13, cronMinute = 0)
        assertEquals(at(2026, 11, 11, 13, 0), EmailScheduler.nextTriggerMs(task, wednesdayNoon))
    }

    @Test fun cron_weekly_sameDayPassed_isNextWeek() {
        val task = EmailTask(scheduleType = ScheduleType.CRON, cronDayOfWeek = 2, cronHour = 8, cronMinute = 0)
        assertEquals(at(2026, 11, 18, 8, 0), EmailScheduler.nextTriggerMs(task, wednesdayNoon))
    }

    @Test fun cron_daily_acrossDstChange_keepsWallClockTime() {
        // Night of 27 → 28 March 2027: clocks jump forward in Berlin.
        val saturdayEvening = at(2027, 3, 27, 22, 0)
        val task = EmailTask(scheduleType = ScheduleType.CRON, cronDayOfWeek = -1, cronHour = 9, cronMinute = 0)
        assertEquals(at(2027, 3, 28, 9, 0), EmailScheduler.nextTriggerMs(task, saturdayEvening))
    }
}
