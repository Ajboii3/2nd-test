package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "donations")
data class Donation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderName: String,
    val amount: String,
    val appName: String,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val webhookStatus: String, // "SUCCESS", "FAILED", "DEDUPLICATED"
    val errorMessage: String? = null
)
