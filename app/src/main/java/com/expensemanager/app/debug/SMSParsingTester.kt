package com.expensemanager.app.debug

import android.util.Log
import java.util.*
import java.util.regex.Pattern

/**
 * SMS Parsing Test Utility
 * Use this class to test SMS parsing logic and identify issues with non-transaction SMS
 */
class SMSParsingTester {
    
    companion object {
        private const val TAG = "SMSParsingTester"
    }
    
    data class SMSTestResult(
        val smsText: String,
        val isValidTransaction: Boolean,
        val extractedAmount: Double?,
        val extractedMerchant: String?,
        val extractedBank: String?,
        val confidence: Float,
        val failureReasons: List<String>,
        val detectionFlags: Map<String, Boolean>
    )
    
    /**
     * Test a single SMS to see if it should be parsed as a transaction
     */
    fun testSMSParsing(smsText: String, senderAddress: String = ""): SMSTestResult {
        Log.d(TAG, "=== TESTING SMS ===")
        Log.d(TAG, "SMS: ${smsText.take(100)}...")
        Log.d(TAG, "Sender: $senderAddress")
        
        val failureReasons = mutableListOf<String>()
        val detectionFlags = mutableMapOf<String, Boolean>()
        
        // MANDATORY CRITERIA 1: Reference/Transaction Number
        val hasRefNumber = hasReferenceNumber(smsText)
        detectionFlags["hasReferenceNumber"] = hasRefNumber
        if (!hasRefNumber) {
            failureReasons.add("No reference/transaction number found")
        }
        
        // MANDATORY CRITERIA 2: Debit/Credit Action  
        val hasDebitCredit = hasDebitCreditAction(smsText)
        detectionFlags["hasDebitCreditAction"] = hasDebitCredit
        if (!hasDebitCredit) {
            failureReasons.add("No debit/credit action found")
        }
        
        // MANDATORY CRITERIA 3: Account Reference
        val hasAccountRef = hasAccountReference(smsText)
        detectionFlags["hasAccountReference"] = hasAccountRef
        if (!hasAccountRef) {
            failureReasons.add("No account number reference found")
        }
        
        // MANDATORY CRITERIA 4: Bank sender validation
        val isFromBank = isFromBankSender(senderAddress)
        detectionFlags["isFromBank"] = isFromBank
        if (!isFromBank) {
            failureReasons.add("Not from a recognized bank sender")
        }
        
        // Additional validation: Amount extraction
        val extractedAmount = extractAmount(smsText)
        detectionFlags["hasValidAmount"] = extractedAmount != null && extractedAmount > 0
        if (extractedAmount == null || extractedAmount <= 0) {
            failureReasons.add("No valid amount found")
        }
        
        // Test 5: Merchant extraction
        val extractedMerchant = extractMerchant(smsText)
        detectionFlags["hasValidMerchant"] = extractedMerchant != null && extractedMerchant != "Unknown Merchant"
        
        // Test 6: Date/time presence
        val hasDateTime = hasDateTimeInfo(smsText)
        detectionFlags["hasDateTime"] = hasDateTime
        if (!hasDateTime) {
            failureReasons.add("No date/time information found")
        }
        
        // Overall validation - ALL 4 mandatory criteria must pass
        val isValidTransaction = hasRefNumber && 
                               hasDebitCredit && 
                               hasAccountRef &&
                               isFromBank &&
                               extractedAmount != null && 
                               extractedAmount > 0
        
        val confidence = calculateConfidence(detectionFlags)
        val bankName = determineBankFromSender(senderAddress)
        
        val result = SMSTestResult(
            smsText = smsText,
            isValidTransaction = isValidTransaction,
            extractedAmount = extractedAmount,
            extractedMerchant = extractedMerchant,
            extractedBank = bankName,
            confidence = confidence,
            failureReasons = failureReasons,
            detectionFlags = detectionFlags
        )
        
        logTestResult(result)
        return result
    }
    
