package com.example.peppergptintegration

import android.content.Context
import android.speech.SpeechRecognizer

fun Context.isSpeechRecognitionAvailable(): Boolean {
    return SpeechRecognizer.isRecognitionAvailable(this)
}

fun Context.createSpeechRecognizer(): SpeechRecognizer {
    return SpeechRecognizer.createSpeechRecognizer(this)
}