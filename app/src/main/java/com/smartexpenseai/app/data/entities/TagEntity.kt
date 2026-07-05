package com.smartexpenseai.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import java.util.Date

/**
 * A user-defined tag that can be attached to any number of transactions.
 *
 * Unlike categories (single-valued and denormalized onto the transaction via
 * category_id), tags are many-to-many: the association lives in
 * [TransactionTagEntity]. Tags are always user-created; there are no system tags.
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: String = "#607D8B",

    @ColumnInfo(name = "created_at")
    val createdAt: Date
)
