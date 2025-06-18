package com.example.peppergptintegration

data class CategoryItem(
    val id: String,
    val name: String,
    val categoryId: String,
    val difficultyLevel: String,
    val description: String,
    val imageBase64: String?
)