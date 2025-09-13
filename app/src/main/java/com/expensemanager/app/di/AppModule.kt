package com.expensemanager.app.di

import android.content.Context
import android.content.SharedPreferences
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.services.SMSParsingService
import com.expensemanager.app.ui.categories.CategoryDisplayProvider
import com.expensemanager.app.ui.categories.DefaultCategoryDisplayProvider
import com.expensemanager.app.utils.AppLogger
import com.expensemanager.app.utils.MerchantAliasManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * AppModule provides application-level dependencies.
 * Handles context, shared preferences, and other app-wide services.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * Provides AI Insights cache SharedPreferences
     * Used for caching AI insights data for offline access
     */
    @Provides
    @Singleton
    @Named("ai_insights_cache")
    fun provideAIInsightsCachePreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("ai_insights_cache", Context.MODE_PRIVATE)
    }
    
    /**
     * Provides budget settings SharedPreferences
     * Used for storing user budget configuration
     */
    @Provides
    @Singleton
    @Named("budget_settings")
    fun provideBudgetSettingsPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("budget_settings", Context.MODE_PRIVATE)
    }
    
    /**
     * Provides app settings SharedPreferences
     * Used for general app configuration like currency, theme, etc.
     */
    @Provides
    @Singleton
    @Named("app_settings")
    fun provideAppSettingsPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }
    
    /**
     * Provides offline AI insights cache SharedPreferences
     * Used for long-term offline caching of AI insights
     */
    @Provides
    @Singleton
    @Named("ai_insights_offline_cache")
    fun provideOfflineInsightsCachePreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("ai_insights_offline_cache", Context.MODE_PRIVATE)
    }
    
    /**
     * Provides unified SMS parsing service
     * Used for consistent SMS parsing across Dashboard and Messages screens
     */
    @Provides
    @Singleton
    fun provideSMSParsingService(
        @ApplicationContext context: Context
    ): SMSParsingService {
        return SMSParsingService(context)
    }
    
    /**
     * Provides centralized application logger using Logback
     * Used for professional logging throughout the application
     */
    @Provides
    @Singleton
    fun provideAppLogger(
        @ApplicationContext context: Context
    ): AppLogger {
        return AppLogger(context)
    }
    
    /**
     * Provides merchant alias manager for merchant name normalization
     * Used for consistent merchant categorization and aliasing
     */
    @Provides
    @Singleton
    fun provideMerchantAliasManager(
        @ApplicationContext context: Context
    ): MerchantAliasManager {
        return MerchantAliasManager(context)
    }
    
    /**
     * Provides category display provider for flexible category visualization
     * Used for managing category icons, emojis, and display formatting
     */
    @Provides
    @Singleton
    fun provideCategoryDisplayProvider(
        @ApplicationContext context: Context,
        repository: ExpenseRepository
    ): CategoryDisplayProvider {
        return DefaultCategoryDisplayProvider(context, repository)
    }
}