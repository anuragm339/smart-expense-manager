package com.smartexpenseai.app.data.models

/**
 * Data class representing spending trends over time
 * This is a stub implementation for ViewModels
 */
data class SpendingTrend(
    val period: String,
    val amount: Double,
    val category: String? = null,
    val changePercentage: Double = 0.0
)