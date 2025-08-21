package com.expensemanager.app.ui.profile

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var prefs: SharedPreferences
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        setupUI()
        setupClickListeners()
        loadSettings()
    }
    
    private fun setupUI() {
        // Load current settings values
        loadSettings()
    }
    
    private fun setupClickListeners() {
        binding.layoutCurrency.setOnClickListener {
            showCurrencyDialog()
        }
        
        binding.layoutLanguage.setOnClickListener {
            showLanguageDialog()
        }
        
        binding.switchAutoCategorization.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_categorization", isChecked).apply()
            Toast.makeText(
                requireContext(),
                if (isChecked) "Auto categorization enabled" else "Auto categorization disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        binding.switchRealtimeProcessing.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("realtime_processing", isChecked).apply()
            Toast.makeText(
                requireContext(),
                if (isChecked) "Real-time processing enabled" else "Real-time processing disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        binding.layoutClearData.setOnClickListener {
            showClearDataConfirmation()
        }
        
        binding.layoutResetCategories.setOnClickListener {
            showResetCategoriesConfirmation()
        }
        
        binding.btnBudgetGoals.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_settings_to_navigation_budget_goals)
        }
    }
    
    private fun loadSettings() {
        // Load saved settings
        binding.switchAutoCategorization.isChecked = prefs.getBoolean("auto_categorization", true)
        binding.switchRealtimeProcessing.isChecked = prefs.getBoolean("realtime_processing", true)
        
        val currency = prefs.getString("currency", "INR")
        binding.tvCurrencyValue.text = when (currency) {
            "INR" -> "Indian Rupee (₹)"
            "USD" -> "US Dollar ($)"
            "EUR" -> "Euro (€)"
            "GBP" -> "British Pound (£)"
            else -> "Indian Rupee (₹)"
        }
        
        val language = prefs.getString("language", "en")
        binding.tvLanguageValue.text = when (language) {
            "en" -> "English"
            "hi" -> "हिंदी (Hindi)"
            "te" -> "తెలుగు (Telugu)"
            "ta" -> "தமிழ் (Tamil)"
            else -> "English"
        }
    }
    
    private fun showCurrencyDialog() {
        val currencies = arrayOf("Indian Rupee (₹)", "US Dollar ($)", "Euro (€)", "British Pound (£)")
        val currencyCodes = arrayOf("INR", "USD", "EUR", "GBP")
        val currentCurrency = prefs.getString("currency", "INR")
        val currentIndex = currencyCodes.indexOf(currentCurrency)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Currency")
            .setSingleChoiceItems(currencies, currentIndex) { dialog, which ->
                prefs.edit().putString("currency", currencyCodes[which]).apply()
                binding.tvCurrencyValue.text = currencies[which]
                Toast.makeText(requireContext(), "Currency updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showLanguageDialog() {
        val languages = arrayOf("English", "हिंदी (Hindi)", "తెలుగు (Telugu)", "தமிழ் (Tamil)")
        val languageCodes = arrayOf("en", "hi", "te", "ta")
        val currentLanguage = prefs.getString("language", "en")
        val currentIndex = languageCodes.indexOf(currentLanguage)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Language")
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                prefs.edit().putString("language", languageCodes[which]).apply()
                binding.tvLanguageValue.text = languages[which]
                Toast.makeText(requireContext(), "Language updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showClearDataConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear All Data")
            .setMessage("This will permanently delete all your transactions, categories, and settings. This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllData()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showResetCategoriesConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset Categories")
            .setMessage("This will reset all learned categories to their defaults. Your custom category assignments will be lost.")
            .setPositiveButton("Reset") { _, _ ->
                resetCategories()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun clearAllData() {
        try {
            // Clear category rules
            requireContext().getSharedPreferences("category_rules", Context.MODE_PRIVATE)
                .edit().clear().apply()
            
            // Clear app settings (except basic preferences)
            prefs.edit()
                .remove("budget_goals")
                .remove("notification_settings")
                .apply()
            
            Toast.makeText(requireContext(), "All data cleared successfully", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error clearing data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun resetCategories() {
        try {
            // Clear learned category rules
            requireContext().getSharedPreferences("category_rules", Context.MODE_PRIVATE)
                .edit().clear().apply()
            
            Toast.makeText(requireContext(), "Categories reset to defaults", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error resetting categories: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}