package com.expensemanager.app.ui.dashboard

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
        binding.rowMerchant1.setOnClickListener { handleMerchantClick(binding.tvMerchant1Name.text.toString()) }
        binding.rowMerchant2.setOnClickListener { handleMerchantClick(binding.tvMerchant2Name.text.toString()) }
        binding.rowMerchant3.setOnClickListener { handleMerchantClick(binding.tvMerchant3Name.text.toString()) }
        binding.rowMerchant4.setOnClickListener { handleMerchantClick(binding.tvMerchant4Name.text.toString()) }
    }

    fun render(merchants: List<MerchantSpending>, minimumCount: Int = DEFAULT_MINIMUM_COUNT) {
        val visibleMerchants = merchants.take(minimumCount)
        logger.debug(
            "TopMerchantsSection.render",
            "Rendering top merchants section",
            "Visible=${visibleMerchants.size}, Source=${merchants.size}"
        )

        val merchantViews = listOf(
            MerchantRowViews(
                binding.tvMerchant1Emoji,
                binding.tvMerchant1Name,
                binding.tvMerchant1Category,
                binding.tvMerchant1Amount,
                binding.tvMerchant1Count,
                binding.rowMerchant1
            ),
            MerchantRowViews(
                binding.tvMerchant2Emoji,
                binding.tvMerchant2Name,
                binding.tvMerchant2Category,
                binding.tvMerchant2Amount,
                binding.tvMerchant2Count,
                binding.rowMerchant2
            ),
            MerchantRowViews(
                binding.tvMerchant3Emoji,
                binding.tvMerchant3Name,
                binding.tvMerchant3Category,
                binding.tvMerchant3Amount,
                binding.tvMerchant3Count,
                binding.rowMerchant3
            ),
            MerchantRowViews(
                binding.tvMerchant4Emoji,
                binding.tvMerchant4Name,
                binding.tvMerchant4Category,
                binding.tvMerchant4Amount,
                binding.tvMerchant4Count,
                binding.rowMerchant4
            )
        )

        merchantViews.forEachIndexed { index, views ->
            val merchant = visibleMerchants.getOrNull(index)
            views.bind(merchant)
        }
    }

    private fun handleMerchantClick(merchantName: String) {
        if (merchantName.isBlank()) return
        onMerchantSelected(merchantName)
    }

    private fun MerchantRowViews.bind(merchant: MerchantSpending?) {
        if (merchant == null) {
            container.visibility = android.view.View.GONE
            return
        }

        container.visibility = android.view.View.VISIBLE
        emoji.text = merchant.categoryEmoji()
        name.text = merchant.merchantName
        category.text = merchant.category
        amount.text = "₹${String.format(Locale.getDefault(), "%.0f", merchant.totalAmount)}"
        count.text = "${merchant.transactionCount} tx"
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

    private data class MerchantRowViews(
        val emoji: android.widget.TextView,
        val name: android.widget.TextView,
        val category: android.widget.TextView,
        val amount: android.widget.TextView,
        val count: android.widget.TextView,
        val container: android.view.View
    )

    companion object {
        private const val DEFAULT_MINIMUM_COUNT = 3
    }
}
