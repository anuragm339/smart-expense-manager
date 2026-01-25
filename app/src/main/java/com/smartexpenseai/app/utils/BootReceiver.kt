package com.smartexpenseai.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartexpenseai.app.services.SMSMonitoringManager
import com.smartexpenseai.app.utils.logging.StructuredLogger

/**
 * BootReceiver - Ensures SMS monitoring continues after device restart
 *
 * Android kills all app processes on reboot, which can stop background tasks.
 * This receiver re-initializes SMS monitoring using WorkManager when the device boots.
 *
 * Required for:
 * - Re-scheduling WorkManager tasks after device restart
 * - Ensuring SMSReceiver continues to work
 * - Maintaining notification functionality
 *
 * New Approach (WorkManager-based):
 * - No foreground service required
 * - WorkManager automatically persists across reboots (Android 6.0+)
 * - Combined with battery optimization exemption for high reliability
 */
class BootReceiver : BroadcastReceiver() {

    private val logger = StructuredLogger(
        featureTag = "SYSTEM",
        className = "BootReceiver"
    )

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            logger.info(
                where = "onReceive",
                what = "📱 Device boot completed - re-initializing SMS monitoring"
            )

            // Start WorkManager-based monitoring
            // This schedules periodic health checks for SMS monitoring
            try {
                SMSMonitoringManager.startMonitoring(context)
                logger.info(
                    where = "onReceive",
                    what = "✅ SMS monitoring worker scheduled after boot"
                )
            } catch (e: Exception) {
                logger.error(
                    where = "onReceive",
                    what = "❌ Failed to start SMS monitoring after boot",
                    throwable = e
                )
            }

            logger.debug(
                where = "onReceive",
                what = "BootReceiver completed - SMSReceiver is ready to receive SMS"
            )
        }
    }
}
