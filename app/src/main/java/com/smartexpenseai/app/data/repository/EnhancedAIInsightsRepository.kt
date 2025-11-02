package com.smartexpenseai.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.smartexpenseai.app.utils.logging.StructuredLogger
import com.smartexpenseai.app.data.api.insights.*
import com.smartexpenseai.app.data.dao.AICallDao
import com.smartexpenseai.app.data.models.*
import kotlinx.coroutines.flow.first
import com.smartexpenseai.app.services.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Enhanced AI Insights Repository with backend integration and smart thresholds
 * Manages AI insights with cost optimization and user-controlled refresh triggers
 */
@Singleton
class EnhancedAIInsightsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val backendService: BackendInsightsService,
    private val thresholdService: AIThresholdService,
    private val dataProcessor: FinancialDataProcessor,
    private val promptBuilder: InsightsPromptBuilder,
    private val aiCallDao: AICallDao,
    @Named("ai_insights_cache") private val prefs: SharedPreferences,
    private val gson: Gson,
    private val errorHandler: NetworkErrorHandler,
    private val retryMechanism: RetryMechanism
) {

    companion object {
        private const val CACHED_INSIGHTS_KEY = "enhanced_cached_insights"
        private const val CACHE_TIMESTAMP_KEY = "cache_timestamp"
        private const val OFFLINE_INSIGHTS_KEY = "offline_fallback_insights"
        private const val CACHE_VERSION_KEY = "cache_version"
        private const val CURRENT_CACHE_VERSION = 2
    }

    // Circuit breaker for API calls
    private val circuitBreaker = RetryMechanism.CircuitBreaker()
    private val enhancedOfflineGenerator = EnhancedOfflineInsightsGenerator()
    private val logger = StructuredLogger(
        featureTag = "INSIGHTS",
        className = "EnhancedAIInsightsRepository"
    )

    /**
     * Main method to get AI insights with intelligent threshold management
     */
    fun getInsights(
        forceRefresh: Boolean = false
    ): Flow<Result<InsightsResult>> = flow<Result<InsightsResult>> {
        emit(Result.success(InsightsResult.Loading(emptyList())))

        try {
            // Initialize tracking if needed
            aiCallDao.initializeTrackerIfNeeded()

            // First, emit cached insights for immediate UI response
            val cachedInsights = getCachedInsights()
            if (cachedInsights.isNotEmpty()) {
                emit(Result.success(
                    InsightsResult.Success(
                        insights = cachedInsights,
                        isCached = true,
                        lastUpdated = getCacheTimestamp(),
                        source = "cache"
                    )
                ))
                logger.debug(
                    where = "getInsights",
                    what = "Emitted cached insights: ${cachedInsights.size}"
                )
            }

            // Evaluate if we should call AI
            val shouldCallAI = forceRefresh || thresholdService.shouldCallAI()

            if (shouldCallAI && errorHandler.isNetworkAvailable()) {
                logger.debug(
                    where = "getInsights",
                    what = "Triggering AI call (forceRefresh: $forceRefresh)"
                )

                // Emit loading state if no cached insights
                if (cachedInsights.isEmpty()) {
                    emit(Result.success(InsightsResult.Loading(emptyList())))
                }

                try {
                    val freshInsights = fetchInsightsFromBackendWithRetry()

                    // Cache the results
                    cacheInsights(freshInsights)

                    // Update threshold tracking
                    val currentTransactionEntities = expenseRepository.getAllTransactions().first()
                    val currentTransactions = Transaction.fromEntities(currentTransactionEntities)
                    thresholdService.updateAfterSuccessfulCall(currentTransactions)

                    emit(Result.success(
                        InsightsResult.Success(
                            insights = freshInsights,
                            isCached = false,
                            lastUpdated = System.currentTimeMillis(),
                            source = "backend_api"
                        )
                    ))

                    logger.debug(
                        where = "getInsights",
                        what = "Successfully fetched fresh insights: ${freshInsights.size}"
                    )

                } catch (e: Exception) {
                    val networkError = errorHandler.analyzeError(e)
                    errorHandler.logError(networkError, e, "AI insights fetch")

                    // Record error for threshold management
                    thresholdService.recordError()

                    // No fallback - return error with empty data
                    emit(Result.success(
                        InsightsResult.Error(
                            message = errorHandler.getUserFriendlyMessage(networkError),
                            cachedInsights = emptyList(),
                            lastUpdated = getCacheTimestamp()
                        )
                    ))
                }
            } else if (!errorHandler.isNetworkAvailable()) {
                // Offline mode - no fallback
                logger.debug(
                    where = "getInsights",
                    what = "Offline mode - no internet connection"
                )

                emit(Result.success(
                    InsightsResult.Error(
                        message = "No internet connection. Please check your network and try again.",
                        cachedInsights = emptyList(),
                        lastUpdated = getCacheTimestamp()
                    )
                ))

            } else {
                // Threshold not met - no data shown
                logger.debug(
                    where = "getInsights",
                    what = "Threshold not met - waiting for conditions"
                )

                emit(Result.success(
                    InsightsResult.Error(
                        message = "Waiting for more data to generate insights.",
                        cachedInsights = emptyList(),
                        lastUpdated = getCacheTimestamp()
                    )
                ))
            }

        } catch (e: Exception) {
            logger.error(
                where = "getInsights",
                what = "Error in getInsights",
                throwable = e
            )
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get threshold progress for UI display
     */
    suspend fun getThresholdProgress(): ThresholdProgress = withContext(Dispatchers.IO) {
        val evaluation = thresholdService.evaluateThresholds()
        evaluation.progressToNextCall
    }

    /**
     * Manual refresh triggered by user
     */
    suspend fun manualRefresh(): Result<List<AIInsight>> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!errorHandler.isNetworkAvailable()) {
                Result.failure(Exception("No internet connection"))
            } else {
                val insights = fetchInsightsFromBackendWithRetry(triggerReason = TriggerReason.USER_MANUAL)
                cacheInsights(insights)

                val currentTransactionEntities = expenseRepository.getAllTransactions().first()
                val currentTransactions = Transaction.fromEntities(currentTransactionEntities)
                thresholdService.updateAfterSuccessfulCall(currentTransactions)

                Result.success(insights)
            }
        } catch (e: Exception) {
            val networkError = errorHandler.analyzeError(e)
            errorHandler.logError(networkError, e, "Manual refresh")
            thresholdService.recordError()
            Result.failure(Exception(errorHandler.getUserFriendlyMessage(networkError)))
        }
    }

    /**
     * Update user's AI call frequency preference
     */
    suspend fun updateCallFrequency(frequency: CallFrequency) {
        thresholdService.updateCallFrequency(frequency)
    }

    /**
     * Force refresh insights from backend (bypass thresholds)
     */
    suspend fun refreshInsights(): Result<List<AIInsight>> = withContext(Dispatchers.IO) {
        logger.debug(
            where = "refreshInsights",
            what = "Force refresh requested - bypassing thresholds"
        )

        // Clear cache before fetching fresh data to ensure we don't use stale data
        logger.debug(
            where = "refreshInsights",
            what = "🗑️ Clearing cache before fresh API call"
        )
        prefs.edit()
            .remove(CACHED_INSIGHTS_KEY)
            .remove(CACHE_TIMESTAMP_KEY)
            .remove(OFFLINE_INSIGHTS_KEY)
            .apply()

        return@withContext try {
            val freshInsights = fetchInsightsFromBackendWithRetry()

            logger.debug(
                where = "refreshInsights",
                what = "📥 Received ${freshInsights.size} fresh insights from API"
            )
            freshInsights.forEachIndexed { index, insight ->
                logger.debug(
                    where = "refreshInsights",
                    what = "  API Insight #${index + 1}: id=${insight.id}, type=${insight.type}, impact=₹${insight.impactAmount}"
                )
            }

            // Cache the fresh insights
            cacheInsights(freshInsights)

            // Update threshold tracking
            val currentTransactionEntities = expenseRepository.getAllTransactions().first()
            val currentTransactions = Transaction.fromEntities(currentTransactionEntities)
            thresholdService.updateAfterSuccessfulCall(currentTransactions)

            Result.success(freshInsights)
        } catch (e: Exception) {
            logger.error(
                where = "refreshInsights",
                what = "Force refresh failed",
                throwable = e
            )

            // No fallback - return failure
            Result.failure(e)
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(CACHED_INSIGHTS_KEY)
            .remove(CACHE_TIMESTAMP_KEY)
            .remove(OFFLINE_INSIGHTS_KEY)
            .apply()

        aiCallDao.clearAllTracking()
        logger.debug(
            where = "clearCache",
            what = "Cache and tracking cleared"
        )
    }

    // Private methods

    /**
     * Fetch insights from backend with intelligent retry logic
     */
    private suspend fun fetchInsightsFromBackendWithRetry(
        triggerReason: TriggerReason = TriggerReason.MIN_TRANSACTIONS
    ): List<AIInsight> {
        val retryConfig = when (triggerReason) {
            TriggerReason.USER_MANUAL -> retryMechanism.createNetworkAwareRetryConfig()
            else -> retryMechanism.createTimeoutRetryConfig()
        }

        val result = retryMechanism.executeWithCircuitBreaker(
            circuitBreaker = circuitBreaker,
            config = retryConfig
        ) {
            fetchInsightsFromBackend(triggerReason)
        }

        return result.getOrThrow()
    }

    /**
     * Generate intelligent fallback based on error type
     */
    private suspend fun generateIntelligentFallback(
        networkError: NetworkErrorHandler.NetworkError
    ): List<AIInsight> {
        return when (networkError) {
            is NetworkErrorHandler.NetworkError.NoInternet,
            is NetworkErrorHandler.NetworkError.UnknownHost -> {
                // Use enhanced offline analysis
                generateOfflineFallbackInsights()
            }

            is NetworkErrorHandler.NetworkError.RateLimited -> {
                // Return cached data with rate limit message
                val cached = getCachedInsights()
                if (cached.isNotEmpty()) cached else generateBasicInsights()
            }

            is NetworkErrorHandler.NetworkError.ServerError,
            is NetworkErrorHandler.NetworkError.Timeout -> {
                // Try cached first, then offline
                val cached = getCachedInsights()
                if (cached.isNotEmpty()) cached else generateOfflineFallbackInsights()
            }

            else -> {
                // Generic error - use basic offline insights
                generateBasicInsights()
            }
        }
    }

    /**
     * Fetch insights from backend service
     */
    private suspend fun fetchInsightsFromBackend(
        triggerReason: TriggerReason = TriggerReason.MIN_TRANSACTIONS
    ): List<AIInsight> {
        // Get transaction data
        val transactionEntities = expenseRepository.getAllTransactions().first()
        val transactions = Transaction.fromEntities(transactionEntities)
        if (transactions.isEmpty()) {
            throw Exception("No transaction data available for analysis")
        }

        // Create anonymized data
        val anonymizedData = dataProcessor.createAnonymizedData(transactions)

        // Validate anonymization
        if (!dataProcessor.validateAnonymization(anonymizedData)) {
            throw Exception("Data anonymization validation failed")
        }

        // Build appropriate prompt
        val prompt = promptBuilder.buildPrompt(anonymizedData, InsightType.CATEGORY_ANALYSIS)

        // Create request
        val request = BackendInsightsRequest(
            prompt = prompt,
            data = anonymizedData
        )

        // Call backend API
        val response = backendService.generateInsights(request)

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            throw HttpException(response)
        }

        val responseBody = response.body()
        if (responseBody == null || !responseBody.success) {
            throw Exception("Backend returned unsuccessful response: ${responseBody?.error}")
        }

        // Convert to domain models
        val insights = responseBody.insights?.map { it.toAIInsight() } ?: emptyList()

        if (insights.isEmpty()) {
            throw Exception("No insights returned from backend")
        }

        logger.debug(
            where = "fetchInsightsFromBackend",
            what = "Successfully fetched ${insights.size} insights from backend"
        )
        return insights
    }

    /**
     * Cache insights locally
     */
    private fun cacheInsights(insights: List<AIInsight>) {
        try {
            val json = gson.toJson(insights)
            prefs.edit()
                .putString(CACHED_INSIGHTS_KEY, json)
                .putLong(CACHE_TIMESTAMP_KEY, System.currentTimeMillis())
                .putInt(CACHE_VERSION_KEY, CURRENT_CACHE_VERSION)
                .apply()

            logger.debug(
                where = "cacheInsights",
                what = "Cached ${insights.size} insights"
            )
        } catch (e: Exception) {
            logger.error(
                where = "cacheInsights",
                what = "Failed to cache insights",
                throwable = e
            )
        }
    }

    /**
     * Get cached insights
     */
    private fun getCachedInsights(): List<AIInsight> {
        return try {
            val version = prefs.getInt(CACHE_VERSION_KEY, 0)
            if (version != CURRENT_CACHE_VERSION) {
                // Clear old cache version
                prefs.edit().clear().apply()
                return emptyList()
            }

            val json = prefs.getString(CACHED_INSIGHTS_KEY, null) ?: return emptyList()
            val type = object : TypeToken<List<AIInsight>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            logger.error(
                where = "getCachedInsights",
                what = "Failed to load cached insights",
                throwable = e
            )
            emptyList()
        }
    }

    /**
     * Get cache timestamp
     */
    private fun getCacheTimestamp(): Long {
        return prefs.getLong(CACHE_TIMESTAMP_KEY, 0L)
    }

    /**
     * Generate enhanced rule-based insights when AI is not available
     */
    private suspend fun generateBasicInsights(): List<AIInsight> {
        return try {
            val transactionEntities = expenseRepository.getAllTransactions().first()
            val transactions = Transaction.fromEntities(transactionEntities)
            enhancedOfflineGenerator.generateAdvancedInsights(transactions)
        } catch (e: Exception) {
            logger.error(
                where = "generateBasicInsights",
                what = "Failed to generate enhanced offline insights",
                throwable = e
            )
            listOf(
                AIInsight(
                    id = UUID.randomUUID().toString(),
                    type = InsightType.CATEGORY_ANALYSIS,
                    title = "Basic Financial Summary",
                    description = "Add more transactions to get detailed AI insights",
                    actionableAdvice = "Continue tracking your expenses for better analysis",
                    priority = InsightPriority.LOW
                )
            )
        }
    }

    /**
     * Generate offline fallback insights with caching
     */
    private suspend fun generateOfflineFallbackInsights(): List<AIInsight> {
        return try {
            // Try to get cached offline insights first
            val cachedOffline = getOfflineFallbackInsights()
            if (cachedOffline.isNotEmpty()) {
                logger.debug(
                    where = "generateOfflineFallbackInsights",
                    what = "Using cached offline insights"
                )
                return cachedOffline
            }

            // Generate new enhanced insights
            val transactionEntities = expenseRepository.getAllTransactions().first()
            val transactions = Transaction.fromEntities(transactionEntities)
            val offlineInsights = enhancedOfflineGenerator.generateAdvancedInsights(transactions)

            // Cache offline insights for future use
            cacheOfflineFallbackInsights(offlineInsights)

            offlineInsights
        } catch (e: Exception) {
            logger.error(
                where = "generateOfflineFallbackInsights",
                what = "Failed to generate offline fallback insights",
                throwable = e
            )
            logger.debug(
                where = "generateOfflineFallbackInsights",
                what = "No cached offline insights available; returning empty list"
            )
            emptyList()
        }
    }

    /**
     * Cache offline fallback insights
     */
    private fun cacheOfflineFallbackInsights(insights: List<AIInsight>) {
        try {
            val json = gson.toJson(insights)
            prefs.edit()
                .putString(OFFLINE_INSIGHTS_KEY, json)
                .apply()
            logger.debug(
                where = "cacheOfflineFallbackInsights",
                what = "Cached ${insights.size} offline insights"
            )
        } catch (e: Exception) {
            logger.error(
                where = "cacheOfflineFallbackInsights",
                what = "Failed to cache offline insights",
                throwable = e
            )
        }
    }

    /**
     * Get offline fallback insights from cache
     */
    private fun getOfflineFallbackInsights(): List<AIInsight> {
        return try {
            val json = prefs.getString(OFFLINE_INSIGHTS_KEY, null) ?: return emptyList()
            val type = object : TypeToken<List<AIInsight>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            logger.error(
                where = "getOfflineFallbackInsights",
                what = "Failed to load offline fallback insights",
                throwable = e
            )
            emptyList()
        }
    }

}

/**
 * Sealed class for different types of insights results
 */
sealed class InsightsResult {
    data class Success(
        val insights: List<AIInsight>,
        val isCached: Boolean,
        val lastUpdated: Long,
        val source: String
    ) : InsightsResult()

    data class Loading(
        val cachedInsights: List<AIInsight>
    ) : InsightsResult()

    data class Error(
        val message: String,
        val cachedInsights: List<AIInsight>,
        val lastUpdated: Long
    ) : InsightsResult()
}
