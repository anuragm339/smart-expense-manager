package com.expensemanager.app.ui.messages

import android.content.Context
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.services.SMSParsingService
import com.expensemanager.app.services.TransactionFilterService
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for Messages screen
 * Manages SMS transaction history, filtering, and sorting
 */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val smsParsingService: SMSParsingService,
    private val transactionFilterService: TransactionFilterService
) : ViewModel() {
    
    companion object {
    }
    
    // Utility classes
    private val categoryManager by lazy { CategoryManager(context) }
    private val merchantAliasManager by lazy { MerchantAliasManager(context) }
    // SMS parsing is now handled by the injected SMSParsingService
    
    // Private mutable state
    private val _uiState = MutableStateFlow(MessagesUIState())
    
    // Public immutable state
    val uiState: StateFlow<MessagesUIState> = _uiState.asStateFlow()
    
    // Debouncing for toggle operations to prevent rapid state changes
    private var toggleDebounceJob: Job? = null
    
    init {
        Timber.tag(LogConfig.FeatureTags.SMS).i("============ ViewModel initialized ============")

        // Apply default "This Month" filter
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startOfMonth = dateFormat.format(calendar.time)

        Timber.tag(LogConfig.FeatureTags.SMS).i("[INIT] Applying default 'This Month' filter from: $startOfMonth")

        applyFilterOptions(
            FilterOptions(
                dateFrom = startOfMonth
            )
        )
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: MessagesUIEvent) {
        Timber.tag(LogConfig.FeatureTags.SMS).d("Handling event: $event")

        when (event) {
            is MessagesUIEvent.LoadMessages -> loadMessages()
            is MessagesUIEvent.LoadMoreMessages -> loadMoreMessages()
            is MessagesUIEvent.RefreshMessages -> refreshMessages()
            is MessagesUIEvent.ResyncSMS -> resyncSMSMessages()
            is MessagesUIEvent.TestSMSScanning -> testSMSScanning()
            is MessagesUIEvent.Search -> searchMessages(event.query)
            is MessagesUIEvent.ApplySort -> applySortOption(event.sortOption)
            is MessagesUIEvent.ApplyFilter -> applyFilterOptions(event.filterOptions)
            is MessagesUIEvent.ApplyFilterTab -> applyFilterTab(event.filterTab)
            is MessagesUIEvent.ResetFilters -> resetFilters()
            is MessagesUIEvent.ToggleGroupInclusion -> toggleGroupInclusion(event.merchantName, event.isIncluded)
            is MessagesUIEvent.UpdateMerchantGroup -> updateMerchantGroup(event.merchantName, event.newDisplayName, event.newCategory)
            is MessagesUIEvent.ResetMerchantGroup -> resetMerchantGroupToOriginal(event.merchantName)
            is MessagesUIEvent.UpdateCategoryForMerchant -> updateCategoryForMerchant(event.merchantName, event.newCategory)
            is MessagesUIEvent.ClearError -> clearError()
        }
    }
    
    /**
     * Load messages from database with pagination
     */
    private fun loadMessages() {
        Timber.tag(LogConfig.FeatureTags.SMS).d("Starting paginated messages load...")

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            hasError = false,
            error = null,
            currentPage = 0
        )

        viewModelScope.launch {
            try {
                // 🔧 PAGINATION: Query database with date filter and limit
                val filterOptions = _uiState.value.currentFilterOptions
                val pageSize = _uiState.value.pageSize

                // Parse date range from filters
                val (startDate, endDate) = getDateRangeFromFilters(filterOptions)

                Timber.tag(LogConfig.FeatureTags.SMS).d("[PAGINATION] Loading page 0, pageSize=$pageSize, dateRange=${startDate} to ${endDate}")

                // Get total count for pagination
                val totalCount = expenseRepository.getTransactionCountByDateRange(startDate, endDate)
                Timber.tag(LogConfig.FeatureTags.SMS).d("[PAGINATION] Total transactions in range: $totalCount")

                // Load first page directly from database
                val dbTransactions = expenseRepository.getTransactionsByDateRangePaginated(
                    startDate = startDate,
                    endDate = endDate,
                    limit = pageSize,
                    offset = 0
                )
                Timber.tag(LogConfig.FeatureTags.SMS).d("[PAGINATION] Loaded ${dbTransactions.size} transactions from database")
                
                if (dbTransactions.isNotEmpty()) {
                    // Convert to MessageItem format
                    val messageItems = dbTransactions.mapNotNull { transaction ->
                        try {
                            // Apply merchant aliases when loading from database
                            val displayName = merchantAliasManager.getDisplayName(transaction.rawMerchant)
                            val aliasCategory = merchantAliasManager.getMerchantCategory(transaction.rawMerchant)
                            val aliasCategoryColor = merchantAliasManager.getMerchantCategoryColor(transaction.rawMerchant)

                            MessageItem(
                                amount = transaction.amount,
                                merchant = displayName,
                                bankName = transaction.bankName,
                                category = aliasCategory,
                                categoryColor = aliasCategoryColor,
                                confidence = (transaction.confidenceScore * 100).toInt(),
                                dateTime = formatDate(transaction.transactionDate),
                                rawSMS = transaction.rawSmsBody,
                                isDebit = transaction.isDebit,
                                rawMerchant = transaction.rawMerchant,
                                actualDate = transaction.transactionDate
                            )
                        } catch (e: Exception) {
                            Timber.tag(LogConfig.FeatureTags.SMS).w("Error converting transaction: ${e.message}")
                            null
                        }
                    }.filter {
                        // Filter out invalid merchants
                        it.merchant.isNotBlank() && it.merchant != "."
                    }

                    val hasMoreData = dbTransactions.size >= pageSize

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allMessages = messageItems,
                        filteredMessages = messageItems,
                        isEmpty = messageItems.isEmpty(),
                        currentPage = 0,
                        hasMoreData = hasMoreData,
                        totalCount = totalCount
                    )

                    // Apply filters and sorting
                    applyFiltersAndSort()

                    Timber.tag(LogConfig.FeatureTags.SMS).d("[PAGINATION] Loaded ${messageItems.size} items, hasMore=$hasMoreData, total=$totalCount")
                } else {
                    // No database data found - check if we have any data at all
                    if (totalCount == 0) {
                        Timber.tag(LogConfig.FeatureTags.SMS).d("No transactions in database, showing empty state")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            allMessages = emptyList(),
                            filteredMessages = emptyList(),
                            groupedMessages = emptyList(),
                            isEmpty = true,
                            hasMoreData = false,
                            totalCount = 0
                        )
                    }
                }
            } catch (e: SecurityException) {
                Timber.tag(LogConfig.FeatureTags.SMS).w(e, "SMS permission denied")
                handleError("SMS permission required to read transaction messages")
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Error loading messages")
                handleError("Error reading messages: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Load more messages (pagination)
     */
    private fun loadMoreMessages() {
        val currentState = _uiState.value

        // Don't load if already loading or no more data
        if (currentState.isLoadingMore || !currentState.hasMoreData) {
            Timber.tag(LogConfig.FeatureTags.SMS).d("[PAGINATION] Skip loadMore: isLoading=${currentState.isLoadingMore}, hasMore=${currentState.hasMoreData}")
            return
        }

        Timber.tag(LogConfig.FeatureTags.SMS).d("[PAGINATION] Loading more messages...")

        _uiState.value = currentState.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val pageSize = currentState.pageSize
                val nextPage = currentState.currentPage + 1
                val offset = nextPage * pageSize

                val filterOptions = currentState.currentFilterOptions
                val (startDate, endDate) = getDateRangeFromFilters(filterOptions)

                Timber.tag(LogConfig.FeatureTags.SMS).d("[PAGINATION] Loading page $nextPage, offset=$offset")

                // Load next page from database
                val dbTransactions = expenseRepository.getTransactionsByDateRangePaginated(
                    startDate = startDate,
                    endDate = endDate,
                    limit = pageSize,
                    offset = offset
                )

                if (dbTransactions.isNotEmpty()) {
                    val newItems = dbTransactions.mapNotNull { transaction ->
                        try {
                            val displayName = merchantAliasManager.getDisplayName(transaction.rawMerchant)
                            val aliasCategory = merchantAliasManager.getMerchantCategory(transaction.rawMerchant)
                            val aliasCategoryColor = merchantAliasManager.getMerchantCategoryColor(transaction.rawMerchant)

                            MessageItem(
                                amount = transaction.amount,
                                merchant = displayName,
                                bankName = transaction.bankName,
                                category = aliasCategory,
                                categoryColor = aliasCategoryColor,
                                confidence = (transaction.confidenceScore * 100).toInt(),
                                dateTime = formatDate(transaction.transactionDate),
                                rawSMS = transaction.rawSmsBody,
                                isDebit = transaction.isDebit,
                                rawMerchant = transaction.rawMerchant,
                                actualDate = transaction.transactionDate
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.filter {
                        it.merchant.isNotBlank() && it.merchant != "."
                    }

                    val hasMoreData = dbTransactions.size >= pageSize
                    val updatedMessages = currentState.allMessages + newItems

                    _uiState.value = currentState.copy(
                        isLoadingMore = false,
                        allMessages = updatedMessages,
                        filteredMessages = updatedMessages,
                        currentPage = nextPage,
                        hasMoreData = hasMoreData
                    )

                    // Apply filters and sorting
                    applyFiltersAndSort()

                    Timber.tag(LogConfig.FeatureTags.SMS).d("[PAGINATION] Loaded ${newItems.size} more items, total=${updatedMessages.size}, hasMore=$hasMoreData")
                } else {
                    // No more data
                    _uiState.value = currentState.copy(
                        isLoadingMore = false,
                        hasMoreData = false
                    )
                    Timber.tag(LogConfig.FeatureTags.SMS).d("[PAGINATION] No more data available")
                }
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.SMS).e(e, "[PAGINATION] Error loading more messages")
                _uiState.value = currentState.copy(
                    isLoadingMore = false,
                    hasError = true,
                    error = "Error loading more: ${e.message}"
                )
            }
        }
    }

    /**
     * Parse date range from filter options
     */
    private fun getDateRangeFromFilters(filterOptions: FilterOptions): Pair<Date, Date> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val startDate = if (filterOptions.dateFrom != null) {
            try {
                dateFormat.parse(filterOptions.dateFrom) ?: Date(0)
            } catch (e: Exception) {
                Date(0) // Very old date
            }
        } else {
            Date(0) // Very old date
        }

        val endDate = if (filterOptions.dateTo != null) {
            try {
                dateFormat.parse(filterOptions.dateTo) ?: Date()
            } catch (e: Exception) {
                Date() // Now
            }
        } else {
            Date() // Now
        }

        return Pair(startDate, endDate)
    }

    /**
     * Fallback to SMS scanning if no database data - now using unified SMSParsingService
     */
    private fun loadFromSMSFallback() {
        viewModelScope.launch {
            try {
                val historicalTransactions = smsParsingService.scanHistoricalSMS { current, total, status ->
                    Timber.tag(LogConfig.FeatureTags.SMS).d("[UNIFIED] SMS Fallback progress: $status ($current/$total)")
                }
                Timber.tag(LogConfig.FeatureTags.SMS).d("[UNIFIED] SMS Fallback: Found ${historicalTransactions.size} transactions")
                
                if (historicalTransactions.isNotEmpty()) {
                    val messageItems = historicalTransactions.map { transaction ->
                        val displayName = merchantAliasManager.getDisplayName(transaction.merchant)
                        val category = merchantAliasManager.getMerchantCategory(transaction.merchant)
                        val categoryColor = merchantAliasManager.getMerchantCategoryColor(transaction.merchant)
                        MessageItem(
                            amount = transaction.amount,
                            merchant = displayName,
                            bankName = transaction.bankName,
                            category = category,
                            categoryColor = categoryColor,
                            confidence = (transaction.confidence * 100).toInt(),
                            dateTime = formatDate(transaction.date),
                            rawSMS = transaction.rawSMS,
                            isDebit = transaction.isDebit,
                            actualDate = transaction.date // 🔧 BUG FIX: Actual date for filtering
                        )
                    }.distinctBy { 
                        "${it.merchant}_${it.amount}_${it.dateTime}_${it.bankName}"
                    }
                    
                    updateMessagesState(messageItems)
                } else {
                    // No transactions found
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allMessages = emptyList(),
                        filteredMessages = emptyList(),
                        groupedMessages = emptyList(),
                        isEmpty = true
                    )
                }
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.SMS).e(e, "[UNIFIED] Error in SMS fallback")
                handleError("Error scanning SMS: ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    /**
     * Update messages state with loaded data
     */
    private fun updateMessagesState(messageItems: List<MessageItem>) {
        val currentState = _uiState.value

        Timber.tag(LogConfig.FeatureTags.SMS).d("[UPDATE_STATE] Current filter BEFORE update: ${currentState.currentFilterOptions.dateFrom} to ${currentState.currentFilterOptions.dateTo}")

        _uiState.value = currentState.copy(
            isLoading = false,
            allMessages = messageItems,
            filteredMessages = messageItems,
            isEmpty = messageItems.isEmpty()
        )

        Timber.tag(LogConfig.FeatureTags.SMS).d("[UPDATE_STATE] Current filter AFTER update: ${_uiState.value.currentFilterOptions.dateFrom} to ${_uiState.value.currentFilterOptions.dateTo}")

        // Apply current filters and sorting
        applyFiltersAndSort()

        Timber.tag(LogConfig.FeatureTags.SMS).d("Messages state updated with ${messageItems.size} items")
    }
    
    /**
     * Refresh messages (pull-to-refresh)
     */
    private fun refreshMessages() {
        Timber.tag(LogConfig.FeatureTags.SMS).d("Refreshing messages...")
        
        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            try {
                // Reload messages
                loadMessages()
                
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    lastRefreshTime = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Error refreshing messages")
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    hasError = true,
                    error = "Failed to refresh: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Resync SMS messages - now using unified SMSParsingService
     */
    private fun resyncSMSMessages() {
        Timber.tag(LogConfig.FeatureTags.SMS).d("[UNIFIED] Starting SMS resync...")
        
        _uiState.value = _uiState.value.copy(
            isSyncingSMS = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            try {
                val historicalTransactions = smsParsingService.scanHistoricalSMS { current, total, status ->
                    Timber.tag(LogConfig.FeatureTags.SMS).d("[UNIFIED] SMS Resync progress: $status ($current/$total)")
                }
                Timber.tag(LogConfig.FeatureTags.SMS).d("[UNIFIED] SMS resync found ${historicalTransactions.size} transactions")
                
                if (historicalTransactions.isNotEmpty()) {
                    val messageItems = historicalTransactions.map { transaction ->
                        val displayName = merchantAliasManager.getDisplayName(transaction.merchant)
                        val category = merchantAliasManager.getMerchantCategory(transaction.merchant)
                        val categoryColor = merchantAliasManager.getMerchantCategoryColor(transaction.merchant)
                        MessageItem(
                            amount = transaction.amount,
                            merchant = displayName,
                            bankName = transaction.bankName,
                            category = category,
                            categoryColor = categoryColor,
                            confidence = (transaction.confidence * 100).toInt(),
                            dateTime = formatDate(transaction.date),
                            rawSMS = transaction.rawSMS,
                            isDebit = transaction.isDebit,
                            actualDate = transaction.date // 🔧 BUG FIX: Actual date for filtering
                        )
                    }.distinctBy { 
                        "${it.merchant}_${it.amount}_${it.dateTime}_${it.bankName}"
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        isSyncingSMS = false,
                        allMessages = messageItems,
                        filteredMessages = messageItems,
                        isEmpty = false,
                        syncMessage = "Found ${historicalTransactions.size} new transaction SMS messages!"
                    )
                    
                    applyFiltersAndSort()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSyncingSMS = false,
                        allMessages = emptyList(),
                        filteredMessages = emptyList(),
                        groupedMessages = emptyList(),
                        isEmpty = true,
                        syncMessage = "No transaction SMS found in your inbox"
                    )
                }
            } catch (e: SecurityException) {
                Timber.tag(LogConfig.FeatureTags.SMS).w(e, "[UNIFIED] SMS permission denied during resync")
                _uiState.value = _uiState.value.copy(
                    isSyncingSMS = false,
                    hasError = true,
                    error = "SMS permission required for resync"
                )
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.SMS).e(e, "[UNIFIED] Error during SMS resync")
                _uiState.value = _uiState.value.copy(
                    isSyncingSMS = false,
                    hasError = true,
                    error = "Error syncing SMS: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }
    
    /**
     * Test SMS scanning functionality - now using unified SMSParsingService
     */
    private fun testSMSScanning() {
        Timber.tag(LogConfig.FeatureTags.SMS).d("[UNIFIED] Starting SMS scanning test...")
        
        viewModelScope.launch {
            try {
                val transactions = smsParsingService.scanHistoricalSMS { current, total, status ->
                    Timber.tag(LogConfig.FeatureTags.SMS).d("[UNIFIED] SMS Test progress: $status ($current/$total)")
                }
                
                val testResults = buildString {
                    appendLine("UNIFIED SMS Scanning Test Results:")
                    appendLine("Total valid transactions: ${transactions.size}")
                    appendLine()
                    
                    if (transactions.isNotEmpty()) {
                        appendLine("Found transactions:")
                        transactions.take(5).forEach { transaction ->
                            appendLine("• ${transaction.merchant}: ₹${String.format("%.2f", transaction.amount)}")
                            appendLine("  Bank: ${transaction.bankName}")
                            appendLine("  Date: ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(transaction.date)}")
                            appendLine("  Confidence: ${(transaction.confidence * 100).toInt()}%")
                            appendLine()
                        }
                        
                        val totalAmount = transactions.sumOf { it.amount }
                        appendLine("Total amount: ₹${String.format("%.2f", totalAmount)}")
                    } else {
                        appendLine("No valid transaction SMS found.")
                        appendLine()
                        appendLine("This could mean:")
                        appendLine("• No real transaction SMS in last 6 months")
                        appendLine("• SMS are being filtered as non-transactions")
                        appendLine("• Check logcat for detailed filtering info")
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    testResults = testResults
                )
                
                Timber.tag(LogConfig.FeatureTags.SMS).d("[UNIFIED] SMS Test: Found ${transactions.size} transactions")
                
            } catch (e: SecurityException) {
                _uiState.value = _uiState.value.copy(
                    hasError = true,
                    error = "SMS permission is required to test SMS scanning"
                )
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.SMS).e(e, "[UNIFIED] SMS test error")
                _uiState.value = _uiState.value.copy(
                    hasError = true,
                    error = "Error testing SMS scanning: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Search messages by query
     */
    private fun searchMessages(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query
        )
        applyFiltersAndSort()
    }
    
    /**
     * Apply sort option
     */
    private fun applySortOption(sortOption: SortOption) {
        _uiState.value = _uiState.value.copy(
            currentSortOption = sortOption
        )
        applyFiltersAndSort()
    }
    
    /**
     * Apply filter options - reloads data from database with new filters
     */
    private fun applyFilterOptions(filterOptions: FilterOptions) {
        Timber.tag(LogConfig.FeatureTags.SMS).i("[FILTER_CHANGE] Filter changed: dateFrom=${filterOptions.dateFrom}, dateTo=${filterOptions.dateTo}")
        _uiState.value = _uiState.value.copy(
            currentFilterOptions = filterOptions
        )
        Timber.tag(LogConfig.FeatureTags.SMS).i("[FILTER_CHANGE] Calling loadMessages() to reload data from database")
        // Reload data from database with new date range
        loadMessages()
    }
    
    /**
     * Apply filter tab (ALL/INCLUDED/EXCLUDED)
     */
    private fun applyFilterTab(filterTab: TransactionFilterTab) {
        Timber.tag(LogConfig.FeatureTags.SMS).d("Applying filter tab: ${filterTab.displayName}")
        _uiState.value = _uiState.value.copy(
            currentFilterTab = filterTab
        )
        applyFiltersAndSort()
    }
    
    /**
     * Reset all filters
     */
    private fun resetFilters() {
        _uiState.value = _uiState.value.copy(
            currentFilterOptions = FilterOptions(),
            searchQuery = "",
            currentFilterTab = TransactionFilterTab.ALL
        )
        applyFiltersAndSort()
    }
    
    /**
     * Apply filters and sorting to messages
     */
    private fun applyFiltersAndSort() {
        viewModelScope.launch {
            val currentState = _uiState.value
            
            // Apply filter tab logic first (ALL/INCLUDED/EXCLUDED)
            var filtered = when (currentState.currentFilterTab) {
                TransactionFilterTab.ALL -> currentState.allMessages
                TransactionFilterTab.INCLUDED -> transactionFilterService.getIncludedMessageItems(currentState.allMessages)
                TransactionFilterTab.EXCLUDED -> transactionFilterService.getExcludedMessageItems(currentState.allMessages)
            }.toList()
            
            Timber.tag(LogConfig.FeatureTags.SMS).d("Applied filter tab ${currentState.currentFilterTab.displayName}: ${currentState.allMessages.size} -> ${filtered.size} items")
            
            // Apply search filter
            if (currentState.searchQuery.isNotEmpty()) {
                val query = currentState.searchQuery.lowercase()
                filtered = filtered.filter { item ->
                    item.merchant.lowercase().contains(query) ||
                    item.bankName.lowercase().contains(query) ||
                    item.rawSMS.lowercase().contains(query) ||
                    item.category.lowercase().contains(query)
                }
            }
            
            // Apply filters
            val filters = currentState.currentFilterOptions
            
            filters.minAmount?.let { minAmount ->
                filtered = filtered.filter { it.amount >= minAmount }
            }
            filters.maxAmount?.let { maxAmount ->
                filtered = filtered.filter { it.amount <= maxAmount }
            }
            
            if (filters.selectedBanks.isNotEmpty()) {
                filtered = filtered.filter { filters.selectedBanks.contains(it.bankName) }
            }
            
            if (filters.minConfidence > 0) {
                filtered = filtered.filter { it.confidence >= filters.minConfidence }
            }
            
            // Apply date filters using actual Date objects (parse String dates from filter UI)
            filters.dateFrom?.let { dateFromStr ->
                try {
                    // Try with time first (yyyy-MM-dd HH:mm:ss), fallback to date only (yyyy-MM-dd)
                    val dateFrom = try {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateFromStr)
                    } catch (e: Exception) {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateFromStr)
                    }
                    if (dateFrom != null) {
                        filtered = filtered.filter { it.actualDate >= dateFrom }
                        Timber.tag(LogConfig.FeatureTags.SMS).d("[DATE_FILTER] Applied dateFrom filter: $dateFromStr (parsed: $dateFrom)")
                    }
                } catch (e: Exception) {
                    Timber.tag(LogConfig.FeatureTags.SMS).w("Invalid dateFrom format: $dateFromStr - ${e.message}")
                }
            }
            filters.dateTo?.let { dateToStr ->
                try {
                    // Try with time first (yyyy-MM-dd HH:mm:ss), fallback to date only (yyyy-MM-dd)
                    val dateTo = try {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateToStr)
                    } catch (e: Exception) {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateToStr)
                    }
                    if (dateTo != null) {
                        filtered = filtered.filter { it.actualDate <= dateTo }
                        Timber.tag(LogConfig.FeatureTags.SMS).d("[DATE_FILTER] Applied dateTo filter: $dateToStr (parsed: $dateTo)")
                    }
                } catch (e: Exception) {
                    Timber.tag(LogConfig.FeatureTags.SMS).w("Invalid dateTo format: $dateToStr - ${e.message}")
                }
            }

            Timber.tag(LogConfig.FeatureTags.SMS).d("[DATE_FILTER] Applied date filters - From: ${filters.dateFrom}, To: ${filters.dateTo}, Filtered: ${filtered.size} items")
            
            // Skip individual transaction sorting - let groupTransactionsByMerchant handle the sorting
            // This ensures that the final merchant group sorting is what determines the display order
            val sortOption = currentState.currentSortOption
            Timber.tag(LogConfig.FeatureTags.SMS).d("[DEBUG] Skipping individual transaction sorting - groups will be sorted by ${sortOption.field}")
            
            // Group by merchant - this will handle the proper sorting
            val groupedMessages = groupTransactionsByMerchant(filtered, sortOption)
            
            // Update state
            _uiState.value = currentState.copy(
                filteredMessages = filtered,
                groupedMessages = groupedMessages
            )
            
            Timber.tag(LogConfig.FeatureTags.SMS).d("Applied filters and sort: ${filtered.size} items, ${groupedMessages.size} groups")
        }
    }
    
    /**
     * Group transactions by merchant for display
     */
    private fun groupTransactionsByMerchant(transactions: List<MessageItem>, sortOption: SortOption): List<MerchantGroup> {
        // 🔧 BUG FIX: Filter out transactions with empty/invalid merchant names
        val validTransactions = transactions.filter { transaction ->
            transaction.merchant.isNotBlank() && transaction.merchant != "."
        }

        if (validTransactions.size < transactions.size) {
            Timber.tag(LogConfig.FeatureTags.SMS).w("Filtered out ${transactions.size - validTransactions.size} transactions with empty merchant names")
        }

        val groups = validTransactions
            .groupBy { it.merchant }
            .map { (displayName, merchantTransactions) ->
                val sortedTransactions = merchantTransactions.sortedByDescending { transaction ->
                    getDateSortOrderReverse(transaction.dateTime)
                }
                val totalAmount = merchantTransactions.sumOf { it.amount }
                val category = merchantTransactions.firstOrNull()?.category ?: "Other"
                val categoryColor = merchantTransactions.firstOrNull()?.categoryColor ?: "#9e9e9e"
                
                val latestTransactionDate = sortedTransactions.firstOrNull()?.let { 
                    getDateSortOrderReverse(it.dateTime) 
                } ?: 0L
                
                val primaryBankName = merchantTransactions
                    .groupBy { it.bankName }
                    .maxByOrNull { it.value.size }
                    ?.key ?: ""
                
                val averageConfidence = if (merchantTransactions.isNotEmpty()) {
                    merchantTransactions.map { it.confidence }.average()
                } else {
                    0.0
                }
                
                MerchantGroup(
                    merchantName = displayName,
                    transactions = sortedTransactions,
                    totalAmount = totalAmount,
                    categoryColor = categoryColor,
                    category = category,
                    isExpanded = false,
                    isIncludedInCalculations = true,
                    latestTransactionDate = latestTransactionDate,
                    primaryBankName = primaryBankName,
                    averageConfidence = averageConfidence
                )
            }
        
        // Sort groups based on current sort option
        return when (sortOption.field) {
            "date" -> {
                if (sortOption.ascending) {
                    groups.sortedBy { it.latestTransactionDate }
                } else {
                    groups.sortedByDescending { it.latestTransactionDate }
                }
            }
            "amount" -> {
                if (sortOption.ascending) {
                    groups.sortedBy { it.totalAmount }
                } else {
                    groups.sortedByDescending { it.totalAmount }
                }
            }
            "merchant" -> {
                if (sortOption.ascending) {
                    groups.sortedBy { it.merchantName.lowercase() }
                } else {
                    groups.sortedByDescending { it.merchantName.lowercase() }
                }
            }
            "bank" -> {
                if (sortOption.ascending) {
                    groups.sortedBy { it.primaryBankName.lowercase() }
                } else {
                    groups.sortedByDescending { it.primaryBankName.lowercase() }
                }
            }
            "confidence" -> {
                if (sortOption.ascending) {
                    groups.sortedBy { it.averageConfidence }
                } else {
                    groups.sortedByDescending { it.averageConfidence }
                }
            }
            else -> groups.sortedByDescending { it.latestTransactionDate }
        }
    }
    
    /**
     * Toggle group inclusion in calculations with proper database sync and immediate data refresh
     */
    private fun toggleGroupInclusion(merchantName: String, isIncluded: Boolean) {
        try {
            Timber.tag(LogConfig.FeatureTags.SMS).d("Toggling group inclusion for '$merchantName': $isIncluded")
            
            // Cancel any pending toggle operations
            toggleDebounceJob?.cancel()
            
            // Update state immediately for responsive UI
            val currentGroups = _uiState.value.groupedMessages
            val updatedGroups = currentGroups.map { group ->
                if (group.merchantName == merchantName) {
                    group.copy(isIncludedInCalculations = isIncluded)
                } else {
                    group
                }
            }
            
            // Debounce the state update to prevent RecyclerView layout conflicts
            toggleDebounceJob = viewModelScope.launch {
                try {
                    // Small delay to let RecyclerView finish any ongoing layout operations
                    delay(100)
                    
                    _uiState.value = _uiState.value.copy(
                        groupedMessages = updatedGroups
                    )
                    
                    // CRITICAL FIX: Update both SharedPreferences AND database, then refresh data
                    launch {
                        delay(200) // Additional delay for persistence operations
                        
                        // 1. Save to SharedPreferences (legacy support)
                        saveGroupInclusionStates(updatedGroups)
                        
                        // 2. Update database exclusion state
                        updateMerchantExclusionInDatabase(merchantName, !isIncluded)
                        
                        // 3. CRITICAL: Trigger data refresh to update summary calculations
                        notifyDataChanged()
                    }
                    
                    Timber.tag(LogConfig.FeatureTags.SMS).d("Successfully toggled group inclusion for '$merchantName'")
                    
                } catch (e: Exception) {
                    Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Error in debounced toggle for '$merchantName'")
                    handleError("Failed to update merchant exclusion settings. Please try again.")
                }
            }
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Error toggling group inclusion for '$merchantName'")
            handleError("Failed to update merchant exclusion settings. Please try again.")
        }
    }
    
    /**
     * Update merchant group display name and category
     */
    private fun updateMerchantGroup(merchantName: String, newDisplayName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                Timber.tag(LogConfig.FeatureTags.SMS).d("Updating merchant group: $merchantName -> $newDisplayName, category: $newCategory")
                
                // Find all transactions in this group to get their original merchant names
                val originalMerchantNames = mutableSetOf<String>()
                
                val existingOriginalNames = merchantAliasManager.getMerchantsByDisplayName(merchantName)
                if (existingOriginalNames.isNotEmpty()) {
                    originalMerchantNames.addAll(existingOriginalNames)
                } else {
                    // Extract from raw SMS data
                    val group = _uiState.value.groupedMessages.find { it.merchantName == merchantName }
                    group?.transactions?.forEach { transaction ->
                        val originalName = extractOriginalMerchantFromRawSMS(transaction.rawSMS)
                        if (originalName.isNotEmpty()) {
                            originalMerchantNames.add(originalName)
                        }
                    }
                    
                    if (originalMerchantNames.isEmpty()) {
                        originalMerchantNames.add(merchantName)
                    }
                }
                
                // Validate the new category exists
                val allCategories = categoryManager.getAllCategories()
                if (!allCategories.contains(newCategory)) {
                    categoryManager.addCustomCategory(newCategory)
                }
                
                // Create/update aliases for all original merchant names
                originalMerchantNames.forEach { originalName ->
                    merchantAliasManager.setMerchantAlias(originalName, newDisplayName, newCategory)
                }
                
                // Reload data to reflect changes
                loadMessages()
                
                _uiState.value = _uiState.value.copy(
                    successMessage = "Updated group '$newDisplayName' in category '$newCategory'"
                )
                
                Timber.tag(LogConfig.FeatureTags.SMS).d("Merchant group update completed successfully")
                
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Error updating merchant group")
                handleError("Error updating group: ${e.message}")
            }
        }
    }
    
    /**
     * Reset merchant group to original names and categories
     */
    private fun resetMerchantGroupToOriginal(merchantName: String) {
        viewModelScope.launch {
            try {
                val originalNames = merchantAliasManager.getMerchantsByDisplayName(merchantName)
                
                originalNames.forEach { originalName ->
                    merchantAliasManager.removeMerchantAlias(originalName)
                }
                
                if (originalNames.isEmpty()) {
                    merchantAliasManager.removeMerchantAlias(merchantName)
                }
                
                // Reload data to reflect changes
                loadMessages()
                
                _uiState.value = _uiState.value.copy(
                    successMessage = "Reset group to original names and categories"
                )
                
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Error resetting merchant group")
                handleError("Error resetting group: ${e.message}")
            }
        }
    }
    
    /**
     * Update category for specific merchant
     */
    private fun updateCategoryForMerchant(merchantName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                categoryManager.updateCategory(merchantName, newCategory)
                
                // Reload data to reflect changes
                loadMessages()
                
                _uiState.value = _uiState.value.copy(
                    successMessage = "Updated category for $merchantName to '$newCategory'"
                )
                
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Error updating category")
                handleError("Failed to update category: ${e.message}")
            }
        }
    }
    
    /**
     * Clear error state
     */
    private fun clearError() {
        _uiState.value = _uiState.value.copy(
            hasError = false,
            error = null,
            successMessage = null,
            syncMessage = null,
            testResults = null
        )
    }
    
    /**
     * Handle errors
     */
    private fun handleError(message: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isRefreshing = false,
            isSyncingSMS = false,
            hasError = true,
            error = message
        )
    }
    
    /**
     * Helper functions
     */
    private fun formatDate(date: Date): String {
        val now = Date()
        val diffInMs = now.time - date.time
        val diffInDays = diffInMs / (1000 * 60 * 60 * 24)
        val diffInHours = diffInMs / (1000 * 60 * 60)
        
        return when {
            diffInHours < 1 -> "Just now"
            diffInHours < 24 -> "$diffInHours hours ago"
            diffInDays == 1L -> "Yesterday"
            diffInDays < 7 -> "$diffInDays days ago"
            else -> {
                val formatter = SimpleDateFormat("MMM dd", Locale.getDefault())
                formatter.format(date)
            }
        }
    }
    
    private fun getDateSortOrderReverse(dateTimeString: String): Long {
        return when {
            dateTimeString.contains("Just now") -> System.currentTimeMillis()
            dateTimeString.contains("hour") -> {
                val hours = dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
            }
            dateTimeString.contains("Yesterday") -> System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            dateTimeString.contains("days ago") -> {
                val days = dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            }
            else -> {
                // Handle absolute dates like "Aug 29", "Dec 15", "2024-08-29", etc.
                parseAbsoluteDateToTimestamp(dateTimeString)
            }
        }
    }
    
    private fun parseAbsoluteDateToTimestamp(dateString: String): Long {
        try {
            // Try different date formats commonly used in the app
            val formats = listOf(
                java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()), // Aug 29
                java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()),  // Aug 9  
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()), // 2024-08-29
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()), // 29/08/2024
                java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.getDefault())  // 08/29/2024
            )
            
            for (format in formats) {
                try {
                    val parsedDate = format.parse(dateString)
                    if (parsedDate != null) {
                        // If no year specified (like "Aug 29"), assume current year
                        val calendar = java.util.Calendar.getInstance()
                        calendar.time = parsedDate
                        if (calendar.get(java.util.Calendar.YEAR) == 1970) {
                            calendar.set(java.util.Calendar.YEAR, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
                        }
                        return calendar.timeInMillis
                    }
                } catch (e: Exception) {
                    // Try next format
                    continue
                }
            }
            
            // Fallback to a reasonable old timestamp instead of 0L
            return System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000L) // 1 year ago
            
        } catch (e: Exception) {
            return System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000L) // 1 year ago
        }
    }
    
    private fun extractOriginalMerchantFromRawSMS(rawSMS: String): String {
        val patterns = listOf(
            Regex("""at\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""to\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""for\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""UPI[/-]([A-Z][A-Z0-9\s&'-]+?)(?:\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""([A-Z]{3,})\*[A-Z0-9]+""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(rawSMS)
            if (match != null) {
                val merchantName = match.groupValues[1].trim()
                return merchantName
                    .replace(Regex("""[*#@\-_]+.*"""), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                    .takeIf { it.length >= 2 } ?: ""
            }
        }
        
        return ""
    }
    
    private fun saveGroupInclusionStates(groups: List<MerchantGroup>) {
        try {
            Timber.tag(LogConfig.FeatureTags.SMS).d("Saving inclusion states for ${groups.size} groups")
            
            val prefs = context.getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            val inclusionStates = org.json.JSONObject()
            var savedCount = 0
            
            groups.forEach { group ->
                try {
                    inclusionStates.put(group.merchantName, group.isIncludedInCalculations)
                    savedCount++
                } catch (e: Exception) {
                    Timber.tag(LogConfig.FeatureTags.SMS).w(e, "Failed to save inclusion state for '${group.merchantName}'")
                }
            }
            
            editor.putString("group_inclusion_states", inclusionStates.toString())
            val success = editor.commit()
            
            if (success) {
                Timber.tag(LogConfig.FeatureTags.SMS).d("Successfully saved $savedCount inclusion states")
            } else {
                Timber.tag(LogConfig.FeatureTags.SMS).e("Failed to commit inclusion states to SharedPreferences")
                throw Exception("SharedPreferences commit failed")
            }
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Error saving group inclusion states")
            // Don't show error to user for this - it's not critical
            // The UI state is already updated, this is just persistence
        }
    }
    
    /**
     * Update merchant exclusion status in database
     */
    private suspend fun updateMerchantExclusionInDatabase(merchantName: String, isExcluded: Boolean) {
        try {
            Timber.tag(LogConfig.FeatureTags.SMS).d("Updating database exclusion for '$merchantName': $isExcluded")
            
            // Get original merchant names for this display name
            val originalMerchantNames = merchantAliasManager.getMerchantsByDisplayName(merchantName)
            
            if (originalMerchantNames.isNotEmpty()) {
                // Update all original merchants
                originalMerchantNames.forEach { originalName ->
                    val normalizedName = normalizeMerchantName(originalName)
                    expenseRepository.updateMerchantExclusion(normalizedName, isExcluded)
                }
            } else {
                // Fallback: update using the display name itself
                val normalizedName = normalizeMerchantName(merchantName)
                expenseRepository.updateMerchantExclusion(normalizedName, isExcluded)
            }
            
            Timber.tag(LogConfig.FeatureTags.SMS).i("Successfully updated database exclusion for '$merchantName'")
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Failed to update database exclusion for '$merchantName'")
        }
    }
    
    /**
     * Notify other components that data has changed (triggers dashboard refresh)
     */
    private fun notifyDataChanged() {
        try {
            // Broadcast data change to trigger dashboard refresh
            val intent = android.content.Intent("com.expensemanager.app.DATA_CHANGED")
            context.sendBroadcast(intent)
            
            Timber.tag(LogConfig.FeatureTags.SMS).d("[DATA_SYNC] Broadcast sent to refresh dependent screens")
            
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Error broadcasting data change")
        }
    }
    
    /**
     * Normalize merchant name to match database format
     */
    private fun normalizeMerchantName(merchantName: String): String {
        return merchantName.uppercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("\\*(ORDER|PAYMENT|TXN|TRANSACTION).*$"), "")
            .replace(Regex("#\\d+.*$"), "")
            .replace(Regex("@\\w+.*$"), "")
            .replace(Regex("-{2,}.*$"), "")
            .replace(Regex("_{2,}.*$"), "")
            .trim()
    }
    
    /**
     * Invalidate MerchantAliasManager cache to ensure fresh data after external updates
     * This method should be called after the Fragment updates merchant aliases
     */
    fun invalidateMerchantAliasCache() {
        try {
            merchantAliasManager.invalidateCache()
            Timber.tag(LogConfig.FeatureTags.SMS).d("[CACHE_SYNC] ViewModel MerchantAliasManager cache invalidated")
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.SMS).e(e, "Error invalidating ViewModel merchant alias cache")
        }
    }
    
    /**
     * Force refresh data after external changes (called from Fragment)
     */
    fun refreshDataAfterExternalChanges() {
        Timber.tag(LogConfig.FeatureTags.SMS).d("[DATA_SYNC] Refreshing data after external changes")
        loadMessages()
    }
}