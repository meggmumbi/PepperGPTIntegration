package com.example.peppergptintegration.ui.session

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.peppergptintegration.PcmAudioRecorder
import com.example.peppergptintegration.R
import com.example.peppergptintegration.data.Api
import com.example.peppergptintegration.data.ApiException
import com.example.peppergptintegration.data.Exercise
import com.example.peppergptintegration.data.Questionnaire
import com.example.peppergptintegration.data.SessionItem
import com.example.peppergptintegration.data.Summary
import com.example.peppergptintegration.robot.AttentionTracker
import com.example.peppergptintegration.robot.Robot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One study session, from the first item to the researcher's note.
 *
 * The order of what follows the items is fixed and deliberate:
 *
 *   items -> revisit round -> questionnaire -> summary -> researcher's note
 *
 * The questionnaire comes before the summary because a participant who has
 * just read their score is no longer rating the tutor, they are rating their
 * result.
 *
 * This class decides nothing about the study. Whether an answer was right,
 * whether another attempt follows, what the robot says and whether anything
 * is said at all in an assessment are the server's decisions; they are read
 * from each response and acted on. In particular nothing here branches on the
 * feedback condition, which is never even loaded.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    enum class Stage { Loading, Failed, Intro, Running, Revisit, Questionnaire, Summary, Note, Done }

    /** Where the current item is. Mirrors the three phases attention is logged against. */
    enum class Phase { Prompt, Respond, Recording, Scoring, Feedback }

    enum class Tone { Good, Bad, Neutral }

    data class FeedbackUi(
        val text: String,
        val tone: Tone,
        /** Recording advice, for the researcher's eye rather than the participant's. */
        val advice: String? = null,
        /** Set while the participant is being asked how helpful a correction was. */
        val rateAttemptId: String? = null,
    )

    // --- what the screens read ----------------------------------------------

    var stage by mutableStateOf(Stage.Loading)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var stepKey by mutableStateOf("")
        private set
    var givesFeedback by mutableStateOf(false)
        private set

    var items by mutableStateOf<List<SessionItem>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set
    var inRevisit by mutableStateOf(false)
        private set
    var resumedAt by mutableStateOf(0)
        private set

    var phase by mutableStateOf(Phase.Prompt)
        private set
    var hint by mutableStateOf<String?>(null)
        private set
    var feedback by mutableStateOf<FeedbackUi?>(null)
        private set
    var heardSpeech by mutableStateOf(false)
        private set

    var selectedOption by mutableStateOf<String?>(null)
        private set
    var selectedSyllables by mutableStateOf<Set<Int>>(emptySet())
        private set
    /** How the participant's own choice is coloured once it has been graded. */
    var answerTone by mutableStateOf<Tone?>(null)
        private set
    var revealOption by mutableStateOf<String?>(null)
        private set
    var revealSyllables by mutableStateOf<Set<Int>>(emptySet())
        private set

    var questionnaire by mutableStateOf<Questionnaire?>(null)
        private set
    val answers = mutableStateMapOf<String, Int>()
    var comment by mutableStateOf("")
    var summary by mutableStateOf<Summary?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var noteError by mutableStateOf<String?>(null)
        private set

    val current: SessionItem? get() = items.getOrNull(index)

    val title: String
        get() = when (stepKey) {
            "pretest" -> "Pre-test"
            "posttest" -> "Post-test"
            "delayed" -> "Delayed post-test"
            "block1" -> "Practice, part 1"
            "block2" -> "Practice, part 2"
            else -> "Session"
        }

    // --- internals ----------------------------------------------------------

    private var started = false
    private var childId = ""
    private var resumeId: String? = null
    private var sessionId: String? = null
    private var retry: (() -> Unit)? = null

    private var work: Job? = null
    private var recorder: PcmAudioRecorder? = null
    private val attention = AttentionTracker(viewModelScope)

    private var promptEndedAt = 0L
    private var speechStartedAt = 0L
    private var replays = 0
    private var slowUsed = false
    private var corrections = 0
    private var rating: CompletableDeferred<Unit>? = null
    private var questionnaireOpenedAt = 0L

    private fun now() = System.currentTimeMillis()

    private fun iso(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ms))

    // --- starting -----------------------------------------------------------

    fun start(childId: String, step: String, resumeSessionId: String?) {
        if (started) return
        started = true
        this.childId = childId
        this.stepKey = step
        this.resumeId = resumeSessionId
        load()
    }

    private fun load() {
        stage = Stage.Loading
        failure = null
        retry = ::load
        viewModelScope.launch {
            try {
                // Kept once created: retrying after a failed item load must not
                // ask for the same block twice, which the server refuses.
                val id = sessionId ?: resumeId ?: when (stepKey) {
                    "block1" -> Api.startBlock(childId, 1).session_id
                    "block2" -> Api.startBlock(childId, 2).session_id
                    else -> Api.startAssessment(childId, stepKey).session_id
                }
                sessionId = id
                val payload = Api.sessionItems(id)
                givesFeedback = payload.gives_feedback
                items = payload.items.orEmpty()
                if (items.isEmpty()) throw ApiException(0, "This word list has no items in it.")
                // An interrupted session carries on after the items it already
                // has attempts for. The order is fixed by the session's seed,
                // so "how many" is enough to know "which".
                resumedAt = if (resumeId != null) {
                    runCatching { Api.summary(id).items_attempted }.getOrDefault(0)
                        .coerceIn(0, items.size)
                } else 0
                index = resumedAt
                stage = Stage.Intro
            } catch (e: ApiException) {
                fail(e.message)
            }
        }
    }

    private fun fail(message: String?) {
        failure = message ?: "Something went wrong."
        stage = Stage.Failed
    }

    fun retryFailed() {
        retry?.invoke()
    }

    /** The participant pressed Start on the intro page. */
    fun begin() {
        if (stage != Stage.Intro) return
        stage = Stage.Running
        viewModelScope.launch { Robot.holdTablet() }
        if (index < items.size) present() else afterRound()
    }

    // --- one item -----------------------------------------------------------

    private fun present() {
        work?.cancel()
        val item = current ?: return
        selectedOption = null
        selectedSyllables = emptySet()
        answerTone = null
        revealOption = null
        revealSyllables = emptySet()
        feedback = null
        hint = null
        heardSpeech = false
        replays = 0
        slowUsed = false
        work = viewModelScope.launch {
            phase = Phase.Prompt
            attention.begin(item.id, "prompt", inRevisit)
            playPrompt(item, slow = false, first = true)
            openResponse(item)
        }
    }

    /** The response window opens when the prompt has been heard, not when the screen drew. */
    private fun openResponse(item: SessionItem) {
        promptEndedAt = now()
        phase = Phase.Respond
        attention.begin(item.id, "response", inRevisit)
    }

    /** A perception item is its recording; without one there is nothing to identify. */
    val stimulusMissing: Boolean
        get() = current?.let { it.type == Exercise.MINIMAL_PAIR_ID && it.stimulusAudio == null } ?: false

    private fun target(item: SessionItem): String = item.sentence ?: item.word

    private suspend fun playPrompt(item: SessionItem, slow: Boolean, first: Boolean) {
        val recorded = item.audio_url
        when {
            item.type == Exercise.MINIMAL_PAIR_ID -> {
                if (first) Robot.say(item.description ?: Exercise.instruction(item.type))
                val clip = item.stimulusAudio ?: return
                if (!Robot.playClip(Api.absolute(clip), slow)) {
                    hint = "The recording for this item could not be played."
                }
            }
            // A recorded model, when there is one: the same word every time,
            // which a synthesised one was not for the pilot's participants.
            item.needs_speech && recorded != null -> {
                if (first) Robot.say(Exercise.instruction(item.type))
                if (!Robot.playClip(Api.absolute(recorded), slow)) Robot.say(target(item), slow)
            }
            item.needs_speech ->
                Robot.say(if (first) item.description ?: target(item) else target(item), slow)
            else -> Robot.say(item.description ?: Exercise.title(item.type), slow)
        }
    }

    /** Hear it again. Counted, because an item heard four times is a different trial. */
    fun replay(slow: Boolean) {
        val item = current ?: return
        if (phase != Phase.Respond) return
        replays++
        if (slow) slowUsed = true
        work = viewModelScope.launch {
            phase = Phase.Prompt
            attention.begin(item.id, "prompt", inRevisit)
            playPrompt(item, slow, first = false)
            openResponse(item)
        }
    }

    // --- speaking -----------------------------------------------------------

    /** The microphone button: start listening, or stop early. */
    fun toggleRecording() {
        val item = current ?: return
        if (phase == Phase.Recording) {
            recorder?.stop()
            return
        }
        if (phase != Phase.Respond) return
        Robot.stopSpeaking()
        hint = null
        heardSpeech = false
        speechStartedAt = 0L

        val sentence = item.type == Exercise.READ_SENTENCE
        val describe = item.type == Exercise.PICTURE_DESCRIBE
        val capture = PcmAudioRecorder(
            // A sentence has pauses a single word does not; closing the gate on
            // the first one would cut the reading in half.
            silenceHangoverMs = when { describe -> 2500; sentence -> 1100; else -> 700 },
            maxUtteranceMs = when { describe -> item.seconds * 1000; sentence -> 15_000; else -> 5_000 },
        )
        recorder = capture
        phase = Phase.Recording
        val wav = File(getApplication<Application>().cacheDir, "attempt_${now()}.wav")
        capture.start(wav, object : PcmAudioRecorder.Listener {
            override fun onSpeechStarted() {
                speechStartedAt = now()
                heardSpeech = true
            }

            override fun onUtterance(wav: File, speechDurationMs: Int) = submit(item, wav, speechDurationMs)

            override fun onNoSpeech(reason: String) {
                phase = Phase.Respond
                hint = "I didn't hear that. Tap the microphone and try again."
            }
        })
    }

    fun microphoneRefused() {
        hint = "The microphone is needed to answer by speaking. Allow it in Settings, " +
            "or skip this item."
    }

    private fun submit(item: SessionItem, wav: File, speechMs: Int) {
        val id = sessionId ?: return
        phase = Phase.Scoring
        val startedAt = if (speechStartedAt > 0) speechStartedAt else now() - speechMs
        // Prompt end to speech onset. Both happen on this device, so the
        // measure does not include the upload.
        val responseTime = (startedAt - promptEndedAt).coerceAtLeast(0L) / 1000.0
        work = viewModelScope.launch {
            try {
                val result = Api.attempt(
                    sessionId = id, itemId = item.id, wav = wav,
                    responseTimeSeconds = responseTime,
                    promptEndedAt = iso(promptEndedAt),
                    speechStartedAt = iso(startedAt),
                    speechEndedAt = iso(startedAt + speechMs),
                    promptReplays = replays, slowReplayUsed = slowUsed, revisit = inRevisit,
                )
                runCatching { wav.delete() }

                phase = Phase.Feedback
                attention.begin(item.id, "feedback", inRevisit)
                // An assessment says nothing about the answer, and a gated
                // attempt is a request to repeat rather than a verdict.
                val tone = when {
                    !result.gives_feedback || result.gated -> Tone.Neutral
                    result.is_correct -> Tone.Good
                    else -> Tone.Bad
                }
                val text = result.feedback?.display ?: result.feedback?.speech ?: ""
                feedback = FeedbackUi(
                    text, tone, advice = result.audio_quality?.takeIf { !it.usable }?.advice,
                )
                speak(result.feedback?.speech, tone)

                val corrected = tone == Tone.Bad && result.attempt_id != null
                if (corrected && corrections++ % HELPFULNESS_EVERY == 0) {
                    askHelpfulness(result.attempt_id!!)
                }
                delay(350)
                // The server decides whether a retry follows, because it worded
                // the feedback from that same decision.
                if (result.should_retry || result.gated) {
                    feedback = null
                    hint = text
                    openResponse(item)
                } else {
                    advance()
                }
            } catch (e: ApiException) {
                runCatching { wav.delete() }
                // A failed request is not a failed attempt.
                phase = Phase.Respond
                hint = "That could not be checked. ${e.message.orEmpty()} Tap the microphone to try again."
            }
        }
    }

    /** The robot's words, with the gesture that goes with the verdict. */
    private suspend fun speak(speech: String?, tone: Tone) = coroutineScope {
        when (tone) {
            Tone.Good -> launch { Robot.animate(R.raw.affirmation_a005) }
            Tone.Bad -> launch { Robot.animate(R.raw.both_hands_on_hips_b001) }
            Tone.Neutral -> Unit
        }
        Robot.say(speech.orEmpty())
    }

    private suspend fun askHelpfulness(attemptId: String) {
        val answered = CompletableDeferred<Unit>()
        rating = answered
        feedback = feedback?.copy(rateAttemptId = attemptId)
        withTimeoutOrNull(HELPFULNESS_WAIT_MS) { answered.await() }
        rating = null
        feedback = feedback?.copy(rateAttemptId = null)
    }

    /** A 1-5 rating of the correction just heard, or null to pass. */
    fun rateHelpfulness(value: Int?) {
        val attemptId = feedback?.rateAttemptId
        if (value != null && attemptId != null) {
            viewModelScope.launch { runCatching { Api.rateHelpfulness(attemptId, value) } }
        }
        rating?.complete(Unit)
    }

    // --- tapping ------------------------------------------------------------

    fun select(option: String) {
        if (phase == Phase.Respond || phase == Phase.Prompt) selectedOption = option
    }

    fun tapSyllable(position: Int) {
        if (phase != Phase.Respond && phase != Phase.Prompt) return
        selectedSyllables = if (current?.type == Exercise.STRESS_PLACE) {
            setOf(position)
        } else if (position in selectedSyllables) {
            selectedSyllables - position
        } else {
            selectedSyllables + position
        }
    }

    val canCheck: Boolean
        get() = phase == Phase.Respond && when (current?.type) {
            Exercise.STRESS_PLACE, Exercise.SCHWA_SPOT -> selectedSyllables.isNotEmpty()
            else -> selectedOption != null
        }

    /** Send the tapped answer to be graded. The key is never on this device. */
    fun check() {
        val item = current ?: return
        val id = sessionId ?: return
        if (!canCheck) return
        val answer: Any = when (item.type) {
            Exercise.STRESS_PLACE -> selectedSyllables.first()
            Exercise.SCHWA_SPOT -> selectedSyllables.sorted()
            else -> selectedOption ?: return
        }
        Robot.stopSpeaking()
        phase = Phase.Scoring
        val responseTime = (now() - promptEndedAt).coerceAtLeast(0L) / 1000.0
        work = viewModelScope.launch {
            try {
                val result = Api.answer(id, mapOf(
                    "item_id" to item.id,
                    "answer" to answer,
                    "response_time_seconds" to responseTime,
                    "prompt_replays" to replays,
                    "slow_replay_used" to slowUsed,
                ))
                phase = Phase.Feedback
                attention.begin(item.id, "feedback", inRevisit)
                val tone = when {
                    !result.gives_feedback -> Tone.Neutral
                    result.is_correct -> Tone.Good
                    else -> Tone.Bad
                }
                answerTone = tone
                revealOption = result.reveal?.option
                revealSyllables = result.reveal?.indices?.toSet()
                    ?: result.reveal?.index?.let { setOf(it) } ?: emptySet()
                feedback = FeedbackUi(result.feedback?.display ?: result.feedback?.speech ?: "", tone)
                speak(result.feedback?.speech, tone)
                delay(350)
                advance()
            } catch (e: ApiException) {
                phase = Phase.Respond
                hint = "That could not be checked. ${e.message.orEmpty()} Tap Check to try again."
            }
        }
    }

    // --- skipping and moving on ---------------------------------------------

    /** [reason] is cant_say, cant_hear or other: why, not whether. */
    fun skip(reason: String) {
        val item = current ?: return
        val id = sessionId ?: return
        if (phase != Phase.Respond && phase != Phase.Prompt) return
        work?.cancel()
        Robot.stopSpeaking()
        phase = Phase.Scoring
        work = viewModelScope.launch {
            try {
                val result = Api.skip(id, mapOf(
                    "item_id" to item.id, "reason" to reason,
                    "prompt_replays" to replays, "slow_replay_used" to slowUsed,
                ))
                phase = Phase.Feedback
                attention.begin(item.id, "feedback", inRevisit)
                feedback = FeedbackUi(result.feedback?.display ?: "Skipped", Tone.Neutral)
                Robot.say(result.feedback?.speech.orEmpty())
                delay(350)
                advance()
            } catch (e: ApiException) {
                phase = Phase.Respond
                hint = "That could not be skipped. ${e.message.orEmpty()}"
            }
        }
    }

    private fun advance() {
        index++
        if (index < items.size) present() else afterRound()
    }

    /**
     * The main round is over. A training block brings back the items the
     * participant was corrected on, once; which ones is the server's decision,
     * made from the stored attempts.
     */
    private fun afterRound() {
        val id = sessionId ?: return
        work = viewModelScope.launch {
            if (givesFeedback && !inRevisit) {
                val again = runCatching { Api.revisits(id) }.getOrNull()
                val list = again?.items.orEmpty()
                if (list.isNotEmpty()) {
                    inRevisit = true
                    items = list
                    index = 0
                    stage = Stage.Revisit
                    Robot.say(again?.intro_speech.orEmpty())
                    delay(500)
                    stage = Stage.Running
                    present()
                    return@launch
                }
            }
            finish(endedEarly = false)
        }
    }

    /** The researcher stopped the session before the last item. */
    fun endEarly() {
        work?.cancel()
        recorder?.stop()
        Robot.stopSpeaking()
        work = viewModelScope.launch { finish(endedEarly = true) }
    }

    private suspend fun finish(endedEarly: Boolean) {
        val id = sessionId ?: return
        stage = Stage.Loading
        Robot.releaseTablet()
        try {
            Api.complete(id, endedEarly)
        } catch (e: ApiException) {
            retry = { work = viewModelScope.launch { finish(endedEarly) } }
            fail("The session could not be marked as finished. ${e.message.orEmpty()}")
            return
        }
        val segments = attention.finish()
        if (segments.isNotEmpty()) runCatching { Api.attention(id, segments) }

        if (endedEarly) {
            stage = Stage.Note
            return
        }
        val form = runCatching { Api.questionnaire(id) }.getOrNull()
        if (form != null && form.applicable && !form.already_submitted && !form.scales.isNullOrEmpty()) {
            questionnaire = form
            questionnaireOpenedAt = now()
            stage = Stage.Questionnaire
        } else {
            showSummary()
        }
    }

    // --- after the items ----------------------------------------------------

    fun submitQuestionnaire() {
        val id = sessionId ?: return
        busy = true
        viewModelScope.launch {
            // Partial answers are accepted: declining an item is a participant's right.
            runCatching {
                Api.submitQuestionnaire(
                    id, answers.toMap(), comment.trim().ifBlank { null },
                    ((now() - questionnaireOpenedAt) / 1000).toInt(),
                )
            }
            busy = false
            showSummary()
        }
    }

    private suspend fun showSummary() {
        val id = sessionId ?: return
        stage = Stage.Loading
        summary = runCatching { Api.summary(id) }.getOrNull()
        stage = Stage.Summary
    }

    fun toNote() {
        stage = Stage.Note
    }

    fun saveNote(note: Map<String, Any?>) {
        val id = sessionId ?: return
        busy = true
        noteError = null
        viewModelScope.launch {
            try {
                Api.saveNote(id, note)
                stage = Stage.Done
            } catch (e: ApiException) {
                noteError = e.message
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        recorder?.stop()
        Robot.stopSpeaking()
        Robot.releaseTablet()
        attention.finish()
    }

    companion object {
        /**
         * Which corrections the participant is asked to rate: the first, then
         * every third. Asking after all of them would turn the session into a
         * survey.
         */
        private const val HELPFULNESS_EVERY = 3
        private const val HELPFULNESS_WAIT_MS = 15_000L
    }
}
