package com.expensemanager.app.ui.dashboard

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.expensemanager.app.R
import com.expensemanager.app.utils.logging.StructuredLogger

data class CategorySpending(
    val categoryName: String,
    val amount: Double,
    val categoryColor: String,
    val count: Int = 0
)

class TopCategoriesAdapter(
    private val onCategoryClick: (CategorySpending) -> Unit = {}
) : RecyclerView.Adapter<TopCategoriesAdapter.CategoryViewHolder>() {
    
    private var categories = listOf<CategorySpending>()
    private val logger = StructuredLogger("TOPCATEGORIESADAPTER", "TopCategoriesAdapter")
    fun submitList(newCategories: List<CategorySpending>) {
        logger.debug("submitList","submitList called with ${newCategories.size} categories")
        categories = newCategories
        notifyDataSetChanged()
        logger.debug("submitList","notifyDataSetChanged called, itemCount: $itemCount")
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        logger.debug("onCreateViewHolder","onCreateViewHolder called")
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_dashboard_category, parent, false
        )
        return CategoryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        logger.debug("onBindViewHolder","onBindViewHolder called for position $position: ${category.categoryName} = ₹${String.format("%.0f", category.amount)}")
        holder.bind(category)
    }
    
    override fun getItemCount(): Int = categories.size
    
    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
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
            
            // Set click listener
            itemView.setOnClickListener {
                onCategoryClick(category)
            }
            
            // Debug logging for view dimensions
            itemView.post {
                logger.debug("categoryviewholder","ViewHolder bound - itemView dimensions: ${itemView.width}x${itemView.height}, visibility: ${itemView.visibility}")
                logger.debug("categoryviewholder","Parent dimensions: ${(itemView.parent as? android.view.View)?.width}x${(itemView.parent as? android.view.View)?.height}")
            }
        }
    }
}
