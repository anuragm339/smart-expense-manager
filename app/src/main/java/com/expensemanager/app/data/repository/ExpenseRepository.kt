package com.expensemanager.app.data.repository

import android.content.Context
import com.expensemanager.app.data.database.ExpenseDatabase

import com.expensemanager.app.data.entities.*
import com.expensemanager.app.data.dao.*
import com.expensemanager.app.domain.repository.*
import com.expensemanager.app.services.SMSParsingService
import com.expensemanager.app.models.ParsedTransaction
import com.expensemanager.app.services.TransactionFilterService
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
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
    private val smsParsingService: SMSParsingService,
    private val transactionFilterService: TransactionFilterService? = null,
    private val logConfig: LogConfig
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
                val logConfig = LogConfig(context.applicationContext)
                val instance = ExpenseRepository(
                    context.applicationContext,
                    database.transactionDao(),
                    database.categoryDao(),
                    database.merchantDao(),
                    database.syncStateDao(),
                    smsParsingService,
                    null, // transactionFilterService (optional)
                    logConfig
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
    
    suspend fun getAllTransactionsSync(): List<TransactionEntity> {
        return transactionDao.getAllTransactionsSync()
    }
    
    override suspend fun getTransactionsByDateRange(startDate: Date, endDate: Date): List<TransactionEntity> {
        return transactionDao.getTransactionsByDateRange(startDate, endDate)
    }
    
    // New method for expense-specific transactions (debit only)
    suspend fun getExpenseTransactionsByDateRange(startDate: Date, endDate: Date): List<TransactionEntity> {
        return transactionDao.getExpenseTransactionsByDateRange(startDate, endDate)
    }
    
    override suspend fun getCategorySpending(startDate: Date, endDate: Date): List<CategorySpendingResult> {
        // OPTIMIZED: Use direct SQL query instead of multiple separate operations for better performance
        return try {
            transactionDao.getCategorySpendingBreakdown(startDate, endDate)
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).w(e, "Optimized query failed, falling back to original method")
            
            // Fallback to original method
            val expenseTransactions = getExpenseTransactionsByDateRange(startDate, endDate)
            val filteredTransactions = filterTransactionsByExclusions(expenseTransactions)
            
            // Group transactions by category and calculate totals with improved efficiency
            val categoryTotals = mutableMapOf<String, Triple<Double, Int, Date?>>()
            val merchantCategoryCache = mutableMapOf<String, String>()
            
            for (transaction in filteredTransactions) {
                // Cache merchant category lookups to reduce database hits
                val categoryName = merchantCategoryCache.getOrPut(transaction.normalizedMerchant) {
                    getMerchantWithCategory(transaction.normalizedMerchant)?.category_name ?: "Other"
                }
                
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
            
            // Batch category lookups for better performance
            val categoryNames = categoryTotals.keys.toList()
            val categories = mutableListOf<CategoryEntity>()
            for (name in categoryNames) {
                val category = categoryDao.getCategoryByName(name)
                if (category != null) {
                    categories.add(category)
                }
            }
            val categoryMap = categories.associateBy { it.name }
            
            // Convert to CategorySpendingResult objects
            categoryTotals.map { (categoryName, data) ->
                val category = categoryMap[categoryName]
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
    }
    
    override suspend fun getTopMerchants(startDate: Date, endDate: Date, limit: Int): List<MerchantSpending> {
        val allMerchantsWithCategory = transactionDao.getTopMerchantsBySpending(startDate, endDate, limit * 2) // Get more to account for filtering
        val filteredMerchantsWithCategory = filterMerchantsByExclusionsWithCategory(allMerchantsWithCategory)
        
        // Convert to old MerchantSpending format for backward compatibility
        return filteredMerchantsWithCategory.take(limit).map { merchantWithCategory ->
            com.expensemanager.app.data.dao.MerchantSpending(
                normalized_merchant = merchantWithCategory.normalized_merchant,
                total_amount = merchantWithCategory.total_amount,
                transaction_count = merchantWithCategory.transaction_count
            )
        }
    }
    
    override suspend fun getTopMerchantsWithCategory(startDate: Date, endDate: Date, limit: Int): List<com.expensemanager.app.data.dao.MerchantSpendingWithCategory> {
        val allMerchantsWithCategory = transactionDao.getTopMerchantsBySpending(startDate, endDate, limit * 2) // Get more to account for filtering
        return filterMerchantsByExclusionsWithCategory(allMerchantsWithCategory).take(limit)
    }
    
    override suspend fun getTotalSpent(startDate: Date, endDate: Date): Double {
        // FIXED: Use expense-specific transactions (debit only) instead of all transactions
        val expenseTransactions = getExpenseTransactionsByDateRange(startDate, endDate)
        val filteredTransactions = filterTransactionsByExclusions(expenseTransactions)
        return filteredTransactions.sumOf { it.amount }
    }
    
    /**
     * Get total credits/income for balance calculation
     */
    suspend fun getTotalCredits(startDate: Date, endDate: Date): Double {
        return transactionDao.getTotalCreditsOrIncomeByDateRange(startDate, endDate) ?: 0.0
    }
    
    /**
     * Calculate actual balance: Credits - Debits
     */
    suspend fun getActualBalance(startDate: Date, endDate: Date): Double {
        val totalCredits = getTotalCredits(startDate, endDate)
        val totalDebits = getTotalSpent(startDate, endDate)
        return totalCredits - totalDebits
    }
    
    /**
     * Get the most recent salary transaction
     */
    suspend fun getLastSalaryTransaction(): TransactionEntity? {
        return transactionDao.getLastSalaryTransaction()
    }
    
    /**
     * Get salary transactions with optional minimum amount filter
     */
    suspend fun getSalaryTransactions(minAmount: Double = 10000.0, limit: Int = 10): List<TransactionEntity> {
        return transactionDao.getSalaryTransactions(minAmount, limit)
    }
    
    /**
     * Calculate monthly balance: Last Salary - Current Month's Expenses
     * This provides a meaningful monthly budget view
     */
    suspend fun getMonthlyBudgetBalance(currentMonthStartDate: Date, currentMonthEndDate: Date): MonthlyBalanceInfo {
        // Debug: Check all credit transactions first
        val allCredits = transactionDao.getTransactionsByDateRange(Date(0), Date())
            .filter { !it.isDebit }
        allCredits.take(5).forEach { credit ->
        }
        
        // Get the most recent salary transaction (could be from any month)
        var lastSalary = getLastSalaryTransaction()
        
        // Fallback: If no explicit salary found, try to use the largest credit transaction as potential salary
        if (lastSalary == null && allCredits.isNotEmpty()) {
            lastSalary = allCredits.maxByOrNull { it.amount }
        }
        
        // Get current month's expenses (debits only)
        val currentMonthExpenses = getTotalSpent(currentMonthStartDate, currentMonthEndDate)
        
        // Calculate balance
        val salaryAmount = lastSalary?.amount ?: 0.0
        val remainingBalance = salaryAmount - currentMonthExpenses
        
        
        return MonthlyBalanceInfo(
            lastSalaryAmount = salaryAmount,
            lastSalaryDate = lastSalary?.transactionDate,
            currentMonthExpenses = currentMonthExpenses,
            remainingBalance = remainingBalance,
            hasSalaryData = lastSalary != null
        )
    }
    
    override suspend fun getTransactionCount(startDate: Date, endDate: Date): Int {
        // FIXED: Use expense-specific transactions (debit only) instead of all transactions
        val expenseTransactions = getExpenseTransactionsByDateRange(startDate, endDate)
        val filteredTransactions = filterTransactionsByExclusions(expenseTransactions)
        return filteredTransactions.size
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
        try {
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("[EXCLUSION] Updating merchant exclusion: '$normalizedMerchantName' -> $isExcluded")
            
            merchantDao.updateMerchantExclusion(normalizedMerchantName, isExcluded)
            
            // CRITICAL FIX: Broadcast data change to notify dependent components
            val intent = android.content.Intent("com.expensemanager.app.DATA_CHANGED")
            context.sendBroadcast(intent)
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).i("[EXCLUSION] Successfully updated exclusion and broadcast data change")
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "[EXCLUSION] Failed to update merchant exclusion for '$normalizedMerchantName'")
            throw e
        }
    }
    
    suspend fun updateMerchantsByCategory(oldCategoryId: Long, newCategoryId: Long): Int {
        return merchantDao.updateMerchantsByCategory(oldCategoryId, newCategoryId)
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
                    Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Failed to create category: $newCategoryName")
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
                    Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Failed to process merchant '$originalName'")
                    failedUpdates.add(originalName)
                }
            }
            
            // Final status report
            val totalProcessed = updatedCount + createdCount
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).i("Updated %d merchants, created %d merchants", updatedCount, createdCount)
            
            if (failedUpdates.isNotEmpty()) {
                Timber.tag(LogConfig.FeatureTags.DATABASE).w("Failed to update %d merchants: %s", failedUpdates.size, failedUpdates)
            }
            
            // Consider it successful if we updated at least some merchants
            val isSuccessful = totalProcessed > 0 && failedUpdates.size < originalMerchantNames.size
            
            return isSuccessful
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Critical error during database update")
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
            
            val syncState = syncStateDao.getSyncState()
            val lastSyncTimestamp = syncState?.lastSmsSyncTimestamp ?: Date(0)
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("Starting SMS sync - Last sync timestamp: %s", lastSyncTimestamp)
            
            syncStateDao.updateSyncStatus("IN_PROGRESS")
            
            // UNIFIED: Use SMSParsingService instead of duplicate parsing logic
            val allTransactions = smsParsingService.scanHistoricalSMS { current, total, status ->
            }
            
            val newTransactions = allTransactions.filter { 
                it.date.after(lastSyncTimestamp) 
            }
            
            
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
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("Found %d new transactions to process", newTransactions.size)
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("SMS sync completed - Inserted: %d, Duplicates: %d, Unique merchants: %d", insertedCount, duplicateCount, distinctMerchants.size)
            insertedCount
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "[UNIFIED] Repository-based SMS sync failed")
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
            Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Error checking if database is empty")
            return@withContext true // Assume empty on error
        }
    }
    
    // =======================
    // FAST DASHBOARD QUERIES
    // =======================
    
    override suspend fun getDashboardData(startDate: Date, endDate: Date): DashboardData {
        Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DASHBOARD] Loading dashboard data for range: %s to %s", startDate, endDate)
        
        // Get raw data counts from database (all transactions including credits)
        val allTransactions = getTransactionsByDateRange(startDate, endDate)
        val rawTransactionCount = allTransactions.size
        val rawCreditCount = allTransactions.count { !it.isDebit }
        val rawDebitCount = allTransactions.count { it.isDebit }
        
        // DEBUG: Get expense transactions BEFORE filtering 
        val expenseTransactionsBeforeFilter = getExpenseTransactionsByDateRange(startDate, endDate)
        Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG] Raw debit transactions (before filtering): %d", expenseTransactionsBeforeFilter.size)
        expenseTransactionsBeforeFilter.take(3).forEach { transaction ->
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG] Debit example: %s - ₹%.2f - isDebit: %b", transaction.rawMerchant, transaction.amount, transaction.isDebit)
        }
        
        // CRITICAL FIX: Apply exclusion filtering consistently
        val totalSpent = getTotalSpent(startDate, endDate)
        val transactionCount = getTransactionCount(startDate, endDate)  // This applies filtering to debits only
        val categorySpending = getCategorySpending(startDate, endDate)
        
        // BALANCE FIX: Calculate credits and actual balance
        val totalCredits = getTotalCredits(startDate, endDate)
        val actualBalance = getActualBalance(startDate, endDate)
        
        Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DASHBOARD] Calculated totals - Spent: ₹%.2f, Credits: ₹%.2f, Count: %d", totalSpent, totalCredits, transactionCount)
        
        if (rawDebitCount > 0 && transactionCount == 0) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).w("WARNING: Raw debit transactions found (%d) but none remain after filtering (%d)", rawDebitCount, transactionCount)
            
            // DEBUG: Show examples of excluded transactions
            expenseTransactionsBeforeFilter.take(3).forEach { transaction ->
                Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG] Potentially excluded: %s", transaction.rawMerchant)
            }
            
            // DEBUG: Get exclusion states
            if (transactionFilterService != null) {
                val exclusionDebugInfo = transactionFilterService.getExclusionStatesDebugInfo()
                Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG] Exclusion states: %s", exclusionDebugInfo)
            }
        }
        
        if (rawCreditCount > 0) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG] Found %d credit transactions totaling ₹%.2f", rawCreditCount, totalCredits)
        }
        
        // FIXED: Request more merchants to ensure consistent display after exclusion filtering
        val topMerchants = getTopMerchants(startDate, endDate, 8) // Request 8 to get at least 5 after filtering
        val topMerchantsWithCategory = getTopMerchantsWithCategory(startDate, endDate, 8) // Get merchants with category info
        
        // Calculate monthly balance (Last Salary - Current Period Expenses)
        val monthlyBalance = getMonthlyBudgetBalance(startDate, endDate)
        
        val dashboardData = DashboardData(
            totalSpent = totalSpent,
            totalCredits = totalCredits,
            actualBalance = actualBalance,
            transactionCount = transactionCount,
            topCategories = categorySpending.take(6),
            topMerchants = topMerchants.take(5), // Always take exactly 5 for consistent display (backward compatibility)
            topMerchantsWithCategory = topMerchantsWithCategory.take(5), // New field with category information
            monthlyBalance = monthlyBalance
        )
        
        Timber.tag(LogConfig.FeatureTags.DATABASE).i("[DASHBOARD] Dashboard data loaded successfully - Spent: ₹%.2f, Transactions: %d, Categories: %d", 
            dashboardData.totalSpent, dashboardData.transactionCount, dashboardData.topCategories.size)
        
        return dashboardData
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
            isDebit = parsedTransaction.isDebit, // Use classification from SMS parsing
            createdAt = Date(), // Record creation time (for debugging)
            updatedAt = Date()
        )
        
        Timber.tag(LogConfig.FeatureTags.DATABASE).d("[CONVERSION] %s: %s - ₹%.2f", if (parsedTransaction.isDebit) "DEBIT" else "CREDIT", parsedTransaction.merchant, parsedTransaction.amount)
        
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
        // Updated to match MerchantAliasManager normalization for consistency
        val cleaned = merchant.uppercase()
            .replace(Regex("\\s+"), " ") // Normalize spaces first
            .trim()
        
        // Use same logic as MerchantAliasManager - less aggressive normalization
        return cleaned
            .replace(Regex("\\*(ORDER|PAYMENT|TXN|TRANSACTION).*$"), "") // Remove order/payment suffixes
            .replace(Regex("#\\d+.*$"), "") // Remove transaction numbers
            .replace(Regex("@\\w+.*$"), "") // Remove @ suffixes
            .replace(Regex("-{2,}.*$"), "") // Remove double dashes and everything after
            .replace(Regex("_{2,}.*$"), "") // Remove double underscores and everything after
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
            if (transactionFilterService != null) {
                // UPDATED: Use unified TransactionFilterService for consistent exclusion logic
                Timber.tag(LogConfig.FeatureTags.DATABASE).d("UNIFIED FILTERING: %d transactions before filtering", transactions.size)
                val filteredTransactions = transactionFilterService.filterTransactionsByExclusions(transactions)
                
                Timber.tag(LogConfig.FeatureTags.DATABASE).d("UNIFIED FILTERING: %d transactions after filtering", filteredTransactions.size)
                
                filteredTransactions
            } else {
                // Fallback to legacy filtering if service not available
                Timber.tag(LogConfig.FeatureTags.DATABASE).d("LEGACY FILTERING: Using fallback filtering")
                filterTransactionsByExclusionsLegacy(transactions)
            }
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Error in unified filtering")
            transactions // Return all transactions on error
        }
    }
    
    /**
     * Legacy filtering method for backwards compatibility
     */
    private suspend fun filterTransactionsByExclusionsLegacy(transactions: List<TransactionEntity>): List<TransactionEntity> {
        return try {
            // Legacy filtering system - check both database exclusions AND SharedPreferences inclusion states
            
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
                    Timber.tag(LogConfig.FeatureTags.DATABASE).w("Error parsing inclusion states: %s", e.message)
                }
            }
            
            // 3. Filter transactions using BOTH exclusion systems
            val filteredTransactions = transactions.filter { transaction ->
                val isExcludedInDatabase = excludedNormalizedNames.contains(transaction.normalizedMerchant)
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(transaction.normalizedMerchant) ||
                                             sharedPrefsExclusions.contains(transaction.rawMerchant.lowercase())
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                isIncluded
            }
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("Legacy filtering: %d -> %d transactions (excluded %d from DB, %d from SharedPrefs)", transactions.size, filteredTransactions.size, excludedNormalizedNames.size, sharedPrefsExclusions.size)
            
            filteredTransactions
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Error in legacy filtering")
            transactions
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
     * Debug helper to get current exclusion states from unified service
     */
    override suspend fun getExclusionStatesDebugInfo(): String {
        return try {
            if (transactionFilterService != null) {
                // UPDATED: Use unified TransactionFilterService for exclusion states debug info
                transactionFilterService.getExclusionStatesDebugInfo()
            } else {
                // Fallback to legacy debug info
                "TransactionFilterService not available - using legacy repository"
            }
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
                    Timber.tag(LogConfig.FeatureTags.DATABASE).w("Error parsing inclusion states: %s", e.message)
                }
            }
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG] UNIFIED MERCHANT FILTERING: %d merchants", merchants.size)
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG]   - Database exclusions: %d", excludedNormalizedNames.size)
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG]   - SharedPrefs exclusions: %d", sharedPrefsExclusions.size)
            
            // 3. Filter merchants using unified system
            val filteredMerchants = merchants.filter { merchant ->
                val isExcludedInDatabase = excludedNormalizedNames.contains(merchant.normalized_merchant)
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(merchant.normalized_merchant)
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                isIncluded
            }
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("Filtered merchants: %d -> %d", merchants.size, filteredMerchants.size)
            filteredMerchants
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Error in unified merchant filtering")
            merchants // Return all merchants on error
        }
    }

    private suspend fun filterMerchantsByExclusionsWithCategory(merchants: List<com.expensemanager.app.data.dao.MerchantSpendingWithCategory>): List<com.expensemanager.app.data.dao.MerchantSpendingWithCategory> {
        return try {
            // Apply same unified filtering to merchant results with category information
            
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
                    Timber.tag(LogConfig.FeatureTags.DATABASE).w("Error parsing inclusion states: %s", e.message)
                }
            }
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG] UNIFIED MERCHANT WITH CATEGORY FILTERING: %d merchants", merchants.size)
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG]   - Database exclusions: %d", excludedNormalizedNames.size)
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("[DEBUG]   - SharedPrefs exclusions: %d", sharedPrefsExclusions.size)
            
            // 3. Filter merchants using unified system
            val filteredMerchants = merchants.filter { merchant ->
                val isExcludedInDatabase = excludedNormalizedNames.contains(merchant.normalized_merchant)
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(merchant.normalized_merchant)
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                isIncluded
            }
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("Filtered merchants with category: %d -> %d", merchants.size, filteredMerchants.size)
            filteredMerchants
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Error in unified merchant with category filtering")
            merchants // Return all merchants on error
        }
    }
    
    // =======================
    // DATABASE MAINTENANCE
    // =======================
    
    override suspend fun cleanupDuplicateTransactions(): Int = withContext(Dispatchers.IO) {
        try {
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("Starting duplicate cleanup")
            
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
                Timber.tag(LogConfig.FeatureTags.DATABASE).d("Found duplicate group with key: %s (%d transactions)", key, duplicates.size)
                
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
                    Timber.tag(LogConfig.FeatureTags.DATABASE).d("Removing duplicate transaction: %s", duplicate.rawMerchant)
                }
            }
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("Duplicate cleanup completed - Removed %d transactions", removedCount)
            removedCount
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "[ERROR] Database cleanup failed")
            0
        }
    }
    
    override suspend fun removeObviousTestData(): Int = withContext(Dispatchers.IO) {
        try {
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("Starting test data cleanup")
            
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
                    Timber.tag(LogConfig.FeatureTags.DATABASE).d("Removing test transaction: %s - ₹%.2f", transaction.rawMerchant, transaction.amount)
                }
            }
            
            Timber.tag(LogConfig.FeatureTags.DATABASE).d("Test data cleanup completed - Removed %d transactions", removedCount)
            removedCount
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "[ERROR] Test data cleanup failed")
            0
        }
    }

    // =======================
    // CATEGORY DETAIL METHODS
    // =======================

    /**
     * Get all merchants within a specific category with their transaction statistics
     */
    suspend fun getMerchantsInCategory(categoryName: String): List<com.expensemanager.app.ui.categories.MerchantInCategory> {
        return withContext(Dispatchers.IO) {
            try {
                val category = getCategoryByName(categoryName)
                if (category == null) {
                    Timber.tag(LogConfig.FeatureTags.DATABASE).w("Category not found: %s", categoryName)
                    return@withContext emptyList()
                }

                // Get all merchants in this category with their transaction data
                val merchantsWithStats = transactionDao.getMerchantsInCategoryWithStats(category.id)

                // Calculate total spending for percentage calculation
                val totalCategorySpending = merchantsWithStats.sumOf { it.totalAmount }

                // Convert to MerchantInCategory objects
                merchantsWithStats.map { merchantStats ->
                    val percentage = if (totalCategorySpending > 0) {
                        ((merchantStats.totalAmount / totalCategorySpending) * 100).toFloat()
                    } else 0f

                    com.expensemanager.app.ui.categories.MerchantInCategory(
                        merchantName = merchantStats.displayName,
                        transactionCount = merchantStats.transactionCount,
                        totalAmount = merchantStats.totalAmount,
                        lastTransactionDate = merchantStats.lastTransactionDate,
                        currentCategory = categoryName,
                        percentage = percentage
                    )
                }.sortedByDescending { it.totalAmount } // Sort by spending amount

            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Failed to get merchants in category: %s", categoryName)
                emptyList()
            }
        }
    }

    /**
     * Change a merchant's category
     */
    suspend fun changeMerchantCategory(merchantName: String, newCategoryName: String, applyToFuture: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get the new category
                val newCategory = getCategoryByName(newCategoryName)
                if (newCategory == null) {
                    Timber.tag(LogConfig.FeatureTags.DATABASE).w("Target category not found: %s", newCategoryName)
                    return@withContext false
                }

                // Get the merchant by normalized name
                val normalizedMerchantName = normalizeMerchantName(merchantName)
                val merchant = getMerchantByNormalizedName(normalizedMerchantName)
                if (merchant == null) {
                    Timber.tag(LogConfig.FeatureTags.DATABASE).w("Merchant not found: %s", merchantName)
                    return@withContext false
                }

                // Update the merchant's category
                val updatedMerchant = merchant.copy(
                    categoryId = newCategory.id
                )
                merchantDao.updateMerchant(updatedMerchant)

                // If applyToFuture is false, we might need to update existing transactions
                // For now, we'll keep the existing logic simple and just update the merchant

                Timber.tag(LogConfig.FeatureTags.DATABASE).d("Changed merchant %s to category %s", merchantName, newCategoryName)
                true

            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Failed to change merchant category: %s -> %s", merchantName, newCategoryName)
                false
            }
        }
    }

    /**
     * Rename a category (for custom categories)
     */
    suspend fun renameCategory(oldName: String, newName: String, newEmoji: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val category = getCategoryByName(oldName)
                if (category == null) {
                    Timber.tag(LogConfig.FeatureTags.DATABASE).w("Category not found for rename: %s", oldName)
                    return@withContext false
                }

                // Check if new name already exists
                val existingCategory = getCategoryByName(newName)
                if (existingCategory != null && existingCategory.id != category.id) {
                    Timber.tag(LogConfig.FeatureTags.DATABASE).w("Category name already exists: %s", newName)
                    return@withContext false
                }

                // Update the category
                val updatedCategory = category.copy(
                    name = newName,
                    emoji = newEmoji
                )
                categoryDao.updateCategory(updatedCategory)

                Timber.tag(LogConfig.FeatureTags.DATABASE).d("Renamed category %s to %s", oldName, newName)
                true

            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Failed to rename category: %s -> %s", oldName, newName)
                false
            }
        }
    }

    /**
     * Delete a category and reassign its merchants to another category
     */
    suspend fun deleteCategory(categoryName: String, reassignToCategoryName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val categoryToDelete = getCategoryByName(categoryName)
                val reassignCategory = getCategoryByName(reassignToCategoryName)

                if (categoryToDelete == null || reassignCategory == null) {
                    Timber.tag(LogConfig.FeatureTags.DATABASE).w("Category not found for deletion or reassignment")
                    return@withContext false
                }

                // Check if it's a system category (shouldn't be deletable)
                if (com.expensemanager.app.constants.Categories.isSystemCategory(categoryName)) {
                    Timber.tag(LogConfig.FeatureTags.DATABASE).w("Cannot delete system category: %s", categoryName)
                    return@withContext false
                }

                // Reassign all merchants to the new category
                val merchantsInCategory = merchantDao.getMerchantsByCategory(categoryToDelete.id)
                merchantsInCategory.forEach { merchant ->
                    val updatedMerchant = merchant.copy(
                        categoryId = reassignCategory.id
                    )
                    merchantDao.updateMerchant(updatedMerchant)
                }

                // Delete the category
                categoryDao.deleteCategory(categoryToDelete)

                Timber.tag(LogConfig.FeatureTags.DATABASE).d("Deleted category %s and reassigned %d merchants to %s",
                    categoryName, merchantsInCategory.size, reassignToCategoryName)
                true

            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.DATABASE).e(e, "Failed to delete category: %s", categoryName)
                false
            }
        }
    }

    /**
     * Check if a category is a system category
     */
    suspend fun isSystemCategory(categoryName: String): Boolean {
        return com.expensemanager.app.constants.Categories.isSystemCategory(categoryName)
    }
}

// Data classes for repository responses
data class DashboardData(
    val totalSpent: Double,
    val totalCredits: Double,
    val actualBalance: Double,
    val transactionCount: Int,
    val topCategories: List<CategorySpendingResult>,
    val topMerchants: List<MerchantSpending>, // Kept for backward compatibility
    val topMerchantsWithCategory: List<com.expensemanager.app.data.dao.MerchantSpendingWithCategory>, // New field with category info
    val monthlyBalance: MonthlyBalanceInfo
)

data class MonthlyBalanceInfo(
    val lastSalaryAmount: Double,
    val lastSalaryDate: Date?,
    val currentMonthExpenses: Double,
    val remainingBalance: Double,
    val hasSalaryData: Boolean
)