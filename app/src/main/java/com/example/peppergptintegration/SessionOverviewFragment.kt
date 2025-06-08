package com.example.peppergptintegration


import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.peppergptintegration.databinding.DialogFeedbackBinding
import com.example.peppergptintegration.databinding.FragmentSessionOverviewBinding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class SessionOverviewFragment : Fragment() {
    private var _binding: FragmentSessionOverviewBinding? = null
    private val binding get() = _binding!!
    private val args: SessionOverviewFragmentArgs by navArgs()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Increased timeout
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSessionOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(args.overview)
        setupClickListeners()
    }

    private fun setupUI(overview: SessionOverview) {
        // Header
        binding.sessionSummaryText.text = "${overview.childName} completed ${overview.categoryName} practice"
        binding.correctAnswersText.text = overview.correctAnswers.toString()
        binding.incorrectAnswersText.text = (overview.totalActivities - overview.correctAnswers).toString()

        // Metrics
        binding.accuracyText.text = "${overview.accuracyPercentage.roundToInt()}%"
        binding.avgResponseTimeText.text = "${overview.averageResponseTime} sec"
        binding.durationText.text = "${overview.durationMinutes.roundToInt()} min"

        // Strengths
        overview.strengths.forEach { item ->
            binding.strengthsChipGroup.addView(
                Chip(requireContext()).apply {
                    text = item
                    isCheckable = false
                    setChipBackgroundColorResource(R.color.successContainer)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.onSuccessContainer))
                    chipStrokeWidth = 1f
                    chipStrokeColor = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.success)
                    )
                }
            )
        }

        // Areas for improvement
        overview.areasForImprovement.forEach { item ->
            binding.improvementChipGroup.addView(
                Chip(requireContext()).apply {
                    text = item
                    isCheckable = false
                    setChipBackgroundColorResource(R.color.errorContainer)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.onErrorContainer))
                    chipStrokeWidth = 1f
                    chipStrokeColor = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.error)
                    )
                }
            )
        }

        // Recommendations
        binding.recommendationsText.text = overview.recommendations.joinToString("\n\n")

        // Announce completion
        (activity as? MainActivity)?.safeSay(
            "Great job! You completed ${overview.categoryName} practice with " +
                    "${overview.correctAnswers} out of ${overview.totalActivities} correct. " +
                    "Your accuracy was ${overview.accuracyPercentage.roundToInt()} percent. " +
                    overview.recommendations.joinToString()
        )
    }

    private fun setupClickListeners() {

        binding.doneButton.setOnClickListener {
            val directions = SessionOverviewFragmentDirections.actionSessionOverviewFragmentToChildListFragment()
            findNavController().navigate(directions)
        }
// Add this to your overview fragment to navigate to child profile
        binding.viewChildProfileButton.setOnClickListener {
            val directions = SessionOverviewFragmentDirections
                .actionSessionOverviewFragmentToChildProfileFragment( childId = arguments?.getString("childId") ?: "")
            findNavController().navigate(directions)
        }

        binding.feedbackButton.setOnClickListener {
            showFeedbackDialog()
        }
    }

    private fun showFeedbackDialog() {
        val dialogBinding = DialogFeedbackBinding.inflate(layoutInflater)

        val dialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding.root)
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // Set click listeners using the binding
            dialogBinding.cancelButton.setOnClickListener { dismiss() }

            dialogBinding.submitButton.setOnClickListener {
                val feedback = FeedbackRequest(
                    child_id = args.childId,
                    rating = dialogBinding.ratingBar.rating.toInt(),
                    comments = dialogBinding.commentsEditText.text?.toString() ?: "",
                    progress_achievements = dialogBinding.progressEditText.text?.toString() ?: "",
                    areas_for_improvement = dialogBinding.improvementEditText.text?.toString() ?: "",
                    behavioral_observations = dialogBinding.behaviorEditText.text?.toString() ?: ""
                )

                submitFeedback(feedback)
                dismiss()
            }
        }

        dialog.show()
    }


    private fun submitFeedback(feedback: FeedbackRequest) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.feedbackButton.isEnabled = false
                binding.feedbackButton.text = "Submitting..."

                val response = withContext(Dispatchers.IO) {
                    submitFeedbackToApi(args.overview.sessionId, feedback)
                }

                if (response.isSuccessful) {
                    showSuccessSnackbar("Feedback submitted successfully")
                } else {
                    val errorBody = response.body?.string() ?: "No error message"
                    showErrorSnackbar("Failed to submit feedback: ${response.code} - $errorBody")

                }
            } catch (e: Exception) {
                showToast("Error: ${e.localizedMessage}")
                Log.e("Feedback", "Error submitting feedback", e)
            } finally {
                binding.feedbackButton.isEnabled = true
                binding.feedbackButton.text = "Provide Feedback"
            }
        }
    }

    private suspend fun submitFeedbackToApi(
        sessionId: String,
        feedback: FeedbackRequest
    ): Response {
        val token = getAuthToken() ?: throw Exception("Not authenticated")

        val json = JSONObject().apply {
            put("child_id", feedback.child_id)
            put("rating", feedback.rating)
            put("comments", feedback.comments)
            put("progress_achievements", feedback.progress_achievements)
            put("areas_for_improvement", feedback.areas_for_improvement)
            put("behavioral_observations", feedback.behavioral_observations)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        return client.newCall(
            Request.Builder()
                .url("${BuildConfig.BASE_URL}feedback/sessions/$sessionId/feedback")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
        ).execute()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }


    // Helper functions
    private fun showSuccessSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.success))
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.onSuccessContainer))
            .show()
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.error))
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.onError))
            .show()
    }

    private fun getAuthToken(): String? {
        return activity?.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            ?.getString("auth_token", null)
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}