package com.expensemanager.app.domain.repository

import com.expensemanager.app.data.repository.DashboardData
import java.util.Date

/**
 * Repository interface for dashboard data operations in domain layer
 * This interface defines the contract that data repositories must implement
 * Following clean architecture principles by separating domain contracts from data implementations
 */
interface DashboardRepositoryInterface {
    
    // =======================
    // DASHBOARD DATA OPERATIONS
    // =======================
    
    /**
     * Get comprehensive dashboard data for date range
     * Includes total spent, transaction count, top categories, and top merchants
     */
    suspend fun getDashboardData(startDate: Date, endDate: Date): DashboardData
    
    /**
     * Initialize default data (categories, sync state)
     */
    suspend fun initializeDefaultData()
    
    /**
     * Get exclusion states debug info
     */
    suspend fun getExclusionStatesDebugInfo(): String
    
    /**
     * Cleanup duplicate transactions
     */
    suspend fun cleanupDuplicateTransactions(): Int
    
    /**
     * Remove obvious test data
     */
    suspend fun removeObviousTestData(): Int
}