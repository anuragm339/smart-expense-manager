package com.expensemanager.app.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.expensemanager.app.data.converters.DateConverter;
import com.expensemanager.app.data.entities.SyncStateEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalStateException;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SyncStateDao_Impl implements SyncStateDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SyncStateEntity> __insertionAdapterOfSyncStateEntity;

  private final DateConverter __dateConverter = new DateConverter();

  private final SharedSQLiteStatement __preparedStmtOfUpdateSyncState;

  private final SharedSQLiteStatement __preparedStmtOfUpdateSyncStatus;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLastFullSync;

  private final SharedSQLiteStatement __preparedStmtOfUpdateTransactionCount;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSyncState;

  public SyncStateDao_Impl(RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSyncStateEntity = new EntityInsertionAdapter<SyncStateEntity>(__db) {
      @Override
      public String createQuery() {
        return "INSERT OR REPLACE INTO `sync_state` (`id`,`last_sms_sync_timestamp`,`last_sms_id`,`total_transactions`,`last_full_sync`,`sync_status`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, SyncStateEntity value) {
        stmt.bindLong(1, value.getId());
        final Long _tmp = __dateConverter.dateToTimestamp(value.getLastSmsSyncTimestamp());
        if (_tmp == null) {
          stmt.bindNull(2);
        } else {
          stmt.bindLong(2, _tmp);
        }
        if (value.getLastSmsId() == null) {
          stmt.bindNull(3);
        } else {
          stmt.bindString(3, value.getLastSmsId());
        }
        stmt.bindLong(4, value.getTotalTransactions());
        final Long _tmp_1 = __dateConverter.dateToTimestamp(value.getLastFullSync());
        if (_tmp_1 == null) {
          stmt.bindNull(5);
        } else {
          stmt.bindLong(5, _tmp_1);
        }
        if (value.getSyncStatus() == null) {
          stmt.bindNull(6);
        } else {
          stmt.bindString(6, value.getSyncStatus());
        }
      }
    };
    this.__preparedStmtOfUpdateSyncState = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE sync_state \n"
                + "        SET last_sms_sync_timestamp = ?, \n"
                + "            last_sms_id = ?,\n"
                + "            total_transactions = ?,\n"
                + "            sync_status = ?\n"
                + "        WHERE id = 1\n"
                + "    ";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateSyncStatus = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "UPDATE sync_state SET sync_status = ? WHERE id = 1";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateLastFullSync = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "UPDATE sync_state SET last_full_sync = ? WHERE id = 1";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateTransactionCount = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "UPDATE sync_state SET total_transactions = ? WHERE id = 1";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSyncState = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "DELETE FROM sync_state";
        return _query;
      }
    };
  }

  @Override
  public Object insertOrUpdateSyncState(final SyncStateEntity syncState,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSyncStateEntity.insert(syncState);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object updateSyncState(final Date timestamp, final String smsId,
      final int totalTransactions, final String status,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateSyncState.acquire();
        int _argIndex = 1;
        final Long _tmp = __dateConverter.dateToTimestamp(timestamp);
        if (_tmp == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, _tmp);
        }
        _argIndex = 2;
        if (smsId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, smsId);
        }
        _argIndex = 3;
        _stmt.bindLong(_argIndex, totalTransactions);
        _argIndex = 4;
        if (status == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, status);
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfUpdateSyncState.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object updateSyncStatus(final String status,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateSyncStatus.acquire();
        int _argIndex = 1;
        if (status == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, status);
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfUpdateSyncStatus.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object updateLastFullSync(final Date timestamp,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateLastFullSync.acquire();
        int _argIndex = 1;
        final Long _tmp = __dateConverter.dateToTimestamp(timestamp);
        if (_tmp == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, _tmp);
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfUpdateLastFullSync.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object updateTransactionCount(final int count,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateTransactionCount.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, count);
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfUpdateTransactionCount.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteSyncState(final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSyncState.acquire();
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfDeleteSyncState.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object getSyncState(final Continuation<? super SyncStateEntity> continuation) {
    final String _sql = "SELECT * FROM sync_state WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SyncStateEntity>() {
      @Override
      public SyncStateEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfLastSmsSyncTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "last_sms_sync_timestamp");
          final int _cursorIndexOfLastSmsId = CursorUtil.getColumnIndexOrThrow(_cursor, "last_sms_id");
          final int _cursorIndexOfTotalTransactions = CursorUtil.getColumnIndexOrThrow(_cursor, "total_transactions");
          final int _cursorIndexOfLastFullSync = CursorUtil.getColumnIndexOrThrow(_cursor, "last_full_sync");
          final int _cursorIndexOfSyncStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "sync_status");
          final SyncStateEntity _result;
          if(_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final Date _tmpLastSmsSyncTimestamp;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfLastSmsSyncTimestamp)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfLastSmsSyncTimestamp);
            }
            final Date _tmp_1 = __dateConverter.fromTimestamp(_tmp);
            if(_tmp_1 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpLastSmsSyncTimestamp = _tmp_1;
            }
            final String _tmpLastSmsId;
            if (_cursor.isNull(_cursorIndexOfLastSmsId)) {
              _tmpLastSmsId = null;
            } else {
              _tmpLastSmsId = _cursor.getString(_cursorIndexOfLastSmsId);
            }
            final int _tmpTotalTransactions;
            _tmpTotalTransactions = _cursor.getInt(_cursorIndexOfTotalTransactions);
            final Date _tmpLastFullSync;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfLastFullSync)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfLastFullSync);
            }
            final Date _tmp_3 = __dateConverter.fromTimestamp(_tmp_2);
            if(_tmp_3 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpLastFullSync = _tmp_3;
            }
            final String _tmpSyncStatus;
            if (_cursor.isNull(_cursorIndexOfSyncStatus)) {
              _tmpSyncStatus = null;
            } else {
              _tmpSyncStatus = _cursor.getString(_cursorIndexOfSyncStatus);
            }
            _result = new SyncStateEntity(_tmpId,_tmpLastSmsSyncTimestamp,_tmpLastSmsId,_tmpTotalTransactions,_tmpLastFullSync,_tmpSyncStatus);
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
  public Object getLastSyncTimestamp(final Continuation<? super Date> continuation) {
    final String _sql = "SELECT last_sms_sync_timestamp FROM sync_state WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Date>() {
      @Override
      public Date call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Date _result;
          if(_cursor.moveToFirst()) {
            final Long _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(0);
            }
            _result = __dateConverter.fromTimestamp(_tmp);
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
  public Object getSyncStatus(final Continuation<? super String> continuation) {
    final String _sql = "SELECT sync_status FROM sync_state WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<String>() {
      @Override
      public String call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final String _result;
          if(_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getString(0);
            }
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

  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
