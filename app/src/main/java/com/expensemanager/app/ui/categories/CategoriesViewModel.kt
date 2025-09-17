package com.expensemanager.app.ui.categories

import android.content.Context
import android.util.Log
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
            is CategoriesUIEvent.DeleteCategory -> deleteCategory(event.categoryName)
            is CategoriesUIEvent.RenameCategory -> renameCategory(event.oldName, event.newName, event.newEmoji)
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
        
        // Clear display provider cache to ensure fresh emoji data
        if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
            categoryDisplayProvider.clearCache()
            Log.d(TAG, "Cleared CategoryDisplayProvider cache")
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
                Log.d(TAG, "Category '$name' saved to database with ID: $categoryId")
                
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
                    Log.d(TAG, "Cleared CategoryDisplayProvider cache for '$name' after add")
                }
                
                Log.d(TAG, "Category '$name' added successfully")
                
                // DEBUG: Print all categories from database to verify persistence
                try {
                    val allDbCategories = repository.getAllCategoriesSync()
                    Log.d(TAG, "=== ALL CATEGORIES IN DATABASE AFTER CREATION ===")
                    allDbCategories.forEachIndexed { index, category ->
                        Log.d(TAG, "DB Category $index: id=${category.id}, name='${category.name}', emoji='${category.emoji}'")
                    }
                    Log.d(TAG, "=== END DATABASE CATEGORIES (${allDbCategories.size} total) ===")
                } catch (e: Exception) {
                    Log.e(TAG, "Error printing database categories", e)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding category", e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Delete a category comprehensively across all storage locations
     */
    private fun deleteCategory(categoryName: String) {
        Log.d(TAG, "Deleting category: $categoryName")
        
        viewModelScope.launch {
            try {
                // 1. Remove from CategoryManager (SharedPreferences custom categories)
                categoryManager.removeCustomCategory(categoryName)
                Log.d(TAG, "Removed '$categoryName' from CategoryManager")
                
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
                        Log.d(TAG, "Created 'Other' category as fallback")
                    }
                    
                    if (otherCategory != null) {
                        // Move all merchants with this category to "Other"
                        val movedCount = repository.updateMerchantsByCategory(categoryEntity.id, otherCategory.id)
                        Log.d(TAG, "Moved $movedCount merchants from '$categoryName' to 'Other' in database")
                    }
                    
                    // Delete the category from database
                    repository.deleteCategory(categoryEntity)
                    Log.d(TAG, "Deleted '$categoryName' from database")
                } else {
                    Log.d(TAG, "Category '$categoryName' not found in database, only removing from SharedPreferences")
                }
                
                // 3. Update MerchantAliasManager - change all aliases pointing to this category
                updateMerchantAliasesForCategoryChange(categoryName, "Other")
                
                // 4. Update CategoryManager learned rules
                updateCategoryManagerLearnedRules(categoryName, "Other")
                
                // 5. Clear display provider cache to force icon/emoji refresh
                if (categoryDisplayProvider is DefaultCategoryDisplayProvider) {
                    categoryDisplayProvider.clearCacheForCategory(categoryName)
                    Log.d(TAG, "Cleared CategoryDisplayProvider cache for '$categoryName' after delete")
                }
                
                // 6. Refresh categories to get updated data from repository
                refreshCategories()
                
                Log.d(TAG, "Category '$categoryName' deleted successfully from all locations")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting category", e)
                handleCategoryError(e)
            }
        }
    }
    
    /**
     * Rename a category comprehensively across all storage locations
     */
    private fun renameCategory(oldName: String, newName: String, newEmoji: String) {
        Log.d(TAG, "Renaming category from '$oldName' to '$newName' with emoji '$newEmoji'")
        
        viewModelScope.launch {
            try {
                // 1. Update CategoryManager (SharedPreferences custom categories)
                categoryManager.removeCustomCategory(oldName)
                categoryManager.addCustomCategory(newName)
                Log.d(TAG, "Updated '$oldName' to '$newName' in CategoryManager")
                
                // 2. Handle database category rename
                val categoryEntity = repository.getCategoryByName(oldName)
                if (categoryEntity != null) {
                    // Update the category name and emoji in database
                    val updatedCategory = categoryEntity.copy(
                        name = newName,
                        emoji = newEmoji.ifEmpty { categoryEntity.emoji }
                    )
                    repository.updateCategory(updatedCategory)
                    Log.d(TAG, "Updated '$oldName' to '$newName' in database")
                    
                    // DEBUG: Verify the update worked
                    val verifyCategory = repository.getCategoryByName(newName)
                    Log.d(TAG, "VERIFY: Category '$newName' now has emoji: '${verifyCategory?.emoji}'")
                } else {
                    // Category doesn't exist in database, create it
                    Log.d(TAG, "Category '$oldName' not found in database, creating new entry for '$newName'")
                    val newCategoryEntity = com.expensemanager.app.data.entities.CategoryEntity(
                        name = newName,
                        emoji = newEmoji,
                        color = getRandomCategoryColor(),
                        isSystem = false,
                        displayOrder = 999,
                        createdAt = java.util.Date()
                    )
                    repository.insertCategory(newCategoryEntity)
                    Log.d(TAG, "Created new category '$newName' in database")
                    
                    // DEBUG: Verify the creation worked
                    val verifyCategory = repository.getCategoryByName(newName)
                    Log.d(TAG, "VERIFY: Created category '$newName' has emoji: '${verifyCategory?.emoji}'")
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
                    Log.d(TAG, "Cleared CategoryDisplayProvider cache for '$oldName' and '$newName' after rename")
                }
                
                // 6. Refresh categories to get updated data from repository
                refreshCategories()
                
                Log.d(TAG, "Category renamed from '$oldName' to '$newName' successfully across all locations")
                
                // DEBUG: Log that the display provider fix has been applied
                Log.d(TAG, "=== DISPLAY PROVIDER FIX APPLIED ===")
                Log.d(TAG, "getCategoryEmoji now uses CategoryDisplayProvider (database-aware)")
                Log.d(TAG, "refreshCategories now clears display cache")
                Log.d(TAG, "Expected: UI should show updated emojis immediately")
                
                // COMPREHENSIVE DEBUG: Print all categories from both sources
                printAllCategoriesFromBothSources()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming category", e)
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
            
            Log.d(TAG, "Updated $updatedCount merchant aliases from '$oldCategoryName' to '$newCategoryName'")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating merchant aliases for category change", e)
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
            
            Log.d(TAG, "Updated $updatedCount learned rules from '$oldCategoryName' to '$newCategoryName'")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating CategoryManager learned rules", e)
        }
    }
    
    /**
     * COMPREHENSIVE DEBUG: Print all categories from both SharedPreferences and Room Database
     * This helps verify that rename operations are working correctly across both storage systems
     */
    private fun printAllCategoriesFromBothSources() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "")
                Log.d(TAG, "=================== COMPREHENSIVE CATEGORY DEBUG ===================")
                Log.d(TAG, "")
                
                // 1. ROOM DATABASE CATEGORIES
                Log.d(TAG, "📁 ROOM DATABASE CATEGORIES:")
                val dbCategories = repository.getAllCategoriesSync()
                if (dbCategories.isNotEmpty()) {
                    dbCategories.forEachIndexed { index, category ->
                        Log.d(TAG, "  DB[$index]: id=${category.id} | name='${category.name}' | emoji='${category.emoji}' | color='${category.color}'")
                    }
                    Log.d(TAG, "  📊 Total DB categories: ${dbCategories.size}")
                } else {
                    Log.d(TAG, "  ❌ No categories found in Room database")
                }
                
                Log.d(TAG, "")
                
                // 2. CATEGORYMANAGER (SHAREDPREFERENCES) ALL CATEGORIES
                Log.d(TAG, "💾 CATEGORYMANAGER (SharedPreferences) ALL CATEGORIES:")
                val allCategoryManagerCategories = categoryManager.getAllCategories()
                if (allCategoryManagerCategories.isNotEmpty()) {
                    allCategoryManagerCategories.forEachIndexed { index, categoryName ->
                        val color = categoryManager.getCategoryColor(categoryName)
                        Log.d(TAG, "  CM[$index]: name='$categoryName' | color='$color'")
                    }
                    Log.d(TAG, "  📊 Total CategoryManager categories: ${allCategoryManagerCategories.size}")
                } else {
                    Log.d(TAG, "  ❌ No categories found in CategoryManager")
                }
                
                Log.d(TAG, "")
                
                // 3. CATEGORYDISPLAYPROVIDER TEST
                Log.d(TAG, "🎨 CATEGORY DISPLAY PROVIDER TEST:")
                val testCategories = listOf("ABc1", "TestCat", "Food & Dining", "Other")
                testCategories.forEach { testCategory ->
                    val displayIcon = categoryDisplayProvider.getDisplayIcon(testCategory)
                    val formattedDisplay = categoryDisplayProvider.formatForDisplay(testCategory)
                    val iconType = when (displayIcon) {
                        is com.expensemanager.app.ui.categories.CategoryIcon.Emoji -> "Emoji: '${displayIcon.emoji}'"
                        else -> "Other: $displayIcon"
                    }
                    Log.d(TAG, "  DISP['$testCategory']: $iconType | formatted='$formattedDisplay'")
                }
                
                Log.d(TAG, "")
                Log.d(TAG, "================== END COMPREHENSIVE DEBUG ==================")
                Log.d(TAG, "")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in comprehensive category debug", e)
            }
        }
    }
}