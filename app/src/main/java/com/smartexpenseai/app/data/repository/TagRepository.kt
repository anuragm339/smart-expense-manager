package com.smartexpenseai.app.data.repository

import com.smartexpenseai.app.data.dao.TagDao
import com.smartexpenseai.app.data.dao.TagWithCount
import com.smartexpenseai.app.data.entities.TagEntity
import com.smartexpenseai.app.data.entities.TransactionTagEntity
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tag matching mode when filtering by more than one tag.
 * ANY = transactions carrying at least one of the selected tags (OR).
 * ALL = transactions carrying every selected tag (AND).
 */
enum class TagMatchMode { ANY, ALL }

/**
 * Read/write access to user-defined tags and their associations to transactions.
 *
 * Kept separate from [ExpenseRepository] because tagging is self-contained (no
 * category-duality or merchant coupling) and every consumer is Hilt-injected.
 */
@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao
) {
    private val logger = StructuredLogger(featureTag = "TAGS", className = "TagRepository")

    // ----- Reads -----

    fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()

    fun getTagsWithCounts(): Flow<List<TagWithCount>> = tagDao.getTagsWithCounts()

    fun getTagsForTransaction(transactionId: Long): Flow<List<TagEntity>> =
        tagDao.getTagsForTransaction(transactionId)

    suspend fun getAllTagsSync(): List<TagEntity> = withContext(Dispatchers.IO) {
        tagDao.getAllTagsSync()
    }

    suspend fun getTagsForTransactionSync(transactionId: Long): List<TagEntity> =
        withContext(Dispatchers.IO) { tagDao.getTagsForTransactionSync(transactionId) }

    /** Tags for many transactions, grouped by transaction id (for list rendering). */
    suspend fun getTagsForTransactions(transactionIds: List<Long>): Map<Long, List<TagEntity>> =
        withContext(Dispatchers.IO) {
            if (transactionIds.isEmpty()) return@withContext emptyMap()
            tagDao.getTagsForTransactions(transactionIds)
                .groupBy({ it.transactionId }, { it.tag })
        }

    // ----- Tag CRUD -----

    /**
     * Returns the existing tag with this name (case-insensitive) or creates one.
     * Names are trimmed; blank names are rejected with an exception.
     */
    suspend fun getOrCreateTag(name: String, color: String = "#607D8B"): TagEntity =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            require(trimmed.isNotEmpty()) { "Tag name cannot be blank" }
            tagDao.getTagByName(trimmed)?.let { return@withContext it }
            val id = tagDao.insertTag(TagEntity(name = trimmed, color = color, createdAt = Date()))
            // insertTag uses IGNORE; a race could return -1, so re-read by name.
            tagDao.getTagByName(trimmed)
                ?: tagDao.getTagById(id)
                ?: error("Failed to create tag '$trimmed'")
        }

    suspend fun renameTag(tag: TagEntity, newName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@withContext false
        val existing = tagDao.getTagByName(trimmed)
        if (existing != null && existing.id != tag.id) {
            logger.warn("renameTag", "A tag named '$trimmed' already exists")
            return@withContext false
        }
        tagDao.updateTag(tag.copy(name = trimmed))
        true
    }

    suspend fun updateTagColor(tag: TagEntity, color: String) = withContext(Dispatchers.IO) {
        tagDao.updateTag(tag.copy(color = color))
    }

    suspend fun deleteTag(tag: TagEntity) = withContext(Dispatchers.IO) {
        // transaction_tags rows cascade-delete via the foreign key.
        tagDao.deleteTag(tag)
    }

    // ----- Associations -----

    suspend fun addTagToTransaction(transactionId: Long, tagId: Long) = withContext(Dispatchers.IO) {
        tagDao.addTagToTransaction(TransactionTagEntity(transactionId, tagId))
    }

    suspend fun removeTagFromTransaction(transactionId: Long, tagId: Long) =
        withContext(Dispatchers.IO) {
            tagDao.removeTagFromTransaction(transactionId, tagId)
        }

    // ----- Filtering -----

    /**
     * Ids of active transactions matching the selected tags under [mode].
     * Empty [tagIds] yields an empty result (caller should treat as "no tag filter").
     */
    suspend fun getMatchingTransactionIds(
        tagIds: List<Long>,
        mode: TagMatchMode
    ): Set<Long> = withContext(Dispatchers.IO) {
        if (tagIds.isEmpty()) return@withContext emptySet()
        val ids = when (mode) {
            TagMatchMode.ANY -> tagDao.getTransactionIdsWithAnyTag(tagIds)
            TagMatchMode.ALL -> tagDao.getTransactionIdsWithAllTags(tagIds, tagIds.size)
        }
        ids.toSet()
    }
}
