package com.smartexpenseai.app.core

import com.smartexpenseai.app.BuildConfig
import timber.log.Timber

/**
 * Centralized debug configuration for the app.
 * Controls mock behavior for authentication and subscription in debug builds.
 */
object DebugConfig {

    private const val TAG = "DebugConfig"

    /**
     * Whether to use mock authentication (bypasses Google Sign-In)
     */
    val useMockAuth: Boolean
        get() = BuildConfig.MOCK_AUTH

    /**
     * Whether to use mock subscription (bypasses Google Play Billing)
     */
    val useMockSubscription: Boolean
        get() = BuildConfig.MOCK_SUBSCRIPTION

    /**
     * Whether the app is running in debug mode
     */
    val isDebugBuild: Boolean
        get() = BuildConfig.DEBUG

    /**
     * Build type name for logging
     */
    val buildTypeName: String
        get() = BuildConfig.BUILD_TYPE_NAME

    /**
     * Initialize debug configuration and log current mode
     * Should be called in Application.onCreate()
     */
    fun initialize() {
        Timber.tag(TAG).i("═══════════════════════════════════════")
        Timber.tag(TAG).i("🛠️  Debug Configuration Initialized")
        Timber.tag(TAG).i("═══════════════════════════════════════")
        Timber.tag(TAG).i("Build Type: $buildTypeName")
        Timber.tag(TAG).i("Debug Build: $isDebugBuild")
        Timber.tag(TAG).i("Mock Auth: ${if (useMockAuth) "✅ ENABLED" else "❌ DISABLED"}")
        Timber.tag(TAG).i("Mock Subscription: ${if (useMockSubscription) "✅ ENABLED" else "❌ DISABLED"}")

        if (useMockAuth || useMockSubscription) {
            Timber.tag(TAG).w("⚠️  RUNNING IN MOCK MODE - NOT FOR PRODUCTION USE")
        } else {
            Timber.tag(TAG).i("✅ Production mode - using real Google services")
        }
        Timber.tag(TAG).i("═══════════════════════════════════════")
    }

    /**
     * Get debug banner text for UI display
     */
    fun getDebugBanner(): String? {
        return if (isDebugBuild && (useMockAuth || useMockSubscription)) {
            "🛠️ DEBUG MODE"
        } else {
            null
        }
    }

    /**
     * Log when mock implementation is being used
     */
    fun logMockUsage(feature: String, reason: String) {
        if (isDebugBuild) {
            Timber.tag(TAG).d("🎭 Using MOCK $feature: $reason")
        }
    }

    /**
     * Log when real implementation is being used
     */
    fun logRealUsage(feature: String, reason: String) {
        Timber.tag(TAG).d("🔐 Using REAL $feature: $reason")
    }
}
