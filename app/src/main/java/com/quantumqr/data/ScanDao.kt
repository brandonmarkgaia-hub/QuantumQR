package com.quantumqr.data


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Insert
    suspend fun insert(scan: Scan): Long

    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Scan>>

    @Query("DELETE FROM scans")
    suspend fun clearAll()
}