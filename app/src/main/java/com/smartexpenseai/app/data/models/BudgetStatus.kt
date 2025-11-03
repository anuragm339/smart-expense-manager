package com.smartexpenseai.app.data.models

/**
 * Data class representing budget status for a category
 * This is a stub implementation for ViewModels
 */
data class BudgetStatus(
    val categoryId: String,
    val categoryName: String,
    val budgetAmount: Double,
    val spentAmount: Double,
    val percentageUsed: Double,
    val isOverBudget: Boolean
)