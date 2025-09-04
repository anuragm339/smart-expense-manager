package com.expensemanager.app.ui.messages

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.expensemanager.app.databinding.ItemMerchantGroupBinding
import java.text.SimpleDateFormat
import java.util.*

class GroupedMessagesAdapter(
    private val onTransactionClick: (MessageItem) -> Unit,
    private val onGroupToggle: (MerchantGroup, Boolean) -> Unit,
    private val onGroupEdit: (MerchantGroup) -> Unit
) : RecyclerView.Adapter<GroupedMessagesAdapter.MerchantGroupViewHolder>() {
    
    private var _merchantGroups = listOf<MerchantGroup>()
    val merchantGroups: List<MerchantGroup> get() = _merchantGroups
    
    fun submitList(groups: List<MerchantGroup>) {
        val oldList = _merchantGroups
        
        // Use post() to defer ALL updates until after current layout computation
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            _merchantGroups = groups
            
            if (oldList.isEmpty() && groups.isEmpty()) {
                return@post // No change needed
            }
            
            // Always use diff for safer updates
            updateWithDiff(oldList, groups)
        }
    }
    
    private fun updateWithDiff(oldList: List<MerchantGroup>, newList: List<MerchantGroup>) {
        try {
            val diffCallback = MerchantGroupDiffCallback(oldList, newList)
            val diffResult = DiffUtil.calculateDiff(diffCallback, false)
            
            // Make sure we're still on the main thread and data hasn't changed again
            if (android.os.Looper.getMainLooper() == android.os.Looper.myLooper() && 
                _merchantGroups == newList) {
                diffResult.dispatchUpdatesTo(this)
            }
        } catch (e: Exception) {
            // Fallback to notifyDataSetChanged with additional safety delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (_merchantGroups == newList) {
                        notifyDataSetChanged()
                    }
                } catch (ex: Exception) {
                    // Last resort - ignore the update
                }
            }, 150)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MerchantGroupViewHolder {
        val binding = ItemMerchantGroupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MerchantGroupViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: MerchantGroupViewHolder, position: Int) {
        // Safety check to prevent IndexOutOfBoundsException
        if (position < 0 || position >= _merchantGroups.size) {
            return
        }
        holder.bind(_merchantGroups[position])
    }
    
    override fun getItemCount(): Int = _merchantGroups.size
    
    inner class MerchantGroupViewHolder(
        private val binding: ItemMerchantGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private lateinit var transactionsAdapter: TransactionItemAdapter
        
        fun bind(group: MerchantGroup) {
            binding.apply {
                // Set merchant info
                tvMerchantName.text = group.merchantName
                tvTransactionCount.text = "${group.transactions.size} transactions"
                tvCategory.text = group.category
                
                // Set amount with visual indication of inclusion status
                val amountText = "₹${String.format("%.0f", group.totalAmount)}"
                tvTotalAmount.text = amountText
                tvTotalAmount.alpha = if (group.isIncludedInCalculations) 1.0f else 0.5f
                
                // Set category color
                try {
                    viewCategoryColor.setBackgroundColor(Color.parseColor(group.categoryColor))
                } catch (e: Exception) {
                    // Fallback to default color
                }
                
                // Show date range
                if (group.transactions.isNotEmpty()) {
                    val sortedTransactions = group.transactions.sortedByDescending { getTransactionTimestamp(it) }
                    val latestDate = sortedTransactions.first().dateTime
                    val oldestDate = sortedTransactions.last().dateTime
                    
                    tvDateRange.text = if (sortedTransactions.size == 1) {
                        latestDate
                    } else {
                        "$oldestDate - $latestDate"
                    }
                }
                
                // Setup include/exclude switch with crash prevention
                switchIncludeGroup.setOnCheckedChangeListener(null) // Clear previous listener
                switchIncludeGroup.isChecked = group.isIncludedInCalculations
                switchIncludeGroup.setOnCheckedChangeListener { _, isChecked ->
                    try {
                        
                        // Update UI immediately for responsive feedback
                        group.isIncludedInCalculations = isChecked
                        tvTotalAmount.alpha = if (isChecked) 1.0f else 0.5f
                        root.alpha = if (isChecked) 1.0f else 0.7f
                        
                        // Disable switch temporarily to prevent rapid toggling
                        switchIncludeGroup.isEnabled = false
                        
                        // Post the callback to avoid RecyclerView layout computation conflicts
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            try {
                                onGroupToggle(group, isChecked)
                                // Re-enable switch after successful callback
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    switchIncludeGroup.isEnabled = true
                                }, 500)
                            } catch (callbackError: Exception) {
                                android.util.Log.e("GroupedMessagesAdapter", "Toggle callback error", callbackError)
                                // Revert state on callback error
                                group.isIncludedInCalculations = !isChecked
                                switchIncludeGroup.isChecked = !isChecked
                                tvTotalAmount.alpha = if (!isChecked) 1.0f else 0.5f
                                root.alpha = if (!isChecked) 1.0f else 0.7f
                                switchIncludeGroup.isEnabled = true
                                
                                android.widget.Toast.makeText(
                                    binding.root.context,
                                    "Failed to update exclusion settings. Please try again.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        
                    } catch (e: Exception) {
                        android.util.Log.e("GroupedMessagesAdapter", "Toggle switch error", e)
                        switchIncludeGroup.isEnabled = true
                        android.widget.Toast.makeText(
                            binding.root.context,
                            "Critical error updating settings. Please restart the app.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
                // Update card appearance based on inclusion state
                root.alpha = if (group.isIncludedInCalculations) 1.0f else 0.7f
                
                // Setup expand/collapse with safe state handling
                ivExpandCollapse.rotation = if (group.isExpanded) 180f else 0f
                recyclerTransactions.visibility = if (group.isExpanded) View.VISIBLE else View.GONE
                
                // Setup click listener for expand/collapse with debouncing
                var lastClickTime = 0L
                ivExpandCollapse.setOnClickListener {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < 300) {
                        return@setOnClickListener // Debounce rapid clicks
                    }
                    lastClickTime = currentTime
                    
                    try {
                        group.isExpanded = !group.isExpanded
                        ivExpandCollapse.animate()
                            .rotation(if (group.isExpanded) 180f else 0f)
                            .setDuration(200)
                            .start()
                        
                        if (group.isExpanded) {
                            setupTransactionsRecyclerView(group)
                            recyclerTransactions.visibility = View.VISIBLE
                        } else {
                            recyclerTransactions.visibility = View.GONE
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GroupedMessagesAdapter", "Error expanding/collapsing group", e)
                    }
                }
                
                // Setup long click listener for editing group
                root.setOnLongClickListener {
                    onGroupEdit(group)
                    true
                }
                
                // Setup transactions list if expanded
                if (group.isExpanded) {
                    setupTransactionsRecyclerView(group)
                }
            }
        }
        
        private fun setupTransactionsRecyclerView(group: MerchantGroup) {
            transactionsAdapter = TransactionItemAdapter(onTransactionClick)
            binding.recyclerTransactions.apply {
                adapter = transactionsAdapter
                layoutManager = LinearLayoutManager(context)
                
                // Enable nested scrolling for proper touch handling
                isNestedScrollingEnabled = true
                
                // Ensure the RecyclerView can be scrolled
                setHasFixedSize(false)
                
                // Sort transactions by date descending (newest first)
                val sortedTransactions = group.transactions.sortedByDescending { 
                    getTransactionTimestamp(it) 
                }
                transactionsAdapter.submitList(sortedTransactions)
                
                // Adjust height based on number of transactions
                val layoutParams = this.layoutParams
                val itemHeight = 60 // Each transaction item is about 60dp
                val maxHeight = 200 // Maximum height in dp
                val minHeight = 80  // Minimum height in dp
                
                val calculatedHeight = (sortedTransactions.size * itemHeight).coerceAtMost(maxHeight).coerceAtLeast(minHeight)
                val density = context.resources.displayMetrics.density
                layoutParams.height = (calculatedHeight * density).toInt()
                this.layoutParams = layoutParams
                
                // Fix touch handling for nested scrolling
                addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                        // Allow the inner RecyclerView to handle vertical scrolling
                        val action = e.action
                        when (action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                rv.parent.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                        return false
                    }
                    
                    override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {}
                    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
                })
                
            }
        }
        
        private fun getTransactionTimestamp(transaction: MessageItem): Long {
            // Convert dateTime string to timestamp for proper sorting
            return when {
                transaction.dateTime.contains("Just now") -> System.currentTimeMillis()
                transaction.dateTime.contains("hour") -> {
                    val hours = transaction.dateTime.split(" ")[0].toIntOrNull() ?: 0
                    System.currentTimeMillis() - (hours * 60 * 60 * 1000L)
                }
                transaction.dateTime.contains("Yesterday") -> System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
                transaction.dateTime.contains("days ago") -> {
                    val days = transaction.dateTime.split(" ")[0].toIntOrNull() ?: 0
                    System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
                }
                else -> 0L // Very old
            }
        }
    }
}

