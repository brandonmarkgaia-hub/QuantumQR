package com.quantumqr.main.java.com.quantumqr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert suspend fun insert(note: Note): Long
    @Query("SELECT * FROM Note ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Note>>
}
