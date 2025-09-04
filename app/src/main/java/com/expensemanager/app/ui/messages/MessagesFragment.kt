package com.expensemanager.app.ui.messages

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.view.ContextThemeWrapper
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.expensemanager.app.databinding.FragmentMessagesBinding
import com.expensemanager.app.R
import com.expensemanager.app.utils.SMSHistoryReader
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.utils.AppLogger
import com.expensemanager.app.ui.categories.CategorySelectionDialogFragment
// UPDATED: Import unified services for consistent SMS parsing and filtering
import com.expensemanager.app.services.TransactionParsingService
import com.expensemanager.app.services.TransactionFilterService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.os.Environment
import javax.inject.Inject

@AndroidEntryPoint
class MessagesFragment : Fragment() {
    
    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    
    // ViewModel injection
    private val messagesViewModel: MessagesViewModel by viewModels()
    
    // Hilt-injected unified services for consistent parsing and filtering
    @Inject
    lateinit var transactionParsingService: TransactionParsingService
    
    @Inject
    lateinit var transactionFilterService: TransactionFilterService
    
    @Inject
    lateinit var appLogger: AppLogger
    
    private lateinit var groupedMessagesAdapter: GroupedMessagesAdapter
    private lateinit var categoryManager: CategoryManager
    private lateinit var merchantAliasManager: MerchantAliasManager
    
    // Legacy data management (kept for parallel approach during migration)
    private var allMessageItems = listOf<MessageItem>()
    private var filteredMessageItems = listOf<MessageItem>()
    private var currentSearchQuery = ""
    
    // Legacy sorting and filtering state (kept for backward compatibility)
    data class SortOption(val name: String, val field: String, val ascending: Boolean)
    data class FilterOptions(
        val minAmount: Double? = null,
        val maxAmount: Double? = null,
        val selectedBanks: Set<String> = emptySet(),
        val minConfidence: Int = 0,
        val dateFrom: String? = null,
        val dateTo: String? = null
    )
    
    private var currentSortOption = SortOption("Date (Newest First)", "date", false)
    private var currentFilterOptions = FilterOptions()
    
    // Broadcast receiver for new transaction notifications
    private val newTransactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.expensemanager.NEW_TRANSACTION_ADDED") {
                val merchant = intent.getStringExtra("merchant") ?: "Unknown"
                val amount = intent.getDoubleExtra("amount", 0.0)
                Log.d("MessagesFragment", "📡 Received new transaction broadcast: $merchant - ₹${String.format("%.0f", amount)}")
                
                // Refresh messages data on the main thread
                lifecycleScope.launch {
                    try {
                        Log.d("MessagesFragment", "[REFRESH] Refreshing messages due to new transaction")
                        loadHistoricalTransactions()
                        
                    } catch (e: Exception) {
                        Log.e("MessagesFragment", "Error refreshing messages after new transaction", e)
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
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categoryManager = CategoryManager(requireContext())
        merchantAliasManager = MerchantAliasManager(requireContext())
        
        // Setup UI components
        setupRecyclerView()
        setupClickListeners()
        
        // Observe ViewModel state
        observeViewModelState()
        
        // Check permissions and setup legacy UI (parallel approach)
        checkPermissionsAndSetupUI()
    }
    
    /**
     * Observe ViewModel state changes
     */
    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                messagesViewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }
    
