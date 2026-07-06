package com.smartexpenseai.app.services

import com.smartexpenseai.app.data.dao.CategoryDao
import com.smartexpenseai.app.data.dao.MerchantDao
import com.smartexpenseai.app.data.dao.TransactionDao
import com.smartexpenseai.app.data.entities.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** How often a series recurs. */
enum class Cadence(val label: String, val approxDays: Int) {
    WEEKLY("Weekly", 7),
    FORTNIGHTLY("Every 2 weeks", 14),
    MONTHLY("Monthly", 30),
    QUARTERLY("Quarterly", 91),
    YEARLY("Yearly", 365)
}

/** Where the next expected charge stands relative to today. */
enum class RecurringStatus { ACTIVE, DUE_SOON, OVERDUE }

/**
 * A detected recurring charge / subscription for one merchant.
 */
data class RecurringSeries(
    val normalizedMerchant: String,
    val displayName: String,
    val categoryName: String,
    val cadence: Cadence,
    val typicalAmount: Double,
    val latestAmount: Double,
    val occurrences: Int,
    val lastDate: Date,
    val nextEstimatedDate: Date,
    val priceIncreased: Boolean,
    val status: RecurringStatus
) {
    /** Amount normalized to a monthly figure, for ranking/summary. */
    val monthlyEquivalent: Double get() = typicalAmount * (30.0 / cadence.approxDays)
}

/**
 * Detects recurring charges from local transaction history — no network, no schema
 * change. A merchant is "recurring" when its debits repeat at a regular interval with
 * a reasonably stable amount over at least [MIN_OCCURRENCES] occurrences. Frequent but
 * irregular spend (e.g. food delivery) is intentionally filtered out.
 */
@Singleton
class RecurringDetectionService @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantDao
) {

    suspend fun detect(): List<RecurringSeries> = withContext(Dispatchers.IO) {
        val txns = transactionDao.getAllTransactionsSync().filter { it.isDebit }
        val categoriesById = categoryDao.getAllCategoriesSync().associateBy { it.id }

        txns.groupBy { it.normalizedMerchant }
            .mapNotNull { (merchant, list) ->
                analyzeSeries(merchant, list, categoriesById)
            }
            .sortedByDescending { it.monthlyEquivalent }
    }

    private suspend fun analyzeSeries(
        merchant: String,
        list: List<TransactionEntity>,
        categoriesById: Map<Long, com.smartexpenseai.app.data.entities.CategoryEntity>
    ): RecurringSeries? {
        if (list.size < MIN_OCCURRENCES) return null

        val sorted = list.sortedBy { it.transactionDate }
        val intervals = sorted.zipWithNext { a, b -> daysBetween(a.transactionDate, b.transactionDate) }
            .filter { it > 0 } // drop same-day duplicates
        if (intervals.size < MIN_OCCURRENCES - 1) return null

        val medianInterval = median(intervals.map { it.toDouble() })
        val cadence = classifyCadence(medianInterval) ?: return null

        // Intervals must mostly land near the cadence (regularity).
        val intervalTolerance = cadence.approxDays * INTERVAL_TOLERANCE
        val regularCount = intervals.count { abs(it - cadence.approxDays) <= intervalTolerance }
        if (regularCount < intervals.size * MIN_CONSISTENT_FRACTION) return null

        // Amounts must be reasonably stable (fixed-ish charge, not variable spend).
        val amounts = sorted.map { it.amount }
        val medianAmount = median(amounts)
        if (medianAmount <= 0.0) return null
        val stableCount = amounts.count { abs(it - medianAmount) <= medianAmount * AMOUNT_TOLERANCE }
        if (stableCount < amounts.size * MIN_CONSISTENT_FRACTION) return null

        val latest = sorted.last()
        val priceIncreased = latest.amount > medianAmount * (1 + PRICE_CHANGE_THRESHOLD)
        val nextEstimated = addDays(latest.transactionDate, medianInterval.toInt())

        val merchantEntity = merchantDao.getMerchantByNormalizedName(merchant)
        val displayName = merchantEntity?.displayName?.takeIf { it.isNotBlank() } ?: latest.rawMerchant
        val categoryName = categoriesById[latest.categoryId]?.name ?: "Other"

        return RecurringSeries(
            normalizedMerchant = merchant,
            displayName = displayName,
            categoryName = categoryName,
            cadence = cadence,
            typicalAmount = medianAmount,
            latestAmount = latest.amount,
            occurrences = sorted.size,
            lastDate = latest.transactionDate,
            nextEstimatedDate = nextEstimated,
            priceIncreased = priceIncreased,
            status = statusFor(nextEstimated, cadence)
        )
    }

    private fun classifyCadence(days: Double): Cadence? = when {
        days in 5.0..10.0 -> Cadence.WEEKLY
        days in 11.0..18.0 -> Cadence.FORTNIGHTLY
        days in 24.0..38.0 -> Cadence.MONTHLY
        days in 80.0..100.0 -> Cadence.QUARTERLY
        days in 330.0..400.0 -> Cadence.YEARLY
        else -> null
    }

    private fun statusFor(nextEstimated: Date, cadence: Cadence): RecurringStatus {
        val now = System.currentTimeMillis()
        val diffDays = (nextEstimated.time - now).toDouble() / TimeUnit.DAYS.toMillis(1)
        return when {
            diffDays < -(cadence.approxDays * OVERDUE_GRACE) -> RecurringStatus.OVERDUE
            diffDays <= DUE_SOON_DAYS -> RecurringStatus.DUE_SOON
            else -> RecurringStatus.ACTIVE
        }
    }

    private fun daysBetween(a: Date, b: Date): Int =
        ((b.time - a.time) / TimeUnit.DAYS.toMillis(1)).toInt()

    private fun addDays(date: Date, days: Int): Date =
        Date(date.time + TimeUnit.DAYS.toMillis(days.toLong()))

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    companion object {
        private const val MIN_OCCURRENCES = 3
        private const val INTERVAL_TOLERANCE = 0.35      // ±35% of the cadence's day count
        private const val AMOUNT_TOLERANCE = 0.20        // ±20% of the median amount
        private const val MIN_CONSISTENT_FRACTION = 0.6  // 60% of points must fit
        private const val PRICE_CHANGE_THRESHOLD = 0.10  // >10% above typical = price increase
        private const val OVERDUE_GRACE = 0.5            // overdue past 50% of a cycle
        private const val DUE_SOON_DAYS = 7.0
    }
}
