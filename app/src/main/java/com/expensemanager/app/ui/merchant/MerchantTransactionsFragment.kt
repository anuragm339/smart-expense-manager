package com.expensemanager.app.ui.merchant

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentMerchantTransactionsBinding
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.data.entities.TransactionEntity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MerchantTransactionsFragment : Fragment() {
    
    private var _binding: FragmentMerchantTransactionsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var repository: ExpenseRepository
    private lateinit var transactionsAdapter: MerchantTransactionsAdapter
    
    private var merchantName: String = ""
    private var isIncludedInExpense: Boolean = true
    
    companion object {
        private const val TAG = "MerchantTransactions"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            merchantName = it.getString("merchantName", "")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMerchantTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        repository = ExpenseRepository.getInstance(requireContext())
        
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        loadMerchantTransactions()
        loadInclusionState()
    }
    
    private fun setupUI() {
        binding.tvMerchantName.text = merchantName
        binding.toolbar.title = "Transactions"
    }
    
    private fun setupRecyclerView() {
        transactionsAdapter = MerchantTransactionsAdapter { transaction ->
            // Navigate to transaction details
            val bundle = Bundle().apply {
                putFloat("amount", transaction.amount.toFloat())
                putString("merchant", transaction.rawMerchant)
                putString("bankName", transaction.bankName)
                putString("category", "Other") // Will be populated properly
                putString("dateTime", SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(transaction.transactionDate))
                putInt("confidence", (transaction.confidenceScore * 100).toInt())
                putString("rawSMS", transaction.rawSmsBody)
            }
            findNavController().navigate(
                R.id.action_merchant_transactions_to_transaction_details,
                bundle
            )
        }
        
        binding.recyclerTransactions.apply {
            adapter = transactionsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.switchIncludeInExpense.setOnCheckedChangeListener { _, isChecked ->
            updateInclusionState(isChecked)
        }
        
        binding.btnBulkActions.setOnClickListener {
            showBulkActionsDialog()
        }
    }
    
    private fun loadMerchantTransactions() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading transactions for merchant: $merchantName")
                
                // Get all transactions for this merchant
                val transactions = repository.searchTransactions(merchantName.lowercase(), 1000)
                    .filter { it.normalizedMerchant.contains(merchantName.lowercase()) || 
                             it.rawMerchant.contains(merchantName, ignoreCase = true) }
                
                Log.d(TAG, "Found ${transactions.size} transactions for $merchantName")
                
                // Calculate totals
                val totalAmount = transactions.sumOf { it.amount }
                val totalCount = transactions.size
                
                // Update UI
                binding.tvTotalAmount.text = "₹${String.format("%.0f", totalAmount)}"
                binding.tvTransactionCount.text = "$totalCount transactions"
                
                // Update adapter
                transactionsAdapter.submitList(transactions.sortedByDescending { it.transactionDate })
                
                // Show empty state if no transactions
                if (transactions.isEmpty()) {
                    binding.layoutEmptyState.visibility = View.VISIBLE
                    binding.recyclerTransactions.visibility = View.GONE
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.recyclerTransactions.visibility = View.VISIBLE
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading merchant transactions", e)
                Toast.makeText(requireContext(), "Error loading transactions", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadInclusionState() {
        val prefs = requireContext().getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
        val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
        
        Log.d(TAG, "🔍 Loading inclusion state for '$merchantName'")
        Log.d(TAG, "🔍 Inclusion states JSON: $inclusionStatesJson")
        
        isIncludedInExpense = true // Default to included
        
        if (inclusionStatesJson != null) {
            try {
                val inclusionStates = org.json.JSONObject(inclusionStatesJson)
                
                // Debug: Log all keys in the inclusion states
                val keys = mutableListOf<String>()
                inclusionStates.keys().forEach { key -> keys.add(key) }
                Log.d(TAG, "🔍 All keys in preferences: $keys")
                
                if (inclusionStates.has(merchantName)) {
                    isIncludedInExpense = inclusionStates.getBoolean(merchantName)
                    Log.d(TAG, "🔍 Found '$merchantName' in preferences: $isIncludedInExpense")
                } else {
                    Log.d(TAG, "🔍 '$merchantName' NOT found in preferences, defaulting to included")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error loading inclusion state", e)
            }
        } else {
            Log.d(TAG, "🔍 No inclusion states JSON found, defaulting to included")
        }
        
        binding.switchIncludeInExpense.isChecked = isIncludedInExpense
        updateInclusionUI()
    }
    
    private fun updateInclusionState(isIncluded: Boolean) {
        isIncludedInExpense = isIncluded
        
        // Save to SharedPreferences
        val prefs = requireContext().getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)
        val inclusionStatesJson = prefs.getString("group_inclusion_states", null)
        
        try {
            val inclusionStates = if (inclusionStatesJson != null) {
                org.json.JSONObject(inclusionStatesJson)
            } else {
                org.json.JSONObject()
            }
            
            inclusionStates.put(merchantName, isIncluded)
            
            prefs.edit()
                .putString("group_inclusion_states", inclusionStates.toString())
                .apply()
            
            Log.d(TAG, "🔍 Updated inclusion state for '$merchantName': $isIncluded")
            Log.d(TAG, "🔍 Full inclusion states JSON: ${inclusionStates.toString()}")
            
            // Debug: Log all keys in the inclusion states
            val keys = mutableListOf<String>()
            inclusionStates.keys().forEach { key -> keys.add(key) }
            Log.d(TAG, "🔍 All merchant keys in preferences: $keys")
            
            updateInclusionUI()
            
            Toast.makeText(
                requireContext(),
                if (isIncluded) "✅ $merchantName included in expense calculations"
                else "❌ $merchantName excluded from expense calculations",
                Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating inclusion state", e)
            Toast.makeText(requireContext(), "Error updating inclusion state", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateInclusionUI() {
        if (isIncludedInExpense) {
            binding.tvInclusionStatus.text = "✅ Included in expense calculations"
            binding.tvInclusionStatus.setTextColor(resources.getColor(R.color.success_green, null))
        } else {
            binding.tvInclusionStatus.text = "❌ Excluded from expense calculations"
            binding.tvInclusionStatus.setTextColor(resources.getColor(R.color.error_red, null))
        }
    }
    
    private fun showBulkActionsDialog() {
        val actions = arrayOf(
            "Mark all as Food & Dining",
            "Mark all as Transportation", 
            "Mark all as Shopping",
            "Mark all as Other",
            "Export transactions",
            "Delete all transactions"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bulk Actions")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> Toast.makeText(requireContext(), "Category update coming soon", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(requireContext(), "Category update coming soon", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(requireContext(), "Category update coming soon", Toast.LENGTH_SHORT).show()
                    3 -> Toast.makeText(requireContext(), "Category update coming soon", Toast.LENGTH_SHORT).show()
                    4 -> Toast.makeText(requireContext(), "Export coming soon", Toast.LENGTH_SHORT).show()
                    5 -> showDeleteConfirmation()
                }
            }
            .show()
    }
    
    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete All Transactions")
            .setMessage("Are you sure you want to delete all transactions for $merchantName? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                Toast.makeText(requireContext(), "Delete functionality coming soon", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}