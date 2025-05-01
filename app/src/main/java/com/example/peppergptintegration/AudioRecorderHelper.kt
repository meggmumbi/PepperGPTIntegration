package com.example.peppergptintegration

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class AudioRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    companion object {
        private const val TAG = "AudioRecorder"
        private const val MAX_DURATION_MS = 10000 // 10 seconds max recording
        private const val SAMPLE_RATE = 16000 // Optimal for speech
    }

    fun startRecording(): Result<File> {
        return try {
            // 1. Set up directory
            val recordingsDir = File(context.cacheDir, "recordings").apply {
                if (!exists()) {
                    if (!mkdirs()) {
                        throw IOException("Failed to create recordings directory")
                    }
                }
            }

            // 2. Create output file
            outputFile = File(recordingsDir, "audio_${System.currentTimeMillis()}.mp4").apply {
                if (!createNewFile()) {
                    throw IOException("Failed to create audio file")
                }
            }

            // 3. Test file writing
            FileOutputStream(outputFile).use { it.write(0) }

            // 4. Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile?.absolutePath)
                setAudioSamplingRate(SAMPLE_RATE)
                setMaxDuration(MAX_DURATION_MS)
                prepare()
                start()
            }

            Log.d(TAG, "Recording started to ${outputFile?.absolutePath}")
            Result.success(outputFile!!)
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
            cleanup()
            Result.failure(e)
        }
    }

    fun stopRecording(): Result<Unit> {
        return try {
            mediaRecorder?.stop()
            Log.d(TAG, "Recording stopped successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            Result.failure(e)
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    fun getRecordedFile(): File? = outputFile
}