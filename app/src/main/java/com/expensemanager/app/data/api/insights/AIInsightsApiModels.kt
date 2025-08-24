package com.expensemanager.app.data.api.insights

import com.expensemanager.app.data.models.InsightType
import com.expensemanager.app.data.models.InsightPriority
import com.google.gson.annotations.SerializedName

/**
 * Request model for AI Insights API
 */
data class AIInsightsRequest(
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("timeframe")
    val timeframe: String = "last_30_days",
    
    @SerializedName("transaction_summary")
    val transactionSummary: TransactionSummary,
    
    @SerializedName("context_data")
    val contextData: ContextData,
    
    @SerializedName("insight_types")
    val insightTypes: List<String> = listOf(
        "spending_forecast",
        "pattern_alert", 
        "budget_optimization",
        "savings_opportunity",
        "anomaly_detection"
    ),
    
    @SerializedName("prompts")
    val prompts: List<String> = listOf(
        "Generate spending forecast for next month based on current patterns",
        "Identify unusual spending patterns or anomalies",
        "Suggest budget optimization strategies",
        "Find savings opportunities in top spending categories",
        "Analyze merchant spending efficiency"
    )
)

/**
 * Transaction summary for AI analysis
 */
data class TransactionSummary(
    @SerializedName("total_spent")
    val totalSpent: Double,
    
    @SerializedName("transaction_count")
    val transactionCount: Int,
    
    @SerializedName("category_breakdown")
    val categoryBreakdown: List<CategorySpending>,
    
    @SerializedName("top_merchants")
    val topMerchants: List<MerchantSpending>,
    
    @SerializedName("monthly_trends")
    val monthlyTrends: List<MonthlyTrend>
)

/**
 * Category spending data for AI analysis
 */
data class CategorySpending(
    @SerializedName("category_name")
    val categoryName: String,
    
    @SerializedName("total_amount")
    val totalAmount: Double,
    
    @SerializedName("transaction_count")
    val transactionCount: Int,
    
    @SerializedName("percentage")
    val percentage: Double
)

/**
 * Merchant spending data for AI analysis
 */
data class MerchantSpending(
    @SerializedName("merchant_name")
    val merchantName: String,
    
    @SerializedName("total_amount")
    val totalAmount: Double,
    
    @SerializedName("transaction_count")
    val transactionCount: Int
)

/**
 * Monthly trend data for AI analysis
 */
data class MonthlyTrend(
    @SerializedName("month")
    val month: String,
    
    @SerializedName("total_amount")
    val totalAmount: Double,
    
    @SerializedName("transaction_count")
    val transactionCount: Int,
    
    @SerializedName("average_per_transaction")
    val averagePerTransaction: Double
)

/**
 * Context data for better AI insights
 */
data class ContextData(
    @SerializedName("monthly_budget")
    val monthlyBudget: Double?,
    
    @SerializedName("previous_month_spent")
    val previousMonthSpent: Double,
    
    @SerializedName("budget_progress_percentage")
    val budgetProgressPercentage: Int,
    
    @SerializedName("days_remaining_in_month")
    val daysRemainingInMonth: Int,
    
    @SerializedName("user_preferences")
    val userPreferences: UserPreferences
)

/**
 * User preferences for personalized insights
 */
data class UserPreferences(
    @SerializedName("currency")
    val currency: String = "INR",
    
    @SerializedName("primary_categories")
    val primaryCategories: List<String> = listOf("Food & Dining", "Transportation", "Groceries"),
    
    @SerializedName("savings_goal")
    val savingsGoal: Double? = null,
    
    @SerializedName("risk_tolerance")
    val riskTolerance: String = "medium" // low, medium, high
)

/**
 * API Response model for AI Insights
 */
data class AIInsightsResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("insights")
    val insights: List<AIInsightDto>,
    
    @SerializedName("metadata")
    val metadata: ResponseMetadata,
    
    @SerializedName("error")
    val error: ErrorResponse? = null
)

/**
 * AI Insight DTO from API
 */
data class AIInsightDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("type")
    val type: String, // Will be converted to InsightType enum
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("actionable_advice")
    val actionableAdvice: String,
    
    @SerializedName("impact_amount")
    val impactAmount: Double = 0.0,
    
    @SerializedName("priority")
    val priority: String, // Will be converted to InsightPriority enum
    
    @SerializedName("confidence_score")
    val confidenceScore: Float = 0.8f,
    
    @SerializedName("valid_until")
    val validUntil: Long? = null, // Unix timestamp
    
    @SerializedName("visualization_data")
    val visualizationData: VisualizationData? = null
)

/**
 * Visualization data for charts/graphs
 */
data class VisualizationData(
    @SerializedName("chart_type")
    val chartType: String, // "line", "bar", "pie", etc.
    
    @SerializedName("data_points")
    val dataPoints: List<DataPoint>,
    
    @SerializedName("labels")
    val labels: List<String>,
    
    @SerializedName("colors")
    val colors: List<String>
)

/**
 * Data point for visualization
 */
data class DataPoint(
    @SerializedName("x")
    val x: String,
    
    @SerializedName("y")
    val y: Double
)

/**
 * Response metadata
 */
data class ResponseMetadata(
    @SerializedName("generated_at")
    val generatedAt: Long, // Unix timestamp
    
    @SerializedName("model_version")
    val modelVersion: String,
    
    @SerializedName("processing_time_ms")
    val processingTimeMs: Long,
    
    @SerializedName("total_insights")
    val totalInsights: Int
)

/**
 * Error response model
 */
data class ErrorResponse(
    @SerializedName("code")
    val code: String,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("details")
    val details: String? = null
)

/**
 * Extension function to convert API InsightType to domain InsightType
 */
fun String.toInsightType(): InsightType {
    return when (this.lowercase()) {
        "spending_forecast" -> InsightType.SPENDING_FORECAST
        "pattern_alert" -> InsightType.PATTERN_ALERT
        "budget_optimization" -> InsightType.BUDGET_OPTIMIZATION
        "savings_opportunity" -> InsightType.SAVINGS_OPPORTUNITY
        "anomaly_detection" -> InsightType.ANOMALY_DETECTION
        "merchant_recommendation" -> InsightType.MERCHANT_RECOMMENDATION
        "category_analysis" -> InsightType.CATEGORY_ANALYSIS
        else -> InsightType.CATEGORY_ANALYSIS
    }
}

/**
 * Extension function to convert API InsightPriority to domain InsightPriority
 */
fun String.toInsightPriority(): InsightPriority {
    return when (this.lowercase()) {
        "low" -> InsightPriority.LOW
        "medium" -> InsightPriority.MEDIUM
        "high" -> InsightPriority.HIGH
        "urgent" -> InsightPriority.URGENT
        else -> InsightPriority.MEDIUM
    }
}