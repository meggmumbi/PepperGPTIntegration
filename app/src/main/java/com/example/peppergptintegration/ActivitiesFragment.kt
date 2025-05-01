package com.example.peppergptintegration

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
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
import com.example.peppergptintegration.R
import com.example.peppergptintegration.databinding.FragmentActivitiesBinding
import com.example.peppergptintegration.TherapyItem
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ActivitiesFragment : Fragment() {
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 1  // Moved to companion object
    }

    private var _binding: FragmentActivitiesBinding? = null
    private val binding get() = _binding!!
    private val args: ActivitiesFragmentArgs by navArgs()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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

        setupToolbar()
        setupClickListeners()
        setupResponseTypeToggle()
        startTherapySession()
        startSessionTimer()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            showEndSessionConfirmation()
        }
        setHasOptionsMenu(true)
    }

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
        val url = "http://10.0.2.2:8000/activities/sessions/${sessionId}/selection-options/$itemId"

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
        binding.correctButton.setOnClickListener {
            if (!isProcessingResponse) {
                currentItem?.let { item ->
                    recordResponse(item.id, isCorrect = true)
                }
            }
        }

        binding.incorrectButton.setOnClickListener {
            if (!isProcessingResponse) {
                currentItem?.let { item ->
                    recordResponse(item.id, isCorrect = false)
                }
            }
        }
        binding.nonverbalOptionsGroup.setOnCheckedChangeListener { group, checkedId ->
            if (isProcessingResponse || checkedId == View.NO_ID) return@setOnCheckedChangeListener

            val chip = group.findViewById<Chip>(checkedId)
            currentItem?.let { item ->
                val selectedOption = chip.text.toString()
                val isCorrect = selectedOption.equals(item.name, ignoreCase = true)
                lastSelectedOption = selectedOption

                if (isCorrect) {
                    isProcessingResponse = true
                    recordResponse(item.id, true, selectedOption)
                } else if (retryAttempts < MAX_RETRY_ATTEMPTS) {
                    // First or second incorrect attempt: allow retry
                    retryAttempts++

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
                val response = withContext(Dispatchers.IO) {
                    startSessionOnApi(args.childId, args.categoryId, args.difficultyLevel)
                }

                if (response.isSuccessful) {
                    val sessionResponse = parseSessionResponse(response.body?.string())
                    if (sessionResponse != null) {
                        sessionId = sessionResponse.sessionId
                        fetchNextItem()
                        (activity as? MainActivity)?.safeSay("Session started. Here's your first item.")
                    } else {
                        showErrorState("Failed to parse session response")
                        (activity as? MainActivity)?.safeSay("Failed to start session. Please try again.")
                    }
                } else {
                    val errorBody = response.body?.string() ?: "No error message"
                    showErrorState("Failed to start session: ${response.code} - $errorBody")
                    (activity as? MainActivity)?.safeSay("Error starting session. Code ${response.code}.")
                }
            } catch (e: Exception) {
                showErrorState("Network error: ${e.message} during start therapy session")
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
                .url("http://10.0.2.2:8000/activities/sessions/")
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
                try {
                    val (item, error) = withContext(Dispatchers.IO) {
                        try {
                            val response = getNextItemFromApi(id)

                            if (response.isSuccessful) {
                                val responseBody = response.body?.string()
                                if (!responseBody.isNullOrBlank()) {
                                    try {
                                        Pair(parseTherapyItem(responseBody), null)
                                    } catch (e: Exception) {
                                        Log.e("FetchNextItem", "Parsing error", e)
                                        Pair(null, "Data parsing error: ${e.message}")
                                    }
                                } else {
                                    Log.w("FetchNextItem", "Empty response body")
                                    Pair(null, "Empty response from server")
                                }
                            } else {
                                val errorBody = response.body?.string() ?: "No error details"
                                Log.e("FetchNextItem", "API error ${response.code}: $errorBody")
                                Pair(null, "API error: ${response.code} - $errorBody")
                            }
                        } catch (e: Exception) {
                            Log.e("FetchNextItem", "Network error", e)
                            Pair(null, "Network error: ${e.message ?: "Unknown error"}")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        when {
                            item != null -> {
                                currentItem = item
                                showContentState(item)
                                Log.d("FetchNextItem", "Successfully loaded item: ${item.name}")
                            }
                            error != null -> showErrorState(error)
                            else -> showEmptyState()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showErrorState("Unexpected error: ${e.message}")
                        Log.e("FetchNextItem", "Unexpected error", e)
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
                    .url("http://10.0.2.2:8000/activities/sessions/$sessionId/next-item")
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
        binding.progressIndicator.visibility = View.VISIBLE

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
        binding.progressIndicator.visibility = View.GONE
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
            .url("http://10.0.2.2:8000/activities/sessions/$sessionId/record-response")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        return response.isSuccessful
    }


    private fun disableAllResponseOptions() {
        binding.correctButton.isEnabled = false
        binding.incorrectButton.isEnabled = false
        binding.nonverbalOptionsGroup.isEnabled = false
    }

    private fun enableAllResponseOptions() {
        binding.correctButton.isEnabled = true
        binding.incorrectButton.isEnabled = true
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
            .url("http://10.0.2.2:8000/activities/sessions/$sessionId/responses")
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
        binding.sessionTimeText.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun showLoadingState() {
        binding.loadingStateView.visibility = View.VISIBLE
        binding.contentStateView.visibility = View.GONE
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

    private fun getAuthToken(): String? {
        return activity?.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            ?.getString("auth_token", null)
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

// Extension function to convert dp to pixels
fun Float.dpToPx(context: Context): Float {
    return this * context.resources.displayMetrics.density
}