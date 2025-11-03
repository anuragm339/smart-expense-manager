package com.smartexpenseai.app.utils


import android.content.Context
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Helper to migrate exclusion states from SharedPreferences to database
 */
object ExclusionMigrationHelper {
    
    private const val TAG = "ExclusionMigrationHelper"
    private val logger = StructuredLogger("DataMigrationHelper", "DataMigrationHelper")
    suspend fun migrateExclusionStatesToDatabase(context: Context, repository: ExpenseRepository) = withContext(Dispatchers.IO) {
        try {
            logger.debug("migrateExclusionStatesToDatabase","Starting migration of exclusion states from SharedPreferences to database...")
            
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
                                logger.debug("migrateExclusionStatesToDatabase","Migrated exclusion: $merchantDisplayName -> $normalizedName")
                            }
                        } else {
                            logger.warn("migrateExclusionStatesToDatabase","Merchant not found in database: $merchantDisplayName -> $normalizedName")
                        }
                    } catch (e: Exception) {
                        logger.error("migrateExclusionStatesToDatabase","Error migrating merchant: $merchantDisplayName",e)
                    }
                }
                

                
                // Optionally clear SharedPreferences after successful migration
                // prefs.edit().remove("group_inclusion_states").apply()
                
            } else {

            }
            
        } catch (e: Exception) {
            ExclusionMigrationHelper.logger.error("migrateExclusionStatesToDatabase" ,"Error during exclusion states migration",e)
        }
    }
    
    // REMOVED: excludeLargeTransactionMerchants() method
    // All exclusions should be user-controlled through the Messages screen
    // No automatic system-based exclusions
}