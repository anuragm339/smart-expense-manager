package com.expensemanager.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.expensemanager.app.MainActivity
import com.expensemanager.app.R
import com.expensemanager.app.auth.AuthManager
import com.expensemanager.app.auth.GoogleAuthManager
import com.expensemanager.app.core.DebugConfig
import com.expensemanager.app.databinding.ActivityLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Login screen with Google Sign-In button
 * In debug mode, shows auto-login message
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    @Inject
    lateinit var authManager: AuthManager

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Timber.tag(TAG).d("🔑 Login screen started")
        Timber.tag(TAG).d("🛠️ Auth mode: ${authManager.getAuthMode()}")

        setupUI()
        setupGoogleSignIn()
    }

    private fun setupUI() {
        // Show debug banner if in mock mode
        if (DebugConfig.isDebugBuild && DebugConfig.useMockAuth) {
            binding.tvDebugBanner.visibility = View.VISIBLE
            binding.tvDebugBanner.text = getString(R.string.debug_banner)
            Timber.tag(TAG).d("🛠️ Debug banner shown")
        } else {
            binding.tvDebugBanner.visibility = View.GONE
        }
    }

    private fun setupGoogleSignIn() {
        binding.btnGoogleSignIn.setOnClickListener {
            Timber.tag(TAG).d("🔑 Sign-in button clicked")

            // In mock mode, show user selection dialog
            if (DebugConfig.useMockAuth) {
                showTestUserSelectionDialog()
            } else {
                // Real Google Sign-In
                showLoading(true)
                authManager.signIn(
                    activity = this,
                    onSuccess = { user ->
                        Timber.tag(TAG).i("✅ Sign-in successful: ${user.email}")
                        showLoading(false)
                        navigateToMain()
                    },
                    onError = { exception ->
                        Timber.tag(TAG).e(exception, "❌ Sign-in failed")
                        showLoading(false)
                        Toast.makeText(
                            this,
                            "Sign-in failed: ${exception.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    private fun showTestUserSelectionDialog() {
        val mockAuthManager = authManager as? com.expensemanager.app.auth.MockAuthManager
        if (mockAuthManager == null) {
            Toast.makeText(this, "Mock auth not available", Toast.LENGTH_SHORT).show()
            return
        }

        val users = com.expensemanager.app.auth.MockAuthManager.TEST_USERS
        val userNames = users.map { "${it.displayName}\n${it.email}" }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Test User")
            .setItems(userNames) { dialog, which ->
                val selectedUser = users[which]
                signInWithTestUser(mockAuthManager, selectedUser)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun signInWithTestUser(
        mockAuthManager: com.expensemanager.app.auth.MockAuthManager,
        testUser: com.expensemanager.app.auth.MockAuthManager.TestUser
    ) {
        Timber.tag(TAG).d("🔑 Selected test user: ${testUser.email}")
        showLoading(true)

        mockAuthManager.signInWithTestUser(
            testUser = testUser,
            onSuccess = { user ->
                Timber.tag(TAG).i("✅ Test user sign-in successful: ${user.email}")
                showLoading(false)
                navigateToMain()
            },
            onError = { exception ->
                Timber.tag(TAG).e(exception, "❌ Test user sign-in failed")
                showLoading(false)
                Toast.makeText(
                    this,
                    "Sign-in failed: ${exception.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GoogleAuthManager.RC_SIGN_IN) {
            Timber.tag(TAG).d("🔑 Google Sign-In result received")

            lifecycleScope.launch {
                val result = authManager.handleSignInResult(data)

                result.onSuccess { user ->
                    Timber.tag(TAG).i("✅ Sign-in successful: ${user.email}")
                    showLoading(false)
                    navigateToMain()
                }.onFailure { exception ->
                    Timber.tag(TAG).e(exception, "❌ Sign-in failed")
                    showLoading(false)
                    Toast.makeText(
                        this@LoginActivity,
                        "Sign-in failed: ${exception.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnGoogleSignIn.isEnabled = !show
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
