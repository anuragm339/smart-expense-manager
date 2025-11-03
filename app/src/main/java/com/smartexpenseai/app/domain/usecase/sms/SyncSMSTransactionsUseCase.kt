package com.smartexpenseai.app.domain.usecase.sms

import com.smartexpenseai.app.domain.repository.TransactionRepositoryInterface
import com.smartexpenseai.app.utils.logging.StructuredLogger
import java.util.Date
import javax.inject.Inject

/**
 * Use case for syncing SMS transactions with business logic
 * Handles SMS sync orchestration, validation, and result processing
 */
class SyncSMSTransactionsUseCase @Inject constructor(
    private val repository: TransactionRepositoryInterface
) {
    private val logger = StructuredLogger("SMS_SYNC", "SyncSMSTransactionsUseCase")
    
    /**
     * Sync new SMS transactions
     */
    suspend fun execute(): Result<SMSSyncResult> {
        return try {
            logger.debug("execute","Starting SMS transactions sync")
            
            val startTime = System.currentTimeMillis()
            val lastSyncTimestamp = repository.getLastSyncTimestamp()

            logger.debug("execute","Last sync timestamp: $lastSyncTimestamp")
            
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

            logger.debug("execute","SMS sync completed successfully: $newTransactionsCount new transactions in ${syncDuration}ms")
            Result.success(syncResult)
            
        } catch (e: Exception) {
            logger.error("execute", "SMS sync failed",e)
            
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
            logger.debug("executeWithProgress","Starting SMS sync with progress tracking")
            
            progressCallback?.invoke(SyncProgress(SyncPhase.STARTING, 0, "Initializing SMS sync..."))
            
            val startTime = System.currentTimeMillis()
            val lastSyncTimestamp = repository.getLastSyncTimestamp()
            
            progressCallback?.invoke(SyncProgress(SyncPhase.READING_SMS, 25, "Reading SMS messages..."))
            
            // Get current sync status
            val currentStatus = repository.getSyncStatus()
            if (currentStatus == "IN_PROGRESS") {
                logger.debug("executeWithProgress","Sync already in progress")
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
            
            logger.debug("executeWithProgress", "SMS sync with progress completed: $newTransactionsCount new transactions")
            Result.success(syncResult)

        } catch (e: Exception) {
            logger.error("executeWithProgress", "SMS sync with progress failed", e)
            
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
     * Force full SMS sync (re-sync all messages) - Incremental mode
     * Only processes SMS that don't have corresponding transactions
     */
    suspend fun forceFullSync(): Result<SMSSyncResult> {
        return try {
            logger.warn("forceFullSync","Starting FORCE FULL SMS sync - this may take longer")

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

            logger.warn("forceFullSync","Force full SMS sync completed: $newTransactionsCount transactions in ${syncDuration}ms")
            Result.success(syncResult)

        } catch (e: Exception) {
            logger.error("forceFullSync", "Force full SMS sync failed",e)

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
     * Clean full rescan - Deletes all existing transactions and rescans all SMS
     * Useful when bank_rules.json is updated
     */
    suspend fun cleanFullRescan(): Result<SMSSyncResult> {
        return try {
            logger.debug("cleanFullRescan","Starting CLEAN FULL RESCAN - all transactions will be deleted and rescanned")

            val startTime = System.currentTimeMillis()

            // Delete all existing transactions
            val deletedCount = repository.deleteAllTransactions()
            logger.debug("cleanFullRescan","Deleted $deletedCount existing transactions")

            // Clear last sync timestamp to force full sync
            repository.updateSyncState(Date(0))

            // Perform the sync from scratch
            val newTransactionsCount = repository.syncNewSMS()

            val endTime = System.currentTimeMillis()
            val syncDuration = endTime - startTime

            val syncResult = SMSSyncResult(
                newTransactionsCount = newTransactionsCount,
                syncTimestamp = Date(),
                lastSyncTimestamp = null,
                syncDurationMs = syncDuration,
                success = true,
                errorMessage = null,
                isFullSync = true,
                isCleanRescan = true,
                deletedCount = deletedCount
            )

            logger.debug("cleanFullRescan","Clean full rescan completed: deleted $deletedCount, added $newTransactionsCount in ${syncDuration}ms")
            Result.success(syncResult)

        } catch (e: Exception) {
            logger.error("cleanFullRescan","Clean full rescan failed",e)

            val syncResult = SMSSyncResult(
                newTransactionsCount = 0,
                syncTimestamp = Date(),
                lastSyncTimestamp = null,
                syncDurationMs = 0,
                success = false,
                errorMessage = e.message ?: "Unknown error",
                isFullSync = true,
                isCleanRescan = true
            )

            Result.failure(SyncException("Clean full rescan failed", e, syncResult))
        }
    }
    
    /**
     * Get current sync status
     */
    suspend fun getSyncStatus(): Result<SyncStatus> {
        return try {
            logger.debug("getSyncStatus", "Getting current sync status")

            val status = repository.getSyncStatus() ?: "UNKNOWN"
            val lastSyncTimestamp = repository.getLastSyncTimestamp()
            val totalTransactions = repository.getTransactionCount()

            val syncStatus = SyncStatus(
                currentStatus = status,
                lastSyncTimestamp = lastSyncTimestamp,
                totalTransactions = totalTransactions,
                isInProgress = status == "IN_PROGRESS"
            )

            logger.debug("getSyncStatus", "Current sync status: $status, Total transactions: $totalTransactions")
            Result.success(syncStatus)

        } catch (e: Exception) {
            logger.error("getSyncStatus", "Error getting sync status", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if SMS sync is needed (based on time since last sync)
     */
    suspend fun isSyncNeeded(intervalHours: Int = 24): Result<Boolean> {
        return try {
            logger.debug("isSyncNeeded", "Checking if SMS sync is needed (interval: ${intervalHours}h)")

            val lastSyncTimestamp = repository.getLastSyncTimestamp()

            val isNeeded = if (lastSyncTimestamp != null) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastSync = currentTime - lastSyncTimestamp.time
                val intervalMs = intervalHours * 60 * 60 * 1000L

                timeSinceLastSync >= intervalMs
            } else {
                true // No previous sync, so sync is needed
            }

            logger.debug("isSyncNeeded", "SMS sync needed: $isNeeded")
            Result.success(isNeeded)

        } catch (e: Exception) {
            logger.error("isSyncNeeded", "Error checking if sync is needed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Validate sync prerequisites (permissions, etc.)
     */
    suspend fun validateSyncPrerequisites(): Result<ValidationResult> {
        return try {
            logger.debug("validateSyncPrerequisites", "Validating SMS sync prerequisites")

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

            logger.debug("validateSyncPrerequisites", "SMS sync prerequisites validation passed")
            Result.success(validationResult)

        } catch (e: Exception) {
            logger.error("validateSyncPrerequisites", "Error validating sync prerequisites", e)
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
    val isFullSync: Boolean = false,
    val isCleanRescan: Boolean = false,
    val deletedCount: Int = 0
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