package com.expensemanager.app.domain.usecase.dashboard

import timber.log.Timber
import com.expensemanager.app.domain.repository.TransactionRepositoryInterface
import com.expensemanager.app.domain.repository.CategoryRepositoryInterface
import java.util.Date
import java.util.Calendar
import javax.inject.Inject

/**
 * Use case for getting budget status and analysis with business logic
 * Handles budget calculations, alerts, and spending vs budget analysis
 */
class GetBudgetStatusUseCase @Inject constructor(
    private val transactionRepository: TransactionRepositoryInterface,
    private val categoryRepository: CategoryRepositoryInterface
) {
    
    companion object {
        private const val TAG = "GetBudgetStatusUseCase"
    }
    
    /**
     * Get overall budget status for current month
     */
    suspend fun getCurrentMonthBudgetStatus(monthlyBudget: Double): Result<BudgetStatus> {
        return try {
            Timber.tag(TAG).d("Getting current month budget status with budget: ₹$monthlyBudget")
            
            val (startDate, endDate) = getCurrentMonthDates()
            val totalSpent = transactionRepository.getTotalSpent(startDate, endDate)
            val transactionCount = transactionRepository.getTransactionCount(startDate, endDate)
            
            val budgetStatus = calculateBudgetStatus(monthlyBudget, totalSpent, startDate, endDate, transactionCount)
            
            Timber.tag(TAG).d("Budget status calculated: ${budgetStatus.status}")
            Result.success(budgetStatus)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting budget status")
            Result.failure(e)
        }
    }
    
    /**
     * Get category-wise budget status
     */
    suspend fun getCategoryBudgetStatus(categoryBudgets: Map<String, Double>): Result<List<CategoryBudgetStatus>> {
        return try {
            Timber.tag(TAG).d("Getting category budget status for ${categoryBudgets.size} categories")
            
            val (startDate, endDate) = getCurrentMonthDates()
            val categorySpending = categoryRepository.getCategorySpending(startDate, endDate)
            
            val categoryStatuses = categoryBudgets.map { (categoryName, budget) ->
                val spending = categorySpending.find { it.category_name == categoryName }?.total_amount ?: 0.0
                val transactionCount = categorySpending.find { it.category_name == categoryName }?.transaction_count ?: 0
                
                calculateCategoryBudgetStatus(categoryName, budget, spending, transactionCount, startDate, endDate)
            }.sortedByDescending { it.spentAmount }
            
            Timber.tag(TAG).d("Category budget status calculated for ${categoryStatuses.size} categories")
            Result.success(categoryStatuses)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting category budget status")
            Result.failure(e)
        }
    }
    
    /**
     * Get budget alerts and recommendations
     */
    suspend fun getBudgetAlerts(monthlyBudget: Double, categoryBudgets: Map<String, Double> = emptyMap()): Result<BudgetAlerts> {
        return try {
            Timber.tag(TAG).d("Getting budget alerts")
            
            val overallStatus = getCurrentMonthBudgetStatus(monthlyBudget).getOrNull()
            val categoryStatuses = if (categoryBudgets.isNotEmpty()) {
                getCategoryBudgetStatus(categoryBudgets).getOrNull() ?: emptyList()
            } else {
                emptyList()
            }
            
            val alerts = generateBudgetAlerts(overallStatus, categoryStatuses)
            
            Timber.tag(TAG).d("Generated ${alerts.criticalAlerts.size} critical and ${alerts.warningAlerts.size} warning alerts")
            Result.success(alerts)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting budget alerts")
            Result.failure(e)
        }
    }
    
    /**
     * Get budget projection for rest of month
     */
    suspend fun getBudgetProjection(monthlyBudget: Double): Result<BudgetProjection> {
        return try {
            Timber.tag(TAG).d("Getting budget projection")
            
            val (startDate, endDate) = getCurrentMonthDates()
            val currentDate = Date()
            val totalSpent = transactionRepository.getTotalSpent(startDate, currentDate)
            
            val projection = calculateBudgetProjection(monthlyBudget, totalSpent, startDate, endDate, currentDate)
            
            Timber.tag(TAG).d("Budget projection calculated")
            Result.success(projection)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting budget projection")
            Result.failure(e)
        }
    }
    
    /**
     * Get daily budget allowance based on remaining budget and days
     */
    suspend fun getDailyBudgetAllowance(monthlyBudget: Double): Result<DailyBudgetAllowance> {
        return try {
            Timber.tag(TAG).d("Getting daily budget allowance")
            
            val (startDate, endDate) = getCurrentMonthDates()
            val currentDate = Date()
            val totalSpent = transactionRepository.getTotalSpent(startDate, currentDate)
            
            val allowance = calculateDailyBudgetAllowance(monthlyBudget, totalSpent, currentDate, endDate)
            
            Timber.tag(TAG).d("Daily budget allowance calculated: ₹${allowance.dailyAllowance}")
            Result.success(allowance)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting daily budget allowance")
            Result.failure(e)
        }
    }
    
    /**
     * Calculate overall budget status
     */
    private fun calculateBudgetStatus(
        budget: Double,
        spent: Double,
        startDate: Date,
        endDate: Date,
        transactionCount: Int
    ): BudgetStatus {
        
        val remaining = budget - spent
        val percentageUsed = if (budget > 0) (spent / budget) * 100 else 100.0
        
        // Calculate days progress
        val totalDays = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
        val daysElapsed = ((Date().time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
        val daysRemaining = maxOf(0, totalDays - daysElapsed)
        val dayProgress = if (totalDays > 0) (daysElapsed.toDouble() / totalDays) * 100 else 100.0
        
        val status = when {
            percentageUsed >= 100 -> BudgetStatusType.EXCEEDED
            percentageUsed >= 90 -> BudgetStatusType.CRITICAL
            percentageUsed >= 75 -> BudgetStatusType.WARNING
            percentageUsed > dayProgress + 10 -> BudgetStatusType.CAUTION // Spending faster than time progress
            else -> BudgetStatusType.ON_TRACK
        }
        
        val averageDaily = if (daysElapsed > 0) spent / daysElapsed else 0.0
        val projectedMonthlySpending = if (totalDays > 0) (spent / daysElapsed) * totalDays else spent
        
        return BudgetStatus(
            budgetAmount = budget,
            spentAmount = spent,
            remainingAmount = remaining,
            percentageUsed = percentageUsed,
            status = status,
            daysElapsed = daysElapsed,
            daysRemaining = daysRemaining,
            dayProgress = dayProgress,
            averageDailySpending = averageDaily,
            projectedMonthlySpending = projectedMonthlySpending,
            transactionCount = transactionCount
        )
    }
    
    /**
     * Calculate category budget status
     */
    private fun calculateCategoryBudgetStatus(
        categoryName: String,
        budget: Double,
        spent: Double,
        transactionCount: Int,
        startDate: Date,
        endDate: Date
    ): CategoryBudgetStatus {
        
        val remaining = budget - spent
        val percentageUsed = if (budget > 0) (spent / budget) * 100 else 100.0
        
        val status = when {
            percentageUsed >= 100 -> BudgetStatusType.EXCEEDED
            percentageUsed >= 90 -> BudgetStatusType.CRITICAL
            percentageUsed >= 75 -> BudgetStatusType.WARNING
            else -> BudgetStatusType.ON_TRACK
        }
        
        return CategoryBudgetStatus(
            categoryName = categoryName,
            budgetAmount = budget,
            spentAmount = spent,
            remainingAmount = remaining,
            percentageUsed = percentageUsed,
            status = status,
            transactionCount = transactionCount
        )
    }
    
    /**
     * Generate budget alerts
     */
    private fun generateBudgetAlerts(
        overallStatus: BudgetStatus?,
        categoryStatuses: List<CategoryBudgetStatus>
    ): BudgetAlerts {
        
        val criticalAlerts = mutableListOf<String>()
        val warningAlerts = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // Overall budget alerts
        overallStatus?.let { status ->
            when (status.status) {
                BudgetStatusType.EXCEEDED -> {
                    criticalAlerts.add("Budget exceeded by ₹${String.format("%.0f", kotlin.math.abs(status.remainingAmount))}")
                }
                BudgetStatusType.CRITICAL -> {
                    criticalAlerts.add("Only ₹${String.format("%.0f", status.remainingAmount)} remaining (${String.format("%.0f", 100 - status.percentageUsed)}%)")
                }
                BudgetStatusType.WARNING -> {
                    warningAlerts.add("Budget 75% used with ${status.daysRemaining} days remaining")
                }
                BudgetStatusType.CAUTION -> {
                    warningAlerts.add("Spending faster than expected pace")
                }
                else -> { /* On track - no alerts */ }
            }
            
            // Daily allowance recommendation
            if (status.daysRemaining > 0 && status.remainingAmount > 0) {
                val dailyAllowance = status.remainingAmount / status.daysRemaining
                recommendations.add("Daily allowance for remaining days: ₹${String.format("%.0f", dailyAllowance)}")
            }
        }
        
        // Category budget alerts
        categoryStatuses.forEach { categoryStatus ->
            when (categoryStatus.status) {
                BudgetStatusType.EXCEEDED -> {
                    criticalAlerts.add("${categoryStatus.categoryName} budget exceeded")
                }
                BudgetStatusType.CRITICAL -> {
                    warningAlerts.add("${categoryStatus.categoryName} budget 90% used")
                }
                else -> { /* Other statuses - no alerts */ }
            }
        }
        
        // General recommendations
        if (overallStatus?.averageDailySpending != null && overallStatus.averageDailySpending > 0) {
            val projectedOverspend = overallStatus.projectedMonthlySpending - overallStatus.budgetAmount
            if (projectedOverspend > 0) {
                recommendations.add("Reduce daily spending by ₹${String.format("%.0f", projectedOverspend / overallStatus.daysRemaining)} to stay on budget")
            }
        }
        
        return BudgetAlerts(
            criticalAlerts = criticalAlerts,
            warningAlerts = warningAlerts,
            recommendations = recommendations
        )
    }
    
    /**
     * Calculate budget projection
     */
    private fun calculateBudgetProjection(
        budget: Double,
        spentSoFar: Double,
        monthStart: Date,
        monthEnd: Date,
        currentDate: Date
    ): BudgetProjection {
        
        val totalDays = ((monthEnd.time - monthStart.time) / (1000 * 60 * 60 * 24)).toInt() + 1
        val daysElapsed = ((currentDate.time - monthStart.time) / (1000 * 60 * 60 * 24)).toInt() + 1
        val daysRemaining = maxOf(0, totalDays - daysElapsed)
        
        val averageDaily = if (daysElapsed > 0) spentSoFar / daysElapsed else 0.0
        val projectedTotal = if (totalDays > 0) averageDaily * totalDays else spentSoFar
        val projectedOverunder = projectedTotal - budget
        
        val confidence = calculateProjectionConfidence(daysElapsed, totalDays, averageDaily)
        
        return BudgetProjection(
            projectedMonthlySpending = projectedTotal,
            projectedOverUnder = projectedOverunder,
            averageDailySpending = averageDaily,
            daysUsedForProjection = daysElapsed,
            confidence = confidence,
            willExceedBudget = projectedTotal > budget
        )
    }
    
    /**
     * Calculate daily budget allowance
     */
    private fun calculateDailyBudgetAllowance(
        budget: Double,
        spentSoFar: Double,
        currentDate: Date,
        monthEnd: Date
    ): DailyBudgetAllowance {
        
        val remaining = budget - spentSoFar
        val daysRemaining = maxOf(1, ((monthEnd.time - currentDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1)
        val dailyAllowance = remaining / daysRemaining
        
        val status = when {
            remaining <= 0 -> "Budget exhausted"
            dailyAllowance < 500 -> "Very tight budget"
            dailyAllowance < 1000 -> "Limited budget"
            else -> "Comfortable budget"
        }
        
        return DailyBudgetAllowance(
            dailyAllowance = maxOf(0.0, dailyAllowance),
            remainingBudget = remaining,
            daysRemaining = daysRemaining,
            status = status
        )
    }
    
    /**
     * Calculate projection confidence
     */
    private fun calculateProjectionConfidence(daysElapsed: Int, totalDays: Int, averageDaily: Double): Double {
        val dataPoints = daysElapsed
        val periodProgress = daysElapsed.toDouble() / totalDays
        
        return when {
            dataPoints < 3 -> 0.3 // Low confidence with less than 3 days
            periodProgress < 0.2 -> 0.5 // Medium-low confidence in first 20% of month
            periodProgress < 0.5 -> 0.7 // Medium confidence
            periodProgress < 0.8 -> 0.85 // High confidence
            else -> 0.95 // Very high confidence near month end
        }
    }
    
    /**
     * Get current month date range
     */
    private fun getCurrentMonthDates(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        // Start of month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        // End of month
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        return Pair(startDate, endDate)
    }
}

/**
 * Overall budget status
 */
data class BudgetStatus(
    val budgetAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val percentageUsed: Double,
    val status: BudgetStatusType,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val dayProgress: Double,
    val averageDailySpending: Double,
    val projectedMonthlySpending: Double,
    val transactionCount: Int
)

/**
 * Category budget status
 */
data class CategoryBudgetStatus(
    val categoryName: String,
    val budgetAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val percentageUsed: Double,
    val status: BudgetStatusType,
    val transactionCount: Int
)

/**
 * Budget alerts and recommendations
 */
data class BudgetAlerts(
    val criticalAlerts: List<String>,
    val warningAlerts: List<String>,
    val recommendations: List<String>
)

/**
 * Budget projection
 */
data class BudgetProjection(
    val projectedMonthlySpending: Double,
    val projectedOverUnder: Double,
    val averageDailySpending: Double,
    val daysUsedForProjection: Int,
    val confidence: Double, // 0.0 to 1.0
    val willExceedBudget: Boolean
)

/**
 * Daily budget allowance
 */
data class DailyBudgetAllowance(
    val dailyAllowance: Double,
    val remainingBudget: Double,
    val daysRemaining: Int,
    val status: String
)

/**
 * Budget status types
 */
enum class BudgetStatusType {
    ON_TRACK, CAUTION, WARNING, CRITICAL, EXCEEDED
}