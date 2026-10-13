package com.smartexpenseai.app.parsing

/**
 * The payment rail a transaction was made over, derived from the raw bank SMS.
 *
 * This is computed on demand from the SMS text (see MessageItem.paymentMethod)
 * rather than stored on the transaction. That keeps it consistent with the rest
 * of the Messages filtering pipeline, which already operates in-memory, and means
 * it works for all historical transactions without a schema migration/backfill.
 *
 * Detection order matters: the first rail whose markers appear in the SMS wins.
 * NetBanking (IMPS/NEFT/RTGS) and UPI carry the most unambiguous keywords, so they
 * are checked before the broader "card" keyword.
 */
enum class PaymentMethod(val displayName: String) {
    UPI("UPI"),
    CARD("Card"),
    NETBANKING("NetBanking"),
    WALLET("Wallet"),
    OTHER("Other");

    companion object {
        private val NETBANKING_REGEX = Regex(
            "\\b(?:imps|neft|rtgs|net\\s?banking|internet\\s?banking)\\b",
            RegexOption.IGNORE_CASE
        )

        // Explicit UPI/VPA keywords, or a bank UPI handle (merchant@okhdfcbank etc.)
        private val UPI_REGEX = Regex(
            "\\b(?:upi|vpa)\\b",
            RegexOption.IGNORE_CASE
        )

        // A VPA looks like "name@handle" where the handle is a bare word (no dotted
        // domain). Emails ("care@hdfcbank.com") have a dot after the @ and are excluded.
        private val VPA_REGEX = Regex(
            "[a-z0-9._-]+@[a-z]{2,}\\b",
            RegexOption.IGNORE_CASE
        )

        private val CARD_REGEX = Regex(
            "\\b(?:credit\\s?card|debit\\s?card|card\\b)",
            RegexOption.IGNORE_CASE
        )

        private val WALLET_REGEX = Regex(
            "\\b(?:wallet|amazon\\s?pay\\s?balance|mobikwik|freecharge)\\b",
            RegexOption.IGNORE_CASE
        )

        fun detect(smsBody: String?): PaymentMethod {
            if (smsBody.isNullOrBlank()) return OTHER
            return when {
                NETBANKING_REGEX.containsMatchIn(smsBody) -> NETBANKING
                UPI_REGEX.containsMatchIn(smsBody) || looksLikeVpa(smsBody) -> UPI
                CARD_REGEX.containsMatchIn(smsBody) -> CARD
                WALLET_REGEX.containsMatchIn(smsBody) -> WALLET
                else -> OTHER
            }
        }

        private fun looksLikeVpa(body: String): Boolean =
            VPA_REGEX.findAll(body).any { match ->
                !match.value.substringAfter('@').contains('.')
            }
    }
}
