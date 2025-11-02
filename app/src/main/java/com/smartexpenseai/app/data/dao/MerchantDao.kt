package com.smartexpenseai.app.data.dao

import androidx.room.*
import com.smartexpenseai.app.data.entities.MerchantEntity
import com.smartexpenseai.app.data.entities.MerchantAliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {
    
    @Query("SELECT * FROM merchants ORDER BY display_name ASC")
    fun getAllMerchants(): Flow<List<MerchantEntity>>
    
    @Query("SELECT * FROM merchants ORDER BY display_name ASC")
    suspend fun getAllMerchantsSync(): List<MerchantEntity>
    
    @Query("SELECT * FROM merchants WHERE id = :merchantId")
    suspend fun getMerchantById(merchantId: Long): MerchantEntity?
    
    @Query("SELECT * FROM merchants WHERE normalized_name = :normalizedName")
    suspend fun getMerchantByNormalizedName(normalizedName: String): MerchantEntity?
    
    @Query("SELECT * FROM merchants WHERE category_id = :categoryId ORDER BY display_name ASC")
    suspend fun getMerchantsByCategory(categoryId: Long): List<MerchantEntity>
    
    @Query("""
        SELECT m.*, c.name as category_name, c.color as category_color
        FROM merchants m 
        JOIN categories c ON m.category_id = c.id 
        WHERE m.normalized_name = :normalizedName
    """)
    suspend fun getMerchantWithCategory(normalizedName: String): MerchantWithCategory?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMerchant(merchant: MerchantEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMerchants(merchants: List<MerchantEntity>): List<Long>
    
    @Update
    suspend fun updateMerchant(merchant: MerchantEntity)
    
    @Delete
    suspend fun deleteMerchant(merchant: MerchantEntity)
    
    @Query("DELETE FROM merchants WHERE id = :merchantId")
    suspend fun deleteMerchantById(merchantId: Long)
    
    @Query("DELETE FROM merchants")
    suspend fun deleteAllMerchants()
    
    // Merchant Aliases
    @Query("SELECT * FROM merchant_aliases WHERE merchant_id = :merchantId")
    suspend fun getAliasesForMerchant(merchantId: Long): List<MerchantAliasEntity>
    
    @Query("""
        SELECT ma.*, m.display_name, m.category_id 
        FROM merchant_aliases ma
        JOIN merchants m ON ma.merchant_id = m.id
        WHERE ma.alias_pattern LIKE '%' || :pattern || '%'
        ORDER BY ma.confidence DESC
    """)
    suspend fun findMerchantByAliasPattern(pattern: String): List<MerchantAliasWithMerchant>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlias(alias: MerchantAliasEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAliases(aliases: List<MerchantAliasEntity>): List<Long>
    
    @Delete
    suspend fun deleteAlias(alias: MerchantAliasEntity)
    
    @Query("DELETE FROM merchant_aliases WHERE id = :aliasId")
    suspend fun deleteAliasById(aliasId: Long)
    
    // Exclusion methods
    @Query("SELECT * FROM merchants WHERE is_excluded_from_expense_tracking = 1")
    suspend fun getExcludedMerchants(): List<MerchantEntity>
    
    @Query("UPDATE merchants SET is_excluded_from_expense_tracking = :isExcluded WHERE normalized_name = :normalizedName")
    suspend fun updateMerchantExclusion(normalizedName: String, isExcluded: Boolean)
    
    @Query("UPDATE merchants SET is_excluded_from_expense_tracking = :isExcluded WHERE id = :merchantId")
    suspend fun updateMerchantExclusionById(merchantId: Long, isExcluded: Boolean)

    // Methods for updating merchant category and display name
    @Query("UPDATE merchants SET category_id = :categoryId WHERE normalized_name = :normalizedName")
    suspend fun updateMerchantCategory(normalizedName: String, categoryId: Long)

    @Query("UPDATE merchants SET display_name = :displayName WHERE normalized_name = :normalizedName")
    suspend fun updateMerchantDisplayName(normalizedName: String, displayName: String)

    @Query("UPDATE merchants SET display_name = :displayName, category_id = :categoryId WHERE normalized_name = :normalizedName")
    suspend fun updateMerchantDisplayNameAndCategory(normalizedName: String, displayName: String, categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM merchants WHERE normalized_name = :normalizedName")
    suspend fun merchantExists(normalizedName: String): Int
    
    @Query("UPDATE merchants SET category_id = :newCategoryId WHERE category_id = :oldCategoryId")
    suspend fun updateMerchantsByCategory(oldCategoryId: Long, newCategoryId: Long): Int
}

// Data classes for query results
data class MerchantWithCategory(
    val id: Long,
    val normalized_name: String,
    val display_name: String,
    val category_id: Long,
    val is_user_defined: Boolean,
    val is_excluded_from_expense_tracking: Boolean,
    val created_at: java.util.Date,
    val category_name: String,
    val category_color: String
)

data class MerchantAliasWithMerchant(
    val id: Long,
    val merchant_id: Long,
    val alias_pattern: String,
    val confidence: Int,
    val display_name: String,
    val category_id: Long
)