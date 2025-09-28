package com.expensemanager.app.domain.usecase.transaction

import android.util.Log
import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.domain.repository.TransactionRepositoryInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

/**
 * Use case for getting transactions with business logic
 * Handles filtering, sorting, and transformation of transactions
 */
class GetTransactionsUseCase @Inject constructor(
    private val repository: TransactionRepositoryInterface
) {
    
    companion object {
        private const val TAG = "GetTransactionsUseCase"
    }
    
    /**
     * Get all transactions as a reactive stream
     */
    suspend fun execute(): Flow<Result<List<TransactionEntity>>> {
        return try {
            repository.getAllTransactions().map { transactions ->
                Result.success(transactions)
            }
        } catch (e: Exception) {
            kotlinx.coroutines.flow.flowOf(Result.failure(e))
        }
    }
    
    /**
     * Get transactions with filtering applied
     */
    suspend fun execute(params: GetTransactionsParams): Flow<Result<List<TransactionEntity>>> {
        return try {
            repository.getAllTransactions().map { transactions ->
                Result.success(processTransactions(transactions, params))
            }
        } catch (e: Exception) {
            kotlinx.coroutines.flow.flowOf(Result.failure(e))
        }
    }
    
    /**
     * Get transactions by date range
     */
    suspend fun getTransactionsByDateRange(startDate: Date, endDate: Date): Result<List<TransactionEntity>> {
        return try {
            Log.d(TAG, "Getting transactions from ${startDate} to ${endDate}")
            val transactions = repository.getTransactionsByDateRange(startDate, endDate)
            Log.d(TAG, "Retrieved ${transactions.size} transactions")
            Result.success(transactions)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting transactions by date range", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get transactions by merchant
     */
    suspend fun getTransactionsByMerchant(merchantName: String): Result<List<TransactionEntity>> {
        return try {
            Log.d(TAG, "Getting transactions for merchant: $merchantName")
            val transactions = repository.getTransactionsByMerchant(merchantName)
            Log.d(TAG, "Retrieved ${transactions.size} transactions for merchant")
            Result.success(transactions)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting transactions by merchant", e)
            Result.failure(e)
        }
    }
    
    /**
     * Search transactions
     */
    suspend fun searchTransactions(query: String, limit: Int = 50): Result<List<TransactionEntity>> {
        return try {
            val transactions = repository.searchTransactions(query, limit)
            Result.success(transactions)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching transactions", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get recent transactions (current month - updated to match Dashboard logic)
     */
    suspend fun getRecentTransactions(): Result<List<TransactionEntity>> {
        // Use current month instead of 30-day hardcoded period to match Dashboard
        val calendar = Calendar.getInstance()
        
        // Start of current month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.time
        
        // End of current month  
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfMonth = calendar.time
        
        return getTransactionsByDateRange(startOfMonth, endOfMonth)
    }
    
    /**
     * Get transactions for current month
     */
    suspend fun getCurrentMonthTransactions(): Result<List<TransactionEntity>> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        
        val startDate = calendar.time
        val endDate = Date()
        
        return getTransactionsByDateRange(startDate, endDate)
    }
    
    /**
     * Process transactions with business logic
     */
    private fun processTransactions(transactions: List<TransactionEntity>, params: GetTransactionsParams): List<TransactionEntity> {
        Log.d(TAG, "Processing ${transactions.size} transactions with params: $params")
        
        return transactions
            .let { list ->
                // Filter by date range if specified
                if (params.startDate != null && params.endDate != null) {
                    list.filter { transaction ->
                        transaction.transactionDate >= params.startDate && transaction.transactionDate <= params.endDate
                    }
                } else {
                    list
                }
            }
            .let { list ->
                // Filter by merchant if specified
                if (params.merchantName.isNotEmpty()) {
                    list.filter { transaction ->
                        transaction.normalizedMerchant.contains(params.merchantName, ignoreCase = true) ||
                        transaction.rawMerchant.contains(params.merchantName, ignoreCase = true)
                    }
                } else {
                    list
                }
            }
            .let { list ->
                // Filter by minimum amount if specified
                if (params.minAmount > 0.0) {
                    list.filter { it.amount >= params.minAmount }
                } else {
                    list
                }
            }
            .let { list ->
                // Filter by maximum amount if specified  
                if (params.maxAmount > 0.0) {
                    list.filter { it.amount <= params.maxAmount }
                } else {
                    list
                }
            }
            .let { list ->
                // Filter by bank if specified
                if (params.bankName.isNotEmpty()) {
                    list.filter { transaction ->
                        transaction.bankName.contains(params.bankName, ignoreCase = true)
                    }
                } else {
                    list
                }
            }
            .let { list ->
                // Sort by date (newest first) or amount if specified
                when (params.sortOrder) {
                    TransactionSortOrder.DATE_DESC -> list.sortedByDescending { it.transactionDate }
                    TransactionSortOrder.DATE_ASC -> list.sortedBy { it.transactionDate }
                    TransactionSortOrder.AMOUNT_DESC -> list.sortedByDescending { it.amount }
                    TransactionSortOrder.AMOUNT_ASC -> list.sortedBy { it.amount }
                    TransactionSortOrder.MERCHANT_ASC -> list.sortedBy { it.rawMerchant }
                }
            }
            .let { list ->
                // Apply limit if specified
                if (params.limit > 0) {
                    list.take(params.limit)
                } else {
                    list
                }
            }
            .also { processedList ->
                Log.d(TAG, "Processed transactions: ${processedList.size} after filtering and sorting")
            }
    }
}

/**
 * Parameters for getting transactions
 */
data class GetTransactionsParams(
    val startDate: Date? = null,
    val endDate: Date? = null,
    val merchantName: String = "",
    val bankName: String = "",
    val minAmount: Double = 0.0,
    val maxAmount: Double = 0.0,
    val sortOrder: TransactionSortOrder = TransactionSortOrder.DATE_DESC,
    val limit: Int = 0 // 0 means no limit
)

/**
 * Sort order options for transactions
 */
enum class TransactionSortOrder {
    DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC, MERCHANT_ASC
}