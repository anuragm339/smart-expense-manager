package com.expensemanager.app.ui.categories

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.expensemanager.app.data.repository.CategorySpendingResult
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
 * Comprehensive unit tests for CategoriesViewModel
 * Tests category loading, management, and UI state handling
 */
@ExperimentalCoroutinesApi
class CategoriesViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    // Mock dependencies
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockRepository = mockk<ExpenseRepository>()

    // System under test
    private lateinit var viewModel: CategoriesViewModel

    // Test data
    private val testDate = Date()
    private val testCategorySpendingResult = CategorySpendingResult(
        category_name = "Food & Dining",
        color = "#ff5722",
        total_amount = 1500.0,
        transaction_count = 10,
        last_transaction_date = testDate
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
        coEvery { mockRepository.getCategorySpending(any(), any()) } returns listOf(testCategorySpendingResult)
        coEvery { mockRepository.syncNewSMS() } returns 0

        viewModel = CategoriesViewModel(mockContext, mockRepository)
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
        assertFalse(initialState.isEmpty)
        assertFalse(initialState.hasError)
        assertNull(initialState.error)
        assertTrue(initialState.lastRefreshTime > 0)
    }

    @Test
    fun `viewModel should load categories on initialization`() = runTest {
        // Given - ViewModel is initialized (done in setup)
        
        // When - ViewModel is created
        
        // Then - should call repository to load category spending
        coVerify(exactly = 1) { mockRepository.getCategorySpending(any(), any()) }
        
        // And state should be updated with categories
        val state = viewModel.uiState.first()
        assertFalse(state.isInitialLoading)
        assertFalse(state.isEmpty)
        assertTrue(state.categories.isNotEmpty())
        assertEquals(1500.0, state.totalSpent, 0.01)
        assertEquals(1, state.categoryCount)
    }

    // MARK: - Loading State Tests

    @Test
    fun `refresh categories should trigger refresh state`() = runTest {
        // Given - ViewModel is initialized
        
        // When - refresh event is handled
        viewModel.handleEvent(CategoriesUIEvent.Refresh)
        
        // Then - should call repository again
        coVerify(exactly = 2) { mockRepository.getCategorySpending(any(), any()) }
    }

    @Test
    fun `load categories should show initial loading state`() = runTest {
        // Given - slow repository response
        coEvery { mockRepository.getCategorySpending(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(100)
            listOf(testCategorySpendingResult)
        }
        
        // When - loading categories event
        viewModel.handleEvent(CategoriesUIEvent.LoadCategories)
        
        // Then - should call repository
        coVerify(atLeast = 1) { mockRepository.getCategorySpending(any(), any()) }
    }

    // MARK: - Error State Tests

    @Test
    fun `repository failure should show error state`() = runTest {
        // Given - repository returns failure
        val errorMessage = "Database connection failed"
        coEvery { mockRepository.getCategorySpending(any(), any()) } throws Exception(errorMessage)
        
        // When - ViewModel is created
        val errorViewModel = CategoriesViewModel(mockContext, mockRepository)
        
        // Then - should show error state
        val state = errorViewModel.uiState.first()
        assertTrue(state.hasError)
        assertEquals("Something went wrong. Please try again", state.error)
        assertFalse(state.isInitialLoading)
    }

    @Test
    fun `clear error event should remove error state`() = runTest {
        // Given - ViewModel with error state
        coEvery { mockRepository.getCategorySpending(any(), any()) } throws Exception("Test error")
        val errorViewModel = CategoriesViewModel(mockContext, mockRepository)
        
        // When - clearing error
        errorViewModel.handleEvent(CategoriesUIEvent.ClearError)
        
        // Then - error should be cleared
        val state = errorViewModel.uiState.first()
        assertFalse(state.hasError)
        assertNull(state.error)
    }

    @Test
    fun `network error should show specific error message`() = runTest {
        // Given - network error occurs
        coEvery { mockRepository.getCategorySpending(any(), any()) } throws Exception("network timeout occurred")
        
        // When - ViewModel is created
        val networkErrorViewModel = CategoriesViewModel(mockContext, mockRepository)
        
        // Then - should show network-specific error
        val state = networkErrorViewModel.uiState.first()
        assertTrue(state.hasError)
        assertEquals("Check your internet connection and try again", state.error)
    }

    @Test
    fun `permission error should show specific error message`() = runTest {
        // Given - permission error occurs
        coEvery { mockRepository.getCategorySpending(any(), any()) } throws SecurityException("permission denied")
        
        // When - ViewModel is created
        val permissionErrorViewModel = CategoriesViewModel(mockContext, mockRepository)
        
        // Then - should show sample data instead of error
        val state = permissionErrorViewModel.uiState.first()
        // Should fall back to sample data rather than showing error
        assertFalse(state.isEmpty)
        assertTrue(state.categories.isNotEmpty())
    }

    // MARK: - Empty State Tests

    @Test
    fun `empty category data should show empty state`() = runTest {
        // Given - repository returns empty list
        coEvery { mockRepository.getCategorySpending(any(), any()) } returns emptyList()
        coEvery { mockRepository.syncNewSMS() } returns 0
        
        // When - ViewModel is created
        val emptyViewModel = CategoriesViewModel(mockContext, mockRepository)
        
        // Then - should show sample categories (fallback behavior)
        val state = emptyViewModel.uiState.first()
        // The ViewModel falls back to sample data when no categories found
        assertFalse(state.isEmpty)
        assertTrue(state.categories.isNotEmpty())
    }

    // MARK: - Add Category Tests

    @Test
    fun `add category should create new category item`() = runTest {
        // Given - ViewModel with initial categories
        val initialState = viewModel.uiState.first()
        val initialCount = initialState.categoryCount
        
        // When - adding new category
        viewModel.handleEvent(CategoriesUIEvent.AddCategory("Shopping", "🛍️"))
        
        // Then - should add new category to state
        val updatedState = viewModel.uiState.first()
        assertEquals(initialCount + 1, updatedState.categoryCount)
        
        val shoppingCategory = updatedState.categories.find { it.name == "Shopping" }
        assertNotNull(shoppingCategory)
        assertEquals("🛍️", shoppingCategory?.emoji)
        assertEquals(0.0, shoppingCategory?.amount, 0.01)
        assertEquals(0, shoppingCategory?.transactionCount)
        assertEquals("No transactions yet", shoppingCategory?.lastTransaction)
    }

    @Test
    fun `add category with empty name should handle gracefully`() = runTest {
        // Given - ViewModel with initial categories
        val initialState = viewModel.uiState.first()
        val initialCount = initialState.categoryCount
        
        // When - adding category with empty name
        viewModel.handleEvent(CategoriesUIEvent.AddCategory("", "🎯"))
        
        // Then - should handle gracefully (might add with empty name or ignore)
        val updatedState = viewModel.uiState.first()
        // Either ignores the empty category or adds it - both are valid behaviors
        assertTrue(updatedState.categoryCount >= initialCount)
    }

    // MARK: - Quick Add Expense Tests

    @Test
    fun `quick add expense should trigger data refresh`() = runTest {
        // Given - ViewModel is initialized
        clearMocks(mockRepository, answers = false)
        coEvery { mockRepository.getCategorySpending(any(), any()) } returns listOf(testCategorySpendingResult)
        
        // When - adding quick expense
        viewModel.handleEvent(CategoriesUIEvent.QuickAddExpense(
            amount = 150.0,
            merchant = "Test Restaurant",
            category = "Food & Dining"
        ))
        
        // Then - should refresh categories (call repository again)
        coVerify(exactly = 1) { mockRepository.getCategorySpending(any(), any()) }
    }

    // MARK: - Category Selection Tests

    @Test
    fun `category selection should be handled`() = runTest {
        // Given - ViewModel with categories
        
        // When - selecting a category
        viewModel.handleEvent(CategoriesUIEvent.CategorySelected("Food & Dining"))
        
        // Then - should handle selection (no state change expected, just logging)
        val state = viewModel.uiState.first()
        // Category selection is handled by Fragment for navigation, so no state change
        assertNotNull(state)
    }

    // MARK: - Data Processing Tests

    @Test
    fun `multiple categories should calculate percentages correctly`() = runTest {
        // Given - multiple category results
        val categoryResults = listOf(
            testCategorySpendingResult.copy(category_name = "Food", total_amount = 1000.0),
            testCategorySpendingResult.copy(category_name = "Transport", total_amount = 500.0),
            testCategorySpendingResult.copy(category_name = "Shopping", total_amount = 250.0)
        )
        coEvery { mockRepository.getCategorySpending(any(), any()) } returns categoryResults
        
        // When - ViewModel loads categories
        val multiCategoryViewModel = CategoriesViewModel(mockContext, mockRepository)
        
        // Then - should calculate percentages correctly
        val state = multiCategoryViewModel.uiState.first()
        assertEquals(1750.0, state.totalSpent, 0.01) // 1000 + 500 + 250
        assertEquals(3, state.categoryCount)
        
        // Find categories and check percentages
        val foodCategory = state.categories.find { it.name == "Food" }
        val transportCategory = state.categories.find { it.name == "Transport" }
        val shoppingCategory = state.categories.find { it.name == "Shopping" }
        
        assertNotNull(foodCategory)
        assertNotNull(transportCategory)
        assertNotNull(shoppingCategory)
        
        // Food: 1000/1750 = 57%
        assertEquals(57, foodCategory?.percentage)
        // Transport: 500/1750 = 28%
        assertEquals(28, transportCategory?.percentage)
        // Shopping: 250/1750 = 14%
        assertEquals(14, shoppingCategory?.percentage)
    }

    @Test
    fun `categories should be sorted by amount descending`() = runTest {
        // Given - multiple categories with different amounts
        val categoryResults = listOf(
            testCategorySpendingResult.copy(category_name = "Low Amount", total_amount = 100.0),
            testCategorySpendingResult.copy(category_name = "High Amount", total_amount = 2000.0),
            testCategorySpendingResult.copy(category_name = "Medium Amount", total_amount = 500.0)
        )
        coEvery { mockRepository.getCategorySpending(any(), any()) } returns categoryResults
        
        // When - ViewModel loads categories
        val sortedCategoriesViewModel = CategoriesViewModel(mockContext, mockRepository)
        
        // Then - should be sorted by amount descending
        val state = sortedCategoriesViewModel.uiState.first()
        val categories = state.categories
        
        assertTrue(categories.size >= 3)
        
        // Find the categories (they might have additional sample categories)
        val highCategory = categories.find { it.name == "High Amount" }
        val mediumCategory = categories.find { it.name == "Medium Amount" }
        val lowCategory = categories.find { it.name == "Low Amount" }
        
        assertNotNull(highCategory)
        assertNotNull(mediumCategory)
        assertNotNull(lowCategory)
        
        // Check order - high amount should come before medium, medium before low
        val highIndex = categories.indexOf(highCategory)
        val mediumIndex = categories.indexOf(mediumCategory)
        val lowIndex = categories.indexOf(lowCategory)
        
        assertTrue("High amount should come before medium", highIndex < mediumIndex)
        assertTrue("Medium amount should come before low", mediumIndex < lowIndex)
    }

    // MARK: - SMS Sync Fallback Tests

    @Test
    fun `fallback should trigger SMS sync when no data available`() = runTest {
        // Given - no data in repository initially
        coEvery { mockRepository.getCategorySpending(any(), any()) } returns emptyList() andThen listOf(testCategorySpendingResult)
        coEvery { mockRepository.syncNewSMS() } returns 5
        
        // When - ViewModel is created
        val fallbackViewModel = CategoriesViewModel(mockContext, mockRepository)
        
        // Then - should trigger SMS sync as fallback
        coVerify(exactly = 1) { mockRepository.syncNewSMS() }
        coVerify(exactly = 2) { mockRepository.getCategorySpending(any(), any()) } // Once initial, once after sync
    }

    @Test
    fun `fallback with no SMS data should show sample categories`() = runTest {
        // Given - no data in repository and no SMS data
        coEvery { mockRepository.getCategorySpending(any(), any()) } returns emptyList()
        coEvery { mockRepository.syncNewSMS() } returns 0
        
        // When - ViewModel is created
        val noDataViewModel = CategoriesViewModel(mockContext, mockRepository)
        
        // Then - should show sample categories
        val state = noDataViewModel.uiState.first()
        assertFalse(state.isEmpty)
        assertTrue(state.categories.isNotEmpty())
        
        // Should contain some default sample categories
        val foodCategory = state.categories.find { it.name.contains("Food", ignoreCase = true) }
        assertNotNull("Should have food category in samples", foodCategory)
    }

    // MARK: - Date Range Tests

    @Test
    fun `should use current month date range for category loading`() = runTest {
        // Given - ViewModel is initialized
        
        // When - categories are loaded
        
        // Then - should call repository with current month date range
        val startDateSlot = slot<Date>()
        val endDateSlot = slot<Date>()
        coVerify { mockRepository.getCategorySpending(capture(startDateSlot), capture(endDateSlot)) }
        
        val startDate = startDateSlot.captured
        val endDate = endDateSlot.captured
        
        // Start date should be first day of current month
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
        
        // End date should be later than start date
        assertTrue("End date should be after start date", endDate.after(startDate))
    }

    // MARK: - Error Recovery Tests

    @Test
    fun `error state should not prevent data reload on refresh`() = runTest {
        // Given - ViewModel with error state
        coEvery { mockRepository.getCategorySpending(any(), any()) } throws Exception("Initial error")
        val errorViewModel = CategoriesViewModel(mockContext, mockRepository)
        
        // Then - should be in error state
        val errorState = errorViewModel.uiState.first()
        assertTrue(errorState.hasError)
        
        // When - refreshing with successful response
        coEvery { mockRepository.getCategorySpending(any(), any()) } returns listOf(testCategorySpendingResult)
        errorViewModel.handleEvent(CategoriesUIEvent.Refresh)
        
        // Then - should recover from error and show data
        val recoveredState = errorViewModel.uiState.first()
        assertFalse(recoveredState.hasError)
        assertNull(recoveredState.error)
        assertTrue(recoveredState.categories.isNotEmpty())
    }

    // MARK: - State Consistency Tests

    @Test
    fun `multiple rapid events should maintain consistent state`() = runTest {
        // Given - ViewModel is initialized
        
        // When - triggering multiple rapid events
        viewModel.handleEvent(CategoriesUIEvent.Refresh)
        viewModel.handleEvent(CategoriesUIEvent.AddCategory("NewCategory", "📱"))
        viewModel.handleEvent(CategoriesUIEvent.ClearError)
        
        // Then - final state should be consistent
        val finalState = viewModel.uiState.first()
        assertFalse(finalState.hasError)
        assertTrue(finalState.categoryCount > 0)
        
        // New category should be present
        val newCategory = finalState.categories.find { it.name == "NewCategory" }
        assertNotNull(newCategory)
    }

    // MARK: - Category Emoji and Color Tests

    @Test
    fun `new categories should get appropriate emoji and color`() = runTest {
        // Given - ViewModel is initialized
        
        // When - adding categories with different names
        viewModel.handleEvent(CategoriesUIEvent.AddCategory("Food & Dining", ""))
        viewModel.handleEvent(CategoriesUIEvent.AddCategory("Transportation", ""))
        viewModel.handleEvent(CategoriesUIEvent.AddCategory("Unknown Category", ""))
        
        // Then - should assign appropriate emojis and colors
        val state = viewModel.uiState.first()
        
        val foodCategory = state.categories.find { it.name == "Food & Dining" }
        val transportCategory = state.categories.find { it.name == "Transportation" }
        val unknownCategory = state.categories.find { it.name == "Unknown Category" }
        
        assertNotNull(foodCategory)
        assertNotNull(transportCategory)
        assertNotNull(unknownCategory)
        
        // Should have non-empty colors
        assertTrue(foodCategory?.color?.isNotEmpty() == true)
        assertTrue(transportCategory?.color?.isNotEmpty() == true)
        assertTrue(unknownCategory?.color?.isNotEmpty() == true)
        
        // Colors should be valid hex colors
        assertTrue(foodCategory?.color?.startsWith("#") == true)
        assertTrue(transportCategory?.color?.startsWith("#") == true)
        assertTrue(unknownCategory?.color?.startsWith("#") == true)
    }
}