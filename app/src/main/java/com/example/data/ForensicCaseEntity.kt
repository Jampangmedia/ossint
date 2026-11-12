package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forensic_cases")
data class ForensicCaseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val platform: String,
    val profileUrl: String,
    val postUrl: String,
    val correlationScore: Int,
    val confidenceLabel: String,
    val decodedTimestampWib: String,
    val decodedUnixSeconds: Long,
    val geoIntSummary: String,
    val reportJson: String,
    val createdAtMs: Long
)
