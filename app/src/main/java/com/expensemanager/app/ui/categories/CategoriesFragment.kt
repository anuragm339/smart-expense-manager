package com.expensemanager.app.ui.categories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentCategoriesBinding
import com.expensemanager.app.constants.Categories
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.data.dao.CategorySpendingResult
import com.expensemanager.app.utils.SMSHistoryReader
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.models.ParsedTransaction
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.ui.dashboard.ProcessedTransaction
import kotlinx.coroutines.launch
import java.util.*
import android.content.Context
import android.util.Log

@AndroidEntryPoint
class CategoriesFragment : Fragment() {
    
    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    
    // ViewModel injection
    private val viewModel: CategoriesViewModel by viewModels()
    
    // Keep repository and managers for fallback compatibility
    private lateinit var repository: ExpenseRepository
    private lateinit var categoriesAdapter: CategoriesAdapter
    private lateinit var merchantAliasManager: MerchantAliasManager
    private lateinit var categoryManager: CategoryManager

    private val merchantCategoryChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.expensemanager.MERCHANT_CATEGORY_CHANGED") {
                Log.d("CategoriesFragment", "Received merchant category change broadcast, refreshing categories.")
                viewModel.handleEvent(CategoriesUIEvent.Refresh)
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize legacy components for fallback compatibility
        repository = ExpenseRepository.getInstance(requireContext())
        merchantAliasManager = MerchantAliasManager(requireContext())
        categoryManager = CategoryManager(requireContext())
        
