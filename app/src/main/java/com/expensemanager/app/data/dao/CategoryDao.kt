package com.expensemanager.app.data.dao

import androidx.room.*
import com.expensemanager.app.data.entities.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    
    @Query("SELECT * FROM categories ORDER BY display_order ASC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories ORDER BY display_order ASC, name ASC")
    suspend fun getAllCategoriesSync(): List<CategoryEntity>
    
    @Query("SELECT * FROM categories WHERE is_system = 1 ORDER BY display_order ASC")
    suspend fun getSystemCategories(): List<CategoryEntity>
    
    @Query("SELECT * FROM categories WHERE is_system = 0 ORDER BY name ASC")
    suspend fun getUserCategories(): List<CategoryEntity>
    
    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Long): CategoryEntity?
    
    @Query("SELECT * FROM categories WHERE name = :name")
    suspend fun getCategoryByName(name: String): CategoryEntity?
    
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>): List<Long>
    
    @Update
    suspend fun updateCategory(category: CategoryEntity)
    
    @Delete
    suspend fun deleteCategory(category: CategoryEntity)
    
    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Long)
    
    @Query("UPDATE categories SET display_order = :order WHERE id = :categoryId")
    suspend fun updateCategoryOrder(categoryId: Long, order: Int)
}