package com.expensemanager.app.ui.insights

import com.expensemanager.app.data.models.AIInsight
import com.expensemanager.app.data.models.InsightType

/**
 * UI State for AI Insights screen
 */
data class InsightsUIState(
    // Loading states
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isRetrying: Boolean = false,
    
    // Data
    val insights: List<AIInsight> = emptyList(),
    val groupedInsights: Map<InsightType, List<AIInsight>> = emptyMap(),
    
    // Error handling
    val error: String? = null,
    val hasError: Boolean = false,
    
    // Metadata
    val lastRefreshTime: Long = 0L,
    val isEmpty: Boolean = true,
    val isFromCache: Boolean = false,
    
    // UI specific states
    val expandedCards: Set<String> = emptySet(), // Track which insight cards are expanded
    val showingSampleData: Boolean = false,
    val isOfflineMode: Boolean = false
) {
    
    /**
     * Check if we are in any loading state
     */
    val isLoading: Boolean
        get() = isInitialLoading || isRefreshing || isRetrying
    
    /**
     * Check if we should show content
     */
    val shouldShowContent: Boolean
        get() = !isInitialLoading && insights.isNotEmpty()
    
    /**
     * Check if we should show error
     */
    val shouldShowError: Boolean
        get() = !isInitialLoading && hasError && insights.isEmpty()
    
    /**
     * Check if we should show empty state
     */
    val shouldShowEmptyState: Boolean
        get() = !isInitialLoading && !hasError && insights.isEmpty()
    
    /**
     * Get insights by type
     */
    fun getInsightsByType(type: InsightType): List<AIInsight> {
        return groupedInsights[type] ?: emptyList()
    }
    
    /**
     * Check if a card is expanded
     */
    fun isCardExpanded(insightId: String): Boolean {
        return expandedCards.contains(insightId)
    }
    
    /**
     * Get high priority insights for quick access
     */
    val highPriorityInsights: List<AIInsight>
        get() = insights.filter { it.priority.name == "HIGH" || it.priority.name == "URGENT" }
    
    /**
     * Get total potential savings from all insights
     */
    val totalPotentialSavings: Double
        get() = insights.sumOf { it.impactAmount }
}

/**
 * UI Events that can be triggered from the Insights screen
 */
sealed class InsightsUIEvent {
    object Refresh : InsightsUIEvent()
    object Retry : InsightsUIEvent()
    object ClearError : InsightsUIEvent()
    data class ExpandCard(val insightId: String) : InsightsUIEvent()
    data class CollapseCard(val insightId: String) : InsightsUIEvent()
    data class InsightClicked(val insight: AIInsight) : InsightsUIEvent()
    data class ActionClicked(val insight: AIInsight, val action: String) : InsightsUIEvent()
}

/**
 * Specific UI states for different insight types
 */
data class SpendingForecastUIData(
    val projectedAmount: Double = 0.0,
    val comparisonToLastMonth: Double = 0.0,
    val progressPercentage: Int = 0,
    val daysRemaining: Int = 0,
    val advice: String = ""
)

data class PatternAlertUIData(
    val category: String = "",
    val changePercentage: Double = 0.0,
    val isIncrease: Boolean = true,
    val period: String = "this week",
    val severity: AlertSeverity = AlertSeverity.LOW
)

enum class AlertSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class SavingsOpportunityUIData(
    val monthlyPotential: Double = 0.0,
    val yearlyImpact: Double = 0.0,
    val recommendations: List<String> = emptyList(),
    val confidence: Float = 0.8f
)

/**
 * Extension functions to convert AIInsight to specific UI data
 */
fun AIInsight.toSpendingForecastUIData(): SpendingForecastUIData {
    return SpendingForecastUIData(
        projectedAmount = this.impactAmount,
        comparisonToLastMonth = 12.0, // Would come from API
        progressPercentage = 68, // Would come from API
        daysRemaining = 15, // Would come from API
        advice = this.actionableAdvice
    )
}

fun AIInsight.toPatternAlertUIData(): PatternAlertUIData {
    // Parse actual data from API description
    val description = this.description
    val title = this.title
    
    // Extract category from description or title
    val category = when {
        description.contains("Food", ignoreCase = true) || description.contains("Dining", ignoreCase = true) -> "Food & Dining"
        description.contains("Transport", ignoreCase = true) || description.contains("Uber", ignoreCase = true) || description.contains("Ola", ignoreCase = true) -> "Transportation"
        description.contains("Grocery", ignoreCase = true) || description.contains("Groceries", ignoreCase = true) -> "Groceries"
        description.contains("Shopping", ignoreCase = true) || description.contains("Amazon", ignoreCase = true) -> "Shopping"
        description.contains("Healthcare", ignoreCase = true) || description.contains("Medical", ignoreCase = true) -> "Healthcare"
        else -> "General Spending"
    }
    
    // Extract percentage from description if available
    val percentageRegex = """(\d+(?:\.\d+)?)%""".toRegex()
    val changePercentage = percentageRegex.find(description)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    
    // Determine if it's an increase based on description text
    val isIncrease = description.contains("increase", ignoreCase = true) || 
                    description.contains("high", ignoreCase = true) ||
                    description.contains("more", ignoreCase = true) ||
                    description.contains("spike", ignoreCase = true)
    
    // Determine period from description
    val period = when {
        description.contains("week", ignoreCase = true) -> "this week"
        description.contains("month", ignoreCase = true) -> "this month"
        description.contains("day", ignoreCase = true) -> "recent days"
        else -> "recently"
    }
    
    return PatternAlertUIData(
        category = category,
        changePercentage = changePercentage,
        isIncrease = isIncrease,
        period = period,
        severity = when (this.priority.name) {
            "URGENT" -> AlertSeverity.CRITICAL
            "HIGH" -> AlertSeverity.HIGH
            "MEDIUM" -> AlertSeverity.MEDIUM
            else -> AlertSeverity.LOW
        }
    )
}

fun AIInsight.toSavingsOpportunityUIData(): SavingsOpportunityUIData {
    return SavingsOpportunityUIData(
        monthlyPotential = this.impactAmount,
        yearlyImpact = this.impactAmount * 12,
        recommendations = listOf(this.actionableAdvice),
        confidence = 0.8f
    )
}