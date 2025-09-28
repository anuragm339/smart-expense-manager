package com.expensemanager.app.ui.insights

import android.os.Bundle
import android.util.Log
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.button.MaterialButton
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
import kotlinx.coroutines.launch

class InsightsFragment : Fragment() {
    
    private var _binding: FragmentInsightsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: InsightsViewModel
    
    // Service dependencies
    private lateinit var dateRangeService: DateRangeService
    private lateinit var timeSeriesAggregationService: TimeSeriesAggregationService
    private lateinit var chartConfigurationService: ChartConfigurationService
    
    // State management views
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shimmerLoading: View
    private lateinit var errorState: View
    private lateinit var emptyState: View
    private lateinit var contentLayout: View
    
    // Enhanced Analytics Components
    private lateinit var filtersCard: View
    private lateinit var chartsCard: View
    
    // Filter conditions for charts
    private var currentFilters = ChartFilterConditions()
    
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

        forceInitialRefresh()
    }
    
    
    /**
     * Initialize view references
     */
    private fun initializeViews() {
        swipeRefreshLayout = binding.swipeRefreshLayout
        shimmerLoading = binding.layoutShimmerLoading.root
        errorState = binding.layoutErrorState.root
        emptyState = binding.layoutEmptyState.root
        contentLayout = binding.layoutContent
        
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
            val aiInsightsRepository = AIInsightsRepository.getInstance(requireContext(), expenseRepository)
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
            
            Log.d(TAG, "ViewModel setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up ViewModel", e)
            showError("Failed to initialize AI insights")
        }
    }
    
    /**
     * Setup initial UI elements and loading states
     */
    private fun setupUI() {
        // Initially show shimmer loading state
        showShimmerLoading(true)
        
        // Setup swipe to refresh
        setupSwipeRefresh()
        
        // Setup error state retry buttons
        setupErrorStateButtons()
        
        // Setup empty state button
        setupEmptyStateButton()
        
        Log.d(TAG, "UI setup completed")
    }
    
    /**
     * Setup swipe to refresh functionality
     */
    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Pull-to-refresh triggered")
            viewModel.handleEvent(InsightsUIEvent.Refresh)
        }
        
        // Customize refresh colors
        swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.secondary,
            R.color.success
        )
    }
    
    /**
     * Setup error state buttons
     */
    private fun setupErrorStateButtons() {
        errorState.findViewById<MaterialButton>(R.id.btnRetry)?.setOnClickListener {
            Log.d(TAG, "Retry button clicked")
            viewModel.handleEvent(InsightsUIEvent.Retry)
        }
        
        errorState.findViewById<MaterialButton>(R.id.btnUseSampleData)?.setOnClickListener {
            Log.d(TAG, "Use sample data button clicked")
            // Load sample insights for demo purposes
            Toast.makeText(requireContext(), "Loading sample insights...", Toast.LENGTH_SHORT).show()
            // This would trigger loading sample data in production
        }
    }
    
    /**
     * Setup empty state button
     */
    private fun setupEmptyStateButton() {
        emptyState.findViewById<MaterialButton>(R.id.btnGoToMessages)?.setOnClickListener {
            Log.d(TAG, "Go to messages button clicked")
            // Navigate to messages tab (simplified approach)
            Log.d(TAG, "Navigate to messages requested")
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
                        Log.d(TAG, "UI State updated: loading=${state.isLoading}, insights=${state.insights.size}, error=${state.hasError}")
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
            Log.d(TAG, "Spending forecast card clicked")
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
        // Handle pull-to-refresh state
        swipeRefreshLayout.isRefreshing = state.isRefreshing
        
        when {
            state.isInitialLoading -> {
                showShimmerLoading(true)
                hideAllOtherStates()
            }
            
            state.shouldShowError -> {
                showErrorState(state.error)
                hideAllOtherStates(except = "error")
            }
            
            state.shouldShowContent -> {
                showContentState()
                hideAllOtherStates(except = "content")
                updateInsightsContent(state)
            }
            
            state.shouldShowEmptyState -> {
                showEmptyState()
                hideAllOtherStates(except = "empty")
            }
        }
        
        // Show sample data indicator if applicable
        if (state.showingSampleData && state.shouldShowContent) {
            showSampleDataIndicator()
        }
    }
    
    /**
     * Show shimmer loading state
     */
    private fun showShimmerLoading(show: Boolean) {
        if (show) {
            shimmerLoading.visibility = View.VISIBLE
            // Start shimmer animations
            startAllShimmerAnimations(shimmerLoading)
            Log.d(TAG, "Showing shimmer loading")
        } else {
            shimmerLoading.visibility = View.GONE
            // Stop shimmer animations
            stopAllShimmerAnimations(shimmerLoading)
            Log.d(TAG, "Hiding shimmer loading")
        }
    }
    
    /**
     * Show error state with message
     */
    private fun showErrorState(errorMessage: String?) {
        errorState.visibility = View.VISIBLE
        
        // Update error message
        errorState.findViewById<TextView>(R.id.tvErrorMessage)?.text = 
            errorMessage ?: "Something went wrong. Please try again."
        
        Log.d(TAG, "Showing error state: $errorMessage")
    }
    
    /**
     * Show content state
     */
    private fun showContentState() {
        contentLayout.visibility = View.VISIBLE
        Log.d(TAG, "Showing content state")
    }
    
    /**
     * Show empty state
     */
    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        Log.d(TAG, "Showing empty state")
    }
    
    /**
     * Hide all states except the specified one
     */
    private fun hideAllOtherStates(except: String = "") {
        if (except != "shimmer") showShimmerLoading(false)
        if (except != "error") errorState.visibility = View.GONE
        if (except != "content") contentLayout.visibility = View.GONE
        if (except != "empty") emptyState.visibility = View.GONE
    }
    
    /**
     * Start all shimmer animations in a view hierarchy
     */
    private fun startAllShimmerAnimations(parent: View) {
        if (parent is ShimmerFrameLayout) {
            parent.startShimmer()
        } else if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                startAllShimmerAnimations(parent.getChildAt(i))
            }
        }
    }
    
    /**
     * Stop all shimmer animations in a view hierarchy
     */
    private fun stopAllShimmerAnimations(parent: View) {
        if (parent is ShimmerFrameLayout) {
            parent.stopShimmer()
        } else if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                stopAllShimmerAnimations(parent.getChildAt(i))
            }
        }
    }
    
    /**
     * Update insights content in UI
     */
    private fun updateInsightsContent(state: InsightsUIState) {
        Log.d(TAG, "Updating insights content with ${state.insights.size} insights")
        
        // Update top merchants from actual insights data
        updateTopMerchants(state)
        
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
                
                Log.d(TAG, "Spending forecast updated with direct API data")
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
                Log.d(TAG, "Updated spending progress: ${forecastData.progressPercentage}%")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating spending forecast", e)
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
                
                Log.d(TAG, "Pattern alerts updated with direct API data: ${patternInsights.size} alerts")
            } else {
                // Show no alerts message
                binding.root.findViewById<TextView>(R.id.tvPatternAlert1)?.text = 
                    "No pattern alerts from API"
                binding.root.findViewById<TextView>(R.id.tvPatternAlert2)?.text = 
                    "All spending patterns appear normal"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating pattern alerts", e)
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
                
                Log.d(TAG, "Savings opportunities updated with API recommendations: ${savingsInsights.size}")
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
            Log.e(TAG, "Error updating savings opportunities", e)
        }
    }
    
    /**
     * Update top merchants section with direct API data
     */
    private fun updateTopMerchants(state: InsightsUIState) {
        try {
            // Get merchant analysis insights
            val merchantInsights = state.getInsightsByType(InsightType.MERCHANT_RECOMMENDATION)
            
            if (merchantInsights.isNotEmpty()) {
                val firstMerchant = merchantInsights.first()
                
                // Use merchant title directly from API (merchant name)
                val merchantName = firstMerchant.title
                val avgTransaction = firstMerchant.impactAmount
                
                // Update first merchant with direct API data
                binding.root.findViewById<TextView>(R.id.tvMerchant1Name)?.text = merchantName
                binding.root.findViewById<TextView>(R.id.tvMerchant1Details)?.text = 
                    "Avg: ₹${String.format("%.0f", avgTransaction)}"
                binding.root.findViewById<TextView>(R.id.tvMerchant1Amount)?.text = 
                    "₹${String.format("%.0f", avgTransaction)}" // Show actual average, not calculated
                
                // If we have more merchants, update them too
                if (merchantInsights.size > 1) {
                    val secondMerchant = merchantInsights[1]
                    binding.root.findViewById<TextView>(R.id.tvMerchant2Name)?.text = secondMerchant.title
                    binding.root.findViewById<TextView>(R.id.tvMerchant2Details)?.text = 
                        "Avg: ₹${String.format("%.0f", secondMerchant.impactAmount)}"
                    binding.root.findViewById<TextView>(R.id.tvMerchant2Amount)?.text = 
                        "₹${String.format("%.0f", secondMerchant.impactAmount)}"
                }
                
                if (merchantInsights.size > 2) {
                    val thirdMerchant = merchantInsights[2]
                    binding.root.findViewById<TextView>(R.id.tvMerchant3Name)?.text = thirdMerchant.title
                    binding.root.findViewById<TextView>(R.id.tvMerchant3Details)?.text = 
                        "Avg: ₹${String.format("%.0f", thirdMerchant.impactAmount)}"
                    binding.root.findViewById<TextView>(R.id.tvMerchant3Amount)?.text = 
                        "₹${String.format("%.0f", thirdMerchant.impactAmount)}"
                }
                
                Log.d(TAG, "Updated merchants with direct API data: ${merchantInsights.size} merchants")
            } else {
                // Show "No merchant data available" instead of static fallback
                binding.root.findViewById<TextView>(R.id.tvMerchant1Name)?.text = "No API Data"
                binding.root.findViewById<TextView>(R.id.tvMerchant1Details)?.text = "No merchant insights from API"
                binding.root.findViewById<TextView>(R.id.tvMerchant1Amount)?.text = "₹0"
                Log.d(TAG, "No merchant insights available from API")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating top merchants", e)
        }
    }
    
    /**
     * Extract merchant name from API description
     */
    private fun extractMerchantName(description: String): String {
        // Look for merchant name patterns in the description
        val merchantKeywords = listOf("N/A", "Swiggy", "Zomato", "Amazon", "Flipkart", "Uber", "Ola")
        
        for (keyword in merchantKeywords) {
            if (description.contains(keyword, ignoreCase = true)) {
                return keyword
            }
        }
        
        // If no specific merchant found, extract first word that might be a merchant name
        val words = description.split(" ")
        return words.find { it.length > 3 && it[0].isUpperCase() } ?: "Unknown Merchant"
    }
    
    /**
     * Show/hide loading state (deprecated - use showShimmerLoading instead)
     */
    @Deprecated("Use showShimmerLoading instead")
    private fun showLoadingState(isLoading: Boolean) {
        showShimmerLoading(isLoading)
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
                Log.d(TAG, "Updated first budget recommendation: ${recommendations.first()}")
            }
            
            // Update second recommendation if available
            if (recommendations.size > 1) {
                binding.root.findViewById<TextView>(R.id.tvSavingsRecommendation2)?.text = 
                    recommendations[1]
                Log.d(TAG, "Updated second budget recommendation: ${recommendations[1]}")
            } else {
                // Hide second recommendation if only one available
                binding.root.findViewById<TextView>(R.id.tvSavingsRecommendation2)?.visibility = 
                    android.view.View.GONE
            }
            
            Log.d(TAG, "Budget optimization updated with ${recommendations.size} recommendations")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating budget optimization", e)
        }
    }
    
    /**
     * Show error message via Toast (for additional feedback)
     */
    private fun showError(message: String?) {
        if (message != null) {
            Toast.makeText(requireContext(), "Insights: $message", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Showing error toast: $message")
        }
    }
    
    /**
     * Show indicator that we're using sample data
     */
    private fun showSampleDataIndicator() {
        // Could show a small badge or toast indicating sample data
        Log.d(TAG, "Using sample insights data")
    }
    
    /**
     * Force initial refresh when fragment loads
     */
    private fun forceInitialRefresh() {
        Log.d(TAG, "Forcing initial refresh on fragment load")
        
        // Trigger refresh immediately
        viewLifecycleOwner.lifecycleScope.launch {
            // Small delay to ensure ViewModel is fully setup
            kotlinx.coroutines.delay(100)
            viewModel.handleEvent(InsightsUIEvent.Refresh)
        }
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
    
    /**
     * Handle refresh action (connected to pull-to-refresh)
     */
    private fun onRefresh() {
        viewModel.handleEvent(InsightsUIEvent.Refresh)
        Log.d(TAG, "Manual refresh triggered")
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Stop any running shimmer animations
        stopAllShimmerAnimations(binding.root)
        
        Log.d(TAG, "Fragment view destroyed")
        _binding = null
    }
    
    override fun onPause() {
        super.onPause()
        // Stop shimmer animations when fragment is not visible
        if (::shimmerLoading.isInitialized) {
            stopAllShimmerAnimations(shimmerLoading)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Restart shimmer animations if we're in loading state
        if (::shimmerLoading.isInitialized && shimmerLoading.visibility == View.VISIBLE) {
            startAllShimmerAnimations(shimmerLoading)
        }
        
        // Force refresh if no data is currently displayed
        if (::viewModel.isInitialized && viewModel.uiState.value.insights.isEmpty()) {
            Log.d(TAG, "Fragment resumed with no data - triggering refresh")
            viewModel.handleEvent(InsightsUIEvent.Refresh)
        }
    }
    
    // ===== ENHANCED ANALYTICS FUNCTIONALITY =====
    
    /**
     * Setup enhanced filter functionality
     */
    private fun setupAnalyticsFilters() {
        Log.d(TAG, "Setting up enhanced analytics filters")
        
        // First ensure the filter card is visible
        val cardFilters = binding.root.findViewById<View>(R.id.cardFilters)
        if (cardFilters != null) {
            cardFilters.visibility = View.VISIBLE
            Log.d(TAG, "FILTER: Filter card found and set to visible")
        } else {
            Log.w(TAG, "FILTER: Filter card not found in layout")
        }
        
        // Date Range Filter
        val btnDateRange = binding.root.findViewById<View>(R.id.btnDateRange)
        if (btnDateRange != null) {
            btnDateRange.setOnClickListener {
                Log.d(TAG, "FILTER: Date range button clicked")
                showDateRangeDialog()
            }
            Log.d(TAG, "FILTER: Date range button found and listener set")
        } else {
            Log.w(TAG, "FILTER: Date range button not found in layout")
        }
        
        // Time Period Filter
        val btnTimePeriod = binding.root.findViewById<View>(R.id.btnTimePeriod)
        if (btnTimePeriod != null) {
            btnTimePeriod.setOnClickListener {
                Log.d(TAG, "FILTER: Time period button clicked")
                showTimePeriodDialog()
            }
            Log.d(TAG, "FILTER: Time period button found and listener set")
        } else {
            Log.w(TAG, "FILTER: Time period button not found in layout")
        }
        
        // Category Filter
        val btnCategoryFilter = binding.root.findViewById<View>(R.id.btnCategoryFilter)
        if (btnCategoryFilter != null) {
            btnCategoryFilter.setOnClickListener {
                Log.d(TAG, "FILTER: Category filter button clicked")
                showCategoryFilterDialog()
            }
            Log.d(TAG, "FILTER: Category filter button found and listener set")
        } else {
            Log.w(TAG, "FILTER: Category filter button not found in layout")
        }
        
        // Merchant Filter
        val btnMerchantFilter = binding.root.findViewById<View>(R.id.btnMerchantFilter)
        if (btnMerchantFilter != null) {
            btnMerchantFilter.setOnClickListener {
                Log.d(TAG, "FILTER: Merchant filter button clicked")
                showMerchantFilterDialog()
            }
            Log.d(TAG, "FILTER: Merchant filter button found and listener set")
        } else {
            Log.w(TAG, "FILTER: Merchant filter button not found in layout")
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
                
                Log.d(TAG, "FILTER: Amount range updated: ₹${currentFilters.minAmount} - ₹${currentFilters.maxAmount}")
                
                // Trigger analytics refresh with new filters
                applyFiltersAndRefresh()
            }
            Log.d(TAG, "FILTER: Range slider found and listener set")
        } else {
            Log.w(TAG, "FILTER: Range slider not found in layout")
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
            Log.d(TAG, "PIE_CHART_FIX: Time period dialog blocked - currently in PIE Chart tab")
            return
        }
        
        Log.d(TAG, "TIME_PERIOD_FILTER: Showing time period dialog for tab position: $currentTab")
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
        Log.d(TAG, "FILTER: Category filter dialog requested")
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())
                val categories = expenseRepository.getAllCategoriesSync()
                
                Log.d(TAG, "PIE_CHART_FIX: Loading categories from DB - Found ${categories.size} categories")
                categories.forEach { category ->
                    Log.d(TAG, "PIE_CHART_FIX: Category from DB: ${category.name} (ID: ${category.id})")
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
                        
                        Log.d(TAG, "FILTER: Selected categories: ${selectedCategories.joinToString()}")
                        applyFiltersAndRefresh()
                    }
                    .setNegativeButton("Clear") { _: android.content.DialogInterface, _: Int ->
                        currentFilters.selectedCategories = emptyList()
                        applyFiltersAndRefresh()
                    }
                    .show()
                    
            } catch (e: Exception) {
                Log.e(TAG, "FILTER: Error loading categories", e)
                Toast.makeText(requireContext(), "Error loading categories", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Show merchant filter dialog
     */
    private fun showMerchantFilterDialog() {
        Log.d(TAG, "FILTER: Merchant filter dialog requested")
        
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
                        
                        Log.d(TAG, "FILTER: Selected merchants: ${selectedMerchants.joinToString()}")
                        applyFiltersAndRefresh()
                    }
                    .setNegativeButton("Clear") { _: android.content.DialogInterface, _: Int ->
                        currentFilters.selectedMerchants = emptyList()
                        applyFiltersAndRefresh()
                    }
                    .show()
                    
            } catch (e: Exception) {
                Log.e(TAG, "FILTER: Error loading merchants", e)
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
        Log.d(TAG, "Amount range updated: $displayText")
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
            Log.e(TAG, "Error getting category for transaction", e)
            "Other"
        }
    }
    
    /**
     * Apply date range filter with smart time aggregation update
     */
    private fun applyDateRangeFilter(dateRange: String) {
        Log.d(TAG, "FILTER: Applying date range filter: $dateRange")

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

                // Update smart time aggregation for BAR_CHART when date range changes
                currentFilters.timeAggregation = getSmartTimeAggregation(startDate, endDate)
                binding.root.findViewById<TextView>(R.id.btnTimePeriod)?.text = currentFilters.timeAggregation

                Log.d(TAG, "BAR_CHART_FIX: Applied ${dateRange} filter: ${startDate} to ${endDate}")
                Log.d(TAG, "BAR_CHART_FIX: Updated time aggregation to: ${currentFilters.timeAggregation}")
            } else {
                currentFilters.startDate = null
                currentFilters.endDate = null
                Log.d(TAG, "FILTER: No specific date range filter applied for: $dateRange")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FILTER: Error applying date range filter", e)
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
        Log.d(TAG, "TIME_PERIOD_FILTER: User overriding time period filter: $timePeriod")
        
        // User can override smart aggregation - this takes precedence
        currentFilters.timeAggregation = timePeriod
        
        // Log for debugging
        Log.d(TAG, "BAR_CHART_USER_OVERRIDE: User manually set timeAggregation to: ${currentFilters.timeAggregation}")
        
        // Apply the filter and refresh the charts
        applyFiltersAndRefresh()
    }
    
    /**
     * Apply all current filters and refresh analytics
     */
    private fun applyFiltersAndRefresh() {
        Log.d(TAG, "Applying all filters and refreshing analytics")
        
        // Show loading state
        showShimmerLoading(true)
        
        // Trigger refresh with current filters
        viewModel.handleEvent(InsightsUIEvent.Refresh)
        
        // Update chart data with filtered results
        updateChartsWithFilteredData()
    }
    
    /**
     * Setup interactive charts
     */
    private fun setupInteractiveCharts() {
        Log.d(TAG, "Setting up interactive charts")
        
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
            tabs.addTab(tabs.newTab().setText("Trends").setIcon(R.drawable.ic_chart))
            
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
        Log.d(TAG, "Setting up chart ViewPager")
        // ViewPager2 setup would be implemented with chart fragments
        // This is a placeholder for the actual chart implementation
    }
    
    /**
     * Update chart ViewPager
     */
    private fun updateChartViewPager(position: Int) {
        Log.d(TAG, "Updating chart ViewPager to position: $position")
        // Switch between different chart types
        when (position) {
            0 -> showCategoryPieChart()
            1 -> showMonthlyBarChart()
            2 -> showTrendLineChart()
        }
    }
    
    /**
     * Show category pie chart
     */
    private fun showCategoryPieChart() {
        Log.d(TAG, "PIE_CHART: FILTER_ENABLED Showing category pie chart with applied filters")
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())
                
                // Get filtered category spending data
                val categorySpendingResults = getFilteredCategorySpending(expenseRepository)
                Log.d(TAG, "PIE_CHART: Retrieved ${categorySpendingResults.size} categories with filters applied")
                Log.d(TAG, "FILTER: Active filters - Categories: ${currentFilters.selectedCategories.joinToString()}, Amount: ₹${currentFilters.minAmount}-₹${currentFilters.maxAmount}")
                
                // PIE_CHART_FIX: Debug which categories are coming from spending data
                Log.d(TAG, "PIE_CHART_FIX: Categories from spending data:")
                categorySpendingResults.forEach { category ->
                    Log.d(TAG, "PIE_CHART_FIX: Spending category: ${category.category_name} - ₹${category.total_amount} (${category.transaction_count} transactions)")
                }
                
                if (categorySpendingResults.isNotEmpty()) {
                    setupCategoryPieChart(categorySpendingResults)
                } else {
                    Log.d(TAG, "PIE_CHART: No category data available for filtered period, showing empty state")
                    showEmptyPieChart()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "PIE_CHART: Error loading filtered category data", e)
                showEmptyPieChart()
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
        
        Log.d(TAG, "PIE_CHART: Current system date filter - Start: $startOfMonth, End: $endOfMonth")
        
        return Pair(startOfMonth, endOfMonth)
    }
    
    /**
     * Setup category pie chart with actual data
     */
    private fun setupCategoryPieChart(categorySpendingResults: List<com.expensemanager.app.data.dao.CategorySpendingResult>) {
        Log.d(TAG, "PIE_CHART: Setting up pie chart with ${categorySpendingResults.size} categories")
        
        try {
            // Find the pie chart in the current chart layout
            val chartView = findChartView()
            if (chartView == null) {
                Log.e(TAG, "PIE_CHART: Could not find chart view")
                return
            }
            
            // Show pie chart layout and find PieChart component
            showPieChartLayout()
            val pieChart = chartView.findViewById<PieChart>(R.id.pieChartCategories)
            if (pieChart == null) {
                Log.e(TAG, "PIE_CHART: PieChart component not found in layout")
                return
            }
            
            // Calculate total for percentages
            val totalAmount = categorySpendingResults.sumOf { result -> result.total_amount }
            Log.d(TAG, "PIE_CHART: Total amount: ₹${String.format("%.2f", totalAmount)}")
            
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
                invalidate()
            }
            
            // Setup legend/category list
            setupCategoryLegend(chartView, categorySpendingResults, totalAmount)
            
            // Update footer statistics with real data
            updateChartSummaryStats(categorySpendingResults)
            
            Log.d(TAG, "PIE_CHART: Pie chart setup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "PIE_CHART: Error setting up pie chart", e)
        }
    }
    
    /**
     * Show empty pie chart state
     */
    private fun showEmptyPieChart() {
        Log.d(TAG, "PIE_CHART: Showing empty pie chart state")
        
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
            Log.e(TAG, "PIE_CHART: Error showing empty state", e)
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
            Log.e(TAG, "PIE_CHART: Error finding chart view", e)
            null
        }
    }
    
    /**
     * Show pie chart layout (inflate if needed)
     */
    private fun showPieChartLayout() {
        Log.d(TAG, "PIE_CHART: Showing pie chart layout")
        
        try {
            val viewPager = binding.root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPagerCharts)
            if (viewPager != null) {
                // If ViewPager2 exists, set up the adapter
                if (viewPager.adapter == null) {
                    Log.d(TAG, "PIE_CHART: Setting up ViewPager2 adapter")
                    viewPager.adapter = ChartPagerAdapter(this)
                }
                // Ensure we're on the pie chart page (index 0)
                viewPager.currentItem = 0
            } else {
                Log.w(TAG, "PIE_CHART: ViewPager2 not found, chart layout may not be available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "PIE_CHART: Error setting up chart layout", e)
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
                Log.d(TAG, "PIE_CHART: Setting up category legend with ${categorySpendingResults.size} items")
                
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
                
                Log.d(TAG, "PIE_CHART: Created ${legendItems.size} legend items")
                legendItems.forEach { item ->
                    Log.d(TAG, "PIE_CHART: Legend item - ${item.name}, color: ${item.color}, amount: ${item.amount}")
                }
                
                // Setup RecyclerView with adapter
                recyclerView.layoutManager = LinearLayoutManager(requireContext())
                recyclerView.adapter = CategoryLegendAdapter(legendItems)
                
                // Make sure RecyclerView is visible
                recyclerView.visibility = View.VISIBLE
                
                Log.d(TAG, "PIE_CHART: RecyclerView adapter set with ${legendItems.size} items")
                
            } else {
                Log.w(TAG, "PIE_CHART: Category legend RecyclerView not found in view")
            }
        } catch (e: Exception) {
            Log.e(TAG, "PIE_CHART: Error setting up category legend", e)
        }
    }
    
    /**
     * Show monthly bar chart
     */
    private fun showMonthlyBarChart() {
        Log.d(TAG, "BAR_CHART: Showing monthly bar chart with filters")
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())
                
                Log.d(TAG, "BAR_CHART: Loading monthly data with filter: ${currentFilters.timePeriod}")
                
                // Get monthly spending data based on current filter
                val chartData = getTimeSeriesSpendingData(expenseRepository)
                
                Log.d(TAG, "BAR_CHART_FLOW_DEBUG: getTimeSeriesSpendingData returned ${chartData.size} data points")
                Log.d(TAG, "BAR_CHART_FLOW_DEBUG: Data points: ${chartData.map { "${it.label}: ₹${it.amount}" }}")
                
                val chartView = findChartView()
                Log.d(TAG, "BAR_CHART_FLOW_DEBUG: findChartView() returned: ${chartView != null}")
                
                if (chartView != null) {
                    showBarChartLayout()
                    Log.d(TAG, "BAR_CHART_FLOW_DEBUG: About to call setupTimeSeriesBarChart with ${chartData.size} data points")
                    
                    // BAR_CHART_FIX: For ViewPager2, we need to get the current fragment's BarChart
                    if (chartView is androidx.viewpager2.widget.ViewPager2) {
                        Log.d(TAG, "BAR_CHART_FIX: chartView is ViewPager2, setting up adapter and finding BarChart")
                        
                        // Set up ViewPager2 adapter if not already set
                        if (chartView.adapter == null) {
                            chartView.adapter = ChartPagerAdapter(this@InsightsFragment)
                        }
                        
                        // Switch to bar chart page (index 1)
                        chartView.currentItem = 1
                        
                        // Post delayed to ensure fragment is created and view is inflated
                        chartView.post {
                            // Try to find the actual BarChart in the current fragment
                            val currentFragment = childFragmentManager.findFragmentByTag("f1") // ViewPager2 uses "f{position}" tags
                            val barChart = currentFragment?.view?.findViewById<BarChart>(R.id.barChartMonthly)
                            
                            if (barChart != null && chartData.isNotEmpty()) {
                                Log.d(TAG, "BAR_CHART_FIX: Found BarChart in ViewPager2 fragment")
                                val success = chartConfigurationService.setupTimeSeriesBarChart(
                                    barChart, 
                                    chartData, 
                                    currentFilters.timeAggregation ?: "Monthly"
                                )
                                Log.d(TAG, "BAR_CHART_FIX: ChartConfigurationService setup result: $success")
                                
                                if (success) {
                                    // Update summary statistics
                                    currentFragment.view?.let { fragmentView ->
                                        updateTimeSeriesChartSummary(fragmentView, chartData)
                                    }
                                }
                            } else {
                                Log.w(TAG, "BAR_CHART_FIX: BarChart not found in ViewPager2 fragment or no data")
                            }
                        }
                    } else if (chartView is BarChart && chartData.isNotEmpty()) {
                        // Direct BarChart (fallback case)
                        Log.d(TAG, "BAR_CHART_FIX: Using chartView directly as BarChart")
                        val success = chartConfigurationService.setupTimeSeriesBarChart(
                            chartView, 
                            chartData, 
                            currentFilters.timeAggregation ?: "Monthly"
                        )
                        Log.d(TAG, "BAR_CHART_FIX: ChartConfigurationService setup result: $success")
                        
                        if (success) {
                            // Update summary statistics  
                            updateTimeSeriesChartSummary(chartView, chartData)
                        }
                    } else {
                        Log.w(TAG, "BAR_CHART_FIX: chartView is not BarChart/ViewPager2 or no data available")
                        Log.e(TAG, "BAR_CHART_FIX: chartView type: ${chartView?.javaClass?.simpleName}")
                        Log.e(TAG, "BAR_CHART_FIX: chartData size: ${chartData.size}")
                    }
                } else {
                    Log.e(TAG, "BAR_CHART_FLOW_DEBUG: Chart view not found - cannot setup chart")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "BAR_CHART: Error loading monthly data", e)
            }
        }
    }
    
    /**
     * Get time series spending data based on current time aggregation and date range filters
     * Now uses TimeSeriesAggregationService to eliminate ~400 lines of duplicated logic
     */
    private suspend fun getTimeSeriesSpendingData(repository: ExpenseRepository): List<TimeSeriesData> {
        Log.d(TAG, "TIME_PERIOD_FILTER: Getting time series data for aggregation: ${currentFilters.timeAggregation}")
        
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
                else -> when (aggregationType) {
                    TimeAggregation.DAILY -> 30
                    TimeAggregation.WEEKLY -> 4
                    TimeAggregation.MONTHLY -> 6
                    TimeAggregation.QUARTERLY -> 2
                    TimeAggregation.YEARLY -> 1
                }
            }
            
            Log.d(TAG, "TIME_PERIOD_FILTER: Using aggregation: $aggregationType, periods: $periodCount")
            
            // Use the existing date range from filters if available, otherwise calculate based on aggregation
            val (startDate, endDate) = if (currentFilters.startDate != null && currentFilters.endDate != null) {
                Log.d(TAG, "BAR_CHART: Using filter date range: ${currentFilters.startDate} to ${currentFilters.endDate}")
                Pair(currentFilters.startDate!!, currentFilters.endDate!!)
            } else {
                // Fallback to calculated date range for backward compatibility
                Log.d(TAG, "BAR_CHART: No filter date range set, calculating based on aggregation")
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
            
            Log.d(TAG, "TIME_PERIOD_FILTER: Retrieved ${allTransactions.size} transactions, filtered to ${filteredTransactions.size}")
            
            // Use TimeSeriesAggregationService to generate time series data
            val timeSeriesData = timeSeriesAggregationService.generateTimeSeriesData(
                filteredTransactions,
                aggregationType,
                periodCount
            )
            
            Log.d(TAG, "TIME_PERIOD_FILTER: Generated ${timeSeriesData.size} time series data points")
            return timeSeriesData
            
        } catch (e: Exception) {
            Log.e(TAG, "TIME_PERIOD_FILTER: Error generating time series data", e)
            return emptyList()
        }
    }
    
    /**
     * Setup time series bar chart with actual data (supports Daily, Weekly, Monthly, Quarterly, Yearly)
     */
    private fun setupTimeSeriesBarChart(chartView: View, timeSeriesData: List<TimeSeriesData>) {
        try {
            showBarChartLayout()
            val barChart = chartView.findViewById<BarChart>(R.id.barChartMonthly)
            if (barChart == null) {
                Log.e(TAG, "BAR_CHART: BarChart component not found in layout")
                return
            }
            
            Log.d(TAG, "BAR_CHART_RENDER_DEBUG: Setting up bar chart with ${timeSeriesData.size} data points for ${currentFilters.timeAggregation}")
            Log.d(TAG, "BAR_CHART_RENDER_DEBUG: TimeSeriesData: ${timeSeriesData.map { "${it.label}: ₹${it.amount}" }}")
            
            // Check if we have valid data
            if (timeSeriesData.isEmpty()) {
                Log.w(TAG, "BAR_CHART_RENDER_DEBUG: No data available for chart")
                return
            }
            
            // Create bar entries
            val barEntries = timeSeriesData.mapIndexed {
                index, data ->
                Log.d(TAG, "BAR_CHART_RENDER_DEBUG: Creating BarEntry[$index] = ${data.amount} for ${data.label}")
                BarEntry(index.toFloat(), data.amount.toFloat())
            }
            
            Log.d(TAG, "BAR_CHART_RENDER_DEBUG: Created ${barEntries.size} bar entries: ${barEntries.map { "x=${it.x}, y=${it.y}" }}")
            
            // Create dataset
            val dataSet = BarDataSet(barEntries, "Monthly Spending").apply {
                colors = listOf(
                    ContextCompat.getColor(requireContext(), R.color.primary),
                    ContextCompat.getColor(requireContext(), R.color.secondary),
                    ContextCompat.getColor(requireContext(), R.color.info),
                    ContextCompat.getColor(requireContext(), R.color.success),
                    ContextCompat.getColor(requireContext(), R.color.warning),
                    ContextCompat.getColor(requireContext(), R.color.error)
                )
                valueTextSize = 12f
                valueTextColor = Color.WHITE
            }
            
            // Create bar data
            val barData = BarData(dataSet).apply {
                setValueFormatter(object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "₹${String.format("%.0f", value)}"
                    }
                })
                barWidth = 0.8f
            }
            
            // Configure bar chart
            barChart.apply {
                data = barData
                description.isEnabled = false
                legend.isEnabled = false
                
                // X-axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index >= 0 && index < timeSeriesData.size) {
                                timeSeriesData[index].label
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
                
                animateY(1000)
                invalidate()
            }
            
            // Update summary statistics
            updateTimeSeriesChartSummary(chartView, timeSeriesData)
            
            Log.d(TAG, "BAR_CHART_RENDER_DEBUG: Chart setup completed successfully")
            Log.d(TAG, "BAR_CHART_RENDER_DEBUG: Chart data count: ${barChart.data?.entryCount ?: 0}")
            Log.d(TAG, "BAR_CHART_RENDER_DEBUG: Chart visibility: ${barChart.visibility}")
            Log.d(TAG, "BAR_CHART_RENDER_DEBUG: Chart isEmpty: ${barChart.isEmpty}")
            
        } catch (e: Exception) {
            Log.e(TAG, "BAR_CHART_RENDER_DEBUG: Error setting up bar chart", e)
        }
    }
    
    /**
     * Update monthly chart summary statistics
     */
    private fun updateTimeSeriesChartSummary(chartView: View, timeSeriesData: List<TimeSeriesData>) {
        try {
            val currentPeriod = timeSeriesData.lastOrNull()
            val previousPeriod = if (timeSeriesData.size >= 2) timeSeriesData[timeSeriesData.size - 2] else null
            
            // Current period
            chartView.findViewById<TextView>(R.id.tvCurrentMonth)?.text = 
                "₹${String.format("%.0f", currentPeriod?.amount ?: 0.0)}"
                
            // Previous period  
            chartView.findViewById<TextView>(R.id.tvLastMonth)?.text = 
                "₹${String.format("%.0f", previousPeriod?.amount ?: 0.0)}"
                
            // Calculate trend percentage
            val trendPercentage = if (currentPeriod != null && previousPeriod != null && previousPeriod.amount > 0) {
                ((currentPeriod.amount - previousPeriod.amount) / previousPeriod.amount * 100)
            } else 0.0
            
            val trendColor = when {
                trendPercentage > 0 -> ContextCompat.getColor(requireContext(), R.color.error)
                trendPercentage < 0 -> ContextCompat.getColor(requireContext(), R.color.success)
                else -> ContextCompat.getColor(requireContext(), R.color.text_secondary)
            }
            
            chartView.findViewById<TextView>(R.id.tvTrendPercentage)?.apply {
                text = "${if (trendPercentage >= 0) "+" else ""}${String.format("%.1f", trendPercentage)}%"
                setTextColor(trendColor)
            }
            
            Log.d(TAG, "TIME_PERIOD_FILTER: Updated time series summary - Current: ₹${currentPeriod?.amount}, Previous: ₹${previousPeriod?.amount}, Trend: $trendPercentage%")
            
        } catch (e: Exception) {
            Log.e(TAG, "BAR_CHART: Error updating monthly summary", e)
        }
    }
    
    /**
     * Show bar chart layout
     */
    private fun showBarChartLayout() {
        Log.d(TAG, "BAR_CHART: Showing bar chart layout")
        
        try {
            val viewPager = binding.root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPagerCharts)
            if (viewPager != null) {
                // Ensure we're on the bar chart page (index 1)
                viewPager.currentItem = 1
            }
        } catch (e: Exception) {
            Log.e(TAG, "BAR_CHART: Error setting up chart layout", e)
        }
    }
    
    /**
     * Show trend line chart
     */
    private fun showTrendLineChart() {
        Log.d(TAG, "LINE_CHART: Showing trend line chart with filters")
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())
                
                Log.d(TAG, "LINE_CHART: Loading daily trend data for last 30 days")
                
                // Get daily spending data for last 30 days
                val dailyData = getDailyTrendData(expenseRepository)
                
                val chartView = findChartView()
                if (chartView != null) {
                    showLineChartLayout()
                    setupTrendLineChart(chartView, dailyData)
                } else {
                    Log.e(TAG, "LINE_CHART: Chart view not found")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "LINE_CHART: Error loading trend data", e)
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
        
        Log.d(TAG, "LINE_CHART: Generated ${dailyData.size} days of trend data")
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
                Log.e(TAG, "LINE_CHART: LineChart component not found in layout")
                return
            }
            
            Log.d(TAG, "LINE_CHART: Setting up line chart with ${dailyData.size} days")
            
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
                invalidate()
            }
            
            // Update trend analysis
            updateTrendAnalysis(chartView, dailyData)
            
            Log.d(TAG, "LINE_CHART: Trend line chart setup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "LINE_CHART: Error setting up line chart", e)
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
            
            // Update UI
            chartView.findViewById<TextView>(R.id.tvAvgDailySpend)?.text = 
                "₹${String.format("%.0f", avgDaily)}"
                
            chartView.findViewById<TextView>(R.id.tvPeakDay)?.text = 
                "₹${String.format("%.0f", peakDay?.amount ?: 0.0)}"
                
            chartView.findViewById<TextView>(R.id.tvTrendDirection)?.apply {
                text = trendDirection
                setTextColor(trendColor)
            }
            
            Log.d(TAG, "LINE_CHART: Updated trend analysis - Avg: ₹$avgDaily, Peak: ₹${peakDay?.amount}, Trend: $trendDirection")
            
        } catch (e: Exception) {
            Log.e(TAG, "LINE_CHART: Error updating trend analysis", e)
        }
    }
    
    /**
     * Show line chart layout
     */
    private fun showLineChartLayout() {
        Log.d(TAG, "LINE_CHART: Showing line chart layout")
        
        try {
            val viewPager = binding.root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPagerCharts)
            if (viewPager != null) {
                // Ensure we're on the line chart page (index 2)
                viewPager.currentItem = 2
            }
        } catch (e: Exception) {
            Log.e(TAG, "LINE_CHART: Error setting up chart layout", e)
        }
    }

    private fun getSmartTimeAggregation(startDate: Date, endDate: Date): String {
        val diff = endDate.time - startDate.time
        val days = diff / (1000 * 60 * 60 * 24)
        return when {
            days <= 7 -> "Daily"
            days <= 31 -> "Weekly"
            else -> "Monthly"
        }
    }

    private fun updateChartsWithFilteredData() {
        showMonthlyBarChart()
        showCategoryPieChart()
        showTrendLineChart()
    }

    private suspend fun getFilteredCategorySpending(expenseRepository: ExpenseRepository): List<com.expensemanager.app.data.dao.CategorySpendingResult> {
        currentFilters.startDate?.let { startDate ->
            currentFilters.endDate?.let { endDate ->
                return expenseRepository.getCategorySpending(startDate, endDate)
            }
        }
        return emptyList()
    }


    private fun updateChartSummaryStats(categoryData: List<com.expensemanager.app.data.dao.CategorySpendingResult> = emptyList()) {
        Log.d(TAG, "Updating chart summary statistics with ${categoryData.size} categories")

        try {
            // Update total categories
            binding.root.findViewById<android.widget.TextView>(R.id.tvTotalCategories)?.text = "${categoryData.size}"

            // Update top category (highest spending)
            val topCategory = categoryData.maxByOrNull { it.total_amount }
            if (topCategory != null) {
                binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryAmount)?.text = "₹${String.format("%.0f", topCategory.total_amount)}"
                binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryName)?.text = topCategory.category_name
                Log.d(TAG, "PIE_CHART: Top category: ${topCategory.category_name} = ₹${topCategory.total_amount}")
            } else {
                binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryAmount)?.text = "₹0"
                binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryName)?.text = "No Data"
            }

            // Update average transaction (total amount / total transaction count)
            val totalAmount = categoryData.sumOf { data -> data.total_amount }
            val totalTransactions = categoryData.sumOf { data -> data.transaction_count }
            val avgPerTransaction = if (totalTransactions > 0) totalAmount / totalTransactions else 0.0

            binding.root.findViewById<android.widget.TextView>(R.id.tvAvgTransaction)?.text = "₹${String.format("%.0f", avgPerTransaction)}"

            Log.d(TAG, "PIE_CHART: Summary stats - Categories: ${categoryData.size}, Total: ₹$totalAmount, Avg: ₹$avgPerTransaction")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating chart summary stats", e)
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
 * Generic time series data class that supports Daily, Weekly, Monthly, Quarterly, Yearly aggregations
 */

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
        
        android.util.Log.d("CategoryLegend", "Binding item: ${item.name}, color: ${item.color}, amount: ${item.amount}")
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
        Log.d("ChartFragment", "Chart fragment created for type: $chartType")
        
        // Trigger chart setup when fragment is ready
        if (chartType == "pie_category") {
            // Find parent InsightsFragment and trigger pie chart setup
            val parentFragment = parentFragment as? InsightsFragment
            parentFragment?.triggerPieChartSetup()
        }
    }
}