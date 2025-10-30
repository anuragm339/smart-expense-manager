package com.expensemanager.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.notifications.TransactionNotificationManager
import com.expensemanager.app.parsing.engine.ConfidenceCalculator
import com.expensemanager.app.parsing.engine.RuleLoader
import com.expensemanager.app.parsing.engine.UnifiedSMSParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class SMSReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SMSReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION && context != null) {
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

                    processBankSMS(context, messageBody, sender, timestamp)
                }
            }
        }
    }

    private fun processBankSMS(
        context: Context,
        messageBody: String?,
        sender: String?,
        timestamp: Long
    ) {
        if (messageBody == null || sender == null) return

        // Log SMS processing using Timber
        timber.log.Timber.tag("SMS").d("Processing SMS from $sender")

        // FIXED: Generate consistent SMS ID to prevent duplicates
        val smsId = com.expensemanager.app.data.entities.TransactionEntity.generateSmsId(
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
                    timber.log.Timber.tag(TAG).d("PARSED_FROM_SMS: ${transaction.normalizedMerchant} - ₹${transaction.amount}")

                    // Save to SQLite database via ExpenseRepository
                    try {
                        val repository = ExpenseRepository.getInstance(context)

                        // Transaction is already in the correct format (TransactionEntity)
                        val transactionEntity = transaction

                        // FIXED: Enhanced duplicate prevention with both SMS ID and transaction similarity checks
                        val existingTransaction =
                            repository.getTransactionBySmsId(transactionEntity.smsId)

                        // Additional check for transaction similarity (in case SMS ID generation changed)
                        val similarTransaction = repository.findSimilarTransaction(transactionEntity)

                        if (existingTransaction == null && similarTransaction == null) {
                            val insertedId = repository.insertTransaction(transactionEntity)

                            if (insertedId > 0) {
                                timber.log.Timber.tag(TAG).i("[SUCCESS] New transaction saved: ${transaction.normalizedMerchant} - ₹${transaction.amount}")

                                // Show notification
                                val notificationManager = TransactionNotificationManager(context)
                                notificationManager.showNewTransactionNotification(
                                    com.expensemanager.app.data.models.Transaction(
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

                                timber.log.Timber.tag(TAG).d("[BROADCAST] Broadcast sent for new transaction: ${transactionEntity.smsId}")
                            } else {
                                timber.log.Timber.tag(TAG).w("Failed to insert transaction into database")
                            }
                        } else {
                            val reason =
                                if (existingTransaction != null) "SMS ID already exists" else "Similar transaction found"
                            timber.log.Timber.tag(TAG).d("Duplicate transaction detected ($reason), skipping: ${transactionEntity.smsId}")
                        }

                    } catch (e: Exception) {
                        timber.log.Timber.tag(TAG).e(e, "Error saving transaction to SQLite database")
                    }
                } else {
                    timber.log.Timber.tag(TAG).w("Failed to parse transaction data from SMS: $sender")
                }
            } catch (e: Exception) {
                timber.log.Timber.tag(TAG).e(e, "Error parsing SMS")
            }
        }
    }
}
