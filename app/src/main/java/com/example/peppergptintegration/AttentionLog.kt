package com.example.peppergptintegration

data class AttentionLog(
    val sessionId: String?,
    val timestamp: Long,
    val isFocused: Boolean,
    val gazeState: String,
    val confidence: Float,
    val faceDistance: Float? = null,
    val attentionRatio: Float,
    val totalSessionTime: Long,
    val focusedTime: Long
)