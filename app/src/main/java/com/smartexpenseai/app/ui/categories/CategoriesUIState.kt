package com.smartexpenseai.app.ui.categories

/**
 * UI State for Categories screen
 */
data class CategoriesUIState(
    // Loading states
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncingSMS: Boolean = false,
    
    // Data
    val categories: List<CategoryItem> = emptyList(),
    val totalSpent: Double = 0.0,
    val categoryCount: Int = 0,
    
    // State flags
    val isEmpty: Boolean = false,
    val hasError: Boolean = false,
    val error: String? = null,
    val lastRefreshTime: Long = 0L
) {
    /**
     * Computed properties for UI state management
     */
    val shouldShowContent: Boolean
        get() = !isInitialLoading && !hasError && categories.isNotEmpty() && !isEmpty
    
    val shouldShowEmptyState: Boolean
        get() = !isInitialLoading && !hasError && (categories.isEmpty() || isEmpty)
    
    val shouldShowError: Boolean
        get() = !isInitialLoading && hasError && error != null
    
    val isAnyLoading: Boolean
        get() = isInitialLoading || isLoading || isRefreshing || isSyncingSMS
    
    val formattedTotalSpent: String
        get() = "₹${String.format("%.0f", totalSpent)}"
    
    val formattedCategoryCount: String
        get() = "$categoryCount Categories"
}

/**
 * UI Events for Categories interactions
 */
sealed class CategoriesUIEvent {
    object Refresh : CategoriesUIEvent()
    object LoadCategories : CategoriesUIEvent()
    object ClearError : CategoriesUIEvent()
    
    data class AddCategory(val name: String, val emoji: String) : CategoriesUIEvent()
    data class DeleteCategory(val categoryName: String) : CategoriesUIEvent()
    data class RenameCategory(val oldName: String, val newName: String, val newEmoji: String) : CategoriesUIEvent()
    data class QuickAddExpense(
        val amount: Double,
        val merchant: String,
        val category: String
    ) : CategoriesUIEvent()
    data class CategorySelected(val categoryName: String) : CategoriesUIEvent()
    data class SearchCategories(val query: String) : CategoriesUIEvent()
    data class SortCategories(val sortType: String) : CategoriesUIEvent()
    data class FilterCategories(val filterType: String) : CategoriesUIEvent()
}

/**
 * Category item data class (matches existing CategoryItem)
 */
data class CategoryItem(
    val name: String,
    val emoji: String,
    val color: String,
    val amount: Double,
    val transactionCount: Int,
    val lastTransaction: String,
    val percentage: Int,
    val progress: Int
)