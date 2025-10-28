package com.expensemanager.app.parsing.models

import com.google.gson.annotations.SerializedName

/**
 * Schema for bank SMS parsing rules loaded from JSON
 * Version 1.0 - Initial rule engine implementation
 */
data class BankRulesSchema(
    @SerializedName("version")
    val version: Int,

    @SerializedName("banks")
    val banks: List<BankRule>,

    @SerializedName("fallback_patterns")
    val fallbackPatterns: FallbackPatterns
)

/**
 * Parsing rules for a specific bank
 */
data class BankRule(
    @SerializedName("code")
    val code: String,  // e.g., "HDFCBK", "ICICI"

    @SerializedName("display_name")
    val displayName: String,  // e.g., "HDFC Bank", "ICICI Bank"

    @SerializedName("sender_patterns")
    val senderPatterns: List<String>,  // Regex patterns for sender IDs

    @SerializedName("patterns")
    val patterns: TransactionPatterns,

    @SerializedName("confidence_weights")
    val confidenceWeights: ConfidenceWeights? = null
)

/**
 * Transaction field extraction patterns for a bank
 */
data class TransactionPatterns(
    @SerializedName("amount")
    val amount: List<String>,  // Regex patterns to extract amount

    @SerializedName("merchant")
    val merchant: List<String>,  // Regex patterns to extract merchant

    @SerializedName("date")
    val date: List<String>? = null,  // Optional date patterns

    @SerializedName("transaction_type")
    val transactionType: List<String>? = null,  // Debit/Credit indicators

    @SerializedName("reference_number")
    val referenceNumber: List<String>? = null  // Optional transaction ref
)

/**
 * Confidence scoring weights for pattern matching
 */
data class ConfidenceWeights(
    @SerializedName("sender_match")
    val senderMatch: Float = 0.25f,

    @SerializedName("amount_extraction")
    val amountExtraction: Float = 0.25f,

    @SerializedName("merchant_extraction")
    val merchantExtraction: Float = 0.20f,

    @SerializedName("date_extraction")
    val dateExtraction: Float = 0.10f,

    @SerializedName("reference_number_extraction")
    val referenceNumberExtraction: Float = 0.20f
)

/**
 * Fallback patterns for unknown banks
 */
data class FallbackPatterns(
    @SerializedName("amount")
    val amount: List<String>,

    @SerializedName("merchant")
    val merchant: List<String>,

    @SerializedName("reference_number")
    val referenceNumber: List<String>? = null,

    @SerializedName("debit_keywords")
    val debitKeywords: List<String>,

    @SerializedName("credit_keywords")
    val creditKeywords: List<String>
)

/**
 * Confidence score for a parsed transaction
 */
data class ConfidenceScore(
    val overall: Float,  // 0.0 to 1.0
    val breakdown: Map<Field, FieldConfidence>
) {
    enum class Field {
        SENDER, AMOUNT, MERCHANT, DATE, TRANSACTION_TYPE, REFERENCE_NUMBER
    }

    data class FieldConfidence(
        val extracted: Boolean,
        val score: Float,
        val value: String?
    )

    companion object {
        const val AUTO_ACCEPT_THRESHOLD = 0.85f
        const val MANUAL_REVIEW_THRESHOLD = 0.50f
    }

    fun shouldAutoAccept(): Boolean = overall >= AUTO_ACCEPT_THRESHOLD
    fun needsManualReview(): Boolean = overall < MANUAL_REVIEW_THRESHOLD
}
