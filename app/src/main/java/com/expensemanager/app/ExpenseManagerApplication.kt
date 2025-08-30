package com.expensemanager.app

import android.app.Application
import android.util.Log
import com.expensemanager.app.data.migration.DataMigrationManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ExpenseManagerApplication : Application() {
    
    @Inject
    lateinit var dataMigrationManager: DataMigrationManager
    
    // Application-wide coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val TAG = "ExpenseManagerApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for structured logging
        initializeTimber()
        
        Timber.tag(TAG).d("Application starting up...")
        
        // Perform data migration in background
        applicationScope.launch {
            try {
                Timber.tag(TAG).d("Starting data migration check...")
                
                // FIXED: Remove debug code that was resetting migration state on every app start
                // This was causing fresh installs to appear empty because migration would run repeatedly
                Timber.tag(TAG).d("Checking if migration is needed...")
                
                val success = dataMigrationManager.performMigrationIfNeeded()
                
                if (success) {
                    Timber.tag(TAG).i("App initialization completed successfully")
                } else {
                    Timber.tag(TAG).w("App initialization completed with warnings")
                }
                
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "App initialization failed")
                // App can still continue to work, just with degraded functionality
            }
        }
    }
    
    /**
     * Initialize Timber logging framework with production-grade configuration
     */
    private fun initializeTimber() {
        // For Phase 1, always initialize Timber in debug mode
        // TODO: Add BuildConfig.DEBUG check in Phase 2
        Timber.plant(object : Timber.DebugTree() {
            override fun createStackElementTag(element: StackTraceElement): String {
                // Format: [ClassName][methodName]
                return "[${element.className.substringAfterLast('.')}][${element.methodName}]"
            }
        })
        Timber.tag(TAG).d("Timber initialized for development")
    }
}