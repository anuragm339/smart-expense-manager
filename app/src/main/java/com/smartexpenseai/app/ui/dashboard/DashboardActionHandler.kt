package com.smartexpenseai.app.ui.dashboard

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.smartexpenseai.app.R
import com.smartexpenseai.app.databinding.FragmentDashboardBinding
import com.smartexpenseai.app.domain.usecase.transaction.AddTransactionUseCase
import com.smartexpenseai.app.utils.logging.StructuredLogger
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles all user action interactions for DashboardFragment.
 * Manages click listeners, dialogs, and navigation.
 */
class DashboardActionHandler(
    private val fragment: Fragment,
    private val binding: FragmentDashboardBinding,
    private val viewModel: DashboardViewModel,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val logger: StructuredLogger,
    private val onDataRefreshNeeded: () -> Unit
) {

    private val context: Context get() = fragment.requireContext()

    /**
     * Set up all click listeners - call once during initialization
     */
    fun setupClickListeners() {
        binding.cardAiInsights.setOnClickListener {
            fragment.findNavController().navigate(R.id.navigation_insights)
        }

        binding.btnAddExpense.setOnClickListener {
            showQuickAddExpenseDialog()
        }

        binding.btnSyncSms.setOnClickListener {
            showRescanModeDialog()
        }

        binding.btnViewBudget.setOnClickListener {
            fragment.findNavController().navigate(R.id.navigation_budget_goals)
        }

        binding.btnExportData.setOnClickListener {
            fragment.findNavController().navigate(R.id.navigation_export_data)
        }

        binding.btnViewInsights.setOnClickListener {
            fragment.findNavController().navigate(R.id.navigation_insights)
        }

        binding.layoutTransactionCount.setOnClickListener {
            fragment.findNavController().navigate(R.id.navigation_messages)
        }

        binding.btnSettings.setOnClickListener {
            fragment.findNavController().navigate(R.id.navigation_settings)
        }
    }

    /**
     * Show dialog for adding a manual expense
     */
    fun showQuickAddExpenseDialog() {
        val dialogView = LayoutInflater.from(context).inflate(
            R.layout.dialog_quick_add_expense,
            null
        )

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Quick Add Expense")
            .setView(dialogView)
            .create()

        // Set up category dropdown
        val categorySpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_category)
        val categories = arrayOf(
            "Food & Dining", "Transportation", "Healthcare", "Groceries",
            "Entertainment", "Shopping", "Utilities", "Other"
        )
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, categories)
        categorySpinner.setAdapter(adapter)

        // Cancel button
        dialogView.findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        // Add button
        dialogView.findViewById<MaterialButton>(R.id.btn_add).setOnClickListener {
            handleQuickAddExpense(dialogView, dialog)
        }

        dialog.show()
    }

    /**
     * Show dialog to select rescan mode
     */
    fun showRescanModeDialog() {
        val options = arrayOf(
            "Incremental Rescan",
            "Clean Full Rescan"
        )

        val descriptions = arrayOf(
            "Only scan SMS that don't have transactions yet (recommended)",
            "Delete all existing transactions and rescan everything from scratch"
        )

        MaterialAlertDialogBuilder(context)
            .setTitle("Select Rescan Mode")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Incremental rescan (existing behavior)
                        logger.debug("showRescanModeDialog", "User selected: Incremental Rescan")
                        viewModel.handleEvent(DashboardUIEvent.IncrementalRescan)
                    }
                    1 -> {
                        // Clean full rescan - confirm first
                        showCleanRescanConfirmation()
                    }
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show confirmation dialog before clean full rescan
     */
    private fun showCleanRescanConfirmation() {
        MaterialAlertDialogBuilder(context)
            .setTitle("Clean Full Rescan")
            .setMessage("This will DELETE ALL existing transactions and rescan all SMS messages from scratch.\n\nThis action cannot be undone. Are you sure?")
            .setPositiveButton("Delete & Rescan") { _, _ ->
                logger.warn("showCleanRescanConfirmation", "User confirmed: Clean Full Rescan")
                viewModel.handleEvent(DashboardUIEvent.CleanFullRescan)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show custom month picker dialog for comparison
     */
    fun showCustomMonthPickerDialog() {
        val scrollView = androidx.core.widget.NestedScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        // First Month Section
        layout.addView(TextView(context).apply {
            text = "First Month:"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        })

        val firstMonthSpinner = androidx.appcompat.widget.AppCompatSpinner(context).apply {
            id = android.view.View.generateViewId()
        }
        populateMonthSpinner(firstMonthSpinner)
        layout.addView(firstMonthSpinner)

        // Spacer
        layout.addView(android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                32
            )
        })

        // Second Month Section
        layout.addView(TextView(context).apply {
            text = "Second Month (to compare with):"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        })

        val secondMonthSpinner = androidx.appcompat.widget.AppCompatSpinner(context).apply {
            id = android.view.View.generateViewId()
        }
        populateMonthSpinner(secondMonthSpinner)
        layout.addView(secondMonthSpinner)

        scrollView.addView(layout)

        MaterialAlertDialogBuilder(context)
            .setTitle("Select Two Months to Compare")
            .setView(scrollView)
            .setPositiveButton("Compare") { _, _ ->
                handleMonthComparison(firstMonthSpinner, secondMonthSpinner)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Handle quick add expense form submission
     */
    private fun handleQuickAddExpense(dialogView: android.view.View, dialog: androidx.appcompat.app.AlertDialog) {
        val amountText = dialogView.findViewById<TextInputEditText>(R.id.et_amount).text.toString().trim()
        val merchant = dialogView.findViewById<TextInputEditText>(R.id.et_merchant).text.toString().trim()
        val category = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_category).text.toString().trim()

        if (amountText.isEmpty() || merchant.isEmpty() || category.isEmpty()) {
            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        fragment.lifecycleScope.launch {
            try {
                logger.debug("handleQuickAddExpense", "Saving manual transaction: ₹$amount at $merchant ($category)")

                val manualTransaction = addTransactionUseCase.createManualTransaction(
                    amount = amount,
                    merchantName = merchant,
                    categoryName = category,
                    bankName = "Manual Entry"
                )

                val result = addTransactionUseCase.execute(manualTransaction)

                if (result.isSuccess) {
                    Toast.makeText(
                        context,
                        "Added: ₹$amount at $merchant ($category)",
                        Toast.LENGTH_LONG
                    ).show()

                    dialog.dismiss()
                    onDataRefreshNeeded()

                    // Broadcast to other screens
                    val intent = Intent("com.expensemanager.NEW_TRANSACTION_ADDED").apply {
                        putExtra("merchant", merchant)
                        putExtra("amount", amount)
                        putExtra("category", category)
                        putExtra("source", "manual_entry")
                    }
                    context.sendBroadcast(intent)

                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    logger.error("handleQuickAddExpense", "Failed to save transaction: $error", result.exceptionOrNull())

                    val userMessage = when {
                        error.contains("duplicate", ignoreCase = true) -> "This transaction may already exist"
                        error.contains("validation", ignoreCase = true) -> "Please check that all fields are filled correctly"
                        error.contains("database", ignoreCase = true) -> "Database error - please try again"
                        else -> "Failed to save transaction: $error"
                    }

                    Toast.makeText(context, userMessage, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                logger.error("handleQuickAddExpense", "Error saving transaction", e)

                val userMessage = when (e) {
                    is SecurityException -> "Permission error - please restart the app"
                    is IllegalArgumentException -> "Invalid transaction data - please check your input"
                    else -> "Error saving transaction - please try again"
                }

                Toast.makeText(context, userMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Handle month comparison selection
     */
    private fun handleMonthComparison(
        firstSpinner: androidx.appcompat.widget.AppCompatSpinner,
        secondSpinner: androidx.appcompat.widget.AppCompatSpinner
    ) {
        val firstMonth = firstSpinner.selectedItem?.toString()
        val secondMonth = secondSpinner.selectedItem?.toString()

        if (firstMonth.isNullOrEmpty() || secondMonth.isNullOrEmpty()) {
            Toast.makeText(context, "Please select both months", Toast.LENGTH_SHORT).show()
            return
        }

        if (firstMonth == secondMonth) {
            Toast.makeText(context, "Please select different months", Toast.LENGTH_SHORT).show()
            return
        }

        logger.debug("handleMonthComparison", "Comparing: $firstMonth vs $secondMonth")

        // Parse the month strings to (month, year) pairs
        val firstMonthPair = parseMonthYear(firstMonth)
        val secondMonthPair = parseMonthYear(secondMonth)

        if (firstMonthPair == null || secondMonthPair == null) {
            android.widget.Toast.makeText(context, "Invalid month format", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Trigger custom month comparison in ViewModel
        viewModel.handleEvent(DashboardUIEvent.CustomMonthsSelected(firstMonthPair, secondMonthPair))
    }

    /**
     * Parse month/year string to pair (month, year)
     */
    private fun parseMonthYear(monthYearText: String): Pair<Int, Int>? {
        return try {
            val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val date = dateFormat.parse(monthYearText) ?: return null

            val calendar = Calendar.getInstance()
            calendar.time = date

            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)

            Pair(month, year)
        } catch (e: Exception) {
            logger.error("parseMonthYear", "Failed to parse month/year: $monthYearText", e)
            null
        }
    }

    /**
     * Populate month spinner with last 12 months
     */
    private fun populateMonthSpinner(spinner: androidx.appcompat.widget.AppCompatSpinner) {
        val months = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        for (i in 0..11) {
            if (i > 0) calendar.add(Calendar.MONTH, -1)
            months.add(dateFormat.format(calendar.time))
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

}
