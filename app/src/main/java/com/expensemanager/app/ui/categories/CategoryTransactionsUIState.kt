package com.expensemanager.app.ui.categories

import com.expensemanager.app.ui.messages.MessageItem

/**
 * UI State for CategoryTransactions screen
 */
data class CategoryTransactionsUIState(
    // Loading states
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = false,
    val isUpdatingCategory: Boolean = false,
    
    // Data
    val categoryName: String = "",
    val categoryColor: String = "#9e9e9e",
    val transactions: List<MessageItem> = emptyList(),
    val allTransactions: List<MessageItem> = emptyList(), // Unfiltered list for local filtering
    val totalAmount: Double = 0.0,
    val transactionCount: Int = 0,
    
    // Filter and sort state
    val currentSortOption: String = "Newest First",
    val currentFilterOption: String = "This Month",
    val availableCategories: List<String> = emptyList(),
    
    // State flags
    val isEmpty: Boolean = false,
    val hasError: Boolean = false,
    val error: String? = null,
    val lastRefreshTime: Long = 0L
) {
    /**
     * Computed properties for UI state management
     */
    val shouldShowContent: Boolean
        get() = !isInitialLoading && !hasError && transactions.isNotEmpty() && !isEmpty
    
    val shouldShowEmptyState: Boolean
        get() = !isInitialLoading && !hasError && (transactions.isEmpty() || isEmpty)
    
    val shouldShowError: Boolean
        get() = !isInitialLoading && hasError && error != null
    
    val isAnyLoading: Boolean
        get() = isInitialLoading || isLoading || isRefreshing || isUpdatingCategory
    
    val formattedTotalAmount: String
        get() = "₹${String.format("%.0f", totalAmount)}"
    
    val formattedSummary: String
        get() = "$transactionCount transactions • $currentFilterOption"
    
    val sortOptions: List<String>
        get() = listOf("Newest First", "Oldest First", "Highest Amount", "Lowest Amount")
    
    val filterOptions: List<String>
        get() = listOf("Today", "Yesterday", "This Week", "This Month", "Last Month", "All Time")
}

/**
 * UI Events for CategoryTransactions interactions
 */
sealed class CategoryTransactionsUIEvent {
    object LoadTransactions : CategoryTransactionsUIEvent()
    object Refresh : CategoryTransactionsUIEvent()
    object ClearError : CategoryTransactionsUIEvent()
    
    data class SetCategoryName(val categoryName: String) : CategoryTransactionsUIEvent()
    data class ChangeSortOption(val sortOption: String) : CategoryTransactionsUIEvent()
    data class ChangeFilterOption(val filterOption: String) : CategoryTransactionsUIEvent()
    data class UpdateTransactionCategory(
        val messageItem: MessageItem, 
        val newCategory: String
    ) : CategoryTransactionsUIEvent()
    data class ShowCategoryEditDialog(val messageItem: MessageItem) : CategoryTransactionsUIEvent()
}