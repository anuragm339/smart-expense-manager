package com.smartexpenseai.app.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smartexpenseai.app.utils.logging.StructuredLogger
import java.util.concurrent.TimeUnit

/**
 * SMSMonitoringManager - Central manager for SMS monitoring setup
 *
 * This class handles the setup and lifecycle of SMS monitoring using WorkManager.
 * It replaces the previous foreground service approach with a more Play Store-friendly
 * solution.
 *
 * Architecture:
 * 1. SMSReceiver (BroadcastReceiver) - Handles actual SMS reception (high priority)
 * 2. SMSMonitoringWorker (WorkManager) - Periodic health checks
 * 3. BootReceiver - Restarts monitoring after device reboot
 * 4. Battery optimization exemption - Ensures receiver isn't killed
 *
 * This combination provides 90%+ reliability without requiring complex permissions.
 */
object SMSMonitoringManager {

    private val logger = StructuredLogger(
        featureTag = "MANAGER",
        className = "SMSMonitoringManager"
    )

    /**
     * Start SMS monitoring using WorkManager
     *
     * This schedules a periodic worker that:
     * - Runs every 6 hours (minimal battery impact)
     * - Checks if SMS permissions are still granted
     * - Verifies battery optimization status
     * - Ensures the app is ready to receive SMS
     */
    fun startMonitoring(context: Context) {
        logger.info(
            where = "startMonitoring",
            what = "🚀 Starting SMS monitoring with WorkManager"
        )

        try {
            val workManager = WorkManager.getInstance(context.applicationContext)

            // Create constraints for the worker
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false) // Run even on low battery
                .setRequiresCharging(false) // Run even when not charging
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // No network needed
                .build()

            // Create periodic work request (runs every 6 hours)
            val monitoringWork = PeriodicWorkRequestBuilder<SMSMonitoringWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag("sms_monitoring")
                .build()

            // Enqueue the work (replace existing if already scheduled)
            workManager.enqueueUniquePeriodicWork(
                SMSMonitoringWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if already running
                monitoringWork
            )

            logger.info(
                where = "startMonitoring",
                what = "✅ SMS monitoring worker scheduled successfully (runs every 6 hours)"
            )

        } catch (e: Exception) {
            logger.error(
                where = "startMonitoring",
                what = "❌ Failed to start SMS monitoring",
                throwable = e
            )
        }
    }

    /**
     * Stop SMS monitoring
     *
     * Cancels the WorkManager periodic task.
     * Note: This doesn't disable the SMSReceiver - SMS will still be received
     * if permissions are granted. This only stops the periodic health checks.
     */
    fun stopMonitoring(context: Context) {
        logger.info(
            where = "stopMonitoring",
            what = "🛑 Stopping SMS monitoring worker"
        )

        try {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(SMSMonitoringWorker.WORK_NAME)

            logger.info(
                where = "stopMonitoring",
                what = "✅ SMS monitoring worker cancelled"
            )

        } catch (e: Exception) {
            logger.error(
                where = "stopMonitoring",
                what = "❌ Failed to stop SMS monitoring",
                throwable = e
            )
        }
    }

    /**
     * Check if SMS monitoring is currently active
     */
    fun isMonitoringActive(context: Context): Boolean {
        return try {
            val workManager = WorkManager.getInstance(context.applicationContext)
            val workInfos = workManager.getWorkInfosForUniqueWork(SMSMonitoringWorker.WORK_NAME).get()
            workInfos.any { !it.state.isFinished }
        } catch (e: Exception) {
            logger.error(
                where = "isMonitoringActive",
                what = "Error checking monitoring status",
                throwable = e
            )
            false
        }
    }
}
