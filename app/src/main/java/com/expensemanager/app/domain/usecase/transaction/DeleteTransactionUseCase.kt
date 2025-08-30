package com.expensemanager.app.domain.usecase.transaction

import android.util.Log
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
            Log.d(TAG, "Deleting transaction: ${transaction.rawMerchant} - ₹${transaction.amount}")
            
            repository.deleteTransaction(transaction)
            Log.d(TAG, "Transaction deleted successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transaction", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete a transaction by ID
     */
    suspend fun executeById(transactionId: Long): Result<Unit> {
        return try {
            Log.d(TAG, "Deleting transaction by ID: $transactionId")
            
            if (transactionId <= 0) {
                return Result.failure(IllegalArgumentException("Invalid transaction ID"))
            }
            
            repository.deleteTransactionById(transactionId)
            Log.d(TAG, "Transaction deleted successfully by ID")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transaction by ID", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete multiple transactions (bulk delete)
     */
    suspend fun executeMultiple(transactions: List<TransactionEntity>): Result<Int> {
        return try {
            Log.d(TAG, "Deleting ${transactions.size} transactions")
            
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
            
            Log.d(TAG, "Bulk delete completed: $successCount deleted, $errorCount errors")
            Result.success(successCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in bulk transaction delete", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete transactions by date range (dangerous operation)
     */
    suspend fun deleteByDateRange(startDate: java.util.Date, endDate: java.util.Date): Result<Int> {
        return try {
            Log.w(TAG, "Deleting transactions from $startDate to $endDate - this is a destructive operation")
            
            // Get transactions in date range
            val transactionsToDelete = repository.getTransactionsByDateRange(startDate, endDate)
            
            if (transactionsToDelete.isEmpty()) {
                Log.d(TAG, "No transactions found in date range")
                return Result.success(0)
            }
            
            Log.w(TAG, "About to delete ${transactionsToDelete.size} transactions")
            
            // Delete each transaction
            val result = executeMultiple(transactionsToDelete)
            
            Log.d(TAG, "Date range deletion completed")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transactions by date range", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete transactions by merchant (dangerous operation)  
     */
    suspend fun deleteByMerchant(merchantName: String): Result<Int> {
        return try {
            Log.w(TAG, "Deleting transactions for merchant: $merchantName - this is a destructive operation")
            
            // Get transactions for merchant
            val transactionsToDelete = repository.getTransactionsByMerchant(merchantName)
            
            if (transactionsToDelete.isEmpty()) {
                Log.d(TAG, "No transactions found for merchant")
                return Result.success(0)
            }
            
            Log.w(TAG, "About to delete ${transactionsToDelete.size} transactions for merchant")
            
            // Delete each transaction
            val result = executeMultiple(transactionsToDelete)
            
            Log.d(TAG, "Merchant deletion completed")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transactions by merchant", e)
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
            Log.w(TAG, "Delete operation cancelled - no confirmation provided")
            Result.failure(Exception("Delete operation requires confirmation"))
        }
    }
}