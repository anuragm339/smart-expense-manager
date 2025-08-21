package com.expensemanager.app.ui.profile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.expensemanager.app.databinding.FragmentExportDataBinding
import com.expensemanager.app.utils.CategoryManager
import com.expensemanager.app.utils.SMSHistoryReader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class ExportDataFragment : Fragment() {
    
    private var _binding: FragmentExportDataBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var prefs: SharedPreferences
    private lateinit var categoryManager: CategoryManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportDataBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("export_settings", Context.MODE_PRIVATE)
        categoryManager = CategoryManager(requireContext())
        setupClickListeners()
        loadSettings()
    }
    
    private fun setupClickListeners() {
        binding.cardExportCsv.setOnClickListener {
            showExportConfirmation("CSV") {
                exportDataAsCsv()
            }
        }
        
        binding.cardExportPdf.setOnClickListener {
            showExportConfirmation("PDF") {
                exportDataAsPdf()
            }
        }
        
        binding.cardExportJson.setOnClickListener {
            showExportConfirmation("JSON") {
                exportDataAsJson()
            }
        }
        
        binding.layoutDateRange.setOnClickListener {
            showDateRangeDialog()
        }
        
        binding.layoutCategories.setOnClickListener {
            showCategorySelectionDialog()
        }
        
        binding.layoutCloudBackup.setOnClickListener {
            Toast.makeText(requireContext(), "Cloud backup feature coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_backup", isChecked).apply()
            Toast.makeText(
                requireContext(),
                if (isChecked) "Auto backup enabled" else "Auto backup disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun loadSettings() {
        binding.switchAutoBackup.isChecked = prefs.getBoolean("auto_backup", false)
        binding.tvDateRange.text = prefs.getString("date_range", "Last 6 months")
        binding.tvSelectedCategories.text = prefs.getString("selected_categories", "All categories")
    }
    
    private fun showExportConfirmation(format: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Data as $format")
            .setMessage("This will export your transaction data in $format format. The file will be saved to your device and can be shared.")
            .setPositiveButton("Export") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun exportDataAsCsv() {
        lifecycleScope.launch {
            try {
                val transactions = loadTransactionData()
                
                val csvContent = StringBuilder()
                csvContent.append("Date,Amount,Merchant,Bank,Category,Raw SMS\n")
                
                transactions.forEach { transaction ->
                    csvContent.append("\"${transaction.date}\",")
                    csvContent.append("\"${transaction.amount}\",")
                    csvContent.append("\"${transaction.merchant}\",")
                    csvContent.append("\"${transaction.bankName}\",")
                    csvContent.append("\"${transaction.category}\",")
                    csvContent.append("\"${transaction.rawSMS.replace("\"", "\"\"")}\"\n")
                }
                
                val fileName = "expense_data_${getCurrentDateString()}.csv"
                val file = saveToFile(fileName, csvContent.toString())
                shareFile(file, "Export CSV")
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun exportDataAsJson() {
        lifecycleScope.launch {
            try {
                val transactions = loadTransactionData()
                
                val jsonArray = JSONArray()
                transactions.forEach { transaction ->
                    val jsonObject = JSONObject().apply {
                        put("date", transaction.date)
                        put("amount", transaction.amount)
                        put("merchant", transaction.merchant)
                        put("bank", transaction.bankName)
                        put("category", transaction.category)
                        put("rawSMS", transaction.rawSMS)
                    }
                    jsonArray.put(jsonObject)
                }
                
                val jsonContent = JSONObject().apply {
                    put("exportDate", getCurrentDateString())
                    put("totalTransactions", transactions.size)
                    put("transactions", jsonArray)
                }.toString(2)
                
                val fileName = "expense_data_${getCurrentDateString()}.json"
                val file = saveToFile(fileName, jsonContent)
                shareFile(file, "Export JSON")
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun exportDataAsPdf() {
        Toast.makeText(requireContext(), "PDF export feature coming soon!", Toast.LENGTH_LONG).show()
    }
    
    private suspend fun loadTransactionData(): List<TransactionData> {
        val smsReader = SMSHistoryReader(requireContext())
        val historicalTransactions = smsReader.scanHistoricalSMS()
        
        return historicalTransactions.map { transaction ->
            val category = categoryManager.categorizeTransaction(transaction.merchant)
            TransactionData(
                date = formatDate(transaction.date),
                amount = transaction.amount,
                merchant = transaction.merchant,
                bankName = transaction.bankName,
                category = category,
                rawSMS = transaction.rawSMS
            )
        }
    }
    
    private fun saveToFile(fileName: String, content: String): File {
        val file = File(requireContext().getExternalFilesDir(null), fileName)
        FileWriter(file).use { writer ->
            writer.write(content)
        }
        return file
    }
    
    private fun shareFile(file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, title))
            Toast.makeText(requireContext(), "Export completed: ${file.name}", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showDateRangeDialog() {
        val options = arrayOf("Last 30 days", "Last 3 months", "Last 6 months", "Last year", "All time")
        val current = binding.tvDateRange.text.toString()
        val currentIndex = options.indexOf(current)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Date Range")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                binding.tvDateRange.text = options[which]
                prefs.edit().putString("date_range", options[which]).apply()
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showCategorySelectionDialog() {
        val categories = arrayOf("All categories", "Food & Dining", "Transportation", "Groceries", "Healthcare", "Shopping", "Entertainment", "Utilities", "Other")
        val checkedItems = BooleanArray(categories.size) { it == 0 } // Default to "All categories"
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Categories")
            .setMultiChoiceItems(categories, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
                if (which == 0 && isChecked) {
                    // If "All categories" is selected, uncheck others
                    for (i in 1 until checkedItems.size) {
                        checkedItems[i] = false
                    }
                } else if (which != 0 && isChecked) {
                    // If any specific category is selected, uncheck "All categories"
                    checkedItems[0] = false
                }
            }
            .setPositiveButton("OK") { _, _ ->
                val selectedCategories = mutableListOf<String>()
                checkedItems.forEachIndexed { index, isChecked ->
                    if (isChecked) {
                        selectedCategories.add(categories[index])
                    }
                }
                
                val displayText = if (selectedCategories.isEmpty() || selectedCategories.contains("All categories")) {
                    "All categories"
                } else {
                    selectedCategories.joinToString(", ")
                }
                
                binding.tvSelectedCategories.text = displayText
                prefs.edit().putString("selected_categories", displayText).apply()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    
    private fun formatDate(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class TransactionData(
    val date: String,
    val amount: Double,
    val merchant: String,
    val bankName: String,
    val category: String,
    val rawSMS: String
)