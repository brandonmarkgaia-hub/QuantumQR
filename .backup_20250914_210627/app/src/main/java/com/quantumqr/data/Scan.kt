package com.quantumqr.data


import com.quantumqr.R

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class Scan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val format: String,
    val isUrl: Boolean,
    val timestamp: Long
)