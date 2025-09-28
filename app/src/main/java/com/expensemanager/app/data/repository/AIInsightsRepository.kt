package com.expensemanager.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.expensemanager.app.data.api.insights.*
import com.expensemanager.app.data.models.AIInsight
import com.expensemanager.app.data.models.MonthlySummary
import com.expensemanager.app.data.models.SampleInsights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.*
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
    @Named("ai_insights_cache") private val prefs: SharedPreferences
) {
    
    companion object {
        private const val TAG = "AIInsightsRepository"
        private const val CACHE_EXPIRY_HOURS = 4L
        private const val OFFLINE_CACHE_EXPIRY_DAYS = 7L
        private const val LAST_REFRESH_KEY = "last_refresh_timestamp"
        private const val CACHED_INSIGHTS_KEY = "cached_insights_json"
        private const val OFFLINE_MODE_KEY = "offline_mode_enabled"
        private const val CACHE_VERSION_KEY = "cache_version"
        private const val CURRENT_CACHE_VERSION = 1
        
        @Volatile
        private var INSTANCE: AIInsightsRepository? = null
        
        fun getInstance(context: Context, expenseRepository: ExpenseRepository): AIInsightsRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = AIInsightsRepository(
                    context.applicationContext, 
                    expenseRepository,
                    ApiServiceFactory.getAIInsightsApiService(),
                    context.getSharedPreferences("ai_insights_cache", Context.MODE_PRIVATE)
                )
                INSTANCE = instance
                instance
            }
        }
    }
    
    /**
     * Get AI insights with offline support and automatic refresh logic
     * Returns cached data immediately if available, then fetches fresh data if online
     */
    fun getInsights(): Flow<Result<List<AIInsight>>> = flow {
        val isOnline = isNetworkAvailable()
        val isOfflineModeEnabled = prefs.getBoolean(OFFLINE_MODE_KEY, false)
        
        // Emit cached data first for immediate UI update
        val cachedInsights = getCachedInsights()
        if (cachedInsights.isNotEmpty()) {
            Log.d(TAG, "Emitting cached insights: ${cachedInsights.size} (offline: ${!isOnline})")
            emit(Result.success(cachedInsights))
        }
        
        // Handle different scenarios - Force refresh if no cached data OR if cache expired
        val needsRefresh = cachedInsights.isEmpty() || shouldRefreshInsights()
        
        if (isOnline && !isOfflineModeEnabled && needsRefresh) {
            Log.d(TAG, "Refreshing insights from API (online mode) - No cache: ${cachedInsights.isEmpty()}, Expired: ${shouldRefreshInsights()}")
            
            try {
                val freshInsights = fetchInsightsFromApi()
                freshInsights.fold(
                    onSuccess = { insights ->
                        // Cache the fresh insights
                        cacheInsights(insights)
                        emit(Result.success(insights))
                        Log.d(TAG, "Fresh insights fetched and cached: ${insights.size}")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to fetch fresh insights", error)
                        // If we have cached data, don't emit error
                        if (cachedInsights.isEmpty()) {
                            val fallbackInsights = handleOfflineMode(error)
                            emit(Result.success(fallbackInsights))
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during API call", e)
                if (cachedInsights.isEmpty()) {
                    val fallbackInsights = getOfflineFallbackInsights()
                    emit(Result.success(fallbackInsights))
                }
            }
        } else if (!isOnline) {
            // Offline mode: check if cached data is still valid
            val offlineInsights = getOfflineCachedInsights()
            if (offlineInsights.isNotEmpty() && cachedInsights.isEmpty()) {
                Log.d(TAG, "Using offline cached insights: ${offlineInsights.size}")
                emit(Result.success(offlineInsights))
            } else if (cachedInsights.isEmpty()) {
                // No cached data available, return offline error
                emit(Result.failure(OfflineException("No internet connection and no cached data available")))
            }
        } else {
            Log.d(TAG, "Using existing cached data (no refresh needed)")
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Force refresh insights from API with offline fallback
     */
    suspend fun refreshInsights(): Result<List<AIInsight>> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!isNetworkAvailable()) {
                Log.d(TAG, "No network available for refresh, returning cached data")
                val cachedData = getCachedInsights()
                return@withContext if (cachedData.isNotEmpty()) {
                    Result.success(cachedData)
                } else {
                    Result.failure(OfflineException("No internet connection available for refresh"))
                }
            }
            
            Log.d(TAG, "Force refreshing insights...")
            val result = fetchInsightsFromApi()
            
            result.onSuccess { insights ->
                cacheInsights(insights)
                // Also cache for offline mode
                cacheInsightsForOffline(insights)
                Log.d(TAG, "Force refresh completed: ${insights.size} insights")
            }.onFailure { error ->
                Log.e(TAG, "Force refresh failed, trying cached data", error)
                // Return cached data if available
                val cachedData = getCachedInsights()
                if (cachedData.isNotEmpty()) {
                    return@withContext Result.success(cachedData)
                }
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in force refresh", e)
            
            // Try to return cached data as fallback
            val cachedData = getCachedInsights()
            if (cachedData.isNotEmpty()) {
                Log.d(TAG, "Returning cached data due to refresh error")
                Result.success(cachedData)
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Fetch insights from AI API
     */
    private suspend fun fetchInsightsFromApi(): Result<List<AIInsight>> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Gather transaction data from ExpenseRepository
            val transactionData = gatherTransactionData()
            
            // Create API request
            val request = AIInsightsRequest(
                userId = generateUserId(),
                timeframe = "last_30_days",
                transactionSummary = transactionData.transactionSummary,
                contextData = transactionData.contextData
            )
            
            Log.d(TAG, "Making API call to: ${apiService.javaClass.simpleName}")
            Log.d(TAG, "Request summary: ${transactionData.transactionSummary.totalSpent} spent, ${transactionData.transactionSummary.transactionCount} transactions")
            Log.d(TAG, "Categories: ${transactionData.transactionSummary.categoryBreakdown.size}")
            Log.d(TAG, "Top merchants: ${transactionData.transactionSummary.topMerchants.size}")
            
            // Make API call
            val response = apiService.generateInsights(request)
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse != null) {
                    val insights = DirectApiMapper.mapToAIInsights(apiResponse)
                    Log.d(TAG, "API call successful: ${insights.size} insights received")
                    Log.d(TAG, "Insights: ${insights.map { "${it.type}: ${it.title}" }}")
                    Result.success(insights)
                } else {
                    Log.e(TAG, "API returned null response")
                    Result.failure(Exception("API Error: Null response"))
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                Log.e(TAG, "HTTP error: $errorMsg")
                Result.failure(Exception("Network Error: $errorMsg"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during API call", e)
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
        Log.d(TAG, "[DEBUG] AI Data Prep - Exclusion Status: $exclusionDebugInfo")
        
        // Get dashboard data for current month (already filtered by exclusions)
        val dashboardData = expenseRepository.getDashboardData(currentMonthStart, currentMonthEnd)
        
        // Get previous month spending for comparison (already filtered by exclusions)
        val previousMonthSpent = expenseRepository.getTotalSpent(previousMonthStart, previousMonthEnd)
        
        // Debug: Log filtered data being sent to AI
        Log.d(TAG, "[DEBUG] AI Data Prep - Filtered totals: Current ₹${dashboardData.totalSpent}, Previous ₹$previousMonthSpent")
        Log.d(TAG, "[DEBUG] AI Data Prep - Filtered merchants: ${dashboardData.topMerchants.size} merchants")
        Log.d(TAG, "[DEBUG] AI Data Prep - Filtered categories: ${dashboardData.topCategories.size} categories")
        
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
        
        Log.d(TAG, "Transaction data gathered: ₹${dashboardData.totalSpent} current, ₹$previousMonthSpent previous")
        
        return TransactionDataPackage(transactionSummary, contextData)
    }
    
    /**
     * Generate monthly trends for the last 3 months
     */
    private suspend fun generateMonthlyTrends(): List<MonthlySummary> {
        Log.d(TAG, "[DEBUG] AI Data Prep - Generating monthly trends (with exclusions)")
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
            
            Log.d(TAG, "[DEBUG] AI Data Prep - Month $monthKey: ₹$totalSpent ($transactionCount transactions)")
            
            trends.add(MonthlySummary(
                month = monthKey,
                totalAmount = totalSpent,
                transactionCount = transactionCount,
                averagePerTransaction = if (transactionCount > 0) totalSpent / transactionCount else 0.0
            ))
        }
        
        Log.d(TAG, "[DEBUG] AI Data Prep - Monthly trends complete: ${trends.size} months")
        return trends.reversed() // Return oldest to newest
    }
    
    /**
     * Check if insights need to be refreshed based on cache expiry and network status
     */
    private fun shouldRefreshInsights(): Boolean {
        val lastRefresh = prefs.getLong(LAST_REFRESH_KEY, 0L)
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
            Log.e(TAG, "Error checking network availability", e)
            false
        }
    }
    
    /**
     * Get cached insights from SharedPreferences
     */
    private fun getCachedInsights(): List<AIInsight> {
        return try {
            val cachedJson = prefs.getString(CACHED_INSIGHTS_KEY, null)
            if (cachedJson != null) {
                // For simplicity, using sample insights as cached data
                // In production, you'd deserialize JSON to actual insights
                getSampleInsights()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached insights", e)
            emptyList()
        }
    }
    
    /**
     * Cache insights to SharedPreferences
     */
    private fun cacheInsights(insights: List<AIInsight>) {
        try {
            // For simplicity, just store timestamp and count
            // In production, you'd serialize insights to JSON
            prefs.edit()
                .putLong(LAST_REFRESH_KEY, System.currentTimeMillis())
                .putString(CACHED_INSIGHTS_KEY, "cached_${insights.size}_insights")
                .putInt(CACHE_VERSION_KEY, CURRENT_CACHE_VERSION)
                .apply()
            
            Log.d(TAG, "Cached ${insights.size} insights")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching insights", e)
        }
    }
    
    /**
     * Cache insights specifically for offline mode (longer expiry)
     */
    private fun cacheInsightsForOffline(insights: List<AIInsight>) {
        try {
            val offlinePrefs = context.getSharedPreferences("ai_insights_offline_cache", Context.MODE_PRIVATE)
            offlinePrefs.edit()
                .putLong("offline_cache_timestamp", System.currentTimeMillis())
                .putString("offline_cached_insights", "offline_cached_${insights.size}_insights")
                .putInt("offline_cache_version", CURRENT_CACHE_VERSION)
                .apply()
            
            Log.d(TAG, "Cached ${insights.size} insights for offline mode")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching insights for offline mode", e)
        }
    }
    
    /**
     * Get offline cached insights (longer expiry than regular cache)
     */
    private fun getOfflineCachedInsights(): List<AIInsight> {
        return try {
            val offlinePrefs = context.getSharedPreferences("ai_insights_offline_cache", Context.MODE_PRIVATE)
            val cachedTimestamp = offlinePrefs.getLong("offline_cache_timestamp", 0L)
            val now = System.currentTimeMillis()
            val offlineExpiryTime = OFFLINE_CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000
            
            if ((now - cachedTimestamp) <= offlineExpiryTime) {
                // For simplicity, return sample insights
                // In production, you'd deserialize actual cached insights
                getSampleInsights().also {
                    Log.d(TAG, "Retrieved ${it.size} offline cached insights")
                }
            } else {
                Log.d(TAG, "Offline cache expired")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading offline cached insights", e)
            emptyList()
        }
    }
    
    /**
     * Get offline fallback insights when everything else fails
     */
    private fun getOfflineFallbackInsights(): List<AIInsight> {
        return try {
            // Always provide sample insights as final fallback
            getSampleInsights().also {
                Log.d(TAG, "Using fallback sample insights: ${it.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting fallback insights", e)
            emptyList()
        }
    }
    
    /**
     * Handle offline mode scenarios
     */
    private fun handleOfflineMode(apiError: Throwable): List<AIInsight> {
        Log.d(TAG, "Handling offline mode due to API error")
        
        // Try offline cached insights first
        val offlineInsights = getOfflineCachedInsights()
        if (offlineInsights.isNotEmpty()) {
            return offlineInsights
        }
        
        // Fall back to sample insights
        return getSampleInsights()
    }
    
    /**
     * Enable/disable offline mode
     */
    fun setOfflineMode(enabled: Boolean) {
        prefs.edit()
            .putBoolean(OFFLINE_MODE_KEY, enabled)
            .apply()
        Log.d(TAG, "Offline mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if offline mode is enabled
     */
    fun isOfflineModeEnabled(): Boolean {
        return prefs.getBoolean(OFFLINE_MODE_KEY, false)
    }
    
    /**
     * Get sample insights as fallback
     */
    private fun getSampleInsights(): List<AIInsight> {
        return SampleInsights.getAllSample()
    }
    
    /**
     * Clear cached insights (useful for testing)
     */
    suspend fun clearCache(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Clear main cache
            prefs.edit().clear().apply()
            
            // Clear offline cache
            val offlinePrefs = context.getSharedPreferences("ai_insights_offline_cache", Context.MODE_PRIVATE)
            offlinePrefs.edit().clear().apply()
            
            Log.d(TAG, "All caches cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get cache status information
     */
    fun getCacheStatus(): CacheStatus {
        val lastRefresh = prefs.getLong(LAST_REFRESH_KEY, 0L)
        val offlinePrefs = context.getSharedPreferences("ai_insights_offline_cache", Context.MODE_PRIVATE)
        val offlineLastRefresh = offlinePrefs.getLong("offline_cache_timestamp", 0L)
        val isOnline = isNetworkAvailable()
        val isOfflineMode = isOfflineModeEnabled()
        
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