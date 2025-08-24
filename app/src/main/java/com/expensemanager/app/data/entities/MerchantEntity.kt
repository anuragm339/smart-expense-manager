package com.expensemanager.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date

@Entity(
    tableName = "merchants",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["category_id"]), Index(value = ["normalized_name"], unique = true)]
)
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    
    @ColumnInfo(name = "is_user_defined")
    val isUserDefined: Boolean = false,
    
    @ColumnInfo(name = "is_excluded_from_expense_tracking")
    val isExcludedFromExpenseTracking: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date
)