package com.smartexpenseai.app.parsing.engine

import com.smartexpenseai.app.parsing.models.BankRule
import com.smartexpenseai.app.parsing.models.ConfidenceScore
import com.smartexpenseai.app.parsing.models.ConfidenceScore.Field
import com.smartexpenseai.app.parsing.models.ConfidenceScore.FieldConfidence
import com.smartexpenseai.app.parsing.models.ConfidenceWeights
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates confidence scores for parsed SMS transactions
 * Scores range from 0.0 (no confidence) to 1.0 (high confidence)
 *
 * Thresholds:
 * - ≥0.85: Auto-accept (high confidence)
 * - 0.50-0.84: Accept but monitor
 * - <0.50: Manual review required
 */
@Singleton
class ConfidenceCalculator @Inject constructor() {

    /**
     * Calculate confidence score for a parsed transaction
     *
     * @param senderMatched Whether the SMS sender matched a known bank pattern
     * @param bankRule The bank rule used (null if fallback)
     * @param extractedAmount The extracted amount value (null if not found)
     * @param extractedMerchant The extracted merchant name (null if not found)
     * @param extractedDate The extracted date (null if not found)
     * @param extractedType The extracted transaction type (null if not found)
     * @param extractedReferenceNumber The extracted reference/transaction number (null if not found)
     * @param smsBody The original SMS body for quality checks
     */
    fun calculate(
        senderMatched: Boolean,
        bankRule: BankRule?,
        extractedAmount: String?,
        extractedMerchant: String?,
        extractedDate: String?,
        extractedType: String?,
        extractedReferenceNumber: String?,
        smsBody: String
    ): ConfidenceScore {
        val weights = bankRule?.confidenceWeights ?: getDefaultWeights()
        val breakdown = mutableMapOf<Field, FieldConfidence>()

        // 1. Sender match score
        val senderScore = if (senderMatched) 1.0f else 0.0f
        breakdown[Field.SENDER] = FieldConfidence(
            extracted = senderMatched,
            score = senderScore,
            value = if (senderMatched) bankRule?.code else null
        )

        // 2. Amount extraction score
        val amountScore = calculateAmountScore(extractedAmount, smsBody)
        breakdown[Field.AMOUNT] = FieldConfidence(
            extracted = extractedAmount != null,
            score = amountScore,
            value = extractedAmount
        )

        // 3. Merchant extraction score
        val merchantScore = calculateMerchantScore(extractedMerchant, smsBody)
        breakdown[Field.MERCHANT] = FieldConfidence(
            extracted = extractedMerchant != null,
            score = merchantScore,
            value = extractedMerchant
        )

        // 4. Date extraction score
        val dateScore = if (extractedDate != null) 1.0f else 0.0f
        breakdown[Field.DATE] = FieldConfidence(
            extracted = extractedDate != null,
            score = dateScore,
            value = extractedDate
        )

        // 5. Transaction type score
        val typeScore = if (extractedType != null) 1.0f else 0.0f
        breakdown[Field.TRANSACTION_TYPE] = FieldConfidence(
            extracted = extractedType != null,
            score = typeScore,
            value = extractedType
        )

        // 6. Reference number score (CRITICAL - required for transaction SMS)
        val refNumberScore = if (extractedReferenceNumber != null) 1.0f else 0.0f
        breakdown[Field.REFERENCE_NUMBER] = FieldConfidence(
            extracted = extractedReferenceNumber != null,
            score = refNumberScore,
            value = extractedReferenceNumber
        )

        // Calculate weighted overall score
        val overallScore = (
            senderScore * weights.senderMatch +
            amountScore * weights.amountExtraction +
            merchantScore * weights.merchantExtraction +
            dateScore * weights.dateExtraction +
            refNumberScore * weights.referenceNumberExtraction
        )

        return ConfidenceScore(
            overall = overallScore.coerceIn(0.0f, 1.0f),
            breakdown = breakdown
        )
    }

    /**
     * Calculate quality score for extracted amount
     */
    private fun calculateAmountScore(amount: String?, smsBody: String): Float {
        if (amount == null) return 0.0f

        var score = 0.5f // Base score for extraction

        // Check if amount is valid format
        val cleanAmount = amount.replace(",", "")
        val amountValue = cleanAmount.toDoubleOrNull()

        if (amountValue == null || amountValue <= 0) {
            return 0.1f // Invalid amount
        }

        // Bonus for decimal precision (e.g., "1234.56")
        if (amount.contains(".")) {
            score += 0.2f
        }

        // Bonus if amount appears early in SMS (likely more accurate)
        val amountPosition = smsBody.indexOf(amount, ignoreCase = true)
        if (amountPosition in 0..50) {
            score += 0.2f
        }

        // Bonus if amount is reasonable (not suspiciously large)
        if (amountValue in 1.0..100000.0) {
            score += 0.1f
        }

        return score.coerceIn(0.0f, 1.0f)
    }

