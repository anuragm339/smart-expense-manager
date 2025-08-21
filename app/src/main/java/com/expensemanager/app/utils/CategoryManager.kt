package com.expensemanager.app.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CategoryRule(
    val merchantPattern: String,
    val category: String,
    val isLearned: Boolean = false // User taught vs pre-defined
)

class CategoryManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("category_rules", Context.MODE_PRIVATE)
    
    companion object {
        // Default categorization rules
        private val DEFAULT_RULES = mapOf(
            "food_dining" to listOf(
                "swiggy", "zomato", "ubereats", "dominos", "pizza", "mcdonald", "kfc",
                "restaurant", "cafe", "food", "dining", "kitchen", "meal", "biryani"
            ),
            "transportation" to listOf(
                "uber", "ola", "metro", "taxi", "cab", "bus", "train", "railway",
                "irctc", "petrol", "diesel", "fuel", "parking"
            ),
            "groceries" to listOf(
                "bigbazaar", "dmart", "reliance", "grocery", "supermarket", "bazaar",
                "fresh", "mart", "store", "provisions", "vegetables", "fruits"
            ),
            "healthcare" to listOf(
                "apollo", "hospital", "clinic", "pharmacy", "medical", "doctor",
                "medicine", "health", "lab", "diagnostic", "care"
            ),
            "shopping" to listOf(
                "amazon", "flipkart", "myntra", "ajio", "nykaa", "shopping",
                "mall", "brand", "fashion", "clothes", "electronics", "centre", "center", 
                "store", "mart", "retail", "sansar", "bazar", "market"
            ),
            "entertainment" to listOf(
                "bookmyshow", "paytm", "movie", "cinema", "netflix", "spotify",
                "game", "entertainment", "ticket", "event"
            ),
            "utilities" to listOf(
                "electricity", "water", "gas", "internet", "mobile", "recharge",
                "bill", "utility", "broadband", "postpaid"
            )
        )
    }
    
    fun categorizeTransaction(merchant: String): String {
        val merchantLower = merchant.lowercase().trim()
        
        // First check learned rules (user corrections)
        val learnedCategory = getLearnedCategory(merchantLower)
        if (learnedCategory != null) {
            return learnedCategory
        }
        
        // Then check default rules
        for ((category, patterns) in DEFAULT_RULES) {
            if (patterns.any { pattern -> merchantLower.contains(pattern) }) {
                return formatCategoryName(category)
            }
        }
        
        return "Other"
    }
    
    suspend fun updateCategory(merchant: String, newCategory: String) = withContext(Dispatchers.IO) {
        val merchantKey = merchant.lowercase().trim()
        val editor = prefs.edit()
        
        // Save learned rule
        editor.putString("learned_$merchantKey", newCategory)
        editor.apply()
        
        // Also update similar merchants
        updateSimilarMerchants(merchantKey, newCategory)
    }
    
    suspend fun getAllSimilarTransactions(merchant: String): List<String> = withContext(Dispatchers.IO) {
        val merchantLower = merchant.lowercase().trim()
        val similarMerchants = mutableListOf<String>()
        
        // Find similar merchant names (fuzzy matching)
        val allKeys = prefs.all.keys
        for (key in allKeys) {
            if (key.startsWith("learned_")) {
                val savedMerchant = key.removePrefix("learned_")
                if (isSimilarMerchant(merchantLower, savedMerchant)) {
                    similarMerchants.add(savedMerchant)
                }
            }
        }
        
        return@withContext similarMerchants
    }
    
    private fun getLearnedCategory(merchant: String): String? {
        return prefs.getString("learned_$merchant", null)
    }
    
    private suspend fun updateSimilarMerchants(merchant: String, category: String) = withContext(Dispatchers.IO) {
        val editor = prefs.edit()
        
        // Find and update similar merchant patterns
        val merchantWords = merchant.split(" ")
        val allKeys = prefs.all.keys.toList()
        
        for (key in allKeys) {
            if (key.startsWith("learned_")) {
                val savedMerchant = key.removePrefix("learned_")
                if (isSimilarMerchant(merchant, savedMerchant)) {
                    editor.putString(key, category)
                }
            }
        }
        
        editor.apply()
    }
    
    private fun isSimilarMerchant(merchant1: String, merchant2: String): Boolean {
        if (merchant1 == merchant2) return true
        
        val words1 = merchant1.split(" ", "-", "_").filter { it.length > 2 }
        val words2 = merchant2.split(" ", "-", "_").filter { it.length > 2 }
        
        // Check if they share significant words
        val commonWords = words1.intersect(words2.toSet())
        val similarityRatio = commonWords.size.toFloat() / maxOf(words1.size, words2.size)
        
        return similarityRatio >= 0.5 // 50% similarity threshold
    }
    
    private fun formatCategoryName(category: String): String {
        return when (category) {
            "food_dining" -> "Food & Dining"
            "transportation" -> "Transportation"
            "groceries" -> "Groceries"
            "healthcare" -> "Healthcare"
            "shopping" -> "Shopping"
            "entertainment" -> "Entertainment"
            "utilities" -> "Utilities"
            else -> "Other"
        }
    }
    
    fun getCategoryColor(category: String): String {
        return when (category) {
            "Food & Dining" -> "#ff5722"
            "Transportation" -> "#3f51b5"
            "Healthcare" -> "#e91e63"
            "Groceries" -> "#4caf50"
            "Shopping" -> "#ff9800"
            "Entertainment" -> "#9c27b0"
            "Utilities" -> "#607d8b"
            else -> {
                // For custom categories, generate a consistent color based on the category name
                val colorList = listOf(
                    "#795548", "#e91e63", "#9c27b0", "#673ab7", "#3f51b5", 
                    "#2196f3", "#03a9f4", "#00bcd4", "#009688", "#4caf50", 
                    "#8bc34a", "#cddc39", "#ffeb3b", "#ffc107", "#ff9800", 
                    "#ff5722", "#f44336"
                )
                val index = kotlin.math.abs(category.hashCode()) % colorList.size
                colorList[index]
            }
        }
    }
    
    fun getAllCategories(): List<String> {
        val defaultCategories = listOf(
            "Food & Dining",
            "Transportation", 
            "Groceries",
            "Healthcare",
            "Shopping",
            "Entertainment",
            "Utilities",
            "Other"
        )
        
        // Get custom categories from SharedPreferences
        val customCategories = getCustomCategories()
        
        // Return combined list (default + custom)
        return defaultCategories + customCategories
    }
    
    fun addCustomCategory(categoryName: String) {
        val customCategories = getCustomCategories().toMutableList()
        if (!customCategories.contains(categoryName) && !getDefaultCategories().contains(categoryName)) {
            customCategories.add(categoryName)
            saveCustomCategories(customCategories)
        }
    }
    
    fun removeCustomCategory(categoryName: String) {
        val customCategories = getCustomCategories().toMutableList()
        if (customCategories.remove(categoryName)) {
            saveCustomCategories(customCategories)
        }
    }
    
    private fun getDefaultCategories(): List<String> {
        return listOf(
            "Food & Dining",
            "Transportation", 
            "Groceries",
            "Healthcare",
            "Shopping",
            "Entertainment",
            "Utilities",
            "Other"
        )
    }
    
    private fun getCustomCategories(): List<String> {
        val customCategoriesJson = prefs.getString("custom_categories", "[]")
        return try {
            val jsonArray = org.json.JSONArray(customCategoriesJson ?: "[]")
            val categories = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                categories.add(jsonArray.getString(i))
            }
            categories
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveCustomCategories(categories: List<String>) {
        val jsonArray = org.json.JSONArray()
        categories.forEach { jsonArray.put(it) }
        prefs.edit().putString("custom_categories", jsonArray.toString()).apply()
    }
}