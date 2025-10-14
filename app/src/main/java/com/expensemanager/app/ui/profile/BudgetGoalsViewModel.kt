package com.expensemanager.app.ui.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.utils.logging.StructuredLogger
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BudgetGoalsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExpenseRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BudgetGoalsVM"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("budget_settings", Context.MODE_PRIVATE)
    private val categoryManager = CategoryManager(context)
    private val merchantAliasManager = MerchantAliasManager(context)
    private val logger = StructuredLogger("BudgetGoalsViewModel", "BudgetGoalsViewModel")
    // UI State
    private val _uiState = MutableStateFlow(BudgetGoalsUiState())
    val uiState: StateFlow<BudgetGoalsUiState> = _uiState.asStateFlow()

    // Events
    private val _events = MutableStateFlow<BudgetGoalsEvent?>(null)
    val events: StateFlow<BudgetGoalsEvent?> = _events.asStateFlow()

    fun loadBudgetData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Load monthly budget from database (with SharedPreferences migration)
                val budgetEntity = repository.getMonthlyBudget()
                val monthlyBudget = budgetEntity?.budgetAmount?.toFloat() ?: run {
                    // Migration: If no budget in DB, check SharedPreferences
                    val prefsBudget = prefs.getFloat("monthly_budget", 15000f)
                    if (prefsBudget != 15000f) {
                        // Migrate to database
                        logger.debug("loadBudgetData", "Migrating budget from SharedPreferences to database: ₹$prefsBudget")
                        repository.saveMonthlyBudget(prefsBudget.toDouble())
                    }
                    prefsBudget
                }
                
                // Get current month date range
                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH)
                val currentYear = calendar.get(Calendar.YEAR)
                
                // Start of current month
                calendar.set(currentYear, currentMonth, 1, 0, 0, 0)
                val startDate = calendar.time
                
                // End of current month  
                calendar.set(currentYear, currentMonth, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
                val endDate = calendar.time
                
                // Get transaction data from repository (with exclusions already applied)
                val currentSpent = repository.getTotalSpent(startDate, endDate).toFloat()
                
                val budgetProgress = if (monthlyBudget > 0) ((currentSpent / monthlyBudget) * 100).toInt() else 0
                
                // Calculate insights
                val insights = calculateBudgetInsights(monthlyBudget, currentSpent, budgetProgress)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    monthlyBudget = monthlyBudget,
                    currentSpent = currentSpent,
                    budgetProgress = budgetProgress,
                    insights = insights
                )
                
                // Load category budgets with real spending data
                loadCategoryBudgets(startDate, endDate)

                // Budget alerts disabled - user can see budget status on screen
                
            } catch (e: Exception) {
                logger.error("loadBudgetData","Error loading budget data",e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading budget data: ${e.message}"
                )
                
                // Fallback to default data
                loadBudgetDataFallback()
            }
        }
    }

    private fun loadBudgetDataFallback() {
        val monthlyBudget = prefs.getFloat("monthly_budget", 15000f)
        val currentSpent = 0f
        val insights = calculateBudgetInsights(monthlyBudget, currentSpent, 0)
        
        _uiState.value = _uiState.value.copy(
            monthlyBudget = monthlyBudget,
            currentSpent = currentSpent,
            budgetProgress = 0,
            insights = insights
        )
        
        loadDefaultCategoryBudgets()
    }

    private suspend fun loadCategoryBudgets(startDate: Date, endDate: Date) {
        val categoryBudgetsJson = prefs.getString("category_budgets", "")
        val categoryBudgets = mutableListOf<CategoryBudgetItem>()
        
        // Get category spending from repository (with exclusions already applied)
        val categorySpending = repository.getCategorySpending(startDate, endDate)
        val categorySpendingMap = categorySpending.associate { it.category_name to it.total_amount }
        
        if (categoryBudgetsJson?.isNotEmpty() == true) {
            try {
                val jsonArray = JSONArray(categoryBudgetsJson)
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    val categoryName = json.getString("category")
                    val actualSpent = categorySpendingMap[categoryName] ?: 0.0
                    
                    categoryBudgets.add(
                        CategoryBudgetItem(
                            categoryName = categoryName,
                            budgetAmount = json.getDouble("budget").toFloat(),
                            spentAmount = actualSpent.toFloat(),
                            categoryColor = json.getString("color")
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error("loadCategoryBudgets","Error parsing category budgets",e)
                loadDefaultCategoryBudgetsWithRealSpending(categoryBudgets, categorySpendingMap)
            }
        } else {
            // Load default budgets with real spending
            loadDefaultCategoryBudgetsWithRealSpending(categoryBudgets, categorySpendingMap)
        }
        
        _uiState.value = _uiState.value.copy(categoryBudgets = categoryBudgets)
    }

    private fun loadDefaultCategoryBudgetsWithRealSpending(
        categoryBudgets: MutableList<CategoryBudgetItem>, 
        categorySpendingMap: Map<String, Double>
    ) {
        val defaultCategories = listOf(
            "Food & Dining" to 4000f,
            "Transportation" to 2000f,
            "Groceries" to 3000f,
            "Healthcare" to 1500f,
            "Entertainment" to 1000f,
            "Shopping" to 2000f,
            "Utilities" to 1500f
        )
        
        defaultCategories.forEach { (categoryName, budgetAmount) ->
            val actualSpent = categorySpendingMap[categoryName] ?: 0.0
            categoryBudgets.add(
                CategoryBudgetItem(
                    categoryName = categoryName,
                    budgetAmount = budgetAmount,
                    spentAmount = actualSpent.toFloat(),
                    categoryColor = getCategoryColor(categoryName)
                )
            )
        }
        
        saveCategoryBudgets(categoryBudgets)
    }

    private fun loadDefaultCategoryBudgets() {
        val categoryBudgets = listOf(
            CategoryBudgetItem("Food & Dining", 4000f, 3200f, "#ff5722"),
            CategoryBudgetItem("Transportation", 2000f, 1650f, "#3f51b5"),
            CategoryBudgetItem("Groceries", 3000f, 2850f, "#4caf50"),
            CategoryBudgetItem("Healthcare", 1500f, 950f, "#e91e63"),
            CategoryBudgetItem("Entertainment", 1000f, 750f, "#9c27b0")
        )
        
        _uiState.value = _uiState.value.copy(categoryBudgets = categoryBudgets)
        saveCategoryBudgets(categoryBudgets)
    }

    private fun calculateBudgetInsights(monthlyBudget: Float, currentSpent: Float, budgetProgress: Int): BudgetInsights {
        val calendar = Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysRemaining = daysInMonth - currentDay
        val monthProgress = (currentDay.toFloat() / daysInMonth.toFloat()) * 100
        
        // Calculate projected spending
        val projectedSpending = if (currentDay > 0) (currentSpent / currentDay) * daysInMonth else currentSpent
        
        val statusText = "📊 You're $budgetProgress% through your monthly budget with $daysRemaining days remaining (${monthProgress.toInt()}% of month elapsed)."
        
        val tip = when {
            budgetProgress >= 100 -> {
                val overBudget = currentSpent - monthlyBudget
                "🚨 ALERT: You're ₹${String.format("%.0f", overBudget)} over budget! Consider immediate expense reduction."
            }
            budgetProgress > monthProgress + 20 -> {
                "⚠️ WARNING: You're spending ${(budgetProgress - monthProgress).toInt()}% faster than expected! Projected monthly spend: ₹${String.format("%.0f", projectedSpending)}"
            }
            budgetProgress > monthProgress + 10 -> {
                "💡 CAUTION: Spending slightly ahead of pace. Monitor expenses to stay on track."
            }
            budgetProgress > monthProgress - 10 -> {
                "✅ ON TRACK: Your spending pace matches the month progress. Keep it up!"
            }
            else -> {
                val savedAmount = monthlyBudget - projectedSpending
                "🎯 EXCELLENT: You're spending below budget pace! Potential savings: ₹${String.format("%.0f", savedAmount)}"
            }
        }
        
        return BudgetInsights(statusText, tip)
    }

    fun updateMonthlyBudget(newBudget: Float) {
        viewModelScope.launch {
            try {
                // Save to database instead of SharedPreferences
                repository.saveMonthlyBudget(newBudget.toDouble())
                _events.value = BudgetGoalsEvent.ShowMessage("Monthly budget updated to ₹${String.format("%.0f", newBudget)}")
                loadBudgetData()
            } catch (e: Exception) {
                logger.error("updateMonthlyBudget", "Error updating monthly budget", e)
                _events.value = BudgetGoalsEvent.ShowMessage("Error updating budget: ${e.message}")
            }
        }
    }

    fun addCategoryBudget(categoryName: String, budget: Float) {
        val currentList = _uiState.value.categoryBudgets.toMutableList()
        val categoryColor = getCategoryColor(categoryName)
        
        currentList.add(CategoryBudgetItem(categoryName, budget, 0f, categoryColor))
        _uiState.value = _uiState.value.copy(categoryBudgets = currentList)
        saveCategoryBudgets(currentList)
        _events.value = BudgetGoalsEvent.ShowMessage("Budget set for $categoryName")
    }

    fun updateCategoryBudget(categoryName: String, newBudget: Float) {
        val currentList = _uiState.value.categoryBudgets.toMutableList()
        val index = currentList.indexOfFirst { it.categoryName == categoryName }
        
        if (index != -1) {
            currentList[index] = currentList[index].copy(budgetAmount = newBudget)
            _uiState.value = _uiState.value.copy(categoryBudgets = currentList)
            saveCategoryBudgets(currentList)
            _events.value = BudgetGoalsEvent.ShowMessage("Budget updated")
        }
    }

    private fun saveCategoryBudgets(categoryBudgets: List<CategoryBudgetItem>) {
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
        prefs.edit().putString("category_budgets", jsonArray.toString()).apply()
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

    fun generateBudgetValidationDetails(): String {
        val currentState = _uiState.value
        val budgetProgress = currentState.budgetProgress
        val monthlyBudget = currentState.monthlyBudget
        val currentSpent = currentState.currentSpent
        
        return buildString {
            appendLine("📊 CURRENT BUDGET VALIDATION")
            appendLine()
            appendLine("💰 Monthly Budget: ₹${String.format("%.0f", monthlyBudget)}")
            appendLine("💸 Current Spent: ₹${String.format("%.0f", currentSpent)}")
            appendLine("📈 Progress: $budgetProgress%")
            appendLine("💳 Remaining: ₹${String.format("%.0f", monthlyBudget - currentSpent)}")
            appendLine()
            appendLine("✅ Data Source: ExpenseRepository (Database)")
            appendLine("📊 Exclusions Applied: Yes (merchant-based)")
            appendLine()
            appendLine("🚨 Alert Threshold: ${if (budgetProgress >= 90) "TRIGGERED" else "NOT TRIGGERED"}")
            appendLine("🔢 Calculation: (${String.format("%.0f", currentSpent)} ÷ ${String.format("%.0f", monthlyBudget)}) × 100 = $budgetProgress%")
            
            if (budgetProgress >= 100) {
                appendLine()
                appendLine("🆘 OVER BUDGET by ₹${String.format("%.0f", currentSpent - monthlyBudget)}!")
            }
        }
    }

    fun generateAIRecommendations(): String {
        val currentState = _uiState.value
        val overAmount = currentState.currentSpent - currentState.monthlyBudget
        return buildString {
            appendLine("Based on your spending patterns:")
            appendLine()
            appendLine("🎯 You're ₹${String.format("%.0f", overAmount)} over budget")
            appendLine()
            appendLine("💡 Recommendations:")
            appendLine("• Reduce dining out by 30% (save ~₹800)")
            appendLine("• Use public transport more (save ~₹400)")
            appendLine("• Limit shopping to essentials (save ~₹600)")
            appendLine("• Review subscription services")
            appendLine()
            append("These changes could save ~₹1,800/month")
        }
    }

    fun clearEvent() {
        _events.value = null
    }
}

data class BudgetGoalsUiState(
    val isLoading: Boolean = false,
    val monthlyBudget: Float = 15000f,
    val currentSpent: Float = 0f,
    val budgetProgress: Int = 0,
    val categoryBudgets: List<CategoryBudgetItem> = emptyList(),
    val insights: BudgetInsights = BudgetInsights("", ""),
    val error: String? = null
)

data class BudgetInsights(
    val statusText: String,
    val tipText: String
)

sealed class BudgetGoalsEvent {
    data class ShowMessage(val message: String) : BudgetGoalsEvent()
    data class ShowError(val error: String) : BudgetGoalsEvent()
    data class ShowBudgetAlert(val budgetProgress: Int, val currentSpent: Float, val monthlyBudget: Float) : BudgetGoalsEvent()
    object NavigateToCategories : BudgetGoalsEvent()
}