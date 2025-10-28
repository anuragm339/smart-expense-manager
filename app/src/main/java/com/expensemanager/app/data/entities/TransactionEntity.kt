package com.expensemanager.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import java.util.Date

// FIXED: Add unique index on sms_id to prevent duplicate SMS entries
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["sms_id"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "sms_id")
    val smsId: String,
    
    @ColumnInfo(name = "amount")
    val amount: Double,
    
    @ColumnInfo(name = "raw_merchant")
    val rawMerchant: String,
    
    @ColumnInfo(name = "normalized_merchant") 
    val normalizedMerchant: String,
    
    @ColumnInfo(name = "bank_name")
    val bankName: String,
    
    @ColumnInfo(name = "transaction_date")
    val transactionDate: Date,
    
    @ColumnInfo(name = "raw_sms_body")
    val rawSmsBody: String,

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    @ColumnInfo(name = "is_debit")
    val isDebit: Boolean = true,

    @ColumnInfo(name = "reference_number")
    val referenceNumber: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Date,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date
) {
    companion object {
        /**
         * Generate a consistent SMS ID from SMS content to prevent duplicates
         * Priority: reference_number > (sender + body + timestamp)
         */
        fun generateSmsId(
            address: String,
            body: String,
            timestamp: Long,
            referenceNumber: String? = null
        ): String {
            // If reference number exists, use it for deduplication (more reliable)
            if (!referenceNumber.isNullOrBlank()) {
                return "ref_${referenceNumber}_${address}"
            }

            // Fallback to body hash method
            val bodyHash = body.hashCode().toString()
            return "${address}_${bodyHash}_${timestamp}"
        }

        /**
         * Generate a deduplication key for transaction matching
         * Enhanced with reference number support
         */
        fun generateDeduplicationKey(
            merchant: String,
            amount: Double,
            date: Date,
            bankName: String,
            referenceNumber: String? = null,
            windowMinutes: Long = 10
        ): String {
            // If reference number exists, it's the most reliable dedup key
            if (!referenceNumber.isNullOrBlank()) {
                return "ref_${referenceNumber}_${bankName.lowercase()}"
            }

            // Fallback to time-window based deduplication
            val windowMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(windowMinutes)
            val bucket = if (windowMillis > 0) date.time / windowMillis else date.time
            val roundedAmount = String.format(java.util.Locale.US, "%.2f", amount)

            return listOf(
                merchant.lowercase(),
                roundedAmount,
                bucket.toString(),
                bankName.lowercase()
            ).joinToString(separator = "_")
        }
    }
}
