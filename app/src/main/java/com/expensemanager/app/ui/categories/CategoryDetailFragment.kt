package com.expensemanager.app.ui.categories

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentCategoryDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class CategoryDetailFragment : Fragment() {

    private var _binding: FragmentCategoryDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CategoryDetailViewModel by viewModels()
    private lateinit var merchantsAdapter: MerchantsInCategoryAdapter

    private var categoryName: String = ""

    companion object {
        private const val TAG = "CategoryDetailFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            categoryName = it.getString("categoryName", "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        setupSearchBar()
        observeViewModel()

        // Load category data
        viewModel.loadCategoryDetail(categoryName)
    }

    private fun setupRecyclerView() {
        merchantsAdapter = MerchantsInCategoryAdapter(
            onMerchantClick = { merchant ->
                // Navigate to merchant transactions
                val bundle = Bundle().apply {
                    putString("merchantName", merchant.merchantName)
                }
                findNavController().navigate(
                    R.id.action_category_detail_to_merchant_transactions,
                    bundle
                )
            },
            onChangeCategoryClick = { merchant ->
                showChangeCategoryDialog(merchant)
            }
        )

        binding.recyclerMerchants.apply {
            adapter = merchantsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupClickListeners() {
        binding.btnViewTransactions.setOnClickListener {
            // Navigate to category transactions (existing functionality)
            val bundle = Bundle().apply {
                putString("categoryName", categoryName)
            }
            findNavController().navigate(
                R.id.action_category_detail_to_category_transactions,
                bundle
            )
        }

        binding.btnCategoryActions.setOnClickListener {
            showCategoryActionsDialog()
        }

        binding.fabQuickAdd.setOnClickListener {
            // TODO: Implement quick add transaction
            Toast.makeText(requireContext(), "Quick Add Transaction - Coming Soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchBar() {
        binding.etSearchMerchants.addTextChangedListener { text ->
            viewModel.searchMerchants(text.toString())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }
    }

    private fun updateUI(state: CategoryDetailUIState) {
        // Category header
        binding.tvCategoryName.text = state.categoryName
        binding.tvCategoryEmoji.text = state.categoryEmoji
        binding.tvCategorySummary.text = state.summaryText
        binding.tvTotalAmount.text = state.formattedTotalAmount
        binding.tvMerchantCount.text = state.merchantCountText

        // Category color
        try {
            val color = Color.parseColor(state.categoryColor)
            binding.viewCategoryColor.backgroundTintList =
                android.content.res.ColorStateList.valueOf(color)
        } catch (e: Exception) {
            Timber.tag(TAG).w("Invalid color: %s", state.categoryColor)
        }

        // Merchants list
        merchantsAdapter.submitList(state.filteredMerchants)

        // Empty state
        if (state.filteredMerchants.isEmpty() && !state.isLoading) {
            binding.recyclerMerchants.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            binding.recyclerMerchants.visibility = View.VISIBLE
            binding.layoutEmpty.visibility = View.GONE
        }

        // Error handling
        state.error?.let { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }

        // Category deleted - navigate back
        if (state.categoryDeleted) {
            Toast.makeText(requireContext(), "Category deleted", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }

        // Disable category actions for system categories
        binding.btnCategoryActions.isEnabled = !viewModel.isSystemCategory()
        if (viewModel.isSystemCategory()) {
            binding.btnCategoryActions.text = "System Category"
            binding.btnCategoryActions.alpha = 0.6f
        }
    }

    private fun showChangeCategoryDialog(merchant: MerchantInCategory) {
        val dialogHelper = CategoryChangeDialogHelper(requireContext())
        dialogHelper.showChangeCategoryDialog(merchant) { merchantName, newCategory, applyToFuture ->
            viewModel.changeMerchantCategory(merchantName, newCategory, applyToFuture)
            Toast.makeText(
                requireContext(),
                "Updating ${merchant.merchantName} to $newCategory category...",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showCategoryActionsDialog() {
        if (viewModel.isSystemCategory()) {
            Toast.makeText(
                requireContext(),
                "Cannot modify system categories",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_category_actions,
            null
        )

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setBackgroundInsetStart(16)
            .setBackgroundInsetEnd(16)
            .setBackgroundInsetTop(64)
            .setBackgroundInsetBottom(64)
            .create()

        // Set category info
        val categoryState = viewModel.uiState.value
        dialogView.findViewById<TextView>(R.id.tv_category_name).text = categoryState.categoryName
        dialogView.findViewById<TextView>(R.id.tv_category_emoji).text = categoryState.categoryEmoji

        // Set up click listeners
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_rename).setOnClickListener {
            dialog.dismiss()
            showRenameCategoryDialog()
        }

        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_delete).setOnClickListener {
            dialog.dismiss()
            showDeleteCategoryDialog()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showRenameCategoryDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_add_category,
            null
        )

        val categoryNameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_category_name)
        val emojiInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_emoji)

        // Pre-fill current values
        categoryNameInput.setText(viewModel.uiState.value.categoryName)
        emojiInput.setText(viewModel.uiState.value.categoryEmoji)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Category")
            .setView(dialogView)
            .create()

        // Update button text for rename operation
        val addButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add)
        addButton.text = "Rename"

        // Set up click listeners
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        addButton.setOnClickListener {
            val newName = categoryNameInput.text.toString().trim()
            val newEmoji = emojiInput.text.toString().trim()

            if (newName.isNotEmpty()) {
                viewModel.renameCategory(newName, newEmoji.ifEmpty { "📊" })
                Toast.makeText(
                    requireContext(),
                    "Renaming category to '$newName'...",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Please enter a category name",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        dialog.show()
    }

    private fun showDeleteCategoryDialog() {
        // Get list of other categories for reassignment
        val categoryManager = com.expensemanager.app.utils.CategoryManager(requireContext())
        val allCategories = categoryManager.getAllCategories().toMutableList()
        allCategories.remove(viewModel.uiState.value.categoryName) // Remove current category

        if (allCategories.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Cannot delete the last category. Create another category first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_delete_category,
            null
        )

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setBackgroundInsetStart(16)
            .setBackgroundInsetEnd(16)
            .setBackgroundInsetTop(64)
            .setBackgroundInsetBottom(64)
            .create()

        // Set category name
        dialogView.findViewById<TextView>(R.id.tv_category_name).text = viewModel.uiState.value.categoryName

        // Set up category dropdown
        val categoryAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            allCategories
        )
        val spinner = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_reassign_category)
        spinner.setAdapter(categoryAdapter)

        // Set up button listeners
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete).setOnClickListener {
            val selectedCategory = spinner.text.toString().trim()
            if (selectedCategory.isNotEmpty() && allCategories.contains(selectedCategory)) {
                viewModel.deleteCategory(selectedCategory)
                Toast.makeText(
                    requireContext(),
                    "Deleting category...",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            } else {
                val tilCategory = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_reassign_category)
                tilCategory.error = "Please select a category"
            }
        }

        // Clear error when user selects
        spinner.setOnItemClickListener { _, _, _, _ ->
            val tilCategory = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_reassign_category)
            tilCategory.error = null
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}