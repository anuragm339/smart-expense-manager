package com.expensemanager.app.data.exceptions

/**
 * Custom exception classes for better error handling in production
 */

sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Network related exceptions
 */
sealed class NetworkException(message: String, cause: Throwable? = null) : AppException(message, cause) {
    class NoInternetConnection : NetworkException("No internet connection available")
    class Timeout : NetworkException("Request timed out")
    class ServerError(code: Int, message: String) : NetworkException("Server error: $code - $message")
    class BadRequest(message: String) : NetworkException("Bad request: $message")
    class Unauthorized : NetworkException("Unauthorized access")
    class NotFound : NetworkException("Resource not found")
    class Unknown(message: String, cause: Throwable?) : NetworkException(message, cause)
}

/**
 * Database related exceptions
 */
sealed class DatabaseException(message: String, cause: Throwable? = null) : AppException(message, cause) {
    class ReadError(cause: Throwable?) : DatabaseException("Error reading from database", cause)
    class WriteError(cause: Throwable?) : DatabaseException("Error writing to database", cause)
    class CorruptionError(cause: Throwable?) : DatabaseException("Database corruption detected", cause)
    class MigrationError(cause: Throwable?) : DatabaseException("Database migration failed", cause)
}

/**
 * Data parsing related exceptions
 */
sealed class DataException(message: String, cause: Throwable? = null) : AppException(message, cause) {
    class InvalidFormat(format: String) : DataException("Invalid data format: $format")
    class MissingRequiredField(field: String) : DataException("Missing required field: $field")
    class ParseError(message: String, cause: Throwable?) : DataException("Parse error: $message", cause)
}

/**
 * SMS parsing related exceptions
 */
sealed class SMSException(message: String, cause: Throwable? = null) : AppException(message, cause) {
    class PermissionDenied : SMSException("SMS permission denied")
    class InvalidFormat(sms: String) : SMSException("Invalid SMS format: ${sms.take(50)}...")
    class BankNotSupported(bankCode: String) : SMSException("Bank not supported: $bankCode")
    class AmountParsingError(sms: String) : SMSException("Cannot parse amount from SMS: ${sms.take(50)}...")
}

/**
 * AI/API related exceptions
 */
sealed class AIException(message: String, cause: Throwable? = null) : AppException(message, cause) {
    class ServiceUnavailable : AIException("AI service temporarily unavailable")
    class InvalidResponse(response: String) : AIException("Invalid AI response format: ${response.take(100)}...")
    class QuotaExceeded : AIException("AI service quota exceeded")
    class ModelError(message: String) : AIException("AI model error: $message")
}

/**
 * User input related exceptions
 */
sealed class ValidationException(message: String) : AppException(message) {
    class InvalidAmount(amount: String) : ValidationException("Invalid amount: $amount")
    class InvalidMerchant(merchant: String) : ValidationException("Invalid merchant: $merchant")
    class InvalidCategory(category: String) : ValidationException("Invalid category: $category")
    class InvalidDateRange : ValidationException("Invalid date range")
}