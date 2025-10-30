package com.expensemanager.app.ui.categories

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.ui.messages.MessageItem
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.utils.logging.StructuredLogger
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
    private val repository: ExpenseRepository,
    private val categoryDisplayProvider: CategoryDisplayProvider
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
    private val logger = StructuredLogger("CategoryTransactionsViewModel", "CategoryTransactionsViewModel")
    
    init {
        logger.debug("init","ViewModel initialized")
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: CategoryTransactionsUIEvent) {
        logger.debug("handleEvent","Handling event: $event")
        
        when (event) {
            is CategoryTransactionsUIEvent.LoadTransactions -> loadTransactions()
            is CategoryTransactionsUIEvent.Refresh -> refreshTransactions()
            is CategoryTransactionsUIEvent.SetCategoryName -> setCategoryName(event.categoryName)
            is CategoryTransactionsUIEvent.ChangeSortOption -> changeSortOption(event.sortOption)
            is CategoryTransactionsUIEvent.ChangeFilterOption -> changeFilterOption(event.filterOption)
            is CategoryTransactionsUIEvent.UpdateTransactionCategory -> updateTransactionCategory(event.messageItem, event.newCategory)
            is CategoryTransactionsUIEvent.ShowCategoryEditDialog -> showCategoryEditDialog(event.messageItem)
            is CategoryTransactionsUIEvent.ClearError -> clearError()
            is CategoryTransactionsUIEvent.ClearSuccess -> clearSuccess()
        }
    }
    
    /**
     * Set category name and load related data
     */
    private fun setCategoryName(categoryName: String) {
        logger.debug("setCategoryName","Setting category name: $categoryName")
        
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
        logger.debug("loadTransactions","Loading transactions for category: ${_uiState.value.categoryName}")
        
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
                logger.error("loadTransactions","Error loading transactions",e)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Refresh category transactions
     */
    private fun refreshTransactions() {
        logger.debug("refreshTransactions","Refreshing transactions...")
        
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
                logger.error("refreshTransactions","Error refreshing transactions",e)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Change sort option and reapply
     */
    private fun changeSortOption(sortOption: String) {
        logger.debug("changeSortOption","Changing sort option to: $sortOption")
        
        _uiState.value = _uiState.value.copy(
            currentSortOption = sortOption
        )
        
        applyFilterAndSort()
    }
    
    /**
     * Change filter option and reapply
     */
    private fun changeFilterOption(filterOption: String) {
        logger.debug("changeFilterOption","Changing filter option to: $filterOption")
        
        _uiState.value = _uiState.value.copy(
            currentFilterOption = filterOption
        )
        
        applyFilterAndSort()
    }
    
    /**
     * Update transaction category
     * @param messageItem The transaction item to update
     * @param newCategory The new category name (should be plain name without emojis)
     */
    private fun updateTransactionCategory(messageItem: MessageItem, newCategory: String) {
        // newCategory is now expected to be a plain category name (no emoji extraction needed)
        val plainCategoryName = newCategory.trim()
        
        logger.debug("updateTransactionCategory","Updating merchant ${messageItem.merchant} (raw: ${messageItem.rawMerchant}) from ${messageItem.category} to $plainCategoryName")
        
        _uiState.value = _uiState.value.copy(
            isUpdatingCategory = true,
            hasError = false,
            error = null,
            successMessage = null
        )
        
        viewModelScope.launch {
            try {
                // CRITICAL FIX: Use the exact same normalization as used when storing merchants
                // This should match the normalization used in DataMigrationHelper, AddTransactionUseCase, etc.
                val normalizedMerchant = messageItem.rawMerchant.uppercase()
                    .replace(Regex("[*#@\\-_]+.*"), "") // Remove suffixes after special chars
                    .replace(Regex("\\s+"), " ") // Normalize spaces
                    .trim()

                logger.debug("updateTransactionCategory","MERCHANT_UPDATE: rawMerchant='${messageItem.rawMerchant}' -> normalizedMerchant='$normalizedMerchant'")
                
                // Try to find the merchant in database
                var merchant = repository.getMerchantByNormalizedName(normalizedMerchant)
                
                // FALLBACK: If merchant not found, try alternative normalization strategies
                if (merchant == null) {
                    logger.debug("updateTransactionCategory","Merchant not found with normalization '$normalizedMerchant', trying alternatives...")
                    
                    // Try without removing suffixes
                    val alternativeNormalized1 = messageItem.rawMerchant.uppercase()
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    merchant = repository.getMerchantByNormalizedName(alternativeNormalized1)
                    logger.debug("updateTransactionCategory","Alternative 1: '$alternativeNormalized1' -> ${if (merchant != null) "FOUND" else "NOT FOUND"}")
                    
                    if (merchant == null) {
                        // Try using the display name as-is
                        val alternativeNormalized2 = messageItem.merchant.uppercase()
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        merchant = repository.getMerchantByNormalizedName(alternativeNormalized2)
                        logger.debug("updateTransactionCategory","Alternative 2: '$alternativeNormalized2' -> ${if (merchant != null) "FOUND" else "NOT FOUND"}")
                    }
                    
                    if (merchant == null) {
                        // Try exact raw merchant as stored
                        merchant = repository.getMerchantByNormalizedName(messageItem.rawMerchant)
                        logger.debug("updateTransactionCategory","Alternative 3: '${messageItem.rawMerchant}' -> ${if (merchant != null) "FOUND" else "NOT FOUND"}")
                    }
                }
                
                if (merchant != null) {
                    logger.debug("updateTransactionCategory","MERCHANT_FOUND: id=${merchant.id}, normalizedName='${merchant.normalizedName}', displayName='${merchant.displayName}'")
                    
                    // Get or create the new category using plain category name (without emoji)
                    var newCategoryEntity = repository.getCategoryByName(plainCategoryName)
                    if (newCategoryEntity == null) {
                        // Category doesn't exist in database, create it
                        logger.debug("updateTransactionCategory","Creating new category: $plainCategoryName")
                        
                        // Use display provider to get appropriate emoji for this category
                        val categoryIcon = categoryDisplayProvider.getDisplayIcon(plainCategoryName)
                        val emoji = when (categoryIcon) {
                            is com.expensemanager.app.ui.categories.CategoryIcon.Emoji -> categoryIcon.emoji
                            else -> "📂" // Default fallback
                        }
                        
                        val categoryToCreate = com.expensemanager.app.data.entities.CategoryEntity(
                            name = plainCategoryName,
                            emoji = emoji,
                            color = getRandomCategoryColor(),
                            isSystem = false,
                            displayOrder = 100,
                            createdAt = Date()
                        )
                        
                        val categoryId = repository.insertCategory(categoryToCreate)
                        newCategoryEntity = repository.getCategoryById(categoryId)
                        logger.debug("updateTransactionCategory","Created category with ID: $categoryId")
                    }
                    
                    if (newCategoryEntity != null) {
                        logger.debug("updateTransactionCategory","TARGET_CATEGORY: id=${newCategoryEntity.id}, name='${newCategoryEntity.name}'")
                        
                        // Update merchant's category in database
                        val updatedMerchant = merchant.copy(categoryId = newCategoryEntity.id)
                        repository.updateMerchant(updatedMerchant)
                        logger.debug("updateTransactionCategory","Updated merchant category in database: ${merchant.id} -> categoryId=${newCategoryEntity.id}")
                        
                        // FIXED: Update MerchantAliasManager to ensure consistent behavior across all screens
                        val currentDisplayName = merchantAliasManager.getDisplayName(messageItem.rawMerchant)
                        val aliasUpdateSuccess = merchantAliasManager.setMerchantAlias(
                            originalName = messageItem.rawMerchant,
                            displayName = currentDisplayName, // Keep current display name
                            category = plainCategoryName // Use plain category name (without emoji) for database consistency
                        )
                        
                        if (!aliasUpdateSuccess) {
                            logger.debug("updateTransactionCategory","Failed to update merchant alias for: ${messageItem.rawMerchant}")
                        } else {
                            logger.debug("updateTransactionCategory","Updated MerchantAliasManager for: ${messageItem.rawMerchant}")
                        }
                        
                        // Update CategoryManager for backwards compatibility
                        categoryManager.updateCategory(messageItem.rawMerchant, plainCategoryName)
                        logger.debug("updateTransactionCategory","Updated CategoryManager for: ${messageItem.rawMerchant}")
                        
                        // FIXED: Send broadcast to notify Dashboard and other screens about category change
                        val intent = Intent("com.expensemanager.CATEGORY_UPDATED").apply {
                            putExtra("merchant", messageItem.merchant)
                            putExtra("rawMerchant", messageItem.rawMerchant)
                            putExtra("category", plainCategoryName) // Use plain category name for broadcast consistency
                        }
                        context.sendBroadcast(intent)
                        logger.debug("updateTransactionCategory","BROADCAST_SENT: Category updated ${messageItem.merchant} -> $plainCategoryName")
                        
                        // Show success message
                        _uiState.value = _uiState.value.copy(
                            isUpdatingCategory = false,
                            successMessage = "Successfully moved ${messageItem.merchant} to $plainCategoryName",
                            hasError = false,
                            error = null
                        )
                        
                        // Refresh data if merchant moved to different category or update current list
                        val currentCategoryName = _uiState.value.categoryName
                        if (plainCategoryName != currentCategoryName) {
                            // Merchant moved to different category, refresh list to remove it
                            logger.debug("updateTransactionCategory","Merchant moved from '$currentCategoryName' to '$plainCategoryName', refreshing list")
                            refreshTransactions()
                        } else {
                            // Update the item in the current list (use plain category name)
                            logger.debug("updateTransactionCategory","Merchant stayed in same category '$currentCategoryName', updating list item")
                            updateTransactionInList(messageItem, plainCategoryName)
                        }
                        
                    } else {
                        throw Exception("Failed to create or retrieve category: $plainCategoryName")
                    }
                } else {
                    // Log available merchants for debugging
                    try {
                        logger.error("updateTransactionCategory",String.format("MERCHANT_NOT_FOUND: Tried normalizations: '$normalizedMerchant', '${messageItem.rawMerchant}', '${messageItem.merchant}'"),null)
                    } catch (e: Exception) {
                        logger.error("updateTransactionCategory", "Error logging merchant details",e)
                    }
                    throw Exception("Merchant not found in database. Tried normalizations: '$normalizedMerchant', '${messageItem.rawMerchant}', '${messageItem.merchant}'")
                }
                
            } catch (e: Exception) {
                logger.error("updateTransactionCategory" ,"Error updating transaction category for merchant '${messageItem.rawMerchant}'",e)
                _uiState.value = _uiState.value.copy(isUpdatingCategory = false)
                handleTransactionError(e)
            }
        }
    }
    
    /**
     * Show category edit dialog (handled by fragment)
     */
    private fun showCategoryEditDialog(messageItem: MessageItem) {
        logger.debug("showCategoryEditDialog","Show category edit dialog for: ${messageItem.merchant}")
        // This is handled by the fragment UI
    }
    
    /**
     * Get all available categories as plain names (no emojis)
     * Always fetches fresh data from database to include newly created categories
     */
    fun getAllCategories(): List<String> {
        return try {
            // Always fetch fresh data from database to include newly created categories
            kotlinx.coroutines.runBlocking {
                val dbCategories = repository.getAllCategoriesSync()
                logger.debug("getAllCategories","Loading categories from database: ${dbCategories.size} categories found")
                
                // Debug: Log each category individually
                dbCategories.forEachIndexed { index, category ->
                    logger.debug("getAllCategories","Category $index: id=${category.id}, name='${category.name}', emoji='${category.emoji}'")
                }
                
                if (dbCategories.isNotEmpty()) {
                    // Return plain category names (without emojis)
                    val plainCategoryNames = dbCategories
                        .filter { category -> 
                            // Filter out categories with invalid names
                            category.name.isNotBlank() && category.name.length > 1
                        }
                        .map { it.name } // Just the plain name

                    logger.debug("getAllCategories","Plain category names: ${plainCategoryNames.joinToString(", ")}")
                    plainCategoryNames
                } else {
                    logger.warn("getAllCategories","No database categories found, using fallback defaults")
                    // Fallback to default categories (plain names)
                    listOf(
                        "Food & Dining", "Transportation", "Groceries",
                        "Healthcare", "Entertainment", "Shopping",
                        "Utilities", "Other"
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("getAllCategories","Error loading categories",e)
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
     * Clear success message
     */
    private fun clearSuccess() {
        _uiState.value = _uiState.value.copy(
            successMessage = null
        )
    }
    
    /**
     * Load category transactions from repository
     */
    private suspend fun loadCategoryTransactionsFromRepository(categoryName: String): List<MessageItem> {
        return try {
            // Get date range from the start of the current month to the current time
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            // Set end date to current time to exclude future transactions
            val endDate = Calendar.getInstance().time

            val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            logger.debug("loadCategoryTransactionsFromRepository","Loading category transactions from start of month: ${dateFormatter.format(startDate)}")
            
            // Load transactions from repository
            val allDbTransactions = repository.getTransactionsByDateRange(startDate, endDate)
            logger.debug("loadCategoryTransactionsFromRepository","Found ${allDbTransactions.size} total transactions")
            
            // Filter transactions by category
            val categoryTransactions = allDbTransactions.mapNotNull { transaction ->
                val merchantWithCategory = repository.getMerchantWithCategory(transaction.normalizedMerchant)
                val transactionCategory = merchantWithCategory?.category_name ?: "Other"

                logger.debug("loadCategoryTransactionsFromRepository","Transaction ${transaction.rawMerchant} -> normalized: ${transaction.normalizedMerchant} -> category: $transactionCategory")
                
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
                        isDebit = transaction.isDebit,
                        rawMerchant = transaction.rawMerchant // Pass the original raw merchant name
                    )
                } else null
            }

            logger.debug("loadCategoryTransactionsFromRepository","Filtered to ${categoryTransactions.size} transactions for $categoryName")
            categoryTransactions
            
        } catch (e: Exception) {
            logger.error("loadCategoryTransactionsFromRepository", "Error loading transactions from repository",e)
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