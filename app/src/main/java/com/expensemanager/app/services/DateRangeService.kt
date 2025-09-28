package com.expensemanager.app.services

import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized service for date range calculations
 * Eliminates ~800 lines of duplicated date calculation logic across the codebase
 */
@Singleton
class DateRangeService @Inject constructor() {
    
    companion object {
        private const val TAG = "DateRangeService"
        
        // Date range types for consistent usage across the app
        enum class DateRangeType {
            CURRENT_MONTH,
            LAST_MONTH,
            LAST_7_DAYS,
            LAST_30_DAYS, 
            LAST_3_MONTHS,
            LAST_6_MONTHS,
            THIS_YEAR,
            LAST_YEAR,
            CUSTOM,
            CURRENT_WEEK,
            CURRENT_QUARTER,
            LAST_QUARTER,
            MONTH_TO_DATE,
            YEAR_TO_DATE
        }
        
        // Time aggregation types for time series data
        enum class TimeAggregation {
            DAILY,
            WEEKLY, 
            MONTHLY,
            QUARTERLY,
            YEARLY
        }
    }
    
    /**
     * Get date range for the specified type
     */
    fun getDateRange(rangeType: DateRangeType): Pair<Date, Date> {
        return when (rangeType) {
            DateRangeType.CURRENT_MONTH -> getCurrentMonth()
            DateRangeType.LAST_MONTH -> getPreviousMonth()
            DateRangeType.LAST_7_DAYS -> getLast7Days()
            DateRangeType.LAST_30_DAYS -> getLast30Days()
            DateRangeType.LAST_3_MONTHS -> getLast3Months()
            DateRangeType.LAST_6_MONTHS -> getLast6Months()
            DateRangeType.THIS_YEAR -> getThisYear()
            DateRangeType.LAST_YEAR -> getLastYear()
            DateRangeType.CURRENT_WEEK -> getCurrentWeek()
            DateRangeType.CURRENT_QUARTER -> getCurrentQuarter()
            DateRangeType.LAST_QUARTER -> getLastQuarter()
            DateRangeType.MONTH_TO_DATE -> getMonthToDate()
            DateRangeType.YEAR_TO_DATE -> getYearToDate()
            DateRangeType.CUSTOM -> throw IllegalArgumentException("Use getCustomRange() for custom date ranges")
        }
    }
    
