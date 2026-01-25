package com.smartexpenseai.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.smartexpenseai.app.utils.logging.StructuredLogger

/**
 * BatteryOptimizationHelper - Manages battery optimization settings
 *
 * Android's battery optimization can kill background processes and prevent
 * BroadcastReceivers from working when the app is not in foreground.
 *
 * This helper checks if the app is exempt from battery optimization and
 * provides an intent to request exemption from the user.
 */
object BatteryOptimizationHelper {

    private val logger = StructuredLogger(
        featureTag = "SYSTEM",
        className = "BatteryOptimizationHelper"
    )

    /**
     * Check if the app is currently exempt from battery optimization
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Battery optimization was introduced in Android 6.0 (API 23)
            return true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val packageName = context.packageName

        return powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
    }

    /**
     * Get an intent to request battery optimization exemption
     * User must manually approve this in system settings
     */
    fun getRequestBatteryOptimizationIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }

        val packageName = context.packageName
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
        }

        return intent
    }

    /**
     * Open battery optimization settings for the app
     */
    fun openBatteryOptimizationSettings(context: Context): Intent {
        val intent = Intent().apply {
            action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        }
        return intent
    }

    /**
     * Check and log battery optimization status
     */
    fun checkAndLogStatus(context: Context) {
        val isIgnoring = isIgnoringBatteryOptimizations(context)

        if (isIgnoring) {
            logger.info(
                where = "checkAndLogStatus",
                what = "✅ App is exempt from battery optimization - SMS monitoring will work reliably"
            )
        } else {
            logger.warn(
                where = "checkAndLogStatus",
                what = "⚠️ App is subject to battery optimization - SMS notifications may stop when app is inactive"
            )
        }
    }

    /**
     * Should we show the battery optimization request to the user?
     */
    fun shouldRequestBatteryOptimizationExemption(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        // Check if already ignored
        if (isIgnoringBatteryOptimizations(context)) {
            return false
        }

        // Check if user has already dismissed this request
        val prefs = context.getSharedPreferences("battery_optimization", Context.MODE_PRIVATE)
        val userDismissed = prefs.getBoolean("user_dismissed_request", false)

        return !userDismissed
    }

    /**
     * Mark that user has dismissed the battery optimization request
     */
    fun markRequestDismissed(context: Context) {
        val prefs = context.getSharedPreferences("battery_optimization", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_dismissed_request", true).apply()

        logger.debug(
            where = "markRequestDismissed",
            what = "User dismissed battery optimization request"
        )
    }

    /**
     * Reset the dismissal flag (for testing or settings)
     */
    fun resetDismissal(context: Context) {
        val prefs = context.getSharedPreferences("battery_optimization", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_dismissed_request", false).apply()

        logger.debug(
            where = "resetDismissal",
            what = "Reset battery optimization dismissal flag"
        )
    }
}
