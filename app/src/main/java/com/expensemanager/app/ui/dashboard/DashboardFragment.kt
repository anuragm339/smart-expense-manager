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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import com.expensemanager.app.R
import com.expensemanager.app.MainActivity
import com.expensemanager.app.databinding.FragmentDashboardBinding
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.data.repository.DashboardData
import com.expensemanager.app.ui.dashboard.MerchantSpending
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
// UPDATED: Import unified services for consistent SMS parsing and filtering
import com.expensemanager.app.models.ParsedTransaction
import com.expensemanager.app.services.TransactionParsingService
import com.expensemanager.app.services.TransactionFilterService
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import com.expensemanager.app.domain.usecase.transaction.AddTransactionUseCase
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {
    
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    
    // Hilt-injected ViewModel
    private val viewModel: DashboardViewModel by viewModels()
    
    // Hilt-injected use case for adding manual transactions
    @Inject
    lateinit var addTransactionUseCase: AddTransactionUseCase
    
    // Hilt-injected unified services for consistent parsing and filtering
    @Inject
    lateinit var transactionParsingService: TransactionParsingService
    
    @Inject
    lateinit var transactionFilterService: TransactionFilterService
    
    // Keep existing repository for parallel approach during migration
    private lateinit var repository: ExpenseRepository
    private lateinit var categoryManager: CategoryManager
    private lateinit var merchantAliasManager: MerchantAliasManager
    private var currentDashboardPeriod = "This Month" // Default time period for entire dashboard
    
    // Custom month selection variables
    private var customFirstMonth: Pair<Int, Int>? = null  // (month, year)
    private var customSecondMonth: Pair<Int, Int>? = null // (month, year)
    
    // Broadcast receiver for new transaction, category update, and inclusion state change notifications
    private val newTransactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.expensemanager.NEW_TRANSACTION_ADDED" -> {
                    val merchant = intent.getStringExtra("merchant") ?: "Unknown"
                    val amount = intent.getDoubleExtra("amount", 0.0)
                    Log.d("DashboardFragment", "New transaction broadcast: $merchant - ₹${String.format("%.0f", amount)}")
                    
                    // Refresh dashboard data on the main thread
                    lifecycleScope.launch {
                        try {
                            loadDashboardData()
                            
                            // Show a brief toast to indicate refresh
                            android.widget.Toast.makeText(
                                requireContext(),
                                "New transaction added - Dashboard updated",
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
                    Log.d("DashboardFragment", "Category update broadcast: $merchant → $category")
                    
                    // Refresh dashboard data on the main thread
                    lifecycleScope.launch {
                        try {
                            Log.d("DashboardFragment", "[REFRESH] Refreshing dashboard due to category update")
                            loadDashboardData()
                            
                        } catch (e: Exception) {
                            Log.e("DashboardFragment", "Error refreshing dashboard after category update", e)
                        }
                    }
                }
                
                "com.expensemanager.INCLUSION_STATE_CHANGED" -> {
                    val includedCount = intent.getIntExtra("included_count", 0)
                    val totalAmount = intent.getDoubleExtra("total_amount", 0.0)
                    Log.d("DashboardFragment", "Inclusion state change: $includedCount transactions, ₹${String.format("%.0f", totalAmount)} total")
                    
                    // FIXED: Refresh dashboard to reflect inclusion/exclusion changes from Messages screen
                    lifecycleScope.launch {
                        try {
                            loadDashboardData()
                            
                        } catch (e: Exception) {
                            Log.e("DashboardFragment", "Error refreshing dashboard after inclusion state change", e)
                        }
                    }
                }
                
                "com.expensemanager.MERCHANT_CATEGORY_CHANGED" -> {
                    val merchantName = intent.getStringExtra("merchant_name") ?: "Unknown"
                    val displayName = intent.getStringExtra("display_name") ?: "Unknown"
                    val newCategory = intent.getStringExtra("new_category") ?: "Unknown"
                    Log.d("DashboardFragment", "Merchant category change: '$merchantName' -> '$newCategory'")
                    
                    // Refresh dashboard to reflect category changes in Top Merchants section
                    lifecycleScope.launch {
                        try {
                            loadDashboardData()
                            
                            // Show a brief toast to indicate refresh
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Category updated for '$displayName' - Dashboard refreshed",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            
                        } catch (e: Exception) {
                            Log.e("DashboardFragment", "Error refreshing dashboard after merchant category change", e)
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
        
        // Force background color to override any theme issues
        val lightGrayColor = ContextCompat.getColor(requireContext(), R.color.background_light)
        view.setBackgroundColor(lightGrayColor)
        binding.root.setBackgroundColor(lightGrayColor)
        
        // Initialize existing components (parallel approach during migration)
        repository = ExpenseRepository.getInstance(requireContext())
        categoryManager = CategoryManager(requireContext())
        merchantAliasManager = MerchantAliasManager(requireContext())
        
        setupUI()
        setupClickListeners()
        
        // Add ViewModel state observation
        observeUIState()
        
        // Keep existing data loading for now (parallel approach)
        loadDashboardData()
    }
    
    /**
     * Observe ViewModel UI state following InsightsFragment pattern
     */
    private fun observeUIState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                
                // Observe main UI state
                launch {
                    viewModel.uiState.collect { state ->
                        Log.d("DashboardFragment", "ViewModel UI State updated: loading=${state.isAnyLoading}, data=${state.dashboardData != null}, error=${state.hasError}")
                        updateUIFromViewModel(state)
                    }
                }
            }
        }
    }
    
    /**
     * Update UI based on ViewModel state changes
     */
    private fun updateUIFromViewModel(state: DashboardUIState) {
        when {
            state.isInitialLoading -> {
                showLoadingStateFromViewModel()
            }
            
            state.shouldShowError -> {
                showErrorStateFromViewModel(state.error)
            }
            
            state.shouldShowContent -> {
                showContentFromViewModel(state)
            }
            
            state.shouldShowEmptyState -> {
                showEmptyStateFromViewModel()
            }
        }
        
        // Handle SMS sync status
        if (state.syncedTransactionsCount > 0) {
            Toast.makeText(
                requireContext(),
                "Synced ${state.syncedTransactionsCount} new transactions from SMS!",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Show loading state from ViewModel
     */
    private fun showLoadingStateFromViewModel() {
        binding.tvTotalBalance.text = "Loading from ViewModel..."
        binding.tvTotalSpent.text = "Loading..."
        binding.tvTransactionCount.text = "0"
        Log.d("DashboardFragment", "Showing ViewModel loading state")
    }
    
    /**
     * Show error state from ViewModel
     */
    private fun showErrorStateFromViewModel(error: String?) {
        binding.tvTotalBalance.text = "ViewModel Error"
        binding.tvTotalSpent.text = "₹0"
        binding.tvTransactionCount.text = "0"
        
        error?.let {
            Toast.makeText(requireContext(), "ViewModel: $it", Toast.LENGTH_LONG).show()
        }
        
        Log.d("DashboardFragment", "Showing ViewModel error state: $error")
    }
    
    /**
     * Show content from ViewModel
     */
    private fun showContentFromViewModel(state: DashboardUIState) {
        val dashboardData = state.dashboardData ?: return
        
        // Update spending summary from ViewModel
        binding.tvTotalSpent.text = "₹${String.format("%.0f", state.totalSpent)}"
        
        // Calculate and update savings (salary-based balance)
        val savings = if (state.dashboardPeriod == "This Month" && state.hasSalaryData) {
            state.monthlyBalance
        } else {
            state.totalBalance
        }
        
        // MONTHLY BALANCE FEATURE: Show salary-based balance for "This Month", regular balance for other periods
        val balanceToShow = if (state.dashboardPeriod == "This Month" && state.hasSalaryData) {
            state.monthlyBalance
        } else {
            state.totalBalance
        }
        
        binding.tvTotalBalance.text = if (balanceToShow >= 0) {
            "₹${String.format("%.0f", balanceToShow)}"
        } else {
            "-₹${String.format("%.0f", kotlin.math.abs(balanceToShow))}"
        }
        
        // Add salary info logging for debugging
        if (state.dashboardPeriod == "This Month" && state.hasSalaryData) {
        }
        
        binding.tvTransactionCount.text = "${state.transactionCount}"
        
        // Update top categories from ViewModel data
        updateTopCategoriesFromViewModel(dashboardData)
        
        // Update top merchants from ViewModel data
        updateTopMerchantsFromViewModel(dashboardData)
        
        // Update monthly comparison from ViewModel
        updateMonthlyComparisonFromViewModel(state.monthlyComparison)
        
        // CRITICAL FIX: Update weekly trend chart with real data
        // Calculate date range for current period and update weekly trend chart
        lifecycleScope.launch {
            val (startDate, endDate) = getDateRangeForPeriod(state.dashboardPeriod)
            updateWeeklyTrendWithRealData(startDate, endDate)
            Log.d("DashboardFragment", "[WEEKLY_CHART_FIX] Updated weekly trend chart from ViewModel for period: ${state.dashboardPeriod}")
        }
        
    }
    
    /**
     * Show empty state from ViewModel
     */
    private fun showEmptyStateFromViewModel() {
        binding.tvTotalBalance.text = "₹0"
        binding.tvTotalSpent.text = "₹0"
        binding.tvTransactionCount.text = "0"
        updateTopMerchantsTable(emptyList())
        updateTopCategoriesTable(emptyList())
        Log.d("DashboardFragment", "Showing ViewModel empty state")
    }
    
    /**
     * Update top categories from ViewModel data
     */
    private fun updateTopCategoriesFromViewModel(dashboardData: DashboardData) {
        val categorySpendingItems = dashboardData.topCategories.map { categoryResult ->
            CategorySpending(
                categoryName = categoryResult.category_name,
                amount = categoryResult.total_amount,
                categoryColor = categoryResult.color,
                count = categoryResult.transaction_count
            )
        }
        
        // Ensure consistent display - always show at least 4 categories (2x2 grid)
        val finalCategoryItems = ensureMinimumCategories(categorySpendingItems, 4)
        
        Log.d("DashboardFragment", "ViewModel: Updating top categories: ${finalCategoryItems.map { "${it.categoryName}=₹${String.format("%.0f", it.amount)}" }}")
        updateTopCategoriesTable(finalCategoryItems)
    }
    
    /**
     * Update top merchants from ViewModel data
     */
    private fun updateTopMerchantsFromViewModel(dashboardData: DashboardData) {
        // CRITICAL FIX: Calculate total from merchants data like Top Categories do
        // Don't trust dashboardData.totalSpent as it can be inconsistent across different periods
        val merchantsTotal = dashboardData.topMerchantsWithCategory.sumOf { it.total_amount }
        
        Log.d("DashboardFragment", "[MERCHANTS_TOTAL_FIX] ViewModel: Using merchants total: ₹${String.format("%.0f", merchantsTotal)} instead of dashboard total: ₹${String.format("%.0f", dashboardData.totalSpent)}")
        
        val allMerchantItems = dashboardData.topMerchantsWithCategory.map { merchantResult ->
            // FIXED: Use merchantAliasManager for consistent merchant name display like Messages screen
            val displayName = merchantAliasManager.getDisplayName(merchantResult.normalized_merchant)
            Log.d("DashboardFragment", "[MERCHANT] Top Merchant Display Name: '${merchantResult.normalized_merchant}' -> '$displayName'")
            
            // CRITICAL FIX: Calculate percentage using merchants total, not dashboard total (like Top Categories)
            val percentage = if (merchantsTotal > 0) (merchantResult.total_amount / merchantsTotal) * 100 else 0.0
            Log.d("DashboardFragment", "[PERCENTAGE] ViewModel: ${displayName} = ${String.format("%.1f", percentage)}% (₹${merchantResult.total_amount} / ₹${merchantsTotal})")
            Log.d("DashboardFragment", "[CATEGORY] ViewModel: ${displayName} category: '${merchantResult.category_name}' color: '${merchantResult.category_color}'")
            
            MerchantSpending(
                merchantName = displayName,
                totalAmount = merchantResult.total_amount,
                transactionCount = merchantResult.transaction_count,
                category = merchantResult.category_name, // Now using actual category from database
                categoryColor = merchantResult.category_color, // Now using actual category color
                percentage = percentage
            )
        }
        
        // FIXED: Filter merchants by inclusion state to respect user toggle preferences
        val filteredMerchantItems = filterMerchantsByInclusionState(allMerchantItems)
        Log.d("DashboardFragment", "Filtered merchants from ${allMerchantItems.size} to ${filteredMerchantItems.size} based on inclusion states")
        
        // Ensure consistent display - always show at least 3 merchants (but only from included ones)
        val finalMerchantItems = ensureMinimumMerchants(filteredMerchantItems, 3)
        
        Log.d("DashboardFragment", "ViewModel: Updating top merchants: ${finalMerchantItems.map { "${it.merchantName}=₹${String.format("%.0f", it.totalAmount)}" }}")
        updateTopMerchantsTable(finalMerchantItems)
    }
    
    /**
     * Update monthly comparison from ViewModel
     */
    private fun updateMonthlyComparisonFromViewModel(comparison: MonthlyComparison?) {
        if (comparison == null) return
        
        try {
            val thisMonthView = binding.root.findViewById<TextView>(R.id.tv_this_month_amount)
            val lastMonthView = binding.root.findViewById<TextView>(R.id.tv_last_month_amount)
            val comparisonView = binding.root.findViewById<TextView>(R.id.tv_spending_comparison)
            
            thisMonthView?.text = "₹${String.format("%.0f", comparison.currentAmount)}"
            lastMonthView?.text = "₹${String.format("%.0f", comparison.previousAmount)}"
            comparisonView?.text = comparison.changeText
            
            // Set color based on change
            comparisonView?.setTextColor(
                when {
                    comparison.hasIncrease -> ContextCompat.getColor(requireContext(), R.color.error)
                    comparison.hasDecrease -> ContextCompat.getColor(requireContext(), R.color.success)
                    else -> ContextCompat.getColor(requireContext(), R.color.text_secondary)
                }
            )
            
            Log.d("DashboardFragment", "ViewModel: Updated monthly comparison: ${comparison.currentLabel} = ₹${String.format("%.0f", comparison.currentAmount)}, ${comparison.previousLabel} = ₹${String.format("%.0f", comparison.previousAmount)}")
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error updating monthly comparison from ViewModel", e)
        }
    }
    
    private fun setupUI() {
        // Initialize with loading state
        binding.tvTotalBalance.text = "Loading..."
        binding.tvTotalSpent.text = "Loading..."
        binding.tvTransactionCount.text = "0"
        
        // Setup top merchants table
        setupTopMerchantsTable()
        setupTopCategoriesTable()
        
        
        // Setup dashboard period filter
        setupDashboardPeriodFilter()
        
        // Setup weekly trend chart
        setupWeeklyTrendChart()
    }
    
    private fun setupTopMerchantsTable() {
        // Setup click listeners for merchant rows
        binding.rowMerchant1.setOnClickListener { navigateToMerchant(binding.tvMerchant1Name.text.toString()) }
        binding.rowMerchant2.setOnClickListener { navigateToMerchant(binding.tvMerchant2Name.text.toString()) }
        binding.rowMerchant3.setOnClickListener { navigateToMerchant(binding.tvMerchant3Name.text.toString()) }
        binding.rowMerchant4.setOnClickListener { navigateToMerchant(binding.tvMerchant4Name.text.toString()) }
    }
    
    private fun navigateToMerchant(merchantName: String) {
        val bundle = Bundle().apply {
            putString("merchantName", merchantName)
        }
        findNavController().navigate(
            R.id.action_dashboard_to_merchant_transactions,
            bundle
        )
    }
    
    private fun getCategoryEmoji(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "food & dining", "food", "dining" -> "🍽️"
            "groceries", "grocery" -> "🛒"
            "transportation", "transport" -> "🚗"
            "shopping" -> "🛍️"
            "entertainment" -> "🎬"
            "healthcare", "health", "medical" -> "🏥"
            "utilities" -> "⚡"
            "education" -> "📚"
            "travel" -> "✈️"
            "bills" -> "💳"
            "insurance" -> "🛡️"
            "money", "finance" -> "💰"
            else -> "📂"
        }
    }
    
    private fun updateTopMerchantsTable(merchants: List<MerchantSpending>) {
        val merchantViews = listOf(
            Triple(binding.tvMerchant1Emoji, binding.tvMerchant1Name, binding.tvMerchant1Category) to Triple(binding.tvMerchant1Amount, binding.tvMerchant1Count, binding.rowMerchant1),
            Triple(binding.tvMerchant2Emoji, binding.tvMerchant2Name, binding.tvMerchant2Category) to Triple(binding.tvMerchant2Amount, binding.tvMerchant2Count, binding.rowMerchant2),
            Triple(binding.tvMerchant3Emoji, binding.tvMerchant3Name, binding.tvMerchant3Category) to Triple(binding.tvMerchant3Amount, binding.tvMerchant3Count, binding.rowMerchant3),
            Triple(binding.tvMerchant4Emoji, binding.tvMerchant4Name, binding.tvMerchant4Category) to Triple(binding.tvMerchant4Amount, binding.tvMerchant4Count, binding.rowMerchant4)
        )
        
        // Default merchant data for empty slots
        val defaultMerchants = listOf(
            MerchantSpending("Swiggy", 3250.0, 5, "Food & Dining", "#ff5722", 25.4),
            MerchantSpending("BigBasket", 2180.0, 3, "Groceries", "#4caf50", 18.0),
            MerchantSpending("Uber", 1950.0, 8, "Transportation", "#3f51b5", 15.2),
            MerchantSpending("Amazon", 1680.0, 4, "Shopping", "#ff9800", 13.1)
        )
        
        merchantViews.forEachIndexed { index, (infoViews, amountViews) ->
            val (emojiView, nameView, categoryView) = infoViews
            val (amountView, countView, rowView) = amountViews
            
            val merchant = if (index < merchants.size) merchants[index] else defaultMerchants[index]
            
            emojiView.text = getCategoryEmoji(merchant.category)
            nameView.text = merchant.merchantName
            categoryView.text = merchant.category
            amountView.text = "₹${String.format("%.0f", merchant.totalAmount)}"
            countView.text = "${merchant.transactionCount} transactions"
            
            // Show/hide row based on whether we have data
            rowView.visibility = View.VISIBLE
        }
    }
    
    private fun setupTopCategoriesTable() {
        // Setup click listeners for each category row
        binding.categoryRow1.setOnClickListener {
            val categoryName = binding.categoryName1.text.toString()
            navigateToCategoryTransactions(categoryName)
        }
        binding.categoryRow2.setOnClickListener {
            val categoryName = binding.categoryName2.text.toString()
            navigateToCategoryTransactions(categoryName)
        }
        binding.categoryRow3.setOnClickListener {
            val categoryName = binding.categoryName3.text.toString()
            navigateToCategoryTransactions(categoryName)
        }
        binding.categoryRow4.setOnClickListener {
            val categoryName = binding.categoryName4.text.toString()
            navigateToCategoryTransactions(categoryName)
        }
    }
    
    private fun navigateToCategoryTransactions(categoryName: String) {
        val bundle = Bundle().apply {
            putString("categoryName", categoryName)
        }
        findNavController().navigate(R.id.action_navigation_categories_to_navigation_category_transactions, bundle)
    }
    
    private fun updateTopCategoriesTable(categoryItems: List<CategorySpending>) {
        Log.d("DashboardFragment", "Updating top categories table with ${categoryItems.size} items")
        
        // Get the category colors
        val colorMap = mapOf(
            "Food & Dining" to ContextCompat.getColor(requireContext(), R.color.category_food),
            "Transport" to ContextCompat.getColor(requireContext(), R.color.category_transport),
            "Shopping" to ContextCompat.getColor(requireContext(), R.color.category_shopping),
            "Groceries" to ContextCompat.getColor(requireContext(), R.color.category_groceries),
            "Entertainment" to ContextCompat.getColor(requireContext(), R.color.category_entertainment),
            "Healthcare" to ContextCompat.getColor(requireContext(), R.color.category_healthcare),
            "Utilities" to ContextCompat.getColor(requireContext(), R.color.category_utilities),
            "Other" to ContextCompat.getColor(requireContext(), R.color.category_other)
        )
        
        // Update first category row
        if (categoryItems.isNotEmpty()) {
            val item1 = categoryItems[0]
            binding.categoryName1.text = item1.categoryName
            binding.categoryAmount1.text = "₹${String.format("%.0f", item1.amount)}"
            binding.categoryCount1.text = "${item1.count} transactions"
            binding.categoryColor1.setBackgroundColor(colorMap[item1.categoryName] ?: ContextCompat.getColor(requireContext(), R.color.category_other))
        } else {
            binding.categoryName1.text = "No Data"
            binding.categoryAmount1.text = "₹0"
            binding.categoryCount1.text = "0 transactions"
            binding.categoryColor1.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.category_other))
        }
        
        // Update second category row
        if (categoryItems.size > 1) {
            val item2 = categoryItems[1]
            binding.categoryName2.text = item2.categoryName
            binding.categoryAmount2.text = "₹${String.format("%.0f", item2.amount)}"
            binding.categoryCount2.text = "${item2.count} transactions"
            binding.categoryColor2.setBackgroundColor(colorMap[item2.categoryName] ?: ContextCompat.getColor(requireContext(), R.color.category_other))
        } else {
            binding.categoryName2.text = "No Data"
            binding.categoryAmount2.text = "₹0"
            binding.categoryCount2.text = "0 transactions"
            binding.categoryColor2.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.category_other))
        }
        
        // Update third category row
        if (categoryItems.size > 2) {
            val item3 = categoryItems[2]
            binding.categoryName3.text = item3.categoryName
            binding.categoryAmount3.text = "₹${String.format("%.0f", item3.amount)}"
            binding.categoryCount3.text = "${item3.count} transactions"
            binding.categoryColor3.setBackgroundColor(colorMap[item3.categoryName] ?: ContextCompat.getColor(requireContext(), R.color.category_other))
        } else {
            binding.categoryName3.text = "No Data"
            binding.categoryAmount3.text = "₹0"
            binding.categoryCount3.text = "0 transactions"
            binding.categoryColor3.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.category_other))
        }
        
        // Update fourth category row
        if (categoryItems.size > 3) {
            val item4 = categoryItems[3]
            binding.categoryName4.text = item4.categoryName
            binding.categoryAmount4.text = "₹${String.format("%.0f", item4.amount)}"
            binding.categoryCount4.text = "${item4.count} transactions"
            binding.categoryColor4.setBackgroundColor(colorMap[item4.categoryName] ?: ContextCompat.getColor(requireContext(), R.color.category_other))
        } else {
            binding.categoryName4.text = "No Data"
            binding.categoryAmount4.text = "₹0"
            binding.categoryCount4.text = "0 transactions"
            binding.categoryColor4.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.category_other))
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
                        
                        
                        // Use ViewModel for dashboard period change - primary approach
                        viewModel.handleEvent(DashboardUIEvent.ChangePeriod(selectedPeriod))
                        
                        // COMMENT: Disabling parallel approach to prevent conflicts and ensure consistent data filtering
                        // The ViewModel approach should handle all data loading including Top Merchants and Top Categories consistently
                        // loadDashboardDataForPeriod(selectedPeriod)
                        
                        Log.d("DashboardFragment", "[PERIOD_CHANGE] Period changed to: $selectedPeriod using ViewModel approach only")
                    }
                }
                .show()
        }
    }
    
    private fun setupWeeklyTrendChart() {
        val chart = binding.chartWeeklyTrend
        
        // Basic chart configuration
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            legend.isEnabled = false
            
            // Customize X-axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 10f
            }
            
            // Customize Y-axis  
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.divider)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 10f
            }
            
            axisRight.isEnabled = false
        }
        
        // Load initial chart data
        updateWeeklyTrendChart()
    }
    
    private fun updateWeeklyTrendChart() {
        // This method will be called after chart is properly updated with real data
        // The real chart update happens in updateWeeklyTrendWithRealData()
        lifecycleScope.launch {
            val (startDate, endDate) = getDateRangeForPeriod(currentDashboardPeriod)
            updateWeeklyTrendWithRealData(startDate, endDate)
        }
    }
    
    private suspend fun updateWeeklyTrendWithRealData(startDate: Date, endDate: Date) {
        try {
            Log.d("DashboardFragment", "[WEEKLY_CHART] Updating chart for period: $currentDashboardPeriod")
            Log.d("DashboardFragment", "[WEEKLY_CHART] Dashboard period range: $startDate to $endDate")
            
            // Always show last 7 days for simplicity and consistency
            // Calculate the last 7 days ending at the endDate of the selected period
            val calendar = Calendar.getInstance()
            calendar.time = endDate
            
            // Set to end of day for the end date
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val chartEndDate = calendar.time
            
            // Go back 6 days to get 7 days total
            calendar.add(Calendar.DAY_OF_YEAR, -6)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val chartStartDate = calendar.time
            
            Log.d("DashboardFragment", "[WEEKLY_CHART] Chart showing last 7 days: ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(chartStartDate)} to ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(chartEndDate)}")
            
            // Fetch transactions for the 7-day period
            val transactions = repository.getTransactionsByDateRange(chartStartDate, chartEndDate)
            Log.d("DashboardFragment", "[WEEKLY_CHART] Found ${transactions.size} transactions in 7-day range")
            
            // Always use the simple 7-day calculation with specific date range
            val chartData = calculateLast7DaysDataWithRange(transactions, chartStartDate, chartEndDate)
            
            // Update the chart on main thread
            withContext(Dispatchers.Main) {
                updateChartWithData(chartData)
            }
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "[WEEKLY_CHART] Error updating chart", e)
            withContext(Dispatchers.Main) {
                showEmptyChart("Chart Error")
            }
        }
    }
    
    private fun calculateLast7DaysData(transactions: List<TransactionEntity>): List<ChartDataPoint> {
        val calendar = Calendar.getInstance()
        val dailyData = mutableListOf<ChartDataPoint>()
        
        // Get last 7 days data
        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            
            val dayStart = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val dayEnd = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time
            
            val daySpending = transactions.filter { 
                it.transactionDate >= dayStart && it.transactionDate <= dayEnd 
            }.sumOf { it.amount }
            
            val dayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            dailyData.add(ChartDataPoint(
                index = (6 - i).toFloat(),
                value = daySpending.toFloat(),
                label = dayFormat.format(calendar.time)
            ))
        }
        
        return dailyData
    }
    
    private fun calculateLast7DaysDataWithRange(transactions: List<TransactionEntity>, startDate: Date, endDate: Date): List<ChartDataPoint> {
        val calendar = Calendar.getInstance()
        val dailyData = mutableListOf<ChartDataPoint>()
        
        Log.d("DashboardFragment", "[WEEKLY_CHART] Calculating 7 days from ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(startDate)} to ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(endDate)}")
        
        // Generate 7 days of data using the provided date range
        for (i in 0 until 7) {
            calendar.time = startDate
            calendar.add(Calendar.DAY_OF_YEAR, i)
            
            val dayStart = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val dayEnd = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time
            
            val daySpending = transactions.filter { 
                it.transactionDate >= dayStart && it.transactionDate <= dayEnd 
            }.sumOf { it.amount }
            
            val dayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val dayLabel = dayFormat.format(calendar.time)
            
            dailyData.add(ChartDataPoint(
                index = i.toFloat(),
                value = daySpending.toFloat(),
                label = dayLabel
            ))
            
            Log.d("DashboardFragment", "[WEEKLY_CHART] Day ${i + 1}: $dayLabel = ₹$daySpending")
        }
        
        Log.d("DashboardFragment", "[WEEKLY_CHART] Generated ${dailyData.size} daily data points")
        return dailyData
    }
    
    private fun calculateDailyDataForPeriod(transactions: List<TransactionEntity>, startDate: Date, endDate: Date): List<ChartDataPoint> {
        val calendar = Calendar.getInstance()
        val dailyData = mutableListOf<ChartDataPoint>()
        
        // Calculate days between start and end date
        val daysBetween = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1
        
        Log.d("DashboardFragment", "[WEEKLY_CHART] Calculating daily data for $daysBetween days")
        
        // Get daily spending for each day in the period
        for (i in 0 until daysBetween) {
            calendar.time = startDate
            calendar.add(Calendar.DAY_OF_YEAR, i)
            
            val dayStart = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val dayEnd = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time
            
            val daySpending = transactions.filter { 
                it.transactionDate >= dayStart && it.transactionDate <= dayEnd 
            }.sumOf { it.amount }
            
            val dayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            dailyData.add(ChartDataPoint(
                index = i.toFloat(),
                value = daySpending.toFloat(),
                label = dayFormat.format(calendar.time)
            ))
            
            Log.d("DashboardFragment", "[WEEKLY_CHART] Day ${i + 1}: ${dayFormat.format(calendar.time)} = ₹$daySpending")
        }
        
        Log.d("DashboardFragment", "[WEEKLY_CHART] Generated ${dailyData.size} daily data points")
        return dailyData
    }
    
    private fun calculateDailyDataWithContext(transactions: List<TransactionEntity>, startDate: Date, endDate: Date): List<ChartDataPoint> {
        val calendar = Calendar.getInstance()
        val dailyData = mutableListOf<ChartDataPoint>()
        
        // For better context, show a week's worth of data (7 days)
        // Start from 6 days before the start date to show context
        calendar.time = startDate
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val contextStartDate = calendar.time
        
        Log.d("DashboardFragment", "[WEEKLY_CHART] Calculating daily data with context from ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(contextStartDate)} to ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(endDate)}")
        
        // Show 7 days of data for context
        for (i in 0 until 7) {
            calendar.time = contextStartDate
            calendar.add(Calendar.DAY_OF_YEAR, i)
            
            val dayStart = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val dayEnd = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time
            
            // Count all transactions for this day (including context days)
            val daySpending = transactions.filter { 
                it.transactionDate >= dayStart && it.transactionDate <= dayEnd
            }.sumOf { it.amount }
            
            val dayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val dayLabel = dayFormat.format(calendar.time)
            
            // Mark current month days differently
            val isCurrentPeriod = calendar.time >= startDate && calendar.time <= endDate
            
            dailyData.add(ChartDataPoint(
                index = i.toFloat(),
                value = daySpending.toFloat(),
                label = if (isCurrentPeriod) dayLabel else "($dayLabel)" // Mark non-current days with parentheses
            ))
            
            Log.d("DashboardFragment", "[WEEKLY_CHART] Day ${i + 1}: $dayLabel = ₹$daySpending ${if (isCurrentPeriod) "(current period)" else "(context)"}")
        }
        
        Log.d("DashboardFragment", "[WEEKLY_CHART] Generated ${dailyData.size} daily data points with context")
        return dailyData
    }
    
    private fun calculateMonthlyWeeklyData(transactions: List<TransactionEntity>, startDate: Date, endDate: Date): List<ChartDataPoint> {
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        
        val weeklyData = mutableListOf<ChartDataPoint>()
        var weekIndex = 0f
        
        while (calendar.time <= endDate) {
            val weekStart = calendar.time
            calendar.add(Calendar.DAY_OF_YEAR, 6)
            val weekEnd = if (calendar.time > endDate) endDate else calendar.time
            
            val weekSpending = transactions.filter { 
                it.transactionDate >= weekStart && it.transactionDate <= weekEnd 
            }.sumOf { it.amount }
            
            val weekFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            weeklyData.add(ChartDataPoint(
                index = weekIndex,
                value = weekSpending.toFloat(),
                label = "Week ${weekFormat.format(weekStart)}"
            ))
            
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            weekIndex++
        }
        
        return weeklyData
    }
    
    private fun calculateMultiMonthData(transactions: List<TransactionEntity>, startDate: Date, endDate: Date): List<ChartDataPoint> {
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        
        val monthlyData = mutableListOf<ChartDataPoint>()
        var monthIndex = 0f
        
        while (calendar.time <= endDate) {
            val monthStart = calendar.time
            calendar.add(Calendar.MONTH, 1)
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            val monthEnd = if (calendar.time > endDate) endDate else calendar.time
            
            val monthSpending = transactions.filter { 
                it.transactionDate >= monthStart && it.transactionDate <= monthEnd 
            }.sumOf { it.amount }
            
            val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
            monthlyData.add(ChartDataPoint(
                index = monthIndex,
                value = monthSpending.toFloat(),
                label = monthFormat.format(monthStart)
            ))
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            monthIndex++
        }
        
        return monthlyData
    }
    
    private fun calculateYearlyMonthlyData(transactions: List<TransactionEntity>, startDate: Date, endDate: Date): List<ChartDataPoint> {
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        
        val monthlyData = mutableListOf<ChartDataPoint>()
        var monthIndex = 0f
        
        while (calendar.time <= endDate) {
            val monthStart = calendar.time
            calendar.add(Calendar.MONTH, 1)
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            val monthEnd = if (calendar.time > endDate) endDate else calendar.time
            
            val monthSpending = transactions.filter { 
                it.transactionDate >= monthStart && it.transactionDate <= monthEnd 
            }.sumOf { it.amount }
            
            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
            monthlyData.add(ChartDataPoint(
                index = monthIndex,
                value = monthSpending.toFloat(),
                label = monthFormat.format(monthStart)
            ))
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            monthIndex++
        }
        
        return monthlyData
    }
    
    private fun updateChartWithData(chartData: List<ChartDataPoint>) {
        val chart = binding.chartWeeklyTrend
        
        if (chartData.isEmpty()) {
            showEmptyChart("No Data Available")
            return
        }
        
        Log.d("DashboardFragment", "[WEEKLY_CHART] Updating chart with ${chartData.size} data points")
        
        // Create chart entries
        val entries = chartData.map { Entry(it.index, it.value) }
        
        val dataSet = LineDataSet(entries, "Spending Trend").apply {
            color = ContextCompat.getColor(requireContext(), R.color.primary)
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.primary))
            lineWidth = 3f
            circleRadius = 5f
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(requireContext(), R.color.primary_light)
            fillAlpha = 30
            valueTextSize = 10f
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "₹${String.format("%.0f", value)}"
                }
            }
        }
        
        chart.data = LineData(dataSet)
        
        // Configure X-axis with proper labels
        chart.xAxis.apply {
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    return if (index >= 0 && index < chartData.size) {
                        chartData[index].label
                    } else {
                        ""
                    }
                }
            }
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(true)
            gridColor = ContextCompat.getColor(requireContext(), R.color.divider)
            textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            textSize = 10f
            labelRotationAngle = -45f
        }
        
        // Configure Y-axis with proper currency formatting
        chart.axisLeft.apply {
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "₹${String.format("%.0f", value)}"
                }
            }
            setDrawGridLines(true)
            gridColor = ContextCompat.getColor(requireContext(), R.color.divider)
            textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            textSize = 10f
        }
        
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        chart.description.isEnabled = false
        
        chart.invalidate()
        
        Log.d("DashboardFragment", "[WEEKLY_CHART] Chart updated successfully")
    }
    
    private fun showEmptyChart(message: String) {
        val chart = binding.chartWeeklyTrend
        chart.clear()
        chart.setNoDataText(message)
        chart.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        chart.invalidate()
    }
    
    private fun setupClickListeners() {
        binding.cardAiInsights.setOnClickListener {
            findNavController().navigate(R.id.navigation_insights)
        }
        
        // Quick Action button listeners
        binding.btnAddExpense.setOnClickListener {
            showQuickAddExpenseDialog()
        }
        
        binding.btnSyncSms.setOnClickListener {
            // Use ViewModel for SMS sync
            viewModel.handleEvent(DashboardUIEvent.SyncSMS)
            
            // Also keep existing approach for parallel testing
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
        
        // Make transaction count section clickable
        binding.layoutTransactionCount.setOnClickListener {
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
            .setTitle("Quick Add Expense")
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
            val amountText = dialogView.findViewById<TextInputEditText>(R.id.et_amount).text.toString().trim()
            val merchant = dialogView.findViewById<TextInputEditText>(R.id.et_merchant).text.toString().trim()
            val category = categorySpinner.text.toString().trim()
            
            if (amountText.isNotEmpty() && merchant.isNotEmpty() && category.isNotEmpty()) {
                val amount = amountText.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(
                        requireContext(), 
                        "Please enter a valid amount", 
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                
                // FIXED: Actually save the transaction to database
                lifecycleScope.launch {
                    try {
                        Log.d("DashboardFragment", "[TRANSACTION] Saving manual transaction: ₹$amount at $merchant ($category)")
                        
                        // Create manual transaction entity
                        val manualTransaction = addTransactionUseCase.createManualTransaction(
                            amount = amount,
                            merchantName = merchant,
                            categoryName = category,
                            bankName = "Manual Entry"
                        )
                        
                        // Save transaction to database
                        val result = addTransactionUseCase.execute(manualTransaction)
                        
                        if (result.isSuccess) {
                            val transactionId = result.getOrNull()
                            
                            // Show success message
                            Toast.makeText(
                                requireContext(), 
                                "Added: ₹$amount at $merchant ($category)", 
                                Toast.LENGTH_LONG
                            ).show()
                            
                            dialog.dismiss()
                            
                            // FIXED: Reload dashboard data to show the new transaction
                            loadDashboardData()
                            
                            // Also trigger ViewModel refresh
                            viewModel.handleEvent(DashboardUIEvent.LoadData)
                            
                            // Send broadcast to notify other screens (Messages, Categories, etc.)
                            val intent = android.content.Intent("com.expensemanager.NEW_TRANSACTION_ADDED").apply {
                                putExtra("merchant", merchant)
                                putExtra("amount", amount)
                                putExtra("category", category)
                                putExtra("source", "manual_entry")
                            }
                            requireContext().sendBroadcast(intent)
                            
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            val exception = result.exceptionOrNull()
                            Log.e("DashboardFragment", "Failed to save transaction: $error", exception)
                            
                            // Provide more helpful error messages to users
                            val userMessage = when {
                                error.contains("duplicate", ignoreCase = true) -> "This transaction may already exist"
                                error.contains("validation", ignoreCase = true) -> "Please check that all fields are filled correctly"
                                error.contains("database", ignoreCase = true) -> "Database error - please try again"
                                else -> "Failed to save transaction: $error"
                            }
                            
                            Toast.makeText(
                                requireContext(), 
                                userMessage, 
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        
                    } catch (e: Exception) {
                        Log.e("DashboardFragment", "Error saving transaction", e)
                        
                        // Provide helpful error messages to users
                        val userMessage = when (e) {
                            is SecurityException -> "Permission error - please restart the app"
                            is IllegalArgumentException -> "Invalid transaction data - please check your input"
                            else -> "Error saving transaction - please try again"
                        }
                        
                        Toast.makeText(
                            requireContext(), 
                            userMessage, 
                            Toast.LENGTH_LONG
                        ).show()
                    }
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
            text = "First Month:"
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
            text = "Second Month (to compare with):"
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
            .setTitle("Select Two Months to Compare")
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
            
            Log.d("DashboardFragment", "Custom month selection: '$firstMonthText' vs '$secondMonthText'")
            
            // Parse the month/year from spinner selections
            customFirstMonth = parseMonthYear(firstMonthText)
            customSecondMonth = parseMonthYear(secondMonthText)
            
            if (customFirstMonth != null && customSecondMonth != null) {
                if (customFirstMonth == customSecondMonth) {
                    Toast.makeText(requireContext(), "Please select two different months", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Update dashboard period to show custom selection
                currentDashboardPeriod = "Custom: $firstMonthText vs $secondMonthText"
                binding.btnDashboardPeriod.text = "Custom Months"
                
                // Load dashboard with custom month comparison
                loadDashboardDataWithCustomMonths()
                
                Toast.makeText(
                    requireContext(),
                    "Comparing $firstMonthText vs $secondMonthText",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(requireContext(), "Error parsing selected months", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error handling custom month selection", e)
            Toast.makeText(requireContext(), "Error with month selection", Toast.LENGTH_SHORT).show()
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
                
                Log.d("DashboardFragment", "Loading dashboard with custom months: ${customFirstMonth} vs ${customSecondMonth}")
                
                // Calculate date ranges for both custom months
                val (firstStart, firstEnd) = getDateRangeForCustomMonth(customFirstMonth!!)
                val (secondStart, secondEnd) = getDateRangeForCustomMonth(customSecondMonth!!)
                
                Log.d("DashboardFragment", "First month range: $firstStart to $firstEnd")
                Log.d("DashboardFragment", "Second month range: $secondStart to $secondEnd")
                
                // Load dashboard data for the first month (main display)
                val firstMonthData = repository.getDashboardData(firstStart, firstEnd)
                
                Log.d("DashboardFragment", "[DATA] First month data: ${firstMonthData.transactionCount} transactions, ₹${String.format("%.0f", firstMonthData.totalSpent)}")
                
                if (firstMonthData.transactionCount > 0) {
                    // Update dashboard with first month data
                    updateDashboardWithRepositoryData(firstMonthData, firstStart, firstEnd)
                    
                    // Update monthly comparison with custom months
                    updateCustomMonthlyComparison(firstStart, firstEnd, secondStart, secondEnd)
                } else {
                    Log.d("DashboardFragment", "No data found for first custom month, trying sync...")
                    
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
            
            Log.d("DashboardFragment", "[FINANCIAL] Custom monthly comparison:")
            Log.d("DashboardFragment", "   $firstMonthLabel: ₹${String.format("%.0f", firstMonthSpent)}")
            Log.d("DashboardFragment", "   $secondMonthLabel: ₹${String.format("%.0f", secondMonthSpent)}")
            
            // Update the UI with custom month comparison
            updateMonthlyComparisonUI(firstMonthLabel, secondMonthLabel, firstMonthSpent, secondMonthSpent)
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error updating custom monthly comparison", e)
        }
    }
    
    private fun ensureMinimumMerchants(realMerchants: List<MerchantSpending>, minimumCount: Int): List<MerchantSpending> {
        // FIXED: Don't show placeholder data - show actual data only or empty state
        // This prevents confusing users with fake merchant data when no SMS exist
        return realMerchants.take(minimumCount)
    }
    
    private fun ensureMinimumCategories(realCategories: List<CategorySpending>, minimumCount: Int): List<CategorySpending> {
        // FIXED: Don't show placeholder data - show actual data only or empty state
        // This prevents confusing users with fake category data when no SMS exist
        return realCategories.take(minimumCount)
    }
    
    private fun performSMSSync() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sync SMS Messages")
            .setMessage("This will scan your SMS messages and update all transaction data. This may take a few moments.")
            .setPositiveButton("Sync Now") { _, _ ->
                lifecycleScope.launch {
                    val progressDialog = MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Syncing SMS Messages")
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
                                "Synced $syncedCount new transactions from SMS!",
                                Toast.LENGTH_LONG
                            ).show()
                            
                            // Reload dashboard data from repository
                            loadDashboardData()
                            
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "No new transaction SMS found",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        
                    } catch (e: SecurityException) {
                        progressDialog.dismiss()
                        Toast.makeText(
                            requireContext(),
                            "SMS permission required for sync",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        progressDialog.dismiss()
                        Toast.makeText(
                            requireContext(),
                            "Error syncing SMS: ${e.message ?: "Unknown error"}",
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
                
                // FIXED: Enhanced empty state detection - check if database has any real data
                val isDbEmpty = repository.isDatabaseEmpty()
                
                if (isDbEmpty) {
                    Log.d("DashboardFragment", "Database is empty - showing proper empty state")
                    updateDashboardWithEmptyState()
                    return@launch
                }
                
                // Get date range for current dashboard period
                val (startDate, endDate) = getDateRangeForPeriod(currentDashboardPeriod)
                
                // Get dashboard data for the specified period
                val dashboardData = repository.getDashboardData(startDate, endDate)
                
                if (dashboardData.transactionCount > 0) {
                    Log.d("DashboardFragment", "[SUCCESS] Loaded dashboard data from SQLite: ${dashboardData.transactionCount} transactions, ₹${String.format("%.0f", dashboardData.totalSpent)} spent")
                    Log.d("DashboardFragment", "[DATA] Dashboard Date Range: ${startDate} to ${endDate}")
                    Log.d("DashboardFragment", "[DATA] Dashboard Raw Transactions Count: ${repository.getTransactionsByDateRange(startDate, endDate).size}")
                    Log.d("DashboardFragment", "[DATA] Dashboard Filtered Total: ₹${String.format("%.0f", dashboardData.totalSpent)}")
                    updateDashboardWithRepositoryData(dashboardData, startDate, endDate)
                } else {
                    Log.d("DashboardFragment", "No data in SQLite database yet, checking SMS sync status...")
                    
                    // Check if initial migration is still in progress
                    val syncStatus = repository.getSyncStatus()
                    if (syncStatus == "INITIAL" || syncStatus == "IN_PROGRESS") {
                        Log.d("DashboardFragment", "[MIGRATION] Initial data migration in progress, showing loading state")
                        showLoadingStateWithMessage("Setting up your data for the first time...")
                        
                        // Retry loading data after a delay
                        binding.root.postDelayed({
                            loadDashboardData()
                        }, 2000)
                    } else {
                            // FIXED: For this time period, no data exists - show empty state for the period
                        Log.d("DashboardFragment", "No transactions for period $currentDashboardPeriod")
                        updateDashboardWithEmptyState()
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
        Log.d("DashboardFragment", "[DEBUG] ${repository.getExclusionStatesDebugInfo()}")
        
        // Update spending summary
        val totalSpent = dashboardData.totalSpent
        binding.tvTotalSpent.text = "₹${String.format("%.0f", totalSpent)}"
        binding.tvTransactionCount.text = "${dashboardData.transactionCount}"
        
        // Calculate and update savings
        val savings = if (currentDashboardPeriod == "This Month" && dashboardData.monthlyBalance.hasSalaryData) {
            dashboardData.monthlyBalance.remainingBalance
        } else {
            kotlin.math.max(0.0, -dashboardData.totalSpent) // Assume positive savings from reduced spending
        }
        
        // MONTHLY BALANCE FEATURE: Use salary-based balance for "This Month", regular balance for other periods
        val balanceToShow = if (currentDashboardPeriod == "This Month" && dashboardData.monthlyBalance.hasSalaryData) {
            dashboardData.monthlyBalance.remainingBalance
        } else {
            dashboardData.actualBalance
        }
        
        binding.tvTotalBalance.text = if (balanceToShow >= 0) {
            "₹${String.format("%.0f", balanceToShow)}"
        } else {
            "-₹${String.format("%.0f", kotlin.math.abs(balanceToShow))}"
        }
        
        // Add salary info logging for debugging
        if (currentDashboardPeriod == "This Month" && dashboardData.monthlyBalance.hasSalaryData) {
            Log.d("DashboardFragment", "[REPOSITORY MONTHLY BALANCE] Showing salary-based balance: ₹${balanceToShow} (Last Salary: ₹${dashboardData.monthlyBalance.lastSalaryAmount})")
        }
        
        // Debug logging for balance calculation
        Log.d("DashboardFragment", "[BALANCE UPDATE] Credits: ₹${dashboardData.totalCredits}, Debits: ₹${dashboardData.totalSpent}, Displayed Balance: ₹$balanceToShow")
        
        // Update top categories with repository data
        val categorySpendingItems = dashboardData.topCategories.map { categoryResult ->
            CategorySpending(
                categoryName = categoryResult.category_name,
                amount = categoryResult.total_amount,
                categoryColor = categoryResult.color,
                count = categoryResult.transaction_count
            )
        }
        
        // FIXED: Ensure consistent display - always show at least 4 categories (2x2 grid)
        val finalCategoryItems = ensureMinimumCategories(categorySpendingItems, 4)
        
        Log.d("DashboardFragment", "Updating top categories: ${finalCategoryItems.map { "${it.categoryName}=₹${String.format("%.0f", it.amount)}" }}")
        updateTopCategoriesTable(finalCategoryItems)
        
        // Update top merchants with repository data using NEW category information
        // CRITICAL FIX: Calculate total from merchants data like Top Categories do (Repository approach)
        val merchantsTotal = dashboardData.topMerchantsWithCategory.sumOf { it.total_amount }
        
        Log.d("DashboardFragment", "[MERCHANTS_TOTAL_FIX] Repository: Using merchants total: ₹${String.format("%.0f", merchantsTotal)} instead of dashboard total: ₹${String.format("%.0f", totalSpent)}")
        
        val allMerchantItems = dashboardData.topMerchantsWithCategory.map { merchantResult ->
            // FIXED: Use merchantAliasManager for consistent merchant name display like Messages screen
            val displayName = merchantAliasManager.getDisplayName(merchantResult.normalized_merchant)
            Log.d("DashboardFragment", "[MERCHANT] Repository Top Merchant Display Name: '${merchantResult.normalized_merchant}' -> '$displayName'")
            Log.d("DashboardFragment", "[CATEGORY] Repository: ${displayName} category: '${merchantResult.category_name}' color: '${merchantResult.category_color}'")
            
            // CRITICAL FIX: Calculate percentage using merchants total, not dashboard total (like Top Categories)
            val percentage = if (merchantsTotal > 0) (merchantResult.total_amount / merchantsTotal) * 100 else 0.0
            Log.d("DashboardFragment", "[PERCENTAGE] Repository: ${displayName} = ${String.format("%.1f", percentage)}% (₹${merchantResult.total_amount} / ₹${merchantsTotal})")
            
            MerchantSpending(
                merchantName = displayName,
                totalAmount = merchantResult.total_amount,
                transactionCount = merchantResult.transaction_count,
                category = merchantResult.category_name, // Now using actual category from database
                categoryColor = merchantResult.category_color, // Now using actual category color
                percentage = percentage
            )
        }
        
        // FIXED: Filter merchants by inclusion state to respect user toggle preferences  
        val filteredMerchantItems = filterMerchantsByInclusionState(allMerchantItems)
        Log.d("DashboardFragment", "Repository filtered merchants from ${allMerchantItems.size} to ${filteredMerchantItems.size} based on inclusion states")
        
        // FIXED: Ensure consistent display - always show at least 3 merchants (but only from included ones)
        val finalMerchantItems = ensureMinimumMerchants(filteredMerchantItems, 3)
        
        Log.d("DashboardFragment", "Updating top merchants: ${finalMerchantItems.map { "${it.merchantName}=₹${String.format("%.0f", it.totalAmount)}" }}")
        updateTopMerchantsTable(finalMerchantItems)
        
        // Update monthly comparison based on selected period
        updateMonthlyComparisonFromRepository(startDate, endDate, currentDashboardPeriod)
        
        // Update weekly trend chart with repository data
        updateWeeklyTrendWithRealData(startDate, endDate)
        
        Log.d("DashboardFragment", "[SUCCESS] Dashboard UI updated successfully with repository data")
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
                    Log.d("DashboardFragment", "[DEBUG] Processing 'Last Month' period case")
                    val cal = Calendar.getInstance()
                    Log.d("DashboardFragment", "[DEBUG] Current time: ${cal.time}")
                    
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, -2)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val prevStart = cal.time
                    Log.d("DashboardFragment", "[DEBUG] Two months ago start: $prevStart")
                    
                    cal.add(Calendar.MONTH, 1)
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val prevEnd = cal.time
                    Log.d("DashboardFragment", "[DEBUG] Two months ago end: $prevEnd")
                    
                    Log.d("DashboardFragment", "[DEBUG] Returning comparison 'Last Month' vs 'Two Months Ago'")
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
            
            Log.d("DashboardFragment", "Monthly comparison date ranges for period '$period':")
            Log.d("DashboardFragment", "   $currentLabel: ${currentStart} to ${currentEnd}")
            Log.d("DashboardFragment", "   $previousLabel: ${previousStart} to ${previousEnd}")
            
            val currentPeriodSpent = repository.getTotalSpent(currentStart, currentEnd)
            val previousPeriodSpent = repository.getTotalSpent(previousStart, previousEnd)
            
            Log.d("DashboardFragment", "[FINANCIAL] Monthly spending comparison (single months only):")
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
                Log.d("DashboardFragment", "[SYNC] Attempting SMS sync through repository only...")
                
                // Use repository's syncNewSMS method - no direct SMS reading
                val syncedCount = repository.syncNewSMS()
                Log.d("DashboardFragment", "Repository sync completed: $syncedCount new transactions")
                
                if (syncedCount > 0) {
                    // Reload dashboard data after successful sync
                    Log.d("DashboardFragment", "[SUCCESS] SMS sync successful, reloading dashboard data...")
                    loadDashboardData()
                } else {
                    Log.d("DashboardFragment", "[INFO] No new transactions found during sync")
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
        
        // LEGACY BALANCE CALCULATION - TODO: Update to use ViewModel balance calculation
        // This method might be deprecated as we now use ViewModel for balance calculations
        val currentBalance = 0.0 - currentMonthSpent // Starting from 0, subtracting all expenses
        binding.tvTotalBalance.text = if (currentBalance >= 0) {
            "₹${String.format("%.0f", currentBalance)}"
        } else {
            "-₹${String.format("%.0f", kotlin.math.abs(currentBalance))}"
        }
        
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
    
    /**
     * Filter merchants by inclusion state to respect user toggle preferences
     * FIXED: This ensures disabled merchants don't appear in Dashboard Top Merchants
     */
    private fun filterMerchantsByInclusionState(merchants: List<MerchantSpending>): List<MerchantSpending> {
        // Load inclusion states from SharedPreferences (same as Messages screen)
        val prefs = requireContext().getSharedPreferences("expense_calculations", android.content.Context.MODE_PRIVATE)
        val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
        
        Log.d("DashboardFragment", "[DEBUG] Filtering ${merchants.size} merchants by inclusion state")
        Log.d("DashboardFragment", "[DEBUG] Inclusion states JSON: $inclusionStatesJson")
        
        if (inclusionStatesJson != null) {
            try {
                val inclusionStates = org.json.JSONObject(inclusionStatesJson)
                
                // Debug: Log all keys in the inclusion states
                val keys = mutableListOf<String>()
                inclusionStates.keys().forEach { key -> keys.add(key) }
                Log.d("DashboardFragment", "[DEBUG] All keys in inclusion states: $keys")
                
                val filteredMerchants = merchants.filter { merchant ->
                    val isIncluded = if (inclusionStates.has(merchant.merchantName)) {
                        val included = inclusionStates.getBoolean(merchant.merchantName)
                        Log.d("DashboardFragment", "[DEBUG] Merchant '${merchant.merchantName}': included=$included")
                        included
                    } else {
                        Log.d("DashboardFragment", "[DEBUG] Merchant '${merchant.merchantName}': not found in preferences, defaulting to included")
                        true // Default to included if not found
                    }
                    isIncluded
                }
                
                Log.d("DashboardFragment", "Merchant inclusion filter: ${merchants.size} -> ${filteredMerchants.size}")
                filteredMerchants.forEach { merchant ->
                    Log.d("DashboardFragment", "[SUCCESS] Included merchant: ${merchant.merchantName}")
                }
                
                return filteredMerchants
                
            } catch (e: Exception) {
                Log.w("DashboardFragment", "Error loading merchant inclusion states", e)
            }
        } else {
            Log.d("DashboardFragment", "[DEBUG] No inclusion states found, showing all merchants")
        }
        
        // Return all merchants if no inclusion states found or error occurred
        return merchants
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
                categoryColor = categoryColor,
                count = 1 // Default count for this data source
            )
        }
        
        Log.d("DashboardFragment", "Submitting ${categoryItems.size} items to categories adapter")
        updateTopCategoriesTable(categoryItems)
        Log.d("DashboardFragment", "Updated top categories table")
        
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
        updateTopMerchantsTable(merchantSpending)
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
            updateTopMerchantsTable(combinedMerchants)
            Log.d("DashboardFragment", "Total merchants displayed: ${combinedMerchants.size}")
        }
    }
    
    private fun updateDashboardWithEmptyState() {
        // FIXED: Proper empty state handling - clear all data displays
        binding.tvTotalBalance.text = "₹0"
        binding.tvTotalSpent.text = "₹0"
        binding.tvTransactionCount.text = "0"
        
        // Clear all adapters to show truly empty state
        updateTopMerchantsTable(emptyList())
        updateTopCategoriesTable(emptyList())
        
        // Clear monthly comparison
        updateMonthlyComparisonUI("This Month", "Last Month", 0.0, 0.0)
        
        // Clear weekly trend
        updateWeeklyTrendUI("No Data Available\nNo transactions found")
        
        Log.d("DashboardFragment", "[INFO] Dashboard showing proper empty state - no placeholder data")
    }
    
    private fun updateDashboardWithPermissionError() {
        binding.tvTotalBalance.text = "Permission Required"
        binding.tvTotalSpent.text = "₹0"
        binding.tvTransactionCount.text = "0"
        updateTopMerchantsTable(emptyList())
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
        updateTopMerchantsTable(emptyList())
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
        val weeklyTrendChart = binding.chartWeeklyTrend
        val placeholderTextView = weeklyTrendChart?.getChildAt(0) as? TextView
        
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
            Log.d("DashboardFragment", "[ANALYTICS] Weekly Trend: Using consistent filtering like monthly comparison")
            Log.d("DashboardFragment", "[WEEKLY_TREND] Updating trend for period: $currentDashboardPeriod, dates: $startDate to $endDate")
            
            // Calculate current period total (matches monthly comparison)
            val currentPeriodTotal = repository.getTotalSpent(startDate, endDate)
            Log.d("DashboardFragment", "[WEEKLY_TREND] Current period total: ₹${String.format("%.0f", currentPeriodTotal)}")
            
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
                    
                    val trendText = "Period Summary\n$currentLabel: ₹${String.format("%.0f", currentPeriodTotal)}"
                    updateWeeklyTrendUI(trendText)
                }
            }
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error updating weekly trend from repository", e)
            updateWeeklyTrendUI("Weekly Spending Chart\nData loading...")
        }
    }
    
    private fun createTrendText(currentLabel: String, currentAmount: Double, previousLabel: String, previousAmount: Double): String {
        val trend = when {
            previousAmount > 0 -> {
                val change = ((currentAmount - previousAmount) / previousAmount) * 100
                when {
                    change > 10 -> "Spending increased"
                    change < -10 -> "Spending decreased"  
                    else -> "Spending stable"
                }
            }
            currentAmount > 0 -> "New spending period"
            else -> "No spending data"
        }
        
        return "$trend\n$currentLabel: ₹${String.format("%.0f", currentAmount)}"
    }
    
    private fun updateWeeklyTrendUI(trendText: String) {
        val weeklyTrendChart = binding.chartWeeklyTrend
        val placeholderTextView = weeklyTrendChart?.getChildAt(0) as? TextView
        placeholderTextView?.text = trendText
        
        Log.d("DashboardFragment", "[ANALYTICS] Weekly trend updated with consistent data: $trendText")
    }
    
    // REMOVED: updateWeeklyTrendForPeriod() - no more direct SMS reading
    // All data access now goes through ExpenseRepository
    
    // REMOVED: getTransactionsForPeriod() - no more period-based SMS filtering
    
    private fun updateTrendDisplayForPeriod(transactions: List<ProcessedTransaction>, period: String) {
        val weeklyTrendChart = binding.chartWeeklyTrend
        val placeholderTextView = weeklyTrendChart?.getChildAt(0) as? TextView
        
        if (placeholderTextView != null) {
            val totalSpent = transactions.sumOf { it.amount }
            val count = transactions.size
            
            val trendText = buildString {
                when (period) {
                    "Last Week" -> {
                        appendLine("Last Week Summary")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        append("Transactions: $count")
                    }
                    
                    "This Month" -> {
                        val dailyAverage = if (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) > 0) {
                            totalSpent / Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                        } else 0.0
                        
                        appendLine("This Month")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        appendLine("Daily avg: ₹${String.format("%.0f", dailyAverage)}")
                        append("Transactions: $count")
                    }
                    
                    "Last Month" -> {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MONTH, -1)
                        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val dailyAverage = totalSpent / daysInMonth
                        
                        appendLine("Last Month")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        appendLine("Daily avg: ₹${String.format("%.0f", dailyAverage)}")
                        append("Transactions: $count")
                    }
                    
                    "Last 3 Months" -> {
                        val monthlyAverage = totalSpent / 3
                        appendLine("Last 3 Months")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        appendLine("Monthly avg: ₹${String.format("%.0f", monthlyAverage)}")
                        append("Transactions: $count")
                    }
                    
                    "Last 6 Months" -> {
                        val monthlyAverage = totalSpent / 6
                        appendLine("Last 6 Months")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        appendLine("Monthly avg: ₹${String.format("%.0f", monthlyAverage)}")
                        append("Transactions: $count")
                    }
                    
                    "This Year" -> {
                        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                        val monthlyAverage = if (currentMonth > 0) totalSpent / currentMonth else 0.0
                        
                        appendLine("This Year")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        appendLine("Monthly avg: ₹${String.format("%.0f", monthlyAverage)}")
                        append("Transactions: $count")
                    }
                    
                    else -> {
                        appendLine("Period: $period")
                        appendLine("Total: ₹${String.format("%.0f", totalSpent)}")
                        append("Transactions: $count")
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
                android.util.Log.d("DashboardFragment", "[SUCCESS] Successfully navigated to tab: $tabId")
            } else {
                // Fallback to normal navigation if MainActivity is not available
                android.util.Log.w("DashboardFragment", "[WARNING] MainActivity not available, using fallback navigation")
                findNavController().navigate(tabId)
            }
        } catch (e: Exception) {
            // Fallback to normal navigation if there's any error
            android.util.Log.e("DashboardFragment", "[ERROR] Error navigating to tab $tabId, using fallback", e)
            try {
                findNavController().navigate(tabId)
            } catch (fallbackError: Exception) {
                android.util.Log.e("DashboardFragment", "[ERROR] Fallback navigation also failed", fallbackError)
                Toast.makeText(requireContext(), "Navigation error. Please use bottom navigation.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // FIXED: Register broadcast receiver for new transactions, category updates, merchant category changes, AND inclusion state changes
        val newTransactionFilter = IntentFilter("com.expensemanager.NEW_TRANSACTION_ADDED")
        val categoryUpdateFilter = IntentFilter("com.expensemanager.CATEGORY_UPDATED")
        val inclusionStateFilter = IntentFilter("com.expensemanager.INCLUSION_STATE_CHANGED")
        val merchantCategoryFilter = IntentFilter("com.expensemanager.MERCHANT_CATEGORY_CHANGED")
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(newTransactionReceiver, newTransactionFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
            requireContext().registerReceiver(newTransactionReceiver, categoryUpdateFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
            requireContext().registerReceiver(newTransactionReceiver, inclusionStateFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
            requireContext().registerReceiver(newTransactionReceiver, merchantCategoryFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(newTransactionReceiver, newTransactionFilter)
            requireContext().registerReceiver(newTransactionReceiver, categoryUpdateFilter)
            requireContext().registerReceiver(newTransactionReceiver, inclusionStateFilter)
            requireContext().registerReceiver(newTransactionReceiver, merchantCategoryFilter)
        }
        Log.d("DashboardFragment", "Registered broadcast receiver for transactions, categories, merchant category changes, and inclusion states")
        
        // Refresh dashboard data when returning to this fragment
        // This ensures the dashboard reflects any changes made in the Messages screen
        loadDashboardData()
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister broadcast receiver to prevent memory leaks
        try {
            requireContext().unregisterReceiver(newTransactionReceiver)
            Log.d("DashboardFragment", "Unregistered broadcast receiver for new transactions")
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

/**
 * Data class for chart data points with proper labels
 */
data class ChartDataPoint(
    val index: Float,
    val value: Float,
    val label: String
)