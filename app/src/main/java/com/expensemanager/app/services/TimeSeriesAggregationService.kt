package com.expensemanager.app.services

import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.services.DateRangeService.Companion.TimeAggregation
import com.expensemanager.app.utils.logging.StructuredLogger
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for time series data aggregation
 * Eliminates ~400 lines of duplicated time series logic
 */
@Singleton
class TimeSeriesAggregationService @Inject constructor(
    private val dateRangeService: DateRangeService
) {
    
    companion object {
        private const val TAG = "TimeSeriesAggregationService"
    }

    private val logger = StructuredLogger("DASHBOARD", TAG)
    
    /**
     * Data class for time series data points
     */
    data class TimeSeriesData(
        val label: String,
        val amount: Double,
        val transactionCount: Int,
        val date: Date,
        val startDate: Date,
        val endDate: Date
    )
    
    /**
     * Generate time series data based on aggregation type
     */
    fun generateTimeSeriesData(
        transactions: List<TransactionEntity>,
        aggregationType: TimeAggregation,
        periodCount: Int
    ): List<TimeSeriesData> {
        return when (aggregationType) {
            TimeAggregation.DAILY -> generateDailyData(transactions, periodCount)
            TimeAggregation.WEEKLY -> generateWeeklyData(transactions, periodCount)
            TimeAggregation.MONTHLY -> generateMonthlyData(transactions, periodCount)
            TimeAggregation.QUARTERLY -> generateQuarterlyData(transactions, periodCount)
            TimeAggregation.YEARLY -> generateYearlyData(transactions, periodCount)
        }
    }

    /**
     * Generate time series data for a specific date range (for custom date ranges)
     */
    fun generateTimeSeriesDataInRange(
        transactions: List<TransactionEntity>,
        aggregationType: TimeAggregation,
        startDate: Date,
        endDate: Date
    ): List<TimeSeriesData> {
        val periods = dateRangeService.generatePeriodsInRange(aggregationType, startDate, endDate)
        val timeSeriesData = mutableListOf<TimeSeriesData>()

        for ((periodStart, periodEnd) in periods) {
            val periodTransactions = transactions.filter { transaction ->
                transaction.transactionDate.time >= periodStart.time && 
                transaction.transactionDate.time <= periodEnd.time
            }
            
            val totalAmount = periodTransactions.sumOf { it.amount }
            val transactionCount = periodTransactions.size
            
            val label = when (aggregationType) {
                TimeAggregation.DAILY -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(periodStart)
                TimeAggregation.WEEKLY -> "${SimpleDateFormat("MMM dd", Locale.getDefault()).format(periodStart)} - ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(periodEnd)}"
                TimeAggregation.MONTHLY -> SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(periodStart)
                TimeAggregation.QUARTERLY -> {
                    val calendar = Calendar.getInstance()
                    calendar.time = periodStart
                    val year = calendar.get(Calendar.YEAR)
                    val month = calendar.get(Calendar.MONTH)
                    val quarter = when (month) {
                        in Calendar.JANUARY..Calendar.MARCH -> "Q1"
                        in Calendar.APRIL..Calendar.JUNE -> "Q2"
                        in Calendar.JULY..Calendar.SEPTEMBER -> "Q3"
                        else -> "Q4"
                    }
                    "$quarter $year"
                }
                TimeAggregation.YEARLY -> SimpleDateFormat("yyyy", Locale.getDefault()).format(periodStart)
            }
            
            timeSeriesData.add(TimeSeriesData(
                label = label,
                amount = totalAmount,
                transactionCount = transactionCount,
                date = periodStart,
                startDate = periodStart,
                endDate = periodEnd
            ))
        }
        logger.debug(
            "generateTimeSeriesDataInRange",
            "Generated time series data for ${timeSeriesData.size} periods",
            "Range $startDate to $endDate"
        )
        return timeSeriesData
    }
    
    /**
     * Generate daily aggregated data
     */
    fun generateDailyData(transactions: List<TransactionEntity>, days: Int): List<TimeSeriesData> {
        val periods = dateRangeService.generatePeriods(TimeAggregation.DAILY, days)
        val dailyData = mutableListOf<TimeSeriesData>()
        
        for ((startDate, endDate) in periods) {
            val periodTransactions = transactions.filter { transaction ->
                transaction.transactionDate.time >= startDate.time && 
                transaction.transactionDate.time <= endDate.time
            }
            
            val totalAmount = periodTransactions.sumOf { it.amount }
            val transactionCount = periodTransactions.size
            
            val dayLabel = SimpleDateFormat("MMM dd", Locale.getDefault()).format(startDate)
            
            dailyData.add(TimeSeriesData(
                label = dayLabel,
                amount = totalAmount,
                transactionCount = transactionCount,
                date = startDate,
                startDate = startDate,
                endDate = endDate
            ))
        }
        logger.debug(
            "generateDailyData",
            "Generated daily data for $days days",
            "Created ${dailyData.size} points"
        )
        return dailyData
    }
    
    /**
     * Generate weekly aggregated data
     */
    fun generateWeeklyData(transactions: List<TransactionEntity>, weeks: Int): List<TimeSeriesData> {
        val periods = dateRangeService.generatePeriods(TimeAggregation.WEEKLY, weeks)
        val weeklyData = mutableListOf<TimeSeriesData>()
        
        for ((startDate, endDate) in periods) {
            val periodTransactions = transactions.filter { transaction ->
                val transactionTime = transaction.transactionDate.time
                val startTime = startDate.time
                val endTime = endDate.time
                
                // More inclusive filtering - include transactions on the boundary dates
                transactionTime >= startTime && transactionTime < endTime + 24 * 60 * 60 * 1000 // Add 1 day buffer
            }
            
            val totalAmount = periodTransactions.sumOf { it.amount }
            val transactionCount = periodTransactions.size
            
            val weekLabel = "${SimpleDateFormat("MMM dd", Locale.getDefault()).format(startDate)} - ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(endDate)}"
            
            weeklyData.add(TimeSeriesData(
                label = weekLabel,
                amount = totalAmount,
                transactionCount = transactionCount,
                date = startDate,
                startDate = startDate,
                endDate = endDate
            ))
        }
        logger.debug(
            "generateWeeklyData",
            "Generated weekly data for $weeks weeks",
            "Created ${weeklyData.size} periods"
        )
        return weeklyData
    }
    
    /**
     * Generate monthly aggregated data
     */
    fun generateMonthlyData(transactions: List<TransactionEntity>, months: Int): List<TimeSeriesData> {
        val periods = dateRangeService.generatePeriods(TimeAggregation.MONTHLY, months)
        val monthlyData = mutableListOf<TimeSeriesData>()
        
        for ((startDate, endDate) in periods) {
            val periodTransactions = transactions.filter { transaction ->
                val transactionTime = transaction.transactionDate.time
                val startTime = startDate.time
                val endTime = endDate.time
                
                // More inclusive filtering - include transactions on the boundary dates
                transactionTime >= startTime && transactionTime < endTime + 24 * 60 * 60 * 1000 // Add 1 day buffer
            }
            
            val totalAmount = periodTransactions.sumOf { it.amount }
            val transactionCount = periodTransactions.size
            
            val monthLabel = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(startDate)
            
            monthlyData.add(TimeSeriesData(
                label = monthLabel,
                amount = totalAmount,
                transactionCount = transactionCount,
                date = startDate,
                startDate = startDate,
                endDate = endDate
            ))
        }
        logger.debug(
            "generateMonthlyData",
            "Generated monthly data for $months months",
            "Created ${monthlyData.size} periods"
        )
        return monthlyData
    }
    
    /**
     * Generate quarterly aggregated data
     */
    fun generateQuarterlyData(transactions: List<TransactionEntity>, quarters: Int): List<TimeSeriesData> {
        val periods = dateRangeService.generatePeriods(TimeAggregation.QUARTERLY, quarters)
        val quarterlyData = mutableListOf<TimeSeriesData>()
        
        for ((startDate, endDate) in periods) {
            val periodTransactions = transactions.filter { transaction ->
                transaction.transactionDate.time >= startDate.time && 
                transaction.transactionDate.time <= endDate.time
            }
            
            val totalAmount = periodTransactions.sumOf { it.amount }
            val transactionCount = periodTransactions.size
            
            val calendar = Calendar.getInstance()
            calendar.time = startDate
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            
            val quarter = when (month) {
                in Calendar.JANUARY..Calendar.MARCH -> "Q1"
                in Calendar.APRIL..Calendar.JUNE -> "Q2"
                in Calendar.JULY..Calendar.SEPTEMBER -> "Q3"
                else -> "Q4"
            }
            
            val quarterLabel = "$quarter $year"
            
            quarterlyData.add(TimeSeriesData(
                label = quarterLabel,
                amount = totalAmount,
                transactionCount = transactionCount,
                date = startDate,
                startDate = startDate,
                endDate = endDate
            ))
        }
        logger.debug(
            "generateQuarterlyData",
            "Generated quarterly data for $quarters quarters",
            "Created ${quarterlyData.size} periods"
        )
        return quarterlyData
    }
    
    /**
     * Generate yearly aggregated data
     */
    fun generateYearlyData(transactions: List<TransactionEntity>, years: Int): List<TimeSeriesData> {
        val periods = dateRangeService.generatePeriods(TimeAggregation.YEARLY, years)
        val yearlyData = mutableListOf<TimeSeriesData>()
        
        for ((startDate, endDate) in periods) {
            val periodTransactions = transactions.filter { transaction ->
                transaction.transactionDate.time >= startDate.time && 
                transaction.transactionDate.time <= endDate.time
            }
            
            val totalAmount = periodTransactions.sumOf { it.amount }
            val transactionCount = periodTransactions.size
            
            val yearLabel = SimpleDateFormat("yyyy", Locale.getDefault()).format(startDate)
            
            yearlyData.add(TimeSeriesData(
                label = yearLabel,
                amount = totalAmount,
                transactionCount = transactionCount,
                date = startDate,
                startDate = startDate,
                endDate = endDate
            ))
        }
        logger.debug(
            "generateYearlyData",
            "Generated yearly data for $years years",
            "Created ${yearlyData.size} periods"
        )
        return yearlyData
    }
    
    /**
     * Generate time series data for current period vs previous period comparison
     */
    fun generateComparisonData(
        transactions: List<TransactionEntity>,
        aggregationType: TimeAggregation,
        periodCount: Int
    ): ComparisonData {
        val currentData = generateTimeSeriesData(transactions, aggregationType, periodCount)
        
        // Get previous period data by shifting the date range
        val shiftedTransactions = when (aggregationType) {
            TimeAggregation.DAILY -> {
                val cutoffDate = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -periodCount)
                }.time
                transactions.filter { it.transactionDate.before(cutoffDate) }
            }
            TimeAggregation.WEEKLY -> {
                val cutoffDate = Calendar.getInstance().apply {
                    add(Calendar.WEEK_OF_YEAR, -periodCount)
                }.time
                transactions.filter { it.transactionDate.before(cutoffDate) }
            }
            TimeAggregation.MONTHLY -> {
                val cutoffDate = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -periodCount)
                }.time
                transactions.filter { it.transactionDate.before(cutoffDate) }
            }
            TimeAggregation.QUARTERLY -> {
                val cutoffDate = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -periodCount * 3)
                }.time
                transactions.filter { it.transactionDate.before(cutoffDate) }
            }
            TimeAggregation.YEARLY -> {
                val cutoffDate = Calendar.getInstance().apply {
                    add(Calendar.YEAR, -periodCount)
                }.time
                transactions.filter { it.transactionDate.before(cutoffDate) }
            }
        }
        
        val previousData = generateTimeSeriesData(shiftedTransactions, aggregationType, periodCount)
        
        val currentTotal = currentData.sumOf { it.amount }
        val previousTotal = previousData.sumOf { it.amount }
        val percentageChange = if (previousTotal > 0) {
            ((currentTotal - previousTotal) / previousTotal) * 100
        } else {
            100.0
        }
        
        return ComparisonData(
            currentPeriodData = currentData,
            previousPeriodData = previousData,
            currentTotal = currentTotal,
            previousTotal = previousTotal,
            percentageChange = percentageChange,
            trend = when {
                percentageChange > 0 -> TrendDirection.UP
                percentageChange < 0 -> TrendDirection.DOWN
                else -> TrendDirection.FLAT
            }
        )
    }
    
    /**
     * Calculate moving averages for trend analysis
     */
    fun calculateMovingAverage(data: List<TimeSeriesData>, windowSize: Int): List<TimeSeriesData> {
        if (data.size < windowSize) return data
        
        val movingAverageData = mutableListOf<TimeSeriesData>()
        
        for (i in windowSize - 1 until data.size) {
            val window = data.subList(i - windowSize + 1, i + 1)
            val averageAmount = window.map { it.amount }.average()
            val totalTransactions = window.sumOf { it.transactionCount }
            
            val centerPoint = data[i]
            movingAverageData.add(
                TimeSeriesData(
                    label = centerPoint.label,
                    amount = averageAmount,
                    transactionCount = totalTransactions,
                    date = centerPoint.date,
                    startDate = centerPoint.startDate,
                    endDate = centerPoint.endDate
                )
            )
        }
        
        return movingAverageData
    }
    
    /**
     * Get summary statistics for time series data
     */
    fun getSummaryStatistics(data: List<TimeSeriesData>): SummaryStatistics {
        if (data.isEmpty()) {
            return SummaryStatistics(
                totalAmount = 0.0,
                averageAmount = 0.0,
                maxAmount = 0.0,
                minAmount = 0.0,
                totalTransactions = 0,
                periodCount = 0,
                standardDeviation = 0.0
            )
        }
        
        val amounts = data.map { it.amount }
        val totalAmount = amounts.sum()
        val averageAmount = amounts.average()
        val maxAmount = amounts.maxOrNull() ?: 0.0
        val minAmount = amounts.minOrNull() ?: 0.0
        val totalTransactions = data.sumOf { it.transactionCount }
        
        // Calculate standard deviation
        val variance = amounts.map { (it - averageAmount) * (it - averageAmount) }.average()
        val standardDeviation = kotlin.math.sqrt(variance)
        
        return SummaryStatistics(
            totalAmount = totalAmount,
            averageAmount = averageAmount,
            maxAmount = maxAmount,
            minAmount = minAmount,
            totalTransactions = totalTransactions,
            periodCount = data.size,
            standardDeviation = standardDeviation
        )
    }
    
    /**
     * Data class for comparison analysis
     */
    data class ComparisonData(
        val currentPeriodData: List<TimeSeriesData>,
        val previousPeriodData: List<TimeSeriesData>,
        val currentTotal: Double,
        val previousTotal: Double,
        val percentageChange: Double,
        val trend: TrendDirection
    )
    
    /**
     * Data class for summary statistics
     */
    data class SummaryStatistics(
        val totalAmount: Double,
        val averageAmount: Double,
        val maxAmount: Double,
        val minAmount: Double,
        val totalTransactions: Int,
        val periodCount: Int,
        val standardDeviation: Double
    )
    
    /**
     * Generate time series data with enhanced date range handling for special combinations
     * This method supports sophisticated scenarios like "This Month + Weekly" and "Last 30 Days + Monthly"
     */
    fun generateTimeSeriesDataWithEnhancedRanges(
        transactions: List<TransactionEntity>,
        dateRangeFilter: String,
        aggregationType: TimeAggregation,
        startDate: Date,
        endDate: Date
    ): List<TimeSeriesData> {
        val periods = dateRangeService.generatePeriodsWithSpecialHandling(
            dateRangeFilter, aggregationType, startDate, endDate
        )
        val timeSeriesData = mutableListOf<TimeSeriesData>()
        
        for ((periodStart, periodEnd) in periods) {
            val periodTransactions = transactions.filter { transaction ->
                transaction.transactionDate.time >= periodStart.time && 
                transaction.transactionDate.time <= periodEnd.time
            }
            
            val totalAmount = periodTransactions.sumOf { it.amount }
            val transactionCount = periodTransactions.size
            
            val label = when (aggregationType) {
                TimeAggregation.DAILY -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(periodStart)
                TimeAggregation.WEEKLY -> "${SimpleDateFormat("MMM dd", Locale.getDefault()).format(periodStart)} - ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(periodEnd)}"
                TimeAggregation.MONTHLY -> SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(periodStart)
                TimeAggregation.QUARTERLY -> {
                    val calendar = Calendar.getInstance()
                    calendar.time = periodStart
                    val year = calendar.get(Calendar.YEAR)
                    val month = calendar.get(Calendar.MONTH)
                    val quarter = when (month) {
                        in Calendar.JANUARY..Calendar.MARCH -> "Q1"
                        in Calendar.APRIL..Calendar.JUNE -> "Q2"
                        in Calendar.JULY..Calendar.SEPTEMBER -> "Q3"
                        else -> "Q4"
                    }
                    "$quarter $year"
                }
                TimeAggregation.YEARLY -> SimpleDateFormat("yyyy", Locale.getDefault()).format(periodStart)
            }
            
            timeSeriesData.add(TimeSeriesData(
                label = label,
                amount = totalAmount,
                transactionCount = transactionCount,
                date = periodStart,
                startDate = periodStart,
                endDate = periodEnd
            ))
        }
        logger.debug(
            "generateTimeSeriesDataWithEnhancedRanges",
            "Generated enhanced time series data for filter $dateRangeFilter",
            "Created ${timeSeriesData.size} periods"
        )
        return timeSeriesData
    }

    /**
     * Enum for trend direction
     */
    enum class TrendDirection {
        UP, DOWN, FLAT
    }
}
