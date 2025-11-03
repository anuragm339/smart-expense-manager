package com.smartexpenseai.app.domain.repository

import com.smartexpenseai.app.data.entities.CategoryEntity
import com.smartexpenseai.app.data.dao.CategorySpendingResult
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Repository interface for category data operations in domain layer
 * This interface defines the contract that data repositories must implement
 * Following clean architecture principles by separating domain contracts from data implementations
 */
interface CategoryRepositoryInterface {
    
    // =======================
    // READ OPERATIONS
    // =======================
    
    /**
     * Get all categories as a reactive stream
     */
    suspend fun getAllCategories(): Flow<List<CategoryEntity>>
    
    /**
     * Get all categories synchronously
     */
    suspend fun getAllCategoriesSync(): List<CategoryEntity>
    
    /**
     * Get category by ID
     */
    suspend fun getCategoryById(categoryId: Long): CategoryEntity?
    
    /**
     * Get category by name
     */
    suspend fun getCategoryByName(name: String): CategoryEntity?
    
    /**
     * Get category spending breakdown for date range
     */
    suspend fun getCategorySpending(startDate: Date, endDate: Date): List<CategorySpendingResult>
    
    // =======================
    // WRITE OPERATIONS
    // =======================
    
    /**
     * Insert a new category
     */
    suspend fun insertCategory(category: CategoryEntity): Long
    
    /**
     * Update existing category
     */
    suspend fun updateCategory(category: CategoryEntity)
    
    /**
     * Delete category
     */
    suspend fun deleteCategory(category: CategoryEntity)
}