package com.smartexpenseai.app.services

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.smartexpenseai.app.models.HistoricalSMS
import com.smartexpenseai.app.utils.logging.StructuredLogger
import com.smartexpenseai.app.models.ParsedTransaction
import com.smartexpenseai.app.models.RejectedSMS
import com.smartexpenseai.app.parsing.engine.UnifiedSMSParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class SMSParsingService @Inject constructor(
    private val context: Context,
    private val unifiedParser: UnifiedSMSParser
) {
    private val logger = StructuredLogger(
        featureTag = "SMS",
        className = "SMSParsingService"
    )
    companion object {
        private const val MONTHS_TO_SCAN = 6 // Scan last 6 months
        private const val MAX_SMS_TO_PROCESS = 5000 // Limit SMS processing to prevent ANR
    }
    
    /**
     * Main method to scan historical SMS and extract valid transactions
     * This replaces both SMSHistoryReader.scanHistoricalSMS() and ExpenseRepository.readSMSTransactionsDirectly()
     */
    suspend fun scanHistoricalSMS(
        progressCallback: ((current: Int, total: Int, status: String) -> Unit)? = null
    ): List<ParsedTransaction> = withContext(Dispatchers.IO) {
        val transactions = mutableListOf<ParsedTransaction>()
        val rejectedSMSList = mutableListOf<RejectedSMS>()
        var acceptedCount = 0
        var rejectedCount = 0
        var processedCount = 0
        
        try {
            logger.debug(
                where = "scanHistoricalSMS",
                what = "[UNIFIED] Starting SMS scan using unified parsing service..."
            )
            progressCallback?.invoke(0, 100, "Reading SMS history...")
            
            val historicalSMS = readSMSHistory()
            logger.debug(
                where = "scanHistoricalSMS",
                what = "[UNIFIED] Found ${historicalSMS.size} historical SMS messages (limited to $MAX_SMS_TO_PROCESS)"
            )
            
            val totalSMS = historicalSMS.size
            progressCallback?.invoke(0, totalSMS, "Found $totalSMS messages, analyzing...")
            
            for (sms in historicalSMS) {
                processedCount++

                // Update progress callback every 100 messages for performance
                if (processedCount % 100 == 0) {
                    val status = "Processed $processedCount/$totalSMS messages • Found $acceptedCount transactions"
                    progressCallback?.invoke(processedCount, totalSMS, status)
                }

                // Use new unified parser
                val parseResult = unifiedParser.parseSMS(
                    sender = sms.address,
                    body = sms.body,
                    timestamp = sms.date.time
                )

                when (parseResult) {
                    is UnifiedSMSParser.ParseResult.Success -> {
                        // CRITICAL: Double-check reference number exists (additional safety)
                        if (parseResult.transaction.referenceNumber.isNullOrBlank()) {
                            logger.warn(
                                where = "scanHistoricalSMS",
                                what = "[REJECTED] Transaction has no reference number (conf=${String.format("%.2f", parseResult.confidence.overall)}) - likely promotional: ${sms.body.take(50)}..."
                            )
                            rejectedSMSList.add(RejectedSMS(
                                sender = sms.address,
                                body = sms.body.replace(Regex("\\s+"), " ").trim(),
                                date = sms.date,
                                reason = "No reference number (required for transaction SMS)"
                            ))
                            rejectedCount++
                            continue
                        }

                        // Convert TransactionEntity to ParsedTransaction
                        val transaction = ParsedTransaction(
                            id = "hist_${sms.id}",
                            amount = parseResult.transaction.amount,
                            merchant = parseResult.transaction.rawMerchant,
                            bankName = parseResult.transaction.bankName,
                            date = parseResult.transaction.transactionDate,
                            rawSMS = parseResult.transaction.rawSmsBody,
                            confidence = parseResult.confidence.overall,
                            isDebit = parseResult.transaction.isDebit,
                            referenceNumber = parseResult.transaction.referenceNumber
                        )

                        // Only accept if confidence is reasonable
                        // Threshold: 0.65 minimum confidence score
                        if (parseResult.confidence.overall >= 0.65f) {
                            transactions.add(transaction)
                            acceptedCount++
                        } else {
                            rejectedSMSList.add(RejectedSMS(
                                sender = sms.address,
                                body = sms.body.replace(Regex("\\s+"), " ").trim(),
                                date = sms.date,
                                reason = "Low confidence (${String.format("%.2f", parseResult.confidence.overall)})"
                            ))
                            rejectedCount++
                        }
                    }
                    is UnifiedSMSParser.ParseResult.Failed -> {
                        // Failed parsing - could be non-bank SMS or parsing error
                        rejectedSMSList.add(RejectedSMS(
                            sender = sms.address,
                            body = sms.body.replace(Regex("\\s+"), " ").trim(),
                            date = sms.date,
                            reason = "Parse failed: ${parseResult.reason}"
                        ))
                        // Otherwise, skip silently (not a bank SMS)
                    }
                }

                // Yield occasionally to prevent ANR
                if (processedCount % 100 == 0) {
                    kotlinx.coroutines.yield()
                }
            }
            
            // Save rejected SMS to CSV file
            if (rejectedSMSList.isNotEmpty()) {
                saveRejectedSMSToCSV(rejectedSMSList)
            }
            
            // Final progress update
            progressCallback?.invoke(totalSMS, totalSMS, "Scan complete! Found $acceptedCount transactions")
            
        } catch (e: Exception) {
            logger.error(
                where = "scanHistoricalSMS",
                what = "[UNIFIED] Error scanning historical SMS",
                throwable = e
            )
            progressCallback?.invoke(0, 100, "Error: ${e.message}")
        }
        
        return@withContext transactions
    }
    
    /**
     * Read SMS history from device content provider
     */
    private fun readSMSHistory(): List<HistoricalSMS> {
        val smsList = mutableListOf<HistoricalSMS>()
        
        // Calculate date range (last 6 months)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -MONTHS_TO_SCAN)
        val startDate = calendar.timeInMillis
        
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        
        val selection = "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.TYPE} = ?"
        val selectionArgs = arrayOf(
            startDate.toString(),
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString()
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $MAX_SMS_TO_PROCESS" // Newest first, limited
        
        var cursor: Cursor? = null
        try {
            logger.debug(
                where = "readSMSHistory",
                what = "[UNIFIED] Querying SMS from last $MONTHS_TO_SCAN months (max $MAX_SMS_TO_PROCESS messages)"
            )
            cursor = context.contentResolver.query(
                uri, projection, selection, selectionArgs, sortOrder
            )
            
            cursor?.let {
                val idIndex = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                
                while (it.moveToNext()) {
                    val sms = HistoricalSMS(
                        id = it.getString(idIndex),
                        address = it.getString(addressIndex) ?: "",
                        body = it.getString(bodyIndex) ?: "",
                        date = Date(it.getLong(dateIndex)),
                        type = it.getInt(typeIndex)
                    )
                    smsList.add(sms)
                }
            }
        } catch (e: Exception) {
            logger.error(
                where = "readSMSHistory",
                what = "[UNIFIED] Error reading SMS history",
                throwable = e
            )
        } finally {
            cursor?.close()
        }
        
        return smsList
    }

    private fun calculateConfidence(messageBody: String): Float {
        var confidence = 0.5f
        
        // Higher confidence for specific patterns
        if (messageBody.contains(Regex("""rs\.?\s*[\d,]+""", RegexOption.IGNORE_CASE))) {
            confidence += 0.2f
        }
        
        if (messageBody.contains("debited", ignoreCase = true) || 
            messageBody.contains("credited", ignoreCase = true)) {
            confidence += 0.2f
        }
        
        if (messageBody.contains("balance", ignoreCase = true)) {
            confidence += 0.1f
        }
        
        return minOf(confidence, 1.0f)
    }

    private fun saveRejectedSMSToCSV(rejectedSMSList: List<RejectedSMS>) {
        try {
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir == null) {
                logger.warn(
                    where = "saveRejectedSMSToCSV",
                    what = "External storage not available for CSV export"
                )
                return
            }
            
            val csvFile = File(externalDir, "rejected_sms_${System.currentTimeMillis()}.csv")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            
            csvFile.bufferedWriter().use { writer ->
                // Write CSV header
                writer.write("Date,Sender,Body,Rejection_Reason\n")
                
                // Write rejected SMS data
                for (rejectedSMS in rejectedSMSList) {
                    val dateStr = dateFormat.format(rejectedSMS.date)
                    val sender = rejectedSMS.sender.replace(",", ";") // Escape commas
                    val body = rejectedSMS.body.replace(",", ";").replace("\n", " ").replace("\"", "'") // Escape special characters
                    val reason = rejectedSMS.reason.replace(",", ";")
                    
                    writer.write("\"$dateStr\",\"$sender\",\"$body\",\"$reason\"\n")
                }
            }
        } catch (e: Exception) {
            logger.error(
                where = "saveRejectedSMSToCSV",
                what = "[CSV] Error saving rejected SMS to CSV",
                throwable = e
            )
        }
    }
}
