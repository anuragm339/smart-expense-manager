package com.smartexpenseai.app.ui.dashboard

import android.content.Context
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.runBlocking

/**
 * Filters merchants using the inclusion state from the database.
 * Migrated from SharedPreferences to Database.
 */
class MerchantInclusionFilter(
    context: Context,
    private val logger: StructuredLogger
) {
    private val repository = ExpenseRepository.getInstance(context)

    fun apply(merchants: List<MerchantSpending>): List<MerchantSpending> {
        if (merchants.isEmpty()) return merchants

        return runBlocking {
            try {
                // Get excluded merchants from database
                val excludedMerchants = repository.getExcludedMerchants()
                val excludedMerchantNames = excludedMerchants.map { it.displayName }.toSet()

                logger.debug(
                    "apply",
                    "Loaded exclusions from database",
                    "Excluded merchants: ${excludedMerchantNames.size}"
                )

                // Filter out excluded merchants
                val filtered = merchants.filter { merchant ->
                    !excludedMerchantNames.contains(merchant.merchantName)
                }

                logger.debug(
                    "apply",
                    "Applied inclusion filter from DB",
                    "Before=${merchants.size}, After=${filtered.size}"
                )

                filtered
            } catch (e: Exception) {
                logger.error(
                    "apply",
                    "Failed to load exclusions from database, returning all merchants", e
                )
                merchants
            }
        }
    }
}
