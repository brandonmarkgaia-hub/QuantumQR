package com.quantumqr.main.java.com.quantumqr.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val format: String,
    val timestamp: String
)

@Dao
interface ScansDao {
    @Insert
    suspend fun insert(entity: ScanEntity)

    @Query("SELECT * FROM scans ORDER BY id DESC")
    fun observeAll(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY id DESC")
    suspend fun getAllOnce(): List<ScanEntity>

    @Query("DELETE FROM scans")
    suspend fun clearAll()
}

@Database(entities = [ScanEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase: RoomDatabase() {
    abstract fun scansDao(): ScansDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "quantumqr.db").build().also { INSTANCE = it }
            }
    }
}

