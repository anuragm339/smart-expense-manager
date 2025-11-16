package com.smartexpenseai.app.ui.categories

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.utils.CategoryManager
import com.smartexpenseai.app.utils.MerchantAliasManager
import com.smartexpenseai.app.utils.logging.StructuredLogger
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
    private val repository: ExpenseRepository,
    private val categoryDisplayProvider: CategoryDisplayProvider,
    private val categoryManager: CategoryManager,
    private val merchantAliasManager: MerchantAliasManager
) : ViewModel() {

    companion object {
        /**
         * Get current month date range (start and end)
         */
        fun getCurrentMonthDateRange(): Pair<Date, Date> {
            val calendar = Calendar.getInstance()

            // First day of current month
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            // Current time (end of range)
            val endDate = Calendar.getInstance().time

            return Pair(startDate, endDate)
        }
    }

    // Private mutable state
    private val _uiState = MutableStateFlow(CategoriesUIState())

    // Public immutable state
    val uiState: StateFlow<CategoriesUIState> = _uiState.asStateFlow()

    // Logger instance
    private val logger = StructuredLogger("CATEGORIES", "CategoriesViewModel")
    
    init {
        logger.debug("init","ViewModel initialized, loading categories...")
        loadCategories()
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: CategoriesUIEvent) {
        logger.debug("handleEvent","Handling event: $event")
        
        when (event) {
            is CategoriesUIEvent.Refresh -> refreshCategories()
            is CategoriesUIEvent.LoadCategories -> loadCategories()
            is CategoriesUIEvent.AddCategory -> addNewCategory(event.name, event.emoji)
            is CategoriesUIEvent.DeleteCategory -> deleteCategory(event.categoryName)
            is CategoriesUIEvent.RenameCategory -> renameCategory(event.oldName, event.newName, event.newEmoji)
            is CategoriesUIEvent.QuickAddExpense -> addQuickExpense(event.amount, event.merchant, event.category)
            is CategoriesUIEvent.CategorySelected -> handleCategorySelection(event.categoryName)
            is CategoriesUIEvent.ClearError -> clearError()
            is CategoriesUIEvent.SearchCategories -> searchCategories(event.query)
            is CategoriesUIEvent.SortCategories -> sortCategories(event.sortType)
            is CategoriesUIEvent.FilterCategories -> filterCategories(event.filterType)
        }
    }
    
    /**
     * Initial loading of categories
     */
    private fun loadCategories() {
        logger.debug("loadCategories","Starting initial category load...")
        
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
                    originalCategories = categories, // Store unfiltered data
                    totalSpent = categories.sumOf { it.amount },
                    categoryCount = categories.size,
                    isEmpty = categories.isEmpty(),
                    lastRefreshTime = System.currentTimeMillis()
                )
                
            } catch (e: Exception) {
                logger.error("loadCategories", "Error loading categories",e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Refresh categories (pull-to-refresh)
     */
    private fun refreshCategories() {
        logger.debug("refreshCategories","Refreshing categories...")
        
        // Clear display provider cache to ensure fresh emoji data
        if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
            categoryDisplayProvider.clearCache()
            logger.debug("refreshCategories","Cleared CategoryDisplayProvider cache")
        }
        
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
                    originalCategories = categories, // Store unfiltered data
                    totalSpent = categories.sumOf { it.amount },
                    categoryCount = categories.size,
                    isEmpty = categories.isEmpty(),
                    lastRefreshTime = System.currentTimeMillis()
                )
                
            } catch (e: Exception) {
                logger.error("refreshCategories", "Error refreshing categories",e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Add a new category
     */
    private fun addNewCategory(name: String, emoji: String) {
        logger.debug("addNewCategory","Adding new category: $name with emoji: $emoji")
        
        viewModelScope.launch {
            try {
                // Save category to database with user's chosen emoji
                val categoryColor = getRandomCategoryColor()
                val categoryEntity = com.smartexpenseai.app.data.entities.CategoryEntity(
                    name = name,
                    emoji = emoji,
                    color = categoryColor,
                    isSystem = false,
                    displayOrder = 999,
                    createdAt = java.util.Date()
                )
                val categoryId = repository.insertCategory(categoryEntity)
                logger.debug("addNewCategory","Category '$name' saved to database with ID: $categoryId")
                
                // Create new category item
                val newCategory = CategoryItem(
                    name = name,
                    emoji = emoji,
                    color = categoryColor,
                    amount = 0.0,
                    transactionCount = 0,
                    lastTransaction = "",
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
                
                // Clear display provider cache to ensure new category gets proper icon
                if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
                    categoryDisplayProvider.clearCacheForCategory(name)
                    logger.debug("addNewCategory","Cleared CategoryDisplayProvider cache for '$name' after add")
                }
                
                logger.debug("addNewCategory","Category '$name' added successfully")
                
                // DEBUG: Print all categories from database to verify persistence
                try {
                    val allDbCategories = repository.getAllCategoriesSync()
                    logger.debug("addNewCategory","=== ALL CATEGORIES IN DATABASE AFTER CREATION ===")
                    allDbCategories.forEachIndexed { index, category ->
                       logger.debug("addNewCategory","DB Category $index: id=${category.id}, name='${category.name}', emoji='${category.emoji}'")
                    }
                    logger.debug("addNewCategory","=== END DATABASE CATEGORIES (${allDbCategories.size} total) ===")
                } catch (e: Exception) {
                    logger.error("addNewCategory", "Error printing database categories",e)
                }
                
            } catch (e: Exception) {
                logger.error("addNewCategory","Error adding category",e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Delete a category - Single Source of Truth (Database Only)
     * Simplified: No more SharedPreferences sync needed!
     */
    private fun deleteCategory(categoryName: String) {
        logger.debug("deleteCategory","Deleting category: $categoryName")

        viewModelScope.launch {
            try {
                val categoryEntity = repository.getCategoryByName(categoryName)
                if (categoryEntity == null) {
                    logger.warn("deleteCategory", "Category '$categoryName' not found in database")
                    return@launch
                }

                // Ensure "Other" category exists as fallback
                var otherCategory = repository.getCategoryByName("Other")
                if (otherCategory == null) {
                    val otherCategoryEntity = com.smartexpenseai.app.data.entities.CategoryEntity(
                        name = "Other",
                        emoji = "📂",
                        color = "#607d8b",
                        isSystem = true,
                        displayOrder = 999,
                        createdAt = java.util.Date()
                    )
                    repository.insertCategory(otherCategoryEntity)
                    otherCategory = repository.getCategoryByName("Other")
                    logger.debug("deleteCategory","Created 'Other' category as fallback")
                }

                if (otherCategory != null) {
                    // Move all merchants with this category to "Other"
                    val movedCount = repository.updateMerchantsByCategory(categoryEntity.id, otherCategory.id)
                    logger.debug("deleteCategory","Moved $movedCount merchants from '$categoryName' to 'Other'")
                }

                // Delete the category from database (single operation!)
                repository.deleteCategory(categoryEntity)
                logger.debug("deleteCategory","Deleted category '$categoryName' from database")

                // Update MerchantAliasManager
                updateMerchantAliasesForCategoryChange(categoryName, "Other")

                // Clear display provider cache
                if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
                    categoryDisplayProvider.clearCacheForCategory(categoryName)
                }

                // Refresh UI
                refreshCategories()

                logger.info("deleteCategory","Category '$categoryName' deleted successfully")

            } catch (e: Exception) {
                logger.error("deleteCategory", "Error deleting category",e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Rename a category - Single Source of Truth (Database Only)
     * Simplified: No more SharedPreferences sync needed!
     */
    private fun renameCategory(oldName: String, newName: String, newEmoji: String) {
        logger.debug("renameCategory","Renaming category from '$oldName' to '$newName' with emoji '$newEmoji'")

        viewModelScope.launch {
            try {
                val categoryEntity = repository.getCategoryByName(oldName)
                if (categoryEntity != null) {
                    // Update existing category in database (single operation!)
                    val updatedCategory = categoryEntity.copy(
                        name = newName,
                        emoji = newEmoji.ifEmpty { categoryEntity.emoji }
                    )
                    repository.updateCategory(updatedCategory)
                    logger.debug("renameCategory","Updated category from '$oldName' to '$newName' in database")
                } else {
                    // Category doesn't exist - create new one
                    logger.warn("renameCategory","Category '$oldName' not found, creating new category '$newName'")
                    val newCategoryEntity = com.smartexpenseai.app.data.entities.CategoryEntity(
                        name = newName,
                        emoji = newEmoji.ifEmpty { "📂" },
                        color = getRandomCategoryColor(),
                        isSystem = false,
                        displayOrder = 999,
                        createdAt = java.util.Date()
                    )
                    repository.insertCategory(newCategoryEntity)
                }

                // Update MerchantAliasManager
                updateMerchantAliasesForCategoryChange(oldName, newName)

                // Clear display provider cache
                if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
                    categoryDisplayProvider.clearCacheForCategory(oldName)
                    categoryDisplayProvider.clearCacheForCategory(newName)
                }

                // Refresh UI
                refreshCategories()

                logger.info("renameCategory","Category renamed from '$oldName' to '$newName' successfully")

            } catch (e: Exception) {
                logger.error("renameCategory", "Error renaming category",e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Add quick expense
     */
    private fun addQuickExpense(amount: Double, merchant: String, category: String) {
        logger.debug("addQuickExpense","Adding quick expense: ₹$amount at $merchant ($category)")
        
        viewModelScope.launch {
            try {
                // TODO: Implement with proper repository method when available
                // For now, just refresh categories to show updated data
                refreshCategories()
            } catch (e: Exception) {
                logger.error("addQuickExpense", "Error adding quick expense",e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Handle category selection for navigation
     */
    private fun handleCategorySelection(categoryName: String) {
        logger.debug("handleCategorySelection","Category selected: $categoryName")
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
            logger.debug("loadCategoryData","Loading category data from repository...")

            // Get current month date range using helper
            val (startDate, endDate) = getCurrentMonthDateRange()

            val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            logger.debug("loadCategoryData","Loading categories from start of month: ${dateFormatter.format(startDate)}")
            
            // Get category spending from repository
            val categorySpendingResults = repository.getCategorySpending(startDate, endDate)
            
            logger.debug("loadCategoryData","Repository returned ${categorySpendingResults.size} category results")
            
            return processCategorySpendingResults(categorySpendingResults)
            
        } catch (e: SecurityException) {
            logger.error("loadCategoryData", "Permission denied, showing empty state",e)
            return getEmptyCategories()
        } catch (e: Exception) {
            logger.error("loadCategoryData", "Error loading category data, showing empty state",e)
            return getEmptyCategories()
        }
    }
    
    /**
     * Process category spending results and convert to CategoryItem list
     * Shared logic between loadCategoryData and loadCategoryDataFallback
     */
    private suspend fun processCategorySpendingResults(
        categorySpendingResults: List<com.smartexpenseai.app.data.dao.CategorySpendingResult>
    ): List<CategoryItem> {
        if (categorySpendingResults.isEmpty()) {
            logger.debug("processCategorySpendingResults", "No data in repository, falling back to SMS reading...")
            return loadCategoryDataFallback()
        }

        // Convert repository results to CategoryItem format
        val transactionCategoryData = categorySpendingResults.map { categoryResult ->
            val lastTransactionText = categoryResult.last_transaction_date?.let {
                formatLastTransaction(it)
            } ?: ""

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
                    lastTransaction = "",
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
    }

    /**
     * Fallback category loading from SMS
     * Triggers SMS sync and then reuses main loading logic
     */
    private suspend fun loadCategoryDataFallback(): List<CategoryItem> {
        return try {
            logger.debug("loadCategoryDataFallback","Loading category data from SMS as fallback...")

            // Trigger SMS sync if no data exists
            val syncedCount = repository.syncNewSMS()
            logger.debug("loadCategoryDataFallback","Synced $syncedCount new transactions from SMS")

            if (syncedCount > 0) {
                // Reuse the main loading logic - no need to duplicate code!
                val (startDate, endDate) = getCurrentMonthDateRange()
                val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                logger.debug("loadCategoryDataFallback","Loading categories fallback from start of month: ${dateFormatter.format(startDate)}")

                return processCategorySpendingResults(
                    repository.getCategorySpending(startDate, endDate)
                )
            } else {
                return getEmptyCategories()
            }

        } catch (e: Exception) {
            logger.error("loadCategoryDataFallback", "Error in fallback loading, showing empty state",e)
            return getEmptyCategories()
        }
    }
    
    /**
     * Get empty categories (only custom categories with ₹0 amounts)
     */
    private suspend fun getEmptyCategories(): List<CategoryItem> {
        logger.debug("getEmptyCategories","Showing empty categories state - no dummy data")

        // Only show custom categories with ₹0 amounts - no fake data
        val customCategories = categoryManager.getAllCategories()
        
        return customCategories.map { categoryName ->
            CategoryItem(
                name = categoryName,
                emoji = getCategoryEmoji(categoryName),
                color = getRandomCategoryColor(),
                amount = 0.0,
                transactionCount = 0,
                lastTransaction = "",
                percentage = 0,
                progress = 0
            )
        }
    }
    
    /**
     * Get emoji for category using CategoryDisplayProvider
     * This ensures database-aware emoji lookup with proper fallbacks
     */
    private fun getCategoryEmoji(category: String): String {
        return categoryDisplayProvider.getEmojiString(category)
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
    
    /**
     * Update all merchant aliases that point to the old category name
     */
    private suspend fun updateMerchantAliasesForCategoryChange(oldCategoryName: String, newCategoryName: String) {
        try {
            val aliases = merchantAliasManager.getAllAliases()
            var updatedCount = 0
            
            aliases.values.filter { it.category == oldCategoryName }.forEach { alias ->
                merchantAliasManager.setMerchantAlias(
                    originalName = alias.originalName,
                    displayName = alias.displayName,
                    category = newCategoryName
                )
                updatedCount++
            }
            
            logger.debug("updateMerchantAliasesForCategoryChange","Updated $updatedCount merchant aliases from '$oldCategoryName' to '$newCategoryName'")
        } catch (e: Exception) {
            logger.error("updateMerchantAliasesForCategoryChange", "Error updating merchant aliases for category change",e)
        }
    }
    
    // NOTE: updateCategoryManagerLearnedRules() removed - no longer needed!
    // Merchant category mappings are now stored in MerchantEntity.categoryId + isUserDefined flag

    // NOTE: printAllCategoriesFromBothSources() removed - no longer needed!
    // Single source of truth: Room Database only

    /**
     * Search categories by name or merchant
     */
    private fun searchCategories(query: String) {
        logger.debug("searchCategories","Searching categories with query: '$query'")

        val originalCats = _uiState.value.originalCategories.ifEmpty { _uiState.value.categories }

        val filteredCategories = if (query.isBlank()) {
            originalCats
        } else {
            originalCats.filter { category ->
                category.name.contains(query, ignoreCase = true)
            }
        }

        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            originalCategories = if (_uiState.value.originalCategories.isEmpty()) originalCats else _uiState.value.originalCategories,
            categories = applySortAndFilter(filteredCategories, _uiState.value.sortType),
            isEmpty = filteredCategories.isEmpty()
        )
    }

    /**
     * Sort categories by different criteria
     */
    private fun sortCategories(sortType: String) {
        logger.debug("sortCategories","Sorting categories by: $sortType")

        val sortedCategories = applySortAndFilter(_uiState.value.categories, sortType)

        _uiState.value = _uiState.value.copy(
            sortType = sortType,
            categories = sortedCategories
        )
    }

    /**
     * Filter categories by type
     */
    private fun filterCategories(filterType: String) {
        logger.debug("filterCategories","Filtering categories by: $filterType")

        val originalCats = _uiState.value.originalCategories.ifEmpty { _uiState.value.categories }

        val filteredCategories = when (filterType) {
            "all" -> originalCats
            "has_transactions" -> originalCats.filter { it.transactionCount > 0 }
            "empty" -> originalCats.filter { it.transactionCount == 0 }
            "custom" -> originalCats.filter { !isSystemCategory(it.name) }
            "high_spend" -> {
                val totalSpent = originalCats.sumOf { it.amount }
                val threshold = totalSpent * 0.1 // Top 10% of spending
                originalCats.filter { it.amount >= threshold && it.amount > 0 }
            }
            else -> originalCats
        }

        _uiState.value = _uiState.value.copy(
            filterType = filterType,
            originalCategories = if (_uiState.value.originalCategories.isEmpty()) originalCats else _uiState.value.originalCategories,
            categories = applySortAndFilter(filteredCategories, _uiState.value.sortType),
            isEmpty = filteredCategories.isEmpty()
        )
    }

    /**
     * Apply sort to a list of categories
     */
    private fun applySortAndFilter(categories: List<CategoryItem>, sortType: String): List<CategoryItem> {
        return when (sortType) {
            "amount_desc" -> categories.sortedByDescending { it.amount }
            "amount_asc" -> categories.sortedBy { it.amount }
            "name_asc" -> categories.sortedBy { it.name }
            "name_desc" -> categories.sortedByDescending { it.name }
            "recent_activity" -> categories.sortedByDescending {
                when {
                    it.lastTransaction.contains("Just now") -> 5
                    it.lastTransaction.contains("hours ago") -> 4
                    it.lastTransaction.contains("Yesterday") -> 3
                    it.lastTransaction.contains("days ago") -> 2
                    else -> 1
                }
            }
            else -> categories
        }
    }

    /**
     * Check if category is a system category
     */
    private fun isSystemCategory(categoryName: String): Boolean {
        val systemCategories = listOf(
            "Food & Dining", "Transportation", "Healthcare", "Groceries",
            "Entertainment", "Shopping", "Utilities", "Other"
        )
        return systemCategories.contains(categoryName)
    }
}