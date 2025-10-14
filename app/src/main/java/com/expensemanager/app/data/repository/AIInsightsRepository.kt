package com.expensemanager.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.expensemanager.app.data.api.insights.*
import com.expensemanager.app.data.dao.AICallDao
import com.expensemanager.app.data.dao.UserDao
import com.expensemanager.app.data.models.AIInsight
import com.expensemanager.app.data.models.MonthlySummary
import com.expensemanager.app.data.models.Transaction
import com.expensemanager.app.utils.logging.StructuredLogger
import com.expensemanager.app.data.repository.internal.AIInsightsCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Repository for managing AI Insights data
 * Handles API calls, caching, and offline support
 */
@Singleton
class AIInsightsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val apiService: AIInsightsApiService,
    @Named("ai_insights_cache") private val prefs: SharedPreferences,
    private val insightsHistoryManager: com.expensemanager.app.services.InsightsHistoryManager,
    private val csvGenerator: com.expensemanager.app.services.TransactionCSVGenerator,
    private val aiThresholdService: com.expensemanager.app.services.AIThresholdService
) {
    
    companion object {
        private const val CACHE_EXPIRY_HOURS = 4L
        private const val OFFLINE_CACHE_EXPIRY_DAYS = 7L

        @Volatile
        private var INSTANCE: AIInsightsRepository? = null

        fun getInstance(context: Context, expenseRepository: ExpenseRepository, aiCallDao: AICallDao,
                        userDao: UserDao
        ): AIInsightsRepository {
            return INSTANCE ?: synchronized(this) {
                val gson = com.google.gson.Gson()
                val instance = AIInsightsRepository(
                    context.applicationContext,
                    expenseRepository,
                    ApiServiceFactory.getAIInsightsApiService(),
                    context.getSharedPreferences("ai_insights_cache", Context.MODE_PRIVATE),
                    com.expensemanager.app.services.InsightsHistoryManager(context.applicationContext, gson),
                    com.expensemanager.app.services.TransactionCSVGenerator(expenseRepository),
                            com.expensemanager.app.services.AIThresholdService (
                        context.applicationContext,
                        expenseRepository,
                        aiCallDao,
                        userDao,
                        context.getSharedPreferences("ai_insights_prefs", Context.MODE_PRIVATE)
                    )
                )
                INSTANCE = instance
                instance
            }
        }
    }

    // Mutex to prevent concurrent API calls
    private val apiCallMutex = Mutex()
    private val logger = StructuredLogger(
        featureTag = "INSIGHTS",
        className = "AIInsightsRepository"
    )
    private val cacheManager = AIInsightsCacheManager(
        context = context,
        prefs = prefs,
        offlineCacheExpiryDays = OFFLINE_CACHE_EXPIRY_DAYS
    )

    /**
     * Smart cache-first loading - loads cached data immediately, only calls API when needed
     * This should be used for initial load when app opens
     */
    suspend fun loadInsightsSmartly(): Result<List<AIInsight>> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Step 1: Try to load cached insights
            val cachedInsights = cacheManager.getCachedInsights()
            val isCacheExpired = isCacheExpired()

            // Step 2: Determine action based on cache state
            when {
                // Case 1: Valid cache exists (not expired) - return immediately
                cachedInsights.isNotEmpty() && !isCacheExpired -> {
                    logger.debug(
                        where = "loadInsightsSmartly",
                        what = "✅ Returning valid cached insights (${cachedInsights.size} items)"
                    )
                    Result.success(cachedInsights)
                }

                // Case 2: Cache expired but exists - check if we should refresh
                cachedInsights.isNotEmpty() && isCacheExpired -> {
                    logger.debug(
                        where = "loadInsightsSmartly",
                        what = "⏰ Cache expired, checking if refresh is needed..."
                    )
                    val shouldRefresh = aiThresholdService.shouldCallAI()

                    if (shouldRefresh && isNetworkAvailable()) {
                        logger.debug(
                            where = "loadInsightsSmartly",
                            what = "🔄 Cache expired + thresholds met - returning cache and refreshing in background"
                        )
                        // Return cached data immediately, trigger background refresh
                        // Note: The refresh will update cache for next time
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            refreshInsightsWithMutex()
                        }
                        Result.success(cachedInsights)
                    } else {
                        logger.debug(
                            where = "loadInsightsSmartly",
                            what = "📋 Cache expired but thresholds not met - returning stale cache"
                        )
                        Result.success(cachedInsights)
                    }
                }

                // Case 3: No cache (first launch) - check thresholds and try API
                else -> {
                    logger.debug(
                        where = "loadInsightsSmartly",
                        what = "📭 No cache found (first launch)"
                    )
                    val shouldCallAPI = aiThresholdService.shouldCallAI()

                    if (shouldCallAPI && isNetworkAvailable()) {
                        logger.debug(
                            where = "loadInsightsSmartly",
                            what = "🌐 Making initial API call..."
                        )
                        refreshInsightsWithMutex()
                    } else {
                        logger.debug(
                            where = "loadInsightsSmartly",
                            what = "⚠️ Cannot call API (thresholds/network) - returning empty"
                        )
                        Result.success(emptyList())
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(
                where = "loadInsightsSmartly",
                what = "Error in smart loading",
                throwable = e
            )
            // Try to return any cached data as fallback
            val cachedData = cacheManager.getCachedInsights()
            if (cachedData.isNotEmpty()) {
                Result.success(cachedData)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Check if cache has expired based on TTL
     */
    private fun isCacheExpired(): Boolean {
        val lastRefresh = cacheManager.getLastRefreshTimestamp()
        if (lastRefresh == 0L) return true // No cache

        val cacheAge = System.currentTimeMillis() - lastRefresh
        val expiryThreshold = TimeUnit.HOURS.toMillis(CACHE_EXPIRY_HOURS)
        val isExpired = cacheAge > expiryThreshold

        if (isExpired) {
            val ageHours = TimeUnit.MILLISECONDS.toHours(cacheAge)
            logger.debug(
                where = "isCacheExpired",
                what = "Cache is expired (age: ${ageHours}h, TTL: ${CACHE_EXPIRY_HOURS}h)"
            )
        }

        return isExpired
    }

    /**
     * Refresh insights with mutex protection to prevent concurrent calls
     */
    private suspend fun refreshInsightsWithMutex(): Result<List<AIInsight>> {
        return apiCallMutex.withLock {
            refreshInsights()
        }
    }

    /**
     * Get AI insights with offline support and automatic refresh logic
     * Returns cached data immediately if available, then fetches fresh data if online
     */
    fun getInsights(): Flow<Result<List<AIInsight>>> = flow {
        val isOnline = isNetworkAvailable()
        val isOfflineModeEnabled = cacheManager.isOfflineModeEnabled()
        
        // Emit cached data first for immediate UI update
        val cachedInsights = cacheManager.getCachedInsights()
        if (cachedInsights.isNotEmpty()) {
            emit(Result.success(cachedInsights))
        }
        
        // Handle different scenarios - Force refresh if no cached data OR if cache expired
        val needsRefresh = cachedInsights.isEmpty() || shouldRefreshInsights()
        
        if (isOnline && !isOfflineModeEnabled && needsRefresh) {
            logger.debug(
                where = "getInsights",
                what = "Refreshing insights from API (online mode) - No cache: ${cachedInsights.isEmpty()}, Expired: ${shouldRefreshInsights()}"
            )
            
            try {
                val freshInsights = fetchInsightsFromApi()
                freshInsights.fold(
                    onSuccess = { insights ->
                        // Cache the fresh insights
                        cacheManager.cacheInsights(insights)
                        emit(Result.success(insights))
                        logger.debug(
                            where = "getInsights",
                            what = "Fresh insights fetched and cached: ${insights.size}"
                        )
                    },
                    onFailure = { error ->
                        logger.error(
                            where = "getInsights",
                            what = "Failed to fetch fresh insights",
                            throwable = error
                        )
                        // If we have cached data, don't emit error
                        if (cachedInsights.isEmpty()) {
                            val fallbackInsights = cacheManager.handleOfflineMode()
                            emit(Result.success(fallbackInsights))
                        }
                    }
                )
            } catch (e: Exception) {
                logger.error(
                    where = "getInsights",
                    what = "Exception during API call",
                    throwable = e
                )
                if (cachedInsights.isEmpty()) {
                    val fallbackInsights = cacheManager.getOfflineFallbackInsights()
                    emit(Result.success(fallbackInsights))
                }
            }
        } else if (!isOnline) {
            // Offline mode: check if cached data is still valid
            val offlineInsights = cacheManager.getOfflineCachedInsights()
            if (offlineInsights.isNotEmpty() && cachedInsights.isEmpty()) {
                logger.debug(
                    where = "getInsights",
                    what = "Using offline cached insights: ${offlineInsights.size}"
                )
                emit(Result.success(offlineInsights))
            } else if (cachedInsights.isEmpty()) {
                // No cached data available, return offline error
                emit(Result.failure(OfflineException("No internet connection and no cached data available")))
            }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Force refresh insights from API with offline fallback
     */
    suspend fun refreshInsights(): Result<List<AIInsight>> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!isNetworkAvailable()) {
                val cachedData = cacheManager.getCachedInsights()
                return@withContext if (cachedData.isNotEmpty()) {
                    Result.success(cachedData)
                } else {
                    Result.failure(OfflineException("No internet connection available for refresh"))
                }
            }
            
            val result = fetchInsightsFromApi()
            
            result.onSuccess { insights ->
                cacheManager.cacheInsights(insights)
                // Also cache for offline mode
                cacheManager.cacheInsightsForOffline(insights)
                logger.debug(
                    where = "refreshInsights",
                    what = "Force refresh completed: ${insights.size} insights"
                )
            }.onFailure { error ->
                logger.error(
                    where = "refreshInsights",
                    what = "Force refresh failed, trying cached data",
                    throwable = error
                )
                // Return cached data if available
                val cachedData = cacheManager.getCachedInsights()
                if (cachedData.isNotEmpty()) {
                    return@withContext Result.success(cachedData)
                }
            }

            result
        } catch (e: Exception) {
            logger.error(
                where = "refreshInsights",
                what = "Error in force refresh",
                throwable = e
            )
            
            // Try to return cached data as fallback
            val cachedData = cacheManager.getCachedInsights()
            if (cachedData.isNotEmpty()) {
                Result.success(cachedData)
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Fetch insights from AI API with conversation history and CSV data
     */
    private suspend fun fetchInsightsFromApi(): Result<List<AIInsight>> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check subscription tier rate limits before making API call
            val shouldCallAI = aiThresholdService.shouldCallAI()
            if (!shouldCallAI) {
                logger.warn(
                    where = "fetchInsightsFromApi",
                    what = "⚠️ AI call blocked by subscription tier rate limit"
                )
                // Return cached data if available
                val cachedData = cacheManager.getCachedInsights()
                return@withContext if (cachedData.isNotEmpty()) {
                    Result.success(cachedData)
                } else {
                    Result.failure(Exception("AI call limit reached for your subscription tier. Please upgrade or try again tomorrow."))
                }
            }

            // Gather transaction data from ExpenseRepository
            val transactionData = gatherTransactionData()

            // Get conversation history for AI context
            val conversationHistory = insightsHistoryManager.getRecentHistory(count = 5)
            logger.debug(
                where = "fetchInsightsFromApi",
                what = "[CONTEXT] Including ${conversationHistory.size} historical insights for AI comparison"
            )

            // Get previous period data for comparison
            val previousPeriodData = gatherPreviousPeriodData()

            // NEW: Generate CSV from all transactions
            val allTransactions = expenseRepository.getAllTransactionsSync()
            val transactionsCSV = csvGenerator.generateCSV(allTransactions)
            val csvMetadataInfo = csvGenerator.getMetadata(allTransactions, transactionsCSV.length)

            logger.debug(
                where = "fetchInsightsFromApi",
                what = "[CSV] Generated CSV with ${csvMetadataInfo.totalTransactions} transactions"
            )
            logger.debug(
                where = "fetchInsightsFromApi",
                what = "[CSV] Date range: ${csvMetadataInfo.dateRangeStart} to ${csvMetadataInfo.dateRangeEnd}"
            )
            logger.debug(
                where = "fetchInsightsFromApi",
                what = "[CSV] Payload size: ${csvMetadataInfo.csvSizeBytes / 1024}KB"
            )

            // Create CSV metadata for API
            val csvMetadata = com.expensemanager.app.data.api.insights.CSVMetadata(
                totalTransactions = csvMetadataInfo.totalTransactions,
                dateRangeStart = csvMetadataInfo.dateRangeStart,
                dateRangeEnd = csvMetadataInfo.dateRangeEnd,
                csvSizeBytes = csvMetadataInfo.csvSizeBytes,
                includesCategories = true,
                includesTimeAnalysis = true
            )

            // Create API request with conversation history AND CSV data
            val request = AIInsightsRequest(
                userId = generateUserId(),
                timeframe = "last_30_days",
                transactionSummary = transactionData.transactionSummary,
                contextData = transactionData.contextData,
                conversationHistory = conversationHistory.ifEmpty { null },
                previousPeriodData = previousPeriodData,
                transactionsCSV = transactionsCSV,
                csvMetadata = csvMetadata
            )

            // Make API call
            val response = apiService.generateInsights(request)

            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse != null) {
                    val insights = InsightsDataMapper.mapToDomainInsights(apiResponse)
                    logger.debug(
                        where = "fetchInsightsFromApi",
                        what = "API call successful: ${insights.size} insights received"
                    )

                    // Update AI call tracking (increment counters for subscription tier limits)
                    val allTransactionsForTracking = Transaction.fromEntities(expenseRepository.getAllTransactionsSync())
                    aiThresholdService.updateAfterSuccessfulCall(allTransactionsForTracking)

                    // Save insights to history for future context
                    val topCategories = transactionData.transactionSummary.categoryBreakdown
                        .take(3)
                        .map { it.categoryName }
                    insightsHistoryManager.saveInsights(
                        insights,
                        transactionData.transactionSummary.totalSpent,
                        topCategories
                    )
                    logger.debug(
                        where = "fetchInsightsFromApi",
                        what = "[CONTEXT] Saved ${insights.size} insights to conversation history"
                    )

                    Result.success(insights)
                } else {
                    logger.error(
                        where = "fetchInsightsFromApi",
                        what = "API returned null response",
                        throwable = null
                    )
                    Result.failure(Exception("API Error: Null response"))
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                logger.error(
                    where = "fetchInsightsFromApi",
                    what = "HTTP error: $errorMsg",
                    throwable = null
                )
                Result.failure(Exception("Network Error: $errorMsg"))
            }

        } catch (e: Exception) {
            logger.error(
                where = "fetchInsightsFromApi",
                what = "Exception during API call",
                throwable = e
            )
            Result.failure(e)
        }
    }
    
    /**
     * Gather transaction data from ExpenseRepository
     */
    private suspend fun gatherTransactionData(): TransactionDataPackage {
        val calendar = Calendar.getInstance()
        
        // Current month date range
        val currentMonthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.time
        
        val currentMonthEnd = Date()
        
        // Previous month for comparison
        val previousMonthStart = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.time
        
        val previousMonthEnd = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.time
        
        // Debug: Log exclusion status before gathering data
        val exclusionDebugInfo = expenseRepository.getExclusionStatesDebugInfo()
        logger.debug(
            where = "gatherTransactionData",
            what = "[DEBUG] AI Data Prep - Exclusion Status: $exclusionDebugInfo"
        )
        
        // Get dashboard data for current month (already filtered by exclusions)
        val dashboardData = expenseRepository.getDashboardData(currentMonthStart, currentMonthEnd)
        
        // Get previous month spending for comparison (already filtered by exclusions)
        val previousMonthSpent = expenseRepository.getTotalSpent(previousMonthStart, previousMonthEnd)
        
        // Debug: Log filtered data being sent to AI
        logger.debug(
            where = "gatherTransactionData",
            what = "[DEBUG] AI Data Prep - Filtered totals: Current ₹${dashboardData.totalSpent}, Previous ₹$previousMonthSpent"
        )
        logger.debug(
            where = "gatherTransactionData",
            what = "[DEBUG] AI Data Prep - Filtered merchants: ${dashboardData.topMerchants.size} merchants"
        )
        logger.debug(
            where = "gatherTransactionData",
            what = "[DEBUG] AI Data Prep - Filtered categories: ${dashboardData.topCategories.size} categories"
        )
        
        // Generate monthly trends (last 3 months)
        val monthlyTrends = generateMonthlyTrends()
        
        // Get budget information
        val budgetPrefs = context.getSharedPreferences("budget_settings", Context.MODE_PRIVATE)
        val monthlyBudget = budgetPrefs.getFloat("monthly_budget", 0f).toDouble().takeIf { it > 0 }
        
        val budgetProgress = if (monthlyBudget != null && monthlyBudget > 0) {
            ((dashboardData.totalSpent / monthlyBudget) * 100).toInt()
        } else {
            0
        }
        
        // Map to API models
        val transactionSummary = InsightsDataMapper.mapToTransactionSummary(dashboardData, monthlyTrends)
        
        // Get user currency preference
        val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currency = appPrefs.getString("currency", "INR") ?: "INR"
        
        val contextData = InsightsDataMapper.createContextData(
            monthlyBudget = monthlyBudget,
            previousMonthSpent = previousMonthSpent,
            budgetProgressPercentage = budgetProgress,
            currency = currency
        )

        logger.debug(
            where = "gatherTransactionData",
            what = "Transaction data gathered: ₹${dashboardData.totalSpent} current, ₹$previousMonthSpent previous"
        )
        
        return TransactionDataPackage(transactionSummary, contextData)
    }
    
    /**
     * Gather previous period data for AI comparison
     */
    private suspend fun gatherPreviousPeriodData(): com.expensemanager.app.data.api.insights.PreviousPeriodData? {
        return try {
            // Get data from 1 month ago (same duration as current period)
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -1)

            val previousMonthStart = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.time

            val previousMonthEnd = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.time

            // Get dashboard data for previous month
            val previousData = expenseRepository.getDashboardData(previousMonthStart, previousMonthEnd)

            // Get previous insights for that period
            val previousInsights = insightsHistoryManager.getConversationHistory()
                .filter { insight ->
                    val insightTime = insight.timestamp
                    insightTime >= previousMonthStart.time && insightTime <= previousMonthEnd.time
                }
                .map { "${it.type}: ${it.title}" }

            // Calculate average daily spending
            val daysInPreviousMonth = Calendar.getInstance().apply {
                time = calendar.time
            }.getActualMaximum(Calendar.DAY_OF_MONTH)
            val avgDailySpending = previousData.totalSpent / daysInPreviousMonth

            // Map to API model
            val categorySpending = previousData.topCategories.map {
                com.expensemanager.app.data.api.insights.CategorySpending(
                    categoryName = it.category_name,
                    totalAmount = it.total_amount,
                    transactionCount = it.transaction_count,
                    percentage = (it.total_amount / previousData.totalSpent) * 100,
                    averagePerTransaction = if (it.transaction_count > 0) it.total_amount / it.transaction_count else 0.0
                )
            }

            val merchantSpending = previousData.topMerchants.map {
                com.expensemanager.app.data.api.insights.MerchantSpending(
                    merchantName = it.normalized_merchant,
                    totalAmount = it.total_amount,
                    transactionCount = it.transaction_count,
                    categoryName = "General", // MerchantSpending doesn't have category field
                    averageAmount = if (it.transaction_count > 0) it.total_amount / it.transaction_count else 0.0
                )
            }

            com.expensemanager.app.data.api.insights.PreviousPeriodData(
                periodLabel = "Last Month",
                totalSpent = previousData.totalSpent,
                transactionCount = previousData.transactionCount,
                topCategories = categorySpending,
                topMerchants = merchantSpending,
                averageDailySpending = avgDailySpending,
                highestSpendingDay = previousData.totalSpent, // Could be improved with daily breakdown
                insightsProvided = previousInsights
            )

        } catch (e: Exception) {
            logger.error(
                where = "gatherPreviousPeriodData",
                what = "Error gathering previous period data",
                throwable = e
            )
            null
        }
    }

    /**
     * Generate monthly trends for the last 3 months
     */
    private suspend fun generateMonthlyTrends(): List<MonthlySummary> {
        logger.debug(
            where = "generateMonthlyTrends",
            what = "[DEBUG] AI Data Prep - Generating monthly trends (with exclusions)"
        )
        val trends = mutableListOf<MonthlySummary>()
        
        for (monthsBack in 0..2) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -monthsBack)
            
            val startOfMonth = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.time
            
            val endOfMonth = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.time
            
            // These calls already apply exclusion filtering in ExpenseRepository
            val totalSpent = expenseRepository.getTotalSpent(startOfMonth, endOfMonth)
            val transactionCount = expenseRepository.getTransactionCount(startOfMonth, endOfMonth)
            
            val monthKey = String.format("%d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
            
            trends.add(MonthlySummary(
                month = monthKey,
                totalAmount = totalSpent,
                transactionCount = transactionCount,
                averagePerTransaction = if (transactionCount > 0) totalSpent / transactionCount else 0.0
            ))
        }
        
        logger.debug(
            where = "generateMonthlyTrends",
            what = "[DEBUG] AI Data Prep - Monthly trends complete: ${trends.size} months"
        )
        return trends.reversed() // Return oldest to newest
    }
    
    /**
     * Check if insights need to be refreshed based on cache expiry and network status
     */
    private fun shouldRefreshInsights(): Boolean {
        val lastRefresh = cacheManager.getLastRefreshTimestamp()
        val now = System.currentTimeMillis()
        val expiryTime = CACHE_EXPIRY_HOURS * 60 * 60 * 1000 // Convert to milliseconds
        
        val isExpired = (now - lastRefresh) > expiryTime
        val isOnline = isNetworkAvailable()
        
        // Only refresh if expired AND online
        return isExpired && isOnline
    }
    
    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } else {
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                activeNetworkInfo != null && activeNetworkInfo.isConnected
            }
        } catch (e: Exception) {
            logger.error(
                where = "isNetworkAvailable",
                what = "Error checking network availability",
                throwable = e
            )
            false
        }
    }
    
    
    /**
     * Enable/disable offline mode
     */
    fun setOfflineMode(enabled: Boolean) {
        cacheManager.setOfflineMode(enabled)
    }
    
    /**
     * Check if offline mode is enabled
     */
    fun isOfflineModeEnabled(): Boolean = cacheManager.isOfflineModeEnabled()
    
    /**
     * Clear cached insights (useful for testing)
     */
    suspend fun clearCache(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            cacheManager.clearCache()

            logger.debug(
                where = "clearCache",
                what = "All caches cleared"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(
                where = "clearCache",
                what = "Error clearing cache",
                throwable = e
            )
            Result.failure(e)
        }
    }
    
    /**
     * Get cache status information
     */
    fun getCacheStatus(): CacheStatus {
        val lastRefresh = cacheManager.getLastRefreshTimestamp()
        val offlineLastRefresh = cacheManager.getOfflineLastRefreshTimestamp()
        val isOnline = isNetworkAvailable()
        val isOfflineMode = cacheManager.isOfflineModeEnabled()
        
        return CacheStatus(
            lastRefreshTime = lastRefresh,
            offlineLastRefreshTime = offlineLastRefresh,
            isOnline = isOnline,
            isOfflineModeEnabled = isOfflineMode,
            cacheValid = !shouldRefreshInsights(),
            offlineCacheValid = (System.currentTimeMillis() - offlineLastRefresh) <= (OFFLINE_CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000)
        )
    }
}

/**
 * Data package for transaction information
 */
private data class TransactionDataPackage(
    val transactionSummary: TransactionSummary,
    val contextData: ContextData
)

/**
 * Cache status information
 */
data class CacheStatus(
    val lastRefreshTime: Long,
    val offlineLastRefreshTime: Long,
    val isOnline: Boolean,
    val isOfflineModeEnabled: Boolean,
    val cacheValid: Boolean,
    val offlineCacheValid: Boolean
)

/**
 * Custom exception for offline scenarios
 */
class OfflineException(message: String) : Exception(message)
