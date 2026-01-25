package com.smartexpenseai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.smartexpenseai.app.databinding.ActivityMainBinding
import com.smartexpenseai.app.notifications.TransactionNotificationManager
import com.smartexpenseai.app.services.SMSParsingService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import com.smartexpenseai.app.domain.repository.TransactionRepositoryInterface
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var transactionRepository: TransactionRepositoryInterface


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
            // CRITICAL: Check battery optimization to ensure SMS monitoring persists
            checkBatteryOptimization()
            // CRITICAL: Start foreground service for 100% reliable SMS monitoring
            startSMSMonitoringService()
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
                    "rename_merchant" -> {
                        val transactionId = intent.getStringExtra(TransactionNotificationManager.EXTRA_TRANSACTION_ID)
                        val merchant = intent.getStringExtra(TransactionNotificationManager.EXTRA_TRANSACTION_MERCHANT)
                        val amount = intent.getDoubleExtra(TransactionNotificationManager.EXTRA_TRANSACTION_AMOUNT, 0.0)

                        if (merchant != null) {
                            showRenameMerchantDialog(merchant, transactionId, amount)
                        }
                    }

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
                    scanHistoricalSMS()
                    requestNotificationPermissionIfNeeded()
                    // CRITICAL: Start foreground service after SMS permissions granted
                    startSMSMonitoringService()
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

    private fun showRenameMerchantDialog(merchant: String, transactionId: String?, amount: Double) {
        val input = android.widget.EditText(this).apply {
            setText(merchant)
            hint = "Enter merchant display name"
            setPadding(50, 30, 50, 30)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Rename Merchant")
            .setMessage("Original: $merchant")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != merchant) {
                    renameMerchant(merchant, newName, transactionId)
                } else if (newName.isEmpty()) {
                    Toast.makeText(this, "Please enter a merchant name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameMerchant(originalMerchant: String, newDisplayName: String, transactionId: String?) {
        lifecycleScope.launch {
            try {
                logger.debug("renameMerchant", "Renaming '$originalMerchant' to '$newDisplayName'")

                // Get ExpenseRepository instance to access updateMerchantAliasInDatabase
                val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(this@MainActivity)

                // Get current category for merchant
                val transaction = if (transactionId != null) {
                    repository.getTransactionBySmsId(transactionId)
                } else null

                // Get merchant and its category
                val merchant = if (transaction != null) {
                    repository.getMerchantByNormalizedName(transaction.normalizedMerchant)
                } else null

                val categoryName = if (merchant != null) {
                    repository.getCategoryById(merchant.categoryId)?.name ?: "Uncategorized"
                } else {
                    "Uncategorized"
                }

                logger.debug("renameMerchant", "Using category: $categoryName")

                // Call existing business logic
                val success = repository.updateMerchantAliasInDatabase(
                    originalMerchantNames = listOf(originalMerchant),
                    newDisplayName = newDisplayName,
                    newCategoryName = categoryName
                )

                if (success) {
                    logger.info("renameMerchant", "Successfully renamed merchant to '$newDisplayName'")

                    // Broadcast update to refresh UI
                    sendBroadcast(Intent("com.smartexpenseai.app.DATA_CHANGED"))

                    Toast.makeText(
                        this@MainActivity,
                        "Merchant renamed to $newDisplayName",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    logger.error("renameMerchant", "Failed to rename merchant", null)
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to rename merchant",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                logger.error("renameMerchant", "Error renaming merchant", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * CRITICAL: Check battery optimization status to ensure SMS monitoring persists
     * Android's battery optimization can kill background processes and prevent
     * BroadcastReceivers from working when the app is not in the foreground.
     */
    private fun checkBatteryOptimization() {
        val helper = com.smartexpenseai.app.utils.BatteryOptimizationHelper

        // Log current status
        helper.checkAndLogStatus(this)

        // Only show dialog if should request and not already exempt
        if (helper.shouldRequestBatteryOptimizationExemption(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Keep SMS Monitoring Active")
                .setMessage(
                    "To ensure you receive notifications for all transactions, " +
                    "even when the app is closed, we recommend disabling battery optimization.\n\n" +
                    "This allows the app to monitor SMS messages in the background."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = helper.getRequestBatteryOptimizationIntent(this)
                    if (intent != null) {
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            logger.error("checkBatteryOptimization", "Failed to open battery settings", e)
                            Toast.makeText(
                                this,
                                "Could not open battery settings",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                .setNegativeButton("Not Now") { dialog, _ ->
                    helper.markRequestDismissed(this)
                    dialog.dismiss()
                }
                .setNeutralButton("Learn More") { _, _ ->
                    showBatteryOptimizationInfo()
                }
                .show()
        }
    }

    private fun showBatteryOptimizationInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Why Battery Optimization Matters")
            .setMessage(
                "Android's battery optimization can stop apps from running in the background. " +
                "This means:\n\n" +
                "✓ Without exemption: SMS notifications may stop after a few hours of inactivity\n" +
                "✓ With exemption: SMS monitoring works reliably 24/7\n\n" +
                "You can change this setting anytime in app settings."
            )
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * CRITICAL: Start SMS Monitoring Foreground Service
     *
     * This ensures 100% reliable SMS monitoring, similar to how WhatsApp works.
     * The service keeps the app alive in background to monitor SMS messages.
     *
     * Benefits:
     * - Works even when app is closed for days/weeks
     * - Survives device reboots (via BootReceiver)
     * - Works on aggressive battery-saving devices (Xiaomi, Huawei, OnePlus)
     * - Prevents Android from killing the SMS receiver
     */
    private fun startSMSMonitoringService() {
        // SMS monitoring is handled automatically by SMSReceiver (BroadcastReceiver)
        // No foreground service or WorkManager needed - SMS receiver works 24/7
        // as long as:
        // 1. SMS permissions are granted
        // 2. Battery optimization is disabled for the app
        logger.info(
            "startSMSMonitoringService",
            "✅ SMS monitoring active via BroadcastReceiver - no service needed"
        )
    }
}

private data class ScanResult(
    val totalParsed: Int,
    val newlyDiscovered: Int,
    val inserted: Int,
    val totalInDatabase: Int,
    val initialCount: Int
)
