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
import com.expensemanager.app.data.repository.ExpenseRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: ExpenseRepository
    
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
        repository = ExpenseRepository.getInstance(requireContext())
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
        lifecycleScope.launch {
            try {
                // Clear database data
                val database = com.expensemanager.app.data.database.ExpenseDatabase.getDatabase(requireContext())
                
                // Clear all tables
                database.transactionDao().deleteAllTransactions()
                database.merchantDao().deleteAllMerchants()
                database.syncStateDao().deleteSyncState()
                // Note: We keep categories as they are system defaults
                
                // Clear all SharedPreferences files
                requireContext().getSharedPreferences("category_rules", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                requireContext().getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                requireContext().getSharedPreferences("budget_settings", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                requireContext().getSharedPreferences("export_settings", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                
                // Clear app settings (except basic preferences like currency, language)
                val basicSettings = mapOf(
                    "currency" to prefs.getString("currency", "INR"),
                    "language" to prefs.getString("language", "en"),
                    "auto_categorization" to prefs.getBoolean("auto_categorization", true),
                    "realtime_processing" to prefs.getBoolean("realtime_processing", true)
                )
                
                prefs.edit().clear().apply()
                
                // Restore basic settings
                prefs.edit().apply {
                    basicSettings.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Boolean -> putBoolean(key, value)
                        }
                    }
                }.apply()
                
                Toast.makeText(requireContext(), "✅ All transaction data and settings cleared successfully", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "❌ Error clearing data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun resetCategories() {
        lifecycleScope.launch {
            try {
                // Reset all merchant category assignments in database
                val merchants = repository.getAllMerchants()
                for (merchant in merchants) {
                    // Re-categorize each merchant using the smart categorization logic
                    val newCategoryName = recategorizeMerchant(merchant.displayName)
                    val newCategory = repository.getCategoryByName(newCategoryName)
                    
                    if (newCategory != null) {
                        val updatedMerchant = merchant.copy(categoryId = newCategory.id)
                        repository.updateMerchant(updatedMerchant)
                    }
                }
                
                // Clear learned category rules from SharedPreferences
                requireContext().getSharedPreferences("category_rules", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                
                // Reset exclusion states
                for (merchant in merchants) {
                    repository.updateMerchantExclusion(merchant.normalizedName, false)
                }
                
                Toast.makeText(requireContext(), "✅ Categories reset to defaults successfully", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "❌ Error resetting categories: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun recategorizeMerchant(merchantName: String): String {
        val nameUpper = merchantName.uppercase()
        
        return when {
            // Food & Dining
            nameUpper.contains("SWIGGY") || nameUpper.contains("ZOMATO") || 
            nameUpper.contains("DOMINOES") || nameUpper.contains("PIZZA") ||
            nameUpper.contains("MCDONALD") || nameUpper.contains("KFC") ||
            nameUpper.contains("RESTAURANT") || nameUpper.contains("CAFE") ||
            nameUpper.contains("FOOD") || nameUpper.contains("DINING") -> "Food & Dining"
            
            // Transportation
            nameUpper.contains("UBER") || nameUpper.contains("OLA") ||
            nameUpper.contains("TAXI") || nameUpper.contains("METRO") ||
            nameUpper.contains("BUS") || nameUpper.contains("TRANSPORT") -> "Transportation"
            
            // Groceries
            nameUpper.contains("BIGBAZAAR") || nameUpper.contains("DMART") ||
            nameUpper.contains("RELIANCE") || nameUpper.contains("GROCERY") ||
            nameUpper.contains("SUPERMARKET") || nameUpper.contains("FRESH") ||
            nameUpper.contains("MART") -> "Groceries"
            
            // Healthcare  
            nameUpper.contains("HOSPITAL") || nameUpper.contains("CLINIC") ||
            nameUpper.contains("PHARMACY") || nameUpper.contains("MEDICAL") ||
            nameUpper.contains("HEALTH") || nameUpper.contains("DOCTOR") -> "Healthcare"
            
            // Entertainment
            nameUpper.contains("MOVIE") || nameUpper.contains("CINEMA") ||
            nameUpper.contains("THEATRE") || nameUpper.contains("GAME") ||
            nameUpper.contains("ENTERTAINMENT") || nameUpper.contains("NETFLIX") ||
            nameUpper.contains("SPOTIFY") -> "Entertainment"
            
            // Shopping
            nameUpper.contains("AMAZON") || nameUpper.contains("FLIPKART") ||
            nameUpper.contains("MYNTRA") || nameUpper.contains("AJIO") ||
            nameUpper.contains("SHOPPING") || nameUpper.contains("STORE") -> "Shopping"
            
            // Utilities
            nameUpper.contains("ELECTRICITY") || nameUpper.contains("WATER") ||
            nameUpper.contains("GAS") || nameUpper.contains("INTERNET") ||
            nameUpper.contains("MOBILE") || nameUpper.contains("RECHARGE") -> "Utilities"
            
            else -> "Other"
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}