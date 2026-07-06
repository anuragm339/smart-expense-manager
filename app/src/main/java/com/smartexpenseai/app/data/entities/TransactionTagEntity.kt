package com.smartexpenseai.app.data.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction row linking a transaction to a tag (many-to-many).
 *
 * The composite primary key (transaction_id, tag_id) makes each pairing unique
 * and covers lookups by transaction_id. A separate index covers lookups by
 * tag_id (filter-by-tag). Both foreign keys cascade on delete so removing a
 * transaction or a tag automatically cleans up its associations.
 */
@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transaction_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tag_id"])]
)
data class TransactionTagEntity(
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "tag_id")
    val tagId: Long
)
