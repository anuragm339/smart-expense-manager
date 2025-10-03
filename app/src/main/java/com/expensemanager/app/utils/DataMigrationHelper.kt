package com.expensemanager.app.utils


import android.content.Context
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.data.models.Transaction
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.data.storage.TransactionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to migrate data between old TransactionStorage (SharedPreferences) 
 * and new ExpenseRepository (Room database)
 */
@Singleton
class DataMigrationHelper @Inject constructor(
    private val context: Context,
    private val expenseRepository: ExpenseRepository
) {
    
    companion object {
        private const val TAG = "DataMigrationHelper"
        private const val MIGRATION_PREF_KEY = "data_migration_completed"
    }
    
    private val transactionStorage = TransactionStorage(context)
    
    /**
     * Migrate data from TransactionStorage to Room database
     * This fixes the issue where SMS data is stored in SharedPreferences 
     * but Dashboard/Categories read from Room database
     */
    suspend fun migrateTransactionStorageToRoom(): Int = withContext(Dispatchers.IO) {
        try {
            Timber.tag(TAG).d("[MIGRATION] Starting data migration from TransactionStorage to Room...")
            
            // Check if migration already completed
            val prefs = context.getSharedPreferences("app_migration", Context.MODE_PRIVATE)
            if (prefs.getBoolean(MIGRATION_PREF_KEY, false)) {
                Timber.tag(TAG).d("[SKIP] Migration already completed previously")
                return@withContext 0
            }
            
            // Load all transactions from SharedPreferences storage
            val legacyTransactions = transactionStorage.loadTransactions()
            Timber.tag(TAG).d("[SOURCE] Found ${legacyTransactions.size} transactions in TransactionStorage (SharedPreferences)")
            
            if (legacyTransactions.isEmpty()) {
                Timber.tag(TAG).d("[EMPTY] No legacy transactions to migrate")
                // Mark migration as complete even if no data
                prefs.edit().putBoolean(MIGRATION_PREF_KEY, true).apply()
                return@withContext 0
            }
            
            var migratedCount = 0
            var duplicateCount = 0
            val migrationErrors = mutableListOf<String>()
            
            // Convert and insert each transaction
            for (transaction in legacyTransactions) {
                try {
                    Timber.tag(TAG).d("[PROCESS] Migrating transaction: ${transaction.merchant} - ₹${transaction.amount} (${Date(transaction.date)})")
                    
                    // Check if transaction already exists in Room database
                    val smsId = TransactionEntity.generateSmsId("LEGACY", transaction.rawSMS, transaction.date)
                    val existingTransaction = expenseRepository.getTransactionBySmsId(smsId)
                    
                    if (existingTransaction != null) {
                        duplicateCount++
                        Timber.tag(TAG).d("[DUPLICATE] Transaction already exists in Room: ${transaction.merchant}")
                        continue
                    }
                    
                    // Convert to TransactionEntity
                    val transactionEntity = convertLegacyTransactionToEntity(transaction)
                    
                    // Insert into Room database
                    val insertedId = expenseRepository.insertTransaction(transactionEntity)
                    
                    if (insertedId > 0) {
                        migratedCount++
                        Timber.tag(TAG).d("[SUCCESS] Migrated transaction ID $insertedId: ${transaction.merchant} - ₹${transaction.amount}")
                    } else {
                        migrationErrors.add("Failed to insert: ${transaction.merchant} - ₹${transaction.amount}")
                    }
                    
                } catch (e: Exception) {
                    val errorMessage = "Error migrating ${transaction.merchant}: ${e.message}"
                    migrationErrors.add(errorMessage)
                    Timber.tag(TAG).e(e, "[ERROR] $errorMessage")
                }
            }
            
            // Ensure merchants and categories are properly set up
            ensureDefaultCategories()
            
            // Mark migration as completed
            prefs.edit().putBoolean(MIGRATION_PREF_KEY, true).apply()
            
            // Log final results
            Timber.tag(TAG).d("[STATS] Migration completed:")
            Timber.tag(TAG).d("  Migrated: $migratedCount transactions")
            Timber.tag(TAG).d("  Duplicates skipped: $duplicateCount")
            Timber.tag(TAG).d("  Errors: ${migrationErrors.size}")
            
            if (migrationErrors.isNotEmpty()) {
                Timber.tag(TAG).w("[ERRORS] Migration errors encountered:")
                migrationErrors.forEach { error ->
                    Timber.tag(TAG).w("  • $error")
                }
            }
            
            Timber.tag(TAG).d("[SUCCESS] Data migration completed successfully")
            
            return@withContext migratedCount
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "[CRITICAL] Data migration failed catastrophically")
            throw e
        }
    }
    
    /**
     * Convert legacy Transaction to TransactionEntity for Room database
     */
    private suspend fun convertLegacyTransactionToEntity(transaction: Transaction): TransactionEntity {
        // Normalize merchant name
        val normalizedMerchant = transaction.merchant.uppercase()
            .replace(Regex("[*#@\\-_]+.*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        // Ensure merchant exists in database
        ensureMerchantExists(normalizedMerchant, transaction.merchant)
        
        // Generate SMS ID for compatibility
        val smsId = TransactionEntity.generateSmsId("LEGACY", transaction.rawSMS, transaction.date)
        
        return TransactionEntity(
            smsId = smsId,
            amount = transaction.amount,
            rawMerchant = transaction.merchant,
            normalizedMerchant = normalizedMerchant,
            bankName = transaction.bankName,
            transactionDate = Date(transaction.date), // Convert timestamp to Date
            rawSmsBody = transaction.rawSMS,
            confidenceScore = transaction.confidence,
            isDebit = transaction.transactionType.name == "DEBIT",
            createdAt = Date(transaction.createdAt),
            updatedAt = Date()
        )
    }
    
    /**
     * Ensure merchant exists in database with proper category
     */
    private suspend fun ensureMerchantExists(normalizedName: String, displayName: String) {
        val existingMerchant = expenseRepository.getMerchantByNormalizedName(normalizedName)
        if (existingMerchant != null) {
            return // Merchant already exists
        }
        
        // Determine category for this merchant
        val categoryName = categorizeMerchant(displayName)
        val category = expenseRepository.getCategoryByName(categoryName) 
            ?: expenseRepository.getCategoryByName("Other")!!
        
        // Create new merchant
        val merchantEntity = com.expensemanager.app.data.entities.MerchantEntity(
            normalizedName = normalizedName,
            displayName = displayName,
            categoryId = category.id,
            isUserDefined = false,
            createdAt = Date()
        )
        
        expenseRepository.insertMerchant(merchantEntity)
        Timber.tag(TAG).d("[MERCHANT] Created merchant: $displayName -> $categoryName")
    }
    
    /**
     * Smart merchant categorization
     */
    private fun categorizeMerchant(merchantName: String): String {
        val nameUpper = merchantName.uppercase()
        
        return when {
            // Food & Dining
            nameUpper.contains("SWIGGY") || nameUpper.contains("ZOMATO") || 
            nameUpper.contains("DOMINOES") || nameUpper.contains("PIZZA") ||
            nameUpper.contains("MCDONALD") || nameUpper.contains("KFC") ||
            nameUpper.contains("RESTAURANT") || nameUpper.contains("CAFE") ||
            nameUpper.contains("FOOD") || nameUpper.contains("DINING") ||
            nameUpper.contains("AKSHAYAKALPA") -> "Food & Dining"
            
            // Transportation
            nameUpper.contains("UBER") || nameUpper.contains("OLA") ||
            nameUpper.contains("TAXI") || nameUpper.contains("METRO") ||
            nameUpper.contains("BUS") || nameUpper.contains("TRANSPORT") -> "Transportation"
            
            // Groceries
            nameUpper.contains("BIGBAZAAR") || nameUpper.contains("DMART") ||
            nameUpper.contains("RELIANCE") || nameUpper.contains("GROCERY") ||
            nameUpper.contains("SUPERMARKET") || nameUpper.contains("FRESH") ||
            nameUpper.contains("MART") -> "Groceries"
            
            // Healthcare  
            nameUpper.contains("HOSPITAL") || nameUpper.contains("CLINIC") ||
            nameUpper.contains("PHARMACY") || nameUpper.contains("MEDICAL") ||
            nameUpper.contains("HEALTH") || nameUpper.contains("DOCTOR") -> "Healthcare"
            
            // Entertainment
            nameUpper.contains("MOVIE") || nameUpper.contains("CINEMA") ||
            nameUpper.contains("THEATRE") || nameUpper.contains("GAME") ||
            nameUpper.contains("ENTERTAINMENT") || nameUpper.contains("NETFLIX") ||
            nameUpper.contains("SPOTIFY") -> "Entertainment"
            
            // Shopping
            nameUpper.contains("AMAZON") || nameUpper.contains("FLIPKART") ||
            nameUpper.contains("MYNTRA") || nameUpper.contains("AJIO") ||
            nameUpper.contains("SHOPPING") || nameUpper.contains("STORE") -> "Shopping"
            
            // Utilities
            nameUpper.contains("ELECTRICITY") || nameUpper.contains("WATER") ||
            nameUpper.contains("GAS") || nameUpper.contains("INTERNET") ||
            nameUpper.contains("MOBILE") || nameUpper.contains("RECHARGE") -> "Utilities"
            
            else -> "Other"
        }
    }
    
    /**
     * Ensure default categories exist in database
     */
    private suspend fun ensureDefaultCategories() {
        val defaultCategories = listOf(
            "Food & Dining" to "#FF9800",
            "Transportation" to "#2196F3", 
            "Groceries" to "#4CAF50",
            "Healthcare" to "#F44336",
            "Entertainment" to "#9C27B0",
            "Shopping" to "#E91E63",
            "Utilities" to "#607D8B",
            "Other" to "#9E9E9E"
        )
        
        for ((name, color) in defaultCategories) {
            val existingCategory = expenseRepository.getCategoryByName(name)
            if (existingCategory == null) {
                val categoryEntity = com.expensemanager.app.data.entities.CategoryEntity(
                    name = name,
                    color = color,
                    isSystem = true,
                    createdAt = Date()
                )
                expenseRepository.insertCategory(categoryEntity)
                Timber.tag(TAG).d("[CATEGORY] Created default category: $name")
            }
        }
    }
    
    /**
     * Check if migration is needed
     */
    suspend fun isMigrationNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_migration", Context.MODE_PRIVATE)
            val migrationCompleted = prefs.getBoolean(MIGRATION_PREF_KEY, false)
            
            if (migrationCompleted) {
                return@withContext false
            }
            
            // Check if there's data in TransactionStorage but not in Room
            val legacyCount = transactionStorage.loadTransactions().size
            val roomCount = expenseRepository.getTransactionCount()
            
            val needsMigration = legacyCount > 0 && roomCount < legacyCount
            
            Timber.tag(TAG).d("[CHECK] Migration needed: $needsMigration (Legacy: $legacyCount, Room: $roomCount)")
            
            return@withContext needsMigration
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error checking migration status")
            return@withContext false
        }
    }
    
    /**
     * Force reset migration flag for testing
     */
    suspend fun resetMigrationFlag() {
        val prefs = context.getSharedPreferences("app_migration", Context.MODE_PRIVATE)
        prefs.edit().remove(MIGRATION_PREF_KEY).apply()
        Timber.tag(TAG).d("[RESET] Migration flag reset for testing")
    }
    
    /**
     * Get migration statistics
     */
    suspend fun getMigrationStats(): MigrationStats = withContext(Dispatchers.IO) {
        val legacyCount = transactionStorage.loadTransactions().size
        val roomCount = expenseRepository.getTransactionCount()
        val prefs = context.getSharedPreferences("app_migration", Context.MODE_PRIVATE)
        val migrationCompleted = prefs.getBoolean(MIGRATION_PREF_KEY, false)
        
        return@withContext MigrationStats(
            legacyTransactions = legacyCount,
            roomTransactions = roomCount,
            migrationCompleted = migrationCompleted,
            needsMigration = legacyCount > 0 && roomCount < legacyCount && !migrationCompleted
        )
    }
}

/**
 * Data class for migration statistics
 */
data class MigrationStats(
    val legacyTransactions: Int,
    val roomTransactions: Int,
    val migrationCompleted: Boolean,
    val needsMigration: Boolean
) {
    override fun toString(): String {
        return buildString {
            appendLine("Migration Statistics:")
            appendLine("📦 Legacy Storage: $legacyTransactions transactions")
            appendLine("🗃️ Room Database: $roomTransactions transactions")
            appendLine("✅ Migration Status: ${if (migrationCompleted) "Completed" else "Pending"}")
            appendLine("🔄 Needs Migration: $needsMigration")
        }
    }
}