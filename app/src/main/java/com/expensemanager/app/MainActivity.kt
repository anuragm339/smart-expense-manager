package com.expensemanager.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView

import com.expensemanager.app.utils.logging.LogConfig
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.expensemanager.app.databinding.ActivityMainBinding
import com.expensemanager.app.notifications.TransactionNotificationManager
import com.expensemanager.app.services.SMSParsingService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import com.expensemanager.app.domain.repository.TransactionRepositoryInterface
import com.expensemanager.app.utils.logging.StructuredLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var transactionRepository: TransactionRepositoryInterface

    @Inject
    lateinit var dataMigrationManager: com.expensemanager.app.data.migration.DataMigrationManager

    @Inject
    lateinit var smsParsingService: SMSParsingService

    private lateinit var binding: ActivityMainBinding
    private val logger = StructuredLogger("MainActivity","MainActivity")
        companion object {
        private const val SMS_PERMISSION_REQUEST_CODE = 1001
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupNavigation()
        handleNotificationIntent(intent)
        checkAndRequestPermissions()
        
        // If SMS permissions are already granted, check notification permission
        if (hasSMSPermissions()) {
            logger.debug("onCreate","SMS permissions already granted, checking notification permission")
            requestNotificationPermissionIfNeeded()
        } else {
            logger.debug("onCreate","SMS permissions not granted, will request notification permission after SMS is granted")
        }
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Configure top-level destinations (no back button)
        
        binding.bottomNavigation.setupWithNavController(navController)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }
    
    private fun handleNotificationIntent(intent: Intent?) {
        try {
            if (intent != null) {
                val action = intent.getStringExtra("action")
                when (action) {
                    "create_category_for_transaction" -> {
                        val transactionId = intent.getStringExtra(TransactionNotificationManager.EXTRA_TRANSACTION_ID)
                        val amount = intent.getDoubleExtra(TransactionNotificationManager.EXTRA_TRANSACTION_AMOUNT, 0.0)
                        val merchant = intent.getStringExtra(TransactionNotificationManager.EXTRA_TRANSACTION_MERCHANT)
                        
                        if (transactionId != null) {
                            // Navigate to categories tab
                            binding.bottomNavigation.selectedItemId = R.id.navigation_categories
                            
                            // Show info to user about creating category
                            Toast.makeText(
                                this,
                                "Add category for ₹${String.format("%.0f", amount)} at $merchant",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    else -> {
                        // Handle regular transaction clicks by navigating to messages
                        val transactionId = intent.getStringExtra(TransactionNotificationManager.EXTRA_TRANSACTION_ID)
                        if (transactionId != null) {
                            binding.bottomNavigation.selectedItemId = R.id.navigation_messages
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Prevent crash from notification handling
            logger.error("onCreate","Error handling notification intent",e)
            Toast.makeText(this, "Opening expense manager...", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

            logger.debug("requestNotificationPermissionIfNeeded","Android API ${Build.VERSION.SDK_INT}, Notification permission granted: $hasNotificationPermission")
            
            if (!hasNotificationPermission) {
                logger.debug("requestNotificationPermissionIfNeeded","Requesting notification permission")
                
                // Check if we should show rationale for notification permission
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    logger.debug("requestNotificationPermissionIfNeeded","Showing notification permission rationale")
                    showNotificationPermissionRationale()
                } else {
                    logger.debug("requestNotificationPermissionIfNeeded","Directly requesting notification permission")
                    requestNotificationPermissions()
                }
            } else {
                logger.debug("requestNotificationPermissionIfNeeded","Notification permission already granted")
            }
        } else {
            logger.debug("requestNotificationPermissionIfNeeded","Android API ${Build.VERSION.SDK_INT}, notification permission not required")
        }
    }
    
    private fun showNotificationPermissionRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_notification_title)
            .setMessage(R.string.permission_notification_message)
            .setPositiveButton(R.string.grant_permissions) { _, _ ->
                requestNotificationPermissions()
            }
            .setNegativeButton(R.string.not_now) { dialog, _ ->
                dialog.dismiss()
                // Don't block the user from using the app
                Toast.makeText(this, "You can enable notifications later in Settings", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun requestNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
    
    private fun hasSMSPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            if (shouldShowRequestPermissionRationale(missingPermissions)) {
                showPermissionRationale()
            } else {
                requestPermissions()
            }
        }
    }
    
    private fun shouldShowRequestPermissionRationale(permissions: List<String>): Boolean {
        return permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }
    }
    
    private fun showPermissionRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_sms_title)
            .setMessage(R.string.permission_sms_message)
            .setPositiveButton(R.string.grant_permissions) { _, _ ->
                requestPermissions()
            }
            .setNegativeButton(R.string.not_now) { dialog, _ ->
                dialog.dismiss()
                showPermissionDeniedInfo()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            SMS_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            SMS_PERMISSION_REQUEST_CODE -> {
                val allPermissionsGranted = grantResults.isNotEmpty() && 
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                
                if (allPermissionsGranted) {
                    showPermissionGrantedInfo()

                    // 🔧 BUG FIX #1: Retry initial SMS import if it was skipped during app launch
                    lifecycleScope.launch {
                        logger.debug("onRequestPermissionsResult","SMS permission granted - triggering migration retry...")
                        dataMigrationManager.retryInitialSMSImportIfNeeded()
                    }

                    scanHistoricalSMS()
                    // After SMS permissions are granted, also request notification permission
                    requestNotificationPermissionIfNeeded()
                } else {
                    showPermissionDeniedInfo()
                }
            }
            
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showNotificationPermissionGrantedInfo()
                } else {
                    showNotificationPermissionDeniedInfo()
                }
            }
        }
    }
    
    private fun showPermissionGrantedInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_granted_title)
            .setMessage(R.string.permission_granted_message)
            .setPositiveButton(R.string.get_started) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showPermissionDeniedInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_denied_title)
            .setMessage(R.string.permission_denied_message)
            .setPositiveButton(R.string.i_understand) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(R.string.try_again) { _, _ ->
                checkAndRequestPermissions()
            }
            .show()
    }
    
    private fun showNotificationPermissionGrantedInfo() {
        Toast.makeText(
            this, 
            "Notification permission granted! You'll receive instant alerts for new transactions.", 
            Toast.LENGTH_LONG
        ).show()
        
        // Log notification capability for debugging
        val notificationManager = TransactionNotificationManager(this)
        logger.debug("showNotificationPermissionGrantedInfo","Notifications enabled: ${notificationManager.areNotificationsEnabled()}")
    }
    
    private fun showNotificationPermissionDeniedInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_notification_denied_title)
            .setMessage(R.string.permission_notification_denied_message)
            .setPositiveButton(R.string.i_understand) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun scanHistoricalSMS() {
        lifecycleScope.launch {
            var progressDialog: AlertDialog? = null
            var progressView: ProgressBar? = null
            var progressText: TextView? = null
            
            try {
                
                // Create custom progress dialog with percentage indicator
                val dialogView = layoutInflater.inflate(R.layout.dialog_sms_progress, null)
                progressView = dialogView.findViewById<ProgressBar>(R.id.progressBar)
                progressText = dialogView.findViewById<TextView>(R.id.progressText)
                val statusText = dialogView.findViewById<TextView>(R.id.statusText)
                
                progressDialog = MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Scanning SMS History")
                    .setView(dialogView)
                    .setCancelable(true)
                    .setNegativeButton("Cancel") { dialog, _ ->
                        logger.debug("scanHistoricalSMS","User cancelled SMS scan")
                        dialog.dismiss()
                    }
                    .create()
                progressDialog.show()
                
                // Initialize progress
                progressView?.progress = 0
                progressText?.text = "0%"
                statusText?.text = "Initializing scan..."
                
                // Add timeout protection
                val timeoutMillis = 60000L // 60 seconds timeout
                val startTime = System.currentTimeMillis()
                
                // Use injected repository to sync SMS and store in database
                val repository = transactionRepository
                
                // Create SMS reader with progress callback for repository sync
                val syncResult = withTimeoutOrNull(timeoutMillis) {
                    val initialCount = repository.getTransactionCount()
                    val lastSyncTimestamp = repository.getLastSyncTimestamp() ?: Date(0)

                    // Scan SMS history using unified parsing service with progress updates
                    val transactions = smsParsingService.scanHistoricalSMS { current, total, status ->
                        runOnUiThread {
                            val percentage = if (total > 0) ((current * 100) / total) else 0
                            progressView?.progress = percentage
                            progressText?.text = "$percentage%"
                            statusText?.text = status

                            logger.debug("scanHistoricalSMS", "Progress: $percentage% ($current/$total) - $status")
                        }
                    }

                    val newTransactionsFromScan = transactions.count { sms -> sms.date.after(lastSyncTimestamp) }

                    // Sync transactions through repository (handles deduplication internally)
                    val insertedCount = repository.syncNewSMS()

                    logger.debug("scanHistoricalSMS", "Inserted $insertedCount new transactions into database")
                    
                    // Update sync state
                    repository.updateSyncState(Date())
                    val totalDatabaseCount = repository.getTransactionCount()

                    ScanResult(
                        totalParsed = transactions.size,
                        newlyDiscovered = newTransactionsFromScan,
                        inserted = insertedCount,
                        totalInDatabase = totalDatabaseCount,
                        initialCount = initialCount
                    )
                }
                
                val scanDuration = System.currentTimeMillis() - startTime
                logger.debug("scanHistoricalSMS", "SMS scan completed in ${scanDuration}ms")
                
                progressDialog?.dismiss()
                progressDialog = null
                
                if (syncResult == null) {
                    // Timeout occurred
                    logger.debug("scanHistoricalSMS", "SMS scan timed out after ${timeoutMillis}ms")
                    Toast.makeText(
                        this@MainActivity,
                        "SMS scan is taking longer than expected. You can try again later or check the Messages tab.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                val (totalParsed, newlyDiscovered, insertedCount, totalDatabaseCount, initialCount) = syncResult

                val duplicatesSkipped = if (newlyDiscovered > 0) {
                    (newlyDiscovered - insertedCount).coerceAtLeast(0)
                } else 0
                val previousCount = initialCount
                val netChange = totalDatabaseCount - previousCount

                // Show results
                if (totalParsed > 0) {
                    val messageBuilder = StringBuilder().apply {
                        append("Analyzed $totalParsed SMS transactions from the last 6 months.\n\n")
                        when {
                            insertedCount > 0 -> {
                                append("Added $insertedCount new transaction")
                                if (insertedCount != 1) append("s")
                                append(" to your expense database")
                                if (duplicatesSkipped > 0) {
                                    append(" (skipped $duplicatesSkipped duplicate entries)")
                                }
                                append(".\n\n")
                            }
                            newlyDiscovered == 0 -> {
                                append("No new SMS since your last import, so nothing was added.\n\n")
                            }
                            else -> {
                                append("All $newlyDiscovered SMS already exist in your database, so nothing new was added")
                                if (duplicatesSkipped > 0) {
                                    append(" (skipped $duplicatesSkipped duplicate entries)")
                                }
                                append(".\n\n")
                            }
                        }

                        append("You now have $totalDatabaseCount transactions tracked")
                        when {
                            netChange > 0 -> append(" (previously $previousCount).")
                            netChange == 0 -> append(" (unchanged).")
                            else -> append(".")
                        }
                    }

                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("SMS Scan Complete! 🎉")
                        .setMessage(messageBuilder.toString())
                        .setPositiveButton("View Dashboard") { _, _ ->
                            // Navigate back to dashboard to see the updated data
                            binding.bottomNavigation.selectedItemId = R.id.navigation_dashboard
                        }
                        .setNegativeButton("View Messages") { _, _ ->
                            // Navigate to messages tab
                            binding.bottomNavigation.selectedItemId = R.id.navigation_messages
                        }
                        .show()
                    
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "No bank transactions found in recent SMS history",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: Exception) {
                progressDialog?.dismiss()
                logger.error("scanHistoricalSMS", "Error during SMS scan",e)
                
                val errorMessage = when (e) {
                    is SecurityException -> "SMS permission was revoked during scan"
                    is TimeoutCancellationException -> "SMS scan timed out - too many messages to process"
                    else -> "Error scanning SMS: ${e.message ?: "Unknown error"}"
                }
                
                Toast.makeText(
                    this@MainActivity,
                    errorMessage,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

private data class ScanResult(
    val totalParsed: Int,
    val newlyDiscovered: Int,
    val inserted: Int,
    val totalInDatabase: Int,
    val initialCount: Int
)
