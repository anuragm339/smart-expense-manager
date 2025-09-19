package com.expensemanager.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogger @Inject constructor(
    private val context: Context
) : Timber.Tree() {

    companion object {
        private const val TAG = "AppLogger"
        private const val PREF_NAME = "logging_preferences"
        private const val KEY_LOG_LEVEL = "log_level"
        private const val KEY_FILE_LOGGING_ENABLED = "file_logging_enabled"
        private const val KEY_EXTERNAL_LOGGING_ENABLED = "external_logging_enabled"
    }

    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val logFileFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logFileNameFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        // Ensure log directories exist on initialization
        createLogDirectories()
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!isFileLoggingEnabled()) return

        val logMessage = buildString {
            append(logFileFormatter.format(Date()))
            append(" ")
            append(priorityToString(priority))
            append("/")
            append(tag ?: TAG)
            append(": ")
            append(message)
            t?.let { append("\n").append(Log.getStackTraceString(it)) }
        }

        try {
            val internalLogDir = File(context.cacheDir, "logs")
            val internalLogFile = File(internalLogDir, "expense-manager.log")
            appendLogToFile(internalLogFile, logMessage)

            if (isExternalLoggingEnabled() && isExternalStorageWritable()) {
                val externalLogDir = File(context.getExternalFilesDir(null), "logs")
                val externalLogFile = File(externalLogDir, "expense-manager-debug.log")
                appendLogToFile(externalLogFile, logMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing log to file: ${e.message}", e)
        }
    }

    private fun appendLogToFile(file: File, logMessage: String) {
        try {
            // Simple rotation: if file exceeds 5MB, rename it and start new
            if (file.exists() && file.length() > 5 * 1024 * 1024) { // 5 MB
                val oldFileName = file.nameWithoutExtension + "_" + logFileNameFormatter.format(Date()) + ".log"
                file.renameTo(File(file.parentFile, oldFileName))
            }
            PrintWriter(FileWriter(file, true)).use { it.println(logMessage) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append log to file ${file.absolutePath}: ${e.message}", e)
        }
    }

    private fun priorityToString(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            else -> "UNKNOWN"
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
    
    // ===========================================
    // Logging Methods (now using Timber's log method)
    // ===========================================
    
    fun trace(tag: String, message: String) {
        Timber.tag(tag).v(message)
    }
    
    fun debug(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }
    
    fun debug(tag: String, message: String, vararg args: Any?) {
        Timber.tag(tag).d(message, *args)
    }
    
    fun info(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }
    
    fun info(tag: String, message: String, vararg args: Any?) {
        Timber.tag(tag).i(message, *args)
    }
    
    fun warn(tag: String, message: String) {
        Timber.tag(tag).w(message)
    }
    
    fun warn(tag: String, message: String, throwable: Throwable) {
        Timber.tag(tag).w(throwable, message)
    }
    
    fun error(tag: String, message: String) {
        Timber.tag(tag).e(message)
    }
    
    fun error(tag: String, message: String, throwable: Throwable) {
        Timber.tag(tag).e(throwable, message)
    }
    
    fun error(tag: String, message: String, vararg args: Any?) {
        Timber.tag(tag).e(message, *args)
    }
    
    // ===========================================
    // Special Logging Methods for Financial App
    // ===========================================
    
    fun logSMSProcessing(sender: String, message: String, success: Boolean, details: String? = null) {
        val status = if (success) "SUCCESS" else "FAILED"
        val logMessage = "SMS Processing [".plus(status).plus("] - Sender: ").plus(sender).plus(", Message: ").plus(message.take(50)).plus("...")
        
        if (success) {
            info("SMS_PROCESSING", logMessage)
            details?.let { debug("SMS_PROCESSING", "Details: ".plus(it)) }
        } else {
            warn("SMS_PROCESSING", logMessage)
            details?.let { warn("SMS_PROCESSING", "Error Details: ".plus(it)) }
        }
    }
    
    fun logTransaction(action: String, transactionId: String, amount: Double, merchant: String) {
        info("TRANSACTION", "Transaction ".plus(action).plus(" - ID: ").plus(transactionId).plus(", Amount: ₹").plus(amount).plus(", Merchant: ").plus(merchant))
    }
    
    fun logDatabaseOperation(operation: String, table: String, success: Boolean, recordsAffected: Int = 0) {
        val status = if (success) "SUCCESS" else "FAILED"
        info("DATABASE", "DB Operation [".plus(status).plus("] - ").plus(operation).plus(" on ").plus(table).plus(", Records: ").plus(recordsAffected))
    }
    
    fun logAIOperation(operation: String, inputSize: Int, outputSize: Int, processingTime: Long) {
        info("AI_PROCESSING", "AI Operation: ".plus(operation).plus(", Input: ").plus(inputSize).plus(", Output: ").plus(outputSize).plus(", Time: ").plus(processingTime).plus("ms"))
    }
    
    // ===========================================
    // Configuration Methods
    // ===========================================
    
    fun setLogLevel(level: Int) {
        preferences.edit()
            .putInt(KEY_LOG_LEVEL, level)
            .apply()
        
        // Timber's log level is controlled by its planted trees
        // For now, we'll just log this change
        info(TAG, "Log level changed to: ".plus(priorityToString(level)))
    }
    
    fun getLogLevel(): Int {
        // This will need to be mapped to Timber's internal levels if we use them
        return preferences.getInt(KEY_LOG_LEVEL, Log.DEBUG)
    }
    
    fun setFileLoggingEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_FILE_LOGGING_ENABLED, enabled)
            .apply()
        
        info(TAG, "File logging ".plus(if (enabled) "enabled" else "disabled"))
    }
    
    fun isFileLoggingEnabled(): Boolean {
        return preferences.getBoolean(KEY_FILE_LOGGING_ENABLED, true)
    }
    
    fun setExternalLoggingEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_EXTERNAL_LOGGING_ENABLED, enabled)
            .apply()
        
        info(TAG, "External logging ".plus(if (enabled) "enabled" else "disabled"))
    }
    
    fun isExternalLoggingEnabled(): Boolean {
        return preferences.getBoolean(KEY_EXTERNAL_LOGGING_ENABLED, true)
    }
    
    fun getLogFiles(): List<File> {
        val logFiles = mutableListOf<File>()
        
        val internalLogDir = File(context.cacheDir, "logs")
        if (internalLogDir.exists()) {
            internalLogDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".log")) {
                    logFiles.add(file)
                }
            }
        }
        
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
    
    fun clearLogFiles() {
        try {
            val logFiles = getLogFiles()
            var deletedCount = 0
            
            logFiles.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
            
            info(TAG, "Cleared ".plus(deletedCount).plus(" log files"))
            
        } catch (e: Exception) {
            error(TAG, "Failed to clear log files", e)
        }
    }
}