package com.expensemanager.app.ui.categories

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.expensemanager.app.databinding.FragmentCategoryTransactionsBinding
import com.expensemanager.app.ui.messages.MessageItem
import com.expensemanager.app.ui.messages.MessagesAdapter
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.SMSHistoryReader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CategoryTransactionsFragment : Fragment() {
    
    private var _binding: FragmentCategoryTransactionsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var categoryName: String
    private lateinit var transactionsAdapter: MessagesAdapter
    private lateinit var categoryManager: CategoryManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categoryName = arguments?.getString("categoryName") ?: "Unknown"
        categoryManager = CategoryManager(requireContext())
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        loadCategoryTransactions()
    }
    
    private fun setupUI() {
        binding.tvCategoryName.text = categoryName
        
        // Set category color
        val categoryColor = categoryManager.getCategoryColor(categoryName)
        try {
            binding.viewCategoryColor.setBackgroundColor(Color.parseColor(categoryColor))
        } catch (e: Exception) {
            // Fallback to default color
        }
    }
    
    private fun setupRecyclerView() {
        transactionsAdapter = MessagesAdapter { messageItem ->
            showCategoryEditDialog(messageItem)
        }
        binding.recyclerTransactions.apply {
            adapter = transactionsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun setupClickListeners() {
        binding.btnFilterDate.setOnClickListener {
            // TODO: Implement date filter
            showDateFilterDialog()
        }
        
        binding.btnSort.setOnClickListener {
            // TODO: Implement sort options
            showSortDialog()
        }
    }
    
    private fun loadCategoryTransactions() {
        lifecycleScope.launch {
            try {
                // Read historical SMS data
                val smsReader = SMSHistoryReader(requireContext())
                val allTransactions = smsReader.scanHistoricalSMS()
                
                // Filter transactions by category
                val categoryTransactions = allTransactions.mapNotNull { transaction ->
                    val transactionCategory = categoryManager.categorizeTransaction(transaction.merchant)
                    if (transactionCategory == categoryName) {
                        MessageItem(
                            amount = transaction.amount,
                            merchant = transaction.merchant,
                            bankName = transaction.bankName,
                            category = transactionCategory,
                            categoryColor = categoryManager.getCategoryColor(transactionCategory),
                            confidence = (transaction.confidence * 100).toInt(),
                            dateTime = formatDate(transaction.date),
                            rawSMS = transaction.rawSMS
                        )
                    } else null
                }
                
                // Sort real transactions only
                val allCategoryTransactions = categoryTransactions.sortedBy {
                    getDateSortOrder(it.dateTime)
                }
                
                if (allCategoryTransactions.isNotEmpty()) {
                    transactionsAdapter.submitList(allCategoryTransactions)
                    binding.recyclerTransactions.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                    
                    // Update summary
                    val totalAmount = allCategoryTransactions.sumOf { it.amount }
                    binding.tvCategoryTotal.text = "₹${String.format("%.0f", totalAmount)}"
                    binding.tvCategorySummary.text = "${allCategoryTransactions.size} transactions • This month"
                } else {
                    binding.recyclerTransactions.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.tvCategoryTotal.text = "₹0"
                    binding.tvCategorySummary.text = "0 transactions • This month"
                }
                
            } catch (e: Exception) {
                // Show empty state on error
                binding.recyclerTransactions.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.tvCategoryTotal.text = "₹0"
                binding.tvCategorySummary.text = "0 transactions • This month"
            }
        }
    }
    
    
    private fun showCategoryEditDialog(messageItem: MessageItem) {
        val categories = categoryManager.getAllCategories()
        val currentCategoryIndex = categories.indexOf(messageItem.category)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Category for ${messageItem.merchant}")
            .setSingleChoiceItems(
                categories.toTypedArray(),
                currentCategoryIndex
            ) { dialog, which ->
                val newCategory = categories[which]
                if (newCategory != messageItem.category) {
                    updateCategoryForMerchant(messageItem, newCategory)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun updateCategoryForMerchant(messageItem: MessageItem, newCategory: String) {
        lifecycleScope.launch {
            try {
                categoryManager.updateCategory(messageItem.merchant, newCategory)
                
                // Refresh the current list if the item no longer belongs to this category
                if (newCategory != categoryName) {
                    loadCategoryTransactions()
                } else {
                    // Update the item in the current list
                    val currentList = transactionsAdapter.currentList.toMutableList()
                    val updatedList = currentList.map { item ->
                        if (item.merchant == messageItem.merchant) {
                            item.copy(
                                category = newCategory,
                                categoryColor = categoryManager.getCategoryColor(newCategory)
                            )
                        } else item
                    }
                    transactionsAdapter.submitList(updatedList)
                }
                
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Error")
                    .setMessage("Failed to update category: ${e.message}")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }
    
    private fun showDateFilterDialog() {
        val options = arrayOf("Today", "Yesterday", "This Week", "This Month", "Last Month", "All Time")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Date")
            .setItems(options) { _, which ->
                binding.btnFilterDate.text = options[which]
                // TODO: Implement actual filtering
                loadCategoryTransactions()
            }
            .show()
    }
    
    private fun showSortDialog() {
        val options = arrayOf("Newest First", "Oldest First", "Highest Amount", "Lowest Amount")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort Transactions")
            .setItems(options) { _, which ->
                binding.btnSort.text = options[which]
                // TODO: Implement actual sorting
                loadCategoryTransactions()
            }
            .show()
    }
    
    private fun formatDate(date: Date): String {
        val now = Date()
        val diffInMs = now.time - date.time
        val diffInDays = diffInMs / (1000 * 60 * 60 * 24)
        val diffInHours = diffInMs / (1000 * 60 * 60)
        
        return when {
            diffInHours < 1 -> "Just now"
            diffInHours < 24 -> "$diffInHours hours ago"
            diffInDays == 1L -> "Yesterday"
            diffInDays < 7 -> "$diffInDays days ago"
            else -> {
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                dateFormat.format(date)
            }
        }
    }
    
    private fun getDateSortOrder(dateTimeString: String): Int {
        return when {
            dateTimeString.contains("hour") || dateTimeString.contains("Just now") -> {
                // Extract hours for sorting within same day
                val hours = if (dateTimeString.contains("hour")) {
                    dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                } else 0
                hours // Lower hours = more recent
            }
            dateTimeString.contains("Yesterday") -> 100
            dateTimeString.contains("days ago") -> {
                val days = dateTimeString.split(" ")[0].toIntOrNull() ?: 0
                1000 + days // Higher days = older
            }
            dateTimeString.contains("Aug") || dateTimeString.contains("Today") -> 10000 // Historical dates are oldest
            else -> 5000 // Default for other formats
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}