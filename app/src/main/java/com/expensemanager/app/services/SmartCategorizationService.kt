package com.expensemanager.app.services

import android.content.Context
import com.expensemanager.app.data.repository.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart categorization service that learns from user behavior
 * to provide better category suggestions over time
 */
@Singleton
class SmartCategorizationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository,
    
) {
    
    companion object {
        private const val TAG = "SmartCategorizationService"
    }
    
    // Machine learning patterns based on merchant names
    private val foodKeywords = setOf(
        "restaurant", "cafe", "pizza", "burger", "food", "dining", "eat", "kitchen",
        "swiggy", "zomato", "dominos", "mcdonald", "kfc", "subway", "dunkin"
    )
    
    private val transportKeywords = setOf(
        "uber", "ola", "taxi", "bus", "metro", "train", "petrol", "fuel", "parking",
        "transport", "travel", "cab", "auto", "rickshaw"
    )
    
    private val shoppingKeywords = setOf(
        "amazon", "flipkart", "myntra", "ajio", "shopping", "mall", "store", "market",
        "clothes", "fashion", "electronics", "mobile", "laptop"
    )
    
    private val groceryKeywords = setOf(
        "grocery", "supermarket", "mart", "vegetables", "fruits", "milk", "bread",
        "reliance", "dmart", "bigbazaar", "more", "fresh"
    )
    
    /**
     * Get smart category suggestions for a merchant
     */
    suspend fun getCategorySuggestions(merchantName: String): List<CategorySuggestion> = withContext(Dispatchers.IO) {
        try {
            val suggestions = mutableListOf<CategorySuggestion>()
            val merchantLower = merchantName.lowercase()
            
            // Rule-based suggestions with confidence scores
            val ruleBasedSuggestions = getRuleBasedSuggestions(merchantLower)
            suggestions.addAll(ruleBasedSuggestions)
            
            // Learning-based suggestions from user history
            val learningBasedSuggestions = getLearningBasedSuggestions(merchantName)
            suggestions.addAll(learningBasedSuggestions)
            
            // Similar merchant suggestions
            val similarMerchantSuggestions = getSimilarMerchantSuggestions(merchantName)
            suggestions.addAll(similarMerchantSuggestions)
            
            // Sort by confidence and return top 3
            suggestions.sortedByDescending { it.confidence }.take(3)
            
        } catch (e: Exception) {
            // Logging removed
            getDefaultSuggestions()
        }
    }
    
    /**
     * Rule-based suggestions using keyword matching
     */
    private fun getRuleBasedSuggestions(merchantLower: String): List<CategorySuggestion> {
        val suggestions = mutableListOf<CategorySuggestion>()
        
        when {
            foodKeywords.any { merchantLower.contains(it) } -> {
                suggestions.add(CategorySuggestion("Food & Dining", 0.9f, "Keyword match"))
            }
            transportKeywords.any { merchantLower.contains(it) } -> {
                suggestions.add(CategorySuggestion("Transportation", 0.9f, "Keyword match"))
            }
            shoppingKeywords.any { merchantLower.contains(it) } -> {
                suggestions.add(CategorySuggestion("Shopping", 0.85f, "Keyword match"))
            }
            groceryKeywords.any { merchantLower.contains(it) } -> {
                suggestions.add(CategorySuggestion("Groceries", 0.9f, "Keyword match"))
            }
        }
        
        return suggestions
    }
    
    /**
     * Learning-based suggestions from user's transaction history
     */
    private suspend fun getLearningBasedSuggestions(merchantName: String): List<CategorySuggestion> {
        return try {
            // Get similar merchants that the user has already categorized
            val similarMerchants = repository.getAllMerchants().filter { merchant ->
                val similarity = calculateMerchantSimilarity(merchantName, merchant.displayName)
                similarity > 0.6f
            }
            
            // Find most commonly used categories for similar merchants
            val categoryFrequency = mutableMapOf<String, Int>()
            
            for (merchant in similarMerchants) {
                val merchantCategory = repository.getMerchantWithCategory(merchant.normalizedName)
                merchantCategory?.category_name?.let { category ->
                    categoryFrequency[category] = categoryFrequency.getOrDefault(category, 0) + 1
                }
            }
            
            // Convert to suggestions with confidence based on frequency
            categoryFrequency.map { (category, frequency) ->
                val confidence = (frequency.toFloat() / similarMerchants.size).coerceAtMost(0.8f)
                CategorySuggestion(category, confidence, "User history")
            }
            
        } catch (e: Exception) {
            // Logging removed
            emptyList()
        }
    }
    
    /**
     * Get suggestions based on similar merchants
     */
    private suspend fun getSimilarMerchantSuggestions(merchantName: String): List<CategorySuggestion> {
        return try {
            // Find merchants with similar normalized names
            val normalizedMerchant = merchantName.uppercase().replace(Regex("[*#@\\-_]+.*"), "").trim()
            val allMerchants = repository.getAllMerchants()
            
            val similarMerchants = allMerchants.filter { merchant ->
                val merchantNormalized = merchant.normalizedName.replace(Regex("[*#@\\-_]+.*"), "").trim()
                merchantNormalized.startsWith(normalizedMerchant.take(5)) || 
                normalizedMerchant.startsWith(merchantNormalized.take(5))
            }
            
            if (similarMerchants.isNotEmpty()) {
                // Get the most common category among similar merchants
                val categories = similarMerchants.mapNotNull { merchant ->
                    repository.getMerchantWithCategory(merchant.normalizedName)?.category_name
                }
                
                val mostCommonCategory = categories.groupBy { it }.maxByOrNull { it.value.size }?.key
                
                mostCommonCategory?.let { category ->
                    listOf(CategorySuggestion(category, 0.7f, "Similar merchants"))
                } ?: emptyList()
            } else {
                emptyList()
            }
            
        } catch (e: Exception) {
            // Logging removed
            emptyList()
        }
    }
    
    /**
     * Calculate similarity between two merchant names
     */
    private fun calculateMerchantSimilarity(name1: String, name2: String): Float {
        val normalized1 = name1.lowercase().replace(Regex("[^a-z0-9]"), "")
        val normalized2 = name2.lowercase().replace(Regex("[^a-z0-9]"), "")
        
        if (normalized1.isEmpty() || normalized2.isEmpty()) return 0f
        
        // Simple similarity based on common prefixes and Levenshtein distance
        val commonPrefixLength = normalized1.commonPrefixWith(normalized2).length
        val prefixSimilarity = commonPrefixLength.toFloat() / maxOf(normalized1.length, normalized2.length)
        
        val levenshteinDistance = calculateLevenshteinDistance(normalized1, normalized2)
        val maxLength = maxOf(normalized1.length, normalized2.length)
        val levenshteinSimilarity = 1f - (levenshteinDistance.toFloat() / maxLength)
        
        return (prefixSimilarity * 0.6f + levenshteinSimilarity * 0.4f).coerceIn(0f, 1f)
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun calculateLevenshteinDistance(str1: String, str2: String): Int {
        val matrix = Array(str1.length + 1) { IntArray(str2.length + 1) }
        
        for (i in 0..str1.length) matrix[i][0] = i
        for (j in 0..str2.length) matrix[0][j] = j
        
        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,     // deletion
                    matrix[i][j - 1] + 1,     // insertion
                    matrix[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return matrix[str1.length][str2.length]
    }
    
    /**
     * Default suggestions when no smart suggestions are available
     */
    private fun getDefaultSuggestions(): List<CategorySuggestion> {
        return listOf(
            CategorySuggestion("Food & Dining", 0.3f, "Default"),
            CategorySuggestion("Shopping", 0.2f, "Default"),
            CategorySuggestion("Other", 0.1f, "Default")
        )
    }
}

/**
 * Data class representing a category suggestion
 */
data class CategorySuggestion(
    val categoryName: String,
    val confidence: Float,
    val reason: String
)