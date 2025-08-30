package com.expensemanager.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
        private const val TAG = "MerchantAliasManager"
        private const val PREFS_NAME = "merchant_aliases"
        private const val KEY_ALIASES = "aliases"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val categoryManager = CategoryManager(context)
    
    // Cache for performance
    private var aliasCache: MutableMap<String, MerchantAlias>? = null
    
    /**
     * Get the display name for a merchant (original name or alias)
     */
    fun getDisplayName(originalMerchantName: String): String {
        val normalizedName = normalizeMerchantName(originalMerchantName)
        val alias = getAlias(normalizedName)
        val displayName = alias?.displayName ?: originalMerchantName
        
        
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
     */
    fun setMerchantAlias(originalName: String, displayName: String, category: String): Boolean {
        return try {
            // Enhanced validation
            if (originalName.trim().isEmpty()) {
                return false
            }
            
            if (displayName.trim().isEmpty()) {
                return false
            }
            
            if (category.trim().isEmpty()) {
                return false
            }
            
            val normalizedName = normalizeMerchantName(originalName)
            
            // Get category color with fallback
            var categoryColor = categoryManager.getCategoryColor(category)
            if (categoryColor.isEmpty() || categoryColor == "#000000") {
                categoryColor = "#4CAF50" // Default green
            }
            
            val alias = MerchantAlias(
                originalName = normalizedName,
                displayName = displayName.trim(),
                category = category.trim(),
                categoryColor = categoryColor
            )
            
            // Get current aliases with error handling
            val aliases = try {
                getAllAliases().toMutableMap()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading existing aliases, starting fresh", e)
                mutableMapOf<String, MerchantAlias>()
            }
            
            aliases[normalizedName] = alias
            
            // Save aliases with error handling
            val saveSuccess = try {
                saveAliases(aliases)
                true
            } catch (e: Exception) {
                Log.e(TAG, "[ERROR] Failed to save aliases", e)
                false
            }
            
            if (saveSuccess) {
                // Update cache only if save was successful
                aliasCache = aliases
                return true
            } else {
                Log.e(TAG, "Failed to save alias for $normalizedName")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error setting merchant alias", e)
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
        Log.d(TAG, "Cleared all merchant aliases")
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
            Log.d(TAG, "Loaded ${aliasCache?.size ?: 0} merchant aliases")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading aliases", e)
            aliasCache = mutableMapOf()
        }
    }
    
    private fun saveAliases(aliases: Map<String, MerchantAlias>) {
        try {
            Log.d(TAG, "[SAVE] Saving ${aliases.size} merchant aliases to SharedPreferences...")
            
            val aliasesJson = convertAliasesToJson(aliases)
            Log.d(TAG, "📝 Converted aliases to JSON (${aliasesJson.length} chars)")
            
            val editor = prefs.edit()
            val putSuccess = editor.putString(KEY_ALIASES, aliasesJson)
            Log.d(TAG, "📝 Put string to editor: $putSuccess")
            
            val commitSuccess = editor.commit() // Use commit() instead of apply() for immediate error detection
            
            if (commitSuccess) {
                Log.d(TAG, "[SUCCESS] Successfully saved ${aliases.size} merchant aliases")
                
                // Verify the save by reading it back
                val savedJson = prefs.getString(KEY_ALIASES, null)
                if (savedJson != null && savedJson == aliasesJson) {
                    Log.d(TAG, "[DEBUG] Save verification successful")
                } else {
                    Log.w(TAG, "[WARNING] Save verification failed - data may not have persisted correctly")
                    throw Exception("Save verification failed")
                }
            } else {
                Log.e(TAG, "[ERROR] SharedPreferences commit() returned false")
                throw Exception("SharedPreferences commit failed")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error saving aliases to SharedPreferences", e)
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
            Log.e(TAG, "Error parsing aliases JSON", e)
        }
        return aliases
    }
    
    /**
     * Normalize merchant name for consistent mapping
     * Handles variations like "SWIGGY", "Swiggy*Order", "SWIGGY-CHENNAI"
     */
    fun normalizeMerchantName(name: String): String {
        return name.uppercase()
            .replace(Regex("[*#@\\-_]+.*"), "") // Remove suffixes after special chars
            .replace(Regex("\\s+"), " ") // Normalize spaces
            .trim()
    }
}