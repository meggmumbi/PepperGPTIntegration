package com.example.peppergptintegration.data

import android.content.Context
import android.util.Log
import com.example.peppergptintegration.AppConfig
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A request the server answered with an error, or could not be reached for. */
class ApiException(val code: Int, message: String) : Exception(message)

/**
 * Who is signed in. Kept in the same preferences file the previous app used,
 * so an existing install stays signed in across the upgrade.
 */
object Auth {
    private const val PREFS = "auth_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER = "username"
    private const val KEY_ROLE = "role"

    private lateinit var appContext: Context

    private val _signedIn = MutableStateFlow(false)
    /** Flips to false when the server rejects the token, from anywhere. */
    val signedIn: StateFlow<Boolean> = _signedIn

    @Volatile var token: String? = null
        private set
    @Volatile var username: String? = null
        private set
    @Volatile var role: String? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        token = prefs.getString(KEY_TOKEN, null)
        username = prefs.getString(KEY_USER, null)
        role = prefs.getString(KEY_ROLE, null)
        _signedIn.value = !token.isNullOrBlank()
    }

    fun save(token: String, username: String, role: String?) {
        this.token = token
        this.username = username
        this.role = role
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, username)
            .putString(KEY_ROLE, role)
            .apply()
        _signedIn.value = true
    }

    fun clear() {
        token = null
        role = null
        if (::appContext.isInitialized) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_TOKEN).remove(KEY_ROLE).apply()
        }
        _signedIn.value = false
    }
}

/**
 * The one place the app talks to the backend.
 *
 * Deliberately thin: every decision that matters to the study (which
 * condition, whether an answer was right, whether a retry follows, what the
 * robot says) is made by the server and only rendered here.
 */
