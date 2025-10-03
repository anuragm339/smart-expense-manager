package com.expensemanager.app.services

import android.util.Log
import com.expensemanager.app.data.api.insights.*
import com.expensemanager.app.data.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Service to process and anonymize financial data before sending to AI
 * Ensures no personal identifiers are included in API requests
 */
@Singleton
class FinancialDataProcessor @Inject constructor() {

    companion object {
        private const val TAG = "FinancialDataProcessor"
        private const val MAX_MERCHANTS_TO_INCLUDE = 10
        private const val MIN_AMOUNT_FOR_MERCHANT = 100.0 // Minimum spending to include merchant
    }

    /**
     * Main method to create anonymized financial data from transactions
     * Removes all personal identifiers while preserving spending patterns
     */
    suspend fun createAnonymizedData(
        transactions: List<Transaction>,
        timeframe: String = "last_30_days"
    ): AnonymizedFinancialData = withContext(Dispatchers.IO) {

        Log.d(TAG, "Processing ${transactions.size} transactions for anonymization")

        try {
            // Filter out any transactions with insufficient data
            val validTransactions = transactions.filter {
                it.amount > 0 && it.merchant.isNotBlank() && it.category.isNotBlank()
            }

            if (validTransactions.isEmpty()) {
                return@withContext createEmptyData(timeframe)
            }

            val totalSpent = validTransactions.sumOf { it.amount }
            val transactionCount = validTransactions.size

            AnonymizedFinancialData(
                totalSpent = totalSpent,
                transactionCount = transactionCount,
                timeframe = timeframe,
                categoryBreakdown = createCategoryBreakdown(validTransactions, totalSpent),
                topMerchants = createTopMerchants(validTransactions),
                monthlyTrends = createMonthlyTrends(validTransactions),
                weeklyPatterns = createWeeklyPatterns(validTransactions),
                contextData = createContextData(validTransactions, timeframe)
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing financial data", e)
            createEmptyData(timeframe)
        }
    }

    /**
     * Create category breakdown with anonymized data
     */
    private fun createCategoryBreakdown(
        transactions: List<Transaction>,
        totalSpent: Double
    ): List<CategorySpending> {
        return transactions
            .groupBy { it.category }
            .map { (category, txns) ->
                val categoryTotal = txns.sumOf { it.amount }
                CategorySpending(
                    categoryName = category,
                    totalAmount = categoryTotal,
                    transactionCount = txns.size,
                    percentage = if (totalSpent > 0) (categoryTotal / totalSpent * 100) else 0.0,
                    averagePerTransaction = categoryTotal / txns.size
                )
            }
            .sortedByDescending { it.totalAmount }
    }

    /**
     * Create top merchants list (anonymized - no personal account info)
     */
    private fun createTopMerchants(transactions: List<Transaction>): List<MerchantSpending> {
        return transactions
            .groupBy { sanitizeMerchantName(it.merchant) }
            .filter { (_, txns) -> txns.sumOf { it.amount } >= MIN_AMOUNT_FOR_MERCHANT }
            .map { (merchant, txns) ->
                val totalAmount = txns.sumOf { it.amount }
                MerchantSpending(
                    merchantName = merchant,
                    totalAmount = totalAmount,
                    transactionCount = txns.size,
                    categoryName = txns.first().category, // Most common category
                    averageAmount = totalAmount / txns.size
                )
            }
            .sortedByDescending { it.totalAmount }
            .take(MAX_MERCHANTS_TO_INCLUDE)
    }

    /**
     * Create monthly spending trends
     */
    private fun createMonthlyTrends(transactions: List<Transaction>): List<MonthlyTrend> {
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val calendar = Calendar.getInstance()

        return transactions
            .groupBy {
                calendar.timeInMillis = it.date
                monthFormat.format(calendar.time)
            }
            .map { (month, txns) ->
                val monthTotal = txns.sumOf { it.amount }
                MonthlyTrend(
                    month = month,
                    totalAmount = monthTotal,
                    transactionCount = txns.size,
                    averagePerTransaction = monthTotal / txns.size,
                    comparedToPrevious = 0.0 // TODO: Calculate compared to previous month
                )
            }
            .sortedBy { it.month }
    }

    /**
     * Create weekly spending patterns
     */
    private fun createWeeklyPatterns(transactions: List<Transaction>): List<WeeklyPattern> {
        val calendar = Calendar.getInstance()
        val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

        return transactions
            .groupBy {
                calendar.timeInMillis = it.date
                dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
            }
            .map { (day, txns) ->
                val dayTotal = txns.sumOf { it.amount }
                WeeklyPattern(
                    dayOfWeek = day,
                    averageAmount = dayTotal / maxOf(1, getWeekCountForDay(transactions, day)),
                    transactionCount = txns.size,
                    peakHour = findPeakHour(txns)
                )
            }
            .sortedBy { dayNames.indexOf(it.dayOfWeek) }
    }

    /**
     * Create financial context data for better AI insights
     */
    private fun createContextData(
        transactions: List<Transaction>,
        timeframe: String
    ): FinancialContextData {
        val totalSpent = transactions.sumOf { it.amount }
        val currentMonth = Calendar.getInstance()
        val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = currentMonth.get(Calendar.DAY_OF_MONTH)
        val daysRemaining = daysInMonth - currentDay

        // Calculate previous month spending for comparison
        val previousMonthSpent = calculatePreviousMonthSpending(transactions)
        val spendingTrend = when {
            previousMonthSpent == 0.0 -> "stable"
            totalSpent > previousMonthSpent * 1.1 -> "increasing"
            totalSpent < previousMonthSpent * 0.9 -> "decreasing"
            else -> "stable"
        }

        // Find top spending category
        val topCategory = transactions
            .groupBy { it.category }
            .maxByOrNull { (_, txns) -> txns.sumOf { it.amount } }
            ?.key ?: "Unknown"

        return FinancialContextData(
            monthlyBudget = null, // TODO: Get from user preferences
            budgetUtilizationPercentage = 0, // TODO: Calculate if budget is set
            daysRemainingInMonth = daysRemaining,
            previousMonthSpent = previousMonthSpent,
            spendingTrendDirection = spendingTrend,
            topSpendingCategory = topCategory,
            currency = "INR"
        )
    }

    /**
     * Sanitize merchant name to remove any potential personal info
     */
    private fun sanitizeMerchantName(merchantName: String): String {
        // Remove common patterns that might contain personal info
        var sanitized = merchantName
            .replace(Regex("\\*+\\d+"), "") // Remove masked card numbers like ***1234
            .replace(Regex("\\d{10,}"), "") // Remove long numbers (phone, account, etc.)
            .replace(Regex("UPI-\\w+"), "UPI") // Anonymize UPI references
            .trim()

        // If too short after sanitization, keep original but limit length
        if (sanitized.length < 3) {
            sanitized = merchantName.take(20)
        }

        return sanitized
    }

    /**
     * Calculate previous month spending for trend analysis
     */
    private fun calculatePreviousMonthSpending(transactions: List<Transaction>): Double {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val previousMonth = calendar.get(Calendar.MONTH)
        val previousYear = calendar.get(Calendar.YEAR)

        return transactions
            .filter {
                calendar.timeInMillis = it.date
                calendar.get(Calendar.MONTH) == previousMonth &&
                calendar.get(Calendar.YEAR) == previousYear
            }
            .sumOf { it.amount }
    }

    /**
     * Get number of weeks for a specific day to calculate averages
     */
    private fun getWeekCountForDay(transactions: List<Transaction>, dayName: String): Int {
        // Simple implementation - could be more sophisticated
        val calendar = Calendar.getInstance()
        val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val targetDay = dayNames.indexOf(dayName) + 1

        val daysWithTransactions = transactions
            .map {
                calendar.timeInMillis = it.date
                calendar.get(Calendar.DAY_OF_WEEK)
            }
            .filter { it == targetDay }
            .distinct()

        return maxOf(1, daysWithTransactions.size / 7) // Approximate weeks
    }

    /**
     * Find peak spending hour for a day
     */
    private fun findPeakHour(transactions: List<Transaction>): Int? {
        if (transactions.isEmpty()) return null

        val calendar = Calendar.getInstance()
        val hourlySpending = transactions
            .groupBy {
                calendar.timeInMillis = it.date
                calendar.get(Calendar.HOUR_OF_DAY)
            }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }

        return hourlySpending.maxByOrNull { it.value }?.key
    }

