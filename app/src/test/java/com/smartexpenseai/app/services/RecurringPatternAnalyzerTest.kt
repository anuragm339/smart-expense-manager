package com.smartexpenseai.app.services

import com.smartexpenseai.app.data.entities.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class RecurringPatternAnalyzerTest {

    @Test
    fun shouldDetectDailySeriesWhenAmountAndTimeRepeatWithinOneHour() {
        val transactions = listOf(
            transaction(1, 1, 9, 0),
            transaction(2, 2, 9, 30),
            transaction(3, 3, 8, 45),
            transaction(4, 4, 9, 20),
            transaction(5, 5, 9, 10),
        )

        val result = RecurringPatternAnalyzer.analyzeMerchant(transactions)

        assertEquals(1, result.size)
        assertEquals(Cadence.DAILY, result.single().cadence)
        assertEquals(RecurringKind.SUBSCRIPTION, result.single().kind)
        assertTrue(result.single().confidence >= 90)
    }

    @Test
    fun shouldRejectDailySeriesWhenClockTimesAreNotConsistent() {
        val transactions = listOf(
            transaction(1, 1, 9, 0),
            transaction(2, 2, 12, 0),
            transaction(3, 3, 9, 0),
            transaction(4, 4, 12, 0),
            transaction(5, 5, 9, 0),
        )

        val result = RecurringPatternAnalyzer.analyzeMerchant(transactions)

        assertTrue(result.isEmpty())
    }

    @Test
    fun shouldKeepDifferentWeeklyAmountsAsSeparateSubscriptions() {
        val transactions = listOf(
            transaction(1, 1, 9, 0, amount = 199.0),
            transaction(2, 8, 9, 0, amount = 199.0),
            transaction(3, 15, 9, 0, amount = 199.0),
            transaction(4, 1, 10, 0, amount = 499.0),
            transaction(5, 8, 10, 0, amount = 499.0),
            transaction(6, 15, 10, 0, amount = 499.0),
        )

        val result = RecurringPatternAnalyzer.analyzeMerchant(transactions)

        assertEquals(2, result.size)
        assertTrue(result.all { it.cadence == Cadence.WEEKLY })
        assertEquals(listOf(199.0, 499.0), result.map { it.typicalAmount }.sorted())
    }

    @Test
    fun shouldClassifyVariableMonthlyAmountsAsRecurringBill() {
        val transactions = listOf(
            transaction(1, 5, 9, 0, amount = 1_000.0, month = Calendar.JANUARY),
            transaction(2, 5, 14, 0, amount = 1_100.0, month = Calendar.FEBRUARY),
            transaction(3, 5, 8, 0, amount = 900.0, month = Calendar.MARCH),
        )

        val result = RecurringPatternAnalyzer.analyzeMerchant(transactions)

        assertEquals(1, result.size)
        assertEquals(Cadence.MONTHLY, result.single().cadence)
        assertEquals(RecurringKind.RECURRING_BILL, result.single().kind)
    }

    @Test
    fun shouldKeepDailySeriesUntilOneFullDayAfterExpectedCharge() {
        val nextExpected = date(10, Calendar.OCTOBER, 9, 0)

        assertTrue(
            !RecurringSchedule.isExpired(
                nextExpected,
                Cadence.DAILY,
                date(11, Calendar.OCTOBER, 8, 59),
            ),
        )
        assertTrue(
            RecurringSchedule.isExpired(
                nextExpected,
                Cadence.DAILY,
                date(11, Calendar.OCTOBER, 9, 1),
            ),
        )
    }

    @Test
    fun shouldExpireWeeklySeriesOneWeekAfterExpectedCharge() {
        val nextExpected = date(10, Calendar.OCTOBER, 9, 0)

        assertTrue(
            !RecurringSchedule.isExpired(
                nextExpected,
                Cadence.WEEKLY,
                date(17, Calendar.OCTOBER, 8, 59),
            ),
        )
        assertTrue(
            RecurringSchedule.isExpired(
                nextExpected,
                Cadence.WEEKLY,
                date(17, Calendar.OCTOBER, 9, 1),
            ),
        )
    }

    @Test
    fun shouldExpireMonthlySeriesOneCalendarMonthAfterExpectedCharge() {
        val nextExpected = date(31, Calendar.JANUARY, 9, 0)

        assertTrue(
            !RecurringSchedule.isExpired(
                nextExpected,
                Cadence.MONTHLY,
                date(28, Calendar.FEBRUARY, 8, 59),
            ),
        )
        assertTrue(
            RecurringSchedule.isExpired(
                nextExpected,
                Cadence.MONTHLY,
                date(28, Calendar.FEBRUARY, 9, 1),
            ),
        )
    }

    private fun transaction(
        id: Long,
        day: Int,
        hour: Int,
        minute: Int,
        amount: Double = 299.0,
        month: Int = Calendar.JANUARY,
    ): TransactionEntity {
        val date = date(day, month, hour, minute)
        return TransactionEntity(
            id = id,
            smsId = "sms-$id",
            amount = amount,
            rawMerchant = "Example Service",
            normalizedMerchant = "example service",
            bankName = "Test Bank",
            transactionDate = date,
            rawSmsBody = "Test transaction",
            confidenceScore = 1f,
            isDebit = true,
            createdAt = Date(date.time),
            updatedAt = Date(date.time),
        )
    }

    private fun date(day: Int, month: Int, hour: Int, minute: Int): Date =
        Calendar.getInstance().apply {
            set(2026, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
}
