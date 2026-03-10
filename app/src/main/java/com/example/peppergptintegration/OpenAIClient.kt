package com.example.peppergptintegration

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OpenAIClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val apiKey = BuildConfig.OPENAI_API_KEY

    suspend fun getAIResponse(prompt: String): String {
        val requestBody = gson.toJson(OpenAIRequest(
            model = "gpt-3.5-turbo",
            messages = listOf(
                Message(
                    role = "system",
                    content = "You are Pepper, a humanoid robot assisting therapists working with children with Autism Spectrum Disorder (ASD). " +
                            "Provide helpful, professional responses about ASD topics. Keep responses clear and concise."
                ),
                Message(
                    role = "user",
                    content = prompt
                )
            ),
            temperature = 0.7
        ))

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Unexpected code $response")
        }

        val responseBody = response.body?.string() ?: throw IOException("Empty response")
        val openAIResponse = gson.fromJson(responseBody, OpenAIResponse::class.java)

        return openAIResponse.choices.firstOrNull()?.message?.content
            ?: "I'm not sure how to respond to that."
    }
}

data class OpenAIRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double
)

data class Message(
    val role: String,
    val content: String
)

data class OpenAIResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)