package com.smartexpenseai.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User entity for Room database
 * Stores authenticated user information and subscription tier
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userId: String,              // Google user ID or test user ID for mock mode
    val email: String,               // User email address
    val displayName: String,         // User's display name
    val photoUrl: String?,           // Profile picture URL (nullable)
    val isAuthenticated: Boolean,    // Authentication status
    val lastLoginTimestamp: Long,    // Last login time in milliseconds
    val createdAt: Long,             // Account creation timestamp
    val subscriptionTier: String = "FREE"  // Subscription tier: FREE, PREMIUM
)

/**
 * Subscription tiers with AI call limits
 */
enum class SubscriptionTier(
    val tierName: String,
    val dailyAICallLimit: Int,
    val monthlyAICallLimit: Int,
    val displayName: String
) {
    FREE(
        tierName = "FREE",
        dailyAICallLimit = 3,
        monthlyAICallLimit = 30,
        displayName = "Free"
    ),
    PREMIUM(
        tierName = "PREMIUM",
        dailyAICallLimit = 20,
        monthlyAICallLimit = 300,
        displayName = "Premium"
    );

    companion object {
        fun fromString(tier: String): SubscriptionTier {
            return values().find { it.tierName == tier.uppercase() } ?: FREE
        }
    }
}
