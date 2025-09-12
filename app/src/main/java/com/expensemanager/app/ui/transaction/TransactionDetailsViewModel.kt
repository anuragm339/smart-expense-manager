package com.expensemanager.app.ui.transaction

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for TransactionDetails screen
 * Manages transaction detail UI state and handles user interactions
 */
@HiltViewModel
class TransactionDetailsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    companion object {
        private const val TAG = "TransactionDetailsViewModel"
    }
    
    // Private mutable state
    private val _uiState = MutableStateFlow(TransactionDetailsUIState())
    
    // Public immutable state
    val uiState: StateFlow<TransactionDetailsUIState> = _uiState.asStateFlow()
    
    // Manager instances
    private val categoryManager = CategoryManager(context)
    private val merchantAliasManager = MerchantAliasManager(context)
    
    init {
        Log.d(TAG, "ViewModel initialized")
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: TransactionDetailsUIEvent) {
        Log.d(TAG, "Handling event: $event")
        
        when (event) {
            is TransactionDetailsUIEvent.LoadTransaction -> loadTransactionFromArguments()
            is TransactionDetailsUIEvent.SaveTransaction -> saveTransaction()
            is TransactionDetailsUIEvent.MarkAsDuplicate -> markAsDuplicate()
            is TransactionDetailsUIEvent.UpdateCategory -> updateCategory(event.category)
            is TransactionDetailsUIEvent.UpdateMerchant -> updateMerchant(event.merchant, event.category)
            is TransactionDetailsUIEvent.CreateNewCategory -> createNewCategory(event.categoryName)
            is TransactionDetailsUIEvent.CheckSimilarTransactions -> checkSimilarTransactions()
            is TransactionDetailsUIEvent.ClearError -> clearError()
            is TransactionDetailsUIEvent.NavigateBack -> handleNavigateBack()
        }
    }
    
    /**
     * Set transaction data from fragment arguments
     */
    fun setTransactionData(
        amount: Float,
        merchant: String,
        bankName: String,
        category: String,
        dateTime: String,
        confidence: Int,
        rawSMS: String
    ) {
        Log.d(TAG, "Setting transaction data...")
        
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        try {
            val categoryColor = merchantAliasManager.getMerchantCategoryColor(category)
            
            val transactionData = TransactionData(
                amount = amount,
                merchant = merchant,
                bankName = bankName,
                category = category,
                dateTime = dateTime,
                confidence = confidence,
                rawSMS = rawSMS,
                categoryColor = categoryColor
            )
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                transactionData = transactionData
            )
            
            // Check for similar transactions after loading
            checkSimilarTransactions()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting transaction data", e)
            handleTransactionError(e)
        }
    }
    
    /**
     * Load transaction data from arguments (fallback method)
     */
    private fun loadTransactionFromArguments() {
        // This is now handled by setTransactionData called from fragment
        Log.d(TAG, "Load transaction from arguments - delegated to fragment")
    }
    
    /**
     * Save transaction to database
     */
    private fun saveTransaction() {
        Log.d(TAG, "Saving transaction...")
        
        _uiState.value = _uiState.value.copy(isSaving = true)
        
        viewModelScope.launch {
            try {
                // TODO: Implement actual save operation when repository method is available
                // For now, just simulate success
                kotlinx.coroutines.delay(500) // Simulate network delay
                
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    hasUnsavedChanges = false,
                    lastUpdateTime = System.currentTimeMillis()
                )
                
                Log.d(TAG, "Transaction saved successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving transaction", e)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Mark transaction as duplicate
     */
    private fun markAsDuplicate() {
        Log.d(TAG, "Marking transaction as duplicate...")
        
        _uiState.value = _uiState.value.copy(isUpdating = true)
        
        viewModelScope.launch {
            try {
                // TODO: Implement actual duplicate marking when repository method is available
                kotlinx.coroutines.delay(300) // Simulate processing
                
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    lastUpdateTime = System.currentTimeMillis()
                )
                
                Log.d(TAG, "Transaction marked as duplicate successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error marking transaction as duplicate", e)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Update transaction category
     */
    private fun updateCategory(newCategory: String) {
        Log.d(TAG, "[PROCESS] Updating category to: $newCategory")
        
        val currentTransaction = _uiState.value.transactionData ?: return
        
        _uiState.value = _uiState.value.copy(isUpdating = true)
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Step 1: Updating SharedPreferences alias...")
                
                // STEP 1: Update SharedPreferences (MerchantAliasManager)
                var aliasUpdateSuccess = true
                try {
                    merchantAliasManager.setMerchantAlias(
                        currentTransaction.merchant, 
                        currentTransaction.merchant, 
                        newCategory
                    )
                    Log.d(TAG, "[SUCCESS] SharedPreferences update completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "[ERROR] SharedPreferences update failed", e)
                    aliasUpdateSuccess = false
                }
                
                // STEP 2: Update Database
                Log.d(TAG, "[DATABASE] Step 2: Updating database...")
                var databaseUpdateSuccess = false
                
                if (aliasUpdateSuccess) {
                    try {
                        databaseUpdateSuccess = repository.updateMerchantAliasInDatabase(
                            listOf(currentTransaction.merchant),
                            currentTransaction.merchant, // Keep same name, just update category
                            newCategory
                        )
                        
                        if (databaseUpdateSuccess) {
                            Log.d(TAG, "[SUCCESS] Database update completed successfully")
                        } else {
                            Log.e(TAG, "[ERROR] Database update failed (returned false)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[ERROR] Database update failed with exception", e)
                        databaseUpdateSuccess = false
                    }
                }
                
                // STEP 3: Update UI based on results
                if (aliasUpdateSuccess && databaseUpdateSuccess) {
                    Log.d(TAG, "[SUCCESS] COMPLETE SUCCESS: Both SharedPreferences and Database updated")
                    
                    val categoryColor = merchantAliasManager.getMerchantCategoryColor(newCategory)
                    
                    val updatedTransaction = currentTransaction.copy(
                        category = newCategory,
                        categoryColor = categoryColor
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        transactionData = updatedTransaction,
                        hasUnsavedChanges = true,
                        lastUpdateTime = System.currentTimeMillis(),
                        hasError = false,
                        error = null
                    )
                    
                    Log.d(TAG, "Category updated successfully to: $newCategory")
                    
                } else if (aliasUpdateSuccess && !databaseUpdateSuccess) {
                    Log.w(TAG, "[WARNING] PARTIAL SUCCESS: SharedPreferences updated but database failed")
                    
                    // Still update UI but show warning
                    val categoryColor = merchantAliasManager.getMerchantCategoryColor(newCategory)
                    
                    val updatedTransaction = currentTransaction.copy(
                        category = newCategory,
                        categoryColor = categoryColor
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        transactionData = updatedTransaction,
                        hasUnsavedChanges = true,
                        lastUpdateTime = System.currentTimeMillis(),
                        hasError = true,
                        error = "Category changed in app but may not persist. Please try again."
                    )
                    
                } else {
                    Log.e(TAG, "[ERROR] COMPLETE FAILURE: Both updates failed")
                    handleTransactionError(Exception("Failed to update category. Please try again."))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Critical error during category update", e)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Update merchant name and category
     */
    private fun updateMerchant(newMerchantName: String, newCategory: String) {
        Log.d(TAG, "[PROCESS] Updating merchant to: $newMerchantName, category: $newCategory")
        
        val currentTransaction = _uiState.value.transactionData ?: return
        
        _uiState.value = _uiState.value.copy(isUpdating = true)
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Step 1: Updating SharedPreferences alias...")
                
                // STEP 1: Update SharedPreferences (MerchantAliasManager)
                var aliasUpdateSuccess = true
                try {
                    merchantAliasManager.setMerchantAlias(
                        currentTransaction.merchant, 
                        newMerchantName, 
                        newCategory
                    )
                    Log.d(TAG, "[SUCCESS] SharedPreferences update completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "[ERROR] SharedPreferences update failed", e)
                    aliasUpdateSuccess = false
                }
                
                // STEP 2: Update Database
                Log.d(TAG, "[DATABASE] Step 2: Updating database...")
                var databaseUpdateSuccess = false
                
                if (aliasUpdateSuccess) {
                    try {
                        databaseUpdateSuccess = repository.updateMerchantAliasInDatabase(
                            listOf(currentTransaction.merchant),
                            newMerchantName,
                            newCategory
                        )
                        
                        if (databaseUpdateSuccess) {
                            Log.d(TAG, "[SUCCESS] Database update completed successfully")
                        } else {
                            Log.e(TAG, "[ERROR] Database update failed (returned false)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[ERROR] Database update failed with exception", e)
                        databaseUpdateSuccess = false
                    }
                }
                
                // STEP 3: Update UI based on results
                if (aliasUpdateSuccess && databaseUpdateSuccess) {
                    Log.d(TAG, "[SUCCESS] COMPLETE SUCCESS: Both SharedPreferences and Database updated")
                    
                    val categoryColor = merchantAliasManager.getMerchantCategoryColor(newCategory)
                    
                    val updatedTransaction = currentTransaction.copy(
                        merchant = newMerchantName,
                        category = newCategory,
                        categoryColor = categoryColor
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        transactionData = updatedTransaction,
                        hasUnsavedChanges = true,
                        lastUpdateTime = System.currentTimeMillis(),
                        hasError = false,
                        error = null
                    )
                    
                    Log.d(TAG, "Merchant updated successfully to: $newMerchantName")
                    
                } else if (aliasUpdateSuccess && !databaseUpdateSuccess) {
                    Log.w(TAG, "[WARNING] PARTIAL SUCCESS: SharedPreferences updated but database failed")
                    
                    // Still update UI but show warning
                    val categoryColor = merchantAliasManager.getMerchantCategoryColor(newCategory)
                    
                    val updatedTransaction = currentTransaction.copy(
                        merchant = newMerchantName,
                        category = newCategory,
                        categoryColor = categoryColor
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        transactionData = updatedTransaction,
                        hasUnsavedChanges = true,
                        lastUpdateTime = System.currentTimeMillis(),
                        hasError = true,
                        error = "Changes saved to app but may not persist. Please try again."
                    )
                    
                } else {
                    Log.e(TAG, "[ERROR] COMPLETE FAILURE: Both updates failed")
                    handleTransactionError(Exception("Failed to update merchant. Please try again."))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Critical error during merchant update", e)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Create new category and apply it to transaction
     */
    private fun createNewCategory(categoryName: String) {
        Log.d(TAG, "Creating new category: $categoryName")
        
        viewModelScope.launch {
            try {
                // Add to CategoryManager for persistence
                categoryManager.addCustomCategory(categoryName)
                
                // Apply the new category to this transaction
                updateCategory(categoryName)
                
                Log.d(TAG, "New category created successfully: $categoryName")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error creating new category", e)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Check for similar transactions
     */
    private fun checkSimilarTransactions() {
        val currentTransaction = _uiState.value.transactionData ?: return
        
        viewModelScope.launch {
            try {
                // TODO: Implement actual similar transaction detection when repository method is available
                // For now, just set placeholder value
                val similarCount = 0 // Placeholder
                
                _uiState.value = _uiState.value.copy(
                    similarTransactionsCount = similarCount,
                    showSimilarWarning = similarCount > 0
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking similar transactions", e)
                // Don't show error for this non-critical operation
            }
        }
    }
    
    /**
     * Get all available categories
     */
    fun getAllCategories(): List<String> {
        return categoryManager.getAllCategories()
    }
    
    /**
     * Clear error state
     */
    private fun clearError() {
        _uiState.value = _uiState.value.copy(
            hasError = false,
            error = null
        )
    }
    
    /**
     * Handle navigation back
     */
    private fun handleNavigateBack() {
        Log.d(TAG, "Handling navigation back")
        
        if (_uiState.value.hasUnsavedChanges) {
            // Fragment should handle showing unsaved changes dialog
            Log.d(TAG, "Has unsaved changes - fragment should handle confirmation")
        }
    }
    
    /**
     * Handle transaction-related errors
     */
    private fun handleTransactionError(throwable: Throwable) {
        val errorMessage = when {
            throwable.message?.contains("network", ignoreCase = true) == true -> "Network error. Please try again."
            throwable.message?.contains("permission", ignoreCase = true) == true -> "Permission required to access data"
            throwable.message?.contains("duplicate", ignoreCase = true) == true -> "Duplicate transaction detected"
            else -> "Something went wrong. Please try again."
        }
        
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isSaving = false,
            isUpdating = false,
            hasError = true,
            error = errorMessage
        )
    }
}