package com.smartexpenseai.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.domain.repository.TransactionRepositoryInterface
import com.smartexpenseai.app.domain.repository.CategoryRepositoryInterface
import com.smartexpenseai.app.domain.repository.MerchantRepositoryInterface
import com.smartexpenseai.app.domain.repository.DashboardRepositoryInterface

/**
 * RepositoryModule provides repository dependencies and binds implementations to interfaces.
 * This module handles the binding of domain interfaces to data layer implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    /**
     * Binds ExpenseRepository to TransactionRepositoryInterface
     * This allows use cases to depend on the interface rather than concrete implementation
     */
    @Binds
    abstract fun bindTransactionRepository(
        expenseRepository: ExpenseRepository
    ): TransactionRepositoryInterface
    
    /**
     * Binds ExpenseRepository to CategoryRepositoryInterface
     * Enables category-specific operations through clean architecture interfaces
     */
    @Binds
    abstract fun bindCategoryRepository(
        expenseRepository: ExpenseRepository
    ): CategoryRepositoryInterface
    
    /**
     * Binds ExpenseRepository to MerchantRepositoryInterface
     * Provides merchant operations through domain interface abstraction
     */
    @Binds
    abstract fun bindMerchantRepository(
        expenseRepository: ExpenseRepository
    ): MerchantRepositoryInterface
    
    /**
     * Binds ExpenseRepository to DashboardRepositoryInterface
     * Enables dashboard data operations through clean architecture
     */
    @Binds
    abstract fun bindDashboardRepository(
        expenseRepository: ExpenseRepository
    ): DashboardRepositoryInterface
    
    // Note: AIInsightsRepository doesn't need binding as it's used directly
    // It could have its own interface in future if needed for testing/mocking
}