package com.expensemanager.app.ui.insights

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.models.AIInsight
import com.expensemanager.app.data.models.InsightType
import com.expensemanager.app.data.repository.OfflineException
import com.expensemanager.app.domain.insights.GetAIInsightsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
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
    
    init {
        Log.d(TAG, "ViewModel initialized, loading insights...")
        loadInsights()
    }
    
    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: InsightsUIEvent) {
        Log.d(TAG, "Handling event: $event")
        
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
     */
    private fun loadInsights() {
        Log.d(TAG, "Starting initial load...")
        
        _uiState.value = _uiState.value.copy(
            isInitialLoading = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            getAIInsightsUseCase.execute().collect { result ->
                handleInsightsResult(result, isInitialLoad = true)
            }
        }
    }
    
    /**
     * Refresh insights (pull-to-refresh)
     */
    private fun refreshInsights() {
        Log.d(TAG, "Refreshing insights...")
        
        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            val result = getAIInsightsUseCase.refreshInsights()
            handleInsightsResult(result, isRefresh = true)
        }
    }
    
    /**
     * Retry loading after error
     */
    private fun retryLoadingInsights() {
        Log.d(TAG, "Retrying to load insights...")
        
        _uiState.value = _uiState.value.copy(
            isRetrying = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            val result = getAIInsightsUseCase.refreshInsights()
            handleInsightsResult(result, isRetry = true)
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
                Log.d(TAG, "Insights loaded successfully: ${insights.size} insights")
                
                val groupedInsights = insights.groupBy { it.type }
                val isSampleData = insights.any { it.id.startsWith("forecast_") || it.id.startsWith("pattern_") }
                
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
                Log.d(TAG, "Loaded insights by type: $summary")
                
                // If this was sample data due to offline mode, show appropriate message
                if (isSampleData && (isRefresh || isRetry)) {
                    Log.d(TAG, "Loaded sample data due to connectivity issues")
                }
            },
            onFailure = { throwable ->
                Log.e(TAG, "Failed to load insights", throwable)
                
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
                    Log.d(TAG, "Keeping existing insights, error: $errorMessage")
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
        Log.d(TAG, "Expanded card: $insightId")
    }
    
    /**
     * Collapse insight card
     */
    private fun collapseCard(insightId: String) {
        val currentExpanded = _uiState.value.expandedCards
        _uiState.value = _uiState.value.copy(
            expandedCards = currentExpanded - insightId
        )
        Log.d(TAG, "Collapsed card: $insightId")
    }
    
    /**
     * Handle insight card click
     */
    private fun handleInsightClick(insight: AIInsight) {
        Log.d(TAG, "Insight clicked: ${insight.title}")
        
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
        Log.d(TAG, "Action clicked: $action for insight: ${insight.title}")
        
        // Handle different action types
        when (action.lowercase()) {
            "create savings plan" -> {
                // Navigate to budget goals or create a savings plan
                // This would typically trigger a navigation event
                Log.d(TAG, "Creating savings plan for ${insight.impactAmount}")
            }
            "view breakdown" -> {
                // Navigate to categories screen
                Log.d(TAG, "Viewing breakdown for ${insight.type}")
            }
            "set reminder" -> {
                // Create a spending reminder
                Log.d(TAG, "Setting reminder for ${insight.title}")
            }
            else -> {
                Log.d(TAG, "Unhandled action: $action")
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
                Log.d(TAG, "Cache cleared")
                // Reload insights after clearing cache
                refreshInsights()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache", e)
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
            if (savingsInsights.isNotEmpty()) {
                val totalMonthly = savingsInsights.sumOf { it.impactAmount }
                val confidence = if (state.isOfflineMode) 0.70f else 0.85f // Lower confidence for offline data
                
                SavingsOpportunityUIData(
                    monthlyPotential = totalMonthly,
                    yearlyImpact = totalMonthly * 12,
                    recommendations = savingsInsights.map { 
                        if (state.isOfflineMode) "${it.actionableAdvice} (Offline estimate)"
                        else it.actionableAdvice
                    },
                    confidence = confidence
                )
            } else {
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
            Log.d(TAG, "Current cache status - Offline: ${state.isOfflineMode}, Sample data: ${state.showingSampleData}")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cache status", e)
        }
    }
    
    /**
     * Toggle offline mode (for testing)
     */
    fun toggleOfflineMode() {
        viewModelScope.launch {
            try {
                // This would call repository to toggle offline mode
                Log.d(TAG, "Toggling offline mode")
                refreshInsights()
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling offline mode", e)
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