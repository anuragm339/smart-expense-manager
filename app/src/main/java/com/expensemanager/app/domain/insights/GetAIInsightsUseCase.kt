package com.expensemanager.app.domain.insights

import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.data.models.AIInsight
import com.expensemanager.app.data.models.InsightType
import com.expensemanager.app.data.models.InsightPriority
import com.expensemanager.app.data.repository.AIInsightsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for getting AI insights with business logic
 * Handles filtering, sorting, and transformation of insights
 */
@Singleton
class GetAIInsightsUseCase @Inject constructor(
    private val repository: AIInsightsRepository
) {
    
    companion object {
        private const val TAG = "GetAIInsightsUseCase"
    }
    
    /**
     * Get AI insights with filtering and sorting applied
     */
    fun execute(params: GetInsightsParams = GetInsightsParams()): Flow<Result<List<AIInsight>>> {
        return repository.getInsights().map { result ->
            result.map { insights ->
                processInsights(insights, params)
            }
        }
    }
    
    /**
     * Force refresh insights from API
     */
    suspend fun refreshInsights(): Result<List<AIInsight>> {
        Timber.tag(TAG).d("Requesting force refresh")
        return repository.refreshInsights().map { insights ->
            processInsights(insights, GetInsightsParams())
        }
    }
    
    /**
     * Clear insights cache
     */
    suspend fun clearCache(): Result<Unit> {
        Timber.tag(TAG).d("Clearing insights cache")
        return repository.clearCache()
    }
    
    /**
     * Process insights with business logic
     */
    private fun processInsights(insights: List<AIInsight>, params: GetInsightsParams): List<AIInsight> {
        Timber.tag(TAG).d("Processing ${insights.size} insights with params: $params")
        
        return insights
            .let { list ->
                // Filter by types if specified
                if (params.insightTypes.isNotEmpty()) {
                    list.filter { it.type in params.insightTypes }
                } else {
                    list
                }
            }
            .let { list ->
                // Filter by priority if specified
                if (params.minPriority != null) {
                    list.filter { it.priority.ordinal >= params.minPriority.ordinal }
                } else {
                    list
                }
            }
            .let { list ->
                // Filter by minimum impact amount if specified
                if (params.minImpactAmount > 0.0) {
                    list.filter { it.impactAmount >= params.minImpactAmount }
                } else {
                    list
                }
            }
            .let { list ->
                // Sort insights by priority (urgent first) then by impact amount (descending)
                list.sortedWith(
                    compareByDescending<AIInsight> { it.priority.ordinal }
                        .thenByDescending { it.impactAmount }
                        .thenBy { it.createdAt }
                )
            }
            .let { list ->
                // Apply limit if specified
                if (params.limit > 0) {
                    list.take(params.limit)
                } else {
                    list
                }
            }
            .also { processedList ->
                Timber.tag(TAG).d("Processed insights: ${processedList.size} after filtering and sorting")
                
                // Log insight summary for debugging
                if (processedList.isNotEmpty()) {
                    val summary = processedList.groupBy { it.type }.mapValues { it.value.size }
                    Timber.tag(TAG).d("Insight types: $summary")
                }
            }
    }
    
    /**
     * Get insights grouped by type for organized display
     */
    fun getInsightsGroupedByType(params: GetInsightsParams = GetInsightsParams()): Flow<Result<Map<InsightType, List<AIInsight>>>> {
        return execute(params).map { result ->
            result.map { insights ->
                insights.groupBy { it.type }
            }
        }
    }
    
    /**
     * Get only high priority insights for dashboard
     */
    fun getHighPriorityInsights(): Flow<Result<List<AIInsight>>> {
        return execute(
            GetInsightsParams(
                minPriority = InsightPriority.HIGH,
                limit = 3
            )
        )
    }
    
    /**
     * Get insights with minimum impact amount (for savings opportunities)
     */
    fun getImpactfulInsights(minAmount: Double = 500.0): Flow<Result<List<AIInsight>>> {
        return execute(
            GetInsightsParams(
                minImpactAmount = minAmount,
                limit = 5
            )
        )
    }
}

/**
 * Parameters for getting insights
 */
data class GetInsightsParams(
    val insightTypes: List<InsightType> = emptyList(),
    val minPriority: InsightPriority? = null,
    val minImpactAmount: Double = 0.0,
    val limit: Int = 0, // 0 means no limit
    val includeRead: Boolean = true
)

/**
 * Factory for creating use case instances
 * In production, this would be handled by dependency injection
 */
object InsightsUseCaseFactory {
    
    @Volatile
    private var getAIInsightsUseCase: GetAIInsightsUseCase? = null
    
    fun createGetAIInsightsUseCase(repository: AIInsightsRepository): GetAIInsightsUseCase {
        return getAIInsightsUseCase ?: synchronized(this) {
            getAIInsightsUseCase ?: GetAIInsightsUseCase(repository).also {
                getAIInsightsUseCase = it
            }
        }
    }
}