package com.expensemanager.app.domain.usecase.transaction

import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.domain.repository.TransactionRepositoryInterface
import java.util.Date
import javax.inject.Inject

/**
 * Use case for updating existing transactions with business logic
 * Handles validation and transaction updates
 */
class UpdateTransactionUseCase @Inject constructor(
    private val repository: TransactionRepositoryInterface
) {
    
    companion object {
        private const val TAG = "UpdateTransactionUseCase"
    }
    
    /**
     * Update an existing transaction
     */
    suspend fun execute(transaction: TransactionEntity): Result<Unit> {
        return try {
            Timber.tag(TAG).d("Updating transaction ID: ${transaction.id}")
            
            // Validate transaction data
            val validationResult = validateTransactionUpdate(transaction)
            if (!validationResult.isValid) {
                Timber.tag(TAG).w("Transaction update validation failed: ${validationResult.error}")
                return Result.failure(IllegalArgumentException(validationResult.error))
            }
            
            // Update the updatedAt timestamp
            val updatedTransaction = transaction.copy(updatedAt = Date())
            
            repository.updateTransaction(updatedTransaction)
            Timber.tag(TAG).d("Transaction updated successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating transaction")
            Result.failure(e)
        }
    }
    
    /**
     * Update merchant for a transaction
     */
    suspend fun updateMerchant(transactionId: Long, newMerchant: String): Result<Unit> {
        return try {
            Timber.tag(TAG).d("Updating merchant for transaction ID: $transactionId to: $newMerchant")
            
            // Get existing transaction
            val existingTransaction = getTransactionById(transactionId)
                ?: return Result.failure(Exception("Transaction not found"))
            
            // Update merchant fields
            val normalizedMerchant = normalizeMerchantName(newMerchant)
            val updatedTransaction = existingTransaction.copy(
                rawMerchant = newMerchant,
                normalizedMerchant = normalizedMerchant,
                updatedAt = Date()
            )
            
            repository.updateTransaction(updatedTransaction)
            Timber.tag(TAG).d("Transaction merchant updated successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating transaction merchant")
            Result.failure(e)
        }
    }
    
    /**
     * Update amount for a transaction
     */
    suspend fun updateAmount(transactionId: Long, newAmount: Double): Result<Unit> {
        return try {
            Timber.tag(TAG).d("Updating amount for transaction ID: $transactionId to: ₹$newAmount")
            
            if (newAmount <= 0) {
                return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
            }
            
            // Get existing transaction
            val existingTransaction = getTransactionById(transactionId)
                ?: return Result.failure(Exception("Transaction not found"))
            
            // Update amount
            val updatedTransaction = existingTransaction.copy(
                amount = newAmount,
                updatedAt = Date()
            )
            
            repository.updateTransaction(updatedTransaction)
            Timber.tag(TAG).d("Transaction amount updated successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating transaction amount")
            Result.failure(e)
        }
    }
    
    /**
     * Update transaction date
     */
    suspend fun updateTransactionDate(transactionId: Long, newDate: Date): Result<Unit> {
        return try {
            Timber.tag(TAG).d("Updating date for transaction ID: $transactionId")
            
            // Get existing transaction
            val existingTransaction = getTransactionById(transactionId)
                ?: return Result.failure(Exception("Transaction not found"))
            
            // Update date
            val updatedTransaction = existingTransaction.copy(
                transactionDate = newDate,
                updatedAt = Date()
            )
            
            repository.updateTransaction(updatedTransaction)
            Timber.tag(TAG).d("Transaction date updated successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating transaction date")
            Result.failure(e)
        }
    }
    
    /**
     * Helper method to get transaction by ID (would need to be added to repository interface)
     */
    private suspend fun getTransactionById(transactionId: Long): TransactionEntity? {
        // This would need to be implemented in the repository interface and implementation
        // For now, we'll handle this through the business logic layer
        return null
    }
    
    /**
     * Validate transaction update data
     */
    private fun validateTransactionUpdate(transaction: TransactionEntity): ValidationResult {
        return when {
            transaction.id <= 0 -> ValidationResult(false, "Transaction ID must be valid")
            transaction.amount <= 0 -> ValidationResult(false, "Amount must be greater than 0")
            transaction.rawMerchant.isBlank() -> ValidationResult(false, "Merchant name cannot be empty")
            transaction.normalizedMerchant.isBlank() -> ValidationResult(false, "Normalized merchant name cannot be empty")
            transaction.bankName.isBlank() -> ValidationResult(false, "Bank name cannot be empty")
            transaction.rawSmsBody.isBlank() -> ValidationResult(false, "SMS body cannot be empty")
            transaction.confidenceScore < 0.0f || transaction.confidenceScore > 1.0f -> 
                ValidationResult(false, "Confidence score must be between 0.0 and 1.0")
            else -> ValidationResult(true, null)
        }
    }
    
    /**
     * Helper method to normalize merchant names consistently
     */
    private fun normalizeMerchantName(merchant: String): String {
        return merchant.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}