package com.expensemanager.app.utils.logging

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context
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

    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "TimberFileCleanup").apply { isDaemon = true }
    }
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    init {
        // Create log directories on initialization
        createLogDirectories()
        
        // Schedule periodic cleanup
        scheduleLogCleanup()
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Log everything (VERBOSE and above)
        return true
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        android.util.Log.v(TAG, "log() called with priority=$priority, tag=$tag")

        val featureTag = extractFeatureTag(tag)

        // Format log message
        val formattedMessage = formatLogMessage(priority, tag, message, t)

        // Write to file asynchronously
        try {
            logExecutor.execute {
                try {
                    android.util.Log.v(TAG, "Executor running, about to write to file...")
                    writeToLogFile(formattedMessage, featureTag)
                    android.util.Log.v(TAG, "File write completed")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error in executor task: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error submitting to executor: ${e.message}", e)
        }
    }

    private fun extractFeatureTag(tag: String?): String {
        if (tag == null) return "APP"
        
        // Extract feature tag from various tag formats
        return when {
            tag.contains("Dashboard", true) -> "DASHBOARD"
            tag.contains("SMS", true) || tag.contains("Message", true) -> "SMS"
            tag.contains("Transaction", true) -> "TRANSACTION"
            tag.contains("Categor", true) -> "CATEGORIES"
            tag.contains("Database", true) || tag.contains("Dao", true) || tag.contains("DB", true) -> "DATABASE"
            tag.contains("Network", true) || tag.contains("Api", true) || tag.contains("Http", true) -> "NETWORK"
            tag.contains("UI", true) || tag.contains("Fragment", true) || tag.contains("Adapter", true) -> "UI"
            tag.contains("Merchant", true) -> "MERCHANT"
            tag.contains("Insight", true) || tag.contains("AI", true) -> "INSIGHTS"
            tag.contains("Migration", true) -> "MIGRATION"
            else -> {
                // Check for exact feature tag matches
                when (tag.uppercase()) {
                    "DASHBOARD" -> "DASHBOARD"
                    "SMS" -> "SMS"
                    "TRANSACTION" -> "TRANSACTION"
                    "CATEGORIES" -> "CATEGORIES"
                    "DATABASE" -> "DATABASE"
                    "NETWORK" -> "NETWORK"
                    "UI" -> "UI"
                    "MERCHANT" -> "MERCHANT"
                    "INSIGHTS" -> "INSIGHTS"
                    "MIGRATION" -> "MIGRATION"
                    else -> "APP"
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
                append(throwable.stackTraceToString())
            }
        }
    }

    private fun getPriorityString(priority: Int): String {
        return when (priority) {
            android.util.Log.VERBOSE -> "V"
            android.util.Log.DEBUG -> "D"
            android.util.Log.INFO -> "I"
            android.util.Log.WARN -> "W"
            android.util.Log.ERROR -> "E"
            android.util.Log.ASSERT -> "A"
            else -> "U"
        }
    }

    private fun writeToLogFile(message: String, featureTag: String) {
        try {
            // Write to main log file (all logs)
            val mainLogFile = getOrCreateLogFile("all")
            android.util.Log.v(TAG, "Writing to log file: ${mainLogFile.absolutePath}")
            appendToFile(mainLogFile, message)

            // Write to feature-specific log file if feature logging is detailed
            if (shouldCreateFeatureSpecificLog(featureTag)) {
                val featureLogFile = getOrCreateLogFile(featureTag.lowercase())
                appendToFile(featureLogFile, message)
            }

            // Write to external log if enabled
            if (isExternalStorageWritable()) {
                val externalLogFile = getOrCreateExternalLogFile("all")
                appendToFile(externalLogFile, message)
            }

        } catch (e: Exception) {
            // Fallback to system error for critical errors
            System.err.println("$TAG: Failed to write to log file: ${e.message}")
            e.printStackTrace(System.err)
        }
    }

    private fun shouldCreateFeatureSpecificLog(featureTag: String): Boolean {
        // Only create feature-specific logs for major features to avoid file proliferation
        return featureTag in listOf(
            "DASHBOARD",
            "SMS",
            "TRANSACTION",
            "DATABASE"
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
                // Removed log: "Rotated log file: ${currentFile.name} -> ${rotatedFile.name}")
                
                // Clean up old files
                cleanupOldLogFiles(currentFile.parentFile, prefix)
            }
        } catch (e: Exception) {
            System.err.println("$TAG: Failed to rotate log file: ${e.message}")
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
                    // Removed log: "Deleted old log file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            System.err.println("$TAG: Failed to cleanup old log files: ${e.message}")
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
            android.util.Log.d(TAG, "Checking internal log directory: ${internalLogDir.absolutePath}")
            if (!internalLogDir.exists()) {
                val created = internalLogDir.mkdirs()
                android.util.Log.d(TAG, "Created internal log directory: $created at ${internalLogDir.absolutePath}")
            } else {
                android.util.Log.d(TAG, "Internal log directory already exists: ${internalLogDir.absolutePath}")
            }

            // External log directory (if available)
            if (isExternalStorageWritable()) {
                val externalLogDir = File(context.getExternalFilesDir(null), "logs")
                android.util.Log.d(TAG, "Checking external log directory: ${externalLogDir.absolutePath}")
                if (!externalLogDir.exists()) {
                    val created = externalLogDir.mkdirs()
                    android.util.Log.d(TAG, "Created external log directory: $created at ${externalLogDir.absolutePath}")
                } else {
                    android.util.Log.d(TAG, "External log directory already exists: ${externalLogDir.absolutePath}")
                }
            } else {
                android.util.Log.d(TAG, "External storage not writable, skipping external logging")
            }
        } catch (e: Exception) {
            System.err.println("$TAG: Failed to create log directories: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    private fun scheduleLogCleanup() {
        // Schedule periodic cleanup every hour using separate executor
        (cleanupExecutor as java.util.concurrent.ScheduledExecutorService).scheduleAtFixedRate(
            { performPeriodicCleanup() },
            1, // Initial delay
            1, // Period
            TimeUnit.HOURS
        )
    }

    private fun performPeriodicCleanup() {
        try {
            val internalLogDir = File(context.cacheDir, "logs")
            cleanupDirectory(internalLogDir)
            
            if (isExternalStorageWritable()) {
                val externalLogDir = File(context.getExternalFilesDir(null), "logs")
                cleanupDirectory(externalLogDir)
            }
        } catch (e: Exception) {
            System.err.println("$TAG: Failed periodic cleanup: ${e.message}")
        }
    }

    private fun cleanupDirectory(dir: File) {
        if (!dir.exists()) return
        
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7) // Keep 7 days
        
        dir.listFiles { file ->
            file.isFile && file.lastModified() < cutoffTime
        }?.forEach { file ->
            if (file.delete()) {
                // Removed log: "Deleted old log file: ${file.name}")
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
        if (isExternalStorageWritable()) {
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
                        Timber.tag(TAG).d("Deleted log file: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to clear logs: ${e.message}")
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