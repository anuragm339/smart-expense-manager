package com.expensemanager.app.data.api.insights

import com.google.gson.annotations.SerializedName

/**
 * Response model that matches your updated generic AI API format
 */
data class YourApiResponse(
    @SerializedName("user_id")
    val userId: String?,
    
    @SerializedName("timeframe")
    val timeframe: String?,
    
    @SerializedName("insights")
    val insights: YourApiInsights
)

data class YourApiInsights(
    @SerializedName("spending_forecast")
    val spendingForecast: SpendingForecast?,
    
    @SerializedName("pattern_alert")
    val patternAlert: PatternAlert?,
    
    @SerializedName("budget_optimization")
    val budgetOptimization: BudgetOptimization?,
    
    @SerializedName("savings_opportunity")
    val savingsOpportunity: SavingsOpportunity?,
    
    @SerializedName("anomaly_detection")
    val anomalyDetection: AnomalyDetection?,
    
    @SerializedName("merchant_efficiency")
    val merchantEfficiency: MerchantEfficiency?
)

data class SpendingForecast(
    @SerializedName("current_month_spent")
    val currentMonthSpent: Double?,
    
    @SerializedName("previous_month_spent")
    val previousMonthSpent: Double?,
    
    @SerializedName("forecast_next_month")
    val forecastNextMonth: String?,
    
    @SerializedName("trend")
    val trend: String?
)

data class PatternAlert(
    @SerializedName("alerts")
    val alerts: List<String>?
)

data class BudgetOptimization(
    @SerializedName("suggested_monthly_budget")
    val suggestedMonthlyBudget: String?,
    
    @SerializedName("recommendations")
    val recommendations: List<String>?
)

data class SavingsOpportunity(
    @SerializedName("recommendations")
    val recommendations: List<String>?
)

data class AnomalyDetection(
    @SerializedName("anomalies")
    val anomalies: List<String>?
)

data class MerchantEfficiency(
    @SerializedName("top_merchants")
    val topMerchants: List<TopMerchant>?
)

data class TopMerchant(
    @SerializedName("merchant_name")
    val merchantName: String,
    
    @SerializedName("avg_transaction")
    val avgTransaction: Double?,
    
    @SerializedName("insight")
    val insight: String?
)