package com.smartexpenseai.app.utils

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for exporting application logs
 * Used for debugging and troubleshooting issues
 */
class LogExporter(private val context: Context) {

    companion object {
        private const val TAG = "LogExporter"
        private const val APP_LOG_FILE = "android-app.log"
    }

    /**
     * Export application logs to a shareable file
     * Collects both app-specific logs and recent logcat output
     *
     * @return Pair of (success: Boolean, message: String)
     */
    fun exportLogs(): Pair<Boolean, String> {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "app_logs_$timestamp.txt"

            // Create log content
            val logContent = buildString {
                append("=" .repeat(60))
                append("\n")
                append("Smart Expense Manager - Application Logs\n")
                append("=" .repeat(60))
                append("\n")
                append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                append("=" .repeat(60))
                append("\n\n")

                // Add app info
                append("[APP INFORMATION]\n")
                append("Package: ${context.packageName}\n")
                append("Version: ${getAppVersion()}\n")
                append("Build Type: ${android.os.Build.TYPE}\n")
                append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                append("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                append("\n")

                // Read app-specific log file if exists
                val appLogFile = File(context.filesDir, APP_LOG_FILE)
                if (appLogFile.exists()) {
                    append("[APP LOG FILE]\n")
                    append("-" .repeat(60))
                    append("\n")
                    try {
                        val appLogs = appLogFile.readText()
                        append(appLogs)
                    } catch (e: Exception) {
                        append("Error reading app log file: ${e.message}\n")
                    }
                    append("\n\n")
                } else {
                    append("[APP LOG FILE]\n")
                    append("No app log file found at: ${appLogFile.absolutePath}\n\n")
                }

                // Collect recent logcat output
                append("[RECENT LOGCAT OUTPUT - Last 500 lines]\n")
                append("-" .repeat(60))
                append("\n")
                try {
                    val logcat = collectLogcat()
                    append(logcat)
                } catch (e: Exception) {
                    append("Error collecting logcat: ${e.message}\n")
                }
                append("\n\n")

                append("=" .repeat(60))
                append("\n")
                append("End of Log Export\n")
                append("=" .repeat(60))
            }

            // Save to Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logFile = File(downloadsDir, fileName)

            FileWriter(logFile).use { writer ->
                writer.write(logContent)
            }

            return Pair(true, logFile.absolutePath)

        } catch (e: Exception) {
            return Pair(false, "Export failed: ${e.message}")
        }
    }

    /**
     * Create share intent for the log file
     */
    fun createShareIntent(filePath: String): Intent? {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return null
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            return Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Smart Expense Manager - App Logs")
                putExtra(Intent.EXTRA_TEXT, "Application logs attached for debugging")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Collect recent logcat output filtered for this app
     */
    private fun collectLogcat(): String {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat",
                    "-d",  // Dump and exit
                    "-v", "time",  // Time format
                    "-t", "500",  // Last 500 lines
                    "${context.packageName}:V",  // App logs with verbose
                    "*:S"  // Silence other apps
                )
            )

            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            val log = StringBuilder()

            bufferedReader.useLines { lines ->
                lines.forEach { line ->
                    log.append(line).append("\n")
                }
            }

            process.waitFor()

            if (log.isEmpty()) {
                "No logcat entries found for ${context.packageName}\n"
            } else {
                log.toString()
            }

        } catch (e: Exception) {
            "Error collecting logcat: ${e.message}\n"
        }
    }

    /**
     * Get app version name
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get the app log file path for reference
     */
    fun getAppLogFilePath(): String {
        return File(context.filesDir, APP_LOG_FILE).absolutePath
    }
}
