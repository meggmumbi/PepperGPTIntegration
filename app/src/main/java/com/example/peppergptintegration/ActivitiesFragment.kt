package com.example.peppergptintegration

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaRecorder
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.example.peppergptintegration.R
import com.example.peppergptintegration.databinding.FragmentActivitiesBinding
import com.example.peppergptintegration.TherapyItem
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ActivitiesFragment : Fragment() {
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 1
        private const val AUDIO_PERMISSION_REQUEST_CODE = 101
        private const val CONNECTION_TIMEOUT = 30L
        private const val CORRECT_ANIMATION = "affirmation_a005.qianim"
        private const val CORRECT_ANIMATION_DURATION = 2160L
        private const val INCORRECT_ANIMATION = "both_hands_on_hips_b001.qianim"
        private const val INCORRECT_ANIMATION_DURATION = 1440L
    }

    private var _binding: FragmentActivitiesBinding? = null
    private val binding get() = _binding!!
    private val args: ActivitiesFragmentArgs by navArgs()
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var sessionId: String? = null
    private var currentItem: TherapyItem? = null
    private var nonverbalOptions: List<NonverbalOption> = emptyList()
    private var sessionTimer: CountDownTimer? = null
    private var elapsedSeconds: Long = 0
    private var responseStartTime: Long = 0
    private var isProcessingResponse = false
    private var currentAttempt = 0
    private var retryAttempts = 0
    private var lastSelectedOption: String? = null
    private var audioRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var audioTimer: CountDownTimer? = null
    private var feedbackPopup: Dialog? = null
    private var isFeedbackInProgress = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestAudioPermissions()
        setupAudioRecording()
