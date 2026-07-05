package com.smartexpenseai.app.data.repository

import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.min

/**
 * Pure time-of-day matching used by auto-tagging. No Android dependencies, so it is
 * directly unit-testable. Two transactions match when their clock times fall within a
 * window, comparing minute-of-day with midnight wrap-around (23:50 is 20 min from 00:10).
 */
object TagMatchWindow {

    const val WINDOW_MINUTES = 30
    private const val MINUTES_PER_DAY = 24 * 60

    /** Minute-of-day (0..1439) for [date] in the device's local time zone. */
    fun minutesOfDay(date: Date): Int {
        val cal = Calendar.getInstance().apply { time = date }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** True when two minute-of-day values are within [windowMinutes], wrapping midnight. */
    fun withinWindow(aMinutes: Int, bMinutes: Int, windowMinutes: Int = WINDOW_MINUTES): Boolean {
        val diff = abs(aMinutes - bMinutes)
        return min(diff, MINUTES_PER_DAY - diff) <= windowMinutes
    }

    fun withinWindow(a: Date, b: Date, windowMinutes: Int = WINDOW_MINUTES): Boolean =
        withinWindow(minutesOfDay(a), minutesOfDay(b), windowMinutes)
}
