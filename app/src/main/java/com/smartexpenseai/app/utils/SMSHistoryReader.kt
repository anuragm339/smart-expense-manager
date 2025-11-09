package com.smartexpenseai.app.utils


import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.smartexpenseai.app.models.HistoricalSMS
import com.smartexpenseai.app.models.ParsedTransaction
import com.smartexpenseai.app.parsing.engine.UnifiedSMSParser
import com.smartexpenseai.app.utils.logging.StructuredLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Scans historical SMS messages and delegates parsing to UnifiedSMSParser
 * This class is responsible ONLY for:
 * 1. Reading SMS from the system SMS provider
 * 2. Delegating parsing to UnifiedSMSParser (which uses bank_rules.json)
 * 3. Converting parsed results to legacy ParsedTransaction format
 *
 * All parsing logic, patterns, and rules come from bank_rules.json via UnifiedSMSParser
 */
@Singleton
class SMSHistoryReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val unifiedSMSParser: UnifiedSMSParser
) {
    private val logger = StructuredLogger("SMSHistoryReader", "SMSHistoryReader")

    // Progress callback can be set after construction (not injectable)
    var progressCallback: ((current: Int, total: Int, status: String) -> Unit)? = null

    companion object {
        private const val TAG = "SMSHistoryReader"
        private const val MONTHS_TO_SCAN = 6 // Scan last 6 months
        private const val MAX_SMS_TO_PROCESS = 5000 // Limit SMS processing to prevent ANR
    }

    suspend fun scanHistoricalSMS(): List<ParsedTransaction> = withContext(Dispatchers.IO) {
        val transactions = mutableListOf<ParsedTransaction>()
        var acceptedCount = 0
        var rejectedCount = 0
        var processedCount = 0

        try {
            logger.debug("scanHistoricalSMS","Starting SMS scan...")
            progressCallback?.invoke(0, 100, "Reading SMS history...")

            val historicalSMS = readSMSHistory()
            logger.debug("scanHistoricalSMS","Found ${historicalSMS.size} historical SMS messages (limited to $MAX_SMS_TO_PROCESS)")

            val totalSMS = historicalSMS.size
            progressCallback?.invoke(0, totalSMS, "Found $totalSMS messages, analyzing...")

            for (sms in historicalSMS) {
                processedCount++

                // Update progress callback every 100 messages for performance
                if (processedCount % 100 == 0) {
                    val status = "Processed $processedCount/$totalSMS messages • Found $acceptedCount transactions"
                    progressCallback?.invoke(processedCount, totalSMS, status)
                }

                // Delegate to UnifiedSMSParser for validation and parsing
                val transaction = parseTransactionFromSMS(sms)
                if (transaction != null) {
                    transactions.add(transaction)
                    acceptedCount++
                } else {
                    rejectedCount++
                }

                // Yield occasionally to prevent ANR
                if (processedCount % 100 == 0) {
                    kotlinx.coroutines.yield()
                }
            }

            // Final progress update
            progressCallback?.invoke(totalSMS, totalSMS, "Scan complete! Found $acceptedCount transactions")

            logger.info("scanHistoricalSMS","SMS Processing Summary:")
            logger.info("scanHistoricalSMS","Total SMS scanned: $totalSMS")
            logger.info("scanHistoricalSMS","Accepted transactions: $acceptedCount")
            logger.info("scanHistoricalSMS","Rejected SMS: $rejectedCount")
            logger.info("scanHistoricalSMS","Final parsed transactions: ${transactions.size}")

        } catch (e: Exception) {
            logger.error("scanHistoricalSMS","Error scanning historical SMS",e)
            progressCallback?.invoke(0, 100, "Error: ${e.message}")
        }

        return@withContext transactions
    }

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
            logger.debug("scanHistoricalSMS","Querying SMS from last $MONTHS_TO_SCAN months (max $MAX_SMS_TO_PROCESS messages)")
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
            logger.error("scanHistoricalSMS","Error reading SMS history",e)
        } finally {
            cursor?.close()
        }

        return smsList
    }


    /**
     * Parse transaction from SMS by delegating to UnifiedSMSParser
     * All parsing logic (amount, merchant, bank, reference) comes from bank_rules.json
     */

    suspend fun parseTransactionFromSMS(sms: HistoricalSMS): ParsedTransaction? {
        return try {
            val result = unifiedSMSParser.parseSMS(sms.address, sms.body, sms.date.time)

            when (result) {
                is UnifiedSMSParser.ParseResult.Success -> {
                    // Convert UnifiedSMSParser result to legacy ParsedTransaction format
                    logger.debug("parseTransactionFromSMS", "TransactionEntity ref: ${result.transaction.referenceNumber}")

                    val parsed = ParsedTransaction(
                        id = "hist_${sms.id}",
                        amount = result.transaction.amount,
                        merchant = result.transaction.normalizedMerchant,
                        bankName = result.transaction.bankName,
                        date = sms.date,
                        rawSMS = sms.body,
                        confidence = result.transaction.confidenceScore,
                        referenceNumber = result.transaction.referenceNumber
                    )

                    logger.debug("parseTransactionFromSMS", "ParsedTransaction ref: ${parsed.referenceNumber}")
                    parsed
                }
                is UnifiedSMSParser.ParseResult.Failed -> {
                    logger.debug("parseTransactionFromSMS", "Failed to parse: ${result.reason}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("parseTransactionFromSMS","Error parsing transaction: ${sms.body}",e)
            null
        }
    }
}
