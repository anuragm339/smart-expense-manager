package com.expensemanager.app.utils


import android.content.Context
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import org.json.JSONObject

/**
 * One-time utility to fix exclusion states for large/test transactions
 */
object ExclusionStatesFixer {
    
    private const val TAG = "ExclusionStatesFixer"
    
    fun fixLargeTransactionExclusions(context: Context) {
        try {
            Timber.tag(TAG).d("[FIX] Fixing exclusion states for large transactions...")
            
            val prefs = context.getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
            val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
            
            val inclusionStates = if (inclusionStatesJson != null) {
                JSONObject(inclusionStatesJson)
            } else {
                JSONObject()
            }
            
            // List of merchants to exclude (set to false)
            // Using the exact display format: first letter capitalized, rest lowercase
            val merchantsToExclude = listOf(
                "Myhdfc ac x3300 with hdfc0005493 sent from yono",
                "Inr",
                "Vishal kumar",
                "Chandra shekhar mishra",
                "Deepak prakash srivastav",
                "Learning",
                "Imps",
                "Linked to mobile 8xxxxxx832"
            )
            
            var changesCount = 0
            for (merchant in merchantsToExclude) {
                val currentValue = if (inclusionStates.has(merchant)) {
                    inclusionStates.getBoolean(merchant)
                } else {
                    true // Default is included
                }
                
                if (currentValue) {
                    inclusionStates.put(merchant, false)
                    changesCount++
                    Timber.tag(TAG).d("🚫 Excluded: $merchant")
                }
            }
            
            if (changesCount > 0) {
                // Save back to preferences
                prefs.edit()
                    .putString("group_inclusion_states", inclusionStates.toString())
                    .apply()
                
                Timber.tag(TAG).d("[SUCCESS] Fixed $changesCount exclusion states")
            } else {
                Timber.tag(TAG).d("[SUCCESS] All large transactions already excluded")
            }
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "[ERROR] Error fixing exclusion states")
        }
    }
}