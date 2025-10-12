package com.expensemanager.app.data.repository.internal

import com.expensemanager.app.data.dao.TransactionDao
import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.utils.logging.StructuredLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DatabaseMaintenanceOperations(
    private val transactionDao: TransactionDao
) {

    private val logger = StructuredLogger(
        featureTag = LogConfig.FeatureTags.DATABASE,
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

    suspend fun removeObviousTestData(): Int = withContext(Dispatchers.IO) {
        try {
            logger.debug(
                where = "removeObviousTestData",
                what = "Starting test data cleanup"
            )

            val testMerchants = listOf(
                "test", "example", "demo", "sample", "dummy",
                "PRAGATHI HARDWARE AND ELECTRICALS",
                "AMAZON PAY", "SWIGGY", "ZOMATO"
            )

            var removedCount = 0

            for (merchant in testMerchants) {
                val transactions = transactionDao.getTransactionsByMerchantAndAmount(
                    merchant.lowercase(),
                    10000.0
                )

                for (transaction in transactions) {
                    transactionDao.deleteTransaction(transaction)
                    removedCount++
                    logger.debug(
                        where = "removeObviousTestData",
                        what = "Removing test transaction: ${transaction.rawMerchant} - ${"₹%.2f".format(transaction.amount)}"
                    )
                }
            }

            logger.debug(
                where = "removeObviousTestData",
                what = "Test data cleanup completed - Removed $removedCount transactions"
            )
            removedCount
        } catch (e: Exception) {
            logger.error(
                where = "removeObviousTestData",
                what = "[ERROR] Test data cleanup failed",
                throwable = e
            )
            0
        }
    }
}