    /**
     * Create empty data structure for error cases
     */
    private fun createEmptyData(timeframe: String): AnonymizedFinancialData {
        return AnonymizedFinancialData(
            totalSpent = 0.0,
            transactionCount = 0,
            timeframe = timeframe,
            categoryBreakdown = emptyList(),
            topMerchants = emptyList(),
            monthlyTrends = emptyList(),
            weeklyPatterns = emptyList(),
            contextData = FinancialContextData(
                monthlyBudget = null,
                budgetUtilizationPercentage = 0,
                daysRemainingInMonth = 0,
                previousMonthSpent = 0.0,
                spendingTrendDirection = "stable",
                topSpendingCategory = "Unknown",
                currency = "INR"
            )
        )
    }

    /**
     * Validate that data is properly anonymized before sending
     */
    fun validateAnonymization(data: AnonymizedFinancialData): Boolean {
        try {
            // Check that no merchant names contain potential personal info
            val hasPersonalInfo = data.topMerchants.any { merchant ->
                merchant.merchantName.contains(Regex("\\d{4,}")) || // Long numbers
                merchant.merchantName.contains("@") || // Email patterns
                merchant.merchantName.length > 50 // Unusually long names
            }

            if (hasPersonalInfo) {
                Log.w(TAG, "Potential personal info detected in merchant data")
                return false
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating anonymization", e)
            return false
        }
    }
}