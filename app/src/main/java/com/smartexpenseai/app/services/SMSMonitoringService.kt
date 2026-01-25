package com.smartexpenseai.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.smartexpenseai.app.MainActivity
import com.smartexpenseai.app.R
import com.smartexpenseai.app.utils.logging.StructuredLogger

/**
 * SMSMonitoringService - Foreground Service for 100% Reliable SMS Monitoring
 *
 * This service ensures that the app stays alive in the background to monitor
 * SMS messages continuously, similar to how WhatsApp, Telegram, and other
 * messaging apps work.
 *
 * Why Foreground Service?
 * - BroadcastReceivers can be killed by Android's battery optimization
 * - Aggressive OEMs (Xiaomi, Huawei, OnePlus) have extra battery-saving features
 * - Foreground services are protected from being killed by the system
 * - Required for reliable 24/7 SMS monitoring
 *
 * Trade-off:
 * - Shows a persistent notification (can be minimized to low priority)
 * - User sees "Smart Expense Manager is monitoring SMS" in status bar
 * - Similar to "WhatsApp is running" notification
 */
class SMSMonitoringService : Service() {

    private val logger = StructuredLogger(
        featureTag = "SERVICE",
        className = "SMSMonitoringService"
    )

    companion object {
        private const val NOTIFICATION_ID = 10001
        private const val CHANNEL_ID = "sms_monitoring_service"
        private const val CHANNEL_NAME = "SMS Monitoring"

        // Action to stop the service
        const val ACTION_STOP_SERVICE = "com.smartexpenseai.app.ACTION_STOP_SERVICE"

        /**
         * Start the SMS monitoring service
         */
        fun start(context: Context) {
            val intent = Intent(context, SMSMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the SMS monitoring service
         */
        fun stop(context: Context) {
            val intent = Intent(context, SMSMonitoringService::class.java)
            context.stopService(intent)
        }

        /**
         * Check if the service is running
         */
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (manager != null) {
                @Suppress("DEPRECATION")
                for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                    if (SMSMonitoringService::class.java.name == service.service.className) {
                        return true
                    }
                }
            }
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        logger.info(
            where = "onCreate",
            what = "📡 SMS Monitoring Service created"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.info(
            where = "onStartCommand",
            what = "🚀 SMS Monitoring Service starting..."
        )

        // Handle stop action
        if (intent?.action == ACTION_STOP_SERVICE) {
            logger.info(
                where = "onStartCommand",
                what = "🛑 Stop service requested by user"
            )
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        // Create notification channel (required for Android 8.0+)
        createNotificationChannel()

        // Create notification
        val notification = createNotification()

        // Start foreground service with dataSync type (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        logger.info(
            where = "onStartCommand",
            what = "✅ SMS Monitoring Service is now running in foreground"
        )

        // Return START_STICKY to ensure service is restarted if killed by system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This service doesn't support binding
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.info(
            where = "onDestroy",
            what = "🛑 SMS Monitoring Service destroyed"
        )
    }

    /**
     * Create notification channel for the foreground service
     * Required for Android 8.0 (API 26) and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low priority - won't make sound/vibration
            ).apply {
                description = "Keeps the app running to monitor SMS transactions"
                setShowBadge(false) // Don't show badge on app icon
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            logger.debug(
                where = "createNotificationChannel",
                what = "Notification channel created for SMS monitoring service"
            )
        }
    }

    /**
     * Create the persistent notification for the foreground service
     */
    private fun createNotification(): Notification {
        // Intent to open the app when notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop the service
        val stopIntent = Intent(this, SMSMonitoringService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Expense Manager")
            .setContentText("Monitoring SMS for transactions")
            .setSmallIcon(R.drawable.ic_money) // Use your app icon
            .setOngoing(true) // Can't be dismissed by user
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority - appears at bottom
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false) // Don't show timestamp
            .addAction(
                R.drawable.ic_close, // You'll need to add this icon
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    /**
     * Called when service is killed by system due to low memory
     * Return START_STICKY to request restart
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        logger.warn(
            where = "onTaskRemoved",
            what = "⚠️ Task removed - service will be restarted by system"
        )

        // Restart the service when task is removed
        val restartServiceIntent = Intent(applicationContext, SMSMonitoringService::class.java)
        val restartServicePendingIntent = PendingIntent.getService(
            this,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }
}
