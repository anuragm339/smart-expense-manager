package com.expensemanager.app.data.api.insights

import com.expensemanager.app.data.models.AIInsight
import com.expensemanager.app.data.models.InsightPriority
import com.expensemanager.app.data.models.InsightType
import java.util.Date

/**
 * Mapper to convert your updated generic AI API response format to Android app's expected format
 */
object YourApiMapper {
    
    /**
     * Convert your updated generic API response to AIInsight list
     */
    fun mapToAIInsights(response: YourApiResponse): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val apiInsights = response.insights
        
        // Convert Spending Forecast
        apiInsights.spendingForecast?.let { forecast ->
            val currentSpent = forecast.currentMonthSpent ?: 0.0
            val previousSpent = forecast.previousMonthSpent ?: 0.0
            val forecastAmount = parseAmountFromString(forecast.forecastNextMonth) ?: run {
                // If forecast is text like "uncertain", calculate based on trend and current spending
                when (forecast.trend?.lowercase()) {
                    "increasing" -> currentSpent * 1.15 // 15% increase
                    "decreasing" -> currentSpent * 0.85 // 15% decrease
                    else -> currentSpent * 1.05 // 5% increase as default
                }
            }
            val trend = forecast.trend ?: "stable"
            
            val description = buildString {
                append("Based on your current spending pattern, ")
                if (forecastAmount > 0) {
                    append("you're likely to spend ₹${formatAmount(forecastAmount)} next month. ")
                }
                append("Current month: ₹${formatAmount(currentSpent)}, ")
                append("Previous month: ₹${formatAmount(previousSpent)}.")
                if (trend == "upward") {
                    append(" Your spending is trending upward.")
                } else if (trend == "downward") {
                    append(" Your spending is trending downward.")
                }
            }
            
            insights.add(
                AIInsight(
                    id = "spending_forecast_${System.currentTimeMillis()}",
                    type = InsightType.SPENDING_FORECAST,
                    title = "Spending Forecast",
                    description = description,
                    actionableAdvice = getTrendAdvice(trend, forecastAmount, currentSpent),
                    impactAmount = forecastAmount,
                    priority = if (forecastAmount > currentSpent * 1.2) InsightPriority.HIGH else InsightPriority.MEDIUM,
                    createdAt = Date()
                )
            )
        }
        
        // Convert Pattern Alerts
        apiInsights.patternAlert?.alerts?.forEachIndexed { index, alert ->
            if (alert.isNotEmpty()) {
                insights.add(
                    AIInsight(
                        id = "pattern_alert_${index}_${System.currentTimeMillis()}",
                        type = InsightType.PATTERN_ALERT,
                        title = "Pattern Alert",
                        description = alert,
                        actionableAdvice = "Review this spending pattern and consider adjustments to optimize your budget.",
                        impactAmount = 800.0 + (index * 200.0),
                        priority = InsightPriority.HIGH,
                        createdAt = Date()
                    )
                )
            }
        }
        
        // Convert Budget Optimization
        apiInsights.budgetOptimization?.let { budget ->
            val suggestedBudget = parseAmountFromString(budget.suggestedMonthlyBudget) ?: 0.0
            
            budget.recommendations?.forEachIndexed { index, recommendation ->
                if (recommendation.isNotEmpty()) {
                    val title = if (index == 0 && suggestedBudget > 0) {
                        "Budget Optimization (Suggested: ₹${formatAmount(suggestedBudget)})"
                    } else {
                        "Budget Recommendation ${index + 1}"
                    }
                    
                    insights.add(
                        AIInsight(
                            id = "budget_optimization_${index}_${System.currentTimeMillis()}",
                            type = InsightType.BUDGET_OPTIMIZATION,
                            title = title,
                            description = recommendation,
                            actionableAdvice = "Implement this recommendation to improve your budget management.",
                            impactAmount = suggestedBudget / (index + 1),
                            priority = InsightPriority.MEDIUM,
                            createdAt = Date()
                        )
                    )
                }
            }
        }
        
