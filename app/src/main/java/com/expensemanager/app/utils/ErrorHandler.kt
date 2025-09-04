package com.expensemanager.app.utils

import android.content.Context
import com.expensemanager.app.R
import com.expensemanager.app.data.exceptions.*
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Centralized error handling utility for production-ready error management
 */
object ErrorHandler {

    /**
     * Convert exceptions to user-friendly messages
     */
    fun getErrorMessage(context: Context, throwable: Throwable): String {
        return when (throwable) {
            is AppException -> getAppExceptionMessage(context, throwable)
            is HttpException -> getHttpExceptionMessage(context, throwable)
            is IOException -> getIOExceptionMessage(context, throwable)
            else -> context.getString(R.string.error_generic)
        }
    }

    /**
     * Log error appropriately based on type and severity
     */
    fun logError(throwable: Throwable, context: String = "Unknown") {
        when (throwable) {
            is NetworkException.NoInternetConnection -> Timber.i("No internet connection in $context")
            is NetworkException.Timeout -> Timber.w("Request timeout in $context")
            is NetworkException -> Timber.e(throwable, "Network error in $context")
            is DatabaseException -> Timber.e(throwable, "Database error in $context")
            is ValidationException -> Timber.d("Validation error in $context: ${throwable.message}")
            is AIException.ServiceUnavailable -> Timber.w("AI service unavailable in $context")
            is AIException -> Timber.e(throwable, "AI service error in $context")
            is SMSException.PermissionDenied -> Timber.w("SMS permission denied in $context")
            is SMSException -> Timber.d("SMS parsing error in $context: ${throwable.message}")
            else -> Timber.e(throwable, "Unexpected error in $context")
        }
    }

    /**
     * Determine if error is recoverable
     */
    fun isRecoverable(throwable: Throwable): Boolean {
        return when (throwable) {
            is NetworkException.NoInternetConnection -> true
            is NetworkException.Timeout -> true
            is NetworkException.ServerError -> throwable.message?.contains("5") == true // 5xx errors
            is AIException.ServiceUnavailable -> true
            is AIException.QuotaExceeded -> false
            is DatabaseException.CorruptionError -> false
            is ValidationException -> true
            else -> false
        }
    }

    /**
     * Convert HTTP exceptions to app exceptions
     */
    fun mapHttpException(httpException: HttpException): NetworkException {
        return when (httpException.code()) {
            400 -> NetworkException.BadRequest(httpException.message())
            401 -> NetworkException.Unauthorized()
            404 -> NetworkException.NotFound()
            in 500..599 -> NetworkException.ServerError(httpException.code(), httpException.message())
            else -> NetworkException.Unknown(httpException.message(), httpException)
        }
    }

    /**
     * Convert IO exceptions to app exceptions
     */
    fun mapIOException(ioException: IOException): NetworkException {
        return when (ioException) {
            is UnknownHostException -> NetworkException.NoInternetConnection()
            is SocketTimeoutException -> NetworkException.Timeout()
            else -> NetworkException.Unknown(ioException.message ?: "Network error", ioException)
        }
    }

    private fun getAppExceptionMessage(context: Context, exception: AppException): String {
        return when (exception) {
            is NetworkException.NoInternetConnection -> 
                context.getString(R.string.error_no_internet)
            is NetworkException.Timeout -> 
                context.getString(R.string.error_timeout)
            is NetworkException.ServerError -> 
                context.getString(R.string.error_server)
            is NetworkException.Unauthorized -> 
                context.getString(R.string.error_unauthorized)
            is DatabaseException.ReadError -> 
                context.getString(R.string.error_database_read)
            is DatabaseException.WriteError -> 
                context.getString(R.string.error_database_write)
            is DatabaseException.CorruptionError -> 
                context.getString(R.string.error_database_corruption)
            is ValidationException.InvalidAmount -> 
                context.getString(R.string.error_invalid_amount)
            is ValidationException.InvalidMerchant -> 
                context.getString(R.string.error_invalid_merchant)
            is AIException.ServiceUnavailable -> 
                context.getString(R.string.error_ai_service_unavailable)
            is SMSException.PermissionDenied -> 
                context.getString(R.string.error_sms_permission)
            else -> exception.message ?: context.getString(R.string.error_generic)
        }
    }

    private fun getHttpExceptionMessage(context: Context, exception: HttpException): String {
        return when (exception.code()) {
            400 -> context.getString(R.string.error_bad_request)
            401 -> context.getString(R.string.error_unauthorized)
            404 -> context.getString(R.string.error_not_found)
            in 500..599 -> context.getString(R.string.error_server)
            else -> context.getString(R.string.error_network)
        }
    }

    private fun getIOExceptionMessage(context: Context, exception: IOException): String {
        return when (exception) {
            is UnknownHostException -> context.getString(R.string.error_no_internet)
            is SocketTimeoutException -> context.getString(R.string.error_timeout)
            else -> context.getString(R.string.error_network)
        }
    }
}