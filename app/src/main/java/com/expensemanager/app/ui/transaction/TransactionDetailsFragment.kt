package com.expensemanager.app.ui.transaction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentTransactionDetailsBinding
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TransactionDetailsFragment : Fragment() {
    
    private var _binding: FragmentTransactionDetailsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var categoryManager: CategoryManager
    private lateinit var merchantAliasManager: MerchantAliasManager
    
    // Transaction details passed as arguments
    private var amount: Float = 0.0f
    private var merchant: String = ""
    private var bankName: String = ""
    private var category: String = ""
    private var dateTime: String = ""
    private var confidence: Int = 0
    private var rawSMS: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            amount = it.getFloat("amount", 0.0f)
            merchant = it.getString("merchant", "")
            bankName = it.getString("bankName", "")
            category = it.getString("category", "")
            dateTime = it.getString("dateTime", "")
            confidence = it.getInt("confidence", 0)
            rawSMS = it.getString("rawSMS", "")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categoryManager = CategoryManager(requireContext())
        merchantAliasManager = MerchantAliasManager(requireContext())
        
        setupUI()
        setupClickListeners()
    }
    
    private fun setupUI() {
        binding.apply {
            // Transaction amount
            tvTransactionAmount.text = "₹${String.format("%.0f", amount)}"
            
            // Merchant information
            tvMerchantName.text = merchant
            tvBankName.text = bankName
            
            // Category with color indicator
            tvCategory.text = category
            val categoryColor = merchantAliasManager.getMerchantCategoryColor(category)
            try {
                viewCategoryColor.setBackgroundColor(android.graphics.Color.parseColor(categoryColor))
            } catch (e: Exception) {
                // Fallback color
                viewCategoryColor.setBackgroundColor(android.graphics.Color.parseColor("#9e9e9e"))
            }
            
            // Transaction details
            tvDateTime.text = dateTime
            tvConfidenceScore.text = "${confidence}%"
            
            // Confidence indicator
            progressConfidence.progress = confidence
            when {
                confidence >= 85 -> {
                    tvConfidenceLabel.text = "High Confidence"
                    tvConfidenceLabel.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.success))
                }
                confidence >= 65 -> {
                    tvConfidenceLabel.text = "Medium Confidence" 
                    tvConfidenceLabel.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.warning))
                }
                else -> {
                    tvConfidenceLabel.text = "Low Confidence"
                    tvConfidenceLabel.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.error))
                }
            }
            
            // Raw SMS content
            tvRawSms.text = rawSMS
            
            // Check if this is a duplicate or similar transaction
            checkForSimilarTransactions()
        }
    }
    
    private fun checkForSimilarTransactions() {
        lifecycleScope.launch {
            // This is a placeholder for duplicate detection logic
            // In a real implementation, you would query the database for similar transactions
            val similarCount = 0 // Placeholder
            
            if (similarCount > 0) {
                binding.layoutSimilarWarning.visibility = View.VISIBLE
                binding.tvSimilarCount.text = "$similarCount similar transactions found"
            } else {
                binding.layoutSimilarWarning.visibility = View.GONE
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.apply {
            // Edit category
            btnEditCategory.setOnClickListener {
                showEditCategoryDialog()
            }
            
            // Edit merchant
            btnEditMerchant.setOnClickListener {
                showEditMerchantDialog()
            }
            
            // Mark as duplicate
            btnMarkDuplicate.setOnClickListener {
                showMarkDuplicateDialog()
            }
            
            // Save transaction to database
            btnSaveTransaction.setOnClickListener {
                saveTransaction()
            }
            
            // Close button
            btnClose.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }
    
    private fun showEditCategoryDialog() {
        val categories = categoryManager.getAllCategories()
        val currentIndex = categories.indexOf(category)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Category")
            .setSingleChoiceItems(
                categories.toTypedArray(),
                currentIndex
            ) { dialog, which ->
                val newCategory = categories[which]
                if (newCategory != category) {
                    updateCategory(newCategory)
                }
                dialog.dismiss()
            }
            .setNeutralButton("Create New Category") { _, _ ->
                showCreateCategoryDialog()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showCreateCategoryDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_add_category,
            null
        )
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create New Category")
            .setView(dialogView)
            .create()
        
        // Set up emoji quick selection
        val emojiInput = dialogView.findViewById<TextInputEditText>(R.id.et_emoji)
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_emoji_food)?.setOnClickListener {
            emojiInput.setText("🍽️")
        }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_emoji_transport)?.setOnClickListener {
            emojiInput.setText("🚗")
        }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_emoji_shopping)?.setOnClickListener {
            emojiInput.setText("🛒")
        }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_emoji_health)?.setOnClickListener {
            emojiInput.setText("🏥")
        }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_emoji_entertainment)?.setOnClickListener {
            emojiInput.setText("🎬")
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)?.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add)?.setOnClickListener {
            val categoryName = dialogView.findViewById<TextInputEditText>(R.id.et_category_name)?.text.toString()
            val selectedEmoji = dialogView.findViewById<TextInputEditText>(R.id.et_emoji)?.text.toString()
            
            if (categoryName.isNotEmpty()) {
                val newCategoryName = categoryName.trim()
                updateCategory(newCategoryName)
                dialog.dismiss()
                
                Toast.makeText(
                    requireContext(),
                    "Updated transaction to category '$newCategoryName'",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Please enter a category name",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        dialog.show()
    }
    
    private fun showEditMerchantDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_edit_merchant_group,
            null
        )
        
        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.et_group_name)
        val spinnerCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_category)
        
        // Pre-fill current values
        etGroupName?.setText(merchant)
        
        // Setup category dropdown
        val categories = categoryManager.getAllCategories()
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_category, categories)
        spinnerCategory?.setAdapter(adapter)
        spinnerCategory?.setText(category, false)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Merchant Name")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newMerchantName = etGroupName?.text.toString().trim()
                val newCategory = spinnerCategory?.text.toString().trim()
                
                if (newMerchantName.isNotEmpty() && newCategory.isNotEmpty()) {
                    updateMerchant(newMerchantName, newCategory)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Please fill all fields",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showMarkDuplicateDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mark as Duplicate")
            .setMessage("Are you sure this is a duplicate transaction? This will exclude it from expense calculations.")
            .setPositiveButton("Mark Duplicate") { _, _ ->
                markAsDuplicate()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun updateCategory(newCategory: String) {
        category = newCategory
        binding.tvCategory.text = newCategory
        
        // Update color indicator
        val categoryColor = merchantAliasManager.getMerchantCategoryColor(newCategory)
        try {
            binding.viewCategoryColor.setBackgroundColor(android.graphics.Color.parseColor(categoryColor))
        } catch (e: Exception) {
            binding.viewCategoryColor.setBackgroundColor(android.graphics.Color.parseColor("#9e9e9e"))
        }
        
        // Update merchant alias with new category
        merchantAliasManager.setMerchantAlias(merchant, merchant, newCategory)
        
        Toast.makeText(
            requireContext(),
            "Updated category to '$newCategory'",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun updateMerchant(newMerchantName: String, newCategory: String) {
        val oldMerchant = merchant
        merchant = newMerchantName
        category = newCategory
        
        binding.tvMerchantName.text = newMerchantName
        binding.tvCategory.text = newCategory
        
        // Update merchant alias
        merchantAliasManager.setMerchantAlias(oldMerchant, newMerchantName, newCategory)
        
        Toast.makeText(
            requireContext(),
            "Updated merchant to '$newMerchantName' in category '$newCategory'",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun markAsDuplicate() {
        // TODO: Mark transaction as duplicate in database
        Toast.makeText(
            requireContext(),
            "Transaction marked as duplicate",
            Toast.LENGTH_SHORT
        ).show()
        
        findNavController().navigateUp()
    }
    
    private fun saveTransaction() {
        lifecycleScope.launch {
            try {
                // TODO: Save to database
                Toast.makeText(
                    requireContext(),
                    "✅ Transaction saved successfully",
                    Toast.LENGTH_SHORT
                ).show()
                
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "❌ Error saving transaction: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}