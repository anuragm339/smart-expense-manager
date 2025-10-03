package com.expensemanager.app.services

import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.data.repository.ExpenseRepository
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for generating CSV from transaction data for AI analysis
 * OPTIMIZED for o1-mini model: 50 most recent transactions, 6 columns
 * AI extracts day-of-week and time-of-day patterns from Date timestamp
 */
@Singleton
class TransactionCSVGenerator @Inject constructor(
    private val repository: ExpenseRepository
) {

    companion object {
        private const val TAG = "TransactionCSVGenerator"
        private const val MAX_TRANSACTIONS = 50 // Optimized for o1-mini: reduced from 200 to save 75% tokens
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    /**
     * Generate CSV string from transactions with enriched metadata
     * @param transactions List of all transactions (will be filtered and limited)
     * @return CSV string with headers and data rows
     */
    suspend fun generateCSV(transactions: List<TransactionEntity>): String {
        val csvBuilder = StringBuilder()

        // CSV Header (optimized: removed DayOfWeek/TimeOfDay - AI extracts from Date timestamp)
        csvBuilder.appendLine("Date,Amount,Merchant,Category,Type,Bank")

        // Filter to only debit transactions (expenses), sort newest first, limit to 50
        val selectedTransactions = transactions
            .filter { it.isDebit }
            .sortedByDescending { it.transactionDate }
            .take(MAX_TRANSACTIONS)

        Timber.tag(TAG).d("Generating CSV for ${selectedTransactions.size} transactions")

        // Generate CSV rows (optimized: removed day/time columns - AI extracts from timestamp)
        selectedTransactions.forEach { transaction ->
            val date = DATE_FORMAT.format(transaction.transactionDate)
            val amount = String.format(Locale.US, "%.2f", transaction.amount)
            val merchant = escapeCsvValue(transaction.rawMerchant)
            val category = getCategory(transaction.normalizedMerchant)
            val type = if (transaction.isDebit) "Debit" else "Credit"
            val bank = escapeCsvValue(transaction.bankName)

            csvBuilder.appendLine("$date,$amount,$merchant,$category,$type,$bank")
        }

        val csv = csvBuilder.toString()
        Timber.tag(TAG).d("CSV generated: ${csv.length} bytes, ${selectedTransactions.size} rows")

        return csv
    }

    /**
     * Get category for a merchant (lookup from repository)
     */
    private suspend fun getCategory(normalizedMerchant: String): String {
        return try {
            val merchantWithCategory = repository.getMerchantWithCategory(normalizedMerchant)
            escapeCsvValue(merchantWithCategory?.category_name ?: "Other")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting category for merchant: $normalizedMerchant")
            "Other"
        }
    }

    /**
     * Get day of week from date
     */
    private fun getDayOfWeek(date: Date): String {
        val calendar = Calendar.getInstance().apply { time = date }
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Unknown"
        }
    }

    /**
     * Get time of day category from date
     */
    private fun getTimeOfDay(date: Date): String {
        val calendar = Calendar.getInstance().apply { time = date }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
    }

    /**
     * Escape CSV value (handle commas, quotes, newlines)
     */
    private fun escapeCsvValue(value: String): String {
        // If value contains comma, quote, or newline, wrap in quotes and escape internal quotes
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * Get metadata about the generated CSV
     */
    fun getMetadata(transactions: List<TransactionEntity>, csvSize: Int): CSVMetadataInfo {
        val selectedTransactions = transactions
            .filter { it.isDebit }
            .sortedByDescending { it.transactionDate }
            .take(MAX_TRANSACTIONS)

        val oldest = selectedTransactions.lastOrNull()?.transactionDate
        val newest = selectedTransactions.firstOrNull()?.transactionDate

        return CSVMetadataInfo(
            totalTransactions = selectedTransactions.size,
            dateRangeStart = oldest?.let { DATE_FORMAT.format(it) } ?: "",
            dateRangeEnd = newest?.let { DATE_FORMAT.format(it) } ?: "",
            csvSizeBytes = csvSize
        )
    }
}

/**
 * Metadata information about generated CSV
 */
data class CSVMetadataInfo(
    val totalTransactions: Int,
    val dateRangeStart: String,
    val dateRangeEnd: String,
    val csvSizeBytes: Int
)
