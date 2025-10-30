package com.expensemanager.app.domain.usecase.category

import timber.log.Timber
import com.expensemanager.app.data.entities.CategoryEntity
import com.expensemanager.app.domain.repository.CategoryRepositoryInterface
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting categories with business logic
 * Handles filtering, sorting, and transformation of categories
 */
class GetCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepositoryInterface
) {
    
    companion object {
        private const val TAG = "GetCategoriesUseCase"
    }
    
    /**
     * Get all categories as a reactive stream
     */
    fun execute(): Flow<Result<List<CategoryEntity>>> {
        return try {
            kotlinx.coroutines.flow.flow {
                val categories = repository.getAllCategories()
                categories.collect { categoryList ->
                    emit(Result.success(categoryList))
                }
            }
        } catch (e: Exception) {
            kotlinx.coroutines.flow.flowOf(Result.failure(e))
        }
    }
    
    /**
     * Get categories with filtering applied
     */
    fun execute(params: GetCategoriesParams): Flow<Result<List<CategoryEntity>>> {
        return try {
            kotlinx.coroutines.flow.flow {
                val categories = repository.getAllCategories()
                categories.collect { categoryList ->
                    emit(Result.success(processCategories(categoryList, params)))
                }
            }
        } catch (e: Exception) {
            kotlinx.coroutines.flow.flowOf(Result.failure(e))
        }
    }
    
    /**
     * Get all categories synchronously
     */
    suspend fun getAllCategoriesSync(): Result<List<CategoryEntity>> {
        return try {
            Timber.tag(TAG).d("Getting all categories synchronously")
            val categories = repository.getAllCategoriesSync()
            Timber.tag(TAG).d("Retrieved ${categories.size} categories")
            Result.success(categories)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting categories")
            Result.failure(e)
        }
    }
    
    /**
     * Get category by ID
     */
    suspend fun getCategoryById(categoryId: Long): Result<CategoryEntity?> {
        return try {
            Timber.tag(TAG).d("Getting category by ID: $categoryId")
            val category = repository.getCategoryById(categoryId)
            if (category != null) {
                Timber.tag(TAG).d("Found category: ${category.name}")
            } else {
                Timber.tag(TAG).d("Category not found")
            }
            Result.success(category)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting category by ID")
            Result.failure(e)
        }
    }
    
    /**
     * Get category by name
     */
    suspend fun getCategoryByName(name: String): Result<CategoryEntity?> {
        return try {
            Timber.tag(TAG).d("Getting category by name: $name")
            val category = repository.getCategoryByName(name)
            if (category != null) {
                Timber.tag(TAG).d("Found category: ${category.name}")
            } else {
                Timber.tag(TAG).d("Category not found")
            }
            Result.success(category)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting category by name")
            Result.failure(e)
        }
    }
    
    /**
     * Get system categories only
     */
    suspend fun getSystemCategories(): Result<List<CategoryEntity>> {
        return try {
            Timber.tag(TAG).d("Getting system categories")
            val allCategories = repository.getAllCategoriesSync()
            val systemCategories = allCategories.filter { it.isSystem }
            Timber.tag(TAG).d("Found ${systemCategories.size} system categories out of ${allCategories.size} total")
            Result.success(systemCategories)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting system categories")
            Result.failure(e)
        }
    }
    
    /**
     * Get user-created categories only
     */
    suspend fun getUserCategories(): Result<List<CategoryEntity>> {
        return try {
            Timber.tag(TAG).d("Getting user-created categories")
            val allCategories = repository.getAllCategoriesSync()
            val userCategories = allCategories.filter { !it.isSystem }
            Timber.tag(TAG).d("Found ${userCategories.size} user-created categories")
            Result.success(userCategories)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting user categories")
            Result.failure(e)
        }
    }
    
    
    /**
     * Process categories with business logic
     */
    private fun processCategories(categories: List<CategoryEntity>, params: GetCategoriesParams): List<CategoryEntity> {
        Timber.tag(TAG).d("Processing ${categories.size} categories with params: $params")
        
        return categories
            .let { list ->
                // Filter by system status if specified
                when (params.systemOnly) {
                    true -> list.filter { it.isSystem }
                    false -> list.filter { !it.isSystem }
                    null -> list
                }
            }
            .let { list ->
                // Filter by name if specified
                if (params.nameFilter.isNotEmpty()) {
                    list.filter { category ->
                        category.name.contains(params.nameFilter, ignoreCase = true)
                    }
                } else {
                    list
                }
            }
            .let { list ->
                // Sort categories
                when (params.sortOrder) {
                    CategorySortOrder.NAME_ASC -> list.sortedBy { it.name }
                    CategorySortOrder.NAME_DESC -> list.sortedByDescending { it.name }
                    CategorySortOrder.CREATED_ASC -> list.sortedBy { it.createdAt }
                    CategorySortOrder.CREATED_DESC -> list.sortedByDescending { it.createdAt }
                    CategorySortOrder.SYSTEM_FIRST -> list.sortedWith(compareByDescending<CategoryEntity> { it.isSystem }.thenBy { it.name })
                }
            }
            .let { list ->
                // Apply limit if specified
                if (params.limit > 0) {
                    list.take(params.limit)
                } else {
                    list
                }
            }
            .also { processedList ->
                Timber.tag(TAG).d("Processed categories: ${processedList.size} after filtering and sorting")
                
                // Log category summary for debugging
                if (processedList.isNotEmpty()) {
                    val systemCount = processedList.count { it.isSystem }
                    Timber.tag(TAG).d("Category summary: $systemCount system categories")
                }
            }
    }
}

/**
 * Parameters for getting categories
 */
data class GetCategoriesParams(
    val systemOnly: Boolean? = null, // null = all, true = system only, false = user-created only
    val nameFilter: String = "",
    val sortOrder: CategorySortOrder = CategorySortOrder.NAME_ASC,
    val limit: Int = 0 // 0 means no limit
)

/**
 * Sort order options for categories
 */
enum class CategorySortOrder {
    NAME_ASC, NAME_DESC, CREATED_ASC, CREATED_DESC, SYSTEM_FIRST
}