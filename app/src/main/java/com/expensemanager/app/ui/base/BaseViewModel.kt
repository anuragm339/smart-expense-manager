package com.expensemanager.app.ui.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

/**
 * Base ViewModel class that eliminates ~150 lines of duplicated state management logic
 * Provides common patterns for loading states, error handling, and UI state updates
 */
abstract class BaseViewModel<T : BaseUIState>(
    initialState: T,
    private val tag: String = "BaseViewModel"
) : ViewModel() {

    protected val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<T> = _uiState.asStateFlow()

    /**
     * Update UI state with loading state
     */
    @Suppress("UNCHECKED_CAST")
    protected fun setLoading(isLoading: Boolean = true) {
        _uiState.value = _uiState.value.copyWithLoading(isLoading) as T
    }

    /**
     * Update UI state with error
     */
    @Suppress("UNCHECKED_CAST")
    protected fun setError(error: String?, hasError: Boolean = true) {
        Log.e(tag, "ViewModel error: $error")
        _uiState.value = _uiState.value.copyWithError(error, hasError) as T
    }

    /**
     * Clear error state
     */
    @Suppress("UNCHECKED_CAST")
    protected fun clearError() {
        _uiState.value = _uiState.value.copyWithError(null, false) as T
    }

    /**
     * Update UI state with success (clears loading and error)
     */
    @Suppress("UNCHECKED_CAST")
    protected fun setSuccess() {
        _uiState.value = _uiState.value.copyWithSuccess() as T
    }

    /**
     * Execute operation with common error handling pattern
     * Eliminates duplicated try-catch blocks across ViewModels
     */
    protected suspend fun <R> executeWithErrorHandling(
        operation: suspend () -> R,
        onSuccess: (R) -> Unit = {},
        onError: (Exception) -> Unit = { setError(it.message) },
        showLoading: Boolean = true
    ) {
        try {
            if (showLoading) setLoading(true)
            clearError()
            
            val result = operation()
            onSuccess(result)
            
            if (showLoading) setLoading(false)
            
        } catch (e: Exception) {
            Log.e(tag, "Operation failed", e)
            if (showLoading) setLoading(false)
            onError(e)
        }
    }

    /**
     * Execute operation that returns Result<T> with common handling
     */
    protected suspend fun <R> executeWithResult(
        operation: suspend () -> Result<R>,
        onSuccess: (R) -> Unit,
        onError: (String) -> Unit = { setError(it) },
        showLoading: Boolean = true
    ) {
        executeWithErrorHandling(
            operation = {
                val result = operation()
                if (result.isSuccess) {
                    result.getOrThrow()
                } else {
                    throw Exception(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            },
            onSuccess = onSuccess,
            onError = { onError(it.message ?: "Unknown error") },
            showLoading = showLoading
        )
    }
}

/**
 * Base interface for all UI states
 * Ensures consistent state structure across all ViewModels
 */
interface BaseUIState {
    val isLoading: Boolean
    val hasError: Boolean
    val error: String?

    fun copyWithLoading(isLoading: Boolean): BaseUIState
    fun copyWithError(error: String?, hasError: Boolean): BaseUIState
    fun copyWithSuccess(): BaseUIState = copyWithLoading(false).copyWithError(null, false)
}

/**
 * Common loading states enum to eliminate string duplication
 */
enum class LoadingState {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR
}

/**
 * Helper extension functions for common UI state operations
 */
fun <T : BaseUIState> T.withLoading(isLoading: Boolean = true): T {
    @Suppress("UNCHECKED_CAST")
    return this.copyWithLoading(isLoading) as T
}

fun <T : BaseUIState> T.withError(error: String?, hasError: Boolean = true): T {
    @Suppress("UNCHECKED_CAST")
    return this.copyWithError(error, hasError) as T
}

fun <T : BaseUIState> T.withSuccess(): T {
    @Suppress("UNCHECKED_CAST")
    return this.copyWithSuccess() as T
}