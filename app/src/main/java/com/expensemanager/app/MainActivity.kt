package com.expensemanager.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.expensemanager.app.databinding.ActivityMainBinding
import com.expensemanager.app.notifications.TransactionNotificationManager
import com.expensemanager.app.utils.SMSHistoryReader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
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
        requestNotificationPermissionIfNeeded()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Configure top-level destinations (no back button)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_dashboard,
                R.id.navigation_insights,
                R.id.navigation_messages,
                R.id.navigation_categories,
                R.id.navigation_profile
            )
        )
        
        setupActionBarWithNavController(navController, appBarConfiguration)
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
            android.util.Log.e("MainActivity", "Error handling notification intent", e)
            Toast.makeText(this, "Opening expense manager...", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp() || super.onSupportNavigateUp()
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
                } else {
                    showPermissionDeniedInfo()
                }
            }
            
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Notification permission granted. You'll receive notifications for new transactions!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Notification permission denied. You won't receive push notifications for new transactions.", Toast.LENGTH_LONG).show()
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
    
    private fun scanHistoricalSMS() {
        lifecycleScope.launch {
            var progressDialog: androidx.appcompat.app.AlertDialog? = null
            var progressView: android.widget.ProgressBar? = null
            var progressText: android.widget.TextView? = null
            
            try {
                Log.d("MainActivity", "🚀 Starting SMS scan process...")
                
                // Create custom progress dialog with percentage indicator
                val dialogView = layoutInflater.inflate(R.layout.dialog_sms_progress, null)
                progressView = dialogView.findViewById<android.widget.ProgressBar>(R.id.progressBar)
                progressText = dialogView.findViewById<android.widget.TextView>(R.id.progressText)
                val statusText = dialogView.findViewById<android.widget.TextView>(R.id.statusText)
                
                progressDialog = MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Scanning SMS History")
                    .setView(dialogView)
                    .setCancelable(true)
                    .setNegativeButton("Cancel") { dialog, _ ->
                        Log.d("MainActivity", "❌ User cancelled SMS scan")
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
                
                // Use repository to sync SMS and store in database
                val repository = com.expensemanager.app.data.repository.ExpenseRepository.getInstance(this@MainActivity)
                
                // Create SMS reader with progress callback for repository sync
                val syncResult = kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                    // Create a progress-enabled SMS reader for repository sync
                    val smsReader = SMSHistoryReader(this@MainActivity) { current, total, status ->
                        // Update progress on main thread
                        runOnUiThread {
                            val percentage = if (total > 0) ((current * 100) / total) else 0
                            progressView?.progress = percentage
                            progressText?.text = "$percentage%"
                            statusText?.text = status
                            
                            Log.d("MainActivity", "📊 Progress: $percentage% ($current/$total) - $status")
                        }
                    }
                    
                    // Scan and store transactions through repository
                    val transactions = smsReader.scanHistoricalSMS()
                    Log.d("MainActivity", "📱 Found ${transactions.size} transactions from SMS scan")
                    
                    // Now insert them into the database through repository
                    var insertedCount = 0
                    for (parsedTransaction in transactions) {
                        try {
                            val transactionEntity = repository.convertToTransactionEntity(parsedTransaction)
                            
                            // Check if transaction already exists
                            val existingTransaction = repository.getTransactionBySmsId(transactionEntity.smsId)
                            if (existingTransaction == null) {
                                val insertedId = repository.insertTransaction(transactionEntity)
                                if (insertedId > 0) {
                                    insertedCount++
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error inserting transaction: ${parsedTransaction.id}", e)
                        }
                    }
                    
                    Log.d("MainActivity", "💾 Inserted $insertedCount new transactions into database")
                    
                    // Update sync state
                    repository.updateSyncState(java.util.Date())
                    
                    // Return both scan results and insert count
                    Pair(transactions, insertedCount)
                }
                
                val scanDuration = System.currentTimeMillis() - startTime
                Log.d("MainActivity", "⏱️ SMS scan completed in ${scanDuration}ms")
                
                progressDialog?.dismiss()
                progressDialog = null
                
                if (syncResult == null) {
                    // Timeout occurred
                    Log.w("MainActivity", "⚠️ SMS scan timed out after ${timeoutMillis}ms")
                    Toast.makeText(
                        this@MainActivity,
                        "SMS scan is taking longer than expected. You can try again later or check the Messages tab.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                val (transactions, insertedCount) = syncResult
                
                // Show results
                if (transactions.isNotEmpty()) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("SMS Scan Complete! 🎉")
                        .setMessage("Found ${transactions.size} transactions from your SMS history.\n\nSaved $insertedCount new transactions to your expense database.\n\nYour dashboard will now show your spending data!")
                        .setPositiveButton("View Dashboard") { _, _ ->
                            // Navigate back to dashboard to see the updated data
                            binding.bottomNavigation.selectedItemId = R.id.navigation_dashboard
                        }
                        .setNegativeButton("View Messages") { _, _ ->
                            // Navigate to messages tab
                            binding.bottomNavigation.selectedItemId = R.id.navigation_messages
                        }
                        .show()
                    
                    Log.d("MainActivity", "✅ SMS scan and database sync completed successfully")
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "No bank transactions found in recent SMS history",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: Exception) {
                progressDialog?.dismiss()
                Log.e("MainActivity", "❌ Error during SMS scan", e)
                
                val errorMessage = when (e) {
                    is SecurityException -> "SMS permission was revoked during scan"
                    is kotlinx.coroutines.TimeoutCancellationException -> "SMS scan timed out - too many messages to process"
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