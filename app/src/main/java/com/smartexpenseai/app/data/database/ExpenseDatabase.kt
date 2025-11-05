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
        com.smartexpenseai.app.data.models.AICallTracker::class,
        UserEntity::class
    ],
    version = 12,
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
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,   // Add category_id to transactions
                    MIGRATION_9_10,  // Fix "Other" category to have id=1
                    MIGRATION_10_11, // Remove unused category_spending_cache table
                    MIGRATION_11_12  // Fix inconsistent transaction category_ids
                )
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

        // Migration from version 8 to 9: Add category_id column to transactions table
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Step 1: Add category_id column with default value of 1 (Other category)
                database.execSQL("""
                    ALTER TABLE transactions ADD COLUMN category_id INTEGER NOT NULL DEFAULT 1
                """)

                // Step 2: Migrate existing data - copy category_id from merchants table
                // For each transaction, find its merchant and copy the merchant's category_id
                database.execSQL("""
                    UPDATE transactions
                    SET category_id = (
                        SELECT m.category_id
                        FROM merchants m
                        WHERE m.normalized_name = transactions.normalized_merchant
                        LIMIT 1
                    )
                    WHERE EXISTS (
                        SELECT 1
                        FROM merchants m
                        WHERE m.normalized_name = transactions.normalized_merchant
                    )
                """)

                // Step 3: For any transactions without a matching merchant, ensure they default to "Other" (category_id = 1)
                // Find the actual "Other" category ID (it might not be 1)
                database.execSQL("""
                    UPDATE transactions
                    SET category_id = (
                        SELECT id FROM categories WHERE name = 'Other' LIMIT 1
                    )
                    WHERE category_id = 1
                    AND NOT EXISTS (
                        SELECT 1 FROM categories WHERE id = 1
                    )
                """)
            }
        }

        // Migration from version 9 to 10: Ensure "Other" category has id=1
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Step 1: Find the current ID of "Other" category
                val otherCategoryId = database.query(
                    "SELECT id FROM categories WHERE name = 'Other' LIMIT 1"
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else null
                }

                // Step 2: If "Other" category exists but doesn't have id=1, fix it
                if (otherCategoryId != null && otherCategoryId != 1L) {
                    // Check if id=1 is already taken
                    val id1Exists = database.query(
                        "SELECT COUNT(*) FROM categories WHERE id = 1"
                    ).use { cursor ->
                        cursor.moveToFirst() && cursor.getInt(0) > 0
                    }

                    if (id1Exists) {
                        // Move the category at id=1 to a temporary ID
                        val maxId = database.query("SELECT MAX(id) FROM categories").use { cursor ->
                            if (cursor.moveToFirst()) cursor.getLong(0) else 100L
                        }
                        val tempId = maxId + 1

                        database.execSQL("UPDATE categories SET id = $tempId WHERE id = 1")
                        database.execSQL("UPDATE budgets SET category_id = $tempId WHERE category_id = 1")
                        database.execSQL("UPDATE merchants SET category_id = $tempId WHERE category_id = 1")
                        database.execSQL("UPDATE transactions SET category_id = $tempId WHERE category_id = 1")
                    }

                    // Update "Other" category to id=1
                    database.execSQL("UPDATE categories SET id = 1 WHERE id = $otherCategoryId")
                    database.execSQL("UPDATE budgets SET category_id = 1 WHERE category_id = $otherCategoryId")
                    database.execSQL("UPDATE merchants SET category_id = 1 WHERE category_id = $otherCategoryId")
                    database.execSQL("UPDATE transactions SET category_id = 1 WHERE category_id = $otherCategoryId")
                } else if (otherCategoryId == null) {
                    // "Other" category doesn't exist, create it with id=1
                    val currentTime = System.currentTimeMillis()
                    database.execSQL("""
                        INSERT INTO categories (id, name, emoji, color, is_system, display_order, created_at)
                        VALUES (1, 'Other', '📂', '#9e9e9e', 1, 8, $currentTime)
                    """)
                }
                // If otherCategoryId == 1, nothing to do - it's already correct
            }
        }

        // Migration from version 10 to 11: Remove unused category_spending_cache table
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop the unused category_spending_cache table
                database.execSQL("DROP TABLE IF EXISTS category_spending_cache")
            }
        }

        // Migration from version 11 to 12: Fix inconsistent transaction category_ids
        // This migration syncs all transactions' category_id with their merchant's category_id
        // to fix historical data inconsistencies where transactions and merchants had mismatched categories
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // STEP 0: Create backup of database before making changes
                try {
                    val dbPath = database.path
                    if (dbPath != null) {
                        val sourceFile = java.io.File(dbPath)
                        if (sourceFile.exists()) {
                            // Use database's own directory for backup (device-agnostic)
                            val dbDirectory = sourceFile.parentFile
                            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                                .format(java.util.Date())
                            val backupFile = java.io.File(dbDirectory, "expense_database_backup_v11_$timestamp.db")

                            // Copy database file
                            sourceFile.copyTo(backupFile, overwrite = true)

                            // Also backup WAL and SHM files if they exist
                            val walFile = java.io.File("$dbPath-wal")
                            if (walFile.exists()) {
                                walFile.copyTo(java.io.File(dbDirectory, "expense_database_backup_v11_$timestamp.db-wal"), overwrite = true)
                            }
                            val shmFile = java.io.File("$dbPath-shm")
                            if (shmFile.exists()) {
                                shmFile.copyTo(java.io.File(dbDirectory, "expense_database_backup_v11_$timestamp.db-shm"), overwrite = true)
                            }

                            android.util.Log.i("MIGRATION_11_12", "✅ Database backup created: ${backupFile.absolutePath}")
                        }
                    }
                } catch (e: Exception) {
                    // Log error but don't fail migration
                    android.util.Log.e("MIGRATION_11_12", "⚠️ Failed to create backup (continuing with migration): ${e.message}")
                }

                // STEP 1: Log statistics BEFORE migration
                val beforeStats = database.query("""
                    SELECT COUNT(*) as count FROM transactions t
                    JOIN merchants m ON t.normalized_merchant = m.normalized_name
                    WHERE t.category_id != m.category_id
                """).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                android.util.Log.i("MIGRATION_11_12", "Found $beforeStats inconsistent transactions to fix")

                // STEP 2: Sync all transactions' category_id with their merchant's category_id
                // This fixes cases where Messages tab showed one category (from merchant)
                // but Category Manager didn't show the transaction (mismatched transaction.category_id)
                database.execSQL("""
                    UPDATE transactions
                    SET category_id = (
                        SELECT m.category_id
                        FROM merchants m
                        WHERE m.normalized_name = transactions.normalized_merchant
                        LIMIT 1
                    )
                    WHERE EXISTS (
                        SELECT 1
                        FROM merchants m
                        WHERE m.normalized_name = transactions.normalized_merchant
                        AND m.category_id != transactions.category_id
                    )
                """)

                // STEP 3: Log statistics AFTER migration
                val afterStats = database.query("""
                    SELECT COUNT(*) as count FROM transactions t
                    JOIN merchants m ON t.normalized_merchant = m.normalized_name
                    WHERE t.category_id != m.category_id
                """).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                android.util.Log.i("MIGRATION_11_12", "✅ Migration complete. Fixed $beforeStats transactions. Remaining inconsistencies: $afterStats")
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

            // IMPORTANT: Insert "Other" first with explicit id=1 so transactions can default to it
            // Other categories get auto-generated IDs starting from 2
            db.execSQL("""
                INSERT OR IGNORE INTO categories
                (id, name, emoji, color, is_system, display_order, created_at)
                VALUES (1, 'Other', '📂', '#9e9e9e', 1, 8, $currentTime)
            """)

            val defaultCategories = listOf(
                "('Food & Dining', '🍽️', '#ff5722', 1, 1, $currentTime)",
                "('Transportation', '🚗', '#3f51b5', 1, 2, $currentTime)",
                "('Groceries', '🛒', '#4caf50', 1, 3, $currentTime)",
                "('Healthcare', '🏥', '#e91e63', 1, 4, $currentTime)",
                "('Entertainment', '🎬', '#9c27b0', 1, 5, $currentTime)",
                "('Shopping', '🛍️', '#ff9800', 1, 6, $currentTime)",
                "('Utilities', '⚡', '#607d8b', 1, 7, $currentTime)"
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