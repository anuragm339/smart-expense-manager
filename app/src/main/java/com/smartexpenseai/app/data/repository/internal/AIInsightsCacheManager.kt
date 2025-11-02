package com.smartexpenseai.app.data.repository.internal

import android.content.Context
import android.content.SharedPreferences
import com.smartexpenseai.app.data.models.AIInsight
import com.smartexpenseai.app.utils.logging.StructuredLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal class AIInsightsCacheManager(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val offlineCacheExpiryDays: Long
) {

    private val logger = StructuredLogger(
        featureTag = "INSIGHTS",
        className = "AIInsightsCacheManager"
    )
    private val gson = Gson()
    private val offlinePrefs by lazy {
        context.getSharedPreferences(OFFLINE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCachedInsights(): List<AIInsight> {
        return try {
            val version = prefs.getInt(CACHE_VERSION_KEY, CURRENT_CACHE_VERSION)
            if (version != CURRENT_CACHE_VERSION) {
                prefs.edit().clear().apply()
                return emptyList()
            }

            val cachedJson = prefs.getString(CACHED_INSIGHTS_KEY, null) ?: return emptyList()
            if (!cachedJson.startsWith("[")) {
                logger.warn(
                    where = "getCachedInsights",
                    what = "Invalid cached format (old version), clearing cache"
                )
                prefs.edit().remove(CACHED_INSIGHTS_KEY).apply()
                return emptyList()
            }

            val type = object : TypeToken<List<AIInsight>>() {}.type
            val insights: List<AIInsight> = gson.fromJson(cachedJson, type)
            logger.debug(
                where = "getCachedInsights",
                what = "Loaded ${insights.size} cached insights from storage"
            )
            insights
        } catch (e: Exception) {
            logger.error(
                where = "getCachedInsights",
                what = "Error loading cached insights, clearing cache",
                throwable = e
            )
            prefs.edit().remove(CACHED_INSIGHTS_KEY).apply()
            emptyList()
        }
    }

    fun cacheInsights(insights: List<AIInsight>) {
        try {
            val json = gson.toJson(insights)
            prefs.edit()
                .putLong(LAST_REFRESH_KEY, System.currentTimeMillis())
                .putString(CACHED_INSIGHTS_KEY, json)
                .putInt(CACHE_VERSION_KEY, CURRENT_CACHE_VERSION)
                .apply()

            logger.debug(
                where = "cacheInsights",
                what = "Cached ${insights.size} insights to storage (${json.length} bytes)"
            )
        } catch (e: Exception) {
            logger.error(
                where = "cacheInsights",
                what = "Error caching insights",
                throwable = e
            )
        }
    }

    fun cacheInsightsForOffline(insights: List<AIInsight>) {
        try {
            val json = gson.toJson(insights)
            offlinePrefs.edit()
                .putLong(OFFLINE_CACHE_TIMESTAMP_KEY, System.currentTimeMillis())
                .putString(OFFLINE_CACHED_INSIGHTS_KEY, json)
                .putInt(OFFLINE_CACHE_VERSION_KEY, CURRENT_CACHE_VERSION)
                .apply()

            logger.debug(
                where = "cacheInsightsForOffline",
                what = "Cached ${insights.size} insights for offline mode (${json.length} bytes)"
            )
        } catch (e: Exception) {
            logger.error(
                where = "cacheInsightsForOffline",
                what = "Error caching insights for offline mode",
                throwable = e
            )
        }
    }

    fun getOfflineCachedInsights(): List<AIInsight> {
        return try {
            val cachedTimestamp = offlinePrefs.getLong(OFFLINE_CACHE_TIMESTAMP_KEY, 0L)
            val now = System.currentTimeMillis()
            val offlineExpiryTime = offlineCacheExpiryDays * 24 * 60 * 60 * 1000

            if ((now - cachedTimestamp) > offlineExpiryTime) {
                logger.debug(
                    where = "getOfflineCachedInsights",
                    what = "Offline cache expired"
                )
                emptyList()
            } else {
                val cachedJson = offlinePrefs.getString(OFFLINE_CACHED_INSIGHTS_KEY, null)
                if (cachedJson.isNullOrBlank()) {
                    emptyList()
                } else {
                    val type = object : TypeToken<List<AIInsight>>() {}.type
                    val insights: List<AIInsight> = gson.fromJson(cachedJson, type)
                    logger.debug(
                        where = "getOfflineCachedInsights",
                        what = "Loaded ${insights.size} offline cached insights"
                    )
                    insights
                }
            }
        } catch (e: Exception) {
            logger.error(
                where = "getOfflineCachedInsights",
                what = "Error loading offline cached insights",
                throwable = e
            )
            emptyList()
        }
    }

    fun getOfflineFallbackInsights(): List<AIInsight> {
        return getOfflineCachedInsights()
    }

    fun handleOfflineMode(): List<AIInsight> {
        return getOfflineCachedInsights()
    }

    fun setOfflineMode(enabled: Boolean) {
        prefs.edit()
            .putBoolean(OFFLINE_MODE_KEY, enabled)
            .apply()
    }

    fun isOfflineModeEnabled(): Boolean = prefs.getBoolean(OFFLINE_MODE_KEY, false)

    fun clearCache() {
        prefs.edit().clear().apply()
        offlinePrefs.edit().clear().apply()
    }

    fun getLastRefreshTimestamp(): Long = prefs.getLong(LAST_REFRESH_KEY, 0L)

    fun getOfflineLastRefreshTimestamp(): Long = offlinePrefs.getLong(OFFLINE_CACHE_TIMESTAMP_KEY, 0L)

    companion object {
        private const val CACHE_VERSION_KEY = "cache_version"
        private const val CURRENT_CACHE_VERSION = 1
        private const val CACHED_INSIGHTS_KEY = "cached_insights_json"
        private const val LAST_REFRESH_KEY = "last_refresh_timestamp"
        private const val OFFLINE_PREFS_NAME = "ai_insights_offline_cache"
        private const val OFFLINE_CACHE_TIMESTAMP_KEY = "offline_cache_timestamp"
        private const val OFFLINE_CACHED_INSIGHTS_KEY = "offline_cached_insights"
        private const val OFFLINE_CACHE_VERSION_KEY = "offline_cache_version"
        private const val OFFLINE_MODE_KEY = "offline_mode_enabled"
    }
}
