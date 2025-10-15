package com.expensemanager.app.ui.insights

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.expensemanager.app.R
import com.expensemanager.app.data.models.InsightType
import com.expensemanager.app.data.repository.AIInsightsRepository
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.databinding.FragmentInsightsBinding
import com.expensemanager.app.domain.insights.GetAIInsightsUseCase
import com.expensemanager.app.domain.insights.InsightsUseCaseFactory
import com.expensemanager.app.domain.usecase.category.GetCategorySpendingUseCase
import com.expensemanager.app.services.DateRangeService
import com.expensemanager.app.services.DateRangeService.Companion.DateRangeType
import com.expensemanager.app.services.TimeSeriesAggregationService
import com.expensemanager.app.services.TimeSeriesAggregationService.TimeSeriesData
import com.expensemanager.app.services.DateRangeService.Companion.TimeAggregation
import com.expensemanager.app.services.ChartConfigurationService
import com.google.android.material.card.MaterialCardView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import java.util.*
import java.text.SimpleDateFormat
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.expensemanager.app.utils.logging.StructuredLogger
import kotlinx.coroutines.launch

class InsightsFragment : Fragment() {

    private var _binding: FragmentInsightsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: InsightsViewModel

    // Chart setup state tracking to prevent duplicates
    private var isChartSetupInProgress = false

    // Service dependencies
    private lateinit var dateRangeService: DateRangeService
    private lateinit var timeSeriesAggregationService: TimeSeriesAggregationService
    private lateinit var chartConfigurationService: ChartConfigurationService

    private lateinit var viewBinder: InsightsViewBinder

    // Enhanced Analytics Components
    private lateinit var filtersCard: View
    private lateinit var chartsCard: View

    // Filter conditions for charts
    private var currentFilters = ChartFilterConditions()

    // Flag to track if user has explicitly set time aggregation
    private var userHasSetTimeAggregation = false
    private val logger = StructuredLogger("InsightsFragment", "InsightsFragment")

    companion object {
        private const val TAG = "InsightsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews()
        setupViewModel()
        setupUI()
        observeUIState()
        setupClickListeners()
        setupAnalyticsFilters()
        setupInteractiveCharts()
        setupDefaultFilters()
    }


    /**
     * Initialize view references
     */
    private fun initializeViews() {
        viewBinder = InsightsViewBinder(binding)

        // Initialize enhanced analytics components
        filtersCard = binding.root.findViewById(R.id.cardFilters)
        chartsCard = binding.root.findViewById(R.id.cardCharts)
    }

    /**
     * Setup ViewModel with dependencies
     */
    private fun setupViewModel() {
        try {
            // Create dependencies
            val expenseRepository = ExpenseRepository.getInstance(requireContext())
            val database = com.expensemanager.app.data.database.ExpenseDatabase.getDatabase(requireContext())
            val aiCallDao = database.aiCallDao()
            val userDao = database.userDao()
            val aiInsightsRepository = AIInsightsRepository.getInstance(requireContext(), expenseRepository, aiCallDao, userDao)
            val getAIInsightsUseCase = InsightsUseCaseFactory.createGetAIInsightsUseCase(aiInsightsRepository)

            // Create service dependencies
            val dateRangeService = DateRangeService()
            val timeSeriesAggregationService = TimeSeriesAggregationService(dateRangeService)
            val chartConfigurationService = ChartConfigurationService(requireContext())

            // Store services for use in fragment
            this.dateRangeService = dateRangeService
            this.timeSeriesAggregationService = timeSeriesAggregationService
            this.chartConfigurationService = chartConfigurationService

            // Create ViewModel
            val factory = InsightsViewModelFactory(getAIInsightsUseCase)
            viewModel = factory.create()

            logger.debug("setupViewModel","ViewModel setup completed")
        } catch (e: Exception) {
            logger.error("setupViewModel", "Error setting up ViewModel",e)
            showError("Failed to initialize AI insights")
        }
    }

    /**
     * Setup initial UI elements and loading states
     */
    private fun setupUI() {
        setupSwipeRefresh()
        setupErrorStateButtons()
        setupEmptyStateButton()

        logger.debug("setupUI","UI setup completed")
    }

