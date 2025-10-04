package com.expensemanager.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.expensemanager.app.data.models.AICallTracker
import kotlinx.coroutines.flow.Flow

/**
 * DAO for AI call tracking and threshold management
 */
@Dao
interface AICallDao {

    /**
     * Get current AI call tracker (singleton record)
     */
    @Query("SELECT * FROM ai_call_tracking WHERE id = 'current' LIMIT 1")
    suspend fun getCurrentTracker(): AICallTracker?

    /**
     * Get current tracker as Flow for reactive updates
     */
    @Query("SELECT * FROM ai_call_tracking WHERE id = 'current' LIMIT 1")
    fun getCurrentTrackerFlow(): Flow<AICallTracker?>

    /**
     * Insert or update the tracker
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracker(tracker: AICallTracker)

    /**
     * Update existing tracker
     */
    @Update
    suspend fun updateTracker(tracker: AICallTracker)

    /**
     * Update last call information after successful AI call
     */
    @Query("""
        UPDATE ai_call_tracking
        SET lastCallTimestamp = :timestamp,
            transactionCountAtLastCall = :transactionCount,
            totalAmountAtLastCall = :totalAmount,
            categoriesSnapshot = :categoriesJson,
            nextEligibleCallTime = :nextEligibleTime,
            totalApiCalls = totalApiCalls + 1,
            consecutiveErrors = 0
        WHERE id = 'current'
    """)
    suspend fun updateLastCallInfo(
        timestamp: Long,
        transactionCount: Int,
        totalAmount: Double,
        categoriesJson: String,
        nextEligibleTime: Long
    )

    /**
     * Update call frequency preference
     */
    @Query("UPDATE ai_call_tracking SET callFrequency = :frequency WHERE id = 'current'")
    suspend fun updateCallFrequency(frequency: String)

    /**
     * Record error occurrence
     */
    @Query("""
        UPDATE ai_call_tracking
        SET lastErrorTimestamp = :timestamp,
            consecutiveErrors = consecutiveErrors + 1
        WHERE id = 'current'
    """)
    suspend fun recordError(timestamp: Long)

    /**
     * Reset error count after successful call
     */
    @Query("UPDATE ai_call_tracking SET consecutiveErrors = 0 WHERE id = 'current'")
    suspend fun resetErrorCount()

    /**
     * Get total API calls made
     */
    @Query("SELECT totalApiCalls FROM ai_call_tracking WHERE id = 'current'")
    suspend fun getTotalApiCalls(): Int?

    /**
     * Initialize tracker if it doesn't exist
     */
    suspend fun initializeTrackerIfNeeded() {
        val existing = getCurrentTracker()
        if (existing == null) {
            insertTracker(
                AICallTracker(
                    id = "current",
                    lastCallTimestamp = 0L,
                    transactionCountAtLastCall = 0,
                    totalAmountAtLastCall = 0.0,
                    categoriesSnapshot = "{}",
                    nextEligibleCallTime = 0L,
                    callFrequency = "BALANCED",
                    totalApiCalls = 0,
                    lastErrorTimestamp = 0L,
                    consecutiveErrors = 0
                )
            )
        }
    }

    /**
     * Increment daily and monthly call counts
     */
    @Query("""
        UPDATE ai_call_tracking
        SET dailyCallCount = dailyCallCount + 1,
            monthlyCallCount = monthlyCallCount + 1
        WHERE id = 'current'
    """)
    suspend fun incrementCallCounts()

    /**
     * Reset daily call count
     */
    @Query("""
        UPDATE ai_call_tracking
        SET dailyCallCount = 0,
            lastDailyResetTimestamp = :resetTimestamp
        WHERE id = 'current'
    """)
    suspend fun resetDailyCallCount(resetTimestamp: Long)

    /**
     * Reset monthly call count
     */
    @Query("""
        UPDATE ai_call_tracking
        SET monthlyCallCount = 0,
            lastMonthlyResetTimestamp = :resetTimestamp
        WHERE id = 'current'
    """)
    suspend fun resetMonthlyCallCount(resetTimestamp: Long)

    /**
     * Get current daily and monthly call counts
     */
    @Query("SELECT dailyCallCount, monthlyCallCount FROM ai_call_tracking WHERE id = 'current'")
    suspend fun getCallCounts(): CallCounts?

    /**
     * Clear all tracking data (for reset functionality)
     */
    @Query("DELETE FROM ai_call_tracking")
    suspend fun clearAllTracking()
}

/**
 * Data class for call counts query result
 */
data class CallCounts(
    val dailyCallCount: Int,
    val monthlyCallCount: Int
)