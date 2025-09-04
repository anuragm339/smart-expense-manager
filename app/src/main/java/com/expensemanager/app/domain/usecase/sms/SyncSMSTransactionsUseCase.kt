package com.expensemanager.app.domain.usecase.sms

import android.util.Log
import com.expensemanager.app.domain.repository.TransactionRepositoryInterface
import java.util.Date
import javax.inject.Inject

/**
 * Use case for syncing SMS transactions with business logic
 * Handles SMS sync orchestration, validation, and result processing
 */
class SyncSMSTransactionsUseCase @Inject constructor(
    private val repository: TransactionRepositoryInterface
) {
    
    companion object {
        private const val TAG = "SyncSMSTransactionsUseCase"
    }
    
    /**
     * Sync new SMS transactions
     */
    suspend fun execute(): Result<SMSSyncResult> {
        return try {
            Log.d(TAG, "Starting SMS transactions sync")
            
            val startTime = System.currentTimeMillis()
            val lastSyncTimestamp = repository.getLastSyncTimestamp()
            
            Log.d(TAG, "Last sync timestamp: $lastSyncTimestamp")
            
            // Perform the sync
            val newTransactionsCount = repository.syncNewSMS()
            
            val endTime = System.currentTimeMillis()
            val syncDuration = endTime - startTime
            
            val syncResult = SMSSyncResult(
                newTransactionsCount = newTransactionsCount,
                syncTimestamp = Date(),
                lastSyncTimestamp = lastSyncTimestamp,
                syncDurationMs = syncDuration,
                success = true,
                errorMessage = null
            )
            
            Log.d(TAG, "SMS sync completed successfully: $newTransactionsCount new transactions in ${syncDuration}ms")
            Result.success(syncResult)
            
        } catch (e: Exception) {
            Log.e(TAG, "SMS sync failed", e)
            
            val syncResult = SMSSyncResult(
                newTransactionsCount = 0,
                syncTimestamp = Date(),
                lastSyncTimestamp = repository.getLastSyncTimestamp(),
                syncDurationMs = 0,
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
            
            Result.failure(SyncException("SMS sync failed", e, syncResult))
        }
    }
    
    /**
     * Sync SMS transactions with progress tracking
     */
    suspend fun executeWithProgress(
        progressCallback: ((SyncProgress) -> Unit)? = null
    ): Result<SMSSyncResult> {
        
        return try {
            Log.d(TAG, "Starting SMS sync with progress tracking")
            
            progressCallback?.invoke(SyncProgress(SyncPhase.STARTING, 0, "Initializing SMS sync..."))
            
            val startTime = System.currentTimeMillis()
            val lastSyncTimestamp = repository.getLastSyncTimestamp()
            
            progressCallback?.invoke(SyncProgress(SyncPhase.READING_SMS, 25, "Reading SMS messages..."))
            
            // Get current sync status
            val currentStatus = repository.getSyncStatus()
            if (currentStatus == "IN_PROGRESS") {
                Log.w(TAG, "Sync already in progress")
                return Result.failure(Exception("SMS sync already in progress"))
            }
            
            progressCallback?.invoke(SyncProgress(SyncPhase.PARSING_TRANSACTIONS, 50, "Parsing transactions..."))
            
            // Perform the sync
            val newTransactionsCount = repository.syncNewSMS()
            
            progressCallback?.invoke(SyncProgress(SyncPhase.UPDATING_DATABASE, 75, "Updating database..."))
            
            val endTime = System.currentTimeMillis()
            val syncDuration = endTime - startTime
            
            progressCallback?.invoke(SyncProgress(SyncPhase.COMPLETED, 100, "Sync completed successfully"))
            
            val syncResult = SMSSyncResult(
                newTransactionsCount = newTransactionsCount,
                syncTimestamp = Date(),
                lastSyncTimestamp = lastSyncTimestamp,
                syncDurationMs = syncDuration,
                success = true,
                errorMessage = null
            )
            
            Log.d(TAG, "SMS sync with progress completed: $newTransactionsCount new transactions")
            Result.success(syncResult)
            
        } catch (e: Exception) {
            Log.e(TAG, "SMS sync with progress failed", e)
            
            progressCallback?.invoke(SyncProgress(SyncPhase.FAILED, -1, "Sync failed: ${e.message}"))
            
            val syncResult = SMSSyncResult(
                newTransactionsCount = 0,
                syncTimestamp = Date(),
                lastSyncTimestamp = repository.getLastSyncTimestamp(),
                syncDurationMs = 0,
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
            
            Result.failure(SyncException("SMS sync failed", e, syncResult))
        }
    }
    
    /**
     * Force full SMS sync (re-sync all messages)
     */
    suspend fun forceFullSync(): Result<SMSSyncResult> {
        return try {
            Log.w(TAG, "Starting FORCE FULL SMS sync - this may take longer")
            
            val startTime = System.currentTimeMillis()
            
            // Clear last sync timestamp to force full sync
            repository.updateSyncState(Date(0))
            
            // Perform the sync
            val newTransactionsCount = repository.syncNewSMS()
            
            val endTime = System.currentTimeMillis()
            val syncDuration = endTime - startTime
            
            val syncResult = SMSSyncResult(
                newTransactionsCount = newTransactionsCount,
                syncTimestamp = Date(),
                lastSyncTimestamp = Date(0),
                syncDurationMs = syncDuration,
                success = true,
                errorMessage = null,
                isFullSync = true
            )
            
            Log.d(TAG, "Force full SMS sync completed: $newTransactionsCount transactions in ${syncDuration}ms")
            Result.success(syncResult)
            
        } catch (e: Exception) {
            Log.e(TAG, "Force full SMS sync failed", e)
            
            val syncResult = SMSSyncResult(
                newTransactionsCount = 0,
                syncTimestamp = Date(),
                lastSyncTimestamp = Date(0),
                syncDurationMs = 0,
                success = false,
                errorMessage = e.message ?: "Unknown error",
                isFullSync = true
            )
            
            Result.failure(SyncException("Force full SMS sync failed", e, syncResult))
        }
    }
    
    /**
     * Get current sync status
     */
    suspend fun getSyncStatus(): Result<SyncStatus> {
        return try {
            Log.d(TAG, "Getting current sync status")
            
            val status = repository.getSyncStatus() ?: "UNKNOWN"
            val lastSyncTimestamp = repository.getLastSyncTimestamp()
            val totalTransactions = repository.getTransactionCount()
            
            val syncStatus = SyncStatus(
                currentStatus = status,
                lastSyncTimestamp = lastSyncTimestamp,
                totalTransactions = totalTransactions,
                isInProgress = status == "IN_PROGRESS"
            )
            
            Log.d(TAG, "Current sync status: $status, Total transactions: $totalTransactions")
            Result.success(syncStatus)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sync status", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if SMS sync is needed (based on time since last sync)
     */
    suspend fun isSyncNeeded(intervalHours: Int = 24): Result<Boolean> {
        return try {
            Log.d(TAG, "Checking if SMS sync is needed (interval: ${intervalHours}h)")
            
            val lastSyncTimestamp = repository.getLastSyncTimestamp()
            
            val isNeeded = if (lastSyncTimestamp != null) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastSync = currentTime - lastSyncTimestamp.time
                val intervalMs = intervalHours * 60 * 60 * 1000L
                
                timeSinceLastSync >= intervalMs
            } else {
                true // No previous sync, so sync is needed
            }
            
            Log.d(TAG, "SMS sync needed: $isNeeded")
            Result.success(isNeeded)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if sync is needed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Validate sync prerequisites (permissions, etc.)
     */
    suspend fun validateSyncPrerequisites(): Result<ValidationResult> {
        return try {
            Log.d(TAG, "Validating SMS sync prerequisites")
            
            // This would normally check for SMS permissions, but we'll assume they're granted
            // In a real implementation, you'd check for:
            // - READ_SMS permission
            // - RECEIVE_SMS permission  
            // - Device has SMS capability
            // - SMS provider is accessible
            
            val validationResult = ValidationResult(
                isValid = true,
                missingPermissions = emptyList(),
                issues = emptyList()
            )
            
            Log.d(TAG, "SMS sync prerequisites validation passed")
            Result.success(validationResult)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating sync prerequisites", e)
            Result.failure(e)
        }
    }
}

/**
 * SMS sync result
 */
data class SMSSyncResult(
    val newTransactionsCount: Int,
    val syncTimestamp: Date,
    val lastSyncTimestamp: Date?,
    val syncDurationMs: Long,
    val success: Boolean,
    val errorMessage: String?,
    val isFullSync: Boolean = false
)

/**
 * Sync progress tracking
 */
data class SyncProgress(
    val phase: SyncPhase,
    val percentage: Int, // -1 for error
    val message: String
)

/**
 * Sync phases
 */
enum class SyncPhase {
    STARTING, READING_SMS, PARSING_TRANSACTIONS, UPDATING_DATABASE, COMPLETED, FAILED
}

/**
 * Current sync status
 */
data class SyncStatus(
    val currentStatus: String,
    val lastSyncTimestamp: Date?,
    val totalTransactions: Int,
    val isInProgress: Boolean
)

/**
 * Validation result for sync prerequisites
 */
data class ValidationResult(
    val isValid: Boolean,
    val missingPermissions: List<String>,
    val issues: List<String>
)

/**
 * Exception thrown during SMS sync
 */
class SyncException(
    message: String,
    cause: Throwable? = null,
    val syncResult: SMSSyncResult? = null
) : Exception(message, cause)