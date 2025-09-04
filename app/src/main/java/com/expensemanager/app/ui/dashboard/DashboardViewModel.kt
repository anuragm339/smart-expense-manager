package com.expensemanager.app.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.repository.DashboardData
import com.expensemanager.app.domain.usecase.dashboard.GetDashboardDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for Dashboard screen
 * Manages dashboard UI state and handles user interactions
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardDataUseCase: GetDashboardDataUseCase
) : ViewModel() {
    
    companion object {
        private const val TAG = "DashboardViewModel"
    }
    
    // Private mutable state
    private val _uiState = MutableStateFlow(DashboardUIState())
    
    // Public immutable state
    val uiState: StateFlow<DashboardUIState> = _uiState.asStateFlow()
    
    init {
        loadDashboardData()
    }

    /**
     * Handle UI events from the Fragment
     */
    fun handleEvent(event: DashboardUIEvent) {
        when (event) {
            is DashboardUIEvent.LoadData -> loadDashboardData()
            is DashboardUIEvent.Refresh -> refreshDashboard()
            is DashboardUIEvent.SyncSMS -> syncSMSMessages()
            is DashboardUIEvent.ChangePeriod -> changePeriod(event.period)
            is DashboardUIEvent.ChangeTimePeriod -> changeTimePeriod(event.period)
            is DashboardUIEvent.CustomMonthsSelected -> selectCustomMonths(event.firstMonth, event.secondMonth)
            is DashboardUIEvent.ClearError -> clearError()
        }
    }
    
    /**
     * Initial loading of dashboard data
     */
    private fun loadDashboardData() {
        _uiState.value = _uiState.value.copy(
            isInitialLoading = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            // CRITICAL FIX: Add retry mechanism to handle migration timing
            // Dashboard loads faster than migration completes, so retry if empty
            // ENHANCED: More retries and longer delays for fresh installs with SMS import
            loadDashboardDataWithRetry(retryCount = 0, maxRetries = 5)
        }
    }
    
    /**
     * Load dashboard data with retry mechanism to handle migration timing issues
     */
    private suspend fun loadDashboardDataWithRetry(retryCount: Int, maxRetries: Int) {
        val currentState = _uiState.value
        val (startDate, endDate) = getDateRangeForPeriod(currentState.dashboardPeriod)
        
        val result = getDashboardDataUseCase.execute(startDate, endDate)
        
        result.fold(
            onSuccess = { dashboardData ->
                Log.d(TAG, "📊 [DASHBOARD] ${dashboardData.transactionCount} transactions loaded, ₹${dashboardData.totalSpent} total")
                
                if (dashboardData.transactionCount == 0 && retryCount < maxRetries) {
                    // Database might be empty due to ongoing migration/SMS import, retry after delay
                    val delayMillis = when (retryCount) {
                        0 -> 2000L  // 2 seconds - quick retry for UI responsiveness
                        1 -> 4000L  // 4 seconds - migration might be running
                        2 -> 6000L  // 6 seconds - SMS import might be running
                        3 -> 8000L  // 8 seconds - heavy SMS processing
                        else -> 10000L // 10 seconds - final attempt
                    }
                    
                    Log.w(TAG, "📊 [RETRY ${retryCount + 1}/${maxRetries + 1}] Dashboard empty, retrying in ${delayMillis/1000}s...")
                    
                    delay(delayMillis)
                    loadDashboardDataWithRetry(retryCount + 1, maxRetries)
                } else {
                    // Either we have data or we've exhausted retries
                    if (dashboardData.transactionCount > 0) {
                        Log.d(TAG, "✅ Dashboard loaded: ${dashboardData.transactionCount} transactions")
                    } else {
                        Log.w(TAG, "📊 Dashboard empty after ${maxRetries + 1} attempts - check SMS permissions & transaction history")
                    }
                    handleDashboardResult(result, isInitialLoad = true)
                }
            },
            onFailure = { 
                handleDashboardResult(result, isInitialLoad = true)
            }
        )
    }
    
    /**
     * Refresh dashboard data (pull-to-refresh)
     */
    private fun refreshDashboard() {
        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            hasError = false,
            error = null
        )
        
        viewModelScope.launch {
            val currentState = _uiState.value
            val (startDate, endDate) = getDateRangeForPeriod(currentState.dashboardPeriod)
            
            val result = getDashboardDataUseCase.execute(startDate, endDate)
            handleDashboardResult(result, isRefresh = true)
        }
    }
    
    /**
     * Sync SMS messages for new transactions (temporarily disabled)
     */
    private fun syncSMSMessages() {
        _uiState.value = _uiState.value.copy(
            isSyncingSMS = false,
            hasError = true,
            error = "SMS sync not yet implemented in ViewModel"
        )
    }
    
    /**
     * Change dashboard period
     */
    private fun changePeriod(period: String) {
        _uiState.value = _uiState.value.copy(
            dashboardPeriod = period,
            timePeriod = period // Also update time period to match
        )
        
        // Reload data for new period
        loadDashboardDataForPeriod(period)
    }
    
    /**
     * Change time period for trends
     */
    private fun changeTimePeriod(period: String) {
        _uiState.value = _uiState.value.copy(
            timePeriod = period
        )
        
        // Update trends for new time period
        updateTrendsForPeriod(period)
    }
    
    /**
     * Select custom months for comparison
     */
    private fun selectCustomMonths(firstMonth: Pair<Int, Int>, secondMonth: Pair<Int, Int>) {
        if (firstMonth == secondMonth) {
            _uiState.value = _uiState.value.copy(
                hasError = true,
                error = "Please select two different months"
            )
            return
        }
        
        _uiState.value = _uiState.value.copy(
            customFirstMonth = firstMonth,
            customSecondMonth = secondMonth,
            dashboardPeriod = "Custom Months"
        )
        
        // Load data for custom months
        loadCustomMonthsData(firstMonth, secondMonth)
    }
    
    /**
     * Add quick expense
     */
    private fun addQuickExpense(amount: Double, merchant: String, category: String) {
        viewModelScope.launch {
            // TODO: Implement with AddTransactionUseCase when available
            // For now, just refresh dashboard
            refreshDashboard()
        }
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
     * Load dashboard data for specific period
     */
    private fun loadDashboardDataForPeriod(period: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val (startDate, endDate) = getDateRangeForPeriod(period)
            val result = getDashboardDataUseCase.execute(startDate, endDate)
            
            handleDashboardResult(result)
        }
    }
    
    /**
     * Load dashboard data for custom months
     */
    private fun loadCustomMonthsData(firstMonth: Pair<Int, Int>, secondMonth: Pair<Int, Int>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val (firstStart, firstEnd) = getDateRangeForCustomMonth(firstMonth)
                val (secondStart, secondEnd) = getDateRangeForCustomMonth(secondMonth)
                
                // Load data for first month (main display)
                val firstMonthResult = getDashboardDataUseCase.execute(firstStart, firstEnd)
                
                firstMonthResult.fold(
                    onSuccess = { firstMonthData ->
                        // Get second month data for comparison
                        val secondMonthResult = getDashboardDataUseCase.execute(secondStart, secondEnd)
                        
                        secondMonthResult.fold(
                            onSuccess = { secondMonthData ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    dashboardData = firstMonthData,
                                    customMonthComparison = CustomMonthComparison(
                                        firstMonthData = firstMonthData,
                                        secondMonthData = secondMonthData,
                                        firstMonthLabel = formatMonthLabel(firstMonth),
                                        secondMonthLabel = formatMonthLabel(secondMonth)
                                    )
                                )
                            },
                            onFailure = { error ->
                                handleDashboardError(error)
                            }
                        )
                    },
                    onFailure = { error ->
                        handleDashboardError(error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading custom months data", e)
                handleDashboardError(e)
            }
        }
    }
    
    /**
     * Update trends for specific period
     */
    private fun updateTrendsForPeriod(period: String) {
        viewModelScope.launch {
            try {
                val (startDate, endDate) = getDateRangeForPeriod(period)
                
                // Calculate trend data (simplified for now)
                val currentPeriodTotal = _uiState.value.dashboardData?.totalSpent ?: 0.0
                
                // Get previous period for comparison
                val (prevStart, prevEnd) = getPreviousPeriodRange(period)
                val prevPeriodResult = getDashboardDataUseCase.execute(prevStart, prevEnd)
                
                prevPeriodResult.fold(
                    onSuccess = { prevData ->
                        val trendData = calculateTrendData(currentPeriodTotal, prevData.totalSpent, period)
                        
                        _uiState.value = _uiState.value.copy(
                            trendData = trendData
                        )
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error loading trend data", error)
                        // Keep existing trend data if available
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error updating trends", e)
            }
        }
    }
    
    /**
     * Handle dashboard data result
     */
    private fun handleDashboardResult(
        result: Result<DashboardData>, 
        isInitialLoad: Boolean = false,
        isRefresh: Boolean = false
    ) {
        result.fold(
            onSuccess = { dashboardData ->
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    isLoading = false,
                    dashboardData = dashboardData,
                    hasError = false,
                    error = null,
                    isEmpty = dashboardData.transactionCount == 0,
                    lastRefreshTime = System.currentTimeMillis()
                )
                
                // Update monthly comparison
                updateMonthlyComparison()
            },
            onFailure = { throwable ->
                Log.e(TAG, "Failed to load dashboard data", throwable)
                handleDashboardError(throwable)
            }
        )
    }
    
    /**
     * Handle SMS sync errors
     */
    private fun handleSyncError(throwable: Throwable) {
        val errorMessage = when {
            throwable.message?.contains("permission", ignoreCase = true) == true -> "SMS permission required for sync"
            throwable.message?.contains("network", ignoreCase = true) == true -> "Network error during sync"
            else -> "Error syncing SMS: ${throwable.message ?: "Unknown error"}"
        }
        
        _uiState.value = _uiState.value.copy(
            isSyncingSMS = false,
            hasError = true,
            error = errorMessage
        )
    }
    
    /**
     * Handle dashboard loading errors
     */
    private fun handleDashboardError(throwable: Throwable) {
        val errorMessage = when {
            throwable.message?.contains("network", ignoreCase = true) == true -> "Check your internet connection and try again"
            throwable.message?.contains("permission", ignoreCase = true) == true -> "Permission required to access data"
            else -> "Something went wrong. Please try again"
        }
        
        // Don't show error state if we have existing data
        val hasExistingData = _uiState.value.dashboardData != null
        
        _uiState.value = _uiState.value.copy(
            isInitialLoading = false,
            isRefreshing = false,
            isLoading = false,
            hasError = !hasExistingData,
            error = if (hasExistingData) null else errorMessage
        )
    }
    
    /**
     * Update monthly comparison data
     */
    private fun updateMonthlyComparison() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val currentPeriod = currentState.dashboardPeriod
                
                val (currentStart, currentEnd) = getDateRangeForPeriod(currentPeriod)
                val (previousStart, previousEnd) = getPreviousPeriodRange(currentPeriod)
                
                // Get previous period data
                val previousResult = getDashboardDataUseCase.execute(previousStart, previousEnd)
                
                previousResult.fold(
                    onSuccess = { previousData ->
                        val currentAmount = currentState.dashboardData?.totalSpent ?: 0.0
                        val previousAmount = previousData.totalSpent
                        
                        val comparison = MonthlyComparison(
                            currentLabel = getCurrentPeriodLabel(currentPeriod),
                            previousLabel = getPreviousPeriodLabel(currentPeriod),
                            currentAmount = currentAmount,
                            previousAmount = previousAmount,
                            percentageChange = calculatePercentageChange(currentAmount, previousAmount)
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            monthlyComparison = comparison
                        )
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error updating monthly comparison", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in monthly comparison update", e)
            }
        }
    }
    
    /**
     * Calculate percentage change between two amounts
     */
    private fun calculatePercentageChange(current: Double, previous: Double): Double {
        return if (previous > 0) {
            ((current - previous) / previous) * 100
        } else {
            0.0
        }
    }
    
    /**
     * Calculate trend data for UI display
     */
    private fun calculateTrendData(currentAmount: Double, previousAmount: Double, period: String): TrendData {
        val change = calculatePercentageChange(currentAmount, previousAmount)
        
        val trendText = when {
            change > 10 -> "📈 Spending increased"
            change < -10 -> "📉 Spending decreased"  
            else -> "📊 Spending stable"
        }
        
        return TrendData(
            currentPeriodAmount = currentAmount,
            previousPeriodAmount = previousAmount,
            percentageChange = change,
            trendText = trendText,
            currentPeriodLabel = getCurrentPeriodLabel(period)
        )
    }
    
    /**
     * Get date range for a specific period
     */
    private fun getDateRangeForPeriod(period: String): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        
        when (period) {
            "This Month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return Pair(calendar.time, endDate)
            }
            "Last Month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                val lastMonthEnd = calendar.time
                
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return Pair(calendar.time, lastMonthEnd)
            }
            "Last 3 Months" -> {
                calendar.add(Calendar.MONTH, -3)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return Pair(calendar.time, endDate)
            }
            "Last 6 Months" -> {
                calendar.add(Calendar.MONTH, -6)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return Pair(calendar.time, endDate)
            }
            "This Year" -> {
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return Pair(calendar.time, endDate)
            }
            else -> {
                // Default to current month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return Pair(calendar.time, endDate)
            }
        }
    }
    
    /**
     * Get date range for custom month
     */
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
        
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time
        
        return Pair(startDate, endDate)
    }
    
    /**
     * Get previous period date range for comparison
     */
    private fun getPreviousPeriodRange(period: String): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        
        when (period) {
            "This Month" -> {
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
                
                return Pair(prevStart, prevEnd)
            }
            "Last Month" -> {
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
                
                return Pair(prevStart, prevEnd)
            }
            else -> {
                // Default to last month
                return getPreviousPeriodRange("This Month")
            }
        }
    }
    
    /**
     * Format month label for display
     */
    private fun formatMonthLabel(monthYear: Pair<Int, Int>): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, monthYear.second)
        calendar.set(Calendar.MONTH, monthYear.first)
        return java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(calendar.time)
    }
    
    /**
     * Get current period label
     */
    private fun getCurrentPeriodLabel(period: String): String {
        return when (period) {
            "This Month" -> "This Month"
            "Last Month" -> "Last Month"
            else -> period
        }
    }
    
    /**
     * Get previous period label
     */
    private fun getPreviousPeriodLabel(period: String): String {
        return when (period) {
            "This Month" -> "Last Month"
            "Last Month" -> "Two Months Ago"
            else -> "Previous Period"
        }
    }
}