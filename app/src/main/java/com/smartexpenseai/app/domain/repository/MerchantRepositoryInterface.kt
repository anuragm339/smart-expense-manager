package com.smartexpenseai.app.domain.repository

import com.smartexpenseai.app.data.entities.MerchantEntity
import com.smartexpenseai.app.data.dao.MerchantWithCategory

/**
 * Repository interface for merchant data operations in domain layer
 * This interface defines the contract that data repositories must implement
 * Following clean architecture principles by separating domain contracts from data implementations
 */
interface MerchantRepositoryInterface {
    
    // =======================
    // READ OPERATIONS
    // =======================
    
    /**
     * Get all merchants
     */
    suspend fun getAllMerchants(): List<MerchantEntity>
    
    /**
     * Get merchant by normalized name
     */
    suspend fun getMerchantByNormalizedName(normalizedName: String): MerchantEntity?
    
    /**
     * Get merchant with category information
     */
    suspend fun getMerchantWithCategory(normalizedName: String): MerchantWithCategory?
    
    /**
     * Get excluded merchants
     */
    suspend fun getExcludedMerchants(): List<MerchantEntity>
    
    // =======================
    // WRITE OPERATIONS
    // =======================
    
    /**
     * Insert a new merchant
     */
    suspend fun insertMerchant(merchant: MerchantEntity): Long
    
    /**
     * Update existing merchant
     */
    suspend fun updateMerchant(merchant: MerchantEntity)
    
    /**
     * Update merchant exclusion status
     */
    suspend fun updateMerchantExclusion(normalizedMerchantName: String, isExcluded: Boolean)
    
    /**
     * Find or create merchant with category
     */
    suspend fun findOrCreateMerchant(
        normalizedName: String,
        displayName: String,
        categoryId: Long
    ): MerchantEntity
}