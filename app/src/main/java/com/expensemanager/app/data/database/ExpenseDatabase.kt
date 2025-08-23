package com.expensemanager.app.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.expensemanager.app.data.converters.DateConverter
import com.expensemanager.app.data.entities.*
import com.expensemanager.app.data.dao.*

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        MerchantAliasEntity::class,
        SyncStateEntity::class,
        BudgetEntity::class,
        CategorySpendingCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class ExpenseDatabase : RoomDatabase() {
    
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun syncStateDao(): SyncStateDao
    
    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null
        
        fun getDatabase(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "expense_database"
                )
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
    
    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Pre-populate with default categories
            insertDefaultCategories(db)
        }
        
        private fun insertDefaultCategories(db: SupportSQLiteDatabase) {
            val currentTime = System.currentTimeMillis()
            
            val defaultCategories = listOf(
                "('Food & Dining', '🍽️', '#ff5722', 1, 1, $currentTime)",
                "('Transportation', '🚗', '#3f51b5', 1, 2, $currentTime)",
                "('Groceries', '🛒', '#4caf50', 1, 3, $currentTime)",
                "('Healthcare', '🏥', '#e91e63', 1, 4, $currentTime)",
                "('Entertainment', '🎬', '#9c27b0', 1, 5, $currentTime)",
                "('Shopping', '🛍️', '#ff9800', 1, 6, $currentTime)",
                "('Utilities', '⚡', '#607d8b', 1, 7, $currentTime)",
                "('Other', '📂', '#9e9e9e', 1, 8, $currentTime)"
            )
            
            for (category in defaultCategories) {
                db.execSQL("""
                    INSERT OR IGNORE INTO categories 
                    (name, emoji, color, is_system, display_order, created_at) 
                    VALUES $category
                """)
            }
            
            // Initialize sync state
            db.execSQL("""
                INSERT OR IGNORE INTO sync_state 
                (id, last_sms_sync_timestamp, last_sms_id, total_transactions, last_full_sync, sync_status)
                VALUES (1, 0, null, 0, $currentTime, 'INITIAL')
            """)
        }
    }
}