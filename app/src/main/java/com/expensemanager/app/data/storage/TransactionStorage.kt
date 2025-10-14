package com.expensemanager.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import com.expensemanager.app.data.models.Transaction
import com.expensemanager.app.data.models.TransactionType
import com.expensemanager.app.models.ParsedTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class TransactionStorage(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("transactions", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_TRANSACTIONS = "stored_transactions"
        private const val KEY_LAST_SYNC = "last_sync_time"
        private const val TAG = "TransactionStorage"
    }
    
    // Save transactions
    suspend fun saveTransactions(transactions: List<Transaction>) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            transactions.forEach { transaction ->
                jsonArray.put(transactionToJson(transaction))
            }
            
            prefs.edit()
                .putString(KEY_TRANSACTIONS, jsonArray.toString())
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
            
            Timber.tag(TAG).d("Saved ${transactions.size} transactions to storage")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error saving transactions")
        }
    }
    
    // Load transactions
    suspend fun loadTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        try {
            val jsonString = prefs.getString(KEY_TRANSACTIONS, null)
            if (jsonString != null) {
                val jsonArray = JSONArray(jsonString)
                val transactions = mutableListOf<Transaction>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    transactions.add(jsonToTransaction(jsonObject))
                }
                
                Timber.tag(TAG).d("Loaded ${transactions.size} transactions from storage")
                return@withContext transactions
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading transactions")
        }
        return@withContext emptyList()
    }
    
    // Add a single transaction
    suspend fun addTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        val existingTransactions = loadTransactions().toMutableList()
        
        // Check for duplicate
        if (!existingTransactions.any { it.id == transaction.id || it.rawSMS == transaction.rawSMS }) {
            existingTransactions.add(transaction)
            saveTransactions(existingTransactions)
            Timber.tag(TAG).d("Added new transaction: ${transaction.merchant} - ₹${transaction.amount}")
        } else {
            Timber.tag(TAG).d("Duplicate transaction ignored: ${transaction.merchant} - ₹${transaction.amount}")
        }
    }
    
    // Update transaction
    suspend fun updateTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        val existingTransactions = loadTransactions().toMutableList()
        val index = existingTransactions.indexOfFirst { it.id == transaction.id }
        
        if (index != -1) {
            existingTransactions[index] = transaction
            saveTransactions(existingTransactions)
            Timber.tag(TAG).d("Updated transaction: ${transaction.id}")
        }
    }
    
    // Delete transaction
    suspend fun deleteTransaction(transactionId: String) = withContext(Dispatchers.IO) {
        val existingTransactions = loadTransactions().toMutableList()
        val wasRemoved = existingTransactions.removeAll { it.id == transactionId }
        
        if (wasRemoved) {
            saveTransactions(existingTransactions)
            Timber.tag(TAG).d("Deleted transaction: $transactionId")
        }
    }
    
    // Get transactions by date range
    suspend fun getTransactionsByDateRange(startDate: Date, endDate: Date): List<Transaction> = withContext(Dispatchers.IO) {
        loadTransactions().filter { transaction ->
            val transactionDate = Date(transaction.date)
            transactionDate >= startDate && transactionDate <= endDate
        }.sortedByDescending { it.date }
    }
    
    // Get transactions by merchant
    suspend fun getTransactionsByMerchant(merchant: String): List<Transaction> = withContext(Dispatchers.IO) {
        loadTransactions().filter { transaction ->
            transaction.merchant.contains(merchant, ignoreCase = true)
        }.sortedByDescending { it.date }
    }
    
    // Get transactions by category
    suspend fun getTransactionsByCategory(category: String): List<Transaction> = withContext(Dispatchers.IO) {
        loadTransactions().filter { it.category == category }.sortedByDescending { it.date }
    }
    
    // Get unprocessed transactions
    suspend fun getUnprocessedTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        loadTransactions().filter { !it.isProcessed }.sortedByDescending { it.date }
    }
    
    // Analytics methods
    suspend fun getCurrentMonthTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val endOfMonth = Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
        
        getTransactionsByDateRange(startOfMonth, endOfMonth)
    }
    
    suspend fun getLastMonthTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        
        val startOfLastMonth = Calendar.getInstance().apply {
            time = calendar.time
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val endOfLastMonth = Calendar.getInstance().apply {
            time = calendar.time
            val lastDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, lastDay)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
        
        getTransactionsByDateRange(startOfLastMonth, endOfLastMonth)
    }
    
    // Save SMS transactions from parsed data
    suspend fun saveSMSTransactions(parsedTransactions: List<ParsedTransaction>): List<Transaction> = withContext(Dispatchers.IO) {
        val newTransactions = mutableListOf<Transaction>()
        
        for (parsedTransaction in parsedTransactions) {
            val transaction = Transaction(
                id = generateTransactionId(parsedTransaction),
                amount = parsedTransaction.amount,
                merchant = parsedTransaction.merchant,
                category = "Other", // Will be updated by category manager
                date = parsedTransaction.date.time,
                rawSMS = parsedTransaction.rawSMS,
                confidence = parsedTransaction.confidence,
                bankName = parsedTransaction.bankName,
                transactionType = TransactionType.DEBIT,
                isProcessed = false
            )
            
            addTransaction(transaction)
            newTransactions.add(transaction)
        }
        
        newTransactions
    }
    
    // Get storage stats
    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)
    
    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val transactions = loadTransactions()
        val currentMonth = getCurrentMonthTransactions()
        val unprocessed = getUnprocessedTransactions()
        
        StorageStats(
            totalTransactions = transactions.size,
            currentMonthTransactions = currentMonth.size,
            unprocessedTransactions = unprocessed.size,
            totalAmount = transactions.sumOf { it.amount },
            currentMonthAmount = currentMonth.sumOf { it.amount },
            lastSyncTime = getLastSyncTime()
        )
    }
    
    // Clear all data
    suspend fun clearAllTransactions() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_TRANSACTIONS)
            .remove(KEY_LAST_SYNC)
            .apply()
        Timber.tag(TAG).d("Cleared all transaction data")
    }
    
    // Helper methods for JSON conversion
    private fun transactionToJson(transaction: Transaction): JSONObject {
        return JSONObject().apply {
            put("id", transaction.id)
            put("amount", transaction.amount)
            put("merchant", transaction.merchant)
            put("category", transaction.category)
            put("date", transaction.date)
            put("rawSMS", transaction.rawSMS)
            put("confidence", transaction.confidence.toDouble())
            put("bankName", transaction.bankName)
            put("transactionType", transaction.transactionType.name)
            put("isProcessed", transaction.isProcessed)
            put("createdAt", transaction.createdAt)
            put("isDuplicate", transaction.isDuplicate)
            put("notes", transaction.notes)
        }
    }
    
    private fun jsonToTransaction(jsonObject: JSONObject): Transaction {
        return Transaction(
            id = jsonObject.getString("id"),
            amount = jsonObject.getDouble("amount"),
            merchant = jsonObject.getString("merchant"),
            category = jsonObject.getString("category"),
            date = jsonObject.getLong("date"),
            rawSMS = jsonObject.getString("rawSMS"),
            confidence = jsonObject.getDouble("confidence").toFloat(),
            bankName = jsonObject.getString("bankName"),
            transactionType = TransactionType.valueOf(jsonObject.getString("transactionType")),
            isProcessed = jsonObject.getBoolean("isProcessed"),
            createdAt = jsonObject.optLong("createdAt", System.currentTimeMillis()),
            isDuplicate = jsonObject.optBoolean("isDuplicate", false),
            notes = jsonObject.optString("notes", "")
        )
    }
    
    private fun generateTransactionId(parsedTransaction: ParsedTransaction): String {
        val content = "${parsedTransaction.amount}_${parsedTransaction.merchant}_${parsedTransaction.date.time}_${parsedTransaction.bankName}"
        return content.hashCode().toString()
    }
}

data class StorageStats(
    val totalTransactions: Int,
    val currentMonthTransactions: Int,
    val unprocessedTransactions: Int,
    val totalAmount: Double,
    val currentMonthAmount: Double,
    val lastSyncTime: Long
)