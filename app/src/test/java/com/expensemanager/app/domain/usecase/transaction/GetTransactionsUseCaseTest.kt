package com.expensemanager.app.domain.usecase.transaction

import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.domain.repository.TransactionRepositoryInterface
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.*

/**
 * Comprehensive unit tests for GetTransactionsUseCase
 * Tests transaction retrieval, filtering, sorting, and business logic
 */
class GetTransactionsUseCaseTest {

    // Mock dependencies
    private val mockRepository = mockk<TransactionRepositoryInterface>()

    // System under test
    private lateinit var useCase: GetTransactionsUseCase

    // Test data
    private val testDate1 = Calendar.getInstance().apply {
        set(2024, Calendar.JANUARY, 15)
    }.time
    
    private val testDate2 = Calendar.getInstance().apply {
        set(2024, Calendar.JANUARY, 20)
    }.time

    private val testTransactions = listOf(
        TransactionEntity(
            id = 1L,
            amount = 150.0,
            rawMerchant = "McDonald's",
            normalizedMerchant = "mcdonalds",
            bankName = "HDFC Bank",
            transactionDate = testDate1,
            rawSmsBody = "Test SMS 1",
            confidenceScore = 0.95f,
            isExcluded = false,
            category = "Food",
            accountNumber = "****1234"
        ),
        TransactionEntity(
            id = 2L,
            amount = 500.0,
            rawMerchant = "Uber",
            normalizedMerchant = "uber",
            bankName = "ICICI Bank",
            transactionDate = testDate2,
            rawSmsBody = "Test SMS 2",
            confidenceScore = 0.90f,
            isExcluded = false,
            category = "Transport",
            accountNumber = "****5678"
        ),
        TransactionEntity(
            id = 3L,
            amount = 75.0,
            rawMerchant = "Starbucks",
            normalizedMerchant = "starbucks",
            bankName = "HDFC Bank",
            transactionDate = testDate1,
            rawSmsBody = "Test SMS 3",
            confidenceScore = 0.85f,
            isExcluded = false,
            category = "Food",
            accountNumber = "****1234"
        )
    )

