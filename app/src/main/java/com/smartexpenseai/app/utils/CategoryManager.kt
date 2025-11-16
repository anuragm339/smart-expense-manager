package com.smartexpenseai.app.utils

import android.content.Context
import com.smartexpenseai.app.data.entities.CategoryEntity
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.parsing.engine.MerchantRuleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CategoryManager - Single Source of Truth using Room Database + JSON Rules
 *
 * This class manages category operations using:
 * 1. Room Database for merchant-category mappings
 * 2. JSON-based rule engine for auto-categorization (merchant_rules.json)
 *
 * All SharedPreferences usage has been removed for data consistency.
 * Migration: CategoryMigrationHelper handles one-time migration from SharedPrefs to DB
 */
@Singleton
class CategoryManager @Inject constructor(
    private val context: Context,
    private val repository: ExpenseRepository,
    private val merchantRuleEngine: MerchantRuleEngine
) {

    /**
     * Categorize a transaction based on merchant name
     *
     * Priority order:
     * 1. Check if merchant exists in database with user-defined category
     * 2. Use existing merchant's category from database
     * 3. Fall back to rule-based categorization
     */
    suspend fun categorizeTransaction(merchant: String): String = withContext(Dispatchers.IO) {
        val normalizedMerchant = merchant.lowercase().trim()

        // Check database for merchant
        val merchantEntity = repository.getMerchantByNormalizedName(normalizedMerchant)
        if (merchantEntity != null) {
            // Merchant exists - get its category
            val category = repository.getCategoryById(merchantEntity.categoryId)
            if (category != null) {
                return@withContext category.name
            }
        }

        // Fallback to rule-based categorization
        categorizeMerchant(merchant)
    }

    /**
     * Update category for a merchant (learning user preference)
     * This sets isUserDefined = true to track user modifications
     */
    suspend fun updateCategory(merchant: String, newCategory: String) = withContext(Dispatchers.IO) {
        val normalizedMerchant = merchant.lowercase().trim()

        // Find category in database
        val category = repository.getCategoryByName(newCategory)
        if (category == null) {
            // Category doesn't exist - create it as custom category
            val newCategoryEntity = CategoryEntity(
                name = newCategory,
                emoji = "📂",
                color = getCategoryColor(newCategory),
                isSystem = false,
                displayOrder = 999,
                createdAt = Date()
            )
            val categoryId = repository.insertCategory(newCategoryEntity)

            // Update merchant with new category
            repository.updateMerchantCategory(
                normalizedName = normalizedMerchant,
                categoryId = categoryId,
                isUserDefined = true
            )
        } else {
            // Update merchant with existing category
            repository.updateMerchantCategory(
                normalizedName = normalizedMerchant,
                categoryId = category.id,
                isUserDefined = true
            )
        }

        // Also update similar merchants
        updateSimilarMerchants(normalizedMerchant, category?.id ?: return@withContext)
    }

    /**
     * Rule-based merchant categorization using JSON rules (fallback when not in database)
     *
     * REFACTORED: Now uses MerchantRuleEngine with merchant_rules.json instead of hardcoded rules
     * Benefits:
     * - Easy to modify rules without code changes
     * - Regex pattern support for advanced matching
     * - Single source of truth for categorization logic
     * - Eliminates code duplication with TransactionDataRepository
     */
    private fun categorizeMerchant(merchantName: String): String {
        // Initialize rule engine if needed
        merchantRuleEngine.initialize()

        // Use rule engine for categorization
        val result = merchantRuleEngine.categorize(merchantName)
        return result.categoryName
    }

    /**
     * Update similar merchants to have the same category
     * Based on 50% word similarity threshold
     */
    private suspend fun updateSimilarMerchants(merchant: String, categoryId: Long) = withContext(Dispatchers.IO) {
        try {
            // Get all merchants from database
            val allMerchants = repository.getAllMerchants()

            allMerchants.forEach { merchantEntity ->
                if (isSimilarMerchant(merchant, merchantEntity.normalizedName)) {
                    // Update similar merchant to same category
                    repository.updateMerchantCategory(
                        normalizedName = merchantEntity.normalizedName,
                        categoryId = categoryId,
                        isUserDefined = true
                    )
                }
            }
        } catch (e: Exception) {
            // Log but don't throw - this is best-effort
        }
    }

    /**
     * Check if two merchant names are similar (50% word overlap)
     */
    private fun isSimilarMerchant(merchant1: String, merchant2: String): Boolean {
        if (merchant1 == merchant2) return true

        val words1 = merchant1.split(" ", "-", "_").filter { it.length > 2 }
        val words2 = merchant2.split(" ", "-", "_").filter { it.length > 2 }

        if (words1.isEmpty() || words2.isEmpty()) return false

        // Check if they share significant words
        val commonWords = words1.intersect(words2.toSet())
        val similarityRatio = commonWords.size.toFloat() / maxOf(words1.size, words2.size)

        return similarityRatio >= 0.5 // 50% similarity threshold
    }

    /**
     * Get category color
     * Default categories have predefined colors, custom categories get generated colors
     */
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

    /**
     * Get all categories from database
     * Includes both system and custom categories
     */
    suspend fun getAllCategories(): List<String> = withContext(Dispatchers.IO) {
        val categories = repository.getAllCategoriesSync()
        categories.map { it.name }
    }

    /**
     * Add a custom category to database
     */
    suspend fun addCustomCategory(categoryName: String) = withContext(Dispatchers.IO) {
        // Check if already exists
        val existing = repository.getCategoryByName(categoryName)
        if (existing == null) {
            val categoryEntity = CategoryEntity(
                name = categoryName,
                emoji = "📂",
                color = getCategoryColor(categoryName),
                isSystem = false,
                displayOrder = 999,
                createdAt = Date()
            )
            repository.insertCategory(categoryEntity)
        }
    }

    /**
     * Remove a custom category from database
     * Only non-system categories can be removed
     */
    suspend fun removeCustomCategory(categoryName: String) = withContext(Dispatchers.IO) {
        val category = repository.getCategoryByName(categoryName)
        if (category != null && !category.isSystem) {
            repository.deleteCategory(category)
        }
    }
}
