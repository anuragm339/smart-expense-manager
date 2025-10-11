package com.expensemanager.app

import android.app.Application
import com.expensemanager.app.data.migration.DataMigrationManager
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.utils.logging.LoggingMode
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
    lateinit var logConfig: LogConfig

    @Inject
    lateinit var timberFileTree: TimberFileTree

    @Inject
    lateinit var bugFixLoggingConfig: com.expensemanager.app.utils.logging.BugFixLoggingConfig

    // Application-wide coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val logger = StructuredLogger(LogConfig.FeatureTags.APP, ExpenseManagerApplication::class.java.simpleName)
    
    override fun onCreate() {
        super.onCreate()

        // Initialize enhanced Timber logging system
        initializeTimber()

        logger.debug("onCreate","Application onCreate() starting with enhanced Timber logging...")
        logger.debug("onCreate","Enhanced Timber logging system initialized successfully")

        // 🔧 Configure focused logging based on LoggingMode
        configureFocusedLogging()

        // Log current configuration for debugging
        logger.debug("onCreate","Current logging configuration:\n${logConfig.getCurrentConfig()}")

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
     * 🔧 Configure focused logging based on LoggingMode.kt setting
     */
    private fun configureFocusedLogging() {
        val mode = LoggingMode.CURRENT_MODE
        logger.debug("configureFocusedLogging","Configuring logging: ${LoggingMode.getDescription()}")

        when (mode) {
            LoggingMode.Mode.BUG_1 -> bugFixLoggingConfig.enableBug1Logs()
            LoggingMode.Mode.BUG_2 -> bugFixLoggingConfig.enableBug2Logs()
            LoggingMode.Mode.BUG_3 -> bugFixLoggingConfig.enableBug3Logs()
            LoggingMode.Mode.ALL_BUGS -> bugFixLoggingConfig.enableAllBugLogs()
            LoggingMode.Mode.NORMAL -> bugFixLoggingConfig.restoreNormalLogging()
            LoggingMode.Mode.MINIMAL -> bugFixLoggingConfig.enableMinimalLogging()
        }

        bugFixLoggingConfig.printCurrentStatus()
    }
    
    /**
     * Initialize enhanced Timber logging system with feature-specific control
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

        // Plant enhanced file tree for feature-based filtering and persistence.
        Timber.plant(timberFileTree)

        logger.info(
            "initializeTimber",
            "Timber logging system initialized",
            "Trees planted: ${Timber.treeCount}"
        )
        logger.debug(
            "initializeTimber",
            "File logging enabled state",
            "Value: ${logConfig.isFileLoggingEnabled}"
        )
        logger.debug(
            "initializeTimber",
            "External logging enabled state",
            "Value: ${logConfig.isExternalLoggingEnabled}"
        )
    }

}
