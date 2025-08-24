package com.expensemanager.app.data.api.insights

import com.expensemanager.app.data.dao.CategorySpendingResult
import com.expensemanager.app.data.dao.MerchantSpending
import com.expensemanager.app.data.models.AIInsight
import com.expensemanager.app.data.models.MonthlySummary
import com.expensemanager.app.data.repository.DashboardData
import java.util.*

/**
 * Mapper utility to convert between domain models and API models
 */
object InsightsDataMapper {
    
    /**
     * Convert dashboard data to transaction summary for API
     */
    fun mapToTransactionSummary(
        dashboardData: DashboardData,
        monthlyTrends: List<MonthlySummary>
    ): TransactionSummary {
        return TransactionSummary(
            totalSpent = dashboardData.totalSpent,
            transactionCount = dashboardData.transactionCount,
            categoryBreakdown = dashboardData.topCategories.map { it.toCategorySpending() },
            topMerchants = dashboardData.topMerchants.map { it.toMerchantSpending() },
            monthlyTrends = monthlyTrends.map { it.toMonthlyTrend() }
        )
    }
    
    /**
     * Create context data for API request
     */
    fun createContextData(
        monthlyBudget: Double?,
        previousMonthSpent: Double,
        budgetProgressPercentage: Int,
        currency: String = "INR"
    ): ContextData {
        val calendar = Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysRemaining = daysInMonth - currentDay
        
        return ContextData(
            monthlyBudget = monthlyBudget,
            previousMonthSpent = previousMonthSpent,
            budgetProgressPercentage = budgetProgressPercentage,
            daysRemainingInMonth = daysRemaining,
            userPreferences = UserPreferences(
                currency = currency,
                primaryCategories = listOf("Food & Dining", "Transportation", "Groceries"),
                savingsGoal = monthlyBudget?.let { it * 0.2 }, // 20% savings goal
                riskTolerance = "medium"
            )
        )
    }
    
    /**
     * Convert API insight DTO to domain model
     */
    fun mapToDomainInsight(dto: AIInsightDto): AIInsight {
        return AIInsight(
            id = dto.id,
            type = dto.type.toInsightType(),
            title = dto.title,
            description = dto.description,
            actionableAdvice = dto.actionableAdvice,
            impactAmount = dto.impactAmount,
            priority = dto.priority.toInsightPriority(),
            isRead = false,
            createdAt = Date(),
            validUntil = dto.validUntil?.let { Date(it * 1000) } // Convert Unix timestamp to Date
        )
    }
    
    /**
     * Convert list of API insights to domain models
     */
    fun mapToDomainInsights(response: AIInsightsResponse): List<AIInsight> {
        return response.insights.map { mapToDomainInsight(it) }
    }
}

/**
 * Extension functions for data conversion
 */
private fun CategorySpendingResult.toCategorySpending(): CategorySpending {
    return CategorySpending(
        categoryName = this.category_name,
        totalAmount = this.total_amount,
        transactionCount = this.transaction_count,
        percentage = 0.0 // Will be calculated on backend
    )
}

private fun MerchantSpending.toMerchantSpending(): com.expensemanager.app.data.api.insights.MerchantSpending {
    return com.expensemanager.app.data.api.insights.MerchantSpending(
        merchantName = this.normalized_merchant,
        totalAmount = this.total_amount,
        transactionCount = this.transaction_count
    )
}

private fun MonthlySummary.toMonthlyTrend(): MonthlyTrend {
    return MonthlyTrend(
        month = this.month,
        totalAmount = this.totalAmount,
        transactionCount = this.transactionCount,
        averagePerTransaction = this.averagePerTransaction
    )
}

/**
 * Generate unique user ID (in production, this would come from authentication)
 */
fun generateUserId(): String {
    return "user_${System.currentTimeMillis()}_${Random().nextInt(1000)}"
}