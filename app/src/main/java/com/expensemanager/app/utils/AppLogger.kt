package com.expensemanager.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
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
                        Log.d(TAG, "Starting Logback initialization...")
                        
                        // Step 1: Check if Logback classes are available
                        try {
                            val loggerContextClass = LoggerContext::class.java
                            Log.d(TAG, "✅ Logback classes available: ${loggerContextClass.name}")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Logback classes not found", e)
                            throw e
                        }
                        
                        // Step 2: Create log directories FIRST
                        val logDirs = createLogDirectories()
                        Log.d(TAG, "Created log directories: internal=${logDirs.first.exists()}, external=${logDirs.second?.exists()}")
                        
                        // Step 3: Set system properties for Logback to use
                        setLogbackSystemProperties(logDirs.first, logDirs.second)
                        
                        // Step 4: Check if assets/logback.xml is accessible
                        try {
                            val inputStream = context.assets.open("logback.xml")
                            val content = inputStream.bufferedReader().readText()
                            inputStream.close()
                            Log.d(TAG, "✅ logback.xml found in assets (${content.length} chars)")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ logback.xml not accessible in assets", e)
                            throw e
                        }
                        
                        // Step 5: Reset Logback context to pick up new properties
                        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
                        Log.d(TAG, "LoggerContext class: ${loggerContext::class.java.name}")
                        loggerContext.reset()
                        
                        // Step 6: Configure Logback from assets
                        try {
                            val configurator = ch.qos.logback.classic.joran.JoranConfigurator()
                            configurator.context = loggerContext
                            val inputStream = context.assets.open("logback.xml")
                            configurator.doConfigure(inputStream)
                            inputStream.close()
                            Log.d(TAG, "✅ Logback configured from assets/logback.xml")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to configure Logback from assets", e)
                            throw e
                        }
                        
                        // Step 7: Print configuration status for debugging
                        StatusPrinter.print(loggerContext)
                        
                        initialized = true
                        Log.d(TAG, "✅ Logback initialized successfully")
                        
                        // Step 8: Test that logging is working
                        rootLogger.info("Enhanced logging system initialized - Logback is active")
                        Log.d(TAG, "✅ Test log message sent to Logback")
                        
                        // Step 9: Test file appender functionality
                        testFileAppenders()
                        
                        // Step 10: Verify file appender status
                        verifyFileAppenderStatus()
                        
                    } catch (e: Exception) {
                        // Fallback to system logging if Logback fails
                        Log.e(TAG, "❌ Failed to initialize Logback, falling back to Timber", e)
                        initialized = false
                    }
                }
            }
        }
    }
    
    /**
     * Create necessary log directories
     * @return Pair of (internal directory, external directory or null)
     */
    private fun createLogDirectories(): Pair<File, File?> {
        var internalLogDir: File
        var externalLogDir: File? = null
        
        try {
            // Internal storage log directory (cache directory - always available)
            internalLogDir = File(context.cacheDir, "logs")
            if (!internalLogDir.exists()) {
                val created = internalLogDir.mkdirs()
                Log.d(TAG, "Internal log directory created: $created at ${internalLogDir.absolutePath}")
            } else {
                Log.d(TAG, "Internal log directory exists at ${internalLogDir.absolutePath}")
            }
            
            // External storage log directory (if available and permission granted)
            if (isExternalStorageWritable() && isExternalLoggingEnabled()) {
                externalLogDir = File(context.getExternalFilesDir(null), "logs")
                if (!externalLogDir.exists()) {
                    val created = externalLogDir.mkdirs()
                    Log.d(TAG, "External log directory created: $created at ${externalLogDir.absolutePath}")
                } else {
                    Log.d(TAG, "External log directory exists at ${externalLogDir.absolutePath}")
                }
            } else {
                Log.d(TAG, "External logging disabled or storage not writable")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not create all log directories", e)
            // Fallback to cache directory only
            internalLogDir = File(context.cacheDir, "logs")
            internalLogDir.mkdirs()
        }
        
        return Pair(internalLogDir, externalLogDir)
    }
    
    /**
     * Check if external storage is available and writable
     */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
    
    /**
     * Set system properties for Logback to use the correct file paths
     */
    private fun setLogbackSystemProperties(internalDir: File, externalDir: File?) {
        try {
            // Set internal log directory property
            System.setProperty("LOG_DIR", internalDir.absolutePath)
            Log.d(TAG, "Set LOG_DIR system property to: ${internalDir.absolutePath}")
            
            // FIXED: Only set EXTERNAL_LOG_DIR if external logging is enabled and external dir exists
            if (isExternalLoggingEnabled() && externalDir != null) {
                System.setProperty("EXTERNAL_LOG_DIR", externalDir.absolutePath)
                Log.d(TAG, "Set EXTERNAL_LOG_DIR system property to: ${externalDir.absolutePath}")
            } else {
                // Fallback to internal directory to prevent misconfigurations
                System.setProperty("EXTERNAL_LOG_DIR", internalDir.absolutePath)
                Log.d(TAG, "External logging disabled - EXTERNAL_LOG_DIR set to internal: ${internalDir.absolutePath}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Logback system properties", e)
        }
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
        // DEVELOPMENT: Enable by default for easier debugging
        return preferences.getBoolean(KEY_EXTERNAL_LOGGING_ENABLED, true)
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
    
    /**
     * Get initialization status for debugging
     */
    fun getInitializationStatus(): String {
        return "Logback initialized: $initialized, Log level: ${getLevelName(getLogLevel())}, File logging: ${isFileLoggingEnabled()}, External logging: ${isExternalLoggingEnabled()}"
    }
    
    /**
     * Test that logging is working by writing test messages
     */
    fun testLogging(): String {
        return try {
            val testTag = "LOGGING_TEST"
            debug(testTag, "Debug test message")
            info(testTag, "Info test message") 
            warn(testTag, "Warning test message")
            error(testTag, "Error test message")
            "Logging test completed successfully"
        } catch (e: Exception) {
            "Logging test failed: ${e.message}"
        }
    }
    
    /**
     * Test file appenders specifically to ensure they're working
     */
    private fun testFileAppenders() {
        try {
            Log.d(TAG, "Testing file appenders...")
            
            // Test each log level to all appenders
            val testLogger = LoggerFactory.getLogger("FILE_APPENDER_TEST")
            testLogger.debug("FILE APPENDER TEST: Debug message to file appenders")
            testLogger.info("FILE APPENDER TEST: Info message to file appenders")
            testLogger.warn("FILE APPENDER TEST: Warning message to file appenders")
            testLogger.error("FILE APPENDER TEST: Error message to file appenders")
            
            Log.d(TAG, "✅ File appender test messages sent")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ File appender test failed", e)
        }
    }
    
    /**
     * Verify file appender status and report any issues
     */
    private fun verifyFileAppenderStatus() {
        try {
            Log.d(TAG, "Verifying file appender status...")
            
            val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
            val rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
            val appenderIterator = rootLogger.iteratorForAppenders()
            
            var fileAppenderCount = 0
            while (appenderIterator.hasNext()) {
                val appender = appenderIterator.next()
                Log.d(TAG, "Found appender: ${appender.name} (${appender::class.java.simpleName})")
                
                if (appender is ch.qos.logback.core.rolling.RollingFileAppender<*>) {
                    fileAppenderCount++
                    val fileName = appender.file
                    val isStarted = appender.isStarted
                    
                    Log.d(TAG, "File appender '${appender.name}': file=$fileName, started=$isStarted")
                    
                    // Check if file exists and is writable
                    if (fileName != null) {
                        val file = File(fileName)
                        val parentDir = file.parentFile
                        
                        Log.d(TAG, "  - Parent directory: ${parentDir?.absolutePath}")
                        Log.d(TAG, "  - Parent exists: ${parentDir?.exists()}")
                        Log.d(TAG, "  - Parent writable: ${parentDir?.canWrite()}")
                        Log.d(TAG, "  - File exists: ${file.exists()}")
                        Log.d(TAG, "  - File size: ${file.length()} bytes")
                        
                        if (parentDir != null && !parentDir.exists()) {
                            Log.w(TAG, "  ⚠️ Parent directory does not exist for ${appender.name}")
                            // Try to create it
                            val created = parentDir.mkdirs()
                            Log.d(TAG, "  - Attempted to create parent directory: $created")
                        }
                    }
                }
            }
            
            Log.d(TAG, "✅ Found $fileAppenderCount file appenders")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to verify file appender status", e)
        }
    }
}