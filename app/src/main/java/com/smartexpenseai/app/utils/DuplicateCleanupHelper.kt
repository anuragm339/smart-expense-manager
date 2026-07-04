package com.smartexpenseai.app.utils

import android.content.Context
import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * DuplicateCleanupHelper - Advanced utility to detect and remove duplicate transactions
 *
 * This helper identifies duplicates using multiple strategies:
 * 1. SMS ID duplicates (same SMS processed multiple times)
 * 2. Reference number duplicates (same bank transaction reference)
 * 3. Transaction similarity (same merchant, amount, date, bank within time window)
 * 4. Exact duplicates (identical data)
 *
 * Created to clean up existing duplicates caused by the incremental scan bug.
 */
object DuplicateCleanupHelper {

    private val logger = StructuredLogger(
        featureTag = "CLEANUP",
        className = "DuplicateCleanupHelper"
    )

    /**
     * Result of duplicate cleanup operation
     */
    data class CleanupResult(
        val totalTransactions: Int,
        val duplicatesFound: Int,
        val duplicatesRemoved: Int,
        val strategy: String,
        val details: List<DuplicateGroup>
    )

    data class DuplicateGroup(
        val key: String,
        val count: Int,
        val merchant: String,
        val amount: Double,
        val kept: Long,       // Transaction ID that was kept
        val removed: List<Long> // Transaction IDs that were removed
    )

    /**
     * Perform comprehensive duplicate cleanup
     * Uses multiple strategies to find and remove all types of duplicates
     */
    suspend fun cleanupAllDuplicates(context: Context): CleanupResult = withContext(Dispatchers.IO) {
        logger.info(
            where = "cleanupAllDuplicates",
            what = "🧹 Starting comprehensive duplicate cleanup"
        )

        val repository = ExpenseRepository.getInstance(context)
        val allTransactions = repository.getAllTransactionsSync()
        val totalCount = allTransactions.size

        logger.info(
            where = "cleanupAllDuplicates",
            what = "Found $totalCount total transactions in database"
        )

        // Strategy 1: SMS ID duplicates (highest priority)
        val smsIdResult = cleanupBySmsId(allTransactions, repository)

        // Reload transactions after first cleanup
        val remainingAfterSmsId = repository.getAllTransactionsSync()

        // Strategy 2: Reference number duplicates
        val refNumberResult = cleanupByReferenceNumber(remainingAfterSmsId, repository)

        // Reload transactions after second cleanup
        val remainingAfterRefNumber = repository.getAllTransactionsSync()

        // Strategy 3: Transaction similarity (merchant + amount + time + bank)
        val similarityResult = cleanupBySimilarity(remainingAfterRefNumber, repository)

        val totalRemoved = smsIdResult.duplicatesRemoved +
                          refNumberResult.duplicatesRemoved +
                          similarityResult.duplicatesRemoved

        val totalFound = smsIdResult.duplicatesFound +
                        refNumberResult.duplicatesFound +
                        similarityResult.duplicatesFound

        val finalCount = repository.getAllTransactionsSync().size

        logger.info(
            where = "cleanupAllDuplicates",
            what = "✅ Cleanup complete! Before: $totalCount | After: $finalCount | Removed: $totalRemoved"
        )

        CleanupResult(
            totalTransactions = totalCount,
            duplicatesFound = totalFound,
            duplicatesRemoved = totalRemoved,
            strategy = "Multi-Strategy (SMS ID + Reference Number + Similarity)",
            details = smsIdResult.details + refNumberResult.details + similarityResult.details
        )
    }

