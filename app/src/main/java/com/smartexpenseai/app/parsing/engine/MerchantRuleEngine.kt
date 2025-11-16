package com.smartexpenseai.app.parsing.engine

import android.content.Context
import com.google.gson.Gson
import com.smartexpenseai.app.parsing.models.CategorizationResult
import com.smartexpenseai.app.parsing.models.MerchantRulesConfig
import com.smartexpenseai.app.utils.logging.StructuredLogger
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule-based engine for categorizing merchants using JSON-defined patterns
 *
 * This engine:
 * 1. Loads merchant categorization rules from merchant_rules.json
 * 2. Compiles regex patterns for efficient matching
 * 3. Categorizes merchants based on pattern matching with priority
 * 4. Provides fallback to "Other" category for unmatched merchants
 *
 * Benefits over hardcoded rules:
 * - Easy to modify without code changes
 * - Regex support for advanced patterns
 * - Priority-based matching
 * - Single source of truth
 */
@Singleton
class MerchantRuleEngine @Inject constructor(
    private val context: Context
) {

    private val logger = StructuredLogger("MERCHANT", "MerchantRuleEngine")

    private var rulesConfig: MerchantRulesConfig? = null

    /**
     * Lazy initialization flag
     */
    @Volatile
    private var isInitialized = false

    /**
     * Initialize the rule engine by loading and compiling rules from JSON
     */
    fun initialize() {
        if (isInitialized) {
            return
        }

        synchronized(this) {
            if (isInitialized) {
                return
            }

            try {
                logger.info("initialize", "[INIT] Loading merchant rules from JSON...")

                val inputStream = context.assets.open("merchant_rules.json")
                val reader = InputStreamReader(inputStream)
                val gson = Gson()

                rulesConfig = gson.fromJson(reader, MerchantRulesConfig::class.java)
                reader.close()

                // Sort categories by priority (lower number = higher priority)
                rulesConfig = rulesConfig?.copy(
                    categories = rulesConfig?.categories?.sortedBy { it.priority } ?: emptyList()
                )

                logger.info(
                    "initialize",
                    "[SUCCESS] Loaded ${rulesConfig?.categories?.size ?: 0} categories with " +
                    "${rulesConfig?.categories?.sumOf { it.patterns.size } ?: 0} total patterns"
                )

                // Pre-compile all regex patterns for better performance
                rulesConfig?.categories?.forEach { category ->
                    category.getCompiledPatterns() // Trigger compilation
                    logger.debug(
                        "initialize",
                        "[CATEGORY] ${category.name}: ${category.patterns.size} patterns, priority ${category.priority}"
                    )
                }

                isInitialized = true

            } catch (e: Exception) {
                logger.error(
                    "initialize",
                    "[ERROR] Failed to load merchant rules. Fallback to default categorization.",
                    e
                )
                // Keep isInitialized = false so fallback logic works
            }
        }
    }

    /**
     * Categorize a merchant name using the rule engine
     *
     * @param merchantName The merchant name to categorize (will be normalized)
     * @return CategorizationResult with category name, emoji, color, and metadata
     */
    fun categorize(merchantName: String): CategorizationResult {
        // Ensure rules are loaded
        if (!isInitialized) {
            initialize()
        }

        // If still not initialized (loading failed), use fallback
        if (rulesConfig == null) {
            logger.warn(
                "categorize",
                "[FALLBACK] Rules not loaded, using default categorization for: $merchantName"
            )
            return getDefaultCategorization(merchantName)
        }

        val normalizedMerchant = normalizeMerchantName(merchantName)

        logger.debug(
            "categorize",
            "[MATCH] Categorizing merchant: '$merchantName' (normalized: '$normalizedMerchant')"
        )

        // Try to match against all categories (sorted by priority)
        for (category in rulesConfig!!.categories) {
            if (category.matches(normalizedMerchant)) {
                logger.info(
                    "categorize",
                    "[MATCHED] '${merchantName}' → ${category.name} (emoji: ${category.emoji})"
                )

                return CategorizationResult(
                    categoryName = category.name,
                    emoji = category.emoji,
                    color = category.color,
                    matchedPattern = findMatchedPattern(normalizedMerchant, category.patterns),
                    confidence = 100,
                    isFallback = false
                )
            }
        }

        // No match found - use fallback category
        logger.debug(
            "categorize",
            "[NO_MATCH] No pattern matched for '$merchantName', using fallback: ${rulesConfig!!.fallbackCategory}"
        )

        return CategorizationResult(
            categoryName = rulesConfig!!.fallbackCategory,
            emoji = "📂",
            color = "#607d8b",
            matchedPattern = null,
            confidence = 50,
            isFallback = true
        )
    }

    /**
     * Normalize merchant name for consistent matching
     * Same logic as existing normalization in the codebase
     */
    private fun normalizeMerchantName(merchantName: String): String {
        return merchantName.uppercase()
            .replace(Regex("[*#@\\-_]+.*"), "") // Remove suffixes after special chars
            .replace(Regex("\\s+"), " ") // Normalize spaces
            .trim()
    }

    /**
     * Find which specific pattern matched (for debugging/logging)
     */
    private fun findMatchedPattern(merchantName: String, patterns: List<String>): String? {
        return patterns.find { pattern ->
            try {
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(merchantName)
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Fallback categorization when rules can't be loaded
     * Uses simple hardcoded rules as last resort
     */
    private fun getDefaultCategorization(merchantName: String): CategorizationResult {
        val upper = merchantName.uppercase()

        val category = when {
            upper.contains("SWIGGY") || upper.contains("ZOMATO") || upper.contains("FOOD") ->
                Triple("Food & Dining", "🍽️", "#ff5722")
            upper.contains("UBER") || upper.contains("OLA") || upper.contains("TAXI") ->
                Triple("Transportation", "🚗", "#3f51b5")
            upper.contains("BIGBAZAAR") || upper.contains("DMART") || upper.contains("GROCERY") ->
                Triple("Groceries", "🛒", "#4caf50")
            upper.contains("HOSPITAL") || upper.contains("PHARMACY") || upper.contains("MEDICAL") ->
                Triple("Healthcare", "🏥", "#e91e63")
            upper.contains("MOVIE") || upper.contains("NETFLIX") || upper.contains("CINEMA") ->
                Triple("Entertainment", "🎬", "#9c27b0")
            upper.contains("AMAZON") || upper.contains("FLIPKART") || upper.contains("SHOP") ->
                Triple("Shopping", "🛍️", "#ff9800")
            upper.contains("ELECTRICITY") || upper.contains("INTERNET") || upper.contains("UTILITY") ->
                Triple("Utilities", "⚡", "#607d8b")
            else -> Triple("Other", "📂", "#607d8b")
        }

        return CategorizationResult(
            categoryName = category.first,
            emoji = category.second,
            color = category.third,
            matchedPattern = null,
            confidence = 80,
            isFallback = true
        )
    }

    /**
     * Get all available category names from loaded rules
     */
    fun getAvailableCategories(): List<String> {
        if (!isInitialized) {
            initialize()
        }

        return rulesConfig?.categories?.map { it.name } ?: emptyList()
    }

    /**
     * Get category details (emoji, color) for a category name
     */
    fun getCategoryDetails(categoryName: String): Triple<String, String, String>? {
        if (!isInitialized) {
            initialize()
        }

        val category = rulesConfig?.categories?.find { it.name == categoryName }
        return category?.let { Triple(it.name, it.emoji, it.color) }
    }

    /**
     * Reload rules from JSON (useful for hot-reloading during development)
     */
    fun reload() {
        isInitialized = false
        rulesConfig = null
        initialize()
        logger.info("reload", "[RELOAD] Merchant rules reloaded successfully")
    }

    /**
     * Get rules statistics for debugging
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized,
            "categories_count" to (rulesConfig?.categories?.size ?: 0),
            "total_patterns" to (rulesConfig?.categories?.sumOf { it.patterns.size } ?: 0),
            "version" to (rulesConfig?.version ?: 0),
            "fallback_category" to (rulesConfig?.fallbackCategory ?: "Other")
        )
    }
}
