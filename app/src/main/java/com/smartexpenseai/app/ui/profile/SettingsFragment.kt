package com.smartexpenseai.app.ui.profile

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smartexpenseai.app.R
import com.smartexpenseai.app.databinding.FragmentSettingsBinding
import com.smartexpenseai.app.data.repository.ExpenseRepository
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.smartexpenseai.app.data.entities.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Date

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

        binding.switchAutoTag.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_tag_similar", isChecked).apply()
            Toast.makeText(
                requireContext(),
                if (isChecked) "Auto-tagging enabled" else "Auto-tagging disabled",
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

        binding.layoutManageTags.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_settings_to_navigation_tag_management)
        }
    }
    
    private fun loadSettings() {
        // The current parser and UI are INR/English-specific. Keep persisted state
        // aligned with capabilities instead of presenting non-functional choices.
        if (prefs.getString("currency", "INR") != "INR" ||
            prefs.getString("language", "en") != "en") {
            prefs.edit()
                .putString("currency", "INR")
                .putString("language", "en")
                .apply()
        }

        // Load saved settings
        binding.switchAutoCategorization.isChecked = prefs.getBoolean("auto_categorization", true)
        binding.switchRealtimeProcessing.isChecked = prefs.getBoolean("realtime_processing", true)
        binding.switchAutoTag.isChecked = prefs.getBoolean("auto_tag_similar", true)
        
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Currency")
            .setMessage("Indian Rupee (₹) is currently the supported transaction currency.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showLanguageDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Language")
            .setMessage("English is currently the supported application language.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showClearDataConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear All Data")
            .setMessage("This will permanently delete transactions, custom categories, budgets, tags, AI history, and settings. Your signed-in account and built-in categories will remain. This cannot be undone.")
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
                val appContext = requireContext().applicationContext
                val database = com.smartexpenseai.app.data.database.ExpenseDatabase.getDatabase(appContext)
                withContext(Dispatchers.IO) {
                    database.withTransaction {
                        database.tagDao().deleteAllTransactionTags()
                        database.transactionDao().deleteAllTransactions()
                        database.merchantDao().deleteAllMerchants()
                        database.budgetDao().deleteAllBudgets()
                        database.tagDao().deleteAllTags()
                        database.aiCallDao().clearAllTracking()
                        database.categoryDao().deleteUserCategories()
                        database.syncStateDao().insertOrUpdateSyncState(
                            SyncStateEntity(
                                // Clearing expense data is not a request to re-import the
                                // device's entire SMS history on the next app launch. Move the
                                // incremental cursor forward; users can still explicitly run a
                                // full SMS resync from the Messages screen when they want it.
                                lastSmsSyncTimestamp = Date(),
                                lastSmsId = null,
                                totalTransactions = 0,
                                lastFullSync = Date(),
                                syncStatus = "COMPLETED"
                            )
                        )
                    }

                    listOf(
                        "ai_insights_cache",
                        "ai_insights_offline_cache",
                        "ai_insights_prefs",
                        "app_settings",
                        "auth_state",
                        "budget_settings",
                        "category_rules",
                        "expense_calculations",
                        "export_settings",
                        "notification_settings",
                        "onboarding_preferences",
                        "recurring_preferences",
                        "transactions"
                    ).forEach { name ->
                        appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                            .edit()
                            .clear()
                            .commit()
                    }
                }

                loadSettings()
                Toast.makeText(requireContext(), "All expense data and settings were cleared", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error clearing data: ${e.message}", Toast.LENGTH_LONG).show()
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
                
                Toast.makeText(requireContext(), "Categories reset to defaults successfully", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error resetting categories: ${e.message}", Toast.LENGTH_LONG).show()
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
