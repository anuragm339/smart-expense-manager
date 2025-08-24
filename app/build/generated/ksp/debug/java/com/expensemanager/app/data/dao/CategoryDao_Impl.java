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
import com.expensemanager.app.data.entities.CategoryEntity;
import java.lang.Class;
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
public final class CategoryDao_Impl implements CategoryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CategoryEntity> __insertionAdapterOfCategoryEntity;

  private final DateConverter __dateConverter = new DateConverter();

  private final EntityDeletionOrUpdateAdapter<CategoryEntity> __deletionAdapterOfCategoryEntity;

  private final EntityDeletionOrUpdateAdapter<CategoryEntity> __updateAdapterOfCategoryEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteCategoryById;

  private final SharedSQLiteStatement __preparedStmtOfUpdateCategoryOrder;

  public CategoryDao_Impl(RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCategoryEntity = new EntityInsertionAdapter<CategoryEntity>(__db) {
      @Override
      public String createQuery() {
        return "INSERT OR IGNORE INTO `categories` (`id`,`name`,`emoji`,`color`,`is_system`,`display_order`,`created_at`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, CategoryEntity value) {
        stmt.bindLong(1, value.getId());
        if (value.getName() == null) {
          stmt.bindNull(2);
        } else {
          stmt.bindString(2, value.getName());
        }
        if (value.getEmoji() == null) {
          stmt.bindNull(3);
        } else {
          stmt.bindString(3, value.getEmoji());
        }
        if (value.getColor() == null) {
          stmt.bindNull(4);
        } else {
          stmt.bindString(4, value.getColor());
        }
        final int _tmp = value.isSystem() ? 1 : 0;
        stmt.bindLong(5, _tmp);
        stmt.bindLong(6, value.getDisplayOrder());
        final Long _tmp_1 = __dateConverter.dateToTimestamp(value.getCreatedAt());
        if (_tmp_1 == null) {
          stmt.bindNull(7);
        } else {
          stmt.bindLong(7, _tmp_1);
        }
      }
    };
    this.__deletionAdapterOfCategoryEntity = new EntityDeletionOrUpdateAdapter<CategoryEntity>(__db) {
      @Override
      public String createQuery() {
        return "DELETE FROM `categories` WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, CategoryEntity value) {
        stmt.bindLong(1, value.getId());
      }
    };
    this.__updateAdapterOfCategoryEntity = new EntityDeletionOrUpdateAdapter<CategoryEntity>(__db) {
      @Override
      public String createQuery() {
        return "UPDATE OR ABORT `categories` SET `id` = ?,`name` = ?,`emoji` = ?,`color` = ?,`is_system` = ?,`display_order` = ?,`created_at` = ? WHERE `id` = ?";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, CategoryEntity value) {
        stmt.bindLong(1, value.getId());
        if (value.getName() == null) {
          stmt.bindNull(2);
        } else {
          stmt.bindString(2, value.getName());
        }
        if (value.getEmoji() == null) {
          stmt.bindNull(3);
        } else {
          stmt.bindString(3, value.getEmoji());
        }
        if (value.getColor() == null) {
          stmt.bindNull(4);
        } else {
          stmt.bindString(4, value.getColor());
        }
        final int _tmp = value.isSystem() ? 1 : 0;
        stmt.bindLong(5, _tmp);
        stmt.bindLong(6, value.getDisplayOrder());
        final Long _tmp_1 = __dateConverter.dateToTimestamp(value.getCreatedAt());
        if (_tmp_1 == null) {
          stmt.bindNull(7);
        } else {
          stmt.bindLong(7, _tmp_1);
        }
        stmt.bindLong(8, value.getId());
      }
    };
    this.__preparedStmtOfDeleteCategoryById = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "DELETE FROM categories WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateCategoryOrder = new SharedSQLiteStatement(__db) {
      @Override
      public String createQuery() {
        final String _query = "UPDATE categories SET display_order = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertCategory(final CategoryEntity category,
      final Continuation<? super Long> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          long _result = __insertionAdapterOfCategoryEntity.insertAndReturnId(category);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object insertCategories(final List<CategoryEntity> categories,
      final Continuation<? super List<Long>> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          List<Long> _result = __insertionAdapterOfCategoryEntity.insertAndReturnIdsList(categories);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteCategory(final CategoryEntity category,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfCategoryEntity.handle(category);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object updateCategory(final CategoryEntity category,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfCategoryEntity.handle(category);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, continuation);
  }

  @Override
  public Object deleteCategoryById(final long categoryId,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteCategoryById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, categoryId);
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfDeleteCategoryById.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Object updateCategoryOrder(final long categoryId, final int order,
      final Continuation<? super Unit> continuation) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateCategoryOrder.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, order);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, categoryId);
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
          __preparedStmtOfUpdateCategoryOrder.release(_stmt);
        }
      }
    }, continuation);
  }

  @Override
  public Flow<List<CategoryEntity>> getAllCategories() {
    final String _sql = "SELECT * FROM categories ORDER BY display_order ASC, name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[]{"categories"}, new Callable<List<CategoryEntity>>() {
      @Override
      public List<CategoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEmoji = CursorUtil.getColumnIndexOrThrow(_cursor, "emoji");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfIsSystem = CursorUtil.getColumnIndexOrThrow(_cursor, "is_system");
          final int _cursorIndexOfDisplayOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "display_order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final List<CategoryEntity> _result = new ArrayList<CategoryEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final CategoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpEmoji;
            if (_cursor.isNull(_cursorIndexOfEmoji)) {
              _tmpEmoji = null;
            } else {
              _tmpEmoji = _cursor.getString(_cursorIndexOfEmoji);
            }
            final String _tmpColor;
            if (_cursor.isNull(_cursorIndexOfColor)) {
              _tmpColor = null;
            } else {
              _tmpColor = _cursor.getString(_cursorIndexOfColor);
            }
            final boolean _tmpIsSystem;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSystem);
            _tmpIsSystem = _tmp != 0;
            final int _tmpDisplayOrder;
            _tmpDisplayOrder = _cursor.getInt(_cursorIndexOfDisplayOrder);
            final Date _tmpCreatedAt;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_2 = __dateConverter.fromTimestamp(_tmp_1);
            if(_tmp_2 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_2;
            }
            _item = new CategoryEntity(_tmpId,_tmpName,_tmpEmoji,_tmpColor,_tmpIsSystem,_tmpDisplayOrder,_tmpCreatedAt);
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
  public Object getAllCategoriesSync(
      final Continuation<? super List<CategoryEntity>> continuation) {
    final String _sql = "SELECT * FROM categories ORDER BY display_order ASC, name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CategoryEntity>>() {
      @Override
      public List<CategoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEmoji = CursorUtil.getColumnIndexOrThrow(_cursor, "emoji");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfIsSystem = CursorUtil.getColumnIndexOrThrow(_cursor, "is_system");
          final int _cursorIndexOfDisplayOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "display_order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final List<CategoryEntity> _result = new ArrayList<CategoryEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final CategoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpEmoji;
            if (_cursor.isNull(_cursorIndexOfEmoji)) {
              _tmpEmoji = null;
            } else {
              _tmpEmoji = _cursor.getString(_cursorIndexOfEmoji);
            }
            final String _tmpColor;
            if (_cursor.isNull(_cursorIndexOfColor)) {
              _tmpColor = null;
            } else {
              _tmpColor = _cursor.getString(_cursorIndexOfColor);
            }
            final boolean _tmpIsSystem;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSystem);
            _tmpIsSystem = _tmp != 0;
            final int _tmpDisplayOrder;
            _tmpDisplayOrder = _cursor.getInt(_cursorIndexOfDisplayOrder);
            final Date _tmpCreatedAt;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_2 = __dateConverter.fromTimestamp(_tmp_1);
            if(_tmp_2 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_2;
            }
            _item = new CategoryEntity(_tmpId,_tmpName,_tmpEmoji,_tmpColor,_tmpIsSystem,_tmpDisplayOrder,_tmpCreatedAt);
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
  public Object getSystemCategories(final Continuation<? super List<CategoryEntity>> continuation) {
    final String _sql = "SELECT * FROM categories WHERE is_system = 1 ORDER BY display_order ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CategoryEntity>>() {
      @Override
      public List<CategoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEmoji = CursorUtil.getColumnIndexOrThrow(_cursor, "emoji");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfIsSystem = CursorUtil.getColumnIndexOrThrow(_cursor, "is_system");
          final int _cursorIndexOfDisplayOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "display_order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final List<CategoryEntity> _result = new ArrayList<CategoryEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final CategoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpEmoji;
            if (_cursor.isNull(_cursorIndexOfEmoji)) {
              _tmpEmoji = null;
            } else {
              _tmpEmoji = _cursor.getString(_cursorIndexOfEmoji);
            }
            final String _tmpColor;
            if (_cursor.isNull(_cursorIndexOfColor)) {
              _tmpColor = null;
            } else {
              _tmpColor = _cursor.getString(_cursorIndexOfColor);
            }
            final boolean _tmpIsSystem;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSystem);
            _tmpIsSystem = _tmp != 0;
            final int _tmpDisplayOrder;
            _tmpDisplayOrder = _cursor.getInt(_cursorIndexOfDisplayOrder);
            final Date _tmpCreatedAt;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_2 = __dateConverter.fromTimestamp(_tmp_1);
            if(_tmp_2 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_2;
            }
            _item = new CategoryEntity(_tmpId,_tmpName,_tmpEmoji,_tmpColor,_tmpIsSystem,_tmpDisplayOrder,_tmpCreatedAt);
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
  public Object getUserCategories(final Continuation<? super List<CategoryEntity>> continuation) {
    final String _sql = "SELECT * FROM categories WHERE is_system = 0 ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CategoryEntity>>() {
      @Override
      public List<CategoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEmoji = CursorUtil.getColumnIndexOrThrow(_cursor, "emoji");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfIsSystem = CursorUtil.getColumnIndexOrThrow(_cursor, "is_system");
          final int _cursorIndexOfDisplayOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "display_order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final List<CategoryEntity> _result = new ArrayList<CategoryEntity>(_cursor.getCount());
          while(_cursor.moveToNext()) {
            final CategoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpEmoji;
            if (_cursor.isNull(_cursorIndexOfEmoji)) {
              _tmpEmoji = null;
            } else {
              _tmpEmoji = _cursor.getString(_cursorIndexOfEmoji);
            }
            final String _tmpColor;
            if (_cursor.isNull(_cursorIndexOfColor)) {
              _tmpColor = null;
            } else {
              _tmpColor = _cursor.getString(_cursorIndexOfColor);
            }
            final boolean _tmpIsSystem;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSystem);
            _tmpIsSystem = _tmp != 0;
            final int _tmpDisplayOrder;
            _tmpDisplayOrder = _cursor.getInt(_cursorIndexOfDisplayOrder);
            final Date _tmpCreatedAt;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_2 = __dateConverter.fromTimestamp(_tmp_1);
            if(_tmp_2 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_2;
            }
            _item = new CategoryEntity(_tmpId,_tmpName,_tmpEmoji,_tmpColor,_tmpIsSystem,_tmpDisplayOrder,_tmpCreatedAt);
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
  public Object getCategoryById(final long categoryId,
      final Continuation<? super CategoryEntity> continuation) {
    final String _sql = "SELECT * FROM categories WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, categoryId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CategoryEntity>() {
      @Override
      public CategoryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEmoji = CursorUtil.getColumnIndexOrThrow(_cursor, "emoji");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfIsSystem = CursorUtil.getColumnIndexOrThrow(_cursor, "is_system");
          final int _cursorIndexOfDisplayOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "display_order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final CategoryEntity _result;
          if(_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpEmoji;
            if (_cursor.isNull(_cursorIndexOfEmoji)) {
              _tmpEmoji = null;
            } else {
              _tmpEmoji = _cursor.getString(_cursorIndexOfEmoji);
            }
            final String _tmpColor;
            if (_cursor.isNull(_cursorIndexOfColor)) {
              _tmpColor = null;
            } else {
              _tmpColor = _cursor.getString(_cursorIndexOfColor);
            }
            final boolean _tmpIsSystem;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSystem);
            _tmpIsSystem = _tmp != 0;
            final int _tmpDisplayOrder;
            _tmpDisplayOrder = _cursor.getInt(_cursorIndexOfDisplayOrder);
            final Date _tmpCreatedAt;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_2 = __dateConverter.fromTimestamp(_tmp_1);
            if(_tmp_2 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_2;
            }
            _result = new CategoryEntity(_tmpId,_tmpName,_tmpEmoji,_tmpColor,_tmpIsSystem,_tmpDisplayOrder,_tmpCreatedAt);
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
  public Object getCategoryByName(final String name,
      final Continuation<? super CategoryEntity> continuation) {
    final String _sql = "SELECT * FROM categories WHERE name = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (name == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, name);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CategoryEntity>() {
      @Override
      public CategoryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfEmoji = CursorUtil.getColumnIndexOrThrow(_cursor, "emoji");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfIsSystem = CursorUtil.getColumnIndexOrThrow(_cursor, "is_system");
          final int _cursorIndexOfDisplayOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "display_order");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final CategoryEntity _result;
          if(_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final String _tmpEmoji;
            if (_cursor.isNull(_cursorIndexOfEmoji)) {
              _tmpEmoji = null;
            } else {
              _tmpEmoji = _cursor.getString(_cursorIndexOfEmoji);
            }
            final String _tmpColor;
            if (_cursor.isNull(_cursorIndexOfColor)) {
              _tmpColor = null;
            } else {
              _tmpColor = _cursor.getString(_cursorIndexOfColor);
            }
            final boolean _tmpIsSystem;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSystem);
            _tmpIsSystem = _tmp != 0;
            final int _tmpDisplayOrder;
            _tmpDisplayOrder = _cursor.getInt(_cursorIndexOfDisplayOrder);
            final Date _tmpCreatedAt;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCreatedAt)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCreatedAt);
            }
            final Date _tmp_2 = __dateConverter.fromTimestamp(_tmp_1);
            if(_tmp_2 == null) {
              throw new IllegalStateException("Expected non-null java.util.Date, but it was null.");
            } else {
              _tmpCreatedAt = _tmp_2;
            }
            _result = new CategoryEntity(_tmpId,_tmpName,_tmpEmoji,_tmpColor,_tmpIsSystem,_tmpDisplayOrder,_tmpCreatedAt);
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
  public Object getCategoryCount(final Continuation<? super Integer> continuation) {
    final String _sql = "SELECT COUNT(*) FROM categories";
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

  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
