package com.smartexpenseai.app

import android.app.Application
import com.smartexpenseai.app.utils.logging.StructuredLogger
import com.smartexpenseai.app.utils.logging.TimberFileTree
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class ExpenseManagerApplication : Application() {
    @Inject
    lateinit var timberFileTree: TimberFileTree

    private val logger = StructuredLogger("APP", ExpenseManagerApplication::class.java.simpleName)

    override fun onCreate() {
        super.onCreate()
        logger.debug("onCreate","Application onCreate() starting with Timber logging...")
        initializeTimber()
        logger.debug("onCreate","Timber logging system initialized successfully")
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
