package com.expensemanager.app.ui.insights

import com.expensemanager.app.utils.logging.LogConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.models.AIInsight
import com.expensemanager.app.data.models.InsightType
import com.expensemanager.app.data.repository.OfflineException
import com.expensemanager.app.domain.insights.GetAIInsightsUseCase
import com.expensemanager.app.utils.logging.StructuredLogger
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

    private val logger = StructuredLogger("InsightsViewModel", "InsightsViewModel")
    
    init {
        logger.debug("init","ViewModel initialized, loading insights smartly (cache-first)...")
        loadInsightsSmartly() // Use cache-first loading to avoid unnecessary API calls
    }

    /**
     * Smart initial loading of insights (cache-first approach)
     * Prevents unnecessary API calls on every app reopen
     */
    private fun loadInsightsSmartly() {
        // Check if API call already in progress
        if (!isApiCallInProgress.compareAndSet(false, true)) {
            logger.debug("loadInsightsSmartly","API call already in progress, skipping duplicate loadInsightsSmartly()")
            return
        }

        logger.debug("loadInsightsSmartly","Starting smart load (cache-first)...")

        _uiState.value = _uiState.value.copy(
            isInitialLoading = true,
            hasError = false,
            error = null
        )

        viewModelScope.launch {
            try {
                val result = getAIInsightsUseCase.loadInsightsSmartly()
                handleInsightsResult(result, isInitialLoad = true)
            } finally {
                // Reset flag when done (success or failure)
                isApiCallInProgress.set(false)
                logger.debug("loadInsightsSmartly","Smart load completed, flag reset")
            }
        }
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: InsightsUIEvent) {
        logger.debug("handleEvent","Handling event: $event")
        
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
     * Refresh insights (pull-to-refresh)
     * Prevents duplicate API calls using AtomicBoolean flag
     */
    private fun refreshInsights() {
        // Check if API call already in progress
        if (!isApiCallInProgress.compareAndSet(false, true)) {
            logger.debug("refreshInsights", "Refresh already in progress, skipping duplicate refreshInsights()")
            return
        }

        logger.debug("refreshInsights", "Refreshing insights...")

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
                logger.debug("refreshInsights", "Refresh completed, flag reset")
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
            logger.debug("retryLoadingInsights", "API call already in progress, skipping duplicate retry")
            return
        }

        logger.debug("retryLoadingInsights", "Retrying to load insights...")

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
                logger.debug("retryLoadingInsights", "Retry completed, flag reset")
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
                logger.debug("handleInsightsResult", "Insights loaded successfully: ${insights.size} insights")

                // Log all insights with their details
                insights.forEachIndexed { index, insight ->
//                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("  Insight #${index + 1}:")
//                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("    - ID: ${insight.id}")
//                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("    - Type: ${insight.type}")
//                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("    - Title: ${insight.title}")
//                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("    - Impact Amount: ₹${insight.impactAmount}")
//                    Timber.tag(LogConfig.FeatureTags.INSIGHTS).d("    - Priority: ${insight.priority}")
                }

                val groupedInsights = insights.groupBy { it.type }
                val isSampleData = insights.any { it.id.startsWith("forecast_") || it.id.startsWith("pattern_") || it.id.startsWith("offline_") }

                logger.debug("handleInsightsResult", "Is sample/offline data: $isSampleData")

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
                logger.debug("handleInsightsResult", "Loaded insights by type: $summary")
                
                // If this was sample data due to offline mode, show appropriate message
                if (isSampleData && (isRefresh || isRetry)) {
                    logger.debug("handleInsightsResult", "Loaded sample data due to connectivity issues")
                }
            },
            onFailure = { throwable ->
                logger.error("handleInsightsResult","Failed to load insights",throwable)
                
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
                    logger.debug("handleInsightsResult", "Keeping existing insights, error: $errorMessage")
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
        logger.debug("expandCard", "Expanded card: $insightId")
    }
    
    /**
     * Collapse insight card
     */
    private fun collapseCard(insightId: String) {
        val currentExpanded = _uiState.value.expandedCards
        _uiState.value = _uiState.value.copy(
            expandedCards = currentExpanded - insightId
        )
        logger.debug("collapseCard", "Collapsed card: $insightId")
    }
    
    /**
     * Handle insight card click
     */
    private fun handleInsightClick(insight: AIInsight) {
        logger.debug("handleInsightClick","Insight clicked: ${insight.title}")
        
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
        logger.debug("handleActionClick","Action clicked: $action for insight: ${insight.title}")
        
        // Handle different action types
        when (action.lowercase()) {
            "create savings plan" -> {
                // Navigate to budget goals or create a savings plan
                // This would typically trigger a navigation event
                logger.debug("handleActionClick","Creating savings plan for ${insight.impactAmount}")
            }
            "view breakdown" -> {
                // Navigate to categories screen
                logger.debug("handleActionClick","Viewing breakdown for ${insight.type}")
            }
            "set reminder" -> {
                // Create a spending reminder
                logger.debug("handleActionClick","Setting reminder for ${insight.title}")
            }
            else -> {
                logger.debug("handleActionClick","Unhandled action: $action")
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
                logger.debug("clearCache","Cache cleared")
                // Reload insights after clearing cache
                refreshInsights()
            } catch (e: Exception) {
                logger.error("clearCache", "Error clearing cache",e)
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

            logger.debug("savingsOpportunities","Calculating savings opportunities:")
            logger.debug("savingsOpportunities","  - Total insights: ${state.insights.size}")
            logger.debug("savingsOpportunities","  - Savings insights: ${savingsInsights.size}")
            logger.debug("savingsOpportunities","  - Is offline mode: ${state.isOfflineMode}")

            if (savingsInsights.isNotEmpty()) {
                // Log each savings insight
                savingsInsights.forEachIndexed { index, insight ->
                    logger.debug("savingsOpportunities","  - Savings #${index + 1}: id=${insight.id}, title=${insight.title}, impactAmount=₹${insight.impactAmount}")
                }

                val totalMonthly = savingsInsights.sumOf { it.impactAmount }
                val yearlyImpact = totalMonthly * 12
                val confidence = if (state.isOfflineMode) 0.70f else 0.85f // Lower confidence for offline data

                logger.debug("savingsOpportunities","SAVINGS CALCULATION:")
                logger.debug("savingsOpportunities","  - Monthly Total: ₹$totalMonthly")
                logger.debug("savingsOpportunities","  - Yearly Total: ₹$yearlyImpact")
                logger.debug("savingsOpportunities","  - Confidence: $confidence")

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
                logger.debug("savingsOpportunities","No savings insights available")
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
            logger.debug("getCacheStatus","Current cache status - Offline: ${state.isOfflineMode}, Sample data: ${state.showingSampleData}")
        } catch (e: Exception) {
            logger.error("getCacheStatus","Error checking cache status",e)
        }
    }
    
    /**
     * Toggle offline mode (for testing)
     */
    fun toggleOfflineMode() {
        viewModelScope.launch {
            try {
                // This would call repository to toggle offline mode
                logger.debug("getCacheStatus","Toggling offline mode")
                refreshInsights()
            } catch (e: Exception) {
                logger.error("getCacheStatus","Error toggling offline mode",e)
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