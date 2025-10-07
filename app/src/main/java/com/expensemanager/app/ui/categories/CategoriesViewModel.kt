package com.expensemanager.app.ui.categories

import android.content.Context
import com.expensemanager.app.utils.logging.LogConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.utils.logging.StructuredLogger
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
    private val categoryDisplayProvider: CategoryDisplayProvider
) : ViewModel() {
    
    companion object {
    }
    
    // Private mutable state
    private val _uiState = MutableStateFlow(CategoriesUIState())
    
    // Public immutable state
    val uiState: StateFlow<CategoriesUIState> = _uiState.asStateFlow()
    
    // Manager instances
    private val categoryManager = CategoryManager(context)
    private val logger = StructuredLogger(LogConfig.FeatureTags.CATEGORIES, "CategoriesViewModel")
    private val merchantAliasManager = MerchantAliasManager(context)
    
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
                // Add to CategoryManager for persistence (SharedPreferences)
                categoryManager.addCustomCategory(name)
                
                // CRITICAL FIX: Also save category to database so it appears in dropdowns and can be deleted/renamed
                val categoryColor = getRandomCategoryColor() // Use same color for both database and UI
                val categoryEntity = com.expensemanager.app.data.entities.CategoryEntity(
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
     * Delete a category comprehensively across all storage locations
     */
    private fun deleteCategory(categoryName: String) {
        logger.debug("deleteCategory","Deleting category: $categoryName")
        
        viewModelScope.launch {
            try {
                // 1. Remove from CategoryManager (SharedPreferences custom categories)
                categoryManager.removeCustomCategory(categoryName)
                logger.debug("deleteCategory","Removed '$categoryName' from CategoryManager")
                
                // 2. Handle database category deletion
                val categoryEntity = repository.getCategoryByName(categoryName)
                if (categoryEntity != null) {
                    // Ensure "Other" category exists
                    var otherCategory = repository.getCategoryByName("Other")
                    if (otherCategory == null) {
                        // Create "Other" category if it doesn't exist
                        val otherCategoryEntity = com.expensemanager.app.data.entities.CategoryEntity(
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
                        logger.debug("deleteCategory","Moved $movedCount merchants from '$categoryName' to 'Other' in database")
                    }
                    
                    // Delete the category from database
                    repository.deleteCategory(categoryEntity)
                    logger.debug("deleteCategory","Deleted '$categoryName' from database")
                } else {
                    logger.debug("deleteCategory","Category '$categoryName' not found in database, only removing from SharedPreferences")
                }
                
                // 3. Update MerchantAliasManager - change all aliases pointing to this category
                updateMerchantAliasesForCategoryChange(categoryName, "Other")
                
                // 4. Update CategoryManager learned rules
                updateCategoryManagerLearnedRules(categoryName, "Other")
                
                // 5. Clear display provider cache to force icon/emoji refresh
                if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
                    categoryDisplayProvider.clearCacheForCategory(categoryName)
                    logger.debug("deleteCategory","Cleared CategoryDisplayProvider cache for '$categoryName' after delete")
                }
                
                // 6. Refresh categories to get updated data from repository
                refreshCategories()
                
                logger.debug("deleteCategory","Category '$categoryName' deleted successfully from all locations")
                
            } catch (e: Exception) {
                logger.error("deleteCategory", "Error deleting category",e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Rename a category comprehensively across all storage locations
     */
    private fun renameCategory(oldName: String, newName: String, newEmoji: String) {
        logger.debug("renameCategory","Renaming category from '$oldName' to '$newName' with emoji '$newEmoji'")
        
        viewModelScope.launch {
            try {
                // 1. Update CategoryManager (SharedPreferences custom categories)
                categoryManager.removeCustomCategory(oldName)
                categoryManager.addCustomCategory(newName)
                logger.debug("renameCategory","Updated '$oldName' to '$newName' in CategoryManager")
                
                // 2. Handle database category rename
                val categoryEntity = repository.getCategoryByName(oldName)
                if (categoryEntity != null) {
                    // Update the category name and emoji in database
                    val updatedCategory = categoryEntity.copy(
                        name = newName,
                        emoji = newEmoji.ifEmpty { categoryEntity.emoji }
                    )
                    repository.updateCategory(updatedCategory)
                   logger.debug("renameCategory","Updated '$oldName' to '$newName' in database")
                    
                    // DEBUG: Verify the update worked
                    val verifyCategory = repository.getCategoryByName(newName)
                    logger.debug("renameCategory","VERIFY: Category '$newName' now has emoji: '${verifyCategory?.emoji}'")
                } else {
                    // Category doesn't exist in database, create it
                    logger.debug("renameCategory","Category '$oldName' not found in database, creating new entry for '$newName'")
                    val newCategoryEntity = com.expensemanager.app.data.entities.CategoryEntity(
                        name = newName,
                        emoji = newEmoji,
                        color = getRandomCategoryColor(),
                        isSystem = false,
                        displayOrder = 999,
                        createdAt = java.util.Date()
                    )
                    repository.insertCategory(newCategoryEntity)
                    logger.debug("renameCategory","Created new category '$newName' in database")
                    
                    // DEBUG: Verify the creation worked
                    val verifyCategory = repository.getCategoryByName(newName)
                    logger.debug("renameCategory","VERIFY: Created category '$newName' has emoji: '${verifyCategory?.emoji}'")
                }
                
                // 3. Update MerchantAliasManager - change all aliases pointing to old category
                updateMerchantAliasesForCategoryChange(oldName, newName)
                
                // 4. Update CategoryManager learned rules
                updateCategoryManagerLearnedRules(oldName, newName)
                
                // 5. Clear display provider cache to force icon/emoji refresh
                if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
                    // Clear cache for both old and new category names
                    categoryDisplayProvider.clearCacheForCategory(oldName)
                    categoryDisplayProvider.clearCacheForCategory(newName)
                    logger.debug("renameCategory","Cleared CategoryDisplayProvider cache for '$oldName' and '$newName' after rename")
                }
                
                // 6. Refresh categories to get updated data from repository
                refreshCategories()
                
                logger.debug("renameCategory","Category renamed from '$oldName' to '$newName' successfully across all locations")

                printAllCategoriesFromBothSources()
                
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
            logger.debug("loadCategoryData","Loading categories from start of month: ${dateFormatter.format(startDate)}")
            
            // Get category spending from repository
            val categorySpendingResults = repository.getCategorySpending(startDate, endDate)
            
            logger.debug("loadCategoryData","Repository returned ${categorySpendingResults.size} category results")
            
            if (categorySpendingResults.isNotEmpty()) {
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
                
            } else {
                logger.debug("loadCategoryData","No data in repository, falling back to SMS reading...")
                return loadCategoryDataFallback()
            }
            
        } catch (e: SecurityException) {
            logger.error("loadCategoryData", "Permission denied, showing empty state",e)
            return getEmptyCategories()
        } catch (e: Exception) {
            logger.error("loadCategoryData", "Error loading category data, showing empty state",e)
            return getEmptyCategories()
        }
    }
    
    /**
     * Fallback category loading from SMS
     */
    private suspend fun loadCategoryDataFallback(): List<CategoryItem> {
        return try {
            logger.debug("loadCategoryDataFallback","Loading category data from SMS as fallback...")
            
            // Trigger SMS sync if no data exists
            val syncedCount = repository.syncNewSMS()
            logger.debug("loadCategoryDataFallback","Synced $syncedCount new transactions from SMS")
            
            if (syncedCount > 0) {
                // Now load data from repository
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
                logger.debug("loadCategoryDataFallback","Loading categories fallback from start of month: ${dateFormatter.format(startDate)}")
                
                val categorySpendingResults = repository.getCategorySpending(startDate, endDate)
                
                if (categorySpendingResults.isNotEmpty()) {
                    // Convert and calculate as in main method
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
                                lastTransaction = "",
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
            logger.error("loadCategoryDataFallback", "Error in fallback loading, showing empty state",e)
            return getEmptyCategories()
        }
    }
    
    /**
     * Get empty categories (only custom categories with ₹0 amounts)
     */
    private fun getEmptyCategories(): List<CategoryItem> {
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
    
    /**
     * Update CategoryManager learned rules for category changes
     */
    private suspend fun updateCategoryManagerLearnedRules(oldCategoryName: String, newCategoryName: String) {
        try {
            // Get all learned merchant categories from SharedPreferences
            val categoryPrefs = context.getSharedPreferences("category_rules", Context.MODE_PRIVATE)
            val allKeys = categoryPrefs.all.keys.toList()
            var updatedCount = 0
            
            allKeys.filter { it.startsWith("learned_") }.forEach { key ->
                val learnedCategory = categoryPrefs.getString(key, null)
                if (learnedCategory == oldCategoryName) {
                    categoryPrefs.edit().putString(key, newCategoryName).apply()
                    updatedCount++
                }
            }
            
            logger.debug("updateCategoryManagerLearnedRules","Updated $updatedCount learned rules from '$oldCategoryName' to '$newCategoryName'")
        } catch (e: Exception) {
            logger.error("updateCategoryManagerLearnedRules", "Error updating CategoryManager learned rules",e)
        }
    }
    
    /**
     * COMPREHENSIVE DEBUG: Print all categories from both SharedPreferences and Room Database
     * This helps verify that rename operations are working correctly across both storage systems
     */
    private fun printAllCategoriesFromBothSources() {
        viewModelScope.launch {
            try {
                // 1. ROOM DATABASE CATEGORIES
                logger.debug("printAllCategoriesFromBothSources","ROOM DATABASE CATEGORIES:")
                val dbCategories = repository.getAllCategoriesSync()
                if (dbCategories.isNotEmpty()) {
                    dbCategories.forEachIndexed { index, category ->
                        logger.debug("printAllCategoriesFromBothSources","  DB[$index]: id=${category.id} | name='${category.name}' | emoji='${category.emoji}' | color='${category.color}'")
                    }
                    logger.debug("printAllCategoriesFromBothSources","Total DB categories: ${dbCategories.size}")
                } else {
                    logger.debug("printAllCategoriesFromBothSources","No categories found in Room database")
                }

                val allCategoryManagerCategories = categoryManager.getAllCategories()
                if (allCategoryManagerCategories.isNotEmpty()) {
                    allCategoryManagerCategories.forEachIndexed { index, categoryName ->
                        val color = categoryManager.getCategoryColor(categoryName)
                        logger.debug("printAllCategoriesFromBothSources","  CM[$index]: name='$categoryName' | color='$color'")
                    }
                    logger.debug("printAllCategoriesFromBothSources","Total CategoryManager categories: ${allCategoryManagerCategories.size}")
                } else {
                    logger.debug("printAllCategoriesFromBothSources","No categories found in CategoryManager")
                }

            } catch (e: Exception) {
                logger.error("printAllCategoriesFromBothSources", "Error in comprehensive category debug",e)
            }
        }
    }

    // Add state for filtering and searching
    private var originalCategories: List<CategoryItem> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentSortType: String = "amount_desc"
    private var currentFilterType: String = "all"

    /**
     * Search categories by name or merchant
     */
    private fun searchCategories(query: String) {
        currentSearchQuery = query
        logger.debug("searchCategories","Searching categories with query: '$query'")

        val currentCategories = originalCategories.ifEmpty { _uiState.value.categories }
        if (originalCategories.isEmpty()) {
            originalCategories = currentCategories
        }

        val filteredCategories = if (query.isBlank()) {
            currentCategories
        } else {
            currentCategories.filter { category ->
                category.name.contains(query, ignoreCase = true)
            }
        }

        _uiState.value = _uiState.value.copy(
            categories = applySortAndFilter(filteredCategories),
            isEmpty = filteredCategories.isEmpty()
        )
    }

    /**
     * Sort categories by different criteria
     */
    private fun sortCategories(sortType: String) {
        currentSortType = sortType
        logger.debug("sortCategories","Sorting categories by: $sortType")

        val currentCategories = _uiState.value.categories
        val sortedCategories = when (sortType) {
            "amount_desc" -> currentCategories.sortedByDescending { it.amount }
            "amount_asc" -> currentCategories.sortedBy { it.amount }
            "name_asc" -> currentCategories.sortedBy { it.name }
            "name_desc" -> currentCategories.sortedByDescending { it.name }
            "recent_activity" -> currentCategories.sortedByDescending {
                when {
                    it.lastTransaction.contains("Just now") -> 5
                    it.lastTransaction.contains("hours ago") -> 4
                    it.lastTransaction.contains("Yesterday") -> 3
                    it.lastTransaction.contains("days ago") -> 2
                    else -> 1
                }
            }
            else -> currentCategories
        }

        _uiState.value = _uiState.value.copy(categories = sortedCategories)
    }

    /**
     * Filter categories by type
     */
    private fun filterCategories(filterType: String) {
        currentFilterType = filterType
        logger.debug("filterCategories","Filtering categories by: $filterType")

        val currentCategories = originalCategories.ifEmpty { _uiState.value.categories }
        if (originalCategories.isEmpty()) {
            originalCategories = currentCategories
        }

        val filteredCategories = when (filterType) {
            "all" -> currentCategories
            "has_transactions" -> currentCategories.filter { it.transactionCount > 0 }
            "empty" -> currentCategories.filter { it.transactionCount == 0 }
            "custom" -> currentCategories.filter { !isSystemCategory(it.name) }
            "high_spend" -> {
                val totalSpent = currentCategories.sumOf { it.amount }
                val threshold = totalSpent * 0.1 // Top 10% of spending
                currentCategories.filter { it.amount >= threshold && it.amount > 0 }
            }
            else -> currentCategories
        }

        _uiState.value = _uiState.value.copy(
            categories = applySortAndFilter(filteredCategories),
            isEmpty = filteredCategories.isEmpty()
        )
    }

    /**
     * Apply current sort and filter to a list of categories
     */
    private fun applySortAndFilter(categories: List<CategoryItem>): List<CategoryItem> {
        return when (currentSortType) {
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