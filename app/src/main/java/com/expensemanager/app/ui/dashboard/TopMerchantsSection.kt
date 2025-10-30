package com.expensemanager.app.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentDashboardBinding
import com.expensemanager.app.utils.logging.StructuredLogger
import java.util.Locale

/**
 * Handles rendering of the Top Merchants section on the dashboard.
 */
class TopMerchantsSection(
    private val binding: FragmentDashboardBinding,
    private val logger: StructuredLogger,
    private val onMerchantSelected: (String) -> Unit
) {

    fun initialize() {
        // Nothing to initialize for dynamic views
    }

    fun render(merchants: List<MerchantSpending>, minimumCount: Int = DEFAULT_MINIMUM_COUNT) {
        val visibleMerchants = merchants.take(minimumCount)
        logger.debug(
            "TopMerchantsSection.render",
            "Rendering top merchants section",
            "Visible=${visibleMerchants.size}, Source=${merchants.size}"
        )

        // Clear existing views
        binding.layoutTopMerchantsTable.removeAllViews()

        // Add merchant rows dynamically
        visibleMerchants.forEach { merchant ->
            val merchantRow = createMerchantRow(merchant)
            binding.layoutTopMerchantsTable.addView(merchantRow)
        }
    }

    private fun createMerchantRow(merchant: MerchantSpending): View {
        val inflater = LayoutInflater.from(binding.root.context)
        val rowView = inflater.inflate(R.layout.item_merchant_row, binding.layoutTopMerchantsTable, false)

        val emoji = rowView.findViewById<TextView>(R.id.tv_merchant_emoji)
        val name = rowView.findViewById<TextView>(R.id.tv_merchant_name)
        val category = rowView.findViewById<TextView>(R.id.tv_merchant_category)
        val amount = rowView.findViewById<TextView>(R.id.tv_merchant_amount)
        val count = rowView.findViewById<TextView>(R.id.tv_merchant_count)

        emoji.text = merchant.categoryEmoji()
        name.text = merchant.merchantName
        category.text = merchant.category
        amount.text = "₹${String.format(Locale.getDefault(), "%.0f", merchant.totalAmount)}"
        count.text = "${merchant.transactionCount} tx"

        rowView.setOnClickListener {
            handleMerchantClick(merchant.merchantName)
        }

        return rowView
    }

    private fun handleMerchantClick(merchantName: String) {
        if (merchantName.isBlank()) return
        onMerchantSelected(merchantName)
    }

    private fun MerchantSpending.categoryEmoji(): String {
        return when (category.lowercase()) {
            "food & dining", "food", "dining" -> "🍽️"
            "groceries", "grocery" -> "🛒"
            "transportation", "transport" -> "🚗"
            "shopping" -> "🛍️"
            "entertainment" -> "🎬"
            "healthcare", "health", "medical" -> "🏥"
            "utilities" -> "⚡"
            "education" -> "📚"
            "travel" -> "✈️"
            "bills" -> "💳"
            "insurance" -> "🛡️"
            "money", "finance" -> "💰"
            else -> "📂"
        }
    }

    companion object {
        private const val DEFAULT_MINIMUM_COUNT = 3
    }
}
