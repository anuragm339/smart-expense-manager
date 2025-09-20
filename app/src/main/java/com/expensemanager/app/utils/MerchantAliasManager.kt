package com.expensemanager.app.utils

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import org.json.JSONObject
import org.json.JSONException

data class MerchantAlias(
    val originalName: String,
    val displayName: String,
    val category: String,
    val categoryColor: String
)

class MerchantAliasManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "merchant_aliases"
        private const val KEY_ALIASES = "aliases"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val categoryManager = CategoryManager(context)
    
    // Enhanced cache for performance with LRU eviction
    private var aliasCache: MutableMap<String, MerchantAlias>? = null
    private var lastCacheUpdate = 0L
    private val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes
    
    // Category lookup cache to reduce repeated database queries
    private val categoryCache = mutableMapOf<String, String>()
    private var categoryCacheTimestamp = 0L
    
    /**
     * Get the display name for a merchant (original name or alias)
     */
    fun getDisplayName(originalMerchantName: String): String {
        val normalizedName = normalizeMerchantName(originalMerchantName)
        val alias = getAlias(normalizedName)
        
        // Use explicit alias display name if exists, otherwise use enhanced normalized name for better grouping
        val displayName = alias?.displayName ?: normalizedName
        
        Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[DISPLAY] '$originalMerchantName' -> '$displayName' (normalized: '$normalizedName', hasAlias: ${alias != null})")
        return displayName
    }
    
    /**
     * Get the category for a merchant (from alias or default categorization)
     */
    fun getMerchantCategory(originalMerchantName: String): String {
        val normalizedName = normalizeMerchantName(originalMerchantName)
        val alias = getAlias(normalizedName)
        val category = alias?.category ?: categoryManager.categorizeTransaction(originalMerchantName)
        
        
        return category
    }
    
    /**
     * Get the category color for a merchant
     */
    fun getMerchantCategoryColor(originalMerchantName: String): String {
        val normalizedName = normalizeMerchantName(originalMerchantName)
        val alias = getAlias(normalizedName)
        return alias?.categoryColor ?: categoryManager.getCategoryColor(getMerchantCategory(originalMerchantName))
    }
    
    /**
     * Create or update an alias for a merchant
     * This method allows multiple merchants to map to the same display name (grouping)
     */
    fun setMerchantAlias(originalName: String, displayName: String, category: String): Boolean {
        Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[ALIAS] Setting alias: '$originalName' -> '$displayName' in category '$category'")
        
        return try {
            // Enhanced validation
            if (originalName.trim().isEmpty()) {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).e("[ALIAS] Invalid original name: empty")
                return false
            }
            
            if (displayName.trim().isEmpty()) {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).e("[ALIAS] Invalid display name: empty")
                return false
            }
            
            if (category.trim().isEmpty()) {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).e("[ALIAS] Invalid category: empty")
                return false
            }
            
            val normalizedName = normalizeMerchantName(originalName)
            val cleanDisplayName = displayName.trim()
            val cleanCategory = category.trim()
            
            Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[ALIAS] Normalized name: '$normalizedName' -> '$cleanDisplayName'")
            
            // Check for existing aliases and log conflicts
            val existingAliases = getAllAliases()
            val existingSameDisplayName = existingAliases.values.filter { it.displayName == cleanDisplayName }
            
            if (existingSameDisplayName.isNotEmpty()) {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[ALIAS] Found ${existingSameDisplayName.size} existing merchants with display name '$cleanDisplayName'")
                existingSameDisplayName.forEach { existing ->
                    Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[ALIAS] Existing: '${existing.originalName}' -> '${existing.displayName}' (${existing.category})")
                }
            }
            
            // Check if this would create a conflict (same original name with different display name)
            val existingForOriginal = existingAliases[normalizedName]
            if (existingForOriginal != null && existingForOriginal.displayName != cleanDisplayName) {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).w("[ALIAS] Overwriting existing alias for '$normalizedName': '${existingForOriginal.displayName}' -> '$cleanDisplayName'")
            }
            
            // Get category color with fallback
            var categoryColor = categoryManager.getCategoryColor(cleanCategory)
            if (categoryColor.isEmpty() || categoryColor == "#000000") {
                categoryColor = "#4CAF50" // Default green
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[ALIAS] Using default color for category '$cleanCategory'")
            }
            
            val alias = MerchantAlias(
                originalName = normalizedName,
                displayName = cleanDisplayName,
                category = cleanCategory,
                categoryColor = categoryColor
            )
            
            // Get current aliases with error handling
            val aliases = try {
                getAllAliases().toMutableMap()
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).e(e, "Error loading existing aliases, starting fresh")
                mutableMapOf<String, MerchantAlias>()
            }
            
            // Set the alias (this allows multiple original names to map to same display name)
            aliases[normalizedName] = alias
            Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[ALIAS] Added alias to collection. Total aliases: ${aliases.size}")
            
            // Save aliases with error handling
            val saveSuccess = try {
                saveAliases(aliases)
                true
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).e(e, "[ERROR] Failed to save aliases")
                false
            }
            
            if (saveSuccess) {
                // Force cache invalidation and fresh reload to ensure immediate consistency
                aliasCache = null
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CACHE_FIX] Invalidated cache to force fresh reload")
                
                // Force immediate reload to verify the data
                loadAliases()
                val verificationAlias = aliasCache?.get(normalizedName)
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CACHE_FIX] Verification after reload: '$normalizedName' -> '${verificationAlias?.displayName}' (expected: '$cleanDisplayName')")
                
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[ALIAS] Successfully set alias for '$normalizedName' -> '$cleanDisplayName'")
                return true
            } else {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).e("[ALIAS] Failed to save alias for $normalizedName")
                return false
            }
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.MERCHANT).e(e, "Critical error setting merchant alias")
            false
        }
    }
    
    /**
     * Remove an alias for a merchant
     */
    fun removeMerchantAlias(originalName: String) {
        val normalizedName = normalizeMerchantName(originalName)
        val aliases = getAllAliases().toMutableMap()
        aliases.remove(normalizedName)
        saveAliases(aliases)
        
        // Update cache
        aliasCache = aliases
        
    }
    
    /**
     * Get all merchants that map to the same display name
     * This handles cases where multiple original names map to the same group
     */
    fun getMerchantsByDisplayName(displayName: String): List<String> {
        return getAllAliases().values
            .filter { it.displayName == displayName }
            .map { it.originalName }
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
    
    fun checkAliasConflict(originalName: String, displayName: String, category: String): AliasConflict {
        val normalizedName = normalizeMerchantName(originalName)
        val cleanDisplayName = displayName.trim()
        val cleanCategory = category.trim()
        val existingAliases = getAllAliases()
        
        Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] Checking alias conflict for: '$originalName' -> '$cleanDisplayName' in '$cleanCategory'")
        Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] Normalized name: '$normalizedName', existing aliases count: ${existingAliases.size}")
        
        // Enhanced validation
        if (cleanDisplayName.isEmpty()) {
            Timber.tag(LogConfig.FeatureTags.MERCHANT).w("[CONFLICT_CHECK] Empty display name provided")
            return AliasConflict(
                type = ConflictType.NONE,
                existingDisplayName = null,
                existingCategory = null,
                newDisplayName = cleanDisplayName,
                newCategory = cleanCategory,
                affectedMerchants = emptyList()
            )
        }
        
        if (cleanCategory.isEmpty()) {
            Timber.tag(LogConfig.FeatureTags.MERCHANT).w("[CONFLICT_CHECK] Empty category provided")
            return AliasConflict(
                type = ConflictType.NONE,
                existingDisplayName = null,
                existingCategory = null,
                newDisplayName = cleanDisplayName,
                newCategory = cleanCategory,
                affectedMerchants = emptyList()
            )
        }
        
        // Check if this merchant already has an alias
        val existingForOriginal = existingAliases[normalizedName]
        if (existingForOriginal != null) {
            Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] Found existing alias for '$normalizedName': '${existingForOriginal.displayName}' -> '${existingForOriginal.category}'")
            
            if (existingForOriginal.displayName != cleanDisplayName) {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] OVERWRITE_EXISTING detected: changing display name from '${existingForOriginal.displayName}' to '$cleanDisplayName'")
                return AliasConflict(
                    type = ConflictType.OVERWRITE_EXISTING,
                    existingDisplayName = existingForOriginal.displayName,
                    existingCategory = existingForOriginal.category,
                    newDisplayName = cleanDisplayName,
                    newCategory = cleanCategory,
                    affectedMerchants = listOf(originalName)
                )
            } else if (existingForOriginal.category != cleanCategory) {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] OVERWRITE_EXISTING detected: changing category from '${existingForOriginal.category}' to '$cleanCategory'")
                return AliasConflict(
                    type = ConflictType.OVERWRITE_EXISTING,
                    existingDisplayName = existingForOriginal.displayName,
                    existingCategory = existingForOriginal.category,
                    newDisplayName = cleanDisplayName,
                    newCategory = cleanCategory,
                    affectedMerchants = listOf(originalName)
                )
            } else {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] No changes detected for existing alias")
                return AliasConflict(
                    type = ConflictType.NONE,
                    existingDisplayName = null,
                    existingCategory = null,
                    newDisplayName = cleanDisplayName,
                    newCategory = cleanCategory,
                    affectedMerchants = emptyList()
                )
            }
        }
        
        // Check if display name already exists for other merchants
        val existingSameDisplayName = existingAliases.values.filter { 
            it.displayName == cleanDisplayName && it.originalName != normalizedName 
        }
        
        if (existingSameDisplayName.isNotEmpty()) {
            Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] Found ${existingSameDisplayName.size} existing merchants with display name '$cleanDisplayName'")
            existingSameDisplayName.forEach { existing ->
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] - '${existing.originalName}' -> '${existing.displayName}' (${existing.category})")
            }
            
            // Check if all existing merchants with this display name have the same category
            val existingCategories = existingSameDisplayName.map { it.category }.distinct()
            Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] Existing categories: $existingCategories, new category: '$cleanCategory'")
            
            if (existingCategories.size == 1 && existingCategories.first() == cleanCategory) {
                // Same category - this is fine, it's just grouping merchants
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] NONE - same category grouping allowed")
                return AliasConflict(
                    type = ConflictType.NONE,
                    existingDisplayName = null,
                    existingCategory = null,
                    newDisplayName = cleanDisplayName,
                    newCategory = cleanCategory,
                    affectedMerchants = existingSameDisplayName.map { it.originalName }
                )
            } else {
                // Different categories - potential conflict
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] CATEGORY_MISMATCH detected - different categories: ${existingCategories.joinToString(", ")} vs '$cleanCategory'")
                return AliasConflict(
                    type = ConflictType.CATEGORY_MISMATCH,
                    existingDisplayName = cleanDisplayName,
                    existingCategory = existingCategories.joinToString(", "),
                    newDisplayName = cleanDisplayName,
                    newCategory = cleanCategory,
                    affectedMerchants = existingSameDisplayName.map { it.originalName }
                )
            }
        }
        
        // No conflict
        Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[CONFLICT_CHECK] NONE - no conflicts detected")
        return AliasConflict(
            type = ConflictType.NONE,
            existingDisplayName = null,
            existingCategory = null,
            newDisplayName = cleanDisplayName,
            newCategory = cleanCategory,
            affectedMerchants = emptyList()
        )
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
     * Check if a merchant has a custom alias
     */
    fun hasAlias(originalName: String): Boolean {
        val normalizedName = normalizeMerchantName(originalName)
        return getAlias(normalizedName) != null
    }
    
    /**
     * Get all stored aliases
     */
    fun getAllAliases(): Map<String, MerchantAlias> {
        if (aliasCache == null) {
            loadAliases()
        }
        return aliasCache ?: emptyMap()
    }
    
    /**
     * Clear all aliases (for testing/reset)
     */
    fun clearAllAliases() {
        prefs.edit().clear().apply()
        aliasCache = mutableMapOf()
        categoryCache.clear()
        lastCacheUpdate = 0L
        categoryCacheTimestamp = 0L
        Timber.tag(LogConfig.FeatureTags.MERCHANT).d("Cleared all merchant aliases and caches")
    }
    
    /**
     * Invalidate cache to force reload
     */
    fun invalidateCache() {
        aliasCache = null
        categoryCache.clear()
        lastCacheUpdate = 0L
        categoryCacheTimestamp = 0L
        Timber.tag(LogConfig.FeatureTags.MERCHANT).d("Invalidated merchant alias caches")
    }
    
    /**
     * Check if cache is valid
     */
    private fun isCacheValid(): Boolean {
        return aliasCache != null && (System.currentTimeMillis() - lastCacheUpdate) < CACHE_VALIDITY_MS
    }
    
    private fun getAlias(normalizedName: String): MerchantAlias? {
        if (aliasCache == null) {
            loadAliases()
        }
        return aliasCache?.get(normalizedName)
    }
    
    private fun loadAliases() {
        try {
            val aliasesJson = prefs.getString(KEY_ALIASES, null)
            if (aliasesJson != null) {
                aliasCache = parseAliasesFromJson(aliasesJson).toMutableMap()
            } else {
                aliasCache = mutableMapOf()
            }
            Timber.tag(LogConfig.FeatureTags.MERCHANT).d("Loaded ${aliasCache?.size ?: 0} merchant aliases")
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.MERCHANT).e(e, "Error loading aliases")
            aliasCache = mutableMapOf()
        }
    }
    
    private fun saveAliases(aliases: Map<String, MerchantAlias>) {
        try {
            Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[SAVE] Saving ${aliases.size} merchant aliases to SharedPreferences...")
            
            val aliasesJson = convertAliasesToJson(aliases)
            Timber.tag(LogConfig.FeatureTags.MERCHANT).d("Converted aliases to JSON (${aliasesJson.length} chars)")
            
            val editor = prefs.edit()
            val putSuccess = editor.putString(KEY_ALIASES, aliasesJson)
            Timber.tag(LogConfig.FeatureTags.MERCHANT).d("Put string to editor: $putSuccess")
            
            val commitSuccess = editor.commit() // Use commit() instead of apply() for immediate error detection
            
            if (commitSuccess) {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[SUCCESS] Successfully saved ${aliases.size} merchant aliases")
                
                // Verify the save by reading it back
                val savedJson = prefs.getString(KEY_ALIASES, null)
                if (savedJson != null && savedJson == aliasesJson) {
                    Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[DEBUG] Save verification successful")
                } else {
                    Timber.tag(LogConfig.FeatureTags.MERCHANT).w("[WARNING] Save verification failed - data may not have persisted correctly")
                    throw Exception("Save verification failed")
                }
            } else {
                Timber.tag(LogConfig.FeatureTags.MERCHANT).e("[ERROR] SharedPreferences commit() returned false")
                throw Exception("SharedPreferences commit failed")
            }
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.MERCHANT).e(e, "Error saving aliases to SharedPreferences")
            throw e // Re-throw to let caller handle
        }
    }
    
    private fun convertAliasesToJson(aliases: Map<String, MerchantAlias>): String {
        val jsonObject = JSONObject()
        aliases.forEach { (key, alias) ->
            val aliasJson = JSONObject().apply {
                put("originalName", alias.originalName)
                put("displayName", alias.displayName)
                put("category", alias.category)
                put("categoryColor", alias.categoryColor)
            }
            jsonObject.put(key, aliasJson)
        }
        return jsonObject.toString()
    }
    
    private fun parseAliasesFromJson(jsonString: String): Map<String, MerchantAlias> {
        val aliases = mutableMapOf<String, MerchantAlias>()
        try {
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val aliasJson = jsonObject.getJSONObject(key)
                val alias = MerchantAlias(
                    originalName = aliasJson.getString("originalName"),
                    displayName = aliasJson.getString("displayName"),
                    category = aliasJson.getString("category"),
                    categoryColor = aliasJson.getString("categoryColor")
                )
                aliases[key] = alias
            }
        } catch (e: JSONException) {
            Timber.tag(LogConfig.FeatureTags.MERCHANT).e(e, "Error parsing aliases JSON")
        }
        return aliases
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
        
        Timber.tag(LogConfig.FeatureTags.MERCHANT).d("[NORMALIZE] '$name' -> '$normalized'")
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