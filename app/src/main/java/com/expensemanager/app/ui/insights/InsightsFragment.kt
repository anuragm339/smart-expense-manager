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
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
        Log.d(TAG, "Fragment view created")
        
        initializeViews()
        setupViewModel()
        setupUI()
        observeUIState()
        setupClickListeners()
        
        // Force initial refresh when fragment is created
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
}