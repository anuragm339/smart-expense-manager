package com.expensemanager.app.data.repository

import android.content.Context
import android.util.Log
import com.expensemanager.app.data.database.ExpenseDatabase
import com.expensemanager.app.data.entities.*
import com.expensemanager.app.data.dao.*
import com.expensemanager.app.utils.SMSHistoryReader
import com.expensemanager.app.utils.ParsedTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.Calendar

class ExpenseRepository(private val context: Context) {
    
    private val database = ExpenseDatabase.getDatabase(context)
    private val transactionDao = database.transactionDao()
    private val categoryDao = database.categoryDao()
    private val merchantDao = database.merchantDao()
    private val syncStateDao = database.syncStateDao()
    
    companion object {
        private const val TAG = "ExpenseRepository"
        
        @Volatile
        private var INSTANCE: ExpenseRepository? = null
        
        fun getInstance(context: Context): ExpenseRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ExpenseRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    // =======================
    // TRANSACTION OPERATIONS
    // =======================
    
    suspend fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return transactionDao.getAllTransactions()
    }
    
    suspend fun getTransactionsByDateRange(startDate: Date, endDate: Date): List<TransactionEntity> {
        return transactionDao.getTransactionsByDateRange(startDate, endDate)
    }
    
    suspend fun getCategorySpending(startDate: Date, endDate: Date): List<CategorySpendingResult> {
        // Instead of using the database query that doesn't handle exclusions properly,
        // let's rebuild category spending from filtered transactions
        val allTransactions = getTransactionsByDateRange(startDate, endDate)
        val filteredTransactions = filterTransactionsByExclusions(allTransactions)
        
        // Group transactions by category and calculate totals
        val categoryTotals = mutableMapOf<String, Triple<Double, Int, Date?>>()
        
        for (transaction in filteredTransactions) {
            // Get category for this transaction
            val merchantWithCategory = getMerchantWithCategory(transaction.normalizedMerchant)
            val categoryName = merchantWithCategory?.category_name ?: "Other"
            
            val currentData = categoryTotals[categoryName] ?: Triple(0.0, 0, null)
            val newTotal = currentData.first + transaction.amount
            val newCount = currentData.second + 1
            val newLastDate = if (currentData.third == null || transaction.transactionDate.after(currentData.third)) {
                transaction.transactionDate
            } else {
                currentData.third
            }
            
            categoryTotals[categoryName] = Triple(newTotal, newCount, newLastDate)
        }
        
        // Convert to CategorySpendingResult objects
        return categoryTotals.map { (categoryName, data) ->
            // Get category info from database
            val category = getCategoryByName(categoryName)
            CategorySpendingResult(
                category_id = category?.id ?: 0L,
                category_name = categoryName,
                color = category?.color ?: "#888888",
                total_amount = data.first,
                transaction_count = data.second,
                last_transaction_date = data.third
            )
        }.sortedByDescending { it.total_amount }
    }
    
    suspend fun getTopMerchants(startDate: Date, endDate: Date, limit: Int = 10): List<MerchantSpending> {
        val allMerchants = transactionDao.getTopMerchantsBySpending(startDate, endDate, limit * 2) // Get more to account for filtering
        return filterMerchantsByExclusions(allMerchants).take(limit)
    }
    
    suspend fun getTotalSpent(startDate: Date, endDate: Date): Double {
        val transactions = getTransactionsByDateRange(startDate, endDate)
        val filteredTransactions = filterTransactionsByExclusions(transactions)
        return filteredTransactions.sumOf { it.amount }
    }
    
    suspend fun getTransactionCount(startDate: Date, endDate: Date): Int {
        val transactions = getTransactionsByDateRange(startDate, endDate)
        return filterTransactionsByExclusions(transactions).size
    }
    
    suspend fun getTransactionCount(): Int {
        return transactionDao.getTransactionCount()
    }
    
    suspend fun insertTransaction(transactionEntity: TransactionEntity): Long {
        return transactionDao.insertTransaction(transactionEntity)
    }
    
    suspend fun getTransactionBySmsId(smsId: String): TransactionEntity? {
        return transactionDao.getTransactionBySmsId(smsId)
    }
    
