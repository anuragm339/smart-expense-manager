package com.expensemanager.app.ui.merchant

import android.content.Context
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.data.entities.TransactionEntity
import org.json.JSONObject
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

    // Events
    private val _events = MutableStateFlow<MerchantTransactionsEvent?>(null)
    val events: StateFlow<MerchantTransactionsEvent?> = _events.asStateFlow()

    fun loadMerchantTransactions(merchantName: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                Timber.tag(TAG).d("Loading transactions for merchant: $merchantName")
                
                // Get all transactions for this merchant
                val transactions = repository.searchTransactions(merchantName.lowercase(), 1000)
                    .filter { it.normalizedMerchant.contains(merchantName.lowercase()) || 
                             it.rawMerchant.contains(merchantName, ignoreCase = true) }
                
                Timber.tag(TAG).d("Found ${transactions.size} transactions for $merchantName")
                
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
                Timber.tag(TAG).e(e, "Error loading merchant transactions")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading transactions: ${e.message}"
                )
            }
        }
    }

    fun loadInclusionState(merchantName: String) {
        val prefs = context.getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
        val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
        
        // Normalize merchant name for consistent lookup
        val normalizedMerchantName = merchantName.trim()
        
        Timber.tag(TAG).d("[DEBUG] Loading inclusion state for original: '$merchantName'")
        Timber.tag(TAG).d("[DEBUG] Loading inclusion state for normalized: '$normalizedMerchantName'")
        Timber.tag(TAG).d("[DEBUG] Inclusion states JSON: $inclusionStatesJson")
        
        var isIncluded = true // Default to included
        
        if (inclusionStatesJson != null) {
            try {
                val inclusionStates = JSONObject(inclusionStatesJson)
                
                // Debug: Log all keys in the inclusion states
                val keys = mutableListOf<String>()
                inclusionStates.keys().forEach { key -> keys.add(key) }
                Timber.tag(TAG).d("[DEBUG] All keys in preferences: $keys")
                
                // Try normalized merchant name first, then original name for backward compatibility
                when {
                    inclusionStates.has(normalizedMerchantName) -> {
                        isIncluded = inclusionStates.getBoolean(normalizedMerchantName)
                        Timber.tag(TAG).d("[DEBUG] Found normalized '$normalizedMerchantName' in preferences: $isIncluded")
                    }
                    inclusionStates.has(merchantName) -> {
                        isIncluded = inclusionStates.getBoolean(merchantName)
                        Timber.tag(TAG).d("[DEBUG] Found original '$merchantName' in preferences: $isIncluded (legacy)")
                        
                        // Migrate to normalized key
                        inclusionStates.put(normalizedMerchantName, isIncluded)
                        inclusionStates.remove(merchantName)
                        prefs.edit()
                            .putString("group_inclusion_states", inclusionStates.toString())
                            .apply()
                        Timber.tag(TAG).d("[MIGRATION] Migrated '$merchantName' -> '$normalizedMerchantName'")
                    }
                    else -> {
                        Timber.tag(TAG).d("[DEBUG] Neither normalized nor original merchant name found in preferences, defaulting to included")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w("Error loading inclusion state", e)
            }
        } else {
            Timber.tag(TAG).d("[DEBUG] No inclusion states JSON found, defaulting to included")
        }
        
        _uiState.value = _uiState.value.copy(isIncludedInExpense = isIncluded)
    }

    fun updateInclusionState(merchantName: String, isIncluded: Boolean) {
        viewModelScope.launch {
            try {
                // Validate merchant name
                if (merchantName.isBlank()) {
                    Timber.tag(TAG).w("Cannot update inclusion state for blank merchant name")
                    _events.value = MerchantTransactionsEvent.ShowError("Invalid merchant name")
                    return@launch
                }
                
                // Normalize merchant name for consistent storage (trim whitespace and handle special chars)
                val normalizedMerchantName = merchantName.trim()
                
                Timber.tag(TAG).d("[DEBUG] Updating inclusion state for normalized merchant: '$normalizedMerchantName'")
                Timber.tag(TAG).d("[DEBUG] Original merchant name length: ${merchantName.length}")
                Timber.tag(TAG).d("[DEBUG] Normalized merchant name length: ${normalizedMerchantName.length}")
                
                // Save to SharedPreferences
                val prefs = context.getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
                val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
                
                val inclusionStates = if (inclusionStatesJson != null) {
                    JSONObject(inclusionStatesJson)
                } else {
                    JSONObject()
                }
                
                // Use normalized merchant name for consistent key storage
                inclusionStates.put(normalizedMerchantName, isIncluded)
                
                prefs.edit()
                    .putString("group_inclusion_states", inclusionStates.toString())
                    .apply()
                
                Timber.tag(TAG).d("[DEBUG] Updated inclusion state for '$normalizedMerchantName': $isIncluded")
                Timber.tag(TAG).d("[DEBUG] Full inclusion states JSON: ${inclusionStates.toString()}")
                
                // Debug: Log all keys in the inclusion states
                val keys = mutableListOf<String>()
                inclusionStates.keys().forEach { key -> keys.add(key) }
                Timber.tag(TAG).d("[DEBUG] All merchant keys in preferences: $keys")
                
                // FIXED: Send broadcast to notify Dashboard and other screens about inclusion state change
                val totalIncluded = inclusionStates.keys().asSequence().count { key ->
                    inclusionStates.getBoolean(key)
                }
                val intent = android.content.Intent("com.expensemanager.INCLUSION_STATE_CHANGED").apply {
                    putExtra("included_count", totalIncluded)
                    putExtra("total_amount", 0.0) // Could calculate actual amount if needed
                    putExtra("merchant_name", normalizedMerchantName)
                    putExtra("is_included", isIncluded)
                }
                context.sendBroadcast(intent)
                Timber.tag(TAG).d("Broadcast sent for inclusion state change: $normalizedMerchantName = $isIncluded")
                
                _uiState.value = _uiState.value.copy(isIncludedInExpense = isIncluded)
                
                // Create user-friendly display name (truncate if too long but keep original name for UX)
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
                Timber.tag(TAG).e(e, "Error updating inclusion state")
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