package com.expensemanager.app.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.utils.logging.StructuredLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Handles network errors and provides recovery strategies for AI insights
 */
@Singleton
class NetworkErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "NetworkErrorHandler"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val EXPONENTIAL_BACKOFF_MULTIPLIER = 2
    }

    private val logger = StructuredLogger(LogConfig.FeatureTags.NETWORK, TAG)

    /**
     * Error types for different network scenarios
     */
    sealed class NetworkError(val message: String, val recoverable: Boolean) {
        object NoInternet : NetworkError("No internet connection", true)
        object Timeout : NetworkError("Request timed out", true)
        object ServerError : NetworkError("Server error", true)
        object ClientError : NetworkError("Invalid request", false)
        object RateLimited : NetworkError("Rate limit exceeded", true)
        object UnknownHost : NetworkError("Unable to reach server", true)
        class Generic(message: String) : NetworkError(message, false)
    }

    /**
     * Recovery strategy for handling errors
     */
    data class RecoveryStrategy(
        val shouldRetry: Boolean,
        val delayMs: Long,
        val maxAttempts: Int,
        val fallbackAction: FallbackAction
    )

    enum class FallbackAction {
        USE_CACHE,
        GENERATE_OFFLINE,
        SHOW_ERROR,
        RETRY_LATER
    }

    /**
     * Analyze exception and return appropriate error type
     */
    fun analyzeError(throwable: Throwable): NetworkError {
        return when (throwable) {
            is UnknownHostException -> NetworkError.UnknownHost
            is SocketTimeoutException -> NetworkError.Timeout
            is IOException -> {
                if (!isNetworkAvailable()) {
                    NetworkError.NoInternet
                } else {
                    NetworkError.Generic("Network IO error: ${throwable.message}")
                }
            }
            is HttpException -> {
                when (throwable.code()) {
                    in 400..499 -> NetworkError.ClientError
                    429 -> NetworkError.RateLimited
                    in 500..599 -> NetworkError.ServerError
                    else -> NetworkError.Generic("HTTP error ${throwable.code()}")
                }
            }
            else -> NetworkError.Generic(throwable.message ?: "Unknown error")
        }
    }

    /**
     * Get recovery strategy based on error type and attempt count
     */
    fun getRecoveryStrategy(error: NetworkError, attemptCount: Int): RecoveryStrategy {
        return when (error) {
            is NetworkError.NoInternet -> RecoveryStrategy(
                shouldRetry = false,
                delayMs = 0L,
                maxAttempts = 0,
                fallbackAction = FallbackAction.USE_CACHE
            )

            is NetworkError.Timeout -> RecoveryStrategy(
                shouldRetry = attemptCount < MAX_RETRY_ATTEMPTS,
                delayMs = calculateBackoffDelay(attemptCount),
                maxAttempts = MAX_RETRY_ATTEMPTS,
                fallbackAction = FallbackAction.USE_CACHE
            )

            is NetworkError.ServerError -> RecoveryStrategy(
                shouldRetry = attemptCount < MAX_RETRY_ATTEMPTS,
                delayMs = calculateBackoffDelay(attemptCount),
                maxAttempts = MAX_RETRY_ATTEMPTS,
                fallbackAction = FallbackAction.USE_CACHE
            )

            is NetworkError.RateLimited -> RecoveryStrategy(
                shouldRetry = false,
                delayMs = 60000L, // Wait 1 minute for rate limit
                maxAttempts = 1,
                fallbackAction = FallbackAction.USE_CACHE
            )

            is NetworkError.ClientError -> RecoveryStrategy(
                shouldRetry = false,
                delayMs = 0L,
                maxAttempts = 0,
                fallbackAction = FallbackAction.SHOW_ERROR
            )

            is NetworkError.UnknownHost -> RecoveryStrategy(
                shouldRetry = attemptCount < 2,
                delayMs = RETRY_DELAY_MS,
                maxAttempts = 2,
                fallbackAction = FallbackAction.USE_CACHE
            )

            is NetworkError.Generic -> RecoveryStrategy(
                shouldRetry = false,
                delayMs = 0L,
                maxAttempts = 0,
                fallbackAction = FallbackAction.GENERATE_OFFLINE
            )
        }
    }

    /**
     * Calculate exponential backoff delay
     */
    private fun calculateBackoffDelay(attemptCount: Int): Long {
        return (RETRY_DELAY_MS * EXPONENTIAL_BACKOFF_MULTIPLIER.toDouble().pow(attemptCount.toDouble())).toLong()
    }

    /**
     * Check if network is available
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Monitor network connectivity changes
     */
    fun networkConnectivityFlow(): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                logger.debug("networkConnectivityFlow", "Network available")
                trySend(true)
            }

            override fun onLost(network: Network) {
                logger.debug("networkConnectivityFlow", "Network lost")
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                logger.debug("networkConnectivityFlow", "Network capabilities changed", "Has internet: $hasInternet")
                trySend(hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Send initial state
        trySend(isNetworkAvailable())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Get user-friendly error message
     */
    fun getUserFriendlyMessage(error: NetworkError): String {
        return when (error) {
            is NetworkError.NoInternet -> "No internet connection. Please check your network settings."
            is NetworkError.Timeout -> "Request timed out. Please try again."
            is NetworkError.ServerError -> "Server is temporarily unavailable. Please try again later."
            is NetworkError.ClientError -> "Invalid request. Please contact support if this persists."
            is NetworkError.RateLimited -> "Too many requests. Please wait a moment before trying again."
            is NetworkError.UnknownHost -> "Unable to reach the server. Please check your internet connection."
            is NetworkError.Generic -> error.message
        }
    }

    /**
     * Log error with appropriate level based on severity
     */
    fun logError(error: NetworkError, throwable: Throwable, context: String) {
        when (error) {
            is NetworkError.NoInternet,
            is NetworkError.Timeout -> logger.warnWithThrowable("logError", "$context: ${error.message}", throwable)

            is NetworkError.ServerError,
            is NetworkError.RateLimited -> logger.error("logError", "$context: ${error.message}", throwable)

            is NetworkError.ClientError,
            is NetworkError.Generic -> logger.error("logError", "$context: ${error.message}", throwable)

            is NetworkError.UnknownHost -> logger.warnWithThrowable("logError", "$context: ${error.message}", throwable)
        }
    }
}
