package com.smartexpenseai.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
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
            // CRITICAL FIX: Use goAsync() to prevent process from being killed before async work completes
            val pendingResult = goAsync()

            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as? Array<*>
                val format = bundle.getString("format")

                pdus?.forEach { pdu ->
                    val smsMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(pdu as ByteArray, format)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsMessage.createFromPdu(pdu as ByteArray)
                    }
                    val messageBody = smsMessage.messageBody
                    val sender = smsMessage.originatingAddress
                    val timestamp = smsMessage.timestampMillis

                    processBankSMS(context, messageBody, sender, timestamp, pendingResult)
                }
            } else {
                pendingResult.finish()
            }
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

        // FIXED: Generate consistent SMS ID to prevent duplicates
        val smsId = com.smartexpenseai.app.data.entities.TransactionEntity.generateSmsId(
            address = sender,
            body = messageBody,
            timestamp = timestamp
        )

        // Use UnifiedSMSParser directly for real-time SMS parsing
        val ruleLoader = RuleLoader(context)
        val confidenceCalculator = ConfidenceCalculator()
        val unifiedSMSParser = UnifiedSMSParser(ruleLoader, confidenceCalculator)

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
                        if (existingTransaction == null) {
                            val insertedId = repository.insertTransaction(transactionEntity)

                            if (insertedId > 0) {
                                logger.debug("processBankSMS","[SUCCESS] New transaction saved: ${transaction.normalizedMerchant} - ₹${transaction.amount}")

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
                                    Intent("com.expensemanager.NEW_TRANSACTION_ADDED")
                                updateIntent.putExtra("transaction_id", transactionEntity.smsId)
                                updateIntent.putExtra("amount", transactionEntity.amount)
                                updateIntent.putExtra("merchant", transactionEntity.rawMerchant)
                                context.sendBroadcast(updateIntent)

                                logger.debug("processBankSMS","[BROADCAST] Broadcast sent for new transaction: ${transactionEntity.smsId}")
                            } else {
                                logger.warn("processBankSMS","Failed to insert transaction into database",null)
                            }
                        } else {
                            logger.warn("processBankSMS","Duplicate transaction detected (SMS ID already exists), skipping: ${transactionEntity.smsId}")
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
