package com.smartexpenseai.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartexpenseai.app.utils.logging.StructuredLogger

/**
 * BootReceiver - Ensures SMS monitoring continues after device restart
 *
 * Android kills all app processes on reboot, which can stop BroadcastReceivers
 * from functioning. This receiver re-initializes the app when the device boots.
 *
 * Required for:
 * - Re-enabling SMS monitoring after device restart
 * - Ensuring SMSReceiver continues to work
 * - Maintaining notification functionality
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
                what = "Device boot completed - SMS monitoring will continue"
            )

            // CRITICAL: Start the foreground service to ensure reliable SMS monitoring
            // This keeps the app alive in background even after reboot
            try {
                com.smartexpenseai.app.services.SMSMonitoringService.start(context)
                logger.info(
                    where = "onReceive",
                    what = "✅ SMS Monitoring Foreground Service started after boot"
                )
            } catch (e: Exception) {
                logger.error(
                    where = "onReceive",
                    what = "Failed to start SMS Monitoring Service after boot",
                    throwable = e
                )
            }

            logger.debug(
                where = "onReceive",
                what = "BootReceiver triggered successfully - SMSReceiver is ready"
            )
        }
    }
}
