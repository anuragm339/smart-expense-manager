package com.expensemanager.app.ui.categories

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.expensemanager.app.databinding.FragmentCategoryTransactionsBinding
import com.expensemanager.app.ui.messages.MessageItem
import com.expensemanager.app.ui.messages.MessagesAdapter
import com.expensemanager.app.ui.categories.CategoryDisplayProvider
import com.expensemanager.app.ui.categories.getEmojiString
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.utils.SMSHistoryReader
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class CategoryTransactionsFragment : Fragment() {
    
    private var _binding: FragmentCategoryTransactionsBinding? = null
    private val binding get() = _binding!!
    
    // ViewModel injection
    private val viewModel: CategoryTransactionsViewModel by viewModels()
    
    // Injected dependencies
    @Inject
    lateinit var categoryDisplayProvider: CategoryDisplayProvider
    
    // Keep legacy components for fallback compatibility
    private lateinit var categoryName: String
    private lateinit var transactionsAdapter: MessagesAdapter
    private lateinit var categoryManager: CategoryManager
    private lateinit var merchantAliasManager: MerchantAliasManager
    private lateinit var repository: ExpenseRepository
    
    // Legacy filter and sort state (now handled by ViewModel)
    private var currentSortOption = "Newest First"
    private var currentFilterOption = "This Month"
    private var allTransactions = mutableListOf<MessageItem>()
    
    // Broadcast receiver for new transaction notifications
    private val newTransactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.expensemanager.NEW_TRANSACTION_ADDED") {
                val merchant = intent.getStringExtra("merchant") ?: "Unknown"
                val amount = intent.getDoubleExtra("amount", 0.0)
                android.util.Log.d("CategoryTransactions", "Received new transaction broadcast: $merchant - ₹${String.format("%.0f", amount)}")
                
                // Refresh category transactions data on the main thread using ViewModel
                lifecycleScope.launch {
                    try {
                        android.util.Log.d("CategoryTransactions", "[PROCESS] Refreshing category transactions due to new transaction")
                        viewModel.handleEvent(CategoryTransactionsUIEvent.Refresh)
                        
                    } catch (e: Exception) {
                        android.util.Log.e("CategoryTransactions", "Error refreshing category transactions after new transaction", e)
                    }
                }
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize legacy components for fallback compatibility
        categoryName = arguments?.getString("categoryName") ?: "Unknown"
        categoryManager = CategoryManager(requireContext())
        merchantAliasManager = MerchantAliasManager(requireContext())
        repository = ExpenseRepository.getInstance(requireContext())
        
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        // Set category name in ViewModel and start loading data
        viewModel.handleEvent(CategoryTransactionsUIEvent.SetCategoryName(categoryName))
    }
    
    private fun setupUI() {
        binding.tvCategoryName.text = categoryName
        
        // Set category color
        val categoryColor = categoryManager.getCategoryColor(categoryName)
        try {
            binding.viewCategoryColor.setBackgroundColor(Color.parseColor(categoryColor))
        } catch (e: Exception) {
            // Fallback to default color
        }
        
        // Initialize button text
        binding.btnFilterDate.text = currentFilterOption
        binding.btnSort.text = currentSortOption
    }
    
    private fun setupRecyclerView() {
        transactionsAdapter = MessagesAdapter { messageItem ->
            viewModel.handleEvent(CategoryTransactionsUIEvent.ShowCategoryEditDialog(messageItem))
            showCategoryEditDialog(messageItem)
        }
        binding.recyclerTransactions.apply {
            adapter = transactionsAdapter
            layoutManager = LinearLayoutManager(requireContext())
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
    private fun updateUI(uiState: CategoryTransactionsUIState) {
        // Update category name and color
        binding.tvCategoryName.text = uiState.categoryName
        try {
            binding.viewCategoryColor.setBackgroundColor(Color.parseColor(uiState.categoryColor))
        } catch (e: Exception) {
            // Fallback to default color
        }
        
        // Update filter and sort buttons
        binding.btnFilterDate.text = uiState.currentFilterOption
        binding.btnSort.text = uiState.currentSortOption
        
        // Update transactions list
        transactionsAdapter.submitList(uiState.transactions)
        
        // Update summary
        binding.tvCategoryTotal.text = uiState.formattedTotalAmount
        binding.tvCategorySummary.text = uiState.formattedSummary
        
        // Show/hide content based on state
        when {
            uiState.shouldShowContent -> {
                binding.recyclerTransactions.visibility = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE
            }
            uiState.shouldShowEmptyState -> {
                binding.recyclerTransactions.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
            }
        }
        
        // Handle loading states
        if (uiState.isInitialLoading) {
            binding.tvCategoryTotal.text = "Loading..."
            binding.tvCategorySummary.text = "Loading..."
        }
        
        // Handle error states
        if (uiState.shouldShowError && uiState.error != null) {
            android.widget.Toast.makeText(requireContext(), uiState.error, android.widget.Toast.LENGTH_LONG).show()
            viewModel.handleEvent(CategoryTransactionsUIEvent.ClearError)
        }
        
        // Handle success states
        if (uiState.shouldShowSuccess && uiState.successMessage != null) {
            android.widget.Toast.makeText(requireContext(), uiState.successMessage, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.handleEvent(CategoryTransactionsUIEvent.ClearSuccess)
        }
        
        // Update legacy state for backwards compatibility
        currentSortOption = uiState.currentSortOption
        currentFilterOption = uiState.currentFilterOption
    }

    private fun setupClickListeners() {
        binding.btnFilterDate.setOnClickListener {
            showDateFilterDialog()
        }
        
        binding.btnSort.setOnClickListener {
            showSortDialog()
        }
    }
    
    private fun loadCategoryTransactions() {
        lifecycleScope.launch {
            try {
                android.util.Log.d("CategoryTransactions", "[DEBUG] Loading transactions for category: $categoryName")
                
                // Get current date range (this month)
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endDate = calendar.time
                
                // Load transactions from repository (SQLite database)
                val allDbTransactions = repository.getTransactionsByDateRange(startDate, endDate)
                android.util.Log.d("CategoryTransactions", "[ANALYTICS] Found ${allDbTransactions.size} total transactions")
                
                // Filter transactions by category using the merchant-category mapping
                val categoryTransactions = allDbTransactions.mapNotNull { transaction ->
                    val merchantWithCategory = repository.getMerchantWithCategory(transaction.normalizedMerchant)
                    val transactionCategory = merchantWithCategory?.category_name ?: "Other"
                    
                    if (transactionCategory == categoryName) {
                        MessageItem(
                            amount = transaction.amount,
                            merchant = merchantAliasManager.getDisplayName(transaction.rawMerchant), // Apply alias lookup
                            bankName = transaction.bankName,
                            category = transactionCategory,
                            categoryColor = merchantWithCategory?.category_color ?: "#888888",
                            confidence = (transaction.confidenceScore * 100).toInt(),
                            dateTime = formatDate(transaction.transactionDate),
                            rawSMS = transaction.rawSmsBody,
                            isDebit = transaction.isDebit,
                            rawMerchant = transaction.rawMerchant // FIXED: Pass the original raw merchant name
                        )
                    } else null
                }
                
                android.util.Log.d("CategoryTransactions", "Filtered to ${categoryTransactions.size} transactions for $categoryName")
                
                // Store all transactions for filtering/sorting
                allTransactions.clear()
                allTransactions.addAll(categoryTransactions)
                
                // Apply current filter and sort
                val filteredAndSortedTransactions = applyFilterAndSort(allTransactions)
                
                if (filteredAndSortedTransactions.isNotEmpty()) {
                    transactionsAdapter.submitList(filteredAndSortedTransactions)
                    binding.recyclerTransactions.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                    
                    // Update summary
                    val totalAmount = filteredAndSortedTransactions.sumOf { it.amount }
                    binding.tvCategoryTotal.text = "₹${String.format("%.0f", totalAmount)}"
                    binding.tvCategorySummary.text = "${filteredAndSortedTransactions.size} transactions • $currentFilterOption"
                    
                    android.util.Log.d("CategoryTransactions", "[FINANCIAL] Category total: ₹${String.format("%.0f", totalAmount)}")
                } else {
                    binding.recyclerTransactions.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.tvCategoryTotal.text = "₹0"
                    binding.tvCategorySummary.text = "0 transactions • $currentFilterOption"
                    
                    android.util.Log.d("CategoryTransactions", "[INFO] No transactions found for $categoryName")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("CategoryTransactions", "[ERROR] Error loading transactions", e)
                // Show empty state on error
                binding.recyclerTransactions.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.tvCategoryTotal.text = "₹0"
                binding.tvCategorySummary.text = "0 transactions • This month"
            }
        }
    }
    
    
    private fun showCategoryEditDialog(messageItem: MessageItem) {
        lifecycleScope.launch {
            try {
                // Get categories from ViewModel (which handles database and fallbacks)
                val categoryNames = viewModel.getAllCategories()
                android.util.Log.d("CategoryDialog", "[ANALYTICS] Using ViewModel categories: ${categoryNames.joinToString(", ")}")
                
                // ViewModel already handles database and fallbacks
                if (categoryNames.isNotEmpty()) {
                    val currentCategoryIndex = categoryNames.indexOf(messageItem.category)
                    android.util.Log.d("CategoryDialog", "[TARGET] Current category: ${messageItem.category}, Index: $currentCategoryIndex")
                    
                    // Create enhanced category list with emojis for better visibility using display provider
                    val enhancedCategories = categoryNames.map { category ->
                        categoryDisplayProvider.formatForDisplay(category)
                    }.toTypedArray()
                    
                    android.util.Log.d("CategoryDialog", "Enhanced categories: ${enhancedCategories.joinToString(", ")}")
                    
                    // Use custom DialogFragment for guaranteed visibility
                    android.util.Log.d("CategoryDialog", "[TARGET] Creating custom DialogFragment...")
                    
                    val dialog = CategorySelectionDialogFragment.newInstance(
                        categories = enhancedCategories,
                        currentIndex = currentCategoryIndex,
                        merchantName = messageItem.merchant
                    ) { selectedCategory ->
                        android.util.Log.d("CategoryDialog", "[SUCCESS] Custom dialog selected: $selectedCategory")
                        
                        // Find the corresponding plain category name from the original list
                        val selectedIndex = enhancedCategories.indexOf(selectedCategory)
                        val plainCategoryName = if (selectedIndex >= 0 && selectedIndex < categoryNames.size) {
                            categoryNames[selectedIndex]
                        } else {
                            // Fallback: extract from display format
                            categoryDisplayProvider.getDisplayName(selectedCategory)
                        }
                        android.util.Log.d("CategoryDialog", "[FIX] Mapped '$selectedCategory' to database name: '$plainCategoryName'")
                        
                        updateCategoryForMerchant(messageItem, plainCategoryName)
                    }
                    
                    dialog.show(parentFragmentManager, "CategorySelectionDialog")
                    android.util.Log.d("CategoryDialog", "[SMS] Custom DialogFragment shown")
                }
                    
            } catch (e: Exception) {
                android.util.Log.e("CategoryDialog", "[ERROR] Error loading categories for dialog", e)
                
                // Multiple fallback approaches using display provider
                val fallbackCategoryNames = listOf(
                    "Food & Dining", "Transportation", "Groceries", 
                    "Healthcare", "Entertainment", "Shopping", 
                    "Utilities", "Other"
                )
                val enhancedFallback = fallbackCategoryNames.map { category ->
                    categoryDisplayProvider.formatForDisplay(category)
                }
                val currentIndex = fallbackCategoryNames.indexOf(messageItem.category)
                
                android.util.Log.d("CategoryDialog", "[FIX] Using fallback categories, current index: $currentIndex")
                
                try {
                    // Try enhanced fallback first
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("📝 Change Category")
                        .setMessage("Select a new category for ${messageItem.merchant}")
                        .setItems(enhancedFallback.toTypedArray()) { _, which ->
                            val newCategory = fallbackCategoryNames[which]
                            android.util.Log.d("CategoryDialog", "[SUCCESS] Enhanced fallback selected category: $newCategory")
                            updateCategoryForMerchant(messageItem, newCategory)
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                        
                    android.util.Log.d("CategoryDialog", "[SMS] Enhanced fallback dialog shown")
                        
                } catch (e2: Exception) {
                    android.util.Log.e("CategoryDialog", "[ERROR] Enhanced fallback failed, using basic approach", e2)
                    
                    // Ultra-simple fallback
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Change Category")
                        .setItems(fallbackCategoryNames.toTypedArray()) { _, which ->
                            val newCategory = fallbackCategoryNames[which]
                            android.util.Log.d("CategoryDialog", "[SUCCESS] Basic fallback selected category: $newCategory")
                            updateCategoryForMerchant(messageItem, newCategory)
                        }
                        .show()
                        
                    android.util.Log.d("CategoryDialog", "[SMS] Basic fallback dialog shown")
                }
            }
        }
    }
    
    private fun updateCategoryForMerchant(messageItem: MessageItem, newCategory: String) {
        // Use ViewModel to handle category update - success/error will be handled by ViewModel
        viewModel.handleEvent(CategoryTransactionsUIEvent.UpdateTransactionCategory(messageItem, newCategory))
        
        // FIXED: Removed false success message - ViewModel will handle success/error feedback through UI state
        android.util.Log.d("CategoryTransactions", "Initiated category update for ${messageItem.merchant} to $newCategory")
    }
    
    private fun applyFilterAndSort(transactions: List<MessageItem>): List<MessageItem> {
        // Apply date filtering
        val filteredTransactions = when (currentFilterOption) {
            "Today" -> {
                val today = Calendar.getInstance()
                transactions.filter { transaction ->
                    val transactionDate = parseTransactionDate(transaction.dateTime)
                    isSameDay(transactionDate, today.time)
                }
            }
            "Yesterday" -> {
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
                transactions.filter { transaction ->
                    val transactionDate = parseTransactionDate(transaction.dateTime)
                    isSameDay(transactionDate, yesterday.time)
                }
            }
            "This Week" -> {
                val weekStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                transactions.filter { transaction ->
                    val transactionDate = parseTransactionDate(transaction.dateTime)
                    transactionDate.after(weekStart.time)
                }
            }
            "Last Month" -> {
                val lastMonthStart = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val lastMonthEnd = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                transactions.filter { transaction ->
                    val transactionDate = parseTransactionDate(transaction.dateTime)
                    transactionDate.after(lastMonthStart.time) && transactionDate.before(lastMonthEnd.time)
                }
            }
            "All Time" -> transactions
            else -> transactions // "This Month" is default
        }
        
        // Apply sorting
        return when (currentSortOption) {
            "Oldest First" -> filteredTransactions.sortedBy { getDateSortOrder(it.dateTime) }
            "Highest Amount" -> filteredTransactions.sortedByDescending { it.amount }
            "Lowest Amount" -> filteredTransactions.sortedBy { it.amount }
            else -> filteredTransactions.sortedByDescending { getDateSortOrder(it.dateTime) } // "Newest First" is default
        }
    }
    
    private fun parseTransactionDate(dateTimeString: String): Date {
        return when {
            dateTimeString.contains("hour") || dateTimeString.contains("Just now") -> Date()
            dateTimeString.contains("Yesterday") -> {
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.time
            }
            dateTimeString.contains("days ago") -> {
                val days = dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -days) }.time
            }
            else -> {
                try {
                    SimpleDateFormat("MMM dd", Locale.getDefault()).parse(dateTimeString) ?: Date()
                } catch (e: Exception) {
                    Date()
                }
            }
        }
    }
    
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    private fun refreshCurrentView() {
        if (allTransactions.isNotEmpty()) {
            val filteredAndSortedTransactions = applyFilterAndSort(allTransactions)
            transactionsAdapter.submitList(filteredAndSortedTransactions)
            
            // Update summary
            val totalAmount = filteredAndSortedTransactions.sumOf { it.amount }
            binding.tvCategoryTotal.text = "₹${String.format("%.0f", totalAmount)}"
            binding.tvCategorySummary.text = "${filteredAndSortedTransactions.size} transactions • $currentFilterOption"
            
            if (filteredAndSortedTransactions.isEmpty()) {
                binding.recyclerTransactions.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
            } else {
                binding.recyclerTransactions.visibility = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE
            }
        }
    }
    
    private fun showDateFilterDialog() {
        val currentState = viewModel.uiState.value
        val options = currentState.filterOptions.toTypedArray()
        val currentIndex = options.indexOf(currentState.currentFilterOption)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📅 Filter by Date")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val newFilterOption = options[which]
                viewModel.handleEvent(CategoryTransactionsUIEvent.ChangeFilterOption(newFilterOption))
                dialog.dismiss()
                android.util.Log.d("CategoryTransactions", "Applied filter: $newFilterOption")
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    
    private fun showSortDialog() {
        val currentState = viewModel.uiState.value
        val options = currentState.sortOptions.toTypedArray()
        val currentIndex = options.indexOf(currentState.currentSortOption)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("[PROCESS] Sort Transactions")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val newSortOption = options[which]
                viewModel.handleEvent(CategoryTransactionsUIEvent.ChangeSortOption(newSortOption))
                dialog.dismiss()
                android.util.Log.d("CategoryTransactions", "[PROCESS] Applied sort: $newSortOption")
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    
    private fun formatDate(date: Date): String {
        val now = Date()
        val diffInMs = now.time - date.time
        val diffInDays = diffInMs / (1000 * 60 * 60 * 24)
        val diffInHours = diffInMs / (1000 * 60 * 60)
        
        return when {
            diffInHours < 1 -> "Just now"
            diffInHours < 24 -> "$diffInHours hours ago"
            diffInDays == 1L -> "Yesterday"
            diffInDays < 7 -> "$diffInDays days ago"
            else -> {
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                dateFormat.format(date)
            }
        }
    }
    
    private fun getCategoryEmoji(categoryName: String): String {
        // Use display provider to get emoji
        return categoryDisplayProvider.getEmojiString(categoryName)
    }
    
    private fun getRandomCategoryColor(): String {
        val colors = listOf(
            "#ff5722", "#3f51b5", "#4caf50", "#e91e63",
            "#ff9800", "#9c27b0", "#607d8b", "#795548",
            "#2196f3", "#8bc34a", "#ffc107", "#673ab7"
        )
        return colors.random()
    }
    
    private fun getDateSortOrder(dateTimeString: String): Int {
        return when {
            dateTimeString.contains("hour") || dateTimeString.contains("Just now") -> {
                // Extract hours for sorting within same day
                val hours = if (dateTimeString.contains("hour")) {
                    dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                } else 0
                hours // Lower hours = more recent
            }
            dateTimeString.contains("Yesterday") -> 100
            dateTimeString.contains("days ago") -> {
                val days = dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                1000 + days // Higher days = older
            }
            dateTimeString.contains("Aug") || dateTimeString.contains("Today") -> 10000 // Historical dates are oldest
            else -> 5000 // Default for other formats
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Register broadcast receiver for new transactions
        val intentFilter = IntentFilter("com.expensemanager.NEW_TRANSACTION_ADDED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(newTransactionReceiver, intentFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(newTransactionReceiver, intentFilter)
        }
        android.util.Log.d("CategoryTransactions", "Registered broadcast receiver for new transactions")
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister broadcast receiver to prevent memory leaks
        try {
            requireContext().unregisterReceiver(newTransactionReceiver)
            android.util.Log.d("CategoryTransactions", "Unregistered broadcast receiver for new transactions")
        } catch (e: Exception) {
            // Receiver may not have been registered, ignore
            android.util.Log.w("CategoryTransactions", "Broadcast receiver was not registered, ignoring unregister", e)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}