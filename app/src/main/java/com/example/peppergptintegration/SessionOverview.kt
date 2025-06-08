package com.example.peppergptintegration

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class SessionOverview(
    val sessionId: String,
    val childName: String,
    val categoryName: String,
    val startTime: String,
    val durationMinutes: Double,
    val totalActivities: Int,
    val correctAnswers: Int,
    val accuracyPercentage: Double,
    val averageResponseTime: Int,
    val activities: List<SessionActivity>,
    val strengths: List<String>,
    val areasForImprovement: List<String>,
    val recommendations: List<String>
) : Parcelable

@Parcelize
data class SessionActivity(
    val itemName: String,
    val responseType: String,
    val isCorrect: Boolean,
    val pronunciationScore: Int,
    val responseTime: Int,
    val feedback: String?
) : Parcelable

data class FeedbackRequest(
    val child_id: String,
    val rating: Int,
    val comments: String,
    val progress_achievements: String,
    val areas_for_improvement: String,
    val behavioral_observations: String
)