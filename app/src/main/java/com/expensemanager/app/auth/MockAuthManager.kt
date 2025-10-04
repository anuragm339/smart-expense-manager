package com.expensemanager.app.auth

import android.app.Activity
import android.content.Context
import com.expensemanager.app.R
import com.expensemanager.app.core.DebugConfig
import com.expensemanager.app.data.dao.UserDao
import com.expensemanager.app.data.entities.UserEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock authentication implementation for debug builds
 * Automatically signs in as a debug user without requiring Google account
 */
@Singleton
class MockAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDao: UserDao
) : AuthManager {

    companion object {
        private const val TAG = "MockAuthManager"
        private const val DEBUG_USER_ID = "debug_user_001"
        private const val MOCK_SIGN_IN_DELAY = 500L // Simulate network delay
    }

    override suspend fun isAuthenticated(): Boolean {
        DebugConfig.logMockUsage("Authentication", "Checking mock authentication status")

        val user = userDao.getCurrentUser()
        val isAuth = user != null && user.isAuthenticated

        Timber.tag(TAG).d("🎭 Mock auth check: $isAuth")
        return isAuth
    }

    override suspend fun getCurrentUser(): UserEntity? {
        val user = userDao.getCurrentUser()
        Timber.tag(TAG).d("🎭 Getting mock user: ${user?.email}")
        return user
    }

    override fun signIn(
        activity: Activity,
        onSuccess: (UserEntity) -> Unit,
        onError: (Exception) -> Unit
    ) {
        DebugConfig.logMockUsage("Authentication", "Starting mock sign-in")
        Timber.tag(TAG).d("🎭 Mock sign-in started")

        // Launch sign-in in coroutine
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                // Simulate network delay
                delay(MOCK_SIGN_IN_DELAY)

                val debugUser = createDebugUser()
                userDao.insertUser(debugUser)

                Timber.tag(TAG).i("🎭 Mock sign-in successful: ${debugUser.email}")
                onSuccess(debugUser)

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "🎭 Mock sign-in failed")
                onError(e)
            }
        }
    }

    override suspend fun signOut() {
        DebugConfig.logMockUsage("Authentication", "Signing out mock user")
        Timber.tag(TAG).d("🎭 Mock sign-out")

        userDao.deleteAllUsers()
        Timber.tag(TAG).i("🎭 Mock user signed out successfully")
    }

    override suspend fun handleSignInResult(data: android.content.Intent?): Result<UserEntity> {
        // Mock implementation always succeeds
        DebugConfig.logMockUsage("Authentication", "Handling mock sign-in result")

        return try {
            delay(MOCK_SIGN_IN_DELAY)
            val debugUser = createDebugUser()
            userDao.insertUser(debugUser)

            Timber.tag(TAG).i("🎭 Mock sign-in result handled successfully")
            Result.success(debugUser)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "🎭 Mock sign-in result handling failed")
            Result.failure(e)
        }
    }

    override fun getAuthMode(): String {
        return "MOCK (Debug Build)"
    }

    /**
     * Create debug user entity
     */
    private fun createDebugUser(): UserEntity {
        val debugName = context.getString(R.string.debug_user_name)
        val debugEmail = context.getString(R.string.debug_user_email)
        val now = System.currentTimeMillis()

        return UserEntity(
            userId = DEBUG_USER_ID,
            email = debugEmail,
            displayName = debugName,
            photoUrl = null, // No profile picture in mock mode
            isAuthenticated = true,
            lastLoginTimestamp = now,
            createdAt = now
        )
    }
}
