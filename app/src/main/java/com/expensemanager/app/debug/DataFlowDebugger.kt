package com.expensemanager.app.debug

import android.content.Context
import android.util.Log
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.data.storage.TransactionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Debug utility to diagnose data flow issues between different storage systems
 */
class DataFlowDebugger(private val context: Context) {
    
    companion object {
        private const val TAG = "DataFlowDebugger"
    }
    
    /**
     * Comprehensive diagnosis of data flow issue
     */
    suspend fun diagnoseDataFlowIssue(): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        
        try {
            report.appendLine("=== SMART EXPENSE MANAGER DATA FLOW DIAGNOSIS ===")
            report.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            report.appendLine()
            
            // 1. Check Legacy Storage (SharedPreferences)
            report.appendLine("1️⃣ LEGACY STORAGE (TransactionStorage - SharedPreferences)")
            checkLegacyStorage(report)
            report.appendLine()
            
            // 2. Check Modern Storage (Room Database)  
            report.appendLine("2️⃣ MODERN STORAGE (Room Database)")
            checkRoomDatabase(report)
            report.appendLine()
            
            // 3. Check Migration Status
            report.appendLine("3️⃣ MIGRATION STATUS")
            checkMigrationStatus(report)
            report.appendLine()
            
            // 4. Recommendations
            report.appendLine("4️⃣ DIAGNOSIS & RECOMMENDATIONS")
            provideDiagnosisAndRecommendations(report)
            
            report.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during diagnosis", e)
            "❌ Diagnosis failed: ${e.message}"
        }
    }
    
    private suspend fun checkLegacyStorage(report: StringBuilder) {
        try {
            val transactionStorage = TransactionStorage(context)
            val legacyTransactions = transactionStorage.loadTransactions()
            
            report.appendLine("📊 Legacy Transaction Count: ${legacyTransactions.size}")
            
            if (legacyTransactions.isNotEmpty()) {
                report.appendLine("✅ Legacy data EXISTS - this explains why SMS Messages screen works")
                
                // Show sample transactions
                report.appendLine("📝 Sample Legacy Transactions:")
                legacyTransactions.take(3).forEachIndexed { index, transaction ->
                    report.appendLine("   ${index + 1}. ${transaction.merchant} - ₹${String.format("%.2f", transaction.amount)} (${Date(transaction.date)})")
                }
                
                // Statistics
                val totalAmount = legacyTransactions.sumOf { it.amount }
                val uniqueMerchants = legacyTransactions.map { it.merchant }.distinct().size
                val uniqueBanks = legacyTransactions.map { it.bankName }.distinct()
                
                report.appendLine("📈 Legacy Data Statistics:")
                report.appendLine("   • Total Amount: ₹${String.format("%.2f", totalAmount)}")
                report.appendLine("   • Unique Merchants: $uniqueMerchants")
                report.appendLine("   • Banks: ${uniqueBanks.joinToString(", ")}")
                
            } else {
                report.appendLine("❌ No legacy data found - SMS parsing might not be working")
            }
            
        } catch (e: Exception) {
            report.appendLine("❌ Error checking legacy storage: ${e.message}")
            Log.e(TAG, "Error checking legacy storage", e)
        }
    }
    
    private suspend fun checkRoomDatabase(report: StringBuilder) {
        try {
            val expenseRepository = ExpenseRepository.getInstance(context)
            val roomTransactionCount = expenseRepository.getTransactionCount()
            
            report.appendLine("📊 Room Database Transaction Count: $roomTransactionCount")
            
            if (roomTransactionCount == 0) {
                report.appendLine("❌ Room database is EMPTY - this explains why Dashboard/Categories show no data")
                report.appendLine("🔍 The issue is confirmed: Data exists in SharedPreferences but not in Room")
            } else {
                report.appendLine("✅ Room database has data")
                
                // Get some sample transactions
                val currentMonth = Calendar.getInstance()
                currentMonth.set(Calendar.DAY_OF_MONTH, 1)
                val startDate = currentMonth.time
                currentMonth.add(Calendar.MONTH, 1)
                val endDate = currentMonth.time
                
                val roomTransactions = expenseRepository.getTransactionsByDateRange(startDate, endDate)
                report.appendLine("📝 Sample Room Transactions (this month):")
                roomTransactions.take(3).forEach { transaction ->
                    report.appendLine("   • ${transaction.rawMerchant} - ₹${String.format("%.2f", transaction.amount)} (${transaction.transactionDate})")
                }
            }
            
            // Check categories
            val categories = expenseRepository.getAllCategoriesSync()
            report.appendLine("📂 Room Categories: ${categories.size} categories")
            
            // Check merchants
            val merchants = expenseRepository.getAllMerchants()
            report.appendLine("🏪 Room Merchants: ${merchants.size} merchants")
            
        } catch (e: Exception) {
            report.appendLine("❌ Error checking Room database: ${e.message}")
            Log.e(TAG, "Error checking Room database", e)
        }
    }
    
    private fun checkMigrationStatus(report: StringBuilder) {
        try {
            val migrationPrefs = context.getSharedPreferences("app_migration", Context.MODE_PRIVATE)
            val migrationCompleted = migrationPrefs.getBoolean("migration_completed_v1", false)
            val smsImportCompleted = migrationPrefs.getBoolean("initial_sms_import_completed", false)
            val legacyMigrationCompleted = migrationPrefs.getBoolean("legacy_transaction_migration_completed", false)
            
            report.appendLine("🔄 Migration Status:")
            report.appendLine("   • Overall Migration: ${if (migrationCompleted) "✅ Completed" else "❌ Pending"}")
            report.appendLine("   • SMS Import: ${if (smsImportCompleted) "✅ Completed" else "❌ Pending"}")
            report.appendLine("   • Legacy Data Migration: ${if (legacyMigrationCompleted) "✅ Completed" else "❌ Pending"}")
            
        } catch (e: Exception) {
            report.appendLine("❌ Error checking migration status: ${e.message}")
        }
    }
    
    private suspend fun provideDiagnosisAndRecommendations(report: StringBuilder) {
        try {
            val transactionStorage = TransactionStorage(context)
            val legacyCount = transactionStorage.loadTransactions().size
            
            val expenseRepository = ExpenseRepository.getInstance(context)
            val roomCount = expenseRepository.getTransactionCount()
            
            if (legacyCount > 0 && roomCount == 0) {
                report.appendLine("🎯 ROOT CAUSE IDENTIFIED:")
                report.appendLine("   • SMS parsing is working correctly (Legacy storage has $legacyCount transactions)")
                report.appendLine("   • Dashboard/Categories screens read from Room database (0 transactions)")
                report.appendLine("   • Data migration from SharedPreferences to Room hasn't occurred")
                report.appendLine()
                report.appendLine("💡 SOLUTION:")
                report.appendLine("   1. The updated DataMigrationManager will automatically migrate data on next app restart")
                report.appendLine("   2. Or manually trigger migration through the debug settings")
                report.appendLine("   3. After migration, Dashboard and Categories will show your transaction data")
                
            } else if (legacyCount == 0 && roomCount == 0) {
                report.appendLine("🎯 DIAGNOSIS:")
                report.appendLine("   • No data in either storage system")
                report.appendLine("   • SMS parsing might not be working or no transaction SMS found")
                report.appendLine()
                report.appendLine("💡 RECOMMENDATIONS:")
                report.appendLine("   1. Check SMS permissions")
                report.appendLine("   2. Verify transaction SMS exist in phone")
                report.appendLine("   3. Test SMS parsing with known transaction SMS")
                
            } else if (legacyCount > 0 && roomCount > 0) {
                report.appendLine("🎯 DIAGNOSIS:")
                report.appendLine("   • Data exists in both systems ($legacyCount legacy, $roomCount room)")
                report.appendLine("   • Migration likely completed successfully")
                report.appendLine("   • If Dashboard/Categories still show no data, check filtering logic")
                
            } else {
                report.appendLine("🎯 DIAGNOSIS:")
                report.appendLine("   • Unusual state: Room has data ($roomCount) but legacy doesn't ($legacyCount)")
                report.appendLine("   • This might indicate successful migration and cleanup")
            }
            
        } catch (e: Exception) {
            report.appendLine("❌ Error providing diagnosis: ${e.message}")
        }
    }
}