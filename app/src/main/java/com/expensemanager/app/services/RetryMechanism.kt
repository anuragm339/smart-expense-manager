package com.expensemanager.app.services

import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.random.Random

/**
 * Intelligent retry mechanism for AI insights API calls
 * Implements exponential backoff and conditional retry logic
 */
@Singleton
class RetryMechanism @Inject constructor(
    private val errorHandler: NetworkErrorHandler
) {

    companion object {
        private const val TAG = "RetryMechanism"
    }

    /**
     * Retry configuration for different scenarios
     */
    data class RetryConfig(
        val maxAttempts: Int = 3,
        val initialDelayMs: Long = 1000L,
        val backoffMultiplier: Double = 2.0,
        val maxDelayMs: Long = 30000L,
        val retryOnCondition: (Throwable) -> Boolean = { true }
    )

    /**
     * Execute operation with intelligent retry
     */
    suspend fun <T> executeWithRetry(
        config: RetryConfig = RetryConfig(),
        operation: suspend () -> T
    ): Result<T> {
        var lastException: Throwable? = null
        var attemptCount = 0

        repeat(config.maxAttempts) { attempt ->
            attemptCount = attempt + 1

            try {
                Timber.tag(TAG).d("Attempt $attemptCount/${config.maxAttempts}")
                val result = operation()

                if (attemptCount > 1) {
                    Timber.tag(TAG).i("Operation succeeded on attempt $attemptCount")
                }

                return Result.success(result)

            } catch (e: Throwable) {
                lastException = e
                val networkError = errorHandler.analyzeError(e)

                errorHandler.logError(networkError, e, "Retry attempt $attemptCount")

                // Check if we should retry this error
                if (!config.retryOnCondition(e) || !networkError.recoverable) {
                    Timber.tag(TAG).w("Error is not recoverable, stopping retries")
                    return Result.failure(e)
                }

                // Don't delay on the last attempt
                if (attempt < config.maxAttempts - 1) {
                    val delayMs = calculateDelay(attempt, config)
                    Timber.tag(TAG).d("Retrying in ${delayMs}ms...")
                    delay(delayMs)
                }
            }
        }

        return Result.failure(lastException ?: Exception("Max retry attempts exceeded"))
    }

    /**
     * Create a Flow-based retry mechanism
     */
    fun <T> createRetryFlow(
        config: RetryConfig = RetryConfig(),
        operation: suspend () -> T
    ): Flow<Result<T>> = flow {
        emit(executeWithRetry(config, operation))
    }

    /**
     * Conditional retry for specific network scenarios
     */
    fun createNetworkAwareRetryConfig(): RetryConfig {
        return RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 2000L,
            backoffMultiplier = 2.0,
            maxDelayMs = 30000L,
            retryOnCondition = { throwable ->
                val error = errorHandler.analyzeError(throwable)
                when (error) {
                    is NetworkErrorHandler.NetworkError.NoInternet -> false // Don't retry if no internet
                    is NetworkErrorHandler.NetworkError.ClientError -> false // Don't retry client errors
                    is NetworkErrorHandler.NetworkError.RateLimited -> false // Handle rate limits separately
                    else -> true
                }
            }
        )
    }

    /**
     * Retry config for rate-limited scenarios
     */
    fun createRateLimitRetryConfig(): RetryConfig {
        return RetryConfig(
            maxAttempts = 2,
            initialDelayMs = 60000L, // 1 minute
            backoffMultiplier = 1.5,
            maxDelayMs = 300000L, // 5 minutes
            retryOnCondition = { throwable ->
                val error = errorHandler.analyzeError(throwable)
                error is NetworkErrorHandler.NetworkError.RateLimited
            }
        )
    }

    /**
     * Quick retry config for timeout scenarios
     */
    fun createTimeoutRetryConfig(): RetryConfig {
        return RetryConfig(
            maxAttempts = 5,
            initialDelayMs = 1000L,
            backoffMultiplier = 1.5,
            maxDelayMs = 10000L,
            retryOnCondition = { throwable ->
                val error = errorHandler.analyzeError(throwable)
                error is NetworkErrorHandler.NetworkError.Timeout
            }
        )
    }

    /**
     * Calculate delay with exponential backoff and jitter
     */
    private fun calculateDelay(attemptNumber: Int, config: RetryConfig): Long {
        val exponentialDelay = (config.initialDelayMs *
            config.backoffMultiplier.pow(attemptNumber.toDouble())).toLong()

        // Add jitter to prevent thundering herd
        val jitter = (exponentialDelay * 0.1 * Random.nextDouble()).toLong()

        return minOf(exponentialDelay + jitter, config.maxDelayMs)
    }

    /**
     * Advanced retry with circuit breaker pattern
     */
    class CircuitBreaker(
        private val failureThreshold: Int = 5,
        private val recoveryTimeMs: Long = 60000L // 1 minute
    ) {
        private var failureCount = 0
        private var lastFailureTime = 0L
        private var state = State.CLOSED

        enum class State { CLOSED, OPEN, HALF_OPEN }

        fun canExecute(): Boolean {
            return when (state) {
                State.CLOSED -> true
                State.OPEN -> {
                    if (System.currentTimeMillis() - lastFailureTime > recoveryTimeMs) {
                        state = State.HALF_OPEN
                        true
                    } else {
                        false
                    }
                }
                State.HALF_OPEN -> true
            }
        }

        fun onSuccess() {
            failureCount = 0
            state = State.CLOSED
            Timber.tag(TAG).d("Circuit breaker reset - service recovered")
        }

        fun onFailure() {
            failureCount++
            lastFailureTime = System.currentTimeMillis()

            if (failureCount >= failureThreshold) {
                state = State.OPEN
                Timber.tag(TAG).w("Circuit breaker opened - service temporarily disabled")
            }
        }

        fun isOpen() = state == State.OPEN
    }

    /**
     * Execute with circuit breaker protection
     */
    suspend fun <T> executeWithCircuitBreaker(
        circuitBreaker: CircuitBreaker,
        config: RetryConfig = RetryConfig(),
        operation: suspend () -> T
    ): Result<T> {
        if (!circuitBreaker.canExecute()) {
            return Result.failure(Exception("Circuit breaker is open - service temporarily unavailable"))
        }

        val result = executeWithRetry(config, operation)

        if (result.isSuccess) {
            circuitBreaker.onSuccess()
        } else {
            circuitBreaker.onFailure()
        }

        return result
    }
}