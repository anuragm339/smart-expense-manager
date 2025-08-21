package com.expensemanager.app.data.models

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
    
    companion object {
        fun fromParsedTransaction(
            parsedTransaction: com.expensemanager.app.utils.ParsedTransaction,
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
                transactionType = TransactionType.DEBIT,
                isProcessed = false // New transactions start as unprocessed
            )
        }
        
        private fun generateId(parsedTransaction: com.expensemanager.app.utils.ParsedTransaction): String {
            // Generate a unique ID based on transaction data
            val content = "${parsedTransaction.amount}_${parsedTransaction.merchant}_${parsedTransaction.date.time}_${parsedTransaction.bankName}"
            return content.hashCode().toString()
        }
    }
}

enum class TransactionType {
    DEBIT, CREDIT
}