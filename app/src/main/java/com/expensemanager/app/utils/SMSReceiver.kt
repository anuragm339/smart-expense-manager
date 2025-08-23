package com.expensemanager.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.expensemanager.app.data.repository.ExpenseRepository
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
    
    private fun processBankSMS(context: Context, messageBody: String?, sender: String?, timestamp: Long) {
        if (messageBody == null || sender == null) return
        
        Log.d(TAG, "Processing SMS from $sender: ${messageBody.take(100)}...")
        
        // Create a HistoricalSMS-like object for the parser
        val smsData = HistoricalSMS(
            id = timestamp.toString(),
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
                Log.d(TAG, "Parsed new transaction: ${parsedTransaction.merchant} - ₹${parsedTransaction.amount}")
                
                // Save to SQLite database via ExpenseRepository  
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = ExpenseRepository.getInstance(context)
                        
                        // Convert to TransactionEntity and save to SQLite
                        val transactionEntity = repository.convertToTransactionEntity(parsedTransaction)
                        
                        // Check if transaction already exists to avoid duplicates
                        val existingTransaction = repository.getTransactionBySmsId(transactionEntity.smsId)
                        if (existingTransaction == null) {
                            val insertedId = repository.insertTransaction(transactionEntity)
                            
                            if (insertedId > 0) {
                                Log.d(TAG, "✅ New transaction saved to SQLite: ${parsedTransaction.merchant} - ₹${parsedTransaction.amount}")
                                
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
                                val updateIntent = Intent("com.expensemanager.NEW_TRANSACTION_ADDED")
                                updateIntent.putExtra("transaction_id", transactionEntity.smsId)
                                updateIntent.putExtra("amount", transactionEntity.amount)
                                updateIntent.putExtra("merchant", transactionEntity.rawMerchant)
                                context.sendBroadcast(updateIntent)
                                
                                Log.d(TAG, "📡 Broadcast sent for new transaction: ${transactionEntity.smsId}")
                            } else {
                                Log.w(TAG, "Failed to insert transaction into database")
                            }
                        } else {
                            Log.d(TAG, "Transaction already exists, skipping: ${transactionEntity.smsId}")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving transaction to SQLite database", e)
                    }
                }
            } else {
                Log.d(TAG, "Failed to parse transaction from SMS")
            }
        } else {
            Log.d(TAG, "SMS is not a valid bank transaction: $sender")
        }
    }
}