    /**
     * Strategy 1: Clean up duplicates with same SMS ID
     * This catches transactions from the same SMS processed multiple times
     */
    private suspend fun cleanupBySmsId(
        transactions: List<TransactionEntity>,
        repository: ExpenseRepository
    ): CleanupResult {
        logger.debug(
            where = "cleanupBySmsId",
            what = "[Strategy 1] Checking for SMS ID duplicates..."
        )

        val smsIdGroups = transactions
            .groupBy { it.smsId }
            .filter { it.value.size > 1 }

        if (smsIdGroups.isEmpty()) {
            logger.debug(
                where = "cleanupBySmsId",
                what = "[Strategy 1] No SMS ID duplicates found"
            )
            return CleanupResult(
                totalTransactions = transactions.size,
                duplicatesFound = 0,
                duplicatesRemoved = 0,
                strategy = "SMS ID",
                details = emptyList()
            )
        }

        val duplicateGroups = mutableListOf<DuplicateGroup>()
        var removedCount = 0

        for ((smsId, duplicates) in smsIdGroups) {
            logger.warn(
                where = "cleanupBySmsId",
                what = "[Strategy 1] Found ${duplicates.size} duplicates with SMS ID: $smsId"
            )

            // Keep the one with highest confidence score, or earliest created_at if same confidence
            val toKeep = duplicates.maxWithOrNull { a, b ->
                when {
                    a.confidenceScore != b.confidenceScore ->
                        a.confidenceScore.compareTo(b.confidenceScore)
                    else ->
                        b.createdAt.compareTo(a.createdAt) // Keep earliest
                }
            }

            val toRemove = duplicates.filter { it.id != toKeep?.id }

            for (duplicate in toRemove) {
                repository.deleteTransaction(duplicate)
                removedCount++
                logger.debug(
                    where = "cleanupBySmsId",
                    what = "[Strategy 1] Removed duplicate: ${duplicate.rawMerchant} - ₹${duplicate.amount} (ID: ${duplicate.id})"
                )
            }

            duplicateGroups.add(
                DuplicateGroup(
                    key = "SMS_ID:$smsId",
                    count = duplicates.size,
                    merchant = toKeep?.rawMerchant ?: "Unknown",
                    amount = toKeep?.amount ?: 0.0,
                    kept = toKeep?.id ?: 0,
                    removed = toRemove.map { it.id }
                )
            )
        }

        logger.info(
            where = "cleanupBySmsId",
            what = "[Strategy 1] ✅ Removed $removedCount SMS ID duplicates"
        )

        return CleanupResult(
            totalTransactions = transactions.size,
            duplicatesFound = smsIdGroups.values.sumOf { it.size },
            duplicatesRemoved = removedCount,
            strategy = "SMS ID",
            details = duplicateGroups
        )
    }

    /**
     * Strategy 2: Clean up duplicates with same reference number
     * Bank transactions have unique reference numbers
     */
    private suspend fun cleanupByReferenceNumber(
        transactions: List<TransactionEntity>,
        repository: ExpenseRepository
    ): CleanupResult {
        logger.debug(
            where = "cleanupByReferenceNumber",
            what = "[Strategy 2] Checking for reference number duplicates..."
        )

        // Only check transactions that have reference numbers
        val withRefNumbers = transactions.filter { !it.referenceNumber.isNullOrBlank() }

        val refNumberGroups = withRefNumbers
            .groupBy { "${it.referenceNumber}_${it.bankName}" }
            .filter { it.value.size > 1 }

        if (refNumberGroups.isEmpty()) {
            logger.debug(
                where = "cleanupByReferenceNumber",
                what = "[Strategy 2] No reference number duplicates found"
            )
            return CleanupResult(
                totalTransactions = transactions.size,
                duplicatesFound = 0,
                duplicatesRemoved = 0,
                strategy = "Reference Number",
                details = emptyList()
            )
        }

        val duplicateGroups = mutableListOf<DuplicateGroup>()
        var removedCount = 0

        for ((key, duplicates) in refNumberGroups) {
            logger.warn(
                where = "cleanupByReferenceNumber",
                what = "[Strategy 2] Found ${duplicates.size} duplicates with ref: $key"
            )

            // Keep the one with highest confidence score
            val toKeep = duplicates.maxByOrNull { it.confidenceScore }
            val toRemove = duplicates.filter { it.id != toKeep?.id }

            for (duplicate in toRemove) {
                repository.deleteTransaction(duplicate)
                removedCount++
                logger.debug(
                    where = "cleanupByReferenceNumber",
                    what = "[Strategy 2] Removed duplicate: ${duplicate.rawMerchant} - Ref: ${duplicate.referenceNumber}"
                )
            }

            duplicateGroups.add(
                DuplicateGroup(
                    key = "REF:$key",
                    count = duplicates.size,
                    merchant = toKeep?.rawMerchant ?: "Unknown",
                    amount = toKeep?.amount ?: 0.0,
                    kept = toKeep?.id ?: 0,
                    removed = toRemove.map { it.id }
                )
            )
        }

        logger.info(
            where = "cleanupByReferenceNumber",
            what = "[Strategy 2] ✅ Removed $removedCount reference number duplicates"
        )

        return CleanupResult(
            totalTransactions = transactions.size,
            duplicatesFound = refNumberGroups.values.sumOf { it.size },
            duplicatesRemoved = removedCount,
            strategy = "Reference Number",
            details = duplicateGroups
        )
    }

