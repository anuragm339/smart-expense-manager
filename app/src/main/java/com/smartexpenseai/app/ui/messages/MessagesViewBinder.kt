package com.smartexpenseai.app.ui.messages

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartexpenseai.app.R
import com.smartexpenseai.app.databinding.FragmentMessagesBinding
import com.smartexpenseai.app.databinding.ShimmerMessagesLoadingBinding
import com.smartexpenseai.app.utils.logging.StructuredLogger
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText

/**
 * Handles view binding responsibilities for the Messages screen, keeping the fragment focused on
 * orchestration while adapters and summaries live here.
 */
class MessagesViewBinder(
    private val binding: FragmentMessagesBinding,
    context: Context,
    private val logger: StructuredLogger,
    onTransactionClick: (MessageItem) -> Unit,
    onGroupToggle: (MerchantGroup, Boolean) -> Unit,
    onGroupEdit: (MerchantGroup) -> Unit,
    private val onLoadMore: (() -> Unit)? = null
) {
    private val recyclerView = binding.recyclerMessages
    private val emptyLayout = binding.layoutEmpty
    private val permissionButton: MaterialButton = binding.btnGrantPermissions
    private val sortButton: MaterialButton = binding.root.findViewById(R.id.btn_sort)
    private val filterButton: MaterialButton = binding.root.findViewById(R.id.btn_filter)
    private val filterTabs: TabLayout = binding.root.findViewById(R.id.tab_layout_filter)
    private val searchInput: TextInputEditText = binding.etSearch
    private val shimmerBinding: ShimmerMessagesLoadingBinding = binding.layoutShimmerLoading
    private val shimmerLayout: ShimmerFrameLayout = shimmerBinding.root

    private val tvTotalMessages: TextView = binding.root.findViewById(R.id.tv_total_messages)
    private val tvAutoCategorized: TextView = binding.root.findViewById(R.id.tv_auto_categorized)
    private val tvUniqueMerchants: TextView = binding.root.findViewById(R.id.tv_unique_merchants)
    private val tvUniqueBanks: TextView = binding.root.findViewById(R.id.tv_unique_banks)
    private val tvAverageConfidence: TextView = binding.root.findViewById(R.id.tv_avg_confidence)

    private var currentFilterTab: TransactionFilterTab = TransactionFilterTab.ALL

    private var suppressTabCallback = false

    val groupedAdapter: GroupedMessagesAdapter = GroupedMessagesAdapter(
        onTransactionClick = onTransactionClick,
        onGroupToggle = onGroupToggle,
        onGroupEdit = onGroupEdit
    )

    init {
        recyclerView.apply {
            adapter = groupedAdapter
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            isDrawingCacheEnabled = true
            drawingCacheQuality = View.DRAWING_CACHE_QUALITY_HIGH
            itemAnimator?.changeDuration = 0
            itemAnimator?.moveDuration = 0

            // Add scroll listener for pagination
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                    if (layoutManager != null) {
                        val totalItemCount = layoutManager.itemCount
                        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                        // Trigger load when user scrolls near bottom (5 items before end)
                        if (totalItemCount > 0 && lastVisibleItem >= totalItemCount - 5) {
                            logger.debug("MessagesViewBinder", "Near bottom: lastVisible=$lastVisibleItem, total=$totalItemCount - triggering load more")
                            onLoadMore?.invoke()
                        }
                    }
                }
            })
        }

        groupedAdapter.submitList(emptyList())
        updateSummary(0, 0, 0, 0, 0)
        showLoadingState()
    }

    fun bindSearch(onQueryChanged: (String) -> Unit) {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                logger.debug("MessagesViewBinder", "Search query: '$query'")
                onQueryChanged(query)
            }
        })
    }

    fun bindFilterTabs(onTabSelected: (TransactionFilterTab) -> Unit) {
        filterTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (suppressTabCallback) {
                    return
                }
                val filterTab = TransactionFilterTab.fromIndex(tab?.position ?: 0)
                logger.debug("MessagesViewBinder", "Filter tab selected: ${filterTab.displayName}")
                setFilterTab(filterTab, selectTab = false)
                onTabSelected(filterTab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (suppressTabCallback) {
                    return
                }
                val filterTab = TransactionFilterTab.fromIndex(tab?.position ?: 0)
                setFilterTab(filterTab, selectTab = false)
                onTabSelected(filterTab)
            }
        })

        setFilterTab(currentFilterTab, selectTab = true)
    }

    fun bindFilterButtons(
        onSortClick: () -> Unit,
        onFilterClick: () -> Unit,
        onGrantPermissionClick: () -> Unit
    ) {
        sortButton.setOnClickListener {
            logger.debug("MessagesViewBinder", "Sort button clicked")
            onSortClick()
        }
        filterButton.setOnClickListener {
            logger.debug("MessagesViewBinder", "Filter button clicked")
            onFilterClick()
        }
        permissionButton.setOnClickListener {
            logger.debug("MessagesViewBinder", "Grant permissions clicked")
            onGrantPermissionClick()
        }
    }

    fun showPermissionState() {
        groupedAdapter.submitList(emptyList())
        recyclerView.visibility = View.GONE
        stopShimmer()
        shimmerLayout.visibility = View.GONE
        emptyLayout.visibility = View.VISIBLE
        updateSummary(0, 0, 0, 0, 0)
    }

    fun showLoadingState() {
        recyclerView.visibility = View.GONE
        emptyLayout.visibility = View.GONE
        shimmerLayout.visibility = View.VISIBLE
        startShimmer()
    }

    fun showEmptyState() {
        groupedAdapter.submitList(emptyList())
        recyclerView.visibility = View.GONE
        stopShimmer()
        shimmerLayout.visibility = View.GONE
        emptyLayout.visibility = View.VISIBLE
        updateSummary(0, 0, 0, 0, 0)
    }

    fun showContent() {
        recyclerView.visibility = View.VISIBLE
        emptyLayout.visibility = View.GONE
        stopShimmer()
        shimmerLayout.visibility = View.GONE
    }

    fun submitMerchantGroups(groups: List<MerchantGroup>) {
        logger.debug("MessagesViewBinder", "Submitting ${groups.size} merchant groups")
        if (recyclerView.isLaidOut) {
            submitGroupsInternal(groups)
        } else {
            recyclerView.post { submitGroupsInternal(groups) }
        }
    }

    private fun submitGroupsInternal(groups: List<MerchantGroup>) {
        groupedAdapter.submitList(groups)
        groupedAdapter.updateFilterTab(currentFilterTab)
        if (groups.isNotEmpty()) {
            showContent()
        }
    }

    fun updateSummary(
        totalMessages: Int,
        autoCategorized: Int,
        uniqueMerchants: Int,
        uniqueBanks: Int,
        averageConfidence: Int
    ) {
        tvTotalMessages.text = totalMessages.toString()
        tvAutoCategorized.text = autoCategorized.toString()
        tvUniqueMerchants.text = uniqueMerchants.toString()
        tvUniqueBanks.text = uniqueBanks.toString()
        tvAverageConfidence.text = averageConfidence.toString()
    }

    fun updateFilterTabSelection(currentFilterTab: TransactionFilterTab) {
        setFilterTab(currentFilterTab, selectTab = true)
    }

    fun updateSortLabel(label: String) {
        sortButton.text = label
    }

    fun updateFilterLabel(label: String) {
        filterButton.text = label
    }

    private fun setFilterTab(filterTab: TransactionFilterTab, selectTab: Boolean) {
        currentFilterTab = filterTab
        groupedAdapter.updateFilterTab(filterTab)

        if (selectTab) {
            val tab = filterTabs.getTabAt(filterTab.index)
            if (tab != null) {
                selectTabSafely(tab)
            } else {
                filterTabs.post {
                    filterTabs.getTabAt(filterTab.index)?.let { selectTabSafely(it) }
                }
            }
        }
    }

    private fun selectTabSafely(tab: TabLayout.Tab) {
        if (tab.isSelected) {
            return
        }
        suppressTabCallback = true
        tab.select()
        filterTabs.post { suppressTabCallback = false }
    }

    private fun startShimmer() {
        if (!shimmerLayout.isShimmerStarted) {
            shimmerLayout.startShimmer()
        }
    }

    private fun stopShimmer() {
        if (shimmerLayout.isShimmerStarted) {
            shimmerLayout.stopShimmer()
        }
    }
}
