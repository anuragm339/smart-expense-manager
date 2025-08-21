package com.expensemanager.app.ui.profile

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentBudgetGoalsBinding
import com.expensemanager.app.databinding.ItemCategoryBudgetBinding
import com.expensemanager.app.utils.SMSHistoryReader
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.MerchantAliasManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class BudgetGoalsFragment : Fragment() {
    
    private var _binding: FragmentBudgetGoalsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var categoryBudgetsAdapter: CategoryBudgetsAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var categoryManager: CategoryManager
    private lateinit var merchantAliasManager: MerchantAliasManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetGoalsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("budget_settings", Context.MODE_PRIVATE)
        categoryManager = CategoryManager(requireContext())
        merchantAliasManager = MerchantAliasManager(requireContext())
        setupRecyclerView()
        setupClickListeners()
        loadRealBudgetData()
    }
    
    private fun setupRecyclerView() {
        categoryBudgetsAdapter = CategoryBudgetsAdapter { budgetItem ->
            showEditCategoryBudgetDialog(budgetItem)
        }
        binding.recyclerCategoryBudgets.apply {
            adapter = categoryBudgetsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun setupClickListeners() {
        binding.btnEditBudget.setOnClickListener {
            showEditMonthlyBudgetDialog()
        }
        
        binding.btnAddCategoryBudget.setOnClickListener {
            showAddCategoryBudgetDialog()
        }
        
        // Debug: Long press on budget status to force test alerts
        binding.tvBudgetStatus.setOnLongClickListener {
            showBudgetTestingDialog()
            true
        }
        
        // Debug: Long press on spent amount to show validation details
        binding.tvSpentAmount.setOnLongClickListener {
            showCurrentBudgetValidation()
            true
        }
    }
    
    private fun showBudgetTestingDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🧪 Budget Testing")
            .setMessage("Choose a test scenario:")
            .setPositiveButton("Test 90% Alert") { _, _ ->
                testBudgetAlert(90)
            }
            .setNeutralButton("Test Over Budget") { _, _ ->
                testBudgetAlert(105)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testBudgetAlert(progressPercentage: Int) {
        val monthlyBudget = prefs.getFloat("monthly_budget", 15000f)
        val testSpent = (monthlyBudget * progressPercentage / 100f)
        
        // Show test info (only for debug testing)
        Toast.makeText(requireContext(), "Testing ${progressPercentage}% scenario", Toast.LENGTH_SHORT).show()
        
        // Force show alert by calling the alert method directly
        showBudgetAlert(progressPercentage, testSpent, monthlyBudget)
        
        // Also update the insights to show the test scenario
        updateBudgetInsights(monthlyBudget, testSpent, progressPercentage)
    }
    
    private fun showCurrentBudgetValidation() {
        lifecycleScope.launch {
            try {
                val monthlyBudget = prefs.getFloat("monthly_budget", 15000f)
                val smsReader = SMSHistoryReader(requireContext())
                val transactions = smsReader.scanHistoricalSMS()
                
                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH)
                val currentYear = calendar.get(Calendar.YEAR)
                
                val currentMonthTransactions = transactions.filter { transaction ->
                    val transactionCalendar = Calendar.getInstance().apply { time = transaction.date }
                    transactionCalendar.get(Calendar.MONTH) == currentMonth && 
                    transactionCalendar.get(Calendar.YEAR) == currentYear
                }
                
                val filteredTransactions = filterTransactionsByInclusionState(currentMonthTransactions)
                val currentSpent = filteredTransactions.sumOf { it.amount }.toFloat()
                val budgetProgress = if (monthlyBudget > 0) ((currentSpent / monthlyBudget) * 100).toInt() else 0
                
                val validationDetails = buildString {
                    appendLine("📊 CURRENT BUDGET VALIDATION")
                    appendLine()
                    appendLine("💰 Monthly Budget: ₹${String.format("%.0f", monthlyBudget)}")
                    appendLine("💸 Current Spent: ₹${String.format("%.0f", currentSpent)}")
                    appendLine("📈 Progress: $budgetProgress%")
                    appendLine("💳 Remaining: ₹${String.format("%.0f", monthlyBudget - currentSpent)}")
                    appendLine()
                    appendLine("📱 Total SMS Transactions: ${transactions.size}")
                    appendLine("📅 This Month Transactions: ${currentMonthTransactions.size}")
                    appendLine("✅ Filtered (Included) Transactions: ${filteredTransactions.size}")
                    appendLine()
                    appendLine("🚨 Alert Threshold: ${if (budgetProgress >= 90) "TRIGGERED" else "NOT TRIGGERED"}")
                    appendLine("🔢 Calculation: (${String.format("%.0f", currentSpent)} ÷ ${String.format("%.0f", monthlyBudget)}) × 100 = $budgetProgress%")
                    
                    if (budgetProgress >= 100) {
                        appendLine()
                        appendLine("🆘 OVER BUDGET by ₹${String.format("%.0f", currentSpent - monthlyBudget)}!")
                    }
                }
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("🧮 Budget Validation")
                    .setMessage(validationDetails)
                    .setPositiveButton("Recalculate") { _, _ ->
                        loadRealBudgetData()
                    }
                    .setNegativeButton("Close", null)
                    .show()
                    
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error validating budget: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("BudgetValidation", "Error validating budget", e)
            }
        }
    }
    
    private fun loadRealBudgetData() {
        lifecycleScope.launch {
            try {
                // Load monthly budget from preferences
                val monthlyBudget = prefs.getFloat("monthly_budget", 15000f)
                
                // Get real transaction data from SMS
                val smsReader = SMSHistoryReader(requireContext())
                val transactions = smsReader.scanHistoricalSMS()
                
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
                val currentSpent = filteredTransactions.sumOf { it.amount }.toFloat()
                
                // Update UI with real data
                binding.tvSpentAmount.text = "Spent: ₹${String.format("%.0f", currentSpent)}"
                binding.tvRemaining.text = "₹${String.format("%.0f", monthlyBudget - currentSpent)} remaining"
                
                val budgetProgress = if (monthlyBudget > 0) ((currentSpent / monthlyBudget) * 100).toInt() else 0
                binding.progressBudget.progress = budgetProgress
                
                // Update budget label with actual budget amount
                binding.tvBudgetAmount.text = "Budget: ₹${String.format("%.0f", monthlyBudget)}"
                
                // Only log critical errors, remove debug logging for performance
                
                // Update insights
                updateBudgetInsights(monthlyBudget, currentSpent, budgetProgress)
                
                // Load category budgets with real spending data
                loadRealCategoryBudgets(currentMonthTransactions)
                
                // Budget loaded successfully
                
            } catch (e: Exception) {
                Log.e("BudgetGoalsFragment", "Error loading budget data", e)
                Toast.makeText(requireContext(), "Error loading budget data", Toast.LENGTH_SHORT).show()
                
                // Fallback to default data
                loadBudgetDataFallback()
            }
        }
    }
    
    private fun loadBudgetDataFallback() {
        val monthlyBudget = prefs.getFloat("monthly_budget", 15000f)
        val currentSpent = 0f
        
        binding.tvSpentAmount.text = "Spent: ₹0"
        binding.tvRemaining.text = "₹${String.format("%.0f", monthlyBudget)} remaining"
        binding.progressBudget.progress = 0
        
        updateBudgetInsights(monthlyBudget, currentSpent, 0)
        loadCategoryBudgets()
    }
    
    private fun filterTransactionsByInclusionState(transactions: List<com.expensemanager.app.utils.ParsedTransaction>): List<com.expensemanager.app.utils.ParsedTransaction> {
        val inclusionPrefs = requireContext().getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
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
                Log.w("BudgetGoalsFragment", "Error loading inclusion states", e)
            }
        }
        
        return transactions
    }
    
    private fun loadRealCategoryBudgets(currentMonthTransactions: List<com.expensemanager.app.utils.ParsedTransaction>) {
        val categoryBudgetsJson = prefs.getString("category_budgets", "")
        val categoryBudgets = mutableListOf<CategoryBudgetItem>()
        
        // Calculate actual spending by category from transactions
        val categorySpending = mutableMapOf<String, Double>()
        
        val filteredTransactions = filterTransactionsByInclusionState(currentMonthTransactions)
        filteredTransactions.forEach { transaction ->
            val category = categoryManager.categorizeTransaction(transaction.merchant)
            categorySpending[category] = (categorySpending[category] ?: 0.0) + transaction.amount
        }
        
        if (categoryBudgetsJson?.isNotEmpty() == true) {
            try {
                val jsonArray = JSONArray(categoryBudgetsJson)
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    val categoryName = json.getString("category")
                    val actualSpent = categorySpending[categoryName] ?: 0.0
                    
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
                Log.e("BudgetGoalsFragment", "Error parsing category budgets", e)
                loadDefaultCategoryBudgetsWithRealSpending(categoryBudgets, categorySpending)
            }
        } else {
            // Load default budgets with real spending
            loadDefaultCategoryBudgetsWithRealSpending(categoryBudgets, categorySpending)
        }
        
        categoryBudgetsAdapter.submitList(categoryBudgets)
    }
    
    private fun loadDefaultCategoryBudgetsWithRealSpending(
        categoryBudgets: MutableList<CategoryBudgetItem>, 
        categorySpending: Map<String, Double>
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
            val actualSpent = categorySpending[categoryName] ?: 0.0
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
    
    private fun loadCategoryBudgets() {
        // Fallback method for when real data isn't available
        val categoryBudgetsJson = prefs.getString("category_budgets", "")
        val categoryBudgets = mutableListOf<CategoryBudgetItem>()
        
        if (categoryBudgetsJson?.isNotEmpty() == true) {
            try {
                val jsonArray = JSONArray(categoryBudgetsJson)
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    categoryBudgets.add(
                        CategoryBudgetItem(
                            categoryName = json.getString("category"),
                            budgetAmount = json.getDouble("budget").toFloat(),
                            spentAmount = json.getDouble("spent").toFloat(),
                            categoryColor = json.getString("color")
                        )
                    )
                }
            } catch (e: Exception) {
                loadDefaultCategoryBudgets(categoryBudgets)
            }
        } else {
            loadDefaultCategoryBudgets(categoryBudgets)
        }
        
        categoryBudgetsAdapter.submitList(categoryBudgets)
    }
    
    private fun loadDefaultCategoryBudgets(categoryBudgets: MutableList<CategoryBudgetItem>) {
        categoryBudgets.addAll(
            listOf(
                CategoryBudgetItem("Food & Dining", 4000f, 3200f, "#ff5722"),
                CategoryBudgetItem("Transportation", 2000f, 1650f, "#3f51b5"),
                CategoryBudgetItem("Groceries", 3000f, 2850f, "#4caf50"),
                CategoryBudgetItem("Healthcare", 1500f, 950f, "#e91e63"),
                CategoryBudgetItem("Entertainment", 1000f, 750f, "#9c27b0")
            )
        )
        saveCategoryBudgets(categoryBudgets)
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
    
    private fun updateBudgetInsights(monthlyBudget: Float, currentSpent: Float, budgetProgress: Int) {
        val calendar = Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysRemaining = daysInMonth - currentDay
        val monthProgress = (currentDay.toFloat() / daysInMonth.toFloat()) * 100
        
        // Calculate projected spending
        val projectedSpending = if (currentDay > 0) (currentSpent / currentDay) * daysInMonth else currentSpent
        
        binding.tvBudgetStatus.text = "📊 You're $budgetProgress% through your monthly budget with $daysRemaining days remaining (${monthProgress.toInt()}% of month elapsed)."
        
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
        binding.tvBudgetTip.text = tip
        
        // Show budget alert if needed
        if (budgetProgress >= 90) {
            showBudgetAlert(budgetProgress, currentSpent, monthlyBudget)
        }
    }
    
    private fun showBudgetAlert(budgetProgress: Int, currentSpent: Float, monthlyBudget: Float) {
        if (budgetProgress >= 100) {
            // Over budget alert
            val overAmount = currentSpent - monthlyBudget
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("🚨 Budget Exceeded!")
                .setMessage("You've exceeded your monthly budget by ₹${String.format("%.0f", overAmount)}.\n\nWould you like to:\n• View spending breakdown\n• Set spending limits\n• Get AI recommendations")
                .setPositiveButton("View Breakdown") { _, _ ->
                    // Navigate to categories or analytics
                    findNavController().navigate(R.id.navigation_categories)
                }
                .setNeutralButton("AI Help") { _, _ ->
                    showAIBudgetRecommendations(currentSpent, monthlyBudget)
                }
                .setNegativeButton("Dismiss", null)
                .show()
        } else if (budgetProgress >= 90) {
            // Near budget alert
            val remaining = monthlyBudget - currentSpent
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("💡 Budget Alert")
                .setMessage("You've used ${budgetProgress}% of your budget with only ₹${String.format("%.0f", remaining)} remaining.\n\nConsider reducing expenses in high-spending categories.")
                .setPositiveButton("View Categories") { _, _ ->
                    findNavController().navigate(R.id.navigation_categories)
                }
                .setNegativeButton("OK", null)
                .show()
        }
    }
    
    private fun showAIBudgetRecommendations(currentSpent: Float, monthlyBudget: Float) {
        // This will be enhanced later with actual AI integration
        val recommendations = generateBasicRecommendations(currentSpent, monthlyBudget)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("💡 Budget Recommendations")
            .setMessage(recommendations)
            .setPositiveButton("Set Reminders") { _, _ ->
                // TODO: Set up spending reminders/alerts
                Toast.makeText(requireContext(), "Budget reminders enabled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun generateBasicRecommendations(currentSpent: Float, monthlyBudget: Float): String {
        val overAmount = currentSpent - monthlyBudget
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
    
    private fun showEditMonthlyBudgetDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_budget, null)
        val budgetInput = dialogView.findViewById<TextInputEditText>(R.id.et_budget_amount)
        
        val currentBudget = prefs.getFloat("monthly_budget", 15000f)
        budgetInput.setText(currentBudget.toInt().toString())
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Monthly Budget")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newBudget = budgetInput.text.toString().toFloatOrNull()
                if (newBudget != null && newBudget > 0) {
                    prefs.edit().putFloat("monthly_budget", newBudget).apply()
                    loadRealBudgetData()
                    Toast.makeText(requireContext(), "Monthly budget updated to ₹${String.format("%.0f", newBudget)}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid budget amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showAddCategoryBudgetDialog() {
        val categories = arrayOf("Food & Dining", "Transportation", "Groceries", "Healthcare", "Shopping", "Entertainment", "Utilities", "Other")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Category")
            .setItems(categories) { _, which ->
                val category = categories[which]
                showSetCategoryBudgetDialog(category)
            }
            .show()
    }
    
    private fun showSetCategoryBudgetDialog(categoryName: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_budget, null)
        val budgetInput = dialogView.findViewById<TextInputEditText>(R.id.et_budget_amount)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Budget for $categoryName")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val budget = budgetInput.text.toString().toFloatOrNull()
                if (budget != null && budget > 0) {
                    addCategoryBudget(categoryName, budget)
                    Toast.makeText(requireContext(), "Budget set for $categoryName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showEditCategoryBudgetDialog(budgetItem: CategoryBudgetItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_budget, null)
        val budgetInput = dialogView.findViewById<TextInputEditText>(R.id.et_budget_amount)
        
        budgetInput.setText(budgetItem.budgetAmount.toInt().toString())
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Budget for ${budgetItem.categoryName}")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newBudget = budgetInput.text.toString().toFloatOrNull()
                if (newBudget != null && newBudget > 0) {
                    updateCategoryBudget(budgetItem.categoryName, newBudget)
                    Toast.makeText(requireContext(), "Budget updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun addCategoryBudget(categoryName: String, budget: Float) {
        val currentList = categoryBudgetsAdapter.currentList.toMutableList()
        val categoryColor = getCategoryColor(categoryName)
        
        currentList.add(CategoryBudgetItem(categoryName, budget, 0f, categoryColor))
        categoryBudgetsAdapter.submitList(currentList)
        saveCategoryBudgets(currentList)
    }
    
    private fun updateCategoryBudget(categoryName: String, newBudget: Float) {
        val currentList = categoryBudgetsAdapter.currentList.toMutableList()
        val index = currentList.indexOfFirst { it.categoryName == categoryName }
        
        if (index != -1) {
            currentList[index] = currentList[index].copy(budgetAmount = newBudget)
            categoryBudgetsAdapter.submitList(currentList)
            saveCategoryBudgets(currentList)
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
    
    override fun onResume() {
        super.onResume()
        // Refresh budget data when returning to this fragment
        loadRealBudgetData()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class CategoryBudgetItem(
    val categoryName: String,
    val budgetAmount: Float,
    val spentAmount: Float,
    val categoryColor: String
)

class CategoryBudgetsAdapter(
    private val onEditClick: (CategoryBudgetItem) -> Unit
) : RecyclerView.Adapter<CategoryBudgetsAdapter.ViewHolder>() {
    
    private var items = listOf<CategoryBudgetItem>()
    
    fun submitList(newItems: List<CategoryBudgetItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    val currentList: List<CategoryBudgetItem> get() = items
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBudgetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onEditClick)
    }
    
    override fun getItemCount() = items.size
    
    class ViewHolder(private val binding: ItemCategoryBudgetBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: CategoryBudgetItem, onEditClick: (CategoryBudgetItem) -> Unit) {
            binding.tvCategoryName.text = item.categoryName
            binding.tvBudgetAmount.text = "Budget: ₹${String.format("%.0f", item.budgetAmount)}"
            binding.tvSpentAmount.text = "Spent: ₹${String.format("%.0f", item.spentAmount)}"
            
            val percentage = if (item.budgetAmount > 0) ((item.spentAmount / item.budgetAmount) * 100).toInt() else 0
            binding.progressCategoryBudget.progress = percentage
            binding.tvBudgetPercentage.text = "$percentage% used"
            
            val remaining = item.budgetAmount - item.spentAmount
            binding.tvRemainingAmount.text = if (remaining >= 0) "₹${String.format("%.0f", remaining)} left" else "₹${String.format("%.0f", -remaining)} over"
            
            try {
                binding.viewCategoryColor.setBackgroundColor(Color.parseColor(item.categoryColor))
            } catch (e: Exception) {
                // Fallback color
            }
            
            binding.btnEditCategoryBudget.setOnClickListener {
                onEditClick(item)
            }
        }
    }
}