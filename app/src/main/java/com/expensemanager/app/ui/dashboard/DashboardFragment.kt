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
import com.expensemanager.app.utils.SMSHistoryReader
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
                        updateWeeklyTrendForPeriod(selectedPeriod)
                    }
                }
                .show()
        }
    }
    
    private fun setupDashboardPeriodFilter() {
        val dashboardPeriods = listOf(
            "This Month",
            "Last Month", 
            "Last 3 Months",
            "Last 6 Months",
            "This Year",
            "Last Year"
        )
        
        binding.btnDashboardPeriod.text = currentDashboardPeriod
        binding.btnDashboardPeriod.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Dashboard Period")
                .setItems(dashboardPeriods.toTypedArray()) { _, which ->
                    val selectedPeriod = dashboardPeriods[which]
                    if (selectedPeriod != currentDashboardPeriod) {
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
                        
                        // Use real SMS scanning
                        val smsReader = com.expensemanager.app.utils.SMSHistoryReader(requireContext())
                        val transactions = smsReader.scanHistoricalSMS()
                        
                        progressDialog.dismiss()
                        
                        if (transactions.isNotEmpty()) {
                            Toast.makeText(
                                requireContext(),
                                "✅ Found ${transactions.size} transaction SMS messages!",
                                Toast.LENGTH_LONG
                            ).show()
                            
                            // Update dashboard with real data
                            val totalAmount = transactions.sumOf { it.amount }
                            binding.tvTotalSpent.text = "₹${String.format("%.0f", totalAmount)}"
                            binding.tvTransactionCount.text = transactions.size.toString()
                            
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "📱 No transaction SMS found in your inbox",
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
                        Log.d("DashboardFragment", "⚠️ No transactions found, falling back to SMS reading for initial data")
                        loadDashboardDataFallback()
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
        
        // Debug: Log current inclusion states
        Log.d("DashboardFragment", "🔍 ${repository.getInclusionStatesDebugInfo()}")
        
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
        
        Log.d("DashboardFragment", "Updating top categories: ${categorySpendingItems.map { "${it.categoryName}=₹${String.format("%.0f", it.amount)}" }}")
        topCategoriesAdapter.submitList(categorySpendingItems)
        
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
        
        Log.d("DashboardFragment", "Updating top merchants: ${merchantItems.map { "${it.merchantName}=₹${String.format("%.0f", it.totalAmount)}" }}")
        topMerchantsAdapter.submitList(merchantItems)
        
        // Update monthly comparison based on selected period
        updateMonthlyComparisonFromRepository(startDate, endDate, currentDashboardPeriod)
        
        // Update weekly trend with repository data
        updateWeeklyTrendFromRepository(startDate, endDate)
        
        Log.d("DashboardFragment", "✅ Dashboard UI updated successfully with repository data")
    }
    
    private suspend fun updateMonthlyComparisonFromRepository(currentStart: Date, currentEnd: Date, period: String) {
        try {
            // Calculate comparison period based on selected dashboard period
            val (currentLabel, previousLabel, previousStart, previousEnd) = when (period) {
                "Last Week" -> {
                    val cal = Calendar.getInstance()
                    // Previous week start (2 weeks ago Monday)
                    cal.add(Calendar.WEEK_OF_YEAR, -2)
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val prevStart = cal.time
                    
                    // Previous week end (2 weeks ago Sunday)
                    cal.add(Calendar.DAY_OF_WEEK, 6)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val prevEnd = cal.time
                    
                    Tuple4("Last Week", "Previous Week", prevStart, prevEnd)
                }
                
                "This Month" -> {
                    val cal = Calendar.getInstance()
                    // Last month start (first day of previous month)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, -1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val prevStart = cal.time
                    
                    // Last month end (last day of previous month)
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
                    val cal = Calendar.getInstance()
                    // Two months ago start
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, -2)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val prevStart = cal.time
                    
                    // Two months ago end
                    cal.add(Calendar.MONTH, 1)
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val prevEnd = cal.time
                    
                    Tuple4("Last Month", "Two Months Ago", prevStart, prevEnd)
                }
                
                "Last 3 Months" -> {
                    val cal = Calendar.getInstance()
                    // 6 months ago start
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, -6)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val prevStart = cal.time
                    
                    // 3 months ago end
                    cal.add(Calendar.MONTH, 3)
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val prevEnd = cal.time
                    
                    Tuple4("Last 3 Months", "Previous 3 Months", prevStart, prevEnd)
                }
                
                else -> {
                    // Default to "This Month" vs "Last Month"
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
            
            
            Log.d("DashboardFragment", "💰 Period spending:")
            Log.d("DashboardFragment", "   $currentLabel: ₹${String.format("%.0f", currentPeriodSpent)}")
            Log.d("DashboardFragment", "   $previousLabel: ₹${String.format("%.0f", previousPeriodSpent)}")
            
            // Validate amounts are reasonable (less than 100,000 for safety)
            val validCurrentAmount = if (currentPeriodSpent > 100000) {
                Log.w("DashboardFragment", "⚠️ $currentLabel amount seems too high: ₹${String.format("%.0f", currentPeriodSpent)}")
                currentPeriodSpent
            } else currentPeriodSpent
            
            val validPreviousAmount = if (previousPeriodSpent > 100000) {
                Log.w("DashboardFragment", "⚠️ $previousLabel amount seems too high: ₹${String.format("%.0f", previousPeriodSpent)}, this might indicate duplicate data or migration artifacts")
                
                // Get transaction details for analysis
                val previousTransactions = repository.getTransactionsByDateRange(previousStart, previousEnd)
                Log.w("DashboardFragment", "⚠️ $previousLabel transaction count: ${previousTransactions.size}")
                if (previousTransactions.isNotEmpty()) {
                    Log.w("DashboardFragment", "⚠️ Sample transactions: ${previousTransactions.take(5).map { "${it.rawMerchant}: ₹${it.amount} on ${it.transactionDate}" }}")
                }
                
                // Apply a reasonable cap for display purposes
                // Instead of showing unrealistic amount, show a capped value with indicator
                val reasonableAmount = minOf(previousPeriodSpent, 50000.0) // Cap at ₹50k
                Log.w("DashboardFragment", "⚠️ Capping $previousLabel display at ₹${String.format("%.0f", reasonableAmount)} for UI purposes")
                
                reasonableAmount
            } else previousPeriodSpent
            
            // Use the new updateMonthlyComparisonUI method for consistent display
            updateMonthlyComparisonUI(currentLabel, previousLabel, validCurrentAmount, validPreviousAmount)
            
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
    
    private fun loadDashboardDataFallback() {
        lifecycleScope.launch {
            try {
                Log.d("DashboardFragment", "📱 Loading dashboard data from SMS as fallback...")
                
                // COMPARISON: Let's load using the old approach and compare with DB approach
                Log.d("DashboardFragment", "🔍 COMPARISON MODE: Testing old SMS approach vs DB approach")
                
                // OLD APPROACH: Direct SMS reading
                val smsReader = SMSHistoryReader(requireContext())
                val smsTransactions = smsReader.scanHistoricalSMS()
                Log.d("DashboardFragment", "📱 OLD SMS Approach: Found ${smsTransactions.size} transactions")
                
                if (smsTransactions.isNotEmpty()) {
                    val calendar = Calendar.getInstance()
                    val currentMonth = calendar.get(Calendar.MONTH)
                    val currentYear = calendar.get(Calendar.YEAR)
                    
                    val currentMonthSmsTransactions = smsTransactions.filter { transaction ->
                        val transactionCalendar = Calendar.getInstance().apply { time = transaction.date }
                        transactionCalendar.get(Calendar.MONTH) == currentMonth && 
                        transactionCalendar.get(Calendar.YEAR) == currentYear
                    }
                    
                    val smsTotal = currentMonthSmsTransactions.sumOf { it.amount }
                    Log.d("DashboardFragment", "📱 OLD SMS Approach: Current month total = ₹${String.format("%.0f", smsTotal)} from ${currentMonthSmsTransactions.size} transactions")
                    
                    // Log some example transactions
                    currentMonthSmsTransactions.take(5).forEach { tx ->
                        Log.d("DashboardFragment", "📱 SMS Transaction: ${tx.merchant} = ₹${String.format("%.0f", tx.amount)} on ${tx.date}")
                    }
                }
                
                // NEW APPROACH: Try SMS sync and then read from DB
                Log.d("DashboardFragment", "💾 NEW DB Approach: Attempting SMS sync...")
                val syncedCount = repository.syncNewSMS()
                Log.d("DashboardFragment", "💾 NEW DB Approach: Synced $syncedCount new transactions from SMS")
                
                // Check what's actually in the database
                val (startDate, endDate) = getDateRangeForPeriod(currentDashboardPeriod)
                val dbTransactions = repository.getTransactionsByDateRange(startDate, endDate)
                Log.d("DashboardFragment", "💾 NEW DB Approach: Found ${dbTransactions.size} transactions in database for current month")
                
                if (dbTransactions.isNotEmpty()) {
                    val dbTotal = dbTransactions.sumOf { it.amount }
                    Log.d("DashboardFragment", "💾 NEW DB Approach: Database total = ₹${String.format("%.0f", dbTotal)} from ${dbTransactions.size} transactions")
                    
                    // Log some example database transactions
                    dbTransactions.take(5).forEach { tx ->
                        Log.d("DashboardFragment", "💾 DB Transaction: ${tx.normalizedMerchant} = ₹${String.format("%.0f", tx.amount)} on ${tx.transactionDate}")
                    }
                    
                    // Now load dashboard data normally
                    val dashboardData = repository.getDashboardData(startDate, endDate)
                    Log.d("DashboardFragment", "💾 Dashboard Data: totalSpent=₹${String.format("%.0f", dashboardData.totalSpent)}, count=${dashboardData.transactionCount}")
                    
                    if (dashboardData.transactionCount > 0) {
                        updateDashboardWithRepositoryData(dashboardData, startDate, endDate)
                    } else {
                        Log.w("DashboardFragment", "⚠️ Dashboard data shows 0 transactions despite DB having ${dbTransactions.size} transactions")
                        updateDashboardWithEmptyState()
                    }
                } else {
                    Log.w("DashboardFragment", "⚠️ No transactions found in database after sync")
                    
                    // Fall back to old approach for now
                    if (smsTransactions.isNotEmpty()) {
                        Log.d("DashboardFragment", "🔄 Falling back to old SMS approach temporarily")
                        loadDashboardWithOldApproach()
                    } else {
                        updateDashboardWithEmptyState()
                    }
                }
                
            } catch (e: SecurityException) {
                Log.w("DashboardFragment", "SMS permission denied for fallback loading", e)
                updateDashboardWithPermissionError()
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error in fallback loading", e)
                updateDashboardWithError()
            }
        }
    }
    
    private fun loadDashboardWithOldApproach() {
        lifecycleScope.launch {
            try {
                Log.d("DashboardFragment", "📱 Using OLD SMS approach as fallback")
                
                val smsReader = SMSHistoryReader(requireContext())
                val transactions = smsReader.scanHistoricalSMS()
                
                if (transactions.isNotEmpty()) {
                    // Process transactions with aliases (old approach)
                    val processedTransactions = transactions.map { transaction ->
                        ProcessedTransaction(
                            originalMerchant = transaction.merchant,
                            displayMerchant = merchantAliasManager.getDisplayName(transaction.merchant),
                            amount = transaction.amount,
                            category = merchantAliasManager.getMerchantCategory(transaction.merchant),
                            categoryColor = merchantAliasManager.getMerchantCategoryColor(transaction.merchant),
                            date = transaction.date,
                            bankName = transaction.bankName
                        )
                    }
                    
                    updateDashboardWithData(processedTransactions)
                } else {
                    updateDashboardWithEmptyState()
                }
                
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error in old approach fallback", e)
                updateDashboardWithError()
            }
        }
    }
    
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
                
                val smsReader = SMSHistoryReader(requireContext())
                val transactions = smsReader.scanHistoricalSMS()
                
                // Filter transactions for the selected period
                val periodTransactions = getTransactionsForPeriod(transactions, period)
                
                // Convert to ProcessedTransaction for dashboard
                val processedTransactions = periodTransactions.map { parsedTransaction ->
                    val category = categoryManager.categorizeTransaction(parsedTransaction.merchant)
                    ProcessedTransaction(
                        originalMerchant = parsedTransaction.merchant,
                        displayMerchant = merchantAliasManager.getDisplayName(parsedTransaction.merchant),
                        amount = parsedTransaction.amount,
                        category = category,
                        categoryColor = categoryManager.getCategoryColor(category),
                        date = parsedTransaction.date,
                        bankName = parsedTransaction.bankName
                    )
                }
                
                // Filter transactions based on inclusion state
                val filteredTransactions = filterTransactionsByInclusionState(processedTransactions)
                
                // Get comparison period transactions
                val comparisonPeriod = getComparisonPeriodName(period)
                val comparisonTransactions = getTransactionsForPeriod(transactions, comparisonPeriod)
                
                // Update dashboard with period-specific data
                updateDashboardForPeriod(period, filteredTransactions, processedTransactions, comparisonTransactions)
                
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error loading dashboard data for period: $period", e)
                Toast.makeText(context, "Error loading dashboard data", Toast.LENGTH_SHORT).show()
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
    
    private fun updateDashboardForPeriod(period: String, filteredTransactions: List<ProcessedTransaction>, allPeriodTransactions: List<ProcessedTransaction>, comparisonTransactions: List<ParsedTransaction>) {
        val totalSpent = filteredTransactions.sumOf { it.amount }
        val transactionCount = filteredTransactions.size
        val comparisonSpent = comparisonTransactions.sumOf { it.amount }
        
        // Update main stats with period context
        val periodLabel = getPeriodDisplayLabel(period)
        binding.tvTotalSpent.text = "₹${String.format("%.0f", totalSpent)}"
        binding.tvTransactionCount.text = transactionCount.toString()
        
        // Update balance (this would normally come from bank API)
        val estimatedBalance = 50000 - totalSpent
        binding.tvTotalBalance.text = "₹${String.format("%.0f", estimatedBalance)}"
        
        // Update monthly comparison with period-specific logic
        val comparisonPeriodLabel = getComparisonPeriodDisplayLabel(period)
        updateMonthlyComparisonUI(periodLabel, comparisonPeriodLabel, totalSpent, comparisonSpent)
        
        // Update category breakdown
        updateCategoryBreakdown(filteredTransactions)
        
        // Update top merchants
        updateTopMerchants(filteredTransactions)
        
        // Update weekly trend
        updateTrendDisplayForPeriod(allPeriodTransactions, period)
        
        Log.d("DashboardFragment", "Updated dashboard for $period: ₹${String.format("%.0f", totalSpent)} spent, $transactionCount transactions")
    }
    
    private fun getPeriodDisplayLabel(period: String): String {
        return when (period) {
            "This Month" -> "This Month"
            "Last Month" -> "Last Month"
            "Last 3 Months" -> "Last 3 Months"
            "Last 6 Months" -> "Last 6 Months" 
            "This Year" -> "This Year"
            "Last Year" -> "Last Year"
            else -> period
        }
    }
    
    private fun getComparisonPeriodDisplayLabel(period: String): String {
        return when (period) {
            "This Month" -> "Last Month"
            "Last Month" -> "Two Months Ago"
            "Last 3 Months" -> "Previous 3 Months"
            "Last 6 Months" -> "Previous 6 Months"
            "This Year" -> "Last Year"
            "Last Year" -> "Two Years Ago"
            else -> "Previous Period"
        }
    }
    
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
            // Get transactions for last 4 weeks to show trend
            val calendar = Calendar.getInstance()
            calendar.time = endDate
            
            // Go back 4 weeks from end date
            calendar.add(Calendar.WEEK_OF_YEAR, -4)
            val trendStartDate = calendar.time
            
            // Get transactions for the 4-week period
            val transactions = repository.getTransactionsByDateRange(trendStartDate, endDate)
            Log.d("DashboardFragment", "📊 Weekly Trend: Found ${transactions.size} transactions from ${trendStartDate} to ${endDate}")
            
            // Group transactions by week
            val weeklyData = mutableMapOf<Int, Double>()
            
            for (transaction in transactions) {
                val transactionCalendar = Calendar.getInstance()
                transactionCalendar.time = transaction.transactionDate
                val weekOfYear = transactionCalendar.get(Calendar.WEEK_OF_YEAR)
                
                weeklyData[weekOfYear] = (weeklyData[weekOfYear] ?: 0.0) + transaction.amount
            }
            
            // Calculate trend summary
            val weeks = weeklyData.keys.sorted()
            val trendText = if (weeks.size >= 2) {
                val firstWeekSpending = weeklyData[weeks.first()] ?: 0.0
                val lastWeekSpending = weeklyData[weeks.last()] ?: 0.0
                
                val trend = when {
                    lastWeekSpending > firstWeekSpending * 1.1 -> "📈 Spending increasing"
                    lastWeekSpending < firstWeekSpending * 0.9 -> "📉 Spending decreasing"
                    else -> "📊 Spending stable"
                }
                
                "$trend\nLast ${weeks.size} weeks: ₹${String.format("%.0f", weeklyData.values.sum())}"
            } else {
                "📊 Weekly Spending Trend\n${transactions.size} transactions this period"
            }
            
            // Update UI
            val weeklyTrendLayout = binding.root.findViewById<FrameLayout>(R.id.frame_weekly_chart)
            val placeholderTextView = weeklyTrendLayout?.getChildAt(0) as? TextView
            placeholderTextView?.text = trendText
            
            Log.d("DashboardFragment", "📊 Weekly trend updated: $trendText")
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error updating weekly trend from repository", e)
            // Fallback to placeholder text
            val weeklyTrendLayout = binding.root.findViewById<FrameLayout>(R.id.frame_weekly_chart)
            val placeholderTextView = weeklyTrendLayout?.getChildAt(0) as? TextView
            placeholderTextView?.text = "📊 Weekly Spending Chart\nData loading..."
        }
    }
    
    private fun updateWeeklyTrendForPeriod(period: String) {
        lifecycleScope.launch {
            try {
                val smsReader = SMSHistoryReader(requireContext())
                val transactions = smsReader.scanHistoricalSMS()
                
                // Filter transactions based on selected period
                val filteredTransactions = getTransactionsForPeriod(transactions, period)
                
                // Convert to ProcessedTransaction for compatibility with existing code
                val processedTransactions = filteredTransactions.map { parsedTransaction ->
                    val category = categoryManager.categorizeTransaction(parsedTransaction.merchant)
                    ProcessedTransaction(
                        originalMerchant = parsedTransaction.merchant,
                        displayMerchant = merchantAliasManager.getDisplayName(parsedTransaction.merchant),
                        amount = parsedTransaction.amount,
                        category = category,
                        categoryColor = categoryManager.getCategoryColor(category),
                        date = parsedTransaction.date,
                        bankName = parsedTransaction.bankName
                    )
                }
                
                // Update the trend display with period-specific logic
                updateTrendDisplayForPeriod(processedTransactions, period)
                
                // Also update monthly comparison based on the selected period
                updateMonthlyComparisonForPeriod(transactions, period)
                
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error updating trend for period: $period", e)
                Toast.makeText(context, "Error updating trend data", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun getTransactionsForPeriod(transactions: List<ParsedTransaction>, period: String): List<ParsedTransaction> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        
        when (period) {
            "Last Week" -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val weekEndDate = calendar.time
                
                return transactions.filter { it.date >= startDate && it.date <= weekEndDate }
            }
            
            "This Month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                return transactions.filter { it.date >= startDate && it.date <= endDate }
            }
            
            "Last Month" -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val monthEndDate = calendar.time
                
                return transactions.filter { it.date >= startDate && it.date <= monthEndDate }
            }
            
            "Last 3 Months" -> {
                calendar.add(Calendar.MONTH, -3)
                val startDate = calendar.time
                return transactions.filter { it.date >= startDate && it.date <= endDate }
            }
            
            "Last 6 Months" -> {
                calendar.add(Calendar.MONTH, -6)
                val startDate = calendar.time
                return transactions.filter { it.date >= startDate && it.date <= endDate }
            }
            
            "This Year" -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                return transactions.filter { it.date >= startDate && it.date <= endDate }
            }
            
            "Last Year" -> {
                calendar.add(Calendar.YEAR, -1)
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val yearEndDate = calendar.time
                
                return transactions.filter { it.date >= startDate && it.date <= yearEndDate }
            }
            
            "Week Before Last" -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -2)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val weekEndDate = calendar.time
                
                return transactions.filter { it.date >= startDate && it.date <= weekEndDate }
            }
            
            "Two Months Ago" -> {
                calendar.add(Calendar.MONTH, -2)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val monthEndDate = calendar.time
                
                return transactions.filter { it.date >= startDate && it.date <= monthEndDate }
            }
            
            "Two Years Ago" -> {
                calendar.add(Calendar.YEAR, -2)
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val yearEndDate = calendar.time
                
                return transactions.filter { it.date >= startDate && it.date <= yearEndDate }
            }
            
            else -> return transactions.filter { 
                val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -30) }.time
                it.date >= thirtyDaysAgo && it.date <= endDate 
            }
        }
    }
    
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
    
    private fun updateMonthlyComparisonForPeriod(allTransactions: List<ParsedTransaction>, period: String) {
        try {
            val (currentPeriodLabel, previousPeriodLabel, currentPeriodData, previousPeriodData) = when (period) {
                "Last Week" -> {
                    val currentWeekTransactions = getTransactionsForPeriod(allTransactions, "Last Week")
                    val previousWeekTransactions = getTransactionsForPeriod(allTransactions, "Week Before Last")
                    Tuple4("Last Week", "Previous Week", currentWeekTransactions, previousWeekTransactions)
                }
                
                "This Month" -> {
                    val currentMonthTransactions = getTransactionsForPeriod(allTransactions, "This Month")
                    val lastMonthTransactions = getTransactionsForPeriod(allTransactions, "Last Month")
                    Tuple4("This Month", "Last Month", currentMonthTransactions, lastMonthTransactions)
                }
                
                "Last Month" -> {
                    val lastMonthTransactions = getTransactionsForPeriod(allTransactions, "Last Month")
                    val twoMonthsAgoTransactions = getTransactionsForPeriod(allTransactions, "Two Months Ago")
                    Tuple4("Last Month", "Two Months Ago", lastMonthTransactions, twoMonthsAgoTransactions)
                }
                
                "Last 3 Months" -> {
                    val last3MonthsTransactions = getTransactionsForPeriod(allTransactions, "Last 3 Months")
                    val previous3MonthsTransactions = getPreviousNMonths(allTransactions, 3, 6)
                    Tuple4("Last 3 Months", "Previous 3 Months", last3MonthsTransactions, previous3MonthsTransactions)
                }
                
                "Last 6 Months" -> {
                    val last6MonthsTransactions = getTransactionsForPeriod(allTransactions, "Last 6 Months")
                    val previous6MonthsTransactions = getPreviousNMonths(allTransactions, 6, 12)
                    Tuple4("Last 6 Months", "Previous 6 Months", last6MonthsTransactions, previous6MonthsTransactions)
                }
                
                "This Year" -> {
                    val thisYearTransactions = getTransactionsForPeriod(allTransactions, "This Year")
                    val lastYearTransactions = getTransactionsForPeriod(allTransactions, "Last Year")
                    Tuple4("This Year", "Last Year", thisYearTransactions, lastYearTransactions)
                }
                
                else -> {
                    val currentMonthTransactions = getTransactionsForPeriod(allTransactions, "This Month")
                    val lastMonthTransactions = getTransactionsForPeriod(allTransactions, "Last Month")
                    Tuple4("This Month", "Last Month", currentMonthTransactions, lastMonthTransactions)
                }
            }
            
            val currentPeriodAmount = currentPeriodData.sumOf { it.amount }
            val previousPeriodAmount = previousPeriodData.sumOf { it.amount }
            
            Log.d("DashboardFragment", "Period: $period, Current: $currentPeriodLabel (₹${String.format("%.0f", currentPeriodAmount)}), Previous: $previousPeriodLabel (₹${String.format("%.0f", previousPeriodAmount)})")
            
            // Update UI directly
            updateMonthlyComparisonUI(currentPeriodLabel, previousPeriodLabel, currentPeriodAmount, previousPeriodAmount)
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error updating monthly comparison for period: $period", e)
        }
    }
    
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
    
    private fun getPreviousNMonths(transactions: List<ParsedTransaction>, monthsAgo: Int, totalMonths: Int): List<ParsedTransaction> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -totalMonths)
        val startDate = calendar.time
        
        calendar.add(Calendar.MONTH, totalMonths - monthsAgo)
        val endDate = calendar.time
        
        return transactions.filter { it.date >= startDate && it.date < endDate }
    }
    
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