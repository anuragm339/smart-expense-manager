package com.smartexpenseai.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.Date

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "emoji")
    val emoji: String = "📂",
    
    @ColumnInfo(name = "color")
    val color: String,
    
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,
    
    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date
)