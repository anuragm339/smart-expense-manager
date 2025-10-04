package com.expensemanager.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User entity for Room database
 * Stores authenticated user information
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userId: String,           // Google user ID or "debug_user" for mock mode
    val email: String,             // User email address
    val displayName: String,       // User's display name
    val photoUrl: String?,         // Profile picture URL (nullable)
    val isAuthenticated: Boolean,  // Authentication status
    val lastLoginTimestamp: Long,  // Last login time in milliseconds
    val createdAt: Long            // Account creation timestamp
)
