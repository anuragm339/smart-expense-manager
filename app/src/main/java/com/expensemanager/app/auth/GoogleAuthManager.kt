package com.expensemanager.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.expensemanager.app.R
import com.expensemanager.app.core.DebugConfig
import com.expensemanager.app.data.dao.UserDao
import com.expensemanager.app.data.entities.UserEntity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Google Sign-In implementation for release builds
 * Uses Google Play Services for authentication
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDao: UserDao
) : AuthManager {

    companion object {
        private const val TAG = "GoogleAuthManager"
        const val RC_SIGN_IN = 9001 // Request code for sign-in intent
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()

        GoogleSignIn.getClient(context, gso)
    }

    override suspend fun isAuthenticated(): Boolean {
        DebugConfig.logRealUsage("Authentication", "Checking Google Sign-In status")

        // Check local database first
        val localUser = userDao.getCurrentUser()
        if (localUser != null && localUser.isAuthenticated) {
            Timber.tag(TAG).d("🔐 User authenticated from local DB: ${localUser.email}")
            return true
        }

        // Check Google Sign-In status
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val isAuth = account != null

        if (isAuth && localUser == null) {
            // User is signed in with Google but not in local DB - sync it
            Timber.tag(TAG).d("🔐 Syncing Google account to local DB")
            val user = mapGoogleAccountToUser(account!!)
            userDao.insertUser(user)
        }

        Timber.tag(TAG).d("🔐 Google auth check: $isAuth")
        return isAuth
    }

    override suspend fun getCurrentUser(): UserEntity? {
        // Try local database first
        val localUser = userDao.getCurrentUser()
        if (localUser != null) {
            Timber.tag(TAG).d("🔐 Getting user from local DB: ${localUser.email}")
            return localUser
        }

        // Fallback to Google Sign-In account
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            Timber.tag(TAG).d("🔐 Getting user from Google account: ${account.email}")
            val user = mapGoogleAccountToUser(account)
            userDao.insertUser(user) // Cache in local DB
            return user
        }

        Timber.tag(TAG).d("🔐 No authenticated user found")
        return null
    }

    override fun signIn(
        activity: Activity,
        onSuccess: (UserEntity) -> Unit,
        onError: (Exception) -> Unit
    ) {
        DebugConfig.logRealUsage("Authentication", "Starting Google Sign-In flow")
        Timber.tag(TAG).d("🔐 Starting Google Sign-In intent")

        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override suspend fun signOut() {
        DebugConfig.logRealUsage("Authentication", "Signing out Google user")
        Timber.tag(TAG).d("🔐 Google sign-out started")

        try {
            // Sign out from Google
            googleSignInClient.signOut().await()

            // Clear local database
            userDao.deleteAllUsers()

            Timber.tag(TAG).i("🔐 Google sign-out successful")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "🔐 Google sign-out failed")
            throw e
        }
    }

    override suspend fun handleSignInResult(data: Intent?): Result<UserEntity> {
        DebugConfig.logRealUsage("Authentication", "Handling Google Sign-In result")

        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            if (account == null) {
                Timber.tag(TAG).w("🔐 Google Sign-In result: account is null")
                return Result.failure(Exception("Sign-in failed: account is null"))
            }

            val user = mapGoogleAccountToUser(account)
            userDao.insertUser(user)

            Timber.tag(TAG).i("🔐 Google Sign-In successful: ${account.email}")
            Result.success(user)

        } catch (e: ApiException) {
            Timber.tag(TAG).e(e, "🔐 Google Sign-In failed with code: ${e.statusCode}")
            Result.failure(Exception("Google Sign-In failed: ${e.localizedMessage}"))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "🔐 Google Sign-In error")
            Result.failure(e)
        }
    }

    override fun getAuthMode(): String {
        return "GOOGLE (Production)"
    }

    /**
     * Map Google account to UserEntity
     */
    private fun mapGoogleAccountToUser(account: GoogleSignInAccount): UserEntity {
        val now = System.currentTimeMillis()

        return UserEntity(
            userId = account.id ?: "unknown",
            email = account.email ?: "no-email@example.com",
            displayName = account.displayName ?: "User",
            photoUrl = account.photoUrl?.toString(),
            isAuthenticated = true,
            lastLoginTimestamp = now,
            createdAt = now
        )
    }
}
