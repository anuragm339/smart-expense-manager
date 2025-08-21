package com.expensemanager.app.ui.categories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentCategoriesBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.expensemanager.app.utils.SMSHistoryReader
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.utils.ParsedTransaction
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.ui.dashboard.ProcessedTransaction
import kotlinx.coroutines.launch
import java.util.*
import android.content.Context
import android.util.Log

class CategoriesFragment : Fragment() {
    
    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var categoriesAdapter: CategoriesAdapter
    private lateinit var merchantAliasManager: MerchantAliasManager
    private lateinit var categoryManager: CategoryManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        merchantAliasManager = MerchantAliasManager(requireContext())
        categoryManager = CategoryManager(requireContext())
        setupRecyclerView()
        setupUI()
        setupClickListeners()
        loadRealCategoryData()
    }
    
    private fun setupRecyclerView() {
        categoriesAdapter = CategoriesAdapter { categoryItem ->
            // Navigate to category transactions
            val bundle = Bundle().apply {
                putString("categoryName", categoryItem.name)
            }
            findNavController().navigate(
                R.id.action_navigation_categories_to_navigation_category_transactions,
                bundle
            )
        }
        binding.recyclerCategories.apply {
            adapter = categoriesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        
        // Initially show empty list - real data will be loaded by loadRealCategoryData()
        categoriesAdapter.submitList(emptyList())
    }
    
    private fun setupUI() {
        binding.tvTotalSpending.text = "Loading..."
        binding.tvCategoriesCount.text = "0 Categories"
    }
    
    private fun loadRealCategoryData() {
        lifecycleScope.launch {
            try {
                val smsReader = SMSHistoryReader(requireContext())
                val transactions = smsReader.scanHistoricalSMS()
                
                if (transactions.isNotEmpty()) {
                    // Process transactions with current month filter
                    val calendar = Calendar.getInstance()
                    val currentMonth = calendar.get(Calendar.MONTH)
                    val currentYear = calendar.get(Calendar.YEAR)
                    
                    val currentMonthTransactions = transactions.filter { transaction ->
                        val transactionCalendar = Calendar.getInstance().apply { time = transaction.date }
                        transactionCalendar.get(Calendar.MONTH) == currentMonth && 
                        transactionCalendar.get(Calendar.YEAR) == currentYear
                    }
                    
                    // Convert to ProcessedTransaction format (same as Dashboard)
                    val processedTransactions = currentMonthTransactions.map { parsedTransaction ->
                        val category = merchantAliasManager.getMerchantCategory(parsedTransaction.merchant)
                        ProcessedTransaction(
                            originalMerchant = parsedTransaction.merchant,
                            displayMerchant = merchantAliasManager.getDisplayName(parsedTransaction.merchant),
                            amount = parsedTransaction.amount,
                            category = category,
                            categoryColor = merchantAliasManager.getMerchantCategoryColor(parsedTransaction.merchant),
                            date = parsedTransaction.date,
                            bankName = parsedTransaction.bankName
                        )
                    }
                    
                    // Filter by inclusion states from Messages screen (same as Dashboard)
                    val filteredTransactions = filterTransactionsByInclusionState(processedTransactions)
                    
                    Log.d("CategoriesFragment", "Processed ${filteredTransactions.size} transactions for categories")
                    
                    // Group by category and calculate spending (use filtered transactions)
                    val transactionCategoryData = filteredTransactions
                        .groupBy { transaction -> transaction.category }
                        .map { (category, categoryTransactions) ->
                            val amount = categoryTransactions.sumOf { transaction -> transaction.amount }
                            val count = categoryTransactions.size
                            val lastTransaction = categoryTransactions.maxByOrNull { transaction -> transaction.date }
                            val lastTransactionText = lastTransaction?.let { formatLastTransaction(it.date) } ?: "No transactions"
                            
                            Log.d("CategoriesFragment", "Category: $category, Amount: ₹${String.format("%.0f", amount)}, Count: $count")
                            
                            CategoryItem(
                                name = category,
                                emoji = getCategoryEmoji(category),
                                color = categoryTransactions.firstOrNull()?.categoryColor ?: categoryManager.getCategoryColor(category),
                                amount = amount,
                                transactionCount = count,
                                lastTransaction = lastTransactionText,
                                percentage = 0, // Will be calculated after we have total
                                progress = 0    // Will be calculated after we have total
                            )
                        }
                    
                    // Add custom categories that might not have transactions yet
                    val allCategories = categoryManager.getAllCategories()
                    val existingCategoryNames = transactionCategoryData.map { it.name }
                    val missingCategories: List<CategoryItem> = allCategories.filter { !existingCategoryNames.contains(it) }
                        .map { categoryName: String ->
                            CategoryItem(
                                name = categoryName,
                                emoji = getCategoryEmoji(categoryName),
                                color = getRandomCategoryColor(),
                                amount = 0.0,
                                transactionCount = 0,
                                lastTransaction = "No transactions yet",
                                percentage = 0,
                                progress = 0
                            )
                        }
                    
                    val categoryData: List<CategoryItem> = (transactionCategoryData + missingCategories)
                        .sortedByDescending { categoryItem: CategoryItem -> categoryItem.amount }
                    
                    // Calculate percentages and progress
                    val totalSpent = categoryData.sumOf { categoryItem -> categoryItem.amount }
                    val categoriesWithPercentages = categoryData.map { categoryItem ->
                        val percentage = if (totalSpent > 0) ((categoryItem.amount / totalSpent) * 100).toInt() else 0
                        categoryItem.copy(
                            percentage = percentage,
                            progress = percentage.coerceAtMost(100)
                        )
                    }
                    
                    // Update UI
                    categoriesAdapter.submitList(categoriesWithPercentages)
                    binding.tvTotalSpending.text = "₹${String.format("%.0f", totalSpent)}"
                    binding.tvCategoriesCount.text = "${categoriesWithPercentages.size} Categories"
                    
                } else {
                    // No data found - show empty state
                    categoriesAdapter.submitList(getSampleCategories())
                    binding.tvTotalSpending.text = "₹0"
                    binding.tvCategoriesCount.text = "0 Categories"
                }
                
            } catch (e: SecurityException) {
                // Permission denied - show sample data
                categoriesAdapter.submitList(getSampleCategories())
                binding.tvTotalSpending.text = "₹12,540"
                binding.tvCategoriesCount.text = "5 Categories"
                Toast.makeText(requireContext(), "SMS permission required for real data", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Error loading data - show sample data
                categoriesAdapter.submitList(getSampleCategories())
                binding.tvTotalSpending.text = "₹12,540"
                binding.tvCategoriesCount.text = "5 Categories"
                Toast.makeText(requireContext(), "Error loading category data", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun getCategoryEmoji(category: String): String {
        return when (category.lowercase()) {
            "food & dining", "food", "dining" -> "🍽️"
            "transportation", "transport" -> "🚗"
            "groceries", "grocery" -> "🛒"
            "healthcare", "health" -> "🏥"
            "entertainment" -> "🎬"
            "shopping" -> "🛍️"
            "utilities" -> "⚡"
            else -> "📂"
        }
    }
    
    private fun formatLastTransaction(date: Date): String {
        val now = Date()
        val diffInMs = now.time - date.time
        val diffInHours = diffInMs / (1000 * 60 * 60)
        val diffInDays = diffInMs / (1000 * 60 * 60 * 24)
        
        return when {
            diffInHours < 1 -> "Just now"
            diffInHours < 24 -> "$diffInHours hours ago"
            diffInDays == 1L -> "Yesterday"
            diffInDays < 7 -> "$diffInDays days ago"
            else -> "${diffInDays / 7} weeks ago"
        }
    }
    
    private fun setupClickListeners() {
        binding.btnAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }
        
        binding.fabQuickExpense.setOnClickListener {
            showQuickAddDialog()
        }
    }
    
    private fun showAddCategoryDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_add_category, 
            null
        )
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Category")
            .setView(dialogView)
            .create()
        
        // Set up emoji quick selection
        val emojiInput = dialogView.findViewById<TextInputEditText>(R.id.et_emoji)
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_food).setOnClickListener {
            emojiInput.setText("🍽️")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_transport).setOnClickListener {
            emojiInput.setText("🚗")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_shopping).setOnClickListener {
            emojiInput.setText("🛒")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_health).setOnClickListener {
            emojiInput.setText("🏥")
        }
        dialogView.findViewById<MaterialButton>(R.id.btn_emoji_entertainment).setOnClickListener {
            emojiInput.setText("🎬")
        }
        
        // Set up click listeners
        dialogView.findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<MaterialButton>(R.id.btn_add).setOnClickListener {
            val categoryName = dialogView.findViewById<TextInputEditText>(R.id.et_category_name).text.toString()
            val selectedEmoji = dialogView.findViewById<TextInputEditText>(R.id.et_emoji).text.toString()
            
            if (categoryName.isNotEmpty()) {
                addNewCategory(categoryName, selectedEmoji.ifEmpty { "📂" })
                dialog.dismiss()
            } else {
                Toast.makeText(
                    requireContext(), 
                    "Please enter a category name", 
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        dialog.show()
    }
    
    private fun addNewCategory(name: String, emoji: String) {
        // Add to CategoryManager for persistence
        categoryManager.addCustomCategory(name)
        
        val currentList = categoriesAdapter.currentList.toMutableList()
        val newCategory = CategoryItem(
            name = name,
            emoji = emoji,
            color = getRandomCategoryColor(),
            amount = 0.0,
            transactionCount = 0,
            lastTransaction = "No transactions yet",
            percentage = 0,
            progress = 0
        )
        
        currentList.add(newCategory)
        categoriesAdapter.submitList(currentList)
        
        // Update category count
        binding.tvCategoriesCount.text = "${currentList.size} Categories"
        
        Log.d("CategoriesFragment", "Added new category '$name' to CategoryManager")
        
        Toast.makeText(
            requireContext(),
            "Category '$name' added successfully!",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Ensure "Money" category is available (commonly needed category)
        val allCategories = categoryManager.getAllCategories()
        if (!allCategories.contains("Money")) {
            categoryManager.addCustomCategory("Money")
            Log.d("CategoriesFragment", "Added 'Money' category to CategoryManager")
        }
        
        // Reload data when returning to this screen to show any new categories
        loadRealCategoryData()
    }
    
    private fun getRandomCategoryColor(): String {
        val colors = listOf(
            "#ff5722", "#3f51b5", "#4caf50", "#e91e63", 
            "#ff9800", "#9c27b0", "#607d8b", "#795548",
            "#2196f3", "#8bc34a", "#ffc107", "#673ab7"
        )
        return colors.random()
    }
    
    private fun showQuickAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_quick_add_expense, 
            null
        )
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        
        // Set up category dropdown
        val categorySpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_category)
        val categories = arrayOf("Food & Dining", "Transportation", "Healthcare", "Groceries", "Entertainment", "Shopping", "Utilities", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        categorySpinner.setAdapter(adapter)
        
        // Set up click listeners
        dialogView.findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<MaterialButton>(R.id.btn_add).setOnClickListener {
            val amount = dialogView.findViewById<TextInputEditText>(R.id.et_amount).text.toString()
            val merchant = dialogView.findViewById<TextInputEditText>(R.id.et_merchant).text.toString()
            val category = categorySpinner.text.toString()
            
            if (amount.isNotEmpty() && merchant.isNotEmpty()) {
                // TODO: Save expense to database
                Toast.makeText(
                    requireContext(), 
                    "Added: ₹$amount at $merchant ($category)", 
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            } else {
                Toast.makeText(
                    requireContext(), 
                    "Please fill all fields", 
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        dialog.show()
    }
    
    private fun getSampleCategories(): List<CategoryItem> {
        val defaultSampleCategories = listOf(
            CategoryItem(
                name = "Food & Dining",
                emoji = "🍽️",
                color = "#ff5722",
                amount = 4250.0,
                transactionCount = 23,
                lastTransaction = "2 hours ago",
                percentage = 34,
                progress = 65
            ),
            CategoryItem(
                name = "Transportation",
                emoji = "🚗",
                color = "#3f51b5",
                amount = 2150.0,
                transactionCount = 15,
                lastTransaction = "Yesterday",
                percentage = 17,
                progress = 45
            ),
            CategoryItem(
                name = "Groceries",
                emoji = "🛒",
                color = "#4caf50",
                amount = 3100.0,
                transactionCount = 8,
                lastTransaction = "2 days ago",
                percentage = 25,
                progress = 55
            ),
            CategoryItem(
                name = "Healthcare",
                emoji = "🏥",
                color = "#e91e63",
                amount = 1840.0,
                transactionCount = 5,
                lastTransaction = "5 days ago",
                percentage = 15,
                progress = 30
            ),
            CategoryItem(
                name = "Entertainment",
                emoji = "🎬",
                color = "#9c27b0",
                amount = 1200.0,
                transactionCount = 7,
                lastTransaction = "1 week ago",
                percentage = 9,
                progress = 25
            )
        )
        
        // Add custom categories with no transactions
        val customCategories = categoryManager.getAllCategories()
        val defaultCategoryNames = defaultSampleCategories.map { it.name }
        val additionalCustomCategories: List<CategoryItem> = customCategories.filter { !defaultCategoryNames.contains(it) }
            .map { categoryName: String ->
                CategoryItem(
                    name = categoryName,
                    emoji = getCategoryEmoji(categoryName),
                    color = getRandomCategoryColor(),
                    amount = 0.0,
                    transactionCount = 0,
                    lastTransaction = "No transactions yet",
                    percentage = 0,
                    progress = 0
                )
            }
        
        return (defaultSampleCategories + additionalCustomCategories)
    }
    
    private fun filterTransactionsByInclusionState(transactions: List<ProcessedTransaction>): List<ProcessedTransaction> {
        // Load inclusion states from SharedPreferences
        val prefs = requireContext().getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
        val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
        
        if (inclusionStatesJson != null) {
            try {
                val inclusionStates = org.json.JSONObject(inclusionStatesJson)
                return transactions.filter { transaction ->
                    val displayMerchant = transaction.displayMerchant
                    if (inclusionStates.has(displayMerchant)) {
                        inclusionStates.getBoolean(displayMerchant)
                    } else {
                        true // Default to included if not found
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("CategoriesFragment", "Error loading inclusion states", e)
            }
        }
        
        // Return all transactions if no inclusion states found
        return transactions
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class CategoryItem(
    val name: String,
    val emoji: String,
    val color: String,
    val amount: Double,
    val transactionCount: Int,
    val lastTransaction: String,
    val percentage: Int,
    val progress: Int
)