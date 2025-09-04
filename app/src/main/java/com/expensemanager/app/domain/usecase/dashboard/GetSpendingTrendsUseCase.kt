package com.expensemanager.app.domain.usecase.dashboard

import android.util.Log
import com.expensemanager.app.domain.repository.TransactionRepositoryInterface
import java.util.Date
import java.util.Calendar
import javax.inject.Inject

/**
 * Use case for getting spending trends analysis with business logic
 * Handles trend calculations, pattern analysis, and forecasting
 */
class GetSpendingTrendsUseCase @Inject constructor(
    private val repository: TransactionRepositoryInterface
) {
    
    companion object {
        private const val TAG = "GetSpendingTrendsUseCase"
    }
    
    /**
     * Get daily spending trends for date range
     */
    suspend fun getDailyTrends(startDate: Date, endDate: Date): Result<List<DailySpending>> {
        return try {
            Log.d(TAG, "Getting daily spending trends from $startDate to $endDate")
            
            val transactions = repository.getTransactionsByDateRange(startDate, endDate)
            val dailySpending = calculateDailySpending(transactions)
            
            Log.d(TAG, "Retrieved daily trends for ${dailySpending.size} days")
            Result.success(dailySpending)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting daily trends", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get weekly spending trends
     */
    suspend fun getWeeklyTrends(numberOfWeeks: Int = 12): Result<List<WeeklySpending>> {
        return try {
            Log.d(TAG, "Getting weekly spending trends for $numberOfWeeks weeks")
            
            val endDate = Date()
            val startDate = Date(endDate.time - (numberOfWeeks * 7 * 24 * 60 * 60 * 1000L))
            
            val transactions = repository.getTransactionsByDateRange(startDate, endDate)
            val weeklySpending = calculateWeeklySpending(transactions)
            
            Log.d(TAG, "Retrieved weekly trends for ${weeklySpending.size} weeks")
            Result.success(weeklySpending)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting weekly trends", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get monthly spending trends
     */
    suspend fun getMonthlyTrends(numberOfMonths: Int = 12): Result<List<MonthlySpending>> {
        return try {
            Log.d(TAG, "Getting monthly spending trends for $numberOfMonths months")
            
            val monthlySpending = mutableListOf<MonthlySpending>()
            val calendar = Calendar.getInstance()
            
            for (i in 0 until numberOfMonths) {
                // Set to first day of month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val monthStart = calendar.time
                
                // Set to last day of month
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val monthEnd = calendar.time
                
                val monthTotal = repository.getTotalSpent(monthStart, monthEnd)
                val transactionCount = repository.getTransactionCount(monthStart, monthEnd)
                
                monthlySpending.add(
                    MonthlySpending(
                        month = calendar.get(Calendar.MONTH),
                        year = calendar.get(Calendar.YEAR),
                        totalAmount = monthTotal,
                        transactionCount = transactionCount,
                        averagePerDay = monthTotal / calendar.getActualMaximum(Calendar.DAY_OF_MONTH),
                        monthName = getMonthName(calendar.get(Calendar.MONTH))
                    )
                )
                
                // Move to previous month
                calendar.add(Calendar.MONTH, -1)
            }
            
            Log.d(TAG, "Retrieved monthly trends for ${monthlySpending.size} months")
            Result.success(monthlySpending.reversed()) // Return in chronological order
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting monthly trends", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get spending trend analysis with insights
     */
    suspend fun getTrendAnalysis(params: TrendAnalysisParams): Result<SpendingTrendAnalysis> {
        return try {
            Log.d(TAG, "Getting spending trend analysis")
            
            val transactions = repository.getTransactionsByDateRange(params.startDate, params.endDate)
            val analysis = analyzeSpendingTrends(transactions, params)
            
            Log.d(TAG, "Spending trend analysis completed")
            Result.success(analysis)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting trend analysis", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get category-wise spending trends
     */
    suspend fun getCategoryTrends(startDate: Date, endDate: Date): Result<List<CategoryTrend>> {
        return try {
            Log.d(TAG, "Getting category spending trends")
            
            val transactions = repository.getTransactionsByDateRange(startDate, endDate)
            val categoryTrends = calculateCategoryTrends(transactions)
            
            Log.d(TAG, "Retrieved category trends for ${categoryTrends.size} categories")
            Result.success(categoryTrends)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting category trends", e)
            Result.failure(e)
        }
    }
    
    /**
     * Predict next month spending based on trends
     */
    suspend fun predictNextMonthSpending(): Result<SpendingPrediction> {
        return try {
            Log.d(TAG, "Predicting next month spending")
            
            val monthlyTrends = getMonthlyTrends(6).getOrNull() ?: emptyList()
            val prediction = calculateSpendingPrediction(monthlyTrends)
            
            Log.d(TAG, "Spending prediction completed")
            Result.success(prediction)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error predicting spending", e)
            Result.failure(e)
        }
    }
    
    /**
     * Calculate daily spending from transactions
     */
    private fun calculateDailySpending(transactions: List<com.expensemanager.app.data.entities.TransactionEntity>): List<DailySpending> {
        val calendar = Calendar.getInstance()
        val dailyMap = mutableMapOf<String, MutableList<com.expensemanager.app.data.entities.TransactionEntity>>()
        
        // Group transactions by day
        transactions.forEach { transaction ->
            calendar.time = transaction.transactionDate
            val dayKey = String.format("%04d-%02d-%02d", 
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            
            dailyMap.computeIfAbsent(dayKey) { mutableListOf() }.add(transaction)
        }
        
        // Convert to daily spending objects
        return dailyMap.map { (dayKey, dayTransactions) ->
            DailySpending(
                date = dayKey,
                totalAmount = dayTransactions.sumOf { it.amount },
                transactionCount = dayTransactions.size,
                averagePerTransaction = dayTransactions.sumOf { it.amount } / dayTransactions.size
            )
        }.sortedBy { it.date }
    }
    
    /**
     * Calculate weekly spending from transactions
     */
    private fun calculateWeeklySpending(transactions: List<com.expensemanager.app.data.entities.TransactionEntity>): List<WeeklySpending> {
        val calendar = Calendar.getInstance()
        val weeklyMap = mutableMapOf<String, MutableList<com.expensemanager.app.data.entities.TransactionEntity>>()
        
        // Group transactions by week
        transactions.forEach { transaction ->
            calendar.time = transaction.transactionDate
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            
            val weekKey = String.format("%04d-W%02d", 
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.WEEK_OF_YEAR)
            )
            
            weeklyMap.computeIfAbsent(weekKey) { mutableListOf() }.add(transaction)
        }
        
        return weeklyMap.map { (weekKey, weekTransactions) ->
            WeeklySpending(
                weekKey = weekKey,
                totalAmount = weekTransactions.sumOf { it.amount },
                transactionCount = weekTransactions.size,
                averagePerDay = weekTransactions.sumOf { it.amount } / 7.0
            )
        }.sortedBy { it.weekKey }
    }
    
    /**
     * Calculate category trends
     */
    private fun calculateCategoryTrends(transactions: List<com.expensemanager.app.data.entities.TransactionEntity>): List<CategoryTrend> {
        // This would need merchant-to-category mapping which requires additional repository calls
        // For now, returning empty list - implementation would require CategoryRepository
        return emptyList()
    }
    
    /**
     * Analyze spending trends
     */
    private fun analyzeSpendingTrends(
        transactions: List<com.expensemanager.app.data.entities.TransactionEntity>, 
        params: TrendAnalysisParams
    ): SpendingTrendAnalysis {
        
        val dailySpending = calculateDailySpending(transactions)
        val totalAmount = transactions.sumOf { it.amount }
        val averageDaily = if (dailySpending.isNotEmpty()) totalAmount / dailySpending.size else 0.0
        
        // Calculate trend direction
        val trendDirection = if (dailySpending.size >= 2) {
            val firstHalf = dailySpending.take(dailySpending.size / 2).map { it.totalAmount }.average()
            val secondHalf = dailySpending.drop(dailySpending.size / 2).map { it.totalAmount }.average()
            
            when {
                secondHalf > firstHalf * 1.1 -> TrendDirection.INCREASING
                secondHalf < firstHalf * 0.9 -> TrendDirection.DECREASING
                else -> TrendDirection.STABLE
            }
        } else {
            TrendDirection.INSUFFICIENT_DATA
        }
        
        // Generate insights
        val insights = generateTrendInsights(dailySpending, trendDirection, averageDaily)
        
        return SpendingTrendAnalysis(
            dailySpending = dailySpending,
            totalAmount = totalAmount,
            averageDaily = averageDaily,
            trendDirection = trendDirection,
            insights = insights,
            periodDays = dailySpending.size,
            dateRange = "${params.startDate} to ${params.endDate}"
        )
    }
    
    /**
     * Calculate spending prediction
     */
    private fun calculateSpendingPrediction(monthlyTrends: List<MonthlySpending>): SpendingPrediction {
        if (monthlyTrends.size < 3) {
            return SpendingPrediction(
                predictedAmount = 0.0,
                confidence = 0.0,
                basis = "Insufficient data for prediction"
            )
        }
        
        val amounts = monthlyTrends.map { it.totalAmount }
        val average = amounts.average()
        val trend = (amounts.last() - amounts.first()) / amounts.size
        
        val predictedAmount = amounts.last() + trend
        val confidence = calculatePredictionConfidence(amounts)
        
        return SpendingPrediction(
            predictedAmount = maxOf(0.0, predictedAmount),
            confidence = confidence,
            basis = "Based on ${monthlyTrends.size} months of data with ${if (trend > 0) "increasing" else "decreasing"} trend"
        )
    }
    
    /**
     * Calculate prediction confidence based on data consistency
     */
    private fun calculatePredictionConfidence(amounts: List<Double>): Double {
        if (amounts.size < 3) return 0.0
        
        val average = amounts.average()
        val variance = amounts.map { (it - average) * (it - average) }.average()
        val standardDeviation = kotlin.math.sqrt(variance)
        val coefficientOfVariation = if (average > 0) standardDeviation / average else 1.0
        
        // Higher consistency = higher confidence, cap at 95%
        return maxOf(0.1, minOf(0.95, 1.0 - coefficientOfVariation))
    }
    
    /**
     * Generate trend insights
     */
    private fun generateTrendInsights(
        dailySpending: List<DailySpending>,
        trendDirection: TrendDirection,
        averageDaily: Double
    ): List<String> {
        
        val insights = mutableListOf<String>()
        
        // Trend direction insight
        when (trendDirection) {
            TrendDirection.INCREASING -> insights.add("Your spending is increasing over time. Consider reviewing your budget.")
            TrendDirection.DECREASING -> insights.add("Great! Your spending is decreasing over time.")
            TrendDirection.STABLE -> insights.add("Your spending is relatively stable.")
            TrendDirection.INSUFFICIENT_DATA -> insights.add("Not enough data to determine spending trend.")
        }
        
        // Daily average insight
        insights.add("Your average daily spending is ₹${String.format("%.0f", averageDaily)}")
        
        // Variability insight
        if (dailySpending.isNotEmpty()) {
            val amounts = dailySpending.map { it.totalAmount }
            val maxSpending = amounts.maxOrNull() ?: 0.0
            val minSpending = amounts.minOrNull() ?: 0.0
            
            if (maxSpending > averageDaily * 2) {
                insights.add("You have some high-spending days. Consider spreading large purchases.")
            }
            
            if (minSpending == 0.0) {
                val zeroSpendingDays = amounts.count { it == 0.0 }
                insights.add("You had $zeroSpendingDays no-spend days in this period.")
            }
        }
        
        return insights
    }
    
    /**
     * Get month name from month number
     */
    private fun getMonthName(month: Int): String {
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        return months[month]
    }
}

/**
 * Parameters for trend analysis
 */
data class TrendAnalysisParams(
    val startDate: Date,
    val endDate: Date,
    val includeWeekends: Boolean = true,
    val smoothingFactor: Double = 0.1
)

/**
 * Daily spending data
 */
data class DailySpending(
    val date: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val averagePerTransaction: Double
)

/**
 * Weekly spending data
 */
data class WeeklySpending(
    val weekKey: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val averagePerDay: Double
)

/**
 * Monthly spending data
 */
data class MonthlySpending(
    val month: Int,
    val year: Int,
    val totalAmount: Double,
    val transactionCount: Int,
    val averagePerDay: Double,
    val monthName: String
)

/**
 * Category spending trend
 */
data class CategoryTrend(
    val categoryName: String,
    val monthlyAmounts: List<Double>,
    val trendDirection: TrendDirection,
    val averageMonthly: Double
)

/**
 * Spending trend analysis result
 */
data class SpendingTrendAnalysis(
    val dailySpending: List<DailySpending>,
    val totalAmount: Double,
    val averageDaily: Double,
    val trendDirection: TrendDirection,
    val insights: List<String>,
    val periodDays: Int,
    val dateRange: String
)

/**
 * Spending prediction
 */
data class SpendingPrediction(
    val predictedAmount: Double,
    val confidence: Double, // 0.0 to 1.0
    val basis: String
)

/**
 * Trend direction enum
 */
enum class TrendDirection {
    INCREASING, DECREASING, STABLE, INSUFFICIENT_DATA
}