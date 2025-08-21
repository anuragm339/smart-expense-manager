package com.expensemanager.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.expensemanager.app.data.models.Transaction
import com.expensemanager.app.data.storage.TransactionStorage
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
                
                // Convert to Transaction and save
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val transactionStorage = TransactionStorage(context)
                        val transaction = Transaction.fromParsedTransaction(parsedTransaction)
                        
                        // Auto-categorize the transaction
                        val categoryManager = CategoryManager(context)
                        val autoCategory = categoryManager.categorizeTransaction(transaction.merchant)
                        val categorizedTransaction = transaction.copy(category = autoCategory)
                        
                        // Save to storage
                        transactionStorage.addTransaction(categorizedTransaction)
                        
                        // Show notification
                        val notificationManager = TransactionNotificationManager(context)
                        notificationManager.showNewTransactionNotification(categorizedTransaction)
                        
                        android.util.Log.d(TAG, "Transaction saved with category '$autoCategory' and notification sent for: ${transaction.id}")
                        
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Error saving transaction or sending notification", e)
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