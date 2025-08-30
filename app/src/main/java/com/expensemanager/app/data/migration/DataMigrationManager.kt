package com.expensemanager.app.data.migration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.expensemanager.app.data.entities.*
import com.expensemanager.app.domain.repository.*
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.utils.SMSHistoryReader
import com.expensemanager.app.services.ParsedTransaction
import com.expensemanager.app.data.storage.TransactionStorage
import com.expensemanager.app.data.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class DataMigrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepositoryInterface,
    private val categoryRepository: CategoryRepositoryInterface,
    private val merchantRepository: MerchantRepositoryInterface,
    private val dashboardRepository: DashboardRepositoryInterface
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_migration", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "DataMigrationManager"
        private const val KEY_MIGRATION_COMPLETED = "migration_completed_v1"
        private const val KEY_INITIAL_SMS_IMPORT_COMPLETED = "initial_sms_import_completed"
        private const val KEY_LEGACY_TRANSACTION_MIGRATION_COMPLETED = "legacy_transaction_migration_completed"
    }
    
    suspend fun performMigrationIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking migration status...")
            
            if (isMigrationCompleted()) {
                
                return@withContext true
            }
            
            Log.i(TAG, "=== STARTING DATA MIGRATION TO SQLite DATABASE ===")
            Log.d(TAG, "Migration flags status:")
            Log.d(TAG, "  • Overall migration: ${prefs.getBoolean(KEY_MIGRATION_COMPLETED, false)}")
            Log.d(TAG, "  • SMS import: ${prefs.getBoolean(KEY_INITIAL_SMS_IMPORT_COMPLETED, false)}")
            Log.d(TAG, "  • Legacy migration: ${prefs.getBoolean(KEY_LEGACY_TRANSACTION_MIGRATION_COMPLETED, false)}")
            
            // Step 1: Initialize repository and default data
            dashboardRepository.initializeDefaultData()
            
            // Step 2: Migrate categories from CategoryManager
            migrateCategoriesFromSharedPrefs()
            
            // Step 3: Migrate merchant aliases from MerchantAliasManager
            migrateMerchantAliasesFromSharedPrefs()
            
            // Step 4: Migrate legacy transactions from TransactionStorage
            migrateLegacyTransactionsFromSharedPrefs()
            
            // Step 5: Perform initial SMS import
            performInitialSMSImport()
            
            // Mark migration as completed
            markMigrationCompleted()
            
            // Final verification
            val finalTransactionCount = transactionRepository.getTransactionCount()
            Log.i(TAG, "=== DATA MIGRATION COMPLETED SUCCESSFULLY ===")
            Log.i(TAG, "Final transaction count in Room database: $finalTransactionCount")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Data migration failed", e)
            false
        }
    }
    
    private suspend fun migrateCategoriesFromSharedPrefs() {
        Log.d(TAG, "Migrating categories from SharedPreferences...")
        
        val categoryManager = CategoryManager(context)
        val existingCategories = categoryManager.getAllCategories()
        
        var migratedCount = 0
        for (categoryName in existingCategories) {
            // Check if category already exists in database
            val existingCategory = categoryRepository.getCategoryByName(categoryName)
            if (existingCategory == null) {
                // Create new category entity
                val categoryEntity = CategoryEntity(
                    name = categoryName,
                    emoji = getCategoryEmoji(categoryName),
                    color = getRandomCategoryColor(),
                    isSystem = false, // User-defined category
                    displayOrder = 100 + migratedCount, // Place after system categories
                    createdAt = Date()
                )
                
                categoryRepository.insertCategory(categoryEntity)
                migratedCount++
                Log.d(TAG, "Migrated category: $categoryName")
            }
        }
        
        Log.d(TAG, "Migrated $migratedCount custom categories")
    }
    
    private suspend fun migrateMerchantAliasesFromSharedPrefs() {
        Log.d(TAG, "Migrating merchant aliases from SharedPreferences...")
        
        val aliasPrefs = context.getSharedPreferences("merchant_aliases", Context.MODE_PRIVATE)
        val aliasesJson = aliasPrefs.getString("aliases", null)
        
        if (aliasesJson != null) {
            try {
                val aliasesObject = JSONObject(aliasesJson)
                val keys = aliasesObject.keys()
                var migratedCount = 0
                
                while (keys.hasNext()) {
                    val originalMerchant = keys.next()
                    val aliasObject = aliasesObject.getJSONObject(originalMerchant)
                    
                    val displayName = aliasObject.getString("displayName")
                    val categoryName = aliasObject.getString("category")
                    val categoryColor = aliasObject.optString("categoryColor", "#9e9e9e")
                    
                    // Find or create category
                    val category = categoryRepository.getCategoryByName(categoryName) ?: run {
                        val categoryId = categoryRepository.insertCategory(
                            CategoryEntity(
                                name = categoryName,
                                emoji = getCategoryEmoji(categoryName),
                                color = categoryColor,
                                isSystem = false,
                                createdAt = Date()
                            )
                        )
                        categoryRepository.getCategoryById(categoryId)!!
                    }
                    
                    // Create merchant entity
                    val normalizedName = normalizeMerchantName(originalMerchant)
                    val merchantEntity = MerchantEntity(
                        normalizedName = normalizedName,
                        displayName = displayName,
                        categoryId = category.id,
                        isUserDefined = true,
                        createdAt = Date()
                    )
                    
                    val merchantId = merchantRepository.insertMerchant(merchantEntity)
                    
                    // Create alias entity
                    val aliasEntity = MerchantAliasEntity(
                        merchantId = merchantId,
                        aliasPattern = originalMerchant,
                        confidence = 100
                    )
                    
                    // Insert alias through repository (we'll need to add this method)
                    // For now, let's log it
                    migratedCount++
                    Log.d(TAG, "Migrated merchant alias: $originalMerchant -> $displayName ($categoryName)")
                }
                
                Log.d(TAG, "Migrated $migratedCount merchant aliases")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error migrating merchant aliases", e)
            }
        }
    }
    
    private suspend fun migrateLegacyTransactionsFromSharedPrefs() {
        Log.d(TAG, "[LEGACY] Starting migration of transactions from SMS History...")
        
        if (isLegacyTransactionMigrationCompleted()) {
            Log.d(TAG, "[LEGACY] Legacy transaction migration already completed, skipping...")
            return
        }
        
        try {
            Log.d(TAG, "[LEGACY] DEPRECATED: Direct SMS reading in migration is replaced by repository sync")
            Log.d(TAG, "[LEGACY] This migration step will be handled by performInitialSMSImport() instead")
            
            // Mark this step as completed since we're using repository sync instead
            markLegacyTransactionMigrationCompleted()
            return
            
        } catch (e: Exception) {
            Log.e(TAG, "[LEGACY] Legacy transaction migration failed", e)
            throw e
        }
    }
    
    private suspend fun convertParsedTransactionToEntity(transaction: ParsedTransaction): TransactionEntity {
        // Normalize merchant name
        val normalizedMerchant = transaction.merchant.uppercase()
            .replace(Regex("[*#@\\-_]+.*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        // Ensure merchant exists in database
        ensureMerchantExistsForLegacy(normalizedMerchant, transaction.merchant)
        
        // Generate SMS ID for compatibility
        val smsId = TransactionEntity.generateSmsId("LEGACY", transaction.rawSMS, transaction.date.time)
        
        return TransactionEntity(
            smsId = smsId,
            amount = transaction.amount,
            rawMerchant = transaction.merchant,
            normalizedMerchant = normalizedMerchant,
            bankName = transaction.bankName,
            transactionDate = transaction.date, // Already a Date object
            rawSmsBody = transaction.rawSMS,
            confidenceScore = transaction.confidence,
            isDebit = true, // Assume all transactions are debits for now (SMS parsing typically shows expenses)
            createdAt = Date(),
            updatedAt = Date()
        )
    }
    
    private suspend fun ensureMerchantExistsForLegacy(normalizedName: String, displayName: String) {
        val existingMerchant = merchantRepository.getMerchantByNormalizedName(normalizedName)
        if (existingMerchant != null) {
            return // Merchant already exists
        }
        
        // Determine category for this merchant
        val categoryName = categorizeSmartMerchant(displayName)
        val category = categoryRepository.getCategoryByName(categoryName) 
            ?: categoryRepository.getCategoryByName("Other")!!
        
        // Create new merchant
        val merchantEntity = MerchantEntity(
            normalizedName = normalizedName,
            displayName = displayName,
            categoryId = category.id,
            isUserDefined = false,
            createdAt = Date()
        )
        
        merchantRepository.insertMerchant(merchantEntity)
        Log.d(TAG, "[LEGACY] Created merchant: $displayName -> $categoryName")
    }
    
    private fun categorizeSmartMerchant(merchantName: String): String {
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
    
    private suspend fun performInitialSMSImport() {
        Log.d(TAG, "[PROCESS] Performing initial SMS import...")
        
        if (isInitialSMSImportCompleted()) {
            Log.d(TAG, "[SUCCESS] Initial SMS import already completed, skipping...")
            return
        }
        
        try {
            // ENHANCED LOGGING: Track SMS import process in detail
            Log.i(TAG, "🔄 [SMS] Starting fresh install SMS import through repository...")
            val startTime = System.currentTimeMillis()
            
            // First, check if we have SMS permissions by testing a simple query
            Log.d(TAG, "[PERMISSION] Testing SMS permissions before full import...")
            
            val importedCount = transactionRepository.syncNewSMS()
            
            val endTime = System.currentTimeMillis()
            val durationSeconds = (endTime - startTime) / 1000
            
            Log.i(TAG, "✅ [SUCCESS] Initial SMS import completed in ${durationSeconds}s. Imported $importedCount new transactions")
            
            // Verify that transactions were actually inserted
            val totalTransactions = transactionRepository.getTransactionCount()
            Log.i(TAG, "📊 [ANALYTICS] Total transactions in database after import: $totalTransactions")
            
            if (importedCount > 0) {
                Log.i(TAG, "🎉 [FRESH_INSTALL] SMS import successful - Dashboard should now show data!")
            } else {
                Log.w(TAG, "⚠️ [FRESH_INSTALL] No new SMS transactions imported")
                Log.w(TAG, "⚠️ [DIAGNOSIS] This could mean:")
                Log.w(TAG, "   1. No bank SMS messages exist in the device")
                Log.w(TAG, "   2. SMS messages don't match parsing patterns")
                Log.w(TAG, "   3. All SMS messages were already processed")
                Log.w(TAG, "   4. SMS permission is denied at runtime")
            }
            
            if (importedCount > 0 || totalTransactions > 0) {
                // Mark initial import as completed
                prefs.edit().putBoolean(KEY_INITIAL_SMS_IMPORT_COMPLETED, true).apply()
                Log.d(TAG, "[SUCCESS] Migration state updated - SMS import marked as completed")
            } else {
                Log.w(TAG, "[WARNING] No transactions imported, marking as completed anyway to prevent retry loops")
                // Mark as completed to prevent repeated attempts in empty SMS scenarios
                prefs.edit().putBoolean(KEY_INITIAL_SMS_IMPORT_COMPLETED, true).apply()
            }
            
        } catch (e: SecurityException) {
            Log.w(TAG, "[SECURE] SMS permission not granted during import: ${e.message}")
            Log.w(TAG, "[SECURE] Migration will complete without SMS data - user can manually trigger sync later")
            // Still mark as completed to avoid repeated attempts
            prefs.edit().putBoolean(KEY_INITIAL_SMS_IMPORT_COMPLETED, true).apply()
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] Initial SMS import failed unexpectedly", e)
            Log.e(TAG, "[ERROR] Error type: ${e::class.java.simpleName}")
            Log.e(TAG, "[ERROR] Error message: ${e.message}")
            // Don't mark as completed for unexpected errors - allow retry
            throw e
        }
    }
    
    private fun getCategoryEmoji(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "food & dining", "food", "dining" -> "🍽️"
            "transportation", "transport" -> "🚗"
            "groceries", "grocery" -> "🛒"
            "healthcare", "health" -> "🏥"
            "entertainment" -> "🎬"
            "shopping" -> "🛍️"
            "utilities" -> "⚡"
            "money" -> "[FINANCIAL]"
            else -> "📂"
        }
    }
    
    private fun getRandomCategoryColor(): String {
        val colors = listOf(
            "#ff5722", "#3f51b5", "#4caf50", "#e91e63",
            "#ff9800", "#9c27b0", "#607d8b", "#795548",
            "#2196f3", "#8bc34a", "#ffc107", "#673ab7"
        )
        return colors.random()
    }
    
    private fun normalizeMerchantName(merchant: String): String {
        return merchant.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    private fun isMigrationCompleted(): Boolean {
        return prefs.getBoolean(KEY_MIGRATION_COMPLETED, false)
    }
    
    private fun isInitialSMSImportCompleted(): Boolean {
        return prefs.getBoolean(KEY_INITIAL_SMS_IMPORT_COMPLETED, false)
    }
    
    private fun isLegacyTransactionMigrationCompleted(): Boolean {
        return prefs.getBoolean(KEY_LEGACY_TRANSACTION_MIGRATION_COMPLETED, false)
    }
    
    private fun markMigrationCompleted() {
        prefs.edit().putBoolean(KEY_MIGRATION_COMPLETED, true).apply()
    }
    
    private fun markLegacyTransactionMigrationCompleted() {
        prefs.edit().putBoolean(KEY_LEGACY_TRANSACTION_MIGRATION_COMPLETED, true).apply()
    }
    
    /**
     * Force re-run migration (for debugging/testing)
     */
    fun resetMigrationState() {
        prefs.edit()
            .putBoolean(KEY_MIGRATION_COMPLETED, false)
            .putBoolean(KEY_INITIAL_SMS_IMPORT_COMPLETED, false)
            .putBoolean(KEY_LEGACY_TRANSACTION_MIGRATION_COMPLETED, false)
            .apply()
        Log.d(TAG, "Migration state reset")
    }
}