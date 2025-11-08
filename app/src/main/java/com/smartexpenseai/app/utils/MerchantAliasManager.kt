package com.smartexpenseai.app.utils

import android.content.Context
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.runBlocking

data class MerchantAlias(
    val originalName: String,
    val displayName: String,
    val category: String,
    val categoryColor: String
)

/**
 * MerchantAliasManager - Database-backed merchant alias management
 * All operations now use ExpenseRepository instead of SharedPreferences
 */
class MerchantAliasManager(
    private val context: Context,
    private val repository: com.smartexpenseai.app.data.repository.ExpenseRepository
) {

    private val categoryManager = CategoryManager(context, repository)
    private val logger = StructuredLogger("MerchantAliasManager", "MerchantAliasManager")
    
    /**
     * Get the display name for a merchant (original name or alias) - NOW USES DB
     */
    fun getDisplayName(originalMerchantName: String): String = runBlocking {
        val normalizedName = normalizeMerchantName(originalMerchantName)

        // Get from database instead of SharedPreferences
        val merchantWithCategory = repository.getMerchantWithCategory(normalizedName)
        val displayName = merchantWithCategory?.display_name ?: originalMerchantName

        logger.debug("getDisplayName","'$originalMerchantName' -> '$displayName' (from DB, normalized: '$normalizedName')")
        displayName
    }
    
    /**
     * Get the category for a merchant - NOW USES DB
     */
    fun getMerchantCategory(originalMerchantName: String): String = runBlocking {
        val normalizedName = normalizeMerchantName(originalMerchantName)

        // Get from database instead of SharedPreferences
        val merchantWithCategory = repository.getMerchantWithCategory(normalizedName)
        merchantWithCategory?.category_name ?: categoryManager.categorizeTransaction(originalMerchantName)
    }
    
    /**
     * Get the category color for a merchant - NOW USES DB
     */
    fun getMerchantCategoryColor(originalMerchantName: String): String = runBlocking {
        val normalizedName = normalizeMerchantName(originalMerchantName)

        // Get from database instead of SharedPreferences
        val merchantWithCategory = repository.getMerchantWithCategory(normalizedName)
        merchantWithCategory?.category_color ?: categoryManager.getCategoryColor(getMerchantCategory(originalMerchantName))
    }
    
    /**
     * Create or update an alias for a merchant - NOW USES DB
     * This method allows multiple merchants to map to the same display name (grouping)
     */
    fun setMerchantAlias(originalName: String, displayName: String, category: String): Boolean = runBlocking {
        logger.debug("setMerchantAlias","Setting alias: '$originalName' -> '$displayName' in category '$category' (DB-only)")

        try {
            // Validation
            if (originalName.trim().isEmpty() || displayName.trim().isEmpty() || category.trim().isEmpty()) {
                logger.debug("setMerchantAlias","Invalid parameters")
                return@runBlocking false
            }

            val normalizedName = normalizeMerchantName(originalName)
            val cleanDisplayName = displayName.trim()
            val cleanCategory = category.trim()

            logger.debug("setMerchantAlias","Normalized name: '$normalizedName' -> '$cleanDisplayName'")

            // Update merchant in database
            val success = repository.updateMerchantAliasInDatabase(
                listOf(normalizedName),
                cleanDisplayName,
                cleanCategory
            )

            if (success) {
                logger.debug("setMerchantAlias","Successfully updated merchant in database")
            } else {
                logger.error("setMerchantAlias", "Failed to update merchant in database", null)
            }

            success
        } catch (e: Exception) {
            logger.error("setMerchantAlias","Critical error setting merchant alias",e)
            false
        }
    }
    
    /**
     * Remove an alias for a merchant - NOW USES DB
     * Note: In DB model, we can't truly "remove" a merchant, we reset it to default
     */
    fun removeMerchantAlias(originalName: String): Unit = runBlocking {
        val normalizedName = normalizeMerchantName(originalName)
        logger.debug("removeMerchantAlias","Removing alias for: '$normalizedName' (DB-only)")

        // For now, we'll set it back to default category
        // This is a best-effort approach since DB doesn't have a "remove" concept
        try {
            repository.updateMerchantAliasInDatabase(
                listOf(normalizedName),
                normalizedName, // Reset display name to original
                "Other" // Reset to default category
            )
            logger.debug("removeMerchantAlias","Reset merchant to defaults")
        } catch (e: Exception) {
            logger.error("removeMerchantAlias","Error removing alias",e)
        }
    }

    /**
     * Get all merchants that map to the same display name - NOW USES DB
     * This handles cases where multiple original names map to the same group
     */
    fun getMerchantsByDisplayName(displayName: String): List<String> = runBlocking {
        try {
            val allMerchants = repository.getAllMerchants()
            allMerchants
                .filter { it.displayName == displayName }
                .map { it.normalizedName }
        } catch (e: Exception) {
            logger.error("getMerchantsByDisplayName","Error getting merchants by display name",e)
            emptyList()
        }
    }
    
    /**
     * Check if setting this alias would create a conflict
     * Returns conflict details for UI to handle
     */
    data class AliasConflict(
        val type: ConflictType,
        val existingDisplayName: String?,
        val existingCategory: String?,
        val newDisplayName: String,
        val newCategory: String,
        val affectedMerchants: List<String>
    )
    
    enum class ConflictType {
        NONE,                    // No conflict
        DISPLAY_NAME_EXISTS,     // Display name already exists for other merchants
        CATEGORY_MISMATCH,       // Same display name but different category
        OVERWRITE_EXISTING       // Would overwrite existing alias for this merchant
    }
    
    fun checkAliasConflict(originalName: String, displayName: String, category: String): AliasConflict = runBlocking {
        val normalizedName = normalizeMerchantName(originalName)
        val cleanDisplayName = displayName.trim()
        val cleanCategory = category.trim()

        logger.debug("checkAliasConflict","Checking alias conflict for: '$originalName' -> '$cleanDisplayName' in '$cleanCategory' (DB-only)")

        // Enhanced validation
        if (cleanDisplayName.isEmpty() || cleanCategory.isEmpty()) {
            logger.debug("checkAliasConflict","Empty parameters provided")
            return@runBlocking AliasConflict(
                type = ConflictType.NONE,
                existingDisplayName = null,
                existingCategory = null,
                newDisplayName = cleanDisplayName,
                newCategory = cleanCategory,
                affectedMerchants = emptyList()
            )
        }

        try {
            // Get existing merchant from DB
            val existingMerchant = repository.getMerchantWithCategory(normalizedName)

            if (existingMerchant != null) {
                logger.debug("checkAliasConflict","Found existing merchant: '${existingMerchant.display_name}' -> '${existingMerchant.category_name}'")

                if (existingMerchant.display_name != cleanDisplayName || existingMerchant.category_name != cleanCategory) {
                    logger.debug("checkAliasConflict","OVERWRITE_EXISTING detected")
                    return@runBlocking AliasConflict(
                        type = ConflictType.OVERWRITE_EXISTING,
                        existingDisplayName = existingMerchant.display_name,
                        existingCategory = existingMerchant.category_name,
                        newDisplayName = cleanDisplayName,
                        newCategory = cleanCategory,
                        affectedMerchants = listOf(originalName)
                    )
                } else {
                    logger.debug("checkAliasConflict","No changes detected")
                    return@runBlocking AliasConflict(
                        type = ConflictType.NONE,
                        existingDisplayName = null,
                        existingCategory = null,
                        newDisplayName = cleanDisplayName,
                        newCategory = cleanCategory,
                        affectedMerchants = emptyList()
                    )
                }
            }

            // Check if display name exists for other merchants
            val allMerchants = repository.getAllMerchants()
            val merchantsWithSameDisplayName = allMerchants.filter {
                it.displayName == cleanDisplayName && it.normalizedName != normalizedName
            }

            if (merchantsWithSameDisplayName.isNotEmpty()) {
                logger.debug("checkAliasConflict","Found ${merchantsWithSameDisplayName.size} merchants with display name '$cleanDisplayName'")

                // Get unique categories
                val categories = merchantsWithSameDisplayName.map { it.categoryId }.distinct()

                // For simplicity, we'll allow grouping
                // Note: We can't easily check category name without loading all categories
                logger.debug("checkAliasConflict","NONE - grouping allowed")
                return@runBlocking AliasConflict(
                    type = ConflictType.NONE,
                    existingDisplayName = null,
                    existingCategory = null,
                    newDisplayName = cleanDisplayName,
                    newCategory = cleanCategory,
                    affectedMerchants = merchantsWithSameDisplayName.map { it.normalizedName }
                )
            }

            // No conflict
            logger.debug("checkAliasConflict","NONE - no conflicts")
            AliasConflict(
                type = ConflictType.NONE,
                existingDisplayName = null,
                existingCategory = null,
                newDisplayName = cleanDisplayName,
                newCategory = cleanCategory,
                affectedMerchants = emptyList()
            )
        } catch (e: Exception) {
            logger.error("checkAliasConflict","Error checking conflict",e)
            AliasConflict(
                type = ConflictType.NONE,
                existingDisplayName = null,
                existingCategory = null,
                newDisplayName = cleanDisplayName,
                newCategory = cleanCategory,
                affectedMerchants = emptyList()
            )
        }
    }
    
    /**
     * Get the canonical merchant name for grouping
     * This ensures all variations of a merchant (e.g., "SWIGGY", "Swiggy*Order") 
     * are grouped under the same display name
     */
    fun getCanonicalMerchantName(originalName: String): String {
        return getDisplayName(originalName)
    }
    
    /**
     * Check if a merchant has a custom alias - NOW USES DB
     */
    fun hasAlias(originalName: String): Boolean = runBlocking {
        val normalizedName = normalizeMerchantName(originalName)
        try {
            repository.getMerchantWithCategory(normalizedName) != null
        } catch (e: Exception) {
            logger.error("hasAlias","Error checking alias",e)
            false
        }
    }

    /**
     * Get all stored aliases - NOW USES DB
     * Note: MerchantEntity doesn't have category info, so we need to fetch it separately
     */
    fun getAllAliases(): Map<String, MerchantAlias> = runBlocking {
        try {
            val merchants = repository.getAllMerchants()
            merchants.associate { merchant ->
                // Get category info for this merchant
                val merchantWithCategory = repository.getMerchantWithCategory(merchant.normalizedName)
                merchant.normalizedName to MerchantAlias(
                    originalName = merchant.normalizedName,
                    displayName = merchant.displayName,
                    category = merchantWithCategory?.category_name ?: "Other",
                    categoryColor = merchantWithCategory?.category_color ?: "#9e9e9e"
                )
            }
        } catch (e: Exception) {
            logger.error("getAllAliases","Error getting all aliases",e)
            emptyMap()
        }
    }

    /**
     * Clear all aliases (for testing/reset) - NOW NO-OP
     * Note: In DB model, we don't clear merchants, they remain with default values
     */
    fun clearAllAliases() {
        logger.debug("clearAllAliases","Clear all aliases is a no-op in DB-only mode")
        // DB model doesn't support clearing merchants
        // This method is kept for backward compatibility but does nothing
    }

    /**
     * Invalidate cache to force reload - NOW NO-OP
     * Note: DB queries are always fresh, no cache to invalidate
     */
    fun invalidateCache() {
        logger.debug("invalidateCache","Invalidate cache is a no-op in DB-only mode")
        // DB-backed implementation doesn't use cache
        // This method is kept for backward compatibility but does nothing
    }
    
    /**
     * Normalize merchant name for consistent mapping
     * Enhanced to preserve more identity while still grouping similar merchants
     */
    fun normalizeMerchantName(name: String): String {
        val cleaned = name.uppercase()
            .replace(Regex("\\s+"), " ") // Normalize spaces first
            .trim()
        
        // Enhanced normalization - remove company suffixes and transaction artifacts
        val normalized = cleaned
            .replace(Regex("\\*(ORDER|PAYMENT|TXN|TRANSACTION).*$"), "") // Remove order/payment suffixes
            .replace(Regex("#\\d+.*$"), "") // Remove transaction numbers
            .replace(Regex("@\\w+.*$"), "") // Remove @ suffixes
            .replace(Regex("-{2,}.*$"), "") // Remove double dashes and everything after
            .replace(Regex("_{2,}.*$"), "") // Remove double underscores and everything after
            // Remove common company suffixes to group similar merchants
            .replace(Regex("\\s+(PVT\\s+)?LTD\\.?$"), "") // Remove LTD, PVT LTD
            .replace(Regex("\\s+LIMITED$"), "") // Remove LIMITED
            .replace(Regex("\\s+PRIVATE\\s+LIMITED$"), "") // Remove PRIVATE LIMITED
            .replace(Regex("\\s+(LLC|INC|CORP)\\.?$"), "") // Remove LLC, INC, CORP
            .trim()

        logger.debug("normalizeMerchantName","'$name' -> '$normalized'")
        return normalized
    }
    
    /**
     * Get a more aggressive normalized name for grouping similar merchants
     * This is used when user wants to group merchants that should be the same
     */
    fun getAggressiveNormalizedName(name: String): String {
        return name.uppercase()
            .replace(Regex("[*#@\\-_]+.*"), "") // Remove suffixes after special chars
            .replace(Regex("\\s+"), " ") // Normalize spaces
            .replace(Regex("\\b(BANGALORE|MUMBAI|DELHI|CHENNAI|HYDERABAD|PUNE)\\b"), "") // Remove city names
            .replace(Regex("\\b(PVT|LTD|LLC|INC|CORP)\\b"), "") // Remove company suffixes
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}