package com.example.peppergptintegration

import java.time.LocalDateTime
import java.util.*

data class Child(
    val id: String,
    val name: String,
    val age: Int,
    val diagnosisDate: String,
    val notes: String,
    val therapyGoals: String,
    val areasOfInterest: List<CategoryAreas>,
    val createdAt: String
)

data class CategoryAreas(
    val id: String,
    val name: String,
    val difficultyLevel: String = ""
)


// ChildPerformance.kt
data class ChildPerformance(
    val id: String,
    val child_id: String,
    val category_id: String?,
    val overall_score: Float,
    val verbal_attempts: Int,
    val verbal_success: Int,
    val selection_attempts: Int,
    val selection_success: Int,
    val last_updated: String
)

// TherapySession.kt
data class TherapySession(
    val id: String,
    val child_id: String,
    val caregiver_id: String,
    val category_id: String?,
    val start_time: String,
    val end_time: String?,
    val current_level: String?,
    val is_completed: Boolean,
    val category: ActivityCategory? = null
)

// ActivityCategory.kt (needed for TherapySession)
data class ActivityCategory(
    val id: String,
    val name: String,
    val description: String?,
    val difficulty_level: String?
)