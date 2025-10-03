package com.expensemanager.app.services

import android.content.Context
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.data.api.insights.AnonymizedFinancialData
import com.expensemanager.app.data.models.InsightType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Service to build AI prompts tailored for different types of financial insights
 * Creates context-aware prompts that guide Claude to provide specific, actionable advice
 */
@Singleton
class InsightsPromptBuilder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "InsightsPromptBuilder"
        private const val MAX_PROMPT_LENGTH = 2000 // Keep prompts concise for API efficiency
    }

    /**
     * Build appropriate prompt based on insight type and financial data
     */
    fun buildPrompt(
        data: AnonymizedFinancialData,
        insightType: InsightType = InsightType.CATEGORY_ANALYSIS
    ): String {
        return when (insightType) {
            InsightType.SPENDING_FORECAST -> buildForecastPrompt(data)
            InsightType.BUDGET_OPTIMIZATION -> buildBudgetOptimizationPrompt(data)
            InsightType.PATTERN_ALERT -> buildPatternAlertPrompt(data)
            InsightType.SAVINGS_OPPORTUNITY -> buildSavingsPrompt(data)
            InsightType.ANOMALY_DETECTION -> buildAnomalyPrompt(data)
            InsightType.MERCHANT_RECOMMENDATION -> buildMerchantPrompt(data)
            InsightType.CATEGORY_ANALYSIS -> buildGeneralAnalysisPrompt(data)
            InsightType.OPTIMIZATION_TIP -> buildOptimizationTipPrompt(data)
        }
    }

    /**
     * Build spending forecast prompt
     */
    private fun buildForecastPrompt(data: AnonymizedFinancialData): String {
        val monthlyTrend = if (data.monthlyTrends.size >= 2) {
            val recent = data.monthlyTrends.takeLast(2)
            val change = ((recent[1].totalAmount - recent[0].totalAmount) / recent[0].totalAmount * 100).roundToInt()
            "Monthly spending has ${if (change > 0) "increased" else "decreased"} by ${kotlin.math.abs(change)}%."
        } else {
            "Limited historical data available."
        }

        return """
        Analyze my spending data and provide a spending forecast for next month.

        Current spending: ₹${data.totalSpent.roundToInt()} across ${data.transactionCount} transactions
        ${monthlyTrend}
        Days remaining in month: ${data.contextData.daysRemainingInMonth}

        Top categories: ${data.categoryBreakdown.take(3).joinToString(", ") { "${it.categoryName} (₹${it.totalAmount.roundToInt()})" }}

        **Required JSON Response:**
        {
          "insights": [
            {
              "type": "SPENDING_FORECAST",
              "title": "Next Month Spending Forecast",
              "description": "Based on current patterns, predict next month's spending",
              "actionableAdvice": "Specific steps to manage projected spending",
              "impactAmount": 0.0,
              "priority": "MEDIUM"
            }
          ]
        }

        Focus on:
        1. Realistic spending projection for next month
        2. Factors that might increase/decrease spending
        3. Specific actions to optimize budget
        4. Categories with highest forecast growth

        Keep amounts in INR (₹) and advice practical for Indian context.
        """.trimIndent()
    }

    /**
     * Build budget optimization prompt
     */
    private fun buildBudgetOptimizationPrompt(data: AnonymizedFinancialData): String {
        val topCategory = data.categoryBreakdown.firstOrNull()
        val potentialSavings = topCategory?.totalAmount?.times(0.1) ?: 0.0

        return """
        Analyze my spending patterns and suggest budget optimization strategies.

        Total spending: ₹${data.totalSpent.roundToInt()} (${data.transactionCount} transactions)

        Category breakdown:
        ${data.categoryBreakdown.take(5).joinToString("\n") { "- ${it.categoryName}: ₹${it.totalAmount.roundToInt()} (${it.percentage.roundToInt()}%)" }}

        Weekly patterns:
        ${data.weeklyPatterns.joinToString(", ") { "${it.dayOfWeek}: ₹${it.averageAmount.roundToInt()}" }}

        **Required JSON Response:**
        {
          "insights": [
            {
              "type": "BUDGET_OPTIMIZATION",
              "title": "Budget Optimization Opportunity",
              "description": "Specific areas where spending can be optimized",
              "actionableAdvice": "Concrete steps to reduce spending in key categories",
              "impactAmount": ${potentialSavings.roundToInt()}.0,
              "priority": "HIGH"
            }
          ]
        }

        Provide:
        1. Top 2-3 categories with optimization potential
        2. Specific percentage reduction targets (realistic)
        3. Alternative spending strategies
        4. Expected monthly savings amount

        Focus on practical, achievable optimizations for Indian lifestyle.
        """.trimIndent()
    }

    /**
     * Build pattern alert prompt
     */
    private fun buildPatternAlertPrompt(data: AnonymizedFinancialData): String {
        val avgPerTransaction = if (data.transactionCount > 0) data.totalSpent / data.transactionCount else 0.0
        val highValueTransactions = data.categoryBreakdown.filter { it.averagePerTransaction > avgPerTransaction * 1.5 }

        return """
        Analyze my spending patterns and identify unusual or concerning trends.

        Current period: ₹${data.totalSpent.roundToInt()} spent
        Previous period: ₹${data.contextData.previousMonthSpent.roundToInt()} spent
        Trend: ${data.contextData.spendingTrendDirection}

        Categories with high per-transaction amounts:
        ${highValueTransactions.joinToString("\n") { "- ${it.categoryName}: ₹${it.averagePerTransaction.roundToInt()} avg" }}

        Daily patterns:
        ${data.weeklyPatterns.sortedByDescending { it.averageAmount }.take(3).joinToString(", ") { "${it.dayOfWeek}: ₹${it.averageAmount.roundToInt()}" }}

        **Required JSON Response:**
        {
          "insights": [
            {
              "type": "PATTERN_ALERT",
              "title": "Spending Pattern Alert",
              "description": "Unusual spending pattern detected",
              "actionableAdvice": "Steps to address concerning spending trends",
              "impactAmount": 0.0,
              "priority": "HIGH"
            }
          ]
        }

        Identify:
        1. Unusual spending increases or decreases
        2. High-value transaction patterns
        3. Day-of-week spending anomalies
        4. Category concentration risks

        Only flag genuinely concerning patterns, not normal variations.
        """.trimIndent()
    }

    /**
     * Build savings opportunity prompt
     */
    private fun buildSavingsPrompt(data: AnonymizedFinancialData): String {
        val topMerchants = data.topMerchants.take(5)
        val totalMerchantSpending = topMerchants.sumOf { it.totalAmount }

        return """
        Identify specific savings opportunities in my spending patterns.

        Total spending: ₹${data.totalSpent.roundToInt()}

        Top merchants:
        ${topMerchants.joinToString("\n") { "- ${it.merchantName}: ₹${it.totalAmount.roundToInt()} (${it.transactionCount} visits)" }}

        Category spending:
        ${data.categoryBreakdown.take(3).joinToString("\n") { "- ${it.categoryName}: ₹${it.totalAmount.roundToInt()}" }}

        **Required JSON Response:**
        {
          "insights": [
            {
              "type": "SAVINGS_OPPORTUNITY",
              "title": "Savings Opportunity",
              "description": "Specific way to reduce spending and save money",
              "actionableAdvice": "Concrete steps to achieve savings",
              "impactAmount": 0.0,
              "priority": "MEDIUM"
            }
          ]
        }

        Suggest:
        1. Alternative merchants or services with lower costs
        2. Subscription or loyalty programs that could save money
        3. Bulk purchasing opportunities
        4. Category-specific saving strategies

        Provide realistic savings amounts achievable in Indian market context.
        """.trimIndent()
    }

    /**
     * Build anomaly detection prompt
     */
    private fun buildAnomalyPrompt(data: AnonymizedFinancialData): String {
        return """
        Detect any anomalies or unusual patterns in my spending data.

        Current spending: ₹${data.totalSpent.roundToInt()} (${data.transactionCount} transactions)
        Previous period: ₹${data.contextData.previousMonthSpent.roundToInt()}

        **Required JSON Response:**
        {
          "insights": [
            {
              "type": "ANOMALY_DETECTION",
              "title": "Spending Anomaly Detected",
              "description": "Unusual spending pattern that needs attention",
              "actionableAdvice": "Steps to investigate or address anomaly",
              "impactAmount": 0.0,
              "priority": "URGENT"
            }
          ]
        }

        Look for:
        1. Sudden spikes in specific categories
        2. Unusual merchant charges
        3. Duplicate or suspicious transactions
        4. Spending outside normal patterns

        Only report genuine anomalies that warrant user attention.
        """.trimIndent()
    }

    /**
     * Build merchant recommendation prompt
     */
    private fun buildMerchantPrompt(data: AnonymizedFinancialData): String {
        return """
        Analyze my merchant spending and provide recommendations for better financial choices.

        Top merchants by spending:
        ${data.topMerchants.joinToString("\n") { "- ${it.merchantName}: ₹${it.totalAmount.roundToInt()} (${it.categoryName})" }}

        **Required JSON Response:**
        {
          "insights": [
            {
              "type": "MERCHANT_RECOMMENDATION",
              "title": "Merchant Optimization",
              "description": "Better merchant choices for your spending categories",
              "actionableAdvice": "Specific merchant or platform suggestions",
              "impactAmount": 0.0,
              "priority": "MEDIUM"
            }
          ]
        }

        Suggest alternatives that offer:
        1. Better value for money
        2. Rewards or cashback programs
        3. Lower fees or charges
        4. Better financial benefits
        """.trimIndent()
    }

    /**
     * Build general analysis prompt (default)
     */
    private fun buildGeneralAnalysisPrompt(data: AnonymizedFinancialData): String {
        val dataQuality = when {
            data.transactionCount < 5 -> "limited"
            data.transactionCount < 20 -> "moderate"
            else -> "comprehensive"
        }

        return """
        Analyze my financial data and provide comprehensive insights and recommendations.

        **Financial Summary:**
        - Total spent: ₹${data.totalSpent.roundToInt()}
        - Transactions: ${data.transactionCount}
        - Data quality: $dataQuality
        - Spending trend: ${data.contextData.spendingTrendDirection}

        **Category Breakdown:**
        ${data.categoryBreakdown.take(5).joinToString("\n") { "- ${it.categoryName}: ₹${it.totalAmount.roundToInt()} (${it.percentage.roundToInt()}%)" }}

        **Top Merchants:**
        ${data.topMerchants.take(3).joinToString("\n") { "- ${it.merchantName}: ₹${it.totalAmount.roundToInt()}" }}

        **Weekly Patterns:**
        ${data.weeklyPatterns.sortedByDescending { it.averageAmount }.take(3).joinToString(", ") { "${it.dayOfWeek}: ₹${it.averageAmount.roundToInt()}" }}

        **Required JSON Response Format:**
        {
          "insights": [
            {
              "type": "SPENDING_FORECAST|PATTERN_ALERT|BUDGET_OPTIMIZATION|SAVINGS_OPPORTUNITY|ANOMALY_DETECTION",
              "title": "Brief, actionable insight title",
              "description": "Detailed explanation of the insight",
              "actionableAdvice": "Specific steps the user can take",
              "impactAmount": 0.0,
              "priority": "LOW|MEDIUM|HIGH|URGENT"
            }
          ]
        }

        **Analysis Focus:**
        1. **Spending Patterns**: Identify trends, habits, and anomalies
        2. **Budget Optimization**: Find areas to reduce spending without compromising lifestyle
        3. **Savings Opportunities**: Suggest practical ways to save money
        4. **Financial Health**: Assess overall spending health and provide recommendations

        **Guidelines:**
        - Use Indian Rupees (₹) for all amounts
        - Provide practical advice suitable for Indian market context
        - Focus on actionable, specific recommendations
        - Prioritize insights based on potential financial impact
        - Be concise but thorough in explanations
        - Consider Indian spending patterns and lifestyle factors

        Generate 2-3 most relevant insights based on the data provided.
        """.trimIndent()
    }

    /**
     * Build prompt with user context (if available)
     */
    fun buildContextAwarePrompt(
        data: AnonymizedFinancialData,
        userPreferences: Map<String, Any> = emptyMap()
    ): String {
        val basePrompt = buildGeneralAnalysisPrompt(data)

        if (userPreferences.isEmpty()) {
            return basePrompt
        }

        val contextualInfo = buildString {
            userPreferences["monthly_budget"]?.let { budget ->
                appendLine("Monthly budget: ₹$budget")
            }
            userPreferences["savings_goal"]?.let { goal ->
                appendLine("Savings goal: ₹$goal")
            }
            userPreferences["priority_categories"]?.let { categories ->
                appendLine("Priority categories: $categories")
            }
        }

        return if (contextualInfo.isNotBlank()) {
            """
            $basePrompt

            **User Context:**
            $contextualInfo

            Please tailor recommendations considering these user preferences.
            """.trimIndent()
        } else {
            basePrompt
        }
    }

    /**
     * Build optimization tip prompt
     */
    private fun buildOptimizationTipPrompt(data: AnonymizedFinancialData): String {
        return """
        You are a financial advisor analyzing spending patterns. Based on this anonymized transaction data, provide practical optimization tips.

        **Transaction Data:**
        ${buildDataSummary(data)}

        **Category Breakdown:**
        ${data.categoryBreakdown.joinToString("\n") { "- ${it.categoryName}: ₹${it.totalAmount.toInt()} (${it.transactionCount} transactions)" }}

        **Please provide optimization tips in JSON format:**
        {
          "insights": [
            {
              "type": "OPTIMIZATION_TIP",
              "title": "Brief optimization tip title",
              "description": "Detailed explanation of the optimization opportunity",
              "actionableAdvice": "Specific steps to implement this tip",
              "impactAmount": estimated_savings_amount,
              "priority": "HIGH|MEDIUM|LOW"
            }
          ]
        }

        Focus on practical, implementable suggestions that consider the user's spending patterns.
        """.trimIndent()
    }

    /**
     * Build data summary for prompts
     */
    private fun buildDataSummary(data: AnonymizedFinancialData): String {
        return """
        Total Spent: ₹${data.totalSpent.toInt()}
        Transaction Count: ${data.transactionCount}
        Timeframe: ${data.timeframe}
        Average per transaction: ₹${if (data.transactionCount > 0) (data.totalSpent / data.transactionCount).toInt() else 0}
        """.trimIndent()
    }

    /**
     * Validate prompt length and content
     */
    private fun validatePrompt(prompt: String): String {
        var validatedPrompt = prompt

        // Ensure prompt isn't too long
        if (prompt.length > MAX_PROMPT_LENGTH) {
            Timber.tag(TAG).w("Prompt too long (${prompt.length} chars), truncating")
            validatedPrompt = prompt.take(MAX_PROMPT_LENGTH - 100) + "\n\n[Content truncated for API efficiency]"
        }

        // Ensure JSON format requirement is present
        if (!validatedPrompt.contains("JSON Response")) {
            validatedPrompt += "\n\nPlease respond in valid JSON format as specified."
        }

        return validatedPrompt
    }
}