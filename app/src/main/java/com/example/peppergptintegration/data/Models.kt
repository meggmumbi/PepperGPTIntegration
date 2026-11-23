@file:Suppress("PropertyName")

package com.example.peppergptintegration.data

import com.google.gson.JsonObject

/*
 * Shapes of what the backend sends. Field names follow the JSON so no mapping
 * layer can drift from it. Gson fills these without calling a constructor, so
 * anything the server may omit is declared nullable rather than defaulted.
 */

data class TokenResponse(val access_token: String?, val token_type: String?)
data class Me(val username: String?, val role: String?)

// --- participants -----------------------------------------------------------

data class Participant(
    val id: String,
    /** Null for rows enrolled before codes existed. */
    val participant_code: String?,
    val age: Int?,
    val gender: String?,
    val first_language: String?,
    val other_languages: String?,
    val home_language: String?,
    val region_of_upbringing: String?,
    val english_onset_age: Int?,
    val primary_medium_of_instruction: String?,
    val self_rated_english: String?,
    val voice_assistant_use: String?,
    val hearing_or_speech_difficulty: Boolean,
    val hearing_or_speech_note: String?,
    val consent_participation: Boolean,
    val consent_audio_retention: Boolean,
    val consent_date: String?,
    val notes: String?,
    val created_at: String?,
) {
    val label: String get() = participant_code?.takeIf { it.isNotBlank() } ?: "No code"
}

/** Controlled vocabularies, mirroring app/schemas/child.py. */
object Vocab {
    val mediums = listOf("english", "kiswahili", "mother_tongue", "mixed", "other")
    val cefr = listOf("A1", "A2", "B1", "B2", "C1", "C2")
    val voiceUse = listOf("never", "rarely", "monthly", "weekly", "daily")
    val genders = listOf("Female", "Male", "Non-binary", "Prefer not to say")
    val firstLanguages = listOf(
        "Gikuyu", "Dholuo", "Kamba", "Luhya", "Kalenjin", "Kisii", "Meru",
        "Maasai", "Somali", "Mijikenda", "Turkana", "Kiswahili", "English",
    )

    fun pretty(value: String): String =
        value.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// --- study plan -------------------------------------------------------------

data class StudyBlock(val block: Int, val condition: String?, val word_list: String?)

data class Assignment(
    val child_id: String?,
    val group_index: Int,
    val group: String?,
    val blocks: List<StudyBlock>?,
    val excluded: Boolean,
    val exclusion_reason: String?,
    val assigned_at: String?,
)

data class StepRun(val session_id: String, val started_at: String?, val completed: Boolean)

data class Step(
    /** pretest | block1 | block2 | posttest | delayed */
    val step: String,
    /** assessment | training */
    val kind: String,
    val label: String,
    val condition: String?,
    val block: Int?,
    val word_list: String?,
    val done: StepRun?,
)

data class Plan(
    val child_id: String?,
    val participant_code: String?,
    val assignment: Assignment?,
    val steps: List<Step>?,
    val unstamped_sessions: List<String>?,
    val list_problems: List<String>?,
)

data class Balance(
    val per_group: Map<String, Int>?,
    val excluded: Int,
    val list_problems: List<String>?,
)

// --- running a session ------------------------------------------------------

data class SessionStart(
    val session_id: String,
    val session_type: String?,
    val gives_feedback: Boolean,
)

data class Highlight(val text: String?, val start: Int, val end: Int)

data class SessionItem(
    val id: String,
    /** Blank for tap items: there the name is the answer and is withheld. */
    val name: String?,
    /** What the robot says when the item appears. */
    val description: String?,
    val image_url: String?,
    /** Recorded model audio, relative to the server root, when one exists. */
    val audio_url: String?,
    val exercise_type: String?,
    val target_feature_label: String?,
    val needs_speech: Boolean,
    /** The item's spec with the answer key removed. */
    val presentation: JsonObject?,
    val highlight: Highlight?,
    val revisit: Boolean,
) {
    val type: String get() = exercise_type ?: Exercise.REPEAT_WORD

    private fun text(key: String): String? =
        presentation?.get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun list(key: String): List<String> =
        presentation?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.map { it.asString }.orEmpty()

    val word: String get() = text("word") ?: text("target_word") ?: name.orEmpty()
    val sentence: String? get() = text("sentence")
    val targetWord: String? get() = text("target_word")
    val cue: String? get() = text("cue")
    val promptText: String? get() = text("prompt")
    val stimulusAudio: String? get() = text("stimulus_audio")
    val options: List<String> get() = list("options")
    val syllables: List<String> get() = list("syllables")
    val seconds: Int get() = presentation?.get("seconds")?.takeIf { it.isJsonPrimitive }?.asInt ?: 30
}

object Exercise {
    const val REPEAT_WORD = "repeat_word"
    const val READ_SENTENCE = "read_sentence"
    const val PICTURE_DESCRIBE = "picture_describe"
    const val MINIMAL_PAIR_ID = "minimal_pair_id"
    const val ODD_ONE_OUT = "odd_one_out"
    const val SAME_SOUND_MATCH = "same_sound_match"
    const val SYLLABLE_COUNT = "syllable_count"
    const val SCHWA_SPOT = "schwa_spot"
    const val STRESS_PLACE = "stress_place"

