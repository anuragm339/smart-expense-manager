package com.smartexpenseai.app.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** Remembers recurring series that the user marked as not being subscriptions. */
@Singleton
class RecurringExclusionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun exclude(series: RecurringSeries) {
        val exclusions = currentExclusions().toMutableSet()
        exclusions.removeAll { it.matches(series) }
        exclusions += Exclusion(
            merchant = series.normalizedMerchant,
            cadence = series.cadence,
            amountInPaise = (series.typicalAmount * 100).roundToLong(),
        )
        save(exclusions)
    }

    fun restore(series: RecurringSeries) {
        save(currentExclusions().filterNot { it.matches(series) }.toSet())
    }

    fun isExcluded(series: RecurringSeries): Boolean =
        currentExclusions().any { it.matches(series) }

    private fun currentExclusions(): Set<Exclusion> =
        preferences.getStringSet(KEY_EXCLUSIONS, emptySet()).orEmpty()
            .mapNotNull(Exclusion::decode)
            .toSet()

    private fun save(exclusions: Set<Exclusion>) {
        preferences.edit()
            .putStringSet(KEY_EXCLUSIONS, exclusions.mapTo(mutableSetOf(), Exclusion::encode))
            .apply()
    }

    private data class Exclusion(
        val merchant: String,
        val cadence: Cadence,
        val amountInPaise: Long,
    ) {
        fun encode(): String = "$merchant|${cadence.name}|$amountInPaise"

        fun matchesIdentity(series: RecurringSeries): Boolean =
            merchant == series.normalizedMerchant && cadence == series.cadence

        fun matches(series: RecurringSeries): Boolean {
            if (!matchesIdentity(series)) return false
            val seriesAmount = (series.typicalAmount * 100).roundToLong()
            val allowedDifference = max(200L, (amountInPaise * AMOUNT_TOLERANCE).roundToLong())
            return abs(seriesAmount - amountInPaise) <= allowedDifference
        }

        companion object {
            fun decode(encoded: String): Exclusion? {
                val amountSeparator = encoded.lastIndexOf('|')
                if (amountSeparator <= 0) return null
                val cadenceSeparator = encoded.lastIndexOf('|', amountSeparator - 1)
                if (cadenceSeparator <= 0) return null
                val merchant = encoded.substring(0, cadenceSeparator)
                val cadence = runCatching {
                    Cadence.valueOf(encoded.substring(cadenceSeparator + 1, amountSeparator))
                }.getOrNull() ?: return null
                val amount = encoded.substring(amountSeparator + 1).toLongOrNull() ?: return null
                return Exclusion(merchant, cadence, amount)
            }
        }
    }

    companion object {
        private const val PREFERENCES = "recurring_preferences"
        private const val KEY_EXCLUSIONS = "excluded_series"
        private const val AMOUNT_TOLERANCE = 0.03
    }
}
