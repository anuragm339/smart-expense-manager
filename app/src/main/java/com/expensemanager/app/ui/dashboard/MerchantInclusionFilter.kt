package com.expensemanager.app.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import com.expensemanager.app.utils.logging.StructuredLogger
import org.json.JSONObject

/**
 * Filters merchants using the inclusion state persisted by the Messages screen.
 */
class MerchantInclusionFilter(
    context: Context,
    private val logger: StructuredLogger
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("expense_calculations", Context.MODE_PRIVATE)

    fun apply(merchants: List<MerchantSpending>): List<MerchantSpending> {
        if (merchants.isEmpty()) return merchants

        val inclusionStatesJson = preferences.getString("group_inclusion_states", null)
        if (inclusionStatesJson.isNullOrEmpty()) {
            logger.debug(
                "apply",
                "No inclusion states stored; returning original merchant list",
                "Count: ${merchants.size}"
            )
            return merchants
        }

        return runCatching { JSONObject(inclusionStatesJson) }
            .map { inclusionStates ->
                merchants.filter { merchant ->
                    inclusionStates.optBoolean(merchant.merchantName, true)
                }
            }
            .onSuccess { filtered ->
                logger.debug(
                    "apply",
                    "Applied inclusion filter",
                    "Before=${merchants.size}, After=${filtered.size}"
                )
            }
            .onFailure { error ->
                logger.error(
                    "apply",
                    "Failed to parse inclusion states", error
                )
            }
            .getOrElse { merchants }
    }
}
