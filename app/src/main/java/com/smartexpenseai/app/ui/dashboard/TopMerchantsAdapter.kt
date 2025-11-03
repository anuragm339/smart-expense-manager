package com.smartexpenseai.app.ui.dashboard

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartexpenseai.app.R

data class MerchantSpending(
    val merchantName: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val category: String,
    val categoryColor: String,
    val percentage: Double
)

class TopMerchantsAdapter(
    private val onMerchantClick: (MerchantSpending) -> Unit = {}
) : RecyclerView.Adapter<TopMerchantsAdapter.MerchantViewHolder>() {
    
    private var merchants = listOf<MerchantSpending>()
    
    fun submitList(newMerchants: List<MerchantSpending>) {
        merchants = newMerchants
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MerchantViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_top_merchant, parent, false
        )
        return MerchantViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MerchantViewHolder, position: Int) {
        holder.bind(merchants[position])
    }
    
    override fun getItemCount(): Int = merchants.size
    
    inner class MerchantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val tvMerchantName = itemView.findViewById<TextView>(R.id.tv_merchant_name)
        private val tvAmount = itemView.findViewById<TextView>(R.id.tv_amount)
        private val tvTransactionCount = itemView.findViewById<TextView>(R.id.tv_transaction_count)
        private val tvCategory = itemView.findViewById<TextView>(R.id.tv_category)
        private val viewCategoryColor = itemView.findViewById<View>(R.id.view_category_color)
        private val tvPercentage = itemView.findViewById<TextView>(R.id.tv_percentage)
        
        fun bind(merchant: MerchantSpending) {
            tvMerchantName.text = merchant.merchantName
            tvAmount.text = "₹${String.format("%.0f", merchant.totalAmount)}"
            tvTransactionCount.text = "${merchant.transactionCount} transactions"
            tvCategory.text = merchant.category
            tvPercentage.text = "${String.format("%.1f", merchant.percentage)}%"
            
            // Set category color
            try {
                viewCategoryColor.setBackgroundColor(Color.parseColor(merchant.categoryColor))
            } catch (e: Exception) {
                // Fallback to default color
                viewCategoryColor.setBackgroundColor(Color.parseColor("#9e9e9e"))
            }
            
            // Set click listener
            itemView.setOnClickListener {
                onMerchantClick(merchant)
            }
        }
    }
}