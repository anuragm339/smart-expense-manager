package com.smartexpenseai.app.parsing.models

import com.google.gson.annotations.SerializedName

/**
 * Data schema for merchant categorization rules loaded from merchant_rules.json
 * Similar structure to BankRulesSchema but specifically for merchant-to-category mapping
 */
data class MerchantRulesConfig(
    @SerializedName("version")
    val version: Int,

    @SerializedName("last_updated")
    val lastUpdated: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("categories")
    val categories: List<MerchantCategoryRule>,

    @SerializedName("fallback_category")
    val fallbackCategory: String = "Other"
)

/**
 * Represents a single category with its matching patterns
 */
data class MerchantCategoryRule(
    @SerializedName("name")
    val name: String,

    @SerializedName("emoji")
    val emoji: String,

    @SerializedName("color")
    val color: String,

    @SerializedName("priority")
    val priority: Int = 1,

    @SerializedName("patterns")
    val patterns: List<String>
) {
    /**
     * Compiled regex patterns for efficient matching
     * Lazily compiled on first use
     */
    @Transient
    private var compiledPatterns: List<Regex>? = null

    /**
     * Get or compile regex patterns
     */
    fun getCompiledPatterns(): List<Regex> {
        if (compiledPatterns == null) {
            compiledPatterns = patterns.map { pattern ->
                try {
                    Regex(pattern, RegexOption.IGNORE_CASE)
                } catch (e: Exception) {
                    // If regex compilation fails, treat as literal string
                    Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
                }
            }
        }
        return compiledPatterns!!
    }

    /**
     * Check if a merchant name matches any pattern in this category
     */
    fun matches(merchantName: String): Boolean {
        val normalizedMerchant = merchantName.uppercase().trim()

        return getCompiledPatterns().any { regex ->
            try {
                regex.containsMatchIn(normalizedMerchant)
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * Result of merchant categorization with metadata
 */
data class CategorizationResult(
    val categoryName: String,
    val emoji: String,
    val color: String,
    val matchedPattern: String? = null,
    val confidence: Int = 100,
    val isFallback: Boolean = false
)