object Api {
    private const val TAG = "Api"
    val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        // Scoring is quick, but the first request after a server restart can
        // wait on the acoustic model, and a sentence takes longer than a word.
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /** Absolute URL for a path the server returned relative, e.g. model audio. */
    fun absolute(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path
        else AppConfig.baseUrl + path.trimStart('/')

    suspend fun raw(
        method: String,
        path: String,
        body: RequestBody? = null,
        query: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val base = (AppConfig.baseUrl + path.trimStart('/')).toHttpUrlOrNull()
            ?: throw ApiException(0, "The server address ${AppConfig.baseUrl} is not valid.")
        val url = base.newBuilder().apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        val builder = Request.Builder().url(url).addHeader("Accept", "application/json")
        Auth.token?.let { builder.addHeader("Authorization", "Bearer $it") }
        val needsBody = method == "POST" || method == "PUT"
        builder.method(method, body ?: if (needsBody) "".toRequestBody(jsonType) else null)

        try {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) return@withContext text
                val detail = errorDetail(text)
                Log.w(TAG, "$method $path -> ${response.code} $detail")
                // A rejected token anywhere but the sign-in call means the
                // session has expired; send the user back to sign in.
                if (response.code == 401 && !path.startsWith("auth/login")) Auth.clear()
                throw ApiException(response.code, detail ?: "The server returned ${response.code}.")
            }
        } catch (e: IOException) {
            Log.w(TAG, "$method $path failed: ${e.message}")
            throw ApiException(
                0,
                "Cannot reach the server at ${AppConfig.baseUrl}. Check that this device " +
                    "and the laptop are on the same Wi-Fi and that the backend is running.",
            )
        }
    }

    /** FastAPI puts the reason in `detail`: a string, or a list for a 422. */
    private fun errorDetail(body: String): String? = try {
        val detail = JsonParser.parseString(body).asJsonObject.get("detail")
        when {
            detail == null || detail.isJsonNull -> null
            detail.isJsonPrimitive -> detail.asString
            detail.isJsonArray -> detail.asJsonArray.joinToString("; ") { row: JsonElement ->
                val obj = row.asJsonObject
                val field = obj.getAsJsonArray("loc")?.lastOrNull()?.asString
                val message = obj.get("msg")?.asString ?: "invalid"
                if (field != null && field != "body") "$field: $message" else message
            }
            else -> detail.toString()
        }
    } catch (e: Exception) {
        null
    }

    fun json(value: Any?): RequestBody = gson.toJson(value).toRequestBody(jsonType)

    /**
     * A reply that is not the shape expected is reported like any other
     * failure, so one malformed response cannot take the session down.
     */
    inline fun <reified T> parse(text: String): T = try {
        gson.fromJson<T>(text, object : TypeToken<T>() {}.type)
            ?: throw ApiException(0, "The server sent an empty reply.")
    } catch (e: JsonParseException) {
        throw ApiException(0, "The server sent a reply this app does not understand.")
    }

    suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): T =
        parse(raw("GET", path, query = query))

    suspend inline fun <reified T> post(path: String, body: Any? = null): T =
        parse(raw("POST", path, body?.let { json(it) }))

    suspend inline fun <reified T> put(path: String, body: Any?): T =
        parse(raw("PUT", path, json(body)))

    // --- auth -------------------------------------------------------------

    suspend fun login(username: String, password: String) {
        val text = raw(
            "POST", "auth/login",
            query = mapOf("username" to username, "password" to password),
        )
        val token = gson.fromJson(text, TokenResponse::class.java).access_token
            ?: throw ApiException(500, "The server did not return a token.")
        Auth.save(token, username, null)
        val me = runCatching { get<Me>("auth/me") }.getOrNull()
        Auth.save(token, me?.username ?: username, me?.role)
    }

    suspend fun register(username: String, email: String, password: String) {
        raw("POST", "auth/register",
            json(mapOf("username" to username, "email" to email, "password" to password)))
    }

    suspend fun logout() {
        runCatching { raw("POST", "auth/logout") }
        Auth.clear()
    }

    // --- participants -------------------------------------------------------

    suspend fun participants(): List<Participant> = get("children/")
    suspend fun participant(id: String): Participant = get("children/$id")
    suspend fun createParticipant(body: Map<String, Any?>): Participant = post("children/", body)
    suspend fun updateParticipant(id: String, body: Map<String, Any?>): Participant =
        put("children/$id", body)
    suspend fun deleteParticipant(id: String) { raw("DELETE", "children/$id") }

    // --- study --------------------------------------------------------------

    suspend fun plan(childId: String): Plan = get("study/participants/$childId/plan")
    suspend fun balance(): Balance = get("study/balance")
    suspend fun assign(childId: String): Assignment = post("study/participants/$childId/assignment")
    suspend fun exclude(childId: String, reason: String): Assignment =
        post("study/participants/$childId/exclude", mapOf("reason" to reason))

    suspend fun startAssessment(childId: String, phase: String): SessionStart =
        post("study/assessments", mapOf("child_id" to childId, "phase" to phase))
    suspend fun startBlock(childId: String, block: Int): SessionStart =
        post("study/sessions", mapOf("child_id" to childId, "block" to block))

    suspend fun sessionItems(sessionId: String): SessionItems = get("study/sessions/$sessionId/items")
    suspend fun revisits(sessionId: String): SessionItems = get("study/sessions/$sessionId/revisits")

    suspend fun answer(sessionId: String, body: Map<String, Any?>): AnswerResult =
        post("study/sessions/$sessionId/answers", body)
    suspend fun skip(sessionId: String, body: Map<String, Any?>): SkipResult =
        post("study/sessions/$sessionId/skip", body)
    suspend fun complete(sessionId: String, endedEarly: Boolean) {
        raw("POST", "study/sessions/$sessionId/complete", json(mapOf("ended_early" to endedEarly)))
    }
    suspend fun attention(sessionId: String, segments: List<AttentionSegment>) {
        raw("POST", "study/sessions/$sessionId/attention", json(mapOf("segments" to segments)))
    }

    /**
     * One spoken attempt. The recording is the primary record; everything
     * else here describes the conditions it was made under.
     */
    suspend fun attempt(
        sessionId: String,
        itemId: String,
        wav: File,
        responseTimeSeconds: Double,
        promptEndedAt: String?,
        speechStartedAt: String?,
        speechEndedAt: String?,
        promptReplays: Int,
        slowReplayUsed: Boolean,
        revisit: Boolean,
    ): AttemptResult {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("item_id", itemId)
            .addFormDataPart("response_time_seconds", responseTimeSeconds.toString())
            .addFormDataPart("prompt_replays", promptReplays.toString())
            .addFormDataPart("slow_replay_used", slowReplayUsed.toString())
            .addFormDataPart("revisit", revisit.toString())
        promptEndedAt?.let { form.addFormDataPart("prompt_ended_at", it) }
        speechStartedAt?.let { form.addFormDataPart("speech_started_at", it) }
        speechEndedAt?.let { form.addFormDataPart("speech_ended_at", it) }
        form.addFormDataPart("audio", wav.name, wav.asRequestBody("audio/wav".toMediaType()))
        val text = raw("POST", "pronunciation/sessions/$sessionId/attempts", form.build())
        return parse(text)
    }

    // --- after the session --------------------------------------------------

    suspend fun questionnaire(sessionId: String): Questionnaire =
        get("measures/sessions/$sessionId/questionnaire")
    suspend fun submitQuestionnaire(
        sessionId: String, answers: Map<String, Int>, comment: String?, durationSeconds: Int,
    ) {
        raw("POST", "measures/sessions/$sessionId/questionnaire", json(mapOf(
            "answers" to answers, "comment" to comment, "duration_seconds" to durationSeconds)))
    }
    suspend fun rateHelpfulness(attemptId: String, rating: Int) {
        raw("POST", "measures/attempts/$attemptId/helpfulness", json(mapOf("rating" to rating)))
    }
    suspend fun summary(sessionId: String): Summary = get("analytics/sessions/$sessionId/overview")
    suspend fun saveNote(sessionId: String, note: Map<String, Any?>) {
        raw("PUT", "measures/sessions/$sessionId/note", json(note))
    }
}
