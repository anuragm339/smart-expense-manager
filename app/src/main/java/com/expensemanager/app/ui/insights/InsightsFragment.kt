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
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import java.util.*
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
    
    // State management views
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shimmerLoading: View
    private lateinit var errorState: View
    private lateinit var emptyState: View
    private lateinit var contentLayout: View
    
    // Enhanced Analytics Components
    private lateinit var filtersCard: View
    private lateinit var chartsCard: View
    
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
        
        // Date Range Filter
        binding.root.findViewById<View>(R.id.btnDateRange)?.setOnClickListener {
            showDateRangeDialog()
        }
        
        // Time Period Filter
        binding.root.findViewById<View>(R.id.btnTimePeriod)?.setOnClickListener {
            showTimePeriodDialog()
        }
        
        // Category Filter
        binding.root.findViewById<View>(R.id.btnCategoryFilter)?.setOnClickListener {
            showCategoryFilterDialog()
        }
        
        // Merchant Filter
        binding.root.findViewById<View>(R.id.btnMerchantFilter)?.setOnClickListener {
            showMerchantFilterDialog()
        }
        
        // Amount Range Slider
        binding.root.findViewById<com.google.android.material.slider.RangeSlider>(R.id.rangeSliderAmount)?.let { slider ->
            slider.addOnChangeListener { _, _, _ ->
                updateAmountRangeDisplay(slider.values)
                // Trigger analytics refresh with new filters
                applyFiltersAndRefresh()
            }
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
        // Placeholder for category selection - would integrate with existing categories
        Toast.makeText(requireContext(), "Category filter - Coming soon!", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Category filter dialog requested")
    }
    
    /**
     * Show merchant filter dialog
     */
    private fun showMerchantFilterDialog() {
        // Placeholder for merchant selection - would integrate with existing merchants
        Toast.makeText(requireContext(), "Merchant filter - Coming soon!", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Merchant filter dialog requested")
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
     * Apply date range filter
     */
    private fun applyDateRangeFilter(dateRange: String) {
        Log.d(TAG, "Applying date range filter: $dateRange")
        // Implementation would depend on ViewModel integration
        applyFiltersAndRefresh()
    }
    
    /**
     * Apply time period filter
     */
    private fun applyTimePeriodFilter(timePeriod: String) {
        Log.d(TAG, "Applying time period filter: $timePeriod")
        // Implementation would depend on ViewModel integration
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
        Log.d(TAG, "PIE_CHART: CLAUDE_FIXED_VERSION Showing category pie chart with current system date context")
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())
                val getCategorySpendingUseCase = GetCategorySpendingUseCase(expenseRepository)
                
                Log.d(TAG, "PIE_CHART: Using current month data via UseCase (consistent with Dashboard)")
                
                // Get category spending data using UseCase for consistency with Dashboard
                val result = getCategorySpendingUseCase.getCurrentMonthSpending()
                val categorySpendingResults = result.getOrNull() ?: emptyList()
                Log.d(TAG, "PIE_CHART: Retrieved ${categorySpendingResults.size} categories")
                
                if (categorySpendingResults.isNotEmpty()) {
                    setupCategoryPieChart(categorySpendingResults)
                } else {
                    Log.d(TAG, "PIE_CHART: No category data available for current period, showing empty state")
                    showEmptyPieChart()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "PIE_CHART: Error loading category data", e)
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
            val totalAmount = categorySpendingResults.sumOf { it.total_amount }
            Log.d(TAG, "PIE_CHART: Total amount: ₹${String.format("%.2f", totalAmount)}")
            
            // Create pie entries
            val pieEntries = categorySpendingResults.map { categoryResult ->
                PieEntry(
                    categoryResult.total_amount.toFloat(),
                    categoryResult.category_name
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
        return categorySpendingResults.mapIndexed { index, categoryResult ->
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
                val legendItems = categorySpendingResults.map { categoryResult ->
                    CategoryLegendItem(
                        name = categoryResult.category_name,
                        amount = categoryResult.total_amount,
                        percentage = (categoryResult.total_amount / totalAmount * 100),
                        color = try { Color.parseColor(categoryResult.color) } catch (e: Exception) { Color.BLUE },
                        transactionCount = categoryResult.transaction_count
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
        Log.d(TAG, "Showing monthly bar chart")
        // Implementation for monthly bar chart
    }
    
    /**
     * Show trend line chart
     */
    private fun showTrendLineChart() {
        Log.d(TAG, "Showing trend line chart")
        // Implementation for trend line chart
    }
    
    /**
     * Update chart summary statistics with real data
     */
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
            val totalAmount = categoryData.sumOf { it.total_amount }
            val totalTransactions = categoryData.sumOf { it.transaction_count }
            val avgPerTransaction = if (totalTransactions > 0) totalAmount / totalTransactions else 0.0
            
            binding.root.findViewById<android.widget.TextView>(R.id.tvAvgTransaction)?.text = "₹${String.format("%.0f", avgPerTransaction)}"
            
            Log.d(TAG, "PIE_CHART: Summary stats - Categories: ${categoryData.size}, Total: ₹$totalAmount, Avg: ₹$avgPerTransaction")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating chart summary stats", e)
            // Fallback to zero values on error
            binding.root.findViewById<android.widget.TextView>(R.id.tvTotalCategories)?.text = "0"
            binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryAmount)?.text = "₹0"
            binding.root.findViewById<android.widget.TextView>(R.id.tvTopCategoryName)?.text = "No Data"
            binding.root.findViewById<android.widget.TextView>(R.id.tvAvgTransaction)?.text = "₹0"
        }
    }
    
    /**
     * Update charts with filtered data
     */
    private fun updateChartsWithFilteredData() {
        Log.d(TAG, "Updating charts with filtered data")
        
        // This would integrate with the actual data filtering logic
        // Summary stats will be updated when real data is loaded
        
        // Refresh current chart
        val tabLayout = binding.root.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutCharts)
        val selectedTab = tabLayout?.selectedTabPosition ?: 0
        updateChartViewPager(selectedTab)
    }
    
    /**
     * Initialize enhanced analytics features
     */
    private fun setupEnhancedAnalytics() {
        Log.d(TAG, "Setting up enhanced analytics features")
        
        // Setup filters
        setupAnalyticsFilters()
        
        // Setup interactive charts
        setupInteractiveCharts()
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "Fragment view created")
        
        initializeViews()
        setupViewModel()
        setupUI()
        observeUIState()
        setupClickListeners()
        
        // Setup enhanced analytics features
        setupEnhancedAnalytics()
        
        // Force initial refresh when fragment is created
        forceInitialRefresh()
        
        // Auto-load pie chart on fragment load
        autoLoadPieChart()
    }
    
    /**
     * Trigger pie chart setup from child fragment
     */
    fun triggerPieChartSetup(chartView: View) {
        Log.d(TAG, "PIE_CHART: CLAUDE_FIXED_VERSION Triggered from child fragment with system date context")
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val expenseRepository = ExpenseRepository.getInstance(requireContext())
                val getCategorySpendingUseCase = GetCategorySpendingUseCase(expenseRepository)
                
                Log.d(TAG, "PIE_CHART: Child fragment using current month data via UseCase (consistent with Dashboard)")
                
                // Get category spending data using UseCase for consistency with Dashboard
                val result = getCategorySpendingUseCase.getCurrentMonthSpending()
                val categorySpendingResults = result.getOrNull() ?: emptyList()
                Log.d(TAG, "PIE_CHART: Retrieved ${categorySpendingResults.size} categories")
                
                if (categorySpendingResults.isNotEmpty()) {
                    setupPieChartWithView(chartView, categorySpendingResults)
                } else {
                    Log.d(TAG, "PIE_CHART: No category data available for current period, showing empty state")
                    showEmptyPieChartWithView(chartView)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "PIE_CHART: Error loading category data", e)
                showEmptyPieChartWithView(chartView)
            }
        }
    }
    
    /**
     * Setup pie chart with specific view
     */
    private fun setupPieChartWithView(chartView: View, categorySpendingResults: List<com.expensemanager.app.data.dao.CategorySpendingResult>) {
        Log.d(TAG, "PIE_CHART: Setting up pie chart with specific view and ${categorySpendingResults.size} categories")
        
        try {
            // Find PieChart component in the provided view
            val pieChart = chartView.findViewById<PieChart>(R.id.pieChartCategories)
            if (pieChart == null) {
                Log.e(TAG, "PIE_CHART: PieChart component not found in provided view")
                return
            }
            
            // Calculate total for percentages
            val totalAmount = categorySpendingResults.sumOf { it.total_amount }
            Log.d(TAG, "PIE_CHART: Total amount: ₹${String.format("%.2f", totalAmount)}")
            
            // Create pie entries
            val pieEntries = categorySpendingResults.map { categoryResult ->
                PieEntry(
                    categoryResult.total_amount.toFloat(),
                    categoryResult.category_name
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
            
            Log.d(TAG, "PIE_CHART: Pie chart setup completed successfully with specific view")
            
        } catch (e: Exception) {
            Log.e(TAG, "PIE_CHART: Error setting up pie chart with specific view", e)
        }
    }
    
    /**
     * Show empty pie chart with specific view
     */
    private fun showEmptyPieChartWithView(chartView: View) {
        Log.d(TAG, "PIE_CHART: Showing empty pie chart state with specific view")
        
        try {
            val pieChart = chartView.findViewById<PieChart>(R.id.pieChartCategories)
            pieChart?.apply {
                clear()
                setNoDataText("No category data available for the selected period")
                setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                invalidate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "PIE_CHART: Error showing empty state with specific view", e)
        }
    }
    
    /**
     * Auto-load pie chart on fragment load
     */
    private fun autoLoadPieChart() {
        Log.d(TAG, "PIE_CHART: Auto-loading pie chart on fragment creation")
        
        // Delay to ensure ViewPager2 is ready
        view?.postDelayed({
            try {
                val viewPager = binding.root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPagerCharts)
                if (viewPager != null) {
                    Log.d(TAG, "PIE_CHART: Auto-setting ViewPager to Categories tab (position 0)")
                    viewPager.currentItem = 0
                    
                    // Set the TabLayout to match
                    val tabLayout = binding.root.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutCharts)
                    tabLayout?.selectTab(tabLayout.getTabAt(0))
                    
                    // Trigger the pie chart setup
                    showCategoryPieChart()
                } else {
                    Log.w(TAG, "PIE_CHART: ViewPager2 not found for auto-load")
                }
            } catch (e: Exception) {
                Log.e(TAG, "PIE_CHART: Error auto-loading pie chart", e)
            }
        }, 500) // 500ms delay to ensure UI is ready
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
            parentFragment?.triggerPieChartSetup(view)
        }
    }
}