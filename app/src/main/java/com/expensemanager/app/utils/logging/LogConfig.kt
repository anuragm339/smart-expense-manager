package com.expensemanager.app.utils.logging

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration manager for feature-specific logging
 * Allows enabling/disabling logs for specific app features at runtime
 */
@Singleton
class LogConfig @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val PREF_NAME = "timber_logging_config"
        private const val KEY_GLOBAL_LOGGING_ENABLED = "global_logging_enabled"
        private const val KEY_FILE_LOGGING_ENABLED = "file_logging_enabled"
        private const val KEY_EXTERNAL_LOGGING_ENABLED = "external_logging_enabled"
        private const val KEY_LOG_LEVEL = "log_level"
        
        // Feature-specific keys
        private const val KEY_DASHBOARD_LOGS = "dashboard_logs_enabled"
        private const val KEY_SMS_LOGS = "sms_logs_enabled"
        private const val KEY_TRANSACTION_LOGS = "transaction_logs_enabled"
        private const val KEY_CATEGORIES_LOGS = "categories_logs_enabled"
        private const val KEY_DATABASE_LOGS = "database_logs_enabled"
        private const val KEY_NETWORK_LOGS = "network_logs_enabled"
        private const val KEY_UI_LOGS = "ui_logs_enabled"
        private const val KEY_MERCHANT_LOGS = "merchant_logs_enabled"
        private const val KEY_INSIGHTS_LOGS = "insights_logs_enabled"
        private const val KEY_MIGRATION_LOGS = "migration_logs_enabled"
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * Feature tags for logging
     */
    object FeatureTags {
        const val DASHBOARD = "DASHBOARD"
        const val SMS = "SMS"
        const val TRANSACTION = "TRANSACTION"
        const val CATEGORIES = "CATEGORIES"
        const val DATABASE = "DATABASE"
        const val NETWORK = "NETWORK"
        const val UI = "UI"
        const val MERCHANT = "MERCHANT"
        const val INSIGHTS = "INSIGHTS"
        const val MIGRATION = "MIGRATION"
        const val APP = "APP"
        const val ERROR = "ERROR"
    }
    
    /**
     * Log levels
     */
    object LogLevel {
        const val VERBOSE = Log.VERBOSE
        const val DEBUG = Log.DEBUG
        const val INFO = Log.INFO
        const val WARN = Log.WARN
        const val ERROR = Log.ERROR
    }
    
    // Global logging settings
    var isGlobalLoggingEnabled: Boolean
        get() = preferences.getBoolean(KEY_GLOBAL_LOGGING_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_GLOBAL_LOGGING_ENABLED, value).apply()
    
    var isFileLoggingEnabled: Boolean
        get() = preferences.getBoolean(KEY_FILE_LOGGING_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_FILE_LOGGING_ENABLED, value).apply()
    
    var isExternalLoggingEnabled: Boolean
        get() = preferences.getBoolean(KEY_EXTERNAL_LOGGING_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_EXTERNAL_LOGGING_ENABLED, value).apply()
    
    var logLevel: Int
        get() = preferences.getInt(KEY_LOG_LEVEL, Log.DEBUG)
        set(value) = preferences.edit().putInt(KEY_LOG_LEVEL, value).apply()
    
    // Feature-specific logging settings
    var isDashboardLogsEnabled: Boolean
        get() = preferences.getBoolean(KEY_DASHBOARD_LOGS, true)
        set(value) = preferences.edit().putBoolean(KEY_DASHBOARD_LOGS, value).apply()
    
    var isSmsLogsEnabled: Boolean
        get() = preferences.getBoolean(KEY_SMS_LOGS, true)
        set(value) = preferences.edit().putBoolean(KEY_SMS_LOGS, value).apply()
    
    var isTransactionLogsEnabled: Boolean
        get() = preferences.getBoolean(KEY_TRANSACTION_LOGS, true)
        set(value) = preferences.edit().putBoolean(KEY_TRANSACTION_LOGS, value).apply()
    
    var isCategoriesLogsEnabled: Boolean
        get() = preferences.getBoolean(KEY_CATEGORIES_LOGS, true)
        set(value) = preferences.edit().putBoolean(KEY_CATEGORIES_LOGS, value).apply()
    
    var isDatabaseLogsEnabled: Boolean
        get() = preferences.getBoolean(KEY_DATABASE_LOGS, true)
        set(value) = preferences.edit().putBoolean(KEY_DATABASE_LOGS, value).apply()
    
    var isNetworkLogsEnabled: Boolean
        get() = preferences.getBoolean(KEY_NETWORK_LOGS, true)
        set(value) = preferences.edit().putBoolean(KEY_NETWORK_LOGS, value).apply()
    
    var isUiLogsEnabled: Boolean
        get() = preferences.getBoolean(KEY_UI_LOGS, true)
        set(value) = preferences.edit().putBoolean(KEY_UI_LOGS, value).apply()
    
    var isMerchantLogsEnabled: Boolean
        get() = preferences.getBoolean(KEY_MERCHANT_LOGS, true)
        set(value) = preferences.edit().putBoolean(KEY_MERCHANT_LOGS, value).apply()
    
    var isInsightsLogsEnabled: Boolean
        get() = preferences.getBoolean(KEY_INSIGHTS_LOGS, true)
        set(value) = preferences.edit().putBoolean(KEY_INSIGHTS_LOGS, value).apply()
    
    var isMigrationLogsEnabled: Boolean
        get() = preferences.getBoolean(KEY_MIGRATION_LOGS, true)
        set(value) = preferences.edit().putBoolean(KEY_MIGRATION_LOGS, value).apply()
    
    /**
     * Check if logging is enabled for a specific feature
     */
    fun isFeatureLoggingEnabled(featureTag: String): Boolean {
        if (!isGlobalLoggingEnabled) return false
        
        return when (featureTag) {
            FeatureTags.DASHBOARD -> isDashboardLogsEnabled
            FeatureTags.SMS -> isSmsLogsEnabled
            FeatureTags.TRANSACTION -> isTransactionLogsEnabled
            FeatureTags.CATEGORIES -> isCategoriesLogsEnabled
            FeatureTags.DATABASE -> isDatabaseLogsEnabled
            FeatureTags.NETWORK -> isNetworkLogsEnabled
            FeatureTags.UI -> isUiLogsEnabled
            FeatureTags.MERCHANT -> isMerchantLogsEnabled
            FeatureTags.INSIGHTS -> isInsightsLogsEnabled
            FeatureTags.MIGRATION -> isMigrationLogsEnabled
            FeatureTags.APP, FeatureTags.ERROR -> true // Always enabled
            else -> true // Unknown tags are enabled by default
        }
    }
    
    /**
     * Check if logging is enabled for specific priority and feature
     */
    fun shouldLog(priority: Int, featureTag: String): Boolean {
        return priority >= logLevel && isFeatureLoggingEnabled(featureTag)
    }
    
    /**
     * Enable all feature logs (useful for debugging)
     */
    fun enableAllFeatures() {
        preferences.edit().apply {
            putBoolean(KEY_DASHBOARD_LOGS, true)
            putBoolean(KEY_SMS_LOGS, true)
            putBoolean(KEY_TRANSACTION_LOGS, true)
            putBoolean(KEY_CATEGORIES_LOGS, true)
            putBoolean(KEY_DATABASE_LOGS, true)
            putBoolean(KEY_NETWORK_LOGS, true)
            putBoolean(KEY_UI_LOGS, true)
            putBoolean(KEY_MERCHANT_LOGS, true)
            putBoolean(KEY_INSIGHTS_LOGS, true)
            putBoolean(KEY_MIGRATION_LOGS, true)
        }.apply()
    }
    
    /**
     * Disable all feature logs except critical ones
     */
    fun disableAllFeatures() {
        preferences.edit().apply {
            putBoolean(KEY_DASHBOARD_LOGS, false)
            putBoolean(KEY_SMS_LOGS, false)
            putBoolean(KEY_TRANSACTION_LOGS, false)
            putBoolean(KEY_CATEGORIES_LOGS, false)
            putBoolean(KEY_DATABASE_LOGS, false)
            putBoolean(KEY_NETWORK_LOGS, false)
            putBoolean(KEY_UI_LOGS, false)
            putBoolean(KEY_MERCHANT_LOGS, false)
            putBoolean(KEY_INSIGHTS_LOGS, false)
            putBoolean(KEY_MIGRATION_LOGS, false)
        }.apply()
    }
    
    /**
     * Enable only specific features for focused debugging
     */
    fun enableOnlyFeatures(vararg features: String) {
        disableAllFeatures()
        features.forEach { feature ->
            when (feature) {
                FeatureTags.DASHBOARD -> isDashboardLogsEnabled = true
                FeatureTags.SMS -> isSmsLogsEnabled = true
                FeatureTags.TRANSACTION -> isTransactionLogsEnabled = true
                FeatureTags.CATEGORIES -> isCategoriesLogsEnabled = true
                FeatureTags.DATABASE -> isDatabaseLogsEnabled = true
                FeatureTags.NETWORK -> isNetworkLogsEnabled = true
                FeatureTags.UI -> isUiLogsEnabled = true
                FeatureTags.MERCHANT -> isMerchantLogsEnabled = true
                FeatureTags.INSIGHTS -> isInsightsLogsEnabled = true
                FeatureTags.MIGRATION -> isMigrationLogsEnabled = true
            }
        }
    }
    
    /**
     * Get current configuration as a readable string
     */
    fun getCurrentConfig(): String {
        return buildString {
            appendLine("Timber Logging Configuration:")
            appendLine("Global Logging: ${if (isGlobalLoggingEnabled) "ENABLED" else "DISABLED"}")
            appendLine("File Logging: ${if (isFileLoggingEnabled) "ENABLED" else "DISABLED"}")
            appendLine("External Logging: ${if (isExternalLoggingEnabled) "ENABLED" else "DISABLED"}")
            appendLine("Log Level: ${getLogLevelString(logLevel)}")
            appendLine("")
            appendLine("Feature-Specific Logging:")
            appendLine("  Dashboard: ${if (isDashboardLogsEnabled) "ON" else "OFF"}")
            appendLine("  SMS: ${if (isSmsLogsEnabled) "ON" else "OFF"}")
            appendLine("  Transaction: ${if (isTransactionLogsEnabled) "ON" else "OFF"}")
            appendLine("  Categories: ${if (isCategoriesLogsEnabled) "ON" else "OFF"}")
            appendLine("  Database: ${if (isDatabaseLogsEnabled) "ON" else "OFF"}")
            appendLine("  Network: ${if (isNetworkLogsEnabled) "ON" else "OFF"}")
            appendLine("  UI: ${if (isUiLogsEnabled) "ON" else "OFF"}")
            appendLine("  Merchant: ${if (isMerchantLogsEnabled) "ON" else "OFF"}")
            appendLine("  Insights: ${if (isInsightsLogsEnabled) "ON" else "OFF"}")
            appendLine("  Migration: ${if (isMigrationLogsEnabled) "ON" else "OFF"}")
        }
    }
    
    private fun getLogLevelString(level: Int): String {
        return when (level) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            else -> "UNKNOWN"
        }
    }
}