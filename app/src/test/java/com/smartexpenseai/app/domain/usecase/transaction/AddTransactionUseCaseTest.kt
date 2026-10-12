package com.smartexpenseai.app.domain.usecase.transaction

import com.smartexpenseai.app.data.entities.CategoryEntity
import com.smartexpenseai.app.data.entities.MerchantEntity
import com.smartexpenseai.app.data.entities.TransactionEntity
import com.smartexpenseai.app.domain.repository.CategoryRepositoryInterface
import com.smartexpenseai.app.domain.repository.MerchantRepositoryInterface
import com.smartexpenseai.app.domain.repository.TransactionRepositoryInterface
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class AddTransactionUseCaseTest {

    private val transactionRepository = mockk<TransactionRepositoryInterface>()
    private val merchantRepository = mockk<MerchantRepositoryInterface>()
    private val categoryRepository = mockk<CategoryRepositoryInterface>()
    private val useCase = AddTransactionUseCase(
        transactionRepository,
        merchantRepository,
        categoryRepository,
    )

    @Test
    fun shouldInsertManualTransactionWithSelectedCategory() = runTest {
        val category = CategoryEntity(
            id = 7L,
            name = "Food & Dining",
            emoji = "🍽️",
            color = "#ff5722",
            createdAt = Date(),
        )
        val inserted = slot<TransactionEntity>()
        coEvery { transactionRepository.getTransactionBySmsId(any()) } returns null
        coEvery { categoryRepository.getCategoryByName("Food & Dining") } returns category
        coEvery { merchantRepository.getMerchantByNormalizedName("CAFE TEST") } returns null
        coEvery { merchantRepository.insertMerchant(any<MerchantEntity>()) } returns 3L
        coEvery { transactionRepository.insertTransaction(capture(inserted)) } returns 11L

        val transaction = useCase.createManualTransaction(
            amount = 250.0,
            merchantName = "Cafe Test",
            categoryName = "Food & Dining",
        )
        val result = useCase.execute(transaction)

        assertTrue(result.isSuccess)
        assertEquals(7L, inserted.captured.categoryId)
        assertEquals("CAFE TEST", inserted.captured.normalizedMerchant)
    }
}
