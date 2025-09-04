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
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date
) {
    companion object {
        /**
         * Generate a consistent SMS ID from SMS content to prevent duplicates
         */
        fun generateSmsId(address: String, body: String, timestamp: Long): String {
            // Create a unique identifier based on sender, body hash, and timestamp
            // This ensures same SMS won't create multiple records
            val bodyHash = body.hashCode().toString()
            return "${address}_${bodyHash}_${timestamp}"
        }
        
        /**
         * Generate a deduplication key for transaction matching
         */
        fun generateDeduplicationKey(
            merchant: String, 
            amount: Double, 
            date: Date, 
            bankName: String
        ): String {
            // Create key for detecting duplicate transactions from different SMS
            val dayPrecisionDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(date)
            return "${merchant.lowercase()}_${amount}_${dayPrecisionDate}_${bankName.lowercase()}"
        }
    }
}