package com.expensemanager.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["category_id"])]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    
    @ColumnInfo(name = "budget_amount")
    val budgetAmount: Double,
    
    @ColumnInfo(name = "period_type")
    val periodType: String = "MONTHLY", // WEEKLY, MONTHLY, YEARLY
    
    @ColumnInfo(name = "start_date")
    val startDate: Date,
    
    @ColumnInfo(name = "end_date")
    val endDate: Date,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date
)