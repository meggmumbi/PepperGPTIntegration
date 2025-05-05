package com.example.peppergptintegration

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBody = request.body

        // Log request details
        val requestLog = StringBuilder().apply {
            append("\n=== REQUEST ===\n")
            append("URL: ${request.url}\n")
            append("Method: ${request.method}\n")
            append("Headers: ${request.headers}\n")

            if (requestBody != null) {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                val contentType = requestBody.contentType()
                val content = buffer.readUtf8()

                append("Body: $content\n")
                append("Content-Type: $contentType\n")
            }
        }.toString()

        Log.d("API_Request", requestLog)

        val response = chain.proceed(request)

        // Log response details
        val responseLog = StringBuilder().apply {
            append("\n=== RESPONSE ===\n")
            append("URL: ${response.request.url}\n")
            append("Code: ${response.code}\n")
            append("Headers: ${response.headers}\n")

            val responseBody = response.peekBody((1024 * 1024).toLong()) // Peek up to 1MB
            append("Body: ${responseBody.string()}\n")
        }.toString()

        Log.d("API_Response", responseLog)

        return response
    }
}