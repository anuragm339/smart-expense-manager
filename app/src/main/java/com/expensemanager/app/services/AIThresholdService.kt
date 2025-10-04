package com.expensemanager.app.services

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.data.dao.AICallDao
import com.expensemanager.app.data.models.*
import com.expensemanager.app.data.entities.TransactionEntity
import kotlinx.coroutines.flow.first
import com.expensemanager.app.data.repository.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Service to evaluate AI call thresholds and determine when to trigger new insights
 * Implements smart logic to balance cost efficiency with fresh insights
 */
@Singleton
class AIThresholdService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val aiCallDao: AICallDao,
    private val userDao: com.expensemanager.app.data.dao.UserDao,
    @Named("ai_insights_prefs") private val prefs: SharedPreferences
) {

    companion object {
        private const val TAG = "AIThresholdService"
        private const val MAX_CONSECUTIVE_ERRORS = 3
        private const val ERROR_BACKOFF_HOURS = 2
    }

    /**
     * Main evaluation method - determines if AI should be called
     */
    suspend fun shouldCallAI(): Boolean = withContext(Dispatchers.IO) {
        try {
            aiCallDao.initializeTrackerIfNeeded()
            val evaluation = evaluateThresholds()

            Timber.tag(TAG).d("Threshold evaluation: shouldCall=${evaluation.shouldCallAI}, " +
                    "reason=${evaluation.triggerReason}, " +
                    "rateLimited=${evaluation.rateLimitReached}")

            evaluation.shouldCallAI && !evaluation.rateLimitReached
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error evaluating thresholds")
            false
        }
    }

    /**
     * Detailed threshold evaluation with reasons and progress
     */
    suspend fun evaluateThresholds(): ThresholdEvaluationResult = withContext(Dispatchers.IO) {
        val tracker = aiCallDao.getCurrentTracker() ?: return@withContext ThresholdEvaluationResult(
            shouldCallAI = true,
            triggerReason = TriggerReason.FIRST_TIME,
            progressToNextCall = ThresholdProgress(
                isEligibleForRefresh = true,
                progressPercentage = 100,
                daysUntilRefresh = 0,
                transactionsNeeded = 0,
                amountNeeded = 0.0,
                lastUpdateTime = System.currentTimeMillis(),
                nextRefreshReason = "First time setup"
            )
        )

        val currentTransactionEntities = expenseRepository.getAllTransactions().first()
        val currentTransactions = Transaction.fromEntities(currentTransactionEntities)
        val currentData = TransactionSummary(
            transactionCount = currentTransactions.size,
            totalSpent = currentTransactions.sumOf { it.amount },
            categoryCount = currentTransactions.map { it.category }.distinct().size,
            averagePerTransaction = if (currentTransactions.isNotEmpty()) currentTransactions.sumOf { it.amount } / currentTransactions.size else 0.0,
            lastTransactionTime = currentTransactions.maxOfOrNull { it.timestamp } ?: 0L
        )
        val thresholds = getCurrentThresholds()
        val now = System.currentTimeMillis()

        // Check subscription tier rate limiting
        if (isRateLimited(tracker, now)) {
            return@withContext createRateLimitedResult(tracker, thresholds)
        }

        // Check for errors backoff
        if (shouldBackoffDueToErrors(tracker, now)) {
            return@withContext createErrorBackoffResult(tracker, thresholds)
        }

        // Evaluate different trigger conditions
        val triggerReason = when {
            hasMinTransactions(tracker, currentData, thresholds) -> TriggerReason.MIN_TRANSACTIONS
            hasSignificantAmountChange(tracker, currentData, thresholds) -> TriggerReason.MIN_AMOUNT
            hasTimePassed(tracker, thresholds, now) -> TriggerReason.TIME_ELAPSED
            shouldForceRefresh(tracker, thresholds, now) -> TriggerReason.FORCE_REFRESH
            hasCategoryChanges(tracker, currentData, thresholds) -> TriggerReason.CATEGORY_CHANGES
            else -> null
        }

        val shouldCall = triggerReason != null
        val progress = calculateProgress(tracker, currentData, thresholds, now)

        ThresholdEvaluationResult(
            shouldCallAI = shouldCall,
            triggerReason = triggerReason,
            progressToNextCall = progress,
            estimatedApiCost = estimateApiCost(),
            rateLimitReached = false
        )
    }

    /**
     * Update tracking after successful AI call
     */
    suspend fun updateAfterSuccessfulCall(
        transactionData: List<Transaction>
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val thresholds = getCurrentThresholds()
        val nextEligibleTime = now + TimeUnit.DAYS.toMillis(thresholds.minDaysSinceLastCall.toLong())

        val categoriesSnapshot = createCategoriesSnapshot(transactionData)

        aiCallDao.updateLastCallInfo(
            timestamp = now,
            transactionCount = transactionData.size,
            totalAmount = transactionData.sumOf { it.amount },
            categoriesJson = categoriesSnapshot,
            nextEligibleTime = nextEligibleTime
        )

        // Increment daily and monthly call counts for tier limits
        aiCallDao.incrementCallCounts()

        aiCallDao.resetErrorCount()

        // Get updated counts for logging
        val tracker = aiCallDao.getCurrentTracker()
        val user = userDao.getCurrentUser()
        val tier = user?.let { com.expensemanager.app.data.entities.SubscriptionTier.fromString(it.subscriptionTier) }

        Timber.tag(TAG).d("✅ Updated tracking after successful call. " +
                "Next eligible: ${Date(nextEligibleTime)}")
        if (tracker != null && tier != null) {
            Timber.tag(TAG).d("📊 ${tier.displayName} tier usage: " +
                    "daily=${tracker.dailyCallCount}/${tier.dailyAICallLimit}, " +
                    "monthly=${tracker.monthlyCallCount}/${tier.monthlyAICallLimit}")
        }
    }

    /**
     * Record error occurrence
     */
    suspend fun recordError() = withContext(Dispatchers.IO) {
        aiCallDao.recordError(System.currentTimeMillis())
        Timber.tag(TAG).w("Recorded AI call error")
    }

    /**
     * Update user's call frequency preference
     */
    suspend fun updateCallFrequency(frequency: CallFrequency) = withContext(Dispatchers.IO) {
        aiCallDao.updateCallFrequency(frequency.name)
        Timber.tag(TAG).d("Updated call frequency to: ${frequency.displayName}")
    }

    /**
     * Get current thresholds based on user preference
     */
    private suspend fun getCurrentThresholds(): AICallThresholds {
        val tracker = aiCallDao.getCurrentTracker()
        val frequencyName = tracker?.callFrequency ?: "BALANCED"
        val frequency = try {
            CallFrequency.valueOf(frequencyName)
        } catch (e: Exception) {
            CallFrequency.BALANCED
        }
        return frequency.toThresholds()
    }

    // Evaluation methods

    private fun hasMinTransactions(
        tracker: AICallTracker,
        currentData: TransactionSummary,
        thresholds: AICallThresholds
    ): Boolean {
        val newTransactions = currentData.transactionCount - tracker.transactionCountAtLastCall
        return newTransactions >= thresholds.minTransactionCount
    }

    private fun hasSignificantAmountChange(
        tracker: AICallTracker,
        currentData: TransactionSummary,
        thresholds: AICallThresholds
    ): Boolean {
        val amountChange = currentData.totalSpent - tracker.totalAmountAtLastCall
        return amountChange >= thresholds.minAmountChange
    }

    private fun hasTimePassed(
        tracker: AICallTracker,
        thresholds: AICallThresholds,
        now: Long
    ): Boolean {
        val daysSinceLastCall = TimeUnit.MILLISECONDS.toDays(now - tracker.lastCallTimestamp)
        return daysSinceLastCall >= thresholds.minDaysSinceLastCall
    }

    private fun shouldForceRefresh(
        tracker: AICallTracker,
        thresholds: AICallThresholds,
        now: Long
    ): Boolean {
        val daysSinceLastCall = TimeUnit.MILLISECONDS.toDays(now - tracker.lastCallTimestamp)
        return daysSinceLastCall >= thresholds.forceRefreshDays
    }

    private suspend fun hasCategoryChanges(
        tracker: AICallTracker,
        currentData: TransactionSummary,
        thresholds: AICallThresholds
    ): Boolean {
        // Simple implementation - could be enhanced with more sophisticated comparison
        val currentTransactionEntities = expenseRepository.getAllTransactions().first()
        val currentTransactions = Transaction.fromEntities(currentTransactionEntities)
        val uniqueCategories = currentTransactions.map { it.category }.distinct()
        return uniqueCategories.size >= thresholds.minCategoryChanges
    }

    /**
     * Check if user has exceeded their subscription tier limits
     */
    private suspend fun isRateLimited(tracker: AICallTracker, now: Long): Boolean {
        // Get current user and their subscription tier
        val user = userDao.getCurrentUser()

        if (user == null) {
            Timber.tag(TAG).w("🚫 No user found - rate limiting by default")
            return true // No user = rate limited
        }

        Timber.tag(TAG).d("👤 Checking rate limits for user: ${user.email} (${user.subscriptionTier} tier)")
        val tier = com.expensemanager.app.data.entities.SubscriptionTier.fromString(user.subscriptionTier)

        // Check if we need to reset daily/monthly counters
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val monthStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Reset daily count if new day
        if (tracker.lastDailyResetTimestamp < todayStart) {
            aiCallDao.resetDailyCallCount(todayStart)
        }

        // Reset monthly count if new month
        if (tracker.lastMonthlyResetTimestamp < monthStart) {
            aiCallDao.resetMonthlyCallCount(monthStart)
        }

        // Get current counts (after potential reset)
        val updatedTracker = aiCallDao.getCurrentTracker() ?: return true
        val dailyCount = updatedTracker.dailyCallCount
        val monthlyCount = updatedTracker.monthlyCallCount

        Timber.tag(TAG).d("📊 Current usage for ${tier.displayName} tier: " +
                "daily=$dailyCount/${tier.dailyAICallLimit}, " +
                "monthly=$monthlyCount/${tier.monthlyAICallLimit}")

        // Check against tier limits
        val dailyLimitReached = dailyCount >= tier.dailyAICallLimit
        val monthlyLimitReached = monthlyCount >= tier.monthlyAICallLimit

        if (dailyLimitReached || monthlyLimitReached) {
            Timber.tag(TAG).w("🚫 AI call rate limited for ${tier.displayName} tier: " +
                    "daily=$dailyCount/${tier.dailyAICallLimit}, " +
                    "monthly=$monthlyCount/${tier.monthlyAICallLimit}")
        } else {
            Timber.tag(TAG).d("✅ Rate limit check passed - API call allowed")
        }

        return dailyLimitReached || monthlyLimitReached
    }

    private fun shouldBackoffDueToErrors(tracker: AICallTracker, now: Long): Boolean {
        if (tracker.consecutiveErrors < MAX_CONSECUTIVE_ERRORS) return false

        val hoursSinceLastError = TimeUnit.MILLISECONDS.toHours(now - tracker.lastErrorTimestamp)
        return hoursSinceLastError < ERROR_BACKOFF_HOURS
    }

    private suspend fun calculateProgress(
        tracker: AICallTracker,
        currentData: TransactionSummary,
        thresholds: AICallThresholds,
        now: Long
    ): ThresholdProgress {
        val newTransactions = currentData.transactionCount - tracker.transactionCountAtLastCall
        val amountChange = currentData.totalSpent - tracker.totalAmountAtLastCall
        val daysSinceLastCall = TimeUnit.MILLISECONDS.toDays(now - tracker.lastCallTimestamp).toInt()

        val transactionProgress = (newTransactions.toFloat() / thresholds.minTransactionCount * 100).toInt()
        val amountProgress = (amountChange / thresholds.minAmountChange * 100).toInt()
        val timeProgress = (daysSinceLastCall.toFloat() / thresholds.minDaysSinceLastCall * 100).toInt()

        val overallProgress = maxOf(transactionProgress, amountProgress, timeProgress).coerceAtMost(100)

        return ThresholdProgress(
            isEligibleForRefresh = overallProgress >= 100,
            progressPercentage = overallProgress,
            daysUntilRefresh = maxOf(0, thresholds.minDaysSinceLastCall - daysSinceLastCall),
            transactionsNeeded = maxOf(0, thresholds.minTransactionCount - newTransactions),
            amountNeeded = maxOf(0.0, thresholds.minAmountChange - amountChange),
            lastUpdateTime = now,
            nextRefreshReason = when (overallProgress) {
                transactionProgress -> "Transaction count threshold"
                amountProgress -> "Spending amount threshold"
                timeProgress -> "Time threshold"
                else -> "Multiple criteria"
            }
        )
    }

    private fun createRateLimitedResult(
        tracker: AICallTracker,
        thresholds: AICallThresholds
    ): ThresholdEvaluationResult {
        return ThresholdEvaluationResult(
            shouldCallAI = false,
            triggerReason = null,
            progressToNextCall = ThresholdProgress(
                isEligibleForRefresh = false,
                progressPercentage = 0,
                daysUntilRefresh = 1,
                transactionsNeeded = 0,
                amountNeeded = 0.0,
                lastUpdateTime = System.currentTimeMillis(),
                nextRefreshReason = "Rate limited"
            ),
            rateLimitReached = true
        )
    }

    private fun createErrorBackoffResult(
        tracker: AICallTracker,
        thresholds: AICallThresholds
    ): ThresholdEvaluationResult {
        val hoursRemaining = ERROR_BACKOFF_HOURS -
            TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - tracker.lastErrorTimestamp)

        return ThresholdEvaluationResult(
            shouldCallAI = false,
            triggerReason = null,
            progressToNextCall = ThresholdProgress(
                isEligibleForRefresh = false,
                progressPercentage = 0,
                daysUntilRefresh = if (hoursRemaining > 24) 1 else 0,
                transactionsNeeded = 0,
                amountNeeded = 0.0,
                lastUpdateTime = System.currentTimeMillis(),
                nextRefreshReason = "Error backoff: ${hoursRemaining}h remaining"
            )
        )
    }

    private fun createCategoriesSnapshot(transactions: List<Transaction>): String {
        val categoryTotals = transactions.groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }

        return try {
            // Simple JSON representation
            categoryTotals.entries.joinToString(",", "{", "}") { (cat, amount) ->
                "\"$cat\":$amount"
            }
        } catch (e: Exception) {
            "{}"
        }
    }

    private fun estimateApiCost(): Double {
        // Rough estimate based on Claude pricing
        return 0.015 // ~$0.015 per request for Claude Sonnet
    }
}

/**
 * Data class for transaction summary used in threshold evaluation
 */
data class TransactionSummary(
    val transactionCount: Int,
    val totalSpent: Double,
    val categoryCount: Int,
    val averagePerTransaction: Double,
    val lastTransactionTime: Long = 0L
)