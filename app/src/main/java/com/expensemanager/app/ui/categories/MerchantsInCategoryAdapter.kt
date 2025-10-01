package com.expensemanager.app.ui.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.expensemanager.app.databinding.ItemMerchantInCategoryBinding

class MerchantsInCategoryAdapter(
    private val onMerchantClick: (MerchantInCategory) -> Unit,
    private val onChangeCategoryClick: (MerchantInCategory) -> Unit
) : ListAdapter<MerchantInCategory, MerchantsInCategoryAdapter.MerchantViewHolder>(MerchantDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MerchantViewHolder {
        val binding = ItemMerchantInCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MerchantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MerchantViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MerchantViewHolder(
        private val binding: ItemMerchantInCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(merchant: MerchantInCategory) {
            binding.apply {
                // Merchant info
                tvMerchantName.text = merchant.merchantName
                tvMerchantInitial.text = merchant.getInitial()
                tvTransactionCount.text = merchant.getTransactionCountText()
                tvLastTransaction.text = merchant.getLastTransactionText()
                tvMerchantAmount.text = merchant.getFormattedAmount()

                // Progress and percentage
                progressSpending.progress = merchant.percentage.toInt()
                tvPercentage.text = "${merchant.percentage.toInt()}%"

                // Click listeners
                root.setOnClickListener {
                    onMerchantClick(merchant)
                }

                btnChangeCategory.setOnClickListener {
                    onChangeCategoryClick(merchant)
                }
            }
        }
    }

    private class MerchantDiffCallback : DiffUtil.ItemCallback<MerchantInCategory>() {
        override fun areItemsTheSame(oldItem: MerchantInCategory, newItem: MerchantInCategory): Boolean {
            return oldItem.merchantName == newItem.merchantName
        }

        override fun areContentsTheSame(oldItem: MerchantInCategory, newItem: MerchantInCategory): Boolean {
            return oldItem == newItem
        }
    }
}