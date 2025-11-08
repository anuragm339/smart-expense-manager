package com.smartexpenseai.app.ui.categories

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smartexpenseai.app.R
import com.smartexpenseai.app.databinding.DialogSmartCategorySelectionBinding
import com.smartexpenseai.app.services.CategorySuggestion
import com.smartexpenseai.app.services.SmartCategorizationService
import com.smartexpenseai.app.utils.CategoryManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Enhanced category selection dialog with smart suggestions and search functionality
 */
@AndroidEntryPoint
class SmartCategorySelectionDialog : DialogFragment() {
    
    private var _binding: DialogSmartCategorySelectionBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var smartCategorizationService: SmartCategorizationService
    
    private lateinit var categoryManager: CategoryManager
    private lateinit var categoryAdapter: CategoryAdapter
    
    private var merchantName: String = ""
    private var allCategories: List<String> = emptyList()
    private var suggestions: List<CategorySuggestion> = emptyList()
    private var onCategorySelected: ((String) -> Unit)? = null
    
    companion object {
        private const val ARG_MERCHANT_NAME = "merchant_name"
        
        fun newInstance(merchantName: String): SmartCategorySelectionDialog {
            return SmartCategorySelectionDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_MERCHANT_NAME, merchantName)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        merchantName = arguments?.getString(ARG_MERCHANT_NAME) ?: ""
        val repository = com.smartexpenseai.app.data.repository.ExpenseRepository.getInstance(requireContext())
        categoryManager = CategoryManager(requireContext(), repository)
        
        // Load all available categories
        allCategories = getCategoryList()
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSmartCategorySelectionBinding.inflate(layoutInflater)
        
        setupUI()
        loadSmartSuggestions()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setTitle("Select Category for $merchantName")
            .setNegativeButton("Cancel", null)
            .create()
    }
    
    private fun setupUI() {
        // Setup search functionality
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterCategories(s.toString())
            }
        })
        
        // Setup RecyclerView
        categoryAdapter = CategoryAdapter { category ->
            onCategorySelected?.invoke(category)
            dismiss()
        }
        
        binding.categoriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = categoryAdapter
        }
        
        // Initial load
        categoryAdapter.updateCategories(allCategories)
    }
    
    private fun loadSmartSuggestions() {
        lifecycleScope.launch {
            try {
                binding.suggestionsProgressBar.visibility = View.VISIBLE
                binding.suggestionsChipGroup.visibility = View.GONE
                
                suggestions = smartCategorizationService.getCategorySuggestions(merchantName)
                
                binding.suggestionsProgressBar.visibility = View.GONE
                binding.suggestionsChipGroup.visibility = View.VISIBLE
                
                setupSuggestionChips(suggestions)
                
            } catch (e: Exception) {
                binding.suggestionsProgressBar.visibility = View.GONE
                binding.suggestionsLabel.text = "Unable to load suggestions"
            }
        }
    }
    
    private fun setupSuggestionChips(suggestions: List<CategorySuggestion>) {
        binding.suggestionsChipGroup.removeAllViews()
        
        if (suggestions.isEmpty()) {
            binding.suggestionsLabel.text = "No smart suggestions available"
            return
        }
        
        binding.suggestionsLabel.text = "Smart Suggestions"
        
        suggestions.forEach { suggestion ->
            val chip = Chip(requireContext()).apply {
                text = "${suggestion.categoryName} (${(suggestion.confidence * 100).toInt()}%)"
                isCheckable = false
                isClickable = true
                
                // Set chip style based on confidence
                when {
                    suggestion.confidence > 0.8f -> {
                        setChipBackgroundColorResource(R.color.suggestion_high_confidence)
                        setTextColor(resources.getColor(android.R.color.white, null))
                    }
                    suggestion.confidence > 0.5f -> {
                        setChipBackgroundColorResource(R.color.suggestion_medium_confidence)
                    }
                    else -> {
                        setChipBackgroundColorResource(R.color.suggestion_low_confidence)
                    }
                }
                
                setOnClickListener {
                    onCategorySelected?.invoke(suggestion.categoryName)
                    dismiss()
                }
            }
            
            binding.suggestionsChipGroup.addView(chip)
        }
    }
    
    private fun filterCategories(query: String) {
        val filtered = if (query.isEmpty()) {
            allCategories
        } else {
            allCategories.filter { category ->
                category.contains(query, ignoreCase = true)
            }
        }
        
        categoryAdapter.updateCategories(filtered)
    }
    
    private fun getCategoryList(): List<String> {
        // Get categories from CategoryManager or provide defaults
        return listOf(
            "🍽️ Food & Dining",
            "🚗 Transportation", 
            "🛒 Groceries",
            "🏥 Healthcare",
            "🎬 Entertainment",
            "🛍️ Shopping",
            "⚡ Utilities",
            "💳 Bills",
            "✈️ Travel",
            "📚 Education",
            "🛡️ Insurance",
            "📂 Other"
        )
    }
    
    fun setOnCategorySelectedListener(listener: (String) -> Unit) {
        onCategorySelected = listener
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * Adapter for category list
     */
    inner class CategoryAdapter(
        private val onCategoryClick: (String) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {
        
        private var categories = listOf<String>()
        
        fun updateCategories(newCategories: List<String>) {
            categories = newCategories
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return CategoryViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
            holder.bind(categories[position])
        }
        
        override fun getItemCount() = categories.size
        
        inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textView: TextView = itemView.findViewById(android.R.id.text1)
            
            fun bind(category: String) {
                textView.text = category
                textView.setOnClickListener {
                    onCategoryClick(category)
                }
            }
        }
    }
}