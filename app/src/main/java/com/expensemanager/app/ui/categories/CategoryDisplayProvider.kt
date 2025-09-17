package com.expensemanager.app.ui.categories

import android.content.Context
import com.expensemanager.app.constants.Categories
import com.expensemanager.app.data.repository.ExpenseRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for providing category display information
 * Separates category identity (plain names) from visual representation (icons, formatting)
 */
interface CategoryDisplayProvider {
    
    /**
     * Get the display icon for a category
     * @param categoryName Plain category name (e.g., "Food & Dining")
     * @return CategoryIcon representing how to display this category
     */
    fun getDisplayIcon(categoryName: String): CategoryIcon
    
    /**
     * Get the formatted display name for a category
     * @param categoryName Plain category name
     * @return Formatted name for display (may include emoji prefix)
     */
    fun formatForDisplay(categoryName: String): String
    
    /**
     * Get just the display name without any icons
     * @param categoryName Plain category name
     * @return Display name (same as input for most implementations)
     */
    fun getDisplayName(categoryName: String): String
    
    /**
     * Check if a category has a custom icon defined
     * @param categoryName Plain category name
     * @return true if category has custom icon, false for default
     */
    fun hasCustomIcon(categoryName: String): Boolean
}

/**
 * Default implementation using emojis for category icons
 * This maintains backward compatibility while enabling future flexibility
 */
@Singleton
class DefaultCategoryDisplayProvider @Inject constructor(
    private val context: Context,
    private val repository: ExpenseRepository
) : CategoryDisplayProvider {
    
    // Cache for performance
    private val iconCache = mutableMapOf<String, CategoryIcon>()
    private val displayCache = mutableMapOf<String, String>()
    
    override fun getDisplayIcon(categoryName: String): CategoryIcon {
        return iconCache.getOrPut(categoryName) {
            // STEP 1: Check database first for custom user categories
            val dbCategory = try {
                runBlocking {
                    repository.getCategoryByName(categoryName)
                }
            } catch (e: Exception) {
                null // Database lookup failed, fall back to hardcoded
            }
            
            if (dbCategory != null && dbCategory.emoji.isNotBlank()) {
                // Found in database with custom emoji
                return@getOrPut CategoryIcon.Emoji(dbCategory.emoji)
            }
            
            // STEP 2: Fall back to hardcoded system categories
            val canonicalName = Categories.getCanonicalName(categoryName)
            
            when (canonicalName) {
                Categories.FOOD_DINING -> CategoryIcon.Emoji("🍽️")
                Categories.TRANSPORTATION -> CategoryIcon.Emoji("🚗")
                Categories.GROCERIES -> CategoryIcon.Emoji("🛒")
                Categories.HEALTHCARE -> CategoryIcon.Emoji("🏥")
                Categories.ENTERTAINMENT -> CategoryIcon.Emoji("🎬")
                Categories.SHOPPING -> CategoryIcon.Emoji("🛍️")
                Categories.UTILITIES -> CategoryIcon.Emoji("⚡")
                Categories.MONEY, Categories.FINANCE -> CategoryIcon.Emoji("💰")
                Categories.EDUCATION -> CategoryIcon.Emoji("📚")
                Categories.TRAVEL -> CategoryIcon.Emoji("✈️")
                Categories.BILLS -> CategoryIcon.Emoji("💳")
                Categories.INSURANCE -> CategoryIcon.Emoji("🛡️")
                "TestCat" -> CategoryIcon.Emoji("🧪") // For user's test category
                Categories.OTHER -> CategoryIcon.Emoji("📂")
                else -> {
                    // Check if it matches any known variations not in canonical mapping
                    when (categoryName.lowercase()) {
                        "food", "dining" -> CategoryIcon.Emoji("🍽️")
                        "transport" -> CategoryIcon.Emoji("🚗")
                        "grocery" -> CategoryIcon.Emoji("🛒")
                        "health", "medical" -> CategoryIcon.Emoji("🏥")
                        "testcat" -> CategoryIcon.Emoji("🧪")
                        else -> CategoryIcon.Emoji("📂") // Default fallback
                    }
                }
            }
        }
    }
    
    override fun formatForDisplay(categoryName: String): String {
        return displayCache.getOrPut(categoryName) {
            val icon = getDisplayIcon(categoryName)
            when (icon) {
                is CategoryIcon.Emoji -> "${icon.emoji} $categoryName"
                is CategoryIcon.Image, 
                is CategoryIcon.Vector, 
                is CategoryIcon.Url -> categoryName // For images, just show name
                is CategoryIcon.Default -> categoryName
            }
        }
    }
    
    override fun getDisplayName(categoryName: String): String {
        return categoryName // Plain name without any formatting
    }
    
    override fun hasCustomIcon(categoryName: String): Boolean {
        val icon = getDisplayIcon(categoryName)
        return icon !is CategoryIcon.Default && 
               !(icon is CategoryIcon.Emoji && icon.emoji == "📂")
    }
    
    /**
     * Clear caches (useful for testing or when categories change)
     */
    fun clearCache() {
        iconCache.clear()
        displayCache.clear()
    }
    
    /**
     * Clear cache for a specific category (more efficient than clearing all)
     */
    fun clearCacheForCategory(categoryName: String) {
        iconCache.remove(categoryName)
        displayCache.remove(categoryName)
        // Also clear any normalized versions
        iconCache.remove(categoryName.lowercase())
        displayCache.remove(categoryName.lowercase())
    }
}

/**
 * Extension functions for easy usage throughout the app
 */
fun CategoryDisplayProvider.getEmojiString(categoryName: String): String {
    return when (val icon = getDisplayIcon(categoryName)) {
        is CategoryIcon.Emoji -> icon.emoji
        else -> "📂" // Default emoji fallback
    }
}

fun CategoryDisplayProvider.isEmojiIcon(categoryName: String): Boolean {
    return getDisplayIcon(categoryName) is CategoryIcon.Emoji
}