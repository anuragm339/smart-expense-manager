package com.expensemanager.app.ui.categories

import android.content.Context
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.ui.categories.CategoryDisplayProvider
import com.expensemanager.app.ui.categories.DefaultCategoryDisplayProvider
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
    private val merchantAliasManager = MerchantAliasManager(context)
    
    init {
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("ViewModel initialized, loading categories...")
        loadCategories()
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: CategoriesUIEvent) {
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Handling event: $event")
        
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
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Starting initial category load...")
        
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
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error loading categories")
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Refresh categories (pull-to-refresh)
     */
    private fun refreshCategories() {
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Refreshing categories...")
        
        // Clear display provider cache to ensure fresh emoji data
        if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
            categoryDisplayProvider.clearCache()
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Cleared CategoryDisplayProvider cache")
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
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error refreshing categories")
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Add a new category
     */
    private fun addNewCategory(name: String, emoji: String) {
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Adding new category: $name with emoji: $emoji")
        
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
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Category '$name' saved to database with ID: $categoryId")
                
                // Create new category item
                val newCategory = CategoryItem(
                    name = name,
                    emoji = emoji,
                    color = categoryColor,
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
                
                // Clear display provider cache to ensure new category gets proper icon
                if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
                    categoryDisplayProvider.clearCacheForCategory(name)
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Cleared CategoryDisplayProvider cache for '$name' after add")
                }
                
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Category '$name' added successfully")
                
                // DEBUG: Print all categories from database to verify persistence
                try {
                    val allDbCategories = repository.getAllCategoriesSync()
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("=== ALL CATEGORIES IN DATABASE AFTER CREATION ===")
                    allDbCategories.forEachIndexed { index, category ->
                        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("DB Category $index: id=${category.id}, name='${category.name}', emoji='${category.emoji}'")
                    }
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("=== END DATABASE CATEGORIES (${allDbCategories.size} total) ===")
                } catch (e: Exception) {
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error printing database categories")
                }
                
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error adding category")
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Delete a category comprehensively across all storage locations
     */
    private fun deleteCategory(categoryName: String) {
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Deleting category: $categoryName")
        
        viewModelScope.launch {
            try {
                // 1. Remove from CategoryManager (SharedPreferences custom categories)
                categoryManager.removeCustomCategory(categoryName)
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Removed '$categoryName' from CategoryManager")
                
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
                        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Created 'Other' category as fallback")
                    }
                    
                    if (otherCategory != null) {
                        // Move all merchants with this category to "Other"
                        val movedCount = repository.updateMerchantsByCategory(categoryEntity.id, otherCategory.id)
                        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Moved $movedCount merchants from '$categoryName' to 'Other' in database")
                    }
                    
                    // Delete the category from database
                    repository.deleteCategory(categoryEntity)
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Deleted '$categoryName' from database")
                } else {
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Category '$categoryName' not found in database, only removing from SharedPreferences")
                }
                
                // 3. Update MerchantAliasManager - change all aliases pointing to this category
                updateMerchantAliasesForCategoryChange(categoryName, "Other")
                
                // 4. Update CategoryManager learned rules
                updateCategoryManagerLearnedRules(categoryName, "Other")
                
                // 5. Clear display provider cache to force icon/emoji refresh
                if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
                    categoryDisplayProvider.clearCacheForCategory(categoryName)
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Cleared CategoryDisplayProvider cache for '$categoryName' after delete")
                }
                
                // 6. Refresh categories to get updated data from repository
                refreshCategories()
                
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Category '$categoryName' deleted successfully from all locations")
                
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error deleting category")
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Rename a category comprehensively across all storage locations
     */
    private fun renameCategory(oldName: String, newName: String, newEmoji: String) {
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Renaming category from '$oldName' to '$newName' with emoji '$newEmoji'")
        
        viewModelScope.launch {
            try {
                // 1. Update CategoryManager (SharedPreferences custom categories)
                categoryManager.removeCustomCategory(oldName)
                categoryManager.addCustomCategory(newName)
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Updated '$oldName' to '$newName' in CategoryManager")
                
                // 2. Handle database category rename
                val categoryEntity = repository.getCategoryByName(oldName)
                if (categoryEntity != null) {
                    // Update the category name and emoji in database
                    val updatedCategory = categoryEntity.copy(
                        name = newName,
                        emoji = newEmoji.ifEmpty { categoryEntity.emoji }
                    )
                    repository.updateCategory(updatedCategory)
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Updated '$oldName' to '$newName' in database")
                    
                    // DEBUG: Verify the update worked
                    val verifyCategory = repository.getCategoryByName(newName)
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("VERIFY: Category '$newName' now has emoji: '${verifyCategory?.emoji}'")
                } else {
                    // Category doesn't exist in database, create it
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Category '$oldName' not found in database, creating new entry for '$newName'")
                    val newCategoryEntity = com.expensemanager.app.data.entities.CategoryEntity(
                        name = newName,
                        emoji = newEmoji,
                        color = getRandomCategoryColor(),
                        isSystem = false,
                        displayOrder = 999,
                        createdAt = java.util.Date()
                    )
                    repository.insertCategory(newCategoryEntity)
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Created new category '$newName' in database")
                    
                    // DEBUG: Verify the creation worked
                    val verifyCategory = repository.getCategoryByName(newName)
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("VERIFY: Created category '$newName' has emoji: '${verifyCategory?.emoji}'")
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
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Cleared CategoryDisplayProvider cache for '$oldName' and '$newName' after rename")
                }
                
                // 6. Refresh categories to get updated data from repository
                refreshCategories()
                
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Category renamed from '$oldName' to '$newName' successfully across all locations")
                
                // DEBUG: Log that the display provider fix has been applied
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("=== DISPLAY PROVIDER FIX APPLIED ===")
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("getCategoryEmoji now uses CategoryDisplayProvider (database-aware)")
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("refreshCategories now clears display cache")
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Expected: UI should show updated emojis immediately")
                
                // COMPREHENSIVE DEBUG: Print all categories from both sources
                printAllCategoriesFromBothSources()
                
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error renaming category")
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Add quick expense
     */
    private fun addQuickExpense(amount: Double, merchant: String, category: String) {
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Adding quick expense: ₹$amount at $merchant ($category)")
        
        viewModelScope.launch {
            try {
                // TODO: Implement with proper repository method when available
                // For now, just refresh categories to show updated data
                refreshCategories()
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error adding quick expense")
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Handle category selection for navigation
     */
    private fun handleCategorySelection(categoryName: String) {
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Category selected: $categoryName")
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
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Loading category data from repository...")
            
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
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Loading categories from start of month: ${dateFormatter.format(startDate)}")
            
            // Get category spending from repository
            val categorySpendingResults = repository.getCategorySpending(startDate, endDate)
            
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Repository returned ${categorySpendingResults.size} category results")
            
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
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("No data in repository, falling back to SMS reading...")
                return loadCategoryDataFallback()
            }
            
        } catch (e: SecurityException) {
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).w(e, "Permission denied, showing empty state")
            return getEmptyCategories()
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error loading category data, showing empty state")
            return getEmptyCategories()
        }
    }
    
    /**
     * Fallback category loading from SMS
     */
    private suspend fun loadCategoryDataFallback(): List<CategoryItem> {
        return try {
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Loading category data from SMS as fallback...")
            
            // Trigger SMS sync if no data exists
            val syncedCount = repository.syncNewSMS()
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Synced $syncedCount new transactions from SMS")
            
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
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Loading categories fallback from start of month: ${dateFormatter.format(startDate)}")
                
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
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error in fallback loading, showing empty state")
            return getEmptyCategories()
        }
    }
    
    /**
     * Get empty categories (only custom categories with ₹0 amounts)
     */
    private fun getEmptyCategories(): List<CategoryItem> {
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Showing empty categories state - no dummy data")
        
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
            
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Updated $updatedCount merchant aliases from '$oldCategoryName' to '$newCategoryName'")
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error updating merchant aliases for category change")
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
            
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Updated $updatedCount learned rules from '$oldCategoryName' to '$newCategoryName'")
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error updating CategoryManager learned rules")
        }
    }
    
    /**
     * COMPREHENSIVE DEBUG: Print all categories from both SharedPreferences and Room Database
     * This helps verify that rename operations are working correctly across both storage systems
     */
    private fun printAllCategoriesFromBothSources() {
        viewModelScope.launch {
            try {
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("")
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("=================== COMPREHENSIVE CATEGORY DEBUG ===================")
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("")
                
                // 1. ROOM DATABASE CATEGORIES
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("📁 ROOM DATABASE CATEGORIES:")
                val dbCategories = repository.getAllCategoriesSync()
                if (dbCategories.isNotEmpty()) {
                    dbCategories.forEachIndexed { index, category ->
                        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("  DB[$index]: id=${category.id} | name='${category.name}' | emoji='${category.emoji}' | color='${category.color}'")
                    }
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("  📊 Total DB categories: ${dbCategories.size}")
                } else {
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("  ❌ No categories found in Room database")
                }
                
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("")
                
                // 2. CATEGORYMANAGER (SHAREDPREFERENCES) ALL CATEGORIES
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("💾 CATEGORYMANAGER (SharedPreferences) ALL CATEGORIES:")
                val allCategoryManagerCategories = categoryManager.getAllCategories()
                if (allCategoryManagerCategories.isNotEmpty()) {
                    allCategoryManagerCategories.forEachIndexed { index, categoryName ->
                        val color = categoryManager.getCategoryColor(categoryName)
                        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("  CM[$index]: name='$categoryName' | color='$color'")
                    }
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("  📊 Total CategoryManager categories: ${allCategoryManagerCategories.size}")
                } else {
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("  ❌ No categories found in CategoryManager")
                }
                
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("")
                
                // 3. CATEGORYDISPLAYPROVIDER TEST
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("🎨 CATEGORY DISPLAY PROVIDER TEST:")
                val testCategories = listOf("ABc1", "TestCat", "Food & Dining", "Other")
                testCategories.forEach { testCategory ->
                    val displayIcon = categoryDisplayProvider.getDisplayIcon(testCategory)
                    val formattedDisplay = categoryDisplayProvider.formatForDisplay(testCategory)
                    val iconType = when (displayIcon) {
                        is com.expensemanager.app.ui.categories.CategoryIcon.Emoji -> "Emoji: '${displayIcon.emoji}'"
                        else -> "Other: $displayIcon"
                    }
                    Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("  DISP['$testCategory']: $iconType | formatted='$formattedDisplay'")
                }
                
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("")
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("================== END COMPREHENSIVE DEBUG ==================")
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("")
                
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.CATEGORIES).e(e, "Error in comprehensive category debug")
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
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Searching categories with query: '$query'")

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
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Sorting categories by: $sortType")

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
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Filtering categories by: $filterType")

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