package com.example.peppergptintegration.robot

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.RawRes
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.human.AttentionState
import com.aldebaran.qi.sdk.`object`.human.Human
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.EnforceTabletReachabilityBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2

/**
 * The tutor's body: speech, gesture, and where the participant is looking.
 *
 * One build runs on Pepper and on a plain Android phone. On Pepper every call
 * goes to the robot. On a phone QiSDK never gains focus, so speech falls back
 * to Android text-to-speech and gestures are skipped -- and every call still
 * *completes*, because the session is sequenced off these calls returning. A
 * call that never returned is what used to strand the first item on a phone.
 *
 * Nothing here composes what is said. The words come from the backend, so the
 * two feedback conditions cannot differ by anything this class added.
 */
object Robot {
    private const val TAG = "Robot"

    private lateinit var appContext: Context

    /** Set while the robot has given this app focus. */
    @Volatile var qiContext: QiContext? = null
        private set

    /**
     * Whether this device is a Pepper at all, decided by the Qi service being
     * installed rather than by focus. Between launch and focus a robot must
     * stay silent, not speak through the tablet's own speaker.
     */
    var isRobotDevice: Boolean = false
        private set

    val hasRobot: Boolean get() = qiContext != null

    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private var utterance = 0

    private var reachability: Future<Void>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        isRobotDevice = try {
            appContext.packageManager.getPackageInfo("com.aldebaran.qi.serviceconnector", 0)
            true
        } catch (e: Exception) {
            false
        }
        Log.d(TAG, if (isRobotDevice) "Pepper: speech through the robot" else "no robot: Android TTS")
    }

    fun onFocusGained(context: QiContext) {
        qiContext = context
    }

    fun onFocusLost() {
        qiContext = null
        reachability = null
    }

    // --- speech -------------------------------------------------------------

    /**
     * Say [text] and return when the audio has finished.
     *
     * [slow] is the participant's slow replay. It changes the stimulus, which
     * is why the caller records that it was used.
     */
    suspend fun say(text: String, slow: Boolean = false) {
        if (text.isBlank()) return
        val context = qiContext
        when {
            context != null -> sayOnPepper(context, text, slow)
            isRobotDevice -> Log.w(TAG, "speech requested before robot focus; staying silent")
            else -> sayOnPhone(text, slow)
        }
    }

    private suspend fun sayOnPepper(context: QiContext, text: String, slow: Boolean) {
        try {
            // \rspd\ is the robot voice's own speed tag, in percent.
            val phrase = if (slow) "\\rspd=70\\ $text" else text
            val future = withContext(Dispatchers.IO) {
                SayBuilder.with(context).withText(phrase).build().async().run()
            }
            await(future)
        } catch (e: Exception) {
            Log.e(TAG, "robot speech failed", e)
        }
    }

    private suspend fun sayOnPhone(text: String, slow: Boolean) {
        val engine = phoneVoice() ?: return
        suspendCancellableCoroutine<Unit> { continuation ->
            val id = "utt-${utterance++}"
            pending[id] = continuation
            continuation.invokeOnCancellation {
                pending.remove(id)
                engine.stop()
            }
            engine.setSpeechRate(if (slow) 0.6f else 1.0f)
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                finish(id)
            }
        }
    }

    /** Built on first use, and never on a robot. */
    private suspend fun phoneVoice(): TextToSpeech? {
        val ready = ttsReady ?: CompletableDeferred<Boolean>().also { deferred ->
            ttsReady = deferred
            tts = TextToSpeech(appContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TextToSpeech unavailable (status $status)")
                    deferred.complete(false)
                    return@TextToSpeech
                }
                // British English: the reference lexicon is British, and a US
                // voice would model the wrong target back at the participant.
                val result = tts?.setLanguage(Locale.UK)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts?.setLanguage(Locale.ENGLISH)
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = finish(utteranceId)
                    @Deprecated("required by the base class")
                    override fun onError(utteranceId: String?) = finish(utteranceId)
                    override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
                })
                deferred.complete(true)
            }
        }
        val ok = withTimeoutOrNull(4_000) { ready.await() } ?: false
        return if (ok) tts else null
    }

    private fun finish(id: String?) {
        val continuation = pending.remove(id ?: return) ?: return
        if (continuation.isActive) continuation.resume(Unit)
    }

    fun stopSpeaking() {
        tts?.stop()
        pending.keys.toList().forEach { finish(it) }
    }

    // --- recorded audio -----------------------------------------------------

    /**
     * Play a recording from the server and return when it ends. False when it
     * could not be played, so the caller can say so rather than sit in silence.
     *
     * Used for model audio: a recorded word is the same word every time, which
     * a synthesised one was not for the pilot's participants.
     */
    suspend fun playClip(url: String, slow: Boolean = false): Boolean {
        val player = MediaPlayer()
        return try {
            withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    fun done(result: Boolean) {
                        if (continuation.isActive) continuation.resume(result)
                    }
                    continuation.invokeOnCancellation { runCatching { player.stop() } }
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    player.setOnCompletionListener { done(true) }
                    player.setOnErrorListener { _, what, extra ->
                        Log.w(TAG, "clip failed: $what/$extra for $url")
                        done(false)
                        true
                    }
                    player.setOnPreparedListener { prepared ->
                        try {
                            if (slow) {
                                // Setting the speed also starts playback.
                                prepared.playbackParams = prepared.playbackParams.setSpeed(0.7f)
                            }
                            if (!prepared.isPlaying) prepared.start()
                        } catch (e: Exception) {
                            Log.w(TAG, "slow playback unsupported; playing at full speed", e)
                            runCatching { prepared.start() }.onFailure { done(false) }
                        }
                    }
                    try {
                        player.setDataSource(url)
                        player.prepareAsync()
                    } catch (e: Exception) {
                        Log.w(TAG, "could not open clip $url", e)
                        done(false)
                    }
                }
            } ?: false
        } finally {
            runCatching { player.release() }
        }
    }

    // --- gesture ------------------------------------------------------------

    /** Play a gesture and return when it ends. Does nothing on a phone. */
    suspend fun animate(@RawRes resource: Int) {
        val context = qiContext ?: return
        try {
            val future = withContext(Dispatchers.IO) {
                val animation = AnimationBuilder.with(context).withResources(resource).build()
                AnimateBuilder.with(context).withAnimation(animation).build().async().run()
            }
            await(future)
        } catch (e: Exception) {
            Log.e(TAG, "animation failed", e)
        }
    }

    /** Keep the tablet where a seated participant can reach it. */
    suspend fun holdTablet() {
        val context = qiContext ?: return
        if (reachability != null) return
        try {
            reachability = withContext(Dispatchers.IO) {
                EnforceTabletReachabilityBuilder.with(context).build().async().run()
            }
        } catch (e: Exception) {
            Log.w(TAG, "tablet reachability unavailable", e)
        }
    }

    fun releaseTablet() {
        reachability?.requestCancellation()
        reachability = null
    }

    private suspend fun <T> await(future: Future<T>) {
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { future.requestCancellation() }
            future.thenConsume { if (continuation.isActive) continuation.resume(Unit) }
        }
    }

    // --- attention ----------------------------------------------------------

    /** One reading of where the participant is looking. */
    data class AttentionSample(val attending: Boolean, val state: String)

    /**
     * Ask the robot's own human-awareness engine, or null when there is no
     * robot or it sees nobody. Derived labels only; no image leaves the robot.
     */
    suspend fun sampleAttention(): AttentionSample? {
        val context = qiContext ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val awareness = context.humanAwareness
                val human = awareness.engagedHuman ?: awareness.humansAround.firstOrNull()
                    ?: return@withContext null
                val state = human.attention
                AttentionSample(
                    attending = isAttending(state, human, context.actuation.robotFrame()),
                    state = state?.name ?: "UNKNOWN",
                )
            } catch (e: Exception) {
                Log.v(TAG, "attention sample failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Looking at the robot counts, and so does facing it while looking
     * elsewhere: the tablet is on the robot's chest, so a participant reading
     * the screen is "looking down" as far as the engine is concerned.
     */
    private fun isAttending(state: AttentionState?, human: Human, robotFrame: Frame?): Boolean {
        if (state == AttentionState.LOOKING_AT_ROBOT) return true
        if (state == null || robotFrame == null) return false
        val limit = if (state == AttentionState.UNKNOWN) 45.0 else 60.0
        return try {
            val t = human.headFrame.computeTransform(robotFrame).transform.translation
            abs(Math.toDegrees(atan2(t.y, t.x))) < limit
        } catch (e: Exception) {
            false
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = null
        pending.clear()
    }
}
