package com.example.peppergptintegration

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Client for the condition-aware pronunciation endpoint.
 *
 * The backend decides the condition from the session record and returns the
 * exact utterance the robot should speak, so this app renders K and D
 * identically and cannot let them drift apart in timing, UI or scoring.
 * Nothing here branches on condition; that is the point.
 */
object PronunciationApi {

    private const val TAG = "PronunciationApi"

    private val client = OkHttpClient.Builder()
        // Scoring is ~210 ms for a one-second word, but the first request
        // after a server restart can wait on model load.
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class Feedback(
        val speech: String,
        val display: String,
        val kind: String,
        val remodel: Boolean,
        val word_count: Int,
        val named_phone: String?
    )

    data class Result(
        val attempt_id: String,
        val attempt_number: Int,
        val condition: String,
        val is_correct: Boolean,
        val verdict: String,
        /**
         * The recogniser was not confident enough to judge. The robot asks for
         * a repeat; the attempt is logged but excluded from correction-rate
         * denominators, so it must NOT consume one of the item's retries.
         */
        val gated: Boolean,
        /**
         * Whether another attempt at this item follows. Authoritative -- the
         * backend generates the feedback wording from the same value, so a
         * client-side retry counter would eventually disagree with what the
         * robot just said out loud.
         */
        val should_retry: Boolean,
        val score: Float,
        val verdict_score: Float,
        val confidence: Float,
        val feedback: Feedback,
        val reference_source: String?,
        val reference_needs_review: Boolean?,
        val transcript_matches: Boolean?,
        val expected_phones: List<String>?,
        val observed_phones: List<String>?,
        val timings_ms: Map<String, Double>?,
        val note: String?
    )

    sealed class Outcome {
        data class Success(val result: Result) : Outcome()
        /** Network or server failure. The caller should let the item be retried. */
        data class Failure(val message: String) : Outcome()
    }

    suspend fun scoreAttempt(
        baseUrl: String,
        token: String,
        sessionId: String,
        itemId: String,
        responseTimeSeconds: Double,
        wav: File,
        /**
         * On-device recogniser output for the same utterance, if available.
         *
         * Sent alongside the audio rather than instead of it. SpeechRecognizer
         * is reliable at word identity, and the backend uses that only to stop
         * a correct production being marked wrong -- it can never make a
         * verdict worse. The acoustic pass still supplies the graded score and
         * the phone-level diagnosis.
         */
        transcript: String? = null
    ): Outcome = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("item_id", itemId)
                .addFormDataPart("response_time_seconds", responseTimeSeconds.toString())
                .also { if (!transcript.isNullOrBlank()) it.addFormDataPart("transcript", transcript) }
                .addFormDataPart(
                    "audio", wav.name,
                    wav.asRequestBody("audio/wav".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("${baseUrl}pronunciation/sessions/$sessionId/attempts")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val payload = response.body?.string()
                if (!response.isSuccessful || payload.isNullOrBlank()) {
                    Log.e(TAG, "scoring failed: ${response.code} $payload")
                    return@withContext Outcome.Failure(
                        "server returned ${response.code}"
                    )
                }
                Outcome.Success(gson.fromJson(payload, Result::class.java))
            }
        } catch (e: Exception) {
            Log.e(TAG, "scoring request failed", e)
            Outcome.Failure(e.message ?: "network error")
        }
    }
}
