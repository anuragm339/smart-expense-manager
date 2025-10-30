package com.expensemanager.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.expensemanager.app.data.database.ExpenseDatabase
import com.expensemanager.app.data.dao.*
import javax.inject.Singleton

/**
 * DatabaseModule provides database-related dependencies.
 * Handles Room database instance and DAO injections for the entire app.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Provides singleton Room database instance
     * Thread-safe and configured with migrations
     */
    @Provides
    @Singleton
    fun provideExpenseDatabase(
        @ApplicationContext context: Context
    ): ExpenseDatabase {
        return ExpenseDatabase.getDatabase(context)
    }
    
    /**
     * Provides TransactionDao for transaction-related database operations
     */
    @Provides
    @Singleton
    fun provideTransactionDao(
        database: ExpenseDatabase
    ): TransactionDao {
        return database.transactionDao()
    }
    
    /**
     * Provides CategoryDao for category-related database operations
     */
    @Provides
    @Singleton
    fun provideCategoryDao(
        database: ExpenseDatabase
    ): CategoryDao {
        return database.categoryDao()
    }
    
    /**
     * Provides MerchantDao for merchant-related database operations
     */
    @Provides
    @Singleton
    fun provideMerchantDao(
        database: ExpenseDatabase
    ): MerchantDao {
        return database.merchantDao()
    }
    
    /**
     * Provides SyncStateDao for sync state management
     */
    @Provides
    @Singleton
    fun provideSyncStateDao(
        database: ExpenseDatabase
    ): SyncStateDao {
        return database.syncStateDao()
    }

    /**
     * Provides AICallDao for AI call tracking and threshold management
     */
    @Provides
    @Singleton
    fun provideAICallDao(
        database: ExpenseDatabase
    ): AICallDao {
        return database.aiCallDao()
    }

    /**
     * Provides UserDao for user authentication and profile management
     */
    @Provides
    @Singleton
    fun provideUserDao(
        database: ExpenseDatabase
    ): UserDao {
        return database.userDao()
    }

    /**
     * Provides BudgetDao for budget-related database operations
     */
    @Provides
    @Singleton
    fun provideBudgetDao(
        database: ExpenseDatabase
    ): BudgetDao {
        return database.budgetDao()
    }
}