package com.expensemanager.app.domain.usecase.transaction

import timber.log.Timber
import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.domain.repository.TransactionRepositoryInterface
import javax.inject.Inject

/**
 * Use case for deleting transactions with business logic
 * Handles validation and safe deletion of transactions
 */
class DeleteTransactionUseCase @Inject constructor(
    private val repository: TransactionRepositoryInterface
) {
    
    companion object {
        private const val TAG = "DeleteTransactionUseCase"
    }
    
    /**
     * Delete a transaction by entity
     */
    suspend fun execute(transaction: TransactionEntity): Result<Unit> {
        return try {
            Timber.tag(TAG).d("Deleting transaction: ${transaction.rawMerchant} - ₹${transaction.amount}")
            
            repository.deleteTransaction(transaction)
            Timber.tag(TAG).d("Transaction deleted successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error deleting transaction")
            Result.failure(e)
        }
    }
    
    /**
     * Delete a transaction by ID
     */
    suspend fun executeById(transactionId: Long): Result<Unit> {
        return try {
            Timber.tag(TAG).d("Deleting transaction by ID: $transactionId")
            
            if (transactionId <= 0) {
                return Result.failure(IllegalArgumentException("Invalid transaction ID"))
            }
            
            repository.deleteTransactionById(transactionId)
            Timber.tag(TAG).d("Transaction deleted successfully by ID")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error deleting transaction by ID")
            Result.failure(e)
        }
    }
    
    /**
     * Delete multiple transactions (bulk delete)
     */
    suspend fun executeMultiple(transactions: List<TransactionEntity>): Result<Int> {
        return try {
            Timber.tag(TAG).d("Deleting ${transactions.size} transactions")
            
            var successCount = 0
            var errorCount = 0
            
            for (transaction in transactions) {
                val result = execute(transaction)
                if (result.isSuccess) {
                    successCount++
                } else {
                    errorCount++
                }
            }
            
            Timber.tag(TAG).d("Bulk delete completed: $successCount deleted, $errorCount errors")
            Result.success(successCount)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in bulk transaction delete")
            Result.failure(e)
        }
    }
    
    /**
     * Delete transactions by date range (dangerous operation)
     */
    suspend fun deleteByDateRange(startDate: java.util.Date, endDate: java.util.Date): Result<Int> {
        return try {
            Timber.tag(TAG).w("Deleting transactions from $startDate to $endDate - this is a destructive operation")
            
            // Get transactions in date range
            val transactionsToDelete = repository.getTransactionsByDateRange(startDate, endDate)
            
            if (transactionsToDelete.isEmpty()) {
                Timber.tag(TAG).d("No transactions found in date range")
                return Result.success(0)
            }
            
            Timber.tag(TAG).w("About to delete ${transactionsToDelete.size} transactions")
            
            // Delete each transaction
            val result = executeMultiple(transactionsToDelete)
            
            Timber.tag(TAG).d("Date range deletion completed")
            result
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error deleting transactions by date range")
            Result.failure(e)
        }
    }
    
    /**
     * Delete transactions by merchant (dangerous operation)  
     */
    suspend fun deleteByMerchant(merchantName: String): Result<Int> {
        return try {
            Timber.tag(TAG).w("Deleting transactions for merchant: $merchantName - this is a destructive operation")
            
            // Get transactions for merchant
            val transactionsToDelete = repository.getTransactionsByMerchant(merchantName)
            
            if (transactionsToDelete.isEmpty()) {
                Timber.tag(TAG).d("No transactions found for merchant")
                return Result.success(0)
            }
            
            Timber.tag(TAG).w("About to delete ${transactionsToDelete.size} transactions for merchant")
            
            // Delete each transaction
            val result = executeMultiple(transactionsToDelete)
            
            Timber.tag(TAG).d("Merchant deletion completed")
            result
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error deleting transactions by merchant")
            Result.failure(e)
        }
    }
    
    /**
     * Safe delete with confirmation (for UI implementations)
     */
    suspend fun safeDelete(transaction: TransactionEntity, confirmation: Boolean): Result<Unit> {
        return if (confirmation) {
            execute(transaction)
        } else {
            Timber.tag(TAG).w("Delete operation cancelled - no confirmation provided")
            Result.failure(Exception("Delete operation requires confirmation"))
        }
    }
}