package com.expensemanager.app

import android.app.Application
import com.expensemanager.app.data.migration.DataMigrationManager
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.utils.logging.TimberFileTree
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
    lateinit var logConfig: LogConfig
    
    @Inject
    lateinit var timberFileTree: TimberFileTree
    
    // Application-wide coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val TAG = "APP"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize enhanced Timber logging system
        initializeTimber()
        
        Timber.tag(TAG).d("Application onCreate() starting with enhanced Timber logging...")
        Timber.tag(TAG).i("Enhanced Timber logging system initialized successfully")
        
        // Log current configuration for debugging
        Timber.tag(TAG).d("Current logging configuration:\n${logConfig.getCurrentConfig()}")
        
        // Perform data migration in background
        applicationScope.launch {
            try {
                Timber.tag(LogConfig.FeatureTags.MIGRATION).d("Starting data migration check...")
                
                val success = dataMigrationManager.performMigrationIfNeeded()
                
                if (success) {
                    Timber.tag(LogConfig.FeatureTags.MIGRATION).i("Data migration completed successfully")
                    Timber.tag(TAG).i("App initialization completed successfully")
                } else {
                    Timber.tag(LogConfig.FeatureTags.MIGRATION).w("Data migration completed with warnings")
                    Timber.tag(TAG).w("App initialization completed with warnings")
                }
                
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.MIGRATION).e(e, "Data migration failed")
                Timber.tag(TAG).e(e, "App initialization failed")
                // App can still continue to work, just with degraded functionality
            }
        }
    }
    
    /**
     * Initialize enhanced Timber logging system with feature-specific control
     */
    private fun initializeTimber() {
        // Clear any existing trees
        Timber.uprootAll()
        
        // Plant debug tree for development (always visible in logcat)
        Timber.plant(object : Timber.DebugTree() {
            override fun createStackElementTag(element: StackTraceElement): String {
                return "[${element.className.substringAfterLast('.')}:${element.lineNumber}]"
            }
            
            override fun isLoggable(tag: String?, priority: Int): Boolean {
                // Let debug tree handle logcat filtering
                return super.isLoggable(tag, priority)
            }
        })
        
        // Plant enhanced file tree for feature-specific logging
        Timber.plant(timberFileTree)
        
        // Log initialization success
        Timber.tag(TAG).i("Timber logging system initialized with ${Timber.treeCount} trees")
        Timber.tag(TAG).d("File logging: ${if (logConfig.isFileLoggingEnabled) "ENABLED" else "DISABLED"}")
        Timber.tag(TAG).d("External logging: ${if (logConfig.isExternalLoggingEnabled) "ENABLED" else "DISABLED"}")
    }
    
}