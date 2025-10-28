package com.expensemanager.app.data.repository

import android.content.Context
import com.expensemanager.app.data.database.ExpenseDatabase

import com.expensemanager.app.data.entities.*
import com.expensemanager.app.data.dao.*
import com.expensemanager.app.domain.repository.*
import com.expensemanager.app.models.ParsedTransaction
import com.expensemanager.app.services.SMSParsingService
import com.expensemanager.app.services.TransactionFilterService
import com.expensemanager.app.utils.logging.StructuredLogger
import com.expensemanager.app.data.repository.internal.DatabaseMaintenanceOperations
import com.expensemanager.app.data.repository.internal.MerchantCategoryOperations
import com.expensemanager.app.data.repository.internal.TransactionDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantDao,
    private val syncStateDao: SyncStateDao,
    private val budgetDao: BudgetDao,
    private val smsParsingService: SMSParsingService,
    private val transactionFilterService: TransactionFilterService? = null
) : TransactionRepositoryInterface,
    CategoryRepositoryInterface,
    MerchantRepositoryInterface,
    DashboardRepositoryInterface {

    companion object {
        @Volatile
        private var INSTANCE: ExpenseRepository? = null

        // Temporary getInstance method for compatibility - TODO: Remove after migration to Hilt
        fun getInstance(context: Context): ExpenseRepository {
            return INSTANCE ?: synchronized(this) {
                val database = ExpenseDatabase.getDatabase(context)
                // Create dependencies for SMSParsingService
                val ruleLoader = com.expensemanager.app.parsing.engine.RuleLoader(context.applicationContext)
                val confidenceCalculator = com.expensemanager.app.parsing.engine.ConfidenceCalculator()
                val unifiedParser = com.expensemanager.app.parsing.engine.UnifiedSMSParser(ruleLoader, confidenceCalculator)
                val smsParsingService = SMSParsingService(context.applicationContext, unifiedParser)
                val instance = ExpenseRepository(
                    context.applicationContext,
                    database.transactionDao(),
                    database.categoryDao(),
                    database.merchantDao(),
                    database.syncStateDao(),
                    database.budgetDao(),
                    smsParsingService,
                    null // transactionFilterService (optional)
                )
                INSTANCE = instance
                instance
            }
        }
    }
    private val logger = StructuredLogger(
        featureTag = "DATABASE",
        className = "ExpenseRepository"
    )
    private val transactionRepository = TransactionDataRepository(
        context = context,
        transactionDao = transactionDao,
        categoryDao = categoryDao,
        merchantDao = merchantDao,
        syncStateDao = syncStateDao,
        smsParsingService = smsParsingService,
        transactionFilterService = transactionFilterService
    )
    private val merchantCategoryOperations = MerchantCategoryOperations(
        context = context,
        merchantDao = merchantDao,
        categoryDao = categoryDao,
        transactionDao = transactionDao,
        transactionRepository = transactionRepository
    )
    private val databaseMaintenanceOperations = DatabaseMaintenanceOperations(
        transactionDao = transactionDao
    )

    // =======================
    // TRANSACTION OPERATIONS
    // =======================

    override suspend fun getAllTransactions(): Flow<List<TransactionEntity>> =
        transactionRepository.transactions()

    suspend fun getAllTransactionsSync(): List<TransactionEntity> =
        transactionRepository.transactionsSync()

    override suspend fun getTransactionsByDateRange(startDate: Date, endDate: Date): List<TransactionEntity> =
        transactionRepository.transactionsBetween(startDate, endDate)

    suspend fun getTransactionsByDateRangePaginated(
        startDate: Date,
        endDate: Date,
        limit: Int,
        offset: Int
    ): List<TransactionEntity> = transactionRepository.transactionsBetween(startDate, endDate, limit, offset)

    suspend fun getTransactionCountByDateRange(startDate: Date, endDate: Date): Int =
        transactionRepository.transactionCountBetween(startDate, endDate)

    suspend fun getExpenseTransactionsByDateRange(startDate: Date, endDate: Date): List<TransactionEntity> =
        transactionRepository.expenseTransactionsBetween(startDate, endDate)

    override suspend fun getCategorySpending(startDate: Date, endDate: Date): List<CategorySpendingResult> =
        transactionRepository.categorySpending(startDate, endDate)

    override suspend fun getTopMerchants(startDate: Date, endDate: Date, limit: Int): List<MerchantSpending> =
        transactionRepository.topMerchants(startDate, endDate, limit)

    override suspend fun getTopMerchantsWithCategory(
        startDate: Date,
        endDate: Date,
        limit: Int
    ): List<MerchantSpendingWithCategory> =
        transactionRepository.topMerchantsWithCategory(startDate, endDate, limit)

    override suspend fun getTotalSpent(startDate: Date, endDate: Date): Double =
        transactionRepository.totalSpent(startDate, endDate)

    suspend fun getTotalCredits(startDate: Date, endDate: Date): Double =
        transactionRepository.totalCredits(startDate, endDate)

    suspend fun getActualBalance(startDate: Date, endDate: Date): Double =
        transactionRepository.actualBalance(startDate, endDate)

    suspend fun getLastSalaryTransaction(): TransactionEntity? =
        transactionRepository.lastSalaryTransaction()

    suspend fun getSalaryTransactions(
        minAmount: Double = 10_000.0,
        limit: Int = 10
    ): List<TransactionEntity> = transactionRepository.salaryTransactions(minAmount, limit)

    suspend fun getMonthlyBudgetBalance(
        currentMonthStartDate: Date,
        currentMonthEndDate: Date
    ): DashboardMonthlyBalance {
        val info = transactionRepository.monthlyBudgetBalance(currentMonthStartDate, currentMonthEndDate)
        return DashboardMonthlyBalance(
            lastSalaryAmount = info.lastSalaryAmount,
            lastSalaryDate = info.lastSalaryDate,
            currentMonthExpenses = info.currentMonthExpenses,
            remainingBalance = info.remainingBalance,
            hasSalaryData = info.hasSalaryData
        )
    }

    override suspend fun getTransactionCount(startDate: Date, endDate: Date): Int =
        transactionRepository.transactionCountBetween(startDate, endDate)

    override suspend fun getTransactionCount(): Int = transactionRepository.transactionCount()

    override suspend fun insertTransaction(transactionEntity: TransactionEntity): Long =
        transactionRepository.insertTransaction(transactionEntity)

    override suspend fun getTransactionBySmsId(smsId: String): TransactionEntity? =
        transactionRepository.transactionBySmsId(smsId)

    suspend fun findSimilarTransaction(entity: TransactionEntity): TransactionEntity? =
        transactionRepository.findSimilarTransaction(entity)

    override suspend fun updateSyncState(lastSyncDate: Date) {
        transactionRepository.updateSyncState(lastSyncDate)
    }

    override suspend fun searchTransactions(query: String, limit: Int): List<TransactionEntity> =
        transactionRepository.searchTransactions(query, limit)

    override suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionRepository.deleteTransaction(transaction)
    }

    override suspend fun deleteTransactionById(transactionId: Long) {
        transactionRepository.deleteTransactionById(transactionId)
    }

    override suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionRepository.updateTransaction(transaction)
    }

    override suspend fun getTransactionsByMerchant(merchantName: String): List<TransactionEntity> =
        transactionRepository.transactionsByMerchant(merchantName)

    override suspend fun syncNewSMS(): Int = transactionRepository.syncNewSms()

    override suspend fun getLastSyncTimestamp(): Date? = transactionRepository.lastSyncTimestamp()

    override suspend fun getSyncStatus(): String? = transactionRepository.syncStatus()

    suspend fun ensureSyncStateInitialized() {
        transactionRepository.ensureSyncStateInitialized()
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
        merchantCategoryOperations.updateMerchantExclusion(normalizedMerchantName, isExcluded)
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
    ): Boolean = merchantCategoryOperations.updateMerchantAliasInDatabase(
        originalMerchantNames = originalMerchantNames,
        newDisplayName = newDisplayName,
        newCategoryName = newCategoryName
    )
    
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
    
    
    // SMS parsing is now handled by unified SMSParsingService
    
    
    
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
            logger.error(
                where = "isDatabaseEmpty",
                what = "Error checking if database is empty",
                throwable = e
            )
            return@withContext true // Assume empty on error
        }
    }
    
    // =======================
    // FAST DASHBOARD QUERIES
    // =======================
    
    override suspend fun getDashboardData(startDate: Date, endDate: Date): DashboardData {
        logger.debug(
            where = "getDashboardData",
            what = "[DASHBOARD] Loading dashboard data for range: $startDate to $endDate"
        )
        
        // Get raw data counts from database (all transactions including credits)
        val allTransactions = getTransactionsByDateRange(startDate, endDate)
        val rawTransactionCount = allTransactions.size
        val rawCreditCount = allTransactions.count { !it.isDebit }
        val rawDebitCount = allTransactions.count { it.isDebit }
        
        // DEBUG: Get expense transactions BEFORE filtering 
        val expenseTransactionsBeforeFilter = getExpenseTransactionsByDateRange(startDate, endDate)
        logger.debug(
            where = "getDashboardData",
            what = "[DEBUG] Raw debit transactions (before filtering): ${expenseTransactionsBeforeFilter.size}"
        )
        expenseTransactionsBeforeFilter.take(3).forEach { transaction ->
            logger.debug(
                where = "getDashboardData",
                what = "[DEBUG] Debit example: ${transaction.rawMerchant} - ${"₹%.2f".format(transaction.amount)} - isDebit: ${transaction.isDebit}"
            )
        }
        
        // CRITICAL FIX: Apply exclusion filtering consistently
        val totalSpent = getTotalSpent(startDate, endDate)
        val transactionCount = getTransactionCount(startDate, endDate)  // This applies filtering to debits only
        val categorySpending = getCategorySpending(startDate, endDate)
        
        // BALANCE FIX: Calculate credits and actual balance
        val totalCredits = getTotalCredits(startDate, endDate)
        val actualBalance = getActualBalance(startDate, endDate)
        
        logger.debug(
            where = "getDashboardData",
            what = "[DASHBOARD] Calculated totals - Spent: ${"₹%.2f".format(totalSpent)}, Credits: ${"₹%.2f".format(totalCredits)}, Count: $transactionCount"
        )

        if (rawDebitCount > 0 && transactionCount == 0) {
            logger.warn(
                where = "getDashboardData",
                what = "WARNING: Raw debit transactions found ($rawDebitCount) but none remain after filtering ($transactionCount)"
            )

            // DEBUG: Show examples of excluded transactions
            expenseTransactionsBeforeFilter.take(3).forEach { transaction ->
                logger.debug(
                    where = "getDashboardData",
                    what = "[DEBUG] Potentially excluded: ${transaction.rawMerchant}"
                )
            }

            // DEBUG: Get exclusion states
            if (transactionFilterService != null) {
                val exclusionDebugInfo = transactionFilterService.getExclusionStatesDebugInfo()
                logger.debug(
                    where = "getDashboardData",
                    what = "[DEBUG] Exclusion states: $exclusionDebugInfo"
                )
            }
        }

        if (rawCreditCount > 0) {
            logger.debug(
                where = "getDashboardData",
                what = "[DEBUG] Found $rawCreditCount credit transactions totaling ${"₹%.2f".format(totalCredits)}"
            )
        }

        // FIXED: Request more merchants to ensure consistent display after exclusion filtering
        logger.debug(
            where = "getDashboardData",
            what = "[TOP_MERCHANTS] Fetching top merchants for date range: $startDate to $endDate"
        )
        val topMerchants = getTopMerchants(startDate, endDate, 8) // Request 8 to get at least 5 after filtering
        val topMerchantsWithCategory = getTopMerchantsWithCategory(startDate, endDate, 8) // Get merchants with category info
        logger.debug(
            where = "getDashboardData",
            what = "[TOP_MERCHANTS] Retrieved ${topMerchants.size} merchants (with ${topMerchantsWithCategory.size} having category info)"
        )

        // DEBUG: Log top merchants to verify date filtering
        topMerchantsWithCategory.forEachIndexed { index, merchant ->
            logger.debug(
                where = "getDashboardData",
                what = "[TOP_MERCHANTS] #${index + 1}: ${merchant.normalized_merchant} - ${"₹%.2f".format(merchant.total_amount)} (${merchant.transaction_count} txns) - Category: ${merchant.category_name}"
            )
        }

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
        
        logger.info(
            where = "getDashboardData",
            what = "[DASHBOARD] Dashboard data loaded successfully - Spent: ${"₹%.2f".format(dashboardData.totalSpent)}, Transactions: ${dashboardData.transactionCount}, Categories: ${dashboardData.topCategories.size}"
        )
        
        return dashboardData
    }
    
    // =======================
    // UTILITY METHODS
    // =======================
    
    suspend fun convertToTransactionEntity(parsedTransaction: ParsedTransaction): TransactionEntity =
        transactionRepository.convertToTransactionEntity(parsedTransaction)
    
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



    
    // =======================
    // DATABASE MAINTENANCE
    // =======================

    override suspend fun cleanupDuplicateTransactions(): Int =
        databaseMaintenanceOperations.cleanupDuplicateTransactions()

    // removeObviousTestData() has been removed - not needed for production with real data

    // =======================
    // CATEGORY DETAIL METHODS
    // =======================

    /**
     * Get all merchants within a specific category with their transaction statistics
     */
    suspend fun getMerchantsInCategory(categoryName: String): List<com.expensemanager.app.ui.categories.MerchantInCategory> =
        merchantCategoryOperations.getMerchantsInCategory(categoryName)

    /**
     * Change a merchant's category
     */
    suspend fun changeMerchantCategory(merchantName: String, newCategoryName: String, applyToFuture: Boolean): Boolean =
        merchantCategoryOperations.changeMerchantCategory(merchantName, newCategoryName, applyToFuture)

    /**
     * Rename a category (for custom categories)
     */
    suspend fun renameCategory(oldName: String, newName: String, newEmoji: String): Boolean =
        merchantCategoryOperations.renameCategory(oldName, newName, newEmoji)

    /**
     * Delete a category and reassign its merchants to another category
     */
    suspend fun deleteCategory(categoryName: String, reassignToCategoryName: String): Boolean =
        merchantCategoryOperations.deleteCategory(categoryName, reassignToCategoryName)

    /**
     * Check if a category is a system category
     */
    suspend fun isSystemCategory(categoryName: String): Boolean {
        return com.expensemanager.app.constants.Categories.isSystemCategory(categoryName)
    }

    // =======================
    // BUDGET OPERATIONS
    // =======================

    /**
     * Get the current monthly budget from database
     */
    suspend fun getMonthlyBudget(): BudgetEntity? = withContext(Dispatchers.IO) {
        try {
            budgetDao.getMonthlyBudget()
        } catch (e: Exception) {
            logger.error("getMonthlyBudget", "Error fetching monthly budget", e)
            null
        }
    }

    /**
     * Get monthly budget as Flow for reactive updates
     */
    fun getMonthlyBudgetFlow(): Flow<BudgetEntity?> = budgetDao.getMonthlyBudgetFlow()

    /**
     * Save or update the monthly budget
     */
    suspend fun saveMonthlyBudget(amount: Double) = withContext(Dispatchers.IO) {
        try {
            logger.debug("saveMonthlyBudget", "Saving monthly budget: ₹$amount")

            // Deactivate all existing monthly budgets
            budgetDao.deactivateAllMonthlyBudgets()

            // Create date range for current month
            val calendar = Calendar.getInstance()

            val startOfMonth = calendar.apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val endOfMonth = calendar.apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time

            // Insert new active budget (categoryId = null means overall budget)
            val budgetEntity = BudgetEntity(
                categoryId = null,
                budgetAmount = amount,
                periodType = "MONTHLY",
                startDate = startOfMonth,
                endDate = endOfMonth,
                isActive = true,
                createdAt = Date()
            )

            budgetDao.insertBudget(budgetEntity)
            logger.info("saveMonthlyBudget", "Successfully saved monthly budget: ₹$amount")

        } catch (e: Exception) {
            logger.error("saveMonthlyBudget", "Error saving monthly budget", e)
            throw e
        }
    }

    /**
     * Get budget for a specific category
     */
    suspend fun getCategoryBudget(categoryId: Long): BudgetEntity? =
        withContext(Dispatchers.IO) {
            budgetDao.getCategoryBudget(categoryId)
        }

    /**
     * Get all active category budgets
     */
    suspend fun getAllCategoryBudgets(): List<BudgetEntity> =
        withContext(Dispatchers.IO) {
            budgetDao.getAllActiveCategoryBudgets()
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
    val topMerchantsWithCategory: List<MerchantSpendingWithCategory>,
    val monthlyBalance: DashboardMonthlyBalance
)


data class DashboardMonthlyBalance(
    val lastSalaryAmount: Double,
    val lastSalaryDate: Date?,
    val currentMonthExpenses: Double,
    val remainingBalance: Double,
    val hasSalaryData: Boolean
)
