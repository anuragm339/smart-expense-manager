package com.smartexpenseai.app.data.api.insights

import com.smartexpenseai.app.data.models.AIInsight
import com.smartexpenseai.app.data.models.InsightPriority
import com.smartexpenseai.app.data.models.InsightType
import java.util.Date

/**
 * Simple mapper that directly shows what the API returns without transformations
 */
object DirectApiMapper {
    
    fun mapToAIInsights(response: YourApiResponse): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val apiInsights = response.insights
        
        // Show Spending Forecast as-is from API
        apiInsights.spendingForecast?.let { forecast ->
            insights.add(
                AIInsight(
                    id = "spending_forecast_direct",
                    type = InsightType.SPENDING_FORECAST,
                    title = "Spending Forecast",
                    description = "Current month spent: ₹${formatAmount(forecast.currentMonthSpent ?: 0.0)}\n" +
                            "Previous month spent: ₹${formatAmount(forecast.previousMonthSpent ?: 0.0)}\n" +
                            "Forecast next month: ${forecast.forecastNextMonth ?: "Unknown"}\n" +
                            "Trend: ${forecast.trend ?: "Unknown"}",
                    actionableAdvice = "Direct data from API - Trend: ${forecast.trend}",
                    impactAmount = forecast.currentMonthSpent ?: 0.0,
                    priority = InsightPriority.HIGH,
                    createdAt = Date()
                )
            )
        }
        
        // Show Pattern Alerts exactly as API returns them
        apiInsights.patternAlert?.alerts?.forEachIndexed { index, alert ->
            insights.add(
                AIInsight(
                    id = "pattern_alert_${index}",
                    type = InsightType.PATTERN_ALERT,
                    title = "Pattern Alert",
                    description = alert,
                    actionableAdvice = alert, // Use the same text
                    impactAmount = 0.0, // No calculation, just show API data
                    priority = InsightPriority.HIGH,
                    createdAt = Date()
                )
            )
        }
        
        // Show Budget Optimization exactly as API returns
        apiInsights.budgetOptimization?.let { budget ->
            val title = "Budget Optimization" + if (budget.suggestedMonthlyBudget != "Unknown") 
                " (Suggested: ${budget.suggestedMonthlyBudget})" else ""
            
            budget.recommendations?.forEachIndexed { index, recommendation ->
                insights.add(
                    AIInsight(
                        id = "budget_${index}",
                        type = InsightType.BUDGET_OPTIMIZATION,
                        title = title,
                        description = recommendation,
                        actionableAdvice = recommendation,
                        impactAmount = 0.0, // No artificial calculation
                        priority = InsightPriority.MEDIUM,
                        createdAt = Date()
                    )
                )
            }
        }
        
        // Show Savings Opportunities exactly as API returns
        apiInsights.savingsOpportunity?.recommendations?.forEachIndexed { index, recommendation ->
            insights.add(
                AIInsight(
                    id = "savings_${index}",
                    type = InsightType.SAVINGS_OPPORTUNITY,
                    title = "Savings Opportunity",
                    description = recommendation,
                    actionableAdvice = recommendation,
                    impactAmount = apiInsights.spendingForecast?.currentMonthSpent?.times(0.1) ?: 5000.0, // 10% of current spending
                    priority = InsightPriority.MEDIUM,
                    createdAt = Date()
                )
            )
        }
        
        // Show Anomaly Detection exactly as API returns
        apiInsights.anomalyDetection?.anomalies?.forEachIndexed { index, anomaly ->
            insights.add(
                AIInsight(
                    id = "anomaly_${index}",
                    type = InsightType.PATTERN_ALERT,
                    title = "Anomaly Detection",
                    description = anomaly,
                    actionableAdvice = anomaly,
                    impactAmount = 0.0,
                    priority = if (anomaly.contains("No anomalies")) InsightPriority.LOW else InsightPriority.HIGH,
                    createdAt = Date()
                )
            )
        }
        
        // Show Top Merchants exactly as API returns
        apiInsights.merchantEfficiency?.topMerchants?.forEach { merchant ->
            insights.add(
                AIInsight(
                    id = "merchant_${merchant.merchantName}",
                    type = InsightType.MERCHANT_RECOMMENDATION,
                    title = merchant.merchantName ?: "Unknown Merchant",
                    description = "${merchant.insight ?: "Merchant analysis"}\nAverage transaction: ₹${formatAmount(merchant.avgTransaction ?: 0.0)}",
                    actionableAdvice = merchant.insight ?: "Review spending at this merchant",
                    impactAmount = merchant.avgTransaction ?: 0.0,
                    priority = InsightPriority.LOW,
                    createdAt = Date()
                )
            )
        }
        
        return insights
    }
    
    private fun formatAmount(amount: Double): String {
        return String.format("%.0f", amount)
    }
}