package com.smartexpenseai.app.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class representing a parsed transaction from SMS
 */
data class ParsedTransaction(
    val id: String = "",
    val amount: Double,
    val merchant: String,
    val rawMerchant: String = "",
    val bankName: String,
    val category: String = "",
    val categoryColor: String = "",
    val isDebit: Boolean = true,
    val date: Date,
    val confidence: Float,
    val rawSMS: String,
    val referenceNumber: String? = null
) {
    // Constructor for compatibility with different use cases
    constructor(
        amount: Double,
        merchant: String,
        rawMerchant: String,
        bankName: String,
        category: String,
        categoryColor: String,
        isDebit: Boolean,
        date: Date,
        confidence: Double,
        rawSMS: String,
        referenceNumber: String? = null
    ) : this(
        id = "",
        amount = amount,
        merchant = merchant,
        rawMerchant = rawMerchant,
        bankName = bankName,
        category = category,
        categoryColor = categoryColor,
        isDebit = isDebit,
        date = date,
        confidence = confidence.toFloat(),
        rawSMS = rawSMS,
        referenceNumber = referenceNumber
    )
    
    fun formattedAmount(): String = "₹${String.format("%.0f", amount)}"
    
    fun formattedDate(): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return formatter.format(date)
    }
}

/**
 * Data class representing historical SMS data
 */
data class HistoricalSMS(
    val id: String,
    val address: String,
    val body: String,
    val date: Date,
    val type: Int
)

/**
 * Data class representing rejected SMS that couldn't be parsed
 */
data class RejectedSMS(
    val sender: String,
    val body: String,
    val date: Date,
    val reason: String
)