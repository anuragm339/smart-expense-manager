package com.expensemanager.app.utils.logging

import android.content.Context
import android.os.Environment
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced Timber Tree for feature-specific file logging
 * Supports filtering by feature tags and improved file management
 */
@Singleton
class TimberFileTree @Inject constructor(
    private val context: Context,
    private val logConfig: LogConfig
) : Timber.Tree() {

    companion object {
        private const val TAG = "TimberFileTree"
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5MB
        private const val MAX_LOG_FILES = 5
        private const val LOG_FILE_PREFIX = "expense_manager"
    }

    private val logExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "TimberFileLogger").apply { isDaemon = true }
    }
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    init {
        // Create log directories on initialization
        createLogDirectories()
        
        // Schedule periodic cleanup
        scheduleLogCleanup()
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Skip if global logging is disabled
        if (!logConfig.isGlobalLoggingEnabled || !logConfig.isFileLoggingEnabled) {
            return
        }

        val featureTag = extractFeatureTag(tag)
        
        // Check if this feature's logging is enabled
        if (!logConfig.shouldLog(priority, featureTag)) {
            return
        }

        // Format log message
        val formattedMessage = formatLogMessage(priority, tag, message, t)
        
        // Write to file asynchronously
        logExecutor.execute {
            writeToLogFile(formattedMessage, featureTag)
        }
    }

    private fun extractFeatureTag(tag: String?): String {
        if (tag == null) return LogConfig.FeatureTags.APP
        
        // Extract feature tag from various tag formats
        return when {
            tag.contains("Dashboard", true) -> LogConfig.FeatureTags.DASHBOARD
            tag.contains("SMS", true) || tag.contains("Message", true) -> LogConfig.FeatureTags.SMS
            tag.contains("Transaction", true) -> LogConfig.FeatureTags.TRANSACTION
            tag.contains("Categor", true) -> LogConfig.FeatureTags.CATEGORIES
            tag.contains("Database", true) || tag.contains("Dao", true) || tag.contains("DB", true) -> LogConfig.FeatureTags.DATABASE
            tag.contains("Network", true) || tag.contains("Api", true) || tag.contains("Http", true) -> LogConfig.FeatureTags.NETWORK
            tag.contains("UI", true) || tag.contains("Fragment", true) || tag.contains("Adapter", true) -> LogConfig.FeatureTags.UI
            tag.contains("Merchant", true) -> LogConfig.FeatureTags.MERCHANT
            tag.contains("Insight", true) || tag.contains("AI", true) -> LogConfig.FeatureTags.INSIGHTS
            tag.contains("Migration", true) -> LogConfig.FeatureTags.MIGRATION
            else -> {
                // Check for exact feature tag matches
                when (tag.uppercase()) {
                    LogConfig.FeatureTags.DASHBOARD -> LogConfig.FeatureTags.DASHBOARD
                    LogConfig.FeatureTags.SMS -> LogConfig.FeatureTags.SMS
                    LogConfig.FeatureTags.TRANSACTION -> LogConfig.FeatureTags.TRANSACTION
                    LogConfig.FeatureTags.CATEGORIES -> LogConfig.FeatureTags.CATEGORIES
                    LogConfig.FeatureTags.DATABASE -> LogConfig.FeatureTags.DATABASE
                    LogConfig.FeatureTags.NETWORK -> LogConfig.FeatureTags.NETWORK
                    LogConfig.FeatureTags.UI -> LogConfig.FeatureTags.UI
                    LogConfig.FeatureTags.MERCHANT -> LogConfig.FeatureTags.MERCHANT
                    LogConfig.FeatureTags.INSIGHTS -> LogConfig.FeatureTags.INSIGHTS
                    LogConfig.FeatureTags.MIGRATION -> LogConfig.FeatureTags.MIGRATION
                    else -> LogConfig.FeatureTags.APP
                }
            }
        }
    }

    private fun formatLogMessage(priority: Int, tag: String?, message: String, t: Throwable?): String {
        return buildString {
            append(dateFormatter.format(Date()))
            append(" ")
            append(getPriorityString(priority))
            append("/")
            append(tag ?: "APP")
            append(": ")
            append(message)
            
            t?.let { throwable ->
                append("\n")
                append(Log.getStackTraceString(throwable))
            }
        }
    }

    private fun getPriorityString(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "U"
        }
    }

    private fun writeToLogFile(message: String, featureTag: String) {
        try {
            // Write to main log file (all logs)
            val mainLogFile = getOrCreateLogFile("all")
            appendToFile(mainLogFile, message)
            
            // Write to feature-specific log file if feature logging is detailed
            if (shouldCreateFeatureSpecificLog(featureTag)) {
                val featureLogFile = getOrCreateLogFile(featureTag.lowercase())
                appendToFile(featureLogFile, message)
            }
            
            // Write to external log if enabled
            if (logConfig.isExternalLoggingEnabled && isExternalStorageWritable()) {
                val externalLogFile = getOrCreateExternalLogFile("all")
                appendToFile(externalLogFile, message)
            }
            
        } catch (e: Exception) {
            // Fallback to Android Log for critical errors
            Log.e(TAG, "Failed to write to log file: ${e.message}", e)
        }
    }

    private fun shouldCreateFeatureSpecificLog(featureTag: String): Boolean {
        // Only create feature-specific logs for major features to avoid file proliferation
        return featureTag in listOf(
            LogConfig.FeatureTags.DASHBOARD,
            LogConfig.FeatureTags.SMS,
            LogConfig.FeatureTags.TRANSACTION,
            LogConfig.FeatureTags.DATABASE
        )
    }

    private fun getOrCreateLogFile(prefix: String): File {
        val logDir = File(context.cacheDir, "logs")
        val fileName = "${LOG_FILE_PREFIX}_${prefix}.log"
        val logFile = File(logDir, fileName)
        
        // Rotate file if too large
        if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
            rotateLogFile(logFile, prefix)
        }
        
        return logFile
    }

    private fun getOrCreateExternalLogFile(prefix: String): File {
        val logDir = File(context.getExternalFilesDir(null), "logs")
        val fileName = "${LOG_FILE_PREFIX}_${prefix}_external.log"
        val logFile = File(logDir, fileName)
        
        // Rotate file if too large
        if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
            rotateLogFile(logFile, "${prefix}_external")
        }
        
        return logFile
    }

    private fun rotateLogFile(currentFile: File, prefix: String) {
        try {
            val timestamp = fileNameFormatter.format(Date())
            val rotatedFileName = "${LOG_FILE_PREFIX}_${prefix}_$timestamp.log"
            val rotatedFile = File(currentFile.parentFile, rotatedFileName)
            
            if (currentFile.renameTo(rotatedFile)) {
                Log.d(TAG, "Rotated log file: ${currentFile.name} -> ${rotatedFile.name}")
                
                // Clean up old files
                cleanupOldLogFiles(currentFile.parentFile, prefix)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to rotate log file: ${e.message}", e)
        }
    }

    private fun cleanupOldLogFiles(logDir: File?, prefix: String) {
        if (logDir == null || !logDir.exists()) return
        
        try {
            val oldFiles = logDir.listFiles { _, name ->
                name.startsWith("${LOG_FILE_PREFIX}_${prefix}_") && name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() }
            
            oldFiles?.drop(MAX_LOG_FILES)?.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "Deleted old log file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup old log files: ${e.message}", e)
        }
    }

    private fun appendToFile(file: File, message: String) {
        PrintWriter(FileWriter(file, true)).use { writer ->
            writer.println(message)
        }
    }

    private fun createLogDirectories() {
        try {
            // Internal log directory
            val internalLogDir = File(context.cacheDir, "logs")
            Log.d(TAG, "Checking internal log directory: ${internalLogDir.absolutePath}")
            if (!internalLogDir.exists()) {
                val created = internalLogDir.mkdirs()
                Log.d(TAG, "Created internal log directory: $created at ${internalLogDir.absolutePath}")
            } else {
                Log.d(TAG, "Internal log directory already exists: ${internalLogDir.absolutePath}")
            }
            
            // External log directory (if available)
            if (logConfig.isExternalLoggingEnabled && isExternalStorageWritable()) {
                val externalLogDir = File(context.getExternalFilesDir(null), "logs")
                Log.d(TAG, "Checking external log directory: ${externalLogDir.absolutePath}")
                if (!externalLogDir.exists()) {
                    val created = externalLogDir.mkdirs()
                    Log.d(TAG, "Created external log directory: $created at ${externalLogDir.absolutePath}")
                } else {
                    Log.d(TAG, "External log directory already exists: ${externalLogDir.absolutePath}")
                }
            } else {
                Log.d(TAG, "External logging disabled: enabled=${logConfig.isExternalLoggingEnabled}, writable=${isExternalStorageWritable()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create log directories: ${e.message}", e)
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    private fun scheduleLogCleanup() {
        // Schedule periodic cleanup every hour
        logExecutor.execute {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    Thread.sleep(TimeUnit.HOURS.toMillis(1))
                    performPeriodicCleanup()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun performPeriodicCleanup() {
        try {
            val internalLogDir = File(context.cacheDir, "logs")
            cleanupDirectory(internalLogDir)
            
            if (logConfig.isExternalLoggingEnabled && isExternalStorageWritable()) {
                val externalLogDir = File(context.getExternalFilesDir(null), "logs")
                cleanupDirectory(externalLogDir)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed periodic cleanup: ${e.message}", e)
        }
    }

    private fun cleanupDirectory(dir: File) {
        if (!dir.exists()) return
        
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7) // Keep 7 days
        
        dir.listFiles { file ->
            file.isFile && file.lastModified() < cutoffTime
        }?.forEach { file ->
            if (file.delete()) {
                Log.d(TAG, "Deleted old log file: ${file.name}")
            }
        }
    }

    /**
     * Get all current log files
     */
    fun getLogFiles(): List<File> {
        val logFiles = mutableListOf<File>()
        
        // Internal log files
        val internalLogDir = File(context.cacheDir, "logs")
        internalLogDir.listFiles { file ->
            file.isFile && file.name.endsWith(".log")
        }?.let { logFiles.addAll(it) }
        
        // External log files
        if (logConfig.isExternalLoggingEnabled && isExternalStorageWritable()) {
            val externalLogDir = File(context.getExternalFilesDir(null), "logs")
            externalLogDir.listFiles { file ->
                file.isFile && file.name.endsWith(".log")
            }?.let { logFiles.addAll(it) }
        }
        
        return logFiles.sortedByDescending { it.lastModified() }
    }

    /**
     * Clear all log files
     */
    fun clearAllLogs() {
        logExecutor.execute {
            try {
                getLogFiles().forEach { file ->
                    if (file.delete()) {
                        Log.d(TAG, "Deleted log file: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear logs: ${e.message}", e)
            }
        }
    }

    /**
     * Get log statistics
     */
    fun getLogStatistics(): LogStatistics {
        val logFiles = getLogFiles()
        val totalSize = logFiles.sumOf { it.length() }
        val oldestFile = logFiles.minByOrNull { it.lastModified() }
        val newestFile = logFiles.maxByOrNull { it.lastModified() }
        
        return LogStatistics(
            fileCount = logFiles.size,
            totalSizeBytes = totalSize,
            oldestLogTime = oldestFile?.lastModified() ?: 0,
            newestLogTime = newestFile?.lastModified() ?: 0,
            logDirectories = listOf(
                File(context.cacheDir, "logs").absolutePath,
                File(context.getExternalFilesDir(null), "logs").absolutePath
            )
        )
    }
}

/**
 * Log statistics data class
 */
data class LogStatistics(
    val fileCount: Int,
    val totalSizeBytes: Long,
    val oldestLogTime: Long,
    val newestLogTime: Long,
    val logDirectories: List<String>
) {
    val totalSizeMB: Double
        get() = totalSizeBytes / (1024.0 * 1024.0)
        
    val oldestLogDate: Date
        get() = Date(oldestLogTime)
        
    val newestLogDate: Date
        get() = Date(newestLogTime)
}