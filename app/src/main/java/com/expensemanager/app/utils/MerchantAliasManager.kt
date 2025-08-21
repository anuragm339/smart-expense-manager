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
        return getAlias(normalizedName)?.displayName ?: originalMerchantName
    }
    
    /**
     * Get the category for a merchant (from alias or default categorization)
     */
    fun getMerchantCategory(originalMerchantName: String): String {
        val normalizedName = normalizeMerchantName(originalMerchantName)
        val alias = getAlias(normalizedName)
        return alias?.category ?: categoryManager.categorizeTransaction(originalMerchantName)
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
    fun setMerchantAlias(originalName: String, displayName: String, category: String) {
        val normalizedName = normalizeMerchantName(originalName)
        val categoryColor = categoryManager.getCategoryColor(category)
        
        val alias = MerchantAlias(
            originalName = normalizedName,
            displayName = displayName,
            category = category,
            categoryColor = categoryColor
        )
        
        val aliases = getAllAliases().toMutableMap()
        aliases[normalizedName] = alias
        saveAliases(aliases)
        
        // Update cache
        aliasCache = aliases
        
        Log.d(TAG, "Set alias: $normalizedName -> $displayName ($category)")
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
        
        Log.d(TAG, "Removed alias for: $normalizedName")
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
            val aliasesJson = convertAliasesToJson(aliases)
            prefs.edit().putString(KEY_ALIASES, aliasesJson).apply()
            Log.d(TAG, "Saved ${aliases.size} merchant aliases")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving aliases", e)
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
    private fun normalizeMerchantName(name: String): String {
        return name.uppercase()
            .replace(Regex("[*#@\\-_]+.*"), "") // Remove suffixes after special chars
            .replace(Regex("\\s+"), " ") // Normalize spaces
            .trim()
    }
}