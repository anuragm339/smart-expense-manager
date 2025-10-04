package com.expensemanager.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.expensemanager.app.R
import com.expensemanager.app.auth.AuthManager
import com.expensemanager.app.databinding.FragmentProfileBinding
import com.expensemanager.app.ui.auth.SplashActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

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
    }
    
    private fun setupClickListeners() {
        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_profile_to_navigation_settings)
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
        Timber.tag(TAG).d("🔓 Logout initiated")

        lifecycleScope.launch {
            try {
                // Get current user info before logout
                val currentUser = authManager.getCurrentUser()
                Timber.tag(TAG).i("🔓 Logging out user: ${currentUser?.email}")

                // Sign out
                authManager.signOut()

                Timber.tag(TAG).i("✅ Logout successful")
                Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()

                // Navigate to SplashActivity (which will redirect to Login)
                val intent = Intent(requireContext(), SplashActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Logout failed")
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