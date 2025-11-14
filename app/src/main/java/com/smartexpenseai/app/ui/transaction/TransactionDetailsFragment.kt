package com.smartexpenseai.app.ui.transaction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import com.smartexpenseai.app.R
import com.smartexpenseai.app.databinding.FragmentTransactionDetailsBinding
import com.smartexpenseai.app.utils.CategoryManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
class TransactionDetailsFragment : Fragment() {
    
    private var _binding: FragmentTransactionDetailsBinding? = null
    private val binding get() = _binding!!
    
    // ViewModel injection
    private val viewModel: TransactionDetailsViewModel by viewModels()
    
    // Keep managers for fallback compatibility
    private lateinit var categoryManager: CategoryManager
    
    // Transaction details passed as arguments (kept for backward compatibility)
    private var amount: Float = 0.0f
    private var merchant: String = ""
    private var bankName: String = ""
    private var category: String = ""
    private var transactionDate: Long = 0L
    private var confidence: Int = 0
    private var rawSMS: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            amount = it.getFloat("amount", 0.0f)
            merchant = it.getString("merchant", "")
            bankName = it.getString("bankName", "")
            category = it.getString("category", "")
            transactionDate = it.getLong("transactionDate", 0L)
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
        
        viewLifecycleOwner.lifecycleScope.launch {
            // Initialize legacy components for fallback compatibility
            val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
            val merchantRuleEngine = com.smartexpenseai.app.parsing.engine.MerchantRuleEngine(requireContext())
            categoryManager = CategoryManager(requireContext(), repository, merchantRuleEngine)
            
            setupUI()
            setupClickListeners()
            observeViewModel()
            
            // Set transaction data in ViewModel
            viewModel.setTransactionData(
                amount = amount,
                merchant = merchant,
                bankName = bankName,
                category = category,
                transactionDate = transactionDate,
                confidence = confidence,
                rawSMS = rawSMS
            )
        }
    }
    
    /**
     * Observe ViewModel state changes using the proven pattern
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    updateUI(uiState)
                }
            }
        }
    }
    
    /**
     * Update UI based on ViewModel state
     */
    private fun updateUI(uiState: TransactionDetailsUIState) {
        val transactionData = uiState.transactionData
        
        if (transactionData != null) {
            binding.apply {
                // Transaction amount
                tvTransactionAmount.text = uiState.formattedAmount
                
                // Merchant information
                tvMerchantName.text = transactionData.merchant
                tvBankName.text = transactionData.bankName
                
                // Category with color indicator
                tvCategory.text = transactionData.category
                try {
                    viewCategoryColor.setBackgroundColor(android.graphics.Color.parseColor(transactionData.categoryColor))
                } catch (e: Exception) {
                    viewCategoryColor.setBackgroundColor(android.graphics.Color.parseColor("#9e9e9e"))
                }
                
                // Transaction details
                tvDateTime.text = transactionData.dateTime
                tvConfidenceScore.text = "${transactionData.confidence}%"
                
                // Confidence indicator
                progressConfidence.progress = transactionData.confidence
                tvConfidenceLabel.text = uiState.confidenceLabel
                
                val confidenceColorRes = when (uiState.confidenceColor) {
                    "success" -> R.color.success
                    "warning" -> R.color.warning
                    else -> R.color.error
                }
                tvConfidenceLabel.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), confidenceColorRes))
                
                // Raw SMS content
                tvRawSms.text = transactionData.rawSMS
                
                // Similar transactions warning
                if (uiState.showSimilarWarning) {
                    layoutSimilarWarning.visibility = View.VISIBLE
                    tvSimilarCount.text = "${uiState.similarTransactionsCount} similar transactions found"
                } else {
                    layoutSimilarWarning.visibility = View.GONE
                }
            }
        }
        
        // Handle loading states
        if (uiState.isAnyLoading) {
            // Show loading indicator if needed
        }
        
        // Handle error states
        if (uiState.shouldShowError && uiState.error != null) {
            Toast.makeText(requireContext(), uiState.error, Toast.LENGTH_LONG).show()
            viewModel.handleEvent(TransactionDetailsUIEvent.ClearError)
        }

        // Handle success messages
        if (uiState.successMessage != null) {
            Toast.makeText(requireContext(), uiState.successMessage, Toast.LENGTH_LONG).show()
            viewModel.handleEvent(TransactionDetailsUIEvent.ClearSuccess)
        }
    }

    private fun setupUI() {
        // Initial setup - actual data will come from ViewModel
        binding.apply {
            // Set loading state initially
            tvTransactionAmount.text = "Loading..."
            tvMerchantName.text = "Loading..."
            tvBankName.text = "Loading..."
        }
    }
    
    private fun checkForSimilarTransactions() {
        // Use ViewModel to check for similar transactions
        viewModel.handleEvent(TransactionDetailsUIEvent.CheckSimilarTransactions)
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
                viewModel.handleEvent(TransactionDetailsUIEvent.SaveTransaction)
            }
            
            // Close button
            btnClose.setOnClickListener {
                viewModel.handleEvent(TransactionDetailsUIEvent.NavigateBack)
                findNavController().navigateUp()
            }
        }
    }
    
    private fun showEditCategoryDialog() {
        lifecycleScope.launch {
            val categories = viewModel.getAllCategories()
            val currentCategory = viewModel.uiState.value.transactionData?.category ?: ""
            val currentIndex = categories.indexOf(currentCategory)
        
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Change Category")
                .setSingleChoiceItems(
                    categories.toTypedArray(),
                    currentIndex
                ) { dialog, which ->
                    val newCategory = categories[which]
                    if (newCategory != currentCategory) {
                        viewModel.handleEvent(TransactionDetailsUIEvent.UpdateCategory(newCategory))
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
                viewModel.handleEvent(TransactionDetailsUIEvent.CreateNewCategory(newCategoryName))
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
        lifecycleScope.launch {
            val dialogView = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_edit_merchant_group,
                null
            )

            val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.et_group_name)
            val spinnerCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_category)

            val currentTransaction = viewModel.uiState.value.transactionData

            // Pre-fill current values
            etGroupName?.setText(currentTransaction?.merchant ?: "")

            // Setup category dropdown
            val categories = viewModel.getAllCategories()
            val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_category, categories)
            spinnerCategory?.setAdapter(adapter)
            spinnerCategory?.setText(currentTransaction?.category ?: "", false)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Merchant Name")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    val newMerchantName = etGroupName?.text.toString().trim()
                    val newCategory = spinnerCategory?.text.toString().trim()

                    if (newMerchantName.isNotEmpty() && newCategory.isNotEmpty()) {
                        viewModel.handleEvent(TransactionDetailsUIEvent.UpdateMerchant(newMerchantName, newCategory))
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
    }
    
    private fun showMarkDuplicateDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mark as Duplicate")
            .setMessage("Are you sure this is a duplicate transaction? This will exclude it from expense calculations.")
            .setPositiveButton("Mark Duplicate") { _, _ ->
                viewModel.handleEvent(TransactionDetailsUIEvent.MarkAsDuplicate)
                findNavController().navigateUp()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}