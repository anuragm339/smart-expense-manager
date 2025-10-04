package com.expensemanager.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Configuration for AI call thresholds
 * Determines when to trigger new AI insights calls based on user activity
 */
data class AICallThresholds(
    val minTransactionCount: Int = 10,        // Minimum new transactions needed
    val minAmountChange: Double = 1000.0,     // Minimum spending change in INR
    val minDaysSinceLastCall: Int = 3,        // Minimum days between calls
    val forceRefreshDays: Int = 7,            // Force refresh after X days regardless
    val minCategoryChanges: Int = 2           // Minimum categories with significant changes
)

/**
 * Room entity to track AI call history and determine next eligible call
 */
@Entity(tableName = "ai_call_tracking")
data class AICallTracker(
    @PrimaryKey
    val id: String = "current",

    // Last call information
    val lastCallTimestamp: Long = 0L,
    val transactionCountAtLastCall: Int = 0,
    val totalAmountAtLastCall: Double = 0.0,
    val categoriesSnapshot: String = "{}",

    // Next call eligibility
    val nextEligibleCallTime: Long = 0L,

    // Call frequency preference
    val callFrequency: String = "BALANCED",

    // Usage tracking
    val totalApiCalls: Int = 0,
    val lastErrorTimestamp: Long = 0L,
    val consecutiveErrors: Int = 0,

    // Subscription tier limits tracking
    val dailyCallCount: Int = 0,
    val lastDailyResetTimestamp: Long = 0L,
    val monthlyCallCount: Int = 0,
    val lastMonthlyResetTimestamp: Long = 0L
)

/**
 * User-configurable call frequency options
 */
enum class CallFrequency(
    val displayName: String,
    val minTransactions: Int,
    val minAmount: Double,
    val minDays: Int,
    val description: String
) {
    CONSERVATIVE(
        displayName = "Conservative",
        minTransactions = 20,
        minAmount = 2000.0,
        minDays = 7,
        description = "Fewer AI calls, lower costs"
    ),
    BALANCED(
        displayName = "Balanced",
        minTransactions = 10,
        minAmount = 1000.0,
        minDays = 3,
        description = "Good balance of insights and cost"
    ),
    FREQUENT(
        displayName = "Frequent",
        minTransactions = 5,
        minAmount = 500.0,
        minDays = 1,
        description = "More frequent insights, higher costs"
    );

    fun toThresholds(): AICallThresholds {
        return AICallThresholds(
            minTransactionCount = minTransactions,
            minAmountChange = minAmount,
            minDaysSinceLastCall = minDays,
            forceRefreshDays = minDays * 2,
            minCategoryChanges = if (this == FREQUENT) 1 else 2
        )
    }
}

/**
 * Progress towards next AI call eligibility
 */
data class ThresholdProgress(
    val isEligibleForRefresh: Boolean,
    val progressPercentage: Int,
    val daysUntilRefresh: Int,
    val transactionsNeeded: Int,
    val amountNeeded: Double,
    val lastUpdateTime: Long,
    val nextRefreshReason: String,
    val estimatedCostSavings: Double = 0.0
)

/**
 * AI call trigger reasons for analytics
 */
enum class TriggerReason(val description: String) {
    MIN_TRANSACTIONS("Minimum transaction count reached"),
    MIN_AMOUNT("Minimum spending amount reached"),
    TIME_ELAPSED("Minimum time period elapsed"),
    FORCE_REFRESH("Forced refresh due to staleness"),
    CATEGORY_CHANGES("Significant category changes detected"),
    USER_MANUAL("Manually triggered by user"),
    FIRST_TIME("First time generating insights")
}

/**
 * Result of threshold evaluation
 */
data class ThresholdEvaluationResult(
    val shouldCallAI: Boolean,
    val triggerReason: TriggerReason?,
    val progressToNextCall: ThresholdProgress,
    val estimatedApiCost: Double = 0.0,
    val rateLimitReached: Boolean = false
)