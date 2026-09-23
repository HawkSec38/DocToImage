package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_history")
data class DocumentHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val format: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val renderedAt: Long = System.currentTimeMillis(),
    val cachedThumbPath: String? = null,
    val sourceUri: String? = null,
    val sourceType: String = "SAMPLE" // "SAMPLE", "USER_FILE", "COMPOSED"
)
