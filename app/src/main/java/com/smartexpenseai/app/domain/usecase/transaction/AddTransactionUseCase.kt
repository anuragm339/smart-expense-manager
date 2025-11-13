package com.smartexpenseai.app.domain.usecase.transaction

import timber.log.Timber
import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.domain.repository.TransactionRepositoryInterface
import com.smartexpenseai.app.domain.repository.MerchantRepositoryInterface
import com.smartexpenseai.app.domain.repository.CategoryRepositoryInterface
import javax.inject.Inject

/**
 * Use case for adding new transactions with business logic
 * Handles validation, duplicate detection, and transaction creation
 */
class AddTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepositoryInterface,
    private val merchantRepository: MerchantRepositoryInterface,
    private val categoryRepository: CategoryRepositoryInterface
) {
    
    companion object {
        private const val TAG = "AddTransactionUseCase"
    }
    
    /**
     * Add a new transaction with validation
     */
    suspend fun execute(transaction: TransactionEntity): Result<Long> {
        return try {
            Timber.tag(TAG).d("Adding new transaction: ${transaction.rawMerchant} - ₹${transaction.amount}")
            
            // Validate transaction data
            val validationResult = validateTransaction(transaction)
            if (!validationResult.isValid) {
                Timber.tag(TAG).w("Transaction validation failed: ${validationResult.error}")
                return Result.failure(IllegalArgumentException(validationResult.error))
            }
            
            // Check for duplicates by SMS ID
            if (transaction.smsId.isNotEmpty()) {
                val existingTransaction = transactionRepository.getTransactionBySmsId(transaction.smsId)
                if (existingTransaction != null) {
                    Timber.tag(TAG).w("Duplicate transaction found with SMS ID: ${transaction.smsId}")
                    return Result.failure(DuplicateTransactionException("Transaction already exists"))
                }
            }
            
            // For manual transactions, ensure merchant and category entities exist
            if (isManualTransaction(transaction)) {
                ensureMerchantAndCategoryExist(transaction)
            }
            
            // Insert the transaction
            val insertedId = transactionRepository.insertTransaction(transaction)
            if (insertedId > 0) {
                Timber.tag(TAG).d("Transaction added successfully with ID: $insertedId")
                Result.success(insertedId)
            } else {
                Timber.tag(TAG).e("Failed to insert transaction")
                Result.failure(Exception("Failed to insert transaction"))
            }
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error adding transaction")
            Result.failure(e)
        }
    }
    
    /**
     * Add multiple transactions (bulk insert)
     */
    suspend fun executeMultiple(transactions: List<TransactionEntity>): Result<Int> {
        return try {
            Timber.tag(TAG).d("Adding ${transactions.size} transactions")
            
            var successCount = 0
            var duplicateCount = 0
            var errorCount = 0
            
            for (transaction in transactions) {
                val result = execute(transaction)
                when {
                    result.isSuccess -> successCount++
                    result.exceptionOrNull() is DuplicateTransactionException -> duplicateCount++
                    else -> errorCount++
                }
            }
            
            Timber.tag(TAG).d("Bulk insert completed: $successCount added, $duplicateCount duplicates, $errorCount errors")
            Result.success(successCount)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in bulk transaction insert")
            Result.failure(e)
        }
    }
    
    /**
     * Validate transaction data
     * FIXED: Support both SMS and manual transactions
     */
    private fun validateTransaction(transaction: TransactionEntity): ValidationResult {
        return when {
            transaction.amount <= 0 -> ValidationResult(false, "Amount must be greater than 0")
            transaction.rawMerchant.isBlank() -> ValidationResult(false, "Merchant name cannot be empty")
            transaction.normalizedMerchant.isBlank() -> ValidationResult(false, "Normalized merchant name cannot be empty")
            // FIXED: For manual transactions, SMS-specific fields can be placeholder values
            isManualTransaction(transaction) && transaction.bankName.isBlank() -> ValidationResult(false, "Bank name cannot be empty for manual transactions")
            !isManualTransaction(transaction) && transaction.bankName.isBlank() -> ValidationResult(false, "Bank name cannot be empty")
            !isManualTransaction(transaction) && transaction.rawSmsBody.isBlank() -> ValidationResult(false, "SMS body cannot be empty")
            transaction.confidenceScore < 0.0f || transaction.confidenceScore > 1.0f -> 
                ValidationResult(false, "Confidence score must be between 0.0 and 1.0")
            else -> ValidationResult(true, null)
        }
    }
    
    /**
     * Check if this is a manual transaction (not from SMS)
     */
    private fun isManualTransaction(transaction: TransactionEntity): Boolean {
        return transaction.smsId.startsWith("MANUAL_") || 
               transaction.rawSmsBody == "MANUAL_ENTRY"
    }
    
    /**
     * Create a manual transaction entity for quick expense entry
     */
    fun createManualTransaction(
        amount: Double,
        merchantName: String,
        categoryName: String = "Other",
        bankName: String = "Manual Entry"
    ): TransactionEntity {
        val now = java.util.Date()
        val manualSmsId = "MANUAL_${System.currentTimeMillis()}"
        
        return TransactionEntity(
            smsId = manualSmsId,
            amount = amount,
            rawMerchant = merchantName,
            normalizedMerchant = merchantName.trim().lowercase(),
            categoryId = 1L,  // Default to "Other" category - will be updated in ensureMerchantAndCategoryExist
            bankName = bankName,
            transactionDate = now,
            rawSmsBody = "MANUAL_ENTRY: ₹$amount at $merchantName ($categoryName)",
            confidenceScore = 1.0f, // Manual entries are 100% confident
            isDebit = true,
            createdAt = now,
            updatedAt = now
        )
    }
    
    /**
     * Ensure merchant and category entities exist for manual transactions
     */
    private suspend fun ensureMerchantAndCategoryExist(transaction: TransactionEntity) {
        try {
            Timber.tag(TAG).d("Ensuring merchant and category entities exist for manual transaction")
            
            // Extract category name from SMS body (format: "MANUAL_ENTRY: ₹amount at merchant (category)")
            val categoryName = extractCategoryFromManualEntry(transaction.rawSmsBody)
            
            // First, ensure the category exists
            var category = categoryRepository.getCategoryByName(categoryName)
            if (category == null) {
                Timber.tag(TAG).d("Creating new category: $categoryName")
                val categoryId = categoryRepository.insertCategory(
                    com.smartexpenseai.app.data.entities.CategoryEntity(
                        name = categoryName,
                        emoji = getCategoryEmoji(categoryName),
                        color = getDefaultCategoryColor(categoryName),
                        isSystem = false, // Manual categories are not system categories
                        displayOrder = 100, // Put manual categories at the end
                        createdAt = java.util.Date()
                    )
                )
                category = categoryRepository.getCategoryById(categoryId)
            }
            
            if (category != null) {
                // Next, ensure the merchant exists and is linked to the category
                var merchant = merchantRepository.getMerchantByNormalizedName(transaction.normalizedMerchant)
                if (merchant == null) {
                    Timber.tag(TAG).d("Creating new merchant: ${transaction.rawMerchant} -> ${transaction.normalizedMerchant}")
                    merchantRepository.insertMerchant(
                        com.smartexpenseai.app.data.entities.MerchantEntity(
                            normalizedName = transaction.normalizedMerchant,
                            displayName = transaction.rawMerchant,
                            categoryId = category.id,
                            isUserDefined = true, // Manual merchants are user-defined
                            isExcludedFromExpenseTracking = false,
                            createdAt = java.util.Date()
                        )
                    )
                    Timber.tag(TAG).d("[SUCCESS] Created merchant ${transaction.rawMerchant} in category ${category.name}")
                }
            }
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error ensuring merchant and category entities exist")
            // Don't fail the transaction if merchant/category setup fails
        }
    }
    
    /**
     * Extract category name from manual entry SMS body
     */
    private fun extractCategoryFromManualEntry(rawSmsBody: String): String {
        return try {
            // Parse format: "MANUAL_ENTRY: ₹amount at merchant (category)"
            val categoryStart = rawSmsBody.lastIndexOf("(")
            val categoryEnd = rawSmsBody.lastIndexOf(")")
            if (categoryStart != -1 && categoryEnd != -1 && categoryEnd > categoryStart) {
                rawSmsBody.substring(categoryStart + 1, categoryEnd)
            } else {
                "Other"
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Error extracting category from manual entry: $rawSmsBody", e)
            "Other"
        }
    }
    
    /**
     * Get default color for a category
     */
    private fun getDefaultCategoryColor(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "food & dining" -> "#ff5722"
            "transportation" -> "#3f51b5"
            "groceries" -> "#4caf50"
            "healthcare" -> "#e91e63"
            "shopping" -> "#ff9800"
            "entertainment" -> "#9c27b0"
            "utilities" -> "#607d8b"
            else -> "#9e9e9e"
        }
    }
    
    /**
     * Get default emoji for a category
     */
    private fun getCategoryEmoji(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "food & dining" -> "🍽️"
            "transportation" -> "🚗"
            "groceries" -> "🛒"
            "healthcare" -> "🏥"
            "shopping" -> "🛍️"
            "entertainment" -> "🎬"
            "utilities" -> "💡"
            else -> "📂"
        }
    }
}

/**
 * Result of transaction validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val error: String?
)

/**
 * Exception thrown when trying to add a duplicate transaction
 */
class DuplicateTransactionException(message: String) : Exception(message)