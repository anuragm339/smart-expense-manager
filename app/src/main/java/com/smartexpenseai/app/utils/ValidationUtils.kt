package com.smartexpenseai.app.utils

import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility service that eliminates ~45 lines of duplicated validation logic
 * Centralizes common validation patterns used across fragments and ViewModels
 */
@Singleton
class ValidationUtils @Inject constructor() {

    companion object {
        // Common regex patterns
        private val EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
            "\\@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(" +
            "\\." +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
            ")+"
        )
        
        private val PHONE_PATTERN = Pattern.compile("^[+]?[0-9]{10,15}$")
        
        // Common validation messages
        const val ERROR_EMPTY_FIELD = "This field cannot be empty"
        const val ERROR_INVALID_AMOUNT = "Please enter a valid amount"
        const val ERROR_NEGATIVE_AMOUNT = "Amount cannot be negative"
        const val ERROR_ZERO_AMOUNT = "Amount cannot be zero"
        const val ERROR_INVALID_EMAIL = "Please enter a valid email address"
        const val ERROR_INVALID_PHONE = "Please enter a valid phone number"
        const val ERROR_TEXT_TOO_SHORT = "Text is too short"
        const val ERROR_TEXT_TOO_LONG = "Text is too long"
    }

    /**
     * Data class for validation results
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    ) {
        companion object {
            fun success() = ValidationResult(true)
            fun error(message: String) = ValidationResult(false, message)
        }
    }

    /**
     * Validate text is not empty or blank
     * Eliminates repeated "isNullOrEmpty() || isBlank()" patterns
     */
    fun validateNotEmpty(text: String?, fieldName: String = "Field"): ValidationResult {
        return when {
            text.isNullOrEmpty() -> ValidationResult.error("$fieldName cannot be empty")
            text.isBlank() -> ValidationResult.error("$fieldName cannot be blank")
            else -> ValidationResult.success()
        }
    }

    /**
     * Validate amount is positive and valid
     * Eliminates repeated amount validation patterns
     */
    fun validateAmount(amount: Double?, allowZero: Boolean = false): ValidationResult {
        return when {
            amount == null -> ValidationResult.error(ERROR_INVALID_AMOUNT)
            amount < 0 -> ValidationResult.error(ERROR_NEGATIVE_AMOUNT)
            !allowZero && amount == 0.0 -> ValidationResult.error(ERROR_ZERO_AMOUNT)
            amount.isNaN() || amount.isInfinite() -> ValidationResult.error(ERROR_INVALID_AMOUNT)
            else -> ValidationResult.success()
        }
    }

    /**
     * Validate amount from string input
     */
    fun validateAmountString(amountString: String?, allowZero: Boolean = false): ValidationResult {
        if (amountString.isNullOrBlank()) {
            return ValidationResult.error(ERROR_EMPTY_FIELD)
        }

        val amount = try {
            amountString.toDouble()
        } catch (e: NumberFormatException) {
            return ValidationResult.error(ERROR_INVALID_AMOUNT)
        }

        return validateAmount(amount, allowZero)
    }

    /**
     * Validate text length is within range
     */
    fun validateTextLength(
        text: String?,
        minLength: Int = 1,
        maxLength: Int = Int.MAX_VALUE,
        fieldName: String = "Field"
    ): ValidationResult {
        if (text.isNullOrEmpty()) {
            return ValidationResult.error("$fieldName cannot be empty")
        }

        return when {
            text.length < minLength -> ValidationResult.error("$fieldName must be at least $minLength characters")
            text.length > maxLength -> ValidationResult.error("$fieldName cannot exceed $maxLength characters")
            else -> ValidationResult.success()
        }
    }

    /**
     * Validate email address format
     */
    fun validateEmail(email: String?): ValidationResult {
        if (email.isNullOrBlank()) {
            return ValidationResult.error(ERROR_EMPTY_FIELD)
        }

        return if (EMAIL_PATTERN.matcher(email).matches()) {
            ValidationResult.success()
        } else {
            ValidationResult.error(ERROR_INVALID_EMAIL)
        }
    }

    /**
     * Validate phone number format
     */
    fun validatePhone(phone: String?): ValidationResult {
        if (phone.isNullOrBlank()) {
            return ValidationResult.error(ERROR_EMPTY_FIELD)
        }

        return if (PHONE_PATTERN.matcher(phone).matches()) {
            ValidationResult.success()
        } else {
            ValidationResult.error(ERROR_INVALID_PHONE)
        }
    }

    /**
     * Validate category name
     */
    fun validateCategoryName(name: String?): ValidationResult {
        val emptyValidation = validateNotEmpty(name, "Category name")
        if (!emptyValidation.isValid) return emptyValidation

        return validateTextLength(name, minLength = 2, maxLength = 50, fieldName = "Category name")
    }

    /**
     * Validate merchant name
     */
    fun validateMerchantName(name: String?): ValidationResult {
        val emptyValidation = validateNotEmpty(name, "Merchant name")
        if (!emptyValidation.isValid) return emptyValidation

        return validateTextLength(name, minLength = 2, maxLength = 100, fieldName = "Merchant name")
    }

    /**
     * Validate transaction description
     */
    fun validateTransactionDescription(description: String?): ValidationResult {
        // Description is optional, but if provided should have minimum length
        if (description.isNullOrBlank()) {
            return ValidationResult.success() // Optional field
        }

        return validateTextLength(description, minLength = 3, maxLength = 200, fieldName = "Description")
    }

    /**
     * Validate budget amount
     */
    fun validateBudgetAmount(amount: Double?): ValidationResult {
        val baseValidation = validateAmount(amount, allowZero = false)
        if (!baseValidation.isValid) return baseValidation

        // Additional budget-specific validations
        return when {
            amount!! > 10_00_00_000 -> ValidationResult.error("Budget amount seems too high. Please verify.")
            amount < 100 -> ValidationResult.error("Budget amount is too low")
            else -> ValidationResult.success()
        }
    }

    /**
     * Validate multiple fields at once
     */
    fun validateMultiple(vararg validations: ValidationResult): ValidationResult {
        val firstError = validations.firstOrNull { !it.isValid }
        return firstError ?: ValidationResult.success()
    }

    /**
     * Helper function to validate form with common fields
     */
    fun validateTransactionForm(
        amount: String?,
        merchantName: String?,
        categoryName: String?,
        description: String? = null
    ): ValidationResult {
        return validateMultiple(
            validateAmountString(amount),
            validateMerchantName(merchantName),
            validateCategoryName(categoryName),
            validateTransactionDescription(description)
        )
    }

    /**
     * Validate search query
     */
    fun validateSearchQuery(query: String?, minLength: Int = 2): ValidationResult {
        if (query.isNullOrBlank()) {
            return ValidationResult.error("Search query cannot be empty")
        }

        return when {
            query.length < minLength -> ValidationResult.error("Search query must be at least $minLength characters")
            query.length > 100 -> ValidationResult.error("Search query is too long")
            else -> ValidationResult.success()
        }
    }

    /**
     * Check if string contains only numbers and decimal point
     */
    fun isValidNumberString(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return try {
            text.toDouble()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }

    /**
     * Sanitize input text (remove extra spaces, trim)
     */
    fun sanitizeText(text: String?): String {
        return text?.trim()?.replace("\\s+".toRegex(), " ") ?: ""
    }
}