package com.expensemanager.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.expensemanager.app.data.storage.TransactionStorage
import com.expensemanager.app.utils.CategoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionNotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "TransactionNotificationReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        val transactionId = intent.getStringExtra(TransactionNotificationManager.EXTRA_TRANSACTION_ID) ?: return
        val transactionAmount = intent.getDoubleExtra(TransactionNotificationManager.EXTRA_TRANSACTION_AMOUNT, 0.0)
        val transactionMerchant = intent.getStringExtra(TransactionNotificationManager.EXTRA_TRANSACTION_MERCHANT) ?: ""
        
        val notificationManager = TransactionNotificationManager(context)
        
        when (intent.action) {
            TransactionNotificationManager.ACTION_CATEGORIZE -> {
                val category = intent.getStringExtra(TransactionNotificationManager.EXTRA_CATEGORY) ?: return
                handleCategorizeAction(context, transactionId, category, transactionAmount, transactionMerchant, notificationManager)
            }
            
            TransactionNotificationManager.ACTION_CREATE_CATEGORY -> {
                handleCreateCategoryAction(context, transactionId, transactionAmount, transactionMerchant)
            }
            
            TransactionNotificationManager.ACTION_MARK_PROCESSED -> {
                handleMarkProcessedAction(context, transactionId, notificationManager)
            }
        }
    }
    
    private fun handleCategorizeAction(
        context: Context,
        transactionId: String,
        category: String,
        amount: Double,
        merchant: String,
        notificationManager: TransactionNotificationManager
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transactionStorage = TransactionStorage(context)
                val categoryManager = CategoryManager(context)
                
                // Load the transaction
                val transactions = transactionStorage.loadTransactions()
                val transaction = transactions.find { it.id == transactionId }
                
                if (transaction != null) {
                    // Update the transaction with the new category
                    val updatedTransaction = transaction.copy(
                        category = category,
                        isProcessed = true
                    )
                    
                    // Save the updated transaction
                    transactionStorage.updateTransaction(updatedTransaction)
                    
                    // Update category manager to learn from this categorization
                    categoryManager.updateCategory(transaction.merchant, category)
                    
                    Log.d(TAG, "Transaction $transactionId categorized as $category")
                    
                    // Show confirmation notification
                    notificationManager.showCategoryUpdateConfirmation(amount, merchant, category)
                    
                    // Dismiss the original notification
                    notificationManager.dismissNotification(transactionId)
                    
                    // Show toast on main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Transaction categorized as $category", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error categorizing transaction", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Failed to categorize transaction", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handleCreateCategoryAction(
        context: Context,
        transactionId: String,
        amount: Double,
        merchant: String
    ) {
        // Create an intent to open the app to the category creation screen
        val intent = Intent(context, com.expensemanager.app.MainActivity::class.java).apply {
            putExtra("action", "create_category_for_transaction")
            putExtra(TransactionNotificationManager.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(TransactionNotificationManager.EXTRA_TRANSACTION_AMOUNT, amount)
            putExtra(TransactionNotificationManager.EXTRA_TRANSACTION_MERCHANT, merchant)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
        
        Toast.makeText(context, "Opening app to create category", Toast.LENGTH_SHORT).show()
    }
    
    private fun handleMarkProcessedAction(
        context: Context,
        transactionId: String,
        notificationManager: TransactionNotificationManager
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transactionStorage = TransactionStorage(context)
                
                // Load the transaction
                val transactions = transactionStorage.loadTransactions()
                val transaction = transactions.find { it.id == transactionId }
                
                if (transaction != null) {
                    // Mark as processed
                    val updatedTransaction = transaction.copy(isProcessed = true)
                    transactionStorage.updateTransaction(updatedTransaction)
                    
                    // Dismiss notification
                    notificationManager.dismissNotification(transactionId)
                    
                    Log.d(TAG, "Transaction $transactionId marked as processed")
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Transaction marked as processed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking transaction as processed", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Failed to process transaction", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}