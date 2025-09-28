package com.expensemanager.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.expensemanager.app.MainActivity
import com.expensemanager.app.R
import com.expensemanager.app.data.models.Transaction
import androidx.core.content.ContextCompat
import android.util.Log

class TransactionNotificationManager(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "transaction_notifications"
        private const val CHANNEL_NAME = "Transaction Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications for new transaction detections"
        private const val NOTIFICATION_ID_BASE = 1000
        
        // Action identifiers for notification actions
        const val ACTION_CATEGORIZE = "ACTION_CATEGORIZE"
        const val ACTION_MARK_PROCESSED = "ACTION_MARK_PROCESSED"
        const val ACTION_CREATE_CATEGORY = "ACTION_CREATE_CATEGORY"
        
        // Intent extras
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_TRANSACTION_AMOUNT = "transaction_amount"
        const val EXTRA_TRANSACTION_MERCHANT = "transaction_merchant"
        const val EXTRA_CATEGORY = "category"
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            }
            
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showNewTransactionNotification(transaction: Transaction) {
        val notificationId = (NOTIFICATION_ID_BASE + transaction.id.hashCode()).let { 
            if (it < 0) -it else it
        }
        
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_TRANSACTION_ID, transaction.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val mainPendingIntent = PendingIntent.getActivity(
            context, 
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create quick category actions
        val foodAction = createCategoryAction("Food & Dining", transaction, notificationId + 1)
        val shoppingAction = createCategoryAction("Shopping", transaction, notificationId + 2)
        val transportAction = createCategoryAction("Transportation", transaction, notificationId + 3)
        val createCategoryAction = createCustomCategoryAction(transaction, notificationId + 4)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_money)
            .setContentTitle("New Transaction Detected")
            .setContentText("₹${String.format("%.0f", transaction.amount)} at ${transaction.merchant}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("₹${String.format("%.0f", transaction.amount)} spent at ${transaction.merchant}\n" +
                        "Bank: ${transaction.bankName}\n" +
                        "Tap to categorize this transaction"))
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(foodAction)
            .addAction(shoppingAction)
            .addAction(transportAction)
            .addAction(createCategoryAction)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != 
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // In a real app, you would request the permission here.
            // For this example, we'll just log it.
            Log.w("TransactionNotificationManager", "POST_NOTIFICATIONS permission not granted")
            return
        }
        notificationManager.notify(notificationId, notification)
    }
    
    private fun createCategoryAction(category: String, transaction: Transaction, requestCode: Int): NotificationCompat.Action {
        val intent = Intent(context, TransactionNotificationReceiver::class.java).apply {
            action = ACTION_CATEGORIZE
            putExtra(EXTRA_TRANSACTION_ID, transaction.id)
            putExtra(EXTRA_CATEGORY, category)
            putExtra(EXTRA_TRANSACTION_AMOUNT, transaction.amount)
            putExtra(EXTRA_TRANSACTION_MERCHANT, transaction.merchant)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            R.drawable.ic_category,
            category,
            pendingIntent
        ).build()
    }
    
    private fun createCustomCategoryAction(transaction: Transaction, requestCode: Int): NotificationCompat.Action {
        val intent = Intent(context, TransactionNotificationReceiver::class.java).apply {
            action = ACTION_CREATE_CATEGORY
            putExtra(EXTRA_TRANSACTION_ID, transaction.id)
            putExtra(EXTRA_TRANSACTION_AMOUNT, transaction.amount)
            putExtra(EXTRA_TRANSACTION_MERCHANT, transaction.merchant)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            R.drawable.ic_add,
            "Add Category",
            pendingIntent
        ).build()
    }
    
    fun showCategoryUpdateConfirmation(transactionAmount: Double, merchant: String, category: String) {
        val notificationId = System.currentTimeMillis().toInt()
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_category)
            .setContentTitle("Transaction Categorized")
            .setContentText("₹${String.format("%.0f", transactionAmount)} at $merchant → $category")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(3000) // Auto dismiss after 3 seconds
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != 
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // In a real app, you would request the permission here.
            // For this example, we'll just log it.
            Log.w("TransactionNotificationManager", "POST_NOTIFICATIONS permission not granted")
            return
        }
        notificationManager.notify(notificationId, notification)
    }
    
    fun dismissNotification(transactionId: String) {
        val notificationId = (NOTIFICATION_ID_BASE + transactionId.hashCode()).let { 
            if (it < 0) -it else it
        }
        notificationManager.cancel(notificationId)
    }
    
    fun showBulkTransactionsSummary(transactionCount: Int, totalAmount: Double) {
        val notificationId = NOTIFICATION_ID_BASE + 999
        
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_money)
            .setContentTitle("$transactionCount New Transactions")
            .setContentText("Total: ₹${String.format("%.0f", totalAmount)}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$transactionCount new transactions detected\n" +
                        "Total amount: ₹${String.format("%.0f", totalAmount)}\n" +
                        "Tap to review and categorize"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != 
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // In a real app, you would request the permission here.
            // For this example, we'll just log it.
            Log.w("TransactionNotificationManager", "POST_NOTIFICATIONS permission not granted")
            return
        }
        notificationManager.notify(notificationId, notification)
    }
    
    fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }
}