    /**
     * Setup swipe to refresh functionality
     */
    private fun setupSwipeRefresh() {
        viewBinder.bindSwipeRefresh {
            logger.debug("setupSwipeRefresh","Pull-to-refresh triggered")
            viewModel.handleEvent(InsightsUIEvent.Refresh)
        }

        // Customize refresh colors
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.secondary,
            R.color.success
        )
    }

    /**
     * Setup error state buttons
     */
    private fun setupErrorStateButtons() {
        viewBinder.bindErrorActions(
            onRetry = {
                logger.debug("setupErrorStateButtons","Retry button clicked")
                viewModel.handleEvent(InsightsUIEvent.Retry)
            },
            onUseSample = {
                logger.debug("setupErrorStateButtons","Use sample data button clicked")
                Toast.makeText(requireContext(), "Loading sample insights...", Toast.LENGTH_SHORT).show()
            }
        )
    }

    /**
     * Setup empty state button
     */
    private fun setupEmptyStateButton() {
        viewBinder.bindEmptyState {
            logger.debug("setupEmptyStateButton","Go to messages button clicked")
            logger.debug("setupEmptyStateButton","Navigate to messages requested")
            Toast.makeText(requireContext(), "Navigate to Messages tab to add transactions", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Observe ViewModel UI state and update UI accordingly
     */
    private fun observeUIState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Observe main UI state
                launch {
                    viewModel.uiState.collect { state ->
                        logger.debug("observeUIState","UI State updated: loading=${state.isLoading}, insights=${state.insights.size}, error=${state.hasError}")
                        updateUI(state)
                    }
                }

                // Observe spending forecast data
                launch {
                    viewModel.spendingForecastData.collect { forecastData ->
                        updateSpendingForecast(forecastData)
                    }
                }

                // Observe pattern alerts
                launch {
                    viewModel.patternAlerts.collect { alerts ->
                        updatePatternAlerts(alerts)
                    }
                }

                // Observe savings opportunities
                launch {
                    viewModel.savingsOpportunities.collect { savingsData ->
                        updateSavingsOpportunities(savingsData)
                    }
                }

                // Observe budget recommendations
                launch {
                    viewModel.budgetRecommendations.collect { recommendations ->
                        updateBudgetOptimization(recommendations)
                    }
                }
            }
        }
    }

    /**
     * Setup click listeners for interactive elements
     */
    private fun setupClickListeners() {
        // Card click listeners for expansion (example)
        binding.root.findViewById<MaterialCardView>(R.id.cardSpendingForecast)?.setOnClickListener {
            // Handle spending forecast card click
            logger.debug("setupClickListeners", "Spending forecast card clicked")
        }

        // Action button listeners
        binding.root.findViewById<View>(R.id.btnCreateSavingsPlan)?.setOnClickListener {
            viewModel.handleEvent(InsightsUIEvent.ActionClicked(
                insight = viewModel.uiState.value.insights.firstOrNull() ?: return@setOnClickListener,
                action = "create savings plan"
            ))
        }
    }

    /**
     * Update UI based on state changes
     */
    private fun updateUI(state: InsightsUIState) {
        viewBinder.updateSwipeRefreshing(state.isRefreshing)

        when {
            state.isLoading && state.insights.isEmpty() -> {
                viewBinder.showLoadingState()
            }

            state.shouldShowError -> {
                viewBinder.showErrorState(state.error)
            }

            state.shouldShowContent -> {
                viewBinder.showContentState()
                updateInsightsContent(state)
            }

            state.shouldShowEmptyState -> {
                viewBinder.showEmptyState()
            }
        }

        if (state.showingSampleData && state.shouldShowContent) {
            showSampleDataIndicator()
        }
    }

    /**
     * Update insights content in UI
     */
    private fun updateInsightsContent(state: InsightsUIState) {
        logger.debug("updateInsightsContent", "Updating insights content with ${state.insights.size} insights")

        // Show content sections
        binding.root.visibility = View.VISIBLE
    }

    /**
     * Update spending forecast section with direct API data
     */
    private fun updateSpendingForecast(forecastData: SpendingForecastUIData) {
        try {
            // Get direct insight from API instead of calculated data
            val forecastInsights = viewModel.uiState.value.getInsightsByType(InsightType.SPENDING_FORECAST)
            if (forecastInsights.isNotEmpty()) {
                val forecastInsight = forecastInsights.first()

                // Show API description directly (contains all the forecast info)
                binding.root.findViewById<TextView>(R.id.tvSpendingAmount)?.text = forecastInsight.description
                binding.root.findViewById<TextView>(R.id.tvSpendingAdvice)?.text = forecastInsight.actionableAdvice

                logger.debug("updateSpendingForecast", "Spending forecast updated with direct API data")
            } else {
                // Fallback to calculated data if no API insight available
                binding.root.findViewById<TextView>(R.id.tvSpendingAmount)?.text =
                    "Based on your current spending pattern, you're likely to spend ₹${String.format("%.0f", forecastData.projectedAmount)} this month."

                binding.root.findViewById<TextView>(R.id.tvSpendingAdvice)?.text = forecastData.advice.ifEmpty {
                    "That's ${String.format("%.0f", forecastData.comparisonToLastMonth)}% more than last month."
                }
            }

            // Update progress bar with API data if available
            binding.root.findViewById<View>(R.id.progressSpending)?.let { progressBar ->
                logger.debug("updateSpendingForecast", "Updated spending progress: ${forecastData.progressPercentage}%")
            }

        } catch (e: Exception) {
            logger.error("updateSpendingForecast", "Error updating spending forecast", e)
        }
    }

    /**
     * Update pattern alerts section with direct API data
     */
    private fun updatePatternAlerts(alerts: List<PatternAlertUIData>) {
        try {
            // Get pattern alert insights directly from API
            val patternInsights = viewModel.uiState.value.getInsightsByType(InsightType.PATTERN_ALERT)

            if (patternInsights.isNotEmpty()) {
                // Show first alert directly from API
                binding.root.findViewById<TextView>(R.id.tvPatternAlert1)?.text =
                    patternInsights[0].description

                // Show second alert if available
                if (patternInsights.size > 1) {
                    binding.root.findViewById<TextView>(R.id.tvPatternAlert2)?.text =
                        patternInsights[1].description
                } else {
                    binding.root.findViewById<TextView>(R.id.tvPatternAlert2)?.text =
                        "No additional alerts"
                }

                logger.debug("updatePatternAlerts", "Pattern alerts updated with direct API data: ${patternInsights.size} alerts")
            } else {
                // Show no alerts message
                binding.root.findViewById<TextView>(R.id.tvPatternAlert1)?.text =
                    "No pattern alerts from API"
                binding.root.findViewById<TextView>(R.id.tvPatternAlert2)?.text =
                    "All spending patterns appear normal"
            }
        } catch (e: Exception) {
            logger.error("updatePatternAlerts", "Error updating pattern alerts", e)
        }
    }

    /**
     * Update savings opportunities section with direct API data
     */
    private fun updateSavingsOpportunities(savingsData: SavingsOpportunityUIData) {
        try {
            // Keep the calculated savings amounts (API doesn't provide specific amounts)
            binding.root.findViewById<TextView>(R.id.tvMonthlySavings)?.text =
                "₹${String.format("%.0f", savingsData.monthlyPotential)}"

            binding.root.findViewById<TextView>(R.id.tvYearlySavings)?.text =
                "₹${String.format("%.0f", savingsData.yearlyImpact)}"

            // But use actual API recommendations for the text
            val savingsInsights = viewModel.uiState.value.getInsightsByType(InsightType.SAVINGS_OPPORTUNITY)
            if (savingsInsights.isNotEmpty()) {
                // Show first API recommendation
                binding.root.findViewById<TextView>(R.id.tvSavingsRecommendation1)?.text =
                    savingsInsights[0].description

                // Show second recommendation if available from API
                if (savingsInsights.size > 1) {
                    binding.root.findViewById<TextView>(R.id.tvSavingsRecommendation2)?.text =
                        savingsInsights[1].description
                } else {
                    // Hide second recommendation or show placeholder
                    binding.root.findViewById<TextView>(R.id.tvSavingsRecommendation2)?.text =
                        "Continue monitoring spending for more opportunities"
                }

                logger.debug("updateSavingsOpportunities", "Savings opportunities updated with API recommendations: ${savingsInsights.size}")
            } else {
                // Fallback to calculated data
                if (savingsData.recommendations.isNotEmpty()) {
                    binding.root.findViewById<TextView>(R.id.tvSavingsRecommendation1)?.text =
                        savingsData.recommendations.firstOrNull() ?: "No specific recommendations available"

                    if (savingsData.recommendations.size > 1) {
                        binding.root.findViewById<TextView>(R.id.tvSavingsRecommendation2)?.text =
                            savingsData.recommendations[1]
                    }
                }
            }

        } catch (e: Exception) {
            logger.error("updateSavingsOpportunities", "Error updating savings opportunities", e)
        }
    }

    /**
     * Show/hide loading state (deprecated - use showShimmerLoading instead)
     */
    @Deprecated("Use showShimmerLoading instead")
    private fun showLoadingState(isLoading: Boolean) {
        if (isLoading) viewBinder.showLoadingState() else viewBinder.showContentState()
    }

    /**
     * Update budget optimization section with API recommendations
     */
    private fun updateBudgetOptimization(recommendations: List<String>) {
        try {
            // Update first recommendation if available
            if (recommendations.isNotEmpty()) {
                binding.root.findViewById<TextView>(R.id.tvSavingsRecommendation1)?.text =
                    recommendations.first()
                logger.debug("updateBudgetOptimization", "Updated first budget recommendation: ${recommendations.first()}")
            }

            // Update second recommendation if available
            if (recommendations.size > 1) {
                binding.root.findViewById<TextView>(R.id.tvSavingsRecommendation2)?.text =
                    recommendations[1]
                logger.debug("updateBudgetOptimization", "Updated second budget recommendation: ${recommendations[1]}")
            } else {
                // Hide second recommendation if only one available
                binding.root.findViewById<TextView>(R.id.tvSavingsRecommendation2)?.visibility =
                    android.view.View.GONE
            }

            logger.debug("updateBudgetOptimization", "Budget optimization updated with ${recommendations.size} recommendations")
        } catch (e: Exception) {
            logger.error("updateBudgetOptimization", "Error updating budget optimization", e)
        }
    }

    /**
     * Show error message via Toast (for additional feedback)
     */
    private fun showError(message: String?) {
        if (message != null) {
            Toast.makeText(requireContext(), "Insights: $message", Toast.LENGTH_LONG).show()
            logger.error("showError", "Showing error toast: $message",null)
        }
    }

    /**
     * Show indicator that we're using sample data
     */
    private fun showSampleDataIndicator() {
        // Could show a small badge or toast indicating sample data
        logger.debug("showSampleDataIndicator", "Using sample insights data")
    }

    private fun setupDefaultFilters() {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        // Default to This Month
        currentFilters.timePeriod = "This Month"
        binding.root.findViewById<TextView>(R.id.btnDateRange)?.text = "This Month"

        // Smart aggregation for This Month
        currentFilters.timeAggregation = if (currentDay <= 7) {
            "Daily"
        } else {
            "Weekly"
        }
        binding.root.findViewById<TextView>(R.id.btnTimePeriod)?.text = currentFilters.timeAggregation

        // Apply the default filters
        applyDateRangeFilter("This Month")
    }

    override fun onDestroyView() {
        viewBinder.stopShimmer()
        super.onDestroyView()

        logger.debug("onDestroyView", "Fragment view destroyed")
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        viewBinder.stopShimmer()
    }

    override fun onResume() {
        super.onResume()
        viewBinder.restartShimmerIfVisible()

        // Refresh charts when fragment becomes visible if content is already loaded
        if (::viewModel.isInitialized && viewBinder.isContentVisible()) {
            logger.debug("onResume", "Fragment resumed with content visible - refreshing charts to ensure proper rendering")
            viewLifecycleOwner.lifecycleScope.launch {
                // Small delay to ensure fragment is fully visible
                kotlinx.coroutines.delay(100)
                updateChartsWithFilteredData()
            }
        }

        // Force refresh if no data is currently displayed
        if (::viewModel.isInitialized && viewModel.uiState.value.insights.isEmpty()) {
            logger.debug("onResume", "Fragment resumed with no data - triggering refresh")
            viewModel.handleEvent(InsightsUIEvent.Refresh)
        }
    }

    // ===== ENHANCED ANALYTICS FUNCTIONALITY =====

    /**
     * Setup enhanced filter functionality
     */
    private fun setupAnalyticsFilters() {
        logger.debug("setupAnalyticsFilters", "Setting up enhanced analytics filters")

        // First ensure the filter card is visible
        val cardFilters = binding.root.findViewById<View>(R.id.cardFilters)
        if (cardFilters != null) {
            cardFilters.visibility = View.VISIBLE
            logger.debug("setupAnalyticsFilters", "Filter card found and set to visible")
        } else {
            logger.warn("setupAnalyticsFilters", "Filter card not found in layout")
        }

        // Date Range Filter
        val btnDateRange = binding.root.findViewById<View>(R.id.btnDateRange)
        if (btnDateRange != null) {
            btnDateRange.setOnClickListener {
                logger.debug("setupAnalyticsFilters", "Date range button clicked")
                showDateRangeDialog()
            }
            logger.debug("setupAnalyticsFilters", "Date range button found and listener set")
        } else {
            logger.warn("setupAnalyticsFilters", "Date range button not found in layout")
        }

        // Time Period Filter
        val btnTimePeriod = binding.root.findViewById<View>(R.id.btnTimePeriod)
        if (btnTimePeriod != null) {
            btnTimePeriod.setOnClickListener {
                logger.debug("setupAnalyticsFilters", "Time period button clicked")
                showTimePeriodDialog()
            }
            logger.debug("setupAnalyticsFilters", "Time period button found and listener set")
        } else {
            logger.warn("setupAnalyticsFilters", "Time period button not found in layout")
        }

        // Category Filter
        val btnCategoryFilter = binding.root.findViewById<View>(R.id.btnCategoryFilter)
        if (btnCategoryFilter != null) {
            btnCategoryFilter.setOnClickListener {
                logger.debug("setupAnalyticsFilters", "Category filter button clicked")
                showCategoryFilterDialog()
            }
            logger.debug("setupAnalyticsFilters", "Category filter button found and listener set")
        } else {
            logger.warn("setupAnalyticsFilters", "Category filter button not found in layout")
        }

        // Merchant Filter
        val btnMerchantFilter = binding.root.findViewById<View>(R.id.btnMerchantFilter)
        if (btnMerchantFilter != null) {
            btnMerchantFilter.setOnClickListener {
                logger.debug("setupAnalyticsFilters", "Merchant filter button clicked")
                showMerchantFilterDialog()
            }
            logger.debug("setupAnalyticsFilters", "Merchant filter button found and listener set")
        } else {
            logger.warn("setupAnalyticsFilters", "Merchant filter button not found in layout")
        }

        // Amount Range Slider
        val rangeSlider = binding.root.findViewById<com.google.android.material.slider.RangeSlider>(R.id.rangeSliderAmount)
        if (rangeSlider != null) {
            rangeSlider.addOnChangeListener {
                    _, _, _ ->
                updateAmountRangeDisplay(rangeSlider.values)

                // Update filter conditions
                currentFilters.minAmount = rangeSlider.values[0].toDouble()
                currentFilters.maxAmount = rangeSlider.values[1].toDouble()

                logger.debug("setupAnalyticsFilters", "Amount range updated: ₹${currentFilters.minAmount} - ₹${currentFilters.maxAmount}")

                // Trigger analytics refresh with new filters
                applyFiltersAndRefresh()
            }
            logger.debug("setupAnalyticsFilters", "Range slider found and listener set")
        } else {
            logger.warn("setupAnalyticsFilters", "Range slider not found in layout")
        }
    }


    /**
     * Show date range selection dialog
     */
    private fun showDateRangeDialog() {
        val dateRanges = resources.getStringArray(R.array.date_ranges)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Date Range")
            .setItems(dateRanges) { _, which ->
                val selectedRange = dateRanges[which]
                binding.root.findViewById<android.widget.TextView>(R.id.btnDateRange)?.text = selectedRange

                // Apply the selected date range
                applyDateRangeFilter(selectedRange)
            }
            .show()
    }

    /**
     * Show time period selection dialog
     */
    private fun showTimePeriodDialog() {
        // PIE_CHART_FIX: Check if we're currently in PIE Chart tab (position 0)
        val tabLayout = binding.root.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutCharts)
        val currentTab = tabLayout?.selectedTabPosition ?: 0

        if (currentTab == 0) {
            // We're in PIE chart tab - time period doesn't apply to pie charts
            logger.debug("showTimePeriodDialog", "Time period dialog blocked - currently in PIE Chart tab")
            return
        }

        logger.debug("showTimePeriodDialog", "Showing time period dialog for tab position: $currentTab")
        val timePeriods = resources.getStringArray(R.array.time_periods)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Time Period")
            .setItems(timePeriods) { _, which ->
                val selectedPeriod = timePeriods[which]
                binding.root.findViewById<android.widget.TextView>(R.id.btnTimePeriod)?.text = selectedPeriod

                // Apply the selected time period
                applyTimePeriodFilter(selectedPeriod)
            }
            .show()
    }

    /**
     * Show category filter dialog
     */
    private fun showCategoryFilterDialog() {
        logger.debug("showCategoryFilterDialog", "Category filter dialog requested")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())
                val categories = expenseRepository.getAllCategoriesSync()

                logger.debug("showCategoryFilterDialog", "Loading categories from DB - Found ${categories.size} categories")
                categories.forEach { category ->
                    logger.debug("showCategoryFilterDialog", "Category from DB: ${category.name} (ID: ${category.id})")
                }

                val categoryNames = categories.map { category -> category.name }.toTypedArray()
                val selectedItems = BooleanArray(categoryNames.size) { index ->
                    currentFilters.selectedCategories.contains(categoryNames[index])
                }

                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Select Categories")
                    .setMultiChoiceItems(categoryNames, selectedItems) { _, which: Int, isChecked: Boolean ->
                        // Handle selection
                    }
                    .setPositiveButton("Apply") { _: android.content.DialogInterface, _: Int ->
                        val selectedCategories = mutableListOf<String>()
                        for (i in selectedItems.indices) {
                            if (selectedItems[i]) {
                                selectedCategories.add(categoryNames[i])
                            }
                        }
                        currentFilters.selectedCategories = selectedCategories

                        logger.debug("showCategoryFilterDialog", "Selected categories: ${selectedCategories.joinToString()}")
                        applyFiltersAndRefresh()
                    }
                    .setNegativeButton("Clear") { _: android.content.DialogInterface, _: Int ->
                        currentFilters.selectedCategories = emptyList()
                        applyFiltersAndRefresh()
                    }
                    .show()

            } catch (e: Exception) {
                logger.error("showCategoryFilterDialog", "Error loading categories", e)
                Toast.makeText(requireContext(), "Error loading categories", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Show merchant filter dialog
     */
    private fun showMerchantFilterDialog() {
        logger.debug("showMerchantFilterDialog", "Merchant filter dialog requested")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())
                val merchants = expenseRepository.getAllMerchants()

                val merchantNames = merchants.map { merchant -> merchant.displayName }.toTypedArray()
                val selectedItems = BooleanArray(merchantNames.size) { index ->
                    currentFilters.selectedMerchants.contains(merchants[index].normalizedName)
                }

                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Select Merchants")
                    .setMultiChoiceItems(merchantNames, selectedItems) { _, which: Int, isChecked: Boolean ->
                        // Handle selection
                    }
                    .setPositiveButton("Apply") { _: android.content.DialogInterface, _: Int ->
                        val selectedMerchants = mutableListOf<String>()
                        for (i in selectedItems.indices) {
                            if (selectedItems[i]) {
                                // Store normalized name for filtering, not display name
                                selectedMerchants.add(merchants[i].normalizedName)
                            }
                        }
                        currentFilters.selectedMerchants = selectedMerchants

                        logger.debug("showMerchantFilterDialog", "Selected merchants: ${selectedMerchants.joinToString()}")
                        applyFiltersAndRefresh()
                    }
                    .setNegativeButton("Clear") { _: android.content.DialogInterface, _: Int ->
                        currentFilters.selectedMerchants = emptyList()
                        applyFiltersAndRefresh()
                    }
                    .show()

            } catch (e: Exception) {
                logger.error("showMerchantFilterDialog", "Error loading merchants", e)
                Toast.makeText(requireContext(), "Error loading merchants", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Update amount range display
     */
    private fun updateAmountRangeDisplay(values: List<Float>) {
        val minAmount = values[0].toInt()
        val maxAmount = values[1].toInt()
        val displayText = if (maxAmount >= 10000) {
            "${minAmount/1000}K-${maxAmount/1000}K"
        } else {
            "$minAmount-$maxAmount"
        }

        binding.root.findViewById<android.widget.TextView>(R.id.tvAmountRange)?.text = displayText
        logger.debug("updateAmountRangeDisplay", "Amount range updated: $displayText")
    }

    /**
     * Apply filters to transaction list
     */
    private suspend fun applyFiltersToTransactions(
        transactions: List<com.expensemanager.app.data.entities.TransactionEntity>,
        filters: ChartFilterConditions
    ): List<com.expensemanager.app.data.entities.TransactionEntity> {
        return transactions.filter { transaction ->
            // Category filter - check through merchant-category relationship
            val categoryMatch = if (filters.selectedCategories.isNotEmpty()) {
                val categoryName = getCategoryNameForTransaction(transaction)
                filters.selectedCategories.contains(categoryName)
            } else true

            // Merchant filter - use normalized merchant name
            val merchantMatch = if (filters.selectedMerchants.isNotEmpty()) {
                filters.selectedMerchants.contains(transaction.normalizedMerchant)
            } else true

            // Amount range filter
            val amountMatch = transaction.amount >= filters.minAmount &&
                    transaction.amount <= filters.maxAmount

            categoryMatch && merchantMatch && amountMatch
        }
    }

    /**
     * Get category name for a transaction through merchant relationship
     */
    private suspend fun getCategoryNameForTransaction(transaction: com.expensemanager.app.data.entities.TransactionEntity): String {
        return try {
            val expenseRepository = ExpenseRepository.getInstance(requireContext())
            val merchantWithCategory = expenseRepository.getMerchantWithCategory(transaction.normalizedMerchant)
            merchantWithCategory?.category_name ?: "Other"
        } catch (e: Exception) {
            logger.error("getCategoryNameForTransaction", "Error getting category for transaction", e)
            "Other"
        }
    }

    /**
     * Apply date range filter with smart time aggregation update
     */
    private fun applyDateRangeFilter(dateRange: String) {
        logger.debug("applyDateRangeFilter", "Applying date range filter: $dateRange")

        try {
            val dateRangeType = when (dateRange) {
                "This Month" -> DateRangeType.CURRENT_MONTH
                "Last Month" -> DateRangeType.LAST_MONTH
                "Last 7 Days" -> DateRangeType.LAST_7_DAYS
                "Last 30 Days" -> DateRangeType.LAST_30_DAYS
                "Last 3 Months" -> DateRangeType.LAST_3_MONTHS
                "Last 6 Months" -> DateRangeType.LAST_6_MONTHS
                "This Year" -> DateRangeType.THIS_YEAR
                else -> null
            }

            if (dateRangeType != null) {
                val (startDate, endDate) = dateRangeService.getDateRange(dateRangeType)
                currentFilters.startDate = startDate
                currentFilters.endDate = endDate

                // WEEKLY_FIX: Only apply smart aggregation if user hasn't explicitly set time aggregation
                if (!userHasSetTimeAggregation) {
                    currentFilters.timeAggregation = getSmartTimeAggregation(startDate, endDate)
                    binding.root.findViewById<TextView>(R.id.btnTimePeriod)?.text = currentFilters.timeAggregation
                    logger.debug("applyDateRangeFilter", "Applied smart time aggregation: ${currentFilters.timeAggregation}")
                } else {
                    logger.debug("applyDateRangeFilter", "Preserving user-selected time aggregation: ${currentFilters.timeAggregation}")
                }

                logger.debug("applyDateRangeFilter", "Applied ${dateRange} filter: ${startDate} to ${endDate}")
                logger.debug("applyDateRangeFilter", "Updated time aggregation to: ${currentFilters.timeAggregation}")
            } else {
                currentFilters.startDate = null
                currentFilters.endDate = null
                logger.debug("applyDateRangeFilter", "No specific date range filter applied for: $dateRange")
            }
        } catch (e: Exception) {
            logger.error("applyDateRangeFilter", "Error applying date range filter", e)
            currentFilters.startDate = null
            currentFilters.endDate = null
        }

        currentFilters.timePeriod = dateRange
        binding.root.findViewById<TextView>(R.id.btnDateRange)?.text = dateRange
        applyFiltersAndRefresh()
    }

    /**
     * Apply time period filter (user override of smart aggregation)
     */
    private fun applyTimePeriodFilter(timePeriod: String) {
        logger.debug("applyTimePeriodFilter", "User overriding time period filter: $timePeriod")

        // User can override smart aggregation - this takes precedence
        currentFilters.timeAggregation = timePeriod
        userHasSetTimeAggregation = true  // Mark that user explicitly set this

        // Log for debugging
        logger.debug("applyTimePeriodFilter", "User manually set timeAggregation to: ${currentFilters.timeAggregation}")

        // Apply the filter and refresh the charts
        applyFiltersAndRefresh()
    }

    /**
     * Apply all current filters and refresh analytics
     */
    private fun applyFiltersAndRefresh() {
        logger.debug("applyFiltersAndRefresh", "Applying all filters and refreshing analytics")

        // Show loading state
        viewBinder.showLoadingState()

        // Trigger refresh with current filters
        viewModel.handleEvent(InsightsUIEvent.Refresh)

        // Update chart data with filtered results
        updateChartsWithFilteredData()
    }

    /**
     * Setup interactive charts
     */
    private fun setupInteractiveCharts() {
        logger.debug("setupInteractiveCharts", "Setting up interactive charts")

        // Setup chart tabs
        setupChartTabs()

        // Initialize chart view pager
        setupChartViewPager()

        // Chart summary stats will be updated when real data is loaded
    }

    /**
     * Setup chart tabs
     */
    private fun setupChartTabs() {
        val tabLayout = binding.root.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutCharts)

        tabLayout?.let { tabs ->
            tabs.removeAllTabs()
            tabs.addTab(tabs.newTab().setText("Categories").setIcon(R.drawable.ic_chart))
            tabs.addTab(tabs.newTab().setText("Monthly").setIcon(R.drawable.ic_chart))
            // UI_SIMPLIFICATION: Hide Trends tab to keep only PIE and BAR charts

            tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                    tab?.let {
                        updateChartViewPager(it.position)
                    }
                }
                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            })
        }
    }

    /**
     * Setup chart ViewPager
     */
    private fun setupChartViewPager() {
        logger.debug("setupChartViewPager", "Setting up chart ViewPager")
        // ViewPager2 setup would be implemented with chart fragments
        // This is a placeholder for the actual chart implementation
    }

    /**
     * Update chart ViewPager
     */
    private fun updateChartViewPager(position: Int) {
        logger.debug("updateChartViewPager", "Updating chart ViewPager to position: $position")
        // Switch between PIE and BAR charts only (Trends tab removed)
        when (position) {
            0 -> showCategoryPieChart()
            1 -> showMonthlyBarChart()
            // UI_SIMPLIFICATION: Removed Trends chart case (position 2)
            else -> {
                logger.warn("updateChartViewPager", "Unknown tab position: $position, defaulting to PIE chart")
                showCategoryPieChart()
            }
        }
    }

    /**
     * Show category pie chart
     */
    private fun showCategoryPieChart() {
        if (isChartSetupInProgress) {
            logger.debug("showCategoryPieChart", "Chart setup already in progress, skipping duplicate call")
            return
        }

        logger.debug("showCategoryPieChart", "Showing category pie chart with applied filters")
        isChartSetupInProgress = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())

                // Get filtered category spending data
                val categorySpendingResults = getFilteredCategorySpending(expenseRepository)
                logger.debug("showCategoryPieChart", "Retrieved ${categorySpendingResults.size} categories with filters applied")
                logger.debug("showCategoryPieChart", "Active filters - Categories: ${currentFilters.selectedCategories.joinToString()}, Amount: ₹${currentFilters.minAmount}-₹${currentFilters.maxAmount}")

                // PIE_CHART_FIX: Debug which categories are coming from spending data
                logger.debug("showCategoryPieChart", "Categories from spending data:")
                categorySpendingResults.forEach { category ->
                    logger.debug("showCategoryPieChart", "Spending category: ${category.category_name} - ₹${category.total_amount} (${category.transaction_count} transactions)")
                }

                if (categorySpendingResults.isNotEmpty()) {
                    setupCategoryPieChart(categorySpendingResults)
                } else {
                    logger.debug("showCategoryPieChart", "No category data available for filtered period, showing empty state")
                    showEmptyPieChart()
                }

            } catch (e: Exception) {
                logger.error("showCategoryPieChart", "Error loading filtered category data", e)
                showEmptyPieChart()
            } finally {
                isChartSetupInProgress = false
            }
        }
    }

    /**
     * Get current date filter range based on system date and user context
     */
    private fun getCurrentDateFilterRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val today = calendar.time

        // Default to current month for better relevance
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.time

        // End of current month
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfMonth = calendar.time

        logger.debug("getCurrentDateFilterRange", "Current system date filter - Start: $startOfMonth, End: $endOfMonth")

        return Pair(startOfMonth, endOfMonth)
    }

    /**
     * Setup category pie chart with actual data
     */
    private fun setupCategoryPieChart(categorySpendingResults: List<com.expensemanager.app.data.dao.CategorySpendingResult>) {
        logger.debug("setupCategoryPieChart", "Setting up pie chart with ${categorySpendingResults.size} categories")

        try {
            // LAYOUT_FIX: Find the pie chart with ViewPager2 support
            val chartView = findChartView()
            if (chartView == null) {
                logger.error("setupCategoryPieChart", "Could not find chart view",null)
                return
            }

            // Show pie chart layout and find PieChart component
            showPieChartLayout()

            // LAYOUT_FIX: Handle ViewPager2 properly like BAR chart does
            val pieChart = if (chartView is androidx.viewpager2.widget.ViewPager2) {
                logger.debug("setupCategoryPieChart", "chartView is ViewPager2, setting up adapter and finding PieChart")

                // Set up ViewPager2 adapter if not already set
                if (chartView.adapter == null) {
                    chartView.adapter = ChartPagerAdapter(this@InsightsFragment)
                }

                // Switch to pie chart page (index 0)
                chartView.currentItem = 0

                // Give time for the ViewPager2 to settle and then find the PieChart
                chartView.post {
                    try {
                        logger.debug("setupCategoryPieChart", "Looking for PieChart in ViewPager2 at position 0")
                        val currentFragment = chartView.getChildAt(0)
                        currentFragment?.findViewById<PieChart>(R.id.pieChartCategories)?.let { chart ->
                            logger.debug("setupCategoryPieChart", "Found PieChart in ViewPager2")
                            setupPieChartData(chart, categorySpendingResults)
                        } ?: run {
                            logger.error("setupCategoryPieChart", "PieChart not found in ViewPager2 fragment",null)
                            // Try direct lookup as fallback
                            binding.root.findViewById<PieChart>(R.id.pieChartCategories)?.let { chart ->
                                logger.debug("setupCategoryPieChart", "Found PieChart via direct lookup fallback")
                                setupPieChartData(chart, categorySpendingResults)
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("setupCategoryPieChart", "Error in ViewPager2 post block", e)
                    }
                }

                return // Exit early for ViewPager2 case
            } else {
                // Direct layout case
                chartView.findViewById<PieChart>(R.id.pieChartCategories)
            }

            if (pieChart == null) {
                logger.error("setupCategoryPieChart", "PieChart component not found in layout",null)
                return
            }

            // Setup chart data for direct layout case
            setupPieChartData(pieChart, categorySpendingResults)

        } catch (e: Exception) {
            logger.error("setupCategoryPieChart", "Error setting up pie chart", e)
        }
    }

    /**
     * LAYOUT_FIX: Setup pie chart data - extracted to separate method for ViewPager2 compatibility
     */
    private fun setupPieChartData(pieChart: PieChart, categorySpendingResults: List<com.expensemanager.app.data.dao.CategorySpendingResult>) {
        try {
            logger.debug("setupPieChartData", "Setting up pie chart data for ${categorySpendingResults.size} categories")

            // Calculate total for percentages
            val totalAmount = categorySpendingResults.sumOf { result -> result.total_amount }
            logger.debug("setupPieChartData", "Total amount: ₹${String.format("%.2f", totalAmount)}")

            // Create pie entries
            val pieEntries = categorySpendingResults.map {
                PieEntry(
                    it.total_amount.toFloat(),
                    it.category_name
                )
            }

            // Create dataset
            val dataSet = PieDataSet(pieEntries, "Categories").apply {
                colors = getCategoryColors(categorySpendingResults)
                valueTextSize = 12f
                valueTextColor = Color.WHITE
                sliceSpace = 2f
                selectionShift = 8f
            }

            // Create pie data
            val pieData = PieData(dataSet).apply {
                setValueFormatter(PercentFormatter(pieChart))
                setValueTextSize(12f)
                setValueTextColor(Color.WHITE)
            }

            // Configure pie chart
            pieChart.apply {
                data = pieData
                description.isEnabled = false
                legend.isEnabled = false
                setUsePercentValues(true)
                setDrawEntryLabels(false)
                setHoleColor(Color.TRANSPARENT)
                holeRadius = 40f
                transparentCircleRadius = 45f
                animateY(1000)

                // CHART_REFRESH_FIX: Force pie chart refresh after setup
                post {
                    notifyDataSetChanged()
                    invalidate()
                    logger.debug("setupPieChartData", "Pie chart explicitly refreshed")
                }
            }

            // Setup legend/category list - try to find chart view for context
            val chartView = findChartView()
            if (chartView != null) {
                setupCategoryLegend(chartView, categorySpendingResults, totalAmount)
                updateCategoryChartSummaryStats(chartView, categorySpendingResults)
            }

            logger.debug("setupPieChartData", "Pie chart data setup completed successfully")

        } catch (e: Exception) {
            logger.error("setupPieChartData", "Error setting up pie chart data", e)
        }
    }

    /**
     * Show empty pie chart state
     */
    private fun showEmptyPieChart() {
        logger.debug("showEmptyPieChart", "Showing empty pie chart state")

        try {
            val chartView = findChartView()
            if (chartView != null) {
                showPieChartLayout()
                val pieChart = chartView.findViewById<PieChart>(R.id.pieChartCategories)
                pieChart?.apply {
                    clear()
                    setNoDataText("No category data available for the selected period")
                    setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    invalidate()
                }
            }
        } catch (e: Exception) {
            logger.error("showEmptyPieChart", "Error showing empty state", e)
        }
    }

    /**
     * Find chart view in the current layout
     */
    private fun findChartView(): View? {
        return try {
            // Try to find the ViewPager2 or direct chart container
            binding.root.findViewById(R.id.viewPagerCharts) ?:
            binding.root.findViewById(R.id.cardCharts)
        } catch (e: Exception) {
            logger.error("findChartView", "Error finding chart view", e)
            null
        }
    }

    /**
     * Show pie chart layout (inflate if needed)
     */
    private fun showPieChartLayout() {
        logger.debug("showPieChartLayout", "Showing pie chart layout")

        try {
            val viewPager = binding.root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPagerCharts)
            if (viewPager != null) {
                // If ViewPager2 exists, set up the adapter
                if (viewPager.adapter == null) {
                    logger.debug("showPieChartLayout", "Setting up ViewPager2 adapter")
                    viewPager.adapter = ChartPagerAdapter(this)
                }
                // Ensure we're on the pie chart page (index 0)
                viewPager.currentItem = 0
            } else {
                logger.warn("showPieChartLayout", "ViewPager2 not found, chart layout may not be available")
            }
        } catch (e: Exception) {
            logger.error("showPieChartLayout", "Error setting up chart layout", e)
        }
    }

    /**
     * Get appropriate colors for categories
     */
    private fun getCategoryColors(categorySpendingResults: List<com.expensemanager.app.data.dao.CategorySpendingResult>): List<Int> {
        return categorySpendingResults.mapIndexed {
                index, categoryResult ->
            try {
                // Try to parse category color from database
                Color.parseColor(categoryResult.color)
            } catch (e: Exception) {
                // Fallback to default color palette
                ColorTemplate.MATERIAL_COLORS[index % ColorTemplate.MATERIAL_COLORS.size]
            }
        }
    }

    /**
     * Setup category legend with spending details
     */
    private fun setupCategoryLegend(chartView: View, categorySpendingResults: List<com.expensemanager.app.data.dao.CategorySpendingResult>, totalAmount: Double) {
        try {
            val recyclerView = chartView.findViewById<RecyclerView>(R.id.recyclerCategoryLegend)
            if (recyclerView != null) {
                logger.debug("setupCategoryLegend", "Setting up category legend with ${categorySpendingResults.size} items")

                // Create legend items
                val legendItems = categorySpendingResults.map {
                    CategoryLegendItem(
                        name = it.category_name,
                        amount = it.total_amount,
                        percentage = (it.total_amount / totalAmount * 100),
                        color = try { Color.parseColor(it.color) } catch (e: Exception) { Color.BLUE },
                        transactionCount = it.transaction_count
                    )
                }

                logger.debug("setupCategoryLegend", "Created ${legendItems.size} legend items")
                legendItems.forEach { item ->
                    logger.debug("setupCategoryLegend", "Legend item - ${item.name}, color: ${item.color}, amount: ${item.amount}")
                }

                // Setup RecyclerView with adapter
                recyclerView.layoutManager = LinearLayoutManager(requireContext())
                recyclerView.adapter = CategoryLegendAdapter(legendItems)

                // Make sure RecyclerView is visible
                recyclerView.visibility = View.VISIBLE

                logger.debug("setupCategoryLegend", "RecyclerView adapter set with ${legendItems.size} items")

            } else {
                logger.warn("setupCategoryLegend", "Category legend RecyclerView not found in view")
            }
        } catch (e: Exception) {
            logger.error("setupCategoryLegend", "Error setting up category legend", e)
        }
    }

    /**
     * Show monthly bar chart
     */
    private fun showMonthlyBarChart() {
        if (isChartSetupInProgress) {
            logger.debug("showMonthlyBarChart", "Chart setup already in progress, skipping duplicate call")
            return
        }

        logger.debug("showMonthlyBarChart", "Showing monthly bar chart with filters")
        isChartSetupInProgress = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())

                logger.debug("showMonthlyBarChart", "Loading monthly data with filter: ${currentFilters.timePeriod}")

                // Get monthly spending data based on current filter
                val chartData = getTimeSeriesSpendingData(expenseRepository)

                logger.debug("showMonthlyBarChart", "getTimeSeriesSpendingData returned ${chartData.size} data points")
                logger.debug("showMonthlyBarChart", "Data points: ${chartData.map { "${it.label}: ₹${it.amount}" }}")

                val chartView = findChartView()
                logger.debug("showMonthlyBarChart", "findChartView() returned: ${chartView != null}")

                if (chartView != null) {
                    showBarChartLayout()
                    logger.debug("showMonthlyBarChart", "About to call setupTimeSeriesBarChart with ${chartData.size} data points")

                    // BAR_CHART_FIX: For ViewPager2, we need to get the current fragment's BarChart
                    if (chartView is androidx.viewpager2.widget.ViewPager2) {
                        logger.debug("showMonthlyBarChart", "chartView is ViewPager2, setting up adapter and finding BarChart")

                        // Set up ViewPager2 adapter if not already set
                        if (chartView.adapter == null) {
                            chartView.adapter = ChartPagerAdapter(this@InsightsFragment)
                        }

                        // Switch to bar chart page (index 1)
                        chartView.currentItem = 1

                        // CHART_REFRESH_FIX: Improved ViewPager2 chart refresh
                        chartView.post {
                            // Try to find the actual BarChart in the current fragment
                            val currentFragment = childFragmentManager.findFragmentByTag("f1") // ViewPager2 uses "f{position}" tags
                            val barChart = currentFragment?.view?.findViewById<BarChart>(R.id.barChartMonthly)

                            if (barChart != null && chartData.isNotEmpty()) {
                                logger.debug("showMonthlyBarChart", "Found BarChart in ViewPager2 fragment, setting up chart")
                                val success = chartConfigurationService.setupTimeSeriesBarChart(
                                    barChart,
                                    chartData,
                                    currentFilters.timeAggregation ?: "Monthly"
                                )
                                logger.debug("showMonthlyBarChart", "ChartConfigurationService setup result: $success")

                                if (success) {
                                    // CHART_REFRESH_FIX: Force chart refresh after setup
                                    barChart.post {
                                        barChart.notifyDataSetChanged()
                                        barChart.invalidate()
                                        logger.debug("showMonthlyBarChart", "Bar chart explicitly refreshed")
                                    }
                                }
                            } else {
                                logger.warn("showMonthlyBarChart", "BarChart not found in ViewPager2 fragment or no data")
                            }
                        }
                    } else if (chartView is BarChart && chartData.isNotEmpty()) {
                        // Direct BarChart (fallback case)
                        logger.debug("showMonthlyBarChart", "Using chartView directly as BarChart")
                        val success = chartConfigurationService.setupTimeSeriesBarChart(
                            chartView,
                            chartData,
                            currentFilters.timeAggregation ?: "Monthly"
                        )
                        logger.debug("showMonthlyBarChart", "ChartConfigurationService setup result: $success")

                        if (success) {
                            // CHART_REFRESH_FIX: Force chart refresh after setup
                            chartView.post {
                                chartView.notifyDataSetChanged()
                                chartView.invalidate()
                                logger.debug("showMonthlyBarChart", "Direct bar chart explicitly refreshed")
                            }
                        }
                    } else {
                        logger.warn("showMonthlyBarChart", "chartView is not BarChart/ViewPager2 or no data available")
                        logger.error("showMonthlyBarChart", "chartView type: ${chartView?.javaClass?.simpleName}",null)
                        logger.error("showMonthlyBarChart", "chartData size: ${chartData.size}",null)
                    }
                } else {
                    logger.error("showMonthlyBarChart", "Chart view not found - cannot setup chart",null)
                }

            } catch (e: Exception) {
                logger.error("showMonthlyBarChart", "Error loading monthly data", e)
            } finally {
                isChartSetupInProgress = false
            }
        }
    }

    /**
     * Calculate the number of periods for a given date range and aggregation type
     */
    private fun calculatePeriodCount(startDate: Date, endDate: Date, aggregationType: TimeAggregation): Int {
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        val startCalendar = calendar.clone() as Calendar

        calendar.time = endDate
        val endCalendar = calendar.clone() as Calendar

        return when (aggregationType) {
            TimeAggregation.DAILY -> {
                val diffInMillis = endDate.time - startDate.time
                val diffInDays = (diffInMillis / (24 * 60 * 60 * 1000)).toInt() + 1 // +1 to include both start and end dates
                logger.debug("calculatePeriodCount", "Daily - Start: $startDate, End: $endDate, Days: $diffInDays")
                diffInDays
            }
            TimeAggregation.WEEKLY -> {
                var weeks = 0
                while (startCalendar.before(endCalendar) || startCalendar.equals(endCalendar)) {
                    weeks++
                    startCalendar.add(Calendar.WEEK_OF_YEAR, 1)
                }
                logger.debug("calculatePeriodCount", "Weekly - Weeks: $weeks")
                weeks
            }
            TimeAggregation.MONTHLY -> {
                var months = 0
                while (startCalendar.before(endCalendar) || startCalendar.equals(endCalendar)) {
                    months++
                    startCalendar.add(Calendar.MONTH, 1)
                }
                logger.debug("calculatePeriodCount", "Monthly - Months: $months")
                months
            }
            TimeAggregation.QUARTERLY -> {
                var quarters = 0
                while (startCalendar.before(endCalendar) || startCalendar.equals(endCalendar)) {
                    quarters++
                    startCalendar.add(Calendar.MONTH, 3)
                }
                logger.debug("calculatePeriodCount", "Quarterly - Quarters: $quarters")
                quarters
            }
            TimeAggregation.YEARLY -> {
                var years = 0
                while (startCalendar.before(endCalendar) || startCalendar.equals(endCalendar)) {
                    years++
                    startCalendar.add(Calendar.YEAR, 1)
                }
                logger.debug("calculatePeriodCount", "Yearly - Years: $years")
                years
            }
        }
    }

    /**
     * Get time series spending data based on current time aggregation and date range filters
     * Now uses TimeSeriesAggregationService to eliminate ~400 lines of duplicated logic
     */
    private suspend fun getTimeSeriesSpendingData(repository: ExpenseRepository): List<TimeSeriesData> {
        logger.debug("getTimeSeriesSpendingData", "Getting time series data for aggregation: ${currentFilters.timeAggregation}")

        try {
            // Map UI filter strings to TimeAggregation enum
            val aggregationType = when (currentFilters.timeAggregation) {
                "Daily" -> TimeAggregation.DAILY
                "Weekly" -> TimeAggregation.WEEKLY
                "Quarterly" -> TimeAggregation.QUARTERLY
                "Yearly" -> TimeAggregation.YEARLY
                else -> TimeAggregation.MONTHLY // Default to monthly
            }

            // Determine period count based on date range filter
            val periodCount = when (currentFilters.timePeriod) {
                "Last 7 Days" -> if (aggregationType == TimeAggregation.DAILY) 7 else 1
                "Last 30 Days" -> when (aggregationType) {
                    TimeAggregation.DAILY -> 30
                    TimeAggregation.WEEKLY -> 4
                    else -> 1
                }
                "This Month", "Current Month" -> 1
                "Last 3 Months" -> when (aggregationType) {
                    TimeAggregation.DAILY -> 90
                    TimeAggregation.WEEKLY -> 12
                    TimeAggregation.MONTHLY -> 3
                    else -> 1
                }
                "Last 6 Months" -> when (aggregationType) {
                    TimeAggregation.MONTHLY -> 6
                    TimeAggregation.QUARTERLY -> 2
                    else -> 6
                }
                "This Year" -> when (aggregationType) {
                    TimeAggregation.MONTHLY -> {
                        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                        currentMonth + 1 // January is 0, so add 1
                    }
                    TimeAggregation.QUARTERLY -> {
                        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                        (currentMonth / 3) + 1
                    }
                    TimeAggregation.YEARLY -> 1
                    else -> 12
                }
                else -> {
                    // For custom date ranges or unknown filters, calculate actual period count
                    if (currentFilters.startDate != null && currentFilters.endDate != null) {
                        calculatePeriodCount(currentFilters.startDate!!, currentFilters.endDate!!, aggregationType)
                    } else {
                        // Default fallback values
                        when (aggregationType) {
                            TimeAggregation.DAILY -> 30
                            TimeAggregation.WEEKLY -> 4
                            TimeAggregation.MONTHLY -> 6
                            TimeAggregation.QUARTERLY -> 2
                            TimeAggregation.YEARLY -> 1
                        }
                    }
                }
            }

            logger.debug("getTimeSeriesSpendingData", "Using aggregation: $aggregationType, periods: $periodCount")

            // Use the existing date range from filters if available, otherwise calculate based on aggregation
            val (startDate, endDate) = if (currentFilters.startDate != null && currentFilters.endDate != null) {
                logger.debug("getTimeSeriesSpendingData", "Using filter date range: ${currentFilters.startDate} to ${currentFilters.endDate}")
                Pair(currentFilters.startDate!!, currentFilters.endDate!!)
            } else {
                // Fallback to calculated date range for backward compatibility
                logger.debug("getTimeSeriesSpendingData", "No filter date range set, calculating based on aggregation")
                when (aggregationType) {
                    TimeAggregation.DAILY -> {
                        val cal = Calendar.getInstance()
                        val end = cal.time
                        cal.add(Calendar.DAY_OF_MONTH, -periodCount + 1)
                        Pair(cal.time, end)
                    }
                    TimeAggregation.WEEKLY -> {
                        val cal = Calendar.getInstance()
                        val end = cal.time
                        cal.add(Calendar.WEEK_OF_YEAR, -periodCount + 1)
                        Pair(cal.time, end)
                    }
                    TimeAggregation.MONTHLY -> {
                        val cal = Calendar.getInstance()
                        val end = cal.time
                        cal.add(Calendar.MONTH, -periodCount + 1)
                        Pair(cal.time, end)
                    }
                    TimeAggregation.QUARTERLY -> {
                        val cal = Calendar.getInstance()
                        val end = cal.time
                        cal.add(Calendar.MONTH, -(periodCount * 3) + 3)
                        Pair(cal.time, end)
                    }
                    TimeAggregation.YEARLY -> {
                        val cal = Calendar.getInstance()
                        val end = cal.time
                        cal.add(Calendar.YEAR, -periodCount + 1)
                        Pair(cal.time, end)
                    }
                }
            }

            // Get all transactions in the period
            val allTransactions = repository.getExpenseTransactionsByDateRange(startDate, endDate)

            // Apply filters if any
            val filteredTransactions = if (currentFilters.hasFilters()) {
                applyFiltersToTransactions(allTransactions, currentFilters)
            } else {
                allTransactions
            }

            logger.debug("getTimeSeriesSpendingData", "Retrieved ${allTransactions.size} transactions, filtered to ${filteredTransactions.size}")

            // Use TimeSeriesAggregationService to generate time series data
            val timeSeriesData = if (currentFilters.startDate != null && currentFilters.endDate != null) {
                // For custom date ranges, use the new range-based method
                logger.debug("getTimeSeriesSpendingData", "Using range-based generation for custom date range")

                // BAR_CHART_SMART_FIX: When using smart aggregation with Daily mode, cap end date to today
                // This ensures we show only bars for days that have actually elapsed
                val actualEndDate = if (aggregationType == TimeAggregation.DAILY &&
                    currentFilters.timePeriod == "This Month") {
                    val today = Date()
                    if (endDate.after(today)) {
                        logger.debug("getTimeSeriesSpendingData", "Capping end date from $endDate to today: $today")
                        today
                    } else {
                        endDate
                    }
                } else {
                    endDate
                }

                // ENHANCED_DATE_RANGE: Use enhanced logic for special combinations
                if ((currentFilters.timePeriod == "This Month" && aggregationType == TimeAggregation.WEEKLY) ||
                    (currentFilters.timePeriod == "Last 30 Days" && aggregationType == TimeAggregation.MONTHLY)) {
                    logger.debug("getTimeSeriesSpendingData", "Using enhanced date range logic for special combination")
                    logger.debug("getTimeSeriesSpendingData", "Date filter: ${currentFilters.timePeriod}, Aggregation: $aggregationType")

                    timeSeriesAggregationService.generateTimeSeriesDataWithEnhancedRanges(
                        filteredTransactions,
                        currentFilters.timePeriod ?: "Unknown",
                        aggregationType,
                        startDate,
                        actualEndDate
                    )
                } else {
                    timeSeriesAggregationService.generateTimeSeriesDataInRange(
                        filteredTransactions,
                        aggregationType,
                        startDate,
                        actualEndDate
                    )
                }
            } else {
                // For predefined periods, use the count-based method
                logger.debug("getTimeSeriesSpendingData", "Using count-based generation for period: ${currentFilters.timePeriod}")
                timeSeriesAggregationService.generateTimeSeriesData(
                    filteredTransactions,
                    aggregationType,
                    periodCount
                )
            }

            logger.debug("getTimeSeriesSpendingData", "Generated ${timeSeriesData.size} time series data points")
            return timeSeriesData

        } catch (e: Exception) {
            logger.error("getTimeSeriesSpendingData", "Error generating time series data", e)
            return emptyList()
        }
    }


    /**
     * Show bar chart layout
     */
    private fun showBarChartLayout() {
        logger.debug("showBarChartLayout", "Showing bar chart layout")

        try {
            val viewPager = binding.root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPagerCharts)
            if (viewPager != null) {
                // Ensure we're on the bar chart page (index 1)
                viewPager.currentItem = 1
            }
        } catch (e: Exception) {
            logger.error("showBarChartLayout", "Error setting up chart layout", e)
        }
    }

    /**
     * Show trend line chart
     */
    private fun showTrendLineChart() {
        logger.debug("showTrendLineChart", "Showing trend line chart with filters")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())

                logger.debug("showTrendLineChart", "Loading daily trend data for last 30 days")

                // Get daily spending data for last 30 days
                val dailyData = getDailyTrendData(expenseRepository)

                val chartView = findChartView()
                if (chartView != null) {
                    showLineChartLayout()
                    setupTrendLineChart(chartView, dailyData)
                } else {
                    logger.error("showTrendLineChart", "Chart view not found",null)
                }

            } catch (e: Exception) {
                logger.error("showTrendLineChart", "Error loading trend data", e)
            }
        }
    }

    /**
     * Get daily trend data for the last 30 days
     */
    private suspend fun getDailyTrendData(repository: ExpenseRepository): List<DailyTrendData> {
        val dailyData = mutableListOf<DailyTrendData>()
        val calendar = Calendar.getInstance()

        // Get data for last 30 days
        for (i in 29 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_MONTH, -i)

            val startOfDay = calendar.clone() as Calendar
            startOfDay.set(Calendar.HOUR_OF_DAY, 0)
            startOfDay.set(Calendar.MINUTE, 0)
            startOfDay.set(Calendar.SECOND, 0)
            startOfDay.set(Calendar.MILLISECOND, 0)

            val endOfDay = calendar.clone() as Calendar
            endOfDay.set(Calendar.HOUR_OF_DAY, 23)
            endOfDay.set(Calendar.MINUTE, 59)
            endOfDay.set(Calendar.SECOND, 59)
            endOfDay.set(Calendar.MILLISECOND, 999)

            // Apply filters if any
            val transactions = if (currentFilters.hasFilters()) {
                applyFiltersToTransactions(
                    repository.getExpenseTransactionsByDateRange(startOfDay.time, endOfDay.time),
                    currentFilters
                )
            } else {
                repository.getExpenseTransactionsByDateRange(startOfDay.time, endOfDay.time)
            }

            val totalAmount = transactions.sumOf { transaction -> transaction.amount }
            val dayName = SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)

            dailyData.add(DailyTrendData(
                day = dayName,
                date = calendar.time,
                amount = totalAmount,
                transactionCount = transactions.size
            ))
        }

        logger.debug("getDailyTrendData", "Generated ${dailyData.size} days of trend data")
        return dailyData
    }

    /**
     * Setup trend line chart with actual data
     */
    private fun setupTrendLineChart(chartView: View, dailyData: List<DailyTrendData>) {
        try {
            showLineChartLayout()
            val lineChart = chartView.findViewById<LineChart>(R.id.lineChartTrends)
            if (lineChart == null) {
                logger.error("setupTrendLineChart", "LineChart component not found in layout",null)
                return
            }

            logger.debug("setupTrendLineChart", "Setting up line chart with ${dailyData.size} days")

            // Create line entries
            val lineEntries = dailyData.mapIndexed {
                    index, data ->
                Entry(index.toFloat(), data.amount.toFloat())
            }

            // Create dataset
            val dataSet = LineDataSet(lineEntries, "Daily Spending").apply {
                color = ContextCompat.getColor(requireContext(), R.color.primary)
                setCircleColor(ContextCompat.getColor(requireContext(), R.color.secondary))
                lineWidth = 3f
                circleRadius = 4f
                setDrawCircleHole(false)
                setDrawValues(false)
                fillColor = ContextCompat.getColor(requireContext(), R.color.primary)
                fillAlpha = 30
                setDrawFilled(true)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            // Create line data
            val lineData = LineData(dataSet)

            // Configure line chart
            lineChart.apply {
                data = lineData
                description.isEnabled = false
                legend.isEnabled = false

                // X-axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    labelCount = 7 // Show every 4-5 days
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index >= 0 && index < dailyData.size) {
                                dailyData[index].day
                            } else ""
                        }
                    }
                    textColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
                }

                // Y-axis configuration
                axisLeft.apply {
                    setDrawGridLines(true)
                    axisMinimum = 0f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "₹${String.format("%.0f", value)}"
                        }
                    }
                    textColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
                }

                axisRight.isEnabled = false

                // Enable touch interactions
                setTouchEnabled(true)
                setDragEnabled(true)
                setScaleEnabled(true)
                setPinchZoom(true)

                animateX(1000)

                // CHART_REFRESH_FIX: Force line chart refresh after setup
                post {
                    notifyDataSetChanged()
                    invalidate()
                    logger.debug("setupTrendLineChart", "Line chart explicitly refreshed")
                }
            }

            // Update trend analysis
            updateTrendAnalysis(chartView, dailyData)

            logger.debug("setupTrendLineChart", "Trend line chart setup completed successfully")

        } catch (e: Exception) {
            logger.error("setupTrendLineChart", "Error setting up line chart", e)
        }
    }

    /**
     * Update trend analysis statistics
     */
    private fun updateTrendAnalysis(chartView: View, dailyData: List<DailyTrendData>) {
        try {
            // Calculate daily average
            val totalAmount = dailyData.sumOf { data -> data.amount }
            val avgDaily = if (dailyData.isNotEmpty()) totalAmount / dailyData.size else 0.0

            // Find peak day
            val peakDay = dailyData.maxByOrNull { it.amount }

            // Calculate trend direction
            val recentData = dailyData.takeLast(7) // Last 7 days
            val olderData = dailyData.dropLast(7).takeLast(7) // Previous 7 days

            val recentAvg = if (recentData.isNotEmpty()) recentData.sumOf { data -> data.amount } / recentData.size else 0.0
            val olderAvg = if (olderData.isNotEmpty()) olderData.sumOf { data -> data.amount } / olderData.size else 0.0

            val trendDirection = when {
                recentAvg > olderAvg * 1.1 -> "↗️ Rising"
                recentAvg < olderAvg * 0.9 -> "↘️ Falling"
                else -> "→ Stable"
            }

            val trendColor = when {
                recentAvg > olderAvg * 1.1 -> ContextCompat.getColor(requireContext(), R.color.error)
                recentAvg < olderAvg * 0.9 -> ContextCompat.getColor(requireContext(), R.color.success)
                else -> ContextCompat.getColor(requireContext(), R.color.info)
            }

            // Footer removed - no trend UI updates needed

            logger.debug("updateTrendAnalysis", "Updated trend analysis - Avg: ₹$avgDaily, Peak: ₹${peakDay?.amount}, Trend: $trendDirection")

        } catch (e: Exception) {
            logger.error("updateTrendAnalysis", "Error updating trend analysis", e)
        }
    }

    /**
     * Show line chart layout
     */
    private fun showLineChartLayout() {
        logger.debug("showLineChartLayout", "Showing line chart layout")

        try {
            val viewPager = binding.root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPagerCharts)
            if (viewPager != null) {
                // Ensure we're on the line chart page (index 2)
                viewPager.currentItem = 2
            }
        } catch (e: Exception) {
            logger.error("showLineChartLayout", "Error setting up chart layout", e)
        }
    }

    private fun getSmartTimeAggregation(startDate: Date, endDate: Date): String {
        // BAR_CHART_SMART_FIX: Use actual elapsed time (start to TODAY) instead of full range
        val today = Date()
        val actualEndDate = if (endDate.after(today)) today else endDate

        val diff = actualEndDate.time - startDate.time
        val days = diff / (1000 * 60 * 60 * 24)

        logger.debug("getSmartTimeAggregation", "Smart aggregation calculation")
        logger.debug("getSmartTimeAggregation", "Start date: $startDate")
        logger.debug("getSmartTimeAggregation", "Original end date: $endDate")
        logger.debug("getSmartTimeAggregation", "Actual end date (capped to today): $actualEndDate")
        logger.debug("getSmartTimeAggregation", "Actual days elapsed: $days")

        return when {
            days <= 7 -> {
                logger.debug("getSmartTimeAggregation", "Selected Daily aggregation ($days days)")
                "Daily"
            }
            days <= 35 -> {
                logger.debug("getSmartTimeAggregation", "Selected Weekly aggregation ($days days)")
                "Weekly"
            }
            else -> {
                logger.debug("getSmartTimeAggregation", "Selected Monthly aggregation ($days days)")
                "Monthly"
            }
        }
    }

    private fun updateChartsWithFilteredData() {
        // CHART_REFRESH_FIX: Only refresh the currently visible chart to avoid conflicts
        logger.debug("updateChartsWithFilteredData", "Updating charts with filtered data")

        val tabLayout = binding.root.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutCharts)
        val currentTab = tabLayout?.selectedTabPosition ?: 0

        logger.debug("updateChartsWithFilteredData", "Current tab position: $currentTab")

        when (currentTab) {
            0 -> {
                logger.debug("updateChartsWithFilteredData", "Refreshing PIE chart")
                showCategoryPieChart()
            }
            1 -> {
                logger.debug("updateChartsWithFilteredData", "Refreshing BAR chart")
                showMonthlyBarChart()
            }
            // UI_SIMPLIFICATION: Removed case 2 (LINE chart) - Trends tab is hidden
            else -> {
                logger.warn("updateChartsWithFilteredData", "Unknown tab position: $currentTab, defaulting to PIE chart")
                showCategoryPieChart()
            }
        }
    }

    /**
     * PIE_CHART_FILTER_FIX: Enhanced category spending calculation with proper filter support
     * Uses the same date range logic as BAR chart to ensure consistency
     */
    private suspend fun getFilteredCategorySpending(expenseRepository: ExpenseRepository): List<com.expensemanager.app.data.dao.CategorySpendingResult> {
        try {
            logger.debug("getFilteredCategorySpending", "Getting category spending data for period: ${currentFilters.timePeriod}")

            // Use the same period calculation logic as BAR chart
            val (startDate, endDate) = calculateDateRangeForFilter()

            logger.debug("getFilteredCategorySpending", "Calculated date range: $startDate to $endDate")
            logger.debug("getFilteredCategorySpending", "Fetching category data from $startDate to $endDate")

            return expenseRepository.getCategorySpending(startDate, endDate)

        } catch (e: Exception) {
            logger.error("getFilteredCategorySpending", "Error getting filtered category spending", e)
            return emptyList()
        }
    }

    /**
     * PIE_CHART_FILTER_FIX: Calculate date range for current filter using same logic as BAR chart
     */
    private fun calculateDateRangeForFilter(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        // Determine period count based on date range filter (same logic as getTimeSeriesSpendingData)
        val periodCount = when (currentFilters.timePeriod) {
            "Last 7 Days" -> 7
            "Last 30 Days" -> 30
            "This Month", "Current Month" -> 1
            "Last 3 Months" -> 3
            "Last 6 Months" -> 6  // PIE_CHART_FILTER_FIX: Added support for Last 6 Months
            "Last Year" -> 12
            "This Year" -> 1
            else -> 1 // Default to current month
        }

        logger.debug("calculateDateRangeForFilter", "Period count for '${currentFilters.timePeriod}': $periodCount")

        // If we have explicit dates from filters, use them
        if (currentFilters.startDate != null && currentFilters.endDate != null) {
            logger.debug("calculateDateRangeForFilter", "Using explicit filter dates")
            return Pair(currentFilters.startDate!!, endDate!!)
        }

        // Calculate date range based on period (same logic as BAR chart)
        return when (currentFilters.timePeriod) {
            "Last 7 Days", "Last 30 Days" -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_MONTH, -periodCount)
                Pair(cal.time, endDate)
            }
            "This Month", "Current Month" -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.time, endDate)
            }
            "Last 3 Months", "Last 6 Months" -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -periodCount)
                Pair(cal.time, endDate)
            }
            "Last Year" -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.YEAR, -1)
                Pair(cal.time, endDate)
            }
            "This Year" -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.time, endDate)
            }
            else -> {
                // Default: current month
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.time, endDate)
            }
        }
    }


    /**
     * Update category chart summary stats in the chart fragment
     */
    private fun updateCategoryChartSummaryStats(chartView: View, categoryData: List<com.expensemanager.app.data.dao.CategorySpendingResult> = emptyList()) {
        logger.debug("updateCategoryChartSummaryStats", "Updating category chart summary statistics with ${categoryData.size} categories")

        try {
            // Update total categories
            chartView.findViewById<TextView>(R.id.tvTotalCategories)?.text = "${categoryData.size}"

            // Update top category (highest spending)
            val topCategory = categoryData.maxByOrNull { it.total_amount }
            if (topCategory != null) {
                chartView.findViewById<TextView>(R.id.tvTopCategoryAmount)?.text = "₹${String.format("%.0f", topCategory.total_amount)}"
                chartView.findViewById<TextView>(R.id.tvTopCategoryName)?.text = topCategory.category_name
            } else {
                chartView.findViewById<TextView>(R.id.tvTopCategoryAmount)?.text = "₹0"
                chartView.findViewById<TextView>(R.id.tvTopCategoryName)?.text = "No Data"
            }

            // Calculate and update average transaction amount
            val totalAmount = categoryData.sumOf { it.total_amount }
            val totalTransactions = categoryData.sumOf { it.transaction_count }
            val avgTransactionAmount = if (totalTransactions > 0) totalAmount / totalTransactions else 0.0

            chartView.findViewById<TextView>(R.id.tvAvgTransaction)?.text = "₹${String.format("%.0f", avgTransactionAmount)}"

            logger.debug("updateCategoryChartSummaryStats", "Category summary stats updated: ${categoryData.size} categories, top: ${topCategory?.category_name}, avg: ₹${String.format("%.0f", avgTransactionAmount)}")

        } catch (e: Exception) {
            logger.error("updateCategoryChartSummaryStats", "Error updating category chart summary statistics", e)
        }
    }

    private fun updateChartSummaryStats(categoryData: List<com.expensemanager.app.data.dao.CategorySpendingResult> = emptyList()) {
        logger.debug("updateChartSummaryStats", "Updating chart summary statistics with ${categoryData.size} categories - DEPRECATED, should use chart-specific functions")

        try {
            // This function is now deprecated as summary stats are in individual chart fragments
            // Update total categories
            binding.root.findViewById<android.widget.TextView>(R.id.tvTotalCategories)?.text = "${categoryData.size}"

            // Update top category (highest spending)
            val topCategory = categoryData.maxByOrNull { it.total_amount }
            if (topCategory != null) {
                binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryAmount)?.text = "₹${String.format("%.0f", topCategory.total_amount)}"
                binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryName)?.text = topCategory.category_name
                logger.debug("updateChartSummaryStats", "Top category: ${topCategory.category_name} = ₹${topCategory.total_amount}")
            } else {
                binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryAmount)?.text = "₹0"
                binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryName)?.text = "No Data"
            }

            // Update average transaction (total amount / total transaction count)
            val totalAmount = categoryData.sumOf { data -> data.total_amount }
            val totalTransactions = categoryData.sumOf { data -> data.transaction_count }
            val avgPerTransaction = if (totalTransactions > 0) totalAmount / totalTransactions else 0.0

            binding.root.findViewById<android.widget.TextView>(R.id.tvAvgTransaction)?.text = "₹${String.format("%.0f", avgPerTransaction)}"

            logger.debug("updateChartSummaryStats", "Summary stats - Categories: ${categoryData.size}, Total: ₹$totalAmount, Avg: ₹$avgPerTransaction")

        } catch (e: Exception) {
            logger.error("updateChartSummaryStats", "Error updating chart summary stats", e)
            // Fallback to zero values on error
            binding.root.findViewById<android.widget.TextView>(R.id.tvTotalCategories)?.text = "0"
            binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryAmount)?.text = "₹0"
            binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryName)?.text = "No Data"
        }
    }

    fun triggerPieChartSetup() {
        showCategoryPieChart()
    }
}

