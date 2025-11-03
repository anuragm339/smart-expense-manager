package com.smartexpenseai.app.data.api.insights

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit service interface for backend AI insights API
 * Communicates with your backend service that proxies Claude AI requests
 */
interface BackendInsightsService {

    /**
     * Generate AI insights by sending prompt and data to backend
     * Backend will handle Claude API communication securely
     *
     * @param request Contains prompt and anonymized financial data
     * @return Response with AI-generated insights
     */
    @POST("api/insights")
    suspend fun generateInsights(
        @Body request: BackendInsightsRequest
    ): Response<BackendInsightsResponse>
}

/**
 * Request model for backend insights API
 * Simple structure: prompt + anonymized data
 */
data class BackendInsightsRequest(
    val prompt: String,
    val data: AnonymizedFinancialData
)

/**
 * Response model from backend insights API
 */
data class BackendInsightsResponse(
    val success: Boolean,
    val insights: List<BackendInsightDto>? = null,
    val timestamp: Long,
    val error: String? = null,
    val metadata: BackendResponseMetadata? = null
)

/**
 * Insight DTO returned by backend service
 */
data class BackendInsightDto(
    val type: String,
    val title: String,
    val description: String,
    val actionableAdvice: String,
    val impactAmount: Double = 0.0,
    val priority: String,
    val confidenceScore: Float = 0.8f
)

/**
 * Additional metadata from backend response
 */
data class BackendResponseMetadata(
    val processingTimeMs: Long,
    val modelUsed: String? = null,
    val tokensUsed: Int? = null,
    val cacheHit: Boolean = false
)

/**
 * Anonymized financial data for backend processing
 * Contains no personal identifiers - only aggregated spending patterns
 */
data class AnonymizedFinancialData(
    val totalSpent: Double,
    val transactionCount: Int,
    val timeframe: String = "last_30_days",
    val categoryBreakdown: List<CategorySpending>,
    val topMerchants: List<MerchantSpending>,
    val monthlyTrends: List<MonthlyTrend>,
    val weeklyPatterns: List<WeeklyPattern>,
    val contextData: FinancialContextData
)


/**
 * Weekly spending patterns
 */
data class WeeklyPattern(
    val dayOfWeek: String, // "Monday", "Tuesday", etc.
    val averageAmount: Double,
    val transactionCount: Int,
    val peakHour: Int? = null // 0-23, peak spending hour
)

/**
 * Financial context for better AI insights
 */
data class FinancialContextData(
    val monthlyBudget: Double? = null,
    val budgetUtilizationPercentage: Int,
    val daysRemainingInMonth: Int,
    val previousMonthSpent: Double,
    val spendingTrendDirection: String, // "increasing", "decreasing", "stable"
    val topSpendingCategory: String,
    val currency: String = "INR"
)

/**
 * Extension function to convert BackendInsightDto to domain AIInsight
 */
fun BackendInsightDto.toAIInsight(): com.smartexpenseai.app.data.models.AIInsight {
    return com.smartexpenseai.app.data.models.AIInsight(
        id = java.util.UUID.randomUUID().toString(),
        type = this.type.toInsightType(),
        title = this.title,
        description = this.description,
        actionableAdvice = this.actionableAdvice,
        impactAmount = this.impactAmount,
        priority = this.priority.toInsightPriority(),
        isRead = false,
        createdAt = java.util.Date(),
        validUntil = null
    )
}