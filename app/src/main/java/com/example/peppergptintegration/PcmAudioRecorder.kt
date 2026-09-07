package com.example.peppergptintegration

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt

/**
 * Records 16 kHz mono PCM and endpoints it locally, producing a WAV file.
 *
 * Replaces [AudioRecorderHelper] for the pronunciation task. Three reasons the
 * old capture path cannot serve:
 *
 *  - `SpeechRecognizer` never hands back the waveform, so the backend had only
 *    a transcript to score. Pronunciation cannot be assessed from text.
 *  - `MediaRecorder` with AAC in an MP4 container is lossy and needs a decoder
 *    server-side; the study laptop has no ffmpeg. Linear PCM in a WAV is what
 *    the acoustic model wants anyway.
 *  - Endpointing on the tablet means the upload starts the moment the speaker
 *    stops, rather than after a fixed timeout.
 *
 * Voice activity detection is plain frame energy against a noise floor
 * measured at the start of each recording. That is deliberate: the Pepper
 * tablet is `minSdk 23` on weak hardware, and a neural VAD would cost more
 * than the whole server-side scoring pass.
 */
class PcmAudioRecorder(
    private val sampleRate: Int = 16_000,
    /** Speech must exceed the noise floor by this factor to open the gate. */
    private val onsetFactor: Float = 3.0f,
    /** Silence after speech before the utterance is considered finished. */
    private val silenceHangoverMs: Int = 600,
    /** Give up waiting for speech that never starts. */
    private val maxWaitForSpeechMs: Int = 8_000,
    /** Hard cap; single words never approach this. */
    private val maxUtteranceMs: Int = 5_000,
    /** Reject blips: a real word is longer than this. */
    private val minSpeechMs: Int = 180
) {
    interface Listener {
        /** Speech detected; useful for switching the UI to "listening". */
        fun onSpeechStarted()

        /** Endpointed successfully. [wav] is 16 kHz mono PCM16. */
        fun onUtterance(wav: File, speechDurationMs: Int)

        /** No speech, too short, or a recorder fault. */
        fun onNoSpeech(reason: String)
    }

    companion object {
        private const val TAG = "PcmAudioRecorder"
        private const val FRAME_MS = 20
        private const val WAV_HEADER_BYTES = 44

        /** Frames above the floor needed to declare speech; debounces clicks. */
        private const val ONSET_FRAMES = 3

        /** Floor is measured from these leading frames, before speech starts. */
        private const val NOISE_FRAMES = 15

        /**
         * Absolute floor guard. In a silent room the measured noise floor can
         * approach zero, and any multiple of ~0 opens the gate on nothing.
         */
        private const val MIN_NOISE_FLOOR = 120.0
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var recording = false
    private var thread: Thread? = null

    val isRecording: Boolean get() = recording

    fun start(outputFile: File, listener: Listener) {
        if (recording) {
            Log.w(TAG, "start() while already recording; ignoring")
            return
        }
        recording = true
        thread = Thread { captureLoop(outputFile, listener) }.also { it.start() }
    }

    /** Stop early, e.g. the participant tapped the button. */
    fun stop() {
        recording = false
    }

    private fun captureLoop(outputFile: File, listener: Listener) {
        val frameSamples = sampleRate * FRAME_MS / 1000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            finishNoSpeech(listener, "AudioRecord unavailable on this device")
            return
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, frameSamples * 2 * 4)
            )
        } catch (e: Exception) {
            finishNoSpeech(listener, "could not open microphone: ${e.message}")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            finishNoSpeech(listener, "microphone busy")
            return
        }

        val buffer = ShortArray(frameSamples)
        val speech = ByteArrayOutputStream()
        // Frames captured before onset, kept so the start of the first phone
        // survives. Without this the word begins mid-consonant and the
        // pipeline sees a deletion that the speaker did not make.
        val preroll = ArrayDeque<ByteArray>()
        val prerollFrames = 250 / FRAME_MS

        var noiseSum = 0.0
        var noiseCount = 0
        var noiseFloor = MIN_NOISE_FLOOR
        var consecutiveLoud = 0
        var speechStarted = false
        var silenceMs = 0
        var elapsedMs = 0
        var speechMs = 0

        try {
            recorder.startRecording()
            while (recording) {
                val read = recorder.read(buffer, 0, frameSamples)
                if (read <= 0) continue
                elapsedMs += FRAME_MS

                val rms = frameRms(buffer, read)
                val bytes = toLittleEndianBytes(buffer, read)

                if (noiseCount < NOISE_FRAMES && !speechStarted) {
                    noiseSum += rms
                    noiseCount++
                    noiseFloor = maxOf(noiseSum / noiseCount, MIN_NOISE_FLOOR)
                }

                if (!speechStarted) {
                    preroll.addLast(bytes)
                    while (preroll.size > prerollFrames) preroll.removeFirst()

                    if (noiseCount >= NOISE_FRAMES && rms > noiseFloor * onsetFactor) {
                        consecutiveLoud++
                        if (consecutiveLoud >= ONSET_FRAMES) {
                            speechStarted = true
                            preroll.forEach { speech.write(it) }
                            preroll.clear()
                            main.post { listener.onSpeechStarted() }
                        }
                    } else {
                        consecutiveLoud = 0
                    }

                    if (elapsedMs >= maxWaitForSpeechMs) {
                        recording = false
                        finishNoSpeech(listener, "no speech detected")
                        return
                    }
                } else {
                    speech.write(bytes)
                    speechMs += FRAME_MS
                    // Hysteresis: the gate closes at a lower level than it
                    // opens, so a quiet final consonant does not truncate the
                    // word it belongs to.
                    silenceMs = if (rms > noiseFloor * (onsetFactor * 0.6f)) 0
                                else silenceMs + FRAME_MS

                    if (silenceMs >= silenceHangoverMs || speechMs >= maxUtteranceMs) {
                        recording = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "capture failed", e)
            finishNoSpeech(listener, "recording failed: ${e.message}")
            return
        } finally {
            recording = false
            try {
                recorder.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "stop() on an already-stopped recorder", e)
            }
            recorder.release()
        }

        if (!speechStarted || speechMs < minSpeechMs) {
            finishNoSpeech(listener, "utterance too short")
            return
        }

        try {
            writeWav(outputFile, speech.toByteArray())
        } catch (e: Exception) {
            finishNoSpeech(listener, "could not write audio: ${e.message}")
            return
        }
        val duration = speechMs
        main.post { listener.onUtterance(outputFile, duration) }
    }

    private fun finishNoSpeech(listener: Listener, reason: String) {
        recording = false
        main.post { listener.onNoSpeech(reason) }
    }

    private fun frameRms(frame: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val v = frame[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / count)
    }

    private fun toLittleEndianBytes(frame: ShortArray, count: Int): ByteArray {
        val out = ByteArray(count * 2)
        for (i in 0 until count) {
            val v = frame[i].toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun writeWav(file: File, pcm: ByteArray) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            val byteRate = sampleRate * 2
            out.writeBytes("RIFF")
            out.writeIntLe(WAV_HEADER_BYTES - 8 + pcm.size)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLe(16)              // PCM header size
            out.writeShortLe(1)             // format: linear PCM
            out.writeShortLe(1)             // channels: mono
            out.writeIntLe(sampleRate)
            out.writeIntLe(byteRate)
            out.writeShortLe(2)             // block align
            out.writeShortLe(16)            // bits per sample
            out.writeBytes("data")
            out.writeIntLe(pcm.size)
            out.write(pcm)
        }
    }

    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        ))
    }

    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        ))
    }
}
