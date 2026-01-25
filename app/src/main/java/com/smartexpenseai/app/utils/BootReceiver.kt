package com.smartexpenseai.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartexpenseai.app.utils.logging.StructuredLogger

/**
 * BootReceiver - Ensures SMS monitoring continues after device restart
 *
 * Android kills all app processes on reboot. This receiver ensures the
 * SMSReceiver is re-registered and ready to receive SMS messages after boot.
 *
 * How it works:
 * - SMS BroadcastReceiver is automatically re-registered by Android after boot
 * - Combined with battery optimization exemption for high reliability
 * - No foreground service or WorkManager needed
 *
 * Reliability:
 * - SMSReceiver works 24/7 as a system-level broadcast receiver
 * - Battery optimization exemption prevents it from being killed
 * - This simple approach avoids all foreground service permission issues
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
                what = "📱 Device boot completed - SMS monitoring ready"
            )

            logger.debug(
                where = "onReceive",
                what = "SMSReceiver automatically re-registered by Android - ready to receive SMS"
            )
        }
    }
}
