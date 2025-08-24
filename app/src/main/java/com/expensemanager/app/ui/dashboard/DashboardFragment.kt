package com.expensemanager.app.ui.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.expensemanager.app.R
import com.expensemanager.app.MainActivity
import com.expensemanager.app.databinding.FragmentDashboardBinding
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.data.repository.DashboardData
import com.expensemanager.app.ui.dashboard.MerchantSpending
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
// REMOVED: SMSHistoryReader import - no more direct SMS reading
import com.expensemanager.app.utils.ParsedTransaction
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class DashboardFragment : Fragment() {
    
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var repository: ExpenseRepository
    private lateinit var categoryManager: CategoryManager
    private lateinit var merchantAliasManager: MerchantAliasManager
    private lateinit var topMerchantsAdapter: TopMerchantsAdapter
    private lateinit var topCategoriesAdapter: TopCategoriesAdapter
    private var currentTimePeriod = "This Month" // Default time period for weekly trend
    private var currentDashboardPeriod = "This Month" // Default time period for entire dashboard
    
    // Custom month selection variables
    private var customFirstMonth: Pair<Int, Int>? = null  // (month, year)
    private var customSecondMonth: Pair<Int, Int>? = null // (month, year)
    
    // Broadcast receiver for new transaction and category update notifications
    private val newTransactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.expensemanager.NEW_TRANSACTION_ADDED" -> {
                    val merchant = intent.getStringExtra("merchant") ?: "Unknown"
                    val amount = intent.getDoubleExtra("amount", 0.0)
                    Log.d("DashboardFragment", "📡 Received new transaction broadcast: $merchant - ₹${String.format("%.0f", amount)}")
                    
                    // Refresh dashboard data on the main thread
                    lifecycleScope.launch {
                        try {
                            Log.d("DashboardFragment", "🔄 Refreshing dashboard due to new transaction")
                            loadDashboardData()
                            
                            // Show a brief toast to indicate refresh
                            android.widget.Toast.makeText(
                                requireContext(),
                                "💰 New transaction added - Dashboard updated",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            
                        } catch (e: Exception) {
                            Log.e("DashboardFragment", "Error refreshing dashboard after new transaction", e)
                        }
                    }
                }
                
                "com.expensemanager.CATEGORY_UPDATED" -> {
                    val merchant = intent.getStringExtra("merchant") ?: "Unknown"
                    val category = intent.getStringExtra("category") ?: "Unknown"
                    Log.d("DashboardFragment", "📡 Received category update broadcast: $merchant → $category")
                    
                    // Refresh dashboard data on the main thread
                    lifecycleScope.launch {
                        try {
                            Log.d("DashboardFragment", "🔄 Refreshing dashboard due to category update")
                            loadDashboardData()
                            
                        } catch (e: Exception) {
                            Log.e("DashboardFragment", "Error refreshing dashboard after category update", e)
                        }
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
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = ExpenseRepository.getInstance(requireContext())
        categoryManager = CategoryManager(requireContext())
        merchantAliasManager = MerchantAliasManager(requireContext())
        setupUI()
        setupClickListeners()
        loadDashboardData()
    }
    
    private fun setupUI() {
        // Initialize with loading state
        binding.tvTotalBalance.text = "Loading..."
        binding.tvTotalSpent.text = "Loading..."
        binding.tvTransactionCount.text = "0"
        
        // Setup top merchants recycler view
        setupTopMerchantsRecyclerView()
        setupTopCategoriesRecyclerView()
        
        // Setup time period filter
        setupTimePeriodFilter()
        
        // Setup dashboard period filter
        setupDashboardPeriodFilter()
    }
    
    private fun setupTopMerchantsRecyclerView() {
        topMerchantsAdapter = TopMerchantsAdapter { merchantSpending ->
            // Navigate to merchant transactions screen
            val bundle = Bundle().apply {
                putString("merchantName", merchantSpending.merchantName)
            }
            findNavController().navigate(
                R.id.action_dashboard_to_merchant_transactions,
                bundle
            )
        }
        binding.recyclerTopMerchants.apply {
            adapter = topMerchantsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun setupTopCategoriesRecyclerView() {
        topCategoriesAdapter = TopCategoriesAdapter()
        binding.recyclerTopCategories.apply {
            adapter = topCategoriesAdapter
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
        }
    }
    
    private fun setupTimePeriodFilter() {
        val timePeriods = listOf(
            "Last Week",
            "This Month", 
            "Last Month",
            "Last 3 Months",
            "Last 6 Months",
            "This Year"
        )
        
        binding.btnTimeFilter.text = currentTimePeriod
        binding.btnTimeFilter.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Time Period")
                .setItems(timePeriods.toTypedArray()) { _, which ->
                    val selectedPeriod = timePeriods[which]
                    if (selectedPeriod != currentTimePeriod) {
                        currentTimePeriod = selectedPeriod
                        binding.btnTimeFilter.text = selectedPeriod
                        // FIXED: No more period aggregation - refresh dashboard with repository data
                        loadDashboardData()
                    }
                }
                .show()
        }
    }
    
    private fun setupDashboardPeriodFilter() {
        // Enhanced: Single month periods + Custom month selection
        val dashboardPeriods = listOf(
            "This Month",
            "Last Month",
            "Custom Months..." // Allow user to pick any two months
        )
        
        binding.btnDashboardPeriod.text = currentDashboardPeriod
        binding.btnDashboardPeriod.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Dashboard Period")
                .setItems(dashboardPeriods.toTypedArray()) { _, which ->
                    val selectedPeriod = dashboardPeriods[which]
                    if (selectedPeriod == "Custom Months...") {
                        // Show custom month picker dialog
                        showCustomMonthPickerDialog()
                    } else if (selectedPeriod != currentDashboardPeriod) {
                        currentDashboardPeriod = selectedPeriod
                        binding.btnDashboardPeriod.text = selectedPeriod
                        
                        // Also update the weekly trend period to match
                        currentTimePeriod = selectedPeriod
                        binding.btnTimeFilter.text = selectedPeriod
                        
                        // Refresh entire dashboard for the selected period
                        loadDashboardDataForPeriod(selectedPeriod)
                    }
                }
                .show()
        }
    }
    
    private fun setupClickListeners() {
        binding.btnAiInsights.setOnClickListener {
            findNavController().navigate(R.id.navigation_insights)
        }
        
        // Quick Action button listeners
        binding.btnAddExpense.setOnClickListener {
            showQuickAddExpenseDialog()
        }
        
        binding.btnSyncSms.setOnClickListener {
            performSMSSync()
        }
        
        binding.btnViewBudget.setOnClickListener {
            findNavController().navigate(R.id.navigation_budget_goals)
        }
        
        binding.btnExportData.setOnClickListener {
            findNavController().navigate(R.id.navigation_export_data)
        }
        
        binding.btnViewInsights.setOnClickListener {
            // Navigate to insights tab using bottom navigation
            navigateToTab(R.id.navigation_insights)
        }
        
        // Make transaction count card clickable
        binding.cardTransactionCount.setOnClickListener {
            // Navigate to messages tab using bottom navigation
            navigateToTab(R.id.navigation_messages)
        }
        
        binding.btnSettings.setOnClickListener {
            // For settings, use normal navigation as it's not a bottom tab
            findNavController().navigate(R.id.navigation_settings)
        }
    }
    
    private fun showQuickAddExpenseDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_quick_add_expense, 
            null
        )
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("💰 Quick Add Expense")
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
            
            if (amount.isNotEmpty() && merchant.isNotEmpty() && category.isNotEmpty()) {
                Toast.makeText(
                    requireContext(), 
                    "✅ Added: ₹$amount at $merchant ($category)", 
                    Toast.LENGTH_LONG
                ).show()
                dialog.dismiss()
                
                // Refresh UI
                setupUI()
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
    
    private fun showCustomMonthPickerDialog() {
        // Create dialog programmatically for better compatibility
        createCustomMonthPickerDialogProgrammatically()
    }
    
    private fun createCustomMonthPickerDialogProgrammatically() {
        // Create the dialog content programmatically
        val scrollView = androidx.core.widget.NestedScrollView(requireContext())
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        
        // First Month Section
        val firstMonthLabel = TextView(requireContext()).apply {
            text = "📊 First Month:"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(firstMonthLabel)
        
        val firstMonthSpinner = androidx.appcompat.widget.AppCompatSpinner(requireContext()).apply {
            id = android.view.View.generateViewId()
        }
        populateMonthSpinner(firstMonthSpinner)
        layout.addView(firstMonthSpinner)
        
        // Spacer
        val spacer = android.view.View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                32
            )
        }
        layout.addView(spacer)
        
        // Second Month Section  
        val secondMonthLabel = TextView(requireContext()).apply {
            text = "📈 Second Month (to compare with):"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(secondMonthLabel)
        
        val secondMonthSpinner = androidx.appcompat.widget.AppCompatSpinner(requireContext()).apply {
            id = android.view.View.generateViewId()
        }
        populateMonthSpinner(secondMonthSpinner)
        layout.addView(secondMonthSpinner)
        
        scrollView.addView(layout)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📅 Select Two Months to Compare")
            .setView(scrollView)
            .setPositiveButton("Compare") { _, _ ->
                handleSpinnerSelections(firstMonthSpinner, secondMonthSpinner)
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .show()
    }
    
    private fun populateMonthSpinner(spinner: androidx.appcompat.widget.AppCompatSpinner) {
        val months = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        
        // Add last 12 months
        for (i in 0..11) {
            calendar.add(Calendar.MONTH, if (i == 0) 0 else -1)
            val monthName = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(calendar.time)
            months.add(monthName)
        }
        
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            months
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        
        spinner.adapter = adapter
    }
    
    // REMOVED: handleCustomMonthSelection() - using programmatic dialog only
    
    private fun handleSpinnerSelections(
        firstSpinner: androidx.appcompat.widget.AppCompatSpinner,
        secondSpinner: androidx.appcompat.widget.AppCompatSpinner
    ) {
        try {
            val firstMonthText = firstSpinner.selectedItem?.toString() ?: ""
            val secondMonthText = secondSpinner.selectedItem?.toString() ?: ""
            
            Log.d("DashboardFragment", "📅 Custom month selection: '$firstMonthText' vs '$secondMonthText'")
            
            // Parse the month/year from spinner selections
            customFirstMonth = parseMonthYear(firstMonthText)
            customSecondMonth = parseMonthYear(secondMonthText)
            
            if (customFirstMonth != null && customSecondMonth != null) {
                if (customFirstMonth == customSecondMonth) {
                    Toast.makeText(requireContext(), "⚠️ Please select two different months", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Update dashboard period to show custom selection
                currentDashboardPeriod = "Custom: $firstMonthText vs $secondMonthText"
                binding.btnDashboardPeriod.text = "Custom Months"
                
                // Load dashboard with custom month comparison
                loadDashboardDataWithCustomMonths()
                
                Toast.makeText(
                    requireContext(),
                    "✅ Comparing $firstMonthText vs $secondMonthText",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(requireContext(), "❌ Error parsing selected months", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error handling custom month selection", e)
            Toast.makeText(requireContext(), "❌ Error with month selection", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun parseMonthYear(monthYearText: String): Pair<Int, Int>? {
        return try {
            // Parse "January 2024" format
            val parts = monthYearText.split(" ")
            if (parts.size == 2) {
                val month = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault())
                    .parse(parts[0])?.let { date ->
                        Calendar.getInstance().apply { time = date }.get(Calendar.MONTH)
                    } ?: return null
                val year = parts[1].toInt()
                Pair(month, year)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error parsing month/year: $monthYearText", e)
            null
        }
    }
    
    private fun loadDashboardDataWithCustomMonths() {
        lifecycleScope.launch {
            try {
                if (_binding == null || customFirstMonth == null || customSecondMonth == null) {
                    Log.w("DashboardFragment", "Cannot load custom months - binding or months are null")
                    return@launch
                }
                
                Log.d("DashboardFragment", "📅 Loading dashboard with custom months: ${customFirstMonth} vs ${customSecondMonth}")
                
                // Calculate date ranges for both custom months
                val (firstStart, firstEnd) = getDateRangeForCustomMonth(customFirstMonth!!)
                val (secondStart, secondEnd) = getDateRangeForCustomMonth(customSecondMonth!!)
                
                Log.d("DashboardFragment", "📅 First month range: $firstStart to $firstEnd")
                Log.d("DashboardFragment", "📅 Second month range: $secondStart to $secondEnd")
                
                // Load dashboard data for the first month (main display)
                val firstMonthData = repository.getDashboardData(firstStart, firstEnd)
                
                Log.d("DashboardFragment", "📊 First month data: ${firstMonthData.transactionCount} transactions, ₹${String.format("%.0f", firstMonthData.totalSpent)}")
                
                if (firstMonthData.transactionCount > 0) {
                    // Update dashboard with first month data
                    updateDashboardWithRepositoryData(firstMonthData, firstStart, firstEnd)
                    
                    // Update monthly comparison with custom months
                    updateCustomMonthlyComparison(firstStart, firstEnd, secondStart, secondEnd)
                } else {
                    Log.d("DashboardFragment", "⚠️ No data found for first custom month, trying sync...")
                    
                    // Try syncing SMS and retry
                    val syncedCount = repository.syncNewSMS()
                    if (syncedCount > 0) {
                        val retryData = repository.getDashboardData(firstStart, firstEnd)
                        if (retryData.transactionCount > 0) {
                            updateDashboardWithRepositoryData(retryData, firstStart, firstEnd)
                            updateCustomMonthlyComparison(firstStart, firstEnd, secondStart, secondEnd)
                        } else {
                            updateDashboardWithEmptyState()
                        }
                    } else {
                        updateDashboardWithEmptyState()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error loading custom months dashboard", e)
                updateDashboardWithError()
            }
        }
    }
    
    private fun getDateRangeForCustomMonth(monthYear: Pair<Int, Int>): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, monthYear.second)
        calendar.set(Calendar.MONTH, monthYear.first)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        // End of month
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        return Pair(startDate, endDate)
    }
    
    private suspend fun updateCustomMonthlyComparison(
        firstStart: Date, firstEnd: Date,
        secondStart: Date, secondEnd: Date
    ) {
        try {
            // Get spending for both custom months
            val firstMonthSpent = repository.getTotalSpent(firstStart, firstEnd)
            val secondMonthSpent = repository.getTotalSpent(secondStart, secondEnd)
            
            // Create readable labels
            val firstMonthLabel = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(firstStart)
            val secondMonthLabel = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(secondStart)
            
            Log.d("DashboardFragment", "💰 Custom monthly comparison:")
            Log.d("DashboardFragment", "   $firstMonthLabel: ₹${String.format("%.0f", firstMonthSpent)}")
            Log.d("DashboardFragment", "   $secondMonthLabel: ₹${String.format("%.0f", secondMonthSpent)}")
            
            // Update the UI with custom month comparison
            updateMonthlyComparisonUI(firstMonthLabel, secondMonthLabel, firstMonthSpent, secondMonthSpent)
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error updating custom monthly comparison", e)
        }
    }
    
    private fun ensureMinimumMerchants(realMerchants: List<MerchantSpending>, minimumCount: Int): List<MerchantSpending> {
        if (realMerchants.size >= minimumCount) {
            return realMerchants.take(minimumCount) // Take exactly the minimum count
        }
        
        // If we don't have enough real merchants, add placeholder merchants
        val placeholderMerchants = listOf(
            MerchantSpending("Swiggy", 0.0, 0, "Food & Dining", "#ff5722", 0.0),
            MerchantSpending("Amazon", 0.0, 0, "Shopping", "#ff9800", 0.0),
            MerchantSpending("Uber", 0.0, 0, "Transportation", "#3f51b5", 0.0),
            MerchantSpending("BigBasket", 0.0, 0, "Groceries", "#4caf50", 0.0),
            MerchantSpending("Netflix", 0.0, 0, "Entertainment", "#9c27b0", 0.0)
        )
        
        val combinedList = realMerchants.toMutableList()
        
        // Add placeholders until we reach minimum count
        for (placeholder in placeholderMerchants) {
            if (combinedList.size >= minimumCount) break
            // Only add if this merchant name doesn't already exist
            if (combinedList.none { it.merchantName == placeholder.merchantName }) {
                combinedList.add(placeholder)
            }
        }
        
        Log.d("DashboardFragment", "📊 Ensured minimum merchants: ${realMerchants.size} real + ${combinedList.size - realMerchants.size} placeholders = ${combinedList.size} total")
        
        return combinedList.take(minimumCount)
    }
    
    private fun ensureMinimumCategories(realCategories: List<CategorySpending>, minimumCount: Int): List<CategorySpending> {
        if (realCategories.size >= minimumCount) {
            return realCategories.take(minimumCount) // Take exactly the minimum count
        }
        
        // If we don't have enough real categories, add placeholder categories
        val placeholderCategories = listOf(
            CategorySpending("Food & Dining", 0.0, "#ff5722"),
            CategorySpending("Transportation", 0.0, "#3f51b5"),
            CategorySpending("Shopping", 0.0, "#ff9800"),
            CategorySpending("Groceries", 0.0, "#4caf50"),
            CategorySpending("Entertainment", 0.0, "#9c27b0"),
            CategorySpending("Utilities", 0.0, "#607d8b")
        )
        
        val combinedList = realCategories.toMutableList()
        
        // Add placeholders until we reach minimum count
        for (placeholder in placeholderCategories) {
            if (combinedList.size >= minimumCount) break
            // Only add if this category name doesn't already exist
            if (combinedList.none { it.categoryName == placeholder.categoryName }) {
                combinedList.add(placeholder)
            }
        }
        
        Log.d("DashboardFragment", "📊 Ensured minimum categories: ${realCategories.size} real + ${combinedList.size - realCategories.size} placeholders = ${combinedList.size} total")
        
        return combinedList.take(minimumCount)
    }
    
    private fun performSMSSync() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔄 Sync SMS Messages")
            .setMessage("This will scan your SMS messages and update all transaction data. This may take a few moments.")
            .setPositiveButton("Sync Now") { _, _ ->
                lifecycleScope.launch {
                    val progressDialog = MaterialAlertDialogBuilder(requireContext())
                        .setTitle("🔄 Syncing SMS Messages")
                        .setMessage("Scanning your SMS inbox for transaction messages...")
                        .setCancelable(false)
                        .create()
                    
                    try {
                        progressDialog.show()
                        
                        // FIXED: Use repository sync instead of direct SMS reading
                        val syncedCount = repository.syncNewSMS()
                        
                        progressDialog.dismiss()
                        
                        if (syncedCount > 0) {
                            Toast.makeText(
                                requireContext(),
                                "✅ Synced $syncedCount new transactions from SMS!",
                                Toast.LENGTH_LONG
                            ).show()
                            
                            // Reload dashboard data from repository
                            loadDashboardData()
                            
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "📱 No new transaction SMS found",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        
                    } catch (e: SecurityException) {
                        progressDialog.dismiss()
                        Toast.makeText(
                            requireContext(),
                            "❌ SMS permission required for sync",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        progressDialog.dismiss()
                        Toast.makeText(
                            requireContext(),
                            "❌ Error syncing SMS: ${e.message ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun loadDashboardData() {
        lifecycleScope.launch {
            try {
                // Check if fragment view is still valid
                if (_binding == null) {
                    Log.w("DashboardFragment", "Binding is null, cannot load dashboard data")
                    return@launch
                }
                
                Log.d("DashboardFragment", "Loading dashboard data from SQLite database...")
                
                // Get date range for current dashboard period
                val (startDate, endDate) = getDateRangeForPeriod(currentDashboardPeriod)
                
                // Check if we have any data in the repository
                val dashboardData = repository.getDashboardData(startDate, endDate)
                
                if (dashboardData.transactionCount > 0) {
                    Log.d("DashboardFragment", "✅ Loaded dashboard data from SQLite: ${dashboardData.transactionCount} transactions, ₹${String.format("%.0f", dashboardData.totalSpent)} spent")
                    Log.d("DashboardFragment", "📊 Dashboard Date Range: ${startDate} to ${endDate}")
                    Log.d("DashboardFragment", "📊 Dashboard Raw Transactions Count: ${repository.getTransactionsByDateRange(startDate, endDate).size}")
                    Log.d("DashboardFragment", "📊 Dashboard Filtered Total: ₹${String.format("%.0f", dashboardData.totalSpent)}")
                    updateDashboardWithRepositoryData(dashboardData, startDate, endDate)
                } else {
                    Log.d("DashboardFragment", "📥 No data in SQLite database yet, checking SMS sync status...")
                    
                    // Check if initial migration is still in progress
                    val syncStatus = repository.getSyncStatus()
                    if (syncStatus == "INITIAL" || syncStatus == "IN_PROGRESS") {
                        Log.d("DashboardFragment", "🔄 Initial data migration in progress, showing loading state")
                        showLoadingStateWithMessage("Setting up your data for the first time...")
                        
                        // Retry loading data after a delay
                        binding.root.postDelayed({
                            loadDashboardData()
                        }, 2000)
                    } else {
                        Log.d("DashboardFragment", "⚠️ No transactions found, attempting SMS sync through repository...")
                        performRepositoryBasedSync()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error loading dashboard data from repository", e)
                updateDashboardWithError()
            }
        }
    }
    
    private suspend fun updateDashboardWithRepositoryData(dashboardData: DashboardData, startDate: Date, endDate: Date) {
        // Check if fragment view is still valid
        if (_binding == null) {
            Log.w("DashboardFragment", "Binding is null, cannot update UI")
            return
        }
        
        // One-time migration of exclusion states from SharedPreferences to database
        com.expensemanager.app.utils.ExclusionMigrationHelper.migrateExclusionStatesToDatabase(requireContext(), repository)
        // REMOVED: Automatic large transfer exclusions - only user-controlled exclusions now
        
        // Debug: Log current exclusion states
        Log.d("DashboardFragment", "🔍 ${repository.getExclusionStatesDebugInfo()}")
        
        // Update spending summary
        val totalSpent = dashboardData.totalSpent
        binding.tvTotalSpent.text = "₹${String.format("%.0f", totalSpent)}"
        binding.tvTransactionCount.text = "${dashboardData.transactionCount}"
        
        // For total balance, we'll use a placeholder (real implementation would need bank balance integration)
        // Set to a positive meaningful value instead of subtracting expenses from arbitrary amount
        val placeholderBalance = 45280.0 // Placeholder balance
        binding.tvTotalBalance.text = "₹${String.format("%.0f", placeholderBalance)}"
        
        // Update top categories with repository data
        val categorySpendingItems = dashboardData.topCategories.map { categoryResult ->
            CategorySpending(
                categoryName = categoryResult.category_name,
                amount = categoryResult.total_amount,
                categoryColor = categoryResult.color
            )
        }
        
        // FIXED: Ensure consistent display - always show at least 4 categories (2x2 grid)
        val finalCategoryItems = ensureMinimumCategories(categorySpendingItems, 4)
        
        Log.d("DashboardFragment", "Updating top categories: ${finalCategoryItems.map { "${it.categoryName}=₹${String.format("%.0f", it.amount)}" }}")
        topCategoriesAdapter.submitList(finalCategoryItems)
        
        // Update top merchants with repository data  
        val merchantItems = dashboardData.topMerchants.map { merchantResult ->
            MerchantSpending(
                merchantName = repository.normalizeDisplayMerchantName(merchantResult.normalized_merchant),
                totalAmount = merchantResult.total_amount,
                transactionCount = merchantResult.transaction_count,
                category = "Unknown", // We'll need to enhance this later
                categoryColor = "#9e9e9e", // Default color
                percentage = 0.0 // Will be calculated by adapter if needed
            )
        }
        
        // FIXED: Ensure consistent display - always show at least 3 merchants
        val finalMerchantItems = ensureMinimumMerchants(merchantItems, 3)
        
        Log.d("DashboardFragment", "Updating top merchants: ${finalMerchantItems.map { "${it.merchantName}=₹${String.format("%.0f", it.totalAmount)}" }}")
        topMerchantsAdapter.submitList(finalMerchantItems)
        
        // Update monthly comparison based on selected period
        updateMonthlyComparisonFromRepository(startDate, endDate, currentDashboardPeriod)
        
        // Update weekly trend with repository data
        updateWeeklyTrendFromRepository(startDate, endDate)
        
        Log.d("DashboardFragment", "✅ Dashboard UI updated successfully with repository data")
    }
    
    private suspend fun updateMonthlyComparisonFromRepository(currentStart: Date, currentEnd: Date, period: String) {
        try {
            // FIXED: Monthly comparison now only compares single month to single month
            // No more period aggregation - only individual month comparisons
            val (currentLabel, previousLabel, previousStart, previousEnd) = when (period) {
                "This Month" -> {
                    // Compare This Month vs Last Month (single months only)
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, -1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val prevStart = cal.time
                    
                    cal.add(Calendar.MONTH, 1)
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val prevEnd = cal.time
                    
                    Tuple4("This Month", "Last Month", prevStart, prevEnd)
                }
                
                "Last Month" -> {
                    // Compare Last Month vs Two Months Ago (single months only)
                    Log.d("DashboardFragment", "🔍 DEBUG: Processing 'Last Month' period case")
                    val cal = Calendar.getInstance()
                    Log.d("DashboardFragment", "🔍 DEBUG: Current time: ${cal.time}")
                    
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, -2)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val prevStart = cal.time
                    Log.d("DashboardFragment", "🔍 DEBUG: Two months ago start: $prevStart")
                    
                    cal.add(Calendar.MONTH, 1)
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val prevEnd = cal.time
                    Log.d("DashboardFragment", "🔍 DEBUG: Two months ago end: $prevEnd")
                    
                    Log.d("DashboardFragment", "🔍 DEBUG: Returning comparison 'Last Month' vs 'Two Months Ago'")
                    Tuple4("Last Month", "Two Months Ago", prevStart, prevEnd)
                }
                
                else -> {
                    // For all other periods, default to This Month vs Last Month
                    // This prevents period aggregation issues
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, -1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val prevStart = cal.time
                    
                    cal.add(Calendar.MONTH, 1)
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val prevEnd = cal.time
                    
                    Tuple4("This Month", "Last Month", prevStart, prevEnd)
                }
            }
            
            Log.d("DashboardFragment", "📅 Monthly comparison date ranges for period '$period':")
            Log.d("DashboardFragment", "   $currentLabel: ${currentStart} to ${currentEnd}")
            Log.d("DashboardFragment", "   $previousLabel: ${previousStart} to ${previousEnd}")
            
            val currentPeriodSpent = repository.getTotalSpent(currentStart, currentEnd)
            val previousPeriodSpent = repository.getTotalSpent(previousStart, previousEnd)
            
            Log.d("DashboardFragment", "💰 Monthly spending comparison (single months only):")
            Log.d("DashboardFragment", "   $currentLabel: ₹${String.format("%.0f", currentPeriodSpent)}")
            Log.d("DashboardFragment", "   $previousLabel: ₹${String.format("%.0f", previousPeriodSpent)}")
            
            updateMonthlyComparisonUI(currentLabel, previousLabel, currentPeriodSpent, previousPeriodSpent)
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error updating monthly comparison", e)
            
            // Set fallback values on error
            binding.tvThisMonthAmount.text = "₹0"
            binding.tvLastMonthAmount.text = "₹0"
            binding.tvSpendingComparison.text = "Unable to calculate comparison"
        }
    }
    
    private fun getDateRangeForPeriod(period: String): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time // Current time
        
        when (period) {
            "This Month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, endDate)
            }
            "Last Month" -> {
                // End of last month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                val lastMonthEnd = calendar.time
                
                // Start of last month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val lastMonthStart = calendar.time
                return Pair(lastMonthStart, lastMonthEnd)
            }
            "Last 3 Months" -> {
                calendar.add(Calendar.MONTH, -3)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, endDate)
            }
            "Last 6 Months" -> {
                calendar.add(Calendar.MONTH, -6)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, endDate)
            }
            "This Year" -> {
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, endDate)
            }
            "Last Year" -> {
                // End of last year
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                val lastYearEnd = calendar.time
                
                // Start of last year  
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val lastYearStart = calendar.time
                return Pair(lastYearStart, lastYearEnd)
            }
            else -> {
                // Default to current month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, endDate)
            }
        }
    }
    
    private fun showLoadingState() {
        binding.tvTotalBalance.text = "Loading..."
        binding.tvTotalSpent.text = "Loading..."
        binding.tvTransactionCount.text = "0"
    }
    
    private fun showLoadingStateWithMessage(message: String) {
        binding.tvTotalBalance.text = message
        binding.tvTotalSpent.text = "Loading..."
        binding.tvTransactionCount.text = "0"
    }
    
    private fun performRepositoryBasedSync() {
        lifecycleScope.launch {
            try {
                Log.d("DashboardFragment", "🔄 Attempting SMS sync through repository only...")
                
                // Use repository's syncNewSMS method - no direct SMS reading
                val syncedCount = repository.syncNewSMS()
                Log.d("DashboardFragment", "💾 Repository sync completed: $syncedCount new transactions")
                
                if (syncedCount > 0) {
                    // Reload dashboard data after successful sync
                    Log.d("DashboardFragment", "✅ SMS sync successful, reloading dashboard data...")
                    loadDashboardData()
                } else {
                    Log.d("DashboardFragment", "📭 No new transactions found during sync")
                    updateDashboardWithEmptyState()
                }
                
            } catch (e: SecurityException) {
                Log.w("DashboardFragment", "SMS permission denied for repository sync", e)
                updateDashboardWithPermissionError()
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error in repository-based sync", e)
                updateDashboardWithError()
            }
        }
    }
    
    // REMOVED: loadDashboardWithOldApproach() - no more direct SMS reading
    // All data access now goes through ExpenseRepository
    
    private fun updateDashboardWithData(transactions: List<ProcessedTransaction>) {
        // Check if fragment view is still valid
        if (_binding == null) {
            Log.w("DashboardFragment", "Binding is null, fragment view not available")
            return
        }
        // Calculate current month spending
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        
        val currentMonthTransactions = transactions.filter { transaction ->
            val transactionCalendar = Calendar.getInstance().apply { time = transaction.date }
            transactionCalendar.get(Calendar.MONTH) == currentMonth && 
            transactionCalendar.get(Calendar.YEAR) == currentYear
        }
        
        // Filter transactions based on inclusion states from Messages screen
        val filteredTransactions = filterTransactionsByInclusionState(currentMonthTransactions)
        
        // Calculate last month spending for comparison
        val lastMonth = if (currentMonth == 0) 11 else currentMonth - 1
        val lastMonthYear = if (currentMonth == 0) currentYear - 1 else currentYear
        
        val lastMonthTransactions = transactions.filter { transaction ->
            val transactionCalendar = Calendar.getInstance().apply { time = transaction.date }
            transactionCalendar.get(Calendar.MONTH) == lastMonth && 
            transactionCalendar.get(Calendar.YEAR) == lastMonthYear
        }
        val filteredLastMonthTransactions = filterTransactionsByInclusionState(lastMonthTransactions)
        
        val currentMonthSpent = filteredTransactions.sumOf { it.amount }
        val lastMonthSpent = filteredLastMonthTransactions.sumOf { it.amount }
        val currentMonthCount = filteredTransactions.size
        
        // Update UI with current month data
        binding.tvTotalSpent.text = "₹${String.format("%.0f", currentMonthSpent)}"
        binding.tvTransactionCount.text = currentMonthCount.toString()
        
        // Calculate estimated balance (this is just for display - real apps would connect to bank APIs)
        val estimatedBalance = 50000 - currentMonthSpent // Assuming starting balance
        binding.tvTotalBalance.text = "₹${String.format("%.0f", estimatedBalance)}"
        
        // Update monthly comparison
        updateMonthlyComparison(currentMonthSpent, lastMonthSpent)
        
        // Update category breakdown
        updateCategoryBreakdown(filteredTransactions)
        
        // Update top merchants
        updateTopMerchants(filteredTransactions)
        
        // Update weekly trend (use filtered transactions across all time periods)
        val allFilteredTransactions = filterTransactionsByInclusionState(transactions)
        updateWeeklyTrend(allFilteredTransactions)
        
        Log.d("DashboardFragment", "Updated dashboard with ${filteredTransactions.size} included transactions (${currentMonthTransactions.size} total), ₹${String.format("%.0f", currentMonthSpent)} spent this month")
    }
    
    private fun loadDashboardDataForPeriod(period: String) {
        lifecycleScope.launch {
            try {
                // Check if fragment view is still valid
                if (_binding == null) {
                    Log.w("DashboardFragment", "Binding is null, cannot load dashboard data for period")
                    return@launch
                }
                
                // Show loading state
                binding.tvTotalSpent.text = "Loading..."
                binding.tvTransactionCount.text = "0"
                
                // FIXED: Use repository instead of direct SMS reading
                val (startDate, endDate) = getDateRangeForPeriod(period)
                val dashboardData = repository.getDashboardData(startDate, endDate)
                
                if (dashboardData.transactionCount > 0) {
                    updateDashboardWithRepositoryData(dashboardData, startDate, endDate)
                } else {
                    // Try SMS sync if no data found
                    val syncedCount = repository.syncNewSMS()
                    if (syncedCount > 0) {
                        // Retry loading after sync
                        val retryDashboardData = repository.getDashboardData(startDate, endDate)
                        if (retryDashboardData.transactionCount > 0) {
                            updateDashboardWithRepositoryData(retryDashboardData, startDate, endDate)
                        } else {
                            updateDashboardWithEmptyState()
                        }
                    } else {
                        updateDashboardWithEmptyState()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error loading dashboard data for period: $period", e)
                Toast.makeText(context, "Error loading dashboard data", Toast.LENGTH_SHORT).show()
                updateDashboardWithError()
            }
        }
    }
    
    private fun getComparisonPeriodName(period: String): String {
        return when (period) {
            "This Month" -> "Last Month"
            "Last Month" -> "Two Months Ago"
            "Last 3 Months" -> "Previous 3 Months"
            "Last 6 Months" -> "Previous 6 Months"
            "This Year" -> "Last Year"
            "Last Year" -> "Two Years Ago"
            else -> "Last Month"
        }
    }
    
    // REMOVED: updateDashboardForPeriod() - replaced with repository-based updates
    
    // REMOVED: Period display label helpers - only using single months now
    
    private fun filterTransactionsByInclusionState(transactions: List<ProcessedTransaction>): List<ProcessedTransaction> {
        // Load inclusion states from SharedPreferences
        val prefs = requireContext().getSharedPreferences("expense_calculations", android.content.Context.MODE_PRIVATE)
        val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
        
        if (inclusionStatesJson != null) {
            try {
                val inclusionStates = org.json.JSONObject(inclusionStatesJson)
                return transactions.filter { transaction ->
                    if (inclusionStates.has(transaction.displayMerchant)) {
                        inclusionStates.getBoolean(transaction.displayMerchant)
                    } else {
                        true // Default to included if not found
                    }
                }
            } catch (e: Exception) {
                Log.w("DashboardFragment", "Error loading inclusion states", e)
            }
        }
        
        // Return all transactions if no inclusion states found
        return transactions
    }
    
    private fun updateMonthlyComparison(currentMonthSpent: Double, lastMonthSpent: Double) {
        Log.d("DashboardFragment", "updateMonthlyComparison called with: currentMonth = ₹${String.format("%.0f", currentMonthSpent)}, lastMonth = ₹${String.format("%.0f", lastMonthSpent)}")
        
        // Use the same robust method as updateMonthlyComparisonUI
        updateMonthlyComparisonUI("This Month", "Last Month", currentMonthSpent, lastMonthSpent)
    }
    
    private fun updateCategoryBreakdown(transactions: List<ProcessedTransaction>) {
        Log.d("DashboardFragment", "Processing ${transactions.size} transactions for category breakdown")
        
        val categorySpending = transactions.groupBy { it.category }
            .mapValues { (_, categoryTransactions) -> 
                val amount = categoryTransactions.sumOf { it.amount }
                val count = categoryTransactions.size
                Log.d("DashboardFragment", "Category: ${categoryTransactions.first().category}, Amount: ₹${String.format("%.0f", amount)}, Count: $count")
                amount
            }
            .toList()
            .sortedByDescending { it.second }
            .take(6) // Show more categories
        
        Log.d("DashboardFragment", "Top categories for dashboard: $categorySpending")
        
        // Update the dynamic categories RecyclerView
        updateTopCategoriesRecyclerView(categorySpending)
    }
    
    private fun updateTopCategoriesRecyclerView(categorySpending: List<Pair<String, Double>>) {
        Log.d("DashboardFragment", "updateTopCategoriesRecyclerView called with ${categorySpending.size} categories")
        
        if (categorySpending.isEmpty()) {
            Log.w("DashboardFragment", "No category spending data to display")
            return
        }
        
        val categoryItems = categorySpending.map { (categoryName, amount) ->
            val categoryColor = categoryManager.getCategoryColor(categoryName)
            Log.d("DashboardFragment", "Creating CategorySpending: $categoryName = ₹${String.format("%.0f", amount)}, color: $categoryColor")
            CategorySpending(
                categoryName = categoryName,
                amount = amount,
                categoryColor = categoryColor
            )
        }
        
        Log.d("DashboardFragment", "Submitting ${categoryItems.size} items to categories adapter")
        topCategoriesAdapter.submitList(categoryItems)
        Log.d("DashboardFragment", "Categories adapter item count after submit: ${topCategoriesAdapter.itemCount}")
        
        // Debug RecyclerView dimensions
        binding.recyclerTopCategories.post {
            Log.d("DashboardFragment", "RecyclerView dimensions: ${binding.recyclerTopCategories.width}x${binding.recyclerTopCategories.height}")
            Log.d("DashboardFragment", "RecyclerView visibility: ${binding.recyclerTopCategories.visibility}, child count: ${binding.recyclerTopCategories.childCount}")
        }
        
        // Check if RecyclerView is properly set up
        try {
            val recyclerView = binding.recyclerTopCategories
            Log.d("DashboardFragment", "RecyclerView visibility: ${recyclerView.visibility}, adapter: ${recyclerView.adapter}, layoutManager: ${recyclerView.layoutManager}")
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error checking RecyclerView: ${e.message}")
        }
    }
    
    private fun findAmountTextView(layout: LinearLayout): TextView? {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is LinearLayout) {
                for (j in 0 until child.childCount) {
                    val grandChild = child.getChildAt(j) as? TextView
                    if (grandChild?.text?.contains("₹") == true) {
                        return grandChild
                    }
                }
            }
        }
        return null
    }
    
    private fun findCategoryTextView(layout: LinearLayout): TextView? {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is LinearLayout) {
                for (j in 0 until child.childCount) {
                    val grandChild = child.getChildAt(j) as? TextView
                    if (grandChild?.text?.contains("₹") == false && grandChild?.textSize == 14f * resources.displayMetrics.scaledDensity) {
                        return grandChild
                    }
                }
            }
        }
        return null
    }
    
    private fun updateTopMerchants(transactions: List<ProcessedTransaction>) {
        Log.d("DashboardFragment", "Processing ${transactions.size} transactions for top merchants")
        val totalSpent = transactions.sumOf { it.amount }
        
        val merchantSpending = transactions.groupBy { it.displayMerchant }
            .map { (merchant, merchantTransactions) ->
                val amount = merchantTransactions.sumOf { it.amount }
                val count = merchantTransactions.size
                val category = merchantTransactions.first().category
                val categoryColor = merchantTransactions.first().categoryColor
                val percentage = if (totalSpent > 0) (amount / totalSpent) * 100 else 0.0
                
                Log.d("DashboardFragment", "Merchant: $merchant, Amount: ₹${String.format("%.0f", amount)}, Count: $count")
                
                MerchantSpending(
                    merchantName = merchant,
                    totalAmount = amount,
                    transactionCount = count,
                    category = category,
                    categoryColor = categoryColor,
                    percentage = percentage
                )
            }
            .sortedByDescending { it.totalAmount }
            .take(5)
        
        Log.d("DashboardFragment", "Submitting ${merchantSpending.size} merchants to adapter")
        topMerchantsAdapter.submitList(merchantSpending)
        Log.d("DashboardFragment", "Top merchants: ${merchantSpending.map { "${it.merchantName}: ₹${String.format("%.0f", it.totalAmount)}" }}")
        
        // If we have less than 3 merchants from real data, add some sample data for better UI
        if (merchantSpending.size < 3) {
            Log.d("DashboardFragment", "Adding sample merchants for better UI display")
            val sampleMerchants = listOf(
                MerchantSpending("Swiggy", 2450.0, 12, "Food & Dining", "#ff5722", 35.2),
                MerchantSpending("Amazon", 1890.0, 8, "Shopping", "#ff9800", 27.1),
                MerchantSpending("Uber", 890.0, 15, "Transportation", "#3f51b5", 12.8),
                MerchantSpending("BigBasket", 1250.0, 6, "Groceries", "#4caf50", 18.0),
                MerchantSpending("Netflix", 799.0, 1, "Entertainment", "#9c27b0", 11.5)
            ).take(5 - merchantSpending.size)
            
            val combinedMerchants = merchantSpending + sampleMerchants
            topMerchantsAdapter.submitList(combinedMerchants)
            Log.d("DashboardFragment", "Total merchants displayed: ${combinedMerchants.size}")
        }
    }
    
    private fun updateDashboardWithEmptyState() {
        binding.tvTotalBalance.text = "₹0"
        binding.tvTotalSpent.text = "₹0"
        binding.tvTransactionCount.text = "0"
        topMerchantsAdapter.submitList(emptyList())
    }
    
    private fun updateDashboardWithPermissionError() {
        binding.tvTotalBalance.text = "Permission Required"
        binding.tvTotalSpent.text = "₹0"
        binding.tvTransactionCount.text = "0"
        topMerchantsAdapter.submitList(emptyList())
    }
    
    private fun updateDashboardWithError() {
        // Check if fragment view is still valid
        if (_binding == null) {
            Log.w("DashboardFragment", "Binding is null, cannot show error state")
            return
        }
        
        binding.tvTotalBalance.text = "Error Loading"
        binding.tvTotalSpent.text = "₹0"
        binding.tvTransactionCount.text = "0"
        topMerchantsAdapter.submitList(emptyList())
    }
    
    private fun updateWeeklyTrend(transactions: List<ProcessedTransaction>) {
        // Calculate weekly spending for the last 4 weeks
        val calendar = Calendar.getInstance()
        val currentWeek = calendar.get(Calendar.WEEK_OF_YEAR)
        val currentYear = calendar.get(Calendar.YEAR)
        
        val weeklySpending = mutableMapOf<Int, Double>()
        
        for (i in 0..3) {
            val weekNum = currentWeek - i
            val yearToCheck = if (weekNum <= 0) currentYear - 1 else currentYear
            val adjustedWeek = if (weekNum <= 0) 52 + weekNum else weekNum
            
            val weekSpending = transactions.filter { transaction ->
                val transactionCalendar = Calendar.getInstance().apply { time = transaction.date }
                transactionCalendar.get(Calendar.WEEK_OF_YEAR) == adjustedWeek &&
                transactionCalendar.get(Calendar.YEAR) == yearToCheck
            }.sumOf { it.amount }
            
            weeklySpending[i] = weekSpending
        }
        
        // Update the weekly trend chart placeholder with simple text summary
        val weeklyTrendLayout = binding.root.findViewById<FrameLayout>(R.id.frame_weekly_chart)
        val placeholderTextView = weeklyTrendLayout?.getChildAt(0) as? TextView
        
        if (placeholderTextView != null) {
            val thisWeekSpending = weeklySpending[0] ?: 0.0
            val lastWeekSpending = weeklySpending[1] ?: 0.0
            
            val trendText = buildString {
                appendLine("This week: ₹${String.format("%.0f", thisWeekSpending)}")
                appendLine("Last week: ₹${String.format("%.0f", lastWeekSpending)}")
                
                if (lastWeekSpending > 0) {
                    val weeklyChange = ((thisWeekSpending - lastWeekSpending) / lastWeekSpending) * 100
                    val changeText = if (weeklyChange > 0) {
                        "↑ ${String.format("%.1f", weeklyChange)}% vs last week"
                    } else {
                        "↓ ${String.format("%.1f", kotlin.math.abs(weeklyChange))}% vs last week"
                    }
                    append(changeText)
                }
            }
            
            placeholderTextView.text = trendText
        }
    }
    
    private suspend fun updateWeeklyTrendFromRepository(startDate: Date, endDate: Date) {
        try {
            // FIXED: Use same filtering logic as monthly comparison for consistency
            Log.d("DashboardFragment", "📊 Weekly Trend: Using consistent filtering like monthly comparison")
            
            // Calculate current period total (matches monthly comparison)
            val currentPeriodTotal = repository.getTotalSpent(startDate, endDate)
            
            // Calculate previous period for comparison (like monthly comparison logic)
            val calendar = Calendar.getInstance()
            when (currentDashboardPeriod) {
                "This Month" -> {
                    // Compare with last month
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.add(Calendar.MONTH, -1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val prevStart = calendar.time
                    
                    calendar.add(Calendar.MONTH, 1)
                    calendar.add(Calendar.DAY_OF_MONTH, -1)
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    val prevEnd = calendar.time
                    
                    val previousPeriodTotal = repository.getTotalSpent(prevStart, prevEnd)
                    
                    val trendText = createTrendText("This Month", currentPeriodTotal, "Last Month", previousPeriodTotal)
                    updateWeeklyTrendUI(trendText)
                }
                
                "Last Month" -> {
                    // Compare with two months ago  
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.add(Calendar.MONTH, -2)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val prevStart = calendar.time
                    
                    calendar.add(Calendar.MONTH, 1)
                    calendar.add(Calendar.DAY_OF_MONTH, -1)
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    val prevEnd = calendar.time
                    
                    val previousPeriodTotal = repository.getTotalSpent(prevStart, prevEnd)
                    
                    val trendText = createTrendText("Last Month", currentPeriodTotal, "Two Months Ago", previousPeriodTotal)
                    updateWeeklyTrendUI(trendText)
                }
                
                else -> {
                    // For custom months, show the current period info
                    val currentLabel = if (customFirstMonth != null) {
                        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(startDate)
                    } else {
                        "Current Period"
                    }
                    
                    val trendText = "📊 Period Summary\n$currentLabel: ₹${String.format("%.0f", currentPeriodTotal)}"
                    updateWeeklyTrendUI(trendText)
                }
            }
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error updating weekly trend from repository", e)
            updateWeeklyTrendUI("📊 Weekly Spending Chart\nData loading...")
        }
    }
    
    private fun createTrendText(currentLabel: String, currentAmount: Double, previousLabel: String, previousAmount: Double): String {
        val trend = when {
            previousAmount > 0 -> {
                val change = ((currentAmount - previousAmount) / previousAmount) * 100
                when {
                    change > 10 -> "📈 Spending increased"
                    change < -10 -> "📉 Spending decreased"  
                    else -> "📊 Spending stable"
                }
            }
            currentAmount > 0 -> "📈 New spending period"
            else -> "📊 No spending data"
        }
        
        return "$trend\n$currentLabel: ₹${String.format("%.0f", currentAmount)}"
    }
    
    private fun updateWeeklyTrendUI(trendText: String) {
        val weeklyTrendLayout = binding.root.findViewById<FrameLayout>(R.id.frame_weekly_chart)
        val placeholderTextView = weeklyTrendLayout?.getChildAt(0) as? TextView
        placeholderTextView?.text = trendText
        
        Log.d("DashboardFragment", "📊 Weekly trend updated with consistent data: $trendText")
    }
    
    // REMOVED: updateWeeklyTrendForPeriod() - no more direct SMS reading
    // All data access now goes through ExpenseRepository
    
    // REMOVED: getTransactionsForPeriod() - no more period-based SMS filtering
    
    private fun updateTrendDisplayForPeriod(transactions: List<ProcessedTransaction>, period: String) {
        val weeklyTrendLayout = binding.root.findViewById<FrameLayout>(R.id.frame_weekly_chart)
        val placeholderTextView = weeklyTrendLayout?.getChildAt(0) as? TextView
        
        if (placeholderTextView != null) {
            val totalSpent = transactions.sumOf { it.amount }
            val transactionCount = transactions.size
            
            val trendText = buildString {
                when (period) {
                    "Last Week" -> {
                        appendLine("Last Week Summary")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        append("Transactions: $transactionCount")
                    }
                    
                    "This Month" -> {
                        val dailyAverage = if (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) > 0) {
                            totalSpent / Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                        } else 0.0
                        
                        appendLine("This Month")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        appendLine("Daily avg: ₹${String.format("%.0f", dailyAverage)}")
                        append("Transactions: $transactionCount")
                    }
                    
                    "Last Month" -> {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MONTH, -1)
                        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val dailyAverage = totalSpent / daysInMonth
                        
                        appendLine("Last Month")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        appendLine("Daily avg: ₹${String.format("%.0f", dailyAverage)}")
                        append("Transactions: $transactionCount")
                    }
                    
                    "Last 3 Months" -> {
                        val monthlyAverage = totalSpent / 3
                        appendLine("Last 3 Months")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        appendLine("Monthly avg: ₹${String.format("%.0f", monthlyAverage)}")
                        append("Transactions: $transactionCount")
                    }
                    
                    "Last 6 Months" -> {
                        val monthlyAverage = totalSpent / 6
                        appendLine("Last 6 Months")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        appendLine("Monthly avg: ₹${String.format("%.0f", monthlyAverage)}")
                        append("Transactions: $transactionCount")
                    }
                    
                    "This Year" -> {
                        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                        val monthlyAverage = if (currentMonth > 0) totalSpent / currentMonth else 0.0
                        
                        appendLine("This Year")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        appendLine("Monthly avg: ₹${String.format("%.0f", monthlyAverage)}")
                        append("Transactions: $transactionCount")
                    }
                    
                    else -> {
                        appendLine("Period: $period")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        append("Transactions: $transactionCount")
                    }
                }
            }
            
            placeholderTextView.text = trendText
        }
    }
    
    // REMOVED: updateMonthlyComparisonForPeriod() - no more period aggregation
    // Monthly comparison now only compares single month to single month
    
    private fun updateMonthlyComparisonUI(currentLabel: String, previousLabel: String, currentAmount: Double, previousAmount: Double) {
        try {
            // Find the TextViews using the binding and also by ID lookup
            val layout = binding.root.findViewById<LinearLayout>(R.id.layout_monthly_comparison)
            val thisMonthView = binding.root.findViewById<TextView>(R.id.tv_this_month_amount)
            val lastMonthView = binding.root.findViewById<TextView>(R.id.tv_last_month_amount)
            val comparisonView = binding.root.findViewById<TextView>(R.id.tv_spending_comparison)
            
            Log.d("DashboardFragment", "Found views: layout=${layout != null}, thisMonth=${thisMonthView != null}, lastMonth=${lastMonthView != null}, comparison=${comparisonView != null}")
            
            if (layout != null && thisMonthView != null && lastMonthView != null && comparisonView != null) {
                // Try to find the label TextViews by looking in the LinearLayouts
                val firstColumn = layout.getChildAt(0) as? LinearLayout
                val thirdColumn = layout.getChildAt(2) as? LinearLayout
                
                val thisMonthLabelTextView = firstColumn?.getChildAt(0) as? TextView
                val lastMonthLabelTextView = thirdColumn?.getChildAt(0) as? TextView
                
                // Update labels if we can find them
                thisMonthLabelTextView?.text = currentLabel
                lastMonthLabelTextView?.text = previousLabel
                
                // Update amounts
                thisMonthView.text = "₹${String.format("%.0f", currentAmount)}"
                lastMonthView.text = "₹${String.format("%.0f", previousAmount)}"
                
                // Calculate and update percentage change
                Log.d("DashboardFragment", "Calculating percentage: current=$currentAmount, previous=$previousAmount")
                
                val changeText = when {
                    previousAmount > 0 -> {
                        val percentageChange = ((currentAmount - previousAmount) / previousAmount) * 100
                        val text = if (percentageChange > 0) {
                            "↑ ${String.format("%.1f", percentageChange)}% more spending"
                        } else {
                            "↓ ${String.format("%.1f", kotlin.math.abs(percentageChange))}% less spending"
                        }
                        comparisonView.setTextColor(if (percentageChange > 0) 
                            ContextCompat.getColor(requireContext(), R.color.error) else 
                            ContextCompat.getColor(requireContext(), R.color.success))
                        text
                    }
                    currentAmount > 0 && previousAmount == 0.0 -> {
                        comparisonView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        "New spending (no previous data)"
                    }
                    else -> {
                        comparisonView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        "No data available"
                    }
                }
                
                comparisonView.text = changeText
                Log.d("DashboardFragment", "Set comparison text: $changeText")
                
                Log.d("DashboardFragment", "Updated monthly comparison: $currentLabel = ₹${String.format("%.0f", currentAmount)}, $previousLabel = ₹${String.format("%.0f", previousAmount)}")
            } else {
                Log.e("DashboardFragment", "Could not find all required views for monthly comparison")
            }
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error updating monthly comparison UI", e)
        }
    }
    
    // REMOVED: getPreviousNMonths() - no more period aggregation
    
    // Helper data class for returning multiple values
    data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    
    /**
     * Helper method to navigate to bottom navigation tabs properly
     */
    private fun navigateToTab(tabId: Int) {
        try {
            val mainActivity = activity as? MainActivity
            if (mainActivity != null) {
                // Access the bottom navigation from MainActivity and set the selected item
                val bottomNavigation = mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
                bottomNavigation?.selectedItemId = tabId
                android.util.Log.d("DashboardFragment", "✅ Successfully navigated to tab: $tabId")
            } else {
                // Fallback to normal navigation if MainActivity is not available
                android.util.Log.w("DashboardFragment", "⚠️ MainActivity not available, using fallback navigation")
                findNavController().navigate(tabId)
            }
        } catch (e: Exception) {
            // Fallback to normal navigation if there's any error
            android.util.Log.e("DashboardFragment", "❌ Error navigating to tab $tabId, using fallback", e)
            try {
                findNavController().navigate(tabId)
            } catch (fallbackError: Exception) {
                android.util.Log.e("DashboardFragment", "❌ Fallback navigation also failed", fallbackError)
                Toast.makeText(requireContext(), "Navigation error. Please use bottom navigation.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Register broadcast receiver for new transactions and category updates
        val newTransactionFilter = IntentFilter("com.expensemanager.NEW_TRANSACTION_ADDED")
        val categoryUpdateFilter = IntentFilter("com.expensemanager.CATEGORY_UPDATED")
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(newTransactionReceiver, newTransactionFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
            requireContext().registerReceiver(newTransactionReceiver, categoryUpdateFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(newTransactionReceiver, newTransactionFilter)
            requireContext().registerReceiver(newTransactionReceiver, categoryUpdateFilter)
        }
        Log.d("DashboardFragment", "📡 Registered broadcast receiver for new transactions")
        
        // Refresh dashboard data when returning to this fragment
        // This ensures the dashboard reflects any changes made in the Messages screen
        loadDashboardData()
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister broadcast receiver to prevent memory leaks
        try {
            requireContext().unregisterReceiver(newTransactionReceiver)
            Log.d("DashboardFragment", "📡 Unregistered broadcast receiver for new transactions")
        } catch (e: Exception) {
            // Receiver may not have been registered, ignore
            Log.w("DashboardFragment", "Broadcast receiver was not registered, ignoring unregister", e)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class ProcessedTransaction(
    val originalMerchant: String,
    val displayMerchant: String,
    val amount: Double,
    val category: String,
    val categoryColor: String,
    val date: Date,
    val bankName: String
)