package com.smartexpenseai.app.ui.messages

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.services.SMSParsingService
import com.smartexpenseai.app.services.TransactionFilterService
import com.smartexpenseai.app.utils.CategoryManager
import com.smartexpenseai.app.utils.MerchantAliasManager
import com.smartexpenseai.app.utils.logging.StructuredLogger
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
    private val transactionFilterService: TransactionFilterService,
    private val categoryManager: CategoryManager,
    private val merchantAliasManager: MerchantAliasManager
) : ViewModel() {

    companion object {
    }

    private val logger = StructuredLogger(
        featureTag = "UI",
        className = "MessagesViewModel"
    )
    // Private mutable state
    private val _uiState = MutableStateFlow(MessagesUIState())
    
    // Public immutable state
    val uiState: StateFlow<MessagesUIState> = _uiState.asStateFlow()
    
    // Debouncing for toggle operations to prevent rapid state changes
    private var toggleDebounceJob: Job? = null
    
    init {
        logger.info("init","============ ViewModel initialized ============")

        // Set default "This Month" filter but DON'T trigger load yet
        // Fragment will call startInitialLoad() when ready to observe state
        val startOfMonth = getStartOfMonthDate()

        _uiState.value = _uiState.value.copy(
            currentFilterOptions = FilterOptions(
                dateFrom = startOfMonth
            )
        )

        logger.info("init","Default filter set to 'This Month' from: $startOfMonth. Waiting for Fragment to trigger load.")
    }

    /**
     * Get start of month date in yyyy-MM-dd format
     */
    private fun getStartOfMonthDate(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    /**
     * Start initial data load - called by Fragment when ready to observe
     */
    fun startInitialLoad() {
        logger.info("startInitialLoad", "Fragment ready, starting initial data load")
        loadMessages()
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: MessagesUIEvent) {
        logger.debug("handleEvent","Handling event: $event")

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
        logger.info("loadMessages","Starting paginated messages load...")

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

                logger.debug("loadMessages","Loading page 0, pageSize=$pageSize, dateRange=${startDate} to ${endDate}")

                // Get total count for pagination
                val totalCount = expenseRepository.getTransactionCountByDateRange(startDate, endDate)
                logger.debug("loadMessages","Total transactions in range: $totalCount")

                // Load first page directly from database
                val dbTransactions = expenseRepository.getTransactionsByDateRangePaginated(
                    startDate = startDate,
                    endDate = endDate,
                    limit = pageSize,
                    offset = 0
                )
                logger.debug("loadMessages","Loaded ${dbTransactions.size} transactions from database")
                
                if (dbTransactions.isNotEmpty()) {
                    // Convert to MessageItem format
                    val messageItems = dbTransactions.mapNotNull { transaction ->
                        try {
                            // Apply merchant aliases when loading from database
                            val displayName = merchantAliasManager.getDisplayName(transaction.rawMerchant)
                            val aliasCategory = merchantAliasManager.getMerchantCategory(transaction.rawMerchant)
                            val aliasCategoryColor = merchantAliasManager.getMerchantCategoryColor(transaction.rawMerchant)

                            // Log suspicious merchant names
                            if (displayName.isBlank() || displayName == ".") {
                                logger.warn("loadMessages","🚨 SUSPICIOUS merchant: rawMerchant='${transaction.rawMerchant}', displayName='$displayName'")
                            }

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
                            logger.error("loadMessages","Error converting transaction: ${e.message}",e)
                            null
                        }
                    }.filter {
                        // Filter out invalid merchants
                        val isValid = it.merchant.isNotBlank() && it.merchant != "."
                        if (!isValid) {
                            logger.debug("loadMessages","🔴 Filtering out invalid merchant: '${it.merchant}', rawMerchant='${it.rawMerchant}'")
                        }
                        isValid
                    }

                    val hasMoreData = dbTransactions.size >= pageSize

                    // Update state with data but keep loading true to prevent intermediate empty state
                    _uiState.value = _uiState.value.copy(
                        isLoading = true, // Keep loading true until groups are ready
                        allMessages = messageItems,
                        filteredMessages = messageItems,
                        isEmpty = messageItems.isEmpty(),
                        currentPage = 0,
                        hasMoreData = hasMoreData,
                        totalCount = totalCount
                    )

                    // Apply filters and sorting (this will set isLoading = false when done)
                    applyFiltersAndSort()

                    logger.debug("loadMessages","Loaded ${messageItems.size} items, hasMore=$hasMoreData, total=$totalCount")
                } else {
                    // No database data found - check if we have any data at all
                    if (totalCount == 0) {
                        logger.debug("loadMessages","No transactions in database, showing empty state")
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
                logger.error("loadMessages", "SMS permission denied",e)
                handleError("SMS permission required to read transaction messages")
            } catch (e: Exception) {
                logger.error("loadMessages","Error loading messages",e)
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
            logger.debug("loadMoreMessages","Skip loadMore: isLoading=${currentState.isLoadingMore}, hasMore=${currentState.hasMoreData}")
            return
        }

        logger.debug("loadMoreMessages","Loading more messages...")

        _uiState.value = currentState.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val pageSize = currentState.pageSize
                val nextPage = currentState.currentPage + 1
                val offset = nextPage * pageSize

                val filterOptions = currentState.currentFilterOptions
                val (startDate, endDate) = getDateRangeFromFilters(filterOptions)

                logger.debug("loadMoreMessages","Loading page $nextPage, offset=$offset")

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

                    logger.debug("loadMoreMessages","Loaded ${newItems.size} more items, total=${updatedMessages.size}, hasMore=$hasMoreData")
                } else {
                    // No more data
                    _uiState.value = currentState.copy(
                        isLoadingMore = false,
                        hasMoreData = false
                    )
                    logger.debug("loadMoreMessages","No more data available")
                }
            } catch (e: Exception) {
                logger.error("loadMoreMessages","Error loading more messages",e)
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
                    logger.debug("loadFromSMSFallback","SMS Fallback progress: $status ($current/$total)")
                }
                logger.debug("loadFromSMSFallback","SMS Fallback: Found ${historicalTransactions.size} transactions")
                
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
                logger.error("loadFromSMSFallback","Error in SMS fallback",e)
                handleError("Error scanning SMS: ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    /**
     * Update messages state with loaded data
     */
    private fun updateMessagesState(messageItems: List<MessageItem>) {
        val currentState = _uiState.value

        logger.debug("updateMessagesState","Current filter BEFORE update: ${currentState.currentFilterOptions.dateFrom} to ${currentState.currentFilterOptions.dateTo}")

        _uiState.value = currentState.copy(
            isLoading = false,
            allMessages = messageItems,
            filteredMessages = messageItems,
            isEmpty = messageItems.isEmpty()
        )

        logger.debug("updateMessagesState","Current filter AFTER update: ${_uiState.value.currentFilterOptions.dateFrom} to ${_uiState.value.currentFilterOptions.dateTo}")

        // Apply current filters and sorting
        applyFiltersAndSort()

        logger.debug("updateMessagesState","Messages state updated with ${messageItems.size} items")
    }
    
    /**
     * Refresh messages (pull-to-refresh)
     */
    private fun refreshMessages() {
        logger.debug("refreshMessages","Refreshing messages...")
        
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
                logger.error("refreshMessages","Error refreshing messages",e)
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
        logger.debug("resyncSMSMessages","Starting SMS resync...")
        
        _uiState.value = _uiState.value.copy(
            isSyncingSMS = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            try {
                val historicalTransactions = smsParsingService.scanHistoricalSMS { current, total, status ->
                    logger.debug("resyncSMSMessages","SMS Resync progress: $status ($current/$total)")
                }
                logger.debug("resyncSMSMessages","SMS resync found ${historicalTransactions.size} transactions")
                
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
                logger.error("resyncSMSMessages","SMS permission denied during resync",e)
                _uiState.value = _uiState.value.copy(
                    isSyncingSMS = false,
                    hasError = true,
                    error = "SMS permission required for resync"
                )
            } catch (e: Exception) {
                logger.error("resyncSMSMessages","Error during SMS resync",e)
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
        logger.debug("testSMSScanning","Starting SMS scanning test...")
        
        viewModelScope.launch {
            try {
                val transactions = smsParsingService.scanHistoricalSMS { current, total, status ->
                    logger.debug("testSMSScanning","SMS Test progress: $status ($current/$total)")
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

                logger.debug("testSMSScanning","SMS Test: Found ${transactions.size} transactions")
                
            } catch (e: SecurityException) {
                _uiState.value = _uiState.value.copy(
                    hasError = true,
                    error = "SMS permission is required to test SMS scanning"
                )
            } catch (e: Exception) {
                logger.error("testSMSScanning","SMS test error",e)
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
        logger.info("applySortOption", "🔄 Applying sort: field=${sortOption.field}, ascending=${sortOption.ascending}, name=${sortOption.name}")
        _uiState.value = _uiState.value.copy(
            currentSortOption = sortOption
        )
        applyFiltersAndSort()
    }
    
    /**
     * Apply filter options - reloads data from database with new filters
     */
    private fun applyFilterOptions(filterOptions: FilterOptions) {
        logger.debug("applyFilterOptions", "Filter changed: dateFrom=${filterOptions.dateFrom}, dateTo=${filterOptions.dateTo}")
        _uiState.value = _uiState.value.copy(
            currentFilterOptions = filterOptions
        )
        logger.info("applyFilterOptions", "Calling loadMessages() to reload data from database")
        // Reload data from database with new date range
        loadMessages()
    }
    
    /**
     * Apply filter tab (ALL/INCLUDED/EXCLUDED)
     */
    private fun applyFilterTab(filterTab: TransactionFilterTab) {
        logger.debug("applyFilterTab","Applying filter tab: ${filterTab.displayName}")
        _uiState.value = _uiState.value.copy(
            currentFilterTab = filterTab
        )
        applyFiltersAndSort()
    }
    
    /**
     * Reset filters to default (current month)
     */
    private fun resetFilters() {
        val defaultFilters = FilterOptions.getDefault()
        logger.debug("resetFilters", "Resetting filters to current month: ${defaultFilters.dateFrom} to ${defaultFilters.dateTo}")

        _uiState.value = _uiState.value.copy(
            currentFilterOptions = defaultFilters,
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

            logger.debug("applyFiltersAndSort","Applied filter tab ${currentState.currentFilterTab.displayName}: ${currentState.allMessages.size} -> ${filtered.size} items")
            
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

            // Apply date filters using actual Date objects (parse String dates from filter UI)
            val beforeDateFilter = filtered.size
            filters.dateFrom?.let { dateFromStr ->
                try {
                    // Try with time first (yyyy-MM-dd HH:mm:ss), fallback to date only (yyyy-MM-dd)
                    val dateFrom = try {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateFromStr)
                    } catch (e: Exception) {
                        val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateFromStr)
                        // Set to start of day (00:00:00) to include all transactions from that date
                        if (parsedDate != null) {
                            val calendar = Calendar.getInstance()
                            calendar.time = parsedDate
                            calendar.set(Calendar.HOUR_OF_DAY, 0)
                            calendar.set(Calendar.MINUTE, 0)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            calendar.time
                        } else {
                            parsedDate
                        }
                    }
                    if (dateFrom != null) {
                        val beforeFilter = filtered.size
                        filtered = filtered.filter { it.actualDate >= dateFrom }
                        logger.debug("applyFiltersAndSort","✅ Applied dateFrom filter: $dateFromStr (parsed: $dateFrom), ${beforeFilter} -> ${filtered.size} items")
                    }
                } catch (e: Exception) {
                    logger.error("applyFiltersAndSort","❌ Invalid dateFrom format: $dateFromStr - ${e.message}",e)
                }
            }
            filters.dateTo?.let { dateToStr ->
                try {
                    // Try with time first (yyyy-MM-dd HH:mm:ss), fallback to date only (yyyy-MM-dd)
                    val dateTo = try {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateToStr)
                    } catch (e: Exception) {
                        val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateToStr)
                        // Set to end of day (23:59:59) to include all transactions on that date
                        if (parsedDate != null) {
                            val calendar = Calendar.getInstance()
                            calendar.time = parsedDate
                            calendar.set(Calendar.HOUR_OF_DAY, 23)
                            calendar.set(Calendar.MINUTE, 59)
                            calendar.set(Calendar.SECOND, 59)
                            calendar.set(Calendar.MILLISECOND, 999)
                            calendar.time
                        } else {
                            parsedDate
                        }
                    }
                    if (dateTo != null) {
                        val beforeFilter = filtered.size
                        filtered = filtered.filter { it.actualDate <= dateTo }
                        logger.debug("applyFiltersAndSort","✅ Applied dateTo filter: $dateToStr (parsed: $dateTo), ${beforeFilter} -> ${filtered.size} items")
                    }
                } catch (e: Exception) {
                    logger.error("applyFiltersAndSort","❌ Invalid dateTo format: $dateToStr - ${e.message}",e)
                }
            }

            logger.debug("applyFiltersAndSort","Applied date filters - From: ${filters.dateFrom}, To: ${filters.dateTo}, Filtered: ${filtered.size} items")
            
            // Skip individual transaction sorting - let groupTransactionsByMerchant handle the sorting
            // This ensures that the final merchant group sorting is what determines the display order
            val sortOption = currentState.currentSortOption
            logger.debug("applyFiltersAndSort","Skipping individual transaction sorting - groups will be sorted by ${sortOption.field}")
            
            // Group by merchant - this will handle the proper sorting
            val groupedMessages = groupTransactionsByMerchant(filtered, sortOption)

            // Update state - set isLoading to false now that groups are ready
            _uiState.value = currentState.copy(
                isLoading = false,
                filteredMessages = filtered,
                groupedMessages = groupedMessages
            )

            logger.debug("applyFiltersAndSort","Applied filters and sort: ${filtered.size} items, ${groupedMessages.size} groups")
        }
    }
    
    /**
     * Group transactions by merchant for display
     * CRITICAL FIX: Gets category from merchants table instead of transactions table
     * to ensure consistency with Category Manager
     */
    private suspend fun groupTransactionsByMerchant(transactions: List<MessageItem>, sortOption: SortOption): List<MerchantGroup> {
        // 🔧 BUG FIX: Filter out transactions with empty/invalid merchant names
        val invalidTransactions = transactions.filter { transaction ->
            transaction.merchant.isBlank() || transaction.merchant == "."
        }

        if (invalidTransactions.isNotEmpty()) {
            logger.warn("groupTransactionsByMerchant","⚠️ Found ${invalidTransactions.size} transactions with invalid merchant names:")
            invalidTransactions.take(5).forEach { tx ->
                logger.warn("groupTransactionsByMerchant","  - merchant='${tx.merchant}', rawMerchant='${tx.rawMerchant}', amount=${tx.amount}")
            }
        }

        val validTransactions = transactions.filter { transaction ->
            transaction.merchant.isNotBlank() && transaction.merchant != "."
        }

        if (validTransactions.size < transactions.size) {
            logger.warn("groupTransactionsByMerchant","Filtered out ${transactions.size - validTransactions.size} transactions with empty merchant names")
        }

        // DEBUG: Log merchant names to detect duplicates
        val merchantNames = validTransactions.map { it.merchant }.distinct()
        logger.debug("groupTransactionsByMerchant", "Unique merchant names: ${merchantNames.size}")

        // DEBUG: Check for suspected duplicates (case-insensitive, whitespace-trimmed)
        val suspectedDuplicates = merchantNames.groupBy { it.trim().uppercase() }
            .filter { it.value.size > 1 }
        if (suspectedDuplicates.isNotEmpty()) {
            logger.warn("groupTransactionsByMerchant", "SUSPECTED DUPLICATES: $suspectedDuplicates")
        }

        val groups = validTransactions
            .groupBy { it.merchant.trim() }
            .map { (displayName, merchantTransactions) ->
                val sortedTransactions = merchantTransactions.sortedByDescending { transaction ->
                    transaction.actualDate.time
                }
                val totalAmount = merchantTransactions.sumOf { it.amount }
                val category = merchantTransactions.firstOrNull()?.category ?: "Other"
                val categoryColor = merchantTransactions.firstOrNull()?.categoryColor ?: "#9e9e9e"

                val latestTransactionDate = sortedTransactions.firstOrNull()?.actualDate?.time ?: 0L
                
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
        
        // Load saved inclusion states from SharedPreferences
        val groupsWithInclusionStates = loadGroupInclusionStates(groups)

        // Sort groups based on current sort option
        logger.info("groupTransactionsByMerchant", "📊 Sorting ${groupsWithInclusionStates.size} merchant groups by field=${sortOption.field}, ascending=${sortOption.ascending}")

        // Log sample of unsorted groups to debug
        logger.debug("groupTransactionsByMerchant", "BEFORE SORT - Sample groups: ${
            groupsWithInclusionStates.take(5).map {
                val dateStr = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(it.latestTransactionDate))
                "${it.merchantName}=$dateStr (${it.latestTransactionDate})"
            }.joinToString(", ")
        }")

        val sortedGroups = when (sortOption.field) {
            "date" -> {
                if (sortOption.ascending) {
                    groupsWithInclusionStates.sortedWith(
                        compareBy<MerchantGroup> { it.latestTransactionDate }
                            .thenBy { it.merchantName } // Secondary sort for same-day transactions
                    )
                } else {
                    groupsWithInclusionStates.sortedWith(
                        compareByDescending<MerchantGroup> { it.latestTransactionDate }
                            .thenBy { it.merchantName } // Secondary sort for same-day transactions
                    )
                }
            }
            "amount" -> {
                if (sortOption.ascending) {
                    groupsWithInclusionStates.sortedBy { it.totalAmount }
                } else {
                    groupsWithInclusionStates.sortedByDescending { it.totalAmount }
                }
            }
            "merchant" -> {
                if (sortOption.ascending) {
                    groupsWithInclusionStates.sortedBy { it.merchantName.lowercase() }
                } else {
                    groupsWithInclusionStates.sortedByDescending { it.merchantName.lowercase() }
                }
            }
            "bank" -> {
                if (sortOption.ascending) {
                    groupsWithInclusionStates.sortedBy { it.primaryBankName.lowercase() }
                } else {
                    groupsWithInclusionStates.sortedByDescending { it.primaryBankName.lowercase() }
                }
            }
            "confidence" -> {
                if (sortOption.ascending) {
                    groupsWithInclusionStates.sortedBy { it.averageConfidence }
                } else {
                    groupsWithInclusionStates.sortedByDescending { it.averageConfidence }
                }
            }
            else -> groupsWithInclusionStates.sortedByDescending { it.latestTransactionDate }
        }

        // Log the first 5 groups to verify sorting
        logger.info("groupTransactionsByMerchant", "✅ AFTER SORT - First 5 groups: ${
            sortedGroups.take(5).map {
                val dateStr = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(it.latestTransactionDate))
                "${it.merchantName}=$dateStr (${it.latestTransactionDate})"
            }.joinToString(", ")
        }")

        return sortedGroups
    }
    
    /**
     * Toggle group inclusion in calculations with proper database sync and immediate data refresh
     */
    private fun toggleGroupInclusion(merchantName: String, isIncluded: Boolean) {
        try {
            logger.debug("toggleGroupInclusion","Toggling group inclusion for '$merchantName': $isIncluded")
            
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

                        // 3. Trigger data refresh to update summary calculations in other screens
                        notifyDataChanged()

                        // 4. CRITICAL: Reload messages to refresh allMessages cache
                        // This ensures EXCLUDED tab filtering works with current database state
                        loadMessages()
                    }

                    logger.debug("toggleGroupInclusion","Successfully toggled group inclusion for '$merchantName'")
                    
                } catch (e: Exception) {
                    logger.error("toggleGroupInclusion","Error in debounced toggle for '$merchantName'",e)
                    handleError("Failed to update merchant exclusion settings. Please try again.")
                }
            }
            
        } catch (e: Exception) {
            logger.error("toggleGroupInclusion","Error toggling group inclusion for '$merchantName'",e)
            handleError("Failed to update merchant exclusion settings. Please try again.")
        }
    }
    
    /**
     * Update merchant group display name and category
     */
    private fun updateMerchantGroup(merchantName: String, newDisplayName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                logger.debug("updateMerchantGroup","Updating merchant group: $merchantName -> $newDisplayName, category: $newCategory")
                
                // Find all transactions in this group to get their original merchant names
                val originalMerchantNames = mutableSetOf<String>()
                
                val existingOriginalNames = merchantAliasManager.getMerchantsByDisplayName(merchantName)
                if (existingOriginalNames.isNotEmpty()) {
                    originalMerchantNames.addAll(existingOriginalNames)
                } else {
                    // FIXED: Query database instead of using cached UI state
                    val transactions = expenseRepository.getTransactionsByMerchant(merchantName)
                    logger.debug("updateMerchantGroup", "Loaded ${transactions.size} transactions from database for merchant: $merchantName")

                    transactions.forEach { transaction ->
                        // Use rawMerchant from database transaction
                        val originalName = transaction.rawMerchant
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

                logger.debug("updateMerchantGroup","Merchant group update completed successfully")
                
            } catch (e: Exception) {
                logger.error("updateMerchantGroup","Error updating merchant group",e)
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
                logger.error("resetMerchantGroupToOriginal","Error resetting merchant group",e)
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
                logger.error("updateCategoryForMerchant" ,"Error updating category",e)
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
    
    private suspend fun loadGroupInclusionStates(groups: List<MerchantGroup>): List<MerchantGroup> {
        try {
            logger.debug("loadGroupInclusionStates", "Loading inclusion states from database for ${groups.size} groups")

            // FIXED: Query database instead of SharedPreferences for merchant exclusion states
            val excludedMerchants = expenseRepository.getExcludedMerchants().map { it.normalizedName }.toSet()

            logger.debug("loadGroupInclusionStates", "Found ${excludedMerchants.size} excluded merchants in database")

            val updatedGroups = groups.map { group ->
                // FIX: Use rawMerchant from transactions instead of display alias
                // group.merchantName = display alias (e.g., "ABC12345")
                // group.transactions.first().rawMerchant = original merchant name (e.g., "M S MUNCHZESTERFOODS BANGALORE LLP")
                val normalizedMerchantName = if (group.transactions.isNotEmpty()) {
                    // Use the raw merchant name from the first transaction
                    normalizeMerchantName(group.transactions.first().rawMerchant)
                } else {
                    // Fallback to display name if no transactions (shouldn't happen)
                    normalizeMerchantName(group.merchantName)
                }

                val isIncluded = !excludedMerchants.contains(normalizedMerchantName)

                logger.debug("loadGroupInclusionStates",
                    "[CHECK] Group '${group.merchantName}' -> rawMerchant '${if (group.transactions.isNotEmpty()) group.transactions.first().rawMerchant else "N/A"}' -> normalized '$normalizedMerchantName' -> isIncluded: $isIncluded")

                group.copy(isIncludedInCalculations = isIncluded)
            }

            val includedCount = updatedGroups.count { it.isIncludedInCalculations }
            logger.debug("loadGroupInclusionStates", "Loaded states: $includedCount included, ${groups.size - includedCount} excluded")

            return updatedGroups

        } catch (e: Exception) {
            logger.error("loadGroupInclusionStates", "Error loading inclusion states from database", e)
            // Return original groups with default inclusion on error
            return groups.map { it.copy(isIncludedInCalculations = true) }
        }
    }

    private fun saveGroupInclusionStates(groups: List<MerchantGroup>) {
        viewModelScope.launch {
            try {
                logger.debug("saveGroupInclusionStates", "Saving inclusion states to database for ${groups.size} groups")

                // FIXED: Save to database instead of SharedPreferences
                var savedCount = 0
                groups.forEach { group ->
                    try {
                        // isExcluded in database = NOT isIncludedInCalculations in UI
                        val isExcluded = !group.isIncludedInCalculations
                        updateMerchantExclusionInDatabase(group.merchantName, isExcluded)
                        savedCount++
                    } catch (e: Exception) {
                        logger.warn("saveGroupInclusionStates", "Failed to save exclusion state for '${group.merchantName}': ${e.message}")
                    }
                }

                logger.debug("saveGroupInclusionStates", "Successfully saved $savedCount exclusion states to database")

            } catch (e: Exception) {
                logger.error("saveGroupInclusionStates", "Error saving group exclusion states to database", e)
                // Don't show error to user - it's not critical
                // The UI state is already updated, this is just persistence
            }
        }
    }
    
    /**
     * Update merchant exclusion status in database
     */
    private suspend fun updateMerchantExclusionInDatabase(merchantName: String, isExcluded: Boolean) {
        try {
            logger.debug("updateMerchantExclusionInDatabase","Updating database exclusion for '$merchantName': $isExcluded")
            
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

            logger.info("updateMerchantExclusionInDatabase","Successfully updated database exclusion for '$merchantName'")
            
        } catch (e: Exception) {
            logger.error("updateMerchantExclusionInDatabase","Failed to update database exclusion for '$merchantName'",e)
        }
    }
    
    /**
     * Notify other components that data has changed (triggers dashboard refresh)
     */
    private fun notifyDataChanged() {
        try {
            // Broadcast data change to trigger dashboard refresh
            val intent = android.content.Intent("com.smartexpenseai.app.DATA_CHANGED")
            context.sendBroadcast(intent)

            logger.debug("notifyDataChanged","Broadcast sent to refresh dependent screens")
            
        } catch (e: Exception) {
            logger.error("notifyDataChanged","Error broadcasting data change",e)
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
            logger.debug("invalidateMerchantAliasCache","ViewModel MerchantAliasManager cache invalidated")
        } catch (e: Exception) {
            logger.error("invalidateMerchantAliasCache","Error invalidating ViewModel merchant alias cache",e)
        }
    }

    
    /**
     * Force refresh data after external changes (called from Fragment)
     */
    fun refreshDataAfterExternalChanges() {
        logger.debug("invalidateMerchantAliasCache","Refreshing data after external changes")
        loadMessages()
    }
}
