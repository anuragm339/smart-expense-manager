package com.expensemanager.app.ui.dashboard

import com.expensemanager.app.data.repository.DashboardData

/**
 * UI State for Dashboard screen
 */
data class DashboardUIState(
    // Loading states
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncingSMS: Boolean = false,
    
    // Data
    val dashboardData: DashboardData? = null,
    val monthlyComparison: MonthlyComparison? = null,
    val trendData: TrendData? = null,
    val customMonthComparison: CustomMonthComparison? = null,
    
    // Period selection
    val dashboardPeriod: String = "This Month",
    val timePeriod: String = "This Month",
    val customFirstMonth: Pair<Int, Int>? = null, // (month, year)
    val customSecondMonth: Pair<Int, Int>? = null, // (month, year)
    
    // State flags
    val isEmpty: Boolean = false,
    val hasError: Boolean = false,
    val error: String? = null,
    val lastRefreshTime: Long = 0L,
    val syncedTransactionsCount: Int = 0
) {
    /**
     * Computed properties for UI state management
     */
    val shouldShowContent: Boolean
        get() = !isInitialLoading && !hasError && dashboardData != null && !isEmpty
    
    val shouldShowEmptyState: Boolean
        get() = !isInitialLoading && !hasError && (dashboardData == null || isEmpty)
    
    val shouldShowError: Boolean
        get() = !isInitialLoading && hasError && error != null
    
    val isAnyLoading: Boolean
        get() = isInitialLoading || isLoading || isRefreshing || isSyncingSMS
    
    val totalSpent: Double
        get() = dashboardData?.totalSpent ?: 0.0
    
    val totalBalance: Double
        get() = 0.0 - totalSpent // FIXED: Shows actual balance (0 - expenses) instead of hardcoded ₹45280
        // This reflects the reality: without income tracking or initial balance, 
        // total balance = 0 (starting point) - all expenses = negative amount or zero
    
    val transactionCount: Int
        get() = dashboardData?.transactionCount ?: 0
    
    val isCustomPeriod: Boolean
        get() = dashboardPeriod == "Custom Months" && customFirstMonth != null && customSecondMonth != null
}

/**
 * Monthly comparison data
 */
data class MonthlyComparison(
    val currentLabel: String,
    val previousLabel: String,
    val currentAmount: Double,
    val previousAmount: Double,
    val percentageChange: Double
) {
    val hasIncrease: Boolean get() = percentageChange > 0
    val hasDecrease: Boolean get() = percentageChange < 0
    val isStable: Boolean get() = kotlin.math.abs(percentageChange) <= 5.0
    
    val changeText: String
        get() = when {
            previousAmount == 0.0 && currentAmount > 0 -> "New spending (no previous data)"
            previousAmount == 0.0 && currentAmount == 0.0 -> "No data available"
            hasIncrease -> "↑ ${String.format("%.1f", percentageChange)}% more spending"
            hasDecrease -> "↓ ${String.format("%.1f", kotlin.math.abs(percentageChange))}% less spending"
            else -> "📊 Spending stable"
        }
}

/**
 * Trend data for weekly/period analysis
 */
data class TrendData(
    val currentPeriodAmount: Double,
    val previousPeriodAmount: Double,
    val percentageChange: Double,
    val trendText: String,
    val currentPeriodLabel: String
)

/**
 * Custom months comparison data
 */
data class CustomMonthComparison(
    val firstMonthData: DashboardData,
    val secondMonthData: DashboardData,
    val firstMonthLabel: String,
    val secondMonthLabel: String
) {
    val spendingDifference: Double
        get() = firstMonthData.totalSpent - secondMonthData.totalSpent
    
    val percentageChange: Double
        get() = if (secondMonthData.totalSpent > 0) {
            ((firstMonthData.totalSpent - secondMonthData.totalSpent) / secondMonthData.totalSpent) * 100
        } else {
            0.0
        }
}

/**
 * UI Events for Dashboard interactions
 */
sealed class DashboardUIEvent {
    object LoadData : DashboardUIEvent()
    object Refresh : DashboardUIEvent()
    object SyncSMS : DashboardUIEvent()
    object ClearError : DashboardUIEvent()
    
    data class ChangePeriod(val period: String) : DashboardUIEvent()
    data class ChangeTimePeriod(val period: String) : DashboardUIEvent()
    data class CustomMonthsSelected(
        val firstMonth: Pair<Int, Int>, 
        val secondMonth: Pair<Int, Int>
    ) : DashboardUIEvent()
}

// CategorySpending and MerchantSpending are defined in their respective adapter files