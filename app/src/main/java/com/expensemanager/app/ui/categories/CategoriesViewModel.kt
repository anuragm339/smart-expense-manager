package com.expensemanager.app.ui.categories

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for Categories screen
 * Manages category UI state and handles user interactions
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "CategoriesViewModel"
    }
    
    // Private mutable state
    private val _uiState = MutableStateFlow(CategoriesUIState())
    
    // Public immutable state
    val uiState: StateFlow<CategoriesUIState> = _uiState.asStateFlow()
    
    // Manager instances
    private val categoryManager = CategoryManager(context)
    private val merchantAliasManager = MerchantAliasManager(context)
    
    init {
        Log.d(TAG, "ViewModel initialized, loading categories...")
        loadCategories()
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: CategoriesUIEvent) {
        Log.d(TAG, "Handling event: $event")
        
        when (event) {
            is CategoriesUIEvent.Refresh -> refreshCategories()
            is CategoriesUIEvent.LoadCategories -> loadCategories()
            is CategoriesUIEvent.AddCategory -> addNewCategory(event.name, event.emoji)
            is CategoriesUIEvent.QuickAddExpense -> addQuickExpense(event.amount, event.merchant, event.category)
            is CategoriesUIEvent.CategorySelected -> handleCategorySelection(event.categoryName)
            is CategoriesUIEvent.ClearError -> clearError()
        }
    }
    
    /**
     * Initial loading of categories
     */
    private fun loadCategories() {
        Log.d(TAG, "Starting initial category load...")
        
        _uiState.value = _uiState.value.copy(
            isInitialLoading = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            try {
                val categories = loadCategoryData()
                
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    categories = categories,
                    totalSpent = categories.sumOf { it.amount },
                    categoryCount = categories.size,
                    isEmpty = categories.isEmpty(),
                    lastRefreshTime = System.currentTimeMillis()
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading categories", e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Refresh categories (pull-to-refresh)
     */
    private fun refreshCategories() {
        Log.d(TAG, "Refreshing categories...")
        
        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            try {
                val categories = loadCategoryData()
                
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    categories = categories,
                    totalSpent = categories.sumOf { it.amount },
                    categoryCount = categories.size,
                    isEmpty = categories.isEmpty(),
                    lastRefreshTime = System.currentTimeMillis()
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing categories", e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Add a new category
     */
    private fun addNewCategory(name: String, emoji: String) {
        Log.d(TAG, "Adding new category: $name with emoji: $emoji")
        
        viewModelScope.launch {
            try {
                // Add to CategoryManager for persistence
                categoryManager.addCustomCategory(name)
                
                // Create new category item
                val newCategory = CategoryItem(
                    name = name,
                    emoji = emoji,
                    color = getRandomCategoryColor(),
                    amount = 0.0,
                    transactionCount = 0,
                    lastTransaction = "No transactions yet",
                    percentage = 0,
                    progress = 0
                )
                
                // Update UI state with new category
                val currentCategories = _uiState.value.categories.toMutableList()
                currentCategories.add(newCategory)
                
                _uiState.value = _uiState.value.copy(
                    categories = currentCategories,
                    categoryCount = currentCategories.size
                )
                
                Log.d(TAG, "Category '$name' added successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding category", e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Add quick expense
     */
    private fun addQuickExpense(amount: Double, merchant: String, category: String) {
        Log.d(TAG, "Adding quick expense: ₹$amount at $merchant ($category)")
        
        viewModelScope.launch {
            try {
                // TODO: Implement with proper repository method when available
                // For now, just refresh categories to show updated data
                refreshCategories()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding quick expense", e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Handle category selection for navigation
     */
    private fun handleCategorySelection(categoryName: String) {
        Log.d(TAG, "Category selected: $categoryName")
        // This will be handled by the Fragment for navigation
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
     * Load category data from repository
     */
    private suspend fun loadCategoryData(): List<CategoryItem> {
        return try {
            Log.d(TAG, "Loading category data from repository...")
            
            // Use the same date range as Dashboard (current month)
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time
            
            calendar.add(Calendar.MONTH, 1)
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            val endDate = calendar.time
            
            // Get category spending from repository
            val categorySpendingResults = repository.getCategorySpending(startDate, endDate)
            
            Log.d(TAG, "Repository returned ${categorySpendingResults.size} category results")
            
            if (categorySpendingResults.isNotEmpty()) {
                // Convert repository results to CategoryItem format
                val transactionCategoryData = categorySpendingResults.map { categoryResult ->
                    val lastTransactionText = categoryResult.last_transaction_date?.let { 
                        formatLastTransaction(it) 
                    } ?: "No transactions"
                    
                    CategoryItem(
                        name = categoryResult.category_name,
                        emoji = getCategoryEmoji(categoryResult.category_name),
                        color = categoryResult.color,
                        amount = categoryResult.total_amount,
                        transactionCount = categoryResult.transaction_count,
                        lastTransaction = lastTransactionText,
                        percentage = 0, // Will be calculated after we have total
                        progress = 0    // Will be calculated after we have total
                    )
                }
                
                // Add custom categories that might not have transactions yet
                val allCategories = categoryManager.getAllCategories()
                val existingCategoryNames = transactionCategoryData.map { it.name }
                val missingCategories = allCategories.filter { !existingCategoryNames.contains(it) }
                    .map { categoryName ->
                        CategoryItem(
                            name = categoryName,
                            emoji = getCategoryEmoji(categoryName),
                            color = getRandomCategoryColor(),
                            amount = 0.0,
                            transactionCount = 0,
                            lastTransaction = "No transactions yet",
                            percentage = 0,
                            progress = 0
                        )
                    }
                
                val categoryData = (transactionCategoryData + missingCategories)
                    .sortedByDescending { it.amount }
                
                // Calculate percentages and progress
                val totalSpent = categoryData.sumOf { it.amount }
                
                return categoryData.map { categoryItem ->
                    val percentage = if (totalSpent > 0) ((categoryItem.amount / totalSpent) * 100).toInt() else 0
                    categoryItem.copy(
                        percentage = percentage,
                        progress = percentage.coerceAtMost(100)
                    )
                }
                
            } else {
                Log.d(TAG, "No data in repository, falling back to SMS reading...")
                return loadCategoryDataFallback()
            }
            
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied, showing empty state", e)
            return getEmptyCategories()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading category data, showing empty state", e)
            return getEmptyCategories()
        }
    }
    
    /**
     * Fallback category loading from SMS
     */
    private suspend fun loadCategoryDataFallback(): List<CategoryItem> {
        return try {
            Log.d(TAG, "Loading category data from SMS as fallback...")
            
            // Trigger SMS sync if no data exists
            val syncedCount = repository.syncNewSMS()
            Log.d(TAG, "Synced $syncedCount new transactions from SMS")
            
            if (syncedCount > 0) {
                // Now load data from repository
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                val endDate = calendar.time
                
                val categorySpendingResults = repository.getCategorySpending(startDate, endDate)
                
                if (categorySpendingResults.isNotEmpty()) {
                    // Convert and calculate as in main method
                    val transactionCategoryData = categorySpendingResults.map { categoryResult ->
                        val lastTransactionText = categoryResult.last_transaction_date?.let { 
                            formatLastTransaction(it) 
                        } ?: "No transactions"
                        
                        CategoryItem(
                            name = categoryResult.category_name,
                            emoji = getCategoryEmoji(categoryResult.category_name),
                            color = categoryResult.color,
                            amount = categoryResult.total_amount,
                            transactionCount = categoryResult.transaction_count,
                            lastTransaction = lastTransactionText,
                            percentage = 0,
                            progress = 0
                        )
                    }
                    
                    val allCategories = categoryManager.getAllCategories()
                    val existingCategoryNames = transactionCategoryData.map { it.name }
                    val missingCategories = allCategories.filter { !existingCategoryNames.contains(it) }
                        .map { categoryName ->
                            CategoryItem(
                                name = categoryName,
                                emoji = getCategoryEmoji(categoryName),
                                color = getRandomCategoryColor(),
                                amount = 0.0,
                                transactionCount = 0,
                                lastTransaction = "No transactions yet",
                                percentage = 0,
                                progress = 0
                            )
                        }
                    
                    val categoryData = (transactionCategoryData + missingCategories)
                        .sortedByDescending { it.amount }
                    
                    val totalSpent = categoryData.sumOf { it.amount }
                    return categoryData.map { categoryItem ->
                        val percentage = if (totalSpent > 0) ((categoryItem.amount / totalSpent) * 100).toInt() else 0
                        categoryItem.copy(
                            percentage = percentage,
                            progress = percentage.coerceAtMost(100)
                        )
                    }
                } else {
                    return getEmptyCategories()
                }
            } else {
                return getEmptyCategories()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback loading, showing empty state", e)
            return getEmptyCategories()
        }
    }
    
    /**
     * Get empty categories (only custom categories with ₹0 amounts)
     */
    private fun getEmptyCategories(): List<CategoryItem> {
        Log.d(TAG, "Showing empty categories state - no dummy data")
        
        // Only show custom categories with ₹0 amounts - no fake data
        val customCategories = categoryManager.getAllCategories()
        
        return customCategories.map { categoryName ->
            CategoryItem(
                name = categoryName,
                emoji = getCategoryEmoji(categoryName),
                color = getRandomCategoryColor(),
                amount = 0.0,
                transactionCount = 0,
                lastTransaction = "No transactions yet",
                percentage = 0,
                progress = 0
            )
        }
    }
    
    /**
     * Get emoji for category
     */
    private fun getCategoryEmoji(category: String): String {
        return when (category.lowercase()) {
            "food & dining", "food", "dining" -> "🍽️"
            "transportation", "transport" -> "🚗"
            "groceries", "grocery" -> "🛒"
            "healthcare", "health" -> "🏥"
            "entertainment" -> "🎬"
            "shopping" -> "🛍️"
            "utilities" -> "⚡"
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
     * Format last transaction date
     */
    private fun formatLastTransaction(date: Date): String {
        val now = Date()
        val diffInMs = now.time - date.time
        val diffInHours = diffInMs / (1000 * 60 * 60)
        val diffInDays = diffInMs / (1000 * 60 * 60 * 24)
        
        return when {
            diffInHours < 1 -> "Just now"
            diffInHours < 24 -> "$diffInHours hours ago"
            diffInDays == 1L -> "Yesterday"
            diffInDays < 7 -> "$diffInDays days ago"
            else -> "${diffInDays / 7} weeks ago"
        }
    }
    
    /**
     * Handle category loading errors
     */
    private fun handleCategoryError(throwable: Throwable) {
        val errorMessage = when {
            throwable.message?.contains("network", ignoreCase = true) == true -> "Check your internet connection and try again"
            throwable.message?.contains("permission", ignoreCase = true) == true -> "Permission required to access data"
            else -> "Something went wrong. Please try again"
        }
        
        // Don't show error state if we have existing data
        val hasExistingData = _uiState.value.categories.isNotEmpty()
        
        _uiState.value = _uiState.value.copy(
            isInitialLoading = false,
            isRefreshing = false,
            isLoading = false,
            hasError = !hasExistingData,
            error = if (hasExistingData) null else errorMessage
        )
    }
}