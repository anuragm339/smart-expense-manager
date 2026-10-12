package com.smartexpenseai.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.notifications.TransactionNotificationManager
import com.smartexpenseai.app.parsing.engine.ConfidenceCalculator
import com.smartexpenseai.app.parsing.engine.RuleLoader
import com.smartexpenseai.app.parsing.engine.UnifiedSMSParser
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SMSReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SMSReceiver"
    }

    private val logger = StructuredLogger(
        featureTag = "SMSReceiver",
        className = "SMSReceiver"
    )
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION && context != null) {
            val appSettings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            if (!appSettings.getBoolean("realtime_processing", true)) {
                logger.debug("onReceive", "Real-time SMS processing is disabled")
                return
            }

            logger.info("onReceive", "📱 SMS received - starting processing")

            val pendingResult = goAsync()
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) {
                pendingResult.finish()
                return
            }

            // A multipart SMS is delivered as multiple PDUs in one intent. Parse the
            // reconstructed body once and finish the PendingResult exactly once.
            val messageBody = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
            val sender = messages.firstNotNullOfOrNull { it.originatingAddress }
            val timestamp = messages.minOf { it.timestampMillis }
            processBankSMS(context, messageBody, sender, timestamp, pendingResult)
        }
    }

    private fun processBankSMS(
        context: Context,
        messageBody: String?,
        sender: String?,
        timestamp: Long,
        pendingResult: PendingResult
    ) {
        if (messageBody == null || sender == null) {
            pendingResult.finish()
            return
        }

        // Log SMS processing using Timber
        timber.log.Timber.tag("SMS").d("Processing SMS from $sender")

        // Use UnifiedSMSParser directly for real-time SMS parsing
        val ruleLoader = RuleLoader(context)
        val confidenceCalculator = ConfidenceCalculator()
        val unifiedSMSParser = UnifiedSMSParser(ruleLoader, confidenceCalculator)

        // CRITICAL FIX: Use runBlocking to ensure async work completes before pendingResult.finish()
        // This prevents Android from killing the process before database operations complete
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = unifiedSMSParser.parseSMS(sender, messageBody, timestamp)

                if (result is UnifiedSMSParser.ParseResult.Success) {
                    val transaction = result.transaction
                    logger.debug("processBankSMS","PARSED_FROM_SMS: ${transaction.normalizedMerchant} - ₹${transaction.amount} on Date ${transaction.createdAt}")

                    // Save to SQLite database via ExpenseRepository
                    try {
                        val repository = ExpenseRepository.getInstance(context)

                        // Transaction is already in the correct format (TransactionEntity)
                        val transactionEntity = transaction

                        // FIXED: Enhanced duplicate prevention with both SMS ID and transaction similarity checks
                        val existingTransaction =
                            repository.getTransactionBySmsId(transactionEntity.smsId)

                        // Check for duplicate by SMS ID only (reference number is already part of SMS ID)
                        if (existingTransaction != null) {
                            logger.warn("processBankSMS","Duplicate transaction detected (SMS ID already exists), skipping: ${transactionEntity.smsId}")
                        } else if (repository.isMerchantDeleted(transactionEntity.normalizedMerchant)) {
                            // Merchant previously deleted by the user: store the transaction
                            // as inactive silently - no notification, no broadcast
                            repository.insertTransaction(transactionEntity.copy(isActive = false))
                            logger.debug("processBankSMS", "[AUTO-HIDDEN] Transaction from deleted merchant '${transactionEntity.normalizedMerchant}' stored inactive")
                        } else {
                            val insertedId = repository.insertTransaction(transactionEntity)

                            if (insertedId > 0) {
                                logger.debug("processBankSMS","[SUCCESS] New transaction saved: ${transaction.normalizedMerchant} - ₹${transaction.amount}")

                                val autoCategorizationEnabled = context
                                    .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                                    .getBoolean("auto_categorization", true)
                                if (autoCategorizationEnabled) {
                                    val categorized = repository.autoCategorizeTransaction(insertedId)
                                    if (categorized) {
                                        logger.debug("processBankSMS","[AUTO-CATEGORIZE] Transaction auto-categorized successfully")
                                    } else {
                                        logger.warn("processBankSMS","[AUTO-CATEGORIZE] Failed to auto-categorize transaction")
                                    }
                                }

                                // Show notification
                                val notificationManager = TransactionNotificationManager(context)
                                notificationManager.showNewTransactionNotification(
                                    com.smartexpenseai.app.data.models.Transaction(
                                        id = transactionEntity.smsId,
                                        amount = transactionEntity.amount,
                                        merchant = transactionEntity.rawMerchant,
                                        bankName = transactionEntity.bankName,
                                        category = "Pending", // Will be categorized by merchant mapping
                                        date = transactionEntity.transactionDate.time,
                                        rawSMS = transactionEntity.rawSmsBody,
                                        confidence = transactionEntity.confidenceScore
                                    )
                                )

                                // Send broadcast to notify other parts of the app about new transaction
                                val updateIntent =
                                    Intent("com.expensemanager.NEW_TRANSACTION_ADDED").apply {
                                        setPackage(context.packageName)
                                    }
                                updateIntent.putExtra("transaction_id", transactionEntity.smsId)
                                updateIntent.putExtra("amount", transactionEntity.amount)
                                updateIntent.putExtra("merchant", transactionEntity.rawMerchant)
                                context.sendBroadcast(updateIntent)

                                logger.debug("processBankSMS","[BROADCAST] Broadcast sent for new transaction: ${transactionEntity.smsId}")
                            } else {
                                logger.warn("processBankSMS","Failed to insert transaction into database",null)
                            }
                        }

                    } catch (e: Exception) {
                        logger.error("processBankSMS", "Error saving transaction to SQLite database",e)
                    }
                } else {
                    logger.warn("processBankSMS","Failed to parse transaction data from SMS: $sender")
                }
            } catch (e: Exception) {
                logger.error("processBankSMS","Error parsing SMS",e)
            } finally {
                // CRITICAL: Finish pendingResult to tell Android we're done with async work
                pendingResult.finish()
            }
        }
    }
}
