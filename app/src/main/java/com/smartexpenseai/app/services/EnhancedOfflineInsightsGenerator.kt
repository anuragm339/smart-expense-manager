package com.smartexpenseai.app.services

import com.smartexpenseai.app.data.models.*
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Enhanced offline insights generator with sophisticated rule-based analysis
 * Provides meaningful insights when AI services are unavailable
 */
class EnhancedOfflineInsightsGenerator {

    companion object {
        private const val TAG = "EnhancedOfflineInsightsGenerator"
        private const val HIGH_SPENDING_THRESHOLD = 0.3 // 30% of total spending
        private const val UNUSUAL_TRANSACTION_MULTIPLIER = 3.0 // 3x average
        private const val FREQUENT_MERCHANT_THRESHOLD = 3 // 3+ transactions
    }

    private val logger = StructuredLogger("INSIGHTS", TAG)

    /**
     * Generate comprehensive offline insights
     */
    fun generateAdvancedInsights(transactions: List<Transaction>): List<AIInsight> {
        if (transactions.isEmpty()) {
            return listOf(createEmptyStateInsight())
        }

        val insights = mutableListOf<AIInsight>()

        try {
            // Core analysis insights
            insights.addAll(generateSpendingAnalysis(transactions))
            insights.addAll(generateCategoryInsights(transactions))
            insights.addAll(generateMerchantInsights(transactions))
            insights.addAll(generateTimeTrendInsights(transactions))
            insights.addAll(generateBehaviorPatternInsights(transactions))
            insights.addAll(generateBudgetingInsights(transactions))

            // Sort by priority and limit results
            return insights
                .sortedBy { it.priority.ordinal }
                .take(6) // Limit to 6 insights for better UX

        } catch (e: Exception) {
            logger.error("generateAdvancedInsights", "Error generating offline insights", e)
            return listOf(createErrorFallbackInsight())
        }
    }

    /**
     * Spending analysis insights
     */
    private fun generateSpendingAnalysis(transactions: List<Transaction>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val totalSpending = transactions.sumOf { it.amount }
        val avgTransaction = totalSpending / transactions.size

        // Overall spending summary
        insights.add(
            AIInsight(
                id = "offline_spending_summary_${System.currentTimeMillis()}",
                type = InsightType.SPENDING_FORECAST,
                title = "Spending Overview",
                description = "Total spending: ₹${totalSpending.roundToInt()} across ${transactions.size} transactions (Avg: ₹${avgTransaction.roundToInt()} per transaction)",
                actionableAdvice = getSpendingAdvice(totalSpending, transactions.size),
                impactAmount = totalSpending,
                priority = InsightPriority.MEDIUM
            )
        )

        // Large transaction alert
        val largeTransactions = transactions.filter { it.amount > avgTransaction * UNUSUAL_TRANSACTION_MULTIPLIER }
        if (largeTransactions.isNotEmpty()) {
            val largestTransaction = largeTransactions.maxByOrNull { it.amount }!!
            insights.add(
                AIInsight(
                    id = "offline_large_transaction_${System.currentTimeMillis()}",
                    type = InsightType.PATTERN_ALERT,
                    title = "Large Transaction Alert",
                    description = "Found ${largeTransactions.size} unusually large transaction${if (largeTransactions.size > 1) "s" else ""}, including ₹${largestTransaction.amount.roundToInt()} at ${largestTransaction.merchant}",
                    actionableAdvice = "Review these large expenses to ensure they align with your budget goals",
                    impactAmount = largeTransactions.sumOf { it.amount },
                    priority = InsightPriority.HIGH
                )
            )
        }

        return insights
    }

    /**
     * Category-based insights
     */
    private fun generateCategoryInsights(transactions: List<Transaction>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val categorySpending = transactions.groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        if (categorySpending.isNotEmpty()) {
            val topCategory = categorySpending.first()
            val totalSpending = transactions.sumOf { it.amount }
            val percentage = (topCategory.second / totalSpending * 100).roundToInt()

            insights.add(
                AIInsight(
                    id = "offline_top_category_${System.currentTimeMillis()}",
                    type = InsightType.CATEGORY_ANALYSIS,
                    title = "Top Spending Category",
                    description = "${topCategory.first} dominates your spending at ₹${topCategory.second.roundToInt()} (${percentage}%)",
                    actionableAdvice = getCategoryAdvice(topCategory.first, percentage),
                    impactAmount = topCategory.second,
                    priority = if (percentage > 50) InsightPriority.HIGH else InsightPriority.MEDIUM
                )
            )

            // Category balance insight
            if (categorySpending.size >= 3) {
                val categoryBalance = calculateCategoryBalance(categorySpending)
                insights.add(
                    AIInsight(
                        id = "offline_category_balance_${System.currentTimeMillis()}",
                        type = InsightType.OPTIMIZATION_TIP,
                        title = "Spending Distribution",
                        description = categoryBalance.description,
                        actionableAdvice = categoryBalance.advice,
                        priority = InsightPriority.LOW
                    )
                )
            }
        }

        return insights
    }

