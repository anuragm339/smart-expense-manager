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
import com.expensemanager.app.data.entities.TransactionEntity;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.IllegalStateException;
import java.lang.Integer;
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
public final class TransactionDao_Impl implements TransactionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TransactionEntity> __insertionAdapterOfTransactionEntity;

  private final DateConverter __dateConverter = new DateConverter();

  private final EntityDeletionOrUpdateAdapter<TransactionEntity> __deletionAdapterOfTransactionEntity;

  private final EntityDeletionOrUpdateAdapter<TransactionEntity> __updateAdapterOfTransactionEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteTransactionById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllTransactions;

  public TransactionDao_Impl(RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTransactionEntity = new EntityInsertionAdapter<TransactionEntity>(__db) {
      @Override
      public String createQuery() {
        return "INSERT OR IGNORE INTO `transactions` (`id`,`sms_id`,`amount`,`raw_merchant`,`normalized_merchant`,`bank_name`,`transaction_date`,`raw_sms_body`,`confidence_score`,`is_debit`,`created_at`,`updated_at`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, TransactionEntity value) {
        stmt.bindLong(1, value.getId());
        if (value.getSmsId() == null) {
          stmt.bindNull(2);
        } else {
          stmt.bindString(2, value.getSmsId());
        }
        stmt.bindDouble(3, value.getAmount());
        if (value.getRawMerchant() == null) {
          stmt.bindNull(4);
        } else {
          stmt.bindString(4, value.getRawMerchant());
        }
        if (value.getNormalizedMerchant() == null) {
          stmt.bindNull(5);
        } else {
          stmt.bindString(5, value.getNormalizedMerchant());
        }
        if (value.getBankName() == null) {
          stmt.bindNull(6);
        } else {
          stmt.bindString(6, value.getBankName());
        }
        final Long _tmp = __dateConverter.dateToTimestamp(value.getTransactionDate());
        if (_tmp == null) {
          stmt.bindNull(7);
        } else {
          stmt.bindLong(7, _tmp);
        }
        if (value.getRawSmsBody() == null) {
          stmt.bindNull(8);
        } else {
          stmt.bindString(8, value.getRawSmsBody());
        }
        stmt.bindDouble(9, value.getConfidenceScore());
        final int _tmp_1 = value.isDebit() ? 1 : 0;
        stmt.bindLong(10, _tmp_1);
        final Long _tmp_2 = __dateConverter.dateToTimestamp(value.getCreatedAt());
        if (_tmp_2 == null) {
          stmt.bindNull(11);
        } else {
          stmt.bindLong(11, _tmp_2);
        }
        final Long _tmp_3 = __dateConverter.dateToTimestamp(value.getUpdatedAt());
        if (_tmp_3 == null) {
          stmt.bindNull(12);
        } else {
          stmt.bindLong(12, _tmp_3);
        }
      }
    };
    this.__deletionAdapterOfTransactionEntity = new EntityDeletionOrUpdateAdapter<TransactionEntity>(__db) {
      @Override
      public String createQuery() {
        return "DELETE FROM `transactions` WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, TransactionEntity value) {
        stmt.bindLong(1, value.getId());
      }
    };
    this.__updateAdapterOfTransactionEntity = new EntityDeletionOrUpdateAdapter<TransactionEntity>(__db) {
      @Override
      public String createQuery() {
        return "UPDATE OR ABORT `transactions` SET `id` = ?,`sms_id` = ?,`amount` = ?,`raw_merchant` = ?,`normalized_merchant` = ?,`bank_name` = ?,`transaction_date` = ?,`raw_sms_body` = ?,`confidence_score` = ?,`is_debit` = ?,`created_at` = ?,`updated_at` = ? WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, TransactionEntity value) {
        stmt.bindLong(1, value.getId());
        if (value.getSmsId() == null) {
          stmt.bindNull(2);
        } else {
          stmt.bindString(2, value.getSmsId());
        }
        stmt.bindDouble(3, value.getAmount());
        if (value.getRawMerchant() == null) {
          stmt.bindNull(4);
        } else {
          stmt.bindString(4, value.getRawMerchant());
        }
        if (value.getNormalizedMerchant() == null) {
          stmt.bindNull(5);
        } else {
          stmt.bindString(5, value.getNormalizedMerchant());
        }
        if (value.getBankName() == null) {
          stmt.bindNull(6);
        } else {
          stmt.bindString(6, value.getBankName());
        }
        final Long _tmp = __dateConverter.dateToTimestamp(value.getTransactionDate());
        if (_tmp == null) {
          stmt.bindNull(7);
        } else {
          stmt.bindLong(7, _tmp);
        }
        if (value.getRawSmsBody() == null) {
          stmt.bindNull(8);
        } else {
          stmt.bindString(8, value.getRawSmsBody());
        }
        stmt.bindDouble(9, value.getConfidenceScore());
        final int _tmp_1 = value.isDebit() ? 1 : 0;
        stmt.bindLong(10, _tmp_1);
        final Long _tmp_2 = __dateConverter.dateToTimestamp(value.getCreatedAt());
        if (_tmp_2 == null) {
          stmt.bindNull(11);
        } else {
          stmt.bindLong(11, _tmp_2);
        }
        final Long _tmp_3 = __dateConverter.dateToTimestamp(value.getUpdatedAt());
        if (_tmp_3 == null) {
          stmt.bindNull(12);
        } else {
          stmt.bindLong(12, _tmp_3);
        }
        stmt.bindLong(13, value.getId());
      }
    };
    this.__preparedStmtOfDeleteTransactionById = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "DELETE FROM transactions WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllTransactions = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "DELETE FROM transactions";
        return _query;
      }
    };
  }

  @Override
  public Object insertTransaction(final TransactionEntity transaction,
      final Continuation<? super Long> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          long _result = __insertionAdapterOfTransactionEntity.insertAndReturnId(transaction);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object insertTransactions(final List<TransactionEntity> transactions,
      final Continuation<? super List<Long>> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          List<Long> _result = __insertionAdapterOfTransactionEntity.insertAndReturnIdsList(transactions);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteTransaction(final TransactionEntity transaction,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfTransactionEntity.handle(transaction);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object updateTransaction(final TransactionEntity transaction,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfTransactionEntity.handle(transaction);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteTransactionById(final long transactionId,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteTransactionById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, transactionId);
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfDeleteTransactionById.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteAllTransactions(final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllTransactions.acquire();
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfDeleteAllTransactions.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Flow<List<TransactionEntity>> getAllTransactions() {
    final String _sql = "SELECT * FROM transactions ORDER BY transaction_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[]{"transactions"}, new Callable<List<TransactionEntity>>() {
      @Override
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSmsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sms_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfRawMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_merchant");
          final int _cursorIndexOfNormalizedMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_merchant");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bank_name");
          final int _cursorIndexOfTransactionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "transaction_date");
          final int _cursorIndexOfRawSmsBody = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_sms_body");
          final int _cursorIndexOfConfidenceScore = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence_score");
          final int _cursorIndexOfIsDebit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_debit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final TransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSmsId;
            if (_cursor.isNull(_cursorIndexOfSmsId)) {
              _tmpSmsId = null;
            } else {
              _tmpSmsId = _cursor.getString(_cursorIndexOfSmsId);
            }
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpRawMerchant;
            if (_cursor.isNull(_cursorIndexOfRawMerchant)) {
              _tmpRawMerchant = null;
            } else {
              _tmpRawMerchant = _cursor.getString(_cursorIndexOfRawMerchant);
            }
            final String _tmpNormalizedMerchant;
            if (_cursor.isNull(_cursorIndexOfNormalizedMerchant)) {
              _tmpNormalizedMerchant = null;
            } else {
              _tmpNormalizedMerchant = _cursor.getString(_cursorIndexOfNormalizedMerchant);
            }
            final String _tmpBankName;
            if (_cursor.isNull(_cursorIndexOfBankName)) {
              _tmpBankName = null;
            } else {
              _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            }
            final Date _tmpTransactionDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTransactionDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTransactionDate);
            }
            final Date _tmp_1 = __dateConverter.fromTimestamp(_tmp);
            if(_tmp_1 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpTransactionDate = _tmp_1;
            }
            final String _tmpRawSmsBody;
            if (_cursor.isNull(_cursorIndexOfRawSmsBody)) {
              _tmpRawSmsBody = null;
            } else {
              _tmpRawSmsBody = _cursor.getString(_cursorIndexOfRawSmsBody);
            }
            final float _tmpConfidenceScore;
            _tmpConfidenceScore = _cursor.getFloat(_cursorIndexOfConfidenceScore);
            final boolean _tmpIsDebit;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDebit);
            _tmpIsDebit = _tmp_2 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_3;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_4 = __dateConverter.fromTimestamp(_tmp_3);
            if(_tmp_4 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_4;
            }
            final Date _tmpUpdatedAt;
            final Long _tmp_5;
            if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getLong(_cursorIndexOfUpdatedAt);
            }
            final Date _tmp_6 = __dateConverter.fromTimestamp(_tmp_5);
            if(_tmp_6 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpUpdatedAt = _tmp_6;
            }
            _item = new TransactionEntity(_tmpId,_tmpSmsId,_tmpAmount,_tmpRawMerchant,_tmpNormalizedMerchant,_tmpBankName,_tmpTransactionDate,_tmpRawSmsBody,_tmpConfidenceScore,_tmpIsDebit,_tmpCreatedAt,_tmpUpdatedAt);
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
  public Object getAllTransactionsSync(
      final Continuation<? super List<TransactionEntity>> continuation) {
    final String _sql = "SELECT * FROM transactions ORDER BY transaction_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TransactionEntity>>() {
      @Override
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSmsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sms_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfRawMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_merchant");
          final int _cursorIndexOfNormalizedMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_merchant");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bank_name");
          final int _cursorIndexOfTransactionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "transaction_date");
          final int _cursorIndexOfRawSmsBody = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_sms_body");
          final int _cursorIndexOfConfidenceScore = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence_score");
          final int _cursorIndexOfIsDebit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_debit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final TransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSmsId;
            if (_cursor.isNull(_cursorIndexOfSmsId)) {
              _tmpSmsId = null;
            } else {
              _tmpSmsId = _cursor.getString(_cursorIndexOfSmsId);
            }
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpRawMerchant;
            if (_cursor.isNull(_cursorIndexOfRawMerchant)) {
              _tmpRawMerchant = null;
            } else {
              _tmpRawMerchant = _cursor.getString(_cursorIndexOfRawMerchant);
            }
            final String _tmpNormalizedMerchant;
            if (_cursor.isNull(_cursorIndexOfNormalizedMerchant)) {
              _tmpNormalizedMerchant = null;
            } else {
              _tmpNormalizedMerchant = _cursor.getString(_cursorIndexOfNormalizedMerchant);
            }
            final String _tmpBankName;
            if (_cursor.isNull(_cursorIndexOfBankName)) {
              _tmpBankName = null;
            } else {
              _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            }
            final Date _tmpTransactionDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTransactionDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTransactionDate);
            }
            final Date _tmp_1 = __dateConverter.fromTimestamp(_tmp);
            if(_tmp_1 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpTransactionDate = _tmp_1;
            }
            final String _tmpRawSmsBody;
            if (_cursor.isNull(_cursorIndexOfRawSmsBody)) {
              _tmpRawSmsBody = null;
            } else {
              _tmpRawSmsBody = _cursor.getString(_cursorIndexOfRawSmsBody);
            }
            final float _tmpConfidenceScore;
            _tmpConfidenceScore = _cursor.getFloat(_cursorIndexOfConfidenceScore);
            final boolean _tmpIsDebit;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDebit);
            _tmpIsDebit = _tmp_2 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_3;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_4 = __dateConverter.fromTimestamp(_tmp_3);
            if(_tmp_4 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_4;
            }
            final Date _tmpUpdatedAt;
            final Long _tmp_5;
            if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getLong(_cursorIndexOfUpdatedAt);
            }
            final Date _tmp_6 = __dateConverter.fromTimestamp(_tmp_5);
            if(_tmp_6 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpUpdatedAt = _tmp_6;
            }
            _item = new TransactionEntity(_tmpId,_tmpSmsId,_tmpAmount,_tmpRawMerchant,_tmpNormalizedMerchant,_tmpBankName,_tmpTransactionDate,_tmpRawSmsBody,_tmpConfidenceScore,_tmpIsDebit,_tmpCreatedAt,_tmpUpdatedAt);
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
  public Object getTransactionsByDateRange(final Date startDate, final Date endDate,
      final Continuation<? super List<TransactionEntity>> continuation) {
    final String _sql = "SELECT * FROM transactions WHERE transaction_date >= ? AND transaction_date <= ? ORDER BY transaction_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    final Long _tmp = __dateConverter.dateToTimestamp(startDate);
    if (_tmp == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp);
    }
    _argIndex = 2;
    final Long _tmp_1 = __dateConverter.dateToTimestamp(endDate);
    if (_tmp_1 == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp_1);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TransactionEntity>>() {
      @Override
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSmsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sms_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfRawMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_merchant");
          final int _cursorIndexOfNormalizedMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_merchant");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bank_name");
          final int _cursorIndexOfTransactionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "transaction_date");
          final int _cursorIndexOfRawSmsBody = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_sms_body");
          final int _cursorIndexOfConfidenceScore = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence_score");
          final int _cursorIndexOfIsDebit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_debit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final TransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSmsId;
            if (_cursor.isNull(_cursorIndexOfSmsId)) {
              _tmpSmsId = null;
            } else {
              _tmpSmsId = _cursor.getString(_cursorIndexOfSmsId);
            }
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpRawMerchant;
            if (_cursor.isNull(_cursorIndexOfRawMerchant)) {
              _tmpRawMerchant = null;
            } else {
              _tmpRawMerchant = _cursor.getString(_cursorIndexOfRawMerchant);
            }
            final String _tmpNormalizedMerchant;
            if (_cursor.isNull(_cursorIndexOfNormalizedMerchant)) {
              _tmpNormalizedMerchant = null;
            } else {
              _tmpNormalizedMerchant = _cursor.getString(_cursorIndexOfNormalizedMerchant);
            }
            final String _tmpBankName;
            if (_cursor.isNull(_cursorIndexOfBankName)) {
              _tmpBankName = null;
            } else {
              _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            }
            final Date _tmpTransactionDate;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfTransactionDate)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfTransactionDate);
            }
            final Date _tmp_3 = __dateConverter.fromTimestamp(_tmp_2);
            if(_tmp_3 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpTransactionDate = _tmp_3;
            }
            final String _tmpRawSmsBody;
            if (_cursor.isNull(_cursorIndexOfRawSmsBody)) {
              _tmpRawSmsBody = null;
            } else {
              _tmpRawSmsBody = _cursor.getString(_cursorIndexOfRawSmsBody);
            }
            final float _tmpConfidenceScore;
            _tmpConfidenceScore = _cursor.getFloat(_cursorIndexOfConfidenceScore);
            final boolean _tmpIsDebit;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsDebit);
            _tmpIsDebit = _tmp_4 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_5;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_6 = __dateConverter.fromTimestamp(_tmp_5);
            if(_tmp_6 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_6;
            }
            final Date _tmpUpdatedAt;
            final Long _tmp_7;
            if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
              _tmp_7 = null;
            } else {
              _tmp_7 = _cursor.getLong(_cursorIndexOfUpdatedAt);
            }
            final Date _tmp_8 = __dateConverter.fromTimestamp(_tmp_7);
            if(_tmp_8 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpUpdatedAt = _tmp_8;
            }
            _item = new TransactionEntity(_tmpId,_tmpSmsId,_tmpAmount,_tmpRawMerchant,_tmpNormalizedMerchant,_tmpBankName,_tmpTransactionDate,_tmpRawSmsBody,_tmpConfidenceScore,_tmpIsDebit,_tmpCreatedAt,_tmpUpdatedAt);
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
  public Object getTransactionsByMerchant(final String merchantName,
      final Continuation<? super List<TransactionEntity>> continuation) {
    final String _sql = "SELECT * FROM transactions WHERE normalized_merchant = ? ORDER BY transaction_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (merchantName == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, merchantName);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TransactionEntity>>() {
      @Override
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSmsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sms_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfRawMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_merchant");
          final int _cursorIndexOfNormalizedMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_merchant");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bank_name");
          final int _cursorIndexOfTransactionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "transaction_date");
          final int _cursorIndexOfRawSmsBody = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_sms_body");
          final int _cursorIndexOfConfidenceScore = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence_score");
          final int _cursorIndexOfIsDebit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_debit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final TransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSmsId;
            if (_cursor.isNull(_cursorIndexOfSmsId)) {
              _tmpSmsId = null;
            } else {
              _tmpSmsId = _cursor.getString(_cursorIndexOfSmsId);
            }
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpRawMerchant;
            if (_cursor.isNull(_cursorIndexOfRawMerchant)) {
              _tmpRawMerchant = null;
            } else {
              _tmpRawMerchant = _cursor.getString(_cursorIndexOfRawMerchant);
            }
            final String _tmpNormalizedMerchant;
            if (_cursor.isNull(_cursorIndexOfNormalizedMerchant)) {
              _tmpNormalizedMerchant = null;
            } else {
              _tmpNormalizedMerchant = _cursor.getString(_cursorIndexOfNormalizedMerchant);
            }
            final String _tmpBankName;
            if (_cursor.isNull(_cursorIndexOfBankName)) {
              _tmpBankName = null;
            } else {
              _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            }
            final Date _tmpTransactionDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTransactionDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTransactionDate);
            }
            final Date _tmp_1 = __dateConverter.fromTimestamp(_tmp);
            if(_tmp_1 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpTransactionDate = _tmp_1;
            }
            final String _tmpRawSmsBody;
            if (_cursor.isNull(_cursorIndexOfRawSmsBody)) {
              _tmpRawSmsBody = null;
            } else {
              _tmpRawSmsBody = _cursor.getString(_cursorIndexOfRawSmsBody);
            }
            final float _tmpConfidenceScore;
            _tmpConfidenceScore = _cursor.getFloat(_cursorIndexOfConfidenceScore);
            final boolean _tmpIsDebit;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDebit);
            _tmpIsDebit = _tmp_2 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_3;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_4 = __dateConverter.fromTimestamp(_tmp_3);
            if(_tmp_4 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_4;
            }
            final Date _tmpUpdatedAt;
            final Long _tmp_5;
            if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getLong(_cursorIndexOfUpdatedAt);
            }
            final Date _tmp_6 = __dateConverter.fromTimestamp(_tmp_5);
            if(_tmp_6 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpUpdatedAt = _tmp_6;
            }
            _item = new TransactionEntity(_tmpId,_tmpSmsId,_tmpAmount,_tmpRawMerchant,_tmpNormalizedMerchant,_tmpBankName,_tmpTransactionDate,_tmpRawSmsBody,_tmpConfidenceScore,_tmpIsDebit,_tmpCreatedAt,_tmpUpdatedAt);
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
  public Object getTransactionsByMerchantAndAmount(final String merchantName,
      final double minAmount, final Continuation<? super List<TransactionEntity>> continuation) {
    final String _sql = "SELECT * FROM transactions WHERE normalized_merchant LIKE '%' || ? || '%' AND amount >= ? ORDER BY transaction_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    if (merchantName == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, merchantName);
    }
    _argIndex = 2;
    _statement.bindDouble(_argIndex, minAmount);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TransactionEntity>>() {
      @Override
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSmsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sms_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfRawMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_merchant");
          final int _cursorIndexOfNormalizedMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_merchant");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bank_name");
          final int _cursorIndexOfTransactionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "transaction_date");
          final int _cursorIndexOfRawSmsBody = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_sms_body");
          final int _cursorIndexOfConfidenceScore = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence_score");
          final int _cursorIndexOfIsDebit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_debit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final TransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSmsId;
            if (_cursor.isNull(_cursorIndexOfSmsId)) {
              _tmpSmsId = null;
            } else {
              _tmpSmsId = _cursor.getString(_cursorIndexOfSmsId);
            }
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpRawMerchant;
            if (_cursor.isNull(_cursorIndexOfRawMerchant)) {
              _tmpRawMerchant = null;
            } else {
              _tmpRawMerchant = _cursor.getString(_cursorIndexOfRawMerchant);
            }
            final String _tmpNormalizedMerchant;
            if (_cursor.isNull(_cursorIndexOfNormalizedMerchant)) {
              _tmpNormalizedMerchant = null;
            } else {
              _tmpNormalizedMerchant = _cursor.getString(_cursorIndexOfNormalizedMerchant);
            }
            final String _tmpBankName;
            if (_cursor.isNull(_cursorIndexOfBankName)) {
              _tmpBankName = null;
            } else {
              _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            }
            final Date _tmpTransactionDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTransactionDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTransactionDate);
            }
            final Date _tmp_1 = __dateConverter.fromTimestamp(_tmp);
            if(_tmp_1 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpTransactionDate = _tmp_1;
            }
            final String _tmpRawSmsBody;
            if (_cursor.isNull(_cursorIndexOfRawSmsBody)) {
              _tmpRawSmsBody = null;
            } else {
              _tmpRawSmsBody = _cursor.getString(_cursorIndexOfRawSmsBody);
            }
            final float _tmpConfidenceScore;
            _tmpConfidenceScore = _cursor.getFloat(_cursorIndexOfConfidenceScore);
            final boolean _tmpIsDebit;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDebit);
            _tmpIsDebit = _tmp_2 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_3;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_4 = __dateConverter.fromTimestamp(_tmp_3);
            if(_tmp_4 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_4;
            }
            final Date _tmpUpdatedAt;
            final Long _tmp_5;
            if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getLong(_cursorIndexOfUpdatedAt);
            }
            final Date _tmp_6 = __dateConverter.fromTimestamp(_tmp_5);
            if(_tmp_6 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpUpdatedAt = _tmp_6;
            }
            _item = new TransactionEntity(_tmpId,_tmpSmsId,_tmpAmount,_tmpRawMerchant,_tmpNormalizedMerchant,_tmpBankName,_tmpTransactionDate,_tmpRawSmsBody,_tmpConfidenceScore,_tmpIsDebit,_tmpCreatedAt,_tmpUpdatedAt);
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
  public Object getTransactionsByBank(final String bankName,
      final Continuation<? super List<TransactionEntity>> continuation) {
    final String _sql = "SELECT * FROM transactions WHERE bank_name = ? ORDER BY transaction_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (bankName == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, bankName);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TransactionEntity>>() {
      @Override
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSmsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sms_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfRawMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_merchant");
          final int _cursorIndexOfNormalizedMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_merchant");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bank_name");
          final int _cursorIndexOfTransactionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "transaction_date");
          final int _cursorIndexOfRawSmsBody = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_sms_body");
          final int _cursorIndexOfConfidenceScore = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence_score");
          final int _cursorIndexOfIsDebit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_debit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final TransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSmsId;
            if (_cursor.isNull(_cursorIndexOfSmsId)) {
              _tmpSmsId = null;
            } else {
              _tmpSmsId = _cursor.getString(_cursorIndexOfSmsId);
            }
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpRawMerchant;
            if (_cursor.isNull(_cursorIndexOfRawMerchant)) {
              _tmpRawMerchant = null;
            } else {
              _tmpRawMerchant = _cursor.getString(_cursorIndexOfRawMerchant);
            }
            final String _tmpNormalizedMerchant;
            if (_cursor.isNull(_cursorIndexOfNormalizedMerchant)) {
              _tmpNormalizedMerchant = null;
            } else {
              _tmpNormalizedMerchant = _cursor.getString(_cursorIndexOfNormalizedMerchant);
            }
            final String _tmpBankName;
            if (_cursor.isNull(_cursorIndexOfBankName)) {
              _tmpBankName = null;
            } else {
              _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            }
            final Date _tmpTransactionDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTransactionDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTransactionDate);
            }
            final Date _tmp_1 = __dateConverter.fromTimestamp(_tmp);
            if(_tmp_1 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpTransactionDate = _tmp_1;
            }
            final String _tmpRawSmsBody;
            if (_cursor.isNull(_cursorIndexOfRawSmsBody)) {
              _tmpRawSmsBody = null;
            } else {
              _tmpRawSmsBody = _cursor.getString(_cursorIndexOfRawSmsBody);
            }
            final float _tmpConfidenceScore;
            _tmpConfidenceScore = _cursor.getFloat(_cursorIndexOfConfidenceScore);
            final boolean _tmpIsDebit;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDebit);
            _tmpIsDebit = _tmp_2 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_3;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_4 = __dateConverter.fromTimestamp(_tmp_3);
            if(_tmp_4 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_4;
            }
            final Date _tmpUpdatedAt;
            final Long _tmp_5;
            if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getLong(_cursorIndexOfUpdatedAt);
            }
            final Date _tmp_6 = __dateConverter.fromTimestamp(_tmp_5);
            if(_tmp_6 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpUpdatedAt = _tmp_6;
            }
            _item = new TransactionEntity(_tmpId,_tmpSmsId,_tmpAmount,_tmpRawMerchant,_tmpNormalizedMerchant,_tmpBankName,_tmpTransactionDate,_tmpRawSmsBody,_tmpConfidenceScore,_tmpIsDebit,_tmpCreatedAt,_tmpUpdatedAt);
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
  public Object searchTransactions(final String query, final int limit,
      final Continuation<? super List<TransactionEntity>> continuation) {
    final String _sql = "\n"
            + "        SELECT * FROM transactions \n"
            + "        WHERE (raw_merchant LIKE '%' || ? || '%' OR normalized_merchant LIKE '%' || ? || '%')\n"
            + "        ORDER BY transaction_date DESC \n"
            + "        LIMIT ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 2;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 3;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TransactionEntity>>() {
      @Override
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSmsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sms_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfRawMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_merchant");
          final int _cursorIndexOfNormalizedMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_merchant");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bank_name");
          final int _cursorIndexOfTransactionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "transaction_date");
          final int _cursorIndexOfRawSmsBody = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_sms_body");
          final int _cursorIndexOfConfidenceScore = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence_score");
          final int _cursorIndexOfIsDebit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_debit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final TransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSmsId;
            if (_cursor.isNull(_cursorIndexOfSmsId)) {
              _tmpSmsId = null;
            } else {
              _tmpSmsId = _cursor.getString(_cursorIndexOfSmsId);
            }
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpRawMerchant;
            if (_cursor.isNull(_cursorIndexOfRawMerchant)) {
              _tmpRawMerchant = null;
            } else {
              _tmpRawMerchant = _cursor.getString(_cursorIndexOfRawMerchant);
            }
            final String _tmpNormalizedMerchant;
            if (_cursor.isNull(_cursorIndexOfNormalizedMerchant)) {
              _tmpNormalizedMerchant = null;
            } else {
              _tmpNormalizedMerchant = _cursor.getString(_cursorIndexOfNormalizedMerchant);
            }
            final String _tmpBankName;
            if (_cursor.isNull(_cursorIndexOfBankName)) {
              _tmpBankName = null;
            } else {
              _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            }
            final Date _tmpTransactionDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTransactionDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTransactionDate);
            }
            final Date _tmp_1 = __dateConverter.fromTimestamp(_tmp);
            if(_tmp_1 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpTransactionDate = _tmp_1;
            }
            final String _tmpRawSmsBody;
            if (_cursor.isNull(_cursorIndexOfRawSmsBody)) {
              _tmpRawSmsBody = null;
            } else {
              _tmpRawSmsBody = _cursor.getString(_cursorIndexOfRawSmsBody);
            }
            final float _tmpConfidenceScore;
            _tmpConfidenceScore = _cursor.getFloat(_cursorIndexOfConfidenceScore);
            final boolean _tmpIsDebit;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDebit);
            _tmpIsDebit = _tmp_2 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_3;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_4 = __dateConverter.fromTimestamp(_tmp_3);
            if(_tmp_4 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_4;
            }
            final Date _tmpUpdatedAt;
            final Long _tmp_5;
            if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getLong(_cursorIndexOfUpdatedAt);
            }
            final Date _tmp_6 = __dateConverter.fromTimestamp(_tmp_5);
            if(_tmp_6 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpUpdatedAt = _tmp_6;
            }
            _item = new TransactionEntity(_tmpId,_tmpSmsId,_tmpAmount,_tmpRawMerchant,_tmpNormalizedMerchant,_tmpBankName,_tmpTransactionDate,_tmpRawSmsBody,_tmpConfidenceScore,_tmpIsDebit,_tmpCreatedAt,_tmpUpdatedAt);
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
  public Object getTransactionCount(final Continuation<? super Integer> continuation) {
    final String _sql = "SELECT COUNT(*) FROM transactions";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if(_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
  public Object getTransactionCountByDateRange(final Date startDate, final Date endDate,
      final Continuation<? super Integer> continuation) {
    final String _sql = "SELECT COUNT(*) FROM transactions WHERE transaction_date >= ? AND transaction_date <= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    final Long _tmp = __dateConverter.dateToTimestamp(startDate);
    if (_tmp == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp);
    }
    _argIndex = 2;
    final Long _tmp_1 = __dateConverter.dateToTimestamp(endDate);
    if (_tmp_1 == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp_1);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if(_cursor.moveToFirst()) {
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(0);
            _result = _tmp_2;
          } else {
            _result = 0;
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
  public Object getTotalSpentByDateRange(final Date startDate, final Date endDate,
      final Continuation<? super Double> continuation) {
    final String _sql = "SELECT SUM(amount) FROM transactions WHERE transaction_date >= ? AND transaction_date <= ? AND is_debit = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    final Long _tmp = __dateConverter.dateToTimestamp(startDate);
    if (_tmp == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp);
    }
    _argIndex = 2;
    final Long _tmp_1 = __dateConverter.dateToTimestamp(endDate);
    if (_tmp_1 == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp_1);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Double>() {
      @Override
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if(_cursor.moveToFirst()) {
            final Double _tmp_2;
            if (_cursor.isNull(0)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getDouble(0);
            }
            _result = _tmp_2;
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
  public Object getTopMerchantsBySpending(final Date startDate, final Date endDate, final int limit,
      final Continuation<? super List<MerchantSpending>> continuation) {
    final String _sql = "\n"
            + "        SELECT normalized_merchant, SUM(amount) as total_amount, COUNT(*) as transaction_count\n"
            + "        FROM transactions \n"
            + "        WHERE transaction_date >= ? AND transaction_date <= ? AND is_debit = 1\n"
            + "        GROUP BY normalized_merchant \n"
            + "        ORDER BY total_amount DESC \n"
            + "        LIMIT ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    final Long _tmp = __dateConverter.dateToTimestamp(startDate);
    if (_tmp == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp);
    }
    _argIndex = 2;
    final Long _tmp_1 = __dateConverter.dateToTimestamp(endDate);
    if (_tmp_1 == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp_1);
    }
    _argIndex = 3;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MerchantSpending>>() {
      @Override
      public List<MerchantSpending> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfNormalizedMerchant = 0;
          final int _cursorIndexOfTotalAmount = 1;
          final int _cursorIndexOfTransactionCount = 2;
          final List<MerchantSpending> _result = new ArrayList<MerchantSpending>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final MerchantSpending _item;
            final String _tmpNormalized_merchant;
            if (_cursor.isNull(_cursorIndexOfNormalizedMerchant)) {
              _tmpNormalized_merchant = null;
            } else {
              _tmpNormalized_merchant = _cursor.getString(_cursorIndexOfNormalizedMerchant);
            }
            final double _tmpTotal_amount;
            _tmpTotal_amount = _cursor.getDouble(_cursorIndexOfTotalAmount);
            final int _tmpTransaction_count;
            _tmpTransaction_count = _cursor.getInt(_cursorIndexOfTransactionCount);
            _item = new MerchantSpending(_tmpNormalized_merchant,_tmpTotal_amount,_tmpTransaction_count);
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
  public Object getLastSyncInfo(final Continuation<? super SyncInfo> continuation) {
    final String _sql = "\n"
            + "        SELECT MAX(transaction_date) as last_sync_date, MAX(sms_id) as last_sms_id \n"
            + "        FROM transactions\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SyncInfo>() {
      @Override
      public SyncInfo call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfLastSyncDate = 0;
          final int _cursorIndexOfLastSmsId = 1;
          final SyncInfo _result;
          if(_cursor.moveToFirst()) {
            final Date _tmpLast_sync_date;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfLastSyncDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfLastSyncDate);
            }
            _tmpLast_sync_date = __dateConverter.fromTimestamp(_tmp);
            final String _tmpLast_sms_id;
            if (_cursor.isNull(_cursorIndexOfLastSmsId)) {
              _tmpLast_sms_id = null;
            } else {
              _tmpLast_sms_id = _cursor.getString(_cursorIndexOfLastSmsId);
            }
            _result = new SyncInfo(_tmpLast_sync_date,_tmpLast_sms_id);
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
  public Object getTransactionBySmsId(final String smsId,
      final Continuation<? super TransactionEntity> continuation) {
    final String _sql = "SELECT * FROM transactions WHERE sms_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (smsId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, smsId);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TransactionEntity>() {
      @Override
      public TransactionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSmsId = CursorUtil.getColumnIndexOrThrow(_cursor, "sms_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfRawMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_merchant");
          final int _cursorIndexOfNormalizedMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "normalized_merchant");
          final int _cursorIndexOfBankName = CursorUtil.getColumnIndexOrThrow(_cursor, "bank_name");
          final int _cursorIndexOfTransactionDate = CursorUtil.getColumnIndexOrThrow(_cursor, "transaction_date");
          final int _cursorIndexOfRawSmsBody = CursorUtil.getColumnIndexOrThrow(_cursor, "raw_sms_body");
          final int _cursorIndexOfConfidenceScore = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence_score");
          final int _cursorIndexOfIsDebit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_debit");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final TransactionEntity _result;
          if(_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSmsId;
            if (_cursor.isNull(_cursorIndexOfSmsId)) {
              _tmpSmsId = null;
            } else {
              _tmpSmsId = _cursor.getString(_cursorIndexOfSmsId);
            }
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpRawMerchant;
            if (_cursor.isNull(_cursorIndexOfRawMerchant)) {
              _tmpRawMerchant = null;
            } else {
              _tmpRawMerchant = _cursor.getString(_cursorIndexOfRawMerchant);
            }
            final String _tmpNormalizedMerchant;
            if (_cursor.isNull(_cursorIndexOfNormalizedMerchant)) {
              _tmpNormalizedMerchant = null;
            } else {
              _tmpNormalizedMerchant = _cursor.getString(_cursorIndexOfNormalizedMerchant);
            }
            final String _tmpBankName;
            if (_cursor.isNull(_cursorIndexOfBankName)) {
              _tmpBankName = null;
            } else {
              _tmpBankName = _cursor.getString(_cursorIndexOfBankName);
            }
            final Date _tmpTransactionDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTransactionDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTransactionDate);
            }
            final Date _tmp_1 = __dateConverter.fromTimestamp(_tmp);
            if(_tmp_1 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpTransactionDate = _tmp_1;
            }
            final String _tmpRawSmsBody;
            if (_cursor.isNull(_cursorIndexOfRawSmsBody)) {
              _tmpRawSmsBody = null;
            } else {
              _tmpRawSmsBody = _cursor.getString(_cursorIndexOfRawSmsBody);
            }
            final float _tmpConfidenceScore;
            _tmpConfidenceScore = _cursor.getFloat(_cursorIndexOfConfidenceScore);
            final boolean _tmpIsDebit;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDebit);
            _tmpIsDebit = _tmp_2 != 0;
            final Date _tmpCreatedAt;
            final Long _tmp_3;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_4 = __dateConverter.fromTimestamp(_tmp_3);
            if(_tmp_4 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_4;
            }
            final Date _tmpUpdatedAt;
            final Long _tmp_5;
            if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getLong(_cursorIndexOfUpdatedAt);
            }
            final Date _tmp_6 = __dateConverter.fromTimestamp(_tmp_5);
            if(_tmp_6 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpUpdatedAt = _tmp_6;
            }
            _result = new TransactionEntity(_tmpId,_tmpSmsId,_tmpAmount,_tmpRawMerchant,_tmpNormalizedMerchant,_tmpBankName,_tmpTransactionDate,_tmpRawSmsBody,_tmpConfidenceScore,_tmpIsDebit,_tmpCreatedAt,_tmpUpdatedAt);
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
  public Object getCategorySpendingBreakdown(final Date startDate, final Date endDate,
      final Continuation<? super List<CategorySpendingResult>> continuation) {
    final String _sql = "\n"
            + "        SELECT m.category_id, c.name as category_name, c.color, \n"
            + "               SUM(t.amount) as total_amount, COUNT(t.id) as transaction_count,\n"
            + "               MAX(t.transaction_date) as last_transaction_date\n"
            + "        FROM transactions t\n"
            + "        JOIN merchants m ON t.normalized_merchant = m.normalized_name\n"
            + "        JOIN categories c ON m.category_id = c.id\n"
            + "        WHERE t.transaction_date >= ? AND t.transaction_date <= ? AND t.is_debit = 1\n"
            + "        GROUP BY m.category_id, c.name, c.color\n"
            + "        ORDER BY total_amount DESC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    final Long _tmp = __dateConverter.dateToTimestamp(startDate);
    if (_tmp == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp);
    }
    _argIndex = 2;
    final Long _tmp_1 = __dateConverter.dateToTimestamp(endDate);
    if (_tmp_1 == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp_1);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CategorySpendingResult>>() {
      @Override
      public List<CategorySpendingResult> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCategoryId = 0;
          final int _cursorIndexOfCategoryName = 1;
          final int _cursorIndexOfColor = 2;
          final int _cursorIndexOfTotalAmount = 3;
          final int _cursorIndexOfTransactionCount = 4;
          final int _cursorIndexOfLastTransactionDate = 5;
          final List<CategorySpendingResult> _result = new ArrayList<CategorySpendingResult>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final CategorySpendingResult _item;
            final long _tmpCategory_id;
            _tmpCategory_id = _cursor.getLong(_cursorIndexOfCategoryId);
            final String _tmpCategory_name;
            if (_cursor.isNull(_cursorIndexOfCategoryName)) {
              _tmpCategory_name = null;
            } else {
              _tmpCategory_name = _cursor.getString(_cursorIndexOfCategoryName);
            }
            final String _tmpColor;
            if (_cursor.isNull(_cursorIndexOfColor)) {
              _tmpColor = null;
            } else {
              _tmpColor = _cursor.getString(_cursorIndexOfColor);
            }
            final double _tmpTotal_amount;
            _tmpTotal_amount = _cursor.getDouble(_cursorIndexOfTotalAmount);
            final int _tmpTransaction_count;
            _tmpTransaction_count = _cursor.getInt(_cursorIndexOfTransactionCount);
            final Date _tmpLast_transaction_date;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfLastTransactionDate)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfLastTransactionDate);
            }
            _tmpLast_transaction_date = __dateConverter.fromTimestamp(_tmp_2);
            _item = new CategorySpendingResult(_tmpCategory_id,_tmpCategory_name,_tmpColor,_tmpTotal_amount,_tmpTransaction_count,_tmpLast_transaction_date);
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
