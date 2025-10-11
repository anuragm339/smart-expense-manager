package com.expensemanager.app.utils.logging

import android.content.Context
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Specialized logging configuration for bug fixing
 * Enables detailed logging for specific features being debugged
 */
@Singleton
class BugFixLoggingConfig @Inject constructor(
    private val logConfig: LogConfig
) {

    /**
     * Configure logging for BUG #1: SMS Import Permission Timing
     * Focus: SMS, Migration, Permission handling
     */
    fun enableBug1Logs() {
        Timber.tag("BUG_FIX").i("🔧 Enabling logs for BUG #1: SMS Import Permission Timing")

        logConfig.enableOnlyFeatures(
            LogConfig.FeatureTags.SMS,           // SMS parsing and reading
            LogConfig.FeatureTags.MIGRATION,     // Migration process
            LogConfig.FeatureTags.TRANSACTION,   // Transaction creation
            LogConfig.FeatureTags.DATABASE       // Database operations
        )

        logConfig.logLevel = LogConfig.DEBUG
        Timber.tag("BUG_FIX").i("✅ Focused logging enabled: SMS, Migration, Transaction, Database")
    }

    /**
     * Configure logging for BUG #3: Category Classification
     * Focus: Categories, Merchant classification
     */
    fun enableBug3Logs() {
        Timber.tag("BUG_FIX").i("🔧 Enabling logs for BUG #3: Category Classification")

        logConfig.enableOnlyFeatures(
            LogConfig.FeatureTags.CATEGORIES,    // Category logic
            LogConfig.FeatureTags.MERCHANT,      // Merchant classification
            LogConfig.FeatureTags.SMS,           // SMS parsing (where category is assigned)
            LogConfig.FeatureTags.TRANSACTION    // Transaction creation
        )

        logConfig.logLevel = LogConfig.DEBUG
        Timber.tag("BUG_FIX").i("✅ Focused logging enabled: Categories, Merchant, SMS, Transaction")
    }

    /**
     * Configure logging for BUG #2: Dashboard Value Inconsistency
     * Focus: Dashboard, Database queries, Data sync
     */
    fun enableBug2Logs() {
        Timber.tag("BUG_FIX").i("🔧 Enabling logs for BUG #2: Dashboard Value Inconsistency")

        logConfig.enableOnlyFeatures(
            LogConfig.FeatureTags.DASHBOARD,     // Dashboard calculations
            LogConfig.FeatureTags.DATABASE,      // Database queries
            LogConfig.FeatureTags.TRANSACTION,   // Transaction filtering
            LogConfig.FeatureTags.UI             // UI updates
        )

        logConfig.logLevel = LogConfig.DEBUG
        Timber.tag("BUG_FIX").i("✅ Focused logging enabled: Dashboard, Database, Transaction, UI")
    }

    /**
     * Enable all critical bug logs
     * Use when debugging multiple issues
     */
    fun enableAllBugLogs() {
        Timber.tag("BUG_FIX").i("🔧 Enabling logs for ALL critical bugs")

        logConfig.enableOnlyFeatures(
            LogConfig.FeatureTags.SMS,
            LogConfig.FeatureTags.MIGRATION,
            LogConfig.FeatureTags.CATEGORIES,
            LogConfig.FeatureTags.MERCHANT,
            LogConfig.FeatureTags.DASHBOARD,
            LogConfig.FeatureTags.DATABASE,
            LogConfig.FeatureTags.TRANSACTION
        )

        logConfig.logLevel = LogConfig.DEBUG
        Timber.tag("BUG_FIX").i("✅ Focused logging enabled for all bug-related features")
    }

    /**
     * Restore normal logging (all features enabled)
     */
    fun restoreNormalLogging() {
        Timber.tag("BUG_FIX").i("🔄 Restoring normal logging configuration")
        logConfig.enableAllFeatures()
        logConfig.logLevel = LogConfig.DEBUG
        Timber.tag("BUG_FIX").i("✅ Normal logging restored")
    }

    /**
     * Enable minimal logging (only errors and critical info)
     */
    fun enableMinimalLogging() {
        Timber.tag("BUG_FIX").i("📉 Enabling minimal logging (errors only)")
        logConfig.disableAllFeatures()
        logConfig.logLevel = LogConfig.ERROR
        Timber.tag("BUG_FIX").i("✅ Minimal logging enabled")
    }

    /**
     * Print current logging status
     */
    fun printCurrentStatus() {
        Timber.tag("BUG_FIX").d("Current logging configuration:")
        Timber.tag("BUG_FIX").d(logConfig.getCurrentConfig())
    }
}
