package com.example.peppergptintegration

import java.util.Date
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