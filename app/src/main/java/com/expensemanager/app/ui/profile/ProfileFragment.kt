package com.expensemanager.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {
    
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
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
            Toast.makeText(requireContext(), "Logout clicked", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}