//        setupToolbar()
        setupClickListeners()
        setupResponseTypeToggle()
        startTherapySession()
        startSessionTimer()
    }
    private fun requestAudioPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        if (permissions.any { ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(permissions, AUDIO_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(context, "Audio permissions are required", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun setupAudioRecording() {
        binding.recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    private fun startRecording() {
        try {
            audioFile = File(requireContext().cacheDir, "audio_response.mp3")
            if (audioFile?.exists() == true) {
                audioFile?.delete()
            }

            audioRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)  // Important!
                setAudioChannels(1)         // Mono
                setAudioEncodingBitRate(128000)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            binding.recordButton.text = "Stop Recording"


        } catch (e: Exception) {
            Log.e("AudioRecording", "Failed to start recording", e)
            Toast.makeText(context, "Recording failed to start", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            audioRecorder?.apply {
                stop()
                release()
            }
            audioRecorder = null
            isRecording = false
            binding.recordButton.text = "Record Response"
            (activity as? MainActivity)?.safeSay("Recording stopped")

            // Process the recorded audio
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    processAudioResponse(file)
                } else {
                    Toast.makeText(context, "Recording failed", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecording", "Failed to stop recording", e)
        }
    }

    private fun processAudioResponse(audioFile: File) {
        currentItem?.let { item ->
            showLoadingState()
            (activity as? MainActivity)?.safeSay("Processing your response")
            val responseTimeSeconds = (System.currentTimeMillis() - responseStartTime) / 1000
            lifecycleScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        sendAudioToApi(
                            sessionId = sessionId!!,
                            itemId = item.id,
                            response_time_seconds = responseTimeSeconds,
                            audioFile = audioFile
                        )
                    }

                    withContext(Dispatchers.Main) {
                        if (response != null) {
                            val isCorrect = response.analysis.is_correct

                            // Show appropriate feedback (correct/incorrect)
                            showAudioFeedback(isCorrect, response.analysis.feedback)

                            if (isCorrect) {
                                // Correct response - proceed to next item
                                isProcessingResponse = true
                                Handler(Looper.getMainLooper()).postDelayed({
                                    fetchNextItem()
                                }, 2000) // Delay to allow user to see feedback
                            } else if (retryAttempts < MAX_RETRY_ATTEMPTS) {
                                // Incorrect but can retry
                                retryAttempts++
                                Handler(Looper.getMainLooper()).postDelayed({
                                    (activity as? MainActivity)?.safeSay("Try again. Listen and say: ${item.name}")
                                      hideLoadingState()
                                }, 2000)
                            } else {
                                // Final incorrect attempt - proceed to next item
                                isProcessingResponse = true
                                Handler(Looper.getMainLooper()).postDelayed({
                                    fetchNextItem()
                                }, 2000)
                            }
                        } else {
                            showErrorState("Failed to process audio response")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showErrorState("Error processing audio: ${e.message}")
                    }
                }
            }
        }
    }
    private suspend fun sendAudioToApi(
        sessionId: String,
        itemId: String,
        response_time_seconds: Long,
        audioFile: File
    ): AudioResponse? {
        val token = getAuthToken() ?: throw Exception("Not authenticated")

        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio_file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/mpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("${BuildConfig.BASE_URL}speech/sessions/$sessionId/process-audio?item_id=$itemId&response_time_seconds=$response_time_seconds")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { json ->
                    val gson = Gson()
                    gson.fromJson(json, AudioResponse::class.java)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AudioUpload", "Error sending audio", e)
            null
        }
    }
//    private fun setupToolbar() {
//        binding.toolbar.setNavigationOnClickListener {
//            showEndSessionConfirmation()
//        }
//        setHasOptionsMenu(true)
//    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.activity_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_end_session -> {
                showEndSessionConfirmation()
                true
            }
            R.id.action_help -> {
                showHelpDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showEndSessionConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("End Session")
            .setMessage("Are you sure you want to end this therapy session?")
            .setPositiveButton("End Session") { _, _ ->
                endSession()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Help")
            .setMessage("""
                1. Select 'Verbal' for verbal responses with Correct/Incorrect buttons
                2. Select 'Nonverbal' to show alternative communication options
                3. The child can respond by either speaking or selecting an option
                4. Progress is automatically recorded
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun endSession() {
        sessionTimer?.cancel()
        findNavController().navigateUp()
        (activity as? MainActivity)?.safeSay("Session ended")
    }

    private fun setupResponseTypeToggle() {
        binding.responseTypeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.verbalButton -> {
                        binding.verbalResponseContainer.visibility = View.VISIBLE
                        binding.nonverbalResponseContainer.visibility = View.GONE
                        (activity as? MainActivity)?.safeSay("Verbal response selected")
                    }
                    R.id.nonverbalButton -> {
                        binding.verbalResponseContainer.visibility = View.GONE
                        binding.nonverbalResponseContainer.visibility = View.VISIBLE
                        (activity as? MainActivity)?.safeSay("Nonverbal response selected")
                        loadNonverbalOptions()
                    }
                }
            }
        }
    }

    private fun loadNonverbalOptions() {
        currentItem?.let { item ->
            showNonverbalLoading(true)

            lifecycleScope.launch {
                try {
                    val options = withContext(Dispatchers.IO) {
                        fetchNonverbalOptions(item.id)
                    }

                    nonverbalOptions = options
                    withContext(Dispatchers.Main) {
                        populateNonverbalOptions(options)
                        showNonverbalLoading(false)
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showNonverbalLoading(false)
                        Toast.makeText(context, "Failed to load nonverbal options", Toast.LENGTH_SHORT).show()
                        Log.e("ActivitiesFragment", "Error loading nonverbal options", e)
                        // Fall back to verbal mode
                        binding.responseTypeToggleGroup.check(R.id.verbalButton)
                    }
                }
            }
        }
    }

    private suspend fun fetchNonverbalOptions(itemId: String): List<NonverbalOption> {
        val token = getAuthToken() ?: throw Exception("Not authenticated")
        val url = "${BuildConfig.BASE_URL}activities/sessions/${sessionId}/selection-options/$itemId"

        try {
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .build()
            ).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error details"
                Log.e("FetchNonverbalOptions", "API error ${response.code}: $errorBody")
                throw Exception("API error: ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            Log.d("FetchNonverbalOptions", "Response: $responseBody")  // Log the raw response
            return parseNonverbalOptions(responseBody)
        } catch (e: Exception) {
            Log.e("FetchNonverbalOptions", "Network error", e)
            throw e
        }
    }

    private fun parseNonverbalOptions(responseBody: String): List<NonverbalOption> {
        return try {
            val jsonArray = JSONArray(responseBody)
            (0 until jsonArray.length()).map { i ->
                NonverbalOption(
                    id = i.toString(),  // Using index as ID since your response doesn't include IDs
                    text = jsonArray.getString(i),
                    icon = null  // No icons in your current response
                )
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse nonverbal options", e)
        }
    }

    private fun populateNonverbalOptions(options: List<NonverbalOption>) {
        binding.nonverbalOptionsGroup.removeAllViews()

        options.forEach { option ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = option.text
                isCheckable = true
                chipStrokeWidth = 1f
                chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.outline)
                )
                setChipBackgroundColorResource(R.color.surfaceContainerHighest)

                // You can add default icons based on text if you want


                chipIconSize = 24f.dpToPx(requireContext())


            }

            binding.nonverbalOptionsGroup.addView(chip)
        }
    }

    private fun showNonverbalLoading(show: Boolean) {
        binding.nonverbalOptionsGroup.visibility = if (show) View.INVISIBLE else View.VISIBLE
        binding.nonverbalProgressIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupClickListeners() {
        binding.nonverbalOptionsGroup.setOnCheckedChangeListener { group, checkedId ->
            if (isProcessingResponse || checkedId == View.NO_ID) return@setOnCheckedChangeListener

            val chip = group.findViewById<Chip>(checkedId)
            currentItem?.let { item ->
                val selectedOption = chip.text.toString()
                val isCorrect = selectedOption.equals(item.name, ignoreCase = true)
                lastSelectedOption = selectedOption

                if (isCorrect) {
                    isProcessingResponse = true

                    showFeedbackAndProceed(true, item.id, selectedOption)
                    recordResponse(item.id, true, selectedOption)
                } else if (retryAttempts < MAX_RETRY_ATTEMPTS) {
                    // First or second incorrect attempt: allow retry
                    retryAttempts++
                    showFeedbackAndProceed(false, item.id, selectedOption)

                    chip.setChipBackgroundColorResource(R.color.errorLight)
                    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.errorDark))

                    Handler(Looper.getMainLooper()).postDelayed({
                        chip.setChipBackgroundColorResource(R.color.surfaceContainerHighest)
                        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.onSurface))
                        group.clearCheck()

                        (activity as? MainActivity)?.safeSay("Try again. Find ${item.name}")
                    }, 1500)
                } else {
                    // Final incorrect attempt
                    isProcessingResponse = true

                    showFeedbackAndProceed(false, item.id, selectedOption)
                    recordResponse(item.id, false, selectedOption)
                }
            }
        }



        binding.retryButton.setOnClickListener {
            startTherapySession()
        }

        binding.errorRetryButton.setOnClickListener {
            startTherapySession()
        }
    }

    private fun startTherapySession() {
        showLoadingState()
        (activity as? MainActivity)?.safeSay("Starting therapy session...")

        lifecycleScope.launch {
            try {
                val responseBodyString = withContext(Dispatchers.IO) {
                    val response = startSessionOnApi(args.childId, args.categoryId, args.difficultyLevel)
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        val errorBody = response.body?.string() ?: "No error message"
                        throw Exception("Failed to start session: ${response.code} - $errorBody")
                    }
                }

                val sessionResponse = parseSessionResponse(responseBodyString)
                if (sessionResponse != null) {
                    sessionId = sessionResponse.sessionId
                    fetchNextItem()
                    (activity as? MainActivity)?.safeSay("Session started. Here's your first item.")
                } else {
                    showErrorState("Failed to parse session response")
                    (activity as? MainActivity)?.safeSay("Failed to start session. Please try again.")
                }

            } catch (e: Exception) {
                showErrorState("Network error: ${e.message} during start therapy session")
                Log.e("TherapySession", "Exception during session start", e)
                (activity as? MainActivity)?.safeSay("Network error. Please check your connection.")
            }
        }
    }


    private suspend fun startSessionOnApi(
        childId: String,
        categoryId: String,
        difficultyLevel: String
    ): Response {
        val token = getAuthToken() ?: throw Exception("Not authenticated")

        val json = JSONObject().apply {
            put("child_id", childId)
            put("category_id", categoryId)
            put("current_level", difficultyLevel)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        return client.newCall(
            Request.Builder()
                .url("${BuildConfig.BASE_URL}activities/sessions/")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()
        ).execute()
    }

    private fun parseSessionResponse(responseBody: String?): SessionStartResponse? {
        return try {
            val json = responseBody?.let { JSONObject(it) } ?: return null
            SessionStartResponse(
                sessionId = json.getString("id"),
                childId = json.optString("child_id"),
                categoryId = json.optString("category_id"),
                currentLevel = json.optString("current_level")
            )
        } catch (e: Exception) {
            Log.e("ActivitiesFragment", "Error parsing session response", e)
            null
        }
    }

    private fun fetchNextItem() {
        sessionId?.let { id ->
            lifecycleScope.launch {
                var attempt = 0
                var success = false

                while (!success && attempt < MAX_RETRY_ATTEMPTS) {
                    attempt++

                    val (item, error, isSessionComplete) = withContext(Dispatchers.IO) {
                        try {
                            val response = getNextItemFromApi(id)
                            val bodyString = response.body?.string()

                            if (response.isSuccessful && !bodyString.isNullOrBlank()) {
                                val json = JSONObject(bodyString)
                                if (json.optString("status") == "completed") {
                                    Triple(null, null, true)
                                } else {
                                    Triple(parseTherapyItem(bodyString), null, false)
                                }
                            } else {
                                val errorMsg = "API error: ${response.code} - ${bodyString ?: "Empty body"}"
                                Triple(null, errorMsg, false)
                            }
                        } catch (e: Exception) {
                            Triple(null, "Network error: ${e.message}", false)
                        }
                    }

                    if (item != null || isSessionComplete || attempt == MAX_RETRY_ATTEMPTS) {
                        withContext(Dispatchers.Main) {
                            when {
                                item != null -> {
                                    currentItem = item
                                    showContentState(item)
                                    success = true
                                    Log.d("FetchNextItem", "Success after $attempt attempts")
                                }
                                isSessionComplete -> {
                                    fetchSessionOverview()
                                    success = true
                                }
                                error != null -> {
                                    showErrorState(error)
                                    Log.w("FetchNextItem", "Failed after $attempt attempts: $error")
                                    success = true
                                }
                                else -> {
                                    showErrorState("Unknown response from server")
                                    Log.e("FetchNextItem", "Unknown error after $attempt attempts")
                                    success = true
                                }
                            }
                        }
                    }
                }
            }
        } ?: showErrorState("Session not started")
    }



    private suspend fun getNextItemFromApi(sessionId: String): Response {
        val token = getAuthToken() ?: throw Exception("Not authenticated")

        return try {
            client.newCall(
                Request.Builder()
                    .url("${BuildConfig.BASE_URL}activities/sessions/$sessionId/next-item")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .build()
            ).execute()
        } catch (e: Exception) {
            Log.e("GetNextItem", "Network call failed", e)
            throw e
        }
    }

    private fun parseTherapyItem(responseBody: String): TherapyItem {
        return try {
            val json = JSONObject(responseBody)
            TherapyItem(
                id = json.getString("item_id"),
                name = json.getString("name"),
                imageBase64 = json.optString("image_url", null).takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.e("ParseTherapyItem", "Failed to parse: $responseBody", e)
            throw e
        }
    }

    private fun recordResponse(
        itemId: String,
        isCorrect: Boolean,
        selectedOption: String? = null
    ) {
        disableAllResponseOptions()
//        binding.progressIndicator.visibility = View.VISIBLE

        val responseTimeSeconds = (System.currentTimeMillis() - responseStartTime) / 1000

        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    recordResponseOnApi(
                        sessionId = sessionId!!,
                        itemId = itemId,
                        isCorrect = isCorrect,
                        responseTimeSeconds = responseTimeSeconds.toInt(),
                        selectedOption = selectedOption
                    )
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        retryAttempts = 0
                        isProcessingResponse = false
                        fetchNextItem()
                    } else {
                        showErrorState("Failed to record response")
                        enableAllResponseOptions()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorState("Error recording response: ${e.message}")
                    enableAllResponseOptions()
                }
            }
        }
    }

    private fun resetResponseState() {
        retryAttempts = 0
        lastSelectedOption = null
//        binding.progressIndicator.visibility = View.GONE
        binding.nonverbalOptionsGroup.clearCheck()
        enableAllResponseOptions()
        isProcessingResponse = false
    }

    private suspend fun recordResponseOnApi(
        sessionId: String,
        itemId: String,
        isCorrect: Boolean,
        responseTimeSeconds: Int,
        selectedOption: String? = null
    ): Boolean {
        val token = getAuthToken() ?: throw Exception("Not authenticated")

        val json = JSONObject().apply {
            put("item_id", itemId)
            put("is_correct", isCorrect)
            put("response_type", if (selectedOption != null) "nonverbal" else "verbal")
            put("pronunciation_score", 0)
            put("response_time_seconds", responseTimeSeconds)
            selectedOption?.let { put("selected_option", it) }
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}activities/sessions/$sessionId/record-response")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        return response.isSuccessful
    }


    private fun disableAllResponseOptions() {
        binding.nonverbalOptionsGroup.isEnabled = false
    }

    private fun enableAllResponseOptions() {
        binding.nonverbalOptionsGroup.isEnabled = true
    }

    private suspend fun sendResponseToApi(
        sessionId: String,
        itemId: String,
        isCorrect: Boolean,
        nonverbalOptionId: String? = null
    ): Boolean {
        val token = getAuthToken() ?: throw Exception("Not authenticated")

        val json = JSONObject().apply {
            put("item_id", itemId)
            put("is_correct", isCorrect)
            nonverbalOptionId?.let { put("nonverbal_option_id", it) }
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}activities/sessions/$sessionId/responses")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        return response.isSuccessful
    }

    private fun startSessionTimer() {
        sessionTimer?.cancel()
        elapsedSeconds = 0

        sessionTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                elapsedSeconds++
                updateTimerText()
            }

            override fun onFinish() {}
        }.start()
    }

    private fun updateTimerText() {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
//        binding.sessionTimeText.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun showLoadingState() {
        binding.loadingStateView.visibility = View.VISIBLE
        binding.contentStateView.visibility = View.GONE
        binding.emptyStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.GONE
    }
    private fun hideLoadingState() {
        binding.loadingStateView.visibility = View.GONE
        binding.contentStateView.visibility = View.VISIBLE
        binding.emptyStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.GONE
    }

    private fun showContentState(item: TherapyItem) {
        isProcessingResponse = false
        binding.nonverbalOptionsGroup.clearCheck()
        binding.itemNameTextView.text = item.name
        binding.difficultyChip.text = args.difficultyLevel.capitalize()
        responseStartTime = System.currentTimeMillis()
        retryAttempts = 0
        binding.nonverbalOptionsGroup.clearCheck()

        item.imageBase64?.let { base64String ->
            try {
                val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                binding.itemImageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                binding.itemImageView.setImageResource(R.drawable.ic_broken_image)
                Log.e("ActivitiesFragment", "Error decoding image", e)
            }
        } ?: run {
            binding.itemImageView.setImageResource(R.drawable.ic_no_image)
        }

        binding.loadingStateView.visibility = View.GONE
        binding.contentStateView.visibility = View.VISIBLE
        binding.emptyStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.GONE

        // Reset to verbal mode for each new item
        binding.responseTypeToggleGroup.check(R.id.verbalButton)
        (activity as? MainActivity)?.safeSay("This is ${item.name}. Is this correct?")
    }

    private fun showEmptyState() {
        binding.loadingStateView.visibility = View.GONE
        binding.contentStateView.visibility = View.GONE
        binding.emptyStateView.visibility = View.VISIBLE
        binding.errorStateView.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        binding.errorTextView.text = message
        binding.loadingStateView.visibility = View.GONE
        binding.contentStateView.visibility = View.GONE
        binding.emptyStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.VISIBLE
    }

    private fun fetchSessionOverview() {
        sessionId?.let { id ->
            showLoadingState()
            lifecycleScope.launch {
                try {
                    val overview = withContext(Dispatchers.IO) {
                        val response = client.newCall(
                            Request.Builder()
                                .url("${BuildConfig.BASE_URL}analytics/sessions/$id/overview")
                                .addHeader("Authorization", "Bearer ${getAuthToken()}")
                                .addHeader("Accept", "application/json")
                                .build()
                        ).execute()

                        if (response.isSuccessful) {
                            response.body?.string()?.let { parseSessionOverview(it) }
                        } else {
                            null
                        }
                    }

                    overview?.let {
                        navigateToSessionOverview(it)
                    } ?: showErrorState("Failed to load session overview")
                } catch (e: Exception) {
                    showErrorState("Error loading overview: ${e.message}")
                }
            }
        }
    }
    private fun parseSessionOverview(json: String): SessionOverview {
        val data = JSONObject(json)
        return SessionOverview(
            sessionId = data.getString("session_id"),
            childName = data.getString("child_name"),
            categoryName = data.getString("category_name"),
            startTime = data.getString("start_time"),
            durationMinutes = data.getDouble("duration_minutes"),
            totalActivities = data.getInt("total_activities"),
            correctAnswers = data.getInt("correct_answers"),
            accuracyPercentage = data.getDouble("accuracy_percentage"),
            averageResponseTime = data.getInt("average_response_time"),
            activities = data.getJSONArray("activities").let { array ->
                (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    SessionActivity(
                        itemName = item.getString("item_name"),
                        responseType = item.getString("response_type"),
                        isCorrect = item.getBoolean("is_correct"),
                        pronunciationScore = item.getInt("pronunciation_score"),
                        responseTime = item.getInt("response_time"),
                        feedback = item.optString("feedback")
                    )
                }
            },
            strengths = data.getJSONArray("strengths").let { array ->
                (0 until array.length()).map { i -> array.getString(i) }
            },
            areasForImprovement = data.getJSONArray("areas_for_improvement").let { array ->
                (0 until array.length()).map { i -> array.getString(i) }
            },
            recommendations = data.getJSONArray("recommendations").let { array ->
                (0 until array.length()).map { i -> array.getString(i) }
            }
        )
    }

    private fun navigateToSessionOverview(overview: SessionOverview) {
        val directions = ActivitiesFragmentDirections.actionActivitiesFragmentToSessionOverviewFragment(overview,args.childId)
        findNavController().navigate(directions)
    }

    private fun getAuthToken(): String? {
        return activity?.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            ?.getString("auth_token", null)
    }



    private fun showFeedbackPopup(isCorrect: Boolean): Dialog {
        return Dialog(requireContext()).apply {
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.3f)

            }
            setContentView(if (isCorrect) R.layout.popup_correct_feedback
            else R.layout.popup_incorrect_feedback)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            show()
        }
    }

    private fun showFeedbackAndProceed(isCorrect: Boolean, itemId: String, selectedOption: String?) {
        if (isFeedbackInProgress) return
        isFeedbackInProgress = true

        // 1. Immediately show the popup
        feedbackPopup = showFeedbackPopup(isCorrect)
        disableAllResponseOptions()

        val (animationRes, speechText) = if (isCorrect) {
            Pair(R.raw.affirmation_a005, "Yay! That's correct, let's go to the next item.")
        } else {
            Pair(R.raw.both_hands_on_hips_b001, "Oops, incorrect, let's try again.")
        }

        (activity as? MainActivity)?.let { mainActivity ->
            // 2. Start Pepper's animation and speech together
            val startTime = System.currentTimeMillis()

            mainActivity.runPepperAnimation(animationRes,
                if (isCorrect) CORRECT_ANIMATION_DURATION else INCORRECT_ANIMATION_DURATION
            ) {
                Log.d("Feedback", "Animation completed")
            }

            mainActivity.speakWithPepper(speechText) {
                Log.d("Feedback", "Speech completed")
            }

            // 3. Calculate total expected duration
            val totalDuration = max(
                if (isCorrect) CORRECT_ANIMATION_DURATION else INCORRECT_ANIMATION_DURATION,
                speechText.length * 100L // Approximate speech duration
            ) + 500 // Buffer time

            // 4. Wait for the full experience to complete
            Handler(Looper.getMainLooper()).postDelayed({
                dismissFeedbackPopup()

                isFeedbackInProgress = false
            }, totalDuration)

        } ?: run {
            // Fallback mode without Pepper
            Handler(Looper.getMainLooper()).postDelayed({
                dismissFeedbackPopup()
              //  recordResponse(itemId, isCorrect, selectedOption)
                isFeedbackInProgress = false
            }, if (isCorrect) 2500 else 2000)
        }
    }

    private fun showAudioFeedback(isCorrect: Boolean, feedback: String) {
        if (isFeedbackInProgress) return
        isFeedbackInProgress = true

        // 1. Immediately show the popup
        feedbackPopup = showFeedbackPopup(isCorrect)
        disableAllResponseOptions()

        val (animationRes, speechText) = if (isCorrect) {
            Pair(R.raw.affirmation_a005, "Yay! That's correct, let's go to the next item.")
        } else {
            Pair(R.raw.both_hands_on_hips_b001, "Oops, incorrect, let's try again.")
        }

        (activity as? MainActivity)?.let { mainActivity ->
            // 2. Start Pepper's animation and speech together
            val startTime = System.currentTimeMillis()

            mainActivity.runPepperAnimation(animationRes,
                if (isCorrect) CORRECT_ANIMATION_DURATION else INCORRECT_ANIMATION_DURATION
            ) {
                Log.d("Feedback", "Animation completed")
            }

            mainActivity.speakWithPepper(speechText) {
                Log.d("Feedback", "Speech completed")
            }

            // 3. Calculate total expected duration
            val totalDuration = max(
                if (isCorrect) CORRECT_ANIMATION_DURATION else INCORRECT_ANIMATION_DURATION,
                speechText.length * 100L // Approximate speech duration
            ) + 500 // Buffer time

            // 4. Wait for the full experience to complete
            Handler(Looper.getMainLooper()).postDelayed({
                dismissFeedbackPopup()

                isFeedbackInProgress = false
            }, totalDuration)

        } ?: run {
            // Fallback mode without Pepper
            Handler(Looper.getMainLooper()).postDelayed({
                dismissFeedbackPopup()
                isFeedbackInProgress = false
            }, if (isCorrect) 2500 else 2000)
        }
    }


    private fun dismissFeedbackPopup() {
        try {
            feedbackPopup?.takeIf { it.isShowing }?.dismiss()
        } catch (e: Exception) {
            Log.e("Feedback", "Error dismissing popup", e)
        }
        feedbackPopup = null
    }

    override fun onDestroyView() {
        sessionTimer?.cancel()
        _binding = null
        super.onDestroyView()
    }
}

data class SessionStartResponse(
    val sessionId: String,
    val childId: String,
    val categoryId: String,
    val currentLevel: String
)

data class TherapyItem(
    val id: String,
    val name: String,
    val imageBase64: String?
)

data class NonverbalOption(
    val id: String,
    val text: String,
    val icon: String? = null  // Made optional
)

data class AudioResponse(
    val transcription: String,
    val analysis: Analysis,
    val activity_id: String
) {
    data class Analysis(
        val is_correct: Boolean,
        val similarity_score: Float,
        val feedback: String,
        val phonetic_similarity: Float
    )
}

// Extension function to convert dp to pixels
fun Float.dpToPx(context: Context): Float {
    return this * context.resources.displayMetrics.density
}