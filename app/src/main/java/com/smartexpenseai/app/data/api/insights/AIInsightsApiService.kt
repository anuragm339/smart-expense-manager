package com.smartexpenseai.app.data.api.insights

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API service interface for AI Insights
 */
interface AIInsightsApiService {
    
    /**
     * Generate AI insights based on transaction data
     * 
     * @param request The insights request containing transaction data and context
     * @return API response with generated insights
     */
    @POST("api/ai/insights")
    suspend fun generateInsights(
        @Body request: AIInsightsRequest
    ): Response<AIInsightsResponse>
    
    /**
     * Get cached insights for a user (optional endpoint for quick loading)
     * 
     * @param userId The user ID
     * @param timeframe The timeframe for insights (e.g., "last_30_days")
     * @return Cached insights if available
     */
    @GET("api/v1/insights/{user_id}")
    suspend fun getCachedInsights(
        @Path("user_id") userId: String,
        @Query("timeframe") timeframe: String = "last_30_days"
    ): Response<AIInsightsResponse>
    
    /**
     * Health check endpoint to verify API availability
     * 
     * @return Simple health status
     */
    @GET("api/v1/insights/health")
    suspend fun healthCheck(): Response<HealthCheckResponse>
    
    /**
     * Get AI model information
     * 
     * @return Model version and capabilities
     */
    @GET("api/v1/insights/model-info")
    suspend fun getModelInfo(): Response<ModelInfoResponse>
}

/**
 * Health check response
 */
data class HealthCheckResponse(
    val status: String,
    val timestamp: Long,
    val version: String
)

/**
 * Model info response
 */
data class ModelInfoResponse(
    val modelVersion: String,
    val capabilities: List<String>,
    val supportedInsightTypes: List<String>,
    val maxRequestSize: Long,
    val averageResponseTimeMs: Long
)