package com.smartexpenseai.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.smartexpenseai.app.R
import com.smartexpenseai.app.core.DebugConfig
import com.smartexpenseai.app.data.dao.UserDao
import com.smartexpenseai.app.data.entities.UserEntity
import com.smartexpenseai.app.utils.logging.StructuredLogger
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
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

    private val logger = StructuredLogger("AUTH", TAG)

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))  // Web client ID from google-services.json
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
            logger.debug("isAuthenticated", "User authenticated from local DB: ${localUser.email}")
            return true
        }

        // Check Google Sign-In status
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val isAuth = account != null

        if (isAuth && localUser == null) {
            // User is signed in with Google but not in local DB - sync it
            logger.debug("isAuthenticated", "Syncing Google account to local DB")
            val user = mapGoogleAccountToUser(account!!)
            userDao.insertUser(user)
        }

        logger.debug("isAuthenticated", "Google auth check result: $isAuth")
        return isAuth
    }

    override suspend fun getCurrentUser(): UserEntity? {
        // Try local database first
        val localUser = userDao.getCurrentUser()
        if (localUser != null) {
            logger.debug("getCurrentUser", "Returning user from local DB: ${localUser.email}")
            return localUser
        }

        // Fallback to Google Sign-In account
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            logger.debug("getCurrentUser", "Returning user from Google account: ${account.email}")
            val user = mapGoogleAccountToUser(account)
            userDao.insertUser(user) // Cache in local DB
            return user
        }

        logger.debug("getCurrentUser", "No authenticated user found")
        return null
    }

    override fun signIn(
        activity: Activity,
        onSuccess: (UserEntity) -> Unit,
        onError: (Exception) -> Unit
    ) {
        DebugConfig.logRealUsage("Authentication", "Starting Google Sign-In flow")
        logger.debug("signIn", "Launching Google Sign-In intent")

        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override suspend fun signOut() {
        DebugConfig.logRealUsage("Authentication", "Signing out Google user")
        logger.debug("signOut", "Google sign-out started")

        try {
            // Sign out from Google
            googleSignInClient.signOut().await()

            // Clear local database
            userDao.deleteAllUsers()

            logger.info("signOut", "Google sign-out successful")
        } catch (e: Exception) {
            logger.error("signOut", "Google sign-out failed", e)
            throw e
        }
    }

    override suspend fun handleSignInResult(data: Intent?): Result<UserEntity> {
        DebugConfig.logRealUsage("Authentication", "Handling Google Sign-In result")

        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            if (account == null) {
                logger.warn("handleSignInResult", "Google Sign-In returned a null account")
                return Result.failure(GoogleAuthException(CommonStatusCodes.INTERNAL_ERROR, context.getString(R.string.error_sign_in_generic, CommonStatusCodes.INTERNAL_ERROR)))
            }

            val user = mapGoogleAccountToUser(account)
            userDao.insertUser(user)

            logger.info("handleSignInResult", "Google Sign-In successful for ${account.email}")
            Result.success(user)

        } catch (e: ApiException) {
            logger.error("handleSignInResult", "Google Sign-In ApiException: code=${e.statusCode}", e)
            Result.failure(googleAuthErrorForStatus(e))
        } catch (e: Exception) {
            logger.error("handleSignInResult", "Unexpected Google Sign-In error", e)
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

    private fun googleAuthErrorForStatus(exception: ApiException): GoogleAuthException {
        val statusCode = exception.statusCode
        val message = when (statusCode) {
            CommonStatusCodes.NETWORK_ERROR -> context.getString(R.string.error_sign_in_network)
            CommonStatusCodes.CANCELED -> context.getString(R.string.error_sign_in_cancelled)
            else -> context.getString(R.string.error_sign_in_generic, statusCode)
        }
        return GoogleAuthException(statusCode, message, exception)
    }
}

class GoogleAuthException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
