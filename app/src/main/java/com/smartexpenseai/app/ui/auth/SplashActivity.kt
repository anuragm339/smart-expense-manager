package com.smartexpenseai.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartexpenseai.app.MainActivity
import com.smartexpenseai.app.R
import com.smartexpenseai.app.auth.AuthManager
import com.smartexpenseai.app.core.DebugConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.smartexpenseai.app.utils.logging.StructuredLogger
import javax.inject.Inject

/**
 * Splash screen that checks authentication status
 * Redirects to either MainActivity (if authenticated) or LoginActivity (if not)
 */
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DELAY = 1000L // 1 second minimum splash display
    }

    private val logger = StructuredLogger("SplashActivity", "SplashActivity")

    @Inject
    lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        logger.debug("onCreate", "Splash screen started")
        logger.debug("onCreate", "Auth mode: ${authManager.getAuthMode()}")

        if (DebugConfig.isDebugBuild) {
            logger.debug("onCreate", "Debug build - Mock Auth: ${DebugConfig.useMockAuth}, Mock Subscription: ${DebugConfig.useMockSubscription}")
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

                logger.debug("checkAuthenticationAndNavigate", "Auth check complete - Authenticated: $isAuthenticated, User: ${user?.email ?: "none"}")

                // Ensure minimum splash display time
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < SPLASH_DELAY) {
                    delay(SPLASH_DELAY - elapsedTime)
                }

                // Navigate based on authentication status
                if (isAuthenticated && user != null) {
                    logger.info("checkAuthenticationAndNavigate", "User authenticated, navigating to MainActivity")
                    navigateToMain()
                } else {
                    logger.info("checkAuthenticationAndNavigate", "User not authenticated, navigating to LoginActivity")
                    navigateToLogin()
                }

            } catch (e: Exception) {
                logger.error("checkAuthenticationAndNavigate", "Error during authentication check", e)
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
