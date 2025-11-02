package com.smartexpenseai.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpenseai.app.auth.AuthManager
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.utils.logging.StructuredLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val repository: ExpenseRepository
) : ViewModel() {

    private val logger = StructuredLogger("ProfileViewModel", "ProfileViewModel")

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<ProfileEvent?>(null)
    val events: StateFlow<ProfileEvent?> = _events.asStateFlow()

    init {
        loadProfileData()
    }

    fun loadProfileData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                logger.debug("loadProfileData", "Loading user profile data...")

                // Load user data from AuthManager
                val user = authManager.getCurrentUser()

                if (user == null) {
                    logger.error("loadProfileData", "No user found - user not authenticated", null)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "User not authenticated"
                    )
                    return@launch
                }

                logger.debug("loadProfileData", "User loaded: ${user.email}")

                // Format member since date
                val memberSince = formatMemberSince(user.createdAt)

                // Load monthly budget from database
                val budgetEntity = repository.getMonthlyBudget()
                val monthlyBudget = budgetEntity?.budgetAmount ?: 0.0

                logger.debug("loadProfileData", "Monthly budget: ₹$monthlyBudget")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    displayName = user.displayName,
                    email = user.email,
                    photoUrl = user.photoUrl,
                    memberSince = memberSince,
                    monthlyBudget = monthlyBudget,
                    subscriptionTier = user.subscriptionTier
                )

                logger.info("loadProfileData", "Profile data loaded successfully")

            } catch (e: Exception) {
                logger.error("loadProfileData", "Error loading profile data", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading profile: ${e.message}"
                )
            }
        }
    }

    fun updateMonthlyBudget(newBudget: Double) {
        viewModelScope.launch {
            try {
                logger.debug("updateMonthlyBudget", "Updating monthly budget to ₹$newBudget")

                // Save to database
                repository.saveMonthlyBudget(newBudget)

                // Update UI state
                _uiState.value = _uiState.value.copy(monthlyBudget = newBudget)

                // Show success message
                _events.value = ProfileEvent.ShowMessage("Monthly budget updated to ₹${String.format("%.0f", newBudget)}")

                logger.info("updateMonthlyBudget", "Monthly budget updated successfully")

            } catch (e: Exception) {
                logger.error("updateMonthlyBudget", "Error updating budget", e)
                _events.value = ProfileEvent.ShowError("Error updating budget: ${e.message}")
            }
        }
    }

    private fun formatMemberSince(createdAtMillis: Long): String {
        return try {
            val date = Date(createdAtMillis)
            val formatter = SimpleDateFormat("MMM yyyy", Locale.getDefault())
            "Member since ${formatter.format(date)}"
        } catch (e: Exception) {
            logger.error("formatMemberSince", "Error formatting date", e)
            "Member since recently"
        }
    }

    fun clearEvent() {
        _events.value = null
    }
}

/**
 * UI State for Profile screen
 */
data class ProfileUiState(
    val isLoading: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val memberSince: String = "",
    val monthlyBudget: Double = 0.0,
    val subscriptionTier: String = "FREE",
    val error: String? = null
)

/**
 * Events for Profile screen
 */
sealed class ProfileEvent {
    data class ShowMessage(val message: String) : ProfileEvent()
    data class ShowError(val error: String) : ProfileEvent()
}