    /**
     * Test multiple SMS samples
     */
    fun testMultipleSMS(smsTexts: List<Pair<String, String>>): List<SMSTestResult> {
        Log.d(TAG, "\n=== TESTING ${smsTexts.size} SMS MESSAGES ===")
        
        val results = smsTexts.map { (sms, sender) ->
            testSMSParsing(sms, sender)
        }
        
        // Summary
        val validCount = results.count { it.isValidTransaction }
        val invalidCount = results.size - validCount
        
        Log.d(TAG, "\n=== TEST SUMMARY ===")
        Log.d(TAG, "Total SMS tested: ${results.size}")
        Log.d(TAG, "Valid transactions: $validCount")
        Log.d(TAG, "Invalid/promotional: $invalidCount")
        Log.d(TAG, "Success rate: ${(validCount.toFloat() / results.size * 100).toInt()}%")
        
        // Show problematic SMS
        Log.d(TAG, "\n=== PROBLEMATIC SMS ===")
        results.filter { !it.isValidTransaction }.forEach { result ->
            Log.d(TAG, "SMS: ${result.smsText.take(50)}...")
            Log.d(TAG, "Issues: ${result.failureReasons.joinToString(", ")}")
            Log.d(TAG, "---")
        }
        
        return results
    }
    
    // ===== VALIDATION METHODS =====
    
    /**
     * MANDATORY CRITERIA 1: Reference/Transaction Number Detection
     */
    private fun hasReferenceNumber(body: String): Boolean {
        val bodyLower = body.lowercase()
        
        return bodyLower.contains("ref ") || 
               bodyLower.contains("ref-") || 
               bodyLower.contains("ref no") ||
               bodyLower.contains("ref:") ||
               bodyLower.contains("reference") ||
               bodyLower.contains("upi ref") ||
               bodyLower.contains("txn ref") ||
               bodyLower.contains("transaction id") ||
               bodyLower.contains("txn id") ||
               bodyLower.contains("txn no") ||
               bodyLower.contains("transaction no")
    }
    
    /**
     * MANDATORY CRITERIA 2: Debit/Credit Action Detection  
     */
    private fun hasDebitCreditAction(body: String): Boolean {
        val bodyLower = body.lowercase()
        
        val debitPatterns = listOf(
            "debited from", "deducted from", "withdrawn from", "spent on",
            "charged to", "paid from", "purchase at", "purchase from"
        )
        
        val creditPatterns = listOf(
            "credited to", "deposited in", "received in", "added to",
            "refunded to", "cashback to"
        )
        
        val hasDebitAction = debitPatterns.any { pattern -> bodyLower.contains(pattern) }
        val hasCreditAction = creditPatterns.any { pattern -> bodyLower.contains(pattern) }
        
        return hasDebitAction || hasCreditAction
    }
    
    /**
     * MANDATORY CRITERIA 3: Account Number Reference Detection
     */
    private fun hasAccountReference(body: String): Boolean {
        val bodyLower = body.lowercase()
        
        val accountPatterns = listOf(
            Regex("a/c\\s*(?:no\\.?\\s*)?[x*\\d]+\\d{4}", RegexOption.IGNORE_CASE),
            Regex("account\\s*(?:no\\.?\\s*)?[x*\\d]+\\d{4}", RegexOption.IGNORE_CASE),
            Regex("from.*account\\s*[x*\\d]+", RegexOption.IGNORE_CASE),
            Regex("to.*account\\s*[x*\\d]+", RegexOption.IGNORE_CASE),
            Regex("your\\s*a/c\\s*[x*\\d]+", RegexOption.IGNORE_CASE),
            Regex("card\\s*ending\\s*\\d{4}", RegexOption.IGNORE_CASE)
        )
        
        val hasAccountPattern = accountPatterns.any { pattern -> pattern.find(body) != null }
        
        val hasAccountContext = bodyLower.contains("from a/c") ||
                               bodyLower.contains("to a/c") ||
                               bodyLower.contains("from account") ||
                               bodyLower.contains("to account") ||
                               bodyLower.contains("your account") ||
                               bodyLower.contains("your a/c")
        
        return hasAccountPattern || hasAccountContext
    }
    