    /**
     * Merchant-based insights
     */
    private fun generateMerchantInsights(transactions: List<Transaction>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val merchantData = transactions.groupBy { it.merchant }
            .mapValues { (_, txns) ->
                MerchantData(
                    count = txns.size,
                    totalAmount = txns.sumOf { it.amount },
                    avgAmount = txns.sumOf { it.amount } / txns.size
                )
            }

        // Frequent merchant insight
        val frequentMerchants = merchantData.filter { it.value.count >= FREQUENT_MERCHANT_THRESHOLD }
            .toList()
            .sortedByDescending { it.second.totalAmount }

        if (frequentMerchants.isNotEmpty()) {
            val topMerchant = frequentMerchants.first()
            insights.add(
                AIInsight(
                    id = "offline_frequent_merchant_${System.currentTimeMillis()}",
                    type = InsightType.PATTERN_ALERT,
                    title = "Frequent Merchant",
                    description = "You've made ${topMerchant.second.count} transactions at ${topMerchant.first} totaling ₹${topMerchant.second.totalAmount.roundToInt()}",
                    actionableAdvice = "Consider if this frequent spending aligns with your budget priorities",
                    impactAmount = topMerchant.second.totalAmount,
                    priority = InsightPriority.MEDIUM
                )
            )
        }

        // Expensive merchant insight
        val expensiveMerchant = merchantData.maxByOrNull { it.value.avgAmount }
        if (expensiveMerchant != null && expensiveMerchant.value.avgAmount > 1000) {
            insights.add(
                AIInsight(
                    id = "offline_expensive_merchant_${System.currentTimeMillis()}",
                    type = InsightType.OPTIMIZATION_TIP,
                    title = "High-Value Merchant",
                    description = "${expensiveMerchant.key} has the highest average transaction value at ₹${expensiveMerchant.value.avgAmount.roundToInt()}",
                    actionableAdvice = "Review transactions at this merchant for potential cost optimization",
                    impactAmount = expensiveMerchant.value.totalAmount,
                    priority = InsightPriority.LOW
                )
            )
        }

        return insights
    }

    /**
     * Time-based trend insights
     */
    private fun generateTimeTrendInsights(transactions: List<Transaction>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()

        if (transactions.size < 7) return insights // Need at least a week of data

        val sortedTransactions = transactions.sortedBy { it.date }
        val recentTransactions = sortedTransactions.takeLast(7)
        val olderTransactions = sortedTransactions.dropLast(7).takeLast(7)

        if (olderTransactions.isNotEmpty()) {
            val recentAvg = recentTransactions.sumOf { it.amount } / recentTransactions.size
            val olderAvg = olderTransactions.sumOf { it.amount } / olderTransactions.size
            val change = ((recentAvg - olderAvg) / olderAvg * 100).roundToInt()

            if (abs(change) >= 20) { // Significant change threshold
                insights.add(
                    AIInsight(
                        id = "offline_spending_trend_${System.currentTimeMillis()}",
                        type = InsightType.PATTERN_ALERT,
                        title = "Spending Trend Alert",
                        description = "Your average spending has ${if (change > 0) "increased" else "decreased"} by ${abs(change)}% in recent transactions",
                        actionableAdvice = if (change > 0) "Consider reviewing recent expenses to identify spending drivers" else "Great job reducing your spending! Keep up the good work",
                        impactAmount = recentTransactions.sumOf { it.amount },
                        priority = if (abs(change) > 50) InsightPriority.HIGH else InsightPriority.MEDIUM
                    )
                )
            }
        }

        return insights
    }

