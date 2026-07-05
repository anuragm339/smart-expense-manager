package com.smartexpenseai.app.ui.dashboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.smartexpenseai.app.R
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.smartexpenseai.app.data.dao.CategorySpendingResult
import com.smartexpenseai.app.data.dao.MerchantSpendingWithCategory
import com.smartexpenseai.app.databinding.FragmentDashboardBinding
import com.smartexpenseai.app.utils.MerchantAliasManager
import com.smartexpenseai.app.utils.logging.StructuredLogger
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
        binding.tvTotalBalance.text = "0%"
        binding.tvTotalSpent.text = "₹0"
        binding.tvTransactionCount.text = "0"
        topMerchantsSection.render(emptyList())
        updateTopCategories(emptyList())
        logger.debug("showEmpty", "Displaying empty state")
    }

    fun showContent(state: DashboardUIState) {
        val data = state.dashboardData ?: return
        val budgetReached = if (state.dashboardPeriod == "This Month" && state.monthlyBudget > 0) {
            // Calculate percentage of budget reached
            (state.totalSpent / state.monthlyBudget * 100)
        } else {
            // For other periods, show amount spent
            state.totalSpent
        }

        renderSummary(state.totalSpent, budgetReached, state.transactionCount, state.monthlyBudget > 0)
        renderCategoryDonut(data.topCategories)
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
        renderCategoryMovers(state.categoryMovers)
        renderTagSpending(state.tagSpending)
        renderTrackedTagMovers(
            state.trackedTagMovers,
            showCard = state.tagSpending.isNotEmpty() || state.trackedTagMovers.isNotEmpty()
        )
    }

    fun showSyncToast(count: Int) {
        Toast.makeText(context, "Synced $count new transactions from SMS!", Toast.LENGTH_LONG).show()
    }


    fun renderSummary(totalSpent: Double, budgetReached: Double, transactionCount: Int, hasBudget: Boolean) {
        binding.tvTotalSpent.text = "₹${totalSpent.formatAsMoney()}"
        binding.tvTotalBalance.text = if (hasBudget) {
            // Show percentage when budget exists
            "${budgetReached.toInt()}%"
        } else {
            // Show "No Budget Found" when no budget is set
            "No Budget Found"
        }
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

        // Share bars are proportional to the biggest visible category
        val maxAmount = items.take(4).maxOfOrNull { it.amount } ?: 0.0

        // Add category rows dynamically
        items.take(4).forEach { category ->
            val categoryRow = createCategoryRow(category, colorMap, maxAmount)
            binding.layoutTopCategoriesTable.addView(categoryRow)
        }
    }

    private fun createCategoryRow(
        category: CategorySpending,
        colorMap: Map<String, Int>,
        maxAmount: Double
    ): android.view.View {
        val inflater = android.view.LayoutInflater.from(context)
        val rowView = inflater.inflate(R.layout.item_category_row, binding.layoutTopCategoriesTable, false)

        val colorIndicator = rowView.findViewById<android.view.View>(R.id.category_color_indicator)
        val name = rowView.findViewById<android.widget.TextView>(R.id.tv_category_name)
        val amount = rowView.findViewById<android.widget.TextView>(R.id.tv_category_amount)
        val count = rowView.findViewById<android.widget.TextView>(R.id.tv_category_count)
        val shareBar = rowView.findViewById<android.widget.ProgressBar>(R.id.progress_category_share)

        val categoryColor = colorMap[category.categoryName] ?: color(R.color.category_other)
        colorIndicator.background?.setTint(categoryColor)
        name.text = category.categoryName
        amount.text = "₹${category.amount.formatAsMoney()}"
        count.text = "${category.count} transactions"

        shareBar.progressTintList = android.content.res.ColorStateList.valueOf(categoryColor)
        shareBar.progress = if (maxAmount > 0) {
            ((category.amount / maxAmount) * 100).toInt().coerceIn(2, 100)
        } else {
            0
        }

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


    /**
     * Donut of category spend for the period. Category is a clean partition of spend,
     * so slice-of-whole is meaningful; the hole shows the period total.
     */
    fun renderCategoryDonut(categories: List<CategorySpendingResult>) {
        val chart = binding.chartCategoryDonut
        val expenses = categories.filter { it.total_amount > 0 }
        if (expenses.isEmpty()) {
            chart.visibility = View.GONE
            return
        }
        chart.visibility = View.VISIBLE
        val total = expenses.sumOf { it.total_amount }
        val entries = expenses.map { PieEntry(it.total_amount.toFloat(), it.category_name) }
        val sliceColors = expenses.map {
            try { android.graphics.Color.parseColor(it.color) } catch (_: Exception) { color(R.color.category_other) }
        }
        val dataSet = PieDataSet(entries, "").apply {
            colors = sliceColors
            sliceSpace = 2f
            setDrawValues(false) // labels live in the list below; keep the donut clean
        }
        chart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            isDrawHoleEnabled = true
            holeRadius = 62f
            transparentCircleRadius = 66f
            setHoleColor(android.graphics.Color.TRANSPARENT)
            setCenterTextColor(color(R.color.text_primary))
            centerText = "₹${total.formatAsMoney()}"
            setCenterTextSize(16f)
            isRotationEnabled = false
            setTouchEnabled(false)
            animateY(500)
            invalidate()
        }
    }

    /**
     * Ranked spend-by-tag list. Bar width is relative to the top tag (not to total
     * spend), since tags overlap and don't partition the total.
     */
    fun renderTagSpending(items: List<com.smartexpenseai.app.data.dao.TagSpending>) {
        val card = binding.cardTagSpending
        val container = binding.layoutTagSpending
        container.removeAllViews()
        if (items.isEmpty()) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        val max = items.maxOf { it.totalAmount }.coerceAtLeast(1.0)
        val inflater = LayoutInflater.from(context)
        items.forEach { tag ->
            val row = inflater.inflate(R.layout.item_tag_spending, container, false)
            row.findViewById<TextView>(R.id.tv_tag_name).text = tag.name
            row.findViewById<TextView>(R.id.tv_tag_amount).text = "₹${tag.totalAmount.formatAsMoney()}"
            row.findViewById<TextView>(R.id.tv_tag_count).text =
                if (tag.transactionCount == 1) "1 transaction" else "${tag.transactionCount} transactions"

            val tagColor = try { android.graphics.Color.parseColor(tag.color) } catch (_: Exception) {
                color(R.color.category_other)
            }
            row.findViewById<View>(R.id.view_tag_color).background?.setTint(tagColor)

            val bar = row.findViewById<View>(R.id.view_tag_bar)
            val rest = row.findViewById<View>(R.id.view_tag_bar_rest)
            bar.setBackgroundColor(tagColor)
            val fraction = (tag.totalAmount / max).toFloat().coerceIn(0.02f, 1f)
            (bar.layoutParams as android.widget.LinearLayout.LayoutParams).weight = fraction
            (rest.layoutParams as android.widget.LinearLayout.LayoutParams).weight = 1f - fraction
            bar.requestLayout()

            container.addView(row)
        }
    }

    /** Month-over-month comparison for the user's tracked tags. */
    fun renderTrackedTagMovers(movers: List<CategoryMover>, showCard: Boolean) {
        val card = binding.cardTagTrends
        if (!showCard) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        val container = binding.layoutTrackedTags
        val empty = binding.tvTrackedTagsEmpty
        container.removeAllViews()
        if (movers.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE
        val inflater = LayoutInflater.from(context)
        movers.forEach { mover ->
            val row = inflater.inflate(R.layout.item_category_mover, container, false)
            row.findViewById<TextView>(R.id.tv_mover_name).text = mover.label
            row.findViewById<TextView>(R.id.tv_mover_current).text =
                "₹${mover.currentAmount.formatAsMoney()}"
            val delta = row.findViewById<TextView>(R.id.tv_mover_delta)
            delta.text = mover.deltaText
            delta.setTextColor(if (mover.isIncrease) color(R.color.error) else color(R.color.success))
            val dot = row.findViewById<View>(R.id.view_mover_color)
            try {
                dot.background?.setTint(android.graphics.Color.parseColor(mover.color))
            } catch (_: Exception) {
                dot.background?.setTint(color(R.color.text_secondary))
            }
            container.addView(row)
        }
    }

    /** Wire the "Edit" affordance on the Tag Trends card. */
    fun setOnEditTrackedTags(action: () -> Unit) {
        binding.tvEditTrackedTags.setOnClickListener { action() }
    }

    fun renderCategoryMovers(movers: List<CategoryMover>) {
        val container = binding.layoutCategoryMovers
        val header = binding.tvCategoryMoversHeader
        container.removeAllViews()
        if (movers.isEmpty()) {
            header.visibility = View.GONE
            container.visibility = View.GONE
            return
        }
        header.visibility = View.VISIBLE
        container.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(context)
        movers.forEach { mover ->
            val row = inflater.inflate(R.layout.item_category_mover, container, false)
            row.findViewById<TextView>(R.id.tv_mover_name).text = mover.label
            row.findViewById<TextView>(R.id.tv_mover_current).text =
                "₹${mover.currentAmount.formatAsMoney()}"
            val delta = row.findViewById<TextView>(R.id.tv_mover_delta)
            delta.text = mover.deltaText
            delta.setTextColor(if (mover.isIncrease) color(R.color.error) else color(R.color.success))
            val dot = row.findViewById<View>(R.id.view_mover_color)
            try {
                dot.background?.setTint(android.graphics.Color.parseColor(mover.color))
            } catch (_: Exception) {
                dot.background?.setTint(color(R.color.text_secondary))
            }
            container.addView(row)
        }
    }

    private fun Double.formatAsMoney(): String =
        String.format(Locale.getDefault(), "%.0f", this)

    private fun Double.toMoneyString(): String {
        val value = String.format(Locale.getDefault(), "%.0f", kotlin.math.abs(this))
        return if (this >= 0) "₹$value" else "-₹$value"
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)
}
