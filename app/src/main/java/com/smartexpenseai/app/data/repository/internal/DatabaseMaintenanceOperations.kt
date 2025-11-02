package com.smartexpenseai.app.data.repository.internal

import com.smartexpenseai.app.data.dao.TransactionDao
import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DatabaseMaintenanceOperations(
    private val transactionDao: TransactionDao
) {

    private val logger = StructuredLogger(
        featureTag = "DATABASE",
        className = "DatabaseMaintenanceOperations"
    )

    suspend fun cleanupDuplicateTransactions(): Int = withContext(Dispatchers.IO) {
        try {
            logger.debug(
                where = "cleanupDuplicateTransactions",
                what = "Starting duplicate cleanup"
            )

            val allTransactions = transactionDao.getAllTransactionsSync()
            val duplicateGroups = allTransactions.groupBy { transaction ->
                TransactionEntity.generateDeduplicationKey(
                    merchant = transaction.normalizedMerchant,
                    amount = transaction.amount,
                    date = transaction.transactionDate,
                    bankName = transaction.bankName
                )
            }.filter { it.value.size > 1 }

            var removedCount = 0

            for ((key, duplicates) in duplicateGroups) {
                logger.debug(
                    where = "cleanupDuplicateTransactions",
                    what = "Found duplicate group with key: $key (${duplicates.size} transactions)"
                )

                val toKeep = duplicates.maxWithOrNull { a, b ->
                    when {
                        a.confidenceScore != b.confidenceScore -> a.confidenceScore.compareTo(b.confidenceScore)
                        else -> a.createdAt.compareTo(b.createdAt)
                    }
                }
                val toRemove = duplicates.filter { it.id != toKeep?.id }

                for (duplicate in toRemove) {
                    transactionDao.deleteTransaction(duplicate)
                    removedCount++
                    logger.debug(
                        where = "cleanupDuplicateTransactions",
                        what = "Removing duplicate transaction: ${duplicate.rawMerchant}"
                    )
                }
            }

            logger.debug(
                where = "cleanupDuplicateTransactions",
                what = "Duplicate cleanup completed - Removed $removedCount transactions"
            )
            removedCount
        } catch (e: Exception) {
            logger.error(
                where = "cleanupDuplicateTransactions",
                what = "[ERROR] Database cleanup failed",
                throwable = e
            )
            0
        }
    }

    // removeObviousTestData() has been removed - not needed for production with real data
}
