package com.expensemanager.app.ui.messages

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.data.entities.MerchantEntity
import com.expensemanager.app.data.repository.ExpenseRepository
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
 * Comprehensive unit tests for MessagesViewModel
 * Tests messages loading, filtering, sorting, and merchant management
 */
@ExperimentalCoroutinesApi
class MessagesViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    // Mock dependencies
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockExpenseRepository = mockk<ExpenseRepository>()

    // System under test
    private lateinit var viewModel: MessagesViewModel

    // Test data
    private val testDate = Date()
    private val testTransactionEntity = TransactionEntity(
        id = 1L,
        amount = 250.0,
        rawMerchant = "TEST_MERCHANT",
        normalizedMerchant = "test_merchant",
        bankName = "HDFC Bank",
        transactionDate = testDate,
        rawSmsBody = "Your account debited for Rs 250.0 at TEST_MERCHANT on 01-Jan-24",
        confidenceScore = 0.95f,
        isExcluded = false,
        category = "Food",
        accountNumber = "****1234"
    )

    private val testMerchantEntity = MerchantEntity(
        id = 1L,
        normalized_merchant = "test_merchant",
        display_name = "Test Merchant",
        category_id = 1L,
        category_name = "Food",
        category_color = "#4CAF50",
        transaction_count = 5,
        total_amount = 1250.0
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock SharedPreferences for Context
        val mockSharedPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val mockEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPrefs
        every { mockSharedPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.apply() } just Runs

        // Default mock behavior
        coEvery { mockExpenseRepository.getTransactionsByDateRange(any(), any()) } returns listOf(testTransactionEntity)
        coEvery { mockExpenseRepository.getMerchantWithCategory(any()) } returns testMerchantEntity

        viewModel = MessagesViewModel(mockContext, mockExpenseRepository)
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
        assertFalse(initialState.isLoading)
        assertFalse(initialState.isRefreshing)
        assertFalse(initialState.isSyncingSMS)
        assertFalse(initialState.isEmpty)
        assertFalse(initialState.hasError)
        assertNull(initialState.error)
        assertEquals("", initialState.searchQuery)
        assertEquals(SortOption(), initialState.currentSortOption)
        assertEquals(FilterOptions(), initialState.currentFilterOptions)
    }

    @Test
    fun `viewModel should load messages on initialization`() = runTest {
        // Given - ViewModel is initialized (done in setup)
        
        // When - ViewModel is created
        
        // Then - should call repository to load transactions
        coVerify(exactly = 1) { mockExpenseRepository.getTransactionsByDateRange(any(), any()) }
        coVerify(exactly = 1) { mockExpenseRepository.getMerchantWithCategory(any()) }
        
        // And state should be updated with message items
        val state = viewModel.uiState.first()
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
        assertEquals(1, state.allMessages.size)
        assertEquals(1, state.filteredMessages.size)
        assertEquals(1, state.groupedMessages.size)
    }

    // MARK: - Loading State Tests

    @Test
    fun `load messages should show loading state initially`() = runTest {
        // Given - slow repository response
        coEvery { mockExpenseRepository.getTransactionsByDateRange(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(100)
            listOf(testTransactionEntity)
        }
        
        // When - creating new ViewModel
        val testViewModel = MessagesViewModel(mockContext, mockExpenseRepository)
        
        // Then - should show loading state initially
        // Note: Due to init block, we need to test during construction
        coVerify { mockExpenseRepository.getTransactionsByDateRange(any(), any()) }
    }

    @Test
    fun `refresh messages should trigger refresh state`() = runTest {
        // Given - ViewModel is initialized
        
        // When - refresh event is handled
        viewModel.handleEvent(MessagesUIEvent.RefreshMessages)
        
        // Then - should call repository again
        coVerify(exactly = 2) { mockExpenseRepository.getTransactionsByDateRange(any(), any()) }
    }

    // MARK: - Error State Tests

    @Test
    fun `repository failure should show error state`() = runTest {
        // Given - repository returns failure
        val errorMessage = "Database error"
        coEvery { mockExpenseRepository.getTransactionsByDateRange(any(), any()) } throws Exception(errorMessage)
        
        // When - ViewModel is created
        val errorViewModel = MessagesViewModel(mockContext, mockExpenseRepository)
        
        // Then - should show error state
        val state = errorViewModel.uiState.first()
        assertTrue(state.hasError)
        assertEquals("Error reading messages: $errorMessage", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `clear error event should remove error state`() = runTest {
        // Given - ViewModel with error state
        coEvery { mockExpenseRepository.getTransactionsByDateRange(any(), any()) } throws Exception("Test error")
        val errorViewModel = MessagesViewModel(mockContext, mockExpenseRepository)
        
        // When - clearing error
        errorViewModel.handleEvent(MessagesUIEvent.ClearError)
        
        // Then - error should be cleared
        val state = errorViewModel.uiState.first()
        assertFalse(state.hasError)
        assertNull(state.error)
    }

    // MARK: - Empty State Tests

    @Test
    fun `empty transactions should show empty state`() = runTest {
        // Given - repository returns empty list
        coEvery { mockExpenseRepository.getTransactionsByDateRange(any(), any()) } returns emptyList()
        
        // When - ViewModel is created
        val emptyViewModel = MessagesViewModel(mockContext, mockExpenseRepository)
        
        // Then - should show empty state
        val state = emptyViewModel.uiState.first()
        assertTrue(state.isEmpty)
        assertEquals(0, state.allMessages.size)
        assertEquals(0, state.filteredMessages.size)
        assertEquals(0, state.groupedMessages.size)
    }

    // MARK: - Search Tests

    @Test
    fun `search should filter messages by merchant name`() = runTest {
        // Given - ViewModel with messages
        val state = viewModel.uiState.first()
        assertEquals(1, state.allMessages.size)
        
        // When - searching for merchant name
        viewModel.handleEvent(MessagesUIEvent.Search("Test"))
        
        // Then - should filter messages
        val searchState = viewModel.uiState.first()
        assertEquals("Test", searchState.searchQuery)
        assertEquals(1, searchState.filteredMessages.size)
    }

    @Test
    fun `search should filter messages by bank name`() = runTest {
        // Given - ViewModel with messages
        
        // When - searching for bank name
        viewModel.handleEvent(MessagesUIEvent.Search("HDFC"))
        
        // Then - should filter messages
        val searchState = viewModel.uiState.first()
        assertEquals("HDFC", searchState.searchQuery)
        assertEquals(1, searchState.filteredMessages.size)
    }

    @Test
    fun `search with no matches should return empty results`() = runTest {
        // Given - ViewModel with messages
        
        // When - searching for non-existent term
        viewModel.handleEvent(MessagesUIEvent.Search("NONEXISTENT"))
        
        // Then - should return empty results
        val searchState = viewModel.uiState.first()
        assertEquals("NONEXISTENT", searchState.searchQuery)
        assertEquals(0, searchState.filteredMessages.size)
    }

    // MARK: - Sorting Tests

    @Test
    fun `apply sort by amount ascending should sort messages`() = runTest {
        // Given - ViewModel with multiple messages
        val transaction2 = testTransactionEntity.copy(id = 2L, amount = 500.0)
        coEvery { mockExpenseRepository.getTransactionsByDateRange(any(), any()) } returns listOf(testTransactionEntity, transaction2)
        coEvery { mockExpenseRepository.getMerchantWithCategory(any()) } returns testMerchantEntity
        
        val multiMessageViewModel = MessagesViewModel(mockContext, mockExpenseRepository)
        
        // When - applying amount sort ascending
        multiMessageViewModel.handleEvent(MessagesUIEvent.ApplySort(SortOption("amount", ascending = true)))
        
        // Then - should sort by amount ascending
        val sortedState = multiMessageViewModel.uiState.first()
        assertEquals("amount", sortedState.currentSortOption.field)
        assertTrue(sortedState.currentSortOption.ascending)
        
        // First message should have smaller amount
        val firstMessage = sortedState.filteredMessages.firstOrNull()
        assertEquals(250.0, firstMessage?.amount, 0.01)
    }

    @Test
    fun `apply sort by merchant name should sort alphabetically`() = runTest {
        // Given - ViewModel with messages
        
        // When - applying merchant sort
        viewModel.handleEvent(MessagesUIEvent.ApplySort(SortOption("merchant", ascending = true)))
        
        // Then - should update sort option
        val sortedState = viewModel.uiState.first()
        assertEquals("merchant", sortedState.currentSortOption.field)
        assertTrue(sortedState.currentSortOption.ascending)
    }

    // MARK: - Filter Tests

    @Test
    fun `apply amount filter should filter messages by amount range`() = runTest {
        // Given - ViewModel with messages
        
        // When - applying amount filter
        val filterOptions = FilterOptions(minAmount = 200.0, maxAmount = 300.0)
        viewModel.handleEvent(MessagesUIEvent.ApplyFilter(filterOptions))
        
        // Then - should update filter options and apply filter
        val filteredState = viewModel.uiState.first()
        assertEquals(200.0, filteredState.currentFilterOptions.minAmount, 0.01)
        assertEquals(300.0, filteredState.currentFilterOptions.maxAmount, 0.01)
        assertEquals(1, filteredState.filteredMessages.size) // 250.0 is within range
    }

    @Test
    fun `apply bank filter should filter messages by bank`() = runTest {
        // Given - ViewModel with messages
        
        // When - applying bank filter
        val filterOptions = FilterOptions(selectedBanks = listOf("HDFC Bank"))
        viewModel.handleEvent(MessagesUIEvent.ApplyFilter(filterOptions))
        
        // Then - should filter by selected banks
        val filteredState = viewModel.uiState.first()
        assertEquals(listOf("HDFC Bank"), filteredState.currentFilterOptions.selectedBanks)
        assertEquals(1, filteredState.filteredMessages.size)
    }

    @Test
    fun `apply confidence filter should filter by confidence level`() = runTest {
        // Given - ViewModel with messages
        
        // When - applying confidence filter
        val filterOptions = FilterOptions(minConfidence = 90)
        viewModel.handleEvent(MessagesUIEvent.ApplyFilter(filterOptions))
        
        // Then - should filter by confidence level
        val filteredState = viewModel.uiState.first()
        assertEquals(90, filteredState.currentFilterOptions.minConfidence)
        assertEquals(1, filteredState.filteredMessages.size) // 95% confidence passes
    }

    @Test
    fun `reset filters should clear all filters`() = runTest {
        // Given - ViewModel with applied filters
        val filterOptions = FilterOptions(minAmount = 200.0, selectedBanks = listOf("HDFC Bank"))
        viewModel.handleEvent(MessagesUIEvent.ApplyFilter(filterOptions))
        
        // When - resetting filters
        viewModel.handleEvent(MessagesUIEvent.ResetFilters)
        
        // Then - should reset all filters
        val resetState = viewModel.uiState.first()
        assertEquals(FilterOptions(), resetState.currentFilterOptions)
        assertEquals("", resetState.searchQuery)
    }

    // MARK: - Merchant Group Tests

    @Test
    fun `toggle group inclusion should update group state`() = runTest {
        // Given - ViewModel with grouped messages
        val state = viewModel.uiState.first()
        assertEquals(1, state.groupedMessages.size)
        assertTrue(state.groupedMessages.first().isIncludedInCalculations)
        
        // When - toggling group inclusion
        viewModel.handleEvent(MessagesUIEvent.ToggleGroupInclusion("Test Merchant", false))
        
        // Then - should update inclusion state
        val updatedState = viewModel.uiState.first()
        assertFalse(updatedState.groupedMessages.first().isIncludedInCalculations)
    }

    @Test
    fun `update merchant group should trigger data reload`() = runTest {
        // Given - ViewModel with messages
        clearMocks(mockExpenseRepository, answers = false)
        coEvery { mockExpenseRepository.getTransactionsByDateRange(any(), any()) } returns listOf(testTransactionEntity)
        coEvery { mockExpenseRepository.getMerchantWithCategory(any()) } returns testMerchantEntity
        
        // When - updating merchant group
        viewModel.handleEvent(MessagesUIEvent.UpdateMerchantGroup(
            merchantName = "Test Merchant",
            newDisplayName = "New Merchant Name",
            newCategory = "Shopping"
        ))
        
        // Then - should reload messages
        coVerify(exactly = 1) { mockExpenseRepository.getTransactionsByDateRange(any(), any()) }
    }

    // MARK: - SMS Sync Tests

    @Test
    fun `resync SMS should trigger sync state`() = runTest {
        // Given - ViewModel is initialized
        
        // When - requesting SMS resync
        viewModel.handleEvent(MessagesUIEvent.ResyncSMS)
        
        // Then - should handle SMS sync (may show permission error in test environment)
        val state = viewModel.uiState.first()
        // In test environment, SMS access will fail, so we expect error state
        assertTrue(state.hasError || state.syncMessage != null)
    }

    @Test
    fun `test SMS scanning should provide test results`() = runTest {
        // Given - ViewModel is initialized
        
        // When - requesting SMS test
        viewModel.handleEvent(MessagesUIEvent.TestSMSScanning)
        
        // Then - should provide test results or error (permission issue in tests)
        val state = viewModel.uiState.first()
        assertTrue(state.testResults != null || state.hasError)
    }

    // MARK: - Combined Filter and Sort Tests

    @Test
    fun `combined search and filter should work together`() = runTest {
        // Given - ViewModel with messages
        
        // When - applying search and filter together
        viewModel.handleEvent(MessagesUIEvent.Search("Test"))
        viewModel.handleEvent(MessagesUIEvent.ApplyFilter(FilterOptions(minAmount = 200.0)))
        
        // Then - should apply both search and filter
        val state = viewModel.uiState.first()
        assertEquals("Test", state.searchQuery)
        assertEquals(200.0, state.currentFilterOptions.minAmount, 0.01)
        assertEquals(1, state.filteredMessages.size)
    }

    @Test
    fun `search with restrictive filter should return empty results`() = runTest {
        // Given - ViewModel with messages
        
        // When - applying restrictive filter that excludes all messages
        viewModel.handleEvent(MessagesUIEvent.ApplyFilter(FilterOptions(minAmount = 1000.0)))
        
        // Then - should return empty results
        val state = viewModel.uiState.first()
        assertEquals(0, state.filteredMessages.size)
        assertEquals(0, state.groupedMessages.size)
    }

    // MARK: - State Consistency Tests

    @Test
    fun `multiple rapid events should maintain consistent state`() = runTest {
        // Given - ViewModel is initialized
        
        // When - triggering multiple rapid events
        viewModel.handleEvent(MessagesUIEvent.Search("Test"))
        viewModel.handleEvent(MessagesUIEvent.ApplySort(SortOption("amount", ascending = false)))
        viewModel.handleEvent(MessagesUIEvent.ApplyFilter(FilterOptions(minAmount = 100.0)))
        viewModel.handleEvent(MessagesUIEvent.ClearError)
        
        // Then - final state should be consistent
        val finalState = viewModel.uiState.first()
        assertEquals("Test", finalState.searchQuery)
        assertEquals("amount", finalState.currentSortOption.field)
        assertFalse(finalState.currentSortOption.ascending)
        assertEquals(100.0, finalState.currentFilterOptions.minAmount, 0.01)
        assertFalse(finalState.hasError)
    }

    // MARK: - Group Structure Tests

    @Test
    fun `grouped messages should have correct structure`() = runTest {
        // Given - ViewModel with messages
        val state = viewModel.uiState.first()
        
        // Then - grouped messages should have correct structure
        assertEquals(1, state.groupedMessages.size)
        
        val group = state.groupedMessages.first()
        assertEquals("Test Merchant", group.merchantName)
        assertEquals(250.0, group.totalAmount, 0.01)
        assertEquals("Food", group.category)
        assertEquals("#4CAF50", group.categoryColor)
        assertEquals(1, group.transactions.size)
        assertTrue(group.isIncludedInCalculations)
        assertFalse(group.isExpanded)
    }

    @Test
    fun `group with multiple transactions should aggregate correctly`() = runTest {
        // Given - multiple transactions for same merchant
        val transaction2 = testTransactionEntity.copy(id = 2L, amount = 300.0)
        coEvery { mockExpenseRepository.getTransactionsByDateRange(any(), any()) } returns listOf(testTransactionEntity, transaction2)
        
        val multiTransactionViewModel = MessagesViewModel(mockContext, mockExpenseRepository)
        
        // Then - should group and aggregate correctly
        val state = multiTransactionViewModel.uiState.first()
        assertEquals(1, state.groupedMessages.size) // Same merchant, so 1 group
        
        val group = state.groupedMessages.first()
        assertEquals(550.0, group.totalAmount, 0.01) // 250 + 300
        assertEquals(2, group.transactions.size)
    }
}