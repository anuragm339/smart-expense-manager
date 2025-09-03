package com.expensemanager.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.util.StatusPrinter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized logging utility using Logback for the Smart Expense Manager app.
 * 
 * This class provides a professional logging solution with:
 * - Multiple log levels (TRACE, DEBUG, INFO, WARN, ERROR)
 * - File output with rotation
 * - Console output for debug builds
 * - Category-based logging for different app components
 * - Integration with app preferences
 * 
 * Usage:
 * ```kotlin
 * class MyClass @Inject constructor(private val logger: AppLogger) {
 *     fun someMethod() {
 *         logger.debug("MyClass", "Debug message")
 *         logger.info("MyClass", "Info message")
 *         logger.error("MyClass", "Error message", exception)
 *     }
 * }
 * ```
 */
@Singleton
class AppLogger @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "AppLogger"
        private const val PREF_NAME = "logging_preferences"
        private const val KEY_LOG_LEVEL = "log_level"
        private const val KEY_FILE_LOGGING_ENABLED = "file_logging_enabled"
        private const val KEY_EXTERNAL_LOGGING_ENABLED = "external_logging_enabled"
        
        // Log level constants
        const val LEVEL_TRACE = 0
        const val LEVEL_DEBUG = 1
        const val LEVEL_INFO = 2
        const val LEVEL_WARN = 3
        const val LEVEL_ERROR = 4
        
        // Default log level
        private const val DEFAULT_LOG_LEVEL = LEVEL_DEBUG
        
        @Volatile
        private var initialized = false
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val rootLogger: Logger = LoggerFactory.getLogger("ExpenseManager")
    
    init {
        initializeLogback()
    }
    
    /**
     * Initialize Logback configuration
     */
    private fun initializeLogback() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    try {
                        // Ensure log directories exist
                        createLogDirectories()
                        
                        // Print Logback configuration status for debugging
                        val context = LoggerFactory.getILoggerFactory() as LoggerContext
                        StatusPrinter.print(context)
                        
                        initialized = true
                        rootLogger.info("Logback initialized successfully")
                        
                    } catch (e: Exception) {
                        // Fallback to system logging if Logback fails
                        android.util.Log.e(TAG, "Failed to initialize Logback", e)
                        initialized = false
                    }
                }
            }
        }
    }
    
    /**
     * Create necessary log directories
     */
    private fun createLogDirectories() {
        try {
            // Internal storage log directory
            val internalLogDir = File(context.cacheDir, "logs")
            if (!internalLogDir.exists()) {
                internalLogDir.mkdirs()
            }
            
            // External storage log directory (if available and permission granted)
            if (isExternalStorageWritable() && isExternalLoggingEnabled()) {
                val externalLogDir = File(context.getExternalFilesDir(null), "logs")
                if (!externalLogDir.exists()) {
                    externalLogDir.mkdirs()
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not create all log directories", e)
        }
    }
    
    /**
     * Check if external storage is available and writable
     */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
    
    // ===========================================
    // Logging Methods
    // ===========================================
    
    /**
     * Log a TRACE message
     */
    fun trace(tag: String, message: String) {
        if (shouldLog(LEVEL_TRACE)) {
            getLogger(tag).trace(message)
        }
    }
    
    /**
     * Log a DEBUG message
     */
    fun debug(tag: String, message: String) {
        if (shouldLog(LEVEL_DEBUG)) {
            getLogger(tag).debug(message)
        }
    }
    
    /**
     * Log a DEBUG message with formatted arguments
     */
    fun debug(tag: String, message: String, vararg args: Any?) {
        if (shouldLog(LEVEL_DEBUG)) {
            getLogger(tag).debug(message, *args)
        }
    }
    
    /**
     * Log an INFO message
     */
    fun info(tag: String, message: String) {
        if (shouldLog(LEVEL_INFO)) {
            getLogger(tag).info(message)
        }
    }
    
    /**
     * Log an INFO message with formatted arguments
     */
    fun info(tag: String, message: String, vararg args: Any?) {
        if (shouldLog(LEVEL_INFO)) {
            getLogger(tag).info(message, *args)
        }
    }
    
    /**
     * Log a WARN message
     */
    fun warn(tag: String, message: String) {
        if (shouldLog(LEVEL_WARN)) {
            getLogger(tag).warn(message)
        }
    }
    
    /**
     * Log a WARN message with exception
     */
    fun warn(tag: String, message: String, throwable: Throwable) {
        if (shouldLog(LEVEL_WARN)) {
            getLogger(tag).warn(message, throwable)
        }
    }
    
    /**
     * Log an ERROR message
     */
    fun error(tag: String, message: String) {
        if (shouldLog(LEVEL_ERROR)) {
            getLogger(tag).error(message)
        }
    }
    
    /**
     * Log an ERROR message with exception
     */
    fun error(tag: String, message: String, throwable: Throwable) {
        if (shouldLog(LEVEL_ERROR)) {
            getLogger(tag).error(message, throwable)
        }
    }
    
    /**
     * Log an ERROR message with formatted arguments
     */
    fun error(tag: String, message: String, vararg args: Any?) {
        if (shouldLog(LEVEL_ERROR)) {
            getLogger(tag).error(message, *args)
        }
    }
    
    // ===========================================
    // Special Logging Methods for Financial App
    // ===========================================
    
    /**
     * Log SMS processing events with special formatting
     */
    fun logSMSProcessing(sender: String, message: String, success: Boolean, details: String? = null) {
        val status = if (success) "SUCCESS" else "FAILED"
        val logMessage = "SMS Processing [$status] - Sender: $sender, Message: ${message.take(50)}..."
        
        if (success) {
            info("SMS_PROCESSING", logMessage)
            details?.let { debug("SMS_PROCESSING", "Details: $it") }
        } else {
            warn("SMS_PROCESSING", logMessage)
            details?.let { warn("SMS_PROCESSING", "Error Details: $it") }
        }
    }
    
    /**
     * Log transaction events
     */
    fun logTransaction(action: String, transactionId: String, amount: Double, merchant: String) {
        info("TRANSACTION", "Transaction $action - ID: $transactionId, Amount: ₹$amount, Merchant: $merchant")
    }
    
    /**
     * Log database operations
     */
    fun logDatabaseOperation(operation: String, table: String, success: Boolean, recordsAffected: Int = 0) {
        val status = if (success) "SUCCESS" else "FAILED"
        info("DATABASE", "DB Operation [$status] - $operation on $table, Records: $recordsAffected")
    }
    
    /**
     * Log AI/ML operations
     */
    fun logAIOperation(operation: String, inputSize: Int, outputSize: Int, processingTime: Long) {
        info("AI_PROCESSING", "AI Operation: $operation, Input: $inputSize, Output: $outputSize, Time: ${processingTime}ms")
    }
    
    // ===========================================
    // Configuration Methods
    // ===========================================
    
    /**
     * Set the minimum log level
     */
    fun setLogLevel(level: Int) {
        preferences.edit()
            .putInt(KEY_LOG_LEVEL, level)
            .apply()
        
        info(TAG, "Log level changed to: ${getLevelName(level)}")
    }
    
    /**
     * Get the current log level
     */
    fun getLogLevel(): Int {
        return preferences.getInt(KEY_LOG_LEVEL, DEFAULT_LOG_LEVEL)
    }
    
    /**
     * Enable/disable file logging
     */
    fun setFileLoggingEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_FILE_LOGGING_ENABLED, enabled)
            .apply()
        
        info(TAG, "File logging ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if file logging is enabled
     */
    fun isFileLoggingEnabled(): Boolean {
        return preferences.getBoolean(KEY_FILE_LOGGING_ENABLED, true)
    }
    
    /**
     * Enable/disable external storage logging
     */
    fun setExternalLoggingEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_EXTERNAL_LOGGING_ENABLED, enabled)
            .apply()
        
        info(TAG, "External logging ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if external storage logging is enabled
     */
    fun isExternalLoggingEnabled(): Boolean {
        return preferences.getBoolean(KEY_EXTERNAL_LOGGING_ENABLED, false)
    }
    
    /**
     * Get all log files for export/debugging
     */
    fun getLogFiles(): List<File> {
        val logFiles = mutableListOf<File>()
        
        // Internal log files
        val internalLogDir = File(context.cacheDir, "logs")
        if (internalLogDir.exists()) {
            internalLogDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".log")) {
                    logFiles.add(file)
                }
            }
        }
        
        // External log files
        if (isExternalLoggingEnabled() && isExternalStorageWritable()) {
            val externalLogDir = File(context.getExternalFilesDir(null), "logs")
            if (externalLogDir.exists()) {
                externalLogDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".log")) {
                        logFiles.add(file)
                    }
                }
            }
        }
        
        return logFiles
    }
    
    /**
     * Clear all log files
     */
    fun clearLogFiles() {
        try {
            val logFiles = getLogFiles()
            var deletedCount = 0
            
            logFiles.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
            
            info(TAG, "Cleared $deletedCount log files")
            
        } catch (e: Exception) {
            error(TAG, "Failed to clear log files", e)
        }
    }
    
    // ===========================================
    // Private Helper Methods
    // ===========================================
    
    /**
     * Get a logger instance for a specific tag/category
     */
    private fun getLogger(tag: String): Logger {
        return LoggerFactory.getLogger(tag)
    }
    
    /**
     * Check if a message should be logged based on current level
     */
    private fun shouldLog(messageLevel: Int): Boolean {
        return messageLevel >= getLogLevel()
    }
    
    /**
     * Get human-readable level name
     */
    private fun getLevelName(level: Int): String {
        return when (level) {
            LEVEL_TRACE -> "TRACE"
            LEVEL_DEBUG -> "DEBUG"
            LEVEL_INFO -> "INFO"
            LEVEL_WARN -> "WARN"
            LEVEL_ERROR -> "ERROR"
            else -> "UNKNOWN"
        }
    }
}