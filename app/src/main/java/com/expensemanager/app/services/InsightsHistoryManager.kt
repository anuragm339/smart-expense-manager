package com.expensemanager.app.services

import android.content.Context
import android.content.SharedPreferences
import com.expensemanager.app.data.api.insights.HistoricalInsight
import com.expensemanager.app.data.models.AIInsight
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages conversation history for AI Insights
 * Stores and retrieves previous insights so AI can track trends over time
 */
@Singleton
class InsightsHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "InsightsHistoryManager"
        private const val PREFS_NAME = "insights_history"
        private const val KEY_HISTORY = "conversation_history"
        private const val MAX_HISTORY_SIZE = 20 // Keep last 20 insights
        private const val HISTORY_RETENTION_DAYS = 90 // 3 months
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save a new insight to conversation history
     */
    fun saveInsight(insight: AIInsight, currentSpending: Double, topCategories: List<String>) {
        try {
            val history = getConversationHistory().toMutableList()

            // Create historical insight record
            val historicalInsight = HistoricalInsight(
                timestamp = System.currentTimeMillis(),
                type = insight.type.name,
                title = insight.title,
                keyFindings = listOf(
                    insight.description,
                    insight.actionableAdvice
                ).filter { it.isNotBlank() },
                userActionTaken = false, // Will be updated if user acts on it
                spendingAtTime = currentSpending,
                topCategoriesAtTime = topCategories
            )

            // Add to history
            history.add(historicalInsight)

            // Keep only recent history (FIFO)
            val trimmedHistory = if (history.size > MAX_HISTORY_SIZE) {
                history.takeLast(MAX_HISTORY_SIZE)
            } else {
                history
            }

            // Remove insights older than retention period
            val retentionCutoff = System.currentTimeMillis() - (HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            val filteredHistory = trimmedHistory.filter { it.timestamp >= retentionCutoff }

            // Save to SharedPreferences
            val json = gson.toJson(filteredHistory)
            prefs.edit().putString(KEY_HISTORY, json).apply()

            Timber.tag(TAG).d("Saved insight to history. Total history size: ${filteredHistory.size}")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error saving insight to history")
        }
    }

    /**
     * Save multiple insights at once (batch operation)
     */
    fun saveInsights(insights: List<AIInsight>, currentSpending: Double, topCategories: List<String>) {
        insights.forEach { insight ->
            saveInsight(insight, currentSpending, topCategories)
        }
    }

    /**
     * Get conversation history for AI context
     */
    fun getConversationHistory(): List<HistoricalInsight> {
        return try {
            val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
            val type = object : TypeToken<List<HistoricalInsight>>() {}.type
            val history: List<HistoricalInsight> = gson.fromJson(json, type)

            // Filter out expired insights
            val retentionCutoff = System.currentTimeMillis() - (HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            history.filter { it.timestamp >= retentionCutoff }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading conversation history")
            emptyList()
        }
    }

    /**
     * Get recent conversation history (last N insights)
     */
    fun getRecentHistory(count: Int = 5): List<HistoricalInsight> {
        return getConversationHistory().takeLast(count)
    }

    /**
     * Get insights of a specific type from history
     */
    fun getHistoryByType(type: String): List<HistoricalInsight> {
        return getConversationHistory().filter { it.type == type }
    }

    /**
     * Mark an insight as acted upon by the user
     */
    fun markInsightActedUpon(insightTimestamp: Long) {
        try {
            val history = getConversationHistory().toMutableList()
            val updatedHistory = history.map { insight ->
                if (insight.timestamp == insightTimestamp) {
                    insight.copy(userActionTaken = true)
                } else {
                    insight
                }
            }

            val json = gson.toJson(updatedHistory)
            prefs.edit().putString(KEY_HISTORY, json).apply()

            Timber.tag(TAG).d("Marked insight as acted upon: $insightTimestamp")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error marking insight as acted upon")
        }
    }

    /**
     * Clear all conversation history
     */
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        Timber.tag(TAG).d("Cleared all conversation history")
    }

    /**
     * Get summary statistics about history
     */
    fun getHistoryStats(): HistoryStats {
        val history = getConversationHistory()
        return HistoryStats(
            totalInsights = history.size,
            actionTakenCount = history.count { it.userActionTaken },
            oldestTimestamp = history.minByOrNull { it.timestamp }?.timestamp,
            newestTimestamp = history.maxByOrNull { it.timestamp }?.timestamp,
            insightTypeBreakdown = history.groupBy { it.type }.mapValues { it.value.size }
        )
    }

    /**
     * Export history as JSON (for debugging/backup)
     */
    fun exportHistoryJson(): String {
        return try {
            val history = getConversationHistory()
            gson.toJson(history)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error exporting history")
            "[]"
        }
    }
}

/**
 * Statistics about conversation history
 */
data class HistoryStats(
    val totalInsights: Int,
    val actionTakenCount: Int,
    val oldestTimestamp: Long?,
    val newestTimestamp: Long?,
    val insightTypeBreakdown: Map<String, Int>
)
