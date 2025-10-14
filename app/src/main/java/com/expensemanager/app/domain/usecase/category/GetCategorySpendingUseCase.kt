package com.expensemanager.app.domain.usecase.category

import timber.log.Timber
import com.expensemanager.app.data.dao.CategorySpendingResult
import com.expensemanager.app.domain.repository.CategoryRepositoryInterface
import com.expensemanager.app.services.DateRangeService
import com.expensemanager.app.services.DateRangeService.Companion.DateRangeType
import java.util.Date
import javax.inject.Inject

/**
 * Use case for getting category spending data with business logic
 * Handles spending calculations, filtering, and analysis
 */
class GetCategorySpendingUseCase @Inject constructor(
    private val repository: CategoryRepositoryInterface,
    private val dateRangeService: DateRangeService
) {
    
    companion object {
        private const val TAG = "GetCategorySpendingUseCase"
    }
    
    /**
     * Get category spending for date range
     */
    suspend fun execute(startDate: Date, endDate: Date): Result<List<CategorySpendingResult>> {
        return try {
            Timber.tag(TAG).d("Getting category spending from $startDate to $endDate")
            val categorySpending = repository.getCategorySpending(startDate, endDate)
            Timber.tag(TAG).d("Retrieved spending data for ${categorySpending.size} categories")
            Result.success(categorySpending)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting category spending")
            Result.failure(e)
        }
    }
    
    /**
     * Get category spending with filtering and analysis
     */
    suspend fun execute(params: CategorySpendingParams): Result<CategorySpendingAnalysis> {
        return try {
            Timber.tag(TAG).d("Getting category spending with analysis params: $params")
            
            val categorySpending = repository.getCategorySpending(params.startDate, params.endDate)
            val analysis = analyzeCategorySpending(categorySpending, params)
            
            Timber.tag(TAG).d("Category spending analysis completed")
            Result.success(analysis)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting category spending analysis")
            Result.failure(e)
        }
    }
    
    /**
     * Get current month category spending
     */
    suspend fun getCurrentMonthSpending(): Result<List<CategorySpendingResult>> {
        val (startDate, endDate) = dateRangeService.getDateRange(DateRangeType.CURRENT_MONTH)
        return execute(startDate, endDate)
    }
    
    /**
     * Get current month category spending (updated to match Dashboard logic)
     */
    suspend fun getLastThirtyDaysSpending(): Result<List<CategorySpendingResult>> {
        // Use current month instead of 30-day hardcoded period to match Dashboard
        val (startDate, endDate) = dateRangeService.getDateRange(DateRangeType.CURRENT_MONTH)
        return execute(startDate, endDate)
    }
    
    /**
     * Get last 7 days category spending
     */
    suspend fun getLast7DaysSpending(): Result<List<CategorySpendingResult>> {
        val (startDate, endDate) = dateRangeService.getDateRange(DateRangeType.LAST_7_DAYS)
        Timber.tag(TAG).d("Getting last 7 days spending from $startDate to $endDate")
        return execute(startDate, endDate)
    }
    
    /**
     * Get last 30 days category spending (actual 30 days, not current month)
     */
    suspend fun getLast30DaysSpending(): Result<List<CategorySpendingResult>> {
        val (startDate, endDate) = dateRangeService.getDateRange(DateRangeType.LAST_30_DAYS)
        Timber.tag(TAG).d("Getting last 30 days spending from $startDate to $endDate")
        return execute(startDate, endDate)
    }
    
    /**
     * Get last 3 months category spending
     */
    suspend fun getLast3MonthsSpending(): Result<List<CategorySpendingResult>> {
        val (startDate, endDate) = dateRangeService.getDateRange(DateRangeType.LAST_3_MONTHS)
        Timber.tag(TAG).d("Getting last 3 months spending from $startDate to $endDate")
        return execute(startDate, endDate)
    }
    
    /**
     * Get last 6 months category spending
     */
    suspend fun getLast6MonthsSpending(): Result<List<CategorySpendingResult>> {
        val (startDate, endDate) = dateRangeService.getDateRange(DateRangeType.LAST_6_MONTHS)
        Timber.tag(TAG).d("Getting last 6 months spending from $startDate to $endDate")
        return execute(startDate, endDate)
    }
    
    /**
     * Get this year category spending
     */
    suspend fun getThisYearSpending(): Result<List<CategorySpendingResult>> {
        val (startDate, endDate) = dateRangeService.getDateRange(DateRangeType.THIS_YEAR)
        Timber.tag(TAG).d("Getting this year spending from $startDate to $endDate")
        return execute(startDate, endDate)
    }
    
    /**
     * Get category spending comparison between two periods
     */
    suspend fun getSpendingComparison(
        currentPeriodStart: Date,
        currentPeriodEnd: Date,
        previousPeriodStart: Date,
        previousPeriodEnd: Date
    ): Result<CategorySpendingComparison> {
        return try {
            Timber.tag(TAG).d("Getting category spending comparison")
            
            val currentSpending = repository.getCategorySpending(currentPeriodStart, currentPeriodEnd)
            val previousSpending = repository.getCategorySpending(previousPeriodStart, previousPeriodEnd)
            
            val comparison = compareCategorySpending(currentSpending, previousSpending)
            
            Timber.tag(TAG).d("Category spending comparison completed")
            Result.success(comparison)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting category spending comparison")
            Result.failure(e)
        }
    }
    
    /**
     * Get top spending categories
     */
    suspend fun getTopCategories(startDate: Date, endDate: Date, limit: Int = 5): Result<List<CategorySpendingResult>> {
        return try {
            Timber.tag(TAG).d("Getting top $limit spending categories")
            
            val allCategorySpending = repository.getCategorySpending(startDate, endDate)
            val topCategories = allCategorySpending
                .sortedByDescending { it.total_amount }
                .take(limit)
            
            Timber.tag(TAG).d("Retrieved top ${topCategories.size} categories")
            Result.success(topCategories)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting top categories")
            Result.failure(e)
        }
    }
    
    /**
     * Analyze category spending data
     */
    private fun analyzeCategorySpending(
        categorySpending: List<CategorySpendingResult>, 
        params: CategorySpendingParams
    ): CategorySpendingAnalysis {
        
        val filteredSpending = categorySpending
            .let { list ->
                if (params.minAmount > 0.0) {
                    list.filter { it.total_amount >= params.minAmount }
                } else {
                    list
                }
            }
            .let { list ->
                if (params.excludeCategories.isNotEmpty()) {
                    list.filter { it.category_name !in params.excludeCategories }
                } else {
                    list
                }
            }
            .sortedByDescending { it.total_amount }
            .let { list ->
                if (params.limit > 0) {
                    list.take(params.limit)
                } else {
                    list
                }
            }
        
        val totalAmount = filteredSpending.sumOf { it.total_amount }
        val totalTransactions = filteredSpending.sumOf { it.transaction_count }
        val averagePerCategory = if (filteredSpending.isNotEmpty()) totalAmount / filteredSpending.size else 0.0
        
        // Calculate percentages
        val spendingWithPercentages = filteredSpending.map { category ->
            val percentage = if (totalAmount > 0) (category.total_amount / totalAmount) * 100 else 0.0
            CategorySpendingWithPercentage(
                category = category,
                percentage = percentage
            )
        }
        
        return CategorySpendingAnalysis(
            categorySpending = spendingWithPercentages,
            totalAmount = totalAmount,
            totalTransactions = totalTransactions,
            averagePerCategory = averagePerCategory,
            categoryCount = filteredSpending.size,
            dateRange = "${params.startDate} to ${params.endDate}"
        )
    }
    
    /**
     * Compare category spending between two periods
     */
    private fun compareCategorySpending(
        currentSpending: List<CategorySpendingResult>,
        previousSpending: List<CategorySpendingResult>
    ): CategorySpendingComparison {
        
        val currentMap = currentSpending.associateBy { it.category_name }
        val previousMap = previousSpending.associateBy { it.category_name }
        
        val comparisons = mutableListOf<CategoryComparison>()
        
        // Compare existing categories
        for ((categoryName, current) in currentMap) {
            val previous = previousMap[categoryName]
            
            val comparison = if (previous != null) {
                val amountChange = current.total_amount - previous.total_amount
                val percentageChange = if (previous.total_amount > 0) {
                    ((current.total_amount - previous.total_amount) / previous.total_amount) * 100
                } else {
                    100.0 // New spending in this category
                }
                
                CategoryComparison(
                    categoryName = categoryName,
                    currentAmount = current.total_amount,
                    previousAmount = previous.total_amount,
                    amountChange = amountChange,
                    percentageChange = percentageChange,
                    isNewCategory = false
                )
            } else {
                CategoryComparison(
                    categoryName = categoryName,
                    currentAmount = current.total_amount,
                    previousAmount = 0.0,
                    amountChange = current.total_amount,
                    percentageChange = 100.0,
                    isNewCategory = true
                )
            }
            
            comparisons.add(comparison)
        }
        
        val currentTotal = currentSpending.sumOf { it.total_amount }
        val previousTotal = previousSpending.sumOf { it.total_amount }
        val totalChange = currentTotal - previousTotal
        val totalPercentageChange = if (previousTotal > 0) {
            ((currentTotal - previousTotal) / previousTotal) * 100
        } else {
            100.0
        }
        
        return CategorySpendingComparison(
            comparisons = comparisons.sortedByDescending { it.currentAmount },
            currentTotal = currentTotal,
            previousTotal = previousTotal,
            totalChange = totalChange,
            totalPercentageChange = totalPercentageChange
        )
    }
}

/**
 * Parameters for category spending analysis
 */
data class CategorySpendingParams(
    val startDate: Date,
    val endDate: Date,
    val minAmount: Double = 0.0,
    val excludeCategories: List<String> = emptyList(),
    val limit: Int = 0 // 0 means no limit
)

/**
 * Category spending analysis result
 */
data class CategorySpendingAnalysis(
    val categorySpending: List<CategorySpendingWithPercentage>,
    val totalAmount: Double,
    val totalTransactions: Int,
    val averagePerCategory: Double,
    val categoryCount: Int,
    val dateRange: String
)

/**
 * Category spending with percentage
 */
data class CategorySpendingWithPercentage(
    val category: CategorySpendingResult,
    val percentage: Double
)

/**
 * Category spending comparison result
 */
data class CategorySpendingComparison(
    val comparisons: List<CategoryComparison>,
    val currentTotal: Double,
    val previousTotal: Double,
    val totalChange: Double,
    val totalPercentageChange: Double
)

/**
 * Individual category comparison
 */
data class CategoryComparison(
    val categoryName: String,
    val currentAmount: Double,
    val previousAmount: Double,
    val amountChange: Double,
    val percentageChange: Double,
    val isNewCategory: Boolean
)