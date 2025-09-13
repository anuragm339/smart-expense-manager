package com.expensemanager.app.utils

import android.content.Context
import com.expensemanager.app.R
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized error handling utility that converts technical exceptions
 * into user-friendly error messages
 */
@Singleton
class ErrorHandler @Inject constructor() {
    
    /**
     * Convert an exception into a user-friendly error message
     */
    fun getErrorMessage(context: Context, throwable: Throwable): String {
        return when (throwable) {
            is ConnectException, is UnknownHostException -> {
                "Network connection error. Please check your internet connection."
            }
            is SocketTimeoutException -> {
                "Connection timeout. Please try again."
            }
            is SecurityException -> {
                if (throwable.message?.contains("permission", ignoreCase = true) == true) {
                    "Permission required. Please grant the necessary permissions in settings."
                } else {
                    "Security error occurred. Please try again."
                }
            }
            is IllegalArgumentException -> {
                "Invalid data provided. Please check your input."
            }
            is android.database.sqlite.SQLiteException -> {
                "Database error occurred. Please restart the app."
            }
            else -> {
                // Check for specific patterns in the error message
                val message = throwable.message?.lowercase() ?: ""
                when {
                    message.contains("network") || message.contains("connection") -> {
                        "Network connection error. Please check your internet connection."
                    }
                    message.contains("permission") -> {
                        "Permission required. Please grant the necessary permissions in settings."
                    }
                    message.contains("database") || message.contains("sql") -> {
                        "Database error occurred. Please restart the app."
                    }
                    message.contains("timeout") -> {
                        "Operation timed out. Please try again."
                    }
                    message.contains("not found") -> {
                        "Requested data not found."
                    }
                    message.contains("duplicate") -> {
                        "Duplicate data detected."
                    }
                    else -> {
                        "Something went wrong. Please try again."
                    }
                }
            }
        }
    }
    
    /**
     * Get error message with retry option
     */
    fun getErrorMessageWithRetry(context: Context, throwable: Throwable): Pair<String, Boolean> {
        val message = getErrorMessage(context, throwable)
        val canRetry = when (throwable) {
            is ConnectException, is SocketTimeoutException, is UnknownHostException -> true
            is android.database.sqlite.SQLiteException -> false // Database errors usually need app restart
            is SecurityException -> false // Permission errors need user action
            else -> true // Most other errors can be retried
        }
        return Pair(message, canRetry)
    }
    
    /**
     * Get detailed error information for debugging
     */
    fun getDetailedErrorInfo(throwable: Throwable): ErrorInfo {
        return ErrorInfo(
            type = throwable::class.simpleName ?: "Unknown",
            message = throwable.message ?: "No message",
            stackTrace = throwable.stackTrace.take(5).joinToString("\n") { 
                "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            },
            cause = throwable.cause?.let { "Caused by: ${it::class.simpleName}: ${it.message}" }
        )
    }
    
    /**
     * Check if error is recoverable
     */
    fun isRecoverableError(throwable: Throwable): Boolean {
        return when (throwable) {
            is ConnectException, is SocketTimeoutException, is UnknownHostException -> true
            is SecurityException -> false
            is OutOfMemoryError -> false
            is android.database.sqlite.SQLiteDatabaseCorruptException -> false
            else -> true
        }
    }
}

/**
 * Data class containing detailed error information
 */
data class ErrorInfo(
    val type: String,
    val message: String,
    val stackTrace: String,
    val cause: String?
)