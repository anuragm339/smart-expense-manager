package com.smartexpenseai.app.data.repository.internal

import android.content.Context
import com.smartexpenseai.app.data.dao.CategoryDao
import com.smartexpenseai.app.data.dao.MerchantDao
import com.smartexpenseai.app.data.dao.TransactionDao
import com.smartexpenseai.app.data.entities.CategoryEntity
import com.smartexpenseai.app.data.entities.MerchantEntity
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

internal class MerchantCategoryOperations(
    private val context: Context,
    private val merchantDao: MerchantDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val transactionRepository: TransactionDataRepository
) {

    private val logger = StructuredLogger(
        featureTag = "DATABASE",
        className = "MerchantCategoryOperations"
    )

    suspend fun updateMerchantExclusion(normalizedMerchantName: String, isExcluded: Boolean) {
        try {
            logger.debug(
                where = "updateMerchantExclusion",
                what = "[EXCLUSION] Updating merchant exclusion: '$normalizedMerchantName' -> $isExcluded"
            )

            merchantDao.updateMerchantExclusion(normalizedMerchantName, isExcluded)

            // CRITICAL FIX: Broadcast data change to notify dependent components
            val intent = android.content.Intent("com.smartexpenseai.app.DATA_CHANGED")
            context.sendBroadcast(intent)

            logger.info(
                where = "updateMerchantExclusion",
                what = "[EXCLUSION] Successfully updated exclusion and broadcast data change"
            )
        } catch (e: Exception) {
            logger.error(
                where = "updateMerchantExclusion",
                what = "[EXCLUSION] Failed to update merchant exclusion for '$normalizedMerchantName'",
                throwable = e
            )
            throw e
        }
    }

    suspend fun updateMerchantAliasInDatabase(
        originalMerchantNames: List<String>,
        newDisplayName: String,
        newCategoryName: String
    ): Boolean {
        return try {
            if (originalMerchantNames.isEmpty()) {
                return false
            }

            if (newDisplayName.trim().isEmpty()) {
                return false
            }

            if (newCategoryName.trim().isEmpty()) {
                return false
            }

            val category = ensureCategoryExists(newCategoryName) ?: return false

            var updatedCount = 0
            var createdCount = 0
            val failedUpdates = mutableListOf<String>()

            for (originalName in originalMerchantNames) {
                try {
                    val normalizedName = transactionRepository.normalizeMerchantName(originalName)
                    logger.debug(
                        where = "updateMerchantAliasInDatabase",
                        what = "[DB_UPDATE] originalName='$originalName' -> normalizedName='$normalizedName'"
                    )

                    val merchantExistsCount = merchantDao.merchantExists(normalizedName)
                    val merchantExists = merchantExistsCount > 0
                    logger.debug(
                        where = "updateMerchantAliasInDatabase",
                        what = "[DB_UPDATE] merchantExists=$merchantExists (count=$merchantExistsCount) for '$normalizedName'"
                    )

                    if (merchantExists) {
                        logger.debug(
                            where = "updateMerchantAliasInDatabase",
                            what = "[DB_UPDATE] Attempting UPDATE: normalizedName='$normalizedName', displayName='$newDisplayName', categoryId=${category.id}"
                        )
                        val rowsUpdated = merchantDao.updateMerchantDisplayNameAndCategory(
                            normalizedName = normalizedName,
                            displayName = newDisplayName,
                            categoryId = category.id
                        )
                        logger.info(
                            where = "updateMerchantAliasInDatabase",
                            what = "[DB_UPDATE] UPDATE affected $rowsUpdated rows for '$normalizedName'"
                        )

                        // CRITICAL FIX: Update all existing transactions for this merchant
                        val updatedTransactionsCount = transactionDao.updateTransactionsCategoryByMerchant(
                            normalizedMerchant = normalizedName,
                            newCategoryId = category.id
                        )
                        logger.info(
                            where = "updateMerchantAliasInDatabase",
                            what = "[DB_UPDATE] Updated $updatedTransactionsCount transactions for merchant '$normalizedName' to category '${category.name}'"
                        )

                        updatedCount++
                    } else {
                        // CRITICAL CHECK: Before creating new merchant, verify transactions exist for this normalized name
                        val transactionCount = transactionDao.getTransactionCountByMerchant(normalizedName)

                        if (transactionCount == 0) {
                            logger.error(
                                where = "updateMerchantAliasInDatabase",
                                what = "[DB_ERROR] Cannot create merchant '$normalizedName' - no transactions found with this normalized name. This indicates a normalization mismatch!"
                            )
                            failedUpdates.add(originalName)
                            continue
                        }

                        val newMerchant = MerchantEntity(
                            normalizedName = normalizedName,
                            displayName = newDisplayName,
                            categoryId = category.id,
                            isUserDefined = true,
                            createdAt = Date()
                        )

                        merchantDao.insertMerchant(newMerchant)
                        logger.info(
                            where = "updateMerchantAliasInDatabase",
                            what = "[DB_CREATE] Created new merchant '$normalizedName' with category '${category.name}'"
                        )

                        // Update existing transactions for this merchant
                        val updatedTransactionsCount = transactionDao.updateTransactionsCategoryByMerchant(
                            normalizedMerchant = normalizedName,
                            newCategoryId = category.id
                        )
                        logger.info(
                            where = "updateMerchantAliasInDatabase",
                            what = "[DB_CREATE] Updated $updatedTransactionsCount existing transactions for new merchant '$normalizedName'"
                        )

                        createdCount++
                    }
                } catch (e: Exception) {
                    logger.error(
                        where = "updateMerchantAliasInDatabase",
                        what = "Failed to process merchant '$originalName'",
                        throwable = e
                    )
                    failedUpdates.add(originalName)
                }
            }

            val totalProcessed = updatedCount + createdCount

            logger.info(
                where = "updateMerchantAliasInDatabase",
                what = "Updated $updatedCount merchants, created $createdCount merchants"
            )

            if (failedUpdates.isNotEmpty()) {
                logger.warn(
                    where = "updateMerchantAliasInDatabase",
                    what = "Failed to update ${failedUpdates.size} merchants: $failedUpdates"
                )
            }

            totalProcessed > 0 && failedUpdates.size < originalMerchantNames.size
        } catch (e: Exception) {
            logger.error(
                where = "updateMerchantAliasInDatabase",
                what = "Critical error during database update",
                throwable = e
            )
            false
        }
    }

    suspend fun getMerchantsInCategory(categoryName: String): List<com.smartexpenseai.app.ui.categories.MerchantInCategory> {
        return withContext(Dispatchers.IO) {
            try {
                val category = categoryDao.getCategoryByName(categoryName)
                if (category == null) {
                    logger.warn(
                        where = "getMerchantsInCategory",
                        what = "Category not found: $categoryName"
                    )
                    return@withContext emptyList()
                }

                val merchantsWithStats = transactionDao.getMerchantsInCategoryWithStats(category.id)
                val totalCategorySpending = merchantsWithStats.sumOf { it.totalAmount }

                merchantsWithStats.map { merchantStats ->
                    val percentage = if (totalCategorySpending > 0) {
                        ((merchantStats.totalAmount / totalCategorySpending) * 100).toFloat()
                    } else 0f

                    com.smartexpenseai.app.ui.categories.MerchantInCategory(
                        merchantName = merchantStats.displayName,
                        transactionCount = merchantStats.transactionCount,
                        totalAmount = merchantStats.totalAmount,
                        lastTransactionDate = merchantStats.lastTransactionDate,
                        currentCategory = categoryName,
                        percentage = percentage
                    )
                }.sortedByDescending { it.totalAmount }
            } catch (e: Exception) {
                logger.error(
                    where = "getMerchantsInCategory",
                    what = "Failed to get merchants in category: $categoryName",
                    throwable = e
                )
                emptyList()
            }
        }
    }

    suspend fun changeMerchantCategory(
        merchantName: String,
        newCategoryName: String,
        applyToFuture: Boolean
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val newCategory = categoryDao.getCategoryByName(newCategoryName)
                if (newCategory == null) {
                    logger.warn(
                        where = "changeMerchantCategory",
                        what = "Target category not found: $newCategoryName"
                    )
                    return@withContext false
                }

                val normalizedMerchantName = transactionRepository.normalizeMerchantName(merchantName)
                val merchant = merchantDao.getMerchantByNormalizedName(normalizedMerchantName)
                if (merchant == null) {
                    logger.warn(
                        where = "changeMerchantCategory",
                        what = "Merchant not found: $merchantName"
                    )
                    return@withContext false
                }

                // Update merchant's category
                val updatedMerchant = merchant.copy(categoryId = newCategory.id)
                merchantDao.updateMerchant(updatedMerchant)

                // CRITICAL: Update all transactions for this merchant to maintain data consistency
                val updatedTransactionsCount = transactionDao.updateTransactionsCategoryByMerchant(
                    normalizedMerchant = normalizedMerchantName,
                    newCategoryId = newCategory.id
                )

                logger.debug(
                    where = "changeMerchantCategory",
                    what = "✅ Changed merchant '$merchantName' to category '$newCategoryName' (updated $updatedTransactionsCount transactions)"
                )
                true
            } catch (e: Exception) {
                logger.error(
                    where = "changeMerchantCategory",
                    what = "Failed to change merchant category: $merchantName -> $newCategoryName",
                    throwable = e
                )
                false
            }
        }
    }

    suspend fun renameCategory(oldName: String, newName: String, newEmoji: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val category = categoryDao.getCategoryByName(oldName)
                if (category == null) {
                    logger.warn(
                        where = "renameCategory",
                        what = "Category not found for rename: $oldName"
                    )
                    return@withContext false
                }

                val existingCategory = categoryDao.getCategoryByName(newName)
                if (existingCategory != null && existingCategory.id != category.id) {
                    logger.warn(
                        where = "renameCategory",
                        what = "Category name already exists: $newName"
                    )
                    return@withContext false
                }

                val updatedCategory = category.copy(
                    name = newName,
                    emoji = newEmoji
                )
                categoryDao.updateCategory(updatedCategory)

                logger.debug(
                    where = "renameCategory",
                    what = "Renamed category $oldName to $newName"
                )
                true
            } catch (e: Exception) {
                logger.error(
                    where = "renameCategory",
                    what = "Failed to rename category: $oldName -> $newName",
                    throwable = e
                )
                false
            }
        }
    }

    suspend fun deleteCategory(categoryName: String, reassignToCategoryName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val categoryToDelete = categoryDao.getCategoryByName(categoryName)
                val reassignCategory = categoryDao.getCategoryByName(reassignToCategoryName)

                if (categoryToDelete == null || reassignCategory == null) {
                    logger.warn(
                        where = "deleteCategory",
                        what = "Category not found for deletion or reassignment"
                    )
                    return@withContext false
                }

                if (com.smartexpenseai.app.constants.Categories.isSystemCategory(categoryName)) {
                    logger.warn(
                        where = "deleteCategory",
                        what = "Cannot delete system category: $categoryName"
                    )
                    return@withContext false
                }

                val merchantsInCategory = merchantDao.getMerchantsByCategory(categoryToDelete.id)
                merchantsInCategory.forEach { merchant ->
                    val updatedMerchant = merchant.copy(categoryId = reassignCategory.id)
                    merchantDao.updateMerchant(updatedMerchant)
                }

                categoryDao.deleteCategory(categoryToDelete)

                logger.debug(
                    where = "deleteCategory",
                    what = "Deleted category $categoryName and reassigned ${merchantsInCategory.size} merchants to $reassignToCategoryName"
                )
                true
            } catch (e: Exception) {
                logger.error(
                    where = "deleteCategory",
                    what = "Failed to delete category: $categoryName",
                    throwable = e
                )
                false
            }
        }
    }

    private suspend fun ensureCategoryExists(categoryName: String): CategoryEntity? {
        val category = categoryDao.getCategoryByName(categoryName)
        if (category != null) {
            return category
        }

        // Don't auto-create categories - return null to prevent duplicates
        logger.error(
            where = "ensureCategoryExists",
            what = "Category not found in database: $categoryName"
        )
        return null
    }
}
