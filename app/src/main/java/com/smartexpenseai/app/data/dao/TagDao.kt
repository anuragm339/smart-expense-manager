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

    // ----- Auto-tag: sibling matching (same merchant + amount) -----

    /**
     * Tag ids attached to active transactions with the same merchant and amount as
     * a target (excluding the target itself). Callers still filter by time-of-day.
     */
    @Query(
        """SELECT tt.tag_id AS tagId, tx.transaction_date AS transaction_date
           FROM transactions tx
           JOIN transaction_tags tt ON tx.id = tt.transaction_id
           WHERE tx.normalized_merchant = :merchant AND tx.amount = :amount
             AND tx.is_active = 1 AND tx.id != :excludeId"""
    )
    suspend fun getSiblingTagCandidates(
        merchant: String,
        amount: Double,
        excludeId: Long
    ): List<SiblingTagRow>

    /**
     * Active transactions with the same merchant and amount as a target
     * (excluding it), tagged or not. Callers filter by time-of-day.
     */
    @Query(
        """SELECT id, transaction_date FROM transactions
           WHERE normalized_merchant = :merchant AND amount = :amount
             AND is_active = 1 AND id != :excludeId"""
    )
    suspend fun getSiblingTransactions(
        merchant: String,
        amount: Double,
        excludeId: Long
    ): List<SiblingTransaction>

    // ----- Analytics: spend by tag -----

    /**
     * Total debit spend per tag over a date range (active, non-excluded merchants).
     * Tags overlap and don't partition spend, so callers present this as a ranked
     * list, never a share-of-whole.
     */
    @Query(
        """SELECT t.id AS tagId, t.name AS name, t.color AS color,
                  SUM(tx.amount) AS totalAmount, COUNT(DISTINCT tx.id) AS transactionCount
           FROM tags t
           JOIN transaction_tags tt ON tt.tag_id = t.id
           JOIN transactions tx ON tx.id = tt.transaction_id
           LEFT JOIN merchants m ON tx.normalized_merchant = m.normalized_name
           WHERE tx.is_active = 1 AND tx.is_debit = 1
             AND tx.transaction_date >= :startDate AND tx.transaction_date <= :endDate
             AND (m.is_excluded_from_expense_tracking = 0 OR m.is_excluded_from_expense_tracking IS NULL)
           GROUP BY t.id, t.name, t.color
           HAVING totalAmount > 0
           ORDER BY totalAmount DESC"""
    )
    suspend fun getSpendByTag(startDate: java.util.Date, endDate: java.util.Date): List<TagSpending>
}

/** Aggregated debit spend for one tag over a period. */
data class TagSpending(
    val tagId: Long,
    val name: String,
    val color: String,
    val totalAmount: Double,
    val transactionCount: Int
)

/** A candidate tag from a sibling transaction, with that sibling's timestamp. */
data class SiblingTagRow(
    val tagId: Long,
    @ColumnInfo(name = "transaction_date") val transactionDate: java.util.Date
)

/** A sibling transaction id with its timestamp (for time-of-day filtering). */
data class SiblingTransaction(
    val id: Long,
    @ColumnInfo(name = "transaction_date") val transactionDate: java.util.Date
)

/**
 * Flat row for [TagDao.getTagsForTransactions]: a tag plus the transaction id it
 * is attached to. Callers group by transactionId.
 */
data class TransactionTagJoin(
    val transactionId: Long,
    @Embedded val tag: TagEntity
)