    /** The heading over the item, as the backend's exercise catalogue words it. */
    fun title(type: String): String = when (type) {
        REPEAT_WORD -> "Repeat the word"
        READ_SENTENCE -> "Read the sentence"
        PICTURE_DESCRIBE -> "Describe the picture"
        MINIMAL_PAIR_ID -> "Tap what you hear"
        ODD_ONE_OUT -> "Tap the odd one out"
        SAME_SOUND_MATCH -> "Tap the same sound"
        SYLLABLE_COUNT -> "Tap the odd number of beats"
        SCHWA_SPOT -> "Tap the weak syllables"
        STRESS_PLACE -> "Tap the strongest syllable"
        else -> "Listen and answer"
    }

    /** The instruction alone, for when a recording supplies the word itself. */
    fun instruction(type: String): String = when (type) {
        REPEAT_WORD -> "Say the word."
        READ_SENTENCE -> "Read the sentence out loud."
        PICTURE_DESCRIBE -> "Tell me what you can see."
        MINIMAL_PAIR_ID -> "Listen, then tap the word you heard."
        else -> ""
    }
}

data class SessionItems(
    val session_id: String?,
    val session_type: String?,
    val gives_feedback: Boolean,
    val intro_speech: String?,
    val items: List<SessionItem>?,
)

data class Feedback(val speech: String?, val display: String?, val kind: String?)

data class AudioQuality(val verdict: String?, val usable: Boolean, val advice: String?)

data class AttemptResult(
    val attempt_id: String?,
    val gives_feedback: Boolean,
    val is_correct: Boolean,
    val verdict: String?,
    /** A request to repeat, not a scored attempt. */
    val gated: Boolean,
    /** Authoritative: stay on the item when true, advance when false. */
    val should_retry: Boolean,
    val audio_quality: AudioQuality?,
    val feedback: Feedback?,
)

data class Reveal(val option: String?, val index: Int?, val indices: List<Int>?)

data class AnswerResult(
    val attempt_id: String?,
    val gives_feedback: Boolean,
    val is_correct: Boolean,
    val feedback: Feedback?,
    /** Only under diagnostic feedback, only after a wrong answer. */
    val reveal: Reveal?,
)

data class SkipResult(val attempt_id: String?, val feedback: Feedback?)

data class AttentionSegment(
    val item_id: String?,
    val phase: String,
    val revisit: Boolean,
    val started_at_ms: Long,
    val ended_at_ms: Long,
    val samples: Int,
    val attending_samples: Int,
    val states: Map<String, Int>,
)

// --- after the session ------------------------------------------------------

data class ScaleItem(
    val key: String,
    val text: String?,
    val low_label: String?,
    val high_label: String?,
    /** likert7 | differential | single_choice (the free-text question) */
    val kind: String?,
)

data class Scale(val key: String, val title: String?, val items: List<ScaleItem>?)

data class Questionnaire(
    val applicable: Boolean,
    val already_submitted: Boolean,
    val scale_min: Int,
    val scale_max: Int,
    val scales: List<Scale>?,
)

data class Group(
    val key: String?,
    val label: String?,
    val attempted: Int,
    val correct: Int,
    val accuracy_percentage: Double?,
)

data class Summary(
    val participant_code: String?,
    val category_name: String?,
    val session_type: String?,
    /** False for an assessment: nothing below is a verdict, and none is shown. */
    val scored: Boolean,
    val ended_early: Boolean,
    val duration_minutes: Double?,
    val items_attempted: Int,
    val items_total: Int,
    val skipped: Int,
    val replays: Int,
    val correct_answers: Int?,
    val accuracy_percentage: Double?,
    val recording_quality: Map<String, Int>?,
    val by_exercise: List<Group>?,
    val by_feature: List<Group>?,
    val strengths: List<String>?,
    val areas_for_improvement: List<String>?,
    val recommendations: List<String>?,
    val note: String?,
)
