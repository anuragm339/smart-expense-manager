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

    @Query("SELECT * FROM transactions WHERE transaction_date >= :startDate AND transaction_date <= :endDate ORDER BY transaction_date DESC LIMIT :limit OFFSET :offset")
    suspend fun getTransactionsByDateRangePaginated(startDate: Date, endDate: Date, limit: Int, offset: Int): List<TransactionEntity>

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
    
    // EXPENSE-SPECIFIC QUERIES (Only debit transactions)
    @Query("SELECT * FROM transactions WHERE is_debit = 1 AND transaction_date >= :startDate AND transaction_date <= :endDate ORDER BY transaction_date DESC")
    suspend fun getExpenseTransactionsByDateRange(startDate: Date, endDate: Date): List<TransactionEntity>
    
    @Query("SELECT COUNT(*) FROM transactions WHERE is_debit = 1 AND transaction_date >= :startDate AND transaction_date <= :endDate")
    suspend fun getExpenseTransactionCount(startDate: Date, endDate: Date): Int
    
    @Query("SELECT SUM(amount) FROM transactions WHERE transaction_date >= :startDate AND transaction_date <= :endDate AND is_debit = 1")
    suspend fun getTotalSpentByDateRange(startDate: Date, endDate: Date): Double?
    
    // Credit transaction queries for balance calculation
    @Query("SELECT SUM(amount) FROM transactions WHERE transaction_date >= :startDate AND transaction_date <= :endDate AND is_debit = 0")
    suspend fun getTotalCreditsOrIncomeByDateRange(startDate: Date, endDate: Date): Double?
    
    @Query("SELECT COUNT(*) FROM transactions WHERE is_debit = 0 AND transaction_date >= :startDate AND transaction_date <= :endDate")
    suspend fun getCreditTransactionCount(startDate: Date, endDate: Date): Int
    
    // Salary-specific queries for monthly balance calculation
    @Query("""
        SELECT * FROM transactions 
        WHERE is_debit = 0 
        AND (raw_sms_body LIKE '%salary%' OR raw_merchant LIKE '%SALARY%' 
             OR raw_sms_body LIKE '%sal %' OR raw_merchant LIKE '%SAL%'
             OR raw_sms_body LIKE '%wages%' OR raw_merchant LIKE '%WAGE%'
             OR raw_sms_body LIKE '%payroll%' OR raw_merchant LIKE '%PAYROLL%')
        ORDER BY transaction_date DESC 
        LIMIT 1
    """)
    suspend fun getLastSalaryTransaction(): TransactionEntity?
    
    @Query("""
        SELECT * FROM transactions 
        WHERE is_debit = 0 
        AND amount >= :minAmount
        AND (raw_sms_body LIKE '%salary%' OR raw_merchant LIKE '%SALARY%' 
             OR raw_sms_body LIKE '%sal %' OR raw_merchant LIKE '%SAL%'
             OR raw_sms_body LIKE '%wages%' OR raw_merchant LIKE '%WAGE%'
             OR raw_sms_body LIKE '%payroll%' OR raw_merchant LIKE '%PAYROLL%')
        ORDER BY transaction_date DESC 
        LIMIT :limit
    """)
    suspend fun getSalaryTransactions(minAmount: Double = 10000.0, limit: Int = 10): List<TransactionEntity>
    
    @Query("""
        SELECT 
            t.normalized_merchant, 
            SUM(t.amount) as total_amount, 
            COUNT(*) as transaction_count,
            COALESCE(c.name, 'Unknown') as category_name,
            COALESCE(c.color, '#9e9e9e') as category_color
        FROM transactions t
        LEFT JOIN merchants m ON t.normalized_merchant = m.normalized_name
        LEFT JOIN categories c ON m.category_id = c.id
        WHERE t.transaction_date >= :startDate AND t.transaction_date <= :endDate AND t.is_debit = 1
        GROUP BY t.normalized_merchant, c.name, c.color
        ORDER BY total_amount DESC 
        LIMIT :limit
    """)
    suspend fun getTopMerchantsBySpending(startDate: Date, endDate: Date, limit: Int = 10): List<MerchantSpendingWithCategory>
    
    @Query("""
        SELECT MAX(transaction_date) as last_sync_date, MAX(sms_id) as last_sms_id 
        FROM transactions
    """)
    suspend fun getLastSyncInfo(): SyncInfo?
    
    @Query("SELECT * FROM transactions WHERE sms_id = :smsId")
    suspend fun getTransactionBySmsId(smsId: String): TransactionEntity?
    
    @Query("""
        SELECT * FROM transactions 
        WHERE normalized_merchant = :normalizedMerchant 
          AND amount BETWEEN :minAmount AND :maxAmount 
          AND transaction_date BETWEEN :startDate AND :endDate 
          AND bank_name = :bankName
        LIMIT 1
    """)
    suspend fun findSimilarTransaction(
        normalizedMerchant: String,
        minAmount: Double,
        maxAmount: Double,
        startDate: Date,
        endDate: Date,
        bankName: String
    ): TransactionEntity?
    
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
    
    /**
     * Find transactions that might be duplicates based on business logic
     * Used for deduplication when SMS IDs might differ but transactions are identical
     */
    @Query("""
        SELECT COUNT(*) FROM transactions 
        WHERE normalized_merchant = :normalizedMerchant 
        AND amount = :amount 
        AND DATE(transaction_date) = :transactionDateStr
        AND bank_name = :bankName
    """)
    suspend fun countSimilarTransactions(
        normalizedMerchant: String,
        amount: Double,
        transactionDateStr: String,
        bankName: String
    ): Int
    
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

    // Get all merchants in a specific category with their transaction statistics
    @Query("""
        SELECT m.display_name as displayName, m.normalized_name as normalizedName,
               SUM(t.amount) as totalAmount, COUNT(t.id) as transactionCount,
               MAX(t.transaction_date) as lastTransactionDate
        FROM merchants m
        LEFT JOIN transactions t ON m.normalized_name = t.normalized_merchant AND t.is_debit = 1
        WHERE m.category_id = :categoryId
        GROUP BY m.id, m.display_name, m.normalized_name
        HAVING transactionCount > 0
        ORDER BY totalAmount DESC
    """)
    suspend fun getMerchantsInCategoryWithStats(categoryId: Long): List<MerchantCategoryStats>
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

data class MerchantSpendingWithCategory(
    val normalized_merchant: String,
    val total_amount: Double,
    val transaction_count: Int,
    val category_name: String,
    val category_color: String
)

data class CategorySpendingResult(
    val category_id: Long,
    val category_name: String,
    val color: String,
    val total_amount: Double,
    val transaction_count: Int,
    val last_transaction_date: Date?
)

data class MerchantCategoryStats(
    val displayName: String,
    val normalizedName: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val lastTransactionDate: Date
)