    /**
     * Strategy 3: Clean up similar transactions
     * Same merchant, amount, bank within 10-minute time window
     */
    private suspend fun cleanupBySimilarity(
        transactions: List<TransactionEntity>,
        repository: ExpenseRepository
    ): CleanupResult {
        logger.debug(
            where = "cleanupBySimilarity",
            what = "[Strategy 3] Checking for similar transactions..."
        )

        val similarityGroups = transactions.groupBy { transaction ->
            TransactionEntity.generateDeduplicationKey(
                merchant = transaction.normalizedMerchant,
                amount = transaction.amount,
                date = transaction.transactionDate,
                bankName = transaction.bankName,
                referenceNumber = null, // Don't use ref number here (already handled in strategy 2)
                windowMinutes = 10
            )
        }.filter { it.value.size > 1 }

        if (similarityGroups.isEmpty()) {
            logger.debug(
                where = "cleanupBySimilarity",
                what = "[Strategy 3] No similar transaction duplicates found"
            )
            return CleanupResult(
                totalTransactions = transactions.size,
                duplicatesFound = 0,
                duplicatesRemoved = 0,
                strategy = "Similarity",
                details = emptyList()
            )
        }

        val duplicateGroups = mutableListOf<DuplicateGroup>()
        var removedCount = 0

        for ((key, duplicates) in similarityGroups) {
            // Additional check: Only treat as duplicates if amounts are EXACTLY the same
            // and within 10 minutes
            val exactDuplicates = duplicates.groupBy { it.amount }.filter { it.value.size > 1 }

            for ((amount, amountDuplicates) in exactDuplicates) {
                // Further filter by 10-minute window
                val timeSortedDuplicates = amountDuplicates.sortedBy { it.transactionDate.time }
                val duplicateClusters = mutableListOf<List<TransactionEntity>>()
                var currentCluster = mutableListOf(timeSortedDuplicates.first())

                for (i in 1 until timeSortedDuplicates.size) {
                    val current = timeSortedDuplicates[i]
                    val previous = currentCluster.last()
                    val timeDiff = current.transactionDate.time - previous.transactionDate.time

                    if (timeDiff <= TimeUnit.MINUTES.toMillis(10)) {
                        currentCluster.add(current)
                    } else {
                        if (currentCluster.size > 1) {
                            duplicateClusters.add(currentCluster)
                        }
                        currentCluster = mutableListOf(current)
                    }
                }

                if (currentCluster.size > 1) {
                    duplicateClusters.add(currentCluster)
                }

                // Process each cluster
                for (cluster in duplicateClusters) {
                    logger.warn(
                        where = "cleanupBySimilarity",
                        what = "[Strategy 3] Found ${cluster.size} similar duplicates: ${cluster.first().rawMerchant} - ₹$amount"
                    )

                    // Keep the one with highest confidence score
                    val toKeep = cluster.maxByOrNull { it.confidenceScore }
                    val toRemove = cluster.filter { it.id != toKeep?.id }

                    for (duplicate in toRemove) {
                        repository.deleteTransaction(duplicate)
                        removedCount++
                        logger.debug(
                            where = "cleanupBySimilarity",
                            what = "[Strategy 3] Removed similar duplicate: ${duplicate.rawMerchant} - ₹${duplicate.amount}"
                        )
                    }

                    duplicateGroups.add(
                        DuplicateGroup(
                            key = "SIMILAR:$key",
                            count = cluster.size,
                            merchant = toKeep?.rawMerchant ?: "Unknown",
                            amount = toKeep?.amount ?: 0.0,
                            kept = toKeep?.id ?: 0,
                            removed = toRemove.map { it.id }
                        )
                    )
                }
            }
        }

        logger.info(
            where = "cleanupBySimilarity",
            what = "[Strategy 3] ✅ Removed $removedCount similar duplicates"
        )

        return CleanupResult(
            totalTransactions = transactions.size,
            duplicatesFound = duplicateGroups.sumOf { it.count },
            duplicatesRemoved = removedCount,
            strategy = "Similarity",
            details = duplicateGroups
        )
    }

