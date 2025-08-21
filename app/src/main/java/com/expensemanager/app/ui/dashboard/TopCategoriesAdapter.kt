package com.expensemanager.app.ui.dashboard

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.expensemanager.app.R

data class CategorySpending(
    val categoryName: String,
    val amount: Double,
    val categoryColor: String
)

class TopCategoriesAdapter : RecyclerView.Adapter<TopCategoriesAdapter.CategoryViewHolder>() {
    
    private var categories = listOf<CategorySpending>()
    
    fun submitList(newCategories: List<CategorySpending>) {
        android.util.Log.d("TopCategoriesAdapter", "submitList called with ${newCategories.size} categories")
        categories = newCategories
        notifyDataSetChanged()
        android.util.Log.d("TopCategoriesAdapter", "notifyDataSetChanged called, itemCount: $itemCount")
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        android.util.Log.d("TopCategoriesAdapter", "onCreateViewHolder called")
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_dashboard_category, parent, false
        )
        return CategoryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        android.util.Log.d("TopCategoriesAdapter", "onBindViewHolder called for position $position: ${category.categoryName} = ₹${String.format("%.0f", category.amount)}")
        holder.bind(category)
    }
    
    override fun getItemCount(): Int = categories.size
    
    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val tvCategoryName = itemView.findViewById<TextView>(R.id.tv_category_name)
        private val tvAmount = itemView.findViewById<TextView>(R.id.tv_amount)
        private val viewCategoryColor = itemView.findViewById<View>(R.id.view_category_color)
        
        fun bind(category: CategorySpending) {
            tvCategoryName.text = category.categoryName
            tvAmount.text = "₹${String.format("%.0f", category.amount)}"
            
            // Set category color
            try {
                viewCategoryColor.setBackgroundColor(Color.parseColor(category.categoryColor))
            } catch (e: Exception) {
                // Fallback to default color
                viewCategoryColor.setBackgroundColor(Color.parseColor("#9e9e9e"))
            }
            
            // Debug logging for view dimensions
            itemView.post {
                android.util.Log.d("TopCategoriesAdapter", "ViewHolder bound - itemView dimensions: ${itemView.width}x${itemView.height}, visibility: ${itemView.visibility}")
                android.util.Log.d("TopCategoriesAdapter", "Parent dimensions: ${(itemView.parent as? android.view.View)?.width}x${(itemView.parent as? android.view.View)?.height}")
            }
        }
    }
}