package com.expensemanager.app.ui.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentDashboardBinding
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.data.repository.DashboardData
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.services.TransactionParsingService
import com.expensemanager.app.services.TransactionFilterService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import com.expensemanager.app.domain.usecase.transaction.AddTransactionUseCase
import com.expensemanager.app.utils.logging.StructuredLogger
import javax.inject.Inject
import java.util.*

/**
 * Dashboard Fragment - Main entry point for expense tracking overview
 *
 * Responsibilities:
 * - Fragment lifecycle management
 * - ViewModel observation and state updates
 * - Data loading coordination
 * - SMS sync orchestration
 * - Broadcast receiver management
 *
 * Delegates to:
 * - DashboardViewBinder: UI rendering
 * - DashboardTrendBinder: Chart logic
 * - DashboardActionHandler: User interactions and dialogs
 * - DashboardDataOrchestrator: Date calculations and data fetching
 */
@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // Hilt-injected ViewModel
    private val viewModel: DashboardViewModel by viewModels()

    // Hilt-injected dependencies
    @Inject lateinit var addTransactionUseCase: AddTransactionUseCase
    @Inject lateinit var transactionParsingService: TransactionParsingService
    @Inject lateinit var transactionFilterService: TransactionFilterService

    private val logger = StructuredLogger("DASHBOARD", DashboardFragment::class.java.simpleName)

    // Core dependencies
    private lateinit var repository: ExpenseRepository
    private lateinit var categoryManager: CategoryManager
    private lateinit var merchantAliasManager: MerchantAliasManager

    // Helper classes for UI responsibilities
    private lateinit var viewBinder: DashboardViewBinder
    private lateinit var trendBinder: DashboardTrendBinder
    private lateinit var actionHandler: DashboardActionHandler
    private lateinit var dataOrchestrator: DashboardDataOrchestrator

    private val dashboardRefreshRequests = MutableSharedFlow<DashboardRefreshRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var currentDashboardPeriod = "This Month"

    // Custom month selection variables
    private var customFirstMonth: Pair<Int, Int>? = null
    private var customSecondMonth: Pair<Int, Int>? = null

    // Broadcast receiver for transaction and category updates
    private val newTransactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.expensemanager.NEW_TRANSACTION_ADDED" -> {
                    val merchant = intent.getStringExtra("merchant") ?: "Unknown"
                    val amount = intent.getDoubleExtra("amount", 0.0)
                    logger.debug("onReceive","New transaction broadcast: $merchant - ₹${String.format("%.0f", amount)}")

                    requestDashboardRefresh(
                        reason = "broadcast_new_transaction",
                        onComplete = {
                            Toast.makeText(
                                requireContext(),
                                "New transaction added - Dashboard updated",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                "com.expensemanager.CATEGORY_UPDATED" -> {
                    val merchant = intent.getStringExtra("merchant") ?: "Unknown"
                    val category = intent.getStringExtra("category") ?: "Unknown"
                    logger.debug("onReceive","Category update broadcast: $merchant → $category")

                    requestDashboardRefresh(reason = "broadcast_category_update")
                }

                "com.expensemanager.INCLUSION_STATE_CHANGED" -> {
                    val includedCount = intent.getIntExtra("included_count", 0)
                    val totalAmount = intent.getDoubleExtra("total_amount", 0.0)
                    logger.debug("onReceive","Inclusion state change: $includedCount transactions, ₹${String.format("%.0f", totalAmount)} total")

                    requestDashboardRefresh(reason = "broadcast_inclusion_update")
                }

                "com.expensemanager.MERCHANT_CATEGORY_CHANGED" -> {
                    val merchantName = intent.getStringExtra("merchant_name") ?: "Unknown"
                    val displayName = intent.getStringExtra("display_name") ?: "Unknown"
                    val newCategory = intent.getStringExtra("new_category") ?: "Unknown"
                    logger.debug("onReceive","Merchant category change: '$merchantName' -> '$newCategory'")

                    requestDashboardRefresh(
                        reason = "broadcast_merchant_category_change",
                        onComplete = {
                            Toast.makeText(
                                requireContext(),
                                "Category updated for '$displayName' - Dashboard refreshed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }

    private data class DashboardRefreshRequest(
        val reason: String,
        val refreshViewModel: Boolean = false,
        val postRefreshAction: (() -> Unit)? = null
    )

    companion object {
        private const val DASHBOARD_REFRESH_DEBOUNCE_MS = 300L
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

        setupDebouncedRefreshPipeline()

        // Initialize core dependencies
        repository = ExpenseRepository.getInstance(requireContext())
        categoryManager = CategoryManager(requireContext())
        merchantAliasManager = MerchantAliasManager(requireContext())

        // Initialize helper classes
        val inclusionFilter = MerchantInclusionFilter(requireContext(), logger)
        viewBinder = DashboardViewBinder(
            binding = binding,
            context = requireContext(),
            logger = logger,
            merchantInclusionFilter = inclusionFilter,
            aliasManager = merchantAliasManager,
            onMerchantSelected = { merchant -> navigateToMerchant(merchant) },
            onCategorySelected = { category -> navigateToCategoryTransactions(category) }
        )
        viewBinder.initialize()

        trendBinder = DashboardTrendBinder(binding, requireContext(), repository, logger)
        trendBinder.setupChart()

        actionHandler = DashboardActionHandler(
            this, binding, viewModel, addTransactionUseCase, logger,
            onDataRefreshNeeded = {
                requestDashboardRefresh(
                    reason = "action_handler_refresh",
                    refreshViewModel = true
                )
            }
        )
        actionHandler.setupClickListeners()

        dataOrchestrator = DashboardDataOrchestrator(
            repository, transactionParsingService, transactionFilterService, categoryManager, logger
        )

        setupDashboardPeriodFilter()
        observeUIState()

        requestDashboardRefresh(reason = "initial_load", refreshViewModel = true)
    }

    private fun setupDebouncedRefreshPipeline() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardRefreshRequests
                    .onEach { request ->
                        logger.debug(
                            "setupDebouncedRefreshPipeline",
                            "Queued dashboard refresh (reason=${request.reason}, refreshViewModel=${request.refreshViewModel})"
                        )
                    }
                    .debounce(DASHBOARD_REFRESH_DEBOUNCE_MS)
                    .collect { request ->
                        logger.debug(
                            "setupDebouncedRefreshPipeline",
                            "Executing debounced dashboard refresh (reason=${request.reason})"
                        )

                        performDashboardReload()

                        if (request.refreshViewModel) {
                            viewModel.handleEvent(DashboardUIEvent.LoadData)
                        }

                        request.postRefreshAction?.invoke()
                    }
            }
        }
    }

    private fun requestDashboardRefresh(
        reason: String,
        refreshViewModel: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        val request = DashboardRefreshRequest(reason, refreshViewModel, onComplete)
        if (!dashboardRefreshRequests.tryEmit(request)) {
            viewLifecycleOwner.lifecycleScope.launch {
                dashboardRefreshRequests.emit(request)
            }
        }
    }

    /**
     * Observe ViewModel UI state
     */
    private fun observeUIState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        logger.debug("observeUIState","ViewModel UI State updated: loading=${state.isAnyLoading}, data=${state.dashboardData != null}, error=${state.hasError}")
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
            state.isInitialLoading -> viewBinder.showLoading()
            state.shouldShowError -> viewBinder.showError(state.error)
            state.shouldShowContent -> {
                viewBinder.showContent(state)
                refreshWeeklyTrend(state.dashboardPeriod)
            }
            state.shouldShowEmptyState -> viewBinder.showEmpty()
        }

        if (state.syncedTransactionsCount > 0) {
            viewBinder.showSyncToast(state.syncedTransactionsCount)
        }
    }

    private fun refreshWeeklyTrend(period: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val (startDate, endDate) = dataOrchestrator.getDateRangeForPeriod(period)
            trendBinder.updateTrend(startDate, endDate)
        }
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

    private fun navigateToCategoryTransactions(categoryName: String) {
        val bundle = Bundle().apply {
            putString("categoryName", categoryName)
        }
        findNavController().navigate(R.id.action_navigation_categories_to_navigation_category_transactions, bundle)
    }

    private fun setupDashboardPeriodFilter() {
        val dashboardPeriods = listOf(
            "This Month",
            "Last Month",
            "Custom Months..."
        )

        binding.btnDashboardPeriod.text = currentDashboardPeriod
        binding.btnDashboardPeriod.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Dashboard Period")
                .setItems(dashboardPeriods.toTypedArray()) { _, which ->
                    val selectedPeriod = dashboardPeriods[which]
                    if (selectedPeriod == "Custom Months...") {
                        actionHandler.showCustomMonthPickerDialog()
                    } else if (selectedPeriod != currentDashboardPeriod) {
                        currentDashboardPeriod = selectedPeriod
                        binding.btnDashboardPeriod.text = selectedPeriod
                        viewModel.handleEvent(DashboardUIEvent.ChangePeriod(selectedPeriod))
                        logger.debug("setupDashboardPeriodFilter","Period changed to: $selectedPeriod")
                    }
                }
                .show()
        }
    }

    /**
     * Load dashboard data for the current period - coordination method
     */
    private suspend fun performDashboardReload() {
        try {
            val (startDate, endDate) = dataOrchestrator.getDateRangeForPeriod(currentDashboardPeriod)

            logger.debug("performDashboardReload", "Loading dashboard for period: $currentDashboardPeriod")

            // Fetch dashboard data from repository
            val dashboardData = repository.getDashboardData(startDate, endDate)

            // Update dashboard UI with fetched data
            updateDashboardWithRepositoryData(dashboardData, startDate, endDate)

            // Update monthly comparison
            updateMonthlyComparisonFromRepository(startDate, endDate, currentDashboardPeriod)

            // Update weekly trend chart
            trendBinder.updateTrend(startDate, endDate)

        } catch (e: Exception) {
            logger.error("performDashboardReload", "Error loading dashboard data", e)
            viewBinder.showError("Failed to load dashboard data")
        }
    }

    /**
     * Update dashboard UI with repository data
     */
    private suspend fun updateDashboardWithRepositoryData(dashboardData: DashboardData, startDate: Date, endDate: Date) {
        val totalSpent = dashboardData.totalSpent
        val transactionCount = dashboardData.transactionCount

        // Calculate balance
        val balance = calculateBalance(dashboardData)

        logger.debug("updateDashboardWithRepositoryData",
            "Updating dashboard: spent=₹$totalSpent, balance=₹$balance, count=$transactionCount")

        // Update summary - check if budget exists
        val hasBudget = viewModel.uiState.value.monthlyBudget > 0
        viewBinder.renderSummary(totalSpent, balance, transactionCount, hasBudget)

        // Update categories
        val categorySpending = dashboardData.topCategories.map { category ->
            CategorySpending(
                categoryName = category.category_name,
                amount = category.total_amount,
                categoryColor = category.color,
                count = category.transaction_count
            )
        }
        viewBinder.renderCategories(categorySpending)

        // Update merchants
        viewBinder.renderMerchantsWithCategory(dashboardData.topMerchantsWithCategory)
    }

    /**
     * Calculate budget percentage from dashboard data
     */
    private fun calculateBalance(dashboardData: DashboardData): Double {
        // Check if we're in "This Month" period and have monthly budget
        if (currentDashboardPeriod == "This Month") {
            val monthlyBudget = viewModel.uiState.value.monthlyBudget
            if (monthlyBudget > 0) {
                // Return budget percentage
                return (dashboardData.totalSpent / monthlyBudget) * 100
            }
        }
        // No budget set - return spent amount
        return dashboardData.totalSpent
    }

    /**
     * Update monthly comparison from repository
     */
    private suspend fun updateMonthlyComparisonFromRepository(currentStart: Date, currentEnd: Date, period: String) {
        try {
            val (prevStart, prevEnd) = dataOrchestrator.getPreviousPeriodRange(period, currentStart, currentEnd)

            val currentData = repository.getDashboardData(currentStart, currentEnd)
            val previousData = repository.getDashboardData(prevStart, prevEnd)

            val currentAmount = currentData.totalSpent
            val previousAmount = previousData.totalSpent

            val change = currentAmount - previousAmount
            val percentChange = if (previousAmount > 0) {
                (change / previousAmount) * 100
            } else {
                0.0
            }

            val comparisonPeriodName = dataOrchestrator.getComparisonPeriodName(period)

            val comparison = MonthlyComparison(
                currentLabel = period,
                previousLabel = comparisonPeriodName,
                currentAmount = currentAmount,
                previousAmount = previousAmount,
                percentageChange = percentChange
            )

            viewBinder.renderMonthlyComparison(comparison)

        } catch (e: Exception) {
            logger.error("updateMonthlyComparisonFromRepository", "Error updating monthly comparison", e)
        }
    }

    /**
     * Perform SMS sync
     */
    private fun performSMSSync() {
        lifecycleScope.launch {
            try {
                logger.debug("performSMSSync", "Starting SMS sync")
                val syncedCount = performRepositoryBasedSync()
                if (syncedCount > 0) {
                    requestDashboardRefresh(
                        reason = "sms_repository_sync",
                        refreshViewModel = true
                    )
                }
            } catch (e: Exception) {
                logger.error("performSMSSync", "Error during SMS sync", e)
                Toast.makeText(requireContext(), "Failed to sync SMS: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Perform repository-based SMS sync
     */
    private suspend fun performRepositoryBasedSync(): Int {
        return try {
            logger.debug("performRepositoryBasedSync", "Attempting SMS sync through repository...")

            val syncedCount = repository.syncNewSMS()
            logger.debug("performRepositoryBasedSync", "Repository sync completed: $syncedCount new transactions")

            if (syncedCount > 0) {
                Toast.makeText(
                    requireContext(),
                    "Synced $syncedCount new transactions from SMS!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                logger.debug("performRepositoryBasedSync", "No new transactions found during sync")
                Toast.makeText(
                    requireContext(),
                    "No new transactions to sync",
                    Toast.LENGTH_SHORT
                ).show()
            }

            syncedCount
        } catch (e: SecurityException) {
            logger.error("performRepositoryBasedSync", "Permission denied for SMS access", e)
            Toast.makeText(
                requireContext(),
                "SMS permission required. Please grant permission in settings.",
                Toast.LENGTH_LONG
            ).show()
            throw e
        } catch (e: Exception) {
            logger.error("performRepositoryBasedSync", "Error during repository-based sync", e)
            Toast.makeText(
                requireContext(),
                "Sync failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            throw e
        }
    }


    override fun onResume() {
        super.onResume()

        // Register broadcast receivers
        val filter = IntentFilter().apply {
            addAction("com.expensemanager.NEW_TRANSACTION_ADDED")
            addAction("com.expensemanager.CATEGORY_UPDATED")
            addAction("com.expensemanager.INCLUSION_STATE_CHANGED")
            addAction("com.expensemanager.MERCHANT_CATEGORY_CHANGED")
        }

        try {
            requireContext().registerReceiver(newTransactionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            logger.debug("onResume", "Broadcast receivers registered successfully")
        } catch (e: Exception) {
            logger.error("onResume", "Failed to register broadcast receivers", e)
        }

        // Refresh dashboard data when returning to this screen
        // This ensures we pick up any changes made in other tabs (like Messages)
        requestDashboardRefresh(reason = "onResume")
    }

    override fun onPause() {
        super.onPause()

        // Unregister broadcast receivers
        try {
            requireContext().unregisterReceiver(newTransactionReceiver)
            logger.debug("onPause", "Broadcast receivers unregistered successfully")
        } catch (e: IllegalArgumentException) {
            logger.warn("onPause", "Receivers were not registered", e.message)
        } catch (e: Exception) {
            logger.error("onPause", "Error unregistering broadcast receivers", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