    @Before
    fun setup() {
        useCase = GetTransactionsUseCase(mockRepository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // MARK: - Basic Execute Tests

    @Test
    fun `execute should return success when repository succeeds`() = runTest {
        // Given
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute()
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testTransactions, result.getOrNull())
        coVerify(exactly = 1) { mockRepository.getAllTransactions() }
    }

    @Test
    fun `execute should return failure when repository throws exception`() = runTest {
        // Given
        val exception = RuntimeException("Database error")
        coEvery { mockRepository.getAllTransactions() } throws exception

        // When
        val resultFlow = useCase.execute()
        val result = resultFlow.first()

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // MARK: - Execute with Params Tests

    @Test
    fun `execute with params should apply filtering and sorting`() = runTest {
        // Given
        val params = GetTransactionsParams(
            minAmount = 100.0,
            sortOrder = TransactionSortOrder.AMOUNT_DESC
        )
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        val filteredTransactions = result.getOrNull()
        assertNotNull(filteredTransactions)
        
        // Should filter out Starbucks (75.0) and sort by amount descending
        assertEquals(2, filteredTransactions?.size)
        assertEquals(500.0, filteredTransactions?.first()?.amount, 0.01) // Uber first (highest)
        assertEquals(150.0, filteredTransactions?.get(1)?.amount, 0.01) // McDonald's second
    }

    @Test
    fun `execute with date range params should filter by dates`() = runTest {
        // Given
        val params = GetTransactionsParams(
            startDate = testDate1,
            endDate = testDate1 // Same day to filter to testDate1 only
        )
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        val filteredTransactions = result.getOrNull()
        assertNotNull(filteredTransactions)
        
        // Should only include transactions from testDate1 (McDonald's and Starbucks)
        assertEquals(2, filteredTransactions?.size)
        filteredTransactions?.forEach { transaction ->
            assertEquals(testDate1, transaction.transactionDate)
        }
    }

    @Test
    fun `execute with merchant filter should filter by merchant name`() = runTest {
        // Given
        val params = GetTransactionsParams(merchantName = "uber")
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        val filteredTransactions = result.getOrNull()
        assertNotNull(filteredTransactions)
        
        // Should only include Uber transaction
        assertEquals(1, filteredTransactions?.size)
        assertEquals("Uber", filteredTransactions?.first()?.rawMerchant)
    }

    @Test
    fun `execute with bank filter should filter by bank name`() = runTest {
        // Given
        val params = GetTransactionsParams(bankName = "HDFC")
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        val filteredTransactions = result.getOrNull()
        assertNotNull(filteredTransactions)
        
        // Should include McDonald's and Starbucks (both HDFC)
        assertEquals(2, filteredTransactions?.size)
        filteredTransactions?.forEach { transaction ->
            assertEquals("HDFC Bank", transaction.bankName)
        }
    }

    @Test
    fun `execute with amount range should filter by amount range`() = runTest {
        // Given
        val params = GetTransactionsParams(
            minAmount = 100.0,
            maxAmount = 200.0
        )
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        val filteredTransactions = result.getOrNull()
        assertNotNull(filteredTransactions)
        
        // Should only include McDonald's (150.0)
        assertEquals(1, filteredTransactions?.size)
        assertEquals(150.0, filteredTransactions?.first()?.amount, 0.01)
        assertEquals("McDonald's", filteredTransactions?.first()?.rawMerchant)
    }

    @Test
    fun `execute with limit should limit results`() = runTest {
        // Given
        val params = GetTransactionsParams(limit = 2)
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        val limitedTransactions = result.getOrNull()
        assertNotNull(limitedTransactions)
        
        // Should only include first 2 transactions
        assertEquals(2, limitedTransactions?.size)
    }

    // MARK: - Sorting Tests

    @Test
    fun `execute should sort by date ascending when specified`() = runTest {
        // Given
        val params = GetTransactionsParams(sortOrder = TransactionSortOrder.DATE_ASC)
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        val sortedTransactions = result.getOrNull()
        assertNotNull(sortedTransactions)
        
        // First two should be from testDate1, last from testDate2
        assertTrue(sortedTransactions?.get(0)?.transactionDate?.equals(testDate1) == true)
        assertTrue(sortedTransactions?.get(1)?.transactionDate?.equals(testDate1) == true)
        assertTrue(sortedTransactions?.get(2)?.transactionDate?.equals(testDate2) == true)
    }

    @Test
    fun `execute should sort by amount ascending when specified`() = runTest {
        // Given
        val params = GetTransactionsParams(sortOrder = TransactionSortOrder.AMOUNT_ASC)
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        val sortedTransactions = result.getOrNull()
        assertNotNull(sortedTransactions)
        
        // Should be ordered: Starbucks (75), McDonald's (150), Uber (500)
        assertEquals(75.0, sortedTransactions?.get(0)?.amount, 0.01)
        assertEquals(150.0, sortedTransactions?.get(1)?.amount, 0.01)
        assertEquals(500.0, sortedTransactions?.get(2)?.amount, 0.01)
    }

    @Test
    fun `execute should sort by merchant name when specified`() = runTest {
        // Given
        val params = GetTransactionsParams(sortOrder = TransactionSortOrder.MERCHANT_ASC)
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        val sortedTransactions = result.getOrNull()
        assertNotNull(sortedTransactions)
        
        // Should be ordered alphabetically: McDonald's, Starbucks, Uber
        assertEquals("McDonald's", sortedTransactions?.get(0)?.rawMerchant)
        assertEquals("Starbucks", sortedTransactions?.get(1)?.rawMerchant)
        assertEquals("Uber", sortedTransactions?.get(2)?.rawMerchant)
    }

    // MARK: - Date Range Tests

    @Test
    fun `getTransactionsByDateRange should call repository with correct parameters`() = runTest {
        // Given
        val startDate = Date(System.currentTimeMillis() - 86400000) // Yesterday
        val endDate = Date()
        coEvery { mockRepository.getTransactionsByDateRange(startDate, endDate) } returns testTransactions

        // When
        val result = useCase.getTransactionsByDateRange(startDate, endDate)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testTransactions, result.getOrNull())
        coVerify(exactly = 1) { mockRepository.getTransactionsByDateRange(startDate, endDate) }
    }

    @Test
    fun `getTransactionsByDateRange should return failure when repository throws exception`() = runTest {
        // Given
        val exception = RuntimeException("Network error")
        coEvery { mockRepository.getTransactionsByDateRange(any(), any()) } throws exception

        // When
        val result = useCase.getTransactionsByDateRange(Date(), Date())

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // MARK: - Merchant Filter Tests

    @Test
    fun `getTransactionsByMerchant should call repository with correct merchant name`() = runTest {
        // Given
        val merchantName = "McDonald's"
        val merchantTransactions = testTransactions.filter { it.rawMerchant == merchantName }
        coEvery { mockRepository.getTransactionsByMerchant(merchantName) } returns merchantTransactions

        // When
        val result = useCase.getTransactionsByMerchant(merchantName)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(merchantTransactions, result.getOrNull())
        coVerify(exactly = 1) { mockRepository.getTransactionsByMerchant(merchantName) }
    }

    @Test
    fun `getTransactionsByMerchant should return failure when repository throws exception`() = runTest {
        // Given
        val exception = RuntimeException("Database error")
        coEvery { mockRepository.getTransactionsByMerchant(any()) } throws exception

        // When
        val result = useCase.getTransactionsByMerchant("TestMerchant")

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // MARK: - Search Tests

    @Test
    fun `searchTransactions should call repository with correct parameters`() = runTest {
        // Given
        val query = "McDonald"
        val limit = 20
        val searchResults = testTransactions.filter { it.rawMerchant.contains(query, ignoreCase = true) }
        coEvery { mockRepository.searchTransactions(query, limit) } returns searchResults

        // When
        val result = useCase.searchTransactions(query, limit)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(searchResults, result.getOrNull())
        coVerify(exactly = 1) { mockRepository.searchTransactions(query, limit) }
    }

    @Test
    fun `searchTransactions should use default limit when not specified`() = runTest {
        // Given
        val query = "test"
        coEvery { mockRepository.searchTransactions(query, 50) } returns testTransactions

        // When
        val result = useCase.searchTransactions(query) // No limit specified

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockRepository.searchTransactions(query, 50) } // Default limit 50
    }

    @Test
    fun `searchTransactions should return failure when repository throws exception`() = runTest {
        // Given
        val exception = RuntimeException("Search error")
        coEvery { mockRepository.searchTransactions(any(), any()) } throws exception

        // When
        val result = useCase.searchTransactions("test")

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // MARK: - Recent Transactions Tests

    @Test
    fun `getRecentTransactions should get last 30 days transactions`() = runTest {
        // Given
        coEvery { mockRepository.getTransactionsByDateRange(any(), any()) } returns testTransactions

        // When
        val result = useCase.getRecentTransactions()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testTransactions, result.getOrNull())
        
        // Verify dates are approximately correct (30 days ago to now)
        val startDateSlot = slot<Date>()
        val endDateSlot = slot<Date>()
        coVerify { mockRepository.getTransactionsByDateRange(capture(startDateSlot), capture(endDateSlot)) }
        
        val startDate = startDateSlot.captured
        val endDate = endDateSlot.captured
        val now = Date()
        val thirtyDaysAgo = Date(System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000))
        
        // Allow some tolerance for execution time
        assertTrue("Start date should be approximately 30 days ago", 
            Math.abs(startDate.time - thirtyDaysAgo.time) < 60000) // Within 1 minute
        assertTrue("End date should be approximately now", 
            Math.abs(endDate.time - now.time) < 60000) // Within 1 minute
    }

    // MARK: - Current Month Tests

    @Test
    fun `getCurrentMonthTransactions should get current month date range`() = runTest {
        // Given
        coEvery { mockRepository.getTransactionsByDateRange(any(), any()) } returns testTransactions

        // When
        val result = useCase.getCurrentMonthTransactions()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testTransactions, result.getOrNull())
        
        // Verify start date is first of current month
        val startDateSlot = slot<Date>()
        coVerify { mockRepository.getTransactionsByDateRange(capture(startDateSlot), any()) }
        
        val calendar = Calendar.getInstance()
        calendar.time = startDateSlot.captured
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
    }

