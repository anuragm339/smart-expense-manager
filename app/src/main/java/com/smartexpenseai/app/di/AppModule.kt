package com.smartexpenseai.app.di

import android.content.Context
import android.content.SharedPreferences
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.services.SMSParsingService
import com.smartexpenseai.app.ui.categories.CategoryDisplayProvider
import com.smartexpenseai.app.ui.categories.DefaultCategoryDisplayProvider
import com.smartexpenseai.app.utils.MerchantAliasManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
     * Provides AI insights preferences SharedPreferences
     * Used for AI call threshold tracking and configuration
     */
    @Provides
    @Singleton
    @Named("ai_insights_prefs")
    fun provideAIInsightsPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("ai_insights_prefs", Context.MODE_PRIVATE)
    }

    /**
     * Provides Gson instance for JSON serialization/deserialization
     * Used throughout the app for JSON handling
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setPrettyPrinting()
            .setLenient()
            .create()
    }
    
    /**
     * Provides rule loader for SMS parsing
     */
    @Provides
    @Singleton
    fun provideRuleLoader(
        @ApplicationContext context: Context
    ): com.smartexpenseai.app.parsing.engine.RuleLoader {
        return com.smartexpenseai.app.parsing.engine.RuleLoader(context)
    }

    /**
     * Provides confidence calculator for SMS parsing
     */
    @Provides
    @Singleton
    fun provideConfidenceCalculator(): com.smartexpenseai.app.parsing.engine.ConfidenceCalculator {
        return com.smartexpenseai.app.parsing.engine.ConfidenceCalculator()
    }

    /**
     * Provides unified SMS parser
     */
    @Provides
    @Singleton
    fun provideUnifiedSMSParser(
        ruleLoader: com.smartexpenseai.app.parsing.engine.RuleLoader,
        confidenceCalculator: com.smartexpenseai.app.parsing.engine.ConfidenceCalculator
    ): com.smartexpenseai.app.parsing.engine.UnifiedSMSParser {
        return com.smartexpenseai.app.parsing.engine.UnifiedSMSParser(ruleLoader, confidenceCalculator)
    }

    /**
     * Provides unified SMS parsing service
     * Used for consistent SMS parsing across Dashboard and Messages screens
     */
    @Provides
    @Singleton
    fun provideSMSParsingService(
        @ApplicationContext context: Context,
        unifiedParser: com.smartexpenseai.app.parsing.engine.UnifiedSMSParser
    ): SMSParsingService {
        return SMSParsingService(context, unifiedParser)
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
