package com.smartexpenseai.app.data.models

/**
 * Data class representing a budget goal for a category
 * This is a stub implementation for ViewModels
 */
data class BudgetGoal(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val amount: Double,
    val period: String = "MONTHLY", // WEEKLY, MONTHLY, YEARLY
    val startDate: Long,
    val endDate: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)