package com.expensemanager.app.ui.insights

import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.models.AIInsight
import com.expensemanager.app.data.models.InsightType
import com.expensemanager.app.data.repository.OfflineException
import com.expensemanager.app.domain.insights.GetAIInsightsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * ViewModel for AI Insights screen
 * Manages UI state and handles user interactions
 */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val getAIInsightsUseCase: GetAIInsightsUseCase
) : ViewModel() {
    
    companion object {
        private const val TAG = "InsightsViewModel"
    }

    // Private mutable state
    private val _uiState = MutableStateFlow(InsightsUIState())

    // Public immutable state
    val uiState: StateFlow<InsightsUIState> = _uiState.asStateFlow()

    // Deduplication flag to prevent concurrent API calls
    private val isApiCallInProgress = AtomicBoolean(false)
    
    init {
        Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("ViewModel initialized, forcing fresh insights from API...")
        refreshInsights() // Always call API on initial load
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: InsightsUIEvent) {
        Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Handling event: $event")
        
        when (event) {
            is InsightsUIEvent.Refresh -> refreshInsights()
            is InsightsUIEvent.Retry -> retryLoadingInsights()
            is InsightsUIEvent.ClearError -> clearError()
            is InsightsUIEvent.ExpandCard -> expandCard(event.insightId)
            is InsightsUIEvent.CollapseCard -> collapseCard(event.insightId)
            is InsightsUIEvent.InsightClicked -> handleInsightClick(event.insight)
            is InsightsUIEvent.ActionClicked -> handleActionClick(event.insight, event.action)
        }
    }
    
    /**
     * Initial loading of insights
     * Prevents duplicate API calls using AtomicBoolean flag
     */
    private fun loadInsights() {
        // Check if API call already in progress
        if (!isApiCallInProgress.compareAndSet(false, true)) {
            Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("⚠️ API call already in progress, skipping duplicate loadInsights()")
            return
        }

        Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Starting initial load...")

        _uiState.value = _uiState.value.copy(
            isInitialLoading = true,
            hasError = false,
            error = null
        )

        viewModelScope.launch {
            try {
                getAIInsightsUseCase.execute().collect { result ->
                    handleInsightsResult(result, isInitialLoad = true)
                }
            } finally {
                // Reset flag when done (success or failure)
                isApiCallInProgress.set(false)
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("✅ API call completed, flag reset")
            }
        }
    }
    
    /**
     * Refresh insights (pull-to-refresh)
     * Prevents duplicate API calls using AtomicBoolean flag
     */
    private fun refreshInsights() {
        // Check if API call already in progress
        if (!isApiCallInProgress.compareAndSet(false, true)) {
            Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("⚠️ Refresh already in progress, skipping duplicate refreshInsights()")
            return
        }

        Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Refreshing insights...")

        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            hasError = false,
            error = null
        )

        viewModelScope.launch {
            try {
                val result = getAIInsightsUseCase.refreshInsights()
                handleInsightsResult(result, isRefresh = true)
            } finally {
                // Reset flag when done (success or failure)
                isApiCallInProgress.set(false)
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("✅ Refresh completed, flag reset")
            }
        }
    }
    
    /**
     * Retry loading after error
     * Prevents duplicate API calls using AtomicBoolean flag
     */
    private fun retryLoadingInsights() {
        // Check if API call already in progress
        if (!isApiCallInProgress.compareAndSet(false, true)) {
            Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("⚠️ API call already in progress, skipping duplicate retry")
            return
        }

        Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Retrying to load insights...")

        _uiState.value = _uiState.value.copy(
            isRetrying = true,
            hasError = false,
            error = null
        )

        viewModelScope.launch {
            try {
                val result = getAIInsightsUseCase.refreshInsights()
                handleInsightsResult(result, isRetry = true)
            } finally {
                // Reset flag when done (success or failure)
                isApiCallInProgress.set(false)
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("✅ Retry completed, flag reset")
            }
        }
    }
    
    /**
     * Handle insights result from use case with improved offline handling
     */
    private fun handleInsightsResult(
        result: Result<List<AIInsight>>, 
        isInitialLoad: Boolean = false,
        isRefresh: Boolean = false,
        isRetry: Boolean = false
    ) {
        result.fold(
            onSuccess = { insights ->
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("✅ Insights loaded successfully: ${insights.size} insights")

                // Log all insights with their details
                insights.forEachIndexed { index, insight ->
                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("  Insight #${index + 1}:")
                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("    - ID: ${insight.id}")
                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("    - Type: ${insight.type}")
                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("    - Title: ${insight.title}")
                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("    - Impact Amount: ₹${insight.impactAmount}")
                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("    - Priority: ${insight.priority}")
                }

                val groupedInsights = insights.groupBy { it.type }
                val isSampleData = insights.any { it.id.startsWith("forecast_") || it.id.startsWith("pattern_") || it.id.startsWith("offline_") }

                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("📊 Is sample/offline data: $isSampleData")

                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    isRetrying = false,
                    insights = insights,
                    groupedInsights = groupedInsights,
                    hasError = false,
                    error = null,
                    isEmpty = insights.isEmpty(),
                    lastRefreshTime = System.currentTimeMillis(),
                    showingSampleData = isSampleData,
                    isOfflineMode = false // Successfully loaded data, not in offline mode
                )

                // Log insight summary
                val summary = groupedInsights.mapValues { it.value.size }
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("📈 Loaded insights by type: $summary")
                
                // If this was sample data due to offline mode, show appropriate message
                if (isSampleData && (isRefresh || isRetry)) {
                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Loaded sample data due to connectivity issues")
                }
            },
            onFailure = { throwable ->
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).e(throwable, "Failed to load insights")
                
                val (errorMessage, isOffline) = when (throwable) {
                    is OfflineException -> {
                        "No internet connection. Showing cached data." to true
                    }
                    else -> {
                        val message = when {
                            throwable.message?.contains("Network") == true -> "Check your internet connection and try again"
                            throwable.message?.contains("API") == true -> "Service temporarily unavailable. Please try again later"
                            throwable.message?.contains("timeout") == true -> "Request timed out. Please try again"
                            else -> "Something went wrong. Please try again"
                        }
                        message to false
                    }
                }
                
                // Don't show error state if we have existing insights
                val hasExistingInsights = _uiState.value.insights.isNotEmpty()
                
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    isRetrying = false,
                    hasError = !hasExistingInsights, // Only show error if no existing data
                    error = if (hasExistingInsights) null else errorMessage,
                    isOfflineMode = isOffline
                )
                
                // If we have existing insights, just show a message instead of error state
                if (hasExistingInsights) {
                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Keeping existing insights, error: $errorMessage")
                }
            }
        )
    }
    
    /**
     * Clear error state
     */
    private fun clearError() {
        _uiState.value = _uiState.value.copy(
            hasError = false,
            error = null
        )
    }
    
    /**
     * Expand insight card
     */
    private fun expandCard(insightId: String) {
        val currentExpanded = _uiState.value.expandedCards
        _uiState.value = _uiState.value.copy(
            expandedCards = currentExpanded + insightId
        )
        Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Expanded card: $insightId")
    }
    
    /**
     * Collapse insight card
     */
    private fun collapseCard(insightId: String) {
        val currentExpanded = _uiState.value.expandedCards
        _uiState.value = _uiState.value.copy(
            expandedCards = currentExpanded - insightId
        )
        Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Collapsed card: $insightId")
    }
    
    /**
     * Handle insight card click
     */
    private fun handleInsightClick(insight: AIInsight) {
        Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Insight clicked: ${insight.title}")
        
        // Toggle card expansion
        if (_uiState.value.isCardExpanded(insight.id)) {
            collapseCard(insight.id)
        } else {
            expandCard(insight.id)
        }
    }
    
    /**
     * Handle action button clicks (e.g., "Create Savings Plan")
     */
    private fun handleActionClick(insight: AIInsight, action: String) {
        Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Action clicked: $action for insight: ${insight.title}")
        
        // Handle different action types
        when (action.lowercase()) {
            "create savings plan" -> {
                // Navigate to budget goals or create a savings plan
                // This would typically trigger a navigation event
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Creating savings plan for ${insight.impactAmount}")
            }
            "view breakdown" -> {
                // Navigate to categories screen
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Viewing breakdown for ${insight.type}")
            }
            "set reminder" -> {
                // Create a spending reminder
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Setting reminder for ${insight.title}")
            }
            else -> {
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Unhandled action: $action")
            }
        }
    }
    
    /**
     * Get insights by specific type for UI binding
     */
    fun getInsightsByType(type: InsightType): List<AIInsight> {
        return _uiState.value.getInsightsByType(type)
    }
    
    /**
     * Clear cache (for testing/debugging)
     */
    fun clearCache() {
        viewModelScope.launch {
            try {
                getAIInsightsUseCase.clearCache()
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Cache cleared")
                // Reload insights after clearing cache
                refreshInsights()
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).e(e, "Error clearing cache")
            }
        }
    }
    
    /**
     * Get formatted spending forecast data for UI
     */
    val spendingForecastData: StateFlow<SpendingForecastUIData> = _uiState
        .map { state ->
            val forecastInsight = state.getInsightsByType(InsightType.SPENDING_FORECAST).firstOrNull()
            val data = forecastInsight?.toSpendingForecastUIData() ?: SpendingForecastUIData()
            
            // Add offline indicator to advice if in offline mode
            if (state.isOfflineMode && data.advice.isNotEmpty()) {
                data.copy(advice = "${data.advice} (Offline data)")
            } else {
                data
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SpendingForecastUIData()
        )
    
    /**
     * Get pattern alerts for UI
     */
    val patternAlerts: StateFlow<List<PatternAlertUIData>> = _uiState
        .map { state ->
            state.getInsightsByType(InsightType.PATTERN_ALERT)
                .map { it.toPatternAlertUIData() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * Get savings opportunities for UI with offline mode awareness
     */
    val savingsOpportunities: StateFlow<SavingsOpportunityUIData> = _uiState
        .map { state ->
            val savingsInsights = state.getInsightsByType(InsightType.SAVINGS_OPPORTUNITY)

            Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("🔍 Calculating savings opportunities:")
            Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("  - Total insights: ${state.insights.size}")
            Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("  - Savings insights: ${savingsInsights.size}")
            Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("  - Is offline mode: ${state.isOfflineMode}")

            if (savingsInsights.isNotEmpty()) {
                // Log each savings insight
                savingsInsights.forEachIndexed { index, insight ->
                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("  - Savings #${index + 1}: id=${insight.id}, title=${insight.title}, impactAmount=₹${insight.impactAmount}")
                }

                val totalMonthly = savingsInsights.sumOf { it.impactAmount }
                val yearlyImpact = totalMonthly * 12
                val confidence = if (state.isOfflineMode) 0.70f else 0.85f // Lower confidence for offline data

                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("💰 SAVINGS CALCULATION:")
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("  - Monthly Total: ₹$totalMonthly")
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("  - Yearly Total: ₹$yearlyImpact")
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("  - Confidence: $confidence")

                SavingsOpportunityUIData(
                    monthlyPotential = totalMonthly,
                    yearlyImpact = yearlyImpact,
                    recommendations = savingsInsights.map {
                        if (state.isOfflineMode) "${it.actionableAdvice} (Offline estimate)"
                        else it.actionableAdvice
                    },
                    confidence = confidence
                )
            } else {
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("⚠️ No savings insights available")
                SavingsOpportunityUIData()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SavingsOpportunityUIData()
        )
    
    /**
     * Get budget optimization recommendations for UI
     */
    val budgetRecommendations: StateFlow<List<String>> = _uiState
        .map { state ->
            state.getInsightsByType(InsightType.BUDGET_OPTIMIZATION)
                .map { it.description }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * Check network status and cache information
     */
    fun getCacheStatus() = viewModelScope.launch {
        try {
            // This would call repository to get cache status
            // For now, just log the current state
            val state = _uiState.value
            Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Current cache status - Offline: ${state.isOfflineMode}, Sample data: ${state.showingSampleData}")
        } catch (e: Exception) {
            Timber.tag(LogConfig.FeatureTags.INSIGHTS).e(e, "Error checking cache status")
        }
    }
    
    /**
     * Toggle offline mode (for testing)
     */
    fun toggleOfflineMode() {
        viewModelScope.launch {
            try {
                // This would call repository to toggle offline mode
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("Toggling offline mode")
                refreshInsights()
            } catch (e: Exception) {
                Timber.tag(LogConfig.FeatureTags.INSIGHTS).e(e, "Error toggling offline mode")
            }
        }
    }
}

/**
 * Factory for creating ViewModel instances
 * In production, this would be handled by dependency injection (Hilt)
 */
class InsightsViewModelFactory(
    private val getAIInsightsUseCase: GetAIInsightsUseCase
) {
    fun create(): InsightsViewModel {
        return InsightsViewModel(getAIInsightsUseCase)
    }
}