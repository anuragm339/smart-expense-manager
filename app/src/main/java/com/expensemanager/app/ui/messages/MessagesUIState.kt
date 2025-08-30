package com.expensemanager.app.ui.messages

/**
 * UI State for Messages screen
 */
data class MessagesUIState(
    // Loading states
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSyncingSMS: Boolean = false,
    
    // Data
    val allMessages: List<MessageItem> = emptyList(),
    val filteredMessages: List<MessageItem> = emptyList(),
    val groupedMessages: List<MerchantGroup> = emptyList(),
    
    // Search and filters
    val searchQuery: String = "",
    val currentSortOption: SortOption = SortOption("Date (Newest First)", "date", false),
    val currentFilterOptions: FilterOptions = FilterOptions(),
    
    // State flags
    val isEmpty: Boolean = false,
    val hasError: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val syncMessage: String? = null,
    val testResults: String? = null,
    val lastRefreshTime: Long = 0L
) {
    /**
     * Computed properties for UI state management
     */
    val shouldShowContent: Boolean
        get() = !isLoading && !hasError && groupedMessages.isNotEmpty()
    
    val shouldShowEmptyState: Boolean
        get() = !isLoading && !hasError && isEmpty && allMessages.isEmpty()
    
    val shouldShowError: Boolean
        get() = !isLoading && hasError && error != null
    
    val isAnyLoading: Boolean
        get() = isLoading || isRefreshing || isSyncingSMS
    
    val totalMessagesCount: Int
        get() = filteredMessages.size
    
    val autoCategorizedCount: Int
        get() = filteredMessages.count { it.category != "Other" && it.confidence >= 80 }
    
    val hasActiveFilters: Boolean
        get() = searchQuery.isNotEmpty() || 
                currentFilterOptions.minAmount != null ||
                currentFilterOptions.maxAmount != null ||
                currentFilterOptions.selectedBanks.isNotEmpty() ||
                currentFilterOptions.minConfidence > 0 ||
                currentFilterOptions.dateFrom != null ||
                currentFilterOptions.dateTo != null
    
    val activeFilterCount: Int
        get() = listOfNotNull(
            if (currentFilterOptions.minAmount != null || currentFilterOptions.maxAmount != null) "Amount" else null,
            if (currentFilterOptions.selectedBanks.isNotEmpty()) "Banks" else null,
            if (currentFilterOptions.minConfidence > 0) "Confidence" else null,
            if (currentFilterOptions.dateFrom != null || currentFilterOptions.dateTo != null) "Date" else null
        ).size
    
    val availableBanks: List<String>
        get() = allMessages.map { it.bankName }.distinct().sorted()
}

/**
 * Individual message/transaction item
 */
data class MessageItem(
    val amount: Double,
    val merchant: String,
    val bankName: String,
    val category: String,
    val categoryColor: String,
    val confidence: Int,
    val dateTime: String,
    val rawSMS: String
)

/**
 * Grouped transactions by merchant
 */
data class MerchantGroup(
    val merchantName: String,
    val transactions: List<MessageItem>,
    val totalAmount: Double,
    val categoryColor: String,
    val category: String,
    var isExpanded: Boolean = false,
    var isIncludedInCalculations: Boolean = true,
    val latestTransactionDate: Long = 0L,
    val primaryBankName: String = "",
    val averageConfidence: Double = 0.0
) {
    val transactionCount: Int get() = transactions.size
    
    val formattedTotalAmount: String 
        get() = "₹${String.format("%.0f", totalAmount)}"
    
    val confidenceText: String
        get() = "${averageConfidence.toInt()}%"
    
    val hasMultipleTransactions: Boolean
        get() = transactions.size > 1
}

/**
 * Sorting options for messages
 */
data class SortOption(
    val name: String,
    val field: String,
    val ascending: Boolean
) {
    companion object {
        val DEFAULT_OPTIONS = listOf(
            SortOption("Date (Newest First)", "date", false),
            SortOption("Date (Oldest First)", "date", true),
            SortOption("Amount (Highest First)", "amount", false),
            SortOption("Amount (Lowest First)", "amount", true),
            SortOption("Merchant Name (A-Z)", "merchant", true),
            SortOption("Merchant Name (Z-A)", "merchant", false),
            SortOption("Bank Name (A-Z)", "bank", true),
            SortOption("Bank Name (Z-A)", "bank", false),
            SortOption("Confidence (Highest First)", "confidence", false),
            SortOption("Confidence (Lowest First)", "confidence", true)
        )
    }
    
    val displayText: String
        get() = name.split(" ")[0] // Show "Date", "Amount", etc.
}

/**
 * Filter options for messages
 */
data class FilterOptions(
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val selectedBanks: Set<String> = emptySet(),
    val minConfidence: Int = 0,
    val dateFrom: String? = null,
    val dateTo: String? = null
) {
    val isEmpty: Boolean
        get() = minAmount == null && 
                maxAmount == null && 
                selectedBanks.isEmpty() && 
                minConfidence == 0 && 
                dateFrom == null && 
                dateTo == null
}

/**
 * UI Events for Messages screen interactions
 */
sealed class MessagesUIEvent {
    object LoadMessages : MessagesUIEvent()
    object RefreshMessages : MessagesUIEvent()
    object ResyncSMS : MessagesUIEvent()
    object TestSMSScanning : MessagesUIEvent()
    object ResetFilters : MessagesUIEvent()
    object ClearError : MessagesUIEvent()
    
    data class Search(val query: String) : MessagesUIEvent()
    data class ApplySort(val sortOption: SortOption) : MessagesUIEvent()
    data class ApplyFilter(val filterOptions: FilterOptions) : MessagesUIEvent()
    data class ToggleGroupInclusion(val merchantName: String, val isIncluded: Boolean) : MessagesUIEvent()
    
    data class UpdateMerchantGroup(
        val merchantName: String,
        val newDisplayName: String,
        val newCategory: String
    ) : MessagesUIEvent()
    
    data class ResetMerchantGroup(val merchantName: String) : MessagesUIEvent()
    
    data class UpdateCategoryForMerchant(
        val merchantName: String,
        val newCategory: String
    ) : MessagesUIEvent()
}