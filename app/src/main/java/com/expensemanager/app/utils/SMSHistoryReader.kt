package com.expensemanager.app.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

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
    val confidence: Float
)

class SMSHistoryReader(private val context: Context) {
    
    companion object {
        private const val TAG = "SMSHistoryReader"
        private const val MONTHS_TO_SCAN = 6 // Scan last 6 months
        
        // Verified bank sender patterns (more specific)
        private val BANK_SENDERS = listOf(
            "HDFCBK", "ICICI", "SBIINB", "AXISBK", "KOTAK", "CANBNK", "BOBCRD", "PNBSMS",
            "UNIONBK", "INDIANBK", "CENBK", "SYNBK", "VIJBK", "DENABNK",
            "ALLBK", "CORPBK", "UCOBK", "OBVBK", "ANDHRABK", "IOBCHN",
            "IDBIBN", "FEDBNK", "KRVYSB", "LAKBNK", "NAINBK", "DHANBK",
            "CUBNK", "CATHBK", "TMBNET", "KERBK", "JKBNET",
            "HSBCIN", "SCBIND", "CITIINDIA", "DEUTCH", "BARCLY",
            "PAYTM", "PHONEPE", "GPAY", "AMAZONPAY", "MOBIKWIK"
        )
        
        // Specific transaction debit/credit keywords
        private val DEBIT_KEYWORDS = listOf(
            "debited", "deducted", "spent", "withdrawn", "paid", "purchase", "charged"
        )
        
        private val CREDIT_KEYWORDS = listOf(
            "credited", "received", "deposited", "refund", "cashback"
        )
        
        // Amount patterns
        private val AMOUNT_PATTERNS = listOf(
            Regex("""(?:rs\.?|inr)\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([\d,]+(?:\.\d{2})?)\s*(?:rs\.?|inr)""", RegexOption.IGNORE_CASE),
            Regex("""([\d,]+(?:\.\d{2})?)\s*/-"""),
            Regex("""amount[:\s]*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        )
        
        // Spam keywords to exclude
        private val SPAM_KEYWORDS = listOf(
            "otp", "verification", "verify", "code", "pin", "password",
            "offer", "discount", "sale", "free", "win", "lucky", "congratulations",
            "click", "link", "website", "download", "install", "register",
            "expire", "urgent", "limited time", "hurry", "act now",
            "minimum balance", "kyc", "update", "renewal", "charges apply",
            "thank you for banking", "dear customer", "greetings",
            "welcome", "congratulations", "festive", "wishes",
            "reminder", "notice", "information", "alert",
            "service request", "request received", "application",
            "branch visit", "call us", "contact us", "help",
            "terms and conditions", "terms & conditions", "t&c"
        )
        
        // Balance enquiry patterns (not transactions)
        private val BALANCE_ENQUIRY_PATTERNS = listOf(
            "available balance", "current balance", "bal enquiry", "balance is",
            "your balance", "account balance", "wallet balance"
        )
    }
    
    suspend fun scanHistoricalSMS(): List<ParsedTransaction> = withContext(Dispatchers.IO) {
        val transactions = mutableListOf<ParsedTransaction>()
        var acceptedCount = 0
        var rejectedCount = 0
        
        try {
            val historicalSMS = readSMSHistory()
            //Log.d(TAG, "Found ${historicalSMS.size} historical SMS messages")
            
            for (sms in historicalSMS) {
                if (isBankTransaction(sms)) {
                    val transaction = parseTransaction(sms)
                    if (transaction != null) {
                        transactions.add(transaction)
                        acceptedCount++
//                        Log.d(TAG, "ACCEPTED TRANSACTION: ${sms.address} - ${sms.body.take(100)}")
                    }
                } else {
                    rejectedCount++
                }
            }
            
//            Log.d(TAG, "SMS Processing Summary:")
//            Log.d(TAG, "Total SMS scanned: ${historicalSMS.size}")
//            Log.d(TAG, "Accepted transactions: $acceptedCount")
//            Log.d(TAG, "Rejected SMS: $rejectedCount")
//            Log.d(TAG, "Final parsed transactions: ${transactions.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning historical SMS", e)
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
        val sortOrder = "${Telephony.Sms.DATE} DESC" // Newest first
        
        var cursor: Cursor? = null
        try {
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
            Log.e(TAG, "Error reading SMS history", e)
        } finally {
            cursor?.close()
        }
        
        return smsList
    }
    
    fun isBankTransactionSMS(sms: HistoricalSMS): Boolean {
        return isBankTransaction(sms)
    }
    
    fun parseTransactionFromSMS(sms: HistoricalSMS): ParsedTransaction? {
        return parseTransaction(sms)
    }
    
    private fun isBankTransaction(sms: HistoricalSMS): Boolean {
        val senderUpper = sms.address.uppercase()
        val bodyLower = sms.body.lowercase()
        
//        Log.d(TAG, "Checking SMS from ${sms.address}: ${sms.body.take(80)}...")
        
        // ALL transaction SMS must have a reference number
        val hasRefNumber = bodyLower.contains("ref ") || 
                          bodyLower.contains("ref-") || 
                          bodyLower.contains("ref no") ||
                          bodyLower.contains("reference")
        
        if (!hasRefNumber) {
//            Log.d(TAG, "Rejected: No reference number found")
            return false
        }
        
        // HDFC Bank transaction patterns (very specific)
        val isHDFCTransaction = senderUpper.contains("HDFCBK") && (
            bodyLower.contains("sent rs.") ||
            bodyLower.contains("money received - inr") ||
            bodyLower.contains("imps inr") ||
            bodyLower.contains("salary credited") ||
            bodyLower.contains("debited from hdfc bank")
        )
        
        // SBI Bank transaction patterns
        val isSBITransaction = (senderUpper.contains("SBYONO") || senderUpper.contains("SBIINB") || senderUpper.contains("CBSSBI")) && (
            bodyLower.contains("imps from ac") ||
            bodyLower.contains("debited inr") ||
            bodyLower.contains("credited by rs") ||
            bodyLower.contains("your a/c") ||
            bodyLower.contains("debited from")
        )

        // Other bank transaction patterns (very specific)
        val isOtherBankTransaction = false // Disable for now to test HDFC/SBI only
        
        val isActualTransaction = isHDFCTransaction || isSBITransaction || isOtherBankTransaction
        
        if (!isActualTransaction) {
           // Log.d(TAG, "Rejected: Not a bank transaction pattern")
            return false
        }
        
        // Exclude promotional/marketing SMS even from banks
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
           // Log.d(TAG, "Rejected: Promotional/marketing SMS")
            return false
        }
        
        // Exclude OTP/verification messages
        val isOTPMessage = bodyLower.contains("otp") ||
            bodyLower.contains("verification") ||
            bodyLower.contains("do not share") ||
            bodyLower.contains("valid for")
        
        if (isOTPMessage) {
            //Log.d(TAG, "Rejected: OTP/verification message")
            return false
        }
        
        // Exclude EMI reminders and notifications
        val isEMINotification = bodyLower.contains("emi reminder") ||
            bodyLower.contains("emi of rs") ||
            bodyLower.contains("e-mandate") ||
            bodyLower.contains("will be deducted")
        
        if (isEMINotification) {
            //Log.d(TAG, "Rejected: EMI/mandate notification")
            return false
        }
        
        // Must have valid amount ≥ 1 rupee
        val hasValidAmount = AMOUNT_PATTERNS.any { pattern ->
            val match = pattern.find(sms.body)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                amount >= 1.0 // Minimum 1 rupee
            } else false
        }
        
        if (!hasValidAmount) {
            //Log.d(TAG, "Rejected: No valid amount ≥ ₹1")
            return false
        }
        
        // Reference number already checked above, so this is redundant
        
       // Log.d(TAG, "Accepted: Valid transaction SMS")
        return true
    }
    
    private fun parseTransaction(sms: HistoricalSMS): ParsedTransaction? {
        return try {
            val amount = extractAmount(sms.body)
            val merchant = extractMerchant(sms.body)
            val bankName = extractBankName(sms.address)
            
            if (amount > 0 && merchant.isNotEmpty()) {
                ParsedTransaction(
                    id = "hist_${sms.id}",
                    amount = amount,
                    merchant = merchant,
                    bankName = bankName,
                    date = sms.date,
                    rawSMS = sms.body,
                    confidence = calculateConfidence(sms.body)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transaction: ${sms.body}", e)
            null
        }
    }
    
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
    
    private fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(Regex("""[*#@\-_]+.*"""), "") // Remove suffixes after special chars
            .replace(Regex("""\s+"""), " ") // Normalize spaces
            .trim()
            .takeIf { it.length >= 2 } ?: "Unknown Merchant"
    }
    
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
}