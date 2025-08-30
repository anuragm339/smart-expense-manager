package com.expensemanager.app.ui.messages

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.services.SMSParsingService
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val smsParsingService: SMSParsingService
) : ViewModel() {
    
    companion object {
        private const val TAG = "MessagesViewModel"
    }
    
    // Utility classes
    private val categoryManager by lazy { CategoryManager(context) }
    private val merchantAliasManager by lazy { MerchantAliasManager(context) }
    // SMS parsing is now handled by the injected SMSParsingService
    
    // Private mutable state
    private val _uiState = MutableStateFlow(MessagesUIState())
    
    // Public immutable state
    val uiState: StateFlow<MessagesUIState> = _uiState.asStateFlow()
    
    init {
        Log.d(TAG, "ViewModel initialized, loading messages data...")
        loadMessages()
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: MessagesUIEvent) {
        Log.d(TAG, "Handling event: $event")
        
        when (event) {
            is MessagesUIEvent.LoadMessages -> loadMessages()
            is MessagesUIEvent.RefreshMessages -> refreshMessages()
            is MessagesUIEvent.ResyncSMS -> resyncSMSMessages()
            is MessagesUIEvent.TestSMSScanning -> testSMSScanning()
            is MessagesUIEvent.Search -> searchMessages(event.query)
            is MessagesUIEvent.ApplySort -> applySortOption(event.sortOption)
            is MessagesUIEvent.ApplyFilter -> applyFilterOptions(event.filterOptions)
            is MessagesUIEvent.ResetFilters -> resetFilters()
            is MessagesUIEvent.ToggleGroupInclusion -> toggleGroupInclusion(event.merchantName, event.isIncluded)
            is MessagesUIEvent.UpdateMerchantGroup -> updateMerchantGroup(event.merchantName, event.newDisplayName, event.newCategory)
            is MessagesUIEvent.ResetMerchantGroup -> resetMerchantGroupToOriginal(event.merchantName)
            is MessagesUIEvent.UpdateCategoryForMerchant -> updateCategoryForMerchant(event.merchantName, event.newCategory)
            is MessagesUIEvent.ClearError -> clearError()
        }
    }
    
    /**
     * Load messages from database and SMS fallback
     */
    private fun loadMessages() {
        Log.d(TAG, "Starting messages load...")
        
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            try {
                // Get current date range (this month for now)
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endDate = calendar.time
                
                // Load transactions from SQLite database first
                val dbTransactions = expenseRepository.getTransactionsByDateRange(startDate, endDate)
                Log.d(TAG, "Found ${dbTransactions.size} transactions in database")
                
                if (dbTransactions.isNotEmpty()) {
                    // Convert to MessageItem format
                    val messageItems = dbTransactions.mapNotNull { transaction ->
                        try {
                            val merchantWithCategory = expenseRepository.getMerchantWithCategory(transaction.normalizedMerchant)
                            val category = merchantWithCategory?.category_name ?: "Other"
                            val categoryColor = merchantWithCategory?.category_color ?: "#888888"
                            
                            // CRITICAL FIX: Apply merchant aliases when loading from database
                            val displayName = merchantAliasManager.getDisplayName(transaction.rawMerchant)
                            val aliasCategory = merchantAliasManager.getMerchantCategory(transaction.rawMerchant)
                            val aliasCategoryColor = merchantAliasManager.getMerchantCategoryColor(transaction.rawMerchant)
                            
                            Log.d(TAG, "Database load: rawMerchant='${transaction.rawMerchant}' -> displayName='$displayName', category='$aliasCategory'")
                            
                            MessageItem(
                                amount = transaction.amount,
                                merchant = displayName, // Use alias display name, not raw merchant
                                bankName = transaction.bankName,
                                category = aliasCategory, // Use alias category
                                categoryColor = aliasCategoryColor, // Use alias category color
                                confidence = (transaction.confidenceScore * 100).toInt(),
                                dateTime = formatDate(transaction.transactionDate),
                                rawSMS = transaction.rawSmsBody
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error converting transaction: ${e.message}")
                            null
                        }
                    }.distinctBy { 
                        // Remove duplicates based on merchant, amount, and date
                        "${it.merchant}_${it.amount}_${it.dateTime}_${it.bankName}"
                    }
                    
                    updateMessagesState(messageItems)
                } else {
                    // No database data found - fallback to SMS scanning
                    Log.d(TAG, "No database transactions found, falling back to SMS scanning...")
                    loadFromSMSFallback()
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SMS permission denied", e)
                handleError("SMS permission required to read transaction messages")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages", e)
                handleError("Error reading messages: ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    /**
     * Fallback to SMS scanning if no database data - now using unified SMSParsingService
     */
    private fun loadFromSMSFallback() {
        viewModelScope.launch {
            try {
                val historicalTransactions = smsParsingService.scanHistoricalSMS { current, total, status ->
                    Log.d(TAG, "[UNIFIED] SMS Fallback progress: $status ($current/$total)")
                }
                Log.d(TAG, "[UNIFIED] SMS Fallback: Found ${historicalTransactions.size} transactions")
                
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
                            rawSMS = transaction.rawSMS
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
                Log.e(TAG, "[UNIFIED] Error in SMS fallback", e)
                handleError("Error scanning SMS: ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    /**
     * Update messages state with loaded data
     */
    private fun updateMessagesState(messageItems: List<MessageItem>) {
        val currentState = _uiState.value
        
        _uiState.value = currentState.copy(
            isLoading = false,
            allMessages = messageItems,
            filteredMessages = messageItems,
            isEmpty = messageItems.isEmpty()
        )
        
        // Apply current filters and sorting
        applyFiltersAndSort()
        
        Log.d(TAG, "Messages state updated with ${messageItems.size} items")
    }
    
    /**
     * Refresh messages (pull-to-refresh)
     */
    private fun refreshMessages() {
        Log.d(TAG, "Refreshing messages...")
        
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
                Log.e(TAG, "Error refreshing messages", e)
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
        Log.d(TAG, "[UNIFIED] Starting SMS resync...")
        
        _uiState.value = _uiState.value.copy(
            isSyncingSMS = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            try {
                val historicalTransactions = smsParsingService.scanHistoricalSMS { current, total, status ->
                    Log.d(TAG, "[UNIFIED] SMS Resync progress: $status ($current/$total)")
                }
                Log.d(TAG, "[UNIFIED] SMS resync found ${historicalTransactions.size} transactions")
                
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
                            rawSMS = transaction.rawSMS
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
                Log.w(TAG, "[UNIFIED] SMS permission denied during resync", e)
                _uiState.value = _uiState.value.copy(
                    isSyncingSMS = false,
                    hasError = true,
                    error = "SMS permission required for resync"
                )
            } catch (e: Exception) {
                Log.e(TAG, "[UNIFIED] Error during SMS resync", e)
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
        Log.d(TAG, "[UNIFIED] Starting SMS scanning test...")
        
        viewModelScope.launch {
            try {
                val transactions = smsParsingService.scanHistoricalSMS { current, total, status ->
                    Log.d(TAG, "[UNIFIED] SMS Test progress: $status ($current/$total)")
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
                
                Log.d(TAG, "[UNIFIED] SMS Test: Found ${transactions.size} transactions")
                
            } catch (e: SecurityException) {
                _uiState.value = _uiState.value.copy(
                    hasError = true,
                    error = "SMS permission is required to test SMS scanning"
                )
            } catch (e: Exception) {
                Log.e(TAG, "[UNIFIED] SMS test error", e)
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
     * Apply filter options
     */
    private fun applyFilterOptions(filterOptions: FilterOptions) {
        _uiState.value = _uiState.value.copy(
            currentFilterOptions = filterOptions
        )
        applyFiltersAndSort()
    }
    
    /**
     * Reset all filters
     */
    private fun resetFilters() {
        _uiState.value = _uiState.value.copy(
            currentFilterOptions = FilterOptions(),
            searchQuery = ""
        )
        applyFiltersAndSort()
    }
    
    /**
     * Apply filters and sorting to messages
     */
    private fun applyFiltersAndSort() {
        val currentState = _uiState.value
        var filtered = currentState.allMessages.toList()
        
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
        
        // Apply date filters (simplified)
        filters.dateFrom?.let { dateFrom ->
            filtered = filtered.filter { it.dateTime >= dateFrom }
        }
        filters.dateTo?.let { dateTo ->
            filtered = filtered.filter { it.dateTime <= dateTo }
        }
        
        // Apply sorting
        val sortOption = currentState.currentSortOption
        filtered = when (sortOption.field) {
            "date" -> {
                if (sortOption.ascending) {
                    filtered.sortedBy { getDateSortOrderReverse(it.dateTime) }
                } else {
                    filtered.sortedByDescending { getDateSortOrderReverse(it.dateTime) }
                }
            }
            "amount" -> {
                if (sortOption.ascending) {
                    filtered.sortedBy { it.amount }
                } else {
                    filtered.sortedByDescending { it.amount }
                }
            }
            "merchant" -> {
                if (sortOption.ascending) {
                    filtered.sortedBy { it.merchant.lowercase() }
                } else {
                    filtered.sortedByDescending { it.merchant.lowercase() }
                }
            }
            "bank" -> {
                if (sortOption.ascending) {
                    filtered.sortedBy { it.bankName.lowercase() }
                } else {
                    filtered.sortedByDescending { it.bankName.lowercase() }
                }
            }
            "confidence" -> {
                if (sortOption.ascending) {
                    filtered.sortedBy { it.confidence }
                } else {
                    filtered.sortedByDescending { it.confidence }
                }
            }
            else -> filtered
        }
        
        // Group by merchant
        val groupedMessages = groupTransactionsByMerchant(filtered, sortOption)
        
        // Update state
        _uiState.value = currentState.copy(
            filteredMessages = filtered,
            groupedMessages = groupedMessages
        )
        
        Log.d(TAG, "Applied filters and sort: ${filtered.size} items, ${groupedMessages.size} groups")
    }
    
    /**
     * Group transactions by merchant for display
     */
    private fun groupTransactionsByMerchant(transactions: List<MessageItem>, sortOption: SortOption): List<MerchantGroup> {
        val groups = transactions
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
     * Toggle group inclusion in calculations
     */
    private fun toggleGroupInclusion(merchantName: String, isIncluded: Boolean) {
        val currentGroups = _uiState.value.groupedMessages
        val updatedGroups = currentGroups.map { group ->
            if (group.merchantName == merchantName) {
                group.copy(isIncludedInCalculations = isIncluded)
            } else {
                group
            }
        }
        
        _uiState.value = _uiState.value.copy(
            groupedMessages = updatedGroups
        )
        
        // Save inclusion states
        saveGroupInclusionStates(updatedGroups)
    }
    
    /**
     * Update merchant group display name and category
     */
    private fun updateMerchantGroup(merchantName: String, newDisplayName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Updating merchant group: $merchantName -> $newDisplayName, category: $newCategory")
                
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
                
                Log.d(TAG, "Merchant group update completed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating merchant group", e)
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
                Log.e(TAG, "Error resetting merchant group", e)
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
                Log.e(TAG, "Error updating category", e)
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
            else -> 0L
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
        val prefs = context.getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val inclusionStates = org.json.JSONObject()
        groups.forEach { group ->
            inclusionStates.put(group.merchantName, group.isIncludedInCalculations)
        }
        
        editor.putString("group_inclusion_states", inclusionStates.toString())
        editor.apply()
    }
}