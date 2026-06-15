package com.quantumqr.data


import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ScanRepository private constructor(ctx: Context) {
    private val dao = AppDatabase.get(ctx).scans()

    fun history(): Flow<List<Scan>> = dao.observeAll()

    suspend fun add(content: String, format: String, isUrl: Boolean, ts: Long) = withContext(Dispatchers.IO) {
        dao.insert(Scan(content = content, format = format, isUrl = isUrl, timestamp = ts))
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.clearAll() }

    companion object {
        @Volatile private var INSTANCE: ScanRepository? = null
        fun get(ctx: Context): ScanRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScanRepository(ctx.applicationContext).also { INSTANCE = it }
            }
    }
}