package com.expensemanager.app.ui.categories

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.expensemanager.app.databinding.ItemCategoryBinding

class CategoriesAdapter(
    private val onCategoryClick: (CategoryItem) -> Unit = {},
    private val onCategoryLongClick: (CategoryItem, android.view.View) -> Unit = { _, _ -> },
    private val onViewMerchantsClick: (CategoryItem) -> Unit = {}
) : ListAdapter<CategoryItem, CategoriesAdapter.CategoryViewHolder>(CategoryDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position), onCategoryClick, onCategoryLongClick, onViewMerchantsClick)
    }
    
    class CategoryViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: CategoryItem, onCategoryClick: (CategoryItem) -> Unit, onCategoryLongClick: (CategoryItem, android.view.View) -> Unit, onViewMerchantsClick: (CategoryItem) -> Unit) {
            with(binding) {
                tvCategoryName.text = item.name
                tvCategoryEmoji.text = item.emoji
                tvCategoryAmount.text = "₹${String.format("%.0f", item.amount)}"
                tvTransactionCount.text = "${item.transactionCount} transactions"
                tvLastTransaction.text = item.lastTransaction
                tvPercentage.text = "${item.percentage}%"
                progressSpending.progress = item.progress

                // Set category color
                try {
                    val color = Color.parseColor(item.color)
                    viewCategoryIcon.setBackgroundColor(color)
                    progressSpending.progressTintList = android.content.res.ColorStateList.valueOf(color)
                } catch (e: Exception) {
                    // Fallback to default color if parsing fails
                }

                // Set click listener for category item
                root.setOnClickListener {
                    onCategoryClick(item)
                }

                // Set long click listener for delete/rename actions
                root.setOnLongClickListener {
                    onCategoryLongClick(item, root)
                    true
                }

                // Set click listener for View Merchants button
                btnViewMerchants.setOnClickListener {
                    onViewMerchantsClick(item)
                }
            }
        }
    }
}

class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryItem>() {
    override fun areItemsTheSame(oldItem: CategoryItem, newItem: CategoryItem): Boolean {
        return oldItem.name == newItem.name
    }
    
    override fun areContentsTheSame(oldItem: CategoryItem, newItem: CategoryItem): Boolean {
        return oldItem == newItem
    }
}