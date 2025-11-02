package com.smartexpenseai.app.ui.merchant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartexpenseai.app.R
import com.smartexpenseai.app.data.entities.TransactionEntity
import java.text.SimpleDateFormat
import java.util.*

class MerchantTransactionsAdapter(
    private val onTransactionClick: (TransactionEntity) -> Unit
) : RecyclerView.Adapter<MerchantTransactionsAdapter.TransactionViewHolder>() {
    
    private var transactions = listOf<TransactionEntity>()
    
    fun submitList(newTransactions: List<TransactionEntity>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_transaction_simple, parent, false
        )
        return TransactionViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }
    
    override fun getItemCount(): Int = transactions.size
    
    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val tvAmountBank = itemView.findViewById<TextView>(R.id.tv_amount_bank)
        private val tvDateConfidence = itemView.findViewById<TextView>(R.id.tv_date_confidence)
        
        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        
        fun bind(transaction: TransactionEntity) {
            // Format amount and bank
            tvAmountBank.text = "₹${String.format("%.0f", transaction.amount)} • ${transaction.bankName}"
            
            // Format date and confidence  
            val formattedDate = dateFormat.format(transaction.transactionDate)
            val confidencePercent = (transaction.confidenceScore * 100).toInt()
            tvDateConfidence.text = "$formattedDate • $confidencePercent% confidence"
            
            // Set confidence color for the entire date/confidence text
            when {
                transaction.confidenceScore >= 0.8 -> {
                    tvDateConfidence.setTextColor(itemView.context.getColor(R.color.success_green))
                }
                transaction.confidenceScore >= 0.6 -> {
                    tvDateConfidence.setTextColor(itemView.context.getColor(R.color.warning_orange))
                }
                else -> {
                    tvDateConfidence.setTextColor(itemView.context.getColor(R.color.error_red))
                }
            }
            
            itemView.setOnClickListener {
                onTransactionClick(transaction)
            }
        }
    }
}