package com.expensemanager.app.services

import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to handle network recovery and automatic retry after connectivity is restored
 */
@Singleton
class NetworkRecoveryService @Inject constructor(
    private val errorHandler: NetworkErrorHandler,
    private val retryMechanism: RetryMechanism
) {

    companion object {
        private const val TAG = "NetworkRecoveryService"
        private const val RECOVERY_DELAY_MS = 5000L // 5 seconds after network recovery
    }

    private val _networkState = MutableStateFlow(false)
    val networkState: StateFlow<Boolean> = _networkState.asStateFlow()

    private val _recoveryEvents = MutableSharedFlow<RecoveryEvent>()
    val recoveryEvents: SharedFlow<RecoveryEvent> = _recoveryEvents.asSharedFlow()

    private var networkMonitoringJob: Job? = null
    private val pendingOperations = mutableListOf<PendingOperation>()

    data class PendingOperation(
        val id: String,
        val operation: suspend () -> Unit,
        val description: String,
        val priority: Priority = Priority.NORMAL
    )

    enum class Priority { LOW, NORMAL, HIGH }

    sealed class RecoveryEvent {
        object NetworkRestored : RecoveryEvent()
        data class OperationRetried(val operationId: String, val success: Boolean) : RecoveryEvent()
        data class OperationFailed(val operationId: String, val error: String) : RecoveryEvent()
    }

    /**
     * Start monitoring network connectivity
     */
    fun startNetworkMonitoring(scope: CoroutineScope) {
        stopNetworkMonitoring()

        networkMonitoringJob = scope.launch {
            errorHandler.networkConnectivityFlow()
                .distinctUntilChanged()
                .collect { isConnected ->
                    handleNetworkStateChange(isConnected, this)
                }
        }

        Timber.tag(TAG).d("Network monitoring started")
    }

    /**
     * Stop network monitoring
     */
    fun stopNetworkMonitoring() {
        networkMonitoringJob?.cancel()
        networkMonitoringJob = null
        Timber.tag(TAG).d("Network monitoring stopped")
    }

    /**
     * Add operation to retry when network is restored
     */
    fun addPendingOperation(
        id: String,
        description: String,
        priority: Priority = Priority.NORMAL,
        operation: suspend () -> Unit
    ) {
        synchronized(pendingOperations) {
            // Remove existing operation with same ID
            pendingOperations.removeAll { it.id == id }

            // Add new operation
            pendingOperations.add(
                PendingOperation(
                    id = id,
                    operation = operation,
                    description = description,
                    priority = priority
                )
            )

            Timber.tag(TAG).d("Added pending operation: $description (ID: $id)")
        }

        // If network is available, execute immediately
        if (_networkState.value) {
            CoroutineScope(Dispatchers.IO).launch {
                executePendingOperations()
            }
        }
    }

    /**
     * Remove pending operation
     */
    fun removePendingOperation(id: String) {
        synchronized(pendingOperations) {
            val removed = pendingOperations.removeAll { it.id == id }
            if (removed) {
                Timber.tag(TAG).d("Removed pending operation: $id")
            }
        }
    }

    /**
     * Get count of pending operations
     */
    fun getPendingOperationsCount(): Int {
        synchronized(pendingOperations) {
            return pendingOperations.size
        }
    }

    /**
     * Clear all pending operations
     */
    fun clearPendingOperations() {
        synchronized(pendingOperations) {
            val count = pendingOperations.size
            pendingOperations.clear()
            Timber.tag(TAG).d("Cleared $count pending operations")
        }
    }

    /**
     * Handle network state changes
     */
    private suspend fun handleNetworkStateChange(isConnected: Boolean, scope: CoroutineScope) {
        val previousState = _networkState.value
        _networkState.value = isConnected

        Timber.tag(TAG).d("Network state changed: $previousState -> $isConnected")

        if (!previousState && isConnected) {
            // Network was restored
            Timber.tag(TAG).i("Network connectivity restored")
            _recoveryEvents.emit(RecoveryEvent.NetworkRestored)

            // Wait a bit for network to stabilize
            delay(RECOVERY_DELAY_MS)

            // Execute pending operations
            executePendingOperations()
        }
    }

    /**
     * Execute all pending operations
     */
    private suspend fun executePendingOperations() {
        val operations = synchronized(pendingOperations) {
            pendingOperations.sortedByDescending { it.priority.ordinal }.toList()
        }

        if (operations.isEmpty()) {
            Timber.tag(TAG).d("No pending operations to execute")
            return
        }

        Timber.tag(TAG).i("Executing ${operations.size} pending operations")

        for (operation in operations) {
            try {
                Timber.tag(TAG).d("Executing operation: ${operation.description}")

                // Execute with retry
                val result = retryMechanism.executeWithRetry(
                    config = retryMechanism.createNetworkAwareRetryConfig()
                ) {
                    operation.operation()
                }

                if (result.isSuccess) {
                    Timber.tag(TAG).i("Operation succeeded: ${operation.description}")
                    _recoveryEvents.emit(RecoveryEvent.OperationRetried(operation.id, true))
                    removePendingOperation(operation.id)
                } else {
                    val error = result.exceptionOrNull()
                    Timber.tag(TAG).e(error, "Operation failed: ${operation.description}")
                    _recoveryEvents.emit(
                        RecoveryEvent.OperationFailed(
                            operation.id,
                            error?.message ?: "Unknown error"
                        )
                    )
                }

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error executing operation: ${operation.description}")
                _recoveryEvents.emit(
                    RecoveryEvent.OperationFailed(operation.id, e.message ?: "Unknown error")
                )
            }

            // Small delay between operations to avoid overwhelming the system
            delay(1000)
        }
    }

    /**
     * Execute operation with automatic retry on network recovery
     */
    suspend fun <T> executeWithNetworkRecovery(
        operationId: String,
        description: String,
        priority: Priority = Priority.NORMAL,
        operation: suspend () -> T
    ): Result<T> {
        return try {
            // Try immediate execution
            val result = operation()
            Result.success(result)

        } catch (e: Exception) {
            val networkError = errorHandler.analyzeError(e)

            if (networkError.recoverable && !errorHandler.isNetworkAvailable()) {
                // Add to pending operations for retry when network is restored
                addPendingOperation(
                    id = operationId,
                    description = description,
                    priority = priority,
                    operation = { operation() }
                )

                Timber.tag(TAG).i("Operation queued for network recovery: $description")
            }

            Result.failure(e)
        }
    }

    /**
     * Create a Flow that emits when specific operations complete
     */
    fun operationCompletionFlow(operationId: String): Flow<Boolean> {
        return recoveryEvents
            .filterIsInstance<RecoveryEvent.OperationRetried>()
            .filter { it.operationId == operationId }
            .map { it.success }
    }

    /**
     * Get recovery statistics
     */
    fun getRecoveryStats(): RecoveryStats {
        synchronized(pendingOperations) {
            return RecoveryStats(
                isNetworkAvailable = _networkState.value,
                pendingOperationsCount = pendingOperations.size,
                highPriorityCount = pendingOperations.count { it.priority == Priority.HIGH },
                normalPriorityCount = pendingOperations.count { it.priority == Priority.NORMAL },
                lowPriorityCount = pendingOperations.count { it.priority == Priority.LOW }
            )
        }
    }

    data class RecoveryStats(
        val isNetworkAvailable: Boolean,
        val pendingOperationsCount: Int,
        val highPriorityCount: Int,
        val normalPriorityCount: Int,
        val lowPriorityCount: Int
    )
}