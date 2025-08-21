package com.expensemanager.app.data.models

// @Entity(tableName = "categories") - Room annotations temporarily removed
data class Category(
    // @PrimaryKey
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val totalSpent: Double = 0.0,
    val transactionCount: Int = 0,
    val isDefault: Boolean = true,
    val isActive: Boolean = true
)

object DefaultCategories {
    val FOOD_DINING = Category(
        id = "food_dining",
        name = "Food & Dining",
        icon = "🍽️",
        color = "#ff5722"
    )
    
    val TRANSPORTATION = Category(
        id = "transportation",
        name = "Transportation", 
        icon = "🚗",
        color = "#3f51b5"
    )
    
    val HEALTHCARE = Category(
        id = "healthcare",
        name = "Healthcare",
        icon = "🏥",
        color = "#e91e63"
    )
    
    val GROCERIES = Category(
        id = "groceries",
        name = "Groceries",
        icon = "🛒",
        color = "#4caf50"
    )
    
    val ENTERTAINMENT = Category(
        id = "entertainment",
        name = "Entertainment",
        icon = "🎬",
        color = "#9c27b0"
    )
    
    val SHOPPING = Category(
        id = "shopping",
        name = "Shopping",
        icon = "🛍️",
        color = "#ff9800"
    )
    
    val UTILITIES = Category(
        id = "utilities",
        name = "Utilities",
        icon = "⚡",
        color = "#607d8b"
    )
    
    val OTHER = Category(
        id = "other",
        name = "Other",
        icon = "📦",
        color = "#795548"
    )
    
    fun getAllDefault(): List<Category> = listOf(
        FOOD_DINING, TRANSPORTATION, HEALTHCARE, GROCERIES,
        ENTERTAINMENT, SHOPPING, UTILITIES, OTHER
    )
}