/**
 * Data class for category legend items
 */
data class CategoryLegendItem(
    val name: String,
    val amount: Double,
    val percentage: Double,
    val color: Int,
    val transactionCount: Int
)

/**
 * Data class for chart filter conditions
 */
data class ChartFilterConditions(
    var startDate: Date? = null,
    var endDate: Date? = null,
    var selectedCategories: List<String> = emptyList(),
    var selectedMerchants: List<String> = emptyList(),
    var minAmount: Double = 0.0,
    var maxAmount: Double = Double.MAX_VALUE,
    var timePeriod: String = "This Month", // This Month/Current Month, Last 3 Months, Last 6 Months, Custom
    var timeAggregation: String = "Monthly" // Daily, Weekly, Monthly, Quarterly, Yearly
) {
    fun hasFilters(): Boolean {
        return selectedCategories.isNotEmpty() ||
                selectedMerchants.isNotEmpty() ||
                minAmount > 0.0 ||
                maxAmount < Double.MAX_VALUE ||
                startDate != null
    }
}

/**
 * Data class for monthly spending data
 */
data class MonthlySpendingData(
    val month: String,
    val amount: Double,
    val transactionCount: Int,
    val date: Date
)

/**
 * Data class for daily trend data
 */
data class DailyTrendData(
    val day: String,
    val date: Date,
    val amount: Double,
    val transactionCount: Int
)

