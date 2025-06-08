package com.example.peppergptintegration

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

// PerformanceDetailsFragment data
data class PerformanceDetails(
    val category: String,
    val overallScore: Double,
    val verbalAccuracy: Double,
    val selectionAccuracy: Double,
    val lastUpdated: String
)

// SessionHistoryFragment data
data class SessionHistoryItem(
    val id: String,
    val date: String,
    val category: String,
    val durationMinutes: Double?,
    val score: Double?,
    val feedback: SessionFeedback?
)

@Parcelize
data class SessionFeedback(
    val rating: Int,
    val comments: String?,
    val progressAchievements: String?,
    val areasForImprovement: String?,
    val behavioralObservations: String?,
    val feedbackType: String?,
    val createdAt: String?
) : Parcelable

// ProgressTrendsFragment data
data class ProgressTrends(
    val weeklyTrend: Trend? = null,
    val monthlyTrend: Trend? = null,
    val improvementAreas: List<ImprovementArea> = emptyList()
) {
    data class Trend(
        val trend: String? = null,
        val rate: Double? = null,
        val currentScore: Double? = null,
        val startingScore: Double? = null
    )

    data class ImprovementArea(
        val category: String,
        val improvementRate: Double? = null // Made nullable
    )
}

data class ChildProfile(
    val id: String,
    val name: String,
    val age: Int,
    val diagnosisDate: String,
    val notes: String,
    val createdAt: String
)

data class ProgressData(
    val averageScore: Double,
    val strongestCategory: String,
    val weakestCategory: String,
    val practiceMore: List<String>,
    val nextActivities: List<NextActivity>,
    val mlRecommendation: String? = null,
    val confidenceScore: Double? = null,
    val encouragement: String? = null
)

data class NextActivity(
    val category: String,
    val items: List<String>
)

data class TrendData(
    val weeklyTrend: Trend,
    val monthlyTrend: Trend,
    val improvementAreas: List<ImprovementArea>
) {
    data class Trend(
        val trend: String,
        val rate: Double,
        val currentScore: Double,
        val startingScore: Double
    )

    data class ImprovementArea(
        val category: String,
        val improvementRate: Double
    )
}
