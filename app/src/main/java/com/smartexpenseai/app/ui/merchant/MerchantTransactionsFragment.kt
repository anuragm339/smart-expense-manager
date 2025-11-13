package com.smartexpenseai.app.ui.merchant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartexpenseai.app.R
import com.smartexpenseai.app.databinding.FragmentMerchantTransactionsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class MerchantTransactionsFragment : Fragment() {
    
    private var _binding: FragmentMerchantTransactionsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MerchantTransactionsViewModel by viewModels()
    private lateinit var transactionsAdapter: MerchantTransactionsAdapter
    
    private var merchantName: String = ""
    
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
        val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
        
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        setupObservers()
        
        // Load data
        viewModel.loadMerchantTransactions(merchantName)
        viewModel.loadInclusionState(merchantName)
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
                putString("merchant", merchantName)
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
            viewModel.updateInclusionState(merchantName, isChecked)
        }
        
        binding.btnBulkActions.setOnClickListener {
            showBulkActionsDialog()
        }
    }
    
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { event ->
                event?.let {
                    handleEvent(it)
                    viewModel.clearEvent()
                }
            }
        }
    }
    
    private fun updateUI(state: MerchantTransactionsUiState) {
        if (state.isLoading) {
            // Show loading state if needed
        }
        
        state.error?.let { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
        
        // Update totals
        binding.tvTotalAmount.text = "₹${String.format("%.0f", state.totalAmount)}"
        binding.tvTransactionCount.text = "${state.totalCount} transactions"
        
        // Update adapter
        transactionsAdapter.submitList(state.transactions)
        
        // Show empty state if no transactions
        if (state.transactions.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerTransactions.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerTransactions.visibility = View.VISIBLE
        }
        
        // Update inclusion switch and status
        binding.switchIncludeInExpense.isChecked = state.isIncludedInExpense
        updateInclusionUI(state.isIncludedInExpense)
    }
    
    private fun handleEvent(event: MerchantTransactionsEvent) {
        when (event) {
            is MerchantTransactionsEvent.ShowMessage -> {
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
            }
            is MerchantTransactionsEvent.ShowError -> {
                Toast.makeText(requireContext(), event.error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Removed: loadInclusionState() - now handled by ViewModel
    
    // Removed: updateInclusionState() - now handled by ViewModel
    
    private fun updateInclusionUI(isIncluded: Boolean) {
        if (isIncluded) {
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