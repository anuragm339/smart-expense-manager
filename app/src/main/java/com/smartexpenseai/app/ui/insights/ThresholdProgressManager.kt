package com.smartexpenseai.app.ui.insights

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.smartexpenseai.app.R
import com.smartexpenseai.app.data.models.ThresholdProgress
import com.smartexpenseai.app.data.repository.EnhancedAIInsightsRepository
import com.smartexpenseai.app.data.repository.InsightsResult
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Helper class to manage threshold progress UI components
 * Displays smart indicators about AI insights status and refresh eligibility
 */
class ThresholdProgressManager(
    private val context: Context,
    private val repository: EnhancedAIInsightsRepository,
    private val lifecycleScope: CoroutineScope
) {

    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    /**
     * Setup threshold progress UI components
     */
    fun setupThresholdProgress(
        progressLayout: View,
        onRefreshClick: () -> Unit
    ) {
        val tvStatus = progressLayout.findViewById<TextView>(R.id.tv_threshold_status)
        val btnRefresh = progressLayout.findViewById<MaterialButton>(R.id.btn_refresh_now)
        val layoutProgress = progressLayout.findViewById<View>(R.id.layout_progress)
        val progressBar = progressLayout.findViewById<ProgressBar>(R.id.progress_threshold)
        val tvProgressPercentage = progressLayout.findViewById<TextView>(R.id.tv_progress_percentage)
        val layoutDetails = progressLayout.findViewById<View>(R.id.layout_threshold_details)
        val tvTransactionsNeeded = progressLayout.findViewById<TextView>(R.id.tv_transactions_needed)
        val tvAmountNeeded = progressLayout.findViewById<TextView>(R.id.tv_amount_needed)
        val layoutCostInfo = progressLayout.findViewById<View>(R.id.layout_cost_info)
        val tvCostSavings = progressLayout.findViewById<TextView>(R.id.tv_cost_savings)
        val tvLastUpdated = progressLayout.findViewById<TextView>(R.id.tv_last_updated)

        btnRefresh?.setOnClickListener { onRefreshClick() }

        // Load and display threshold progress
        lifecycleScope.launch {
            try {
                val progress = repository.getThresholdProgress()
                updateProgressUI(
                    progress = progress,
                    tvStatus = tvStatus,
                    layoutProgress = layoutProgress,
                    progressBar = progressBar,
                    tvProgressPercentage = tvProgressPercentage,
                    layoutDetails = layoutDetails,
                    tvTransactionsNeeded = tvTransactionsNeeded,
                    tvAmountNeeded = tvAmountNeeded,
                    layoutCostInfo = layoutCostInfo,
                    tvCostSavings = tvCostSavings,
                    tvLastUpdated = tvLastUpdated,
                    btnRefresh = btnRefresh
                )
            } catch (e: Exception) {
                // Fallback to basic status
                tvStatus?.text = "Status unavailable"
                btnRefresh?.isEnabled = true
            }
        }
    }

    /**
     * Update progress UI after insights result
     */
    fun updateAfterInsightsUpdate(
        progressLayout: View,
        result: InsightsResult
    ) {
        lifecycleScope.launch {
            val progress = repository.getThresholdProgress()
            updateProgressUIFromResult(progressLayout, result, progress)
        }
    }

    private fun updateProgressUI(
        progress: ThresholdProgress,
        tvStatus: TextView?,
        layoutProgress: View?,
        progressBar: ProgressBar?,
        tvProgressPercentage: TextView?,
        layoutDetails: View?,
        tvTransactionsNeeded: TextView?,
        tvAmountNeeded: TextView?,
        layoutCostInfo: View?,
        tvCostSavings: TextView?,
        tvLastUpdated: TextView?,
        btnRefresh: MaterialButton?
    ) {
        // Main status text
        val statusText = when {
            progress.isEligibleForRefresh -> "Ready for fresh insights"
            progress.daysUntilRefresh > 0 -> "Next update in ${progress.daysUntilRefresh} day${if (progress.daysUntilRefresh > 1) "s" else ""}"
            progress.transactionsNeeded > 0 -> "Add ${progress.transactionsNeeded} more transaction${if (progress.transactionsNeeded > 1) "s" else ""} for update"
            progress.amountNeeded > 0 -> "Spend ₹${progress.amountNeeded.roundToInt()} more for update"
            else -> "Insights are up to date"
        }
        tvStatus?.text = statusText

        // Progress bar (only show if not eligible)
        if (progress.isEligibleForRefresh) {
            layoutProgress?.visibility = View.GONE
        } else {
            layoutProgress?.visibility = View.VISIBLE
            progressBar?.progress = progress.progressPercentage
            tvProgressPercentage?.text = "${progress.progressPercentage}%"

            // Color coding based on progress
            val progressColor = when {
                progress.progressPercentage >= 80 -> ContextCompat.getColor(context, R.color.success)
                progress.progressPercentage >= 50 -> ContextCompat.getColor(context, R.color.warning)
                else -> ContextCompat.getColor(context, R.color.primary)
            }
            progressBar?.progressTintList = android.content.res.ColorStateList.valueOf(progressColor)
        }

        // Detailed information
        if (progress.transactionsNeeded > 0 || progress.amountNeeded > 0) {
            layoutDetails?.visibility = View.VISIBLE

            if (progress.transactionsNeeded > 0) {
                tvTransactionsNeeded?.text = "Need ${progress.transactionsNeeded} more transaction${if (progress.transactionsNeeded > 1) "s" else ""}"
                tvTransactionsNeeded?.visibility = View.VISIBLE
            } else {
                tvTransactionsNeeded?.visibility = View.GONE
            }

            if (progress.amountNeeded > 0) {
                tvAmountNeeded?.text = "or ₹${progress.amountNeeded.roundToInt()} more spending"
                tvAmountNeeded?.visibility = View.VISIBLE
            } else {
                tvAmountNeeded?.visibility = View.GONE
            }
        } else {
            layoutDetails?.visibility = View.GONE
        }

        // Cost awareness
        if (progress.estimatedCostSavings > 0) {
            layoutCostInfo?.visibility = View.VISIBLE
            tvCostSavings?.text = "Saving ~₹${String.format("%.2f", progress.estimatedCostSavings)} by using cached insights"
        } else {
            layoutCostInfo?.visibility = View.GONE
        }

        // Last updated
        if (progress.lastUpdateTime > 0) {
            val timeAgo = getTimeAgoString(progress.lastUpdateTime)
            tvLastUpdated?.text = "Last updated: $timeAgo"
        } else {
            tvLastUpdated?.text = "Never updated"
        }

        // Refresh button
        btnRefresh?.isEnabled = true
        btnRefresh?.text = if (progress.isEligibleForRefresh) "Refresh Now" else "Force Refresh"
    }

    private fun updateProgressUIFromResult(
        progressLayout: View,
        result: InsightsResult,
        progress: ThresholdProgress
    ) {
        val tvStatus = progressLayout.findViewById<TextView>(R.id.tv_threshold_status)
        val tvLastUpdated = progressLayout.findViewById<TextView>(R.id.tv_last_updated)
        val layoutCostInfo = progressLayout.findViewById<View>(R.id.layout_cost_info)
        val btnRefresh = progressLayout.findViewById<MaterialButton>(R.id.btn_refresh_now)

        when (result) {
            is InsightsResult.Success -> {
                val statusText = if (result.isCached) {
                    "Using cached insights (${result.source})"
                } else {
                    "Fresh insights generated"
                }
                tvStatus?.text = statusText

                val timeAgo = getTimeAgoString(result.lastUpdated)
                tvLastUpdated?.text = "Last updated: $timeAgo"

                // Show cost info for cached results
                if (result.isCached && progress.estimatedCostSavings > 0) {
                    layoutCostInfo?.visibility = View.VISIBLE
                } else {
                    layoutCostInfo?.visibility = View.GONE
                }

                btnRefresh?.isEnabled = true
            }

            is InsightsResult.Error -> {
                tvStatus?.text = "Error: ${result.message}"
                if (result.lastUpdated > 0) {
                    val timeAgo = getTimeAgoString(result.lastUpdated)
                    tvLastUpdated?.text = "Last successful update: $timeAgo"
                }
                btnRefresh?.isEnabled = true
            }

            is InsightsResult.Loading -> {
                tvStatus?.text = "Generating fresh insights..."
                btnRefresh?.isEnabled = false
            }
        }
    }

    private fun getTimeAgoString(timestamp: Long): String {
        if (timestamp == 0L) return "Unknown"

        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> dateFormat.format(Date(timestamp))
        }
    }

    /**
     * Show loading state
     */
    fun showLoading(progressLayout: View) {
        val tvStatus = progressLayout.findViewById<TextView>(R.id.tv_threshold_status)
        val btnRefresh = progressLayout.findViewById<MaterialButton>(R.id.btn_refresh_now)

        tvStatus?.text = "Generating insights..."
        btnRefresh?.isEnabled = false
    }

    /**
     * Show error state
     */
    fun showError(progressLayout: View, error: String) {
        val tvStatus = progressLayout.findViewById<TextView>(R.id.tv_threshold_status)
        val btnRefresh = progressLayout.findViewById<MaterialButton>(R.id.btn_refresh_now)

        tvStatus?.text = "Error: $error"
        btnRefresh?.isEnabled = true
    }

    /**
     * Show success state after manual refresh
     */
    fun showRefreshSuccess(progressLayout: View) {
        val tvStatus = progressLayout.findViewById<TextView>(R.id.tv_threshold_status)
        val tvLastUpdated = progressLayout.findViewById<TextView>(R.id.tv_last_updated)
        val btnRefresh = progressLayout.findViewById<MaterialButton>(R.id.btn_refresh_now)

        tvStatus?.text = "Fresh insights generated"
        tvLastUpdated?.text = "Last updated: Just now"
        btnRefresh?.isEnabled = true
    }
}