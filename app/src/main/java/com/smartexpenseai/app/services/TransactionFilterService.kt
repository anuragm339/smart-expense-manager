package com.smartexpenseai.app.services

import android.content.Context
import com.smartexpenseai.app.data.dao.MerchantDao
import com.smartexpenseai.app.utils.logging.StructuredLogger
import com.smartexpenseai.app.data.entities.MerchantEntity
import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.models.ParsedTransaction
import com.smartexpenseai.app.ui.messages.MessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Unified transaction filtering service that handles exclusion logic consistently
 * across Dashboard and Messages screens.
 *
 * This service provides:
 * 1. Consistent exclusion/inclusion logic for merchants using database as single source of truth
 * 2. Generic transaction filtering based on various criteria
 * 3. Debug information for exclusion states
 *
 * FIXED: Removed dual storage system - now uses ONLY database for merchant exclusions
 */
@Singleton
class TransactionFilterService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val merchantDao: MerchantDao
) {

    private val logger = StructuredLogger(
        featureTag = "TRANSACTION",
        className = "TransactionFilterService"
    )
    
    /**
     * Filter TransactionEntity list by exclusions (used by Repository and Dashboard)
     * FIXED: Now uses ONLY database for exclusions (removed SharedPreferences fallback)
     */
    suspend fun filterTransactionsByExclusions(transactions: List<TransactionEntity>): List<TransactionEntity> = withContext(Dispatchers.IO) {
        try {
            logger.debug(
                where = "filterTransactionsByExclusions",
                what = "[FILTER] Starting transaction filtering with ${transactions.size} transactions"
            )

            // Get database exclusions (single source of truth)
            val excludedMerchants = merchantDao.getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()

            logger.debug(
                where = "filterTransactionsByExclusions",
                what = "[DEBUG] Filtering with ${excludedNormalizedNames.size} database exclusions"
            )

            // Filter transactions using database exclusions only
            val filteredTransactions = transactions.filter { transaction ->
                val isExcluded = excludedNormalizedNames.contains(transaction.normalizedMerchant)

                if (isExcluded) {
                    logger.debug(
                        where = "filterTransactionsByExclusions",
                        what = "[EXCLUDE] ${transaction.rawMerchant} (normalized: ${transaction.normalizedMerchant})"
                    )
                }

                !isExcluded
            }

            logger.info(
                where = "filterTransactionsByExclusions",
                what = "[RESULT] Filtered ${transactions.size} -> ${filteredTransactions.size} transactions (excluded ${transactions.size - filteredTransactions.size})"
            )

            filteredTransactions

        } catch (e: Exception) {
            logger.error(
                where = "filterTransactionsByExclusions",
                what = "[ERROR] Error filtering transactions by exclusions",
                throwable = e
            )
            transactions // Return original list on error
        }
    }
    
    /**
     * Filter MessageItem list by exclusions (used by Messages screen)
     * FIXED: Now uses ONLY database for exclusions (removed SharedPreferences fallback)
     */
    suspend fun filterMessageItemsByExclusions(messageItems: List<MessageItem>): List<MessageItem> = withContext(Dispatchers.IO) {
        try {
            logger.debug(
                where = "filterMessageItemsByExclusions",
                what = "[FILTER] Starting MessageItem filtering with ${messageItems.size} items"
            )

            // Get database exclusions (single source of truth)
            val excludedMerchants = merchantDao.getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()

            // Filter MessageItems using database exclusions only
            val filteredItems = messageItems.filter { item ->
                // FIX: Use rawMerchant (original name) instead of merchant (display alias)
                // to match against database normalized names
                val normalizedMerchant = normalizeMerchantName(item.rawMerchant)

                val isExcluded = excludedNormalizedNames.contains(normalizedMerchant)

                if (isExcluded) {
                    logger.debug(
                        where = "filterMessageItemsByExclusions",
                        what = "[EXCLUDE] ${item.merchant} (raw: ${item.rawMerchant}, normalized: $normalizedMerchant)"
                    )
                }

                !isExcluded
            }

            logger.info(
                where = "filterMessageItemsByExclusions",
                what = "[RESULT] Filtered ${messageItems.size} -> ${filteredItems.size} MessageItems (excluded ${messageItems.size - filteredItems.size})"
            )

            filteredItems

        } catch (e: Exception) {
            logger.error(
                where = "filterMessageItemsByExclusions",
                what = "[ERROR] Error filtering MessageItems by exclusions",
                throwable = e
            )
            messageItems // Return original list on error
        }
    }
    
    /**
     * Filter parsed transactions by exclusions (used by parsing services)
     * FIXED: Now uses ONLY database for exclusions (removed SharedPreferences fallback)
     */
    suspend fun filterParsedTransactionsByExclusions(transactions: List<ParsedTransaction>): List<ParsedTransaction> = withContext(Dispatchers.IO) {
        try {
            logger.debug(
                where = "filterParsedTransactionsByExclusions",
                what = "[FILTER] Starting ParsedTransaction filtering with ${transactions.size} transactions"
            )

            // Get database exclusions (single source of truth)
            val excludedMerchants = merchantDao.getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()

            // Filter ParsedTransactions using database exclusions only
            val filteredTransactions = transactions.filter { transaction ->
                // Normalize merchant name for comparison
                val normalizedMerchant = normalizeMerchantName(transaction.merchant)
                val normalizedRawMerchant = if (transaction.rawMerchant.isNotEmpty())
                    normalizeMerchantName(transaction.rawMerchant) else ""

                val isExcluded = excludedNormalizedNames.contains(normalizedMerchant) ||
                    (normalizedRawMerchant.isNotEmpty() && excludedNormalizedNames.contains(normalizedRawMerchant))

                if (isExcluded) {
                    val rawMerchantInfo = if (transaction.rawMerchant.isNotEmpty()) " (raw: ${transaction.rawMerchant})" else ""
                    logger.debug(
                        where = "filterParsedTransactionsByExclusions",
                        what = "[EXCLUDE] ${transaction.merchant}$rawMerchantInfo"
                    )
                }

                !isExcluded
            }

            logger.info(
                where = "filterParsedTransactionsByExclusions",
                what = "[RESULT] Filtered ${transactions.size} -> ${filteredTransactions.size} ParsedTransactions (excluded ${transactions.size - filteredTransactions.size})"
            )

            filteredTransactions

        } catch (e: Exception) {
            logger.error(
                where = "filterParsedTransactionsByExclusions",
                what = "[ERROR] Error filtering ParsedTransactions by exclusions",
                throwable = e
            )
            transactions // Return original list on error
        }
    }
    
    /**
     * Generic filtering for transactions based on various criteria
     */
    fun applyGenericFilters(
        transactions: List<MessageItem>,
        minAmount: Double? = null,
        maxAmount: Double? = null,
        selectedBanks: Set<String> = emptySet(),
        minConfidence: Int = 0,
        dateFrom: String? = null,
        dateTo: String? = null,
        searchQuery: String = ""
    ): List<MessageItem> {
        
        var filtered = transactions.toList()
        
        // Apply amount range filter
        if (minAmount != null) {
            filtered = filtered.filter { it.amount >= minAmount }
            logger.debug(
                where = "applyGenericFilters",
                what = "[FILTER] After minAmount ($minAmount): ${filtered.size} items"
            )
        }

        if (maxAmount != null) {
            filtered = filtered.filter { it.amount <= maxAmount }
            logger.debug(
                where = "applyGenericFilters",
                what = "[FILTER] After maxAmount ($maxAmount): ${filtered.size} items"
            )
        }
        
        // Apply bank filter
        if (selectedBanks.isNotEmpty()) {
            filtered = filtered.filter { selectedBanks.contains(it.bankName) }
            logger.debug(
                where = "applyGenericFilters",
                what = "[FILTER] After banks (${selectedBanks.size} selected): ${filtered.size} items"
            )
        }
        
        // Apply confidence filter
        filtered = filtered.filter { it.confidence >= minConfidence }
        logger.debug(
            where = "applyGenericFilters",
            what = "[FILTER] After confidence (>= $minConfidence%): ${filtered.size} items"
        )
        
        // Apply date range filter
        if (dateFrom != null || dateTo != null) {
            // Implement date filtering logic if needed
            logger.debug(
                where = "applyGenericFilters",
                what = "[FILTER] Date filtering not implemented yet"
            )
        }
        
        // Apply search query filter
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                it.merchant.contains(searchQuery, ignoreCase = true) ||
                    it.bankName.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
            }
            logger.debug(
                where = "applyGenericFilters",
                what = "[FILTER] After search ('$searchQuery'): ${filtered.size} items"
            )
        }
        
        return filtered
    }
    
    /**
     * Update merchant exclusion status in database
     */
    suspend fun updateMerchantExclusion(normalizedMerchantName: String, isExcluded: Boolean): Boolean {
        return try {
            if (normalizedMerchantName.isBlank()) {
                logger.warn(
                    where = "updateMerchantExclusion",
                    what = "[VALIDATION] Cannot update exclusion for blank merchant name"
                )
                return false
            }
            
            logger.debug(
                where = "updateMerchantExclusion",
                what = "[UPDATE] Updating exclusion for '$normalizedMerchantName' to $isExcluded"
            )
            merchantDao.updateMerchantExclusion(normalizedMerchantName, isExcluded)
            logger.info(
                where = "updateMerchantExclusion",
                what = "[SUCCESS] Updated exclusion for '$normalizedMerchantName' to $isExcluded"
            )
            true
            
        } catch (e: Exception) {
            logger.error(
                where = "updateMerchantExclusion",
                what = "[ERROR] Failed to update merchant exclusion for '$normalizedMerchantName'",
                throwable = e
            )
            false
        }
    }
    
    /**
     * Get current exclusion states debug information
     * FIXED: Now uses ONLY database (removed SharedPreferences check)
     */
    suspend fun getExclusionStatesDebugInfo(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            // Database exclusions (single source of truth)
            val dbExcludedMerchants = merchantDao.getExcludedMerchants()
            val dbExcludedNames = dbExcludedMerchants.map { "${it.displayName} (${it.normalizedName})" }

            buildString {
                appendLine("[EXCLUSION STATES DEBUG]")
                append("Database exclusions (${dbExcludedNames.size}): ${if (dbExcludedNames.isEmpty()) "None" else dbExcludedNames.joinToString(", ")}")
            }
        } catch (e: Exception) {
            "Error loading exclusion states: ${e.message}"
        }
    }
    
    
    /**
     * Normalize merchant name for consistent comparison
     * This should match the normalization logic used in ExpenseRepository
     */
    private fun normalizeMerchantName(merchantName: String): String {
        // Use same normalization as MerchantAliasManager and DataMigrationHelper for consistency
        return merchantName.uppercase()
            .replace(Regex("[*#@\\-_]+.*"), "") // Remove suffixes after special chars
            .replace(Regex("\\s+"), " ") // Normalize spaces
            .trim()
    }
    
    /**
     * Check if a merchant is excluded
     * FIXED: Now uses ONLY database (removed SharedPreferences check)
     */
    suspend fun isMerchantExcluded(merchantName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedName = normalizeMerchantName(merchantName)

            // Check database exclusions (single source of truth)
            return@withContext merchantDao.getExcludedMerchants()
                .any { it.normalizedName == normalizedName }

        } catch (e: Exception) {
            logger.error(
                where = "isMerchantExcluded",
                what = "Error checking merchant exclusion status",
                throwable = e
            )
            false
        }
    }
    
    /**
     * Get all excluded merchants from database
     */
    suspend fun getAllExcludedMerchants(): List<MerchantEntity> = withContext(Dispatchers.IO) {
        try {
            merchantDao.getExcludedMerchants()
        } catch (e: Exception) {
            logger.error(
                where = "getAllExcludedMerchants",
                what = "Error getting all excluded merchants",
                throwable = e
            )
            emptyList()
        }
    }
    
    /**
     * Get included and excluded MessageItems separately for tab filtering
     * FIXED: Now uses ONLY database for exclusions (removed SharedPreferences fallback)
     */
    suspend fun separateMessageItemsByInclusion(messageItems: List<MessageItem>): Triple<List<MessageItem>, List<MessageItem>, List<MessageItem>> = withContext(Dispatchers.IO) {
        try {
            logger.debug(
                where = "separateMessageItemsByInclusion",
                what = "[SEPARATE] Separating ${messageItems.size} MessageItems by inclusion"
            )

            // Get database exclusions (single source of truth)
            val excludedMerchants = merchantDao.getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()

            // Separate MessageItems
            val includedItems = mutableListOf<MessageItem>()
            val excludedItems = mutableListOf<MessageItem>()

            messageItems.forEach { item ->
                // FIX: Use rawMerchant (original name) instead of merchant (display alias)
                // to match against database normalized names
                val normalizedMerchant = normalizeMerchantName(item.rawMerchant)

                val isExcluded = excludedNormalizedNames.contains(normalizedMerchant)

                if (isExcluded) {
                    excludedItems.add(item)
                } else {
                    includedItems.add(item)
                }
            }

            logger.info(
                where = "separateMessageItemsByInclusion",
                what = "[SEPARATE] Results: All: ${messageItems.size}, Included: ${includedItems.size}, Excluded: ${excludedItems.size}"
            )

            Triple(messageItems, includedItems, excludedItems)

        } catch (e: Exception) {
            logger.error(
                where = "separateMessageItemsByInclusion",
                what = "[ERROR] Error separating MessageItems by inclusion",
                throwable = e
            )
            Triple(messageItems, messageItems, emptyList()) // Return original list for all and included on error
        }
    }
    
    /**
     * Get only included MessageItems (convenience method)
     */
    suspend fun getIncludedMessageItems(messageItems: List<MessageItem>): List<MessageItem> = withContext(Dispatchers.IO) {
        val (_, included, _) = separateMessageItemsByInclusion(messageItems)
        included
    }
    
    /**
     * Get only excluded MessageItems (convenience method)
     */
    suspend fun getExcludedMessageItems(messageItems: List<MessageItem>): List<MessageItem> = withContext(Dispatchers.IO) {
        val (_, _, excluded) = separateMessageItemsByInclusion(messageItems)
        excluded
    }
}
