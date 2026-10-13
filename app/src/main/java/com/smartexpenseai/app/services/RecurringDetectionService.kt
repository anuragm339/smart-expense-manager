package com.smartexpenseai.app.services

import com.smartexpenseai.app.data.dao.CategoryDao
import com.smartexpenseai.app.data.dao.MerchantDao
import com.smartexpenseai.app.data.dao.TransactionDao
import com.smartexpenseai.app.data.entities.CategoryEntity
import com.smartexpenseai.app.data.entities.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How often a series recurs. */
enum class Cadence(val label: String, val approxDays: Int, val minimumOccurrences: Int) {
    DAILY("Daily", 1, 5),
    WEEKLY("Weekly", 7, 3),
    FORTNIGHTLY("Every 2 weeks", 14, 3),
    MONTHLY("Monthly", 30, 3),
    QUARTERLY("Quarterly", 91, 3),
    YEARLY("Yearly", 365, 3),
}

/** Whether the amount behaves like a fixed subscription or a variable recurring bill. */
enum class RecurringKind(val label: String) {
    SUBSCRIPTION("Subscription"),
    RECURRING_BILL("Recurring bill"),
}

/** Where the next expected charge stands relative to today. */
enum class RecurringStatus { ACTIVE, DUE_SOON, OVERDUE }

/** A recurring series detected from transaction history. */
data class RecurringSeries(
    val normalizedMerchant: String,
    val displayName: String,
    val categoryName: String,
    val cadence: Cadence,
    val kind: RecurringKind,
    val confidence: Int,
    val typicalAmount: Double,
    val latestAmount: Double,
    val occurrences: Int,
    val lastDate: Date,
    val nextEstimatedDate: Date,
    val priceIncreased: Boolean,
    val status: RecurringStatus,
) {
    /** Amount normalized to a monthly figure, for ranking/summary. */
    val monthlyEquivalent: Double get() = typicalAmount * (30.0 / cadence.approxDays)
}

/** Internal evidence produced before merchant/category presentation data is attached. */
internal data class RecurringPattern(
    val transactions: List<TransactionEntity>,
    val cadence: Cadence,
    val kind: RecurringKind,
    val confidence: Int,
    val typicalAmount: Double,
)

/** Pure recurring-pattern analysis, independent from Android and Room. */
internal object RecurringPatternAnalyzer {
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val MIN_CONSISTENT_FRACTION = 0.67
    private const val DAILY_CONSISTENT_FRACTION = 0.75
    private const val DAILY_TIME_CONSISTENT_FRACTION = 0.80
    private const val DAILY_TIME_WINDOW_MINUTES = 60
    private const val MINUTES_PER_DAY = 24 * 60
    private const val FIXED_AMOUNT_TOLERANCE = 0.02
    private const val VARIABLE_AMOUNT_TOLERANCE = 0.20
    private const val MIN_ABSOLUTE_AMOUNT_TOLERANCE = 2.0

    fun analyzeMerchant(transactions: List<TransactionEntity>): List<RecurringPattern> {
        if (transactions.size < Cadence.WEEKLY.minimumOccurrences) return emptyList()

        val fixedPatterns = clusterByFixedAmount(transactions)
            .mapNotNull { analyzeCluster(it, RecurringKind.SUBSCRIPTION, FIXED_AMOUNT_TOLERANCE) }

        // Do not merge multiple fixed subscriptions from one merchant into one variable bill.
        if (fixedPatterns.isNotEmpty()) return fixedPatterns

        return listOfNotNull(
            analyzeCluster(
                transactions = transactions,
                kind = RecurringKind.RECURRING_BILL,
                amountTolerance = VARIABLE_AMOUNT_TOLERANCE,
            ),
        )
    }

