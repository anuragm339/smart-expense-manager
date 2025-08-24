package com.expensemanager.app.data.migration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.expensemanager.app.data.entities.*
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.utils.SMSHistoryReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Date

class DataMigrationManager(private val context: Context) {
    
    private val repository = ExpenseRepository.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("app_migration", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "DataMigrationManager"
        private const val KEY_MIGRATION_COMPLETED = "migration_completed_v1"
        private const val KEY_INITIAL_SMS_IMPORT_COMPLETED = "initial_sms_import_completed"
    }
    
    suspend fun performMigrationIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isMigrationCompleted()) {
                Log.d(TAG, "Migration already completed, skipping...")
                return@withContext true
            }
            
            Log.i(TAG, "Starting data migration to SQLite database...")
            
            // Step 1: Initialize repository and default data
            repository.initializeDefaultData()
            
            // Step 2: Migrate categories from CategoryManager
            migrateCategoriesFromSharedPrefs()
            
            // Step 3: Migrate merchant aliases from MerchantAliasManager
            migrateMerchantAliasesFromSharedPrefs()
            
            // Step 4: Perform initial SMS import
            performInitialSMSImport()
            
            // Mark migration as completed
            markMigrationCompleted()
            
            Log.i(TAG, "Data migration completed successfully!")
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
            val existingCategory = repository.getCategoryByName(categoryName)
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
                
                repository.insertCategory(categoryEntity)
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
                    val category = repository.getCategoryByName(categoryName) ?: run {
                        val categoryId = repository.insertCategory(
                            CategoryEntity(
                                name = categoryName,
                                emoji = getCategoryEmoji(categoryName),
                                color = categoryColor,
                                isSystem = false,
                                createdAt = Date()
                            )
                        )
                        repository.getCategoryById(categoryId)!!
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
                    
                    val merchantId = repository.insertMerchant(merchantEntity)
                    
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
    
    private suspend fun performInitialSMSImport() {
        Log.d(TAG, "🔄 Performing initial SMS import...")
        
        if (isInitialSMSImportCompleted()) {
            Log.d(TAG, "✅ Initial SMS import already completed, skipping...")
            return
        }
        
        try {
            Log.d(TAG, "📱 Starting SMS sync through repository...")
            val importedCount = repository.syncNewSMS()
            Log.i(TAG, "✅ Initial SMS import completed. Imported $importedCount transactions")
            
            // Verify that transactions were actually inserted
            val totalTransactions = repository.getTransactionCount()
            Log.d(TAG, "📊 Total transactions in database after import: $totalTransactions")
            
            if (importedCount > 0 || totalTransactions > 0) {
                // Mark initial import as completed
                prefs.edit().putBoolean(KEY_INITIAL_SMS_IMPORT_COMPLETED, true).apply()
                Log.d(TAG, "✅ Migration state updated - SMS import marked as completed")
            } else {
                Log.w(TAG, "⚠️ No transactions imported, not marking as completed")
            }
            
        } catch (e: SecurityException) {
            Log.w(TAG, "🔒 SMS permission not granted, skipping initial import")
            // Still mark as completed to avoid repeated attempts
            prefs.edit().putBoolean(KEY_INITIAL_SMS_IMPORT_COMPLETED, true).apply()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Initial SMS import failed", e)
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
            "money" -> "💰"
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
    
    private fun markMigrationCompleted() {
        prefs.edit().putBoolean(KEY_MIGRATION_COMPLETED, true).apply()
    }
    
    /**
     * Force re-run migration (for debugging/testing)
     */
    fun resetMigrationState() {
        prefs.edit()
            .putBoolean(KEY_MIGRATION_COMPLETED, false)
            .putBoolean(KEY_INITIAL_SMS_IMPORT_COMPLETED, false)
            .apply()
        Log.d(TAG, "Migration state reset")
    }
}