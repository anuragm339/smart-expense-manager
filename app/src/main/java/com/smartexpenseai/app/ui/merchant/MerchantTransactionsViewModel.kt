package com.smartexpenseai.app.ui.merchant

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.utils.logging.StructuredLogger
import javax.inject.Inject

@HiltViewModel
class MerchantTransactionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MerchantTransactionsVM"
    }

    // UI State
    private val _uiState = MutableStateFlow(MerchantTransactionsUiState())
    val uiState: StateFlow<MerchantTransactionsUiState> = _uiState.asStateFlow()

    private val logger = StructuredLogger("MerchantTransactionsViewModel", "MerchantTransactionsViewModel")
    // Events
    private val _events = MutableStateFlow<MerchantTransactionsEvent?>(null)
    val events: StateFlow<MerchantTransactionsEvent?> = _events.asStateFlow()

    /** Start of the current month (00:00) to now - matches the other transaction screens. */
    private fun currentMonthRange(): Pair<java.util.Date, java.util.Date> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return Pair(calendar.time, java.util.Date())
    }

    fun loadMerchantTransactions(merchantName: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                logger.debug("loadMerchantTransactions","Loading transactions for merchant: $merchantName")

                // Restrict to the current month so the total/count/list match the
                // Dashboard and Category screens this is opened from (which are month-scoped).
                val (startDate, endDate) = currentMonthRange()
                val target = merchantName.trim()

                val transactions = repository.searchTransactions(target.lowercase(), 1000)
                    .filter { txn ->
                        // Exact merchant match so "AMAZON" does not also pull in "AMAZON PAY"
                        (txn.rawMerchant.equals(target, ignoreCase = true) ||
                            txn.normalizedMerchant.equals(target, ignoreCase = true)) &&
                            // Current month only
                            txn.transactionDate >= startDate && txn.transactionDate <= endDate
                    }

                logger.debug("loadMerchantTransactions","Found ${transactions.size} transactions for $merchantName")
                
                // Calculate totals
                val totalAmount = transactions.sumOf { it.amount }
                val totalCount = transactions.size
                val sortedTransactions = transactions.sortedByDescending { it.transactionDate }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    transactions = sortedTransactions,
                    totalAmount = totalAmount,
                    totalCount = totalCount,
                    merchantName = merchantName
                )
                
            } catch (e: Exception) {
                logger.error("loadMerchantTransactions","Error loading merchant transactions",e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading transactions: ${e.message}"
                )
            }
        }
    }

    fun loadInclusionState(merchantName: String) {
        viewModelScope.launch {
            try {
                logger.debug("loadInclusionState","Loading inclusion state from DB for: '$merchantName'")

                // Get merchant from database
                val merchant = repository.getMerchantWithCategory(merchantName)

                // Default to included (not excluded)
                val isIncluded = if (merchant != null) {
                    !merchant.is_excluded_from_expense_tracking
                } else {
                    true // If merchant doesn't exist in DB, default to included
                }

                logger.debug("loadInclusionState","Merchant '$merchantName' inclusion state from DB: $isIncluded")

                _uiState.value = _uiState.value.copy(isIncludedInExpense = isIncluded)
            } catch (e: Exception) {
                logger.error("loadInclusionState","Error loading inclusion state from DB", e)
                // Default to included on error
                _uiState.value = _uiState.value.copy(isIncludedInExpense = true)
            }
        }
    }

    fun updateInclusionState(merchantName: String, isIncluded: Boolean) {
        viewModelScope.launch {
            try {
                // Validate merchant name
                if (merchantName.isBlank()) {
                    logger.debug("updateInclusionState","Cannot update inclusion state for blank merchant name")
                    _events.value = MerchantTransactionsEvent.ShowError("Invalid merchant name")
                    return@launch
                }

                logger.debug("updateInclusionState","Updating inclusion state in DB for: '$merchantName' to $isIncluded")

                // Update merchant exclusion in database
                // isIncluded = true means NOT excluded, so we need to invert the boolean
                val isExcluded = !isIncluded
                repository.updateMerchantExclusion(merchantName, isExcluded)

                logger.debug("updateInclusionState","Updated merchant exclusion in DB: '$merchantName' isExcluded=$isExcluded")

                // Send broadcast to notify Dashboard and other screens about inclusion state change
                val intent = android.content.Intent("com.expensemanager.INCLUSION_STATE_CHANGED").apply {
                    putExtra("merchant_name", merchantName)
                    putExtra("is_included", isIncluded)
                }
                context.sendBroadcast(intent)
                logger.debug("updateInclusionState","Broadcast sent for inclusion state change: $merchantName = $isIncluded")

                _uiState.value = _uiState.value.copy(isIncludedInExpense = isIncluded)

                // Create user-friendly display name
                val displayName = if (merchantName.length > 50) {
                    "${merchantName.take(47)}..."
                } else {
                    merchantName
                }

                _events.value = MerchantTransactionsEvent.ShowMessage(
                    if (isIncluded) "✓ $displayName is now included in expense calculations"
                    else "ℹ️ $displayName is now excluded from expense calculations"
                )

            } catch (e: Exception) {
                logger.error("updateInclusionState","Error updating inclusion state in DB",e)
                _events.value = MerchantTransactionsEvent.ShowError("Error updating inclusion state")
            }
        }
    }

    fun clearEvent() {
        _events.value = null
    }
}

data class MerchantTransactionsUiState(
    val isLoading: Boolean = false,
    val transactions: List<TransactionEntity> = emptyList(),
    val totalAmount: Double = 0.0,
    val totalCount: Int = 0,
    val merchantName: String = "",
    val isIncludedInExpense: Boolean = true,
    val error: String? = null
)

sealed class MerchantTransactionsEvent {
    data class ShowMessage(val message: String) : MerchantTransactionsEvent()
    data class ShowError(val error: String) : MerchantTransactionsEvent()
}