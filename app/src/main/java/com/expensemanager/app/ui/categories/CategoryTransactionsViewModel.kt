package com.expensemanager.app.ui.categories

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.ui.messages.MessageItem
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
 * ViewModel for CategoryTransactions screen
 * Manages category transaction UI state and handles user interactions
 */
@HiltViewModel
class CategoryTransactionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "CategoryTransactionsViewModel"
    }
    
    // Private mutable state
    private val _uiState = MutableStateFlow(CategoryTransactionsUIState())
    
    // Public immutable state
    val uiState: StateFlow<CategoryTransactionsUIState> = _uiState.asStateFlow()
    
    // Manager instances
    private val categoryManager = CategoryManager(context)
    private val merchantAliasManager = MerchantAliasManager(context)
    
    init {
        Log.d(TAG, "ViewModel initialized")
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: CategoryTransactionsUIEvent) {
        Log.d(TAG, "Handling event: $event")
        
        when (event) {
            is CategoryTransactionsUIEvent.LoadTransactions -> loadTransactions()
            is CategoryTransactionsUIEvent.Refresh -> refreshTransactions()
            is CategoryTransactionsUIEvent.SetCategoryName -> setCategoryName(event.categoryName)
            is CategoryTransactionsUIEvent.ChangeSortOption -> changeSortOption(event.sortOption)
            is CategoryTransactionsUIEvent.ChangeFilterOption -> changeFilterOption(event.filterOption)
            is CategoryTransactionsUIEvent.UpdateTransactionCategory -> updateTransactionCategory(event.messageItem, event.newCategory)
            is CategoryTransactionsUIEvent.ShowCategoryEditDialog -> showCategoryEditDialog(event.messageItem)
            is CategoryTransactionsUIEvent.ClearError -> clearError()
        }
    }
    
    /**
     * Set category name and load related data
     */
    private fun setCategoryName(categoryName: String) {
        Log.d(TAG, "Setting category name: $categoryName")
        
        val categoryColor = categoryManager.getCategoryColor(categoryName)
        
        _uiState.value = _uiState.value.copy(
            categoryName = categoryName,
            categoryColor = categoryColor
        )
        
        // Load transactions for this category
        loadTransactions()
    }
    
    /**
     * Initial loading of category transactions
     */
    private fun loadTransactions() {
        Log.d(TAG, "Loading transactions for category: ${_uiState.value.categoryName}")
        
        _uiState.value = _uiState.value.copy(
            isInitialLoading = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            try {
                val categoryName = _uiState.value.categoryName
                val transactions = loadCategoryTransactionsFromRepository(categoryName)
                
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    allTransactions = transactions,
                    lastRefreshTime = System.currentTimeMillis()
                )
                
                // Apply current filter and sort
                applyFilterAndSort()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading transactions", e)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Refresh category transactions
     */
    private fun refreshTransactions() {
        Log.d(TAG, "Refreshing transactions...")
        
        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            try {
                val categoryName = _uiState.value.categoryName
                val transactions = loadCategoryTransactionsFromRepository(categoryName)
                
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    allTransactions = transactions,
                    lastRefreshTime = System.currentTimeMillis()
                )
                
                // Apply current filter and sort
                applyFilterAndSort()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing transactions", e)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Change sort option and reapply
     */
    private fun changeSortOption(sortOption: String) {
        Log.d(TAG, "Changing sort option to: $sortOption")
        
        _uiState.value = _uiState.value.copy(
            currentSortOption = sortOption
        )
        
        applyFilterAndSort()
    }
    
    /**
     * Change filter option and reapply
     */
    private fun changeFilterOption(filterOption: String) {
        Log.d(TAG, "Changing filter option to: $filterOption")
        
        _uiState.value = _uiState.value.copy(
            currentFilterOption = filterOption
        )
        
        applyFilterAndSort()
    }
    
    /**
     * Update transaction category
     */
    private fun updateTransactionCategory(messageItem: MessageItem, newCategory: String) {
        Log.d(TAG, "Updating merchant ${messageItem.merchant} from ${messageItem.category} to $newCategory")
        
        _uiState.value = _uiState.value.copy(isUpdatingCategory = true)
        
        viewModelScope.launch {
            try {
                // Use the same normalization as MerchantAliasManager and DataMigrationHelper
                val normalizedMerchant = messageItem.merchant.uppercase()
                    .replace(Regex("[*#@\\-_]+.*"), "") // Remove suffixes after special chars
                    .replace(Regex("\\s+"), " ") // Normalize spaces
                    .trim()
                
                // Find the merchant in database
                val merchant = repository.getMerchantByNormalizedName(normalizedMerchant)
                if (merchant != null) {
                    // Get or create the new category
                    var newCategoryEntity = repository.getCategoryByName(newCategory)
                    if (newCategoryEntity == null) {
                        // Category doesn't exist in database, create it
                        Log.d(TAG, "Creating new category: $newCategory")
                        
                        val categoryToCreate = com.expensemanager.app.data.entities.CategoryEntity(
                            name = newCategory,
                            emoji = getCategoryEmoji(newCategory),
                            color = getRandomCategoryColor(),
                            isSystem = false,
                            displayOrder = 100,
                            createdAt = Date()
                        )
                        
                        val categoryId = repository.insertCategory(categoryToCreate)
                        newCategoryEntity = repository.getCategoryById(categoryId)
                    }
                    
                    if (newCategoryEntity != null) {
                        // Update merchant's category
                        val updatedMerchant = merchant.copy(categoryId = newCategoryEntity.id)
                        repository.updateMerchant(updatedMerchant)
                        
                        // FIXED: Update MerchantAliasManager to ensure consistent behavior across all screens
                        val currentDisplayName = merchantAliasManager.getDisplayName(messageItem.merchant)
                        val aliasUpdateSuccess = merchantAliasManager.setMerchantAlias(
                            originalName = messageItem.merchant,
                            displayName = currentDisplayName, // Keep current display name
                            category = newCategory // But update the category
                        )
                        
                        if (!aliasUpdateSuccess) {
                            Log.w(TAG, "Failed to update merchant alias: ${messageItem.merchant}")
                        }
                        
                        // Update CategoryManager for backwards compatibility
                        categoryManager.updateCategory(messageItem.merchant, newCategory)
                        
                        // FIXED: Send broadcast to notify Dashboard and other screens about category change
                        val intent = Intent("com.expensemanager.CATEGORY_UPDATED").apply {
                            putExtra("merchant", messageItem.merchant)
                            putExtra("category", newCategory)
                        }
                        context.sendBroadcast(intent)
                        Log.d(TAG, "Category updated: ${messageItem.merchant} -> $newCategory")
                        
                        _uiState.value = _uiState.value.copy(isUpdatingCategory = false)
                        
                        // Refresh data if merchant moved to different category or update current list
                        val currentCategoryName = _uiState.value.categoryName
                        if (newCategory != currentCategoryName) {
                            // Merchant moved to different category, refresh list
                            refreshTransactions()
                        } else {
                            // Update the item in the current list
                            updateTransactionInList(messageItem, newCategory)
                        }
                        
                    }
                } else {
                    throw Exception("Merchant '$normalizedMerchant' not found in database")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating transaction category", e)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Show category edit dialog (handled by fragment)
     */
    private fun showCategoryEditDialog(messageItem: MessageItem) {
        Log.d(TAG, "Show category edit dialog for: ${messageItem.merchant}")
        // This is handled by the fragment UI
    }
    
    /**
     * Get all available categories
     */
    fun getAllCategories(): List<String> {
        return try {
            // Use runBlocking temporarily for synchronous access
            kotlinx.coroutines.runBlocking {
                val dbCategories = repository.getAllCategoriesSync()
                if (dbCategories.isNotEmpty()) {
                    dbCategories.map { it.name }
                } else {
                    // Fallback to default categories
                    listOf(
                        "Food & Dining", "Transportation", "Groceries",
                        "Healthcare", "Entertainment", "Shopping",
                        "Utilities", "Other"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading categories", e)
            listOf("Food & Dining", "Transportation", "Other")
        }
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
     * Load category transactions from repository
     */
    private suspend fun loadCategoryTransactionsFromRepository(categoryName: String): List<MessageItem> {
        return try {
            // Get current date range (this month by default)
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
            
            // Load transactions from repository
            val allDbTransactions = repository.getTransactionsByDateRange(startDate, endDate)
            Log.d(TAG, "Found ${allDbTransactions.size} total transactions")
            
            // Filter transactions by category
            val categoryTransactions = allDbTransactions.mapNotNull { transaction ->
                val merchantWithCategory = repository.getMerchantWithCategory(transaction.normalizedMerchant)
                val transactionCategory = merchantWithCategory?.category_name ?: "Other"
                
                if (transactionCategory == categoryName) {
                    MessageItem(
                        amount = transaction.amount,
                        merchant = merchantAliasManager.getDisplayName(transaction.rawMerchant), // Apply alias lookup
                        bankName = transaction.bankName,
                        category = transactionCategory,
                        categoryColor = merchantWithCategory?.category_color ?: "#888888",
                        confidence = (transaction.confidenceScore * 100).toInt(),
                        dateTime = formatDate(transaction.transactionDate),
                        rawSMS = transaction.rawSmsBody,
                        isDebit = transaction.isDebit
                    )
                } else null
            }
            
            Log.d(TAG, "Filtered to ${categoryTransactions.size} transactions for $categoryName")
            categoryTransactions
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading transactions from repository", e)
            emptyList()
        }
    }
    
    /**
     * Apply current filter and sort options
     */
    private fun applyFilterAndSort() {
        val currentState = _uiState.value
        val allTransactions = currentState.allTransactions
        
        if (allTransactions.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                transactions = emptyList(),
                totalAmount = 0.0,
                transactionCount = 0,
                isEmpty = true
            )
            return
        }
        
        // Apply date filtering
        val filteredTransactions = applyDateFilter(allTransactions, currentState.currentFilterOption)
        
        // Apply sorting
        val sortedTransactions = applySorting(filteredTransactions, currentState.currentSortOption)
        
        // Update state
        val totalAmount = sortedTransactions.sumOf { it.amount }
        
        _uiState.value = _uiState.value.copy(
            transactions = sortedTransactions,
            totalAmount = totalAmount,
            transactionCount = sortedTransactions.size,
            isEmpty = sortedTransactions.isEmpty()
        )
    }
    
    /**
     * Apply date filtering
     */
    private fun applyDateFilter(transactions: List<MessageItem>, filterOption: String): List<MessageItem> {
        return when (filterOption) {
            "Today" -> {
                val today = Calendar.getInstance()
                transactions.filter { transaction ->
                    val transactionDate = parseTransactionDate(transaction.dateTime)
                    isSameDay(transactionDate, today.time)
                }
            }
            "Yesterday" -> {
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
                transactions.filter { transaction ->
                    val transactionDate = parseTransactionDate(transaction.dateTime)
                    isSameDay(transactionDate, yesterday.time)
                }
            }
            "This Week" -> {
                val weekStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                transactions.filter { transaction ->
                    val transactionDate = parseTransactionDate(transaction.dateTime)
                    transactionDate.after(weekStart.time)
                }
            }
            "Last Month" -> {
                val lastMonthStart = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val lastMonthEnd = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                transactions.filter { transaction ->
                    val transactionDate = parseTransactionDate(transaction.dateTime)
                    transactionDate.after(lastMonthStart.time) && transactionDate.before(lastMonthEnd.time)
                }
            }
            "All Time" -> transactions
            else -> transactions // "This Month" is default
        }
    }
    
    /**
     * Apply sorting
     */
    private fun applySorting(transactions: List<MessageItem>, sortOption: String): List<MessageItem> {
        return when (sortOption) {
            "Oldest First" -> transactions.sortedBy { getDateSortOrder(it.dateTime) }
            "Highest Amount" -> transactions.sortedByDescending { it.amount }
            "Lowest Amount" -> transactions.sortedBy { it.amount }
            else -> transactions.sortedByDescending { getDateSortOrder(it.dateTime) } // "Newest First" is default
        }
    }
    
    /**
     * Update transaction in current list
     */
    private fun updateTransactionInList(messageItem: MessageItem, newCategory: String) {
        val currentTransactions = _uiState.value.transactions
        val updatedTransactions = currentTransactions.map { item ->
            if (item.merchant == messageItem.merchant) {
                item.copy(
                    category = newCategory,
                    categoryColor = categoryManager.getCategoryColor(newCategory)
                )
            } else item
        }
        
        _uiState.value = _uiState.value.copy(transactions = updatedTransactions)
    }
    
    /**
     * Parse transaction date from string
     */
    private fun parseTransactionDate(dateTimeString: String): Date {
        return when {
            dateTimeString.contains("hour") || dateTimeString.contains("Just now") -> Date()
            dateTimeString.contains("Yesterday") -> {
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.time
            }
            dateTimeString.contains("days ago") -> {
                val days = dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -days) }.time
            }
            else -> {
                try {
                    SimpleDateFormat("MMM dd", Locale.getDefault()).parse(dateTimeString) ?: Date()
                } catch (e: Exception) {
                    Date()
                }
            }
        }
    }
    
    /**
     * Check if two dates are the same day
     */
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    /**
     * Get date sort order for custom sorting
     */
    private fun getDateSortOrder(dateTimeString: String): Int {
        return when {
            dateTimeString.contains("hour") || dateTimeString.contains("Just now") -> {
                val hours = if (dateTimeString.contains("hour")) {
                    dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                } else 0
                hours
            }
            dateTimeString.contains("Yesterday") -> 100
            dateTimeString.contains("days ago") -> {
                val days = dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                1000 + days
            }
            dateTimeString.contains("Aug") || dateTimeString.contains("Today") -> 10000
            else -> 5000
        }
    }
    
    /**
     * Format date for display
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
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                dateFormat.format(date)
            }
        }
    }
    
    /**
     * Get category emoji
     */
    private fun getCategoryEmoji(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "food & dining", "food", "dining" -> "🍽️"
            "transportation", "transport" -> "🚗"
            "groceries", "grocery" -> "🛒"
            "healthcare", "health" -> "🏥"
            "entertainment" -> "🎬"
            "shopping" -> "🛍️"
            "utilities" -> "⚡"
            "money", "finance" -> "[FINANCIAL]"
            "education" -> "📚"
            "travel" -> "✈️"
            "bills" -> "💳"
            "insurance" -> "🛡️"
            else -> "📂"
        }
    }
    
    /**
     * Get random color for new categories
     */
    private fun getRandomCategoryColor(): String {
        val colors = listOf(
            "#ff5722", "#3f51b5", "#4caf50", "#e91e63",
            "#ff9800", "#9c27b0", "#607d8b", "#795548",
            "#2196f3", "#8bc34a", "#ffc107", "#673ab7"
        )
        return colors.random()
    }
    
    /**
     * Handle transaction-related errors
     */
    private fun handleTransactionError(throwable: Throwable) {
        val errorMessage = when {
            throwable.message?.contains("network", ignoreCase = true) == true -> "Network error. Please try again."
            throwable.message?.contains("permission", ignoreCase = true) == true -> "Permission required to access data"
            throwable.message?.contains("database", ignoreCase = true) == true -> "Database error. Please try again."
            else -> "Something went wrong. Please try again."
        }
        
        _uiState.value = _uiState.value.copy(
            isInitialLoading = false,
            isRefreshing = false,
            isLoading = false,
            isUpdatingCategory = false,
            hasError = true,
            error = errorMessage
        )
    }
}