        setupRecyclerView()
        setupUI()
        setupClickListeners()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        categoriesAdapter = CategoriesAdapter(
            onCategoryClick = { categoryItem ->
                // Notify ViewModel of category selection for any business logic
                viewModel.handleEvent(CategoriesUIEvent.CategorySelected(categoryItem.name))
                
                // Navigate to category transactions
                val bundle = Bundle().apply {
                    putString("categoryName", categoryItem.name)
                }
                findNavController().navigate(
                    R.id.action_navigation_categories_to_navigation_category_transactions,
                    bundle
                )
            },
            onCategoryLongClick = { categoryItem, view ->
                showCategoryActionDialog(categoryItem, view)
            }
        )
        binding.recyclerCategories.apply {
            adapter = categoriesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun setupUI() {
        binding.tvTotalSpending.text = "Loading..."
        binding.tvCategoriesCount.text = "0 Categories"
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
    private fun updateUI(uiState: CategoriesUIState) {
        Log.d("CategoriesFragment", "Updating UI with ${uiState.categories.size} categories")
        
        // Update categories list
        categoriesAdapter.submitList(uiState.categories.toList()) // Create new list to trigger adapter update
        
        // Update summary text
        binding.tvTotalSpending.text = uiState.formattedTotalSpent
        binding.tvCategoriesCount.text = uiState.formattedCategoryCount
        
        // Handle loading states
        if (uiState.isInitialLoading) {
            binding.tvTotalSpending.text = "Loading..."
            binding.tvCategoriesCount.text = "Loading..."
        }
        
        // Handle error states
        if (uiState.shouldShowError && uiState.error != null) {
            Toast.makeText(requireContext(), uiState.error, Toast.LENGTH_SHORT).show()
            // Clear error after showing
            viewModel.handleEvent(CategoriesUIEvent.ClearError)
        }
        
        // Handle empty state
        if (uiState.shouldShowEmptyState) {
            binding.tvTotalSpending.text = "₹0"
            binding.tvCategoriesCount.text = "0 Categories"
        }
    }
    
    private fun loadRealCategoryData() {
        lifecycleScope.launch {
            try {
                Log.d("CategoriesFragment", "Loading category data from repository...")
                
                // Use the same date range as Dashboard (current month)
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                val endDate = calendar.time
                
                // Get category spending from repository (same as Dashboard)
                val categorySpendingResults = repository.getCategorySpending(startDate, endDate)
                
                Log.d("CategoriesFragment", "[ANALYTICS] Categories Date Range: ${startDate} to ${endDate}")
                Log.d("CategoriesFragment", "[ANALYTICS] Categories Raw Transactions Count: ${repository.getTransactionsByDateRange(startDate, endDate).size}")
                Log.d("CategoriesFragment", "Repository returned ${categorySpendingResults.size} category results")
                
                if (categorySpendingResults.isNotEmpty()) {
                    // Convert repository results to CategoryItem format
                    val transactionCategoryData = categorySpendingResults.map { categoryResult ->
                        val lastTransactionText = categoryResult.last_transaction_date?.let { formatLastTransaction(it) } ?: "No transactions"
                        
                        Log.d("CategoriesFragment", "Repository Category: ${categoryResult.category_name}, Amount: ₹${String.format("%.0f", categoryResult.total_amount)}, Count: ${categoryResult.transaction_count}")
                        
                        CategoryItem(
                            name = categoryResult.category_name,
                            emoji = getCategoryEmoji(categoryResult.category_name),
                            color = categoryResult.color,
                            amount = categoryResult.total_amount,
                            transactionCount = categoryResult.transaction_count,
                            lastTransaction = lastTransactionText,
                            percentage = 0, // Will be calculated after we have total
                            progress = 0    // Will be calculated after we have total
                        )
                    }
                    
                    // Add custom categories that might not have transactions yet
                    val allCategories = categoryManager.getAllCategories()
                    val existingCategoryNames = transactionCategoryData.map { it.name }
                    val missingCategories: List<CategoryItem> = allCategories.filter { !existingCategoryNames.contains(it) }
                        .map { categoryName: String ->
                            CategoryItem(
                                name = categoryName,
                                emoji = getCategoryEmoji(categoryName),
                                color = getRandomCategoryColor(),
                                amount = 0.0,
                                transactionCount = 0,
                                lastTransaction = "No transactions yet",
                                percentage = 0,
                                progress = 0
                            )
                        }
                    
                    val categoryData: List<CategoryItem> = (transactionCategoryData + missingCategories)
                        .sortedByDescending { categoryItem: CategoryItem -> categoryItem.amount }
                    
                    // Calculate percentages and progress
                    val totalSpent = categoryData.sumOf { categoryItem -> categoryItem.amount }
                    Log.d("CategoriesFragment", "[ANALYTICS] Categories Calculated Total: ₹${String.format("%.0f", totalSpent)}")
                    val categoriesWithPercentages = categoryData.map { categoryItem ->
                        val percentage = if (totalSpent > 0) ((categoryItem.amount / totalSpent) * 100).toInt() else 0
                        categoryItem.copy(
                            percentage = percentage,
                            progress = percentage.coerceAtMost(100)
                        )
                    }
                    
                    // Update UI
                    categoriesAdapter.submitList(categoriesWithPercentages)
                    binding.tvTotalSpending.text = "₹${String.format("%.0f", totalSpent)}"
                    binding.tvCategoriesCount.text = "${categoriesWithPercentages.size} Categories"
                    
                } else {
                    Log.d("CategoriesFragment", "No data in repository, falling back to SMS reading...")
                    loadCategoryDataFallback()
                }
                
            } catch (e: SecurityException) {
                // Permission denied - show empty state
                showEmptyState()
                Toast.makeText(requireContext(), "SMS permission required for real data", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Error loading data - show empty state
                showEmptyState()
                Toast.makeText(requireContext(), "Error loading category data", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadCategoryDataFallback() {
        lifecycleScope.launch {
            try {
                Log.d("CategoriesFragment", "[SMS] Loading category data from SMS as fallback...")
                
                // Trigger SMS sync if no data exists
                val syncedCount = repository.syncNewSMS()
                Log.d("CategoriesFragment", "📥 Synced $syncedCount new transactions from SMS")
                
                if (syncedCount > 0) {
                    // Now load data from repository
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val startDate = calendar.time

                    // Set end date to current time to exclude future transactions
                    val endDate = Calendar.getInstance().time
                    
                    val categorySpendingResults = repository.getCategorySpending(startDate, endDate)
                    
                    if (categorySpendingResults.isNotEmpty()) {
                        // Convert repository results to CategoryItem format
                        val transactionCategoryData = categorySpendingResults.map { categoryResult ->
                            val lastTransactionText = categoryResult.last_transaction_date?.let { formatLastTransaction(it) } ?: "No transactions"
                            
                            CategoryItem(
                                name = categoryResult.category_name,
                                emoji = getCategoryEmoji(categoryResult.category_name),
                                color = categoryResult.color,
                                amount = categoryResult.total_amount,
                                transactionCount = categoryResult.transaction_count,
                                lastTransaction = lastTransactionText,
                                percentage = 0,
                                progress = 0
                            )
                        }
                        
                        // Add missing categories and calculate percentages
                        val allCategories = categoryManager.getAllCategories()
                        val existingCategoryNames = transactionCategoryData.map { it.name }
                        val missingCategories = allCategories.filter { !existingCategoryNames.contains(it) }
                            .map { categoryName ->
                                CategoryItem(
                                    name = categoryName,
                                    emoji = getCategoryEmoji(categoryName),
                                    color = getRandomCategoryColor(),
                                    amount = 0.0,
                                    transactionCount = 0,
                                    lastTransaction = "No transactions yet",
                                    percentage = 0,
                                    progress = 0
                                )
                            }
                        
                        val categoryData = (transactionCategoryData + missingCategories)
                            .sortedByDescending { it.amount }
                        
                        val totalSpent = categoryData.sumOf { it.amount }
                        val categoriesWithPercentages = categoryData.map { categoryItem ->
                            val percentage = if (totalSpent > 0) ((categoryItem.amount / totalSpent) * 100).toInt() else 0
                            categoryItem.copy(
                                percentage = percentage,
                                progress = percentage.coerceAtMost(100)
                            )
                        }
                        
                        // Update UI
                        categoriesAdapter.submitList(categoriesWithPercentages)
                        binding.tvTotalSpending.text = "₹${String.format("%.0f", totalSpent)}"
                        binding.tvCategoriesCount.text = "${categoriesWithPercentages.size} Categories"
                    } else {
                        showEmptyState()
                    }
                } else {
                    showEmptyState()
                }
                
            } catch (e: SecurityException) {
                Log.w("CategoriesFragment", "SMS permission denied for fallback loading", e)
                showEmptyState()
            } catch (e: Exception) {
                Log.e("CategoriesFragment", "Error in fallback loading", e)
                showEmptyState()
            }
        }
    }
    
    private fun showEmptyState() {
        // Show only custom categories with ₹0 amounts - no fake data
        categoriesAdapter.submitList(getEmptyCategories())
        binding.tvTotalSpending.text = "₹0"
        binding.tvCategoriesCount.text = "0 Categories"
    }
    
    private fun getCategoryEmoji(category: String): String {
        return when (category.lowercase()) {
            "food & dining", "food", "dining" -> "🍽️"
            "transportation", "transport" -> "🚗"
            "groceries", "grocery" -> "🛒"
            "healthcare", "health" -> "🏥"
            "entertainment" -> "🎬"
            "shopping" -> "🛍️"
            "utilities" -> "⚡"
            else -> "📂"
        }
    }
    
    private fun formatLastTransaction(date: Date): String {
        val now = Date()
        val diffInMs = now.time - date.time
        val diffInHours = diffInMs / (1000 * 60 * 60)
        val diffInDays = diffInMs / (1000 * 60 * 60 * 24)
        
        return when {
            diffInHours < 1 -> "Just now"
            diffInHours < 24 -> "$diffInHours hours ago"
            diffInDays == 1L -> "Yesterday"
            diffInDays < 7 -> "$diffInDays days ago"
            else -> "${diffInDays / 7} weeks ago"
        }
    }
    
    private fun setupClickListeners() {
        binding.fabQuickExpense.setOnClickListener {
            showQuickAddDialog()
        }
    }
    
    private fun showAddCategoryDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_add_category, 
            null
        )
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Category")
            .setView(dialogView)
            .create()
        
        // Set up emoji quick selection
        val emojiInput = dialogView.findViewById<TextInputEditText>(R.id.et_emoji)
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_food).setOnClickListener {
            emojiInput.setText("🍽️")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_transport).setOnClickListener {
            emojiInput.setText("🚗")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_shopping).setOnClickListener {
            emojiInput.setText("🛒")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_health).setOnClickListener {
            emojiInput.setText("🏥")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_entertainment).setOnClickListener {
            emojiInput.setText("🎬")
        }
        
        // Set up click listeners
        dialogView.findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<MaterialButton>(R.id.btn_add).setOnClickListener {
            val categoryName = dialogView.findViewById<TextInputEditText>(R.id.et_category_name).text.toString()
            val selectedEmoji = dialogView.findViewById<TextInputEditText>(R.id.et_emoji).text.toString()
            
            if (categoryName.isNotEmpty()) {
                addNewCategory(categoryName, selectedEmoji.ifEmpty { "📂" })
                dialog.dismiss()
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
    
    private fun addNewCategory(name: String, emoji: String) {
        // Use ViewModel to handle category addition
        viewModel.handleEvent(CategoriesUIEvent.AddCategory(name, emoji))
        
        Toast.makeText(
            requireContext(),
            "Category '$name' added successfully!",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Ensure "Money" category is available (commonly needed category)
        val allCategories = categoryManager.getAllCategories()
        if (!allCategories.contains("Money")) {
            categoryManager.addCustomCategory("Money")
            Log.d("CategoriesFragment", "Added 'Money' category to CategoryManager")
        }
        
        // Trigger ViewModel refresh when returning to this screen
        viewModel.handleEvent(CategoriesUIEvent.Refresh)
    }
    
    private fun getRandomCategoryColor(): String {
        val colors = listOf(
            "#ff5722", "#3f51b5", "#4caf50", "#e91e63", 
            "#ff9800", "#9c27b0", "#607d8b", "#795548",
            "#2196f3", "#8bc34a", "#ffc107", "#673ab7"
        )
        return colors.random()
    }
    
    private fun showQuickAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_quick_add_expense, 
            null
        )
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        
        // Set up category dropdown
        val categorySpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_category)
        val categories = arrayOf("Food & Dining", "Transportation", "Healthcare", "Groceries", "Entertainment", "Shopping", "Utilities", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        categorySpinner.setAdapter(adapter)
        
        // Set up click listeners
        dialogView.findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<MaterialButton>(R.id.btn_add).setOnClickListener {
            val amount = dialogView.findViewById<TextInputEditText>(R.id.et_amount).text.toString()
            val merchant = dialogView.findViewById<TextInputEditText>(R.id.et_merchant).text.toString()
            val category = categorySpinner.text.toString()
            
            if (amount.isNotEmpty() && merchant.isNotEmpty()) {
                try {
                    val amountValue = amount.toDouble()
                    // Use ViewModel to handle quick expense addition
                    viewModel.handleEvent(CategoriesUIEvent.QuickAddExpense(amountValue, merchant, category))
                    
                    Toast.makeText(
                        requireContext(), 
                        "Added: ₹$amount at $merchant ($category)", 
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                } catch (e: NumberFormatException) {
                    Toast.makeText(
                        requireContext(), 
                        "Please enter a valid amount", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(), 
                    "Please fill all fields", 
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Get empty categories (only custom categories with ₹0 amounts)
     */
    private fun getEmptyCategories(): List<CategoryItem> {
        Log.d("CategoriesFragment", "Showing empty categories state - no dummy data")
        
        // Only show custom categories with ₹0 amounts - no fake data
        val customCategories = categoryManager.getAllCategories()
        
        return customCategories.map { categoryName ->
            CategoryItem(
                name = categoryName,
                emoji = getCategoryEmoji(categoryName),
                color = getRandomCategoryColor(),
                amount = 0.0,
                transactionCount = 0,
                lastTransaction = "No transactions yet",
                percentage = 0,
                progress = 0
            )
        }
    }
    
    private fun filterTransactionsByInclusionState(transactions: List<ProcessedTransaction>): List<ProcessedTransaction> {
        // Load inclusion states from SharedPreferences
        val prefs = requireContext().getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
        val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
        
        if (inclusionStatesJson != null) {
            try {
                val inclusionStates = org.json.JSONObject(inclusionStatesJson)
                return transactions.filter { transaction ->
                    val displayMerchant = transaction.displayMerchant
                    if (inclusionStates.has(displayMerchant)) {
                        inclusionStates.getBoolean(displayMerchant)
                    } else {
                        true // Default to included if not found
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("CategoriesFragment", "Error loading inclusion states", e)
            }
        }
        
        // Return all transactions if no inclusion states found
        return transactions
    }
    
    private fun showCategoryActionDialog(categoryItem: CategoryItem, anchorView: android.view.View) {
        Log.d("CategoriesFragment", "Long press detected for category: ${categoryItem.name}")
        
        // Check if this is a system/predefined category
        if (Categories.isSystemCategory(categoryItem.name)) {
            Log.d("CategoriesFragment", "Category '${categoryItem.name}' is a system category - actions not allowed")
            Log.d("CategoriesFragment", "System categories: ${Categories.DEFAULT_CATEGORIES}")
            Toast.makeText(
                requireContext(),
                "Cannot modify system category '${categoryItem.name}'",
                Toast.LENGTH_SHORT
            ).show()
            return
        } else {
            Log.d("CategoriesFragment", "Category '${categoryItem.name}' is a custom category - allowing actions")
        }
        
        try {
            // Use BottomSheetDialog for better UX and proper positioning
            showCategoryActionBottomSheet(categoryItem)
            
        } catch (e: Exception) {
            Log.e("CategoriesFragment", "PopupMenu failed, trying fallback dialog", e)
            
            // Fallback to a simple bottom sheet style dialog
            try {
                val options = arrayOf("Rename Category", "Delete Category", "Cancel")
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("${categoryItem.name} Actions")
                    .setItems(options) { dialog, which ->
                        Log.d("CategoriesFragment", "Fallback dialog action selected: $which")
                        when (which) {
                            0 -> {
                                Log.d("CategoriesFragment", "Rename selected from fallback")
                                showRenameCategoryDialog(categoryItem)
                            }
                            1 -> {
                                Log.d("CategoriesFragment", "Delete selected from fallback")  
                                showDeleteCategoryDialog(categoryItem)
                            }
                            2 -> {
                                Log.d("CategoriesFragment", "Cancel selected from fallback")
                                dialog.dismiss()
                            }
                        }
                    }
                    .setCancelable(true)
                    .show()
                    
                Log.d("CategoriesFragment", "Fallback dialog shown")
                
            } catch (fallbackError: Exception) {
                Log.e("CategoriesFragment", "Both popup and fallback dialog failed", fallbackError)
                
                // Last resort - Toast with instructions
                Toast.makeText(
                    requireContext(),
                    "Long press detected on ${categoryItem.name}. Use menu options to rename/delete.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Show a modern BottomSheetDialog for category actions
     */
    private fun showCategoryActionBottomSheet(categoryItem: CategoryItem) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_category_actions, null)
        
        // Set up category info
        view.findViewById<android.widget.TextView>(R.id.tv_category_name)?.text = categoryItem.name
        view.findViewById<android.widget.TextView>(R.id.tv_category_emoji)?.text = categoryItem.emoji
        
        // Set up action buttons
        view.findViewById<MaterialButton>(R.id.btn_rename)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            showRenameCategoryDialog(categoryItem)
        }
        
        view.findViewById<MaterialButton>(R.id.btn_delete)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            showDeleteCategoryDialog(categoryItem)
        }
        
        view.findViewById<MaterialButton>(R.id.btn_cancel)?.setOnClickListener {
            bottomSheetDialog.dismiss()
        }
        
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
        
        Log.d("CategoriesFragment", "BottomSheet dialog shown for category: ${categoryItem.name}")
    }
    
    private fun showRenameCategoryDialog(categoryItem: CategoryItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_add_category, 
            null
        )
        
        // Pre-fill current values
        val categoryNameInput = dialogView.findViewById<TextInputEditText>(R.id.et_category_name)
        val emojiInput = dialogView.findViewById<TextInputEditText>(R.id.et_emoji)
        categoryNameInput.setText(categoryItem.name)
        emojiInput.setText(categoryItem.emoji)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Category")
            .setView(dialogView)
            .create()
        
        // Update button text and icon for rename operation
        val addButton = dialogView.findViewById<MaterialButton>(R.id.btn_add)
        addButton.text = "Rename"
        addButton.icon = null // Remove the add icon for rename operation
        
        // Set up emoji quick selection
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_food).setOnClickListener {
            emojiInput.setText("🍽️")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_transport).setOnClickListener {
            emojiInput.setText("🚗")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_shopping).setOnClickListener {
            emojiInput.setText("🛒")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_health).setOnClickListener {
            emojiInput.setText("🏥")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_entertainment).setOnClickListener {
            emojiInput.setText("🎬")
        }
        
        // Set up click listeners
        dialogView.findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<MaterialButton>(R.id.btn_add).setOnClickListener {
            val newName = categoryNameInput.text.toString().trim()
            val newEmoji = emojiInput.text.toString().trim()
            
            if (newName.isNotEmpty() && (newName != categoryItem.name || newEmoji != categoryItem.emoji)) {
                Log.d("CategoriesFragment", "Triggering rename from '${categoryItem.name}' to '$newName' with emoji '$newEmoji'")
                viewModel.handleEvent(CategoriesUIEvent.RenameCategory(categoryItem.name, newName, newEmoji))
                val message = if (newName != categoryItem.name) {
                    "Updating category to '$newName'..."
                } else {
                    "Updating category emoji..."
                }
                Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            } else if (newName.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Please enter a category name",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        dialog.show()
    }
    
    private fun showDeleteCategoryDialog(categoryItem: CategoryItem) {
        Log.d("CategoriesFragment", "Showing delete dialog for category: ${categoryItem.name}")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete '${categoryItem.name}' category?\n\nAll transactions will be moved to 'Other' category. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                Log.d("CategoriesFragment", "Triggering delete for category: ${categoryItem.name}")
                viewModel.handleEvent(CategoriesUIEvent.DeleteCategory(categoryItem.name))
                Toast.makeText(
                    requireContext(),
                    "Deleting category '${categoryItem.name}'...",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d("CategoriesFragment", "Delete dialog cancelled")
                dialog.dismiss()
            }
            .show()
    }
    
    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(merchantCategoryChangeReceiver)
            Log.d("CategoriesFragment", "Unregistered broadcast receiver for merchant category changes.")
        } catch (e: Exception) {
            Log.w("CategoriesFragment", "Broadcast receiver not registered, ignoring unregister.", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
