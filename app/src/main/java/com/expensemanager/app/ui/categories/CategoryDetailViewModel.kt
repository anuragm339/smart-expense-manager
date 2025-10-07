package com.expensemanager.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.utils.logging.StructuredLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryDetailUIState())
    val uiState: StateFlow<CategoryDetailUIState> = _uiState.asStateFlow()
    private val logger = StructuredLogger("CategoryDetailViewModel", CategoryDetailViewModel.javaClass.name)

    private var currentCategoryName: String = ""

    companion object {
        private const val TAG = "CategoryDetailViewModel"
    }

    fun loadCategoryDetail(categoryName: String) {
        if (currentCategoryName == categoryName && _uiState.value.merchants.isNotEmpty()) {
            // Already loaded this category
            return
        }

        currentCategoryName = categoryName
        _uiState.value = _uiState.value.copy(
            categoryName = categoryName,
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                // Get merchants in this category
                val merchants = repository.getMerchantsInCategory(categoryName)

                // Get category details
                val category = repository.getCategoryByName(categoryName)

                // Calculate totals
                val totalAmount = merchants.sumOf { it.totalAmount }
                val totalTransactions = merchants.sumOf { it.transactionCount }

                _uiState.value = _uiState.value.copy(
                    merchants = merchants,
                    filteredMerchants = merchants,
                    totalAmount = totalAmount,
                    totalTransactions = totalTransactions,
                    categoryColor = category?.color ?: "#6200EE",
                    categoryEmoji = category?.emoji ?: "📊",
                    isLoading = false,
                    error = null
                )

                logger.debug("loadCategoryDetail", String.format("Loaded %d merchants for category %s", merchants.size, categoryName))

            } catch (e: Exception) {
                logger.error("loadCategoryDetail", String.format("Failed to load category detail for %s", categoryName),e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load category details: ${e.message}"
                )
            }
        }
    }

    fun searchMerchants(query: String) {
        val allMerchants = _uiState.value.merchants
        val filteredMerchants = if (query.isBlank()) {
            allMerchants
        } else {
            allMerchants.filter { merchant ->
                merchant.merchantName.contains(query, ignoreCase = true)
            }
        }

        _uiState.value = _uiState.value.copy(
            filteredMerchants = filteredMerchants,
            searchQuery = query
        )
    }

    fun changeMerchantCategory(merchantName: String, newCategoryName: String, applyToFuture: Boolean) {
        viewModelScope.launch {
            try {
                val success = repository.changeMerchantCategory(merchantName, newCategoryName, applyToFuture)
                if (success) {
                    // Reload the category detail to reflect changes
                    loadCategoryDetail(currentCategoryName)
                    logger.debug("changeMerchantCategory",String.format("Changed merchant %s to category %s", merchantName, newCategoryName))
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to change merchant category"
                    )
                }
            } catch (e: Exception) {
                logger.error("changeMerchantCategory", "Failed to change merchant category",e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to change merchant category: ${e.message}"
                )
            }
        }
    }

    fun renameCategory(newName: String, newEmoji: String) {
        viewModelScope.launch {
            try {
                val success = repository.renameCategory(currentCategoryName, newName, newEmoji)
                if (success) {
                    currentCategoryName = newName
                    _uiState.value = _uiState.value.copy(
                        categoryName = newName,
                        categoryEmoji = newEmoji
                    )
                    logger.debug("renameCategory","Renamed category to %s", newName)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to rename category"
                    )
                }
            } catch (e: Exception) {
                logger.error("renameCategory", "Failed to rename category",e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to rename category: ${e.message}"
                )
            }
        }
    }

    fun deleteCategory(reassignToCategoryName: String) {
        viewModelScope.launch {
            try {
                val success = repository.deleteCategory(currentCategoryName, reassignToCategoryName)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        categoryDeleted = true
                    )
                    logger.debug("deleteCategory","Deleted category %s", currentCategoryName)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete category"
                    )
                }
            } catch (e: Exception) {
                logger.error("deleteCategory", "Failed to delete category",e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete category: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun isSystemCategory(): Boolean {
        return com.expensemanager.app.constants.Categories.isSystemCategory(currentCategoryName)
    }
}

data class CategoryDetailUIState(
    val categoryName: String = "",
    val categoryEmoji: String = "📊",
    val categoryColor: String = "#6200EE",
    val merchants: List<MerchantInCategory> = emptyList(),
    val filteredMerchants: List<MerchantInCategory> = emptyList(),
    val totalAmount: Double = 0.0,
    val totalTransactions: Int = 0,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val categoryDeleted: Boolean = false
) {
    val formattedTotalAmount: String
        get() = "₹${String.format("%.0f", totalAmount)}"

    val merchantCountText: String
        get() = when (merchants.size) {
            1 -> "1 merchant"
            else -> "${merchants.size} merchants"
        }

    val transactionCountText: String
        get() = when (totalTransactions) {
            1 -> "1 transaction"
            else -> "$totalTransactions transactions"
        }

    val summaryText: String
        get() = "$merchantCountText • $transactionCountText"
}