    suspend fun updateSyncState(lastSyncDate: Date) {
        syncStateDao.updateSyncState(lastSyncDate, null, getTransactionCount(), "COMPLETED")
    }
    
    suspend fun searchTransactions(query: String, limit: Int = 50): List<TransactionEntity> {
        return transactionDao.searchTransactions(query, limit)
    }
    
    // =======================
    // CATEGORY OPERATIONS  
    // =======================
    
    suspend fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getAllCategories()
    }
    
    suspend fun getAllCategoriesSync(): List<CategoryEntity> {
        return categoryDao.getAllCategoriesSync()
    }
    
    suspend fun getCategoryById(categoryId: Long): CategoryEntity? {
        return categoryDao.getCategoryById(categoryId)
    }
    
    suspend fun getCategoryByName(name: String): CategoryEntity? {
        return categoryDao.getCategoryByName(name)
    }
    
    suspend fun insertCategory(category: CategoryEntity): Long {
        return categoryDao.insertCategory(category)
    }
    
    suspend fun updateCategory(category: CategoryEntity) {
        categoryDao.updateCategory(category)
    }
    
    suspend fun deleteCategory(category: CategoryEntity) {
        categoryDao.deleteCategory(category)
    }
    
    // =======================
    // MERCHANT OPERATIONS
    // =======================
    
    suspend fun getAllMerchants(): List<MerchantEntity> {
        return merchantDao.getAllMerchantsSync()
    }
    
    suspend fun getMerchantByNormalizedName(normalizedName: String): MerchantEntity? {
        return merchantDao.getMerchantByNormalizedName(normalizedName)
    }
    
    suspend fun getMerchantWithCategory(normalizedName: String): MerchantWithCategory? {
        return merchantDao.getMerchantWithCategory(normalizedName)
    }
    
    suspend fun insertMerchant(merchant: MerchantEntity): Long {
        return merchantDao.insertMerchant(merchant)
    }
    
    suspend fun updateMerchant(merchant: MerchantEntity) {
        merchantDao.updateMerchant(merchant)
    }
    
    suspend fun updateMerchantExclusion(normalizedMerchantName: String, isExcluded: Boolean) {
        merchantDao.updateMerchantExclusion(normalizedMerchantName, isExcluded)
    }
    
    suspend fun getExcludedMerchants(): List<MerchantEntity> {
        return merchantDao.getExcludedMerchants()
    }
    
    suspend fun findOrCreateMerchant(
        normalizedName: String,
        displayName: String,
        categoryId: Long
    ): MerchantEntity {
        return getMerchantByNormalizedName(normalizedName) ?: run {
            val merchantId = insertMerchant(
                MerchantEntity(
                    normalizedName = normalizedName,
                    displayName = displayName,
                    categoryId = categoryId,
                    createdAt = Date()
                )
            )
            MerchantEntity(
                id = merchantId,
                normalizedName = normalizedName,
                displayName = displayName,
                categoryId = categoryId,
                createdAt = Date()
            )
        }
    }
    
    // =======================
    // SMS SYNC OPERATIONS
    // =======================
    
    suspend fun syncNewSMS(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting SMS sync...")
            
            val syncState = syncStateDao.getSyncState()
            val lastSyncTimestamp = syncState?.lastSmsSyncTimestamp ?: Date(0)
            
            Log.d(TAG, "Last sync timestamp: $lastSyncTimestamp")
            
            syncStateDao.updateSyncStatus("IN_PROGRESS")
            
            val smsReader = SMSHistoryReader(context)
            val allTransactions = smsReader.scanHistoricalSMS()
            
            val newTransactions = allTransactions.filter { 
                it.date.after(lastSyncTimestamp) 
            }
            
            Log.d(TAG, "Found ${newTransactions.size} new transactions to sync")
            
            var insertedCount = 0
            for (parsedTransaction in newTransactions) {
                val transactionEntity = convertToTransactionEntity(parsedTransaction)
                
                // Check if transaction already exists
                val existingTransaction = transactionDao.getTransactionBySmsId(transactionEntity.smsId)
                if (existingTransaction == null) {
                    val insertedId = transactionDao.insertTransaction(transactionEntity)
                    if (insertedId > 0) {
                        insertedCount++
                    }
                }
            }
            
            // Update sync state
            val latestTransaction = allTransactions.maxByOrNull { it.date }
            if (latestTransaction != null) {
                val totalTransactions = transactionDao.getTransactionCount()
                syncStateDao.updateSyncState(
                    timestamp = latestTransaction.date,
                    smsId = latestTransaction.id,
                    totalTransactions = totalTransactions,
                    status = "COMPLETED"
                )
            }
            
            Log.d(TAG, "SMS sync completed. Inserted $insertedCount new transactions")
            insertedCount
            
        } catch (e: Exception) {
            Log.e(TAG, "SMS sync failed", e)
            syncStateDao.updateSyncStatus("FAILED")
            throw e
        }
    }
    
    suspend fun getLastSyncTimestamp(): Date? {
        return syncStateDao.getLastSyncTimestamp()
    }
    
    suspend fun getSyncStatus(): String? {
        return syncStateDao.getSyncStatus()
    }
    
    // =======================
    // FAST DASHBOARD QUERIES
    // =======================
    
    suspend fun getDashboardData(startDate: Date, endDate: Date): DashboardData {
        val totalSpent = getTotalSpent(startDate, endDate)
        val transactionCount = getTransactionCount(startDate, endDate)
        val categorySpending = getCategorySpending(startDate, endDate)
        val topMerchants = getTopMerchants(startDate, endDate, 5)
        
        return DashboardData(
            totalSpent = totalSpent,
            transactionCount = transactionCount,
            topCategories = categorySpending.take(6),
            topMerchants = topMerchants
        )
    }
    
    // =======================
    // UTILITY METHODS
    // =======================
    
    suspend fun convertToTransactionEntity(parsedTransaction: ParsedTransaction): TransactionEntity {
        val normalizedMerchant = normalizeMerchantName(parsedTransaction.merchant)
        
        // Ensure merchant exists in the merchants table with proper category
        ensureMerchantExists(normalizedMerchant, parsedTransaction.merchant)
        
        return TransactionEntity(
            smsId = parsedTransaction.id,
            amount = parsedTransaction.amount,
            rawMerchant = parsedTransaction.merchant,
            normalizedMerchant = normalizedMerchant,
            bankName = parsedTransaction.bankName,
            transactionDate = parsedTransaction.date,
            rawSmsBody = parsedTransaction.rawSMS,
            confidenceScore = parsedTransaction.confidence,
            isDebit = true, // Assuming debit transactions for now
            createdAt = Date(),
            updatedAt = Date()
        )
    }
    
    private suspend fun ensureMerchantExists(normalizedName: String, displayName: String) {
        // Check if merchant already exists
        val existingMerchant = getMerchantByNormalizedName(normalizedName)
        if (existingMerchant != null) {
            return // Merchant already exists
        }
        
        // Determine category for this merchant using smart categorization
        val categoryName = categorizeMerchant(displayName)
        val category = getCategoryByName(categoryName) ?: getCategoryByName("Other")!!
        
        // Create new merchant entity
        val merchantEntity = MerchantEntity(
            normalizedName = normalizedName,
            displayName = displayName,
            categoryId = category.id,
            isUserDefined = false, // Auto-generated from SMS
            createdAt = Date()
        )
        
        insertMerchant(merchantEntity)
        Log.d(TAG, "✅ Created merchant: $displayName -> $categoryName")
    }
    
    private fun categorizeMerchant(merchantName: String): String {
        val nameUpper = merchantName.uppercase()
        
        return when {
            // Food & Dining
            nameUpper.contains("SWIGGY") || nameUpper.contains("ZOMATO") || 
            nameUpper.contains("DOMINOES") || nameUpper.contains("PIZZA") ||
            nameUpper.contains("MCDONALD") || nameUpper.contains("KFC") ||
            nameUpper.contains("RESTAURANT") || nameUpper.contains("CAFE") ||
            nameUpper.contains("FOOD") || nameUpper.contains("DINING") ||
            nameUpper.contains("AKSHAYAKALPA") -> "Food & Dining"
            
            // Transportation
            nameUpper.contains("UBER") || nameUpper.contains("OLA") ||
            nameUpper.contains("TAXI") || nameUpper.contains("METRO") ||
            nameUpper.contains("BUS") || nameUpper.contains("TRANSPORT") -> "Transportation"
            
            // Groceries
            nameUpper.contains("BIGBAZAAR") || nameUpper.contains("DMART") ||
            nameUpper.contains("RELIANCE") || nameUpper.contains("GROCERY") ||
            nameUpper.contains("SUPERMARKET") || nameUpper.contains("FRESH") ||
            nameUpper.contains("MART") -> "Groceries"
            
            // Healthcare  
            nameUpper.contains("HOSPITAL") || nameUpper.contains("CLINIC") ||
            nameUpper.contains("PHARMACY") || nameUpper.contains("MEDICAL") ||
            nameUpper.contains("HEALTH") || nameUpper.contains("DOCTOR") -> "Healthcare"
            
            // Entertainment
            nameUpper.contains("MOVIE") || nameUpper.contains("CINEMA") ||
            nameUpper.contains("THEATRE") || nameUpper.contains("GAME") ||
            nameUpper.contains("ENTERTAINMENT") || nameUpper.contains("NETFLIX") ||
            nameUpper.contains("SPOTIFY") -> "Entertainment"
            
            // Shopping
            nameUpper.contains("AMAZON") || nameUpper.contains("FLIPKART") ||
            nameUpper.contains("MYNTRA") || nameUpper.contains("AJIO") ||
            nameUpper.contains("SHOPPING") || nameUpper.contains("STORE") -> "Shopping"
            
            // Utilities
            nameUpper.contains("ELECTRICITY") || nameUpper.contains("WATER") ||
            nameUpper.contains("GAS") || nameUpper.contains("INTERNET") ||
            nameUpper.contains("MOBILE") || nameUpper.contains("RECHARGE") -> "Utilities"
            
            else -> "Other"
        }
    }
    
    private fun normalizeMerchantName(merchant: String): String {
        return merchant.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    suspend fun initializeDefaultData() = withContext(Dispatchers.IO) {
        // Check if categories already exist
        val existingCategories = categoryDao.getAllCategoriesSync()
        if (existingCategories.isEmpty()) {
            Log.d(TAG, "Initializing default categories...")
            // Categories are already inserted by DatabaseCallback
        }
        
        // Initialize sync state if not exists
        val syncState = syncStateDao.getSyncState()
        if (syncState == null) {
            syncStateDao.insertOrUpdateSyncState(
                SyncStateEntity(
                    lastSmsSyncTimestamp = Date(0),
                    lastSmsId = null,
                    totalTransactions = 0,
                    lastFullSync = Date(),
                    syncStatus = "INITIAL"
                )
            )
        }
    }
    
    // =======================
    // EXCLUSION FILTERING
    // =======================
    
    private suspend fun filterTransactionsByExclusions(transactions: List<TransactionEntity>): List<TransactionEntity> {
        return try {
            // Get all excluded merchants from database
            val excludedMerchants = merchantDao.getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()
            
            Log.d(TAG, "🔍 Filtering ${transactions.size} transactions with ${excludedNormalizedNames.size} excluded merchants")
            
            // Filter out transactions from excluded merchants
            val filteredTransactions = transactions.filter { transaction ->
                val isExcluded = excludedNormalizedNames.contains(transaction.normalizedMerchant)
                !isExcluded // Include transaction only if merchant is NOT excluded
            }
            
            Log.d(TAG, "✅ Filtered result: ${filteredTransactions.size}/${transactions.size} transactions included")
            
            filteredTransactions
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering transactions by exclusions", e)
            transactions // Return all transactions on error
        }
    }
    
    
    /**
     * Helper method to normalize merchant names consistently across the app
     */
    fun normalizeDisplayMerchantName(normalizedMerchant: String): String {
        return normalizedMerchant.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase() else it.toString() 
        }
    }

    /**
     * Debug helper to get current exclusion states from database
     */
    suspend fun getExclusionStatesDebugInfo(): String {
        return try {
            val excludedMerchants = getExcludedMerchants()
            if (excludedMerchants.isNotEmpty()) {
                val excludedNames = excludedMerchants.map { "'${it.displayName}': excluded" }
                "Excluded merchants (${excludedNames.size}): [${excludedNames.joinToString(", ")}]"
            } else {
                "No merchants are excluded from expense tracking"
            }
        } catch (e: Exception) {
            "Error loading exclusion states: ${e.message}"
        }
    }

    private suspend fun filterMerchantsByExclusions(merchants: List<com.expensemanager.app.data.dao.MerchantSpending>): List<com.expensemanager.app.data.dao.MerchantSpending> {
        return try {
            val excludedMerchants = getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()
            
            Log.d(TAG, "🔍 filterMerchantsByExclusions: Checking ${merchants.size} merchants")
            Log.d(TAG, "🔍 Excluded merchants: ${excludedNormalizedNames.size}")
            
            val filteredMerchants = merchants.filter { merchant ->
                val isExcluded = excludedNormalizedNames.contains(merchant.normalized_merchant)
                !isExcluded // Include only if not excluded
            }
            
            Log.d(TAG, "🔍 Filtered merchants: ${filteredMerchants.size}/${merchants.size}")
            filteredMerchants
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering merchants by exclusions", e)
            merchants // Return all merchants on error
        }
    }
    
    // =======================
    // DATABASE MAINTENANCE
    // =======================
    
    suspend fun cleanupDuplicateTransactions(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🧹 Starting database cleanup for duplicate transactions...")
            
            // Get all transactions grouped by potential duplicate key
            val allTransactions = transactionDao.getAllTransactionsSync()
            val duplicateGroups = allTransactions.groupBy { transaction ->
                // Create a key combining merchant, amount, and date (to day precision)
                val dayPrecisionDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(transaction.transactionDate)
                "${transaction.normalizedMerchant}_${transaction.amount}_${dayPrecisionDate}"
            }.filter { it.value.size > 1 }
            
            var removedCount = 0
            
            for ((key, duplicates) in duplicateGroups) {
                Log.d(TAG, "🔍 Found ${duplicates.size} potential duplicates for key: $key")
                
                // Keep the most recent one, remove others
                val toKeep = duplicates.maxByOrNull { it.createdAt }
                val toRemove = duplicates.filter { it.id != toKeep?.id }
                
                for (duplicate in toRemove) {
                    transactionDao.deleteTransaction(duplicate)
                    removedCount++
                    Log.d(TAG, "🗑️ Removed duplicate transaction: ${duplicate.rawMerchant} - ₹${duplicate.amount}")
                }
            }
            
            Log.d(TAG, "✅ Database cleanup completed. Removed $removedCount duplicate transactions")
            removedCount
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Database cleanup failed", e)
            0
        }
    }
    
    suspend fun removeObviousTestData(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🧹 Starting cleanup of obvious test data...")
            
            val testMerchants = listOf(
                "test", "example", "demo", "sample", "dummy",
                "PRAGATHI HARDWARE AND ELECTRICALS", // Our test SMS
                "AMAZON PAY", "SWIGGY", "ZOMATO" // If amounts are suspiciously high
            )
            
            var removedCount = 0
            
            // Remove transactions from test merchants with high amounts (> ₹10,000)
            for (merchant in testMerchants) {
                val transactions = transactionDao.getTransactionsByMerchantAndAmount(
                    merchant.lowercase(), 10000.0
                )
                
                for (transaction in transactions) {
                    transactionDao.deleteTransaction(transaction)
                    removedCount++
                    Log.d(TAG, "🗑️ Removed test transaction: ${transaction.rawMerchant} - ₹${transaction.amount}")
                }
            }
            
            Log.d(TAG, "✅ Test data cleanup completed. Removed $removedCount test transactions")
            removedCount
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Test data cleanup failed", e)
            0
        }
    }
}

// Data classes for repository responses
data class DashboardData(
    val totalSpent: Double,
    val transactionCount: Int,
    val topCategories: List<CategorySpendingResult>,
    val topMerchants: List<MerchantSpending>
)