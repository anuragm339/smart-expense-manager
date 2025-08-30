package com.expensemanager.app.services

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified SMS parsing service that consolidates the proven parsing logic from SMSHistoryReader
 * for consistent transaction extraction across Dashboard and Messages screens.
 * 
 * This service extracts the core SMS parsing functionality and makes it available
 * as a shared component to eliminate discrepancies between different screens.
 */
@Singleton 
class SMSParsingService @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SMSParsingService"
        private const val MONTHS_TO_SCAN = 6 // Scan last 6 months
        private const val MAX_SMS_TO_PROCESS = 5000 // Limit SMS processing to prevent ANR
        
        // Enhanced bank sender patterns (from proven SMSHistoryReader logic)
        private val BANK_SENDERS = listOf(
            // Major private banks
            "HDFCBK", "HDFC", "ICICI", "AXISBK", "AXIS", "KOTAK", "KOTAKB",
            
            // State Bank variants
            "SBIINB", "SBI", "SBIPSG", "SBYONO", "CBSSBI", "SBIUPI",
            
            // Public sector banks
            "CANBNK", "BOBCRD", "BOB", "PNBSMS", "PNB", "UNIONBK", "INDIANBK",
            "CENBK", "SYNBK", "VIJBK", "DENABNK", "ALLBK", "CORPBK",
            "UCOBK", "OBVBK", "ANDHRABK", "IOBCHN", "IDBIBN", 
            
            // Regional banks
            "FEDBNK", "KRVYSB", "LAKBNK", "NAINBK", "DHANBK",
            "CUBNK", "CATHBK", "TMBNET", "KERBK", "JKBNET",
            
            // International banks
            "HSBCIN", "HSBC", "SCBIND", "CITIINDIA", "CITI", "DEUTCH", "BARCLY",
            
            // Digital payment platforms
            "PAYTM", "PYTMPB", "PHONEPE", "GPAY", "AMAZONPAY", "MOBIKWIK",
            
            // Additional common bank codes
            "YESBNK", "YES", "RBLBNK", "RBL", "DCBBNK", "DCB", "INDBNK"
        )
        
        // Enhanced transaction debit/credit keywords
        private val DEBIT_KEYWORDS = listOf(
            "debited", "deducted", "spent", "withdrawn", "paid", "purchase", "charged",
            "sent", "transferred", "remitted", "upi", "imps", "neft", "rtgs",
            "atm", "pos", "online", "payment", "transaction", "txn"
        )
        
        private val CREDIT_KEYWORDS = listOf(
            "credited", "received", "deposited", "refund", "cashback",
            "salary", "bonus", "interest", "dividend", "transfer received",
            "amount received", "money received"
        )
        
        // Enhanced amount patterns for various bank SMS formats
        private val AMOUNT_PATTERNS = listOf(
            // Standard RS/INR patterns
            Regex("""(?:rs\.?|inr)\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([\d,]+(?:\.\d{2})?)\s*(?:rs\.?|inr)""", RegexOption.IGNORE_CASE),
            
            // Rupee symbol and dash patterns
            Regex("""([\d,]+(?:\.\d{2})?)\s*/-"""),
            Regex("""₹\s*([\d,]+(?:\.\d{2})?)"""),
            
            // Amount keyword patterns
            Regex("""amount[:\s]*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""amt[:\s]*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            
            // Bank-specific patterns
            Regex("""(?:debited|credited|sent|received)[^0-9]*?([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            
            // Simple number patterns (be more cautious with these)
            Regex("""\b([\d,]+(?:\.\d{2})?)\s*(?:has been|was|is)""", RegexOption.IGNORE_CASE)
        )
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
            Log.d(TAG, "[UNIFIED] Starting SMS scan using unified parsing service...")
            progressCallback?.invoke(0, 100, "Reading SMS history...")
            
            val historicalSMS = readSMSHistory()
            Log.d(TAG, "[UNIFIED] Found ${historicalSMS.size} historical SMS messages (limited to $MAX_SMS_TO_PROCESS)")
            
            val totalSMS = historicalSMS.size
            progressCallback?.invoke(0, totalSMS, "Found $totalSMS messages, analyzing...")
            
            for (sms in historicalSMS) {
                processedCount++
                
                // Update progress callback every 100 messages for performance
                if (processedCount % 100 == 0) {
                    val status = "Processed $processedCount/$totalSMS messages • Found $acceptedCount transactions"
                    progressCallback?.invoke(processedCount, totalSMS, status)
                }
                
                if (isBankTransaction(sms)) {
                    val transaction = parseTransaction(sms)
                    if (transaction != null) {
                        transactions.add(transaction)
                        acceptedCount++
                    } else {
                        // SMS passed bank validation but failed transaction parsing
                        rejectedSMSList.add(RejectedSMS(
                            sender = sms.address,
                            body = sms.body.replace(Regex("\\s+"), " ").trim(),
                            date = sms.date,
                            reason = "Failed transaction parsing"
                        ))
                        rejectedCount++
                    }
                } else {
                    // SMS failed bank validation
                    rejectedSMSList.add(RejectedSMS(
                        sender = sms.address,
                        body = sms.body.replace(Regex("\\s+"), " ").trim(),
                        date = sms.date,
                        reason = "Failed bank validation"
                    ))
                    rejectedCount++
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
            
            Log.d(TAG, "[UNIFIED] SMS Processing Summary:")
            Log.d(TAG, "Total SMS scanned: $totalSMS")
            Log.d(TAG, "Accepted transactions: $acceptedCount")
            Log.d(TAG, "Rejected SMS: $rejectedCount")
            Log.d(TAG, "Final parsed transactions: ${transactions.size}")
            Log.d(TAG, "[UNIFIED] Rejected SMS saved to CSV file for verification")
            
        } catch (e: Exception) {
            Log.e(TAG, "[UNIFIED] Error scanning historical SMS", e)
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
            Log.d(TAG, "[UNIFIED] Querying SMS from last $MONTHS_TO_SCAN months (max $MAX_SMS_TO_PROCESS messages)")
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
            Log.e(TAG, "[UNIFIED] Error reading SMS history", e)
        } finally {
            cursor?.close()
        }
        
        return smsList
    }
    
    /**
     * Check if an SMS is a valid bank transaction using proven validation logic
     */
    private fun isBankTransaction(sms: HistoricalSMS): Boolean {
        val senderUpper = sms.address.uppercase()
        val bodyLower = sms.body.lowercase()
        
        // Check 1: Reference number requirement - make it more flexible
        val hasRefNumber = bodyLower.contains("ref ") || 
                          bodyLower.contains("ref-") || 
                          bodyLower.contains("ref no") ||
                          bodyLower.contains("reference") ||
                          bodyLower.contains("txn") ||
                          bodyLower.contains("transaction") ||
                          bodyLower.contains("utr") ||
                          bodyLower.contains("rrn")
        
        if (!hasRefNumber) {
            return false
        }
        
        // Check 2: Bank sender validation - more flexible patterns
        val isFromKnownBank = BANK_SENDERS.any { bankCode -> senderUpper.contains(bankCode) }
        
        if (!isFromKnownBank) {
            return false
        }
        
        // Check 3: Transaction keywords - make more flexible
        val hasDebitKeywords = DEBIT_KEYWORDS.any { keyword -> bodyLower.contains(keyword) }
        val hasCreditKeywords = CREDIT_KEYWORDS.any { keyword -> bodyLower.contains(keyword) }
        val hasTransactionKeywords = hasDebitKeywords || hasCreditKeywords
        
        if (!hasTransactionKeywords) {
            return false
        }
        
        // Check 4: Exclude promotional/marketing SMS even from banks
        val isPromotional = bodyLower.contains("offer") ||
            bodyLower.contains("apply") ||
            bodyLower.contains("check emi") ||
            bodyLower.contains("upgrade") ||
            bodyLower.contains("loan on credit card") ||
            bodyLower.contains("pre-approved") ||
            bodyLower.contains("best deal") ||
            bodyLower.contains("rate drop") ||
            bodyLower.contains("instant cash") ||
            bodyLower.contains("get rs.") ||
            bodyLower.contains("avail now") ||
            bodyLower.contains("click") ||
            bodyLower.contains("http") ||
            bodyLower.contains("t&c") ||
            bodyLower.contains("terms")
        
        if (isPromotional) {
            return false
        }
        
        // Check 5: Exclude OTP/verification messages
        val isOTPMessage = bodyLower.contains("otp") ||
            bodyLower.contains("verification") ||
            bodyLower.contains("do not share") ||
            bodyLower.contains("valid for")
        
        if (isOTPMessage) {
            return false
        }
        
        // Check 6: Exclude EMI reminders and notifications
        val isEMINotification = bodyLower.contains("emi reminder") ||
            bodyLower.contains("emi of rs") ||
            bodyLower.contains("e-mandate") ||
            bodyLower.contains("will be deducted")
        
        if (isEMINotification) {
            return false
        }
        
        // Check 7: Must have valid amount ≥ 1 rupee
        val hasValidAmount = AMOUNT_PATTERNS.any { pattern ->
            val match = pattern.find(sms.body)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                amount >= 1.0 // Minimum 1 rupee
            } else false
        }
        
        if (!hasValidAmount) {
            return false
        }
        
        // All validation checks passed
        return true
    }
    
    /**
     * Parse individual SMS into transaction object
     */
    private fun parseTransaction(sms: HistoricalSMS): ParsedTransaction? {
        return try {
            val amount = extractAmount(sms.body)
            val merchant = extractMerchant(sms.body)
            val bankName = extractBankName(sms.address)
            val isDebit = determineTransactionType(sms.body)
            
            if (amount > 0 && merchant.isNotEmpty()) {
                ParsedTransaction(
                    id = "hist_${sms.id}",
                    amount = amount,
                    merchant = merchant,
                    bankName = bankName,
                    date = sms.date,
                    rawSMS = sms.body,
                    confidence = calculateConfidence(sms.body),
                    isDebit = isDebit
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "[UNIFIED] Error parsing transaction: ${sms.body}", e)
            null
        }
    }
    
    /**
     * Determine if the transaction is debit (money going out) or credit (money coming in)
     */
    private fun determineTransactionType(messageBody: String): Boolean {
        val bodyLower = messageBody.lowercase()
        
        // Check for credit keywords first (money received)
        val hasCreditKeywords = CREDIT_KEYWORDS.any { keyword -> 
            bodyLower.contains(keyword) 
        }
        
        // Check for additional credit patterns
        val hasCreditPatterns = bodyLower.contains("money received") ||
                               bodyLower.contains("amount received") ||
                               bodyLower.contains("balance updated") ||
                               bodyLower.contains("amount added") ||
                               bodyLower.contains("has been added") ||
                               bodyLower.contains("transfer in") ||
                               bodyLower.contains("incoming transfer") ||
                               bodyLower.contains("received from") ||
                               bodyLower.contains("refunded") ||
                               bodyLower.contains("cashback earned")
        
        // Check for debit keywords (money going out)
        val hasDebitKeywords = DEBIT_KEYWORDS.any { keyword -> 
            bodyLower.contains(keyword) 
        }
        
        // If we have both types of keywords, prioritize credit keywords
        // as they are typically more explicit
        return when {
            hasCreditKeywords || hasCreditPatterns -> {
                Log.d(TAG, "[CREDIT] Detected credit transaction: ${messageBody.take(50)}...")
                false // false means credit (money coming in)
            }
            hasDebitKeywords -> {
                Log.d(TAG, "[DEBIT] Detected debit transaction: ${messageBody.take(50)}...")
                true // true means debit (money going out)
            }
            else -> {
                // Default to debit if we can't determine
                Log.w(TAG, "[DEFAULT] Cannot determine transaction type, defaulting to debit: ${messageBody.take(50)}...")
                true
            }
        }
    }
    
    /**
     * Extract amount from SMS body using proven patterns
     */
    private fun extractAmount(messageBody: String): Double {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(messageBody)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                // Filter out suspiciously small amounts (likely account numbers, etc.)
                if (amount >= 1.0) {
                    return amount
                }
            }
        }
        
        return 0.0
    }
    
    /**
     * Extract merchant name using proven extraction patterns
     */
    private fun extractMerchant(messageBody: String): String {
        // Enhanced patterns for merchant extraction
        val patterns = listOf(
            // Pattern: "at MERCHANTNAME on"
            Regex("""at\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            // Pattern: "to MERCHANTNAME on"
            Regex("""to\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            // Pattern: "for MERCHANTNAME on"
            Regex("""for\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            // Pattern: "purchase at MERCHANTNAME"
            Regex("""purchase\s+at\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            // Pattern: "spent at MERCHANTNAME"
            Regex("""spent\s+at\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            // Pattern: "UPI-MERCHANTNAME" or "UPI/MERCHANTNAME"
            Regex("""UPI[/-]([A-Z][A-Z0-9\s&'-]+?)(?:\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            // Pattern: Common merchant formats like "SWIGGY*ORDER"
            Regex("""([A-Z]{3,})\*[A-Z0-9]+""", RegexOption.IGNORE_CASE),
            // Pattern: "MERCHANTNAME-Location" 
            Regex("""([A-Z]{3,}[A-Z0-9\s&'-]*?)-[A-Z]""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(messageBody)
            if (match != null) {
                val merchant = match.groupValues[1].trim()
                // Clean up the merchant name
                return cleanMerchantName(merchant)
            }
        }
        
        // Enhanced fallback: look for well-known merchant patterns
        val knownMerchants = listOf(
            "SWIGGY", "ZOMATO", "UBER", "OLA", "AMAZON", "FLIPKART", "PAYTM", 
            "PHONEPE", "GPAY", "BIGBAZAAR", "DMART", "MCDONALDS", "KFC",
            "DOMINOS", "PIZZAHUT", "STARBUCKS", "CCD", "RELIANCE", "SPENCER",
            "LIFESTYLE", "PANTALOONS", "WESTSIDE", "MYNTRA", "AJIO"
        )
        
        val bodyUpper = messageBody.uppercase()
        for (merchant in knownMerchants) {
            if (bodyUpper.contains(merchant)) {
                return merchant
            }
        }
        
        // Final fallback: extract any capitalized sequence
        val capitalizedPattern = Regex("""([A-Z]{3,}[A-Z0-9\s]*?)(?:\s|$)""")
        val match = capitalizedPattern.find(messageBody)
        if (match != null) {
            return cleanMerchantName(match.groupValues[1].trim())
        }
        
        return "Unknown Merchant"
    }
    
    /**
     * Clean merchant name by removing suffixes and normalizing
     */
    private fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(Regex("""[*#@\-_]+.*"""), "") // Remove suffixes after special chars
            .replace(Regex("""\s+"""), " ") // Normalize spaces
            .trim()
            .takeIf { it.length >= 2 } ?: "Unknown Merchant"
    }
    
    /**
     * Extract bank name from SMS sender address
     */
    private fun extractBankName(address: String): String {
        val addressUpper = address.uppercase()
        
        // Map of bank codes to user-friendly names
        val bankMapping = mapOf(
            "HDFCBK" to "HDFC Bank",
            "ICICI" to "ICICI Bank", 
            "SBIINB" to "SBI",
            "AXISBK" to "Axis Bank",
            "KOTAK" to "Kotak Bank",
            "CANBNK" to "Canara Bank",
            "BOBCRD" to "Bank of Baroda",
            "PNBSMS" to "Punjab National Bank",
            "UNIONBK" to "Union Bank",
            "INDIANBK" to "Indian Bank",
            "CENBK" to "Central Bank",
            "SYNBK" to "Syndicate Bank",
            "VIJBK" to "Vijaya Bank",
            "DENABNK" to "Dena Bank",
            "ALLBK" to "Allahabad Bank",
            "CORPBK" to "Corporation Bank",
            "UCOBK" to "UCO Bank",
            "OBVBK" to "Oriental Bank",
            "ANDHRABK" to "Andhra Bank",
            "IOBCHN" to "Indian Overseas Bank",
            "IDBIBN" to "IDBI Bank",
            "FEDBNK" to "Federal Bank",
            "KRVYSB" to "Karvy",
            "LAKBNK" to "Lakshmi Vilas Bank",
            "NAINBK" to "Nainital Bank",
            "DHANBK" to "Dhanlakshmi Bank",
            "CUBNK" to "City Union Bank",
            "CATHBK" to "Catholic Syrian Bank",
            "TMBNET" to "Tamilnad Mercantile Bank",
            "KERBK" to "Kerala Gramin Bank",
            "JKBNET" to "J&K Bank",
            "HSBCIN" to "HSBC India",
            "SCBIND" to "Standard Chartered",
            "CITIINDIA" to "Citibank",
            "DEUTCH" to "Deutsche Bank",
            "BARCLY" to "Barclays",
            "PAYTM" to "Paytm Payments Bank",
            "PHONEPE" to "PhonePe",
            "GPAY" to "Google Pay",
            "AMAZONPAY" to "Amazon Pay",
            "MOBIKWIK" to "MobiKwik"
        )
        
        for ((code, name) in bankMapping) {
            if (addressUpper.contains(code)) {
                return name
            }
        }
        
        // Fallback: check if address contains common bank keywords
        val fallbackPatterns = mapOf(
            "HDFC" to "HDFC Bank",
            "ICICI" to "ICICI Bank",
            "SBI" to "SBI",
            "AXIS" to "Axis Bank",
            "KOTAK" to "Kotak Bank",
            "PAYTM" to "Paytm",
            "PHONEPE" to "PhonePe",
            "GPAY" to "Google Pay"
        )
        
        for ((pattern, name) in fallbackPatterns) {
            if (addressUpper.contains(pattern)) {
                return name
            }
        }
        
        return address.takeIf { it.isNotEmpty() } ?: "Unknown Bank"
    }
    
    /**
     * Calculate confidence score for the parsed transaction
     */
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
    
    /**
     * Exposed methods for individual validation/parsing (for consistency with old interface)
     */
    fun isBankTransactionSMS(sms: HistoricalSMS): Boolean {
        return isBankTransaction(sms)
    }
    
    fun parseTransactionFromSMS(sms: HistoricalSMS): ParsedTransaction? {
        return parseTransaction(sms)
    }
    
    /**
     * Save rejected SMS to CSV file for verification
     */
    private fun saveRejectedSMSToCSV(rejectedSMSList: List<RejectedSMS>) {
        try {
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir == null) {
                Log.w(TAG, "External storage not available for CSV export")
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
            
            Log.i(TAG, "[CSV] Rejected SMS saved to: ${csvFile.absolutePath}")
            Log.i(TAG, "[CSV] File contains ${rejectedSMSList.size} rejected SMS messages")
            Log.i(TAG, "[CSV] You can find this file at: /Android/data/com.expensemanager.app/files/rejected_sms_*.csv")
            
        } catch (e: Exception) {
            Log.e(TAG, "[CSV] Error saving rejected SMS to CSV", e)
        }
    }
}

/**
 * Data classes for SMS parsing results
 */
data class HistoricalSMS(
    val id: String,
    val address: String,
    val body: String,
    val date: Date,
    val type: Int
)

data class ParsedTransaction(
    val id: String,
    val amount: Double,
    val merchant: String,
    val bankName: String,
    val date: Date,
    val rawSMS: String,
    val confidence: Float,
    val isDebit: Boolean = true
)

data class RejectedSMS(
    val sender: String,
    val body: String,
    val date: Date,
    val reason: String
)