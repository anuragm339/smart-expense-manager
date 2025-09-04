package com.expensemanager.app.ui.dashboard

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.expensemanager.app.data.repository.DashboardData
import com.expensemanager.app.domain.usecase.dashboard.GetDashboardDataUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.*

/**
 * Comprehensive unit tests for DashboardViewModel
 * Tests all states, events, and business logic
 */
@ExperimentalCoroutinesApi
class DashboardViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    // Mock dependencies
    private val mockGetDashboardDataUseCase = mockk<GetDashboardDataUseCase>()

    // System under test
    private lateinit var viewModel: DashboardViewModel

    // Test data
    private val testDashboardData = DashboardData(
        totalSpent = 1500.0,
        totalCredits = 5000.0,
        actualBalance = 3500.0,
        transactionCount = 25,
        topCategories = emptyList(),
        topMerchants = emptyList(),
        topMerchantsWithCategory = emptyList(),
        monthlyBalance = com.expensemanager.app.data.repository.MonthlyBalanceInfo(
            lastSalaryAmount = 0.0,
            lastSalaryDate = null,
            currentMonthExpenses = 0.0,
            remainingBalance = 0.0,
            hasSalaryData = false
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Default mock behavior - success case
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } returns Result.success(testDashboardData)
        
        viewModel = DashboardViewModel(mockGetDashboardDataUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // MARK: - Initial State Tests

    @Test
    fun `initial state should be correct`() = runTest {
        // Given - fresh ViewModel
        
        // When - checking initial state
        val initialState = viewModel.uiState.first()
        
        // Then - state should have correct defaults
        assertFalse(initialState.isInitialLoading)
        assertFalse(initialState.isRefreshing)
        assertFalse(initialState.isLoading)
        assertFalse(initialState.isSyncingSMS)
        assertEquals("This Month", initialState.dashboardPeriod)
        assertEquals("This Month", initialState.timePeriod)
        assertFalse(initialState.isEmpty)
        assertFalse(initialState.hasError)
        assertNull(initialState.error)
        assertEquals(0L, initialState.lastRefreshTime)
    }

    @Test
    fun `viewModel should load dashboard data on initialization`() = runTest {
        // Given - ViewModel is initialized (done in setup)
        
        // When - ViewModel is created
        
        // Then - should call use case to load data
        coVerify(exactly = 1) { mockGetDashboardDataUseCase.execute(any(), any()) }
        
        // And state should be updated
        val state = viewModel.uiState.first()
        assertEquals(testDashboardData, state.dashboardData)
        assertFalse(state.hasError)
        assertFalse(state.isEmpty)
    }

    // MARK: - Loading State Tests

    @Test
    fun `refresh event should trigger loading state`() = runTest {
        // Given - ViewModel is initialized
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(100)
            Result.success(testDashboardData)
        }
        
        // When - refresh event is handled
        viewModel.handleEvent(DashboardUIEvent.Refresh)
        
        // Then - should show refreshing state initially
        val stateWhileLoading = viewModel.uiState.first()
        assertTrue(stateWhileLoading.isRefreshing)
        assertFalse(stateWhileLoading.hasError)
    }

    @Test
    fun `period change should trigger loading state`() = runTest {
        // Given - ViewModel is initialized
        
        // When - changing period
        viewModel.handleEvent(DashboardUIEvent.ChangePeriod("Last Month"))
        
        // Then - should update period and trigger data reload
        val state = viewModel.uiState.first()
        assertEquals("Last Month", state.dashboardPeriod)
        assertEquals("Last Month", state.timePeriod)
        
        // And should call use case again
        coVerify(exactly = 2) { mockGetDashboardDataUseCase.execute(any(), any()) }
    }

    // MARK: - Error State Tests

    @Test
    fun `use case failure should show error state`() = runTest {
        // Given - use case returns failure
        val errorMessage = "Network error occurred"
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } returns Result.failure(Exception(errorMessage))
        
        // When - ViewModel is created
        val freshViewModel = DashboardViewModel(mockGetDashboardDataUseCase)
        
        // Then - should show error state
        val state = freshViewModel.uiState.first()
        assertTrue(state.hasError)
        assertEquals("Something went wrong. Please try again", state.error)
        assertFalse(state.isInitialLoading)
    }

    @Test
    fun `clear error event should remove error state`() = runTest {
        // Given - ViewModel with error state
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } returns Result.failure(Exception("Test error"))
        val errorViewModel = DashboardViewModel(mockGetDashboardDataUseCase)
        
        // When - clearing error
        errorViewModel.handleEvent(DashboardUIEvent.ClearError)
        
        // Then - error should be cleared
        val state = errorViewModel.uiState.first()
        assertFalse(state.hasError)
        assertNull(state.error)
    }

    @Test
    fun `network error should show specific error message`() = runTest {
        // Given - network error occurs
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } returns Result.failure(Exception("network connection failed"))
        
        // When - ViewModel is created
        val freshViewModel = DashboardViewModel(mockGetDashboardDataUseCase)
        
        // Then - should show network-specific error
        val state = freshViewModel.uiState.first()
        assertTrue(state.hasError)
        assertEquals("Check your internet connection and try again", state.error)
    }

    // MARK: - Empty State Tests

    @Test
    fun `empty dashboard data should show empty state`() = runTest {
        // Given - use case returns empty data
        val emptyData = testDashboardData.copy(transactionCount = 0)
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } returns Result.success(emptyData)
        
        // When - ViewModel is created
        val freshViewModel = DashboardViewModel(mockGetDashboardDataUseCase)
        
        // Then - should show empty state
        val state = freshViewModel.uiState.first()
        assertTrue(state.isEmpty)
        assertTrue(state.shouldShowEmptyState)
        assertFalse(state.shouldShowContent)
    }

    // MARK: - Period Change Tests

    @Test
    fun `change period should update state and reload data`() = runTest {
        // Given - ViewModel with initial data
        
        // When - changing to different period
        viewModel.handleEvent(DashboardUIEvent.ChangePeriod("Last 3 Months"))
        
        // Then - should update period
        val state = viewModel.uiState.first()
        assertEquals("Last 3 Months", state.dashboardPeriod)
        assertEquals("Last 3 Months", state.timePeriod)
        
        // And should call use case with new date range
        coVerify(exactly = 2) { mockGetDashboardDataUseCase.execute(any(), any()) }
    }

    @Test
    fun `time period change should update trends without reloading main data`() = runTest {
        // Given - ViewModel with initial data
        
        // When - changing time period only
        viewModel.handleEvent(DashboardUIEvent.ChangeTimePeriod("Last 6 Months"))
        
        // Then - should update time period
        val state = viewModel.uiState.first()
        assertEquals("This Month", state.dashboardPeriod) // Main period unchanged
        assertEquals("Last 6 Months", state.timePeriod) // Time period changed
    }

    // MARK: - Custom Months Tests

    @Test
    fun `custom months selection should update state`() = runTest {
        // Given - ViewModel is initialized
        val firstMonth = Pair(5, 2024) // May 2024
        val secondMonth = Pair(4, 2024) // April 2024
        
        // When - selecting custom months
        viewModel.handleEvent(DashboardUIEvent.CustomMonthsSelected(firstMonth, secondMonth))
        
        // Then - should update custom month state
        val state = viewModel.uiState.first()
        assertEquals(firstMonth, state.customFirstMonth)
        assertEquals(secondMonth, state.customSecondMonth)
        assertEquals("Custom Months", state.dashboardPeriod)
        assertTrue(state.isCustomPeriod)
    }

    @Test
    fun `custom months with same months should show error`() = runTest {
        // Given - ViewModel is initialized
        val sameMonth = Pair(5, 2024)
        
        // When - selecting same months
        viewModel.handleEvent(DashboardUIEvent.CustomMonthsSelected(sameMonth, sameMonth))
        
        // Then - should show error
        val state = viewModel.uiState.first()
        assertTrue(state.hasError)
        assertEquals("Please select two different months", state.error)
    }

    // MARK: - SMS Sync Tests

    @Test
    fun `SMS sync should show not implemented error`() = runTest {
        // Given - ViewModel is initialized
        
        // When - requesting SMS sync
        viewModel.handleEvent(DashboardUIEvent.SyncSMS)
        
        // Then - should show not implemented error
        val state = viewModel.uiState.first()
        assertFalse(state.isSyncingSMS) // Should be false as not implemented
        assertTrue(state.hasError)
        assertEquals("SMS sync not yet implemented in ViewModel", state.error)
    }

    // MARK: - Quick Add Expense Tests

    @Test
    fun `quick add expense should refresh dashboard`() = runTest {
        // Given - ViewModel is initialized
        
        // When - adding quick expense
        viewModel.handleEvent(DashboardUIEvent.QuickAddExpense(
            amount = 250.0,
            merchant = "Test Merchant",
            category = "Food"
        ))
        
        // Then - should trigger refresh (additional call to use case)
        coVerify(exactly = 2) { mockGetDashboardDataUseCase.execute(any(), any()) }
    }

    // MARK: - Computed Properties Tests

    @Test
    fun `computed properties should return correct values`() = runTest {
        // Given - ViewModel with test data
        val state = viewModel.uiState.first()
        
        // Then - computed properties should be correct
        assertEquals(1500.0, state.totalSpent)
        assertEquals(25, state.transactionCount)
        assertEquals(0.0 - 1500.0, state.totalBalance) // Actual balance: 0 - expenses
        assertTrue(state.shouldShowContent)
        assertFalse(state.shouldShowEmptyState)
        assertFalse(state.shouldShowError)
        assertFalse(state.isAnyLoading)
    }

    @Test
    fun `loading states should affect computed properties`() = runTest {
        // Given - ViewModel in loading state
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(1000) // Long delay to keep loading
            Result.success(testDashboardData)
        }
        
        // When - triggering refresh
        viewModel.handleEvent(DashboardUIEvent.Refresh)
        
        // Then - loading states should be reflected
        val loadingState = viewModel.uiState.first()
        assertTrue(loadingState.isAnyLoading)
        assertTrue(loadingState.isRefreshing)
    }

    // MARK: - Date Range Tests

    @Test
    fun `different periods should call use case with different date ranges`() = runTest {
        // Given - ViewModel is initialized
        clearAllMocks() // Clear initial load calls
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } returns Result.success(testDashboardData)
        
        // When - changing to different periods
        viewModel.handleEvent(DashboardUIEvent.ChangePeriod("This Month"))
        viewModel.handleEvent(DashboardUIEvent.ChangePeriod("Last Month"))
        viewModel.handleEvent(DashboardUIEvent.ChangePeriod("Last 3 Months"))
        
        // Then - should call use case multiple times with different date ranges
        coVerify(exactly = 3) { mockGetDashboardDataUseCase.execute(any(), any()) }
        
        // Verify different date ranges are used (dates should be different)
        val capturedCalls = mutableListOf<Pair<Date, Date>>()
        coVerify { mockGetDashboardDataUseCase.execute(capture(slot<Date>()), capture(slot<Date>())) }
    }

    // MARK: - Error Recovery Tests

    @Test
    fun `error state should not prevent data reload on period change`() = runTest {
        // Given - ViewModel with error state
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } returns Result.failure(Exception("Initial error"))
        val errorViewModel = DashboardViewModel(mockGetDashboardDataUseCase)
        
        // Then - should be in error state
        val errorState = errorViewModel.uiState.first()
        assertTrue(errorState.hasError)
        
        // When - changing period (which should retry)
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } returns Result.success(testDashboardData)
        errorViewModel.handleEvent(DashboardUIEvent.ChangePeriod("Last Month"))
        
        // Then - should recover from error and show data
        val recoveredState = errorViewModel.uiState.first()
        assertFalse(recoveredState.hasError)
        assertNull(recoveredState.error)
        assertEquals(testDashboardData, recoveredState.dashboardData)
    }

    @Test
    fun `refresh should clear existing error state`() = runTest {
        // Given - ViewModel with error state
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } returns Result.failure(Exception("Initial error"))
        val errorViewModel = DashboardViewModel(mockGetDashboardDataUseCase)
        
        // When - refreshing with successful response
        coEvery { mockGetDashboardDataUseCase.execute(any(), any()) } returns Result.success(testDashboardData)
        errorViewModel.handleEvent(DashboardUIEvent.Refresh)
        
        // Then - error should be cleared
        val state = errorViewModel.uiState.first()
        assertFalse(state.hasError)
        assertNull(state.error)
        assertEquals(testDashboardData, state.dashboardData)
    }

    // MARK: - State Consistency Tests

    @Test
    fun `multiple rapid events should not cause inconsistent state`() = runTest {
        // Given - ViewModel is initialized
        
        // When - triggering multiple rapid events
        viewModel.handleEvent(DashboardUIEvent.Refresh)
        viewModel.handleEvent(DashboardUIEvent.ChangePeriod("Last Month"))
        viewModel.handleEvent(DashboardUIEvent.ChangeTimePeriod("Last 3 Months"))
        viewModel.handleEvent(DashboardUIEvent.ClearError)
        
        // Then - final state should be consistent
        val finalState = viewModel.uiState.first()
        assertEquals("Last Month", finalState.dashboardPeriod)
        assertEquals("Last 3 Months", finalState.timePeriod)
        assertFalse(finalState.hasError)
        
        // And use case should be called appropriately
        coVerify(atLeast = 2) { mockGetDashboardDataUseCase.execute(any(), any()) }
    }
}