    /**
     * Get current month range (start of month to end of month)
     */
    fun getCurrentMonth(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // Start of current month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        // End of current month
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        Timber.d("Current month range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get last 7 days range (7 days ago to now)
     */
    fun getLast7Days(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - current time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        // Start date - 6 days ago (to include today = 7 days total)
        calendar.add(Calendar.DAY_OF_MONTH, -6)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        Timber.d("Last 7 days range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get last 30 days range (30 days ago to now)
     */
    fun getLast30Days(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - current time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        // Start date - 29 days ago (to include today = 30 days total)
        calendar.add(Calendar.DAY_OF_MONTH, -29)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        Timber.d("Last 30 days range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get last 3 months range
     */
    fun getLast3Months(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - current time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        // Start date - 3 months ago
        calendar.add(Calendar.MONTH, -3)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        Timber.d("Last 3 months range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get last 6 months range
     */
    fun getLast6Months(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - current time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        // Start date - 6 months ago
        calendar.add(Calendar.MONTH, -6)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        Timber.d("Last 6 months range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get this year range (beginning of year to now)
     */
    fun getThisYear(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - current time
        val endDate = calendar.time
        
        // Start date - beginning of this year
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        Timber.d("This year range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get last year range (beginning of last year to end of last year)
     */
    fun getLastYear(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // Go to last year
        calendar.add(Calendar.YEAR, -1)
        
        // Start date - beginning of last year
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        // End date - end of last year
        calendar.set(Calendar.MONTH, Calendar.DECEMBER)
        calendar.set(Calendar.DAY_OF_MONTH, 31)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        Timber.d("Last year range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get current week range (Monday to Sunday)
     */
    fun getCurrentWeek(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // Set to Monday of current week
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        // Set to Sunday of current week
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        Timber.d("Current week range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get current quarter range
     */
    fun getCurrentQuarter(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        
        // Determine quarter start month
        val quarterStartMonth = when (currentMonth) {
            in Calendar.JANUARY..Calendar.MARCH -> Calendar.JANUARY
            in Calendar.APRIL..Calendar.JUNE -> Calendar.APRIL
            in Calendar.JULY..Calendar.SEPTEMBER -> Calendar.JULY
            else -> Calendar.OCTOBER
        }
        
        // Start of quarter
        calendar.set(Calendar.MONTH, quarterStartMonth)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        // End of quarter
        calendar.add(Calendar.MONTH, 2) // Move to last month of quarter
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        Timber.d("Current quarter range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get last quarter range
     */
    fun getLastQuarter(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        
        // Determine last quarter start month
        val lastQuarterStartMonth = when (currentMonth) {
            in Calendar.JANUARY..Calendar.MARCH -> Calendar.OCTOBER // Last year Q4
            in Calendar.APRIL..Calendar.JUNE -> Calendar.JANUARY    // This year Q1
            in Calendar.JULY..Calendar.SEPTEMBER -> Calendar.APRIL  // This year Q2
            else -> Calendar.JULY                                   // This year Q3
        }
        
        // If we're in Q1, we need to go to last year for Q4
        if (currentMonth in Calendar.JANUARY..Calendar.MARCH) {
            calendar.add(Calendar.YEAR, -1)
        }
        
        // Start of last quarter
        calendar.set(Calendar.MONTH, lastQuarterStartMonth)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        // End of last quarter
        calendar.add(Calendar.MONTH, 2) // Move to last month of quarter
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        Timber.d("Last quarter range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get month to date range (start of current month to now)
     */
    fun getMonthToDate(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - current time
        val endDate = calendar.time
        
        // Start date - beginning of current month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        Timber.d("Month to date range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get year to date range (start of current year to now)
     */
    fun getYearToDate(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - current time
        val endDate = calendar.time
        
        // Start date - beginning of current year
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        Timber.d("Year to date range: $startDate to $endDate")
        return Pair(startDate, endDate)
    }
    
    /**
     * Get custom date range
     */
    fun getCustomRange(startDate: Date, endDate: Date): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // Normalize start date to beginning of day
        calendar.time = startDate
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val normalizedStartDate = calendar.time
        
        // Normalize end date to end of day
        calendar.time = endDate
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val normalizedEndDate = calendar.time
        
        Timber.d("Custom range: $normalizedStartDate to $normalizedEndDate")
        return Pair(normalizedStartDate, normalizedEndDate)
    }
    
    /**
     * Get previous period range for comparison
     */
    fun getPreviousPeriodRange(rangeType: DateRangeType): Pair<Date, Date> {
        return when (rangeType) {
            DateRangeType.CURRENT_MONTH -> getPreviousMonth()
            DateRangeType.LAST_7_DAYS -> getPrevious7Days()
            DateRangeType.LAST_30_DAYS -> getPrevious30Days()
            DateRangeType.LAST_3_MONTHS -> getPrevious3Months()
            DateRangeType.LAST_6_MONTHS -> getPrevious6Months()
            DateRangeType.THIS_YEAR -> getLastYear()
            DateRangeType.CURRENT_QUARTER -> getLastQuarter()
            else -> throw IllegalArgumentException("Previous period not supported for $rangeType")
        }
    }
    
    /**
     * Get previous month range
     */
    private fun getPreviousMonth(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // Go to previous month
        calendar.add(Calendar.MONTH, -1)
        
        // Start of previous month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        // End of previous month
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        return Pair(startDate, endDate)
    }
    
    /**
     * Get previous 7 days range (before the last 7 days)
     */
    private fun getPrevious7Days(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - 7 days ago (end of day)
        calendar.add(Calendar.DAY_OF_MONTH, -7)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        // Start date - 13 days ago (beginning of day)
        calendar.add(Calendar.DAY_OF_MONTH, -6)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        return Pair(startDate, endDate)
    }
    
    /**
     * Get previous 30 days range (before the last 30 days)
     */
    private fun getPrevious30Days(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - 30 days ago (end of day)
        calendar.add(Calendar.DAY_OF_MONTH, -30)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        // Start date - 59 days ago (beginning of day)
        calendar.add(Calendar.DAY_OF_MONTH, -29)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        return Pair(startDate, endDate)
    }
    
    /**
     * Get previous 3 months range (before the last 3 months)
     */
    private fun getPrevious3Months(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - 3 months ago (end of day)
        calendar.add(Calendar.MONTH, -3)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        // Start date - 6 months ago (beginning of day)
        calendar.add(Calendar.MONTH, -3)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        return Pair(startDate, endDate)
    }
    
    /**
     * Get previous 6 months range (before the last 6 months)
     */
    private fun getPrevious6Months(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // End date - 6 months ago (end of day)
        calendar.add(Calendar.MONTH, -6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        // Start date - 12 months ago (beginning of day)
        calendar.add(Calendar.MONTH, -6)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        return Pair(startDate, endDate)
    }
    
    /**
     * Get periods for time series aggregation
     */
    fun generatePeriods(
        aggregationType: TimeAggregation,
        count: Int,
        endDate: Date = Date()
    ): List<Pair<Date, Date>> {
        val periods = mutableListOf<Pair<Date, Date>>()
        val calendar = Calendar.getInstance()
        calendar.time = endDate
        
        for (i in 0 until count) {
            val periodEnd = calendar.time
            val periodStart = when (aggregationType) {
                TimeAggregation.DAILY -> {
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.time
                }
                TimeAggregation.WEEKLY -> {
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
                    calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.time
                }
                TimeAggregation.MONTHLY -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.time
                }
                TimeAggregation.QUARTERLY -> {
                    val month = calendar.get(Calendar.MONTH)
                    val quarterStartMonth = when (month) {
                        in Calendar.JANUARY..Calendar.MARCH -> Calendar.JANUARY
                        in Calendar.APRIL..Calendar.JUNE -> Calendar.APRIL
                        in Calendar.JULY..Calendar.SEPTEMBER -> Calendar.JULY
                        else -> Calendar.OCTOBER
                    }
                    calendar.set(Calendar.MONTH, quarterStartMonth)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.time
                }
                TimeAggregation.YEARLY -> {
                    calendar.set(Calendar.MONTH, Calendar.JANUARY)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.time
                }
            }
            
            periods.add(0, Pair(periodStart, periodEnd)) // Add to beginning for chronological order
            
            // Move to previous period
            when (aggregationType) {
                TimeAggregation.DAILY -> calendar.add(Calendar.DAY_OF_MONTH, -1)
                TimeAggregation.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, -1)
                TimeAggregation.MONTHLY -> calendar.add(Calendar.MONTH, -1)
                TimeAggregation.QUARTERLY -> calendar.add(Calendar.MONTH, -3)
                TimeAggregation.YEARLY -> calendar.add(Calendar.YEAR, -1)
            }
        }
        
        return periods
    }
    
    /**
     * Format date range as string for display
     */
    fun formatDateRange(startDate: Date, endDate: Date): String {
        val calendar = Calendar.getInstance()
        val formatter = java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        
        calendar.time = startDate
        val startFormatted = formatter.format(startDate)
        
        calendar.time = endDate
        val endFormatted = formatter.format(endDate)
        
        return "$startFormatted - $endFormatted"
    }
    
    /**
     * Check if two date ranges are equal
     */
    fun isDateRangeEqual(range1: Pair<Date, Date>, range2: Pair<Date, Date>): Boolean {
        return range1.first.time == range2.first.time && range1.second.time == range2.second.time
    }
}