package com.expensemanager.app.domain.usecase.dashboard

import com.expensemanager.app.data.repository.DashboardData
import com.expensemanager.app.domain.repository.DashboardRepositoryInterface
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.*

/**
 * Comprehensive unit tests for GetDashboardDataUseCase
 * Tests business logic, error handling, and date range calculations
 */
class GetDashboardDataUseCaseTest {

    // Mock dependencies
    private val mockRepository = mockk<DashboardRepositoryInterface>()

    // System under test
    private lateinit var useCase: GetDashboardDataUseCase

    // Test data
    private val testDashboardData = DashboardData(
        totalSpent = 1500.0,
        totalCredits = 5000.0,
        actualBalance = 3500.0, // 5000 - 1500
        transactionCount = 25,
        topCategories = emptyList(),
        topMerchants = emptyList()
    )

    @Before
    fun setup() {
        useCase = GetDashboardDataUseCase(mockRepository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // MARK: - Basic Execute Tests

    @Test
    fun `execute should return success when repository succeeds`() = runTest {
        // Given
        val startDate = Date(System.currentTimeMillis() - 86400000) // Yesterday
        val endDate = Date()
        coEvery { mockRepository.getDashboardData(startDate, endDate) } returns testDashboardData

        // When
        val result = useCase.execute(startDate, endDate)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testDashboardData, result.getOrNull())
        coVerify(exactly = 1) { mockRepository.getDashboardData(startDate, endDate) }
    }

    @Test
    fun `execute should return failure when repository throws exception`() = runTest {
        // Given
        val startDate = Date()
        val endDate = Date()
        val exception = RuntimeException("Database error")
        coEvery { mockRepository.getDashboardData(any(), any()) } throws exception

        // When
        val result = useCase.execute(startDate, endDate)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 1) { mockRepository.getDashboardData(startDate, endDate) }
    }

    @Test
    fun `execute should pass correct date parameters to repository`() = runTest {
        // Given
        val specificStartDate = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val specificEndDate = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
        coEvery { mockRepository.getDashboardData(any(), any()) } returns testDashboardData

        // When
        useCase.execute(specificStartDate, specificEndDate)

        // Then
        coVerify(exactly = 1) { mockRepository.getDashboardData(specificStartDate, specificEndDate) }
    }

    // MARK: - Enhanced Analysis Tests

    @Test
    fun `execute with params should return dashboard analysis`() = runTest {
        // Given
        val startDate = Date(System.currentTimeMillis() - 86400000 * 30) // 30 days ago
        val endDate = Date()
        val params = DashboardParams(startDate, endDate, includeInsights = true)
        coEvery { mockRepository.getDashboardData(startDate, endDate) } returns testDashboardData

        // When
        val result = useCase.execute(params)

        // Then
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertEquals(testDashboardData, analysis?.originalData)
        assertEquals(1500.0, analysis?.totalSpent, 0.01)
        assertEquals(25, analysis?.transactionCount)
        assertTrue(analysis?.daysInPeriod ?: 0 > 0)
        assertTrue(analysis?.averageDailySpending ?: 0.0 > 0)
        assertNotNull(analysis?.insights)
    }

    @Test
    fun `execute with params should calculate correct averages`() = runTest {
        // Given - 30 days period
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        calendar.add(Calendar.DAY_OF_MONTH, -30)
        val startDate = calendar.time
        
        val params = DashboardParams(startDate, endDate)
        coEvery { mockRepository.getDashboardData(any(), any()) } returns testDashboardData

        // When
        val result = useCase.execute(params)

        // Then
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        
        // Should be 31 days (inclusive)
        assertEquals(31, analysis?.daysInPeriod)
        
        // Average daily spending = 1500 / 31 ≈ 48.39
        val expectedDailyAverage = 1500.0 / 31
        assertEquals(expectedDailyAverage, analysis?.averageDailySpending, 0.01)
        
        // Average transaction amount = 1500 / 25 = 60
        assertEquals(60.0, analysis?.averageTransactionAmount, 0.01)
    }

    @Test
    fun `execute with params should generate appropriate insights`() = runTest {
        // Given - high spending data
        val highSpendingData = testDashboardData.copy(totalSpent = 62000.0, transactionCount = 31) // 2000/day
        val params = DashboardParams(
            startDate = Date(System.currentTimeMillis() - 86400000 * 30),
            endDate = Date()
        )
        coEvery { mockRepository.getDashboardData(any(), any()) } returns highSpendingData

        // When
        val result = useCase.execute(params)

        // Then
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertTrue(analysis?.insights?.isNotEmpty() == true)
        
        // Should contain high spending insight
        val insights = analysis?.insights?.joinToString(" ") ?: ""
        assertTrue(insights.contains("High daily spending detected") || insights.contains("budget"))
    }

