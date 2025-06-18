package com.example.peppergptintegration



data class Category(
    val id: String,
    val name: String,
    val difficultyLevel: String,
    val description: String,
    val itemCount: Int?,
    val totalAttempts: Int?,
    val latestPerformance: Double?, // percentage
    val lastAttemptDate: String? // nullable in case no attempts yet
)