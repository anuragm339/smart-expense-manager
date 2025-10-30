package com.expensemanager.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.expensemanager.app.models.ParsedTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

/**
 * Manages budget data, calculations, and alerts for expense management
 */
class BudgetManager @Inject constructor(
    private val context: Context,
    private val smsHistoryReader: SMSHistoryReader
) {

    private val prefs: SharedPreferences = context.getSharedPreferences("budget_settings", Context.MODE_PRIVATE)
    private val categoryManager = CategoryManager(context)
    private val merchantAliasManager = MerchantAliasManager(context)
    
    companion object {
        private const val TAG = "BudgetManager"
        private const val PREF_MONTHLY_BUDGET = "monthly_budget"
        private const val PREF_CATEGORY_BUDGETS = "category_budgets"
        private const val PREF_BUDGET_ALERTS_SHOWN = "budget_alerts_shown"
    }
    
    data class BudgetSummary(
        val monthlyBudget: Float,
        val totalSpent: Float,
        val remaining: Float,
        val progressPercentage: Int,
        val isOverBudget: Boolean,
        val categoryBreakdown: List<CategoryBudget>
    )
    
    data class CategoryBudget(
        val categoryName: String,
        val budgetAmount: Float,
        val spentAmount: Float,
        val remaining: Float,
        val progressPercentage: Int,
        val isOverBudget: Boolean,
        val categoryColor: String
    )
    
    /**
     * Get current month's budget summary with real transaction data
     */
    suspend fun getCurrentMonthBudgetSummary(): BudgetSummary {
        val monthlyBudget = getMonthlyBudget()

        // Get real transaction data using injected SMS reader
        val transactions = smsHistoryReader.scanHistoricalSMS()
        
        // Calculate current month spending
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        
        val currentMonthTransactions = transactions.filter { transaction ->
            val transactionCalendar = Calendar.getInstance().apply { time = transaction.date }
            transactionCalendar.get(Calendar.MONTH) == currentMonth && 
            transactionCalendar.get(Calendar.YEAR) == currentYear
        }
        
        // Filter by inclusion state
        val filteredTransactions = filterTransactionsByInclusionState(currentMonthTransactions)
        val totalSpent = filteredTransactions.sumOf { it.amount }.toFloat()
        val remaining = monthlyBudget - totalSpent
        val progressPercentage = if (monthlyBudget > 0) ((totalSpent / monthlyBudget) * 100).toInt() else 0
        
        // Calculate category breakdown
        val categoryBreakdown = calculateCategoryBreakdown(filteredTransactions)
        
        return BudgetSummary(
            monthlyBudget = monthlyBudget,
            totalSpent = totalSpent,
            remaining = remaining,
            progressPercentage = progressPercentage,
            isOverBudget = totalSpent > monthlyBudget,
            categoryBreakdown = categoryBreakdown
        )
    }
    
    /**
     * Calculate spending breakdown by category with budget comparison
     */
    private fun calculateCategoryBreakdown(transactions: List<ParsedTransaction>): List<CategoryBudget> {
        // Calculate actual spending by category
        val categorySpending = mutableMapOf<String, Double>()
        transactions.forEach { transaction ->
            val category = categoryManager.categorizeTransaction(transaction.merchant)
            categorySpending[category] = (categorySpending[category] ?: 0.0) + transaction.amount
        }
        
        // Get saved category budgets
        val savedCategoryBudgets = getSavedCategoryBudgets()
        
        // Create category budget objects
        val categoryBudgets = mutableListOf<CategoryBudget>()
        
        // Add budgets for categories that have spending
        categorySpending.forEach { (categoryName, spent) ->
            val budgetAmount = savedCategoryBudgets[categoryName] ?: getDefaultCategoryBudget(categoryName)
            val remaining = budgetAmount - spent.toFloat()
            val progressPercentage = if (budgetAmount > 0) ((spent / budgetAmount) * 100).toInt() else 0
            
            categoryBudgets.add(
                CategoryBudget(
                    categoryName = categoryName,
                    budgetAmount = budgetAmount,
                    spentAmount = spent.toFloat(),
                    remaining = remaining,
                    progressPercentage = progressPercentage,
                    isOverBudget = spent > budgetAmount,
                    categoryColor = getCategoryColor(categoryName)
                )
            )
        }
        
        // Add budgets for categories with no spending but have set budgets
        savedCategoryBudgets.forEach { (categoryName, budgetAmount) ->
            if (!categorySpending.containsKey(categoryName)) {
                categoryBudgets.add(
                    CategoryBudget(
                        categoryName = categoryName,
                        budgetAmount = budgetAmount,
                        spentAmount = 0f,
                        remaining = budgetAmount,
                        progressPercentage = 0,
                        isOverBudget = false,
                        categoryColor = getCategoryColor(categoryName)
                    )
                )
            }
        }
        
        return categoryBudgets.sortedByDescending { it.spentAmount }
    }
    
    /**
     * Get saved category budgets from preferences
     */
    private fun getSavedCategoryBudgets(): Map<String, Float> {
        val categoryBudgets = mutableMapOf<String, Float>()
        val categoryBudgetsJson = prefs.getString(PREF_CATEGORY_BUDGETS, "")
        
        if (!categoryBudgetsJson.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(categoryBudgetsJson)
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    categoryBudgets[json.getString("category")] = json.getDouble("budget").toFloat()
                }
            } catch (e: Exception) {
                // Parse error - use defaults
            }
        }
        
        return categoryBudgets
    }
    
    /**
     * Save category budgets to preferences
     */
    fun saveCategoryBudgets(categoryBudgets: List<CategoryBudget>) {
        val jsonArray = JSONArray()
        categoryBudgets.forEach { budget ->
            val json = JSONObject().apply {
                put("category", budget.categoryName)
                put("budget", budget.budgetAmount.toDouble())
                put("spent", budget.spentAmount.toDouble())
                put("color", budget.categoryColor)
            }
            jsonArray.put(json)
        }
        prefs.edit().putString(PREF_CATEGORY_BUDGETS, jsonArray.toString()).apply()
    }
    
    /**
     * Get monthly budget amount
     */
    fun getMonthlyBudget(): Float {
        return prefs.getFloat(PREF_MONTHLY_BUDGET, 15000f)
    }
    
    /**
     * Set monthly budget amount
     */
    fun setMonthlyBudget(amount: Float): Boolean {
        return if (amount > 0) {
            prefs.edit().putFloat(PREF_MONTHLY_BUDGET, amount).apply()
            true
        } else {
            false
        }
    }
    
    /**
     * Check if budget alerts should be shown
     */
    fun shouldShowBudgetAlert(progressPercentage: Int): Boolean {
        if (progressPercentage < 90) return false
        
        val calendar = Calendar.getInstance()
        val alertKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-$progressPercentage"
        val shownAlerts = prefs.getStringSet(PREF_BUDGET_ALERTS_SHOWN, setOf()) ?: setOf()
        
        return !shownAlerts.contains(alertKey)
    }
    
    /**
     * Mark budget alert as shown
     */
    fun markBudgetAlertShown(progressPercentage: Int) {
        val calendar = Calendar.getInstance()
        val alertKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-$progressPercentage"
        val shownAlerts = prefs.getStringSet(PREF_BUDGET_ALERTS_SHOWN, setOf())?.toMutableSet() ?: mutableSetOf()
        shownAlerts.add(alertKey)
        prefs.edit().putStringSet(PREF_BUDGET_ALERTS_SHOWN, shownAlerts).apply()
    }
    
    /**
     * Generate budget insights and recommendations
     */
    fun generateBudgetInsights(budgetSummary: BudgetSummary): String {
        val calendar = Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val monthProgress = (currentDay.toFloat() / daysInMonth.toFloat()) * 100
        
        return buildString {
            appendLine("📊 Budget Analysis")
            appendLine()
            
            when {
                budgetSummary.isOverBudget -> {
                    appendLine("🚨 OVER BUDGET by ₹${String.format("%.0f", -budgetSummary.remaining)}")
                    appendLine()
                    appendLine("💡 Immediate Actions:")
                    appendLine("• Stop non-essential spending")
                    appendLine("• Review high-spending categories")
                    appendLine("• Consider budget adjustment")
                }
                budgetSummary.progressPercentage > monthProgress + 15 -> {
                    val projectedSpend = if (currentDay > 0) (budgetSummary.totalSpent / currentDay) * daysInMonth else budgetSummary.totalSpent
                    appendLine("⚠️ SPENDING FAST - ${(budgetSummary.progressPercentage - monthProgress).toInt()}% ahead of schedule")
                    appendLine("Projected month-end: ₹${String.format("%.0f", projectedSpend)}")
                    appendLine()
                    appendLine("💡 Recommendations:")
                    appendLine("• Reduce daily spending by ₹${String.format("%.0f", (projectedSpend - budgetSummary.monthlyBudget) / (daysInMonth - currentDay))}")
                    appendLine("• Focus on top spending categories")
                }
                budgetSummary.progressPercentage <= monthProgress -> {
                    val potentialSavings = budgetSummary.monthlyBudget - if (currentDay > 0) (budgetSummary.totalSpent / currentDay) * daysInMonth else budgetSummary.totalSpent
                    appendLine("🎯 ON TRACK or UNDER BUDGET")
                    appendLine("Potential monthly savings: ₹${String.format("%.0f", potentialSavings)}")
                    appendLine()
                    appendLine("💡 Opportunities:")
                    appendLine("• Consider increasing savings goal")
                    appendLine("• Invest extra funds")
                }
                else -> {
                    appendLine("✅ GOOD PACE - spending on track")
                    appendLine("Days remaining: ${daysInMonth - currentDay}")
                    appendLine("Daily budget remaining: ₹${String.format("%.0f", budgetSummary.remaining / (daysInMonth - currentDay))}")
                }
            }
            
            // Add category-specific insights
            val topSpendingCategory = budgetSummary.categoryBreakdown.maxByOrNull { it.spentAmount }
            if (topSpendingCategory != null && topSpendingCategory.spentAmount > 0) {
                appendLine()
                appendLine("🏷️ Top Spending: ${topSpendingCategory.categoryName}")
                appendLine("₹${String.format("%.0f", topSpendingCategory.spentAmount)} (${topSpendingCategory.progressPercentage}% of category budget)")
            }
        }
    }
    
    private fun filterTransactionsByInclusionState(transactions: List<ParsedTransaction>): List<ParsedTransaction> {
        val inclusionPrefs = context.getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
        val inclusionStatesJson = inclusionPrefs.getString("group_inclusion_states", null)
        
        if (inclusionStatesJson != null) {
            try {
                val inclusionStates = JSONObject(inclusionStatesJson)
                return transactions.filter { transaction ->
                    val displayMerchant = merchantAliasManager.getDisplayName(transaction.merchant)
                    if (inclusionStates.has(displayMerchant)) {
                        inclusionStates.getBoolean(displayMerchant)
                    } else {
                        true // Default to included if not found
                    }
                }
            } catch (e: Exception) {
                // Inclusion state error - use all transactions
            }
        }
        
        return transactions
    }
    
    private fun getDefaultCategoryBudget(categoryName: String): Float {
        return when (categoryName) {
            "Food & Dining" -> 4000f
            "Transportation" -> 2000f
            "Groceries" -> 3000f
            "Healthcare" -> 1500f
            "Entertainment" -> 1000f
            "Shopping" -> 2000f
            "Utilities" -> 1500f
            else -> 1000f
        }
    }
    
    private fun getCategoryColor(categoryName: String): String {
        return when (categoryName) {
            "Food & Dining" -> "#ff5722"
            "Transportation" -> "#3f51b5"
            "Healthcare" -> "#e91e63"
            "Groceries" -> "#4caf50"
            "Shopping" -> "#ff9800"
            "Entertainment" -> "#9c27b0"
            "Utilities" -> "#607d8b"
            else -> "#795548"
        }
    }
}