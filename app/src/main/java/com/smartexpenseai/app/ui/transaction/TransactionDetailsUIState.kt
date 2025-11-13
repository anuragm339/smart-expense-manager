package com.smartexpenseai.app.ui.transaction

/**
 * UI State for TransactionDetails screen
 */
data class TransactionDetailsUIState(
    // Loading states
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUpdating: Boolean = false,
    
    // Transaction data
    val transactionData: TransactionData? = null,
    val similarTransactionsCount: Int = 0,
    val updatedTransactionsCount: Int = 0,

    // State flags
    val hasError: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val showSimilarWarning: Boolean = false,
    val lastUpdateTime: Long = 0L
) {
    /**
     * Computed properties for UI state management
     */
    val shouldShowContent: Boolean
        get() = !isLoading && !hasError && transactionData != null
    
    val shouldShowError: Boolean
        get() = hasError && error != null
    
    val isAnyLoading: Boolean
        get() = isLoading || isSaving || isUpdating
    
    val confidenceColor: String
        get() = when {
            (transactionData?.confidence ?: 0) >= 85 -> "success"
            (transactionData?.confidence ?: 0) >= 65 -> "warning"
            else -> "error"
        }
    
    val confidenceLabel: String
        get() = when {
            (transactionData?.confidence ?: 0) >= 85 -> "High Confidence"
            (transactionData?.confidence ?: 0) >= 65 -> "Medium Confidence"
            else -> "Low Confidence"
        }
    
    val formattedAmount: String
        get() = "₹${String.format("%.0f", transactionData?.amount ?: 0.0f)}"
}

/**
 * Transaction data holder
 */
data class TransactionData(
    val amount: Float,
    val merchant: String,
    val bankName: String,
    val category: String,
    val dateTime: String,
    val confidence: Int,
    val rawSMS: String,
    val categoryColor: String = "#9e9e9e"
)

/**
 * UI Events for TransactionDetails interactions
 */
sealed class TransactionDetailsUIEvent {
    object LoadTransaction : TransactionDetailsUIEvent()
    object SaveTransaction : TransactionDetailsUIEvent()
    object MarkAsDuplicate : TransactionDetailsUIEvent()
    object CheckSimilarTransactions : TransactionDetailsUIEvent()
    object ClearError : TransactionDetailsUIEvent()
    object ClearSuccess : TransactionDetailsUIEvent()
    object NavigateBack : TransactionDetailsUIEvent()
    
    data class UpdateCategory(val category: String) : TransactionDetailsUIEvent()
    data class UpdateMerchant(val merchant: String, val category: String) : TransactionDetailsUIEvent()
    data class CreateNewCategory(val categoryName: String) : TransactionDetailsUIEvent()
}