package com.expensemanager.app.data.dao

import androidx.room.*
import com.expensemanager.app.data.entities.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    /**
     * Get the active monthly budget (categoryId = NULL represents overall budget, not category-specific)
     */
    @Query("SELECT * FROM budgets WHERE category_id IS NULL AND is_active = 1 AND period_type = 'MONTHLY' LIMIT 1")
    suspend fun getMonthlyBudget(): BudgetEntity?

    /**
     * Get monthly budget as Flow for reactive updates
     */
    @Query("SELECT * FROM budgets WHERE category_id IS NULL AND is_active = 1 AND period_type = 'MONTHLY' LIMIT 1")
    fun getMonthlyBudgetFlow(): Flow<BudgetEntity?>

    /**
     * Insert or replace a budget
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    /**
     * Update an existing budget
     */
    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    /**
     * Deactivate all monthly budgets (used before creating a new one)
     */
    @Query("UPDATE budgets SET is_active = 0 WHERE category_id IS NULL AND period_type = 'MONTHLY'")
    suspend fun deactivateAllMonthlyBudgets()

    /**
     * Get budget for a specific category
     */
    @Query("SELECT * FROM budgets WHERE category_id = :categoryId AND is_active = 1 ORDER BY created_at DESC LIMIT 1")
    suspend fun getCategoryBudget(categoryId: Long): BudgetEntity?

    /**
     * Get all active category budgets (excluding overall budget where categoryId = NULL)
     */
    @Query("SELECT * FROM budgets WHERE is_active = 1 AND category_id IS NOT NULL ORDER BY category_id")
    suspend fun getAllActiveCategoryBudgets(): List<BudgetEntity>

    /**
     * Get all active category budgets as Flow
     */
    @Query("SELECT * FROM budgets WHERE is_active = 1 AND category_id IS NOT NULL ORDER BY category_id")
    fun getAllActiveCategoryBudgetsFlow(): Flow<List<BudgetEntity>>

    /**
     * Delete a budget
     */
    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)

    /**
     * Delete all budgets for a specific category
     */
    @Query("DELETE FROM budgets WHERE category_id = :categoryId")
    suspend fun deleteCategoryBudgets(categoryId: Long)
}
