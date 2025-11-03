package com.smartexpenseai.app.auth

import android.app.Activity
import com.smartexpenseai.app.data.entities.UserEntity

/**
 * Unified authentication interface
 * Provides abstraction over mock and real authentication implementations
 */
interface AuthManager {

    /**
     * Check if user is currently authenticated
     */
    suspend fun isAuthenticated(): Boolean

    /**
     * Get current authenticated user
     * @return UserEntity if authenticated, null otherwise
     */
    suspend fun getCurrentUser(): UserEntity?

    /**
     * Start sign-in flow
     * @param activity The activity to launch sign-in from
     * @param onSuccess Callback when sign-in succeeds
     * @param onError Callback when sign-in fails
     */
    fun signIn(
        activity: Activity,
        onSuccess: (UserEntity) -> Unit,
        onError: (Exception) -> Unit
    )

    /**
     * Sign out current user
     */
    suspend fun signOut()

    /**
     * Handle sign-in result (for Google Sign-In)
     * @param data Intent data from onActivityResult
     */
    suspend fun handleSignInResult(data: android.content.Intent?): Result<UserEntity>

    /**
     * Get authentication mode (for logging/debugging)
     */
    fun getAuthMode(): String
}

/**
 * Authentication result sealed class
 */
sealed class AuthResult {
    data class Success(val user: UserEntity) : AuthResult()
    data class Error(val exception: Exception) : AuthResult()
    object Cancelled : AuthResult()
}
