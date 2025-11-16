package com.smartexpenseai.app.ui.messages

import android.Manifest
import com.smartexpenseai.app.parsing.engine.MerchantRuleEngine
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.view.ContextThemeWrapper
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.navigation.fragment.findNavController
import com.smartexpenseai.app.databinding.FragmentMessagesBinding
import com.smartexpenseai.app.R
import com.smartexpenseai.app.utils.SMSHistoryReader
import com.smartexpenseai.app.utils.CategoryManager
import com.smartexpenseai.app.utils.MerchantAliasManager
import com.smartexpenseai.app.ui.categories.CategorySelectionDialogFragment
// UPDATED: Import unified services for consistent SMS parsing and filtering
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import javax.inject.Inject
import com.smartexpenseai.app.utils.logging.StructuredLogger

@AndroidEntryPoint
class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewBinder: MessagesViewBinder
    private lateinit var groupedMessagesAdapter: GroupedMessagesAdapter

    // ViewModel injection
    private val messagesViewModel: MessagesViewModel by viewModels()
    
    // Hilt-injected unified services for consistent parsing and filtering
    @Inject
    lateinit var smsHistoryReader: SMSHistoryReader

    private val logger = StructuredLogger("UI", MessagesFragment::class.java.simpleName)
    private lateinit var categoryManager: CategoryManager
    private lateinit var merchantAliasManager: com.smartexpenseai.app.utils.MerchantAliasManager

    companion object {
        private const val CATEGORY_BROADCAST_DELAY_MS = 100L
    }

    // Legacy data management (kept for parallel approach during migration)
    private var allMessageItems = listOf<MessageItem>()
    private var filteredMessageItems = listOf<MessageItem>()
    
    // Performance optimization: Caches for date operations
    private val dateParsingCache = mutableMapOf<String, Long>()
    private val dateFormattingCache = mutableMapOf<Long, String>()
    private var currentSearchQuery = ""
    
    // Legacy sorting and filtering state (kept for backward compatibility)
    data class SortOption(val name: String, val field: String, val ascending: Boolean)
    data class FilterOptions(
        val minAmount: Double? = null,
        val maxAmount: Double? = null,
        val selectedBanks: Set<String> = emptySet(),
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
                logger.debug("MessagesFragment", "New transaction broadcast: $merchant - ₹${String.format("%.0f", amount)}")
                
                // Refresh messages data on the main thread
                lifecycleScope.launch {
                    try {
                        logger.debug("MessagesFragment", "[REFRESH] Refreshing messages via ViewModel due to new transaction")
                        
                        // Invalidate ViewModel's cache to ensure it gets fresh alias data
                        try {
                            messagesViewModel.invalidateMerchantAliasCache()
                        } catch (e: Exception) {
                            logger.warnWithThrowable("MessagesFragment", "Could not invalidate ViewModel cache for new transaction", e)
                        }

                        try {
                            messagesViewModel.handleEvent(MessagesUIEvent.LoadMessages)
                        } catch (e: Exception) {
                            logger.warnWithThrowable("MessagesFragment", "Could not trigger ViewModel refresh for new transaction", e)
                        }
                        
                    } catch (e: Exception) {
                        logger.error("MessagesFragment", "Error refreshing messages after new transaction", e)
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
        val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
        val merchantRuleEngine = com.smartexpenseai.app.parsing.engine.MerchantRuleEngine(requireContext())
        categoryManager = CategoryManager(requireContext(), repository, merchantRuleEngine)
        merchantAliasManager = com.smartexpenseai.app.utils.MerchantAliasManager(requireContext(), repository, categoryManager)
        
        viewBinder = MessagesViewBinder(
            binding = binding,
            context = requireContext(),
            logger = StructuredLogger("UI", "MessagesViewBinder"),
            onTransactionClick = { messageItem -> navigateToTransactionDetails(messageItem) },
            onGroupToggle = { group, isIncluded -> handleGroupToggle(group, isIncluded) },
            onGroupEdit = { group -> showMerchantGroupEditDialog(group) },
            onLoadMore = {
                messagesViewModel.handleEvent(MessagesUIEvent.LoadMoreMessages)
            }
        )

        viewBinder.bindSearch { query ->
            messagesViewModel.handleEvent(MessagesUIEvent.Search(query))
            currentSearchQuery = query
        }

        viewBinder.bindFilterTabs { filterTab ->
            messagesViewModel.handleEvent(MessagesUIEvent.ApplyFilterTab(filterTab))
        }

        viewBinder.bindFilterButtons(
            onSortClick = { showSortMenu() },
            onFilterClick = { showFilterDialog() },
            onGrantPermissionClick = { openAppSettings() }
        )

        groupedMessagesAdapter = viewBinder.groupedAdapter

        observeViewModelState()
        messagesViewModel.startInitialLoad()
        checkPermissionsAndSetupUI()
    }

    private fun handleGroupToggle(group: MerchantGroup, isIncluded: Boolean) {
        messagesViewModel.handleEvent(
            MessagesUIEvent.ToggleGroupInclusion(
                merchantName = group.merchantName,
                isIncluded = isIncluded
            )
        )
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
        logger.debug("MessagesFragment", "State updated: loading=${state.isAnyLoading}, groups=${state.groupedMessages.size}, filtered=${state.filteredMessages.size}")

        allMessageItems = state.allMessages
        filteredMessageItems = state.filteredMessages

        // Update visibility BEFORE submitting data
        when {
            state.isAnyLoading -> {
                viewBinder.showLoadingState()
                // Don't submit data while loading
                return
            }
            state.shouldShowEmptyState -> {
                viewBinder.showEmptyState()
                // Clear adapter when showing empty state
                viewBinder.submitMerchantGroups(emptyList())
            }
            state.groupedMessages.isNotEmpty() -> {
                viewBinder.showContent()
                // Submit data AFTER making RecyclerView visible
                viewBinder.submitMerchantGroups(state.groupedMessages)

                // Log sorted groups for debugging
                logger.debug("updateUI", "📋 Submitted ${state.groupedMessages.size} groups sorted by ${state.currentSortOption.field}. First 3: ${
                    state.groupedMessages.take(3).map { "${it.merchantName} (₹${String.format("%.0f", it.totalAmount)})" }.joinToString(", ")
                }")
            }
            else -> {
                // Fallback case - no data and not loading
                viewBinder.showEmptyState()
                viewBinder.submitMerchantGroups(emptyList())
            }
        }

        val uniqueMerchants = state.filteredMessages.map { it.merchant }.toSet().size
        val uniqueBanks = state.filteredMessages.map { it.bankName }.toSet().size
        val averageConfidence = if (state.filteredMessages.isNotEmpty()) {
            state.filteredMessages.map { it.confidence }.average().toInt()
        } else {
            0
        }

        viewBinder.updateSummary(
            totalMessages = state.totalMessagesCount,
            autoCategorized = state.autoCategorizedCount,
            uniqueMerchants = uniqueMerchants,
            uniqueBanks = uniqueBanks,
            averageConfidence = averageConfidence
        )

        currentSortOption = SortOption(
            name = state.currentSortOption.name,
            field = state.currentSortOption.field,
            ascending = state.currentSortOption.ascending
        )
        currentFilterOptions = FilterOptions(
            minAmount = state.currentFilterOptions.minAmount,
            maxAmount = state.currentFilterOptions.maxAmount,
            selectedBanks = state.currentFilterOptions.selectedBanks,
            dateFrom = state.currentFilterOptions.dateFrom,
            dateTo = state.currentFilterOptions.dateTo
        )
        currentSearchQuery = state.searchQuery

        viewBinder.updateSortLabel("Sort: ${state.currentSortOption.displayText}")
        viewBinder.updateFilterLabel(
            if (state.hasActiveFilters) "Filter (${state.activeFilterCount})" else "Filter"
        )

        viewBinder.updateFilterTabSelection(state.currentFilterTab)

        if (state.shouldShowError && state.error != null) {
            Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show()
            messagesViewModel.handleEvent(MessagesUIEvent.ClearError)
        }

        state.successMessage?.let { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            messagesViewModel.handleEvent(MessagesUIEvent.ClearError)
        }

        state.syncMessage?.let { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            messagesViewModel.handleEvent(MessagesUIEvent.ClearError)
        }

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
            // Don't show loading state here - ViewModel state controls UI
            // viewBinder.showLoadingState() is handled by updateUI() based on state.isAnyLoading
            logger.debug("MessagesFragment", "SMS permissions granted, ViewModel will control loading state")
        } else {
            viewBinder.showPermissionState()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    private fun showFilterMenu() {
        val options = arrayOf(
            "[PROCESS] Resync SMS Messages", 
            "Filter by Date", 
            "[FINANCIAL] Filter by Amount",
            "Filter by Bank",
            "Test SMS Scanning"
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
        
        MaterialAlertDialogBuilder(requireContext(), R.style.DialogTheme)
            .setTitle("Sort Transactions")
            .setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                
                val oldSortOption = currentSortOption
                val newSortOption = when (which) {
                    0 -> com.smartexpenseai.app.ui.messages.SortOption("Date (Newest First)", "date", false)
                    1 -> com.smartexpenseai.app.ui.messages.SortOption("Date (Oldest First)", "date", true)
                    2 -> com.smartexpenseai.app.ui.messages.SortOption("Amount (Highest First)", "amount", false)
                    3 -> com.smartexpenseai.app.ui.messages.SortOption("Amount (Lowest First)", "amount", true)
                    4 -> com.smartexpenseai.app.ui.messages.SortOption("Merchant Name (A-Z)", "merchant", true)
                    5 -> com.smartexpenseai.app.ui.messages.SortOption("Merchant Name (Z-A)", "merchant", false)
                    6 -> com.smartexpenseai.app.ui.messages.SortOption("Bank Name (A-Z)", "bank", true)
                    7 -> com.smartexpenseai.app.ui.messages.SortOption("Bank Name (Z-A)", "bank", false)
                    8 -> com.smartexpenseai.app.ui.messages.SortOption("Confidence (Highest First)", "confidence", false)
                    9 -> com.smartexpenseai.app.ui.messages.SortOption("Confidence (Lowest First)", "confidence", true)
                    else -> return@setSingleChoiceItems
                }
                

                // Use ViewModel event - ViewModel is the single source of truth
                messagesViewModel.handleEvent(MessagesUIEvent.ApplySort(newSortOption))
                logger.debug("showSortMenu", "🔀 User selected sort: field=${newSortOption.field}, name=${newSortOption.name}")

                // Note: currentSortOption will be synced from ViewModel state in updateUI()
                // Removed redundant applyFiltersAndSort() call - ViewModel handles sorting

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
        val chipGroupBanks = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_group_banks)
        
        // Set current values
        etMinAmount.setText(currentFilterOptions.minAmount?.toString() ?: "")
        etMaxAmount.setText(currentFilterOptions.maxAmount?.toString() ?: "")
        etDateFrom.setText(currentFilterOptions.dateFrom ?: "")
        etDateTo.setText(currentFilterOptions.dateTo ?: "")

        // Setup DatePicker for From Date
        etDateFrom.setOnClickListener {
            showDatePickerDialog { selectedDate ->
                etDateFrom.setText(selectedDate)
            }
        }

        // Setup DatePicker for To Date
        etDateTo.setOnClickListener {
            showDatePickerDialog { selectedDate ->
                etDateTo.setText(selectedDate)
            }
        }

        // Add bank chips
        val uniqueBanks = allMessageItems.map { it.bankName }.distinct().sorted()
        logger.debug("showFilterDialog", "[FILTER_DEBUG] allMessageItems.size=${allMessageItems.size}, uniqueBanks=$uniqueBanks")
        chipGroupBanks.removeAllViews()
        uniqueBanks.forEach { bankName ->
            val chip = com.google.android.material.chip.Chip(requireContext())
            chip.text = bankName
            chip.isCheckable = true
            chip.isChecked = currentFilterOptions.selectedBanks.contains(bankName)
            chipGroupBanks.addView(chip)
        }
        logger.debug("showFilterDialog", "[FILTER_DEBUG] Added ${chipGroupBanks.childCount} bank chips to ChipGroup")
        
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.DialogTheme)
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

                // Create filter options
                val filterOptions = com.smartexpenseai.app.ui.messages.FilterOptions(
                    minAmount = etMinAmount.text.toString().toDoubleOrNull(),
                    maxAmount = etMaxAmount.text.toString().toDoubleOrNull(),
                    selectedBanks = selectedBanks,
                    dateFrom = etDateFrom.text.toString().takeIf { it.isNotEmpty() },
                    dateTo = etDateTo.text.toString().takeIf { it.isNotEmpty() }
                )

                // Use ViewModel to apply filters - ViewModel is the single source of truth
                messagesViewModel.handleEvent(MessagesUIEvent.ApplyFilter(filterOptions))

                logger.debug("showFilterDialog", "Applied filters via ViewModel: $filterOptions")
            }
            .setNegativeButton("Reset", null) // Set to null initially, will override below
            .setNeutralButton("Cancel", null)
            .create()

        dialog.show()

        // Override Reset button to prevent dialog from closing
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
            // Set to current month's date range
            val calendar = java.util.Calendar.getInstance()

            // First day of current month
            calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            val firstDayOfMonth = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)

            // Last day of current month
            calendar.set(java.util.Calendar.DAY_OF_MONTH, calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
            val lastDayOfMonth = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)

            // Set filter fields to current month
            etMinAmount.setText("")
            etMaxAmount.setText("")
            etDateFrom.setText(firstDayOfMonth)
            etDateTo.setText(lastDayOfMonth)

            // Uncheck all bank chips
            for (i in 0 until chipGroupBanks.childCount) {
                val chip = chipGroupBanks.getChildAt(i) as com.google.android.material.chip.Chip
                chip.isChecked = false
            }

            logger.debug("showFilterDialog", "Reset filter fields to current month: $firstDayOfMonth to $lastDayOfMonth")

            // Don't dismiss dialog - user can see the reset fields and then Apply or Cancel
        }
        
        // Apply additional programmatic styling for high contrast (similar to category dialog)
        try {
            // Apply high contrast colors to dialog buttons
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.transparent))
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.transparent))
            }
            
            // Apply high contrast to input fields
            dialogView.findViewById<TextInputEditText>(R.id.et_min_amount)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_input_text))
                setHintTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_text_hint))
            }
            dialogView.findViewById<TextInputEditText>(R.id.et_max_amount)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_input_text))
                setHintTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_text_hint))
            }
            dialogView.findViewById<TextInputEditText>(R.id.et_date_from)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_input_text))
                setHintTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_text_hint))
            }
            dialogView.findViewById<TextInputEditText>(R.id.et_date_to)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_input_text))
                setHintTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_text_hint))
            }
        } catch (e: Exception) {
            logger.warnWithThrowable("MessagesFragment", "Error applying high contrast styling to filter dialog", e)
        }
    }
    
    // REMOVED: Fragment no longer does its own filtering
    // All filtering is now handled by MessagesViewModel as the single source of truth
    // The ViewModel's filtered data comes through the UI state observation in updateUI()
    
    private fun updateTransactionsList(messageItems: List<MessageItem>) {
        if (messageItems.isEmpty()) {
            viewBinder.showEmptyState()
            return
        }

        viewBinder.showContent()
        viewBinder.submitMerchantGroups(groupTransactionsByMerchant(messageItems))
    }

    private fun updateSummaryStats(messageItems: List<MessageItem>) {
        val totalMessages = messageItems.size
        val autoCategorized = messageItems.count { it.category != "Other" && it.confidence >= 80 }
        val uniqueMerchants = messageItems.map { it.merchant }.toSet().size
        val uniqueBanks = messageItems.map { it.bankName }.toSet().size
        val averageConfidence = if (messageItems.isNotEmpty()) {
            messageItems.map { it.confidence }.average().toInt()
        } else {
            0
        }

        viewBinder.updateSummary(
            totalMessages = totalMessages,
            autoCategorized = autoCategorized,
            uniqueMerchants = uniqueMerchants,
            uniqueBanks = uniqueBanks,
            averageConfidence = averageConfidence
        )
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
                
                logger.debug("MessagesFragment", "Starting SMS resync...")
                
                // Clear current adapter to show fresh data
                groupedMessagesAdapter.submitList(emptyList())

                // Use injected SMS reader and scan
                val historicalTransactions = smsHistoryReader.scanHistoricalSMS()
                
                logger.debug("MessagesFragment", "SMS resync found ${historicalTransactions.size} transactions")
                
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
                    viewBinder.showContent()
                    
                    // Update UI stats (initially all groups are included)
                    updateExpenseCalculations()
                    
                    progressDialog.dismiss()
                    
                    Toast.makeText(
                        requireContext(),
                        "Found ${historicalTransactions.size} transaction messages",
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
                logger.warnWithThrowable("MessagesFragment", "SMS permission denied during resync", e)
                Toast.makeText(
                    requireContext(),
                    "SMS permission is required to sync transaction messages",
                    Toast.LENGTH_LONG
                ).show()
                viewBinder.showPermissionState()
            } catch (e: Exception) {
                progressDialog.dismiss()
                logger.error("MessagesFragment", "Error during SMS resync", e)
                Toast.makeText(
                    requireContext(),
                    "Unable to sync SMS messages: ${e.message ?: "Please try again"}",
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
                logger.debug("MessagesFragment", "Starting SMS scanning test...")
                val transactions = smsHistoryReader.scanHistoricalSMS()
                
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
                            appendLine("Top merchants:")
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
                    .setTitle("SMS Test Results")
                    .setMessage(message)
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                
                logger.debug("MessagesFragment", "SMS Test: Found ${transactions.size} transactions")
                
            } catch (e: SecurityException) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("SMS Permission Required")
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
                    .setTitle("Test Failed")
                    .setMessage("Error testing SMS scanning: ${e.message}")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                logger.error("MessagesFragment", "SMS test error", e)
            }
        }
    }
    
    private fun showDateFilterDialog() {
        val options = arrayOf("Today", "Yesterday", "This Week", "This Month", "Last Month", "All Time")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Date")
            .setItems(options) { _, which ->
                Toast.makeText(requireContext(), "Filtering by: ${options[which]}", Toast.LENGTH_SHORT).show()
                applyDateFilter(which)
            }
            .show()
    }
    
    private fun applyDateFilter(filterOption: Int) {
        val calendar = java.util.Calendar.getInstance()
        val startDate: java.util.Date
        val endDate: java.util.Date
        
        when (filterOption) {
            0 -> { // Today
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                startDate = calendar.time
                
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
                calendar.set(java.util.Calendar.MINUTE, 59)
                calendar.set(java.util.Calendar.SECOND, 59)
                endDate = calendar.time
            }
            1 -> { // Yesterday
                calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                startDate = calendar.time
                
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
                calendar.set(java.util.Calendar.MINUTE, 59)
                calendar.set(java.util.Calendar.SECOND, 59)
                endDate = calendar.time
            }
            2 -> { // This Week
                calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                startDate = calendar.time
                
                calendar.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
                calendar.set(java.util.Calendar.MINUTE, 59)
                calendar.set(java.util.Calendar.SECOND, 59)
                endDate = calendar.time
            }
            3 -> { // This Month
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                startDate = calendar.time
                
                calendar.add(java.util.Calendar.MONTH, 1)
                calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
                calendar.set(java.util.Calendar.MINUTE, 59)
                calendar.set(java.util.Calendar.SECOND, 59)
                endDate = calendar.time
            }
            4 -> { // Last Month
                calendar.add(java.util.Calendar.MONTH, -1)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                startDate = calendar.time
                
                calendar.add(java.util.Calendar.MONTH, 1)
                calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
                calendar.set(java.util.Calendar.MINUTE, 59)
                calendar.set(java.util.Calendar.SECOND, 59)
                endDate = calendar.time
            }
            5 -> { // All Time
                calendar.set(2020, java.util.Calendar.JANUARY, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                startDate = calendar.time
                
                val endCalendar = java.util.Calendar.getInstance()
                endCalendar.add(java.util.Calendar.YEAR, 1)
                endDate = endCalendar.time
            }
            else -> return
        }
        
        val filterNames = arrayOf("Today", "Yesterday", "This Week", "This Month", "Last Month", "All Time")
        logger.debug("MessagesFragment", "[DATE_FILTER] Applying filter: ${filterNames[filterOption]} from ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(startDate)} to ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(endDate)}")
        
        // Reload transactions with new date range
        loadTransactionsWithDateRange(startDate, endDate)
    }
    
    private fun loadTransactionsWithDateRange(startDate: java.util.Date, endDate: java.util.Date) {
        showLoadingState()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
                // FIX: Use getExpenseTransactionsByDateRange to filter out credits/mandates (is_debit = 1)
                val dbTransactions = repository.getExpenseTransactionsByDateRange(startDate, endDate)
                logger.debug("MessagesFragment", "[DATA] Found ${dbTransactions.size} expense transactions for date range (filtered is_debit = 1)")
                
                if (dbTransactions.isNotEmpty()) {
                    processAndDisplayTransactions(dbTransactions, isPartial = false)
                } else {
                    withContext(Dispatchers.Main) {
                        groupedMessagesAdapter.submitList(emptyList())
                        viewBinder.showEmptyState()
                        updateSummaryStats(emptyList())
                    }
                }
            } catch (e: Exception) {
                logger.error("MessagesFragment", "Error loading transactions with date filter", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error filtering transactions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        // PERFORMANCE: Show loading state immediately
        showLoadingState()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                
                // Use repository to get transactions from database (faster and more reliable)
                val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
                
                // Get date range from the start of the current month to the current day
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1) // Start of the month
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startDate = calendar.time

                // Set end date to current time to exclude future transactions
                val endDate = java.util.Calendar.getInstance().time
                
                logger.debug("MessagesFragment", "[DATE_FILTER] Loading transactions for current month from ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(startDate)}")

                // PERFORMANCE: Load transactions with limit for faster initial display
                // FIX: Use getExpenseTransactionsByDateRange to filter out credits/mandates (is_debit = 1)
                val dbTransactions = repository.getExpenseTransactionsByDateRange(startDate, endDate)
                logger.debug("MessagesFragment", "[DATA] Found ${dbTransactions.size} expense transactions in database (filtered is_debit = 1)")
                
                // PERFORMANCE: Show partial results immediately if we have many transactions
                val shouldShowProgressively = dbTransactions.size > 50
                val initialBatchSize = if (shouldShowProgressively) 30 else dbTransactions.size
                
                if (dbTransactions.isNotEmpty()) {
                    if (shouldShowProgressively) {
                        // Show first batch immediately
                        val initialTransactions = dbTransactions.take(initialBatchSize)
                        processAndDisplayTransactions(initialTransactions, isPartial = true)
                        
                        // Process remaining transactions in background
                        val remainingTransactions = dbTransactions.drop(initialBatchSize)
                        processRemainingTransactions(remainingTransactions)
                    } else {
                        // Small dataset, process all at once
                        processAndDisplayTransactions(dbTransactions, isPartial = false)
                    }
                } else {
                    // No database data found - fallback to SMS scanning
                    logger.debug("MessagesFragment", "[SMS] No database transactions found, falling back to SMS scanning...")
                    loadFromSMSFallback()
                }
            } catch (e: SecurityException) {
                // Permission denied
                logger.warnWithThrowable("MessagesFragment", "SMS permission denied", e)
                Toast.makeText(
                    requireContext(),
                    "SMS permission is required to read transaction messages",
                    Toast.LENGTH_LONG
                ).show()
                viewBinder.showPermissionState()
            } catch (e: Exception) {
                // Other error loading historical data, show empty state
                logger.error("MessagesFragment", "Error loading SMS data", e)
                viewBinder.showEmptyState()
                groupedMessagesAdapter.submitList(emptyList())
                updateSummaryStats(emptyList())
                
                Toast.makeText(
                    requireContext(),
                    "Error reading SMS: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun loadFromSMSFallback() {
        lifecycleScope.launch {
            try {
                val historicalTransactions = smsHistoryReader.scanHistoricalSMS()
                
                logger.debug("MessagesFragment", "[SMS] SMS Fallback: Found ${historicalTransactions.size} transactions")
                
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
                    viewBinder.showContent()
                    
                    // Update stats with real data
                    updateExpenseCalculations()
                    
                } else {
                    // FIXED: Show empty state instead of falling back to old approach
                    logger.debug("MessagesFragment", "Database has no transactions, showing empty state")
                    showEmptyState()
                }
            } catch (e: Exception) {
                logger.error("MessagesFragment", "Error in SMS fallback", e)
                showEmptyState()
            }
        }
    }
    
    private fun showEmptyState() {
        viewBinder.showEmptyState()
        allMessageItems = emptyList()
        filteredMessageItems = emptyList()
        currentSearchQuery = ""
        currentFilterOptions = FilterOptions()
        logger.debug("MessagesFragment", "Showing empty state; datasets cleared")
    }
    
    private fun showCategoryEditDialog(messageItem: MessageItem) {
        lifecycleScope.launch {
            try {
                // Fetch categories from database to get custom emojis
                val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
                val categoryEntities = repository.getAllCategoriesSync()

                // Get categories from CategoryManager (includes legacy SharedPreferences categories)
                val categoryManagerCategories = categoryManager.getAllCategories()

                // Migrate any SharedPreferences categories that aren't in database yet
                val dbCategoryNames = categoryEntities.map { it.name }.toSet()
                val missingCategories = categoryManagerCategories.filter { it !in dbCategoryNames }

                if (missingCategories.isNotEmpty()) {
                    logger.debug("MessagesFragment", "[MIGRATION] Found ${missingCategories.size} categories in SharedPreferences not in DB: ${missingCategories.joinToString()}")
                    missingCategories.forEach { categoryName ->
                        try {
                            val categoryEntity = com.smartexpenseai.app.data.entities.CategoryEntity(
                                name = categoryName,
                                emoji = "📂", // Default emoji for migrated categories
                                color = getRandomCategoryColor(),
                                isSystem = false,
                                displayOrder = 999,
                                createdAt = java.util.Date()
                            )
                            repository.insertCategory(categoryEntity)
                            logger.debug("MessagesFragment", "[MIGRATION] Migrated category '$categoryName' to database")
                        } catch (e: Exception) {
                            logger.warn("MessagesFragment", "[MIGRATION] Failed to migrate category '$categoryName': ${e.message}")
                        }
                    }
                    // Reload categories from database after migration
                    val updatedCategoryEntities = repository.getAllCategoriesSync()
                    val categoryEmojiMap = updatedCategoryEntities.associate { it.name to it.emoji }

                    logger.debug("MessagesFragment", "[EMOJI DEBUG] After migration - Category entities from DB: ${updatedCategoryEntities.size}")
                } else {
                    logger.debug("MessagesFragment", "[MIGRATION] All categories already in database")
                }

                // Create a map of category names to emojis from database
                val categoryEmojiMap = repository.getAllCategoriesSync().associate { it.name to it.emoji }

                // Log for debugging
                logger.debug("MessagesFragment", "[EMOJI DEBUG] Category entities from DB: ${categoryEmojiMap.size}")
                categoryEmojiMap.forEach { (name, emoji) ->
                    logger.debug("MessagesFragment", "[EMOJI DEBUG] DB Category: name='$name', emoji='$emoji'")
                }

                // Get all category names (including legacy ones from CategoryManager)
                val allCategoryNames = (categoryEmojiMap.keys + categoryManagerCategories).distinct()
                val currentCategoryIndex = allCategoryNames.indexOf(messageItem.category)

                logger.debug("MessagesFragment", "[EMOJI DEBUG] All categories: ${allCategoryNames.joinToString()}")

                // Create enhanced categories with emojis (prefer database emoji over hardcoded)
                val enhancedCategories = allCategoryNames.map { category ->
                    val emoji = categoryEmojiMap[category] ?: getCategoryEmoji(category)
                    logger.debug("MessagesFragment", "[EMOJI DEBUG] Category '$category' -> emoji='$emoji' (from DB: ${categoryEmojiMap.containsKey(category)})")
                    "$emoji $category"
                }.toTypedArray()

                logger.debug("MessagesFragment", "[TARGET] Showing custom category dialog for ${messageItem.merchant}")

                val dialog = CategorySelectionDialogFragment.newInstance(
                    categories = enhancedCategories,
                    currentIndex = currentCategoryIndex,
                    merchantName = messageItem.merchant
                ) { selectedCategory ->
                    // Remove emoji from selected category to get the actual name
                    val actualCategory = parseCategoryName(selectedCategory)
                    if (actualCategory != messageItem.category) {
                        updateCategoryForMerchant(messageItem, actualCategory)
                    }
                }

                dialog.show(parentFragmentManager, "CategoryEditDialog")
            } catch (e: Exception) {
                logger.error("MessagesFragment", "Failed to show category dialog", e)
                // Fallback to old behavior if database access fails
                showCategoryEditDialogFallback(messageItem)
            }
        }
    }

    private fun showCategoryEditDialogFallback(messageItem: MessageItem) {
        lifecycleScope.launch {
            val categories = categoryManager.getAllCategories()
            val currentCategoryIndex = categories.indexOf(messageItem.category)

            // Create enhanced categories with emojis
            val enhancedCategories = categories.map { category ->
                "${getCategoryEmoji(category)} $category"
            }.toTypedArray()

            logger.debug("MessagesFragment", "[TARGET] Showing custom category dialog for ${messageItem.merchant} (fallback)")

            val dialog = CategorySelectionDialogFragment.newInstance(
                categories = enhancedCategories,
                currentIndex = currentCategoryIndex,
                merchantName = messageItem.merchant
            ) { selectedCategory ->
                // Remove emoji from selected category to get the actual name
                val actualCategory = parseCategoryName(selectedCategory)
                if (actualCategory != messageItem.category) {
                    updateCategoryForMerchant(messageItem, actualCategory)
                }
            }

            dialog.show(parentFragmentManager, "CategoryEditDialog")
        }
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

    private fun getRandomCategoryColor(): String {
        val colorList = listOf(
            "#795548", "#e91e63", "#9c27b0", "#673ab7", "#3f51b5",
            "#2196f3", "#03a9f4", "#00bcd4", "#009688", "#4caf50",
            "#8bc34a", "#cddc39", "#ffeb3b", "#ffc107", "#ff9800",
            "#ff5722", "#f44336"
        )
        return colorList.random()
    }

    /**
     * Extract clean category name from display text by removing emoji prefix
     * Example: "🛍️ Shopping" -> "Shopping"
     */
    private fun parseCategoryName(displayText: String): String {
        val firstLetterIndex = displayText.indexOfFirst { it.isLetter() }
        return if (firstLetterIndex > 0) {
            displayText.substring(firstLetterIndex).trim()
        } else {
            displayText.trim()
        }
    }
    
    private fun updateCategoryForMerchant(messageItem: MessageItem, newCategory: String) {
        lifecycleScope.launch {
            try {
                logger.debug("MessagesFragment", "Updating category for ${messageItem.merchant} to $newCategory")

                // Step 1: Update SharedPreferences (for runtime categorization)
                categoryManager.updateCategory(messageItem.merchant, newCategory)

                // Step 2: Update database (for persistent storage and queries)
                val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
                val normalizedName = merchantAliasManager.normalizeMerchantName(messageItem.rawMerchant)

                logger.debug("MessagesFragment", "Updating database for normalized merchant: $normalizedName")

                val databaseUpdateSuccess = repository.updateMerchantAliasInDatabase(
                    listOf(messageItem.rawMerchant),
                    messageItem.merchant, // Keep existing display name
                    newCategory
                )

                if (!databaseUpdateSuccess) {
                    logger.error("MessagesFragment", "Database update failed for category change")
                    throw Exception("Database update failed")
                }

                logger.info("MessagesFragment", "Successfully updated merchant category in database")

                // Invalidate ViewModel's cache to ensure it gets fresh alias data
                try {
                    messagesViewModel.invalidateMerchantAliasCache()
                } catch (e: Exception) {
                    logger.warnWithThrowable("MessagesFragment", "Could not invalidate ViewModel cache for category update", e)
                }

                // Trigger ViewModel update to reflect category changes
                try {
                    messagesViewModel.handleEvent(MessagesUIEvent.LoadMessages)
                } catch (e: Exception) {
                    logger.warnWithThrowable("MessagesFragment", "Could not trigger ViewModel refresh for category update", e)
                }

                notifyCategoryUpdate(messageItem, newCategory)

                // Show feedback
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Category Updated!")
                    .setMessage("Updated category for ${messageItem.merchant} to '$newCategory'.")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()

            } catch (e: Exception) {
                logger.error("MessagesFragment", "Failed to update category", e)
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

    private suspend fun notifyCategoryUpdate(messageItem: MessageItem, newCategory: String) {
        try {
            delay(CATEGORY_BROADCAST_DELAY_MS)

            val categoryIntent = Intent("com.expensemanager.CATEGORY_UPDATED").apply {
                putExtra("merchant", messageItem.merchant)
                putExtra("category", newCategory)
            }
            requireContext().sendBroadcast(categoryIntent)

            val merchantIntent = Intent("com.expensemanager.MERCHANT_CATEGORY_CHANGED").apply {
                putExtra("merchant_name", messageItem.rawMerchant)
                putExtra("display_name", messageItem.merchant)
                putExtra("new_category", newCategory)
            }
            requireContext().sendBroadcast(merchantIntent)

            logger.debug(
                "MessagesFragment",
                "[BROADCAST] Sent category update for ${messageItem.merchant} -> $newCategory"
            )

        } catch (e: Exception) {
            logger.warnWithThrowable(
                "MessagesFragment",
                "Failed to dispatch category update broadcast for ${messageItem.merchant}",
                e
            )
        }
    }
    
    private fun formatDate(date: java.util.Date): String {
        // PERFORMANCE: Cache formatted dates to avoid repeated calculations
        return dateFormattingCache.getOrPut(date.time) {
            val now = java.util.Date()
            val diffInMs = now.time - date.time
            val diffInDays = diffInMs / (1000 * 60 * 60 * 24)
            val diffInHours = diffInMs / (1000 * 60 * 60)
            
            when {
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
        // PERFORMANCE: Use cache for date parsing to avoid repeated calculations
        return dateParsingCache.getOrPut(dateTimeString) {
            when {
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
                        return calendar.timeInMillis
                    }
                } catch (e: Exception) {
                    // Try next format
                    continue
                }
            }
            
            // Fallback to a reasonable old timestamp instead of 0L
            return System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000L) // 1 year ago
            
        } catch (e: Exception) {
            return System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000L) // 1 year ago
        }
    }
    
    private fun groupTransactionsByMerchant(transactions: List<MessageItem>): List<MerchantGroup> {
        
        // Group by the display name (which is already set from the alias manager)
        val groups = transactions
            .groupBy { it.merchant } // merchant field already contains the display name
            .map { (displayName, merchantTransactions) ->
                // PERFORMANCE: Calculate values in a single pass
                var totalAmount = 0.0
                var totalConfidence = 0
                val bankCounts = mutableMapOf<String, Int>()
                var latestTimestamp = 0L
                
                // Single pass calculation
                for (transaction in merchantTransactions) {
                    totalAmount += transaction.amount
                    totalConfidence += transaction.confidence
                    bankCounts[transaction.bankName] = bankCounts.getOrDefault(transaction.bankName, 0) + 1
                    val timestamp = getDateSortOrderReverse(transaction.dateTime)
                    if (timestamp > latestTimestamp) latestTimestamp = timestamp
                }
                
                // Sort transactions only once
                val sortedTransactions = merchantTransactions.sortedByDescending { transaction ->
                    getDateSortOrderReverse(transaction.dateTime)
                }
                
                val category = merchantTransactions.firstOrNull()?.category ?: "Other"
                val categoryColor = merchantTransactions.firstOrNull()?.categoryColor ?: "#9e9e9e"
                val primaryBankName = bankCounts.maxByOrNull { it.value }?.key ?: ""
                val averageConfidence = if (merchantTransactions.isNotEmpty()) {
                    totalConfidence.toDouble() / merchantTransactions.size
                } else {
                    0.0
                }
                val latestTransactionDate = latestTimestamp
                
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
        
        
        // Enhanced debug logging for group sorting results
        if (sortedGroups.isNotEmpty()) {
            sortedGroups.take(3).forEach { group ->
                when (currentSortOption.field) {
                    "amount" -> logger.debug("MessagesFragment", "  - ${group.merchantName}: ₹${String.format("%.2f", group.totalAmount)} (${group.transactions.size} transactions)")
                    "merchant" -> logger.debug("MessagesFragment", "  - ${group.merchantName}: ${group.transactions.size} transactions")
                    "bank" -> logger.debug("MessagesFragment", "  - ${group.merchantName}: ${group.primaryBankName} (${group.transactions.size} transactions)")
                    "confidence" -> logger.debug("MessagesFragment", "  - ${group.merchantName}: ${String.format("%.1f", group.averageConfidence)}% confidence")
                    "date" -> {
                        val dateStr = group.transactions.firstOrNull()?.dateTime ?: "N/A"
                        logger.debug("MessagesFragment", "  - ${group.merchantName}: $dateStr (${group.transactions.size} transactions)")
                    }
                    else -> logger.debug("MessagesFragment", "  - ${group.merchantName}: ${group.transactions.size} transactions")
                }
            }
        }
        
        // Load saved inclusion states
        return loadGroupInclusionStates(sortedGroups)
    }
    
    
    private fun updateExpenseCalculations() {
        try {
            logger.debug("MessagesFragment", "Updating expense calculations...")

            val currentGroups = groupedMessagesAdapter.merchantGroups
            val includedGroups = currentGroups.filter { it.isIncludedInCalculations }
            val totalMessages = includedGroups.sumOf { it.transactions.size }
            val autoCategorized = includedGroups.sumOf { group ->
                group.transactions.count { it.confidence >= 85 }
            }
            val uniqueMerchants = includedGroups.size
            val uniqueBanks = includedGroups.flatMap { group -> group.transactions.map { it.bankName } }
                .toSet().size
            val averageConfidence = if (includedGroups.isNotEmpty()) {
                includedGroups.flatMap { it.transactions }.map { it.confidence }.average().toInt()
            } else 0

            viewBinder.updateSummary(
                totalMessages = totalMessages,
                autoCategorized = autoCategorized,
                uniqueMerchants = uniqueMerchants,
                uniqueBanks = uniqueBanks,
                averageConfidence = averageConfidence
            )
            
            // Store the inclusion state in SharedPreferences for other screens to use
            saveGroupInclusionStates(currentGroups)
            
            // CRITICAL FIX: Notify other screens about data changes using correct broadcast action
            val intent = Intent("com.smartexpenseai.app.DATA_CHANGED")
            intent.putExtra("included_count", totalMessages)
            intent.putExtra("total_amount", includedGroups.sumOf { it.totalAmount })
            intent.putExtra("source", "messages_exclusion_change")
            requireContext().sendBroadcast(intent)
            
            logger.debug("MessagesFragment", "[DATA_SYNC] Broadcast sent to refresh Dashboard after exclusion change")
            
            // Log for debugging
            val totalIncludedAmount = includedGroups.sumOf { it.totalAmount }
            logger.debug("MessagesFragment", "Successfully updated calculations: $totalMessages messages, ₹${String.format("%.0f", totalIncludedAmount)} total from included groups")
            
        } catch (e: Exception) {
            logger.error("MessagesFragment", "Error updating expense calculations", e)
            Toast.makeText(
                requireContext(),
                "Error updating calculations. Some data may not be saved.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun saveGroupInclusionStates(groups: List<MerchantGroup>) {
        try {
            logger.debug("MessagesFragment", "Saving inclusion states for ${groups.size} groups")
            
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
                    logger.warnWithThrowable("MessagesFragment", "Failed to save inclusion state for '${group.merchantName}'", e)
                }
            }
            
            editor.putString("group_inclusion_states", inclusionStates.toString())
            val success = editor.commit()
            
            if (success) {
                logger.debug("MessagesFragment", "Successfully saved $savedCount inclusion states")
            } else {
                logger.error("MessagesFragment", "Failed to commit inclusion states to SharedPreferences")
            }
            
        } catch (e: Exception) {
            logger.error("MessagesFragment", "Error saving group inclusion states", e)
            // Don't show error to user for this - it's not critical for immediate functionality
        }
    }
    
    private fun loadGroupInclusionStates(groups: List<MerchantGroup>): List<MerchantGroup> {
        return try {
            logger.debug("MessagesFragment", "Loading inclusion states for ${groups.size} groups")
            
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
                            logger.warnWithThrowable("MessagesFragment", "Error loading inclusion state for '${group.merchantName}'", e)
                            true // Default to included on error
                        }
                        group.copy(isIncludedInCalculations = isIncluded)
                    }
                    
                    logger.debug("MessagesFragment", "Successfully loaded $loadedCount inclusion states")
                    return updatedGroups
                    
                } catch (e: Exception) {
                    logger.warnWithThrowable("MessagesFragment", "Error parsing inclusion states JSON", e)
                }
            } else {
                logger.debug("MessagesFragment", "No inclusion states found, using defaults")
            }
            
            // Return original groups with default inclusion states
            groups
            
        } catch (e: Exception) {
            logger.error("MessagesFragment", "Critical error loading inclusion states", e)
            // Return original groups as fallback
            groups
        }
    }
    
    private fun showCategorySelectionDialog(categories: List<String>, currentSelection: String, onCategorySelected: (String) -> Unit) {
        lifecycleScope.launch {
            try {
                // Fetch categories from database to get custom emojis
                val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
                val categoryEntities = repository.getAllCategoriesSync()

                // Create emoji map from database
                val categoryEmojiMap = categoryEntities.associate { it.name to it.emoji }

                logger.debug("MessagesFragment", "[EMOJI] Loaded ${categoryEmojiMap.size} categories from DB for selection dialog")

                val currentIndex = categories.indexOf(currentSelection).takeIf { it >= 0 } ?: 0

                // Create enhanced categories with emojis (prefer database emoji over hardcoded)
                val enhancedCategories = categories.map { category ->
                    val emoji = categoryEmojiMap[category] ?: getCategoryEmoji(category)
                    logger.debug("MessagesFragment", "[EMOJI] Category '$category' -> emoji='$emoji' (from DB: ${categoryEmojiMap.containsKey(category)})")
                    "$emoji $category"
                }.toTypedArray()

                logger.debug("MessagesFragment", "[TARGET] Showing custom category selection dialog, current: $currentSelection")

                val dialog = CategorySelectionDialogFragment.newInstance(
                    categories = enhancedCategories,
                    currentIndex = currentIndex,
                    merchantName = "merchant group"
                ) { selectedCategory ->
                    // Remove emoji from selected category to get the actual name
                    val actualCategory = parseCategoryName(selectedCategory)
                    onCategorySelected(actualCategory)
                }

                dialog.show(parentFragmentManager, "CategorySelectionDialog")
            } catch (e: Exception) {
                logger.error("MessagesFragment", "Failed to load category emojis", e)
                // Fallback to hardcoded emojis
                val currentIndex = categories.indexOf(currentSelection).takeIf { it >= 0 } ?: 0
                val enhancedCategories = categories.map { category ->
                    "${getCategoryEmoji(category)} $category"
                }.toTypedArray()

                val dialog = CategorySelectionDialogFragment.newInstance(
                    categories = enhancedCategories,
                    currentIndex = currentIndex,
                    merchantName = "merchant group"
                ) { selectedCategory ->
                    val actualCategory = parseCategoryName(selectedCategory)
                    onCategorySelected(actualCategory)
                }
                dialog.show(parentFragmentManager, "CategorySelectionDialog")
            }
        }
    }
    
    private fun showMerchantGroupEditDialog(group: MerchantGroup) {
        logger.debug(
            "showMerchantGroupEditDialog",
            "Editing merchant '${group.merchantName}' in category '${group.category}' with ${group.transactions.size} transactions"
        )
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
        lifecycleScope.launch {
            val categories = categoryManager.getAllCategories().toMutableList()

            // Ensure "Money" category is available
            if (!categories.contains("Money")) {
                categoryManager.addCustomCategory("Money")
                categories.add("Money")
            }

            logger.debug("MessagesFragment", "Loading categories for selector: $categories")

            // Set current category
            val initialCategory = if (categories.contains(group.category)) group.category else categories.firstOrNull() ?: "Other"
            tvSelectedCategory.text = initialCategory

            logger.debug("MessagesFragment", "Set initial category to: $initialCategory")

            // Handle category selection
            categorySelectorCard.setOnClickListener {
                logger.debug("MessagesFragment", "Category selector clicked, showing dialog")
                showCategorySelectionDialog(categories, tvSelectedCategory.text.toString()) { newCategory ->
                    tvSelectedCategory.text = newCategory
                    logger.debug("MessagesFragment", "Category updated to: $newCategory")
                }
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
                
                logger.debug("MessagesFragment", "Save button clicked - Group: '$newGroupName', Category: '$newCategory'")
                logger.debug("MessagesFragment", "Original values - Group: '${group.merchantName}', Category: '${group.category}'")
                
                // Check if anything actually changed
                val groupNameChanged = newGroupName != group.merchantName
                val categoryChanged = newCategory != group.category
                logger.debug("MessagesFragment", "Changes detected - Group name changed: $groupNameChanged, Category changed: $categoryChanged")
                
                if (newGroupName.isNotEmpty() && newCategory.isNotEmpty()) {
                    if (groupNameChanged || categoryChanged) {
                        updateMerchantGroup(group, newGroupName, newCategory)
                    } else {
                        logger.debug("MessagesFragment", "No changes detected, skipping update")
                        Toast.makeText(
                            requireContext(),
                            "ℹ️ No changes made",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    logger.warn("MessagesFragment", "Empty fields detected - Group: '$newGroupName', Category: '$newCategory'")
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
                
                // Enhanced validation
                if (newDisplayName.trim().isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter a display name", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                if (newCategory.trim().isEmpty()) {
                    Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Enhanced input validation before conflict check
                if (newDisplayName.isBlank()) {
                    logger.warn("MessagesFragment", "[VALIDATION] Empty display name provided")
                    Toast.makeText(requireContext(), "⚠️ Please enter a valid merchant name", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                if (newDisplayName.length < 2) {
                    logger.warn("MessagesFragment", "[VALIDATION] Display name too short: '$newDisplayName'")
                    Toast.makeText(requireContext(), "⚠️ Merchant name must be at least 2 characters", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                if (newDisplayName.length > 50) {
                    logger.warn("MessagesFragment", "[VALIDATION] Display name too long: ${newDisplayName.length} characters")
                    Toast.makeText(requireContext(), "⚠️ Merchant name too long (max 50 characters)", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Check for conflicts before proceeding
                val conflict = merchantAliasManager.checkAliasConflict(group.merchantName, newDisplayName, newCategory)
                logger.debug("MessagesFragment", "[CONFLICT] Conflict check result: ${conflict.type} for '$newDisplayName' -> '$newCategory'")
                
                when (conflict.type) {
                    MerchantAliasManager.ConflictType.CATEGORY_MISMATCH -> {
                        // Show conflict resolution dialog
                        logger.debug("MessagesFragment", "[CONFLICT] Category mismatch detected - showing resolution dialog")
                        showAliasConflictDialog(group, newDisplayName, newCategory, conflict)
                        return@launch
                    }
                    MerchantAliasManager.ConflictType.OVERWRITE_EXISTING -> {
                        // Ask user if they want to overwrite existing alias
                        logger.debug("MessagesFragment", "[CONFLICT] Overwrite existing detected - showing confirmation dialog")
                        showOverwriteConfirmationDialog(group, newDisplayName, newCategory, conflict)
                        return@launch
                    }
                    MerchantAliasManager.ConflictType.DISPLAY_NAME_EXISTS -> {
                        // Display name exists but should be treated as grouping (handled in conflict checking logic)
                        logger.debug("MessagesFragment", "[CONFLICT] Display name exists but no conflict - proceeding with grouping")
                        Toast.makeText(requireContext(), "✅ Grouping with existing '$newDisplayName' merchants", Toast.LENGTH_SHORT).show()
                    }
                    MerchantAliasManager.ConflictType.NONE -> {
                        // No conflict, proceed normally
                        logger.debug("MessagesFragment", "[CONFLICT] No conflicts detected, proceeding with alias update")
                    }
                }
                
                // Find all transactions in this group to get their original merchant names
                val originalMerchantNames = mutableSetOf<String>()

                // FIXED: Always use rawMerchant from actual transactions (not normalized names from SharedPreferences)
                // This ensures database UPDATE uses the correct merchant names that match what's in the database
                logger.debug("MessagesFragment", "[FIX] Extracting original merchant names from ${group.transactions.size} transactions")

                group.transactions.forEach { transaction ->
                    if (transaction.rawMerchant.isNotBlank()) {
                        originalMerchantNames.add(transaction.rawMerchant)
                        logger.debug("MessagesFragment", "[FIX] Added rawMerchant: ${transaction.rawMerchant}")
                    }
                }

                // Fallback: If rawMerchant was blank for all transactions, use display name
                if (originalMerchantNames.isEmpty()) {
                    originalMerchantNames.add(group.merchantName)
                    logger.warn("MessagesFragment", "[FIX] No rawMerchant found, using display name: ${group.merchantName}")
                }
                
                logger.debug("MessagesFragment", "Final original merchant names to update: $originalMerchantNames")

                // Parse clean category name without emoji
                val cleanCategoryName = parseCategoryName(newCategory)
                logger.debug("MessagesFragment", "Parsed category: '$newCategory' -> '$cleanCategoryName'")

                // Validate category exists - don't create new categories during merchant update
                val allCategories = categoryManager.getAllCategories()
                logger.debug("MessagesFragment", "Available categories: $allCategories")

                if (!allCategories.contains(cleanCategoryName)) {
                    logger.warn("MessagesFragment", "Category '$cleanCategoryName' not found in existing categories")
                    Toast.makeText(
                        requireContext(),
                        "Category '$cleanCategoryName' not found. Please select from existing categories.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                // STEP 1: Update SharedPreferences (MerchantAliasManager)
                logger.debug("MessagesFragment", "Step 1: Updating SharedPreferences aliases...")
                var aliasUpdateSuccess = true
                var aliasUpdateError: String? = null
                
                var aliasSetCount = 0
                var aliasFailCount = 0
                
                try {
                    originalMerchantNames.forEachIndexed { index, originalName ->
                        logger.debug("MessagesFragment", "Setting alias ${index + 1}/${originalMerchantNames.size}: $originalName -> $newDisplayName ($cleanCategoryName)")

                        val aliasSetSuccess = merchantAliasManager.setMerchantAlias(originalName, newDisplayName, cleanCategoryName)

                        if (aliasSetSuccess) {
                            aliasSetCount++

                            // Verify the alias was set correctly
                            val verifyDisplayName = merchantAliasManager.getDisplayName(originalName)
                            val verifyCategory = merchantAliasManager.getMerchantCategory(originalName)

                            if (verifyDisplayName != newDisplayName || verifyCategory != cleanCategoryName) {
                                logger.warn("MessagesFragment", "Alias verification failed for $originalName")
                            }
                        } else {
                            aliasFailCount++
                            logger.error("MessagesFragment", "Failed to set alias: $originalName")
                        }
                    }
                    
                    logger.debug("MessagesFragment", "[ANALYTICS] SharedPreferences update summary: ${aliasSetCount} success, ${aliasFailCount} failed out of ${originalMerchantNames.size}")
                    
                    // Consider successful if majority of aliases were set
                    if (aliasSetCount > 0 && aliasSetCount >= aliasFailCount) {
                    } else {
                        logger.error("MessagesFragment", "SharedPreferences update failed")
                        aliasUpdateSuccess = false
                        aliasUpdateError = "$aliasFailCount out of ${originalMerchantNames.size} aliases failed to set"
                    }
                    
                } catch (e: Exception) {
                    logger.error("MessagesFragment", "SharedPreferences update failed", e)
                    aliasUpdateSuccess = false
                    aliasUpdateError = e.message
                }
                
                // STEP 2: Update Database (ExpenseRepository)
                logger.debug("MessagesFragment", "[DATABASE] Step 2: Updating database...")
                var databaseUpdateSuccess = false
                var databaseUpdateError: String? = null
                
                if (aliasUpdateSuccess) {
                    try {
                        val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
                        logger.debug("MessagesFragment", "[DATABASE] Calling repository.updateMerchantAliasInDatabase with ${originalMerchantNames.size} merchants")
                        
                        databaseUpdateSuccess = repository.updateMerchantAliasInDatabase(
                            originalMerchantNames.toList(),
                            newDisplayName,
                            cleanCategoryName
                        )
                        
                        if (databaseUpdateSuccess) {
                            
                            // Verify database changes
                            originalMerchantNames.take(3).forEach { originalName ->
                                try {
                                    // Use MerchantAliasManager's normalizeMerchantName since repository's is private
                                    val normalizedName = merchantAliasManager.normalizeMerchantName(originalName)
                                    val merchantWithCategory = repository.getMerchantWithCategory(normalizedName)
                                } catch (e: Exception) {
                                    logger.warnWithThrowable("MessagesFragment", "Could not verify database changes for $originalName", e)
                                }
                            }
                        } else {
                            logger.error("MessagesFragment", "Database update failed")
                            databaseUpdateError = "Database update returned false"
                        }
                    } catch (e: Exception) {
                        logger.error("MessagesFragment", "Database update failed", e)
                        databaseUpdateSuccess = false
                        databaseUpdateError = e.message
                    }
                } else {
                    logger.warn("MessagesFragment", "Skipping database update due to SharedPreferences failure")
                    databaseUpdateError = "SharedPreferences update failed first"
                }
                
                // STEP 3: Enhanced result handling and user feedback
                when {
                    aliasUpdateSuccess && databaseUpdateSuccess -> {
                        
                        // Invalidate ViewModel's cache to ensure it gets fresh alias data
                        try {
                            logger.debug("MessagesFragment", "[CACHE_SYNC] Invalidating ViewModel cache before refresh")
                            messagesViewModel.invalidateMerchantAliasCache()
                        } catch (e: Exception) {
                            logger.warnWithThrowable("MessagesFragment", "Could not invalidate ViewModel cache", e)
                        }
                        
                        // Trigger ViewModel update to reflect changes (respecting date filtering)
                        try {
                            logger.debug("MessagesFragment", "[UI_FIX] Using ONLY ViewModel refresh to show updated merchant names immediately")
                            messagesViewModel.handleEvent(MessagesUIEvent.LoadMessages)
                        } catch (e: Exception) {
                            logger.warnWithThrowable("MessagesFragment", "Could not trigger ViewModel date-aware refresh", e)
                        }
                        
                        // Enhanced success feedback
                        val successMessage = if (aliasSetCount > 1) {
                            "✅ Successfully grouped $aliasSetCount merchants as '$newDisplayName' in '$newCategory'"
                        } else {
                            "✅ Successfully updated '${group.merchantName}' to '$newDisplayName' in '$newCategory'"
                        }
                        
                        Toast.makeText(
                            requireContext(),
                            successMessage,
                            Toast.LENGTH_LONG
                        ).show()
                        
                        logger.debug("MessagesFragment", "[SUCCESS] Merchant alias update completed successfully: ${group.merchantName} -> $newDisplayName ($newCategory)")
                        
                        // Notify Dashboard and other screens about merchant category change
                        val intent = Intent("com.expensemanager.MERCHANT_CATEGORY_CHANGED")
                        intent.putExtra("merchant_name", group.merchantName)
                        intent.putExtra("display_name", newDisplayName)
                        intent.putExtra("new_category", newCategory)
                        requireContext().sendBroadcast(intent)
                        logger.debug("MessagesFragment", "[BROADCAST] Sent merchant category change broadcast for '${group.merchantName}' -> '$newCategory'")
                    }
                    
                    aliasUpdateSuccess && !databaseUpdateSuccess -> {
                        logger.warn("MessagesFragment", "SharedPreferences updated but database failed")
                        
                        // Invalidate ViewModel's cache to ensure it gets fresh alias data
                        try {
                            logger.debug("MessagesFragment", "[CACHE_SYNC] Invalidating ViewModel cache before partial success refresh")
                            messagesViewModel.invalidateMerchantAliasCache()
                        } catch (e: Exception) {
                            logger.warnWithThrowable("MessagesFragment", "Could not invalidate ViewModel cache for partial success", e)
                        }
                        
                        // Trigger ViewModel update to show UI changes (no Fragment data loading)
                        try {
                            logger.debug("MessagesFragment", "[UI_FIX] Using ONLY ViewModel refresh for partial success case")
                            messagesViewModel.handleEvent(MessagesUIEvent.LoadMessages)
                        } catch (e: Exception) {
                            logger.warnWithThrowable("MessagesFragment", "Could not trigger ViewModel refresh for partial success", e)
                        }
                        
                        Toast.makeText(
                            requireContext(),
                            "Changes saved to app memory but database update failed. Changes may not persist after app restart.\n\nError: ${databaseUpdateError ?: "Unknown database error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    !aliasUpdateSuccess -> {
                        logger.error("MessagesFragment", "SharedPreferences update failed")
                        
                        Toast.makeText(
                            requireContext(),
                            "Unable to save changes to app memory.\n\nError: ${aliasUpdateError ?: "Unknown error"}\n\nPlease try again or restart the app.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
                logger.debug("MessagesFragment", "Merchant group update process completed - Success: Alias=$aliasUpdateSuccess, DB=$databaseUpdateSuccess")
                
            } catch (e: Exception) {
                logger.error("MessagesFragment", "Critical error during merchant group update", e)
                Toast.makeText(
                    requireContext(),
                    "Critical error updating group: ${e.message ?: "Unknown error"}\n\nPlease restart the app and try again.",
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
                    logger.debug("MessagesFragment", "[TARGET] Extracted merchant: '$cleaned' from SMS using pattern: ${pattern.pattern.take(20)}...")
                    return cleaned
                }
            }
        }
        
        logger.debug("MessagesFragment", "No merchant extracted from SMS: ${rawSMS.take(100)}...")
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
            logger.warnWithThrowable("MessagesFragment", "Error in alternative merchant name extraction", e)
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
                
                // Invalidate ViewModel's cache to ensure it gets fresh alias data
                try {
                    logger.debug("MessagesFragment", "[CACHE_SYNC] Invalidating ViewModel cache for reset operation")
                    messagesViewModel.invalidateMerchantAliasCache()
                } catch (e: Exception) {
                    logger.warnWithThrowable("MessagesFragment", "Could not invalidate ViewModel cache for reset", e)
                }
                
                // Trigger ViewModel update to reflect changes
                try {
                    logger.debug("MessagesFragment", "[UI_FIX] Using ONLY ViewModel refresh for merchant reset operation")
                    messagesViewModel.handleEvent(MessagesUIEvent.LoadMessages)
                } catch (e: Exception) {
                    logger.warnWithThrowable("MessagesFragment", "Could not trigger ViewModel refresh for reset", e)
                }
                
                Toast.makeText(
                    requireContext(),
                    "Reset group to original names and categories",
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e: Exception) {
                logger.error("MessagesFragment", "Error resetting merchant group", e)
                Toast.makeText(
                    requireContext(),
                    "Unable to reset group: ${e.message ?: "Please try again"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun hasActiveFilters(): Boolean {
        return currentFilterOptions.minAmount != null ||
               currentFilterOptions.maxAmount != null ||
               currentFilterOptions.selectedBanks.isNotEmpty() ||
               currentFilterOptions.dateFrom != null ||
               currentFilterOptions.dateTo != null ||
               currentSearchQuery.isNotBlank()
    }
    
    private fun navigateToTransactionDetails(messageItem: MessageItem) {
        val bundle = Bundle().apply {
            putFloat("amount", messageItem.amount.toFloat())
            putString("merchant", messageItem.merchant)
            putString("bankName", messageItem.bankName)
            putString("category", messageItem.category)
            putLong("transactionDate", messageItem.actualDate.time)
            putInt("confidence", messageItem.confidence)
            putString("rawSMS", messageItem.rawSMS)
        }
        
        findNavController().navigate(
            R.id.action_navigation_messages_to_transaction_details,
            bundle
        )
    }
    
    private fun showLoadingState() {
        viewBinder.showLoadingState()
    }
    
    private suspend fun processAndDisplayTransactions(transactions: List<com.smartexpenseai.app.data.entities.TransactionEntity>, isPartial: Boolean) {
        val messageItems = convertTransactionsToMessageItems(transactions)
        
        withContext(Dispatchers.Main) {
            // Store for filtering/sorting
            if (isPartial) {
                allMessageItems = messageItems // Will be updated later with full dataset
                filteredMessageItems = messageItems
            } else {
                allMessageItems = messageItems
                filteredMessageItems = messageItems
            }
            
            // Apply initial sorting and filtering
            viewBinder.showContent()
            
            // Update stats with current data
            updateExpenseCalculations()
        }
    }
    
    private suspend fun processRemainingTransactions(remainingTransactions: List<com.smartexpenseai.app.data.entities.TransactionEntity>) {
        if (remainingTransactions.isEmpty()) return
        
        // Process remaining transactions in chunks to avoid blocking
        val chunkSize = 20
        val chunks = remainingTransactions.chunked(chunkSize)
        
        for (chunk in chunks) {
            val chunkMessageItems = convertTransactionsToMessageItems(chunk)
            
            withContext(Dispatchers.Main) {
                // Append to existing data
                allMessageItems = allMessageItems + chunkMessageItems
                filteredMessageItems = allMessageItems // Will be re-filtered
                
                // Update display with growing dataset
                updateExpenseCalculations()
            }
            
            // Small delay to allow UI updates
            kotlinx.coroutines.delay(50)
        }
    }
    
    private suspend fun convertTransactionsToMessageItems(transactions: List<com.smartexpenseai.app.data.entities.TransactionEntity>): List<MessageItem> {
        // PERFORMANCE: Pre-load all merchant data in batch (existing optimization)
        val uniqueRawMerchants = transactions.map { it.rawMerchant }.distinct()
        
        val merchantCategoryMap = mutableMapOf<String, Triple<String, String, String>>()
        
        // Batch load alias data
        uniqueRawMerchants.forEach { rawMerchant ->
            val displayName = merchantAliasManager.getDisplayName(rawMerchant)
            val category = merchantAliasManager.getMerchantCategory(rawMerchant)
            val categoryColor = merchantAliasManager.getMerchantCategoryColor(rawMerchant)
            merchantCategoryMap[rawMerchant] = Triple(displayName, category, categoryColor)
        }
        
        // Convert to MessageItem format with cached data
        return transactions.mapNotNull { transaction ->
            try {
                val (displayName, aliasCategory, aliasCategoryColor) = merchantCategoryMap[transaction.rawMerchant]
                    ?: Triple(transaction.rawMerchant, "Other", "#888888")
                
                MessageItem(
                    amount = transaction.amount,
                    merchant = displayName,
                    bankName = transaction.bankName,
                    category = aliasCategory,
                    categoryColor = aliasCategoryColor,
                    confidence = (transaction.confidenceScore * 100).toInt(),
                    dateTime = formatDate(transaction.transactionDate),
                    rawSMS = transaction.rawSmsBody,
                    isDebit = transaction.isDebit
                )
            } catch (e: Exception) {
                logger.warn("MessagesFragment", "Error converting transaction: ${e.message}")
                null
            }
        }.distinctBy { 
            "${it.merchant}_${it.amount}_${it.dateTime}_${it.bankName}"
        }
    }
    
    /**
     * Download and export log files to the Downloads directory
     */
//    private fun downloadLogs() {
//        lifecycleScope.launch {
//            try {
//                val logFiles = // Logging removed
//
//                if (logFiles.isEmpty()) {
//                    Toast.makeText(requireContext(), "No log files found", Toast.LENGTH_SHORT).show()
//                    return@launch
//                }
//
//                // Create Downloads directory file
//                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
//                val zipFile = File(downloadsDir, "ExpenseManager_logs_$timestamp.zip")
//
////                // Create zip file with all logs
////                ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
////                    logFiles.forEach { logFile ->
////                        if (logFile.exists() && logFile.length() > 0) {
////                            val entry = ZipEntry(logFile.name)
////                            zipOut.putNextEntry(entry)
////
////                            FileInputStream(logFile).use { fileInput ->
////                                fileInput.copyTo(zipOut)
////                            }
////                            zipOut.closeEntry()
////                        }
////                    }
//
//                    // Add a summary file with app info
//                    val summaryEntry = ZipEntry("log_summary.txt")
////                    zipOut.putNextEntry(summaryEntry)
//                    val summary = """
//                        Smart Expense Manager - Log Export
//                        Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
//
//                        Log Files Included:
//                        ${logFiles.joinToString("\n") { "- ${it.name} (${it.length()} bytes)" }}
//
//                        Logging Configuration:
//                        Timber logging is active
//
//                        App Version: ${requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName}
//                    """.trimIndent()
//
//                    zipOut.write(summary.toByteArray())
//                    zipOut.closeEntry()
//                }
//
//                // Show success message with file location
//                Toast.makeText(
//                    requireContext(),
//                    "Logs exported to Downloads/ExpenseManager_logs_$timestamp.zip",
//                    Toast.LENGTH_LONG
//                ).show()
//
//                // Log the export activity
//                // Logging removed
//
//            } catch (e: Exception) {
//                // Logging removed
//                Toast.makeText(requireContext(), "Failed to export logs: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//        }
    
    override fun onResume() {
        super.onResume()
        
        // Register broadcast receiver for new transactions
        val intentFilter = IntentFilter("com.expensemanager.NEW_TRANSACTION_ADDED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(newTransactionReceiver, intentFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(newTransactionReceiver, intentFilter)
        }
        logger.debug("MessagesFragment", "Registered broadcast receiver for new transactions")
        
        // Refresh UI when user returns (in case they granted permissions in settings)
        checkPermissionsAndSetupUI()
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister broadcast receiver to prevent memory leaks
        try {
            requireContext().unregisterReceiver(newTransactionReceiver)
            logger.debug("MessagesFragment", "Unregistered broadcast receiver for new transactions")
        } catch (e: Exception) {
            // Receiver may not have been registered, ignore
            logger.warnWithThrowable("MessagesFragment", "Broadcast receiver was not registered, ignoring unregister", e)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * Show dialog when there's a category mismatch conflict
     */
    private fun showAliasConflictDialog(
        group: MerchantGroup, 
        newDisplayName: String, 
        newCategory: String, 
        conflict: MerchantAliasManager.AliasConflict
    ) {
        logger.debug("MessagesFragment", "[CONFLICT] Showing enhanced category mismatch dialog")
        
        val affectedMerchantCount = conflict.affectedMerchants.size
        val merchantText = if (affectedMerchantCount == 1) "merchant" else "merchants"
        
        val message = """
            🚨 Merchant Name Conflict Detected
            
            The name "$newDisplayName" is already used by $affectedMerchantCount $merchantText in "${conflict.existingCategory}" category.
            
            You're trying to assign it to "$newCategory" category.
            
            📝 Your Options:
            
            1️⃣ Merge All: Move ALL "$newDisplayName" merchants to one category
            2️⃣ Use Different Name: Keep them separate with a unique name
            3️⃣ Cancel: Keep everything as it is
            
            💡 This ensures your transaction grouping stays organized!
        """.trimIndent()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📋 Smart Grouping Assistant")
            .setMessage(message)
            .setPositiveButton("🔄 Merge All") { _, _ ->
                // Ask which category to use for all merchants with this display name
                showCategoryMergeDialog(group, newDisplayName, newCategory, conflict)
            }
            .setNeutralButton("✏️ Use Different Name") { _, _ ->
                // Re-open the edit dialog with a suggested alternative name
                val suggestedName = "${newDisplayName} (${newCategory})"
                showMerchantGroupEditDialogWithSuggestion(group, suggestedName, newCategory)
            }
            .setNegativeButton("❌ Cancel") { dialog, _ ->
                logger.debug("MessagesFragment", "[CONFLICT] User cancelled merchant aliasing")
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Show dialog to choose which category to use when merging
     */
    private fun showCategoryMergeDialog(
        group: MerchantGroup,
        displayName: String,
        newCategory: String,
        conflict: MerchantAliasManager.AliasConflict
    ) {
        val categories = listOf(newCategory, conflict.existingCategory!!).distinct()
        val categoryArray = categories.toTypedArray()
        val affectedMerchantCount = conflict.affectedMerchants.size + 1 // +1 for the current merchant
        var selectedCategory = categories[0] // Default selection

        val enhancedMessage = """
            🎯 Merging Categories for '$displayName'
            
            📊 Impact: This will affect $affectedMerchantCount merchant groups
            
            🔄 All transactions from merchants named '$displayName' will be grouped under the selected category.
            
            💡 Choose wisely - this action will reorganize your transaction history!
        """.trimIndent()

        // Add emoji indicators for categories
        val enhancedCategories = categoryArray.map {
            val emoji = getCategoryEmoji(it)
            "$emoji $it"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔄 Merge Categories")
            .setMessage(enhancedMessage)
            .setSingleChoiceItems(enhancedCategories, 0) { _, which ->
                // Just update the selection, don't perform the action
                selectedCategory = categories[which]
            }
            .setPositiveButton("Merge") { dialog, _ ->
                dialog.dismiss()
                logger.debug("MessagesFragment", "[MERGE] User confirmed merge with category '$selectedCategory'")
                performMerchantAliasUpdateDirectly(group, displayName, selectedCategory)
            }
            .setNegativeButton("❌ Cancel") { dialog, _ ->
                logger.debug("MessagesFragment", "[MERGE] User cancelled category merge")
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Show dialog when user wants to overwrite existing alias
     */
    private fun showOverwriteConfirmationDialog(
        group: MerchantGroup,
        newDisplayName: String,
        newCategory: String,
        conflict: MerchantAliasManager.AliasConflict
    ) {
        logger.debug("MessagesFragment", "[CONFLICT] Showing enhanced overwrite confirmation dialog")
        
        val currentEmojiCategory = getCategoryEmoji(conflict.existingCategory ?: "")
        val newEmojiCategory = getCategoryEmoji(newCategory)
        
        val changeType = if (conflict.existingDisplayName != newDisplayName && conflict.existingCategory != newCategory) {
            "both name and category"
        } else if (conflict.existingDisplayName != newDisplayName) {
            "name only"
        } else {
            "category only"
        }
        
        val message = """
            📝 Update Existing Merchant Alias
            
            Current Settings:
            🏷️ Name: "${conflict.existingDisplayName}"
            $currentEmojiCategory Category: "${conflict.existingCategory}"
            
            New Settings:
            🏷️ Name: "$newDisplayName"
            $newEmojiCategory Category: "$newCategory"
            
            📊 This will update $changeType for this merchant group.
            
            ⚠️ All existing transactions will be regrouped accordingly.
        """.trimIndent()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔄 Update Merchant Settings")
            .setMessage(message)
            .setPositiveButton("✅ Update") { _, _ ->
                logger.debug("MessagesFragment", "[OVERWRITE] User confirmed overwrite: '$changeType' update")
                performMerchantAliasUpdateDirectly(group, newDisplayName, newCategory)
            }
            .setNegativeButton("❌ Cancel") { dialog, _ ->
                logger.debug("MessagesFragment", "[OVERWRITE] User cancelled overwrite")
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Re-open edit dialog with suggested name
     */
    private fun showMerchantGroupEditDialogWithSuggestion(group: MerchantGroup, suggestedName: String, category: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_edit_merchant_group,
            null
        )
        
        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.et_group_name)
        val categorySelectorCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.category_selector_card)
        val tvSelectedCategory = dialogView.findViewById<TextView>(R.id.tv_selected_category)
        
        // Pre-fill with suggested values
        etGroupName.setText(suggestedName)
        tvSelectedCategory.text = category
        
        // Handle category selection
        categorySelectorCard.setOnClickListener {
            lifecycleScope.launch {
                val categories = categoryManager.getAllCategories()
                showCategorySelectionDialog(categories, tvSelectedCategory.text.toString()) { newCategory ->
                    tvSelectedCategory.text = newCategory
                }
            }
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Merchant Group")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val finalName = etGroupName.text.toString().trim()
                val finalCategory = tvSelectedCategory.text.toString()
                
                if (finalName.isNotEmpty() && finalCategory.isNotEmpty()) {
                    updateMerchantGroup(group, finalName, finalCategory)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Perform merchant alias update directly without conflict checking
     */
    private fun performMerchantAliasUpdateDirectly(group: MerchantGroup, newDisplayName: String, newCategory: String) {
        lifecycleScope.launch {
            try {
                logger.debug("MessagesFragment", "[DIRECT] Performing direct merchant update: '${group.merchantName}' -> '$newDisplayName' in '$newCategory'")
                
                // Continue with the normal update flow but skip conflict checking
                val originalMerchantNames = mutableSetOf<String>()

                // FIXED: Always use rawMerchant from actual transactions (not normalized names from SharedPreferences)
                logger.debug("MessagesFragment", "[DIRECT_FIX] Extracting original merchant names from ${group.transactions.size} transactions")

                group.transactions.forEach { transaction ->
                    if (transaction.rawMerchant.isNotBlank()) {
                        originalMerchantNames.add(transaction.rawMerchant)
                        logger.debug("MessagesFragment", "[DIRECT_FIX] Added rawMerchant: ${transaction.rawMerchant}")
                    }
                }

                // Fallback: If rawMerchant was blank for all transactions, use display name
                if (originalMerchantNames.isEmpty()) {
                    originalMerchantNames.add(group.merchantName)
                    logger.warn("MessagesFragment", "[DIRECT_FIX] No rawMerchant found, using display name: ${group.merchantName}")
                }
                
                // Create category if it doesn't exist
                val allCategories = categoryManager.getAllCategories()
                if (!allCategories.contains(newCategory)) {
                    categoryManager.addCustomCategory(newCategory)
                }
                
                // Update SharedPreferences
                var aliasSetCount = 0
                originalMerchantNames.forEach { originalName ->
                    if (merchantAliasManager.setMerchantAlias(originalName, newDisplayName, newCategory)) {
                        aliasSetCount++
                    }
                }
                
                // Update Database
                if (aliasSetCount > 0) {
                    val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
                    val databaseUpdateSuccess = repository.updateMerchantAliasInDatabase(
                        originalMerchantNames.toList(),
                        newDisplayName,
                        newCategory
                    )
                    
                    if (databaseUpdateSuccess) {
                        // Invalidate ViewModel's cache to ensure it gets fresh alias data
                        try {
                            logger.debug("MessagesFragment", "[CACHE_SYNC] Invalidating ViewModel cache for database success")
                            messagesViewModel.invalidateMerchantAliasCache()
                        } catch (e: Exception) {
                            logger.warnWithThrowable("MessagesFragment", "Could not invalidate ViewModel cache for database success", e)
                        }
                        
                        // Trigger ViewModel update to reflect changes
                        try {
                            logger.debug("MessagesFragment", "[UI_FIX] Using ONLY ViewModel refresh for database success case")
                            messagesViewModel.handleEvent(MessagesUIEvent.LoadMessages)
                        } catch (e: Exception) {
                            logger.warnWithThrowable("MessagesFragment", "Could not trigger ViewModel refresh for database success", e)
                        }
                        Toast.makeText(
                            requireContext(),
                            "Successfully updated '${group.merchantName}' to '$newDisplayName' in '$newCategory'",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Database update failed, changes may not persist",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
            } catch (e: Exception) {
                logger.error("MessagesFragment", "Error in direct merchant update", e)
                Toast.makeText(
                    requireContext(),
                    "Failed to update merchant: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Check if a date string is within the specified range
     * Supports multiple date formats commonly used in the app
     */
    // REMOVED: Date filtering helper functions
    // These are no longer needed in Fragment since ViewModel handles all filtering

    /**
     * Show DatePicker dialog and return selected date in yyyy-MM-dd format
     * This format matches what the ViewModel filtering logic expects
     */
    private fun showDatePickerDialog(onDateSelected: (String) -> Unit) {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH)
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

        val datePickerDialog = android.app.DatePickerDialog(
            requireContext(),
            R.style.DatePickerTheme, // Apply light theme for visibility
            { _, selectedYear, selectedMonth, selectedDay ->
                // Format date as yyyy-MM-dd (matches ViewModel filter format)
                val formattedDate = String.format(
                    "%04d-%02d-%02d",
                    selectedYear,
                    selectedMonth + 1, // Month is 0-based
                    selectedDay
                )
                onDateSelected(formattedDate)
            },
            year,
            month,
            day
        )

        datePickerDialog.show()
    }
}
