package com.expensemanager.app.ui.categories

import java.util.Date

/**
 * Data class representing a merchant within a specific category
 * Used for displaying merchant information in category detail screens
 */
data class MerchantInCategory(
    val merchantName: String,           // Read-only merchant name (no renaming allowed)
    val transactionCount: Int,          // Number of transactions for this merchant
    val totalAmount: Double,            // Total amount spent at this merchant
    val lastTransactionDate: Date,      // Date of the most recent transaction
    val currentCategory: String,        // Current category of this merchant
    val percentage: Float = 0f          // Percentage of total category spending
) {
    /**
     * Get the first letter of merchant name for display in avatar
     */
    fun getInitial(): String {
        return if (merchantName.isNotEmpty()) {
            merchantName.first().uppercaseChar().toString()
        } else {
            "?"
        }
    }

    /**
     * Format the last transaction date for display
     */
    fun getLastTransactionText(): String {
        val currentTime = System.currentTimeMillis()
        val transactionTime = lastTransactionDate.time
        val diffInMillis = currentTime - transactionTime

        val hours = diffInMillis / (1000 * 60 * 60)
        val days = diffInMillis / (1000 * 60 * 60 * 24)

        return when {
            hours < 24 -> {
                if (hours <= 1) "1 hour ago"
                else "${hours}h ago"
            }
            days <= 7 -> {
                if (days <= 1) "1 day ago"
                else "${days}d ago"
            }
            else -> {
                val weeks = days / 7
                if (weeks <= 1) "1 week ago"
                else "${weeks}w ago"
            }
        }
    }

    /**
     * Format transaction count for display
     */
    fun getTransactionCountText(): String {
        return when (transactionCount) {
            1 -> "1 transaction"
            else -> "$transactionCount transactions"
        }
    }

    /**
     * Format amount for display
     */
    fun getFormattedAmount(): String {
        return "₹${String.format("%.0f", totalAmount)}"
    }
}