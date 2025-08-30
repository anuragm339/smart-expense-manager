package com.expensemanager.app.domain.usecase.merchant

import android.util.Log
import com.expensemanager.app.data.entities.MerchantEntity
import com.expensemanager.app.data.dao.MerchantWithCategory
import com.expensemanager.app.domain.repository.MerchantRepositoryInterface
import javax.inject.Inject

/**
 * Use case for getting merchants with business logic
 * Handles merchant data retrieval, filtering, and transformations
 */
class GetMerchantsUseCase @Inject constructor(
    private val repository: MerchantRepositoryInterface
) {
    
    companion object {
        private const val TAG = "GetMerchantsUseCase"
    }
    
    /**
     * Get all merchants
     */
    suspend fun execute(): Result<List<MerchantEntity>> {
        return try {
            Log.d(TAG, "Getting all merchants")
            val merchants = repository.getAllMerchants()
            Log.d(TAG, "Retrieved ${merchants.size} merchants")
            Result.success(merchants)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all merchants", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get merchants with filtering applied
     */
    suspend fun execute(params: GetMerchantsParams): Result<List<MerchantEntity>> {
        return try {
            Log.d(TAG, "Getting merchants with params: $params")
            
            val allMerchants = repository.getAllMerchants()
            val filteredMerchants = processMerchants(allMerchants, params)
            
            Log.d(TAG, "Filtered to ${filteredMerchants.size} merchants")
            Result.success(filteredMerchants)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting filtered merchants", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get merchant by normalized name
     */
    suspend fun getMerchantByNormalizedName(normalizedName: String): Result<MerchantEntity?> {
        return try {
            Log.d(TAG, "Getting merchant by normalized name: $normalizedName")
            val merchant = repository.getMerchantByNormalizedName(normalizedName)
            if (merchant != null) {
                Log.d(TAG, "Found merchant: ${merchant.displayName}")
            } else {
                Log.d(TAG, "Merchant not found")
            }
            Result.success(merchant)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting merchant by normalized name", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get merchant with category information
     */
    suspend fun getMerchantWithCategory(normalizedName: String): Result<MerchantWithCategory?> {
        return try {
            Log.d(TAG, "Getting merchant with category: $normalizedName")
            val merchantWithCategory = repository.getMerchantWithCategory(normalizedName)
            if (merchantWithCategory != null) {
                Log.d(TAG, "Found merchant: ${merchantWithCategory.display_name} in category: ${merchantWithCategory.category_name}")
            } else {
                Log.d(TAG, "Merchant with category not found")
            }
            Result.success(merchantWithCategory)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting merchant with category", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get excluded merchants
     */
    suspend fun getExcludedMerchants(): Result<List<MerchantEntity>> {
        return try {
            Log.d(TAG, "Getting excluded merchants")
            val excludedMerchants = repository.getExcludedMerchants()
            Log.d(TAG, "Retrieved ${excludedMerchants.size} excluded merchants")
            Result.success(excludedMerchants)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting excluded merchants", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get active (non-excluded) merchants
     */
    suspend fun getActiveMerchants(): Result<List<MerchantEntity>> {
        return try {
            Log.d(TAG, "Getting active merchants")
            val allMerchants = repository.getAllMerchants()
            val activeMerchants = allMerchants.filter { !it.isExcludedFromExpenseTracking }
            Log.d(TAG, "Retrieved ${activeMerchants.size} active merchants")
            Result.success(activeMerchants)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active merchants", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get user-defined merchants only
     */
    suspend fun getUserDefinedMerchants(): Result<List<MerchantEntity>> {
        return try {
            Log.d(TAG, "Getting user-defined merchants")
            val allMerchants = repository.getAllMerchants()
            val userMerchants = allMerchants.filter { it.isUserDefined }
            Log.d(TAG, "Retrieved ${userMerchants.size} user-defined merchants")
            Result.success(userMerchants)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user-defined merchants", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get auto-generated merchants only
     */
    suspend fun getAutoGeneratedMerchants(): Result<List<MerchantEntity>> {
        return try {
            Log.d(TAG, "Getting auto-generated merchants")
            val allMerchants = repository.getAllMerchants()
            val autoMerchants = allMerchants.filter { !it.isUserDefined }
            Log.d(TAG, "Retrieved ${autoMerchants.size} auto-generated merchants")
            Result.success(autoMerchants)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting auto-generated merchants", e)
            Result.failure(e)
        }
    }
    
    /**
     * Search merchants by name
     */
    suspend fun searchMerchants(query: String): Result<List<MerchantEntity>> {
        return try {
            Log.d(TAG, "Searching merchants with query: $query")
            
            if (query.isBlank()) {
                return Result.success(emptyList())
            }
            
            val allMerchants = repository.getAllMerchants()
            val matchingMerchants = allMerchants.filter { merchant ->
                merchant.displayName.contains(query, ignoreCase = true) ||
                merchant.normalizedName.contains(query, ignoreCase = true)
            }.sortedBy { it.displayName }
            
            Log.d(TAG, "Found ${matchingMerchants.size} merchants matching query")
            Result.success(matchingMerchants)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching merchants", e)
            Result.failure(e)
        }
    }
    
    /**
     * Process merchants with business logic
     */
    private fun processMerchants(merchants: List<MerchantEntity>, params: GetMerchantsParams): List<MerchantEntity> {
        Log.d(TAG, "Processing ${merchants.size} merchants with params: $params")
        
        return merchants
            .let { list ->
                // Filter by exclusion status if specified
                when (params.excludedOnly) {
                    true -> list.filter { it.isExcludedFromExpenseTracking }
                    false -> list.filter { !it.isExcludedFromExpenseTracking }
                    null -> list
                }
            }
            .let { list ->
                // Filter by user-defined status if specified
                when (params.userDefinedOnly) {
                    true -> list.filter { it.isUserDefined }
                    false -> list.filter { !it.isUserDefined }
                    null -> list
                }
            }
            .let { list ->
                // Filter by category if specified
                if (params.categoryId > 0) {
                    list.filter { it.categoryId == params.categoryId }
                } else {
                    list
                }
            }
            .let { list ->
                // Filter by name if specified
                if (params.nameFilter.isNotEmpty()) {
                    list.filter { merchant ->
                        merchant.displayName.contains(params.nameFilter, ignoreCase = true) ||
                        merchant.normalizedName.contains(params.nameFilter, ignoreCase = true)
                    }
                } else {
                    list
                }
            }
            .let { list ->
                // Sort merchants
                when (params.sortOrder) {
                    MerchantSortOrder.NAME_ASC -> list.sortedBy { it.displayName }
                    MerchantSortOrder.NAME_DESC -> list.sortedByDescending { it.displayName }
                    MerchantSortOrder.CREATED_ASC -> list.sortedBy { it.createdAt }
                    MerchantSortOrder.CREATED_DESC -> list.sortedByDescending { it.createdAt }
                    MerchantSortOrder.CATEGORY_NAME -> list.sortedBy { it.categoryId } // Would need category names for proper sorting
                }
            }
            .let { list ->
                // Apply limit if specified
                if (params.limit > 0) {
                    list.take(params.limit)
                } else {
                    list
                }
            }
            .also { processedList ->
                Log.d(TAG, "Processed merchants: ${processedList.size} after filtering and sorting")
                
                // Log merchant summary for debugging
                if (processedList.isNotEmpty()) {
                    val excludedCount = processedList.count { it.isExcludedFromExpenseTracking }
                    val userDefinedCount = processedList.count { it.isUserDefined }
                    Log.d(TAG, "Merchant summary: $excludedCount excluded, $userDefinedCount user-defined")
                }
            }
    }
}

/**
 * Parameters for getting merchants
 */
data class GetMerchantsParams(
    val excludedOnly: Boolean? = null, // null = all, true = excluded only, false = active only
    val userDefinedOnly: Boolean? = null, // null = all, true = user-defined only, false = auto-generated only
    val categoryId: Long = 0, // 0 = all categories
    val nameFilter: String = "",
    val sortOrder: MerchantSortOrder = MerchantSortOrder.NAME_ASC,
    val limit: Int = 0 // 0 means no limit
)

/**
 * Sort order options for merchants
 */
enum class MerchantSortOrder {
    NAME_ASC, NAME_DESC, CREATED_ASC, CREATED_DESC, CATEGORY_NAME
}