    // MARK: - Current Month Tests

    @Test
    fun `getCurrentMonthDashboard should use correct date range`() = runTest {
        // Given
        coEvery { mockRepository.getDashboardData(any(), any()) } returns testDashboardData

        // When
        val result = useCase.getCurrentMonthDashboard()

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockRepository.getDashboardData(any(), any()) }
        
        // Verify the dates are for current month (start of month to now)
        val capturedStartDate = slot<Date>()
        val capturedEndDate = slot<Date>()
        coVerify { mockRepository.getDashboardData(capture(capturedStartDate), capture(capturedEndDate)) }
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Start date should be first of current month
        assertEquals(calendar.timeInMillis, capturedStartDate.captured.time, 1000) // Allow 1 second tolerance
    }

    @Test
    fun `getCurrentMonthDashboard should handle repository failure`() = runTest {
        // Given
        val exception = RuntimeException("Network error")
        coEvery { mockRepository.getDashboardData(any(), any()) } throws exception

        // When
        val result = useCase.getCurrentMonthDashboard()

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // MARK: - Last 30 Days Tests

    @Test
    fun `getLastThirtyDaysDashboard should use correct date range`() = runTest {
        // Given
        coEvery { mockRepository.getDashboardData(any(), any()) } returns testDashboardData

        // When
        val result = useCase.getLastThirtyDaysDashboard()

        // Then
        assertTrue(result.isSuccess)
        
        val capturedStartDate = slot<Date>()
        val capturedEndDate = slot<Date>()
        coVerify { mockRepository.getDashboardData(capture(capturedStartDate), capture(capturedEndDate)) }
        
        // Start date should be approximately 30 days ago
        val thirtyDaysAgo = Date(System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000))
        val timeDiff = Math.abs(capturedStartDate.captured.time - thirtyDaysAgo.time)
        assertTrue("Start date should be approximately 30 days ago", timeDiff < 60000) // Within 1 minute
        
        // End date should be approximately now
        val now = Date()
        val endTimeDiff = Math.abs(capturedEndDate.captured.time - now.time)
        assertTrue("End date should be approximately now", endTimeDiff < 60000) // Within 1 minute
    }

    // MARK: - Current Week Tests

    @Test
    fun `getCurrentWeekDashboard should use correct date range`() = runTest {
        // Given
        coEvery { mockRepository.getDashboardData(any(), any()) } returns testDashboardData

        // When
        val result = useCase.getCurrentWeekDashboard()

        // Then
        assertTrue(result.isSuccess)
        
        val capturedStartDate = slot<Date>()
        coVerify { mockRepository.getDashboardData(capture(capturedStartDate), any()) }
        
        // Start date should be Monday of current week
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        assertEquals(calendar.timeInMillis, capturedStartDate.captured.time, 1000)
    }

    // MARK: - Dashboard Comparison Tests

    @Test
    fun `getDashboardComparison should return comparison data`() = runTest {
        // Given
        val currentStart = Date(System.currentTimeMillis() - 86400000 * 30)
        val currentEnd = Date()
        val previousStart = Date(System.currentTimeMillis() - 86400000 * 60)
        val previousEnd = Date(System.currentTimeMillis() - 86400000 * 31)
        
        val currentData = testDashboardData
        val previousData = testDashboardData.copy(totalSpent = 1000.0, transactionCount = 20)
        
        coEvery { mockRepository.getDashboardData(currentStart, currentEnd) } returns currentData
        coEvery { mockRepository.getDashboardData(previousStart, previousEnd) } returns previousData

        // When
        val result = useCase.getDashboardComparison(currentStart, currentEnd, previousStart, previousEnd)

        // Then
        assertTrue(result.isSuccess)
        val comparison = result.getOrNull()
        assertNotNull(comparison)
        
        assertEquals(currentData, comparison?.currentData)
        assertEquals(previousData, comparison?.previousData)
        
        // Spending change: 1500 - 1000 = 500
        assertEquals(500.0, comparison?.spendingChange, 0.01)
        
        // Percentage change: ((1500 - 1000) / 1000) * 100 = 50%
        assertEquals(50.0, comparison?.spendingPercentageChange, 0.01)
        
        // Transaction change: 25 - 20 = 5
        assertEquals(5, comparison?.transactionChange)
        
        // Transaction percentage change: ((25 - 20) / 20) * 100 = 25%
        assertEquals(25.0, comparison?.transactionPercentageChange, 0.01)
    }

    @Test
    fun `getDashboardComparison should handle zero previous values`() = runTest {
        // Given
        val currentStart = Date()
        val currentEnd = Date()
        val previousStart = Date()
        val previousEnd = Date()
        
        val currentData = testDashboardData
        val previousData = testDashboardData.copy(totalSpent = 0.0, transactionCount = 0)
        
        coEvery { mockRepository.getDashboardData(currentStart, currentEnd) } returns currentData
        coEvery { mockRepository.getDashboardData(previousStart, previousEnd) } returns previousData

        // When
        val result = useCase.getDashboardComparison(currentStart, currentEnd, previousStart, previousEnd)

        // Then
        val comparison = result.getOrNull()
        assertNotNull(comparison)
        
        // When previous is 0, percentage should be 100% (new spending)
        assertEquals(100.0, comparison?.spendingPercentageChange, 0.01)
        assertEquals(100.0, comparison?.transactionPercentageChange, 0.01)
    }

    @Test
    fun `getDashboardComparison should handle repository failure`() = runTest {
        // Given
        val exception = RuntimeException("Database error")
        coEvery { mockRepository.getDashboardData(any(), any()) } throws exception

        // When
        val result = useCase.getDashboardComparison(Date(), Date(), Date(), Date())

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // MARK: - Monthly Comparison Tests

    @Test
    fun `getMonthlyComparison should use correct date ranges`() = runTest {
        // Given
        coEvery { mockRepository.getDashboardData(any(), any()) } returns testDashboardData

        // When
        val result = useCase.getMonthlyComparison()

        // Then
        assertTrue(result.isSuccess)
        
        // Should call repository twice - once for current month, once for last month
        coVerify(exactly = 2) { mockRepository.getDashboardData(any(), any()) }
    }

    // MARK: - Edge Cases Tests

    @Test
    fun `execute should handle null or invalid dates gracefully`() = runTest {
        // Given
        val startDate = Date(0) // Epoch
        val endDate = Date(Long.MAX_VALUE) // Far future
        coEvery { mockRepository.getDashboardData(any(), any()) } returns testDashboardData

        // When
        val result = useCase.execute(startDate, endDate)

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockRepository.getDashboardData(startDate, endDate) }
    }

    @Test
    fun `execute should handle empty dashboard data`() = runTest {
        // Given
        val emptyData = DashboardData(
            totalSpent = 0.0,
            totalCredits = 0.0,
            actualBalance = 0.0,
            transactionCount = 0,
            topCategories = emptyList(),
            topMerchants = emptyList()
        )
        coEvery { mockRepository.getDashboardData(any(), any()) } returns emptyData

        // When
        val result = useCase.execute(Date(), Date())

        // Then
        assertTrue(result.isSuccess)
        assertEquals(emptyData, result.getOrNull())
    }

    // MARK: - Insight Generation Tests

    @Test
    fun `insights should reflect spending patterns correctly`() = runTest {
        // Given - low spending data
        val lowSpendingData = testDashboardData.copy(totalSpent = 300.0, transactionCount = 10) // 10/day for 30 days
        val params = DashboardParams(
            startDate = Date(System.currentTimeMillis() - 86400000 * 30),
            endDate = Date()
        )
        coEvery { mockRepository.getDashboardData(any(), any()) } returns lowSpendingData

        // When
        val result = useCase.execute(params)

        // Then
        val analysis = result.getOrNull()
        val insights = analysis?.insights?.joinToString(" ") ?: ""
        
        // Should contain low spending insight
        assertTrue(insights.contains("Good spending control") || insights.contains("under"))
    }

    @Test
    fun `insights should handle high transaction frequency`() = runTest {
        // Given - high frequency data
        val highFrequencyData = testDashboardData.copy(transactionCount = 310) // 10/day for 31 days
        val params = DashboardParams(
            startDate = Date(System.currentTimeMillis() - 86400000 * 30),
            endDate = Date()
        )
        coEvery { mockRepository.getDashboardData(any(), any()) } returns highFrequencyData

        // When
        val result = useCase.execute(params)

        // Then
        val analysis = result.getOrNull()
        val insights = analysis?.insights?.joinToString(" ") ?: ""
        
        // Should contain high frequency insight
        assertTrue(insights.contains("High transaction frequency") || insights.contains("consolidating"))
    }
}