package com.expensemanager.app.utils

import android.graphics.Color
import android.view.View
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility service that eliminates ~90 lines of duplicated UI formatting logic
 * Centralizes common formatting patterns used across adapters and fragments
 */
@Singleton
class UIFormatUtils @Inject constructor() {

    companion object {
        // Common date formatters
        private val CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        private val DATE_FORMAT_SHORT = SimpleDateFormat("MMM dd", Locale.getDefault())
        private val DATE_FORMAT_FULL = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        // Common color constants
        private const val DEFAULT_COLOR = "#2196F3"
        private const val ERROR_COLOR = "#F44336"
        private const val SUCCESS_COLOR = "#4CAF50"
        private const val WARNING_COLOR = "#FF9800"
    }

    /**
     * Format amount with currency symbol (₹)
     * Eliminates repeated "₹${String.format("%.0f", amount)}" patterns
     */
    fun formatAmount(amount: Double): String {
        return "₹${String.format("%.0f", amount)}"
    }

    /**
     * Format amount with decimal places
     */
    fun formatAmountWithDecimals(amount: Double, decimals: Int = 2): String {
        return "₹${String.format("%.${decimals}f", amount)}"
    }

    /**
     * Format large amounts with K/M/B suffixes
     */
    fun formatLargeAmount(amount: Double): String {
        return when {
            amount >= 1_00_00_000 -> "₹${String.format("%.1f", amount / 1_00_00_000)}Cr"
            amount >= 1_00_000 -> "₹${String.format("%.1f", amount / 1_00_000)}L"
            amount >= 1_000 -> "₹${String.format("%.1f", amount / 1_000)}K"
            else -> formatAmount(amount)
        }
    }

    /**
     * Format transaction count
     * Eliminates repeated "${count} transactions" patterns
     */
    fun formatTransactionCount(count: Int): String {
        return when (count) {
            0 -> "No transactions"
            1 -> "1 transaction"
            else -> "$count transactions"
        }
    }

    /**
     * Format percentage with % symbol
     */
    fun formatPercentage(percentage: Double, decimals: Int = 1): String {
        return "${String.format("%.${decimals}f", percentage)}%"
    }

    /**
     * Format percentage change with + or - prefix
     */
    fun formatPercentageChange(percentage: Double): String {
        val sign = if (percentage >= 0) "+" else ""
        return "$sign${formatPercentage(percentage)}"
    }

    /**
     * Format date in short format (MMM dd)
     */
    fun formatDateShort(date: Date): String {
        return DATE_FORMAT_SHORT.format(date)
    }

    /**
     * Format date in full format (MMM dd, yyyy)
     */
    fun formatDateFull(date: Date): String {
        return DATE_FORMAT_FULL.format(date)
    }

    /**
     * Format time (HH:mm)
     */
    fun formatTime(date: Date): String {
        return TIME_FORMAT.format(date)
    }

    /**
     * Parse color safely with fallback
     * Eliminates repeated try-catch color parsing patterns
     */
    fun parseColorSafely(colorString: String?, fallbackColor: String = DEFAULT_COLOR): Int {
        return try {
            Color.parseColor(colorString ?: fallbackColor)
        } catch (e: Exception) {
            Color.parseColor(fallbackColor)
        }
    }

    /**
     * Apply color to view background safely
     */
    fun applyColorToView(view: View, colorString: String?, fallbackColor: String = DEFAULT_COLOR) {
        val color = parseColorSafely(colorString, fallbackColor)
        view.setBackgroundColor(color)
    }

    /**
     * Get trend indicator text and color
     */
    fun getTrendIndicator(currentValue: Double, previousValue: Double): Pair<String, Int> {
        val change = currentValue - previousValue
        val percentageChange = if (previousValue > 0) (change / previousValue) * 100 else 0.0
        
        return when {
            change > 0 -> Pair(
                "↗ ${formatPercentageChange(percentageChange)}", 
                parseColorSafely(SUCCESS_COLOR)
            )
            change < 0 -> Pair(
                "↘ ${formatPercentageChange(percentageChange)}", 
                parseColorSafely(ERROR_COLOR)
            )
            else -> Pair(
                "→ 0%", 
                parseColorSafely(DEFAULT_COLOR)
            )
        }
    }

    /**
     * Format spending summary text
     */
    fun formatSpendingSummary(amount: Double, transactionCount: Int): String {
        return "${formatAmount(amount)} • ${formatTransactionCount(transactionCount)}"
    }

    /**
     * Format merchant name with transaction count
     */
    fun formatMerchantSummary(merchantName: String, transactionCount: Int): String {
        return "$merchantName (${formatTransactionCount(transactionCount)})"
    }

    /**
     * Format category summary with amount and percentage
     */
    fun formatCategorySummary(
        categoryName: String, 
        amount: Double, 
        percentage: Double
    ): String {
        return "$categoryName • ${formatAmount(amount)} (${formatPercentage(percentage)})"
    }

    /**
     * Get status color based on amount type
     */
    fun getAmountStatusColor(amount: Double): Int {
        return when {
            amount > 0 -> parseColorSafely(SUCCESS_COLOR)
            amount < 0 -> parseColorSafely(ERROR_COLOR)
            else -> parseColorSafely(DEFAULT_COLOR)
        }
    }

    /**
     * Format budget status
     */
    fun formatBudgetStatus(spent: Double, budget: Double): String {
        val percentage = if (budget > 0) (spent / budget) * 100 else 0.0
        return "${formatAmount(spent)} of ${formatAmount(budget)} (${formatPercentage(percentage)})"
    }

    /**
     * Get budget status color
     */
    fun getBudgetStatusColor(spent: Double, budget: Double): Int {
        val percentage = if (budget > 0) (spent / budget) * 100 else 0.0
        return when {
            percentage <= 70 -> parseColorSafely(SUCCESS_COLOR)
            percentage <= 90 -> parseColorSafely(WARNING_COLOR)
            else -> parseColorSafely(ERROR_COLOR)
        }
    }

    /**
     * Format time ago text
     */
    fun formatTimeAgo(date: Date): String {
        val now = Date()
        val diffMs = now.time - date.time
        val diffHours = diffMs / (1000 * 60 * 60)
        val diffDays = diffHours / 24
        
        return when {
            diffHours < 1 -> "Just now"
            diffHours < 24 -> "${diffHours}h ago"
            diffDays < 7 -> "${diffDays}d ago"
            else -> formatDateShort(date)
        }
    }

    /**
     * Truncate text with ellipsis
     */
    fun truncateText(text: String, maxLength: Int): String {
        return if (text.length > maxLength) {
            "${text.substring(0, maxLength - 3)}..."
        } else {
            text
        }
    }
}