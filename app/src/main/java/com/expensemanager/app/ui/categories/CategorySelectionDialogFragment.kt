package com.expensemanager.app.ui.categories

import android.app.Dialog
import android.os.Bundle
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.expensemanager.app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

class CategorySelectionDialogFragment : DialogFragment() {
    
    companion object {
        private const val TAG = "CategorySelectionDialog"
        private const val ARG_CATEGORIES = "categories"
        private const val ARG_CURRENT_INDEX = "current_index"
        private const val ARG_MERCHANT_NAME = "merchant_name"
        
        fun newInstance(
            categories: Array<String>,
            currentIndex: Int,
            merchantName: String,
            onCategorySelected: (String) -> Unit
        ): CategorySelectionDialogFragment {
            return CategorySelectionDialogFragment().apply {
                arguments = Bundle().apply {
                    putStringArray(ARG_CATEGORIES, categories)
                    putInt(ARG_CURRENT_INDEX, currentIndex)
                    putString(ARG_MERCHANT_NAME, merchantName)
                }
                this.onCategorySelected = onCategorySelected
            }
        }
    }
    
    private var onCategorySelected: ((String) -> Unit)? = null
    private var selectedIndex = -1
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.tag(TAG).d("[FIX] Creating custom dialog view")
        
        val view = inflater.inflate(R.layout.dialog_category_selection_custom, container, false)
        
        val categories = arguments?.getStringArray(ARG_CATEGORIES) ?: arrayOf()
        val currentIndex = arguments?.getInt(ARG_CURRENT_INDEX, -1) ?: -1
        val merchantName = arguments?.getString(ARG_MERCHANT_NAME) ?: ""
        
        selectedIndex = currentIndex
        
        Timber.tag(TAG).d("[ANALYTICS] Setting up dialog with ${categories.size} categories, current: $currentIndex")
        
        // Set title and message
        view.findViewById<MaterialTextView>(R.id.dialogTitle).text = "📝 Change Category"
        view.findViewById<MaterialTextView>(R.id.dialogMessage).text = "Select a new category for $merchantName"
        
        // Set up ListView with custom layout for better visibility
        val listView = view.findViewById<ListView>(R.id.categoryListView)
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dialog_category_list_item,
            categories
        )
        
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        
        // Dynamically adjust ListView height based on number of categories and screen size
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density
        
        // Calculate appropriate height (leaving space for title, buttons, and margins)
        val reservedHeight = (180 * density).toInt() // Space for title, buttons, margins
        val availableHeight = screenHeight - reservedHeight
        val itemHeight = (48 * density).toInt() // Approximate item height
        val calculatedHeight = (categories.size * itemHeight).coerceAtMost((availableHeight * 0.6).toInt())
        val minHeight = (150 * density).toInt()
        val maxHeight = (250 * density).toInt()
        
        val finalHeight = calculatedHeight.coerceIn(minHeight, maxHeight)
        
        // Apply calculated height
        val layoutParams = listView.layoutParams
        layoutParams.height = finalHeight
        listView.layoutParams = layoutParams
        
        Timber.tag(TAG).d("ListView height adjusted: categories=${categories.size}, calculated=$calculatedHeight, final=$finalHeight")
        
        // Apply dialog styling programmatically
        listView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dialog_input_background))
        
        if (currentIndex >= 0 && currentIndex < categories.size) {
            listView.setItemChecked(currentIndex, true)
        }
        
        listView.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
            // Update the checked state immediately for visual feedback
            listView.clearChoices()
            listView.setItemChecked(position, true)
            Timber.tag(TAG).d("[SUCCESS] Category selected: ${categories[position]} at position $position")
        }
        
        // Set up buttons
        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            Timber.tag(TAG).d("[ERROR] Dialog cancelled")
            dismiss()
        }
        
        view.findViewById<MaterialButton>(R.id.btnUpdate).setOnClickListener {
            if (selectedIndex >= 0 && selectedIndex < categories.size) {
                val selectedCategory = categories[selectedIndex]
                Timber.tag(TAG).d("[SUCCESS] Updating to category: $selectedCategory")
                onCategorySelected?.invoke(selectedCategory)
            }
            dismiss()
        }
        
        Timber.tag(TAG).d("[SMS] Custom dialog view created successfully")
        return view
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }
    
    override fun onStart() {
        super.onStart()
        
        // Calculate appropriate dialog height to ensure buttons are visible
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val maxDialogHeight = (screenHeight * 0.8).toInt() // Use 80% of screen height max
        
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Ensure dialog doesn't exceed screen bounds
        dialog?.window?.attributes?.let { params ->
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.y = 0 // Center vertically
            dialog?.window?.attributes = params
        }
        
        Timber.tag(TAG).d("Dialog layout set - Screen height: $screenHeight, Max dialog height: $maxDialogHeight")
        
        // Apply additional programmatic styling for high contrast
        view?.let { dialogView ->
            dialogView.findViewById<MaterialButton>(R.id.btnCancel)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.transparent))
            }
            
            dialogView.findViewById<MaterialButton>(R.id.btnUpdate)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
            }
            
            dialogView.findViewById<MaterialTextView>(R.id.dialogTitle)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_text_primary))
            }
            
            dialogView.findViewById<MaterialTextView>(R.id.dialogMessage)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.dialog_text_secondary))
            }
        }
    }
}