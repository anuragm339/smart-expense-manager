package com.expensemanager.app.services

import android.content.Context
import android.content.SharedPreferences
import com.expensemanager.app.data.dao.MerchantDao
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.utils.logging.StructuredLogger
import com.expensemanager.app.data.entities.MerchantEntity
import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.models.ParsedTransaction
import com.expensemanager.app.ui.messages.MessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Unified transaction filtering service that handles exclusion logic consistently
 * across Dashboard and Messages screens.
 * 
 * This service provides:
 * 1. Consistent exclusion/inclusion logic for merchants
 * 2. Generic transaction filtering based on various criteria
 * 3. Unified handling of both database and SharedPreferences exclusions
 * 4. Debug information for exclusion states
 */
@Singleton
class TransactionFilterService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val merchantDao: MerchantDao
) {
    
    companion object {
        private const val SHARED_PREFS_NAME = "ExpenseManagerPrefs"
        private const val INCLUSION_KEY_PREFIX = "include_"
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val logger = StructuredLogger(
        featureTag = LogConfig.FeatureTags.TRANSACTION,
        className = "TransactionFilterService"
    )
    
    /**
     * Filter TransactionEntity list by exclusions (used by Repository and Dashboard)
     */
    suspend fun filterTransactionsByExclusions(transactions: List<TransactionEntity>): List<TransactionEntity> = withContext(Dispatchers.IO) {
        try {
            logger.debug(
                where = "filterTransactionsByExclusions",
                what = "[FILTER] Starting transaction filtering with ${transactions.size} transactions"
            )
            
            // 1. Get database exclusions
            val excludedMerchants = merchantDao.getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()
            
            // 2. Get SharedPreferences exclusions (legacy system)
            val sharedPrefsExclusions = getSharedPreferencesExclusions()
            
            logger.debug(
                where = "filterTransactionsByExclusions",
                what = "[DEBUG] Filtering with ${excludedNormalizedNames.size} database exclusions and ${sharedPrefsExclusions.size} SharedPrefs exclusions"
            )
            
            // 3. Filter transactions using BOTH exclusion systems
            val filteredTransactions = transactions.filter { transaction ->
                val isExcludedInDatabase = excludedNormalizedNames.contains(transaction.normalizedMerchant)
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(transaction.normalizedMerchant) ||
                    sharedPrefsExclusions.contains(transaction.rawMerchant)
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                
                if (!isIncluded) {
                    logger.debug(
                        where = "filterTransactionsByExclusions",
                        what = "[EXCLUDE] ${transaction.rawMerchant} (normalized: ${transaction.normalizedMerchant}) - DB: $isExcludedInDatabase, Prefs: $isExcludedInSharedPrefs"
                    )
                }
                
                isIncluded
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
     */
    suspend fun filterMessageItemsByExclusions(messageItems: List<MessageItem>): List<MessageItem> = withContext(Dispatchers.IO) {
        try {
            logger.debug(
                where = "filterMessageItemsByExclusions",
                what = "[FILTER] Starting MessageItem filtering with ${messageItems.size} items"
            )
            
            // 1. Get database exclusions
            val excludedMerchants = merchantDao.getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()
            
            // 2. Get SharedPreferences exclusions (legacy system)
            val sharedPrefsExclusions = getSharedPreferencesExclusions()
            
            // 3. Filter MessageItems using BOTH exclusion systems
            val filteredItems = messageItems.filter { item ->
                // Normalize merchant name for comparison (similar to ExpenseRepository logic)
                val normalizedMerchant = normalizeMerchantName(item.merchant)
                
                val isExcludedInDatabase = excludedNormalizedNames.contains(normalizedMerchant)
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(normalizedMerchant) ||
                    sharedPrefsExclusions.contains(item.merchant)
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                
                if (!isIncluded) {
                    logger.debug(
                        where = "filterMessageItemsByExclusions",
                        what = "[EXCLUDE] ${item.merchant} (normalized: $normalizedMerchant) - DB: $isExcludedInDatabase, Prefs: $isExcludedInSharedPrefs"
                    )
                }
                
                isIncluded
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
     */
    suspend fun filterParsedTransactionsByExclusions(transactions: List<ParsedTransaction>): List<ParsedTransaction> = withContext(Dispatchers.IO) {
        try {
            logger.debug(
                where = "filterParsedTransactionsByExclusions",
                what = "[FILTER] Starting ParsedTransaction filtering with ${transactions.size} transactions"
            )
            
            // 1. Get database exclusions
            val excludedMerchants = merchantDao.getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()
            
            // 2. Get SharedPreferences exclusions (legacy system)
            val sharedPrefsExclusions = getSharedPreferencesExclusions()
            
            // 3. Filter ParsedTransactions using BOTH exclusion systems
            val filteredTransactions = transactions.filter { transaction ->
                // Normalize merchant name for comparison
                val normalizedMerchant = normalizeMerchantName(transaction.merchant)
                val normalizedRawMerchant = if (transaction.rawMerchant.isNotEmpty()) 
                    normalizeMerchantName(transaction.rawMerchant) else ""
                
                val isExcludedInDatabase = excludedNormalizedNames.contains(normalizedMerchant) ||
                    (normalizedRawMerchant.isNotEmpty() && excludedNormalizedNames.contains(normalizedRawMerchant))
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(normalizedMerchant) ||
                    sharedPrefsExclusions.contains(transaction.merchant) ||
                    (normalizedRawMerchant.isNotEmpty() && sharedPrefsExclusions.contains(normalizedRawMerchant)) ||
                    (transaction.rawMerchant.isNotEmpty() && sharedPrefsExclusions.contains(transaction.rawMerchant))
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                
                if (!isIncluded) {
                    val rawMerchantInfo = if (transaction.rawMerchant.isNotEmpty()) " (raw: ${transaction.rawMerchant})" else ""
                    logger.debug(
                        where = "filterParsedTransactionsByExclusions",
                        what = "[EXCLUDE] ${transaction.merchant}$rawMerchantInfo - DB: $isExcludedInDatabase, Prefs: $isExcludedInSharedPrefs"
                    )
                }
                
                isIncluded
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
     */
    suspend fun getExclusionStatesDebugInfo(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            // 1. Database exclusions
            val dbExcludedMerchants = merchantDao.getExcludedMerchants()
            val dbExcludedNames = dbExcludedMerchants.map { "${it.displayName} (${it.normalizedName})" }
            
            // 2. SharedPreferences exclusions
            val sharedPrefsExcluded = getSharedPreferencesExclusionsWithDetails()
            
            buildString {
                appendLine("[EXCLUSION STATES DEBUG]")
                appendLine("Database exclusions (${dbExcludedNames.size}): ${if (dbExcludedNames.isEmpty()) "None" else dbExcludedNames.joinToString(", ")}")
                append("SharedPrefs exclusions (${sharedPrefsExcluded.size}): ${if (sharedPrefsExcluded.isEmpty()) "None" else sharedPrefsExcluded.joinToString(", ")}")
            }
        } catch (e: Exception) {
            "Error loading unified exclusion states: ${e.message}"
        }
    }
    
    /**
     * Get SharedPreferences exclusions (merchants marked as excluded)
     */
    private fun getSharedPreferencesExclusions(): Set<String> {
        return try {
            logger.debug(
                where = "getSharedPreferencesExclusions",
                what = "[PREFS] Loading SharedPreferences exclusions..."
            )
            
            val currentPrefsExclusions = try {
                sharedPreferences.all
                    .filterKeys { it.startsWith(INCLUSION_KEY_PREFIX) }
                    .filterValues { !(it as? Boolean ?: true) } // Get excluded merchants (not included)
                    .keys
                    .map { it.removePrefix(INCLUSION_KEY_PREFIX) }
                    .toSet()
            } catch (e: Exception) {
                logger.warn(
                    where = "getSharedPreferencesExclusions",
                    what = "[PREFS] Error reading current preferences format",
                    why = e.message
                )
                emptySet<String>()
            }
            
            // LEGACY SUPPORT: Also check the old SharedPreferences format
            val legacyExclusions = try {
                val legacyPrefs = context.getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
                val legacyInclusionStatesJson = legacyPrefs.getString("group_inclusion_states", null)
                val exclusions = mutableSetOf<String>()
                
                if (legacyInclusionStatesJson != null && legacyInclusionStatesJson.isNotBlank()) {
                    try {
                        val inclusionStates = org.json.JSONObject(legacyInclusionStatesJson)
                        val keys = inclusionStates.keys()
                        while (keys.hasNext()) {
                            val merchantName = keys.next()
                            try {
                                val isIncluded = inclusionStates.getBoolean(merchantName)
                                if (!isIncluded) {
                                    exclusions.add(merchantName)
                                }
                            } catch (e: Exception) {
                                logger.warn(
                                    where = "getSharedPreferencesExclusions",
                                    what = "[PREFS] Error parsing inclusion state for '$merchantName'",
                                    why = e.message
                                )
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn(
                            where = "getSharedPreferencesExclusions",
                            what = "[PREFS] Error parsing legacy inclusion states JSON",
                            why = e.message
                        )
                    }
                }
                exclusions
            } catch (e: Exception) {
                logger.warn(
                    where = "getSharedPreferencesExclusions",
                    what = "[PREFS] Error reading legacy preferences",
                    why = e.message
                )
                emptySet<String>()
            }
            
            val combinedExclusions = currentPrefsExclusions + legacyExclusions
            logger.debug(
                where = "getSharedPreferencesExclusions",
                what = "[DEBUG] SharedPrefs exclusions - New format: ${currentPrefsExclusions.size}, Legacy format: ${legacyExclusions.size}, Combined: ${combinedExclusions.size}"
            )
            
            combinedExclusions
            
        } catch (e: Exception) {
            logger.error(
                where = "getSharedPreferencesExclusions",
                what = "[ERROR] Critical error reading SharedPreferences exclusions",
                throwable = e
            )
            emptySet()
        }
    }
    
    /**
     * Get SharedPreferences exclusions with details for debug
     */
    private fun getSharedPreferencesExclusionsWithDetails(): List<String> {
        return try {
            sharedPreferences.all
                .filterKeys { it.startsWith(INCLUSION_KEY_PREFIX) }
                .filterValues { !(it as? Boolean ?: true) }
                .keys
                .map { it.removePrefix(INCLUSION_KEY_PREFIX) }
        } catch (e: Exception) {
            logger.error(
                where = "getSharedPreferencesExclusionsWithDetails",
                what = "Error reading SharedPreferences exclusions details",
                throwable = e
            )
            emptyList()
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
     */
    suspend fun isMerchantExcluded(merchantName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedName = normalizeMerchantName(merchantName)
            
            // Check database exclusions
            val dbExcluded = merchantDao.getExcludedMerchants()
                .any { it.normalizedName == normalizedName }
            
            // Check SharedPreferences exclusions
            val prefsExcluded = getSharedPreferencesExclusions().contains(normalizedName) ||
                getSharedPreferencesExclusions().contains(merchantName)
            
            return@withContext dbExcluded || prefsExcluded
            
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
     * Get all excluded merchants from both systems
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
     * Clear SharedPreferences exclusions (for migration purposes)
     */
    suspend fun clearSharedPreferencesExclusions() = withContext(Dispatchers.IO) {
        try {
            val editor = sharedPreferences.edit()
            val exclusionKeys = sharedPreferences.all.keys.filter { it.startsWith(INCLUSION_KEY_PREFIX) }
            
            exclusionKeys.forEach { key ->
                editor.remove(key)
            }
            
            editor.apply()
            logger.info(
                where = "clearSharedPreferencesExclusions",
                what = "[CLEANUP] Cleared ${exclusionKeys.size} SharedPreferences exclusions"
            )
            
        } catch (e: Exception) {
            logger.error(
                where = "clearSharedPreferencesExclusions",
                what = "Error clearing SharedPreferences exclusions",
                throwable = e
            )
        }
    }
    
    /**
     * Get included and excluded MessageItems separately for tab filtering
     */
    suspend fun separateMessageItemsByInclusion(messageItems: List<MessageItem>): Triple<List<MessageItem>, List<MessageItem>, List<MessageItem>> = withContext(Dispatchers.IO) {
        try {
            logger.debug(
                where = "separateMessageItemsByInclusion",
                what = "[SEPARATE] Separating ${messageItems.size} MessageItems by inclusion"
            )
            
            // 1. Get database exclusions
            val excludedMerchants = merchantDao.getExcludedMerchants()
            val excludedNormalizedNames = excludedMerchants.map { it.normalizedName }.toSet()
            
            // 2. Get SharedPreferences exclusions (legacy system)
            val sharedPrefsExclusions = getSharedPreferencesExclusions()
            
            // 3. Separate MessageItems
            val includedItems = mutableListOf<MessageItem>()
            val excludedItems = mutableListOf<MessageItem>()
            
            messageItems.forEach { item ->
                // Normalize merchant name for comparison
                val normalizedMerchant = normalizeMerchantName(item.merchant)
                
                val isExcludedInDatabase = excludedNormalizedNames.contains(normalizedMerchant)
                val isExcludedInSharedPrefs = sharedPrefsExclusions.contains(normalizedMerchant) ||
                    sharedPrefsExclusions.contains(item.merchant)
                
                val isIncluded = !isExcludedInDatabase && !isExcludedInSharedPrefs
                
                if (isIncluded) {
                    includedItems.add(item)
                } else {
                    excludedItems.add(item)
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
