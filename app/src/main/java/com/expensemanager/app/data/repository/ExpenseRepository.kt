package com.expensemanager.app.data.repository

import android.content.Context
import android.util.Log
import com.expensemanager.app.data.database.ExpenseDatabase
import com.expensemanager.app.data.entities.*
import com.expensemanager.app.data.dao.*
import com.expensemanager.app.domain.repository.*
import com.expensemanager.app.services.SMSParsingService
import com.expensemanager.app.services.ParsedTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class ExpenseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantDao,
    private val syncStateDao: SyncStateDao,
    private val smsParsingService: SMSParsingService
) : TransactionRepositoryInterface, 
    CategoryRepositoryInterface, 
    MerchantRepositoryInterface, 
    DashboardRepositoryInterface {
    
    companion object {
        private const val TAG = "ExpenseRepository"
        
        @Volatile
        private var INSTANCE: ExpenseRepository? = null
        
        // Temporary getInstance method for compatibility - TODO: Remove after migration to Hilt
        fun getInstance(context: Context): ExpenseRepository {
            return INSTANCE ?: synchronized(this) {
                val database = ExpenseDatabase.getDatabase(context)
                val smsParsingService = SMSParsingService(context.applicationContext)
                val instance = ExpenseRepository(
                    context.applicationContext,
                    database.transactionDao(),
                    database.categoryDao(),
                    database.merchantDao(),
                    database.syncStateDao(),
                    smsParsingService
                )
                INSTANCE = instance
                instance
            }
        }
    }
    
    // =======================
    // TRANSACTION OPERATIONS
    // =======================
    
    override suspend fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return transactionDao.getAllTransactions()
    }
    
    override suspend fun getTransactionsByDateRange(startDate: Date, endDate: Date): List<TransactionEntity> {
        return transactionDao.getTransactionsByDateRange(startDate, endDate)
    }
    
    override suspend fun getCategorySpending(startDate: Date, endDate: Date): List<CategorySpendingResult> {
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
    
    override suspend fun getTopMerchants(startDate: Date, endDate: Date, limit: Int): List<MerchantSpending> {
        val allMerchants = transactionDao.getTopMerchantsBySpending(startDate, endDate, limit * 2) // Get more to account for filtering
        return filterMerchantsByExclusions(allMerchants).take(limit)
    }
    
    override suspend fun getTotalSpent(startDate: Date, endDate: Date): Double {
        val transactions = getTransactionsByDateRange(startDate, endDate)
        val filteredTransactions = filterTransactionsByExclusions(transactions)
        return filteredTransactions.sumOf { it.amount }
    }
    
    override suspend fun getTransactionCount(startDate: Date, endDate: Date): Int {
        val transactions = getTransactionsByDateRange(startDate, endDate)
        return filterTransactionsByExclusions(transactions).size
    }
    
    override suspend fun getTransactionCount(): Int {
        return transactionDao.getTransactionCount()
    }
    
    override suspend fun insertTransaction(transactionEntity: TransactionEntity): Long {
        return transactionDao.insertTransaction(transactionEntity)
    }
    
    override suspend fun getTransactionBySmsId(smsId: String): TransactionEntity? {
        return transactionDao.getTransactionBySmsId(smsId)
    }
    
    /**
     * Find similar transactions to prevent duplicates from different SMS sources
     */
    suspend fun findSimilarTransaction(deduplicationKey: String): TransactionEntity? {
        // Parse the deduplication key to extract components
        val parts = deduplicationKey.split("_")
        if (parts.size < 4) return null
        
        val merchant = parts[0]
        val amount = parts[1].toDoubleOrNull() ?: return null
        val dateStr = parts[2]
        val bankName = parts.drop(3).joinToString("_")
        
        return transactionDao.findSimilarTransaction(merchant, amount, dateStr, bankName)
    }
    
    override suspend fun updateSyncState(lastSyncDate: Date) {
        syncStateDao.updateSyncState(lastSyncDate, null, getTransactionCount(), "COMPLETED")
    }
    
    override suspend fun searchTransactions(query: String, limit: Int): List<TransactionEntity> {
        return transactionDao.searchTransactions(query, limit)
    }
    
    override suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }
    
    override suspend fun deleteTransactionById(transactionId: Long) {
        transactionDao.deleteTransactionById(transactionId)
    }
    
    override suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }
    
    override suspend fun getTransactionsByMerchant(merchantName: String): List<TransactionEntity> {
        return transactionDao.getTransactionsByMerchant(merchantName)
    }
    
    // =======================
    // CATEGORY OPERATIONS  
    // =======================
    
    override suspend fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getAllCategories()
    }
    
    override suspend fun getAllCategoriesSync(): List<CategoryEntity> {
        return categoryDao.getAllCategoriesSync()
    }
    
    override suspend fun getCategoryById(categoryId: Long): CategoryEntity? {
        return categoryDao.getCategoryById(categoryId)
    }
    
    override suspend fun getCategoryByName(name: String): CategoryEntity? {
        return categoryDao.getCategoryByName(name)
    }
    
    override suspend fun insertCategory(category: CategoryEntity): Long {
        return categoryDao.insertCategory(category)
    }
    
    override suspend fun updateCategory(category: CategoryEntity) {
        categoryDao.updateCategory(category)
    }
    
    override suspend fun deleteCategory(category: CategoryEntity) {
        categoryDao.deleteCategory(category)
    }
    
    // =======================
    // MERCHANT OPERATIONS
    // =======================
    
    override suspend fun getAllMerchants(): List<MerchantEntity> {
        return merchantDao.getAllMerchantsSync()
    }
    
    override suspend fun getMerchantByNormalizedName(normalizedName: String): MerchantEntity? {
        return merchantDao.getMerchantByNormalizedName(normalizedName)
    }
    
    override suspend fun getMerchantWithCategory(normalizedName: String): MerchantWithCategory? {
        return merchantDao.getMerchantWithCategory(normalizedName)
    }
    
    override suspend fun insertMerchant(merchant: MerchantEntity): Long {
        return merchantDao.insertMerchant(merchant)
    }
    
    override suspend fun updateMerchant(merchant: MerchantEntity) {
        merchantDao.updateMerchant(merchant)
    }
    
    override suspend fun updateMerchantExclusion(normalizedMerchantName: String, isExcluded: Boolean) {
        merchantDao.updateMerchantExclusion(normalizedMerchantName, isExcluded)
    }
    
    override suspend fun getExcludedMerchants(): List<MerchantEntity> {
        return merchantDao.getExcludedMerchants()
    }

    suspend fun updateMerchantAliasInDatabase(
        originalMerchantNames: List<String>,
        newDisplayName: String,
        newCategoryName: String
    ): Boolean {
        return try {
            // Enhanced validation
            if (originalMerchantNames.isEmpty()) {
                return false
            }
            
            if (newDisplayName.trim().isEmpty()) {
                return false
            }
            
            if (newCategoryName.trim().isEmpty()) {
                return false
            }
            
            // Get category with enhanced error handling
            val category = getCategoryByName(newCategoryName)
            if (category == null) {
                // Try to create the category if it doesn't exist
                try {
                    val newCategory = CategoryEntity(
                        name = newCategoryName,
                        color = "#4CAF50", // Default green color
                        isSystem = false, // User-defined category
                        createdAt = Date()
                    )
                    val newCategoryId = insertCategory(newCategory)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create category: $newCategoryName", e)
                    return false
                }
                
                // Retry getting the category
                val retryCategory = getCategoryByName(newCategoryName)
                if (retryCategory == null) {
                    return false
                }
            }
            
            val finalCategory = getCategoryByName(newCategoryName)!!

            // Track update progress
            var updatedCount = 0
            var createdCount = 0
            val failedUpdates = mutableListOf<String>()
            
            // Process each merchant
            for (originalName in originalMerchantNames) {
                try {
                    val normalizedName = normalizeMerchantName(originalName)
                    
                    // Check if merchant exists in database
                    val merchantExistsCount = merchantDao.merchantExists(normalizedName)
                    val merchantExists = merchantExistsCount > 0
                    
                    if (merchantExists) {
                        // Update existing merchant
                        merchantDao.updateMerchantDisplayNameAndCategory(
                            normalizedName = normalizedName,
                            displayName = newDisplayName,
                            categoryId = finalCategory.id
                        )
                        updatedCount++
                    } else {
                        // Create new merchant entry
                        val newMerchant = MerchantEntity(
                            normalizedName = normalizedName,
                            displayName = newDisplayName,
                            categoryId = finalCategory.id,
                            isUserDefined = true,
                            createdAt = Date()
                        )
                        
                        val insertedMerchantId = insertMerchant(newMerchant)
                        createdCount++
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process merchant '$originalName'", e)
                    failedUpdates.add(originalName)
                }
            }
            
            // Final status report
            val totalProcessed = updatedCount + createdCount
            
            Log.d(TAG, "Merchant alias update: $totalProcessed/${originalMerchantNames.size} processed ($updatedCount updated, $createdCount created)")
            
            if (failedUpdates.isNotEmpty()) {
                Log.w(TAG, "Failed to update ${failedUpdates.size} merchants: $failedUpdates")
            }
            
            // Consider it successful if we updated at least some merchants
            val isSuccessful = totalProcessed > 0 && failedUpdates.size < originalMerchantNames.size
            
            return isSuccessful
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during database update", e)
            false
        }
    }
    
    override suspend fun findOrCreateMerchant(
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
    
    override suspend fun syncNewSMS(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 [UNIFIED] Starting SMS sync using unified SMSParsingService...")
            
            val syncState = syncStateDao.getSyncState()
            val lastSyncTimestamp = syncState?.lastSmsSyncTimestamp ?: Date(0)
            
            Log.d(TAG, "Last sync timestamp: $lastSyncTimestamp")
            
            syncStateDao.updateSyncStatus("IN_PROGRESS")
            
            // UNIFIED: Use SMSParsingService instead of duplicate parsing logic
            val allTransactions = smsParsingService.scanHistoricalSMS { current, total, status ->
                Log.d(TAG, "[UNIFIED PROGRESS] $status ($current/$total)")
            }
            Log.d(TAG, "📊 [UNIFIED] SMS scanning completed: ${allTransactions.size} valid transactions extracted from SMS")
            
            val newTransactions = allTransactions.filter { 
                it.date.after(lastSyncTimestamp) 
            }
            
            Log.d(TAG, "📊 [UNIFIED] SMS validation completed: ${newTransactions.size} new transactions to sync (filtered from ${allTransactions.size} total)")
            
            var insertedCount = 0
            var duplicateCount = 0
            val distinctMerchants = mutableSetOf<String>()
            
            for (parsedTransaction in newTransactions) {
                distinctMerchants.add(parsedTransaction.merchant)
                val transactionEntity = convertToTransactionEntity(parsedTransaction)
                
                // Enhanced duplicate detection - check both SMS ID and transaction similarity
                val existingTransaction = transactionDao.getTransactionBySmsId(transactionEntity.smsId)
                
                // Check for similar transactions that might be duplicates from different sources
                val deduplicationKey = TransactionEntity.generateDeduplicationKey(
                    merchant = parsedTransaction.merchant,
                    amount = parsedTransaction.amount, 
                    date = parsedTransaction.date,
                    bankName = parsedTransaction.bankName
                )
                val similarTransaction = findSimilarTransaction(deduplicationKey)
                
                if (existingTransaction == null && similarTransaction == null) {
                    val insertedId = transactionDao.insertTransaction(transactionEntity)
                    if (insertedId > 0) {
                        insertedCount++
                    }
                } else {
                    duplicateCount++
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
            
            Log.d(TAG, "📊 [UNIFIED] Database storage completed: $insertedCount transactions inserted into Room database")
            Log.d(TAG, "📊 [UNIFIED FINAL STATS] Complete SMS-to-Database pipeline summary:")
            Log.d(TAG, "  - 📱 SMS messages scanned: ${allTransactions.size}")
            Log.d(TAG, "  - ✅ Valid bank transactions found: ${newTransactions.size}")
            Log.d(TAG, "  - 💾 New transactions stored in database: $insertedCount")
            Log.d(TAG, "  - 🔄 Duplicates skipped: $duplicateCount")
            Log.d(TAG, "  - 🏪 Distinct merchants found: ${distinctMerchants.size}")
            Log.d(TAG, "  - 📋 Merchants: ${distinctMerchants.joinToString(", ")}")
            insertedCount
            
        } catch (e: Exception) {
            Log.e(TAG, "[UNIFIED] Repository-based SMS sync failed", e)
            syncStateDao.updateSyncStatus("FAILED")
            throw e
        }
    }
    
    // SMS parsing is now handled by unified SMSParsingService
    
    override suspend fun getLastSyncTimestamp(): Date? {
        return syncStateDao.getLastSyncTimestamp()
    }
    
    override suspend fun getSyncStatus(): String? {
        return syncStateDao.getSyncStatus()
    }
    
    /**
     * Check if database has any real transaction data
     * Returns true if database is completely empty or has only test data
     */
    suspend fun isDatabaseEmpty(): Boolean = withContext(Dispatchers.IO) {
        try {
            val totalTransactions = transactionDao.getTransactionCount()
            
            if (totalTransactions == 0) {
                return@withContext true
            }
            
            // Check if all transactions are test data
            val testMerchants = listOf(
                "test", "example", "demo", "sample", "dummy", "placeholder"
            )
            
            val allTransactions = transactionDao.getAllTransactionsSync()
            val realTransactions = allTransactions.filter { transaction ->
                val merchantLower = transaction.rawMerchant.lowercase()
                !testMerchants.any { testMerchant -> merchantLower.contains(testMerchant) }
            }
            
            val isEmpty = realTransactions.isEmpty()
            
            return@withContext isEmpty
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if database is empty", e)
            return@withContext true // Assume empty on error
        }
    }
    
    // =======================
    // FAST DASHBOARD QUERIES
    // =======================
    
    override suspend fun getDashboardData(startDate: Date, endDate: Date): DashboardData {
        // Get raw data counts from database
        val allTransactions = getTransactionsByDateRange(startDate, endDate)
        val rawTransactionCount = allTransactions.size
        
        val totalSpent = getTotalSpent(startDate, endDate)
        val transactionCount = getTransactionCount(startDate, endDate)  // This applies filtering
        val categorySpending = getCategorySpending(startDate, endDate)
        
        Log.d(TAG, "📊 [DASHBOARD] $transactionCount transactions, ₹$totalSpent total (filtered from $rawTransactionCount raw)")
        
        if (rawTransactionCount > 0 && transactionCount == 0) {
            Log.w(TAG, "⚠️ All $rawTransactionCount transactions were filtered out - check exclusion settings")
        }
        
        // FIXED: Request more merchants to ensure consistent display after exclusion filtering
        val topMerchants = getTopMerchants(startDate, endDate, 8) // Request 8 to get at least 5 after filtering
        
        return DashboardData(
            totalSpent = totalSpent,
            transactionCount = transactionCount,
            topCategories = categorySpending.take(6),
            topMerchants = topMerchants.take(5) // Always take exactly 5 for consistent display
        )
    }
    
    // =======================
    // UTILITY METHODS
    // =======================
    
    suspend fun convertToTransactionEntity(parsedTransaction: ParsedTransaction): TransactionEntity {
        val normalizedMerchant = normalizeMerchantName(parsedTransaction.merchant)
        
        // Ensure merchant exists in the merchants table with proper category
        ensureMerchantExists(normalizedMerchant, parsedTransaction.merchant)
        
        val transactionEntity = TransactionEntity(
            smsId = parsedTransaction.id,
            amount = parsedTransaction.amount,
            rawMerchant = parsedTransaction.merchant,
            normalizedMerchant = normalizedMerchant,
            bankName = parsedTransaction.bankName,
            transactionDate = parsedTransaction.date, // CRITICAL: Using SMS timestamp, not current time
            rawSmsBody = parsedTransaction.rawSMS,
            confidenceScore = parsedTransaction.confidence,
            isDebit = true, // Assuming debit transactions for now
            createdAt = Date(), // Record creation time (for debugging)
            updatedAt = Date()
        )
        
        return transactionEntity
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
        // Use same logic as MerchantAliasManager for consistency
        return merchant.uppercase()
            .replace(Regex("[*#@\\-_]+.*"), "") // Remove suffixes after special chars
            .replace(Regex("\\s+"), " ") // Normalize spaces
            .trim()
    }
    
    override suspend fun initializeDefaultData() = withContext(Dispatchers.IO) {
        // Check if categories already exist
        val existingCategories = categoryDao.getAllCategoriesSync()
        if (existingCategories.isEmpty()) {
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
            // FIXED: Unified filtering system - check both database exclusions AND SharedPreferences inclusion states
            
            // 1. Get database exclusions
            val excludedMerchants = merchantDao.getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()
            
            // 2. Get SharedPreferences inclusion states (from Messages screen toggles)
            val prefs = context.getSharedPreferences("expense_calculations", android.content.Context.MODE_PRIVATE)
            val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
            val sharedPrefsExclusions = mutableSetOf<String>()
            
            if (inclusionStatesJson != null) {
                try {
                    val inclusionStates = org.json.JSONObject(inclusionStatesJson)
                    val keys = inclusionStates.keys()
                    while (keys.hasNext()) {
                        val merchantName = keys.next()
                        val isIncluded = inclusionStates.getBoolean(merchantName)
                        if (!isIncluded) {
                            // Convert display name to normalized name for comparison
                            val normalizedName = normalizeMerchantName(merchantName)
                            sharedPrefsExclusions.add(normalizedName)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing SharedPreferences inclusion states", e)
                }
            }
            
            Log.d(TAG, "[DEBUG] UNIFIED FILTERING: ${transactions.size} transactions")
            Log.d(TAG, "[DEBUG]   - Database exclusions: ${excludedNormalizedNames.size}")
            Log.d(TAG, "[DEBUG]   - SharedPrefs exclusions: ${sharedPrefsExclusions.size}")
            
            // 3. Filter transactions using BOTH exclusion systems
            val filteredTransactions = transactions.filter { transaction ->
                val isExcludedInDatabase = excludedNormalizedNames.contains(transaction.normalizedMerchant)
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(transaction.normalizedMerchant) ||
                                             sharedPrefsExclusions.contains(transaction.rawMerchant.lowercase())
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                isIncluded
            }
            
            Log.d(TAG, "[SUCCESS] UNIFIED FILTERING result: ${filteredTransactions.size}/${transactions.size} transactions included")
            
            filteredTransactions
        } catch (e: Exception) {
            Log.e(TAG, "Error in unified filtering", e)
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
     * Debug helper to get current exclusion states from BOTH systems
     */
    override suspend fun getExclusionStatesDebugInfo(): String {
        return try {
            // 1. Database exclusions
            val databaseExcluded = getExcludedMerchants()
            val dbExcludedNames = databaseExcluded.map { "'${it.displayName}'" }
            
            // 2. SharedPreferences exclusions
            val prefs = context.getSharedPreferences("expense_calculations", android.content.Context.MODE_PRIVATE)
            val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
            val sharedPrefsExcluded = mutableListOf<String>()
            
            if (inclusionStatesJson != null) {
                try {
                    val inclusionStates = org.json.JSONObject(inclusionStatesJson)
                    val keys = inclusionStates.keys()
                    while (keys.hasNext()) {
                        val merchantName = keys.next()
                        val isIncluded = inclusionStates.getBoolean(merchantName)
                        if (!isIncluded) {
                            sharedPrefsExcluded.add("'$merchantName'")
                        }
                    }
                } catch (e: Exception) {
                    // Ignore JSON parsing errors
                }
            }
            
            val result = buildString {
                appendLine("UNIFIED EXCLUSION SYSTEM STATUS:")
                appendLine("[STATS] Database exclusions (${dbExcludedNames.size}): ${if (dbExcludedNames.isEmpty()) "None" else dbExcludedNames.joinToString(", ")}")
                append("[STATS] SharedPrefs exclusions (${sharedPrefsExcluded.size}): ${if (sharedPrefsExcluded.isEmpty()) "None" else sharedPrefsExcluded.joinToString(", ")}")
            }
            
            result
        } catch (e: Exception) {
            "Error loading unified exclusion states: ${e.message}"
        }
    }

    private suspend fun filterMerchantsByExclusions(merchants: List<com.expensemanager.app.data.dao.MerchantSpending>): List<com.expensemanager.app.data.dao.MerchantSpending> {
        return try {
            // FIXED: Apply same unified filtering to merchant results
            
            // 1. Get database exclusions
            val excludedMerchants = getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()
            
            // 2. Get SharedPreferences exclusions
            val prefs = context.getSharedPreferences("expense_calculations", android.content.Context.MODE_PRIVATE)
            val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
            val sharedPrefsExclusions = mutableSetOf<String>()
            
            if (inclusionStatesJson != null) {
                try {
                    val inclusionStates = org.json.JSONObject(inclusionStatesJson)
                    val keys = inclusionStates.keys()
                    while (keys.hasNext()) {
                        val merchantName = keys.next()
                        val isIncluded = inclusionStates.getBoolean(merchantName)
                        if (!isIncluded) {
                            val normalizedName = normalizeMerchantName(merchantName)
                            sharedPrefsExclusions.add(normalizedName)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing SharedPreferences for merchants", e)
                }
            }
            
            Log.d(TAG, "[DEBUG] UNIFIED MERCHANT FILTERING: ${merchants.size} merchants")
            Log.d(TAG, "[DEBUG]   - Database exclusions: ${excludedNormalizedNames.size}")
            Log.d(TAG, "[DEBUG]   - SharedPrefs exclusions: ${sharedPrefsExclusions.size}")
            
            // 3. Filter merchants using unified system
            val filteredMerchants = merchants.filter { merchant ->
                val isExcludedInDatabase = excludedNormalizedNames.contains(merchant.normalized_merchant)
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(merchant.normalized_merchant)
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                isIncluded
            }
            
            Log.d(TAG, "[SUCCESS] UNIFIED MERCHANT FILTERING result: ${filteredMerchants.size}/${merchants.size} merchants included")
            filteredMerchants
        } catch (e: Exception) {
            Log.e(TAG, "Error in unified merchant filtering", e)
            merchants // Return all merchants on error
        }
    }
    
    // =======================
    // DATABASE MAINTENANCE
    // =======================
    
    override suspend fun cleanupDuplicateTransactions(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🧹 Starting enhanced database cleanup for duplicate transactions...")
            
            // Get all transactions grouped by potential duplicate key
            val allTransactions = transactionDao.getAllTransactionsSync()
            val duplicateGroups = allTransactions.groupBy { transaction ->
                // FIXED: Use the standardized deduplication key
                TransactionEntity.generateDeduplicationKey(
                    merchant = transaction.rawMerchant,
                    amount = transaction.amount,
                    date = transaction.transactionDate,
                    bankName = transaction.bankName
                )
            }.filter { it.value.size > 1 }
            
            var removedCount = 0
            
            for ((key, duplicates) in duplicateGroups) {
                Log.d(TAG, "[DEBUG] Found ${duplicates.size} potential duplicates for key: $key")
                
                // Keep the one with the highest confidence score, or most recent if tied
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
                    Log.d(TAG, "[DELETE] Removed duplicate transaction: ${duplicate.rawMerchant} - ₹${duplicate.amount} (SMS: ${duplicate.smsId})")
                }
            }
            
            Log.d(TAG, "[SUCCESS] Enhanced database cleanup completed. Removed $removedCount duplicate transactions")
            removedCount
            
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] Database cleanup failed", e)
            0
        }
    }
    
    override suspend fun removeObviousTestData(): Int = withContext(Dispatchers.IO) {
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
                    Log.d(TAG, "[DELETE] Removed test transaction: ${transaction.rawMerchant} - ₹${transaction.amount}")
                }
            }
            
            Log.d(TAG, "[SUCCESS] Test data cleanup completed. Removed $removedCount test transactions")
            removedCount
            
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] Test data cleanup failed", e)
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