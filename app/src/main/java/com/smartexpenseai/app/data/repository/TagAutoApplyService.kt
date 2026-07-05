package com.smartexpenseai.app.data.repository

import android.content.Context
import com.smartexpenseai.app.data.dao.TagDao
import com.smartexpenseai.app.data.dao.TransactionDao
import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.data.entities.TransactionTagEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-applies tags to transactions that recur with the same signature:
 * **same merchant + same amount + same time-of-day (within ±30 min)**, ignoring date.
 *
 * The user's already-tagged transactions are the implicit rule set — no separate rule
 * store. Tags are copied as independent per-transaction associations, so later edits to
 * one transaction never affect the others, and past transactions are never rewritten.
 */
@Singleton
class TagAutoApplyService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tagDao: TagDao,
    private val transactionDao: TransactionDao
) {

    fun isEnabled(): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_KEY, true)

    /**
     * On ingest: if a tagged sibling exists (same merchant+amount, time-of-day within
     * the window), copy its tags onto [transactionId]. No-op when disabled or no match.
     */
    suspend fun autoApplyOnIngest(transactionId: Long) = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext
        val txn = transactionDao.getTransactionById(transactionId) ?: return@withContext
        val tagIds = matchingTagIds(txn)
        tagIds.forEach { tagDao.addTagToTransaction(TransactionTagEntity(transactionId, it)) }
    }

    /** How many existing active transactions match [sourceTransactionId]'s signature. */
    suspend fun countMatchingTransactions(sourceTransactionId: Long): Int =
        withContext(Dispatchers.IO) { matchingTransactionIds(sourceTransactionId).size }

    /**
     * Backfill: attach [tagIds] to every existing transaction matching the source's
     * signature. Uses IGNORE semantics, so already-tagged rows are untouched.
     * Returns the number of transactions affected.
     */
    suspend fun applyTagsToMatching(sourceTransactionId: Long, tagIds: List<Long>): Int =
        withContext(Dispatchers.IO) {
            if (tagIds.isEmpty()) return@withContext 0
            val matches = matchingTransactionIds(sourceTransactionId)
            matches.forEach { txId ->
                tagIds.forEach { tagId -> tagDao.addTagToTransaction(TransactionTagEntity(txId, tagId)) }
            }
            matches.size
        }

    // ----- internals -----

    private suspend fun matchingTagIds(txn: TransactionEntity): Set<Long> {
        val target = TagMatchWindow.minutesOfDay(txn.transactionDate)
        return tagDao.getSiblingTagCandidates(txn.normalizedMerchant, txn.amount, txn.id)
            .filter { TagMatchWindow.withinWindow(TagMatchWindow.minutesOfDay(it.transactionDate), target) }
            .map { it.tagId }
            .toSet()
    }

    private suspend fun matchingTransactionIds(sourceTransactionId: Long): List<Long> {
        val src = transactionDao.getTransactionById(sourceTransactionId) ?: return emptyList()
        val target = TagMatchWindow.minutesOfDay(src.transactionDate)
        return tagDao.getSiblingTransactions(src.normalizedMerchant, src.amount, src.id)
            .filter { TagMatchWindow.withinWindow(TagMatchWindow.minutesOfDay(it.transactionDate), target) }
            .map { it.id }
    }

    companion object {
        const val PREFS = "app_settings"
        const val PREF_KEY = "auto_tag_similar"
    }
}
