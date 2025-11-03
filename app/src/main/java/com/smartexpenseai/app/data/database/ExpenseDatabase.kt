package com.smartexpenseai.app.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.smartexpenseai.app.data.converters.DateConverter
import com.smartexpenseai.app.data.entities.*
import com.smartexpenseai.app.data.dao.*

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        MerchantAliasEntity::class,
        SyncStateEntity::class,
        BudgetEntity::class,
        CategorySpendingCacheEntity::class,
        com.smartexpenseai.app.data.models.AICallTracker::class,
        UserEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class ExpenseDatabase : RoomDatabase() {
    
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun budgetDao(): BudgetDao
    abstract fun aiCallDao(): AICallDao
    abstract fun userDao(): UserDao
    
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
                .fallbackToDestructiveMigration()  // Temporarily use destructive migration for budget changes
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        // Migration from version 1 to 2: Add exclusion column to merchants table
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE merchants ADD COLUMN is_excluded_from_expense_tracking INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Migration from version 2 to 3: Add AI call tracking table
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop existing table if it exists (for development)
                database.execSQL("DROP TABLE IF EXISTS `ai_call_tracking`")

                // Create new table with correct schema
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_call_tracking` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `lastCallTimestamp` INTEGER NOT NULL,
                        `transactionCountAtLastCall` INTEGER NOT NULL,
                        `totalAmountAtLastCall` REAL NOT NULL,
                        `categoriesSnapshot` TEXT NOT NULL,
                        `nextEligibleCallTime` INTEGER NOT NULL,
                        `callFrequency` TEXT NOT NULL,
                        `totalApiCalls` INTEGER NOT NULL,
                        `lastErrorTimestamp` INTEGER NOT NULL,
                        `consecutiveErrors` INTEGER NOT NULL
                    )
                """)
            }
        }

        // Migration from version 3 to 4: Add users table for authentication
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `users` (
                        `userId` TEXT NOT NULL PRIMARY KEY,
                        `email` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `photoUrl` TEXT,
                        `isAuthenticated` INTEGER NOT NULL,
                        `lastLoginTimestamp` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
            }
        }

        // Migration from version 4 to 5: Add subscriptionTier column to users table
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE users ADD COLUMN subscriptionTier TEXT NOT NULL DEFAULT 'FREE'
                """)
            }
        }

        // Migration from version 5 to 6: Add tier-based call tracking to ai_call_tracking table
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE ai_call_tracking ADD COLUMN dailyCallCount INTEGER NOT NULL DEFAULT 0
                """)
                database.execSQL("""
                    ALTER TABLE ai_call_tracking ADD COLUMN lastDailyResetTimestamp INTEGER NOT NULL DEFAULT 0
                """)
                database.execSQL("""
                    ALTER TABLE ai_call_tracking ADD COLUMN monthlyCallCount INTEGER NOT NULL DEFAULT 0
                """)
                database.execSQL("""
                    ALTER TABLE ai_call_tracking ADD COLUMN lastMonthlyResetTimestamp INTEGER NOT NULL DEFAULT 0
                """)
            }
        }

        // Migration from version 6 to 7: Make category_id nullable in budgets table
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQLite doesn't support ALTER COLUMN directly, so we need to recreate the table

                // 1. Create new table with nullable category_id
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS budgets_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        category_id INTEGER,
                        budget_amount REAL NOT NULL,
                        period_type TEXT NOT NULL DEFAULT 'MONTHLY',
                        start_date INTEGER NOT NULL,
                        end_date INTEGER NOT NULL,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL
                    )
                """)

                // 2. Create index on category_id
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_budgets_new_category_id ON budgets_new(category_id)
                """)

                // 3. Copy data from old table to new table (if table exists)
                database.execSQL("""
                    INSERT INTO budgets_new (id, category_id, budget_amount, period_type, start_date, end_date, is_active, created_at)
                    SELECT id, category_id, budget_amount, period_type, start_date, end_date, is_active, created_at
                    FROM budgets
                    WHERE EXISTS (SELECT 1 FROM sqlite_master WHERE type='table' AND name='budgets')
                """)

                // 4. Drop old table (if exists)
                database.execSQL("DROP TABLE IF EXISTS budgets")

                // 5. Rename new table to budgets
                database.execSQL("ALTER TABLE budgets_new RENAME TO budgets")
            }
        }

        // Migration from version 7 to 8: Add reference_number column to transactions table
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE transactions ADD COLUMN reference_number TEXT DEFAULT NULL
                """)
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