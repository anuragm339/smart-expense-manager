package com.expensemanager.app.data.dao

import androidx.room.*
import com.expensemanager.app.data.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface TransactionDao {
    
    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>
    
    @Query("SELECT * FROM transactions WHERE transaction_date >= :startDate AND transaction_date <= :endDate ORDER BY transaction_date DESC")
    suspend fun getTransactionsByDateRange(startDate: Date, endDate: Date): List<TransactionEntity>
    
    @Query("SELECT * FROM transactions WHERE normalized_merchant = :merchantName ORDER BY transaction_date DESC")
    suspend fun getTransactionsByMerchant(merchantName: String): List<TransactionEntity>
    
    @Query("SELECT * FROM transactions WHERE normalized_merchant LIKE '%' || :merchantName || '%' AND amount >= :minAmount ORDER BY transaction_date DESC")
    suspend fun getTransactionsByMerchantAndAmount(merchantName: String, minAmount: Double): List<TransactionEntity>
    
    @Query("SELECT * FROM transactions WHERE bank_name = :bankName ORDER BY transaction_date DESC")
    suspend fun getTransactionsByBank(bankName: String): List<TransactionEntity>
    
    @Query("""
        SELECT * FROM transactions 
        WHERE (raw_merchant LIKE '%' || :query || '%' OR normalized_merchant LIKE '%' || :query || '%')
        ORDER BY transaction_date DESC 
        LIMIT :limit
    """)
    suspend fun searchTransactions(query: String, limit: Int = 50): List<TransactionEntity>
    
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int
    
    @Query("SELECT COUNT(*) FROM transactions WHERE transaction_date >= :startDate AND transaction_date <= :endDate")
    suspend fun getTransactionCountByDateRange(startDate: Date, endDate: Date): Int
    
    @Query("SELECT SUM(amount) FROM transactions WHERE transaction_date >= :startDate AND transaction_date <= :endDate AND is_debit = 1")
    suspend fun getTotalSpentByDateRange(startDate: Date, endDate: Date): Double?
    
    @Query("""
        SELECT normalized_merchant, SUM(amount) as total_amount, COUNT(*) as transaction_count
        FROM transactions 
        WHERE transaction_date >= :startDate AND transaction_date <= :endDate AND is_debit = 1
        GROUP BY normalized_merchant 
        ORDER BY total_amount DESC 
        LIMIT :limit
    """)
    suspend fun getTopMerchantsBySpending(startDate: Date, endDate: Date, limit: Int = 10): List<MerchantSpending>
    
    @Query("""
        SELECT MAX(transaction_date) as last_sync_date, MAX(sms_id) as last_sms_id 
        FROM transactions
    """)
    suspend fun getLastSyncInfo(): SyncInfo?
    
    @Query("SELECT * FROM transactions WHERE sms_id = :smsId")
    suspend fun getTransactionBySmsId(smsId: String): TransactionEntity?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>
    
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)
    
    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
    
    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)
    
    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
    
    // For dashboard category breakdown - joins with merchants to get categories
    @Query("""
        SELECT m.category_id, c.name as category_name, c.color, 
               SUM(t.amount) as total_amount, COUNT(t.id) as transaction_count,
               MAX(t.transaction_date) as last_transaction_date
        FROM transactions t
        JOIN merchants m ON t.normalized_merchant = m.normalized_name
        JOIN categories c ON m.category_id = c.id
        WHERE t.transaction_date >= :startDate AND t.transaction_date <= :endDate AND t.is_debit = 1
        GROUP BY m.category_id, c.name, c.color
        ORDER BY total_amount DESC
    """)
    suspend fun getCategorySpendingBreakdown(startDate: Date, endDate: Date): List<CategorySpendingResult>
}

// Data classes for query results
data class MerchantSpending(
    val normalized_merchant: String,
    val total_amount: Double,
    val transaction_count: Int
)

data class SyncInfo(
    val last_sync_date: Date?,
    val last_sms_id: String?
)

data class CategorySpendingResult(
    val category_id: Long,
    val category_name: String,
    val color: String,
    val total_amount: Double,
    val transaction_count: Int,
    val last_transaction_date: Date?
)