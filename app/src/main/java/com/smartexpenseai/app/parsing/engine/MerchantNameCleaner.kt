package com.smartexpenseai.app.parsing.engine

/**
 * Normalizes a raw extracted merchant string into a short, stable display name.
 *
 * A greedy "(?:at|to) <name>" pattern can swallow the SMS tail - dates,
 * reference/phone numbers and fraud boilerplate - into the merchant name.
 * Because the app keys grouping and delete/exclude on the normalized name,
 * that volatile text made every message look like a distinct merchant, so
 * duplicates never grouped and a deleted merchant never stayed deleted.
 *
 * Shared by [UnifiedSMSParser] (parse time) and the one-time re-normalization
 * migration so both produce identical names.
 */
object MerchantNameCleaner {

    // Marks where a merchant name ends and SMS noise begins: numeric dates
    // (03 07 26, 03/07/2026), reference/transaction labels, phone or ref digit
    // runs (5+ digits), and block/fraud boilerplate.
    private val NOISE_REGEX = Regex(
        "\\b(?:" +
            "\\d{1,2}[\\s/-]\\d{1,2}[\\s/-]\\d{2,4}" +          // dates
            "|ref(?:erence)?(?:\\s*(?:no|number))?\\b" +        // Ref / Reference No
            "|txn(?:\\s*(?:id|no))?\\b" +                       // Txn Id / Txn No
            "|\\d{5,}" +                                        // ref / phone digit runs
            "|not\\s+you" +
            "|sms\\s+block" +
            "|call\\s+\\d" +
            "|upi\\s+to" +
            // Prepositions that trail the merchant into channel/source noise, e.g.
            // "NETFLIX via UPI", "DOMINOS from Paytm", "MYNTRA using card".
            "|via\\b|from\\b|using\\b|thru\\b" +
        ")",
        RegexOption.IGNORE_CASE
    )

    fun clean(merchant: String): String {
        var collapsed = merchant.trim().replace(Regex("\\s+"), " ")

        // UPI VPAs ("merchant@ybl") must keep their @ and dots or they collapse
        // into unreadable strings; return them as-is minus stray symbols.
        if (collapsed.contains("@")) {
            return collapsed.replace(Regex("[^A-Za-z0-9.@_\\s-]"), "").trim()
        }

        // Drop the Indian "M/S." (Messrs) business prefix so "M/S.HEALONEST" groups
        // as "HEALONEST", not "MSHEALONEST". Requires the slash so real names starting
        // with "MS" (e.g. "MSN") are left alone.
        collapsed = collapsed.replace(Regex("^\\s*M\\s*/\\s*S\\.?\\s*", RegexOption.IGNORE_CASE), "")

        // Drop a leading account reference ("A/c XX123", "Ac No 1234") — common in
        // credit SMS ("credited to A/c XX123 by SALARY"), where it isn't a merchant.
        collapsed = collapsed.replace(Regex("(?i)^a\\s*/?\\s*c\\s*(?:no\\.?)?\\s*"), "")

        // Cut the name at the first noise token so repeated messages share one name.
        NOISE_REGEX.find(collapsed)?.let {
            collapsed = collapsed.substring(0, it.range.first)
        }

        val result = collapsed.replace(Regex("[^A-Za-z0-9\\s&'-]"), "").trim()
        // An account-number remnant ("XX123", "1234") is never a merchant name.
        return if (result.matches(Regex("(?i)x{2,}\\d*")) || result.matches(Regex("\\d+"))) "" else result
    }
}
