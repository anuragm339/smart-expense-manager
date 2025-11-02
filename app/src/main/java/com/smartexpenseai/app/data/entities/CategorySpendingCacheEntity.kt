package com.smartexpenseai.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date

@Entity(
    tableName = "category_spending_cache",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["category_id", "period_start", "period_end"], unique = true)
    ]
)
data class CategorySpendingCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    
    @ColumnInfo(name = "period_start")
    val periodStart: Date,
    
    @ColumnInfo(name = "period_end")
    val periodEnd: Date,
    
    @ColumnInfo(name = "total_amount")
    val totalAmount: Double,
    
    @ColumnInfo(name = "transaction_count")
    val transactionCount: Int,
    
    @ColumnInfo(name = "last_transaction_date")
    val lastTransactionDate: Date?,
    
    @ColumnInfo(name = "cache_timestamp")
    val cacheTimestamp: Date
)