package com.smartexpenseai.app.ui.profile

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
import com.smartexpenseai.app.databinding.FragmentExportDataBinding
import com.smartexpenseai.app.utils.CategoryManager
import com.smartexpenseai.app.data.repository.ExpenseRepository
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.util.Calendar
import android.os.Environment
import android.content.ActivityNotFoundException
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
    private lateinit var repository: ExpenseRepository
    private lateinit var merchantAliasManager: com.smartexpenseai.app.utils.MerchantAliasManager

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
        repository = ExpenseRepository.getInstance(requireContext())
        categoryManager = CategoryManager(requireContext(), repository)
        merchantAliasManager = com.smartexpenseai.app.utils.MerchantAliasManager(requireContext(), repository)
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
        val dateRange = binding.tvDateRange.text.toString()
        val categories = binding.tvSelectedCategories.text.toString()
        
        val filterInfo = buildString {
            append("📅 Date Range: $dateRange\n")
            append("🏷️ Categories: $categories\n\n")
            append("This will export your filtered transaction data in $format format. The file will be saved to your Downloads folder.")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Data as $format")
            .setMessage(filterInfo)
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
                val exportData = loadTransactionData()
                
                val csvContent = StringBuilder()
                
                // Enhanced CSV with summary information at top
                csvContent.append("EXPENSE REPORT SUMMARY\n")
                csvContent.append("Date Range,${exportData.summary.dateRange}\n")
                csvContent.append("Export Date,${exportData.summary.exportDate}\n")
                csvContent.append("Total Transactions,${exportData.summary.totalTransactions}\n")
                csvContent.append("Total Amount,₹${String.format("%.2f", exportData.summary.totalAmount)}\n")
                csvContent.append("Average Per Transaction,₹${String.format("%.2f", exportData.summary.averageTransaction)}\n")
                csvContent.append("\n")
                
                // Category breakdown
                csvContent.append("CATEGORY BREAKDOWN\n")
                csvContent.append("Category,Amount,Transactions,Percentage\n")
                exportData.categoryBreakdown.forEach { category ->
                    csvContent.append("\"${category.name}\",")
                    csvContent.append("₹${String.format("%.2f", category.totalAmount)},")
                    csvContent.append("${category.transactionCount},")
                    csvContent.append("${String.format("%.1f", category.percentage)}%\n")
                }
                csvContent.append("\n")
                
                // Monthly trends
                csvContent.append("MONTHLY TRENDS\n")
                csvContent.append("Month,Amount,Transactions,Average Per Transaction\n")
                exportData.monthlySummaries.forEach { month ->
                    csvContent.append("\"${month.month}\",")
                    csvContent.append("₹${String.format("%.2f", month.totalAmount)},")
                    csvContent.append("${month.transactionCount},")
                    csvContent.append("₹${String.format("%.2f", month.averagePerTransaction)}\n")
                }
                csvContent.append("\n")
                
                // Top merchants
                csvContent.append("TOP MERCHANTS\n")
                csvContent.append("Merchant,Amount,Transactions\n")
                exportData.topMerchants.forEach { merchant ->
                    csvContent.append("\"${merchant.name}\",")
                    csvContent.append("₹${String.format("%.2f", merchant.totalAmount)},")
                    csvContent.append("${merchant.transactionCount}\n")
                }
                csvContent.append("\n")
                
                // Detailed transactions
                csvContent.append("DETAILED TRANSACTIONS\n")
                csvContent.append("Date,Amount,Merchant,Category,Bank,Raw SMS\n")
                
                exportData.transactions.forEach { transaction ->
                    csvContent.append("\"${transaction.date}\",")
                    csvContent.append("₹${String.format("%.2f", transaction.amount)},")
                    csvContent.append("\"${transaction.merchant}\",")
                    csvContent.append("\"${transaction.category}\",")
                    csvContent.append("\"${transaction.bankName}\",")
                    csvContent.append("\"${transaction.rawSMS.replace("\"", "\"\"")}\"\n")
                }
                
                val fileName = "expense_report_${getCurrentDateString()}.csv"
                val file = saveToFile(fileName, csvContent.toString())
                
                // Show file location to user
                Toast.makeText(requireContext(), "CSV saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                shareFile(file, "Comprehensive Expense Report (CSV)")
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "CSV export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun exportDataAsJson() {
        lifecycleScope.launch {
            try {
                val exportData = loadTransactionData()
                
                // Create comprehensive JSON structure
                val rootJson = JSONObject().apply {
                    // Summary section
                    put("summary", JSONObject().apply {
                        put("dateRange", exportData.summary.dateRange)
                        put("exportDate", exportData.summary.exportDate)
                        put("totalTransactions", exportData.summary.totalTransactions)
                        put("totalAmount", exportData.summary.totalAmount)
                        put("averageTransaction", exportData.summary.averageTransaction)
                    })
                    
                    // Category breakdown
                    put("categoryBreakdown", JSONArray().apply {
                        exportData.categoryBreakdown.forEach { category ->
                            put(JSONObject().apply {
                                put("name", category.name)
                                put("totalAmount", category.totalAmount)
                                put("transactionCount", category.transactionCount)
                                put("percentage", category.percentage)
                                put("color", category.color)
                            })
                        }
                    })
                    
                    // Monthly trends
                    put("monthlyTrends", JSONArray().apply {
                        exportData.monthlySummaries.forEach { month ->
                            put(JSONObject().apply {
                                put("month", month.month)
                                put("totalAmount", month.totalAmount)
                                put("transactionCount", month.transactionCount)
                                put("averagePerTransaction", month.averagePerTransaction)
                            })
                        }
                    })
                    
                    // Top merchants
                    put("topMerchants", JSONArray().apply {
                        exportData.topMerchants.forEach { merchant ->
                            put(JSONObject().apply {
                                put("name", merchant.name)
                                put("totalAmount", merchant.totalAmount)
                                put("transactionCount", merchant.transactionCount)
                            })
                        }
                    })
                    
                    // All transactions
                    put("transactions", JSONArray().apply {
                        exportData.transactions.forEach { transaction ->
                            put(JSONObject().apply {
                                put("date", transaction.date)
                                put("amount", transaction.amount)
                                put("merchant", transaction.merchant)
                                put("normalizedMerchant", transaction.normalizedMerchant)
                                put("category", transaction.category)
                                put("categoryColor", transaction.categoryColor)
                                put("bankName", transaction.bankName)
                                put("rawSMS", transaction.rawSMS)
                            })
                        }
                    })
                }
                
                val fileName = "expense_report_${getCurrentDateString()}.json"
                val file = saveToFile(fileName, rootJson.toString(2))
                
                // Show file location to user
                Toast.makeText(requireContext(), "JSON saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                shareFile(file, "Comprehensive Expense Report (JSON)")
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "JSON export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun exportDataAsPdf() {
        lifecycleScope.launch {
            try {
                val exportData = loadTransactionData()
                
                // Create PDF document
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
                val page = pdfDocument.startPage(pageInfo)
                
                val canvas = page.canvas
                val paint = Paint().apply {
                    isAntiAlias = true
                }
                
                drawPdfContent(canvas, paint, exportData)
                
                pdfDocument.finishPage(page)
                
                // Save PDF to public Downloads directory
                val fileName = "expense_report_${getCurrentDateString()}.pdf"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                
                file.outputStream().use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                
                pdfDocument.close()
                
                // Show file location to user
                Toast.makeText(requireContext(), "PDF saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                shareFile(file, "Comprehensive Expense Report (PDF)")
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "PDF export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun drawPdfContent(canvas: Canvas, paint: Paint, exportData: ExportData) {
        val margin = 40f
        var yPosition = 60f
        val pageWidth = canvas.width.toFloat()
        
        // Title
        paint.apply {
            textSize = 24f
            color = Color.BLACK
            isFakeBoldText = true
        }
        canvas.drawText("💰 EXPENSE REPORT", margin, yPosition, paint)
        yPosition += 40f
        
        // Export info
        paint.apply {
            textSize = 12f
            isFakeBoldText = false
        }
        canvas.drawText("📅 ${exportData.summary.dateRange}", margin, yPosition, paint)
        yPosition += 20f
        canvas.drawText("📊 Generated on: ${exportData.summary.exportDate}", margin, yPosition, paint)
        yPosition += 30f
        
        // Summary section
        drawSummarySection(canvas, paint, exportData.summary, margin, yPosition)
        yPosition += 120f
        
        // Monthly trend chart
        drawMonthlyTrendChart(canvas, paint, exportData.monthlySummaries, margin, yPosition, pageWidth - 2 * margin, 150f)
        yPosition += 200f
        
        // Category breakdown chart
        if (yPosition + 200f < canvas.height) {
            drawCategoryChart(canvas, paint, exportData.categoryBreakdown, margin, yPosition, pageWidth - 2 * margin, 150f)
        } else {
            // Start new page if needed
            // For simplicity, we'll fit everything on one page
            drawCategoryChart(canvas, paint, exportData.categoryBreakdown, margin + 200f, yPosition - 200f, 300f, 150f)
        }
    }
    
    private fun drawSummarySection(canvas: Canvas, paint: Paint, summary: ExportSummary, x: Float, y: Float) {
        var yPos = y
        
        // Summary box
        paint.apply {
            color = Color.rgb(240, 240, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(x, y, x + 500f, y + 100f, paint)
        
        paint.apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(x, y, x + 500f, y + 100f, paint)
        
        // Summary text
        paint.apply {
            style = Paint.Style.FILL
            textSize = 16f
            isFakeBoldText = true
        }
        yPos += 25f
        canvas.drawText("📊 SUMMARY", x + 20f, yPos, paint)
        
        paint.isFakeBoldText = false
        paint.textSize = 12f
        yPos += 25f
        canvas.drawText("Total Transactions: ${summary.totalTransactions}", x + 20f, yPos, paint)
        canvas.drawText("Total Amount: ₹${String.format("%.0f", summary.totalAmount)}", x + 250f, yPos, paint)
        yPos += 20f
        canvas.drawText("Average per Transaction: ₹${String.format("%.0f", summary.averageTransaction)}", x + 20f, yPos, paint)
    }
    
    private fun drawMonthlyTrendChart(canvas: Canvas, paint: Paint, monthlySummaries: List<MonthlySummary>, x: Float, y: Float, width: Float, height: Float) {
        // Chart title
        paint.apply {
            textSize = 16f
            isFakeBoldText = true
            color = Color.BLACK
        }
        canvas.drawText("📈 MONTHLY SPENDING TRENDS", x, y, paint)
        
        val chartY = y + 30f
        
        // Draw chart background
        paint.apply {
            color = Color.rgb(250, 250, 250)
            style = Paint.Style.FILL
        }
        canvas.drawRect(x, chartY, x + width, chartY + height, paint)
        
        // Draw chart border
        paint.apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(x, chartY, x + width, chartY + height, paint)
        
        if (monthlySummaries.isNotEmpty()) {
            val maxAmount = monthlySummaries.maxOf { it.totalAmount }
            val barWidth = width / monthlySummaries.size
            
            // Draw bars
            paint.apply {
                color = Color.rgb(76, 175, 80) // Green
                style = Paint.Style.FILL
            }
            
            monthlySummaries.forEachIndexed { index, month ->
                val barHeight = (month.totalAmount / maxAmount * (height - 40f)).toFloat()
                val barX = x + index * barWidth + barWidth * 0.1f
                val barY = chartY + height - 20f - barHeight
                
                canvas.drawRect(barX, barY, barX + barWidth * 0.8f, chartY + height - 20f, paint)
                
                // Month label
                paint.apply {
                    color = Color.BLACK
                    textSize = 10f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(month.month.substring(5), barX + barWidth * 0.4f, chartY + height - 5f, paint)
                
                // Amount label
                paint.textSize = 8f
                canvas.drawText("₹${String.format("%.0f", month.totalAmount)}", barX + barWidth * 0.4f, barY - 5f, paint)
            }
        }
    }
    
    private fun drawCategoryChart(canvas: Canvas, paint: Paint, categoryBreakdown: List<CategorySummary>, x: Float, y: Float, width: Float, height: Float) {
        // Chart title
        paint.apply {
            textSize = 16f
            isFakeBoldText = true
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("🥧 CATEGORY BREAKDOWN", x, y, paint)
        
        val chartY = y + 30f
        
        if (categoryBreakdown.isNotEmpty()) {
            // Draw pie chart (simplified as horizontal bars for PDF)
            val totalAmount = categoryBreakdown.sumOf { it.totalAmount }
            val barWidth = width
            val barHeight = 20f
            var currentY = chartY
            
            categoryBreakdown.take(6).forEach { category ->
                val barLength = (category.totalAmount / totalAmount * barWidth).toFloat()
                
                // Draw category bar
                val categoryColor = try {
                    Color.parseColor(category.color)
                } catch (e: Exception) {
                    Color.GRAY
                }
                
                paint.apply {
                    color = categoryColor
                    style = Paint.Style.FILL
                }
                canvas.drawRect(x, currentY, x + barLength, currentY + barHeight, paint)
                
                // Category label and percentage
                paint.apply {
                    color = Color.BLACK
                    textSize = 12f
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText("${category.name}: ${String.format("%.1f", category.percentage)}% (₹${String.format("%.0f", category.totalAmount)})", 
                    x + barLength + 10f, currentY + 15f, paint)
                
                currentY += 25f
            }
        }
    }
    
    private suspend fun loadTransactionData(): ExportData {
        // Use user-selected date range from UI
        val (startDate, endDate) = getSelectedDateRange()
        
        // Get selected categories from UI
        val selectedCategories = getSelectedCategories()
        
        // Get all transaction data from repository (with exclusions applied)
        val allTransactions = repository.getTransactionsByDateRange(startDate, endDate)
        
        // Filter transactions by selected categories if specific categories are selected
        val filteredTransactions = if (selectedCategories.isEmpty()) {
            // No category filter - include all transactions
            allTransactions
        } else {
            // Filter by selected categories
            allTransactions.filter { transaction ->
                val merchantWithCategory = repository.getMerchantWithCategory(transaction.normalizedMerchant)
                val categoryName = merchantWithCategory?.category_name ?: "Other"
                selectedCategories.contains(categoryName)
            }
        }
        
        // Get dashboard data for the same filtered date range
        val dashboardData = repository.getDashboardData(startDate, endDate)
        
        // Create comprehensive export data using filtered transactions
        val transactionList = filteredTransactions.map { transaction ->
            // Get category information for this transaction
            val merchantWithCategory = repository.getMerchantWithCategory(transaction.normalizedMerchant)
            
            TransactionData(
                date = formatDate(transaction.transactionDate),
                amount = transaction.amount,
                merchant = merchantAliasManager.getDisplayName(transaction.rawMerchant), // Use display name for export
                normalizedMerchant = transaction.normalizedMerchant,
                bankName = transaction.bankName,
                category = merchantWithCategory?.category_name ?: "Other",
                categoryColor = merchantWithCategory?.category_color ?: "#9e9e9e",
                rawSMS = transaction.rawSmsBody
            )
        }
        
        // Generate monthly summaries for trends using filtered transactions
        val monthlySummaries = generateMonthlySummaries(filteredTransactions)
        
        // Calculate filtered totals for accurate summaries
        val filteredTotalSpent = filteredTransactions.sumOf { it.amount }
        val filteredTransactionCount = filteredTransactions.size
        
        // Generate category breakdown from filtered transactions
        val categoryBreakdown = if (selectedCategories.isEmpty()) {
            // No category filter - use dashboard data
            dashboardData.topCategories.map { category ->
                CategorySummary(
                    name = category.category_name,
                    totalAmount = category.total_amount,
                    transactionCount = category.transaction_count,
                    percentage = (category.total_amount / dashboardData.totalSpent * 100),
                    color = category.color
                )
            }
        } else {
            // Generate category breakdown from filtered transactions
            val categoryTotals = filteredTransactions
                .groupBy { transaction ->
                    val merchantWithCategory = repository.getMerchantWithCategory(transaction.normalizedMerchant)
                    merchantWithCategory?.category_name ?: "Other"
                }
                .map { (categoryName, transactions) ->
                    val category = repository.getCategoryByName(categoryName)
                    CategorySummary(
                        name = categoryName,
                        totalAmount = transactions.sumOf { it.amount },
                        transactionCount = transactions.size,
                        percentage = (transactions.sumOf { it.amount } / filteredTotalSpent * 100),
                        color = category?.color ?: "#9e9e9e"
                    )
                }
                .sortedByDescending { it.totalAmount }
            categoryTotals
        }
        
        // Generate top merchants from filtered transactions
        val topMerchants = filteredTransactions
            .groupBy { it.normalizedMerchant }
            .map { (normalizedMerchant, transactions) ->
                MerchantSummary(
                    name = normalizedMerchant,
                    totalAmount = transactions.sumOf { it.amount },
                    transactionCount = transactions.size
                )
            }
            .sortedByDescending { it.totalAmount }
            .take(10) // Top 10 merchants
        
        return ExportData(
            transactions = transactionList,
            summary = ExportSummary(
                totalTransactions = filteredTransactionCount,
                totalAmount = filteredTotalSpent,
                averageTransaction = if (filteredTransactionCount > 0) filteredTotalSpent / filteredTransactionCount else 0.0,
                dateRange = "${formatDate(startDate)} to ${formatDate(endDate)} ${if (selectedCategories.isNotEmpty()) "• Categories: ${selectedCategories.joinToString(", ")}" else ""}",
                exportDate = formatDate(Date())
            ),
            monthlySummaries = monthlySummaries,
            categoryBreakdown = categoryBreakdown,
            topMerchants = topMerchants
        )
    }
    
    private suspend fun generateMonthlySummaries(transactions: List<com.smartexpenseai.app.data.entities.TransactionEntity>): List<MonthlySummary> {
        val monthlyData = mutableMapOf<String, MutableList<com.smartexpenseai.app.data.entities.TransactionEntity>>()
        
        transactions.forEach { transaction ->
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(transaction.transactionDate)
            monthlyData.getOrPut(monthKey) { mutableListOf() }.add(transaction)
        }
        
        return monthlyData.map { (month, monthTransactions) ->
            MonthlySummary(
                month = month,
                totalAmount = monthTransactions.sumOf { it.amount },
                transactionCount = monthTransactions.size,
                averagePerTransaction = monthTransactions.sumOf { it.amount } / monthTransactions.size
            )
        }.sortedBy { it.month }
    }
    
    private fun saveToFile(fileName: String, content: String): File {
        // Save to public Downloads directory instead of private app directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
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
            
            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Create view intent to open file manager
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Create chooser with both options
            val chooserIntent = Intent.createChooser(shareIntent, title)
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(viewIntent))
            
            startActivity(chooserIntent)
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
            
            // Fallback: try to open file manager directly to Downloads folder
            try {
                val downloadsIntent = Intent(Intent.ACTION_VIEW).apply {
                    setType("*/*")
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivity(Intent.createChooser(downloadsIntent, "Open File Manager"))
            } catch (e2: ActivityNotFoundException) {
                // If no file manager available, just show the file path
                Toast.makeText(requireContext(), "File saved to Downloads folder", Toast.LENGTH_LONG).show()
            }
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
    
    private fun getSelectedDateRange(): Pair<Date, Date> {
        val dateRangeText = binding.tvDateRange.text.toString()
        val calendar = Calendar.getInstance()
        val endDate = Date() // Always current date as end
        
        val startDate = when (dateRangeText) {
            "Last 30 days" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                calendar.time
            }
            "Last 3 months" -> {
                calendar.add(Calendar.MONTH, -3)
                calendar.time
            }
            "Last 6 months" -> {
                calendar.add(Calendar.MONTH, -6)
                calendar.time
            }
            "Last year" -> {
                calendar.add(Calendar.YEAR, -1)
                calendar.time
            }
            "All time" -> {
                calendar.add(Calendar.YEAR, -5) // Go back 5 years for "all time"
                calendar.time
            }
            else -> {
                // Default to last 6 months
                calendar.add(Calendar.MONTH, -6)
                calendar.time
            }
        }
        
        return Pair(startDate, endDate)
    }
    
    private fun getSelectedCategories(): List<String> {
        val selectedText = binding.tvSelectedCategories.text.toString()
        return if (selectedText == "All categories") {
            emptyList() // Empty list means no filtering
        } else {
            selectedText.split(", ").map { it.trim() }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Enhanced data classes for comprehensive export
data class TransactionData(
    val date: String,
    val amount: Double,
    val merchant: String,
    val normalizedMerchant: String,
    val bankName: String,
    val category: String,
    val categoryColor: String,
    val rawSMS: String
)

data class ExportData(
    val transactions: List<TransactionData>,
    val summary: ExportSummary,
    val monthlySummaries: List<MonthlySummary>,
    val categoryBreakdown: List<CategorySummary>,
    val topMerchants: List<MerchantSummary>
)

data class ExportSummary(
    val totalTransactions: Int,
    val totalAmount: Double,
    val averageTransaction: Double,
    val dateRange: String,
    val exportDate: String
)

data class MonthlySummary(
    val month: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val averagePerTransaction: Double
)

data class CategorySummary(
    val name: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentage: Double,
    val color: String
)

data class MerchantSummary(
    val name: String,
    val totalAmount: Double,
    val transactionCount: Int
)