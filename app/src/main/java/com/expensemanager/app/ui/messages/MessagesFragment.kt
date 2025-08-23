package com.expensemanager.app.ui.messages

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.expensemanager.app.databinding.FragmentMessagesBinding
import com.expensemanager.app.R
import com.expensemanager.app.utils.SMSHistoryReader
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.ui.categories.CategorySelectionDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

class MessagesFragment : Fragment() {
    
    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var groupedMessagesAdapter: GroupedMessagesAdapter
    private lateinit var categoryManager: CategoryManager
    private lateinit var merchantAliasManager: MerchantAliasManager
    
    // Data management
    private var allMessageItems = listOf<MessageItem>()
    private var filteredMessageItems = listOf<MessageItem>()
    private var currentSearchQuery = ""
    
    // Sorting and filtering state
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
                        Log.d("MessagesFragment", "🔄 Refreshing messages due to new transaction")
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
        checkPermissionsAndSetupUI()
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
                updateExpenseCalculations()
            },
            onGroupEdit = { group ->
                showMerchantGroupEditDialog(group)
            }
        )
        binding.recyclerMessages.apply {
            adapter = groupedMessagesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        
        // Initialize with empty list - real data will be loaded by loadHistoricalTransactions()
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
                currentSearchQuery = s?.toString() ?: ""
                applyFiltersAndSort()
            }
        })
    }
    
    private fun showFilterMenu() {
        val options = arrayOf(
            "🔄 Resync SMS Messages", 
            "📅 Filter by Date", 
            "💰 Filter by Amount",
            "🏦 Filter by Bank",
            "🔍 Test SMS Scanning"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter & Sync Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> resyncSMSMessages()
                    1 -> showDateFilterDialog()
                    2 -> showAmountFilterDialog()
                    3 -> showBankFilterDialog()
                    4 -> testSMSScanning()
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
                Log.d("MessagesFragment", "🔍 User selected sort option: $which (${sortOptions[which]})")
                
                val oldSortOption = currentSortOption
                currentSortOption = when (which) {
                    0 -> SortOption("Date (Newest First)", "date", false)
                    1 -> SortOption("Date (Oldest First)", "date", true)
                    2 -> SortOption("Amount (Highest First)", "amount", false)
                    3 -> SortOption("Amount (Lowest First)", "amount", true)
                    4 -> SortOption("Merchant Name (A-Z)", "merchant", true)
                    5 -> SortOption("Merchant Name (Z-A)", "merchant", false)
                    6 -> SortOption("Bank Name (A-Z)", "bank", true)
                    7 -> SortOption("Bank Name (Z-A)", "bank", false)
                    8 -> SortOption("Confidence (Highest First)", "confidence", false)
                    9 -> SortOption("Confidence (Lowest First)", "confidence", true)
                    else -> currentSortOption
                }
                
                Log.d("MessagesFragment", "🔍 Sort option changed from ${oldSortOption.name} to ${currentSortOption.name}")
                
                binding.btnSort.text = "Sort: ${currentSortOption.name.split(" ")[0]}"
                Log.d("MessagesFragment", "🔍 Updated sort button text to: ${binding.btnSort.text}")
                
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
        Log.d("MessagesFragment", "🔍 Starting applyFiltersAndSort() with ${allMessageItems.size} total items")
        Log.d("MessagesFragment", "🔍 Current sort option: ${currentSortOption.name} (field: ${currentSortOption.field}, ascending: ${currentSortOption.ascending})")
        
        // Start with all message items
        var filtered = allMessageItems.toList()
        Log.d("MessagesFragment", "🔍 After copying all items: ${filtered.size} items")
        
        // Apply search filter
        if (currentSearchQuery.isNotEmpty()) {
            val query = currentSearchQuery.lowercase()
            filtered = filtered.filter { item ->
                item.merchant.lowercase().contains(query) ||
                item.bankName.lowercase().contains(query) ||
                item.rawSMS.lowercase().contains(query) ||
                item.category.lowercase().contains(query)
            }
            Log.d("MessagesFragment", "🔍 After search filter '$query': ${filtered.size} items")
        }
        
        // Apply amount filter
        currentFilterOptions.minAmount?.let { minAmount ->
            filtered = filtered.filter { it.amount >= minAmount }
            Log.d("MessagesFragment", "🔍 After min amount filter (≥$minAmount): ${filtered.size} items")
        }
        currentFilterOptions.maxAmount?.let { maxAmount ->
            filtered = filtered.filter { it.amount <= maxAmount }
            Log.d("MessagesFragment", "🔍 After max amount filter (≤$maxAmount): ${filtered.size} items")
        }
        
        // Apply bank filter
        if (currentFilterOptions.selectedBanks.isNotEmpty()) {
            filtered = filtered.filter { currentFilterOptions.selectedBanks.contains(it.bankName) }
            Log.d("MessagesFragment", "🔍 After bank filter (${currentFilterOptions.selectedBanks}): ${filtered.size} items")
        }
        
        // Apply confidence filter
        if (currentFilterOptions.minConfidence > 0) {
            filtered = filtered.filter { it.confidence >= currentFilterOptions.minConfidence }
            Log.d("MessagesFragment", "🔍 After confidence filter (≥${currentFilterOptions.minConfidence}%): ${filtered.size} items")
        }
        
        // Apply date filters (simplified - would need proper date parsing in real implementation)
        currentFilterOptions.dateFrom?.let { dateFrom ->
            // In a real implementation, you'd parse the date string and compare properly
            filtered = filtered.filter { it.dateTime >= dateFrom }
            Log.d("MessagesFragment", "🔍 After date from filter (≥$dateFrom): ${filtered.size} items")
        }
        currentFilterOptions.dateTo?.let { dateTo ->
            // In a real implementation, you'd parse the date string and compare properly
            filtered = filtered.filter { it.dateTime <= dateTo }
            Log.d("MessagesFragment", "🔍 After date to filter (≤$dateTo): ${filtered.size} items")
        }
        
        Log.d("MessagesFragment", "🔍 Before sorting: ${filtered.size} items")
        if (filtered.isNotEmpty()) {
            Log.d("MessagesFragment", "🔍 Sample items before sorting:")
            filtered.take(3).forEach { item ->
                Log.d("MessagesFragment", "  - ${item.merchant}: ₹${item.amount} (${item.dateTime})")
            }
        }
        
        // Apply sorting
        filtered = when (currentSortOption.field) {
            "date" -> {
                if (currentSortOption.ascending) {
                    filtered.sortedBy { getDateSortOrderReverse(it.dateTime) }
                } else {
                    filtered.sortedByDescending { getDateSortOrderReverse(it.dateTime) }
                }
            }
            "amount" -> {
                if (currentSortOption.ascending) {
                    filtered.sortedBy { it.amount }
                } else {
                    filtered.sortedByDescending { it.amount }
                }
            }
            "merchant" -> {
                if (currentSortOption.ascending) {
                    filtered.sortedBy { it.merchant.lowercase() }
                } else {
                    filtered.sortedByDescending { it.merchant.lowercase() }
                }
            }
            "bank" -> {
                if (currentSortOption.ascending) {
                    filtered.sortedBy { it.bankName.lowercase() }
                } else {
                    filtered.sortedByDescending { it.bankName.lowercase() }
                }
            }
            "confidence" -> {
                if (currentSortOption.ascending) {
                    filtered.sortedBy { it.confidence }
                } else {
                    filtered.sortedByDescending { it.confidence }
                }
            }
            else -> {
                Log.w("MessagesFragment", "🔍 Unknown sort field: ${currentSortOption.field}")
                filtered
            }
        }
        
        Log.d("MessagesFragment", "🔍 After sorting: ${filtered.size} items")
        if (filtered.isNotEmpty()) {
            Log.d("MessagesFragment", "🔍 Sample items after sorting:")
            filtered.take(3).forEach { item ->
                Log.d("MessagesFragment", "  - ${item.merchant}: ₹${item.amount} (${item.dateTime})")
            }
        }
        
        filteredMessageItems = filtered
        
        // Update UI
        updateTransactionsList(filteredMessageItems)
        updateSummaryStats(filteredMessageItems)
        
        Log.d("MessagesFragment", "🔍 applyFiltersAndSort() completed successfully")
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
                .setTitle("🔄 Syncing SMS Messages")
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
                            rawSMS = transaction.rawSMS
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
                        "✅ Found ${historicalTransactions.size} new transaction SMS messages!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // No transactions found - show empty state
                    groupedMessagesAdapter.submitList(emptyList())
                    binding.recyclerMessages.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.tvTotalMessages.text = "0"
                    binding.tvAutoCategorized.text = "0"
                    
                    progressDialog.dismiss()
                    
                    Toast.makeText(
                        requireContext(),
                        "📱 No transaction SMS found in your inbox",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: SecurityException) {
                progressDialog.dismiss()
                Log.w("MessagesFragment", "SMS permission denied during resync", e)
                Toast.makeText(
                    requireContext(),
                    "❌ SMS permission required for resync",
                    Toast.LENGTH_LONG
                ).show()
                showNoPermissionState()
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e("MessagesFragment", "Error during SMS resync", e)
                Toast.makeText(
                    requireContext(),
                    "❌ Error syncing SMS: ${e.message ?: "Unknown error"}",
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
                    appendLine("📊 SMS Scanning Test Results:")
                    appendLine("Total valid transactions: ${transactions.size}")
                    appendLine()
                    
                    if (transactions.isNotEmpty()) {
                        appendLine("📱 Found transactions:")
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
                        appendLine("💰 Total amount: ₹${String.format("%.2f", totalAmount)}")
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
                    .setTitle("🔍 SMS Test Results")
                    .setMessage(message)
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                
                Log.d("MessagesFragment", "SMS Test: Found ${transactions.size} transactions")
                
            } catch (e: SecurityException) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("❌ Permission Error")
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
                    .setTitle("❌ Test Error")
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
                Log.d("MessagesFragment", "🔄 Loading historical transactions...")
                
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
                Log.d("MessagesFragment", "📊 Found ${dbTransactions.size} transactions in database")
                
                if (dbTransactions.isNotEmpty()) {
                    // Convert to MessageItem format with proper deduplication
                    val messageItems = dbTransactions.mapNotNull { transaction ->
                        try {
                            val merchantWithCategory = repository.getMerchantWithCategory(transaction.normalizedMerchant)
                            val category = merchantWithCategory?.category_name ?: "Other"
                            val categoryColor = merchantWithCategory?.category_color ?: "#888888"
                            
                            MessageItem(
                                amount = transaction.amount,
                                merchant = transaction.rawMerchant, // Use display name
                                bankName = transaction.bankName,
                                category = category,
                                categoryColor = categoryColor,
                                confidence = (transaction.confidenceScore * 100).toInt(),
                                dateTime = formatDate(transaction.transactionDate),
                                rawSMS = transaction.rawSmsBody
                            )
                        } catch (e: Exception) {
                            Log.w("MessagesFragment", "Error converting transaction: ${e.message}")
                            null
                        }
                    }.distinctBy { 
                        // Remove duplicates based on merchant, amount, and date
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
                    
                    Log.d("MessagesFragment", "✅ Successfully loaded ${messageItems.size} unique transactions from database")
                    
                } else {
                    // No database data found - fallback to SMS scanning
                    Log.d("MessagesFragment", "📱 No database transactions found, falling back to SMS scanning...")
                    loadFromSMSFallback()
                }
            } catch (e: SecurityException) {
                // Permission denied
                Log.w("MessagesFragment", "SMS permission denied", e)
                Toast.makeText(
                    requireContext(),
                    "❌ SMS permission required to read transaction messages",
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
                    "⚠️ Error reading SMS: ${e.message ?: "Unknown error"}",
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
                
                Log.d("MessagesFragment", "📱 SMS Fallback: Found ${historicalTransactions.size} transactions")
                
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
                            rawSMS = transaction.rawSMS
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
                    
                    Log.d("MessagesFragment", "✅ SMS Fallback: Loaded ${messageItems.size} unique transactions")
                } else {
                    showEmptyState()
                }
            } catch (e: Exception) {
                Log.e("MessagesFragment", "Error in SMS fallback", e)
                showEmptyState()
            }
        }
    }
    
    private fun showEmptyState() {
        groupedMessagesAdapter.submitList(emptyList())
        binding.recyclerMessages.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvTotalMessages.text = "0"
        binding.tvAutoCategorized.text = "0"
        
        Log.d("MessagesFragment", "📭 Showing empty state")
    }
    
    private fun showCategoryEditDialog(messageItem: MessageItem) {
        val categories = categoryManager.getAllCategories()
        val currentCategoryIndex = categories.indexOf(messageItem.category)
        
        // Create enhanced categories with emojis
        val enhancedCategories = categories.map { category ->
            "${getCategoryEmoji(category)} $category"
        }.toTypedArray()
        
        Log.d("MessagesFragment", "🎯 Showing custom category dialog for ${messageItem.merchant}")
        
        val dialog = CategorySelectionDialogFragment.newInstance(
            categories = enhancedCategories,
            currentIndex = currentCategoryIndex,
            merchantName = messageItem.merchant
        ) { selectedCategory ->
            // Remove emoji from selected category to get the actual name
            val actualCategory = selectedCategory.replace(Regex("^[\\p{So}\\p{Cn}]\\s+"), "")
            Log.d("MessagesFragment", "✅ Category selected: $actualCategory for ${messageItem.merchant}")
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
            "money", "finance" -> "💰"
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
                    .setTitle("Category Updated! ✅")
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
            else -> 0L // Very old
        }
    }
    
    private fun groupTransactionsByMerchant(transactions: List<MessageItem>): List<MerchantGroup> {
        Log.d("MessagesFragment", "🔍 Grouping ${transactions.size} transactions by merchant")
        Log.d("MessagesFragment", "🔍 Current sort option for grouping: ${currentSortOption.name}")
        
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
        
        Log.d("MessagesFragment", "🔍 Created ${sortedGroups.size} merchant groups, sorted by ${currentSortOption.field}")
        
        // Load saved inclusion states
        return loadGroupInclusionStates(sortedGroups)
    }
    
    
    private fun updateExpenseCalculations() {
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
        
        // Log for debugging
        val totalIncludedAmount = includedGroups.sumOf { it.totalAmount }
        android.util.Log.d("MessagesFragment", "Updated calculations: $totalMessages messages, ₹${String.format("%.0f", totalIncludedAmount)} total from included groups")
    }
    
    private fun saveGroupInclusionStates(groups: List<MerchantGroup>) {
        val prefs = requireContext().getSharedPreferences("expense_calculations", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Save inclusion states as JSON
        val inclusionStates = org.json.JSONObject()
        groups.forEach { group ->
            inclusionStates.put(group.merchantName, group.isIncludedInCalculations)
        }
        
        editor.putString("group_inclusion_states", inclusionStates.toString())
        editor.apply()
    }
    
    private fun loadGroupInclusionStates(groups: List<MerchantGroup>): List<MerchantGroup> {
        val prefs = requireContext().getSharedPreferences("expense_calculations", android.content.Context.MODE_PRIVATE)
        val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
        
        if (inclusionStatesJson != null) {
            try {
                val inclusionStates = org.json.JSONObject(inclusionStatesJson)
                return groups.map { group ->
                    val isIncluded = if (inclusionStates.has(group.merchantName)) {
                        inclusionStates.getBoolean(group.merchantName)
                    } else {
                        true // Default to included
                    }
                    group.copy(isIncludedInCalculations = isIncluded)
                }
            } catch (e: Exception) {
                android.util.Log.w("MessagesFragment", "Error loading inclusion states", e)
            }
        }
        
        return groups
    }
    
    private fun showCategorySelectionDialog(categories: List<String>, currentSelection: String, onCategorySelected: (String) -> Unit) {
        val currentIndex = categories.indexOf(currentSelection).takeIf { it >= 0 } ?: 0
        
        // Create enhanced categories with emojis
        val enhancedCategories = categories.map { category ->
            "${getCategoryEmoji(category)} $category"
        }.toTypedArray()
        
        Log.d("MessagesFragment", "🎯 Showing custom category selection dialog, current: $currentSelection")
        
        val dialog = CategorySelectionDialogFragment.newInstance(
            categories = enhancedCategories,
            currentIndex = currentIndex,
            merchantName = "merchant group"
        ) { selectedCategory ->
            // Remove emoji from selected category to get the actual name
            val actualCategory = selectedCategory.replace(Regex("^[\\p{So}\\p{Cn}]\\s+"), "")
            Log.d("MessagesFragment", "✅ Category selected from long press: $actualCategory")
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
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
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
        
        // Adjust dropdown to appear near the center of the dialog
        dialog.window?.let { window ->
            val params = window.attributes
            params.y = 0 // Center vertically
            window.attributes = params
        }
    }
    
    private fun updateMerchantGroup(group: MerchantGroup, newDisplayName: String, newCategory: String) {
        lifecycleScope.launch {
            try {
                Log.d("MessagesFragment", "Updating merchant group: ${group.merchantName} -> $newDisplayName, category: ${group.category} -> $newCategory")
                
                // Find all transactions in this group to get their original merchant names
                val originalMerchantNames = mutableSetOf<String>()
                
                // If this group already has aliases, get all the original names
                val existingOriginalNames = merchantAliasManager.getMerchantsByDisplayName(group.merchantName)
                Log.d("MessagesFragment", "Found existing original names: $existingOriginalNames")
                
                if (existingOriginalNames.isNotEmpty()) {
                    originalMerchantNames.addAll(existingOriginalNames)
                } else {
                    // This group has no aliases yet, so we need to find the original merchant names
                    // by looking at the raw SMS data in the transactions
                    group.transactions.forEach { transaction ->
                        // Extract original merchant name from raw SMS
                        val originalName = extractOriginalMerchantFromRawSMS(transaction.rawSMS)
                        if (originalName.isNotEmpty()) {
                            originalMerchantNames.add(originalName)
                            Log.d("MessagesFragment", "Extracted original name from SMS: $originalName")
                        }
                    }
                    
                    // If we couldn't extract from raw SMS, use the current display name as original
                    if (originalMerchantNames.isEmpty()) {
                        originalMerchantNames.add(group.merchantName)
                        Log.d("MessagesFragment", "Using current display name as original: ${group.merchantName}")
                    }
                }
                
                Log.d("MessagesFragment", "All original merchant names to update: $originalMerchantNames")
                
                // Validate the new category exists in CategoryManager
                val allCategories = categoryManager.getAllCategories()
                if (!allCategories.contains(newCategory)) {
                    Log.w("MessagesFragment", "Category '$newCategory' not found in available categories: $allCategories")
                    // Add the category if it's new
                    categoryManager.addCustomCategory(newCategory)
                    Log.d("MessagesFragment", "Added new category '$newCategory' to CategoryManager")
                }
                
                // Create/update aliases for all original merchant names
                originalMerchantNames.forEach { originalName ->
                    Log.d("MessagesFragment", "Setting alias: $originalName -> $newDisplayName ($newCategory)")
                    merchantAliasManager.setMerchantAlias(originalName, newDisplayName, newCategory)
                }
                
                Log.d("MessagesFragment", "All aliases updated, reloading historical transactions...")
                
                // Reload data to reflect changes
                loadHistoricalTransactions()
                
                Toast.makeText(
                    requireContext(),
                    "✅ Updated group '$newDisplayName' in category '$newCategory'",
                    Toast.LENGTH_LONG
                ).show()
                
                Log.d("MessagesFragment", "Merchant group update completed successfully")
                
            } catch (e: Exception) {
                Log.e("MessagesFragment", "Error updating merchant group", e)
                Toast.makeText(
                    requireContext(),
                    "❌ Error updating group: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun extractOriginalMerchantFromRawSMS(rawSMS: String): String {
        // Try to extract the original merchant name from the raw SMS
        // This uses the same logic as the SMSHistoryReader
        val patterns = listOf(
            Regex("""at\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""to\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s+for\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""for\s+([A-Z][A-Z0-9\s&'-]+?)(?:\s+on\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""UPI[/-]([A-Z][A-Z0-9\s&'-]+?)(?:\s+|\s*\.|,|$)""", RegexOption.IGNORE_CASE),
            Regex("""([A-Z]{3,})\*[A-Z0-9]+""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(rawSMS)
            if (match != null) {
                val merchantName = match.groupValues[1].trim()
                return cleanMerchantName(merchantName)
            }
        }
        
        return ""
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
                    "✅ Reset group to original names and categories",
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e: Exception) {
                Log.e("MessagesFragment", "Error resetting merchant group", e)
                Toast.makeText(
                    requireContext(),
                    "❌ Error resetting group: ${e.message}",
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

data class MessageItem(
    val amount: Double,
    val merchant: String,
    val bankName: String,
    val category: String,
    val categoryColor: String,
    val confidence: Int,
    val dateTime: String,
    val rawSMS: String
)