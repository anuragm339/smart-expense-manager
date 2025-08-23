package com.expensemanager.app.utils

import android.content.Context
import android.util.Log
import com.expensemanager.app.data.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Helper to migrate exclusion states from SharedPreferences to database
 */
object ExclusionMigrationHelper {
    
    private const val TAG = "ExclusionMigrationHelper"
    
    suspend fun migrateExclusionStatesToDatabase(context: Context, repository: ExpenseRepository) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Starting migration of exclusion states from SharedPreferences to database...")
            
            // Read existing exclusion states from SharedPreferences
            val prefs = context.getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
            val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
            
            if (inclusionStatesJson != null) {
                val inclusionStates = JSONObject(inclusionStatesJson)
                var migratedCount = 0
                
                val keys = inclusionStates.keys()
                while (keys.hasNext()) {
                    val merchantDisplayName = keys.next()
                    val isIncluded = inclusionStates.getBoolean(merchantDisplayName)
                    val isExcluded = !isIncluded // Convert inclusion to exclusion
                    
                    // Convert display name back to normalized name for database lookup
                    val normalizedName = merchantDisplayName.lowercase()
                        .replace(Regex("[^a-zA-Z0-9\\s]"), "")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    
                    try {
                        // Check if merchant exists in database
                        val merchant = repository.getMerchantByNormalizedName(normalizedName)
                        if (merchant != null) {
                            // Update exclusion state in database
                            repository.updateMerchantExclusion(normalizedName, isExcluded)
                            migratedCount++
                            
                            if (isExcluded) {
                                Log.d(TAG, "🚫 Migrated exclusion: $merchantDisplayName -> $normalizedName")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Merchant not found in database: $merchantDisplayName -> $normalizedName")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error migrating merchant: $merchantDisplayName", e)
                    }
                }
                
                Log.d(TAG, "✅ Migration completed: $migratedCount merchants migrated")
                
                // Optionally clear SharedPreferences after successful migration
                // prefs.edit().remove("group_inclusion_states").apply()
                
            } else {
                Log.d(TAG, "ℹ️ No exclusion states found in SharedPreferences")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during exclusion states migration", e)
        }
    }
    
    /**
     * Mark specific merchants as excluded in the database (for large/test transactions)
     */
    suspend fun excludeLargeTransactionMerchants(repository: ExpenseRepository) {
        try {
            Log.d(TAG, "🔧 Marking large transaction merchants as excluded...")
            
            val merchantsToExclude = listOf(
                "myhdfc ac x3300 with hdfc0005493 sent from yono",
                "inr",
                "vishal kumar",
                "chandra shekhar mishra", 
                "deepak prakash srivastav",
                "learning",
                "imps",
                "linked to mobile 8xxxxxx832"
            )
            
            var excludedCount = 0
            for (normalizedName in merchantsToExclude) {
                try {
                    val merchant = repository.getMerchantByNormalizedName(normalizedName)
                    if (merchant != null) {
                        repository.updateMerchantExclusion(normalizedName, true)
                        excludedCount++
                        Log.d(TAG, "🚫 Excluded large transaction merchant: ${merchant.displayName}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error excluding merchant: $normalizedName", e)
                }
            }
            
            Log.d(TAG, "✅ Excluded $excludedCount large transaction merchants")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error excluding large transaction merchants", e)
        }
    }
}