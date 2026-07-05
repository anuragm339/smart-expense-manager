package com.smartexpenseai.app.data.dao

import androidx.room.*
import com.smartexpenseai.app.data.entities.TagEntity
import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.data.entities.TransactionTagEntity
import kotlinx.coroutines.flow.Flow

/**
 * A tag together with how many transactions currently carry it.
 * Used by the tag management screen.
 */
data class TagWithCount(
    @Embedded val tag: TagEntity,
    @ColumnInfo(name = "usage_count") val usageCount: Int
)

@Dao
interface TagDao {

    // ----- Tag CRUD -----

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAllTagsSync(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :tagId")
    suspend fun getTagById(tagId: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Query(
        """SELECT t.*, COUNT(tt.transaction_id) AS usage_count
           FROM tags t
           LEFT JOIN transaction_tags tt ON t.id = tt.tag_id
           GROUP BY t.id
           ORDER BY t.name ASC"""
    )
    fun getTagsWithCounts(): Flow<List<TagWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Delete
    suspend fun deleteTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTagById(tagId: Long)

    // ----- Transaction <-> Tag associations -----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToTransaction(link: TransactionTagEntity)

    @Query("DELETE FROM transaction_tags WHERE transaction_id = :transactionId AND tag_id = :tagId")
    suspend fun removeTagFromTransaction(transactionId: Long, tagId: Long)

    @Query(
        """SELECT t.* FROM tags t
           JOIN transaction_tags tt ON t.id = tt.tag_id
           WHERE tt.transaction_id = :transactionId
           ORDER BY t.name ASC"""
    )
    fun getTagsForTransaction(transactionId: Long): Flow<List<TagEntity>>

    @Query(
        """SELECT t.* FROM tags t
           JOIN transaction_tags tt ON t.id = tt.tag_id
           WHERE tt.transaction_id = :transactionId
           ORDER BY t.name ASC"""
    )
    suspend fun getTagsForTransactionSync(transactionId: Long): List<TagEntity>

    /**
     * Tags for many transactions at once, so a list screen can render chips
     * without one query per row. Returns junction rows joined to tag ids.
     */
    @Query(
        """SELECT tt.transaction_id AS transactionId, t.*
           FROM tags t
           JOIN transaction_tags tt ON t.id = tt.tag_id
           WHERE tt.transaction_id IN (:transactionIds)
           ORDER BY t.name ASC"""
    )
    suspend fun getTagsForTransactions(transactionIds: List<Long>): List<TransactionTagJoin>

    // ----- Filter by tag -----

    /** Active transactions carrying ANY of the given tags (OR semantics). */
    @Query(
        """SELECT DISTINCT tx.* FROM transactions tx
           JOIN transaction_tags tt ON tx.id = tt.transaction_id
           WHERE tt.tag_id IN (:tagIds) AND tx.is_active = 1
           ORDER BY tx.transaction_date DESC"""
    )
    suspend fun getTransactionsWithAnyTag(tagIds: List<Long>): List<TransactionEntity>

    /** Active transactions carrying ALL of the given tags (AND semantics). */
    @Query(
        """SELECT tx.* FROM transactions tx
           JOIN transaction_tags tt ON tx.id = tt.transaction_id
           WHERE tt.tag_id IN (:tagIds) AND tx.is_active = 1
           GROUP BY tx.id
           HAVING COUNT(DISTINCT tt.tag_id) = :tagCount
           ORDER BY tx.transaction_date DESC"""
    )
    suspend fun getTransactionsWithAllTags(tagIds: List<Long>, tagCount: Int): List<TransactionEntity>

    /** Ids of active transactions carrying ANY of the given tags. */
    @Query(
        """SELECT DISTINCT tt.transaction_id FROM transaction_tags tt
           JOIN transactions tx ON tx.id = tt.transaction_id
           WHERE tt.tag_id IN (:tagIds) AND tx.is_active = 1"""
    )
    suspend fun getTransactionIdsWithAnyTag(tagIds: List<Long>): List<Long>

    /** Ids of active transactions carrying ALL of the given tags. */
    @Query(
        """SELECT tt.transaction_id FROM transaction_tags tt
           JOIN transactions tx ON tx.id = tt.transaction_id
           WHERE tt.tag_id IN (:tagIds) AND tx.is_active = 1
           GROUP BY tt.transaction_id
           HAVING COUNT(DISTINCT tt.tag_id) = :tagCount"""
    )
    suspend fun getTransactionIdsWithAllTags(tagIds: List<Long>, tagCount: Int): List<Long>
}

/**
 * Flat row for [TagDao.getTagsForTransactions]: a tag plus the transaction id it
 * is attached to. Callers group by transactionId.
 */
data class TransactionTagJoin(
    val transactionId: Long,
    @Embedded val tag: TagEntity
)
