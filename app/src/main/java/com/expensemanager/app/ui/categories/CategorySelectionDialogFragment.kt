package com.expensemanager.app.ui.categories

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
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
        Log.d(TAG, "[FIX] Creating custom dialog view")
        
        val view = inflater.inflate(R.layout.dialog_category_selection_custom, container, false)
        
        val categories = arguments?.getStringArray(ARG_CATEGORIES) ?: arrayOf()
        val currentIndex = arguments?.getInt(ARG_CURRENT_INDEX, -1) ?: -1
        val merchantName = arguments?.getString(ARG_MERCHANT_NAME) ?: ""
        
        selectedIndex = currentIndex
        
        Log.d(TAG, "[ANALYTICS] Setting up dialog with ${categories.size} categories, current: $currentIndex")
        
        // Set title and message
        view.findViewById<MaterialTextView>(R.id.dialogTitle).text = "📝 Change Category"
        view.findViewById<MaterialTextView>(R.id.dialogMessage).text = "Select a new category for $merchantName"
        
        // Set up ListView
        val listView = view.findViewById<ListView>(R.id.categoryListView)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_single_choice,
            categories
        )
        
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        
        if (currentIndex >= 0 && currentIndex < categories.size) {
            listView.setItemChecked(currentIndex, true)
        }
        
        listView.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
            Log.d(TAG, "[SUCCESS] Category selected: ${categories[position]} at position $position")
        }
        
        // Set up buttons
        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            Log.d(TAG, "[ERROR] Dialog cancelled")
            dismiss()
        }
        
        view.findViewById<MaterialButton>(R.id.btnUpdate).setOnClickListener {
            if (selectedIndex >= 0 && selectedIndex < categories.size) {
                val selectedCategory = categories[selectedIndex]
                Log.d(TAG, "[SUCCESS] Updating to category: $selectedCategory")
                onCategorySelected?.invoke(selectedCategory)
            }
            dismiss()
        }
        
        Log.d(TAG, "[SMS] Custom dialog view created successfully")
        return view
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}