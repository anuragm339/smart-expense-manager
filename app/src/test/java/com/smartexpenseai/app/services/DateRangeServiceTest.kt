package com.smartexpenseai.app.services

import com.smartexpenseai.app.services.DateRangeService.Companion.TimeAggregation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateRangeServiceTest {

    private val service = DateRangeService()

    @Test
    fun shouldGenerateCompleteDailyPeriodsWithoutZeroLengthBuckets() {
        val end = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 12, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val periods = service.generatePeriods(TimeAggregation.DAILY, 3, end)

        assertEquals(3, periods.size)
        periods.forEach { (start, periodEnd) ->
            assertTrue(periodEnd.after(start))
        }
        assertEquals(Calendar.OCTOBER, calendar(periods.first().first).get(Calendar.MONTH))
        assertEquals(8, calendar(periods.first().first).get(Calendar.DAY_OF_MONTH))
        assertEquals(10, calendar(periods.last().first).get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun shouldGenerateDistinctMonthlyPeriods() {
        val end = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 12, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val periods = service.generatePeriods(TimeAggregation.MONTHLY, 3, end)

        assertEquals(3, periods.size)
        assertEquals(Calendar.AUGUST, calendar(periods[0].first).get(Calendar.MONTH))
        assertEquals(Calendar.SEPTEMBER, calendar(periods[1].first).get(Calendar.MONTH))
        assertEquals(Calendar.OCTOBER, calendar(periods[2].first).get(Calendar.MONTH))
        periods.forEach { (start, periodEnd) -> assertTrue(periodEnd.after(start)) }
    }

    private fun calendar(date: java.util.Date): Calendar =
        Calendar.getInstance().apply { time = date }
}
