package com.expensemanager.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "merchant_aliases",
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchant_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["merchant_id"]),
        Index(value = ["alias_pattern"]),
        Index(value = ["merchant_id", "alias_pattern"], unique = true)
    ]
)
data class MerchantAliasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "merchant_id")
    val merchantId: Long,
    
    @ColumnInfo(name = "alias_pattern")
    val aliasPattern: String,
    
    @ColumnInfo(name = "confidence")
    val confidence: Int = 100
)