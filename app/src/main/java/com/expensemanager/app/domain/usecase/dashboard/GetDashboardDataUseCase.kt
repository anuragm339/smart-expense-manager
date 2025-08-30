package com.expensemanager.app.domain.usecase.dashboard

import android.util.Log
import com.expensemanager.app.data.repository.DashboardData
import com.expensemanager.app.domain.repository.DashboardRepositoryInterface
import java.util.Date
import java.util.Calendar
import javax.inject.Inject

/**
 * Use case for getting dashboard data with business logic
 * Handles data aggregation, filtering, and dashboard-specific transformations
 */
class GetDashboardDataUseCase @Inject constructor(
    private val repository: DashboardRepositoryInterface
) {
    
    companion object {
        private const val TAG = "GetDashboardDataUseCase"
    }
    
    /**
     * Get comprehensive dashboard data for date range
     */
    suspend fun execute(startDate: Date, endDate: Date): Result<DashboardData> {
        return try {
            val dashboardData = repository.getDashboardData(startDate, endDate)
            
            Log.d(TAG, "📊 [USECASE] Dashboard: ${dashboardData.transactionCount} transactions, ₹${dashboardData.totalSpent}")
            
            Result.success(dashboardData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dashboard data", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get dashboard data with enhanced analysis
     */
    suspend fun execute(params: DashboardParams): Result<DashboardAnalysis> {
        return try {
            val dashboardData = repository.getDashboardData(params.startDate, params.endDate)
            val analysis = analyzeDashboardData(dashboardData, params)
            
            Result.success(analysis)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dashboard analysis", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get current month dashboard data
     */
    suspend fun getCurrentMonthDashboard(): Result<DashboardData> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val startDate = calendar.time
        val endDate = Date()
        
        return execute(startDate, endDate)
    }
    
    /**
     * Get last 30 days dashboard data
     */
    suspend fun getLastThirtyDaysDashboard(): Result<DashboardData> {
        val thirtyDaysAgo = Date(System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000))
        val today = Date()
        
        return execute(thirtyDaysAgo, today)
    }
    
    /**
     * Get dashboard data for current week
     */
    suspend fun getCurrentWeekDashboard(): Result<DashboardData> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val startDate = calendar.time
        val endDate = Date()
        
        return execute(startDate, endDate)
    }
    
    /**
     * Get dashboard comparison between two periods
     */
    suspend fun getDashboardComparison(
        currentPeriodStart: Date,
        currentPeriodEnd: Date,
        previousPeriodStart: Date,
        previousPeriodEnd: Date
    ): Result<DashboardComparison> {
        return try {
            val currentData = repository.getDashboardData(currentPeriodStart, currentPeriodEnd)
            val previousData = repository.getDashboardData(previousPeriodStart, previousPeriodEnd)
            
            val comparison = compareDashboardData(currentData, previousData)
            
            Result.success(comparison)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dashboard comparison", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get this month vs last month dashboard comparison
     */
    suspend fun getMonthlyComparison(): Result<DashboardComparison> {
        val now = Calendar.getInstance()
        
        // Current month
        val currentMonthStart = Calendar.getInstance()
        currentMonthStart.set(Calendar.DAY_OF_MONTH, 1)
        currentMonthStart.set(Calendar.HOUR_OF_DAY, 0)
        currentMonthStart.set(Calendar.MINUTE, 0)
        currentMonthStart.set(Calendar.SECOND, 0)
        currentMonthStart.set(Calendar.MILLISECOND, 0)
        
        // Last month
        val lastMonthStart = Calendar.getInstance()
        lastMonthStart.add(Calendar.MONTH, -1)
        lastMonthStart.set(Calendar.DAY_OF_MONTH, 1)
        lastMonthStart.set(Calendar.HOUR_OF_DAY, 0)
        lastMonthStart.set(Calendar.MINUTE, 0)
        lastMonthStart.set(Calendar.SECOND, 0)
        lastMonthStart.set(Calendar.MILLISECOND, 0)
        
        val lastMonthEnd = Calendar.getInstance()
        lastMonthEnd.add(Calendar.MONTH, -1)
        lastMonthEnd.set(Calendar.DAY_OF_MONTH, lastMonthEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
        lastMonthEnd.set(Calendar.HOUR_OF_DAY, 23)
        lastMonthEnd.set(Calendar.MINUTE, 59)
        lastMonthEnd.set(Calendar.SECOND, 59)
        lastMonthEnd.set(Calendar.MILLISECOND, 999)
        
        return getDashboardComparison(
            currentMonthStart.time, now.time,
            lastMonthStart.time, lastMonthEnd.time
        )
    }
    
    /**
     * Analyze dashboard data with business intelligence
     */
    private fun analyzeDashboardData(dashboardData: DashboardData, params: DashboardParams): DashboardAnalysis {
        
        // Calculate averages and insights
        val daysInPeriod = ((params.endDate.time - params.startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
        val averageDailySpending = if (daysInPeriod > 0) dashboardData.totalSpent / daysInPeriod else 0.0
        val averageTransactionAmount = if (dashboardData.transactionCount > 0) {
            dashboardData.totalSpent / dashboardData.transactionCount
        } else {
            0.0
        }
        
        // Top category analysis
        val topCategoryPercentages = if (dashboardData.totalSpent > 0) {
            dashboardData.topCategories.map { category ->
                CategoryAnalysis(
                    categoryName = category.category_name,
                    amount = category.total_amount,
                    percentage = (category.total_amount / dashboardData.totalSpent) * 100,
                    transactionCount = category.transaction_count,
                    color = category.color
                )
            }
        } else {
            emptyList()
        }
        
        // Generate insights
        val insights = generateDashboardInsights(dashboardData, averageDailySpending, daysInPeriod)
        
        return DashboardAnalysis(
            originalData = dashboardData,
            totalSpent = dashboardData.totalSpent,
            transactionCount = dashboardData.transactionCount,
            averageDailySpending = averageDailySpending,
            averageTransactionAmount = averageTransactionAmount,
            daysInPeriod = daysInPeriod,
            categoryAnalysis = topCategoryPercentages,
            insights = insights,
            dateRange = "${params.startDate} to ${params.endDate}"
        )
    }
    
    /**
     * Compare dashboard data between two periods
     */
    private fun compareDashboardData(currentData: DashboardData, previousData: DashboardData): DashboardComparison {
        
        val spendingChange = currentData.totalSpent - previousData.totalSpent
        val spendingPercentageChange = if (previousData.totalSpent > 0) {
            ((currentData.totalSpent - previousData.totalSpent) / previousData.totalSpent) * 100
        } else {
            if (currentData.totalSpent > 0) 100.0 else 0.0
        }
        
        val transactionChange = currentData.transactionCount - previousData.transactionCount
        val transactionPercentageChange = if (previousData.transactionCount > 0) {
            ((currentData.transactionCount - previousData.transactionCount).toDouble() / previousData.transactionCount) * 100
        } else {
            if (currentData.transactionCount > 0) 100.0 else 0.0
        }
        
        // Category comparison
        val currentCategoriesMap = currentData.topCategories.associateBy { it.category_name }
        val previousCategoriesMap = previousData.topCategories.associateBy { it.category_name }
        
        val categoryComparisons = mutableListOf<CategoryComparison>()
        
        // Compare current categories with previous
        currentCategoriesMap.forEach { (categoryName, current) ->
            val previous = previousCategoriesMap[categoryName]
            val change = if (previous != null) {
                current.total_amount - previous.total_amount
            } else {
                current.total_amount // New category
            }
            
            val percentageChange = if (previous != null && previous.total_amount > 0) {
                ((current.total_amount - previous.total_amount) / previous.total_amount) * 100
            } else {
                100.0 // New category or previous was 0
            }
            
            categoryComparisons.add(
                CategoryComparison(
                    categoryName = categoryName,
                    currentAmount = current.total_amount,
                    previousAmount = previous?.total_amount ?: 0.0,
                    change = change,
                    percentageChange = percentageChange
                )
            )
        }
        
        return DashboardComparison(
            currentData = currentData,
            previousData = previousData,
            spendingChange = spendingChange,
            spendingPercentageChange = spendingPercentageChange,
            transactionChange = transactionChange,
            transactionPercentageChange = transactionPercentageChange,
            categoryComparisons = categoryComparisons.sortedByDescending { it.currentAmount }
        )
    }
    
    /**
     * Generate dashboard insights based on spending patterns
     */
    private fun generateDashboardInsights(
        dashboardData: DashboardData,
        averageDailySpending: Double,
        daysInPeriod: Int
    ): List<String> {
        
        val insights = mutableListOf<String>()
        
        // Spending insights
        when {
            averageDailySpending > 2000 -> insights.add("High daily spending detected. Consider reviewing your budget.")
            averageDailySpending < 500 -> insights.add("Good spending control! Your daily average is under ₹500.")
            else -> insights.add("Your daily spending is moderate at ₹${String.format("%.0f", averageDailySpending)}.")
        }
        
        // Transaction frequency insights
        val averageTransactionsPerDay = if (daysInPeriod > 0) dashboardData.transactionCount.toDouble() / daysInPeriod else 0.0
        when {
            averageTransactionsPerDay > 10 -> insights.add("High transaction frequency. Consider consolidating purchases.")
            averageTransactionsPerDay < 2 -> insights.add("Low transaction frequency. You make large, planned purchases.")
            else -> insights.add("Moderate transaction frequency of ${String.format("%.1f", averageTransactionsPerDay)} per day.")
        }
        
        // Category insights
        if (dashboardData.topCategories.isNotEmpty()) {
            val topCategory = dashboardData.topCategories.first()
            val topCategoryPercentage = if (dashboardData.totalSpent > 0) {
                (topCategory.total_amount / dashboardData.totalSpent) * 100
            } else {
                0.0
            }
            
            if (topCategoryPercentage > 50) {
                insights.add("${topCategory.category_name} dominates your spending (${String.format("%.0f", topCategoryPercentage)}%). Consider diversifying.")
            } else {
                insights.add("${topCategory.category_name} is your top category at ${String.format("%.0f", topCategoryPercentage)}% of spending.")
            }
        }
        
        return insights
    }
}

/**
 * Parameters for dashboard data analysis
 */
data class DashboardParams(
    val startDate: Date,
    val endDate: Date,
    val includeInsights: Boolean = true,
    val categoryLimit: Int = 6,
    val merchantLimit: Int = 5
)

/**
 * Enhanced dashboard analysis result
 */
data class DashboardAnalysis(
    val originalData: DashboardData,
    val totalSpent: Double,
    val transactionCount: Int,
    val averageDailySpending: Double,
    val averageTransactionAmount: Double,
    val daysInPeriod: Int,
    val categoryAnalysis: List<CategoryAnalysis>,
    val insights: List<String>,
    val dateRange: String
)

/**
 * Category analysis with percentage
 */
data class CategoryAnalysis(
    val categoryName: String,
    val amount: Double,
    val percentage: Double,
    val transactionCount: Int,
    val color: String
)

/**
 * Dashboard comparison result
 */
data class DashboardComparison(
    val currentData: DashboardData,
    val previousData: DashboardData,
    val spendingChange: Double,
    val spendingPercentageChange: Double,
    val transactionChange: Int,
    val transactionPercentageChange: Double,
    val categoryComparisons: List<CategoryComparison>
)

/**
 * Individual category comparison
 */
data class CategoryComparison(
    val categoryName: String,
    val currentAmount: Double,
    val previousAmount: Double,
    val change: Double,
    val percentageChange: Double
)