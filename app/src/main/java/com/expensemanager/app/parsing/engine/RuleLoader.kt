package com.expensemanager.app.parsing.engine

import android.content.Context
import com.expensemanager.app.parsing.models.BankRulesSchema
import com.google.gson.Gson
import com.google.gson.JsonParseException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe loader for bank SMS parsing rules with caching
 * Loads rules from assets/bank_rules.json and compiles regex patterns
 */
@Singleton
class RuleLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Thread-safe cache for loaded rules
    private val rulesCache = AtomicReference<BankRulesSchema?>(null)

    // Cache for compiled regex patterns to avoid recompilation
    private val compiledRegexCache = ConcurrentHashMap<String, Regex>()

    // Track if rules have been validated
    private var isValidated = false

    companion object {
        private const val RULES_FILE_NAME = "bank_rules.json"
        private const val SUPPORTED_VERSION = 1
        private const val TAG = "RuleLoader"
    }

    /**
     * Load bank rules from JSON file with caching
     * Thread-safe and uses cached value if available
     */
    suspend fun loadRules(): Result<BankRulesSchema> = withContext(Dispatchers.IO) {
        try {
            // Return cached rules if available and validated
            rulesCache.get()?.let { cached ->
                if (isValidated) {
                    return@withContext Result.success(cached)
                }
            }

            // Load from assets
            val json = context.assets.open(RULES_FILE_NAME).bufferedReader().use { it.readText() }
            val rules = Gson().fromJson(json, BankRulesSchema::class.java)

            // Validate schema
            validateRules(rules)

            // Cache the validated rules
            rulesCache.set(rules)
            isValidated = true

            // Pre-compile commonly used patterns
            preCompilePatterns(rules)

            Result.success(rules)
        } catch (e: IOException) {
            Result.failure(RuleLoadException("Failed to read rules file: ${e.message}", e))
        } catch (e: JsonParseException) {
            Result.failure(RuleLoadException("Invalid JSON format in rules file: ${e.message}", e))
        } catch (e: ValidationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(RuleLoadException("Unexpected error loading rules: ${e.message}", e))
        }
    }

    /**
     * Get or compile a regex pattern with caching
     * Thread-safe using ConcurrentHashMap
     */
    fun getCompiledRegex(pattern: String): Regex {
        return compiledRegexCache.getOrPut(pattern) {
            try {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                throw RegexCompilationException("Failed to compile pattern: $pattern", e)
            }
        }
    }

    /**
     * Clear all caches (useful for testing or force reload)
     */
    fun clearCache() {
        rulesCache.set(null)
        compiledRegexCache.clear()
        isValidated = false
    }

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            rulesLoaded = rulesCache.get() != null,
            compiledPatternsCount = compiledRegexCache.size,
            isValidated = isValidated
        )
    }

    /**
     * Validate the loaded rules schema
     */
    private fun validateRules(rules: BankRulesSchema) {
        // Check version compatibility
        if (rules.version != SUPPORTED_VERSION) {
            throw ValidationException(
                "Unsupported schema version: ${rules.version}. Expected: $SUPPORTED_VERSION"
            )
        }

        // Ensure we have banks
        if (rules.banks.isEmpty()) {
            throw ValidationException("No banks defined in rules file")
        }

        // Validate each bank rule
        rules.banks.forEach { bank ->
            if (bank.code.isBlank()) {
                throw ValidationException("Bank code cannot be blank")
            }
            if (bank.senderPatterns.isEmpty()) {
                throw ValidationException("Bank ${bank.code} has no sender patterns")
            }
            if (bank.patterns.amount.isEmpty()) {
                throw ValidationException("Bank ${bank.code} has no amount patterns")
            }
            if (bank.patterns.merchant.isEmpty()) {
                throw ValidationException("Bank ${bank.code} has no merchant patterns")
            }

            // Validate confidence weights sum to reasonable value
            bank.confidenceWeights?.let { weights ->
                val sum = weights.senderMatch + weights.amountExtraction +
                         weights.merchantExtraction + weights.dateExtraction +
                         weights.referenceNumberExtraction
                if (sum < 0.9f || sum > 1.1f) {
                    throw ValidationException(
                        "Bank ${bank.code} confidence weights sum to $sum (should be ~1.0)"
                    )
                }
            }
        }

        // Validate fallback patterns exist
        if (rules.fallbackPatterns.amount.isEmpty()) {
            throw ValidationException("No fallback amount patterns defined")
        }
        if (rules.fallbackPatterns.merchant.isEmpty()) {
            throw ValidationException("No fallback merchant patterns defined")
        }
    }

    /**
     * Pre-compile commonly used patterns for performance
     */
    private fun preCompilePatterns(rules: BankRulesSchema) {
        // Compile all bank patterns
        rules.banks.forEach { bank ->
            bank.senderPatterns.forEach { getCompiledRegex(it) }
            bank.patterns.amount.forEach { getCompiledRegex(it) }
            bank.patterns.merchant.forEach { getCompiledRegex(it) }
            bank.patterns.date?.forEach { getCompiledRegex(it) }
            bank.patterns.transactionType?.forEach { getCompiledRegex(it) }
        }

        // Compile fallback patterns
        rules.fallbackPatterns.amount.forEach { getCompiledRegex(it) }
        rules.fallbackPatterns.merchant.forEach { getCompiledRegex(it) }
    }

    /**
     * Cache statistics for debugging
     */
    data class CacheStats(
        val rulesLoaded: Boolean,
        val compiledPatternsCount: Int,
        val isValidated: Boolean
    )
}

/**
 * Exception thrown when rules fail to load
 */
class RuleLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when rules fail validation
 */
class ValidationException(message: String) : Exception(message)

/**
 * Exception thrown when regex compilation fails
 */
class RegexCompilationException(message: String, cause: Throwable? = null) : Exception(message, cause)
