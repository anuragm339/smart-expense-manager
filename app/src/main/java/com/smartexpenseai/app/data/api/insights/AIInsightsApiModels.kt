package com.smartexpenseai.app.data.api.insights

import com.smartexpenseai.app.data.models.InsightType
import com.smartexpenseai.app.data.models.InsightPriority
import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.JsonAdapter

/**
 * Request model for AI Insights API with conversation history and CSV data
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
    ),

    // Conversation history for AI to remember previous insights
    @SerializedName("conversation_history")
    val conversationHistory: List<HistoricalInsight>? = null,

    // Previous period data for comparison
    @SerializedName("previous_period_data")
    val previousPeriodData: PreviousPeriodData? = null,

    // NEW: Raw transaction data as CSV for deep analysis
    @SerializedName("transactions_csv")
    val transactionsCSV: String? = null,

    // NEW: Metadata about the CSV data
    @SerializedName("csv_metadata")
    val csvMetadata: CSVMetadata? = null
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
    val percentage: Double,

    @SerializedName("average_per_transaction")
    val averagePerTransaction: Double
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
    val transactionCount: Int,

    @SerializedName("category_name")
    val categoryName: String,

    @SerializedName("average_amount")
    val averageAmount: Double
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
    val averagePerTransaction: Double,

    @SerializedName("compared_to_previous")
    val comparedToPrevious: Double
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
 * Response metadata with flexible timestamp handling
 * Supports both Unix timestamp (Long) and ISO-8601 string from o1-mini
 */
data class ResponseMetadata(
    @SerializedName("generated_at")
    @JsonAdapter(FlexibleTimestampAdapter::class)  // Handles both number and ISO-8601 string
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

/**
 * Historical insight for conversation context
 * Stores previous AI insights so the model can track trends over time
 */
data class HistoricalInsight(
    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("type")
    val type: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("key_findings")
    val keyFindings: List<String>,

    @SerializedName("user_action_taken")
    val userActionTaken: Boolean = false,

    @SerializedName("spending_at_time")
    val spendingAtTime: Double,

    @SerializedName("top_categories_at_time")
    val topCategoriesAtTime: List<String>
)

/**
 * Previous period data for comparison
 * Allows AI to compare current period with past periods
 */
data class PreviousPeriodData(
    @SerializedName("period_label")
    val periodLabel: String, // e.g., "Last Month", "3 Months Ago"

    @SerializedName("total_spent")
    val totalSpent: Double,

    @SerializedName("transaction_count")
    val transactionCount: Int,

    @SerializedName("top_categories")
    val topCategories: List<CategorySpending>,

    @SerializedName("top_merchants")
    val topMerchants: List<MerchantSpending>,

    @SerializedName("average_daily_spending")
    val averageDailySpending: Double,

    @SerializedName("highest_spending_day")
    val highestSpendingDay: Double,

    @SerializedName("insights_provided")
    val insightsProvided: List<String> // Previous insights given for this period
)

/**
 * Metadata about the CSV transaction data
 * Provides context about the raw transaction data being sent
 */
data class CSVMetadata(
    @SerializedName("total_transactions")
    val totalTransactions: Int,

    @SerializedName("date_range_start")
    val dateRangeStart: String,

    @SerializedName("date_range_end")
    val dateRangeEnd: String,

    @SerializedName("csv_size_bytes")
    val csvSizeBytes: Int,

    @SerializedName("includes_categories")
    val includesCategories: Boolean = true,

    @SerializedName("includes_time_analysis")
    val includesTimeAnalysis: Boolean = true
)