    /**
     * Preview duplicates without removing them
     * Useful for showing user what will be cleaned up
     */
    suspend fun previewDuplicates(context: Context): CleanupResult = withContext(Dispatchers.IO) {
        logger.info(
            where = "previewDuplicates",
            what = "🔍 Previewing duplicates (no removal)"
        )

        val repository = ExpenseRepository.getInstance(context)
        val allTransactions = repository.getAllTransactionsSync()

        // Find duplicates without removing
        val smsIdDuplicates = findSmsIdDuplicates(allTransactions)
        val refNumberDuplicates = findReferenceNumberDuplicates(allTransactions)
        val similarityDuplicates = findSimilarityDuplicates(allTransactions)

        val totalDuplicates = smsIdDuplicates.size + refNumberDuplicates.size + similarityDuplicates.size
        val allDetails = smsIdDuplicates + refNumberDuplicates + similarityDuplicates

        logger.info(
            where = "previewDuplicates",
            what = "Preview complete: Found $totalDuplicates duplicate transactions"
        )

        CleanupResult(
            totalTransactions = allTransactions.size,
            duplicatesFound = totalDuplicates,
            duplicatesRemoved = 0, // Preview only
            strategy = "Preview (Multi-Strategy)",
            details = allDetails
        )
    }

    private fun findSmsIdDuplicates(transactions: List<TransactionEntity>): List<DuplicateGroup> {
        return transactions
            .groupBy { it.smsId }
            .filter { it.value.size > 1 }
            .map { (smsId, duplicates) ->
                val kept = duplicates.maxByOrNull { it.confidenceScore }
                DuplicateGroup(
                    key = "SMS_ID:$smsId",
                    count = duplicates.size,
                    merchant = kept?.rawMerchant ?: "Unknown",
                    amount = kept?.amount ?: 0.0,
                    kept = kept?.id ?: 0,
                    removed = duplicates.filter { it.id != kept?.id }.map { it.id }
                )
            }
    }

    private fun findReferenceNumberDuplicates(transactions: List<TransactionEntity>): List<DuplicateGroup> {
        return transactions
            .filter { !it.referenceNumber.isNullOrBlank() }
            .groupBy { "${it.referenceNumber}_${it.bankName}" }
            .filter { it.value.size > 1 }
            .map { (key, duplicates) ->
                val kept = duplicates.maxByOrNull { it.confidenceScore }
                DuplicateGroup(
                    key = "REF:$key",
                    count = duplicates.size,
                    merchant = kept?.rawMerchant ?: "Unknown",
                    amount = kept?.amount ?: 0.0,
                    kept = kept?.id ?: 0,
                    removed = duplicates.filter { it.id != kept?.id }.map { it.id }
                )
            }
    }

    private fun findSimilarityDuplicates(transactions: List<TransactionEntity>): List<DuplicateGroup> {
        return transactions
            .groupBy { transaction ->
                TransactionEntity.generateDeduplicationKey(
                    merchant = transaction.normalizedMerchant,
                    amount = transaction.amount,
                    date = transaction.transactionDate,
                    bankName = transaction.bankName,
                    windowMinutes = 10
                )
            }
            .filter { it.value.size > 1 }
            .map { (key, duplicates) ->
                val kept = duplicates.maxByOrNull { it.confidenceScore }
                DuplicateGroup(
                    key = "SIMILAR:$key",
                    count = duplicates.size,
                    merchant = kept?.rawMerchant ?: "Unknown",
                    amount = kept?.amount ?: 0.0,
                    kept = kept?.id ?: 0,
                    removed = duplicates.filter { it.id != kept?.id }.map { it.id }
                )
            }
    }
}
