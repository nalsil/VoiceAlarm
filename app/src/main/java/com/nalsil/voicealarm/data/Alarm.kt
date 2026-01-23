package com.nalsil.voicealarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String, // Comma-separated indices "0,1,2" (0=Sun, 1=Mon, etc.)
    val isEnabled: Boolean = true,
    val languageCode: String = "ko", // Default to Korean
    val volume: Float = 1.0f,
    val vibrate: Boolean = true,
    val label: String = ""
)
