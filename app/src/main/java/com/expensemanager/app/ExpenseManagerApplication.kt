package com.expensemanager.app

import android.app.Application
import android.util.Log
import com.expensemanager.app.data.migration.DataMigrationManager
import com.expensemanager.app.utils.AppLogger
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
    
    @Inject
    lateinit var appLogger: AppLogger
    
    // Application-wide coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val TAG = "ExpenseManagerApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Application onCreate() starting...")
        
        // Initialize both logging systems during transition period
        initializeLogging()
        
        // Give Logback a moment to finish initialization
        try {
            Thread.sleep(100) // Small delay to ensure Logback is ready
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted during logging initialization delay")
        }
        
        // Test that logging is working and log startup
        appLogger.info(TAG, "Application starting up with Logback logging system...")
        
        // Perform data migration in background
        applicationScope.launch {
            try {
                appLogger.debug(TAG, "Starting data migration check...")
                
                // FIXED: Remove debug code that was resetting migration state on every app start
                // This was causing fresh installs to appear empty because migration would run repeatedly
                appLogger.debug(TAG, "Checking if migration is needed...")
                
                val success = dataMigrationManager.performMigrationIfNeeded()
                
                if (success) {
                    appLogger.info(TAG, "App initialization completed successfully")
                } else {
                    appLogger.warn(TAG, "App initialization completed with warnings")
                }
                
            } catch (e: Exception) {
                appLogger.error(TAG, "App initialization failed", e)
                Log.e(TAG, "App initialization failed (fallback)", e)
                // App can still continue to work, just with degraded functionality
            }
        }
    }
    
    /**
     * Initialize logging systems - both Logback (primary) and Timber (temporary during transition)
     */
    private fun initializeLogging() {
        Log.d(TAG, "Initializing logging systems...")
        
        try {
            // Initialize Logback (primary logging system)
            // AppLogger automatically initializes Logback when instantiated via Hilt
            Log.d(TAG, "AppLogger instance will be initialized by Hilt dependency injection")
            
            // Keep Timber for backward compatibility during transition
            // TODO: Remove Timber after all components are migrated to Logback
            initializeTimber()
            
            Log.d(TAG, "Logging systems initialized - Logback (primary), Timber (legacy)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during logging initialization", e)
        }
    }
    
    /**
     * Initialize legacy Timber logging framework (temporary during transition)
     * TODO: Remove this method once migration to Logback is complete
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
        Timber.tag(TAG).d("Timber initialized for legacy compatibility")
    }
}