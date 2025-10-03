package com.expensemanager.app.data.models

import com.expensemanager.app.models.ParsedTransaction
import com.expensemanager.app.data.entities.TransactionEntity
import java.util.Date

data class Transaction(
    val id: String,
    val amount: Double,
    val merchant: String,
    val category: String,
    val date: Long, // Store as timestamp
    val rawSMS: String,
    val confidence: Float,
    val bankName: String,
    val transactionType: TransactionType = TransactionType.DEBIT,
    val isProcessed: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(), // Store as timestamp
    val isDuplicate: Boolean = false,
    val notes: String = ""
) {
    // Helper methods for Date conversion
    fun getDateAsDate(): Date = Date(date)
    fun getCreatedAtAsDate(): Date = Date(createdAt)
    
    // Extension properties for ViewModel compatibility
    val timestamp: Long get() = date
    val categoryId: String get() = category
    val categoryName: String? get() = if (category.isNotEmpty()) category else null
    val description: String get() = merchant
    val smsBody: String? get() = if (rawSMS.isNotEmpty()) rawSMS else null
    val note: String? get() = if (notes.isNotEmpty()) notes else null
    val excludeFromBudget: Boolean get() = false // Default to false
    
    companion object {
        fun fromParsedTransaction(
            parsedTransaction: ParsedTransaction,
            id: String = generateId(parsedTransaction)
        ): Transaction {
            return Transaction(
                id = id,
                amount = parsedTransaction.amount,
                merchant = parsedTransaction.merchant,
                category = "Other", // Default category, will be updated by category manager
                date = parsedTransaction.date.time,
                rawSMS = parsedTransaction.rawSMS,
                confidence = parsedTransaction.confidence,
                bankName = parsedTransaction.bankName,
                transactionType = if (parsedTransaction.isDebit) TransactionType.DEBIT else TransactionType.CREDIT,
                isProcessed = false // New transactions start as unprocessed
            )
        }
        
        private fun generateId(parsedTransaction: ParsedTransaction): String {
            // Generate a unique ID based on transaction data
            val content = "${parsedTransaction.amount}_${parsedTransaction.merchant}_${parsedTransaction.date.time}_${parsedTransaction.bankName}"
            return content.hashCode().toString()
        }

        /**
         * Convert TransactionEntity to Transaction domain model
         */
        fun fromEntity(entity: TransactionEntity): Transaction {
            return Transaction(
                id = entity.id.toString(),
                amount = entity.amount,
                merchant = entity.normalizedMerchant,
                category = "General", // Default category since not available in entity
                date = entity.transactionDate.time,
                rawSMS = entity.rawSmsBody,
                confidence = entity.confidenceScore,
                bankName = entity.bankName,
                transactionType = if (entity.isDebit) TransactionType.DEBIT else TransactionType.CREDIT,
                isProcessed = true,
                createdAt = entity.createdAt.time
            )
        }

        /**
         * Convert list of TransactionEntity to Transaction domain models
         */
        fun fromEntities(entities: List<TransactionEntity>): List<Transaction> {
            return entities.map { fromEntity(it) }
        }
    }
}

enum class TransactionType {
    DEBIT, CREDIT
}