    private fun hasTransactionKeywords(sms: String): Boolean {
        val transactionKeywords = listOf(
            "debited", "credited", "spent", "paid", "transaction", "purchase",
            "withdrawn", "deposited", "transferred", "upi", "card", "payment",
            "debit", "credit", "txn", "amount", "balance", "a/c", "account",
            "rs.", "rs ", "₹", "inr", "rupees"
        )
        
        val smsLower = sms.lowercase()
        return transactionKeywords.any { keyword -> smsLower.contains(keyword) }
    }
    
    private fun extractAmount(sms: String): Double? {
        val amountPatterns = listOf(
            // Indian Rupee patterns
            Regex("""(?:rs\.?\s*|inr\s*|₹\s*)?(\d{1,2}(?:,\d{2})*(?:,\d{3})*(?:\.\d{2})?)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{1,3}(?:,\d{3})*(?:\.\d{2})?)\s*(?:rs\.?|inr|rupees?)\b""", RegexOption.IGNORE_CASE),
            Regex("""amount\s*(?:of\s*)?(?:rs\.?\s*|₹\s*)?(\d{1,2}(?:,\d+)*(?:\.\d{2})?)\b""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in amountPatterns) {
            val match = pattern.find(sms)
            if (match != null) {
                try {
                    val amountStr = match.groupValues[1].replace(",", "")
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount > 0) { // Reasonable limits
                        Log.d(TAG, "Extracted amount: ₹$amount from: ${match.value}")
                        return amount
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing amount: ${match.value}")
                }
            }
        }
        
        Log.w(TAG, "No valid amount found in SMS")
        return null
    }
    
    private fun isPromotionalSMS(sms: String): Boolean {
        val promotionalKeywords = listOf(
            "offer", "sale", "discount", "cashback", "reward", "bonus", "gift",
            "free", "win", "prize", "lucky", "congratulations", "congrats",
            "upgrade", "apply", "eligible", "activate", "register", "subscribe",
            "call now", "click here", "visit", "download", "install", "app",
            "emi", "loan", "insurance", "policy", "investment", "scheme",
            "delivery", "courier", "otp", "verification", "confirm", "verify",
            "get information", "info", "update", "notification", "alert",
            "terms", "conditions", "privacy", "unsubscribe", "stop",
            "promotional", "advertisement", "marketing", "campaign"
        )
        
        val smsLower = sms.lowercase()
        val promotionalCount = promotionalKeywords.count { keyword -> 
            smsLower.contains(keyword) 
        }
        
        // Consider promotional if has 2+ promotional keywords
        val isPromotional = promotionalCount >= 2
        
        if (isPromotional) {
            Log.d(TAG, "Promotional SMS detected. Keywords found: $promotionalCount")
        }
        
        return isPromotional
    }
    
    private fun isFromBankSender(address: String): Boolean {
        val bankPatterns = listOf(
            "hdfc", "icici", "sbi", "axis", "kotak", "bob", "pnb", "canara",
            "union", "indian", "central", "syndicate", "corporation", "dena",
            "vijaya", "allahabad", "andhra", "karnataka", "tamilnad", "kerala",
            "paytm", "phonepe", "gpay", "mobikwik", "freecharge", "amazonpay"
        )
        
        val addressLower = address.lowercase()
        val isBank = bankPatterns.any { bank -> addressLower.contains(bank) }
        
        // Also check for typical bank sender patterns
        val isBankFormat = address.matches(Regex("[A-Z]{2}-\\d{6}")) || // XX-123456 format
                          address.matches(Regex("[A-Z]{6}")) ||          // HDFCBK format
                          address.length == 6                            // 6-digit bank codes
        
        return isBank || isBankFormat
    }
    
    private fun extractMerchant(sms: String): String? {
        val merchantPatterns = listOf(
            Regex("""at\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""to\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""UPI[/-]([A-Z][A-Z0-9\s&'-]+?)(?:\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""([A-Z]{3,})\*[A-Z0-9]+""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in merchantPatterns) {
            val match = pattern.find(sms)
            if (match != null) {
                val merchant = match.groupValues[1].trim()
                if (merchant.length >= 3) {
                    Log.d(TAG, "Extracted merchant: '$merchant'")
                    return merchant
                }
            }
        }
        
        return null
    }
    
    private fun hasDateTimeInfo(sms: String): Boolean {
        val datePatterns = listOf(
            Regex("""\b\d{1,2}[-/]\d{1,2}[-/]\d{2,4}\b"""), // DD-MM-YYYY
            Regex("""\b\d{1,2}\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b\d{1,2}:\d{2}""") // Time pattern
        )
        
        return datePatterns.any { it.find(sms) != null }
    }
    
    private fun calculateConfidence(flags: Map<String, Boolean>): Float {
        val weights = mapOf(
            // Mandatory criteria (higher weights)
            "hasReferenceNumber" to 0.25f,
            "hasDebitCreditAction" to 0.25f,
            "hasAccountReference" to 0.25f,
            "isFromBank" to 0.15f,
            // Supporting criteria (lower weights)
            "hasValidAmount" to 0.05f,
            "hasValidMerchant" to 0.03f,
            "hasDateTime" to 0.02f
        )
        
        var confidence = 0f
        flags.forEach { (flag, value) ->
            if (value) {
                confidence += weights[flag] ?: 0f
            }
        }
        
        return confidence
    }
    
    private fun determineBankFromSender(address: String): String {
        return when {
            address.contains("HDFC", ignoreCase = true) -> "HDFC Bank"
            address.contains("ICICI", ignoreCase = true) -> "ICICI Bank"
            address.contains("SBI", ignoreCase = true) -> "SBI"
            address.contains("AXIS", ignoreCase = true) -> "Axis Bank"
            address.contains("KOTAK", ignoreCase = true) -> "Kotak Bank"
            else -> "Unknown Bank"
        }
    }
    
    private fun logTestResult(result: SMSTestResult) {
        Log.d(TAG, "\n--- TEST RESULT ---")
        Log.d(TAG, "Valid Transaction: ${result.isValidTransaction}")
        Log.d(TAG, "Confidence: ${(result.confidence * 100).toInt()}%")
        Log.d(TAG, "Amount: ₹${result.extractedAmount ?: "Not found"}")
        Log.d(TAG, "Merchant: ${result.extractedMerchant ?: "Not found"}")
        Log.d(TAG, "Bank: ${result.extractedBank}")
        Log.d(TAG, "Issues: ${result.failureReasons.joinToString(", ")}")
        Log.d(TAG, "Flags: ${result.detectionFlags}")
        Log.d(TAG, "-------------------\n")
    }
}

// ===== TEST USAGE EXAMPLES =====

/**
 * Example usage in your app - call this from a debug screen or test function
 */
fun runSMSParsingTests() {
    val tester = SMSParsingTester()
    
    // Test sample SMS messages
    val testSMS = listOf(
        // Valid transaction SMS
        "HDFC Bank" to "Alert: You have spent Rs.250.00 on your HDFC Bank Credit Card at SWIGGY BANGALORE on 25-Dec-24 at 14:30. Available limit: Rs.45,000. Call 18002586161 for help.",
        
        // Your problematic SMS samples
        "UNKNOWN" to "DELIVERY confirmation for your order",
        "PROMO" to "GET INFORMATION about our latest offers",
        "BANK" to "INR500000 & PAY IN EASY EMI FROM INDUSLND BANK - Apply now!",
        
        // Valid UPI transaction
        "PAYTM" to "Rs.150 debited from your account via UPI to AMAZON PAY on 26-Dec-24. UPI Ref: 123456789",
        
        // Promotional SMS (should be rejected)
        "OFFERS" to "Congratulations! You are eligible for a loan of Rs.5,00,000. Apply now and get instant approval.",
        
        // OTP SMS (should be rejected)
        "VERIFY" to "OTP for your transaction is 123456. Valid for 10 minutes. Do not share with anyone."
    )
    
    val results = tester.testMultipleSMS(testSMS)
    
    // You can also test individual SMS
    val singleResult = tester.testSMSParsing(
        "Alert: Rs.500 debited from A/c XX1234 for purchase at ZOMATO on 25-Dec-24",
        "HDFCBK"
    )
}