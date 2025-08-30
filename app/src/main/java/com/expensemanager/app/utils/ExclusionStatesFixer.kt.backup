package com.expensemanager.app.utils

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * One-time utility to fix exclusion states for large/test transactions
 */
object ExclusionStatesFixer {
    
    private const val TAG = "ExclusionStatesFixer"
    
    fun fixLargeTransactionExclusions(context: Context) {
        try {
            Log.d(TAG, "🔧 Fixing exclusion states for large transactions...")
            
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
                    Log.d(TAG, "🚫 Excluded: $merchant")
                }
            }
            
            if (changesCount > 0) {
                // Save back to preferences
                prefs.edit()
                    .putString("group_inclusion_states", inclusionStates.toString())
                    .apply()
                
                Log.d(TAG, "✅ Fixed $changesCount exclusion states")
            } else {
                Log.d(TAG, "✅ All large transactions already excluded")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fixing exclusion states", e)
        }
    }
}