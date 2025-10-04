package com.expensemanager.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.expensemanager.app.MainActivity
import com.expensemanager.app.R
import com.expensemanager.app.auth.AuthManager
import com.expensemanager.app.core.DebugConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Splash screen that checks authentication status
 * Redirects to either MainActivity (if authenticated) or LoginActivity (if not)
 */
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DELAY = 1000L // 1 second minimum splash display
    }

    @Inject
    lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Timber.tag(TAG).d("🚀 Splash screen started")
        Timber.tag(TAG).d("🛠️ Auth mode: ${authManager.getAuthMode()}")

        // Log debug configuration
        if (DebugConfig.isDebugBuild) {
            Timber.tag(TAG).d("🛠️ Debug build detected")
            Timber.tag(TAG).d("   Mock Auth: ${DebugConfig.useMockAuth}")
            Timber.tag(TAG).d("   Mock Subscription: ${DebugConfig.useMockSubscription}")
        }

        checkAuthenticationAndNavigate()
    }

    private fun checkAuthenticationAndNavigate() {
        lifecycleScope.launch {
            try {
                // Show splash for minimum duration
                val startTime = System.currentTimeMillis()

                // Check authentication status
                val isAuthenticated = authManager.isAuthenticated()
                val user = authManager.getCurrentUser()

                Timber.tag(TAG).d("🔍 Authentication check complete")
                Timber.tag(TAG).d("   Authenticated: $isAuthenticated")
                Timber.tag(TAG).d("   User: ${user?.email ?: "none"}")

                // Ensure minimum splash display time
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < SPLASH_DELAY) {
                    delay(SPLASH_DELAY - elapsedTime)
                }

                // Navigate based on authentication status
                if (isAuthenticated && user != null) {
                    Timber.tag(TAG).i("✅ User authenticated, navigating to MainActivity")
                    navigateToMain()
                } else {
                    Timber.tag(TAG).i("🔑 User not authenticated, navigating to LoginActivity")
                    navigateToLogin()
                }

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Error during authentication check")
                // On error, navigate to login
                navigateToLogin()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
