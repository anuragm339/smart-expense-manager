package com.expensemanager.app.di

import android.content.Context
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.utils.logging.LoggingManager
import com.expensemanager.app.utils.logging.TimberFileTree
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing logging-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    
    /**
     * Provides LogConfig singleton for managing logging configuration
     */
    @Provides
    @Singleton
    fun provideLogConfig(
        @ApplicationContext context: Context
    ): LogConfig {
        return LogConfig(context)
    }
    
    /**
     * Provides TimberFileTree singleton for file-based logging
     */
    @Provides
    @Singleton
    fun provideTimberFileTree(
        @ApplicationContext context: Context,
        logConfig: LogConfig
    ): TimberFileTree {
        return TimberFileTree(context, logConfig)
    }
    
    /**
     * Provides LoggingManager singleton for runtime logging control
     */
    @Provides
    @Singleton
    fun provideLoggingManager(
        @ApplicationContext context: Context,
        logConfig: LogConfig,
        timberFileTree: TimberFileTree
    ): LoggingManager {
        return LoggingManager(context, logConfig, timberFileTree)
    }
}