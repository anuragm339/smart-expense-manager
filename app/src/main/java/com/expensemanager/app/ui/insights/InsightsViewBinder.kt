package com.expensemanager.app.ui.insights

import android.view.View
import android.widget.TextView
import android.view.ViewGroup
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentInsightsBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.button.MaterialButton

/**
 * Handles view binding responsibilities for the Insights screen.
 * Keeps the fragment focused on orchestration while this class manages
 * loading states, shimmer animations, and shared UI controls.
 */
class InsightsViewBinder(
    private val binding: FragmentInsightsBinding
) {

    private val swipeRefreshLayout: SwipeRefreshLayout = binding.swipeRefreshLayout
    private val shimmerLayout: ViewGroup = binding.layoutShimmerLoading.root
    private val errorLayout: View = binding.layoutErrorState.root
    private val emptyLayout: View = binding.layoutEmptyState.root
    private val contentLayout: View = binding.layoutContent

    init {
        showLoadingState()
    }

    fun bindSwipeRefresh(onRefresh: () -> Unit) {
        swipeRefreshLayout.setOnRefreshListener(onRefresh)
    }

    fun bindErrorActions(onRetry: () -> Unit, onUseSample: () -> Unit) {
        errorLayout.findViewById<MaterialButton>(R.id.btnRetry)?.setOnClickListener { onRetry() }
        errorLayout.findViewById<MaterialButton>(R.id.btnUseSampleData)?.setOnClickListener { onUseSample() }
    }

    fun bindEmptyState(onNavigateToMessages: () -> Unit) {
        emptyLayout.findViewById<MaterialButton>(R.id.btnGoToMessages)?.setOnClickListener { onNavigateToMessages() }
    }

    fun updateSwipeRefreshing(isRefreshing: Boolean) {
        if (swipeRefreshLayout.isRefreshing != isRefreshing) {
            swipeRefreshLayout.isRefreshing = isRefreshing
        }
    }

    fun showLoadingState() {
        shimmerLayout.visibility = View.VISIBLE
        startAllShimmerAnimations(shimmerLayout)

        errorLayout.visibility = View.GONE
        emptyLayout.visibility = View.GONE
        contentLayout.visibility = View.GONE
    }

    fun showContentState() {
        stopAllShimmerAnimations(shimmerLayout)
        shimmerLayout.visibility = View.GONE

        errorLayout.visibility = View.GONE
        emptyLayout.visibility = View.GONE
        contentLayout.visibility = View.VISIBLE
    }

    fun showErrorState(message: String?) {
        stopAllShimmerAnimations(shimmerLayout)
        shimmerLayout.visibility = View.GONE

        errorLayout.visibility = View.VISIBLE
        emptyLayout.visibility = View.GONE
        contentLayout.visibility = View.GONE

        errorLayout.findViewById<TextView>(R.id.tvErrorMessage)?.text =
            message ?: binding.root.context.getString(R.string.error_generic)
    }

    fun showEmptyState() {
        stopAllShimmerAnimations(shimmerLayout)
        shimmerLayout.visibility = View.GONE

        errorLayout.visibility = View.GONE
        emptyLayout.visibility = View.VISIBLE
        contentLayout.visibility = View.GONE
    }

    fun stopShimmer() {
        stopAllShimmerAnimations(shimmerLayout)
    }

    fun restartShimmerIfVisible() {
        if (shimmerLayout.visibility == View.VISIBLE) {
            startAllShimmerAnimations(shimmerLayout)
        }
    }

    fun isContentVisible(): Boolean = contentLayout.visibility == View.VISIBLE

    private fun startAllShimmerAnimations(parent: View) {
        if (parent is ShimmerFrameLayout) {
            if (!parent.isShimmerStarted) {
                parent.startShimmer()
            }
        }
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                startAllShimmerAnimations(parent.getChildAt(i))
            }
        }
    }

    private fun stopAllShimmerAnimations(parent: View) {
        if (parent is ShimmerFrameLayout) {
            if (parent.isShimmerStarted) {
                parent.stopShimmer()
            }
        }
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                stopAllShimmerAnimations(parent.getChildAt(i))
            }
        }
    }
}
