package com.smartexpenseai.app.domain.repository

import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.data.dao.MerchantSpending
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Repository interface for transaction data operations in domain layer
 * This interface defines the contract that data repositories must implement
 * Following clean architecture principles by separating domain contracts from data implementations
 */
interface TransactionRepositoryInterface {
    
    // =======================
    // READ OPERATIONS
    // =======================
    
    /**
     * Get all transactions as a reactive stream
     */
    suspend fun getAllTransactions(): Flow<List<TransactionEntity>>
    
    /**
     * Get transactions for a specific date range
     */
    suspend fun getTransactionsByDateRange(startDate: Date, endDate: Date): List<TransactionEntity>
    
    /**
     * Get transactions by merchant name
     */
    suspend fun getTransactionsByMerchant(merchantName: String): List<TransactionEntity>
    
    /**
     * Search transactions by query string
     */
    suspend fun searchTransactions(query: String, limit: Int = 50): List<TransactionEntity>
    
    /**
     * Get total transaction count
     */
    suspend fun getTransactionCount(): Int
    
    /**
     * Get transaction count for date range
     */
    suspend fun getTransactionCount(startDate: Date, endDate: Date): Int
    
    /**
     * Get total spent amount for date range
     */
    suspend fun getTotalSpent(startDate: Date, endDate: Date): Double
    
    /**
     * Get top merchants by spending
     */
    suspend fun getTopMerchants(startDate: Date, endDate: Date, limit: Int = 10): List<MerchantSpending>
    
    /**
     * Get top merchants by spending with category information
     */
    suspend fun getTopMerchantsWithCategory(startDate: Date, endDate: Date, limit: Int = 10): List<com.smartexpenseai.app.data.dao.MerchantSpendingWithCategory>
    
    /**
     * Get transaction by SMS ID
     */
    suspend fun getTransactionBySmsId(smsId: String): TransactionEntity?
    
    // =======================
    // WRITE OPERATIONS  
    // =======================
    
    /**
     * Insert a new transaction
     */
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    
    /**
     * Update existing transaction
     */
    suspend fun updateTransaction(transaction: TransactionEntity)
    
    /**
     * Delete transaction
     */
    suspend fun deleteTransaction(transaction: TransactionEntity)
    
    /**
     * Delete transaction by ID
     */
    suspend fun deleteTransactionById(transactionId: Long)
    
    // =======================
    // SYNC OPERATIONS
    // =======================
    
    /**
     * Sync new SMS transactions
     */
    suspend fun syncNewSMS(): Int
    
    /**
     * Get last sync timestamp
     */
    suspend fun getLastSyncTimestamp(): Date?
    
    /**
     * Get sync status
     */
    suspend fun getSyncStatus(): String?
    
    /**
     * Update sync state
     */
    suspend fun updateSyncState(lastSyncDate: Date)
}