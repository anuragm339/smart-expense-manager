package com.expensemanager.app.data.dao

import androidx.room.*
import com.expensemanager.app.data.entities.MerchantEntity
import com.expensemanager.app.data.entities.MerchantAliasEntity
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