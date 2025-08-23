package com.expensemanager.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.Date

@Entity(tableName = "transactions")
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
)