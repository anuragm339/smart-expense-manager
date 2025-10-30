package com.expensemanager.app.auth

import android.app.Activity
import android.content.Context
import com.expensemanager.app.core.DebugConfig
import com.expensemanager.app.data.dao.UserDao
import com.expensemanager.app.data.entities.UserEntity
import com.expensemanager.app.utils.logging.StructuredLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
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
        private const val MOCK_SIGN_IN_DELAY = 500L // Simulate network delay

        // Test user IDs
        const val USER_FREE_ID = "test_user_free"
        const val USER_PREMIUM_ID = "test_user_premium"

        // Test user data
        val TEST_USERS = listOf(
            TestUser(
                userId = USER_FREE_ID,
                email = "free@test.com",
                displayName = "Test User (Free)",
                tier = "FREE"
            ),
            TestUser(
                userId = USER_PREMIUM_ID,
                email = "premium@test.com",
                displayName = "Test User (Premium)",
                tier = "PREMIUM"
            )
        )
    }

    private val logger = StructuredLogger("APP", TAG)

    data class TestUser(
        val userId: String,
        val email: String,
        val displayName: String,
        val tier: String
    )

    override suspend fun isAuthenticated(): Boolean {
        DebugConfig.logMockUsage("Authentication", "Checking mock authentication status")

        val user = userDao.getCurrentUser()
        val isAuth = user != null && user.isAuthenticated

        logger.debug("isAuthenticated", "🎭 Mock auth check: $isAuth")
        return isAuth
    }

    override suspend fun getCurrentUser(): UserEntity? {
        val user = userDao.getCurrentUser()
        logger.debug("getCurrentUser", "🎭 Getting mock user: ${user?.email}")
        return user
    }

    /**
     * Sign in with a specific test user
     */
    fun signInWithTestUser(
        testUser: TestUser,
        onSuccess: (UserEntity) -> Unit,
        onError: (Exception) -> Unit
    ) {
        DebugConfig.logMockUsage("Authentication", "Starting mock sign-in with ${testUser.tier} user")
        logger.debug("signInWithTestUser", "🎭 Mock sign-in started for: ${testUser.email}")

        GlobalScope.launch(Dispatchers.Main) {
            try {
                delay(MOCK_SIGN_IN_DELAY)

                val userEntity = createTestUserEntity(testUser)
                userDao.insertUser(userEntity)

                logger.info("signInWithTestUser", "🎭 Mock sign-in successful: ${testUser.email} (${testUser.tier})")
                onSuccess(userEntity)

            } catch (e: Exception) {
                logger.error("signInWithTestUser", "🎭 Mock sign-in failed", e)
                onError(e)
            }
        }
    }

    override fun signIn(
        activity: Activity,
        onSuccess: (UserEntity) -> Unit,
        onError: (Exception) -> Unit
    ) {
        // This will be called from LoginActivity which will show user selection
        DebugConfig.logMockUsage("Authentication", "Mock sign-in - use signInWithTestUser instead")
        logger.warn("signIn", "🎭 signIn() called - should use signInWithTestUser() for test users")
    }

    override suspend fun signOut() {
        DebugConfig.logMockUsage("Authentication", "Signing out mock user")
        logger.debug("signOut", "🎭 Mock sign-out")

        userDao.deleteAllUsers()
        logger.info("signOut", "🎭 Mock user signed out successfully")
    }

    override suspend fun handleSignInResult(data: android.content.Intent?): Result<UserEntity> {
        // Mock implementation always succeeds - use default Free user
        DebugConfig.logMockUsage("Authentication", "Handling mock sign-in result")

        return try {
            delay(MOCK_SIGN_IN_DELAY)
            val debugUser = createTestUserEntity(TEST_USERS[0]) // Default to Free user
            userDao.insertUser(debugUser)

            logger.info("handleSignInResult", "🎭 Mock sign-in result handled successfully")
            Result.success(debugUser)
        } catch (e: Exception) {
            logger.error("handleSignInResult", "🎭 Mock sign-in result handling failed", e)
            Result.failure(e)
        }
    }

    override fun getAuthMode(): String {
        return "MOCK (Debug Build)"
    }

    /**
     * Create test user entity
     */
    private fun createTestUserEntity(testUser: TestUser): UserEntity {
        val now = System.currentTimeMillis()

        return UserEntity(
            userId = testUser.userId,
            email = testUser.email,
            displayName = testUser.displayName,
            photoUrl = null, // No profile picture in mock mode
            isAuthenticated = true,
            lastLoginTimestamp = now,
            createdAt = now,
            subscriptionTier = testUser.tier
        )
    }
}
