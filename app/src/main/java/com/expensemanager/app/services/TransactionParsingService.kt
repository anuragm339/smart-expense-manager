package com.expensemanager.app.services

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.expensemanager.app.models.HistoricalSMS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.expensemanager.app.models.ParsedTransaction
import com.expensemanager.app.utils.MerchantAliasManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Unified transaction parsing service that consolidates SMS parsing logic
 * used across Dashboard and Messages screens.
 * 
 * This service provides a consistent way to:
 * 1. Parse SMS messages into transactions
 * 2. Apply merchant aliases and categories
 * 3. Validate transaction data
 * 4. Format dates and amounts consistently
 */
@Singleton
class TransactionParsingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val merchantAliasManager: MerchantAliasManager
) {
    
    companion object {
        private const val TAG = "TransactionParsingService"
        private const val MONTHS_TO_SCAN = 6 // Scan last 6 months
        private const val MAX_SMS_TO_PROCESS = 5000 // Limit SMS processing to prevent ANR
        private val logger: Logger = LoggerFactory.getLogger(TAG)
        
        // Enhanced bank sender patterns (unified from proven logic)
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
        
        // Exclusion patterns for promotional/marketing SMS
        private val PROMOTIONAL_EXCLUSIONS = listOf(
            "offer", "discount", "sale", "cashback offer", "reward points",
            "promotional", "advertisement", "marketing", "subscribe", "unsubscribe"
        )
        
        // OTP/verification exclusions
        private val OTP_EXCLUSIONS = listOf(
            "otp", "verification code", "verify", "authentication", "pin",
            "secure code", "temporary password", "login code"
        )
        
        // EMI/notification exclusions
        private val EMI_EXCLUSIONS = listOf(
            "emi due", "payment reminder", "due date", "overdue", "pending payment",
            "statement", "bill generation", "auto pay failed"
        )
    }
    
    /**
     * Main method to scan historical SMS and extract valid transactions
     * This provides consistent SMS parsing across all screens
     */
    suspend fun scanHistoricalSMS(
        progressCallback: ((current: Int, total: Int, status: String) -> Unit)? = null
    ): List<ParsedTransaction> = withContext(Dispatchers.IO) {
        val transactions = mutableListOf<ParsedTransaction>()
        
        try {
            logger.debug("[SCAN] Starting unified SMS scan for transaction parsing")
            
            // Calculate date range for scanning
            val cutoffDate = Calendar.getInstance().apply {
                add(Calendar.MONTH, -MONTHS_TO_SCAN)
            }.timeInMillis
            
            // Query SMS with proper selection and sorting
            val uri: Uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )
            
            val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}"
            val selectionArgs = arrayOf(cutoffDate.toString())
            val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $MAX_SMS_TO_PROCESS"
            
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val totalMessages = cursor.count
                logger.debug("[SCAN] Found $totalMessages SMS messages to process")
                
                progressCallback?.invoke(0, totalMessages, "Scanning SMS messages...")
                
                var processedCount = 0
                while (cursor.moveToNext() && processedCount < MAX_SMS_TO_PROCESS) {
                    try {
                        val sms = extractSMSFromCursor(cursor)
                        if (sms != null && isValidBankSMS(sms)) {
                            val transaction = parseTransaction(sms)
                            if (transaction != null) {
                                transactions.add(transaction)
                            }
                        }
                        
                        processedCount++
                        if (processedCount % 100 == 0) {
                            progressCallback?.invoke(
                                processedCount, 
                                totalMessages, 
                                "Processed $processedCount messages, found ${transactions.size} transactions"
                            )
                        }
                        
                    } catch (e: Exception) {
                        logger.warn("Error processing SMS at position $processedCount: ${e.message}")
                    }
                }
            }
            
            logger.info("[RESULT] SMS scan completed: ${transactions.size} valid transactions found")
            
        } catch (e: Exception) {
            logger.error("[ERROR] Failed to scan SMS", e)
        }
        
        return@withContext transactions
    }
    
    /**
     * Parse a single SMS message into a transaction
     * This method consolidates the parsing logic from multiple sources
     */
    fun parseTransactionFromSMS(sms: HistoricalSMS): ParsedTransaction? {
        return parseTransaction(sms)
    }
    
    /**
     * Core transaction parsing logic
     */
    private fun parseTransaction(sms: HistoricalSMS): ParsedTransaction? {
        try {
            val body = sms.body.lowercase(Locale.ROOT)
            
            // Skip if this is not a financial transaction
            if (!isFinancialTransaction(body)) {
                return null
            }
            
            // Extract amount
            val amount = extractAmount(sms.body) ?: return null
            
            // Determine transaction type (debit/credit)
            val isDebit = isDebitTransaction(body)
            
            // Extract merchant name
            val rawMerchant = extractMerchantName(sms.body, sms.address) ?: "Unknown Merchant"
            
            // Apply merchant aliases and get category
            val displayMerchant = merchantAliasManager.getDisplayName(rawMerchant)
            val category = merchantAliasManager.getMerchantCategory(rawMerchant)
            val categoryColor = merchantAliasManager.getMerchantCategoryColor(rawMerchant)
            
            // Extract bank name from sender
            val bankName = extractBankName(sms.address)
            
            // Calculate confidence score
            val confidence = calculateConfidenceScore(sms.body, amount, rawMerchant)
            
            return ParsedTransaction(
                amount = amount,
                merchant = displayMerchant,
                rawMerchant = rawMerchant,
                bankName = bankName,
                category = category,
                categoryColor = categoryColor,
                isDebit = isDebit,
                date = sms.date,
                confidence = confidence,
                rawSMS = sms.body
            )
            
        } catch (e: Exception) {
            logger.warn("Error parsing SMS transaction: ${e.message}")
            return null
        }
    }
    
    /**
     * Extract SMS data from cursor
     */
    private fun extractSMSFromCursor(cursor: Cursor): HistoricalSMS? {
        return try {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
            val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
            val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
            val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
            
            HistoricalSMS(
                id = id.toString(),
                address = address,
                body = body,
                date = Date(date),
                type = 0 // SMS type
            )
        } catch (e: Exception) {
            logger.warn("Error extracting SMS from cursor: ${e.message}")
            null
        }
    }
    
    /**
     * Check if SMS is from a valid bank sender
     */
    private fun isValidBankSMS(sms: HistoricalSMS): Boolean {
        val sender = sms.address.uppercase(Locale.ROOT)
        val body = sms.body.lowercase(Locale.ROOT)
        
        // Check if sender matches known bank patterns
        val isFromBank = BANK_SENDERS.any { sender.contains(it) }
        if (!isFromBank) return false
        
        // Exclude promotional/marketing SMS
        if (PROMOTIONAL_EXCLUSIONS.any { body.contains(it, ignoreCase = true) }) {
            return false
        }
        
        // Exclude OTP/verification messages
        if (OTP_EXCLUSIONS.any { body.contains(it, ignoreCase = true) }) {
            return false
        }
        
        // Exclude EMI reminders and notifications
        if (EMI_EXCLUSIONS.any { body.contains(it, ignoreCase = true) }) {
            return false
        }
        
        return true
    }
    
    /**
     * Check if the SMS body indicates a financial transaction
     */
    private fun isFinancialTransaction(body: String): Boolean {
        val hasDebitKeyword = DEBIT_KEYWORDS.any { body.contains(it, ignoreCase = true) }
        val hasCreditKeyword = CREDIT_KEYWORDS.any { body.contains(it, ignoreCase = true) }
        
        return hasDebitKeyword || hasCreditKeyword
    }
    
    /**
     * Extract amount from SMS body
     */
    private fun extractAmount(body: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(body)
            if (match != null) {
                try {
                    val amountStr = match.groupValues[1].replace(",", "")
                    val amount = amountStr.toDouble()
                    
                    // Filter out suspiciously small amounts (likely account numbers, etc.)
                    if (amount >= 1.0) {
                        return amount
                    }
                } catch (e: NumberFormatException) {
                    continue
                }
            }
        }
        return null
    }
    
    /**
     * Determine if transaction is a debit
     */
    private fun isDebitTransaction(body: String): Boolean {
        val hasDebitKeyword = DEBIT_KEYWORDS.any { body.contains(it, ignoreCase = true) }
        val hasCreditKeyword = CREDIT_KEYWORDS.any { body.contains(it, ignoreCase = true) }
        
        // If both keywords are present, prioritize debit keywords
        return hasDebitKeyword || !hasCreditKeyword
    }
    
    /**
     * Extract merchant name from SMS body
     */
    private fun extractMerchantName(body: String, sender: String): String? {
        // Try multiple merchant extraction patterns
        val patterns = listOf(
            // Pattern 1: "at MERCHANT" or "@ MERCHANT"
            Regex("""(?:at|@)\s+([A-Z][A-Z0-9\s&.-]{2,30})""", RegexOption.IGNORE_CASE),
            
            // Pattern 2: "to MERCHANT" or "towards MERCHANT"
            Regex("""(?:to|towards)\s+([A-Z][A-Z0-9\s&.-]{2,30})""", RegexOption.IGNORE_CASE),
            
            // Pattern 3: "for MERCHANT" purchases
            Regex("""for\s+([A-Z][A-Z0-9\s&.-]{2,30})\s+(?:purchase|transaction)""", RegexOption.IGNORE_CASE),
            
            // Pattern 4: "VIA MERCHANT" or "via MERCHANT"
            Regex("""via\s+([A-Z][A-Z0-9\s&.-]{2,30})""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                val merchant = match.groupValues[1].trim()
                if (merchant.length >= 3 && !merchant.matches(Regex("""^\d+$"""))) {
                    return merchant
                }
            }
        }
        
        // Fallback to sender-based extraction
        return extractBankName(sender)
    }
    
    /**
     * Extract bank name from sender
     */
    private fun extractBankName(sender: String): String {
        val upperSender = sender.uppercase(Locale.ROOT)
        
        // Map known senders to bank names
        return when {
            upperSender.contains("HDFC") -> "HDFC Bank"
            upperSender.contains("ICICI") -> "ICICI Bank"
            upperSender.contains("SBI") -> "State Bank of India"
            upperSender.contains("AXIS") -> "Axis Bank"
            upperSender.contains("KOTAK") -> "Kotak Mahindra Bank"
            upperSender.contains("CANARA") || upperSender.contains("CANBNK") -> "Canara Bank"
            upperSender.contains("BOB") -> "Bank of Baroda"
            upperSender.contains("PNB") -> "Punjab National Bank"
            upperSender.contains("UNION") -> "Union Bank"
            upperSender.contains("INDIAN") -> "Indian Bank"
            upperSender.contains("PAYTM") -> "Paytm Payments Bank"
            upperSender.contains("YES") -> "YES Bank"
            upperSender.contains("RBL") -> "RBL Bank"
            else -> sender
        }
    }
    
    /**
     * Calculate confidence score for the parsed transaction
     */
    private fun calculateConfidenceScore(body: String, amount: Double, merchant: String): Double {
        var confidence = 0.5 // Base confidence
        
        // Increase confidence for clear transaction keywords
        if (DEBIT_KEYWORDS.any { body.contains(it, ignoreCase = true) }) {
            confidence += 0.2
        }
        
        // Increase confidence for proper amount formatting
        if (body.contains("RS", ignoreCase = true) || body.contains("₹")) {
            confidence += 0.1
        }
        
        // Increase confidence for merchant information
        if (merchant.length > 5 && !merchant.equals("Unknown Merchant", ignoreCase = true)) {
            confidence += 0.15
        }
        
        // Decrease confidence for very high amounts (might be account balances)
        if (amount > 50000) {
            confidence -= 0.1
        }
        
        return confidence.coerceIn(0.0, 1.0)
    }
}

