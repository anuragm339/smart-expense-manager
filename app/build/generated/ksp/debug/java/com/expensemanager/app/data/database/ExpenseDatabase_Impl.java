package com.expensemanager.app.data.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomOpenHelper;
import androidx.room.RoomOpenHelper.Delegate;
import androidx.room.RoomOpenHelper.ValidationResult;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.room.util.TableInfo.Column;
import androidx.room.util.TableInfo.ForeignKey;
import androidx.room.util.TableInfo.Index;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.SupportSQLiteOpenHelper.Callback;
import androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration;
import com.expensemanager.app.data.dao.CategoryDao;
import com.expensemanager.app.data.dao.CategoryDao_Impl;
import com.expensemanager.app.data.dao.MerchantDao;
import com.expensemanager.app.data.dao.MerchantDao_Impl;
import com.expensemanager.app.data.dao.SyncStateDao;
import com.expensemanager.app.data.dao.SyncStateDao_Impl;
import com.expensemanager.app.data.dao.TransactionDao;
import com.expensemanager.app.data.dao.TransactionDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ExpenseDatabase_Impl extends ExpenseDatabase {
  private volatile TransactionDao _transactionDao;

  private volatile CategoryDao _categoryDao;

  private volatile MerchantDao _merchantDao;

  private volatile SyncStateDao _syncStateDao;

  @Override
  protected SupportSQLiteOpenHelper createOpenHelper(DatabaseConfiguration configuration) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(configuration, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(SupportSQLiteDatabase _db) {
        _db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sms_id` TEXT NOT NULL, `amount` REAL NOT NULL, `raw_merchant` TEXT NOT NULL, `normalized_merchant` TEXT NOT NULL, `bank_name` TEXT NOT NULL, `transaction_date` INTEGER NOT NULL, `raw_sms_body` TEXT NOT NULL, `confidence_score` REAL NOT NULL, `is_debit` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)");
        _db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `color` TEXT NOT NULL, `is_system` INTEGER NOT NULL, `display_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)");
        _db.execSQL("CREATE TABLE IF NOT EXISTS `merchants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `normalized_name` TEXT NOT NULL, `display_name` TEXT NOT NULL, `category_id` INTEGER NOT NULL, `is_user_defined` INTEGER NOT NULL, `is_excluded_from_expense_tracking` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        _db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchants_category_id` ON `merchants` (`category_id`)");
        _db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_merchants_normalized_name` ON `merchants` (`normalized_name`)");
        _db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_aliases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `merchant_id` INTEGER NOT NULL, `alias_pattern` TEXT NOT NULL, `confidence` INTEGER NOT NULL, FOREIGN KEY(`merchant_id`) REFERENCES `merchants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        _db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_aliases_merchant_id` ON `merchant_aliases` (`merchant_id`)");
        _db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_aliases_alias_pattern` ON `merchant_aliases` (`alias_pattern`)");
        _db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_merchant_aliases_merchant_id_alias_pattern` ON `merchant_aliases` (`merchant_id`, `alias_pattern`)");
        _db.execSQL("CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `last_sms_sync_timestamp` INTEGER NOT NULL, `last_sms_id` TEXT, `total_transactions` INTEGER NOT NULL, `last_full_sync` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, PRIMARY KEY(`id`))");
        _db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category_id` INTEGER NOT NULL, `budget_amount` REAL NOT NULL, `period_type` TEXT NOT NULL, `start_date` INTEGER NOT NULL, `end_date` INTEGER NOT NULL, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        _db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_category_id` ON `budgets` (`category_id`)");
        _db.execSQL("CREATE TABLE IF NOT EXISTS `category_spending_cache` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category_id` INTEGER NOT NULL, `period_start` INTEGER NOT NULL, `period_end` INTEGER NOT NULL, `total_amount` REAL NOT NULL, `transaction_count` INTEGER NOT NULL, `last_transaction_date` INTEGER, `cache_timestamp` INTEGER NOT NULL, FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        _db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_spending_cache_category_id` ON `category_spending_cache` (`category_id`)");
        _db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_spending_cache_category_id_period_start_period_end` ON `category_spending_cache` (`category_id`, `period_start`, `period_end`)");
        _db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        _db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'bd0dd6203bb46a851ebcf335b2b4c52c')");
      }

      @Override
      public void dropAllTables(SupportSQLiteDatabase _db) {
        _db.execSQL("DROP TABLE IF EXISTS `transactions`");
        _db.execSQL("DROP TABLE IF EXISTS `categories`");
        _db.execSQL("DROP TABLE IF EXISTS `merchants`");
        _db.execSQL("DROP TABLE IF EXISTS `merchant_aliases`");
        _db.execSQL("DROP TABLE IF EXISTS `sync_state`");
        _db.execSQL("DROP TABLE IF EXISTS `budgets`");
        _db.execSQL("DROP TABLE IF EXISTS `category_spending_cache`");
        if (mCallbacks != null) {
          for (int _i = 0, _size = mCallbacks.size(); _i < _size; _i++) {
            mCallbacks.get(_i).onDestructiveMigration(_db);
          }
        }
      }

      @Override
      public void onCreate(SupportSQLiteDatabase _db) {
        if (mCallbacks != null) {
          for (int _i = 0, _size = mCallbacks.size(); _i < _size; _i++) {
            mCallbacks.get(_i).onCreate(_db);
          }
        }
      }

      @Override
      public void onOpen(SupportSQLiteDatabase _db) {
        mDatabase = _db;
        _db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(_db);
        if (mCallbacks != null) {
          for (int _i = 0, _size = mCallbacks.size(); _i < _size; _i++) {
            mCallbacks.get(_i).onOpen(_db);
          }
        }
      }

      @Override
      public void onPreMigrate(SupportSQLiteDatabase _db) {
        DBUtil.dropFtsSyncTriggers(_db);
      }

      @Override
      public void onPostMigrate(SupportSQLiteDatabase _db) {
      }

      @Override
      public RoomOpenHelper.ValidationResult onValidateSchema(SupportSQLiteDatabase _db) {
        final HashMap<String, TableInfo.Column> _columnsTransactions = new HashMap<String, TableInfo.Column>(12);
        _columnsTransactions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("sms_id", new TableInfo.Column("sms_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("raw_merchant", new TableInfo.Column("raw_merchant", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("normalized_merchant", new TableInfo.Column("normalized_merchant", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("bank_name", new TableInfo.Column("bank_name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("transaction_date", new TableInfo.Column("transaction_date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("raw_sms_body", new TableInfo.Column("raw_sms_body", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("confidence_score", new TableInfo.Column("confidence_score", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("is_debit", new TableInfo.Column("is_debit", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("updated_at", new TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTransactions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTransactions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTransactions = new TableInfo("transactions", _columnsTransactions, _foreignKeysTransactions, _indicesTransactions);
        final TableInfo _existingTransactions = TableInfo.read(_db, "transactions");
        if (! _infoTransactions.equals(_existingTransactions)) {
          return new RoomOpenHelper.ValidationResult(false, "transactions(com.expensemanager.app.data.entities.TransactionEntity).\n"
                  + " Expected:\n" + _infoTransactions + "\n"
                  + " Found:\n" + _existingTransactions);
        }
        final HashMap<String, TableInfo.Column> _columnsCategories = new HashMap<String, TableInfo.Column>(7);
        _columnsCategories.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("emoji", new TableInfo.Column("emoji", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("color", new TableInfo.Column("color", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("is_system", new TableInfo.Column("is_system", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("display_order", new TableInfo.Column("display_order", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCategories = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCategories = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCategories = new TableInfo("categories", _columnsCategories, _foreignKeysCategories, _indicesCategories);
        final TableInfo _existingCategories = TableInfo.read(_db, "categories");
        if (! _infoCategories.equals(_existingCategories)) {
          return new RoomOpenHelper.ValidationResult(false, "categories(com.expensemanager.app.data.entities.CategoryEntity).\n"
                  + " Expected:\n" + _infoCategories + "\n"
                  + " Found:\n" + _existingCategories);
        }
        final HashMap<String, TableInfo.Column> _columnsMerchants = new HashMap<String, TableInfo.Column>(7);
        _columnsMerchants.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchants.put("normalized_name", new TableInfo.Column("normalized_name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchants.put("display_name", new TableInfo.Column("display_name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchants.put("category_id", new TableInfo.Column("category_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchants.put("is_user_defined", new TableInfo.Column("is_user_defined", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchants.put("is_excluded_from_expense_tracking", new TableInfo.Column("is_excluded_from_expense_tracking", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchants.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMerchants = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysMerchants.add(new TableInfo.ForeignKey("categories", "CASCADE", "NO ACTION",Arrays.asList("category_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesMerchants = new HashSet<TableInfo.Index>(2);
        _indicesMerchants.add(new TableInfo.Index("index_merchants_category_id", false, Arrays.asList("category_id"), Arrays.asList("ASC")));
        _indicesMerchants.add(new TableInfo.Index("index_merchants_normalized_name", true, Arrays.asList("normalized_name"), Arrays.asList("ASC")));
        final TableInfo _infoMerchants = new TableInfo("merchants", _columnsMerchants, _foreignKeysMerchants, _indicesMerchants);
        final TableInfo _existingMerchants = TableInfo.read(_db, "merchants");
        if (! _infoMerchants.equals(_existingMerchants)) {
          return new RoomOpenHelper.ValidationResult(false, "merchants(com.expensemanager.app.data.entities.MerchantEntity).\n"
                  + " Expected:\n" + _infoMerchants + "\n"
                  + " Found:\n" + _existingMerchants);
        }
        final HashMap<String, TableInfo.Column> _columnsMerchantAliases = new HashMap<String, TableInfo.Column>(4);
        _columnsMerchantAliases.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantAliases.put("merchant_id", new TableInfo.Column("merchant_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantAliases.put("alias_pattern", new TableInfo.Column("alias_pattern", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMerchantAliases.put("confidence", new TableInfo.Column("confidence", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMerchantAliases = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysMerchantAliases.add(new TableInfo.ForeignKey("merchants", "CASCADE", "NO ACTION",Arrays.asList("merchant_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesMerchantAliases = new HashSet<TableInfo.Index>(3);
        _indicesMerchantAliases.add(new TableInfo.Index("index_merchant_aliases_merchant_id", false, Arrays.asList("merchant_id"), Arrays.asList("ASC")));
        _indicesMerchantAliases.add(new TableInfo.Index("index_merchant_aliases_alias_pattern", false, Arrays.asList("alias_pattern"), Arrays.asList("ASC")));
        _indicesMerchantAliases.add(new TableInfo.Index("index_merchant_aliases_merchant_id_alias_pattern", true, Arrays.asList("merchant_id","alias_pattern"), Arrays.asList("ASC","ASC")));
        final TableInfo _infoMerchantAliases = new TableInfo("merchant_aliases", _columnsMerchantAliases, _foreignKeysMerchantAliases, _indicesMerchantAliases);
        final TableInfo _existingMerchantAliases = TableInfo.read(_db, "merchant_aliases");
        if (! _infoMerchantAliases.equals(_existingMerchantAliases)) {
          return new RoomOpenHelper.ValidationResult(false, "merchant_aliases(com.expensemanager.app.data.entities.MerchantAliasEntity).\n"
                  + " Expected:\n" + _infoMerchantAliases + "\n"
                  + " Found:\n" + _existingMerchantAliases);
        }
        final HashMap<String, TableInfo.Column> _columnsSyncState = new HashMap<String, TableInfo.Column>(6);
        _columnsSyncState.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncState.put("last_sms_sync_timestamp", new TableInfo.Column("last_sms_sync_timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncState.put("last_sms_id", new TableInfo.Column("last_sms_id", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncState.put("total_transactions", new TableInfo.Column("total_transactions", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncState.put("last_full_sync", new TableInfo.Column("last_full_sync", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncState.put("sync_status", new TableInfo.Column("sync_status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSyncState = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSyncState = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSyncState = new TableInfo("sync_state", _columnsSyncState, _foreignKeysSyncState, _indicesSyncState);
        final TableInfo _existingSyncState = TableInfo.read(_db, "sync_state");
        if (! _infoSyncState.equals(_existingSyncState)) {
          return new RoomOpenHelper.ValidationResult(false, "sync_state(com.expensemanager.app.data.entities.SyncStateEntity).\n"
                  + " Expected:\n" + _infoSyncState + "\n"
                  + " Found:\n" + _existingSyncState);
        }
        final HashMap<String, TableInfo.Column> _columnsBudgets = new HashMap<String, TableInfo.Column>(8);
        _columnsBudgets.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("category_id", new TableInfo.Column("category_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("budget_amount", new TableInfo.Column("budget_amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("period_type", new TableInfo.Column("period_type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("start_date", new TableInfo.Column("start_date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("end_date", new TableInfo.Column("end_date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("is_active", new TableInfo.Column("is_active", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBudgets.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBudgets = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysBudgets.add(new TableInfo.ForeignKey("categories", "CASCADE", "NO ACTION",Arrays.asList("category_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesBudgets = new HashSet<TableInfo.Index>(1);
        _indicesBudgets.add(new TableInfo.Index("index_budgets_category_id", false, Arrays.asList("category_id"), Arrays.asList("ASC")));
        final TableInfo _infoBudgets = new TableInfo("budgets", _columnsBudgets, _foreignKeysBudgets, _indicesBudgets);
        final TableInfo _existingBudgets = TableInfo.read(_db, "budgets");
        if (! _infoBudgets.equals(_existingBudgets)) {
          return new RoomOpenHelper.ValidationResult(false, "budgets(com.expensemanager.app.data.entities.BudgetEntity).\n"
                  + " Expected:\n" + _infoBudgets + "\n"
                  + " Found:\n" + _existingBudgets);
        }
        final HashMap<String, TableInfo.Column> _columnsCategorySpendingCache = new HashMap<String, TableInfo.Column>(8);
        _columnsCategorySpendingCache.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategorySpendingCache.put("category_id", new TableInfo.Column("category_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategorySpendingCache.put("period_start", new TableInfo.Column("period_start", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategorySpendingCache.put("period_end", new TableInfo.Column("period_end", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategorySpendingCache.put("total_amount", new TableInfo.Column("total_amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategorySpendingCache.put("transaction_count", new TableInfo.Column("transaction_count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategorySpendingCache.put("last_transaction_date", new TableInfo.Column("last_transaction_date", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategorySpendingCache.put("cache_timestamp", new TableInfo.Column("cache_timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCategorySpendingCache = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysCategorySpendingCache.add(new TableInfo.ForeignKey("categories", "CASCADE", "NO ACTION",Arrays.asList("category_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesCategorySpendingCache = new HashSet<TableInfo.Index>(2);
        _indicesCategorySpendingCache.add(new TableInfo.Index("index_category_spending_cache_category_id", false, Arrays.asList("category_id"), Arrays.asList("ASC")));
        _indicesCategorySpendingCache.add(new TableInfo.Index("index_category_spending_cache_category_id_period_start_period_end", true, Arrays.asList("category_id","period_start","period_end"), Arrays.asList("ASC","ASC","ASC")));
        final TableInfo _infoCategorySpendingCache = new TableInfo("category_spending_cache", _columnsCategorySpendingCache, _foreignKeysCategorySpendingCache, _indicesCategorySpendingCache);
        final TableInfo _existingCategorySpendingCache = TableInfo.read(_db, "category_spending_cache");
        if (! _infoCategorySpendingCache.equals(_existingCategorySpendingCache)) {
          return new RoomOpenHelper.ValidationResult(false, "category_spending_cache(com.expensemanager.app.data.entities.CategorySpendingCacheEntity).\n"
                  + " Expected:\n" + _infoCategorySpendingCache + "\n"
                  + " Found:\n" + _existingCategorySpendingCache);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "bd0dd6203bb46a851ebcf335b2b4c52c", "8f0e69440995210ef231c0ae8aebae62");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
        .name(configuration.name)
        .callback(_openCallback)
        .build();
    final SupportSQLiteOpenHelper _helper = configuration.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "transactions","categories","merchants","merchant_aliases","sync_state","budgets","category_spending_cache");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `transactions`");
      _db.execSQL("DELETE FROM `categories`");
      _db.execSQL("DELETE FROM `merchants`");
      _db.execSQL("DELETE FROM `merchant_aliases`");
      _db.execSQL("DELETE FROM `sync_state`");
      _db.execSQL("DELETE FROM `budgets`");
      _db.execSQL("DELETE FROM `category_spending_cache`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(TransactionDao.class, TransactionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CategoryDao.class, CategoryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(MerchantDao.class, MerchantDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SyncStateDao.class, SyncStateDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  public List<Migration> getAutoMigrations(
      @NonNull Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecsMap) {
    return Arrays.asList();
  }

  @Override
  public TransactionDao transactionDao() {
    if (_transactionDao != null) {
      return _transactionDao;
    } else {
      synchronized(this) {
        if(_transactionDao == null) {
          _transactionDao = new TransactionDao_Impl(this);
        }
        return _transactionDao;
      }
    }
  }

  @Override
  public CategoryDao categoryDao() {
    if (_categoryDao != null) {
      return _categoryDao;
    } else {
      synchronized(this) {
        if(_categoryDao == null) {
          _categoryDao = new CategoryDao_Impl(this);
        }
        return _categoryDao;
      }
    }
  }

  @Override
  public MerchantDao merchantDao() {
    if (_merchantDao != null) {
      return _merchantDao;
    } else {
      synchronized(this) {
        if(_merchantDao == null) {
          _merchantDao = new MerchantDao_Impl(this);
        }
        return _merchantDao;
      }
    }
  }

  @Override
  public SyncStateDao syncStateDao() {
    if (_syncStateDao != null) {
      return _syncStateDao;
    } else {
      synchronized(this) {
        if(_syncStateDao == null) {
          _syncStateDao = new SyncStateDao_Impl(this);
        }
        return _syncStateDao;
      }
    }
  }
}
