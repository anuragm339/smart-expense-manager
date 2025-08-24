package com.expensemanager.app.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.expensemanager.app.data.converters.DateConverter;
import com.expensemanager.app.data.entities.MerchantAliasEntity;
import com.expensemanager.app.data.entities.MerchantEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalStateException;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MerchantDao_Impl implements MerchantDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MerchantEntity> __insertionAdapterOfMerchantEntity;

  private final DateConverter __dateConverter = new DateConverter();

  private final EntityInsertionAdapter<MerchantAliasEntity> __insertionAdapterOfMerchantAliasEntity;

  private final EntityDeletionOrUpdateAdapter<MerchantEntity> __deletionAdapterOfMerchantEntity;

  private final EntityDeletionOrUpdateAdapter<MerchantAliasEntity> __deletionAdapterOfMerchantAliasEntity;

  private final EntityDeletionOrUpdateAdapter<MerchantEntity> __updateAdapterOfMerchantEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteMerchantById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllMerchants;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAliasById;

  private final SharedSQLiteStatement __preparedStmtOfUpdateMerchantExclusion;

  private final SharedSQLiteStatement __preparedStmtOfUpdateMerchantExclusionById;

  public MerchantDao_Impl(RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMerchantEntity = new EntityInsertionAdapter<MerchantEntity>(__db) {
      @Override
      public String createQuery() {
        return "INSERT OR IGNORE INTO `merchants` (`id`,`normalized_name`,`display_name`,`category_id`,`is_user_defined`,`is_excluded_from_expense_tracking`,`created_at`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, MerchantEntity value) {
        stmt.bindLong(1, value.getId());
        if (value.getNormalizedName() == null) {
          stmt.bindNull(2);
        } else {
          stmt.bindString(2, value.getNormalizedName());
        }
        if (value.getDisplayName() == null) {
          stmt.bindNull(3);
        } else {
          stmt.bindString(3, value.getDisplayName());
        }
        stmt.bindLong(4, value.getCategoryId());
        final int _tmp = value.isUserDefined() ? 1 : 0;
        stmt.bindLong(5, _tmp);
        final int _tmp_1 = value.isExcludedFromExpenseTracking() ? 1 : 0;
        stmt.bindLong(6, _tmp_1);
        final Long _tmp_2 = __dateConverter.dateToTimestamp(value.getCreatedAt());
        if (_tmp_2 == null) {
          stmt.bindNull(7);
        } else {
          stmt.bindLong(7, _tmp_2);
        }
      }
    };
    this.__insertionAdapterOfMerchantAliasEntity = new EntityInsertionAdapter<MerchantAliasEntity>(__db) {
      @Override
      public String createQuery() {
        return "INSERT OR IGNORE INTO `merchant_aliases` (`id`,`merchant_id`,`alias_pattern`,`confidence`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, MerchantAliasEntity value) {
        stmt.bindLong(1, value.getId());
        stmt.bindLong(2, value.getMerchantId());
        if (value.getAliasPattern() == null) {
          stmt.bindNull(3);
        } else {
          stmt.bindString(3, value.getAliasPattern());
        }
        stmt.bindLong(4, value.getConfidence());
      }
    };
    this.__deletionAdapterOfMerchantEntity = new EntityDeletionOrUpdateAdapter<MerchantEntity>(__db) {
      @Override
      public String createQuery() {
        return "DELETE FROM `merchants` WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, MerchantEntity value) {
        stmt.bindLong(1, value.getId());
      }
    };
    this.__deletionAdapterOfMerchantAliasEntity = new EntityDeletionOrUpdateAdapter<MerchantAliasEntity>(__db) {
      @Override
      public String createQuery() {
        return "DELETE FROM `merchant_aliases` WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, MerchantAliasEntity value) {
        stmt.bindLong(1, value.getId());
      }
    };
    this.__updateAdapterOfMerchantEntity = new EntityDeletionOrUpdateAdapter<MerchantEntity>(__db) {
      @Override
      public String createQuery() {
        return "UPDATE OR ABORT `merchants` SET `id` = ?,`normalized_name` = ?,`display_name` = ?,`category_id` = ?,`is_user_defined` = ?,`is_excluded_from_expense_tracking` = ?,`created_at` = ? WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, MerchantEntity value) {
        stmt.bindLong(1, value.getId());
        if (value.getNormalizedName() == null) {
          stmt.bindNull(2);
        } else {
          stmt.bindString(2, value.getNormalizedName());
        }
        if (value.getDisplayName() == null) {
          stmt.bindNull(3);
        } else {
          stmt.bindString(3, value.getDisplayName());
        }
        stmt.bindLong(4, value.getCategoryId());
        final int _tmp = value.isUserDefined() ? 1 : 0;
        stmt.bindLong(5, _tmp);
        final int _tmp_1 = value.isExcludedFromExpenseTracking() ? 1 : 0;
        stmt.bindLong(6, _tmp_1);
        final Long _tmp_2 = __dateConverter.dateToTimestamp(value.getCreatedAt());
        if (_tmp_2 == null) {
          stmt.bindNull(7);
        } else {
          stmt.bindLong(7, _tmp_2);
        }
        stmt.bindLong(8, value.getId());
      }
    };
    this.__preparedStmtOfDeleteMerchantById = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "DELETE FROM merchants WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllMerchants = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "DELETE FROM merchants";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAliasById = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "DELETE FROM merchant_aliases WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateMerchantExclusion = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "UPDATE merchants SET is_excluded_from_expense_tracking = ? WHERE normalized_name = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateMerchantExclusionById = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "UPDATE merchants SET is_excluded_from_expense_tracking = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertMerchant(final MerchantEntity merchant,
      final Continuation<? super Long> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          long _result = __insertionAdapterOfMerchantEntity.insertAndReturnId(merchant);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object insertMerchants(final List<MerchantEntity> merchants,
      final Continuation<? super List<Long>> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          List<Long> _result = __insertionAdapterOfMerchantEntity.insertAndReturnIdsList(merchants);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object insertAlias(final MerchantAliasEntity alias,
      final Continuation<? super Long> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          long _result = __insertionAdapterOfMerchantAliasEntity.insertAndReturnId(alias);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object insertAliases(final List<MerchantAliasEntity> aliases,
      final Continuation<? super List<Long>> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          List<Long> _result = __insertionAdapterOfMerchantAliasEntity.insertAndReturnIdsList(aliases);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteMerchant(final MerchantEntity merchant,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfMerchantEntity.handle(merchant);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteAlias(final MerchantAliasEntity alias,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfMerchantAliasEntity.handle(alias);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object updateMerchant(final MerchantEntity merchant,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfMerchantEntity.handle(merchant);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteMerchantById(final long merchantId,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteMerchantById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, merchantId);
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfDeleteMerchantById.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteAllMerchants(final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllMerchants.acquire();
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfDeleteAllMerchants.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteAliasById(final long aliasId, final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAliasById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, aliasId);
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfDeleteAliasById.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object updateMerchantExclusion(final String normalizedName, final boolean isExcluded,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateMerchantExclusion.acquire();
        int _argIndex = 1;
        final int _tmp = isExcluded ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        if (normalizedName == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, normalizedName);
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfUpdateMerchantExclusion.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object updateMerchantExclusionById(final long merchantId, final boolean isExcluded,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateMerchantExclusionById.acquire();
        int _argIndex = 1;
        final int _tmp = isExcluded ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, merchantId);
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfUpdateMerchantExclusionById.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Flow<List<MerchantEntity>> getAllMerchants() {
    final String _sql = "SELECT * FROM merchants ORDER BY display_name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[]{"merchants"}, new Callable<List<MerchantEntity>>() {
      @Override
      public List<MerchantEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNormalizedName = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_name");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "display_name");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "category_id");
          final int _cursorIndexOfIsUserDefined = CursorUtil.getColumnIndexOrThrow(_cursor, "is_user_defined");
          final int _cursorIndexOfIsExcludedFromExpenseTracking = CursorUtil.getColumnIndexOrThrow(_cursor, "is_excluded_from_expense_tracking");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final List<MerchantEntity> _result = new ArrayList<MerchantEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final MerchantEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpNormalizedName;
            if (_cursor.isNull(_cursorIndexOfNormalizedName)) {
              _tmpNormalizedName = null;
            } else {
              _tmpNormalizedName = _cursor.getString(_cursorIndexOfNormalizedName);
            }
            final String _tmpDisplayName;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplayName = null;
            } else {
              _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCategoryId;
            _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            final boolean _tmpIsUserDefined;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUserDefined);
            _tmpIsUserDefined = _tmp != 0;
            final boolean _tmpIsExcludedFromExpenseTracking;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExcludedFromExpenseTracking);
            _tmpIsExcludedFromExpenseTracking = _tmp_1 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_3 = __dateConverter.fromTimestamp(_tmp_2);
            if(_tmp_3 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_3;
            }
            _item = new MerchantEntity(_tmpId,_tmpNormalizedName,_tmpDisplayName,_tmpCategoryId,_tmpIsUserDefined,_tmpIsExcludedFromExpenseTracking,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAllMerchantsSync(final Continuation<? super List<MerchantEntity>> continuation) {
    final String _sql = "SELECT * FROM merchants ORDER BY display_name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MerchantEntity>>() {
      @Override
      public List<MerchantEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNormalizedName = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_name");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "display_name");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "category_id");
          final int _cursorIndexOfIsUserDefined = CursorUtil.getColumnIndexOrThrow(_cursor, "is_user_defined");
          final int _cursorIndexOfIsExcludedFromExpenseTracking = CursorUtil.getColumnIndexOrThrow(_cursor, "is_excluded_from_expense_tracking");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final List<MerchantEntity> _result = new ArrayList<MerchantEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final MerchantEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpNormalizedName;
            if (_cursor.isNull(_cursorIndexOfNormalizedName)) {
              _tmpNormalizedName = null;
            } else {
              _tmpNormalizedName = _cursor.getString(_cursorIndexOfNormalizedName);
            }
            final String _tmpDisplayName;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplayName = null;
            } else {
              _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCategoryId;
            _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            final boolean _tmpIsUserDefined;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUserDefined);
            _tmpIsUserDefined = _tmp != 0;
            final boolean _tmpIsExcludedFromExpenseTracking;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExcludedFromExpenseTracking);
            _tmpIsExcludedFromExpenseTracking = _tmp_1 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_3 = __dateConverter.fromTimestamp(_tmp_2);
            if(_tmp_3 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_3;
            }
            _item = new MerchantEntity(_tmpId,_tmpNormalizedName,_tmpDisplayName,_tmpCategoryId,_tmpIsUserDefined,_tmpIsExcludedFromExpenseTracking,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, continuation);
  }

  @Override
  public Object getMerchantById(final long merchantId,
      final Continuation<? super MerchantEntity> continuation) {
    final String _sql = "SELECT * FROM merchants WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, merchantId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MerchantEntity>() {
      @Override
      public MerchantEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNormalizedName = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_name");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "display_name");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "category_id");
          final int _cursorIndexOfIsUserDefined = CursorUtil.getColumnIndexOrThrow(_cursor, "is_user_defined");
          final int _cursorIndexOfIsExcludedFromExpenseTracking = CursorUtil.getColumnIndexOrThrow(_cursor, "is_excluded_from_expense_tracking");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final MerchantEntity _result;
          if(_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpNormalizedName;
            if (_cursor.isNull(_cursorIndexOfNormalizedName)) {
              _tmpNormalizedName = null;
            } else {
              _tmpNormalizedName = _cursor.getString(_cursorIndexOfNormalizedName);
            }
            final String _tmpDisplayName;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplayName = null;
            } else {
              _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCategoryId;
            _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            final boolean _tmpIsUserDefined;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUserDefined);
            _tmpIsUserDefined = _tmp != 0;
            final boolean _tmpIsExcludedFromExpenseTracking;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExcludedFromExpenseTracking);
            _tmpIsExcludedFromExpenseTracking = _tmp_1 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_3 = __dateConverter.fromTimestamp(_tmp_2);
            if(_tmp_3 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_3;
            }
            _result = new MerchantEntity(_tmpId,_tmpNormalizedName,_tmpDisplayName,_tmpCategoryId,_tmpIsUserDefined,_tmpIsExcludedFromExpenseTracking,_tmpCreatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, continuation);
  }

  @Override
  public Object getMerchantByNormalizedName(final String normalizedName,
      final Continuation<? super MerchantEntity> continuation) {
    final String _sql = "SELECT * FROM merchants WHERE normalized_name = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (normalizedName == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, normalizedName);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MerchantEntity>() {
      @Override
      public MerchantEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNormalizedName = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_name");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "display_name");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "category_id");
          final int _cursorIndexOfIsUserDefined = CursorUtil.getColumnIndexOrThrow(_cursor, "is_user_defined");
          final int _cursorIndexOfIsExcludedFromExpenseTracking = CursorUtil.getColumnIndexOrThrow(_cursor, "is_excluded_from_expense_tracking");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final MerchantEntity _result;
          if(_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpNormalizedName;
            if (_cursor.isNull(_cursorIndexOfNormalizedName)) {
              _tmpNormalizedName = null;
            } else {
              _tmpNormalizedName = _cursor.getString(_cursorIndexOfNormalizedName);
            }
            final String _tmpDisplayName;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplayName = null;
            } else {
              _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCategoryId;
            _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            final boolean _tmpIsUserDefined;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUserDefined);
            _tmpIsUserDefined = _tmp != 0;
            final boolean _tmpIsExcludedFromExpenseTracking;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExcludedFromExpenseTracking);
            _tmpIsExcludedFromExpenseTracking = _tmp_1 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_3 = __dateConverter.fromTimestamp(_tmp_2);
            if(_tmp_3 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_3;
            }
            _result = new MerchantEntity(_tmpId,_tmpNormalizedName,_tmpDisplayName,_tmpCategoryId,_tmpIsUserDefined,_tmpIsExcludedFromExpenseTracking,_tmpCreatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, continuation);
  }

  @Override
  public Object getMerchantsByCategory(final long categoryId,
      final Continuation<? super List<MerchantEntity>> continuation) {
    final String _sql = "SELECT * FROM merchants WHERE category_id = ? ORDER BY display_name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, categoryId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MerchantEntity>>() {
      @Override
      public List<MerchantEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNormalizedName = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_name");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "display_name");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "category_id");
          final int _cursorIndexOfIsUserDefined = CursorUtil.getColumnIndexOrThrow(_cursor, "is_user_defined");
          final int _cursorIndexOfIsExcludedFromExpenseTracking = CursorUtil.getColumnIndexOrThrow(_cursor, "is_excluded_from_expense_tracking");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final List<MerchantEntity> _result = new ArrayList<MerchantEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final MerchantEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpNormalizedName;
            if (_cursor.isNull(_cursorIndexOfNormalizedName)) {
              _tmpNormalizedName = null;
            } else {
              _tmpNormalizedName = _cursor.getString(_cursorIndexOfNormalizedName);
            }
            final String _tmpDisplayName;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplayName = null;
            } else {
              _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCategoryId;
            _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            final boolean _tmpIsUserDefined;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUserDefined);
            _tmpIsUserDefined = _tmp != 0;
            final boolean _tmpIsExcludedFromExpenseTracking;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExcludedFromExpenseTracking);
            _tmpIsExcludedFromExpenseTracking = _tmp_1 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_3 = __dateConverter.fromTimestamp(_tmp_2);
            if(_tmp_3 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_3;
            }
            _item = new MerchantEntity(_tmpId,_tmpNormalizedName,_tmpDisplayName,_tmpCategoryId,_tmpIsUserDefined,_tmpIsExcludedFromExpenseTracking,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, continuation);
  }

  @Override
  public Object getMerchantWithCategory(final String normalizedName,
      final Continuation<? super MerchantWithCategory> continuation) {
    final String _sql = "\n"
            + "        SELECT m.*, c.name as category_name, c.color as category_color\n"
            + "        FROM merchants m \n"
            + "        JOIN categories c ON m.category_id = c.id \n"
            + "        WHERE m.normalized_name = ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (normalizedName == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, normalizedName);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MerchantWithCategory>() {
      @Override
      public MerchantWithCategory call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNormalizedName = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_name");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "display_name");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "category_id");
          final int _cursorIndexOfIsUserDefined = CursorUtil.getColumnIndexOrThrow(_cursor, "is_user_defined");
          final int _cursorIndexOfIsExcludedFromExpenseTracking = CursorUtil.getColumnIndexOrThrow(_cursor, "is_excluded_from_expense_tracking");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfCategoryName = CursorUtil.getColumnIndexOrThrow(_cursor, "category_name");
          final int _cursorIndexOfCategoryColor = CursorUtil.getColumnIndexOrThrow(_cursor, "category_color");
          final MerchantWithCategory _result;
          if(_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpNormalized_name;
            if (_cursor.isNull(_cursorIndexOfNormalizedName)) {
              _tmpNormalized_name = null;
            } else {
              _tmpNormalized_name = _cursor.getString(_cursorIndexOfNormalizedName);
            }
            final String _tmpDisplay_name;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplay_name = null;
            } else {
              _tmpDisplay_name = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCategory_id;
            _tmpCategory_id = _cursor.getLong(_cursorIndexOfCategoryId);
            final boolean _tmpIs_user_defined;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUserDefined);
            _tmpIs_user_defined = _tmp != 0;
            final boolean _tmpIs_excluded_from_expense_tracking;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExcludedFromExpenseTracking);
            _tmpIs_excluded_from_expense_tracking = _tmp_1 != 0;
            final Date _tmpCreated_at;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_3 = __dateConverter.fromTimestamp(_tmp_2);
            if(_tmp_3 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreated_at = _tmp_3;
            }
            final String _tmpCategory_name;
            if (_cursor.isNull(_cursorIndexOfCategoryName)) {
              _tmpCategory_name = null;
            } else {
              _tmpCategory_name = _cursor.getString(_cursorIndexOfCategoryName);
            }
            final String _tmpCategory_color;
            if (_cursor.isNull(_cursorIndexOfCategoryColor)) {
              _tmpCategory_color = null;
            } else {
              _tmpCategory_color = _cursor.getString(_cursorIndexOfCategoryColor);
            }
            _result = new MerchantWithCategory(_tmpId,_tmpNormalized_name,_tmpDisplay_name,_tmpCategory_id,_tmpIs_user_defined,_tmpIs_excluded_from_expense_tracking,_tmpCreated_at,_tmpCategory_name,_tmpCategory_color);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, continuation);
  }

  @Override
  public Object getAliasesForMerchant(final long merchantId,
      final Continuation<? super List<MerchantAliasEntity>> continuation) {
    final String _sql = "SELECT * FROM merchant_aliases WHERE merchant_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, merchantId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MerchantAliasEntity>>() {
      @Override
      public List<MerchantAliasEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMerchantId = CursorUtil.getColumnIndexOrThrow(_cursor, "merchant_id");
          final int _cursorIndexOfAliasPattern = CursorUtil.getColumnIndexOrThrow(_cursor, "alias_pattern");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final List<MerchantAliasEntity> _result = new ArrayList<MerchantAliasEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final MerchantAliasEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMerchantId;
            _tmpMerchantId = _cursor.getLong(_cursorIndexOfMerchantId);
            final String _tmpAliasPattern;
            if (_cursor.isNull(_cursorIndexOfAliasPattern)) {
              _tmpAliasPattern = null;
            } else {
              _tmpAliasPattern = _cursor.getString(_cursorIndexOfAliasPattern);
            }
            final int _tmpConfidence;
            _tmpConfidence = _cursor.getInt(_cursorIndexOfConfidence);
            _item = new MerchantAliasEntity(_tmpId,_tmpMerchantId,_tmpAliasPattern,_tmpConfidence);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, continuation);
  }

  @Override
  public Object findMerchantByAliasPattern(final String pattern,
      final Continuation<? super List<MerchantAliasWithMerchant>> continuation) {
    final String _sql = "\n"
            + "        SELECT ma.*, m.display_name, m.category_id \n"
            + "        FROM merchant_aliases ma\n"
            + "        JOIN merchants m ON ma.merchant_id = m.id\n"
            + "        WHERE ma.alias_pattern LIKE '%' || ? || '%'\n"
            + "        ORDER BY ma.confidence DESC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (pattern == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, pattern);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MerchantAliasWithMerchant>>() {
      @Override
      public List<MerchantAliasWithMerchant> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMerchantId = CursorUtil.getColumnIndexOrThrow(_cursor, "merchant_id");
          final int _cursorIndexOfAliasPattern = CursorUtil.getColumnIndexOrThrow(_cursor, "alias_pattern");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "display_name");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "category_id");
          final List<MerchantAliasWithMerchant> _result = new ArrayList<MerchantAliasWithMerchant>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final MerchantAliasWithMerchant _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMerchant_id;
            _tmpMerchant_id = _cursor.getLong(_cursorIndexOfMerchantId);
            final String _tmpAlias_pattern;
            if (_cursor.isNull(_cursorIndexOfAliasPattern)) {
              _tmpAlias_pattern = null;
            } else {
              _tmpAlias_pattern = _cursor.getString(_cursorIndexOfAliasPattern);
            }
            final int _tmpConfidence;
            _tmpConfidence = _cursor.getInt(_cursorIndexOfConfidence);
            final String _tmpDisplay_name;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplay_name = null;
            } else {
              _tmpDisplay_name = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCategory_id;
            _tmpCategory_id = _cursor.getLong(_cursorIndexOfCategoryId);
            _item = new MerchantAliasWithMerchant(_tmpId,_tmpMerchant_id,_tmpAlias_pattern,_tmpConfidence,_tmpDisplay_name,_tmpCategory_id);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, continuation);
  }

  @Override
  public Object getExcludedMerchants(
      final Continuation<? super List<MerchantEntity>> continuation) {
    final String _sql = "SELECT * FROM merchants WHERE is_excluded_from_expense_tracking = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MerchantEntity>>() {
      @Override
      public List<MerchantEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNormalizedName = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_name");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "display_name");
          final int _cursorIndexOfCategoryId = CursorUtil.getColumnIndexOrThrow(_cursor, "category_id");
          final int _cursorIndexOfIsUserDefined = CursorUtil.getColumnIndexOrThrow(_cursor, "is_user_defined");
          final int _cursorIndexOfIsExcludedFromExpenseTracking = CursorUtil.getColumnIndexOrThrow(_cursor, "is_excluded_from_expense_tracking");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final List<MerchantEntity> _result = new ArrayList<MerchantEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final MerchantEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpNormalizedName;
            if (_cursor.isNull(_cursorIndexOfNormalizedName)) {
              _tmpNormalizedName = null;
            } else {
              _tmpNormalizedName = _cursor.getString(_cursorIndexOfNormalizedName);
            }
            final String _tmpDisplayName;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplayName = null;
            } else {
              _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCategoryId;
            _tmpCategoryId = _cursor.getLong(_cursorIndexOfCategoryId);
            final boolean _tmpIsUserDefined;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsUserDefined);
            _tmpIsUserDefined = _tmp != 0;
            final boolean _tmpIsExcludedFromExpenseTracking;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExcludedFromExpenseTracking);
            _tmpIsExcludedFromExpenseTracking = _tmp_1 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_3 = __dateConverter.fromTimestamp(_tmp_2);
            if(_tmp_3 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_3;
            }
            _item = new MerchantEntity(_tmpId,_tmpNormalizedName,_tmpDisplayName,_tmpCategoryId,_tmpIsUserDefined,_tmpIsExcludedFromExpenseTracking,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, continuation);
  }

  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
