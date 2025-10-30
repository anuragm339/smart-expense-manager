package com.expensemanager.app

import android.app.Application
import com.expensemanager.app.data.migration.DataMigrationManager
import com.expensemanager.app.utils.logging.StructuredLogger
import com.expensemanager.app.utils.logging.TimberFileTree
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class ExpenseManagerApplication : Application() {

    @Inject
    lateinit var dataMigrationManager: DataMigrationManager

    @Inject
    lateinit var timberFileTree: TimberFileTree

    // Application-wide coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val logger = StructuredLogger("APP", ExpenseManagerApplication::class.java.simpleName)
    
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber logging system
        initializeTimber()

        logger.debug("onCreate","Application onCreate() starting with Timber logging...")
        logger.debug("onCreate","Timber logging system initialized successfully")

        // Perform data migration in background
        applicationScope.launch {
            try {
                logger.debug("onCreate","Starting data migration check...")

                val success = dataMigrationManager.performMigrationIfNeeded()

                if (success) {
                    logger.debug("onCreate","Data migration completed successfully")
                    logger.debug("onCreate","App initialization completed successfully")
                } else {
                    logger.debug("onCreate","Data migration completed with warnings")
                    logger.debug("onCreate","App initialization completed with warnings")
                }

            } catch (e: Exception) {
                logger.error("onCreate","Data migration failed",e)
                // App can still continue to work, just with degraded functionality
            }
        }
    }

    /**
     * Initialize Timber logging system
     */
    private fun initializeTimber() {
        Timber.uprootAll()

        // Plant debug tree when running debug builds so logs appear in Logcat while developing.
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "${element.className.substringAfterLast('.')}:${element.lineNumber}"
                }
            })
        }

        // Plant file tree to write logs to files for debugging
        Timber.plant(timberFileTree)

        // Test log to verify file logging works
        Timber.tag("APP").i("TimberFileTree planted successfully - file logging enabled")

        logger.info("initializeTimber", "Timber logging system initialized with file logging")
    }

}