/**
 * Adapter for category legend RecyclerView
 */
class CategoryLegendAdapter(
    private val items: List<CategoryLegendItem>
) : RecyclerView.Adapter<CategoryLegendAdapter.CategoryLegendViewHolder>() {

    private val logger = StructuredLogger("CategoryLegendAdapter", "CategoryLegendAdapter")

    class CategoryLegendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorIndicator: View = view.findViewById(R.id.viewColorIndicator)
        val categoryName: TextView = view.findViewById(R.id.tvCategoryName)
        val categoryDetails: TextView = view.findViewById(R.id.tvCategoryDetails)
        val categoryPercentage: TextView = view.findViewById(R.id.tvCategoryPercentage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryLegendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_legend, parent, false)
        return CategoryLegendViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryLegendViewHolder, position: Int) {
        val item = items[position]

        // Set color indicator with proper shape
        holder.colorIndicator.setBackgroundColor(item.color)

        // Set category name
        holder.categoryName.text = item.name

        // Set category details (amount + transaction count)
        holder.categoryDetails.text = "₹${String.format("%.0f", item.amount)} • ${item.transactionCount} transactions"

        // Set percentage
        holder.categoryPercentage.text = "${String.format("%.1f", item.percentage)}%"

        logger.debug("onBindViewHolder", "Binding item: ${item.name}, color: ${item.color}, amount: ${item.amount}")
    }

    override fun getItemCount(): Int = items.size
}

