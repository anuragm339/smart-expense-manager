package com.expensemanager.app.data.models

import java.util.Date

// @Entity(tableName = "ai_insights") - Room annotations temporarily removed
data class AIInsight(
    // @PrimaryKey
    val id: String,
    val type: InsightType,
    val title: String,
    val description: String,
    val actionableAdvice: String,
    val impactAmount: Double = 0.0,
    val priority: InsightPriority = InsightPriority.MEDIUM,
    val isRead: Boolean = false,
    val createdAt: Date = Date(),
    val validUntil: Date? = null
)

enum class InsightType {
    SPENDING_FORECAST,
    PATTERN_ALERT,
    BUDGET_OPTIMIZATION,
    SAVINGS_OPPORTUNITY,
    ANOMALY_DETECTION,
    MERCHANT_RECOMMENDATION,
    CATEGORY_ANALYSIS
}

enum class InsightPriority {
    LOW, MEDIUM, HIGH, URGENT
}

object SampleInsights {
    val SPENDING_FORECAST = AIInsight(
        id = "forecast_monthly",
        type = InsightType.SPENDING_FORECAST,
        title = "Monthly Spending Forecast",
        description = "Based on your current spending pattern, you're likely to spend ₹18,400 this month.",
        actionableAdvice = "Consider reducing dining expenses to stay within budget.",
        impactAmount = 18400.0,
        priority = InsightPriority.MEDIUM
    )
    
    val FOOD_PATTERN_ALERT = AIInsight(
        id = "pattern_food_increase",
        type = InsightType.PATTERN_ALERT,
        title = "Food Expenses Increased",
        description = "Your food expenses increased by 45% this week",
        actionableAdvice = "Try cooking at home 2 more days per week to save money.",
        impactAmount = 1200.0,
        priority = InsightPriority.HIGH
    )
    
    val TRANSPORT_SAVINGS = AIInsight(
        id = "savings_transport",
        type = InsightType.SAVINGS_OPPORTUNITY,
        title = "Transportation Savings",
        description = "Use metro instead of cab for daily commute",
        actionableAdvice = "Switch to public transport for regular commutes to save significantly.",
        impactAmount = 800.0,
        priority = InsightPriority.MEDIUM
    )
    
    fun getAllSample(): List<AIInsight> = listOf(
        SPENDING_FORECAST, FOOD_PATTERN_ALERT, TRANSPORT_SAVINGS
    )
}