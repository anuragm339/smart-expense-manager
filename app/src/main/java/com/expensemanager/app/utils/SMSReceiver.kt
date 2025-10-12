package com.expensemanager.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.models.HistoricalSMS
import com.expensemanager.app.notifications.TransactionNotificationManager
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

        // Use AppLogger instance - will be injected once this class is converted to injectable
        // For now, create instance directly (TODO: Convert to injectable class)
        val logger = AppLogger(context)
        logger.logSMSProcessing(
            sender = sender,
            message = messageBody,
            success = true,
            details = "Processing incoming SMS for transaction parsing"
        )

        // FIXED: Generate consistent SMS ID to prevent duplicates
        val smsId = com.expensemanager.app.data.entities.TransactionEntity.generateSmsId(
            address = sender,
            body = messageBody,
            timestamp = timestamp
        )

        // Create a HistoricalSMS-like object for the parser
        val smsData = HistoricalSMS(
            id = smsId,
            address = sender,
            body = messageBody,
            date = Date(timestamp),
            type = 1 // MESSAGE_TYPE_INBOX
        )

        // Use the existing parsing logic from SMSHistoryReader
        val smsHistoryReader = SMSHistoryReader(context)
        if (smsHistoryReader.isBankTransactionSMS(smsData)) {
            val parsedTransaction = smsHistoryReader.parseTransactionFromSMS(smsData)

            if (parsedTransaction != null) {
                logger.logTransaction(
                    action = "PARSED_FROM_SMS",
                    transactionId = parsedTransaction.id,
                    amount = parsedTransaction.amount,
                    merchant = parsedTransaction.merchant
                )

                // Save to SQLite database via ExpenseRepository  
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = ExpenseRepository.getInstance(context)

                        // Convert to TransactionEntity and save to SQLite
                        val transactionEntity =
                            repository.convertToTransactionEntity(parsedTransaction)

                        // FIXED: Enhanced duplicate prevention with both SMS ID and transaction similarity checks
                        val existingTransaction =
                            repository.getTransactionBySmsId(transactionEntity.smsId)

                        // Additional check for transaction similarity (in case SMS ID generation changed)
                        val similarTransaction = repository.findSimilarTransaction(transactionEntity)

                        if (existingTransaction == null && similarTransaction == null) {
                            val insertedId = repository.insertTransaction(transactionEntity)

                            if (insertedId > 0) {
                                logger.logDatabaseOperation(
                                    operation = "INSERT_TRANSACTION",
                                    table = "transactions",
                                    success = true,
                                    recordsAffected = 1
                                )
                                logger.info(
                                    TAG,
                                    "[SUCCESS] New transaction saved: ${parsedTransaction.merchant} - ₹${parsedTransaction.amount}"
                                )

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

                                logger.debug(
                                    TAG,
                                    "[BROADCAST] Broadcast sent for new transaction: ${transactionEntity.smsId}"
                                )
                            } else {
                                logger.warn(TAG, "Failed to insert transaction into database")
                            }
                        } else {
                            val reason =
                                if (existingTransaction != null) "SMS ID already exists" else "Similar transaction found"
                            logger.debug(
                                TAG,
                                "Duplicate transaction detected ($reason), skipping: ${transactionEntity.smsId}"
                            )
                        }

                    } catch (e: Exception) {
                        logger.error(TAG, "Error saving transaction to SQLite database", e)
                    }
                }
            } else {
                logger.logSMSProcessing(
                    sender = sender,
                    message = messageBody,
                    success = false,
                    details = "Failed to parse transaction data from SMS content"
                )
            }
        } else {
            logger.debug(TAG, "SMS is not a valid bank transaction: $sender")
        }
    }
}
