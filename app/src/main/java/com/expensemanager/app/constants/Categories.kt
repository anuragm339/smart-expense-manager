package com.expensemanager.app.constants

/**
 * Centralized category constants to replace hardcoded strings throughout the app
 * This provides a single source of truth for category names and enables easy refactoring
 */
object Categories {
    
    // Core system categories
    const val FOOD_DINING = "Food & Dining"
    const val TRANSPORTATION = "Transportation"
    const val GROCERIES = "Groceries"
    const val HEALTHCARE = "Healthcare"
    const val ENTERTAINMENT = "Entertainment"
    const val SHOPPING = "Shopping"
    const val UTILITIES = "Utilities"
    const val OTHER = "Other"
    
    // Additional categories that may exist in the system
    const val MONEY = "Money"
    const val FINANCE = "Finance"
    const val EDUCATION = "Education"
    const val TRAVEL = "Travel"
    const val BILLS = "Bills"
    const val INSURANCE = "Insurance"
    
    /**
     * Default system categories list
     * These are the core categories that should always be available
     */
    val DEFAULT_CATEGORIES = listOf(
        FOOD_DINING,
        TRANSPORTATION,
        GROCERIES,
        HEALTHCARE,
        ENTERTAINMENT,
        SHOPPING,
        UTILITIES,
        OTHER
    )
    
    /**
     * Extended categories list including all known categories
     */
    val ALL_SYSTEM_CATEGORIES = listOf(
        FOOD_DINING,
        TRANSPORTATION,
        GROCERIES,
        HEALTHCARE,
        ENTERTAINMENT,
        SHOPPING,
        UTILITIES,
        MONEY,
        FINANCE,
        EDUCATION,
        TRAVEL,
        BILLS,
        INSURANCE,
        OTHER
    )
    
    /**
     * Default budgets for categories (in local currency)
     */
    val DEFAULT_BUDGETS = mapOf(
        FOOD_DINING to 4000f,
        TRANSPORTATION to 2000f,
        GROCERIES to 3000f,
        HEALTHCARE to 1500f,
        ENTERTAINMENT to 1000f,
        SHOPPING to 2000f,
        UTILITIES to 1500f,
        OTHER to 1000f
    )
    
    /**
     * Default colors for categories
     */
    val DEFAULT_COLORS = mapOf(
        FOOD_DINING to "#ff5722",
        TRANSPORTATION to "#3f51b5",
        HEALTHCARE to "#e91e63",
        GROCERIES to "#4caf50",
        SHOPPING to "#ff9800",
        ENTERTAINMENT to "#9c27b0",
        UTILITIES to "#607d8b",
        MONEY to "#ff9800",
        FINANCE to "#ff9800",
        EDUCATION to "#673ab7",
        TRAVEL to "#00bcd4",
        BILLS to "#795548",
        INSURANCE to "#9e9e9e",
        OTHER to "#888888"
    )
    
    /**
     * Category aliases for legacy compatibility
     * Maps old/alternative names to standard category names
     */
    val CATEGORY_ALIASES = mapOf(
        "food_dining" to FOOD_DINING,
        "transportation" to TRANSPORTATION,
        "groceries" to GROCERIES,
        "healthcare" to HEALTHCARE,
        "shopping" to SHOPPING,
        "entertainment" to ENTERTAINMENT,
        "utilities" to UTILITIES,
        "food" to FOOD_DINING,
        "dining" to FOOD_DINING,
        "transport" to TRANSPORTATION,
        "grocery" to GROCERIES,
        "health" to HEALTHCARE,
        "medical" to HEALTHCARE,
        "doctor" to HEALTHCARE,
        "hospital" to HEALTHCARE,
        "shop" to SHOPPING,
        "store" to SHOPPING,
        "mall" to SHOPPING,
        "movie" to ENTERTAINMENT,
        "movies" to ENTERTAINMENT,
        "music" to ENTERTAINMENT,
        "game" to ENTERTAINMENT,
        "games" to ENTERTAINMENT,
        "gas" to UTILITIES,
        "electricity" to UTILITIES,
        "water" to UTILITIES,
        "internet" to UTILITIES,
        "mobile" to UTILITIES,
        "phone" to UTILITIES,
        "recharge" to UTILITIES
    )
    
    /**
     * Get the canonical category name for any input
     * Handles aliases and case variations
     */
    fun getCanonicalName(input: String): String {
        val normalized = input.trim().lowercase()
        return CATEGORY_ALIASES[normalized] ?: input.trim()
    }
    
    /**
     * Check if a category is a system default category
     */
    fun isSystemCategory(categoryName: String): Boolean {
        return DEFAULT_CATEGORIES.contains(getCanonicalName(categoryName))
    }
    
    /**
     * Get default budget for a category
     */
    fun getDefaultBudget(categoryName: String): Float {
        return DEFAULT_BUDGETS[getCanonicalName(categoryName)] ?: 1000f
    }
    
    /**
     * Get default color for a category
     */
    fun getDefaultColor(categoryName: String): String {
        return DEFAULT_COLORS[getCanonicalName(categoryName)] ?: "#888888"
    }
}