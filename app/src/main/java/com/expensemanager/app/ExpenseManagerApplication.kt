package com.expensemanager.app

import android.app.Application
import android.util.Log
import com.expensemanager.app.data.migration.DataMigrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExpenseManagerApplication : Application() {
    
    // Application-wide coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val TAG = "ExpenseManagerApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Application starting up...")
        
        // Perform data migration in background
        applicationScope.launch {
            try {
                val migrationManager = DataMigrationManager(applicationContext)
                val success = migrationManager.performMigrationIfNeeded()
                
                if (success) {
                    Log.i(TAG, "App initialization completed successfully")
                } else {
                    Log.w(TAG, "App initialization completed with warnings")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "App initialization failed", e)
                // App can still continue to work, just with degraded functionality
            }
        }
    }
}