    /**
     * Behavioral pattern insights
     */
    private fun generateBehaviorPatternInsights(transactions: List<Transaction>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()

        // Small transactions pattern
        val smallTransactions = transactions.filter { it.amount < 100 }
        if (smallTransactions.size > transactions.size * 0.6) { // More than 60% are small
            val totalSmall = smallTransactions.sumOf { it.amount }
            insights.add(
                AIInsight(
                    id = "offline_small_transactions_${System.currentTimeMillis()}",
                    type = InsightType.OPTIMIZATION_TIP,
                    title = "Frequent Small Purchases",
                    description = "${smallTransactions.size} small transactions (under ₹100) add up to ₹${totalSmall.roundToInt()}",
                    actionableAdvice = "Consider consolidating small purchases or setting a minimum spending threshold",
                    impactAmount = totalSmall,
                    priority = InsightPriority.LOW
                )
            )
        }

        return insights
    }

    /**
     * Budgeting and savings insights
     */
    private fun generateBudgetingInsights(transactions: List<Transaction>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val totalSpending = transactions.sumOf { it.amount }
        val dailyAverage = totalSpending / maxOf(1, transactions.distinctBy { it.date.toString().substring(0, 10) }.size)

        // Daily spending insight
        insights.add(
            AIInsight(
                id = "offline_daily_budget_${System.currentTimeMillis()}",
                type = InsightType.BUDGET_OPTIMIZATION,
                title = "Daily Spending Average",
                description = "Your daily average spending is ₹${dailyAverage.roundToInt()}",
                actionableAdvice = getBudgetAdvice(dailyAverage),
                impactAmount = dailyAverage,
                priority = InsightPriority.LOW
            )
        )

        return insights
    }

    // Helper methods

    private fun getSpendingAdvice(totalSpending: Double, transactionCount: Int): String {
        return when {
            totalSpending > 50000 -> "Consider reviewing your high spending patterns and setting monthly budget limits"
            totalSpending > 20000 -> "Track your expenses more closely to identify potential savings opportunities"
            transactionCount > 50 -> "You're actively tracking expenses - great habit for financial awareness!"
            else -> "Continue building your expense tracking habit for better financial insights"
        }
    }

    private fun getCategoryAdvice(category: String, percentage: Int): String {
        return when {
            percentage > 60 -> "This category dominates your spending. Consider setting limits or finding alternatives"
            percentage > 40 -> "Significant spending in this category. Review for optimization opportunities"
            else -> "This is your top spending category but seems well-balanced"
        }
    }

    private fun getBudgetAdvice(dailyAverage: Double): String {
        return when {
            dailyAverage > 2000 -> "Consider setting a daily spending limit below ₹2000 to control expenses"
            dailyAverage > 1000 -> "Your daily spending is moderate. Track it against your monthly budget goals"
            else -> "Your daily spending is well-controlled. Keep monitoring for consistency"
        }
    }

    private fun calculateCategoryBalance(categorySpending: List<Pair<String, Double>>): CategoryBalance {
        val top3 = categorySpending.take(3)
        val top3Percentage = (top3.sumOf { it.second } / categorySpending.sumOf { it.second } * 100).roundToInt()

        return if (top3Percentage > 80) {
            CategoryBalance(
                "Your spending is concentrated in just 3 categories (${top3Percentage}%)",
                "Consider diversifying your spending or reviewing if this concentration is intentional"
            )
        } else {
            CategoryBalance(
                "Your spending is well-distributed across ${categorySpending.size} categories",
                "Good balance! Continue monitoring to maintain healthy spending diversity"
            )
        }
    }

    private fun createEmptyStateInsight(): AIInsight {
        return AIInsight(
            id = "offline_empty_${System.currentTimeMillis()}",
            type = InsightType.CATEGORY_ANALYSIS,
            title = "Start Your Financial Journey",
            description = "Begin tracking your expenses to unlock personalized insights and recommendations",
            actionableAdvice = "Add your first transaction to start building your financial profile",
            priority = InsightPriority.LOW
        )
    }

    private fun createErrorFallbackInsight(): AIInsight {
        return AIInsight(
            id = "offline_error_${System.currentTimeMillis()}",
            type = InsightType.CATEGORY_ANALYSIS,
            title = "Analysis Temporarily Unavailable",
            description = "Unable to analyze your transactions at the moment",
            actionableAdvice = "Try again later or add more transactions for better analysis",
            priority = InsightPriority.LOW
        )
    }

    // Data classes for internal calculations
    private data class MerchantData(
        val count: Int,
        val totalAmount: Double,
        val avgAmount: Double
    )

    private data class CategoryBalance(
        val description: String,
        val advice: String
    )
}