class TransactionItemAdapter(
    private val onTransactionClick: (MessageItem) -> Unit
) : RecyclerView.Adapter<TransactionItemAdapter.TransactionViewHolder>() {
    
    private var transactions = listOf<MessageItem>()
    
    fun submitList(items: List<MessageItem>) {
        val oldSize = transactions.size
        transactions = items
        
        // Use more granular updates for better performance
        when {
            oldSize == 0 && items.isNotEmpty() -> notifyItemRangeInserted(0, items.size)
            oldSize > 0 && items.isEmpty() -> notifyItemRangeRemoved(0, oldSize)
            oldSize != items.size -> notifyDataSetChanged() // Size changed
            else -> notifyItemRangeChanged(0, items.size) // Content may have changed
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            com.expensemanager.app.R.layout.item_transaction_simple, parent, false
        )
        return TransactionViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }
    
    override fun getItemCount(): Int = transactions.size
    
    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        fun bind(transaction: MessageItem) {
            val amountBankText = itemView.findViewById<android.widget.TextView>(com.expensemanager.app.R.id.tv_amount_bank)
            val dateConfidenceText = itemView.findViewById<android.widget.TextView>(com.expensemanager.app.R.id.tv_date_confidence)
            
            // Add debit/credit indicator
            val transactionType = if (transaction.isDebit) "DEBIT" else "CREDIT"
            val typeIndicator = if (transaction.isDebit) "−" else "+"
            
            amountBankText.text = "$typeIndicator₹${String.format("%.0f", transaction.amount)} • ${transaction.bankName} • $transactionType"
            dateConfidenceText.text = "${transaction.dateTime} • ${transaction.confidence}% confidence"
            
            // Color coding for debit/credit
            if (transaction.isDebit) {
                amountBankText.setTextColor(itemView.context.getColor(com.expensemanager.app.R.color.debit_red))
            } else {
                amountBankText.setTextColor(itemView.context.getColor(com.expensemanager.app.R.color.credit_green))
            }
            
            itemView.setOnClickListener {
                try {
                    onTransactionClick(transaction)
                } catch (e: Exception) {
                    android.util.Log.e("GroupedMessagesAdapter", "Error handling transaction click", e)
                }
            }
        }
    }
}

/**
 * DiffUtil callback for efficient RecyclerView updates
 */
private class MerchantGroupDiffCallback(
    private val oldList: List<MerchantGroup>,
    private val newList: List<MerchantGroup>
) : DiffUtil.Callback() {
    
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size
    
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].merchantName == newList[newItemPosition].merchantName
    }
    
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        return oldItem.merchantName == newItem.merchantName &&
                oldItem.totalAmount == newItem.totalAmount &&
                oldItem.category == newItem.category &&
                oldItem.categoryColor == newItem.categoryColor &&
                oldItem.isIncludedInCalculations == newItem.isIncludedInCalculations &&
                oldItem.isExpanded == newItem.isExpanded &&
                oldItem.transactions.size == newItem.transactions.size
    }
}