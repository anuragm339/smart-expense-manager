package com.smartexpenseai.app.parsing.engine

import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.parsing.models.BankRule
import com.smartexpenseai.app.parsing.models.BankRulesSchema
import com.smartexpenseai.app.parsing.models.ConfidenceScore
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified SMS parser using rule-based engine
 * Replaces hardcoded parsing logic with data-driven approach
 */
@Singleton
class UnifiedSMSParser @Inject constructor(
    private val ruleLoader: RuleLoader,
    private val confidenceCalculator: ConfidenceCalculator
) {
    private val logger = StructuredLogger("SMS_PARSING", "UnifiedSMSParser")

    companion object {
        // IMPORTANT: 2-digit year patterns MUST come first to prevent "yyyy" from accepting 2 digits as year 0-99
        private val DATE_FORMATS = listOf(
            "yyyy-MM-dd",    // ISO first (unambiguous)
            "dd-MM-yy",      // Try 2-digit patterns first
            "dd/MM/yy",
            "dd.MM.yy",
            "dd-MMM-yy",     // Alpha-month 2-digit ("04-Jul-25", common in SBI/HDFC/ICICI)
            "dd/MMM/yy",
            "dd MMM yy",
            "dd-MM-yyyy",    // Then 4-digit patterns
            "dd/MM/yyyy",
            "dd-MMM-yyyy",
            "dd/MMM/yyyy",
            "dd MMM yyyy"
        )

        // Alpha-month dates ("04-Jul-25") that the numeric bank date regexes miss
        private val ALPHA_MONTH_DATE_REGEX = Regex(
            "\\b(\\d{1,2}[-/ ](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/ ]\\d{2,4})\\b",
            RegexOption.IGNORE_CASE
        )

        // ISO datetime dates ("2026-07-07" / "2026-07-07:14:10:33"). Matched before the
        // bank date patterns so the loose "dd-MM-yy" rule can't grab "26-07-07" out of a
        // 4-digit year and mis-date the transaction to 2007.
        private val ISO_DATE_REGEX = Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b")

        // Anti-fraud / boilerplate trailer that ends most bank SMS
        // ("Not You? Call 1800.../SMS BLOCK ... to 7308080808"). It carries phone
        // numbers that a greedy "(?:at|to) <name>" merchant pattern would grab, so the
        // trailer is cut off before merchant extraction.
        private val TRAILER_REGEX = Regex(
            "(?i)\\b(?:not\\s+you|sms\\s+block|to\\s+block|block\\+?re-?issue|call\\s+1[89]00|dispute\\b|report\\s+(?:this|fraud))"
        )

        // A *completed* debit/credit signal ("Rs.X spent/debited/credited"). The
        // negative lookbehinds exclude the future forms ("will be debited",
        // "to be debited") so a genuine future-autopay notice is still rejected.
        private val COMPLETED_TXN_REGEX = Regex(
            "(?<!will\\s{0,3}be\\s{0,3})(?<!to\\s{0,3}be\\s{0,3})" +
                "\\b(?:spent|withdrawn|purchased|debited|credited|deducted|charged)\\b",
            RegexOption.IGNORE_CASE
        )

        // Non-transaction reasons that should reject even when a completed signal is
        // present (these are never real completed spends).
        private val HARD_REJECT_REASONS = setOf(
            "OTP message", "UPI collect request", "declined transaction"
        )

        // Amount tied to a spend/debit verb, in either order. Tried before bare "Rs.X"
        // so credit-card SMS don't pick up "Avl Lmt Rs.50000" instead of the spend.
        private val ACTION_AMOUNT_REGEXES = listOf(
            Regex(
                "(?i)(?:spent|debited|withdrawn|paid|sent|deducted|charged|credited|purchased)" +
                    "\\s+(?:of\\s+)?(?:₹|Rs\\.?|INR)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"
            ),
            Regex(
                "(?i)(?:₹|Rs\\.?|INR)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*" +
                    "(?:is\\s+|has\\s+been\\s+|was\\s+)?" +
                    "(?:spent|debited|withdrawn|paid|deducted|charged|credited)"
            )
        )

        // Account reference ("A/c XX3300", "a/c no 1234") — a strong transaction signal
        // for ref-less UPI/bank debits.
        private val ACCOUNT_REGEX = Regex("(?i)\\ba/?c\\b")

        // Card identifier ("Card x1234" / "Card ending 1234" / "Card **1234") used to
        // accept card-present transactions that carry no reference number
        private val CARD_LAST4_REGEX = Regex(
            "(?:card|crd)\\s*(?:no\\.?\\s*)?(?:number\\s*)?(?:ending(?:\\s+in)?\\s*|[xX*]+\\s*)?(\\d{4})\\b",
            RegexOption.IGNORE_CASE
        )

        // SMS that mention amounts but are not completed transactions. Checked before
        // extraction so future-autopay notices, UPI collect requests, bill reminders,
        // OTPs and declined payments never enter the database.
        private val NON_TRANSACTION_PATTERNS = listOf(
            "future autopay notice" to Regex("will\\s+be\\s+debited", RegexOption.IGNORE_CASE),
            "UPI collect request" to Regex("has\\s+requested|requested\\s+money|payment\\s+request|collect\\s+request", RegexOption.IGNORE_CASE),
            "OTP message" to Regex("\\botp\\b|one\\s*time\\s*password", RegexOption.IGNORE_CASE),
            "bill/due reminder" to Regex("payment\\s+due|min(?:imum)?\\s+(?:amount\\s+)?due|due\\s+on|is\\s+due", RegexOption.IGNORE_CASE),
            "declined transaction" to Regex("insufficient\\s+balance|transaction\\s+(?:declined|failed)", RegexOption.IGNORE_CASE),
            // Promotional offers name an amount and a card but are not spends. Markers
            // (voucher / coupon / reward points / "T&C" / "by doing N Trxns") do not
            // appear in genuine debit alerts, so this will not drop real transactions.
            "promotional offer" to Regex(
                "\\b(?:e-?voucher|voucher|gift\\s*card|coupon|reward\\s*points?)\\b" +
                    "|\\bt&c\\b|\\bterms\\s+(?:and|&)\\s+conditions\\b" +
                    "|\\bby\\s+doing\\s+\\d+\\s+(?:txn|trxn|transaction)s?\\b",
                RegexOption.IGNORE_CASE
            )
        )
    }

    /**
     * Parse SMS message into transaction with confidence score
     */
    suspend fun parseSMS(
        sender: String,
        body: String,
        timestamp: Long
    ): ParseResult = withContext(Dispatchers.IO) {
        try {
            // Load rules
            val rulesResult = ruleLoader.loadRules()
            if (rulesResult.isFailure) {
                logger.error("parseSMS", "Failed to load rules", rulesResult.exceptionOrNull())
                return@withContext ParseResult.Failed("Rule loading failed")
            }

            val rules = rulesResult.getOrNull()!!

            // 0. Reject non-transactional SMS early (future autopay notices, UPI collect
            // requests, OTPs, bill reminders) - these contain amounts but are not spends
            // A real completed spend can *also* mention reward points, a statement due
            // date, or the next autopay charge. Only drop such SMS for the unambiguous
            // reasons (OTP, collect request, declined) — not the "soft" markers.
            val hasCompletedTxn = COMPLETED_TXN_REGEX.containsMatchIn(body)
            NON_TRANSACTION_PATTERNS.forEach { (reason, pattern) ->
                if (pattern.containsMatchIn(body)) {
                    if (reason in HARD_REJECT_REASONS || !hasCompletedTxn) {
                        logger.debug("parseSMS", "Rejected non-transactional SMS ($reason): ${body.take(50)}...")
                        return@withContext ParseResult.Failed("Non-transactional SMS: $reason")
                    }
                    logger.debug("parseSMS", "Kept despite '$reason' marker — completed transaction present")
                }
            }

            // 1. Try to match sender to a bank
            val bankRule = findMatchingBank(sender, rules)
            val senderMatched = bankRule != null

            // 2. Extract transaction fields
            // NOTE: merchant extraction uses the RAW body - UPI VPA patterns ("merchant@ybl")
            // need the '@' that the old special-character stripping removed
            // Strip the anti-fraud trailer before merchant extraction so its phone
            // numbers ("...SMS BLOCK ... to 7308080808") can't be picked up as the merchant.
            val bodyForMerchant = stripTrailer(body)

            val amount = extractAmount(body, bankRule, rules)
            val merchant = extractMerchant(bodyForMerchant, bankRule, rules)
            val date = extractDate(body, bankRule, timestamp)
            val transactionType = extractTransactionType(body.replace(Regex("[^A-Za-z0-9\\s]"), " ").trim(), bankRule, rules)
            var referenceNumber = extractReferenceNumber(body, bankRule, rules)

            logger.debug("parseSMS", "Extracted referenceNumber: $referenceNumber for amount: $amount, merchant: $merchant")

            // 3. Validate required fields (HARD REQUIREMENTS)
            if (amount == null) {
                logger.warn("parseSMS", "No amount found in SMS: ${body.take(50)}...")
                return@withContext ParseResult.Failed("Amount not found")
            }

            // Zero/negative amounts are noise (fee reversals of 0, malformed SMS)
            val numericAmount = amount.replace(",", "").toDoubleOrNull()
            if (numericAmount == null || numericAmount <= 0.0) {
                logger.warn("parseSMS", "Invalid amount '$amount' in SMS: ${body.take(50)}...")
                return@withContext ParseResult.Failed("Invalid amount: $amount")
            }

            // Card-present (POS) transactions frequently carry no reference number.
            // If the SMS names a card AND uses an explicit debit/credit keyword,
            // synthesize a pseudo-reference instead of rejecting it as promotional.
            // IMPORTANT: derived from the SMS body hash, NOT the arrival timestamp,
            // so the same SMS always produces the same ref and dedup keeps working
            // across re-delivery and history rescans.
            if (referenceNumber == null) {
                val cardLast4 = CARD_LAST4_REGEX.find(body)?.groupValues?.get(1)
                val bodyLower = body.lowercase()
                val hasTxnKeyword = rules.fallbackPatterns.debitKeywords.any { bodyLower.contains(it) } ||
                    rules.fallbackPatterns.creditKeywords.any { bodyLower.contains(it) }
                // A strong transaction signal (debit/credit keyword) plus a card,
                // merchant, or account is enough to synthesize a stable pseudo-ref, so
                // ref-less UPI/bank debits aren't dropped as promotional.
                val hasMerchantOrAccount =
                    !merchant.isNullOrBlank() || ACCOUNT_REGEX.containsMatchIn(body)
                if (hasTxnKeyword && (cardLast4 != null || hasMerchantOrAccount)) {
                    val bodyHash = Integer.toHexString(body.trim().hashCode())
                    referenceNumber = if (cardLast4 != null) "CARD${cardLast4}H$bodyHash" else "TXNH$bodyHash"
                    logger.debug("parseSMS", "No ref number - synthesized pseudo-ref $referenceNumber")
                }
            }

            // CRITICAL: Reference number is MANDATORY for transaction SMS
            if (referenceNumber == null) {
                logger.warn("parseSMS", "No reference number found - likely promotional SMS: ${body.take(50)}...")
                return@withContext ParseResult.Failed("Reference number not found (required for transaction SMS)")
            }

            // 4. Calculate confidence score
            val confidence = confidenceCalculator.calculate(
                senderMatched = senderMatched,
                bankRule = bankRule,
                extractedAmount = amount,
                extractedMerchant = merchant,
                extractedDate = date.toString(),
                extractedType = transactionType,
                extractedReferenceNumber = referenceNumber,
                smsBody = body
            )

            // 5. Create transaction entity
            val transaction = createTransactionEntity(
                sender = sender,
                body = body,
                timestamp = timestamp,
                amount = amount,
                merchant = merchant,
                date = date,
                transactionType = transactionType,
                bankName = bankRule?.displayName,
                referenceNumber = referenceNumber
            )

            // 6. Update transaction with calculated confidence score
            val transactionWithConfidence = transaction.copy(
                confidenceScore = confidence.overall
            )

            logger.debug("parseSMS", "Created TransactionEntity with referenceNumber: ${transactionWithConfidence.referenceNumber}")

            ParseResult.Success(transactionWithConfidence, confidence)

        } catch (e: Exception) {
            logger.error("parseSMS", "Parse error", e)
            ParseResult.Failed("Parse exception: ${e.message}")
        }
    }

    /**
     * Find matching bank rule for sender
     */
    private fun findMatchingBank(sender: String, rules: BankRulesSchema): BankRule? {
        return rules.banks.firstOrNull { bank ->
            bank.senderPatterns.any { pattern ->
                try {
                    val regex = ruleLoader.getCompiledRegex(pattern)
                    regex.containsMatchIn(sender)
                } catch (e: Exception) {
                    logger.warn("findMatchingBank", "Invalid sender pattern: $pattern")
                    false
                }
            }
        }
    }

    /**
     * Extract amount from SMS body
     */
    private fun extractAmount(
        body: String,
        bankRule: BankRule?,
        rules: BankRulesSchema
    ): String? {
        // Prefer the amount next to a spend/debit verb so credit-card SMS don't grab
        // "Avl Lmt Rs.50000" instead of the actual "Rs.750 spent".
        ACTION_AMOUNT_REGEXES.forEach { regex ->
            regex.find(body)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return it }
        }

        // Try bank-specific patterns first
        bankRule?.patterns?.amount?.forEach { pattern ->
            val amount = tryExtractWithPattern(body, pattern)
            if (amount != null) return amount
        }

        // Fall back to generic patterns
        rules.fallbackPatterns.amount.forEach { pattern ->
            val amount = tryExtractWithPattern(body, pattern)
            if (amount != null) return amount
        }

        return null
    }

    /**
     * Extract merchant from SMS body
     */
    private fun extractMerchant(
        body: String,
        bankRule: BankRule?,
        rules: BankRulesSchema
    ): String? {
        // Try bank-specific patterns first
        bankRule?.patterns?.merchant?.forEach { pattern ->
            val merchant = tryExtractWithPattern(body, pattern)
            if (merchant != null) {
                return cleanMerchantName(merchant)
            }
        }

        // Fall back to generic patterns
        rules.fallbackPatterns.merchant.forEach { pattern ->
            val merchant = tryExtractWithPattern(body, pattern)
            if (merchant != null) {
                return cleanMerchantName(merchant)
            }
        }

        return null
    }

    /**
     * Extract date from SMS body
     */
    /** Remove the trailing anti-fraud boilerplate (and its phone numbers). */
    private fun stripTrailer(body: String): String {
        val match = TRAILER_REGEX.find(body) ?: return body
        return body.substring(0, match.range.first).trim()
    }

    private fun extractDate(
        body: String,
        bankRule: BankRule?,
        defaultTimestamp: Long
    ): Date {
        // ISO dates first (yyyy-MM-dd), before the looser bank patterns.
        ISO_DATE_REGEX.find(body)?.groupValues?.get(1)?.let { dateStr ->
            tryParseDate(dateStr)?.let { return it }
        }

        // Try bank-specific date patterns
        bankRule?.patterns?.date?.forEach { pattern ->
            val dateStr = tryExtractWithPattern(body, pattern)
            if (dateStr != null) {
                val parsedDate = tryParseDate(dateStr)
                if (parsedDate != null) return parsedDate
            }
        }

        // Alpha-month dates ("04-Jul-25") - the per-bank regexes are numeric-only
        ALPHA_MONTH_DATE_REGEX.find(body)?.groupValues?.get(1)?.let { dateStr ->
            tryParseDate(dateStr)?.let { return it }
        }

        // Default to SMS timestamp
        return Date(defaultTimestamp)
    }

    /**
     * Extract transaction type (debit/credit)
     */
    private fun extractTransactionType(
        body: String,
        bankRule: BankRule?,
        rules: BankRulesSchema
    ): String? {
        val bodyLower = body.lowercase()

        // Try bank-specific type patterns
        bankRule?.patterns?.transactionType?.forEach { pattern ->
            val type = tryExtractWithPattern(body, pattern)
            if (type != null) {
                // Handle BOB abbreviations (Dr. -> debit, Cr. -> credit)
                val normalized = type.lowercase().trim('.', ' ')
                return when (normalized) {
                    "dr" -> "debit"
                    "cr" -> "credit"
                    else -> normalized
                }
            }
        }

        // Check fallback keywords
        if (rules.fallbackPatterns.debitKeywords.any { bodyLower.contains(it) }) {
            return "debit"
        }
        if (rules.fallbackPatterns.creditKeywords.any { bodyLower.contains(it) }) {
            return "credit"
        }

        return "debit" // Default to debit if unclear
    }

    /**
     * Extract reference number from SMS body
     */
    private fun extractReferenceNumber(
        body: String,
        bankRule: BankRule?,
        rules: BankRulesSchema
    ): String? {
        // Try bank-specific patterns first
        bankRule?.patterns?.referenceNumber?.forEach { pattern ->
            val refNum = tryExtractWithPattern(body, pattern)
            if (refNum != null) return refNum
        }

        // Fall back to generic patterns
        rules.fallbackPatterns.referenceNumber?.forEach { pattern ->
            val refNum = tryExtractWithPattern(body, pattern)
            if (refNum != null) return refNum
        }

        return null
    }

    /**
     * Try to extract value using a regex pattern
     */
    private fun tryExtractWithPattern(body: String, pattern: String): String? {
        return try {
            val regex = ruleLoader.getCompiledRegex(pattern)
            regex.find(body)?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            logger.warn("tryExtractWithPattern", "Pattern match failed: $pattern")
            null
        }
    }

    /**
     * Clean merchant name (remove extra spaces, special chars)
     */
    private fun cleanMerchantName(merchant: String): String {
        val cleaned = MerchantNameCleaner.clean(merchant)
        logger.debug("cleanMerchantName","$cleaned actual merchant name $merchant")
        return cleaned
    }

    /**
     * Try to parse date string with multiple formats
     * Creates new SimpleDateFormat instances for thread safety
     */
    private fun tryParseDate(dateStr: String): Date? {
        // Calendar for 2-digit year interpretation
        val calendar = java.util.Calendar.getInstance()
        calendar.set(2000, 0, 1) // Jan 1, 2000
        val yearStartDate = calendar.time

        // Title-case alpha months ("04-JUL-25" -> "04-Jul-25") so SimpleDateFormat
        // matches them reliably, and normalize "/" or space separators to "-"
        val normalized = dateStr.replace(
            Regex("(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)")
        ) { m -> m.value.lowercase().replaceFirstChar { it.uppercase() } }
        val candidates = if (normalized == dateStr) listOf(dateStr) else listOf(dateStr, normalized)

        candidates.forEach { candidate ->
            DATE_FORMATS.forEach { pattern ->
                try {
                    // Create new SimpleDateFormat for thread safety
                    val format = SimpleDateFormat(pattern, Locale.ENGLISH)
                    format.isLenient = false
                    // Set 2-digit year start to interpret "yy" as 2000-2099
                    format.set2DigitYearStart(yearStartDate)
                    return format.parse(candidate)
                } catch (e: Exception) {
                    // Try next format
                }
            }
        }
        return null
    }

    /**
     * Create TransactionEntity from parsed data
     */
    private fun createTransactionEntity(
        sender: String,
        body: String,
        timestamp: Long,
        amount: String,
        merchant: String?,
        date: Date?,
        transactionType: String?,
        bankName: String?,
        referenceNumber: String?
    ): TransactionEntity {
        val cleanAmount = amount.replace(",", "").toDoubleOrNull() ?: 0.0
        val merchantName = merchant ?: "Unknown Merchant"
        // FIX: Use spaces instead of underscores to match repository normalization
        val normalizedMerchant = merchantName.uppercase().replace(Regex("\\s+"), " ").trim()
        val now = Date()

        // FIX: Combine parsed date (from SMS) with current system time for accurate timestamp
        // - Date from SMS body: More accurate day/month/year (transaction date in SMS)
        // - Time from system: Current time when processing (simpler and consistent)
        val transactionDateTime = if (date != null) {
            // Use parsed date (day/month/year) from SMS
            val calendar = java.util.Calendar.getInstance()
            calendar.time = date  // Set to parsed date (has 00:00:00 time)

            // Get current system time
            val currentTime = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, currentTime.get(java.util.Calendar.HOUR_OF_DAY))
            calendar.set(java.util.Calendar.MINUTE, currentTime.get(java.util.Calendar.MINUTE))
            calendar.set(java.util.Calendar.SECOND, currentTime.get(java.util.Calendar.SECOND))
            calendar.set(java.util.Calendar.MILLISECOND, currentTime.get(java.util.Calendar.MILLISECOND))
            calendar.time
        } else {
            // Fallback: Use current system time if no date parsed from SMS
            Date()
        }

        return TransactionEntity(
            id = 0, // Will be auto-generated
            smsId = TransactionEntity.generateSmsId(sender, body, timestamp, referenceNumber),
            amount = cleanAmount,
            rawMerchant = merchantName,
            normalizedMerchant = normalizedMerchant,
            categoryId = 1L,  // Default to "Other" - will be set properly when merchant is created/link
            bankName = bankName ?: "Unknown Bank",
            transactionDate = transactionDateTime,
            rawSmsBody = body,
            confidenceScore = 0.0f, // Will be set by caller
            isDebit = transactionType?.lowercase()?.contains("credit") != true,
            referenceNumber = referenceNumber,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Result of SMS parsing
     */
    sealed class ParseResult {
        data class Success(
            val transaction: TransactionEntity,
            val confidence: ConfidenceScore
        ) : ParseResult()

        data class Failed(val reason: String) : ParseResult()
    }
}
