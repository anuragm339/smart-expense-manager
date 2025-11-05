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
            "dd-MM-yy",      // Try 2-digit patterns first
            "dd/MM/yy",
            "dd.MM.yy",
            "dd-MM-yyyy",    // Then 4-digit patterns
            "dd/MM/yyyy",
            "dd MMM yyyy"
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

            // 1. Try to match sender to a bank
            val bankRule = findMatchingBank(sender, rules)
            val senderMatched = bankRule != null

            // 2. Extract transaction fields
            val amount = extractAmount(body, bankRule, rules)
            val merchant = extractMerchant(body, bankRule, rules)
            val date = extractDate(body, bankRule, timestamp)
            val transactionType = extractTransactionType(body, bankRule, rules)
            val referenceNumber = extractReferenceNumber(body, bankRule, rules)

            // 3. Validate required fields (HARD REQUIREMENTS)
            if (amount == null) {
                logger.warn("parseSMS", "No amount found in SMS: ${body.take(50)}...")
                return@withContext ParseResult.Failed("Amount not found")
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
                extractedDate = date?.toString(),
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
    private fun extractDate(
        body: String,
        bankRule: BankRule?,
        defaultTimestamp: Long
    ): Date {
        // Try bank-specific date patterns
        bankRule?.patterns?.date?.forEach { pattern ->
            val dateStr = tryExtractWithPattern(body, pattern)
            if (dateStr != null) {
                val parsedDate = tryParseDate(dateStr)
                if (parsedDate != null) return parsedDate
            }
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
        return merchant
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^A-Za-z0-9\\s&'-]"), "")
            .trim()
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

        DATE_FORMATS.forEach { pattern ->
            try {
                // Create new SimpleDateFormat for thread safety
                val format = SimpleDateFormat(pattern, Locale.ENGLISH)
                format.isLenient = false
                // Set 2-digit year start to interpret "yy" as 2000-2099
                format.set2DigitYearStart(yearStartDate)
                return format.parse(dateStr)
            } catch (e: Exception) {
                // Try next format
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
        val normalizedMerchant = merchantName.uppercase().replace(Regex("\\s+"), "_")
        val now = Date()

        return TransactionEntity(
            id = 0, // Will be auto-generated
            smsId = TransactionEntity.generateSmsId(sender, body, timestamp, referenceNumber),
            amount = cleanAmount,
            rawMerchant = merchantName,
            normalizedMerchant = normalizedMerchant,
            categoryId = 1L,  // Default to "Other" - will be set properly when merchant is created/linked
            bankName = bankName ?: "Unknown Bank",
            transactionDate = date ?: Date(timestamp),
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
