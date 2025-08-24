package com.expensemanager.app.data.models

/**
 * Data class representing monthly spending summary
 */
data class MonthlySummary(
    val month: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val averagePerTransaction: Double
)