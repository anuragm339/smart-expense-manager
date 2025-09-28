package com.expensemanager.app.services

import android.content.Context
import android.net.Uri
import android.os.Build
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for exporting transaction data to various formats
 */
@Singleton
class DataExportService @Inject constructor(
    private val repository: ExpenseRepository,
    private val appLogger: AppLogger
) {
    
    companion object {
        private const val TAG = "DataExportService"
    }
    
    /**
     * Export data to JSON format
     */
    suspend fun exportToJson(context: Context, uri: Uri, includeCategories: Boolean = true, includeMerchants: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            val exportData = JSONObject()
            
            // Add metadata
            val metadata = JSONObject().apply {
                put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                put("appVersion", getAppVersion(context))
                put("totalTransactions", repository.getTransactionCount())
            }
            exportData.put("metadata", metadata)
            
            // Export transactions
            val transactions = repository.getAllTransactionsSync()
            val transactionsArray = JSONArray()
            
            transactions.forEach { transaction ->
                val transactionJson = JSONObject().apply {
                    put("id", transaction.id)
                    put("amount", transaction.amount)
                    put("rawMerchant", transaction.rawMerchant)
                    put("normalizedMerchant", transaction.normalizedMerchant)
                    put("bankName", transaction.bankName)
                    put("transactionDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(transaction.transactionDate))
                    put("isDebit", transaction.isDebit)
                    put("confidenceScore", transaction.confidenceScore)
                    put("rawSmsBody", transaction.rawSmsBody)
                    put("smsId", transaction.smsId)
                }
                transactionsArray.put(transactionJson)
            }
            exportData.put("transactions", transactionsArray)
            
            // Export categories if requested
            if (includeCategories) {
                val categories = repository.getAllCategoriesSync()
                val categoriesArray = JSONArray()
                
                categories.forEach { category ->
                    val categoryJson = JSONObject().apply {
                        put("id", category.id)
                        put("name", category.name)
                        put("emoji", category.emoji)
                        put("color", category.color)
                        put("isSystem", category.isSystem)
                        put("displayOrder", category.displayOrder)
                    }
                    categoriesArray.put(categoryJson)
                }
                exportData.put("categories", categoriesArray)
            }
            
            // Export merchants if requested
            if (includeMerchants) {
                val merchants = repository.getAllMerchants()
                val merchantsArray = JSONArray()
                
                merchants.forEach { merchant ->
                    val merchantJson = JSONObject().apply {
                        put("id", merchant.id)
                        put("normalizedName", merchant.normalizedName)
                        put("displayName", merchant.displayName)
                        put("categoryId", merchant.categoryId)
                        put("isUserDefined", merchant.isUserDefined)
                        put("isExcluded", merchant.isExcludedFromExpenseTracking)
                    }
                    merchantsArray.put(merchantJson)
                }
                exportData.put("merchants", merchantsArray)
            }
            
            // Write to file
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                    outputStream.write(exportData.toString(2).toByteArray())
                }
            }
            
            appLogger.info(TAG, "Successfully exported data to JSON")
            true
            
        } catch (e: Exception) {
            appLogger.error(TAG, "Error exporting data to JSON", e)
            false
        }
    }
    
    /**
     * Export data to CSV format
     */
    suspend fun exportToCsv(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val transactions = repository.getAllTransactionsSync()
            val csvContent = StringBuilder()
            
            // CSV Header
            csvContent.appendLine("Date,Amount,Merchant,Bank,Type,Category,Confidence,SMS_ID")
            
            // CSV Data
            transactions.forEach { transaction ->
                val merchantWithCategory = repository.getMerchantWithCategory(transaction.normalizedMerchant)
                val category = merchantWithCategory?.category_name ?: "Other"
                
                val dateString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(transaction.transactionDate)
                val merchantString = transaction.rawMerchant.replace("\"", "\"\"")
                val typeString = if (transaction.isDebit) "Debit" else "Credit"
                
                csvContent.appendLine(
                    "\"$dateString\"," +
                    "\"${transaction.amount}\"," +
                    "\"$merchantString\"," +
                    "\"${transaction.bankName}\"," +
                    "\"$typeString\"," +
                    "\"$category\"," +
                    "\"${transaction.confidenceScore}\"," +
                    "\"${transaction.smsId}\""
                )
            }
            
            // Write to file
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                    outputStream.write(csvContent.toString().toByteArray())
                }
            }
            
            appLogger.info(TAG, "Successfully exported data to CSV")
            true
            
        } catch (e: Exception) {
            appLogger.error(TAG, "Error exporting data to CSV", e)
            false
        }
    }
    
    /**
     * Export filtered data by date range
     */
    suspend fun exportByDateRange(
        context: Context,
        uri: Uri,
        startDate: Date,
        endDate: Date,
        format: ExportFormat
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val transactions = repository.getTransactionsByDateRange(startDate, endDate)
            
            when (format) {
                ExportFormat.JSON -> {
                    val exportData = JSONObject()
                    
                    // Add metadata
                    val metadata = JSONObject().apply {
                        put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                        put("dateRange", "${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(startDate)} to ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(endDate)}")
                        put("totalTransactions", transactions.size)
                    }
                    exportData.put("metadata", metadata)
                    
                    // Add transactions
                    val transactionsArray = JSONArray()
                    transactions.forEach { transaction ->
                        val transactionJson = JSONObject().apply {
                            put("amount", transaction.amount)
                            put("merchant", transaction.rawMerchant)
                            put("bankName", transaction.bankName)
                            put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(transaction.transactionDate))
                            put("type", if (transaction.isDebit) "Debit" else "Credit")
                            put("confidence", transaction.confidenceScore)
                        }
                        transactionsArray.put(transactionJson)
                    }
                    exportData.put("transactions", transactionsArray)
                    
                    // Write JSON
                    context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                            outputStream.write(exportData.toString(2).toByteArray())
                        }
                    }
                }
                
                ExportFormat.CSV -> {
                    val csvContent = StringBuilder()
                    csvContent.appendLine("Date,Amount,Merchant,Bank,Type,Category,Confidence")
                    
                    transactions.forEach { transaction ->
                        val merchantWithCategory = repository.getMerchantWithCategory(transaction.normalizedMerchant)
                        val category = merchantWithCategory?.category_name ?: "Other"
                        
                        val dateString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(transaction.transactionDate)
                        val merchantString = transaction.rawMerchant.replace("\"", "\"\"")
                        val typeString = if (transaction.isDebit) "Debit" else "Credit"
                        
                        csvContent.appendLine(
                            "\"$dateString\"," +
                            "\"${transaction.amount}\"," +
                            "\"$merchantString\"," +
                            "\"${transaction.bankName}\"," +
                            "\"$typeString\"," +
                            "\"$category\"," +
                            "\"${transaction.confidenceScore}\""
                        )
                    }
                    
                    // Write CSV
                    context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                            outputStream.write(csvContent.toString().toByteArray())
                        }
                    }
                }
            }
            
            appLogger.info(TAG, "Successfully exported ${transactions.size} transactions for date range")
            true
            
        } catch (e: Exception) {
            appLogger.error(TAG, "Error exporting data by date range", e)
            false
        }
    }
    
    /**
     * Get app version for metadata
     */
    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "${packageInfo.versionName} (${packageInfo.longVersionCode})"
            } else {
                "${packageInfo.versionName} (${packageInfo.versionCode})"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Get export statistics
     */
    suspend fun getExportStatistics(): ExportStatistics = withContext(Dispatchers.IO) {
        try {
            val totalTransactions = repository.getTransactionCount()
            val categories = repository.getAllCategoriesSync()
            val merchants = repository.getAllMerchants()
            
            ExportStatistics(
                totalTransactions = totalTransactions,
                totalCategories = categories.size,
                totalMerchants = merchants.size,
                lastSyncDate = repository.getLastSyncTimestamp()
            )
        } catch (e: Exception) {
            appLogger.error(TAG, "Error getting export statistics", e)
            ExportStatistics(0, 0, 0, null)
        }
    }
}

enum class ExportFormat {
    JSON, CSV
}

data class ExportStatistics(
    val totalTransactions: Int,
    val totalCategories: Int,
    val totalMerchants: Int,
    val lastSyncDate: Date?
)