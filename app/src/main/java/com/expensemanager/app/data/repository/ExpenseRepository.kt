package com.expensemanager.app.data.repository

import android.content.Context
import com.expensemanager.app.data.database.ExpenseDatabase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.expensemanager.app.data.entities.*
import com.expensemanager.app.data.dao.*
import com.expensemanager.app.domain.repository.*
import com.expensemanager.app.services.SMSParsingService
import com.expensemanager.app.models.ParsedTransaction
import com.expensemanager.app.services.TransactionFilterService
import com.expensemanager.app.utils.AppLogger
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
    private val appLogger: AppLogger
) : TransactionRepositoryInterface, 
    CategoryRepositoryInterface, 
    MerchantRepositoryInterface, 
    DashboardRepositoryInterface {
    
    companion object {
        private const val TAG = "ExpenseRepository"
        private val logger: Logger = LoggerFactory.getLogger(TAG)
        
        @Volatile
        private var INSTANCE: ExpenseRepository? = null
        
        // Temporary getInstance method for compatibility - TODO: Remove after migration to Hilt
        fun getInstance(context: Context): ExpenseRepository {
            return INSTANCE ?: synchronized(this) {
                val database = ExpenseDatabase.getDatabase(context)
                val smsParsingService = SMSParsingService(context.applicationContext)
                val appLogger = AppLogger(context.applicationContext)
                val instance = ExpenseRepository(
                    context.applicationContext,
                    database.transactionDao(),
                    database.categoryDao(),
                    database.merchantDao(),
                    database.syncStateDao(),
                    smsParsingService,
                    null, // transactionFilterService (optional)
                    appLogger
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
            logger.warn("Optimized query failed, falling back to original method", e)
            
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
        merchantDao.updateMerchantExclusion(normalizedMerchantName, isExcluded)
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
                    logger.error("Failed to create category: $newCategoryName", e)
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
                    logger.error("Failed to process merchant '$originalName'", e)
                    failedUpdates.add(originalName)
                }
            }
            
            // Final status report
            val totalProcessed = updatedCount + createdCount
            
            logger.info("Merchant alias update: $totalProcessed/${originalMerchantNames.size} processed ($updatedCount updated, $createdCount created)")
            
            if (failedUpdates.isNotEmpty()) {
                logger.warn("Failed to update ${failedUpdates.size} merchants: $failedUpdates")
            }
            
            // Consider it successful if we updated at least some merchants
            val isSuccessful = totalProcessed > 0 && failedUpdates.size < originalMerchantNames.size
            
            return isSuccessful
            
        } catch (e: Exception) {
            logger.error("Critical error during database update", e)
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
            
            logger.debug("Last sync timestamp: $lastSyncTimestamp")
            
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
            
            logger.debug("  - Distinct merchants found: ${distinctMerchants.size}")
            logger.debug("  - Merchants: ${distinctMerchants.joinToString(", ")}")
            insertedCount
            
        } catch (e: Exception) {
            logger.error("[UNIFIED] Repository-based SMS sync failed", e)
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
            logger.error("Error checking if database is empty", e)
            return@withContext true // Assume empty on error
        }
    }
    
    // =======================
    // FAST DASHBOARD QUERIES
    // =======================
    
    override suspend fun getDashboardData(startDate: Date, endDate: Date): DashboardData {
        // Get raw data counts from database (all transactions including credits)
        val allTransactions = getTransactionsByDateRange(startDate, endDate)
        val rawTransactionCount = allTransactions.size
        val rawCreditCount = allTransactions.count { !it.isDebit }
        val rawDebitCount = allTransactions.count { it.isDebit }
        
        // DEBUG: Get expense transactions BEFORE filtering 
        val expenseTransactionsBeforeFilter = getExpenseTransactionsByDateRange(startDate, endDate)
        logger.debug("[DEBUG] Raw debit transactions (before filtering): ${expenseTransactionsBeforeFilter.size}")
        expenseTransactionsBeforeFilter.take(3).forEach { transaction ->
            logger.debug("[DEBUG] Debit example: ${transaction.rawMerchant} - ₹${transaction.amount} - isDebit: ${transaction.isDebit}")
        }
        
        // Get expense-specific data (debits only)
        val totalSpent = getTotalSpent(startDate, endDate)
        val transactionCount = getTransactionCount(startDate, endDate)  // This applies filtering to debits only
        val categorySpending = getCategorySpending(startDate, endDate)
        
        // BALANCE FIX: Calculate credits and actual balance
        val totalCredits = getTotalCredits(startDate, endDate)
        val actualBalance = getActualBalance(startDate, endDate)
        
        
        if (rawDebitCount > 0 && transactionCount == 0) {
            logger.warn("All $rawDebitCount debit transactions were filtered out - check exclusion settings")
            
            // DEBUG: Show examples of excluded transactions
            expenseTransactionsBeforeFilter.take(3).forEach { transaction ->
            }
            
            // DEBUG: Get exclusion states
            if (transactionFilterService != null) {
                val exclusionDebugInfo = transactionFilterService.getExclusionStatesDebugInfo()
            }
        }
        
        if (rawCreditCount > 0) {
        }
        
        // FIXED: Request more merchants to ensure consistent display after exclusion filtering
        val topMerchants = getTopMerchants(startDate, endDate, 8) // Request 8 to get at least 5 after filtering
        val topMerchantsWithCategory = getTopMerchantsWithCategory(startDate, endDate, 8) // Get merchants with category info
        
        // Calculate monthly balance (Last Salary - Current Period Expenses)
        val monthlyBalance = getMonthlyBudgetBalance(startDate, endDate)
        
        return DashboardData(
            totalSpent = totalSpent,
            totalCredits = totalCredits,
            actualBalance = actualBalance,
            transactionCount = transactionCount,
            topCategories = categorySpending.take(6),
            topMerchants = topMerchants.take(5), // Always take exactly 5 for consistent display (backward compatibility)
            topMerchantsWithCategory = topMerchantsWithCategory.take(5), // New field with category information
            monthlyBalance = monthlyBalance
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
            isDebit = parsedTransaction.isDebit, // Use classification from SMS parsing
            createdAt = Date(), // Record creation time (for debugging)
            updatedAt = Date()
        )
        
        logger.debug("[CONVERSION] ${if (parsedTransaction.isDebit) "DEBIT" else "CREDIT"}: ${parsedTransaction.merchant} - ₹${parsedTransaction.amount}")
        
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
                logger.debug("[UNIFIED] Using TransactionFilterService for exclusion filtering")
                val filteredTransactions = transactionFilterService.filterTransactionsByExclusions(transactions)
                
                logger.debug("[SUCCESS] UNIFIED FILTERING result: ${filteredTransactions.size}/${transactions.size} transactions included")
                
                filteredTransactions
            } else {
                // Fallback to legacy filtering if service not available
                logger.debug("[FALLBACK] TransactionFilterService not available, using legacy filtering")
                filterTransactionsByExclusionsLegacy(transactions)
            }
        } catch (e: Exception) {
            logger.error("Error in unified filtering", e)
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
                    logger.warn("Error parsing SharedPreferences inclusion states", e)
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
            
            logger.debug("[LEGACY] Filtering result: ${filteredTransactions.size}/${transactions.size} transactions included")
            
            filteredTransactions
            
        } catch (e: Exception) {
            logger.error("Error in legacy filtering", e)
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
                    logger.warn("Error parsing SharedPreferences for merchants", e)
                }
            }
            
            logger.debug("[DEBUG] UNIFIED MERCHANT FILTERING: ${merchants.size} merchants")
            logger.debug("[DEBUG]   - Database exclusions: ${excludedNormalizedNames.size}")
            logger.debug("[DEBUG]   - SharedPrefs exclusions: ${sharedPrefsExclusions.size}")
            
            // 3. Filter merchants using unified system
            val filteredMerchants = merchants.filter { merchant ->
                val isExcludedInDatabase = excludedNormalizedNames.contains(merchant.normalized_merchant)
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(merchant.normalized_merchant)
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                isIncluded
            }
            
            logger.debug("[SUCCESS] UNIFIED MERCHANT FILTERING result: ${filteredMerchants.size}/${merchants.size} merchants included")
            filteredMerchants
        } catch (e: Exception) {
            logger.error("Error in unified merchant filtering", e)
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
                    logger.warn("Error parsing SharedPreferences for merchants with category", e)
                }
            }
            
            logger.debug("[DEBUG] UNIFIED MERCHANT WITH CATEGORY FILTERING: ${merchants.size} merchants")
            logger.debug("[DEBUG]   - Database exclusions: ${excludedNormalizedNames.size}")
            logger.debug("[DEBUG]   - SharedPrefs exclusions: ${sharedPrefsExclusions.size}")
            
            // 3. Filter merchants using unified system
            val filteredMerchants = merchants.filter { merchant ->
                val isExcludedInDatabase = excludedNormalizedNames.contains(merchant.normalized_merchant)
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(merchant.normalized_merchant)
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                isIncluded
            }
            
            logger.debug("[SUCCESS] UNIFIED MERCHANT WITH CATEGORY FILTERING result: ${filteredMerchants.size}/${merchants.size} merchants included")
            filteredMerchants
        } catch (e: Exception) {
            logger.error("Error in unified merchant with category filtering", e)
            merchants // Return all merchants on error
        }
    }
    
    // =======================
    // DATABASE MAINTENANCE
    // =======================
    
    override suspend fun cleanupDuplicateTransactions(): Int = withContext(Dispatchers.IO) {
        try {
            logger.debug("Starting enhanced database cleanup for duplicate transactions...")
            
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
                logger.debug("[DEBUG] Found ${duplicates.size} potential duplicates for key: $key")
                
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
                    logger.debug("[DELETE] Removed duplicate transaction: ${duplicate.rawMerchant} - ₹${duplicate.amount} (SMS: ${duplicate.smsId})")
                }
            }
            
            logger.debug("[SUCCESS] Enhanced database cleanup completed. Removed $removedCount duplicate transactions")
            removedCount
            
        } catch (e: Exception) {
            logger.error("[ERROR] Database cleanup failed", e)
            0
        }
    }
    
    override suspend fun removeObviousTestData(): Int = withContext(Dispatchers.IO) {
        try {
            logger.debug("Starting cleanup of obvious test data...")
            
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
                    logger.debug("[DELETE] Removed test transaction: ${transaction.rawMerchant} - ₹${transaction.amount}")
                }
            }
            
            logger.debug("[SUCCESS] Test data cleanup completed. Removed $removedCount test transactions")
            removedCount
            
        } catch (e: Exception) {
            logger.error("[ERROR] Test data cleanup failed", e)
            0
        }
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