    private fun analyzeCluster(
        transactions: List<TransactionEntity>,
        kind: RecurringKind,
        amountTolerance: Double,
    ): RecurringPattern? {
        val sorted = transactions.sortedBy { it.transactionDate }
        if (sorted.size < Cadence.WEEKLY.minimumOccurrences) return null

        val intervalDays = sorted.zipWithNext { first, second ->
            (second.transactionDate.time - first.transactionDate.time) / MILLIS_PER_DAY
        }.filter { it > 0.0 }
        if (intervalDays.isEmpty()) return null

        val cadenceMatch = Cadence.entries
            .filter { sorted.size >= it.minimumOccurrences }
            .map { cadence ->
                val consistency = intervalDays.count { cadence.acceptsInterval(it) }.toDouble() /
                    intervalDays.size
                cadence to consistency
            }
            .filter { (cadence, consistency) ->
                consistency >= if (cadence == Cadence.DAILY) {
                    DAILY_CONSISTENT_FRACTION
                } else {
                    MIN_CONSISTENT_FRACTION
                }
            }
            .maxByOrNull { it.second }
            ?: return null

        val cadence = cadenceMatch.first
        val intervalConsistency = cadenceMatch.second
        val timeConsistency = timeOfDayConsistency(sorted)
        if (cadence == Cadence.DAILY && timeConsistency < DAILY_TIME_CONSISTENT_FRACTION) {
            return null
        }

        val amounts = sorted.map { it.amount }
        val typicalAmount = median(amounts)
        if (typicalAmount <= 0.0) return null
        val allowedDifference = max(MIN_ABSOLUTE_AMOUNT_TOLERANCE, typicalAmount * amountTolerance)
        val amountConsistency = amounts.count { abs(it - typicalAmount) <= allowedDifference }
            .toDouble() / amounts.size
        if (amountConsistency < MIN_CONSISTENT_FRACTION) return null

        val confidence = if (cadence == Cadence.DAILY) {
            (intervalConsistency * 40 + amountConsistency * 30 + timeConsistency * 30).roundToInt()
        } else {
            (intervalConsistency * 55 + amountConsistency * 40 + timeConsistency * 5).roundToInt()
        }.coerceIn(0, 100)

        return RecurringPattern(
            transactions = sorted,
            cadence = cadence,
            kind = kind,
            confidence = confidence,
            typicalAmount = typicalAmount,
        )
    }

    private fun clusterByFixedAmount(
        transactions: List<TransactionEntity>,
    ): List<List<TransactionEntity>> {
        val clusters = mutableListOf<MutableList<TransactionEntity>>()
        transactions.sortedBy { it.amount }.forEach { transaction ->
            val cluster = clusters.firstOrNull { existing ->
                val center = median(existing.map { it.amount })
                abs(transaction.amount - center) <= max(
                    MIN_ABSOLUTE_AMOUNT_TOLERANCE,
                    center * FIXED_AMOUNT_TOLERANCE,
                )
            }
            if (cluster == null) {
                clusters += mutableListOf(transaction)
            } else {
                cluster += transaction
            }
        }
        return clusters.filter { it.size >= Cadence.WEEKLY.minimumOccurrences }
    }

    /** Best circular cluster around any observed clock time, including midnight wrap-around. */
    private fun timeOfDayConsistency(transactions: List<TransactionEntity>): Double {
        val minutes = transactions.map { minuteOfDay(it.transactionDate) }
        if (minutes.isEmpty()) return 0.0
        val bestCount = minutes.maxOf { anchor ->
            minutes.count { candidate ->
                circularMinuteDifference(anchor, candidate) <= DAILY_TIME_WINDOW_MINUTES
            }
        }
        return bestCount.toDouble() / minutes.size
    }

    private fun minuteOfDay(date: Date): Int = Calendar.getInstance().run {
        time = date
        get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
    }

    private fun circularMinuteDifference(first: Int, second: Int): Int {
        val difference = abs(first - second)
        return min(difference, MINUTES_PER_DAY - difference)
    }

    private fun Cadence.acceptsInterval(days: Double): Boolean = when (this) {
        Cadence.DAILY -> days in 0.875..1.125
        Cadence.WEEKLY -> days in 6.0..8.0
        Cadence.FORTNIGHTLY -> days in 12.0..16.0
        Cadence.MONTHLY -> days in 24.0..38.0
        Cadence.QUARTERLY -> days in 80.0..100.0
        Cadence.YEARLY -> days in 330.0..400.0
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
    }
}

/** Calendar-aware prediction and expiry rules shared by detection and tests. */
internal object RecurringSchedule {
    fun nextDate(date: Date, cadence: Cadence): Date = addCadence(date, cadence)

