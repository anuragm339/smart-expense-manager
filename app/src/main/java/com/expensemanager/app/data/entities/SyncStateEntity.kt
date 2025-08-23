package com.expensemanager.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.Date

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    val id: Int = 1, // Single row table
    
    @ColumnInfo(name = "last_sms_sync_timestamp")
    val lastSmsSyncTimestamp: Date,
    
    @ColumnInfo(name = "last_sms_id")
    val lastSmsId: String?,
    
    @ColumnInfo(name = "total_transactions")
    val totalTransactions: Int = 0,
    
    @ColumnInfo(name = "last_full_sync")
    val lastFullSync: Date,
    
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "COMPLETED" // COMPLETED, IN_PROGRESS, FAILED
)