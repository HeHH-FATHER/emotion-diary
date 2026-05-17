package com.example.sensordiary.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_records")
data class MoodRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val emoji: String,
    val title: String,
    val description: String,
    val timestamp: Long,
    val energyScore: Float = 0.5f,
    val activityState: String = "未知",
    val confidenceScore: Float = 1.0f,
    val lightValue: Int = 0,
    val dbValue: Int = 0,
    val shakeValue: Float = 0f,
    val voicePitch: Float = 0f,
    val voiceTone: String = "未知",
    val voiceContent: String = ""
)

data class MoodOption(
    val emoji: String,
    val title: String,
    val description: String,
    val detail: String = ""
)
