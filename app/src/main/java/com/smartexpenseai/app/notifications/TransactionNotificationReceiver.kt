package com.smartexpenseai.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartexpenseai.app.utils.logging.StructuredLogger
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.utils.CategoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionNotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TransactionNotificationReceiver"
    }

    private val logger = StructuredLogger("TRANSACTION", TAG)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val transactionId = intent.getStringExtra(TransactionNotificationManager.EXTRA_TRANSACTION_ID) ?: return
        val transactionAmount = intent.getDoubleExtra(TransactionNotificationManager.EXTRA_TRANSACTION_AMOUNT, 0.0)
        val transactionMerchant = intent.getStringExtra(TransactionNotificationManager.EXTRA_TRANSACTION_MERCHANT) ?: ""

        val notificationManager = TransactionNotificationManager(context)

        // Use goAsync() to extend the receiver's lifetime for background work
        val pendingResult = goAsync()

        when (intent.action) {
            TransactionNotificationManager.ACTION_CATEGORIZE -> {
                val category = intent.getStringExtra(TransactionNotificationManager.EXTRA_CATEGORY) ?: return
                handleCategorizeAction(context, transactionId, category, transactionAmount, transactionMerchant, notificationManager, pendingResult)
            }

            TransactionNotificationManager.ACTION_RENAME_MERCHANT -> {
                handleRenameMerchantAction(context, intent, transactionId, transactionMerchant, transactionAmount, notificationManager, pendingResult)
            }

            TransactionNotificationManager.ACTION_CREATE_CATEGORY -> {
                handleCreateCategoryAction(context, transactionId, transactionAmount, transactionMerchant)
                pendingResult.finish()
            }

            TransactionNotificationManager.ACTION_MARK_PROCESSED -> {
                handleMarkProcessedAction(context, transactionId, notificationManager, pendingResult)
            }
        }
    }
    
    private fun handleCategorizeAction(
        context: Context,
        transactionId: String,
        category: String,
        amount: Double,
        merchant: String,
        notificationManager: TransactionNotificationManager,
        pendingResult: PendingResult
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ExpenseRepository.getInstance(context)
                val categoryManager = CategoryManager(context)
                
                logger.debug("handleCategorizeAction", "Categorizing transaction $transactionId as $category for merchant $merchant")
                
                // Find transaction by SMS ID in SQLite database
                val transaction = repository.getTransactionBySmsId(transactionId)
                
                if (transaction != null) {
                    // Get or create the new category in SQLite
                    var newCategoryEntity = repository.getCategoryByName(category)
                    if (newCategoryEntity == null) {
                        logger.debug("handleCategorizeAction", "Creating new category: $category")
                        val categoryToCreate = com.smartexpenseai.app.data.entities.CategoryEntity(
                            name = category,
                            emoji = getCategoryEmoji(category),
                            color = getRandomCategoryColor(),
                            isSystem = false,
                            displayOrder = 100,
                            createdAt = java.util.Date()
                        )
                        val categoryId = repository.insertCategory(categoryToCreate)
                        newCategoryEntity = repository.getCategoryById(categoryId)
                    }
                    
                    if (newCategoryEntity != null) {
                        // Update merchant's category mapping in SQLite
                        val normalizedMerchant = transaction.normalizedMerchant
                        val merchantEntity = repository.getMerchantByNormalizedName(normalizedMerchant)
                        
                        if (merchantEntity != null) {
                            val updatedMerchant = merchantEntity.copy(categoryId = newCategoryEntity.id)
                            repository.updateMerchant(updatedMerchant)
                        } else {
                            // Create merchant if it doesn't exist
                            repository.findOrCreateMerchant(normalizedMerchant, transaction.rawMerchant, newCategoryEntity.id)
                        }
                        
                        // Update legacy CategoryManager for backward compatibility
                        categoryManager.updateCategory(merchant, category)
                        
                        logger.debug("handleCategorizeAction", "Transaction $transactionId categorized as $category in SQLite")
                        
                        // Send broadcast to notify UI components to refresh
                        val updateIntent = Intent("com.expensemanager.CATEGORY_UPDATED")
                        updateIntent.putExtra("merchant", merchant)
                        updateIntent.putExtra("category", category)
                        updateIntent.putExtra("amount", amount)
                        context.sendBroadcast(updateIntent)
                        
                        // Show confirmation notification
                        notificationManager.showCategoryUpdateConfirmation(amount, merchant, category)
                        
                        // Dismiss the original notification
                        notificationManager.dismissNotification(transactionId)
                        
                        // Show toast on main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, " $merchant categorized as $category", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        logger.error("handleCategorizeAction", "Failed to create or find category: $category")
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Failed to create category: $category", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    logger.warn("handleCategorizeAction", "Transaction $transactionId not found in SQLite database")
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Transaction not found in database", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                logger.error("handleCategorizeAction", "Error categorizing transaction via notification", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Failed to categorize transaction", Toast.LENGTH_SHORT).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun handleRenameMerchantAction(
        context: Context,
        intent: Intent,
        transactionId: String,
        merchant: String,
        amount: Double,
        notificationManager: TransactionNotificationManager,
        pendingResult: PendingResult
    ) {
        // Get text from RemoteInput (inline reply)
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val newMerchantName = remoteInput?.getCharSequence(TransactionNotificationManager.KEY_TEXT_REPLY)?.toString()

        if (newMerchantName.isNullOrBlank()) {
            logger.warn("handleRenameMerchantAction", "No merchant name provided in inline reply")
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Please enter a merchant name", Toast.LENGTH_SHORT).show()
            }
            pendingResult.finish()
            return
        }

        logger.debug("handleRenameMerchantAction", "Renaming '$merchant' to '$newMerchantName'")

        // Process rename in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ExpenseRepository.getInstance(context)

                // Get transaction to find merchant and category
                val transaction = repository.getTransactionBySmsId(transactionId)

                if (transaction != null) {
                    // Get merchant and its category
                    val merchantEntity = repository.getMerchantByNormalizedName(transaction.normalizedMerchant)
                    val categoryName = if (merchantEntity != null) {
                        repository.getCategoryById(merchantEntity.categoryId)?.name ?: "Uncategorized"
                    } else {
                        "Uncategorized"
                    }

                    logger.debug("handleRenameMerchantAction", "Using category: $categoryName")

                    // Call existing business logic
                    val success = repository.updateMerchantAliasInDatabase(
                        originalMerchantNames = listOf(merchant),
                        newDisplayName = newMerchantName,
                        newCategoryName = categoryName
                    )

                    if (success) {
                        logger.info("handleRenameMerchantAction", "Successfully renamed merchant to '$newMerchantName'")

                        // Broadcast update to refresh UI
                        context.sendBroadcast(Intent("com.smartexpenseai.app.DATA_CHANGED"))

                        // Show confirmation notification
                        notificationManager.showCategoryUpdateConfirmation(amount, newMerchantName, categoryName)

                        // Dismiss the original notification
                        notificationManager.dismissNotification(transactionId)

                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Renamed to $newMerchantName", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        logger.error("handleRenameMerchantAction", "Failed to rename merchant", null)
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Failed to rename merchant", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    logger.warn("handleRenameMerchantAction", "Transaction $transactionId not found")
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Transaction not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                logger.error("handleRenameMerchantAction", "Error renaming merchant", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                pendingResult.finish()
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
        val intent = Intent(context, com.smartexpenseai.app.MainActivity::class.java).apply {
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
        notificationManager: TransactionNotificationManager,
        pendingResult: PendingResult
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ExpenseRepository.getInstance(context)
                
                // Check if transaction exists in SQLite database
                val transaction = repository.getTransactionBySmsId(transactionId)
                
                if (transaction != null) {
                    logger.debug("handleMarkProcessedAction", "Transaction $transactionId exists in database - dismissing notification")
                    
                    // Simply dismiss notification since SQLite transactions don't have "processed" field
                    // The transaction already exists in the database which means it's been processed
                    notificationManager.dismissNotification(transactionId)
                    
                    logger.debug("handleMarkProcessedAction", "Transaction $transactionId notification dismissed")
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Transaction acknowledged", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    logger.warn("handleMarkProcessedAction", "Transaction $transactionId not found in SQLite database")
                    
                    // Dismiss notification anyway since transaction might not exist
                    notificationManager.dismissNotification(transactionId)
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Transaction not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                logger.error("handleMarkProcessedAction", "Error handling mark processed action", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Failed to process action", Toast.LENGTH_SHORT).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun getCategoryEmoji(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "food & dining", "food", "dining" -> "🍽️"
            "transportation", "transport" -> "🚗"
            "groceries", "grocery" -> "🛒"
            "healthcare", "health" -> "🏥"
            "entertainment" -> "🎬"
            "shopping" -> "🛍️"
            "utilities" -> "⚡"
            "money", "finance" -> "💰"
            "education" -> "📚"
            "travel" -> "✈️"
            "bills" -> "💳"
            "insurance" -> "🛡️"
            else -> "📂"
        }
    }
    
    private fun getRandomCategoryColor(): String {
        val colors = listOf(
            "#ff5722", "#3f51b5", "#4caf50", "#e91e63",
            "#ff9800", "#9c27b0", "#607d8b", "#795548",
            "#2196f3", "#8bc34a", "#ffc107", "#673ab7"
        )
        return colors.random()
    }
}
