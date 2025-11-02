package com.smartexpenseai.app.data.dao

import androidx.room.*
import com.smartexpenseai.app.data.entities.SyncStateEntity
import java.util.Date

@Dao
interface SyncStateDao {
    
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun getSyncState(): SyncStateEntity?
    
    @Query("SELECT last_sms_sync_timestamp FROM sync_state WHERE id = 1")
    suspend fun getLastSyncTimestamp(): Date?
    
    @Query("SELECT sync_status FROM sync_state WHERE id = 1")
    suspend fun getSyncStatus(): String?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSyncState(syncState: SyncStateEntity)
    
    @Query("""
        UPDATE sync_state 
        SET last_sms_sync_timestamp = :timestamp, 
            last_sms_id = :smsId,
            total_transactions = :totalTransactions,
            sync_status = :status
        WHERE id = 1
    """)
    suspend fun updateSyncState(
        timestamp: Date,
        smsId: String?,
        totalTransactions: Int,
        status: String
    )
    
    @Query("UPDATE sync_state SET sync_status = :status WHERE id = 1")
    suspend fun updateSyncStatus(status: String)
    
    @Query("UPDATE sync_state SET last_full_sync = :timestamp WHERE id = 1")
    suspend fun updateLastFullSync(timestamp: Date)
    
    @Query("UPDATE sync_state SET total_transactions = :count WHERE id = 1")
    suspend fun updateTransactionCount(count: Int)
    
    @Query("DELETE FROM sync_state")
    suspend fun deleteSyncState()
}