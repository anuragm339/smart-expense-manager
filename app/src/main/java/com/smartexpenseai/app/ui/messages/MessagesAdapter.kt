package com.smartexpenseai.app.ui.messages

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartexpenseai.app.databinding.ItemMessageBinding

class MessagesAdapter(
    private val onCategoryEditClick: (MessageItem) -> Unit = {}
) : ListAdapter<MessageItem, MessagesAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position), onCategoryEditClick)
    }
    
    class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: MessageItem, onCategoryEditClick: (MessageItem) -> Unit) {
            with(binding) {
                tvAmount.text = "₹${String.format("%.0f", item.amount)}"
                tvMerchant.text = item.merchant
                tvBank.text = item.bankName
                tvCategory.text = item.category
                tvConfidence.text = "${item.confidence}%"
                tvDateTime.text = item.dateTime
                tvRawSms.text = item.rawSMS
                
                // Set category color
                try {
                    viewCategoryColor.setBackgroundColor(Color.parseColor(item.categoryColor))
                } catch (e: Exception) {
                    // Fallback to default color if parsing fails
                }
                
                // Set confidence color based on value
                tvConfidence.setTextColor(
                    when {
                        item.confidence >= 90 -> Color.parseColor("#4caf50") // Green
                        item.confidence >= 70 -> Color.parseColor("#ff9800") // Orange
                        else -> Color.parseColor("#e53e3e") // Red
                    }
                )
                
                // Category edit click
                tvCategory.setOnClickListener {
                    onCategoryEditClick(item)
                }
                
                // Toggle SMS preview
                var isExpanded = false
                tvExpandCollapse.setOnClickListener {
                    isExpanded = !isExpanded
                    layoutSmsPreview.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    tvExpandCollapse.text = if (isExpanded) "Hide SMS ↑" else "View SMS ↓"
                }
            }
        }
    }
}

class MessageDiffCallback : DiffUtil.ItemCallback<MessageItem>() {
    override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
        return oldItem.rawSMS == newItem.rawSMS
    }
    
    override fun areContentsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
        return oldItem == newItem
    }
}