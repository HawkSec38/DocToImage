package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentHistoryDao {
    @Query("SELECT * FROM document_history ORDER BY renderedAt DESC")
    fun getAllHistory(): Flow<List<DocumentHistoryEntity>>

    @Query("SELECT * FROM document_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DocumentHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DocumentHistoryEntity): Long

    @Query("DELETE FROM document_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM document_history")
    suspend fun clearAll()
}
