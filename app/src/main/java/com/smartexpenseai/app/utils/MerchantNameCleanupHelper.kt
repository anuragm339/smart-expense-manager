package com.smartexpenseai.app.utils

import android.content.Context
import com.smartexpenseai.app.data.dao.TransactionDao
import com.smartexpenseai.app.parsing.engine.MerchantNameCleaner
import com.smartexpenseai.app.utils.logging.StructuredLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time cleanup that re-normalizes merchant names on transactions parsed
 * before [MerchantNameCleaner] stripped embedded dates/reference/phone numbers.
 *
 * Legacy rows stored the full SMS tail as the merchant, so each looked unique
 * and never grouped or stayed deleted. Re-running the cleaner on the stored
 * name collapses them onto one stable name.
 *
 * Safe by construction: it only rewrites the two name columns (never deletes or
 * moves a transaction; the full SMS stays in raw_sms_body), and it is
 * idempotent - cleaning an already-clean name is a no-op - so re-running does
 * no harm. Guarded by a preference flag so it runs once per install.
 */
@Singleton
class MerchantNameCleanupHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao
) {
    private val logger = StructuredLogger("MerchantNameCleanupHelper", "MerchantNameCleanupHelper")

    companion object {
        private const val PREFS = "app_migration"
        private const val KEY_DONE = "merchant_name_cleanup_done"
    }

    suspend fun runIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return@withContext

        try {
            val all = transactionDao.getAllTransactionsSync()
            var updated = 0
            all.forEach { txn ->
                val cleaned = MerchantNameCleaner.clean(txn.rawMerchant)
                if (cleaned.isNotBlank() && cleaned != txn.rawMerchant) {
                    transactionDao.updateMerchantNames(
                        id = txn.id,
                        rawMerchant = cleaned,
                        normalizedMerchant = cleaned.uppercase()
                    )
                    updated++
                }
            }
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            logger.info("runIfNeeded", "Re-normalized $updated of ${all.size} transaction merchant names")
        } catch (e: Exception) {
            // Leave the flag unset so the cleanup retries on the next launch.
            logger.error("runIfNeeded", "Merchant name cleanup failed", e)
        }
    }
}
