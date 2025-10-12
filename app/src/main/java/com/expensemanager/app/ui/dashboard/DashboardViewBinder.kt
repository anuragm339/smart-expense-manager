package com.expensemanager.app.ui.dashboard

import android.content.Context
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.expensemanager.app.R
import com.expensemanager.app.data.dao.MerchantSpendingWithCategory
import com.expensemanager.app.databinding.FragmentDashboardBinding
import com.expensemanager.app.utils.MerchantAliasManager
import com.expensemanager.app.utils.logging.StructuredLogger
import com.expensemanager.app.data.repository.DashboardData
import java.util.Locale

/**
 * Handles all binding logic for [DashboardFragment].
 * Keeps view updates isolated from fragment lifecycle plumbing.
 */
class DashboardViewBinder(
    private val binding: FragmentDashboardBinding,
    private val context: Context,
    private val logger: StructuredLogger,
    private val merchantInclusionFilter: MerchantInclusionFilter,
    private val aliasManager: MerchantAliasManager,
    private val onMerchantSelected: (String) -> Unit,
    private val onCategorySelected: (String) -> Unit
) {

    private val topMerchantsSection = TopMerchantsSection(binding, logger, onMerchantSelected)

    fun initialize() {
        topMerchantsSection.initialize()
    }

    fun showLoading() {
        binding.tvTotalBalance.text = "Loading..."
        binding.tvTotalSpent.text = "Loading..."
        binding.tvTransactionCount.text = "0"
        logger.debug("showLoading", "Displaying loading state")
    }

    fun showError(message: String?) {
        binding.tvTotalBalance.text = "Error"
        binding.tvTotalSpent.text = "₹0"
        binding.tvTransactionCount.text = "0"
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        logger.warn("showError", "Dashboard error", message)
    }

    fun showEmpty() {
        binding.tvTotalBalance.text = "₹0"
        binding.tvTotalSpent.text = "₹0"
        binding.tvTransactionCount.text = "0"
        topMerchantsSection.render(emptyList())
        updateTopCategories(emptyList())
        logger.debug("showEmpty", "Displaying empty state")
    }

    fun showContent(state: DashboardUIState) {
        val data = state.dashboardData ?: return
        val balance = if (state.dashboardPeriod == "This Month" && state.hasSalaryData) {
            state.monthlyBalance
        } else {
            state.totalBalance
        }

        renderSummary(state.totalSpent, balance, state.transactionCount)
        renderCategories(data.topCategories.map { category ->
            CategorySpending(
                categoryName = category.category_name,
                amount = category.total_amount,
                categoryColor = category.color,
                count = category.transaction_count
            )
        })
        renderMerchantsWithCategory(data.topMerchantsWithCategory)
        renderMonthlyComparison(state.monthlyComparison)
    }

    fun showSyncToast(count: Int) {
        Toast.makeText(context, "Synced $count new transactions from SMS!", Toast.LENGTH_LONG).show()
    }


    fun renderSummary(totalSpent: Double, balance: Double, transactionCount: Int) {
        binding.tvTotalSpent.text = "₹${totalSpent.formatAsMoney()}"
        binding.tvTotalBalance.text = balance.toMoneyString()
        binding.tvTransactionCount.text = transactionCount.toString()
    }

    fun renderCategories(items: List<CategorySpending>) = updateTopCategories(items)

    fun renderMerchantsWithCategory(merchants: List<MerchantSpendingWithCategory>) =
        updateTopMerchants(merchants)

    fun renderMonthlyComparison(comparison: MonthlyComparison?) =
        updateMonthlyComparison(comparison)

    private fun updateTopMerchants(merchants: List<MerchantSpendingWithCategory>) {
        val total = merchants.sumOf { it.total_amount }
        val displayMerchants = merchantInclusionFilter.apply(
            merchants.map { merchant ->
                val displayName = aliasManager.getDisplayName(merchant.normalized_merchant)
                MerchantSpending(
                    merchantName = displayName,
                    totalAmount = merchant.total_amount,
                    transactionCount = merchant.transaction_count,
                    category = merchant.category_name,
                    categoryColor = merchant.category_color,
                    percentage = if (total > 0) (merchant.total_amount / total) * 100 else 0.0
                )
            }
        )
        logger.debug(
            "updateTopMerchants",
            "Rendering top merchants",
            "Source=${merchants.size}, Visible=${displayMerchants.size}"
        )
        topMerchantsSection.render(displayMerchants)
    }

    private fun updateTopCategories(items: List<CategorySpending>) {
        val colorMap = mapOf(
            "Food & Dining" to color(R.color.category_food),
            "Transport" to color(R.color.category_transport),
            "Shopping" to color(R.color.category_shopping),
            "Groceries" to color(R.color.category_groceries),
            "Entertainment" to color(R.color.category_entertainment),
            "Healthcare" to color(R.color.category_healthcare),
            "Utilities" to color(R.color.category_utilities),
            "Other" to color(R.color.category_other)
        )

        // Clear existing views
        binding.layoutTopCategoriesTable.removeAllViews()

        // Add category rows dynamically
        items.take(4).forEach { category ->
            val categoryRow = createCategoryRow(category, colorMap)
            binding.layoutTopCategoriesTable.addView(categoryRow)
        }
    }

    private fun createCategoryRow(category: CategorySpending, colorMap: Map<String, Int>): android.view.View {
        val inflater = android.view.LayoutInflater.from(context)
        val rowView = inflater.inflate(R.layout.item_category_row, binding.layoutTopCategoriesTable, false)

        val colorIndicator = rowView.findViewById<android.view.View>(R.id.category_color_indicator)
        val name = rowView.findViewById<android.widget.TextView>(R.id.tv_category_name)
        val amount = rowView.findViewById<android.widget.TextView>(R.id.tv_category_amount)
        val count = rowView.findViewById<android.widget.TextView>(R.id.tv_category_count)

        colorIndicator.setBackgroundColor(colorMap[category.categoryName] ?: color(R.color.category_other))
        name.text = category.categoryName
        amount.text = "₹${category.amount.formatAsMoney()}"
        count.text = "${category.count} transactions"

        rowView.setOnClickListener {
            onCategorySelected(category.categoryName)
        }

        return rowView
    }

    private fun updateMonthlyComparison(comparison: MonthlyComparison?) {
        if (comparison == null) return

        val thisMonthView = binding.root.findViewById<TextView>(R.id.tv_this_month_amount)
        val lastMonthView = binding.root.findViewById<TextView>(R.id.tv_last_month_amount)
        val comparisonView = binding.root.findViewById<TextView>(R.id.tv_spending_comparison)

        thisMonthView?.text = "₹${comparison.currentAmount.formatAsMoney()}"
        lastMonthView?.text = "₹${comparison.previousAmount.formatAsMoney()}"
        comparisonView?.text = comparison.changeText
        comparisonView?.setTextColor(
            when {
                comparison.hasIncrease -> color(R.color.error)
                comparison.hasDecrease -> color(R.color.success)
                else -> color(R.color.text_secondary)
            }
        )
    }


    private fun Double.formatAsMoney(): String =
        String.format(Locale.getDefault(), "%.0f", this)

    private fun Double.toMoneyString(): String {
        val value = String.format(Locale.getDefault(), "%.0f", kotlin.math.abs(this))
        return if (this >= 0) "₹$value" else "-₹$value"
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)
}