/**
 * ViewPager2 adapter for chart layouts
 */
class ChartPagerAdapter(
    private val fragment: Fragment
) : androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3 // Categories, Monthly, Trends

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ChartFragment.newInstance("pie_category")
            1 -> ChartFragment.newInstance("bar_monthly")
            2 -> ChartFragment.newInstance("line_trends")
            else -> ChartFragment.newInstance("pie_category")
        }
    }
}

/**
 * Simple fragment to hold chart layouts
 */
class ChartFragment : Fragment() {

    private val logger = StructuredLogger("ChartFragment", "ChartFragment")

    companion object {
        private const val ARG_CHART_TYPE = "chart_type"

        fun newInstance(chartType: String): ChartFragment {
            val fragment = ChartFragment()
            val args = Bundle()
            args.putString(ARG_CHART_TYPE, chartType)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val chartType = arguments?.getString(ARG_CHART_TYPE) ?: "pie_category"

        return when (chartType) {
            "pie_category" -> inflater.inflate(R.layout.chart_pie_category, container, false)
            "bar_monthly" -> inflater.inflate(R.layout.chart_bar_monthly, container, false)
            "line_trends" -> inflater.inflate(R.layout.chart_line_trends, container, false)
            else -> inflater.inflate(R.layout.chart_pie_category, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chartType = arguments?.getString(ARG_CHART_TYPE) ?: "pie_category"
        logger.debug("onViewCreated", "Chart fragment created for type: $chartType")

        // Trigger chart setup when fragment is ready
        if (chartType == "pie_category") {
            // Find parent InsightsFragment and trigger pie chart setup
            val parentFragment = parentFragment as? InsightsFragment
            parentFragment?.triggerPieChartSetup()
        }
    }
}
