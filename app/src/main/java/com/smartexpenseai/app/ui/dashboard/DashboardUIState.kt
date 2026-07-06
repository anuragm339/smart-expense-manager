package com.smartexpenseai.app.ui.dashboard

import com.smartexpenseai.app.data.repository.DashboardData

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
    val categoryMovers: List<CategoryMover> = emptyList(),
    val tagSpending: List<com.smartexpenseai.app.data.dao.TagSpending> = emptyList(),
    val trackedTagMovers: List<CategoryMover> = emptyList(),
    val comparisonMode: ComparisonMode = ComparisonMode.THIS_MONTH,
    val customRangeA: Pair<java.util.Date, java.util.Date>? = null,
    val customRangeB: Pair<java.util.Date, java.util.Date>? = null,
    val trendData: TrendData? = null,
    val customMonthComparison: CustomMonthComparison? = null,
    val monthlyBudget: Double = 0.0,
    
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
        get() = dashboardData?.actualBalance ?: 0.0 // BALANCE FIX: Use actual balance from Credits - Debits calculation
        // This properly shows: Total Credits (income) - Total Debits (expenses) = Actual Balance
    
    val transactionCount: Int
        get() = dashboardData?.transactionCount ?: 0
    
    // Monthly Balance: Last Salary - Current Period Expenses
    val monthlyBalance: Double
        get() = dashboardData?.monthlyBalance?.remainingBalance ?: 0.0
    
    val lastSalaryAmount: Double
        get() = dashboardData?.monthlyBalance?.lastSalaryAmount ?: 0.0
    
    val hasSalaryData: Boolean
        get() = dashboardData?.monthlyBalance?.hasSalaryData ?: false
    
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
            // Guard against absurd percentages when the previous period is tiny:
            // a % off a near-zero base isn't meaningful, so show direction instead.
            hasIncrease && percentageChange >= HUGE_CHANGE_PCT -> "↑ Much more than last period"
            hasIncrease -> "↑ ${String.format("%.1f", percentageChange)}% more spending"
            hasDecrease -> "↓ ${String.format("%.1f", kotlin.math.abs(percentageChange))}% less spending"
            else -> "📊 Spending stable"
        }

    companion object {
        // Above this, the percentage is dominated by a near-zero baseline and unhelpful.
        private const val HUGE_CHANGE_PCT = 1000.0
    }
}

/**
 * How the comparison card pairs its two periods.
 * Presets compare full period vs full previous period; CUSTOM compares two
 * independent user-chosen ranges (A vs B).
 */
enum class ComparisonMode(val menuLabel: String, val selectorLabel: String) {
    THIS_MONTH("This Month vs Last Month", "This Month"),
    THIS_WEEK("This Week vs Last Week", "This Week"),
    CUSTOM("Custom range…", "Custom")
}

/**
 * A labelled thing's spend change between the current period and the previous one.
 * Drives the "top movers by category" list and the tracked-tag comparison.
 */
data class CategoryMover(
    val label: String,
    val color: String,
    val currentAmount: Double,
    val previousAmount: Double
) {
    val delta: Double get() = currentAmount - previousAmount
    val isIncrease: Boolean get() = delta > 0

    val percentageChange: Double
        get() = when {
            previousAmount > 0 -> (delta / previousAmount) * 100
            currentAmount > 0 -> 100.0
            else -> 0.0
        }

    /** e.g. "+₹4,200" / "−₹1,100". */
    val deltaText: String
        get() {
            val sign = if (delta >= 0) "+" else "−"
            return "$sign₹${kotlin.math.abs(delta).let { java.text.DecimalFormat("#,##0").format(it) }}"
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
    object IncrementalRescan : DashboardUIEvent()  // Only scan SMS without transactions
    object CleanFullRescan : DashboardUIEvent()    // Delete all and rescan everything
    object ClearError : DashboardUIEvent()

    data class ChangePeriod(val period: String) : DashboardUIEvent()
    data class ChangeTimePeriod(val period: String) : DashboardUIEvent()
    data class CustomMonthsSelected(
        val firstMonth: Pair<Int, Int>,
        val secondMonth: Pair<Int, Int>
    ) : DashboardUIEvent()
}

// CategorySpending and MerchantSpending are defined in their respective adapter files