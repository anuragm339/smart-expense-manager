package com.smartexpenseai.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.smartexpenseai.app.R
import com.smartexpenseai.app.MainActivity
import com.smartexpenseai.app.databinding.FragmentBudgetGoalsBinding
import com.smartexpenseai.app.utils.logging.StructuredLogger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BudgetGoalsFragment : Fragment() {
    
    private var _binding: FragmentBudgetGoalsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BudgetGoalsViewModel by viewModels()
    private val logger = StructuredLogger("BudgetGoalsFragment", "BudgetGoalsFragment")

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

        setupClickListeners()
        setupObservers()

        // Load data
        viewModel.loadBudgetData()
    }
    
    private fun setupClickListeners() {
        binding.btnEditBudget.setOnClickListener {
            showEditMonthlyBudgetDialog()
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
    
    private fun updateUI(state: BudgetGoalsUiState) {
        if (state.isLoading) {
            // Show loading state if needed
        }
        
        state.error?.let { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
        
        // Update budget display
        binding.tvSpentAmount.text = "Spent: ₹${String.format("%.0f", state.currentSpent)}"
        binding.tvRemaining.text = "₹${String.format("%.0f", state.monthlyBudget - state.currentSpent)} remaining"
        binding.progressBudget.progress = state.budgetProgress
        binding.tvBudgetAmount.text = "Budget: ₹${String.format("%.0f", state.monthlyBudget)}"
        binding.tvBudgetPercentage.text = "${state.budgetProgress}% of budget used"
        
        // Update insights
        binding.tvBudgetStatus.text = state.insights.statusText
        binding.tvBudgetTip.text = state.insights.tipText
    }
    
    private fun handleEvent(event: BudgetGoalsEvent) {
        when (event) {
            is BudgetGoalsEvent.ShowMessage -> {
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
            }
            is BudgetGoalsEvent.ShowError -> {
                Toast.makeText(requireContext(), event.error, Toast.LENGTH_SHORT).show()
            }
            is BudgetGoalsEvent.ShowBudgetAlert -> {
                showBudgetAlert(event.budgetProgress, event.currentSpent, event.monthlyBudget)
            }
            is BudgetGoalsEvent.NavigateToCategories -> {
                navigateToBottomTab(R.id.navigation_categories)
            }
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
        val currentState = viewModel.uiState.value
        val testSpent = (currentState.monthlyBudget * progressPercentage / 100f)
        
        // Show test info (only for debug testing)
        Toast.makeText(requireContext(), "Testing ${progressPercentage}% scenario", Toast.LENGTH_SHORT).show()
        
        // Force show alert by calling the alert method directly
        showBudgetAlert(progressPercentage, testSpent, currentState.monthlyBudget)
    }
    
    private fun showCurrentBudgetValidation() {
        val validationDetails = viewModel.generateBudgetValidationDetails()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🧮 Budget Validation")
            .setMessage(validationDetails)
            .setPositiveButton("Recalculate") { _, _ ->
                viewModel.loadBudgetData()
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    // Removed: loadRealBudgetData() - now handled by ViewModel
    
    // Removed: loadBudgetDataFallback() - now handled by ViewModel
    
    // Removed: loadRealCategoryBudgets() - now handled by ViewModel
    
    // Removed: loadDefaultCategoryBudgetsWithRealSpending() - now handled by ViewModel
    
    // Removed: loadCategoryBudgets() - now handled by ViewModel
    
    // Removed: loadDefaultCategoryBudgets() - now handled by ViewModel
    
    // Removed: saveCategoryBudgets() - now handled by ViewModel
    
    // Removed: updateBudgetInsights() - now handled by ViewModel
    
    private fun showBudgetAlert(budgetProgress: Int, currentSpent: Float, monthlyBudget: Float) {
        if (budgetProgress >= 100) {
            // Over budget alert
            val overAmount = currentSpent - monthlyBudget
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("🚨 Budget Exceeded!")
                .setMessage("You've exceeded your monthly budget by ₹${String.format("%.0f", overAmount)}.\n\nWould you like to:\n• View spending breakdown\n• Set spending limits\n• Get AI recommendations")
                .setPositiveButton("View Breakdown") { _, _ ->
                    // Navigate to categories tab using bottom navigation
                    navigateToBottomTab(R.id.navigation_categories)
                }
                .setNeutralButton("AI Help") { _, _ ->
                    showAIBudgetRecommendations()
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
                    navigateToBottomTab(R.id.navigation_categories)
                }
                .setNegativeButton("OK", null)
                .show()
        }
    }
    
    private fun showAIBudgetRecommendations() {
        val recommendations = viewModel.generateAIRecommendations()
        
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
    
    // Removed: generateBasicRecommendations() - now handled by ViewModel
    
    private fun showEditMonthlyBudgetDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_budget, null)
        val budgetInput = dialogView.findViewById<TextInputEditText>(R.id.et_budget_amount)
        
        val currentBudget = viewModel.uiState.value.monthlyBudget
        budgetInput.setText(currentBudget.toInt().toString())
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Monthly Budget")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newBudget = budgetInput.text.toString().toFloatOrNull()
                if (newBudget != null && newBudget > 0) {
                    viewModel.updateMonthlyBudget(newBudget)
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid budget amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Helper method to navigate to bottom navigation tabs properly
     */
    private fun navigateToBottomTab(tabId: Int) {
        try {
            val mainActivity = activity as? MainActivity
            if (mainActivity != null) {
                // Bottom navigation removed for space - navigation disabled
                logger.debug("navigateToBottomTab","[SUCCESS] Successfully navigated to tab: $tabId")
            } else {
                logger.debug("navigateToBottomTab","MainActivity not available, using fallback navigation")
                findNavController().navigate(tabId)
            }
        } catch (e: Exception) {
            logger.error("navigateToBottomTab","Error navigating to tab $tabId, using fallback",e)
            try {
                findNavController().navigate(tabId)
            } catch (fallbackError: Exception) {
                logger.error("navigateToBottomTab","Fallback navigation also failed",fallbackError)
                Toast.makeText(requireContext(), "Navigation error. Please use bottom navigation.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh budget data when returning to this fragment
        viewModel.loadBudgetData()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Data class kept for ViewModel compatibility (not used in UI anymore)
data class CategoryBudgetItem(
    val categoryName: String,
    val budgetAmount: Float,
    val spentAmount: Float,
    val categoryColor: String
)