    // MARK: - Complex Filtering Tests

    @Test
    fun `execute with multiple filters should apply all filters correctly`() = runTest {
        // Given
        val params = GetTransactionsParams(
            startDate = testDate1,
            endDate = testDate1,
            bankName = "HDFC",
            minAmount = 100.0,
            sortOrder = TransactionSortOrder.AMOUNT_DESC
        )
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        val filteredTransactions = result.getOrNull()
        assertNotNull(filteredTransactions)
        
        // Should only include McDonald's (testDate1, HDFC, amount >= 100)
        // Starbucks is filtered out by minAmount (75 < 100)
        // Uber is filtered out by date and bank
        assertEquals(1, filteredTransactions?.size)
        assertEquals("McDonald's", filteredTransactions?.first()?.rawMerchant)
        assertEquals(150.0, filteredTransactions?.first()?.amount, 0.01)
    }

    @Test
    fun `execute with filters that match no transactions should return empty list`() = runTest {
        // Given
        val params = GetTransactionsParams(
            merchantName = "NONEXISTENT_MERCHANT"
        )
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        val filteredTransactions = result.getOrNull()
        assertNotNull(filteredTransactions)
        assertEquals(0, filteredTransactions?.size)
    }

    // MARK: - Edge Cases Tests

    @Test
    fun `execute with empty transaction list should return empty result`() = runTest {
        // Given
        coEvery { mockRepository.getAllTransactions() } returns flowOf(emptyList())

        // When
        val resultFlow = useCase.execute()
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        val transactions = result.getOrNull()
        assertNotNull(transactions)
        assertEquals(0, transactions?.size)
    }

    @Test
    fun `execute with zero limits should not limit results`() = runTest {
        // Given
        val params = GetTransactionsParams(limit = 0) // No limit
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        val transactions = result.getOrNull()
        assertEquals(testTransactions.size, transactions?.size)
    }

    @Test
    fun `execute with negative amounts should be handled gracefully`() = runTest {
        // Given
        val params = GetTransactionsParams(
            minAmount = -100.0, // Negative minimum
            maxAmount = -50.0   // Negative maximum
        )
        coEvery { mockRepository.getAllTransactions() } returns flowOf(testTransactions)

        // When
        val resultFlow = useCase.execute(params)
        val result = resultFlow.first()

        // Then
        assertTrue(result.isSuccess)
        // Should handle negative amounts gracefully (likely return empty or all transactions)
        val transactions = result.getOrNull()
        assertNotNull(transactions)
    }
}