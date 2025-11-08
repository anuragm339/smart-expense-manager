package com.smartexpenseai.app.utils

import android.content.Context
import com.smartexpenseai.app.data.entities.CategoryEntity
import com.smartexpenseai.app.data.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CategoryManager - Single Source of Truth using Room Database
 *
 * This class manages category operations using ONLY the Room Database.
 * All SharedPreferences usage has been removed for data consistency.
 *
 * Migration: CategoryMigrationHelper handles one-time migration from SharedPrefs to DB
 */
@Singleton
class CategoryManager @Inject constructor(
    private val context: Context,
    private val repository: ExpenseRepository
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
     * Rule-based merchant categorization (fallback when not in database)
     */
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
