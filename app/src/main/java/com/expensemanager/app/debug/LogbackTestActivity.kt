package com.expensemanager.app.debug

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.expensemanager.app.databinding.ActivityLogbackTestBinding
import com.expensemanager.app.utils.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Debug activity for testing Logback logging implementation.
 * This activity demonstrates various logging scenarios and verifies
 * that logs are properly written to files and console.
 */
@AndroidEntryPoint
class LogbackTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogbackTestBinding

    @Inject
    lateinit var appLogger: AppLogger

    companion object {
        private const val TAG = "LogbackTest"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityLogbackTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        
        // Test basic logging on startup
        appLogger.info(TAG, "LogbackTestActivity started")
    }

    private fun setupUI() {
        binding.apply {
            
            // Test Different Log Levels
            buttonTestLogLevels.setOnClickListener {
                testLogLevels()
            }
            
            // Test SMS Processing Logging
            buttonTestSmsLogging.setOnClickListener {
                testSMSLogging()
            }
            
            // Test Transaction Logging
            buttonTestTransactionLogging.setOnClickListener {
                testTransactionLogging()
            }
            
            // Test Database Logging
            buttonTestDatabaseLogging.setOnClickListener {
                testDatabaseLogging()
            }
            
            // Test AI Processing Logging
            buttonTestAiLogging.setOnClickListener {
                testAILogging()
            }
            
            // Test Error Logging
            buttonTestErrorLogging.setOnClickListener {
                testErrorLogging()
            }
            
            // Check Log Files
            buttonCheckLogFiles.setOnClickListener {
                checkLogFiles()
            }
            
            // Clear Logs
            buttonClearLogs.setOnClickListener {
                clearLogs()
            }
        }
    }

    private fun testLogLevels() {
        appLogger.info(TAG, "Testing different log levels...")
        
        appLogger.trace(TAG, "This is a TRACE message")
        appLogger.debug(TAG, "This is a DEBUG message with parameter: {}", "test-value")
        appLogger.info(TAG, "This is an INFO message")
        appLogger.warn(TAG, "This is a WARN message")
        appLogger.error(TAG, "This is an ERROR message")
        
        Toast.makeText(this, "Log levels tested - check console and log files", Toast.LENGTH_SHORT).show()
    }

    private fun testSMSLogging() {
        appLogger.info(TAG, "Testing SMS processing logging...")
        
        // Test successful SMS processing
        appLogger.logSMSProcessing(
            sender = "HD-HDFCBK",
            message = "Rs.2,500.00 debited from A/c **1234 on 01-Jan-24 at SWIGGY BANGALORE",
            success = true,
            details = "Transaction parsed successfully - amount: 2500.0, merchant: SWIGGY"
        )
        
        // Test failed SMS processing
        appLogger.logSMSProcessing(
            sender = "UNKNOWN",
            message = "Invalid SMS format for transaction parsing",
            success = false,
            details = "Unable to extract transaction details from SMS content"
        )
        
        Toast.makeText(this, "SMS logging tested", Toast.LENGTH_SHORT).show()
    }

    private fun testTransactionLogging() {
        appLogger.info(TAG, "Testing transaction logging...")
        
        appLogger.logTransaction(
            action = "CREATED",
            transactionId = "test-tx-001",
            amount = 1250.75,
            merchant = "Zomato"
        )
        
        appLogger.logTransaction(
            action = "UPDATED",
            transactionId = "test-tx-001",
            amount = 1250.75,
            merchant = "Zomato Online"
        )
        
        appLogger.logTransaction(
            action = "DELETED",
            transactionId = "test-tx-001",
            amount = 1250.75,
            merchant = "Zomato Online"
        )
        
        Toast.makeText(this, "Transaction logging tested", Toast.LENGTH_SHORT).show()
    }

    private fun testDatabaseLogging() {
        appLogger.info(TAG, "Testing database operation logging...")
        
        appLogger.logDatabaseOperation(
            operation = "INSERT",
            table = "transactions",
            success = true,
            recordsAffected = 1
        )
        
        appLogger.logDatabaseOperation(
            operation = "UPDATE",
            table = "merchants",
            success = true,
            recordsAffected = 3
        )
        
        appLogger.logDatabaseOperation(
            operation = "DELETE",
            table = "categories",
            success = false,
            recordsAffected = 0
        )
        
        Toast.makeText(this, "Database logging tested", Toast.LENGTH_SHORT).show()
    }

    private fun testAILogging() {
        appLogger.info(TAG, "Testing AI processing logging...")
        
        appLogger.logAIOperation(
            operation = "CATEGORIZE_TRANSACTION",
            inputSize = 1,
            outputSize = 1,
            processingTime = 234
        )
        
        appLogger.logAIOperation(
            operation = "GENERATE_INSIGHTS",
            inputSize = 150,
            outputSize = 5,
            processingTime = 1567
        )
        
        Toast.makeText(this, "AI logging tested", Toast.LENGTH_SHORT).show()
    }

    private fun testErrorLogging() {
        appLogger.info(TAG, "Testing error logging...")
        
        try {
            // Simulate an error
            throw RuntimeException("Test exception for logging")
        } catch (e: Exception) {
            appLogger.error(TAG, "Caught test exception", e)
        }
        
        appLogger.warn(TAG, "This is a warning with context: database connection slow")
        appLogger.error(TAG, "This is an error message without exception")
        
        Toast.makeText(this, "Error logging tested", Toast.LENGTH_SHORT).show()
    }

    private fun checkLogFiles() {
        lifecycleScope.launch {
            try {
                val logFiles = appLogger.getLogFiles()
                
                withContext(Dispatchers.Main) {
                    val message = if (logFiles.isNotEmpty()) {
                        val totalSize = logFiles.sumOf { it.length() }
                        "Found ${logFiles.size} log files, total size: ${formatFileSize(totalSize)}"
                    } else {
                        "No log files found"
                    }
                    
                    binding.textLogStatus.text = message
                    Toast.makeText(this@LogbackTestActivity, message, Toast.LENGTH_LONG).show()
                    
                    appLogger.info(TAG, "Log file check completed: $message")
                }
                
            } catch (e: Exception) {
                appLogger.error(TAG, "Error checking log files", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LogbackTestActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearLogs() {
        lifecycleScope.launch {
            try {
                appLogger.clearLogFiles()
                
                withContext(Dispatchers.Main) {
                    binding.textLogStatus.text = "Log files cleared"
                    Toast.makeText(this@LogbackTestActivity, "Log files cleared", Toast.LENGTH_SHORT).show()
                }
                
                appLogger.info(TAG, "Log files cleared by user in test activity")
                
            } catch (e: Exception) {
                appLogger.error(TAG, "Error clearing log files", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LogbackTestActivity, "Error clearing logs: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.1f %s", size, units[unitIndex])
    }
}