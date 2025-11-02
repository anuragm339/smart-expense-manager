package com.smartexpenseai.app.data.repository.internal

import android.content.Context
import com.smartexpenseai.app.data.dao.CategoryDao
import com.smartexpenseai.app.data.dao.MerchantDao
import com.smartexpenseai.app.data.dao.MerchantSpending
import com.smartexpenseai.app.data.dao.MerchantSpendingWithCategory
import com.smartexpenseai.app.data.dao.SyncStateDao
import com.smartexpenseai.app.data.dao.TransactionDao
import com.smartexpenseai.app.data.dao.CategorySpendingResult
import com.smartexpenseai.app.data.entities.CategoryEntity
import com.smartexpenseai.app.data.entities.MerchantEntity
import com.smartexpenseai.app.data.entities.SyncStateEntity
import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.models.ParsedTransaction
import com.smartexpenseai.app.services.SMSParsingService
import com.smartexpenseai.app.services.TransactionFilterService
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit

internal class TransactionDataRepository(
    private val context: Context,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantDao,
    private val syncStateDao: SyncStateDao,
    private val smsParsingService: SMSParsingService,
    private val transactionFilterService: TransactionFilterService?,
    
) {

    private val logger = StructuredLogger(
        featureTag = "DATABASE",
        className = "TransactionDataRepository"
    )

    // ---------------------------------------------------------------------
    // Basic queries
    // ---------------------------------------------------------------------

    suspend fun transactions(): Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    suspend fun transactionsSync(): List<TransactionEntity> = transactionDao.getAllTransactionsSync()

    suspend fun transactionsBetween(startDate: Date, endDate: Date): List<TransactionEntity> =
        transactionDao.getTransactionsByDateRange(startDate, endDate)

    suspend fun transactionsBetween(
        startDate: Date,
        endDate: Date,
        limit: Int,
        offset: Int
    ): List<TransactionEntity> =
        transactionDao.getTransactionsByDateRangePaginated(startDate, endDate, limit, offset)

    suspend fun transactionCountBetween(startDate: Date, endDate: Date): Int =
        transactionDao.getTransactionCountByDateRange(startDate, endDate)

    suspend fun expenseTransactionsBetween(startDate: Date, endDate: Date): List<TransactionEntity> =
        transactionDao.getExpenseTransactionsByDateRange(startDate, endDate)

    suspend fun transactionCount(): Int = transactionDao.getTransactionCount()

    suspend fun transactionsByMerchant(merchantName: String): List<TransactionEntity> =
        transactionDao.getTransactionsByMerchant(merchantName)

    suspend fun searchTransactions(query: String, limit: Int): List<TransactionEntity> =
        transactionDao.searchTransactions(query, limit)

    suspend fun transactionBySmsId(smsId: String): TransactionEntity? =
        transactionDao.getTransactionBySmsId(smsId)

    suspend fun insertTransaction(transaction: TransactionEntity): Long =
        transactionDao.insertTransaction(transaction)

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(transactionId: Long) {
        transactionDao.deleteTransactionById(transactionId)
    }

    // ---------------------------------------------------------------------
    // Totals and analytics
    // ---------------------------------------------------------------------

    suspend fun totalSpent(startDate: Date, endDate: Date): Double {
        val expenseTransactions = expenseTransactionsBetween(startDate, endDate)
        val filteredTransactions = filterTransactions(expenseTransactions)
        return filteredTransactions.sumOf { it.amount }
    }

    suspend fun totalCredits(startDate: Date, endDate: Date): Double =
        transactionDao.getTotalCreditsOrIncomeByDateRange(startDate, endDate) ?: 0.0

    suspend fun actualBalance(startDate: Date, endDate: Date): Double {
        val credits = totalCredits(startDate, endDate)
        val debits = totalSpent(startDate, endDate)
        return credits - debits
    }

    suspend fun salaryTransactions(minAmount: Double = 10_000.0, limit: Int = 10): List<TransactionEntity> =
        transactionDao.getSalaryTransactions(minAmount, limit)

    suspend fun lastSalaryTransaction(): TransactionEntity? = transactionDao.getLastSalaryTransaction()

    data class MonthlyBalanceInfo(
        val lastSalaryAmount: Double,
        val lastSalaryDate: Date?,
        val currentMonthExpenses: Double,
        val remainingBalance: Double,
        val hasSalaryData: Boolean
    )

    suspend fun monthlyBudgetBalance(
        currentMonthStartDate: Date,
        currentMonthEndDate: Date
    ): MonthlyBalanceInfo {
        val allCredits = transactionDao.getTransactionsByDateRange(Date(0), Date())
            .filter { !it.isDebit }

        val lastSalary = lastSalaryTransaction() ?: allCredits.maxByOrNull { it.amount }
        val expenses = totalSpent(currentMonthStartDate, currentMonthEndDate)

        val salaryAmount = lastSalary?.amount ?: 0.0
        val remaining = salaryAmount - expenses

        return MonthlyBalanceInfo(
            lastSalaryAmount = salaryAmount,
            lastSalaryDate = lastSalary?.transactionDate,
            currentMonthExpenses = expenses,
            remainingBalance = remaining,
            hasSalaryData = lastSalary != null
        )
    }

    suspend fun categorySpending(startDate: Date, endDate: Date): List<CategorySpendingResult> =
        try {
            transactionDao.getCategorySpendingBreakdown(startDate, endDate)
        } catch (error: Exception) {
            logger.warnWithThrowable(
                where = "categorySpending",
                what = "Optimized category query failed, falling back to in-memory aggregation",
                throwable = error
            )

            val expenseTransactions = expenseTransactionsBetween(startDate, endDate)
            val filteredTransactions = filterTransactions(expenseTransactions)

            val totals = mutableMapOf<String, Triple<Double, Int, Date?>>()
            val merchantCategoryCache = mutableMapOf<String, String>()

            filteredTransactions.forEach { transaction ->
                val categoryName = merchantCategoryCache.getOrPut(transaction.normalizedMerchant) {
                    merchantWithCategory(transaction.normalizedMerchant)?.category_name ?: "Other"
                }

                val existing = totals[categoryName] ?: Triple(0.0, 0, null)
                val newTotal = existing.first + transaction.amount
                val newCount = existing.second + 1
                val newestDate = existing.third?.let { previous ->
                    if (transaction.transactionDate.after(previous)) transaction.transactionDate else previous
                } ?: transaction.transactionDate

                totals[categoryName] = Triple(newTotal, newCount, newestDate)
            }

            val categories = totals.keys.mapNotNull { categoryDao.getCategoryByName(it) }
                .associateBy { it.name }

            totals.map { (categoryName, data) ->
                val category = categories[categoryName]
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

    suspend fun topMerchants(
        startDate: Date,
        endDate: Date,
        limit: Int
    ): List<MerchantSpending> = topMerchantsWithCategory(startDate, endDate, limit)
        .map { merchant ->
            MerchantSpending(
                normalized_merchant = merchant.normalized_merchant,
                total_amount = merchant.total_amount,
                transaction_count = merchant.transaction_count
            )
        }

    suspend fun topMerchantsWithCategory(
        startDate: Date,
        endDate: Date,
        limit: Int
    ): List<MerchantSpendingWithCategory> =
        filterMerchantsByExclusionWithCategory(
            transactionDao.getTopMerchantsBySpending(startDate, endDate, limit * 2)
        ).take(limit)

    // ---------------------------------------------------------------------
    // Sync state
    // ---------------------------------------------------------------------

    suspend fun syncNewSms(): Int = withContext(Dispatchers.IO) {
        try {
            val syncState = syncStateDao.getSyncState()
            val lastSyncTimestamp = syncState?.lastSmsSyncTimestamp ?: Date(0)

            logger.debug(
                where = "syncNewSms",
                what = "Starting SMS sync - Last sync timestamp: $lastSyncTimestamp"
            )

            syncStateDao.updateSyncStatus("IN_PROGRESS")

            val allTransactions = smsParsingService.scanHistoricalSMS { _, _, _ -> }

            val newTransactions = allTransactions.filter { it.date.after(lastSyncTimestamp) }

            var insertedCount = 0
            var duplicateCount = 0

            newTransactions.forEach { parsed ->
                val entity = convertToTransactionEntity(parsed)
                val existing = transactionDao.getTransactionBySmsId(entity.smsId)

                val similar = findSimilarTransaction(entity)

                if (existing == null && similar == null) {
                    if (transactionDao.insertTransaction(entity) > 0) {
                        insertedCount++
                    }
                } else {
                    duplicateCount++
                }
            }

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

            logger.debug(
                where = "syncNewSms",
                what = "SMS sync completed - Inserted: $insertedCount, Duplicates: $duplicateCount"
            )

            insertedCount
        } catch (error: Exception) {
            logger.error(
                where = "syncNewSms",
                what = "[UNIFIED] Repository SMS sync failed",
                throwable = error
            )

            syncStateDao.updateSyncStatus("FAILED")
            0
        }
    }

    suspend fun lastSyncTimestamp(): Date? = syncStateDao.getLastSyncTimestamp()

    suspend fun syncStatus(): String? = syncStateDao.getSyncStatus()

    suspend fun updateSyncState(lastSyncDate: Date) {
        syncStateDao.updateSyncState(lastSyncDate, null, transactionDao.getTransactionCount(), "COMPLETED")
    }

    suspend fun ensureSyncStateInitialized() {
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

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    suspend fun convertToTransactionEntity(parsed: ParsedTransaction): TransactionEntity {
        val normalizedMerchant = normalizeMerchantName(parsed.merchant)
        ensureMerchantExists(normalizedMerchant, parsed.merchant)

        return TransactionEntity(
            smsId = parsed.id,
            amount = parsed.amount,
            rawMerchant = parsed.merchant,
            normalizedMerchant = normalizedMerchant,
            bankName = parsed.bankName,
            transactionDate = parsed.date,
            rawSmsBody = parsed.rawSMS,
            confidenceScore = parsed.confidence,
            isDebit = parsed.isDebit,
            createdAt = Date(),
            updatedAt = Date()
        )
    }

    suspend fun findSimilarTransaction(entity: TransactionEntity): TransactionEntity? {
        val amountTolerance = 1.0 // ₹1 cushion to account for rounding differences
        val timeWindowMillis = TimeUnit.MINUTES.toMillis(10)

        val minAmount = (entity.amount - amountTolerance).coerceAtLeast(0.0)
        val maxAmount = entity.amount + amountTolerance

        val startDate = Date(entity.transactionDate.time - timeWindowMillis)
        val endDate = Date(entity.transactionDate.time + timeWindowMillis)

        return transactionDao.findSimilarTransaction(
            normalizedMerchant = entity.normalizedMerchant,
            minAmount = minAmount,
            maxAmount = maxAmount,
            startDate = startDate,
            endDate = endDate,
            bankName = entity.bankName
        )
    }

    private suspend fun ensureMerchantExists(normalizedName: String, displayName: String) {
        val existing = merchantDao.getMerchantByNormalizedName(normalizedName)
        if (existing != null) return

        val categoryName = categorizeMerchant(displayName)
        val category = categoryDao.getCategoryByName(categoryName)
            ?: categoryDao.getCategoryByName("Other")
            ?: createDefaultCategory()

        merchantDao.insertMerchant(
            MerchantEntity(
                normalizedName = normalizedName,
                displayName = displayName,
                categoryId = category.id,
                isUserDefined = false,
                createdAt = Date()
            )
        )
    }

    private suspend fun createDefaultCategory(): CategoryEntity {
        val other = CategoryEntity(
            name = "Other",
            color = "#888888",
            isSystem = true,
            createdAt = Date()
        )
        val id = categoryDao.insertCategory(other)
        return other.copy(id = id)
    }

    private fun categorizeMerchant(merchantName: String): String {
        val upper = merchantName.uppercase()
        return when {
            upper.contains("SWIGGY") || upper.contains("ZOMATO") ||
                upper.contains("DOMINOES") || upper.contains("PIZZA") ||
                upper.contains("MCDONALD") || upper.contains("KFC") ||
                upper.contains("RESTAURANT") || upper.contains("CAFE") ||
                upper.contains("FOOD") || upper.contains("DINING") ||
                upper.contains("AKSHAYAKALPA") -> "Food & Dining"

            upper.contains("UBER") || upper.contains("OLA") ||
                upper.contains("TAXI") || upper.contains("METRO") ||
                upper.contains("BUS") || upper.contains("TRANSPORT") -> "Transportation"

            upper.contains("BIGBAZAAR") || upper.contains("DMART") ||
                upper.contains("RELIANCE") || upper.contains("GROCERY") ||
                upper.contains("SUPERMARKET") || upper.contains("FRESH") ||
                upper.contains("MART") -> "Groceries"

            upper.contains("HOSPITAL") || upper.contains("CLINIC") ||
                upper.contains("PHARMACY") || upper.contains("MEDICAL") ||
                upper.contains("HEALTH") || upper.contains("DOCTOR") -> "Healthcare"

            upper.contains("MOVIE") || upper.contains("CINEMA") ||
                upper.contains("THEATRE") || upper.contains("GAME") ||
                upper.contains("ENTERTAINMENT") || upper.contains("NETFLIX") ||
                upper.contains("SPOTIFY") -> "Entertainment"

            upper.contains("AMAZON") || upper.contains("FLIPKART") ||
                upper.contains("MYNTRA") || upper.contains("AJIO") ||
                upper.contains("SHOPPING") || upper.contains("STORE") -> "Shopping"

            upper.contains("ELECTRICITY") || upper.contains("WATER") ||
                upper.contains("GAS") || upper.contains("INTERNET") ||
                upper.contains("MOBILE") || upper.contains("RECHARGE") -> "Utilities"

            else -> "Other"
        }
    }

    fun normalizeMerchantName(merchant: String): String {
        val cleaned = merchant.uppercase()
            .replace(Regex("\\s+"), " ")
            .trim()

        return cleaned
            .replace(Regex("\\*(ORDER|PAYMENT|TXN|TRANSACTION).*$"), "")
            .replace(Regex("#\\d+.*$"), "")
            .replace(Regex("@\\w+.*$"), "")
            .replace(Regex("-{2,}.*$"), "")
            .replace(Regex("_{2,}.*$"), "")
            .trim()
    }

    private suspend fun merchantWithCategory(normalizedName: String) =
        merchantDao.getMerchantWithCategory(normalizedName)

    private suspend fun filterTransactions(transactions: List<TransactionEntity>): List<TransactionEntity> =
        try {
            transactionFilterService?.let { service ->
                logger.debug(
                    where = "filterTransactions",
                    what = "UNIFIED FILTERING: ${transactions.size} transactions before filtering"
                )
                val filtered = service.filterTransactionsByExclusions(transactions)
                logger.debug(
                    where = "filterTransactions",
                    what = "UNIFIED FILTERING: ${filtered.size} transactions after filtering"
                )
                filtered
            } ?: filterTransactionsLegacy(transactions)
        } catch (error: Exception) {
            logger.error(
                where = "filterTransactions",
                what = "Error in unified filtering",
                throwable = error
            )
            transactions
        }

    private suspend fun filterTransactionsLegacy(transactions: List<TransactionEntity>): List<TransactionEntity> {
        val excludedMerchants = merchantDao.getExcludedMerchants()
            .map { it.normalizedName }
            .toSet()

        val sharedPrefsExclusions = loadSharedPrefsExclusions()

        return transactions.filter { transaction ->
            val normalized = transaction.normalizedMerchant
            val rawMerchant = transaction.rawMerchant.lowercase()
            !excludedMerchants.contains(normalized) &&
                !sharedPrefsExclusions.contains(normalized) &&
                !sharedPrefsExclusions.contains(rawMerchant)
        }
    }

    private fun loadSharedPrefsExclusions(): Set<String> {
        val prefs = context.getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
        val inclusionStatesJson = prefs.getString("group_inclusion_states", null) ?: return emptySet()

        return try {
            val inclusionStates = org.json.JSONObject(inclusionStatesJson)
            val exclusions = mutableSetOf<String>()
            val keys = inclusionStates.keys()
            while (keys.hasNext()) {
                val merchant = keys.next()
                if (!inclusionStates.getBoolean(merchant)) {
                    exclusions.add(normalizeMerchantName(merchant))
                }
            }
            exclusions
        } catch (error: Exception) {
            logger.warn(
                where = "loadSharedPrefsExclusions",
                what = "Error parsing inclusion states: ${error.message ?: "unknown"}"
            )
            emptySet()
        }
    }

    private suspend fun filterMerchantsByExclusionWithCategory(
        merchants: List<MerchantSpendingWithCategory>
    ): List<MerchantSpendingWithCategory> {
        val excluded = merchantDao.getExcludedMerchants().map { it.normalizedName }.toSet()
        val sharedPrefs = loadSharedPrefsExclusions()

        return merchants.filter { merchant ->
            val normalized = merchant.normalized_merchant
            !excluded.contains(normalized) && !sharedPrefs.contains(normalized)
        }
    }
}
