package com.expensemanager.app.ui.dashboard

import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.services.TransactionFilterService
import com.expensemanager.app.services.TransactionParsingService
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.logging.StructuredLogger
import java.text.SimpleDateFormat
import java.util.*

/**
 * Orchestrates data loading and period calculations for DashboardFragment.
 * Keeps data fetching logic separate from UI concerns.
 */
class DashboardDataOrchestrator(
    private val repository: ExpenseRepository,
    private val parsingService: TransactionParsingService,
    private val filterService: TransactionFilterService,
    private val categoryManager: CategoryManager,
    private val logger: StructuredLogger
) {

    /**
     * Calculate date range for a given period string
     */
    fun getDateRangeForPeriod(period: String): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time

        when (period) {
            "Today" -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            "This Week" -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            "This Month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            "Last 3 Months" -> {
                calendar.add(Calendar.MONTH, -3)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            "Last 6 Months" -> {
                calendar.add(Calendar.MONTH, -6)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            "This Year" -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            "All Time" -> {
                calendar.set(2000, Calendar.JANUARY, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            else -> {
                // Default to this month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
        }

        val startDate = calendar.time
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        logger.debug("getDateRangeForPeriod", "Period '$period': ${dateFormat.format(startDate)} to ${dateFormat.format(endDate)}")

        return Pair(startDate, endDate)
    }

    /**
     * Get previous period date range for comparison
     */
    fun getPreviousPeriodRange(period: String, currentStart: Date, currentEnd: Date): Pair<Date, Date> {
        val calendar = Calendar.getInstance()

        when (period) {
            "Today" -> {
                calendar.time = currentStart
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                val prevStart = calendar.time
                val prevEnd = calendar.time
                return Pair(prevStart, prevEnd)
            }
            "This Week" -> {
                calendar.time = currentStart
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                val prevStart = calendar.time
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                val prevEnd = calendar.time
                return Pair(prevStart, prevEnd)
            }
            "This Month" -> {
                calendar.time = currentStart
                calendar.add(Calendar.MONTH, -1)
                val prevStart = calendar.time
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                val prevEnd = calendar.time
                return Pair(prevStart, prevEnd)
            }
            "Last 3 Months" -> {
                calendar.time = currentStart
                calendar.add(Calendar.MONTH, -3)
                val prevStart = calendar.time
                calendar.time = currentStart
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                val prevEnd = calendar.time
                return Pair(prevStart, prevEnd)
            }
            "Last 6 Months" -> {
                calendar.time = currentStart
                calendar.add(Calendar.MONTH, -6)
                val prevStart = calendar.time
                calendar.time = currentStart
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                val prevEnd = calendar.time
                return Pair(prevStart, prevEnd)
            }
            "This Year" -> {
                calendar.time = currentStart
                calendar.add(Calendar.YEAR, -1)
                val prevStart = calendar.time
                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                val prevEnd = calendar.time
                return Pair(prevStart, prevEnd)
            }
            else -> {
                // Default: previous month
                calendar.time = currentStart
                calendar.add(Calendar.MONTH, -1)
                val prevStart = calendar.time
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                val prevEnd = calendar.time
                return Pair(prevStart, prevEnd)
            }
        }
    }

    /**
     * Get user-friendly name for comparison period
     */
    fun getComparisonPeriodName(period: String): String {
        return when (period) {
            "Today" -> "Yesterday"
            "This Week" -> "Last Week"
            "This Month" -> "Last Month"
            "Last 3 Months" -> "Previous 3 Months"
            "Last 6 Months" -> "Previous 6 Months"
            "This Year" -> "Last Year"
            else -> "Previous Period"
        }
    }

    /**
     * Parse month/year string to pair (month, year)
     */
    fun parseMonthYear(monthYearText: String): Pair<Int, Int>? {
        return try {
            val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val date = dateFormat.parse(monthYearText) ?: return null

            val calendar = Calendar.getInstance()
            calendar.time = date

            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)

            Pair(month, year)
        } catch (e: Exception) {
            logger.error("parseMonthYear", "Failed to parse month/year: $monthYearText", e)
            null
        }
    }

    /**
     * Get date range for a specific custom month
     */
    fun getDateRangeForCustomMonth(monthYear: Pair<Int, Int>): Pair<Date, Date> {
        val (month, year) = monthYear
        val calendar = Calendar.getInstance()

        // Start of month
        calendar.set(year, month, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        // End of month
        calendar.set(year, month, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        return Pair(startDate, endDate)
    }
}
