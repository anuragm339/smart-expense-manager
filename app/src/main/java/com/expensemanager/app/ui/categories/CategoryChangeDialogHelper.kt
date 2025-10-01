package com.expensemanager.app.ui.categories

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.expensemanager.app.R
import com.expensemanager.app.databinding.DialogChangeMerchantCategoryBinding
import com.expensemanager.app.utils.CategoryManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber

class CategoryChangeDialogHelper(private val context: Context) {

    companion object {
        private const val TAG = "CategoryChangeDialog"
    }

    fun showChangeCategoryDialog(
        merchant: MerchantInCategory,
        onCategoryChanged: (merchantName: String, newCategory: String, applyToFuture: Boolean) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(
            R.layout.dialog_change_merchant_category,
            null
        )

        val binding = DialogChangeMerchantCategoryBinding.bind(dialogView)

        // Set merchant info
        binding.tvMerchantName.text = merchant.merchantName
        binding.tvMerchantInitial.text = merchant.getInitial()
        binding.tvCurrentCategory.text = "Current Category: ${merchant.currentCategory}"

        // Get available categories
        val categoryManager = CategoryManager(context)
        val availableCategories = categoryManager.getAllCategories().toMutableList()

        // Remove current category from selection (since it's already assigned)
        availableCategories.remove(merchant.currentCategory)

        // Set up category dropdown
        val categoryAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_dropdown_item_1line,
            availableCategories
        )
        binding.spinnerCategory.setAdapter(categoryAdapter)

        // Create dialog
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        // Set up button listeners
        binding.btnCancel.setOnClickListener {
            Timber.tag(TAG).d("Category change cancelled for merchant: %s", merchant.merchantName)
            dialog.dismiss()
        }

        binding.btnChange.setOnClickListener {
            val selectedCategory = binding.spinnerCategory.text.toString().trim()
            val applyToFuture = binding.cbApplyToFuture.isChecked

            if (selectedCategory.isNotEmpty() && selectedCategory != merchant.currentCategory) {
                Timber.tag(TAG).d(
                    "Changing category for %s from %s to %s (apply to future: %s)",
                    merchant.merchantName,
                    merchant.currentCategory,
                    selectedCategory,
                    applyToFuture
                )

                onCategoryChanged(merchant.merchantName, selectedCategory, applyToFuture)
                dialog.dismiss()
            } else {
                // Show error if no category selected or same category
                binding.tilCategory.error = if (selectedCategory.isEmpty()) {
                    "Please select a category"
                } else {
                    "Please select a different category"
                }
            }
        }

        // Clear error when user starts typing
        binding.spinnerCategory.setOnItemClickListener { _, _, _, _ ->
            binding.tilCategory.error = null
        }

        dialog.show()
    }
}