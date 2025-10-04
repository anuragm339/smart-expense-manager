package com.expensemanager.app.utils.logging

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for logging operations and configuration
 * Provides runtime control over Timber logging features
 */
@Singleton
class LoggingManager @Inject constructor(
    private val context: Context,
    private val logConfig: LogConfig,
    private val timberFileTree: TimberFileTree
) {
    
    companion object {
        private const val TAG = "LOGGING_MANAGER"
        private const val FILE_PROVIDER_AUTHORITY = "com.expensemanager.app.fileprovider"
    }
    
    /**
     * Quick setup methods for common debugging scenarios
     */
    
    /**
     * Enable only Dashboard logging for focused debugging
     */
    fun enableOnlyDashboard() {
        logConfig.enableOnlyFeatures(LogConfig.FeatureTags.DASHBOARD)
        Timber.tag(TAG).i("Logging enabled only for DASHBOARD feature")
        logCurrentConfiguration()
    }
    
    /**
     * Enable only SMS processing logging
     */
    fun enableOnlySms() {
        logConfig.enableOnlyFeatures(LogConfig.FeatureTags.SMS)
        Timber.tag(TAG).i("Logging enabled only for SMS feature")
        logCurrentConfiguration()
    }
    
    /**
     * Enable only Transaction logging
     */
    fun enableOnlyTransaction() {
        logConfig.enableOnlyFeatures(LogConfig.FeatureTags.TRANSACTION)
        Timber.tag(TAG).i("Logging enabled only for TRANSACTION feature")
        logCurrentConfiguration()
    }
    
    /**
     * Enable only Categories logging
     */
    fun enableOnlyCategories() {
        logConfig.enableOnlyFeatures(LogConfig.FeatureTags.CATEGORIES)
        Timber.tag(TAG).i("Logging enabled only for CATEGORIES feature")
        logCurrentConfiguration()
    }
    
    /**
     * Enable only Database logging
     */
    fun enableOnlyDatabase() {
        logConfig.enableOnlyFeatures(LogConfig.FeatureTags.DATABASE)
        Timber.tag(TAG).i("Logging enabled only for DATABASE feature")
        logCurrentConfiguration()
    }
    
    /**
     * Enable multiple features for combined debugging
     */
    fun enableFeatures(vararg features: String) {
        logConfig.enableOnlyFeatures(*features)
        Timber.tag(TAG).i("Logging enabled for features: ${features.joinToString(", ")}")
        logCurrentConfiguration()
    }
    
    /**
     * Enable all feature logging
     */
    fun enableAllFeatures() {
        logConfig.enableAllFeatures()
        Timber.tag(TAG).i("All feature logging enabled")
        logCurrentConfiguration()
    }
    
    /**
     * Disable all feature logging except critical ones
     */
    fun disableAllFeatures() {
        logConfig.disableAllFeatures()
        Timber.tag(TAG).i("All feature logging disabled (except critical)")
        logCurrentConfiguration()
    }
    
    /**
     * Log level management
     */
    
    fun setLogLevelVerbose() {
        logConfig.logLevel = LogConfig.VERBOSE
        Timber.tag(TAG).i("Log level set to VERBOSE")
    }
    
    fun setLogLevelDebug() {
        logConfig.logLevel = LogConfig.DEBUG
        Timber.tag(TAG).i("Log level set to DEBUG")
    }
    
    fun setLogLevelInfo() {
        logConfig.logLevel = LogConfig.INFO
        Timber.tag(TAG).i("Log level set to INFO")
    }
    
    fun setLogLevelWarn() {
        logConfig.logLevel = LogConfig.WARN
        Timber.tag(TAG).i("Log level set to WARN")
    }
    
    fun setLogLevelError() {
        logConfig.logLevel = LogConfig.ERROR
        Timber.tag(TAG).i("Log level set to ERROR")
    }
    
    /**
     * File logging management
     */
    
    fun enableFileLogging() {
        logConfig.isFileLoggingEnabled = true
        Timber.tag(TAG).i("File logging enabled")
    }
    
    fun disableFileLogging() {
        logConfig.isFileLoggingEnabled = false
        Timber.tag(TAG).i("File logging disabled")
    }
    
    fun enableExternalLogging() {
        logConfig.isExternalLoggingEnabled = true
        Timber.tag(TAG).i("External file logging enabled")
    }
    
    fun disableExternalLogging() {
        logConfig.isExternalLoggingEnabled = false
        Timber.tag(TAG).i("External file logging disabled")
    }
    
    /**
     * Log file operations
     */
    
    fun getLogFiles(): List<File> {
        return timberFileTree.getLogFiles()
    }
    
    fun getLogStatistics(): LogStatistics {
        return timberFileTree.getLogStatistics()
    }
    
    fun clearAllLogs() {
        timberFileTree.clearAllLogs()
        Timber.tag(TAG).i("All log files cleared")
    }
    
    /**
     * Export log files as ZIP
     */
    fun exportLogsAsZip(): File? {
        return try {
            val logFiles = getLogFiles()
            if (logFiles.isEmpty()) {
                Timber.tag(TAG).w("No log files to export")
                return null
            }
            
            val zipFile = File(context.cacheDir, "expense_manager_logs_${System.currentTimeMillis()}.zip")
            
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                logFiles.forEach { file ->
                    val entry = ZipEntry(file.name)
                    zipOut.putNextEntry(entry)
                    
                    FileInputStream(file).use { fileIn ->
                        fileIn.copyTo(zipOut)
                    }
                    
                    zipOut.closeEntry()
                }
            }
            
            Timber.tag(TAG).i("Logs exported to: ${zipFile.absolutePath}")
            zipFile
            
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Failed to export logs as ZIP")
            null
        }
    }
    
    /**
     * Share log files via intent
     */
    fun shareLogs(): Intent? {
        return try {
            val zipFile = exportLogsAsZip()
            if (zipFile == null) {
                Timber.tag(TAG).w("Cannot share logs - export failed")
                return null
            }
            
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, zipFile)
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Expense Manager Debug Logs")
                putExtra(Intent.EXTRA_TEXT, "Debug logs from Expense Manager app")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            Timber.tag(TAG).i("Log sharing intent created")
            Intent.createChooser(shareIntent, "Share Debug Logs")
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create log sharing intent")
            null
        }
    }
    
    /**
     * Get log files as URIs for sharing
     */
    fun getLogFileUris(): List<Uri> {
        return getLogFiles().mapNotNull { file ->
            try {
                FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to get URI for file: ${file.name}")
                null
            }
        }
    }
    
    /**
     * Configuration and status methods
     */
    
    fun getCurrentConfiguration(): String {
        return logConfig.getCurrentConfig()
    }
    
    fun logCurrentConfiguration() {
        Timber.tag(TAG).d("Current logging configuration:\n${getCurrentConfiguration()}")
    }
    
    fun getLoggingStatus(): LoggingStatus {
        val stats = getLogStatistics()
        
        return LoggingStatus(
            isGlobalLoggingEnabled = logConfig.isGlobalLoggingEnabled,
            isFileLoggingEnabled = logConfig.isFileLoggingEnabled,
            isExternalLoggingEnabled = logConfig.isExternalLoggingEnabled,
            currentLogLevel = logConfig.logLevel,
            enabledFeatures = getEnabledFeatures(),
            logFileCount = stats.fileCount,
            totalLogSizeMB = stats.totalSizeMB,
            oldestLogDate = stats.oldestLogDate,
            newestLogDate = stats.newestLogDate
        )
    }
    
    private fun getEnabledFeatures(): List<String> {
        val enabledFeatures = mutableListOf<String>()
        
        if (logConfig.isDashboardLogsEnabled) enabledFeatures.add(LogConfig.FeatureTags.DASHBOARD)
        if (logConfig.isSmsLogsEnabled) enabledFeatures.add(LogConfig.FeatureTags.SMS)
        if (logConfig.isTransactionLogsEnabled) enabledFeatures.add(LogConfig.FeatureTags.TRANSACTION)
        if (logConfig.isCategoriesLogsEnabled) enabledFeatures.add(LogConfig.FeatureTags.CATEGORIES)
        if (logConfig.isDatabaseLogsEnabled) enabledFeatures.add(LogConfig.FeatureTags.DATABASE)
        if (logConfig.isNetworkLogsEnabled) enabledFeatures.add(LogConfig.FeatureTags.NETWORK)
        if (logConfig.isUiLogsEnabled) enabledFeatures.add(LogConfig.FeatureTags.UI)
        if (logConfig.isMerchantLogsEnabled) enabledFeatures.add(LogConfig.FeatureTags.MERCHANT)
        if (logConfig.isInsightsLogsEnabled) enabledFeatures.add(LogConfig.FeatureTags.INSIGHTS)
        if (logConfig.isMigrationLogsEnabled) enabledFeatures.add(LogConfig.FeatureTags.MIGRATION)
        
        return enabledFeatures
    }
    
    /**
     * Diagnostic methods
     */
    
    fun runLoggingDiagnostic(): String {
        return buildString {
            appendLine("=== Timber Logging Diagnostic ===")
            appendLine()
            appendLine("Current Configuration:")
            appendLine(getCurrentConfiguration())
            appendLine()
            
            val stats = getLogStatistics()
            appendLine("File Statistics:")
            appendLine("  Total Files: ${stats.fileCount}")
            appendLine("  Total Size: ${"%.2f".format(stats.totalSizeMB)} MB")
            appendLine("  Oldest Log: ${stats.oldestLogDate}")
            appendLine("  Newest Log: ${stats.newestLogDate}")
            appendLine()
            
            appendLine("Log Directories:")
            stats.logDirectories.forEach { dir ->
                val dirFile = File(dir)
                appendLine("  $dir (exists: ${dirFile.exists()})")
            }
            appendLine()
            
            appendLine("Available Log Files:")
            getLogFiles().forEach { file ->
                appendLine("  ${file.name} (${"%.2f".format(file.length() / (1024.0 * 1024.0))} MB)")
            }
        }
    }
    
    /**
     * Test logging functionality
     */
    fun testAllFeatureLogging() {
        Timber.tag(TAG).i("Starting feature logging test...")
        
        // Test each feature tag
        Timber.tag(LogConfig.FeatureTags.DASHBOARD).d("Dashboard test log message")
        Timber.tag(LogConfig.FeatureTags.SMS).d("SMS test log message")
        Timber.tag(LogConfig.FeatureTags.TRANSACTION).d("Transaction test log message")
        Timber.tag(LogConfig.FeatureTags.CATEGORIES).d("Categories test log message")
        Timber.tag(LogConfig.FeatureTags.DATABASE).d("Database test log message")
        Timber.tag(LogConfig.FeatureTags.NETWORK).d("Network test log message")
        Timber.tag(LogConfig.FeatureTags.UI).d("UI test log message")
        Timber.tag(LogConfig.FeatureTags.MERCHANT).d("Merchant test log message")
        Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Insights test log message")
        Timber.tag(LogConfig.FeatureTags.MIGRATION).d("Migration test log message")
        
        Timber.tag(TAG).i("Feature logging test completed")
    }
}

/**
 * Data class representing current logging status
 */
data class LoggingStatus(
    val isGlobalLoggingEnabled: Boolean,
    val isFileLoggingEnabled: Boolean,
    val isExternalLoggingEnabled: Boolean,
    val currentLogLevel: Int,
    val enabledFeatures: List<String>,
    val logFileCount: Int,
    val totalLogSizeMB: Double,
    val oldestLogDate: java.util.Date,
    val newestLogDate: java.util.Date
)