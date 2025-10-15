package com.expensemanager.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.expensemanager.app.R
import com.expensemanager.app.auth.AuthManager
import com.expensemanager.app.databinding.FragmentProfileBinding
import com.expensemanager.app.ui.auth.SplashActivity
import com.expensemanager.app.utils.logging.StructuredLogger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val logger = StructuredLogger("ProfileFragment", "ProfileFragment")

    private val viewModel: ProfileViewModel by viewModels()

    @Inject
    lateinit var authManager: AuthManager

    companion object {
        private const val TAG = "ProfileFragment"
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeViewModel()
    }
    
    private fun setupClickListeners() {
        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_profile_to_navigation_settings)
        }

        binding.cardMonthlyBudget.setOnClickListener {
            showEditBudgetDialog()
        }

        binding.layoutNotifications.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_profile_to_navigation_notifications_settings)
        }

        binding.layoutPrivacy.setOnClickListener {
            Toast.makeText(requireContext(), "Privacy & Security", Toast.LENGTH_SHORT).show()
        }

        binding.layoutExport.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_profile_to_navigation_export_data)
        }

        binding.layoutAbout.setOnClickListener {
            Toast.makeText(requireContext(), "About Smart Expense Manager v1.0", Toast.LENGTH_SHORT).show()
        }

        binding.btnBackupData.setOnClickListener {
            Toast.makeText(requireContext(), "Backup started...", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                event?.let { handleEvent(it) }
            }
        }
    }

    private fun updateUI(state: ProfileUiState) {
        logger.debug("updateUI", "Updating UI with state: displayName=${state.displayName}, email=${state.email}, budget=${state.monthlyBudget}")

        // Update user info
        binding.tvProfileName.text = state.displayName.ifEmpty { "User" }
        binding.tvProfileEmail.text = state.email.ifEmpty { "Not available" }
        binding.tvMemberSince.text = state.memberSince.ifEmpty { "Member since recently" }

        // Update monthly budget
        binding.tvMonthlyBudget.text = "₹${String.format("%.0f", state.monthlyBudget)}"

        // Handle loading state
        if (state.isLoading) {
            logger.debug("updateUI", "Loading state active")
        }

        // Handle error state
        state.error?.let { error ->
            logger.error("updateUI", "Error state: $error", null)
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.ShowMessage -> {
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
            }
            is ProfileEvent.ShowError -> {
                Toast.makeText(requireContext(), event.error, Toast.LENGTH_LONG).show()
            }
        }
        viewModel.clearEvent()
    }

    private fun showEditBudgetDialog() {
        val currentBudget = viewModel.uiState.value.monthlyBudget

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.0f", currentBudget))
            hint = "Enter monthly budget"
            setPadding(50, 30, 50, 30)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Monthly Budget")
            .setMessage("Set your monthly spending budget")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val budgetText = input.text.toString()
                if (budgetText.isNotEmpty()) {
                    try {
                        val newBudget = budgetText.toDouble()
                        if (newBudget >= 0) {
                            logger.debug("showEditBudgetDialog", "User setting budget to ₹$newBudget")
                            viewModel.updateMonthlyBudget(newBudget)
                        } else {
                            Toast.makeText(requireContext(), "Budget must be positive", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: NumberFormatException) {
                        Toast.makeText(requireContext(), "Invalid budget amount", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showLogoutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        logger.debug( "performLogout","Logout initiated")

        lifecycleScope.launch {
            try {
                // Get current user info before logout
                val currentUser = authManager.getCurrentUser()
                logger.debug( "performLogout","Logging out user: ${currentUser?.email}")

                // Sign out
                authManager.signOut()

                logger.info( "performLogout","Logout successful")
                Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()

                // Navigate to SplashActivity (which will redirect to Login)
                val intent = Intent(requireContext(), SplashActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()

            } catch (e: Exception) {
                logger.error( "performLogout", "Logout failed",e)
                Toast.makeText(
                    requireContext(),
                    "Logout failed: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}