    /**
     * Update UI based on ViewModel state
     */
    private fun updateUI(state: MessagesUIState) {
        Log.d("MessagesFragment", "Updating UI with ViewModel state: loading=${state.isLoading}, messages=${state.filteredMessages.size}")
        
        // Handle loading states
        if (state.isAnyLoading) {
            // Show loading indicator if appropriate
            Log.d("MessagesFragment", "ViewModel is loading data...")
        }
        
        // Update message counts
        binding.tvTotalMessages.text = state.totalMessagesCount.toString()
        binding.tvAutoCategorized.text = state.autoCategorizedCount.toString()
        
        // Update adapter with grouped messages using post to avoid layout conflicts
        if (state.groupedMessages.isNotEmpty()) {
            // Use post to defer adapter update until after current layout computation
            binding.recyclerMessages.post {
                try {
                    groupedMessagesAdapter.submitList(state.groupedMessages)
                    binding.recyclerMessages.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                } catch (e: Exception) {
                    android.util.Log.e("MessagesFragment", "Error updating adapter", e)
                    // Retry with delay if initial update fails
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            groupedMessagesAdapter.submitList(state.groupedMessages)
                            binding.recyclerMessages.visibility = View.VISIBLE
                            binding.layoutEmpty.visibility = View.GONE
                        } catch (retryError: Exception) {
                            android.util.Log.e("MessagesFragment", "Retry adapter update failed", retryError)
                        }
                    }, 200)
                }
            }
        } else if (state.shouldShowEmptyState) {
            binding.recyclerMessages.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
        }
        
        // Update sort button
        binding.btnSort.text = "Sort: ${state.currentSortOption.displayText}"
        
        // Update filter button
        binding.btnFilter.text = if (state.hasActiveFilters) {
            "Filter (${state.activeFilterCount})"
        } else {
            "Filter"
        }
        
        // Handle error states
        if (state.shouldShowError && state.error != null) {
            Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show()
            messagesViewModel.handleEvent(MessagesUIEvent.ClearError)
        }
        
        // Handle success messages
        state.successMessage?.let { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            messagesViewModel.handleEvent(MessagesUIEvent.ClearError)
        }
        
        // Handle sync messages
        state.syncMessage?.let { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            messagesViewModel.handleEvent(MessagesUIEvent.ClearError)
        }
        
        // Handle test results
        state.testResults?.let { results ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("SMS Test Results")
                .setMessage(results)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    messagesViewModel.handleEvent(MessagesUIEvent.ClearError)
                }
                .show()
        }
    }
    
    private fun checkPermissionsAndSetupUI() {
        val hasReadSmsPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasReceiveSmsPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasReadSmsPermission && hasReceiveSmsPermission) {
            // Permissions granted - show normal UI and load historical data
            setupRecyclerView()
            setupUI()
            setupClickListeners()
            loadHistoricalTransactions()
            binding.layoutEmpty.visibility = View.GONE
            binding.recyclerMessages.visibility = View.VISIBLE
        } else {
            // Permissions not granted - show empty state
            showNoPermissionState()
        }
    }
    
    private fun showNoPermissionState() {
        binding.recyclerMessages.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        
        // Update the empty state message for permissions
        binding.tvTotalMessages.text = "0"
        binding.tvAutoCategorized.text = "0"
    }
    
    private fun setupRecyclerView() {
        groupedMessagesAdapter = GroupedMessagesAdapter(
            onTransactionClick = { messageItem ->
                navigateToTransactionDetails(messageItem)
            },
            onGroupToggle = { group, isIncluded ->
                try {
                    Log.d("MessagesFragment", "Group toggle requested: '${group.merchantName}' -> $isIncluded")
                    // Use ViewModel event
                    messagesViewModel.handleEvent(MessagesUIEvent.ToggleGroupInclusion(group.merchantName, isIncluded))
                    // Keep legacy method for fallback
                    updateExpenseCalculations()
                } catch (e: Exception) {
                    Log.e("MessagesFragment", "Error handling group toggle", e)
                    Toast.makeText(
                        requireContext(),
                        "Failed to update merchant exclusion. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onGroupEdit = { group ->
                showMerchantGroupEditDialog(group)
            }
        )
        binding.recyclerMessages.apply {
            adapter = groupedMessagesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        
        // Initialize with empty list - data will be loaded by ViewModel
        groupedMessagesAdapter.submitList(emptyList())
    }
    
    private fun setupUI() {
        binding.tvTotalMessages.text = "0"
        binding.tvAutoCategorized.text = "0"
    }
    
    private fun setupClickListeners() {
        binding.btnSort.setOnClickListener {
            showSortMenu()
        }
        
        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }
        
        binding.btnDownloadLogs.setOnClickListener {
            // Disable button temporarily to prevent rapid clicks
            it.isEnabled = false
            
            // Use post to avoid any potential layout conflicts
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    downloadLogs()
                } finally {
                    // Re-enable button after a delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        it.isEnabled = true
                    }, 2000)
                }
            }
        }
        
        binding.btnGrantPermissions.setOnClickListener {
            // Open app settings to grant permissions
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        }
        
        // Setup search functionality
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString() ?: ""
                // Use ViewModel event
                messagesViewModel.handleEvent(MessagesUIEvent.Search(query))
                // Keep legacy for fallback
                currentSearchQuery = query
                applyFiltersAndSort()
            }
        })
    }
    
    private fun showFilterMenu() {
        val options = arrayOf(
            "[PROCESS] Resync SMS Messages", 
            "📅 Filter by Date", 
            "[FINANCIAL] Filter by Amount",
            "🏦 Filter by Bank",
            "[DEBUG] Test SMS Scanning"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter & Sync Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Use ViewModel event
                        messagesViewModel.handleEvent(MessagesUIEvent.ResyncSMS)
                        // Keep legacy for fallback
                        resyncSMSMessages()
                    }
                    1 -> showDateFilterDialog()
                    2 -> showAmountFilterDialog()
                    3 -> showBankFilterDialog()
                    4 -> {
                        // Use ViewModel event
                        messagesViewModel.handleEvent(MessagesUIEvent.TestSMSScanning)
                        // Keep legacy for fallback
                        testSMSScanning()
                    }
                }
            }
            .show()
    }
    
    private fun showSortMenu() {
        val sortOptions = arrayOf(
            "Date (Newest First)",
            "Date (Oldest First)", 
            "Amount (Highest First)",
            "Amount (Lowest First)",
            "Merchant Name (A-Z)",
            "Merchant Name (Z-A)",
            "Bank Name (A-Z)",
            "Bank Name (Z-A)",
            "Confidence (Highest First)",
            "Confidence (Lowest First)"
        )
        
        val currentIndex = when (currentSortOption.field + "_" + currentSortOption.ascending) {
            "date_false" -> 0
            "date_true" -> 1
            "amount_false" -> 2
            "amount_true" -> 3
            "merchant_true" -> 4
            "merchant_false" -> 5
            "bank_true" -> 6
            "bank_false" -> 7
            "confidence_false" -> 8
            "confidence_true" -> 9
            else -> 0
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort Transactions")
            .setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                Log.d("MessagesFragment", "[DEBUG] User selected sort option: $which (${sortOptions[which]})")
                
                val oldSortOption = currentSortOption
                val newSortOption = when (which) {
                    0 -> com.expensemanager.app.ui.messages.SortOption("Date (Newest First)", "date", false)
                    1 -> com.expensemanager.app.ui.messages.SortOption("Date (Oldest First)", "date", true)
                    2 -> com.expensemanager.app.ui.messages.SortOption("Amount (Highest First)", "amount", false)
                    3 -> com.expensemanager.app.ui.messages.SortOption("Amount (Lowest First)", "amount", true)
                    4 -> com.expensemanager.app.ui.messages.SortOption("Merchant Name (A-Z)", "merchant", true)
                    5 -> com.expensemanager.app.ui.messages.SortOption("Merchant Name (Z-A)", "merchant", false)
                    6 -> com.expensemanager.app.ui.messages.SortOption("Bank Name (A-Z)", "bank", true)
                    7 -> com.expensemanager.app.ui.messages.SortOption("Bank Name (Z-A)", "bank", false)
                    8 -> com.expensemanager.app.ui.messages.SortOption("Confidence (Highest First)", "confidence", false)
                    9 -> com.expensemanager.app.ui.messages.SortOption("Confidence (Lowest First)", "confidence", true)
                    else -> return@setSingleChoiceItems
                }
                
                Log.d("MessagesFragment", "[DEBUG] Sort option changed from ${oldSortOption.name} to ${newSortOption.name}")
                
                // Use ViewModel event
                messagesViewModel.handleEvent(MessagesUIEvent.ApplySort(newSortOption))
                
                // Keep legacy for fallback
                currentSortOption = SortOption(newSortOption.name, newSortOption.field, newSortOption.ascending)
                binding.btnSort.text = "Sort: ${currentSortOption.name.split(" ")[0]}"
                Log.d("MessagesFragment", "[DEBUG] Updated sort button text to: ${binding.btnSort.text}")
                applyFiltersAndSort()
                
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_advanced_filter, null
        )
        
        // Find views
        val etMinAmount = dialogView.findViewById<TextInputEditText>(R.id.et_min_amount)
        val etMaxAmount = dialogView.findViewById<TextInputEditText>(R.id.et_max_amount)
        val etDateFrom = dialogView.findViewById<TextInputEditText>(R.id.et_date_from)
        val etDateTo = dialogView.findViewById<TextInputEditText>(R.id.et_date_to)
        val sliderConfidence = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.slider_confidence)
        val chipGroupBanks = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_group_banks)
        
        // Set current values
        etMinAmount.setText(currentFilterOptions.minAmount?.toString() ?: "")
        etMaxAmount.setText(currentFilterOptions.maxAmount?.toString() ?: "")
        etDateFrom.setText(currentFilterOptions.dateFrom ?: "")
        etDateTo.setText(currentFilterOptions.dateTo ?: "")
        sliderConfidence.value = currentFilterOptions.minConfidence.toFloat()
        
        // Add bank chips
        val uniqueBanks = allMessageItems.map { it.bankName }.distinct().sorted()
        chipGroupBanks.removeAllViews()
        uniqueBanks.forEach { bankName ->
            val chip = com.google.android.material.chip.Chip(requireContext())
            chip.text = bankName
            chip.isCheckable = true
            chip.isChecked = currentFilterOptions.selectedBanks.contains(bankName)
            chipGroupBanks.addView(chip)
        }
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Advanced Filters")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                // Collect selected banks
                val selectedBanks = mutableSetOf<String>()
                for (i in 0 until chipGroupBanks.childCount) {
                    val chip = chipGroupBanks.getChildAt(i) as com.google.android.material.chip.Chip
                    if (chip.isChecked) {
                        selectedBanks.add(chip.text.toString())
                    }
                }
                
                // Update filter options
                currentFilterOptions = FilterOptions(
                    minAmount = etMinAmount.text.toString().toDoubleOrNull(),
                    maxAmount = etMaxAmount.text.toString().toDoubleOrNull(),
                    selectedBanks = selectedBanks,
                    minConfidence = sliderConfidence.value.toInt(),
                    dateFrom = etDateFrom.text.toString().takeIf { it.isNotEmpty() },
                    dateTo = etDateTo.text.toString().takeIf { it.isNotEmpty() }
                )
                
                // Update filter button text
                val activeFilters = listOfNotNull(
                    if (currentFilterOptions.minAmount != null || currentFilterOptions.maxAmount != null) "Amount" else null,
                    if (currentFilterOptions.selectedBanks.isNotEmpty()) "Banks" else null,
                    if (currentFilterOptions.minConfidence > 0) "Confidence" else null,
                    if (currentFilterOptions.dateFrom != null || currentFilterOptions.dateTo != null) "Date" else null
                )
                
                binding.btnFilter.text = if (activeFilters.isEmpty()) "Filter" else "Filter (${activeFilters.size})"
                
                applyFiltersAndSort()
            }
            .setNegativeButton("Reset") { _, _ ->
                currentFilterOptions = FilterOptions()
                binding.btnFilter.text = "Filter"
                applyFiltersAndSort()
            }
            .setNeutralButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun applyFiltersAndSort() {
        Log.d("MessagesFragment", "[DEBUG] Starting applyFiltersAndSort() with ${allMessageItems.size} total items")
        Log.d("MessagesFragment", "[DEBUG] Current sort option: ${currentSortOption.name} (field: ${currentSortOption.field}, ascending: ${currentSortOption.ascending})")
        
        // UPDATED: Use unified TransactionFilterService for consistent filtering across screens
        lifecycleScope.launch {
            try {
                // Step 1: Apply exclusion filtering first (unified logic)
                val excludedFiltered = transactionFilterService.filterMessageItemsByExclusions(allMessageItems)
                Log.d("MessagesFragment", "[UNIFIED] After exclusion filtering: ${excludedFiltered.size} items (excluded ${allMessageItems.size - excludedFiltered.size})")
                
                // Step 2: Apply generic filters using unified service
                val filtered = transactionFilterService.applyGenericFilters(
                    transactions = excludedFiltered,
                    minAmount = currentFilterOptions.minAmount,
                    maxAmount = currentFilterOptions.maxAmount,
                    selectedBanks = currentFilterOptions.selectedBanks,
                    minConfidence = currentFilterOptions.minConfidence,
                    dateFrom = currentFilterOptions.dateFrom,
                    dateTo = currentFilterOptions.dateTo,
                    searchQuery = currentSearchQuery
                )
                
                Log.d("MessagesFragment", "[UNIFIED] Final filtering result: ${filtered.size} items")
                
                if (filtered.isNotEmpty()) {
                    Log.d("MessagesFragment", "[DEBUG] Sample items after unified filtering:")
                    filtered.take(3).forEach { item ->
                        Log.d("MessagesFragment", "  - ${item.merchant}: ₹${item.amount} (${item.dateTime})")
                    }
                }
                
                // Update UI state with unified filtering results
                filteredMessageItems = filtered
                
                // Update UI - this will call groupTransactionsByMerchant which handles the sorting
                updateTransactionsList(filteredMessageItems)
                updateSummaryStats(filteredMessageItems)
                
                Log.d("MessagesFragment", "[UNIFIED] applyFiltersAndSort() completed using unified services - ${filtered.size} transactions displayed")
                
            } catch (e: Exception) {
                Log.e("MessagesFragment", "Error applying unified filters", e)
                // Fallback to original data on error
                filteredMessageItems = allMessageItems
                updateTransactionsList(filteredMessageItems)
                updateSummaryStats(filteredMessageItems)
            }
        }
    }
    
    private fun updateTransactionsList(messageItems: List<MessageItem>) {
        if (messageItems.isEmpty()) {
            binding.recyclerMessages.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            return
        }
        
        binding.recyclerMessages.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        
        // Group filtered messages by merchant for display
        val merchantGroups = groupTransactionsByMerchant(messageItems)
        groupedMessagesAdapter.submitList(merchantGroups)
    }
    
    private fun updateSummaryStats(messageItems: List<MessageItem>) {
        binding.tvTotalMessages.text = messageItems.size.toString()
        val autoCategorized = messageItems.count { it.category != "Other" && it.confidence >= 80 }
        binding.tvAutoCategorized.text = autoCategorized.toString()
    }
    
    private fun resyncSMSMessages() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Resync SMS Messages")
            .setMessage("This will scan your SMS messages again and update the transaction list. This may take a few moments.")
            .setPositiveButton("Resync") { _, _ ->
                performSMSResync()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun performSMSResync() {
        lifecycleScope.launch {
            val progressDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("[PROCESS] Syncing SMS Messages")
                .setMessage("Scanning your SMS inbox for transaction messages...")
                .setCancelable(false)
                .create()
            
            try {
                progressDialog.show()
                
                Log.d("MessagesFragment", "Starting SMS resync...")
                
                // Clear current adapter to show fresh data
                groupedMessagesAdapter.submitList(emptyList())
                
                // Create new SMS reader instance and scan
                val smsReader = SMSHistoryReader(requireContext())
                val historicalTransactions = smsReader.scanHistoricalSMS()
                
                Log.d("MessagesFragment", "SMS resync found ${historicalTransactions.size} transactions")
                
                // Process the scanned data
                if (historicalTransactions.isNotEmpty()) {
                    val messageItems = historicalTransactions.map { transaction ->
                        val displayName = merchantAliasManager.getDisplayName(transaction.merchant)
                        val category = merchantAliasManager.getMerchantCategory(transaction.merchant)
                        val categoryColor = merchantAliasManager.getMerchantCategoryColor(transaction.merchant)
                        MessageItem(
                            amount = transaction.amount,
                            merchant = displayName,
                            bankName = transaction.bankName,
                            category = category,
                            categoryColor = categoryColor,
                            confidence = (transaction.confidence * 100).toInt(),
                            dateTime = formatDate(transaction.date),
                            rawSMS = transaction.rawSMS,
                            isDebit = transaction.isDebit
                        )
                    }
                    
                    // Store for filtering/sorting
                    allMessageItems = messageItems
                    filteredMessageItems = messageItems
                    
                    // Apply initial sorting and filtering
                    applyFiltersAndSort()
                    binding.recyclerMessages.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                    
                    // Update UI stats (initially all groups are included)
                    updateExpenseCalculations()
                    
                    progressDialog.dismiss()
                    
                    Toast.makeText(
                        requireContext(),
                        "[SUCCESS] Found ${historicalTransactions.size} new transaction SMS messages!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // FIXED: Proper empty state handling - no transactions found
                    showEmptyState()
                    
                    progressDialog.dismiss()
                    
                    Toast.makeText(
                        requireContext(),
                        "[SMS] No transaction SMS found in your inbox",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: SecurityException) {
                progressDialog.dismiss()
                Log.w("MessagesFragment", "SMS permission denied during resync", e)
                Toast.makeText(
                    requireContext(),
                    "📱 SMS permission is required to sync transaction messages",
                    Toast.LENGTH_LONG
                ).show()
                showNoPermissionState()
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e("MessagesFragment", "Error during SMS resync", e)
                Toast.makeText(
                    requireContext(),
                    "⚠️ Unable to sync SMS messages: ${e.message ?: "Please try again"}",
                    Toast.LENGTH_LONG
                ).show()
                
                // Reload original data on error
                loadHistoricalTransactions()
            }
        }
    }
    
    private fun testSMSScanning() {
        lifecycleScope.launch {
            try {
                Log.d("MessagesFragment", "Starting SMS scanning test...")
                val smsReader = SMSHistoryReader(requireContext())
                val transactions = smsReader.scanHistoricalSMS()
                
                val message = buildString {
                    appendLine("[ANALYTICS] SMS Scanning Test Results:")
                    appendLine("Total valid transactions: ${transactions.size}")
                    appendLine()
                    
                    if (transactions.isNotEmpty()) {
                        appendLine("[SMS] Found transactions:")
                        transactions.take(5).forEach { transaction ->
                            appendLine("• ${transaction.merchant}: ₹${String.format("%.2f", transaction.amount)}")
                            appendLine("  Bank: ${transaction.bankName}")
                            appendLine("  Date: ${SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(transaction.date)}")
                            appendLine("  Confidence: ${(transaction.confidence * 100).toInt()}%")
                            appendLine()
                        }
                        
                        val merchantCounts = transactions.groupBy { it.merchant }.mapValues { it.value.size }
                        if (merchantCounts.isNotEmpty()) {
                            appendLine("🏪 Top merchants:")
                            merchantCounts.toList().sortedByDescending { it.second }.take(3).forEach { (merchant, count) ->
                                appendLine("• $merchant: $count transactions")
                            }
                        }
                        
                        val totalAmount = transactions.sumOf { it.amount }
                        appendLine()
                        appendLine("[FINANCIAL] Total amount: ₹${String.format("%.2f", totalAmount)}")
                    } else {
                        appendLine("ℹ️ No valid transaction SMS found.")
                        appendLine()
                        appendLine("This could mean:")
                        appendLine("• No real transaction SMS in last 6 months")
                        appendLine("• SMS are being filtered as non-transactions")
                        appendLine("• Check logcat for detailed filtering info")
                        appendLine()
                        appendLine("Make sure you have:")
                        appendLine("• Granted SMS permissions")
                        appendLine("• Bank transaction SMS in inbox")
                        appendLine("• SMS with debit/credit keywords")
                    }
                }
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("[DEBUG] SMS Test Results")
                    .setMessage(message)
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                
                Log.d("MessagesFragment", "SMS Test: Found ${transactions.size} transactions")
                
            } catch (e: SecurityException) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("📱 SMS Permission Required")
                    .setMessage("SMS permission is required to test SMS scanning. Please grant SMS permissions in app settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", requireContext().packageName, null)
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("⚠️ Test Failed")
                    .setMessage("Error testing SMS scanning: ${e.message}")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                Log.e("MessagesFragment", "SMS test error", e)
            }
        }
    }
    
    private fun showDateFilterDialog() {
        val options = arrayOf("Today", "Yesterday", "This Week", "This Month", "Last Month", "All Time")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Date")
            .setItems(options) { _, which ->
                Toast.makeText(requireContext(), "Filtering by: ${options[which]}", Toast.LENGTH_SHORT).show()
                // TODO: Implement actual date filtering
            }
            .show()
    }
    
    private fun showAmountFilterDialog() {
        val options = arrayOf("Under ₹100", "₹100 - ₹500", "₹500 - ₹2000", "Above ₹2000", "All Amounts")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Amount")
            .setItems(options) { _, which ->
                Toast.makeText(requireContext(), "Filtering by: ${options[which]}", Toast.LENGTH_SHORT).show()
                // TODO: Implement actual amount filtering
            }
            .show()
    }
    
    private fun showBankFilterDialog() {
        val banks = arrayOf("HDFC Bank", "ICICI Bank", "SBI", "Axis Bank", "Kotak Bank", "All Banks")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Bank")
            .setItems(banks) { _, which ->
                Toast.makeText(requireContext(), "Filtering by: ${banks[which]}", Toast.LENGTH_SHORT).show()
                // TODO: Implement actual bank filtering
            }
            .show()
    }
    
    
    private fun loadHistoricalTransactions() {
        lifecycleScope.launch {
            try {
                Log.d("MessagesFragment", "[LOAD] Loading historical transactions...")
                
                // Use repository to get transactions from database (faster and more reliable)
                val repository = com.expensemanager.app.data.repository.ExpenseRepository.getInstance(requireContext())
                
                // Get current date range (this month for now, but could be expanded)
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(java.util.Calendar.MONTH, 1)
                calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
                calendar.set(java.util.Calendar.MINUTE, 59)
                calendar.set(java.util.Calendar.SECOND, 59)
                val endDate = calendar.time
                
                // Load transactions from SQLite database
                val dbTransactions = repository.getTransactionsByDateRange(startDate, endDate)
                val dbDebitCount = dbTransactions.count { it.isDebit }
                val dbCreditCount = dbTransactions.count { !it.isDebit }
                Log.d("MessagesFragment", "[DATA] Found ${dbTransactions.size} transactions in database ($dbDebitCount debits, $dbCreditCount credits)")
                
                // DEBUG: Also check what the Dashboard method would return
                val expenseTransactions = repository.getExpenseTransactionsByDateRange(startDate, endDate)
                Log.d("MessagesFragment", "[DEBUG] Dashboard would get ${expenseTransactions.size} debit-only transactions vs Messages gets ${dbTransactions.size} total")
                
                if (dbTransactions.isNotEmpty()) {
                    // Convert to MessageItem format with proper deduplication
                    val messageItems = dbTransactions.mapNotNull { transaction ->
                        try {
                            val merchantWithCategory = repository.getMerchantWithCategory(transaction.normalizedMerchant)
                            val category = merchantWithCategory?.category_name ?: "Other"
                            val categoryColor = merchantWithCategory?.category_color ?: "#888888"
                            
                            // CRITICAL FIX: Apply merchant aliases when loading from database
                            val displayName = merchantAliasManager.getDisplayName(transaction.rawMerchant)
                            val aliasCategory = merchantAliasManager.getMerchantCategory(transaction.rawMerchant)
                            val aliasCategoryColor = merchantAliasManager.getMerchantCategoryColor(transaction.rawMerchant)
                            
                            Log.d("MessagesFragment", "[DEBUG] Database load: rawMerchant='${transaction.rawMerchant}' -> displayName='$displayName', category='$aliasCategory'")
                            
                            MessageItem(
                                amount = transaction.amount,
                                merchant = displayName, // Use alias display name, not raw merchant
                                bankName = transaction.bankName,
                                category = aliasCategory, // Use alias category
                                categoryColor = aliasCategoryColor, // Use alias category color
                                confidence = (transaction.confidenceScore * 100).toInt(),
                                dateTime = formatDate(transaction.transactionDate),
                                rawSMS = transaction.rawSmsBody,
                                isDebit = transaction.isDebit
                            )
                        } catch (e: Exception) {
                            Log.w("MessagesFragment", "Error converting transaction: ${e.message}")
                            null
                        }
                    }.distinctBy { 
                        // Remove duplicates based on merchant, amount, and date
                        "${it.merchant}_${it.amount}_${it.dateTime}_${it.bankName}"
                    }
                    
                    Log.d("MessagesFragment", "[DEBUG] Before filtering: ${messageItems.size} message items")
                    
                    // IMPORTANT: For debugging only - show what would be excluded
                    // Note: Messages screen shows ALL transactions but Dashboard should apply filtering
                    // This difference is intentional - Messages shows everything, Dashboard shows filtered results
                    
                    // Store for filtering/sorting
                    allMessageItems = messageItems
                    filteredMessageItems = messageItems
                    
                    // Apply initial sorting and filtering
                    applyFiltersAndSort()
                    binding.recyclerMessages.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                    
                    // Update stats with real data
                    updateExpenseCalculations()
                    
                    Log.d("MessagesFragment", "[SUCCESS] Successfully loaded ${messageItems.size} unique transactions from database")
                    
                } else {
                    // No database data found - fallback to SMS scanning
                    Log.d("MessagesFragment", "[SMS] No database transactions found, falling back to SMS scanning...")
                    loadFromSMSFallback()
                }
            } catch (e: SecurityException) {
                // Permission denied
                Log.w("MessagesFragment", "SMS permission denied", e)
                Toast.makeText(
                    requireContext(),
                    "📱 SMS permission is required to read transaction messages",
                    Toast.LENGTH_LONG
                ).show()
                showNoPermissionState()
            } catch (e: Exception) {
                // Other error loading historical data, show empty state
                Log.e("MessagesFragment", "Error loading SMS data", e)
                groupedMessagesAdapter.submitList(emptyList())
                binding.recyclerMessages.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.tvTotalMessages.text = "0"
                binding.tvAutoCategorized.text = "0"
                
                Toast.makeText(
                    requireContext(),
                    "[WARNING] Error reading SMS: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun loadFromSMSFallback() {
        lifecycleScope.launch {
            try {
                val smsReader = SMSHistoryReader(requireContext())
                val historicalTransactions = smsReader.scanHistoricalSMS()
                
                Log.d("MessagesFragment", "[SMS] SMS Fallback: Found ${historicalTransactions.size} transactions")
                
                if (historicalTransactions.isNotEmpty()) {
                    // Convert to MessageItem format
                    val messageItems = historicalTransactions.map { transaction ->
                        val displayName = merchantAliasManager.getDisplayName(transaction.merchant)
                        val category = merchantAliasManager.getMerchantCategory(transaction.merchant)
                        val categoryColor = merchantAliasManager.getMerchantCategoryColor(transaction.merchant)
                        MessageItem(
                            amount = transaction.amount,
                            merchant = displayName,
                            bankName = transaction.bankName,
                            category = category,
                            categoryColor = categoryColor,
                            confidence = (transaction.confidence * 100).toInt(),
                            dateTime = formatDate(transaction.date),
                            rawSMS = transaction.rawSMS,
                            isDebit = transaction.isDebit
                        )
                    }.distinctBy { 
                        // Remove duplicates
                        "${it.merchant}_${it.amount}_${it.dateTime}_${it.bankName}"
                    }
                    
                    // Store for filtering/sorting
                    allMessageItems = messageItems
                    filteredMessageItems = messageItems
                    
                    // Apply initial sorting and filtering
                    applyFiltersAndSort()
                    binding.recyclerMessages.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                    
                    // Update stats with real data
                    updateExpenseCalculations()
                    
                    Log.d("MessagesFragment", "[SUCCESS] SMS Fallback: Loaded ${messageItems.size} unique transactions")
                } else {
                    // FIXED: Show empty state instead of falling back to old approach
                    Log.d("MessagesFragment", "Database has no transactions, showing empty state")
                    showEmptyState()
                }
            } catch (e: Exception) {
                Log.e("MessagesFragment", "Error in SMS fallback", e)
                showEmptyState()
            }
        }
    }
    
    private fun showEmptyState() {
        // FIXED: Clear all message data and show proper empty state
        groupedMessagesAdapter.submitList(emptyList())
        binding.recyclerMessages.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        
        // Reset counters to zero
        binding.tvTotalMessages.text = "0"
        binding.tvAutoCategorized.text = "0"
        
        // Clear internal data structures
        allMessageItems = emptyList()
        filteredMessageItems = emptyList()
        
        // Reset search and filters
        binding.etSearch.setText("")
        currentSearchQuery = ""
        currentFilterOptions = FilterOptions()
        binding.btnFilter.text = "Filter"
        
        Log.d("MessagesFragment", "[INFO] Showing proper empty state - all data cleared")
    }
    
    private fun showCategoryEditDialog(messageItem: MessageItem) {
        val categories = categoryManager.getAllCategories()
        val currentCategoryIndex = categories.indexOf(messageItem.category)
        
        // Create enhanced categories with emojis
        val enhancedCategories = categories.map { category ->
            "${getCategoryEmoji(category)} $category"
        }.toTypedArray()
        
        Log.d("MessagesFragment", "[TARGET] Showing custom category dialog for ${messageItem.merchant}")
        
        val dialog = CategorySelectionDialogFragment.newInstance(
            categories = enhancedCategories,
            currentIndex = currentCategoryIndex,
            merchantName = messageItem.merchant
        ) { selectedCategory ->
            // Remove emoji from selected category to get the actual name
            val actualCategory = selectedCategory.replace(Regex("^[\\p{So}\\p{Cn}]\\s+"), "")
            Log.d("MessagesFragment", "[SUCCESS] Category selected: $actualCategory for ${messageItem.merchant}")
            if (actualCategory != messageItem.category) {
                updateCategoryForMerchant(messageItem, actualCategory)
            }
        }
        
        dialog.show(parentFragmentManager, "CategoryEditDialog")
    }
    
    private fun getCategoryEmoji(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "food & dining", "food", "dining" -> "🍽️"
            "transportation", "transport" -> "🚗"
            "groceries", "grocery" -> "🛒"
            "healthcare", "health" -> "🏥"
            "entertainment" -> "🎬"
            "shopping" -> "🛍️"
            "utilities" -> "⚡"
            "money", "finance" -> "[FINANCIAL]"
            "education" -> "📚"
            "travel" -> "✈️"
            "bills" -> "💳"
            "insurance" -> "🛡️"
            else -> "📂"
        }
    }
    
    private fun updateCategoryForMerchant(messageItem: MessageItem, newCategory: String) {
        lifecycleScope.launch {
            try {
                // Update category using CategoryManager
                categoryManager.updateCategory(messageItem.merchant, newCategory)
                
                // Find similar transactions
                val similarTransactions = categoryManager.getAllSimilarTransactions(messageItem.merchant)
                
                // Reload the grouped data to reflect category changes
                loadHistoricalTransactions()
                
                // Show feedback
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Category Updated! [SUCCESS]")
                    .setMessage("Updated category for ${messageItem.merchant} to '$newCategory'.")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Error")
                    .setMessage("Failed to update category: ${e.message}")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }
    
    private fun formatDate(date: java.util.Date): String {
        val now = java.util.Date()
        val diffInMs = now.time - date.time
        val diffInDays = diffInMs / (1000 * 60 * 60 * 24)
        val diffInHours = diffInMs / (1000 * 60 * 60)
        
        return when {
            diffInHours < 1 -> "Just now"
            diffInHours < 24 -> "$diffInHours hours ago"
            diffInDays == 1L -> "Yesterday"
            diffInDays < 7 -> "$diffInDays days ago"
            else -> {
                val formatter = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                formatter.format(date)
            }
        }
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
            dateTimeString.contains("Aug") -> 10000 // Historical dates are oldest
            else -> 5000 // Default for other formats
        }
    }
    
    private fun getDateSortOrderReverse(dateTimeString: String): Long {
        // Return a timestamp-like value for sorting (higher = newer)
        return when {
            dateTimeString.contains("Just now") -> System.currentTimeMillis()
            dateTimeString.contains("hour") -> {
                val hours = dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
            }
            dateTimeString.contains("Yesterday") -> System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            dateTimeString.contains("days ago") -> {
                val days = dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            }
            else -> {
                // Handle absolute dates like "Aug 29", "Dec 15", "2024-08-29", etc.
                parseAbsoluteDateToTimestamp(dateTimeString)
            }
        }
    }
    
    private fun parseAbsoluteDateToTimestamp(dateString: String): Long {
        try {
            // Try different date formats commonly used in the app
            val formats = listOf(
                java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()), // Aug 29
                java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()),  // Aug 9  
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()), // 2024-08-29
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()), // 29/08/2024
                java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.getDefault())  // 08/29/2024
            )
            
            for (format in formats) {
                try {
                    val parsedDate = format.parse(dateString)
                    if (parsedDate != null) {
                        // If no year specified (like "Aug 29"), assume current year
                        val calendar = java.util.Calendar.getInstance()
                        calendar.time = parsedDate
                        if (calendar.get(java.util.Calendar.YEAR) == 1970) {
                            calendar.set(java.util.Calendar.YEAR, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
                        }
                        Log.d("MessagesFragment", "[DATE] Parsed absolute date '$dateString' to timestamp: ${calendar.timeInMillis}")
                        return calendar.timeInMillis
                    }
                } catch (e: Exception) {
                    // Try next format
                    continue
                }
            }
            
            Log.w("MessagesFragment", "[DATE] Could not parse absolute date: '$dateString', using default")
            // Fallback to a reasonable old timestamp instead of 0L
            return System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000L) // 1 year ago
            
        } catch (e: Exception) {
            Log.w("MessagesFragment", "[DATE] Error parsing date '$dateString'", e)
            return System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000L) // 1 year ago
        }
    }
    
    private fun groupTransactionsByMerchant(transactions: List<MessageItem>): List<MerchantGroup> {
        Log.d("MessagesFragment", "[DEBUG] Grouping ${transactions.size} transactions by merchant")
        Log.d("MessagesFragment", "[DEBUG] Current sort option for grouping: ${currentSortOption.name}")
        
        // Group by the display name (which is already set from the alias manager)
        val groups = transactions
            .groupBy { it.merchant } // merchant field already contains the display name
            .map { (displayName, merchantTransactions) ->
                // Sort transactions within each group by date (newest first) for consistent display
                val sortedTransactions = merchantTransactions.sortedByDescending { transaction ->
                    getDateSortOrderReverse(transaction.dateTime)
                }
                val totalAmount = merchantTransactions.sumOf { it.amount }
                val category = merchantTransactions.firstOrNull()?.category ?: "Other"
                val categoryColor = merchantTransactions.firstOrNull()?.categoryColor ?: "#9e9e9e"
                
                // Get the latest transaction timestamp for fallback sorting
                val latestTransactionDate = sortedTransactions.firstOrNull()?.let { 
                    getDateSortOrderReverse(it.dateTime) 
                } ?: 0L
                
                // Get primary bank (most frequent bank for this merchant)
                val primaryBankName = merchantTransactions
                    .groupBy { it.bankName }
                    .maxByOrNull { it.value.size }
                    ?.key ?: ""
                
                // Calculate average confidence
                val averageConfidence = if (merchantTransactions.isNotEmpty()) {
                    merchantTransactions.map { it.confidence }.average()
                } else {
                    0.0
                }
                
                MerchantGroup(
                    merchantName = displayName,
                    transactions = sortedTransactions,
                    totalAmount = totalAmount,
                    categoryColor = categoryColor,
                    category = category,
                    isExpanded = false,
                    isIncludedInCalculations = true, // Will be updated from saved state
                    latestTransactionDate = latestTransactionDate,
                    primaryBankName = primaryBankName,
                    averageConfidence = averageConfidence
                )
            }
        
        // Sort groups based on current sort option instead of always by latest transaction date
        val sortedGroups = when (currentSortOption.field) {
            "date" -> {
                if (currentSortOption.ascending) {
                    groups.sortedBy { it.latestTransactionDate }
                } else {
                    groups.sortedByDescending { it.latestTransactionDate }
                }
            }
            "amount" -> {
                if (currentSortOption.ascending) {
                    groups.sortedBy { it.totalAmount }
                } else {
                    groups.sortedByDescending { it.totalAmount }
                }
            }
            "merchant" -> {
                if (currentSortOption.ascending) {
                    groups.sortedBy { it.merchantName.lowercase() }
                } else {
                    groups.sortedByDescending { it.merchantName.lowercase() }
                }
            }
            "bank" -> {
                // For bank sorting, use the primary bank name
                if (currentSortOption.ascending) {
                    groups.sortedBy { it.primaryBankName.lowercase() }
                } else {
                    groups.sortedByDescending { it.primaryBankName.lowercase() }
                }
            }
            "confidence" -> {
                // For confidence sorting, use average confidence
                if (currentSortOption.ascending) {
                    groups.sortedBy { it.averageConfidence }
                } else {
                    groups.sortedByDescending { it.averageConfidence }
                }
            }
            else -> {
                // Default to sorting by latest transaction date (newest first)
                groups.sortedByDescending { it.latestTransactionDate }
            }
        }
        
        Log.d("MessagesFragment", "[DEBUG] Created ${sortedGroups.size} merchant groups, sorted by ${currentSortOption.field}")
        
        // Enhanced debug logging for group sorting results
        if (sortedGroups.isNotEmpty()) {
            Log.d("MessagesFragment", "[DEBUG] Top 3 groups after sorting by ${currentSortOption.field}:")
            sortedGroups.take(3).forEach { group ->
                when (currentSortOption.field) {
                    "amount" -> Log.d("MessagesFragment", "  - ${group.merchantName}: ₹${String.format("%.2f", group.totalAmount)} (${group.transactions.size} transactions)")
                    "merchant" -> Log.d("MessagesFragment", "  - ${group.merchantName}: ${group.transactions.size} transactions")
                    "bank" -> Log.d("MessagesFragment", "  - ${group.merchantName}: ${group.primaryBankName} (${group.transactions.size} transactions)")
                    "confidence" -> Log.d("MessagesFragment", "  - ${group.merchantName}: ${String.format("%.1f", group.averageConfidence)}% confidence")
                    "date" -> {
                        val dateStr = group.transactions.firstOrNull()?.dateTime ?: "N/A"
                        Log.d("MessagesFragment", "  - ${group.merchantName}: $dateStr (${group.transactions.size} transactions)")
                    }
                    else -> Log.d("MessagesFragment", "  - ${group.merchantName}: ${group.transactions.size} transactions")
                }
            }
        }
        
        // Load saved inclusion states
        return loadGroupInclusionStates(sortedGroups)
    }
    
    
    private fun updateExpenseCalculations() {
        try {
            Log.d("MessagesFragment", "Updating expense calculations...")
            
            // Get current groups from adapter
            val currentGroups = groupedMessagesAdapter.merchantGroups
            
            // Calculate totals only for included groups
            val includedGroups = currentGroups.filter { it.isIncludedInCalculations }
            val totalMessages = includedGroups.sumOf { it.transactions.size }
            val autoCategorized = includedGroups.sumOf { group -> 
                group.transactions.count { it.confidence >= 85 } 
            }
            
            // Update Messages UI
            binding.tvTotalMessages.text = totalMessages.toString()
            binding.tvAutoCategorized.text = autoCategorized.toString()
            
            // Store the inclusion state in SharedPreferences for other screens to use
            saveGroupInclusionStates(currentGroups)
            
            // FIXED: Notify other screens about inclusion state changes
            val intent = Intent("com.expensemanager.INCLUSION_STATE_CHANGED")
            intent.putExtra("included_count", totalMessages)
            intent.putExtra("total_amount", includedGroups.sumOf { it.totalAmount })
            requireContext().sendBroadcast(intent)
            
            // Log for debugging
            val totalIncludedAmount = includedGroups.sumOf { it.totalAmount }
            Log.d("MessagesFragment", "Successfully updated calculations: $totalMessages messages, ₹${String.format("%.0f", totalIncludedAmount)} total from included groups")
            
        } catch (e: Exception) {
            Log.e("MessagesFragment", "Error updating expense calculations", e)
            Toast.makeText(
                requireContext(),
                "Error updating calculations. Some data may not be saved.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun saveGroupInclusionStates(groups: List<MerchantGroup>) {
        try {
            Log.d("MessagesFragment", "Saving inclusion states for ${groups.size} groups")
            
            val prefs = requireContext().getSharedPreferences("expense_calculations", android.content.Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Save inclusion states as JSON
            val inclusionStates = org.json.JSONObject()
            var savedCount = 0
            
            groups.forEach { group ->
                try {
                    inclusionStates.put(group.merchantName, group.isIncludedInCalculations)
                    savedCount++
                } catch (e: Exception) {
                    Log.w("MessagesFragment", "Failed to save inclusion state for '${group.merchantName}'", e)
                }
            }
            
            editor.putString("group_inclusion_states", inclusionStates.toString())
            val success = editor.commit()
            
            if (success) {
                Log.d("MessagesFragment", "Successfully saved $savedCount inclusion states")
            } else {
                Log.e("MessagesFragment", "Failed to commit inclusion states to SharedPreferences")
            }
            
        } catch (e: Exception) {
            Log.e("MessagesFragment", "Error saving group inclusion states", e)
            // Don't show error to user for this - it's not critical for immediate functionality
        }
    }
    
    private fun loadGroupInclusionStates(groups: List<MerchantGroup>): List<MerchantGroup> {
        return try {
            Log.d("MessagesFragment", "Loading inclusion states for ${groups.size} groups")
            
            val prefs = requireContext().getSharedPreferences("expense_calculations", android.content.Context.MODE_PRIVATE)
            val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
            
            if (inclusionStatesJson != null && inclusionStatesJson.isNotBlank()) {
                try {
                    val inclusionStates = org.json.JSONObject(inclusionStatesJson)
                    var loadedCount = 0
                    
                    val updatedGroups = groups.map { group ->
                        val isIncluded = try {
                            if (inclusionStates.has(group.merchantName)) {
                                loadedCount++
                                inclusionStates.getBoolean(group.merchantName)
                            } else {
                                true // Default to included
                            }
                        } catch (e: Exception) {
                            Log.w("MessagesFragment", "Error loading inclusion state for '${group.merchantName}'", e)
                            true // Default to included on error
                        }
                        group.copy(isIncludedInCalculations = isIncluded)
                    }
                    
                    Log.d("MessagesFragment", "Successfully loaded $loadedCount inclusion states")
                    return updatedGroups
                    
                } catch (e: Exception) {
                    Log.w("MessagesFragment", "Error parsing inclusion states JSON", e)
                }
            } else {
                Log.d("MessagesFragment", "No inclusion states found, using defaults")
            }
            
            // Return original groups with default inclusion states
            groups
            
        } catch (e: Exception) {
            Log.e("MessagesFragment", "Critical error loading inclusion states", e)
            // Return original groups as fallback
            groups
        }
    }
    
    private fun showCategorySelectionDialog(categories: List<String>, currentSelection: String, onCategorySelected: (String) -> Unit) {
        val currentIndex = categories.indexOf(currentSelection).takeIf { it >= 0 } ?: 0
        
        // Create enhanced categories with emojis
        val enhancedCategories = categories.map { category ->
            "${getCategoryEmoji(category)} $category"
        }.toTypedArray()
        
        Log.d("MessagesFragment", "[TARGET] Showing custom category selection dialog, current: $currentSelection")
        
        val dialog = CategorySelectionDialogFragment.newInstance(
            categories = enhancedCategories,
            currentIndex = currentIndex,
            merchantName = "merchant group"
        ) { selectedCategory ->
            // Remove emoji from selected category to get the actual name
            val actualCategory = selectedCategory.replace(Regex("^[\\p{So}\\p{Cn}]\\s+"), "")
            Log.d("MessagesFragment", "[SUCCESS] Category selected from long press: $actualCategory")
            onCategorySelected(actualCategory)
        }
        
        dialog.show(parentFragmentManager, "CategorySelectionDialog")
    }
    
    private fun showMerchantGroupEditDialog(group: MerchantGroup) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_edit_merchant_group,
            null
        )
        
        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.et_group_name)
        val categorySelectorCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.category_selector_card)
        val tvSelectedCategory = dialogView.findViewById<TextView>(R.id.tv_selected_category)
        
        // Enforce text colors programmatically for high contrast
        etGroupName.setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_input_text))
        etGroupName.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_text_hint))
        tvSelectedCategory.setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_text_primary))
        
        // Pre-fill current values
        etGroupName.setText(group.merchantName)
        
        // Setup category selection
        val categories = categoryManager.getAllCategories().toMutableList()
        
        // Ensure "Money" category is available
        if (!categories.contains("Money")) {
            categoryManager.addCustomCategory("Money")
            categories.add("Money")
        }
        
        Log.d("MessagesFragment", "Loading categories for selector: $categories")
        
        // Set current category
        val initialCategory = if (categories.contains(group.category)) group.category else categories.firstOrNull() ?: "Other"
        tvSelectedCategory.text = initialCategory
        
        Log.d("MessagesFragment", "Set initial category to: $initialCategory")
        
        // Handle category selection
        categorySelectorCard.setOnClickListener {
            Log.d("MessagesFragment", "Category selector clicked, showing dialog")
            showCategorySelectionDialog(categories, tvSelectedCategory.text.toString()) { newCategory ->
                tvSelectedCategory.text = newCategory
                Log.d("MessagesFragment", "Category updated to: $newCategory")
            }
        }
        
        // Apply high contrast dialog theme
        val themedContext = ContextThemeWrapper(requireContext(), R.style.DialogTheme)
        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setTitle("Edit Merchant Group")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newGroupName = etGroupName.text.toString().trim()
                val newCategory = tvSelectedCategory.text.toString().trim()
                
                Log.d("MessagesFragment", "Save button clicked - Group: '$newGroupName', Category: '$newCategory'")
                Log.d("MessagesFragment", "Original values - Group: '${group.merchantName}', Category: '${group.category}'")
                
                // Check if anything actually changed
                val groupNameChanged = newGroupName != group.merchantName
                val categoryChanged = newCategory != group.category
                Log.d("MessagesFragment", "Changes detected - Group name changed: $groupNameChanged, Category changed: $categoryChanged")
                
                if (newGroupName.isNotEmpty() && newCategory.isNotEmpty()) {
                    if (groupNameChanged || categoryChanged) {
                        updateMerchantGroup(group, newGroupName, newCategory)
                    } else {
                        Log.d("MessagesFragment", "No changes detected, skipping update")
                        Toast.makeText(
                            requireContext(),
                            "ℹ️ No changes made",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.w("MessagesFragment", "Empty fields detected - Group: '$newGroupName', Category: '$newCategory'")
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
            .setNeutralButton("Reset to Original") { _, _ ->
                resetMerchantGroupToOriginal(group)
            }
            .create()
        
        // Show dialog and adjust dropdown position after it's displayed
        dialog.show()
        
        // Force light background and button colors
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(R.color.dialog_background)
            val params = window.attributes
            params.y = 0 // Center vertically
            window.attributes = params
        }
        
        // Ensure action buttons are visible
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.apply {
            setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.transparent))
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.apply {
            setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.transparent))
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.apply {
            setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.transparent))
        }
    }
    
    private fun updateMerchantGroup(group: MerchantGroup, newDisplayName: String, newCategory: String) {
        lifecycleScope.launch {
            try {
                Log.d("MessagesFragment", "[PROCESS] Starting merchant group update: ${group.merchantName} -> $newDisplayName, category: ${group.category} -> $newCategory")
                
                // Enhanced validation
                if (newDisplayName.trim().isEmpty()) {
                    Toast.makeText(requireContext(), "⚠️ Please enter a display name", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                if (newCategory.trim().isEmpty()) {
                    Toast.makeText(requireContext(), "⚠️ Please select a category", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Find all transactions in this group to get their original merchant names
                val originalMerchantNames = mutableSetOf<String>()
                
                // Enhanced original merchant name detection
                Log.d("MessagesFragment", "[DEBUG] Finding original merchant names for group: ${group.merchantName}")
                
                // METHOD 1: Check if this group already has aliases
                val existingOriginalNames = merchantAliasManager.getMerchantsByDisplayName(group.merchantName)
                Log.d("MessagesFragment", "[LIST] Existing original names from aliases: $existingOriginalNames")
                
                if (existingOriginalNames.isNotEmpty()) {
                    originalMerchantNames.addAll(existingOriginalNames)
                    Log.d("MessagesFragment", "[SUCCESS] Using existing alias mappings")
                } else {
                    Log.d("MessagesFragment", "[DEBUG] No existing aliases found, extracting from transactions...")
                    
                    // METHOD 2: Extract from raw SMS data in transactions
                    var extractedCount = 0
                    group.transactions.forEach { transaction ->
                        val extractedName = extractOriginalMerchantFromRawSMS(transaction.rawSMS)
                        if (extractedName.isNotEmpty() && extractedName.length >= 3) { // Minimum length check
                            originalMerchantNames.add(extractedName)
                            extractedCount++
                            Log.d("MessagesFragment", "📤 Extracted: $extractedName from SMS: ${transaction.rawSMS.take(50)}...")
                        }
                    }
                    
                    Log.d("MessagesFragment", "[ANALYTICS] Extracted $extractedCount original names from ${group.transactions.size} transactions")
                    
                    // METHOD 3: Fallback to current display name if no extraction worked
                    if (originalMerchantNames.isEmpty()) {
                        originalMerchantNames.add(group.merchantName)
                        Log.w("MessagesFragment", "[WARNING] Fallback: Using current display name as original: ${group.merchantName}")
                    }
                    
                    // METHOD 4: Try alternative extraction using transaction data
                    if (originalMerchantNames.size == 1 && originalMerchantNames.first() == group.merchantName) {
                        Log.d("MessagesFragment", "[PROCESS] Attempting alternative merchant name extraction...")
                        group.transactions.forEach { transaction ->
                            // Try different patterns for merchant extraction
                            val alternativeNames = extractAlternativeMerchantNames(transaction.rawSMS)
                            alternativeNames.forEach { name ->
                                if (name.isNotEmpty() && name != group.merchantName) {
                                    originalMerchantNames.add(name)
                                    Log.d("MessagesFragment", "[TARGET] Alternative extraction: $name")
                                }
                            }
                        }
                    }
                }
                
                Log.d("MessagesFragment", "📝 Final original merchant names to update: $originalMerchantNames")
                
                // Enhanced category validation and creation
                val allCategories = categoryManager.getAllCategories()
                Log.d("MessagesFragment", "📂 Available categories: $allCategories")
                
                if (!allCategories.contains(newCategory)) {
                    Log.i("MessagesFragment", "➕ Creating new category: $newCategory")
                    try {
                        categoryManager.addCustomCategory(newCategory)
                        Log.d("MessagesFragment", "[SUCCESS] Successfully added new category: $newCategory")
                    } catch (e: Exception) {
                        Log.e("MessagesFragment", "[ERROR] Failed to add new category: $newCategory", e)
                        Toast.makeText(
                            requireContext(),
                            "⚠️ Unable to create category '$newCategory': ${e.message ?: "Please try again"}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                }
                
                // STEP 1: Update SharedPreferences (MerchantAliasManager)
                Log.d("MessagesFragment", "📝 Step 1: Updating SharedPreferences aliases...")
                var aliasUpdateSuccess = true
                var aliasUpdateError: String? = null
                
                var aliasSetCount = 0
                var aliasFailCount = 0
                
                try {
                    originalMerchantNames.forEachIndexed { index, originalName ->
                        Log.d("MessagesFragment", "📝 Setting alias ${index + 1}/${originalMerchantNames.size}: $originalName -> $newDisplayName ($newCategory)")
                        
                        val aliasSetSuccess = merchantAliasManager.setMerchantAlias(originalName, newDisplayName, newCategory)
                        
                        if (aliasSetSuccess) {
                            aliasSetCount++
                            Log.d("MessagesFragment", "[SUCCESS] Successfully set alias for: $originalName")
                            
                            // Verify the alias was set correctly
                            val verifyDisplayName = merchantAliasManager.getDisplayName(originalName)
                            val verifyCategory = merchantAliasManager.getMerchantCategory(originalName)
                            Log.d("MessagesFragment", "[DEBUG] Verification: $originalName -> display: $verifyDisplayName, category: $verifyCategory")
                            
                            if (verifyDisplayName != newDisplayName || verifyCategory != newCategory) {
                                Log.w("MessagesFragment", "[WARNING] Alias verification failed for $originalName: expected $newDisplayName/$newCategory, got $verifyDisplayName/$verifyCategory")
                            }
                        } else {
                            aliasFailCount++
                            Log.e("MessagesFragment", "[ERROR] Failed to set alias for: $originalName")
                        }
                    }
                    
                    Log.d("MessagesFragment", "[ANALYTICS] SharedPreferences update summary: ${aliasSetCount} success, ${aliasFailCount} failed out of ${originalMerchantNames.size}")
                    
                    // Consider successful if majority of aliases were set
                    if (aliasSetCount > 0 && aliasSetCount >= aliasFailCount) {
                        Log.d("MessagesFragment", "[SUCCESS] SharedPreferences update completed successfully")
                    } else {
                        Log.e("MessagesFragment", "[ERROR] SharedPreferences update failed - too many individual failures")
                        aliasUpdateSuccess = false
                        aliasUpdateError = "$aliasFailCount out of ${originalMerchantNames.size} aliases failed to set"
                    }
                    
                } catch (e: Exception) {
                    Log.e("MessagesFragment", "[ERROR] SharedPreferences update failed", e)
                    aliasUpdateSuccess = false
                    aliasUpdateError = e.message
                }
                
                // STEP 2: Update Database (ExpenseRepository)
                Log.d("MessagesFragment", "[DATABASE] Step 2: Updating database...")
                var databaseUpdateSuccess = false
                var databaseUpdateError: String? = null
                
                if (aliasUpdateSuccess) {
                    try {
                        val repository = com.expensemanager.app.data.repository.ExpenseRepository.getInstance(requireContext())
                        Log.d("MessagesFragment", "[DATABASE] Calling repository.updateMerchantAliasInDatabase with ${originalMerchantNames.size} merchants")
                        
                        databaseUpdateSuccess = repository.updateMerchantAliasInDatabase(
                            originalMerchantNames.toList(),
                            newDisplayName,
                            newCategory
                        )
                        
                        if (databaseUpdateSuccess) {
                            Log.d("MessagesFragment", "[SUCCESS] Database update completed successfully")
                            
                            // Verify database changes
                            originalMerchantNames.take(3).forEach { originalName ->
                                try {
                                    // Use MerchantAliasManager's normalizeMerchantName since repository's is private
                                    val normalizedName = merchantAliasManager.normalizeMerchantName(originalName)
                                    val merchantWithCategory = repository.getMerchantWithCategory(normalizedName)
                                    Log.d("MessagesFragment", "[DEBUG] DB Verification: $originalName -> ${merchantWithCategory?.display_name} (${merchantWithCategory?.category_name})")
                                } catch (e: Exception) {
                                    Log.w("MessagesFragment", "Could not verify database changes for $originalName", e)
                                }
                            }
                        } else {
                            Log.e("MessagesFragment", "[ERROR] Database update failed (returned false)")
                            databaseUpdateError = "Database update returned false"
                        }
                    } catch (e: Exception) {
                        Log.e("MessagesFragment", "[ERROR] Database update failed with exception", e)
                        databaseUpdateSuccess = false
                        databaseUpdateError = e.message
                    }
                } else {
                    Log.w("MessagesFragment", "[SKIP] Skipping database update due to SharedPreferences failure")
                    databaseUpdateError = "SharedPreferences update failed first"
                }
                
                // STEP 3: Enhanced result handling and user feedback
                when {
                    aliasUpdateSuccess && databaseUpdateSuccess -> {
                        Log.d("MessagesFragment", "[SUCCESS] COMPLETE SUCCESS: Both SharedPreferences and Database updated")
                        
                        // Reload data to reflect changes
                        loadHistoricalTransactions()
                        
                        // Also trigger ViewModel update if available
                        try {
                            messagesViewModel.handleEvent(MessagesUIEvent.ResyncSMS)
                        } catch (e: Exception) {
                            Log.w("MessagesFragment", "Could not trigger ViewModel resync", e)
                        }
                        
                        Toast.makeText(
                            requireContext(),
                            "[SUCCESS] Successfully updated '${group.merchantName}' to '$newDisplayName' in category '$newCategory'",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Notify Dashboard and other screens about merchant category change
                        val intent = Intent("com.expensemanager.MERCHANT_CATEGORY_CHANGED")
                        intent.putExtra("merchant_name", group.merchantName)
                        intent.putExtra("display_name", newDisplayName)
                        intent.putExtra("new_category", newCategory)
                        requireContext().sendBroadcast(intent)
                        Log.d("MessagesFragment", "[BROADCAST] Sent merchant category change broadcast for '${group.merchantName}' -> '$newCategory'")
                    }
                    
                    aliasUpdateSuccess && !databaseUpdateSuccess -> {
                        Log.w("MessagesFragment", "[WARNING] PARTIAL SUCCESS: SharedPreferences updated but database failed")
                        
                        // Still reload data to show UI changes
                        loadHistoricalTransactions()
                        
                        Toast.makeText(
                            requireContext(),
                            "[WARNING] Changes saved to app memory but database update failed. Changes may not persist after app restart.\n\nError: ${databaseUpdateError ?: "Unknown database error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    !aliasUpdateSuccess -> {
                        Log.e("MessagesFragment", "[ERROR] COMPLETE FAILURE: SharedPreferences update failed")
                        
                        Toast.makeText(
                            requireContext(),
                            "⚠️ Unable to save changes to app memory.\n\nError: ${aliasUpdateError ?: "Unknown error"}\n\nPlease try again or restart the app.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
                Log.d("MessagesFragment", "🏁 Merchant group update process completed - Success: Alias=$aliasUpdateSuccess, DB=$databaseUpdateSuccess")
                
            } catch (e: Exception) {
                Log.e("MessagesFragment", "💥 Critical error during merchant group update", e)
                Toast.makeText(
                    requireContext(),
                    "💥 Critical error updating group: ${e.message ?: "Unknown error"}\n\nPlease restart the app and try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun extractOriginalMerchantFromRawSMS(rawSMS: String): String {
        // Try to extract the original merchant name from the raw SMS
        // This uses enhanced logic for better merchant name detection
        val patterns = listOf(
            // Standard patterns
            Regex("""at\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""to\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""for\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""UPI[/-]([A-Z][A-Z0-9\s&'-]+?)(?:\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""([A-Z]{3,})\*[A-Z0-9]+""", RegexOption.IGNORE_CASE),
            
            // Additional enhanced patterns
            Regex("""merchant\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""from\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""via\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(rawSMS)
            if (match != null && match.groupValues.size > 1) {
                val merchantName = match.groupValues[1].trim()
                val cleaned = cleanMerchantName(merchantName)
                if (cleaned.isNotEmpty() && cleaned.length >= 3) {
                    Log.d("MessagesFragment", "[TARGET] Extracted merchant: '$cleaned' from SMS using pattern: ${pattern.pattern.take(20)}...")
                    return cleaned
                }
            }
        }
        
        Log.d("MessagesFragment", "[ERROR] No merchant extracted from SMS: ${rawSMS.take(100)}...")
        return ""
    }
    
    private fun extractAlternativeMerchantNames(rawSMS: String): List<String> {
        // Alternative extraction methods for merchant names
        val names = mutableListOf<String>()
        
        try {
            // Method 1: Extract words that look like merchant names (capitalized sequences)
            val capitalizedWords = Regex("""([A-Z][A-Za-z0-9&'-]{2,}(?:\s+[A-Z][A-Za-z0-9&'-]{2,})*)""").findAll(rawSMS)
            capitalizedWords.forEach { match ->
                val word = match.value.trim()
                if (word.length >= 3 && !isCommonWord(word)) {
                    names.add(cleanMerchantName(word))
                }
            }
            
            // Method 2: Extract from transaction references
            val transactionRefs = Regex("""([A-Z]{2,}[0-9]{3,})""").findAll(rawSMS)
            transactionRefs.forEach { match ->
                val ref = match.value
                // Extract letters part as potential merchant
                val merchantPart = ref.replace(Regex("""[0-9]+"""), "")
                if (merchantPart.length >= 3) {
                    names.add(merchantPart)
                }
            }
            
            // Method 3: Extract from payment descriptions
            val descriptions = listOf(
                Regex("""payment\s+to\s+([A-Z][A-Za-z0-9\s&'-]{2,})""", RegexOption.IGNORE_CASE),
                Regex("""paid\s+to\s+([A-Z][A-Za-z0-9\s&'-]{2,})""", RegexOption.IGNORE_CASE),
                Regex("""transfer\s+to\s+([A-Z][A-Za-z0-9\s&'-]{2,})""", RegexOption.IGNORE_CASE)
            )
            
            descriptions.forEach { pattern ->
                pattern.findAll(rawSMS).forEach { match ->
                    if (match.groupValues.size > 1) {
                        val name = cleanMerchantName(match.groupValues[1])
                        if (name.isNotEmpty()) {
                            names.add(name)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.w("MessagesFragment", "Error in alternative merchant name extraction", e)
        }
        
        return names.distinct().filter { it.length >= 3 }
    }
    
    private fun isCommonWord(word: String): Boolean {
        // Filter out common words that aren't merchant names
        val commonWords = setOf(
            "BANK", "CARD", "DEBIT", "CREDIT", "PAYMENT", "TRANSACTION", "TRANSFER",
            "AMOUNT", "BALANCE", "ACCOUNT", "UPI", "NEFT", "RTGS", "IMPS",
            "DATE", "TIME", "FROM", "YOUR", "AVAILABLE", "INFO", "DETAILS"
        )
        return commonWords.contains(word.uppercase())
    }
    
    private fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(Regex("""[*#@\-_]+.*"""), "") // Remove suffixes after special chars
            .replace(Regex("""\s+"""), " ") // Normalize spaces
            .trim()
            .takeIf { it.length >= 2 } ?: ""
    }
    
    private fun resetMerchantGroupToOriginal(group: MerchantGroup) {
        lifecycleScope.launch {
            try {
                // Get all original names for this group
                val originalNames = merchantAliasManager.getMerchantsByDisplayName(group.merchantName)
                
                // Remove aliases for all original names
                originalNames.forEach { originalName ->
                    merchantAliasManager.removeMerchantAlias(originalName)
                }
                
                // If no aliases were found, remove the current display name as well
                if (originalNames.isEmpty()) {
                    merchantAliasManager.removeMerchantAlias(group.merchantName)
                }
                
                // Reload data to reflect changes
                loadHistoricalTransactions()
                
                Toast.makeText(
                    requireContext(),
                    "[SUCCESS] Reset group to original names and categories",
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e: Exception) {
                Log.e("MessagesFragment", "Error resetting merchant group", e)
                Toast.makeText(
                    requireContext(),
                    "⚠️ Unable to reset group: ${e.message ?: "Please try again"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun navigateToTransactionDetails(messageItem: MessageItem) {
        val bundle = Bundle().apply {
            putFloat("amount", messageItem.amount.toFloat())
            putString("merchant", messageItem.merchant)
            putString("bankName", messageItem.bankName)
            putString("category", messageItem.category)
            putString("dateTime", messageItem.dateTime)
            putInt("confidence", messageItem.confidence)
            putString("rawSMS", messageItem.rawSMS)
        }
        
        findNavController().navigate(
            R.id.action_navigation_messages_to_transaction_details,
            bundle
        )
    }
    
    /**
     * Download and export log files to the Downloads directory
     */
    private fun downloadLogs() {
        lifecycleScope.launch {
            try {
                val logFiles = appLogger.getLogFiles()
                
                if (logFiles.isEmpty()) {
                    Toast.makeText(requireContext(), "No log files found", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Create Downloads directory file
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val zipFile = File(downloadsDir, "ExpenseManager_logs_$timestamp.zip")
                
                // Create zip file with all logs
                ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                    logFiles.forEach { logFile ->
                        if (logFile.exists() && logFile.length() > 0) {
                            val entry = ZipEntry(logFile.name)
                            zipOut.putNextEntry(entry)
                            
                            FileInputStream(logFile).use { fileInput ->
                                fileInput.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                        }
                    }
                    
                    // Add a summary file with app info
                    val summaryEntry = ZipEntry("log_summary.txt")
                    zipOut.putNextEntry(summaryEntry)
                    val summary = """
                        Smart Expense Manager - Log Export
                        Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
                        
                        Log Files Included:
                        ${logFiles.joinToString("\n") { "- ${it.name} (${it.length()} bytes)" }}
                        
                        Logging Configuration:
                        ${appLogger.getInitializationStatus()}
                        
                        App Version: ${requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName}
                    """.trimIndent()
                    
                    zipOut.write(summary.toByteArray())
                    zipOut.closeEntry()
                }
                
                // Show success message with file location
                Toast.makeText(
                    requireContext(), 
                    "Logs exported to Downloads/ExpenseManager_logs_$timestamp.zip", 
                    Toast.LENGTH_LONG
                ).show()
                
                // Log the export activity
                appLogger.info("LOG_EXPORT", "Log files exported to ${zipFile.absolutePath}")
                
            } catch (e: Exception) {
                appLogger.error("LOG_EXPORT", "Failed to export logs", e)
                Toast.makeText(requireContext(), "Failed to export logs: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
        Log.d("MessagesFragment", "📡 Registered broadcast receiver for new transactions")
        
        // Refresh UI when user returns (in case they granted permissions in settings)
        checkPermissionsAndSetupUI()
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister broadcast receiver to prevent memory leaks
        try {
            requireContext().unregisterReceiver(newTransactionReceiver)
            Log.d("MessagesFragment", "📡 Unregistered broadcast receiver for new transactions")
        } catch (e: Exception) {
            // Receiver may not have been registered, ignore
            Log.w("MessagesFragment", "Broadcast receiver was not registered, ignoring unregister", e)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}