    /** A series expires after one complete cadence passes beyond its expected charge. */
    fun isExpired(nextExpectedDate: Date, cadence: Cadence, now: Date = Date()): Boolean =
        now.after(addCadence(nextExpectedDate, cadence))

    private fun addCadence(date: Date, cadence: Cadence): Date = Calendar.getInstance().run {
        time = date
        when (cadence) {
            Cadence.DAILY -> add(Calendar.DAY_OF_YEAR, 1)
            Cadence.WEEKLY -> add(Calendar.WEEK_OF_YEAR, 1)
            Cadence.FORTNIGHTLY -> add(Calendar.WEEK_OF_YEAR, 2)
            Cadence.MONTHLY -> add(Calendar.MONTH, 1)
            Cadence.QUARTERLY -> add(Calendar.MONTH, 3)
            Cadence.YEARLY -> add(Calendar.YEAR, 1)
        }
        time
    }
}

/** Detects recurring charges from local transaction history without network access. */
@Singleton
class RecurringDetectionService @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantDao,
    private val exclusionStore: RecurringExclusionStore,
) {

    suspend fun detect(): List<RecurringSeries> = withContext(Dispatchers.IO) {
        val transactions = transactionDao.getAllTransactionsSync()
            .filter { it.isDebit && it.normalizedMerchant.isNotBlank() }
        val categoriesById = categoryDao.getAllCategoriesSync().associateBy { it.id }

        transactions.groupBy { it.normalizedMerchant }
            .flatMap { (merchant, merchantTransactions) ->
                RecurringPatternAnalyzer.analyzeMerchant(merchantTransactions).mapNotNull { pattern ->
                    createSeries(merchant, pattern, categoriesById)
                }
            }
            .filterNot { exclusionStore.isExcluded(it) }
            .sortedByDescending { it.monthlyEquivalent }
    }

    private suspend fun createSeries(
        merchant: String,
        pattern: RecurringPattern,
        categoriesById: Map<Long, CategoryEntity>,
    ): RecurringSeries? {
        val latest = pattern.transactions.last()
        val nextEstimated = RecurringSchedule.nextDate(latest.transactionDate, pattern.cadence)
        if (RecurringSchedule.isExpired(nextEstimated, pattern.cadence)) return null
        val merchantEntity = merchantDao.getMerchantByNormalizedName(merchant)
        val displayName = merchantEntity?.displayName?.takeIf { it.isNotBlank() }
            ?: latest.rawMerchant
        val categoryName = categoriesById[latest.categoryId]?.name ?: "Other"

        return RecurringSeries(
            normalizedMerchant = merchant,
            displayName = displayName,
            categoryName = categoryName,
            cadence = pattern.cadence,
            kind = pattern.kind,
            confidence = pattern.confidence,
            typicalAmount = pattern.typicalAmount,
            latestAmount = latest.amount,
            occurrences = pattern.transactions.size,
            lastDate = latest.transactionDate,
            nextEstimatedDate = nextEstimated,
            priceIncreased = latest.amount > pattern.typicalAmount * (1 + PRICE_CHANGE_THRESHOLD),
            status = statusFor(nextEstimated, pattern.cadence),
        )
    }

    private fun statusFor(nextEstimated: Date, cadence: Cadence): RecurringStatus {
        val differenceDays = (nextEstimated.time - System.currentTimeMillis()).toDouble() /
            TimeUnit.DAYS.toMillis(1)
        val dueSoonDays = when (cadence) {
            Cadence.DAILY -> 0.5
            Cadence.WEEKLY -> 2.0
            Cadence.FORTNIGHTLY -> 3.0
            Cadence.MONTHLY -> 7.0
            Cadence.QUARTERLY -> 14.0
            Cadence.YEARLY -> 30.0
        }
        return when {
            differenceDays < -(cadence.approxDays * OVERDUE_GRACE) -> RecurringStatus.OVERDUE
            differenceDays <= dueSoonDays -> RecurringStatus.DUE_SOON
            else -> RecurringStatus.ACTIVE
        }
    }

    companion object {
        private const val PRICE_CHANGE_THRESHOLD = 0.10
        private const val OVERDUE_GRACE = 0.5
    }
}