    /**
     * Calculate quality score for extracted merchant
     */
    private fun calculateMerchantScore(merchant: String?, smsBody: String): Float {
        if (merchant == null) return 0.0f

        var score = 0.4f // Base score for extraction

        // Check merchant name quality
        val trimmedMerchant = merchant.trim()

        // Penalize very short merchant names (likely false positives)
        if (trimmedMerchant.length < 3) {
            return 0.1f
        }

        // Bonus for reasonable length (3-50 characters)
        if (trimmedMerchant.length in 3..50) {
            score += 0.2f
        }

        // Bonus if merchant starts with capital letter (proper name)
        if (trimmedMerchant.firstOrNull()?.isUpperCase() == true) {
            score += 0.1f
        }

        // Penalize if merchant contains suspicious promotional patterns
        val suspiciousPatterns = listOf(
            "offer", "apply", "withdraw", "loan", "emi", "click", "http",
            "get instant", "t&c", "limited", "promo", "discount",
            "your", "account", "card", "bank", "available", "balance"
        )
        if (!suspiciousPatterns.any { trimmedMerchant.contains(it, ignoreCase = true) }) {
            score += 0.2f
        } else {
            // Strong penalty for promotional keywords
            score -= 0.3f
        }

        // Bonus if merchant name appears to be a proper business name
        // (contains multiple words or has mixed case)
        if (trimmedMerchant.contains(' ') || trimmedMerchant.any { it.isLowerCase() }) {
            score += 0.1f
        }

        return score.coerceIn(0.0f, 1.0f)
    }

    /**
     * Get default confidence weights when bank rule doesn't specify them
     */
    private fun getDefaultWeights(): ConfidenceWeights {
        return ConfidenceWeights(
            senderMatch = 0.25f,
            amountExtraction = 0.25f,
            merchantExtraction = 0.20f,
            dateExtraction = 0.10f,
            referenceNumberExtraction = 0.20f
        )
    }

    /**
     * Analyze why a score is low (for debugging/logging)
     */
    fun explainLowScore(score: ConfidenceScore): String {
        if (score.overall >= ConfidenceScore.AUTO_ACCEPT_THRESHOLD) {
            return "High confidence score: ${String.format("%.2f", score.overall)}"
        }

        val issues = mutableListOf<String>()

        score.breakdown.forEach { (field, confidence) ->
            if (!confidence.extracted || confidence.score < 0.5f) {
                val reason = when (field) {
                    Field.SENDER -> "Unknown sender"
                    Field.AMOUNT -> if (!confidence.extracted) "Amount not found" else "Low quality amount"
                    Field.MERCHANT -> if (!confidence.extracted) "Merchant not found" else "Suspicious merchant name"
                    Field.DATE -> "Date not found"
                    Field.TRANSACTION_TYPE -> "Transaction type unclear"
                    Field.REFERENCE_NUMBER -> "No transaction/reference number"
                }
                issues.add(reason)
            }
        }

        return if (issues.isEmpty()) {
            "Low confidence (${String.format("%.2f", score.overall)}) - review needed"
        } else {
            "Issues: ${issues.joinToString(", ")}"
        }
    }

    /**
     * Check if a transaction should be auto-accepted
     */
    fun shouldAutoAccept(score: ConfidenceScore): Boolean {
        return score.shouldAutoAccept() &&
               // Additional safety: must have critical fields
               score.breakdown[Field.AMOUNT]?.extracted == true &&
               score.breakdown[Field.MERCHANT]?.extracted == true &&
               score.breakdown[Field.REFERENCE_NUMBER]?.extracted == true
    }

    /**
     * Check if a transaction needs manual review
     */
    fun needsManualReview(score: ConfidenceScore): Boolean {
        return score.needsManualReview() ||
               // Force review if critical fields missing
               score.breakdown[Field.AMOUNT]?.extracted != true ||
               score.breakdown[Field.REFERENCE_NUMBER]?.extracted != true
    }
}