        // Convert Savings Opportunities
        apiInsights.savingsOpportunity?.recommendations?.forEachIndexed { index, recommendation ->
            if (recommendation.isNotEmpty()) {
                // Calculate more realistic savings based on spending forecast
                val currentSpent = apiInsights.spendingForecast?.currentMonthSpent ?: 50000.0
                val baseSavingsPercentage = when {
                    currentSpent > 50000 -> 0.08 // 8% potential savings for higher spenders
                    currentSpent > 30000 -> 0.06 // 6% for medium spenders
                    currentSpent > 15000 -> 0.04 // 4% for moderate spenders
                    else -> 0.03 // 3% for lower spenders
                }
                val calculatedSavings = currentSpent * baseSavingsPercentage * (index + 1)
                
                insights.add(
                    AIInsight(
                        id = "savings_opportunity_${index}_${System.currentTimeMillis()}",
                        type = InsightType.SAVINGS_OPPORTUNITY,
                        title = "Savings Opportunity ${index + 1}",
                        description = recommendation,
                        actionableAdvice = "Follow this suggestion to increase your savings potential.",
                        impactAmount = calculatedSavings, // Dynamic savings based on actual spending
                        priority = InsightPriority.MEDIUM,
                        createdAt = Date()
                    )
                )
            }
        }
        
        // Convert Anomaly Detection
        apiInsights.anomalyDetection?.anomalies?.forEachIndexed { index, anomaly ->
            if (anomaly.isNotEmpty()) {
                insights.add(
                    AIInsight(
                        id = "anomaly_detection_${index}_${System.currentTimeMillis()}",
                        type = InsightType.PATTERN_ALERT,
                        title = "Spending Anomaly Detected",
                        description = anomaly,
                        actionableAdvice = "Investigate this unusual spending pattern and adjust if necessary.",
                        impactAmount = 1200.0 + (index * 400.0),
                        priority = InsightPriority.URGENT,
                        createdAt = Date()
                    )
                )
            }
        }
        
        // Convert Merchant Efficiency
        apiInsights.merchantEfficiency?.topMerchants?.forEachIndexed { index, merchant ->
            val avgTransaction = merchant.avgTransaction ?: 0.0
            val merchantName = merchant.merchantName
            val insight = merchant.insight ?: "Review your spending at this merchant."
            
            val description = buildString {
                append("$merchantName: ")
                if (avgTransaction > 0) {
                    append("Average transaction ₹${formatAmount(avgTransaction)}. ")
                }
                append(insight)
            }
            
            insights.add(
                AIInsight(
                    id = "merchant_efficiency_${index}_${System.currentTimeMillis()}",
                    type = InsightType.MERCHANT_RECOMMENDATION,
                    title = "Merchant Analysis: $merchantName",
                    description = description,
                    actionableAdvice = insight,
                    impactAmount = avgTransaction * 5, // Estimated monthly impact
                    priority = if (index == 0) InsightPriority.MEDIUM else InsightPriority.LOW,
                    createdAt = Date()
                )
            )
        }
        
        // If no insights were created, add a default one
        if (insights.isEmpty()) {
            insights.add(
                AIInsight(
                    id = "default_insight_${System.currentTimeMillis()}",
                    type = InsightType.SPENDING_FORECAST,
                    title = "Financial Overview",
                    description = "Your financial data has been analyzed. Continue tracking your expenses for more detailed insights.",
                    actionableAdvice = "Keep recording your transactions to get personalized financial recommendations.",
                    impactAmount = 0.0,
                    priority = InsightPriority.LOW,
                    createdAt = Date()
                )
            )
        }
        
        return insights
    }
    
    /**
     * Parse amount from string (handles "1600", "₹1600", etc.)
     */
    private fun parseAmountFromString(amountStr: String?): Double? {
        if (amountStr.isNullOrEmpty()) return null
        
        return try {
            // Remove currency symbols and parse
            amountStr.replace("₹", "").replace(",", "").trim().toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Format amount for display
     */
    private fun formatAmount(amount: Double): String {
        return if (amount >= 1000) {
            String.format("%.0f", amount)
        } else {
            String.format("%.2f", amount)
        }
    }
    
    /**
     * Get trend-based advice
     */
    private fun getTrendAdvice(trend: String, forecastAmount: Double, currentAmount: Double): String {
        return when (trend.lowercase()) {
            "upward" -> "Your spending is increasing. Consider reviewing your expenses and setting stricter budget limits."
            "downward" -> "Great job! Your spending is decreasing. Keep up the good financial discipline."
            "stable" -> "Your spending is consistent. Look for opportunities to optimize and save more."
            else -> {
                if (forecastAmount > currentAmount * 1.1) {
                    "Monitor your spending closely to stay within budget."
                } else {
                    "Continue your current spending patterns while looking for optimization opportunities."
                }
            }
        }
    }
}