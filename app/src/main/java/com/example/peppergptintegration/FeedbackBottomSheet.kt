package com.example.peppergptintegration

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.peppergptintegration.databinding.BottomSheetFeedbackBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class FeedbackBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFeedbackBinding? = null
    private val binding get() = _binding!!
    private lateinit var feedback: SessionFeedback

    companion object {
        fun newInstance(feedback: SessionFeedback) = FeedbackBottomSheet().apply {
            arguments = Bundle().apply {
                putParcelable("feedback", feedback)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        feedback = arguments?.getParcelable("feedback")!!

        setupViews()
        populateFeedbackData()
    }

    private fun setupViews() {
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        // Set up chips for feedback type
        binding.feedbackTypeChip.text = feedback.feedbackType?.replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(Locale.getDefault()).first() // Returns Char
            } else {
                char // Returns original Char
            }
        } ?: "Unknown" // Handle null case
    }

    private fun populateFeedbackData() {
        binding.apply {
            // Rating
            ratingBar.rating = feedback.rating.toFloat()

            // General comments
            if (feedback.comments.isNullOrEmpty()) {
                commentsSection.visibility = View.GONE
            } else {
                commentsSection.visibility = View.VISIBLE
                commentsText.text = feedback.comments
            }

            // Progress achievements
            if (feedback.progressAchievements.isNullOrEmpty()) {
                progressSection.visibility = View.GONE
            } else {
                progressSection.visibility = View.VISIBLE
                progressText.text = feedback.progressAchievements
            }

            // Areas for improvement
            if (feedback.areasForImprovement.isNullOrEmpty()) {
                improvementSection.visibility = View.GONE
            } else {
                improvementSection.visibility = View.VISIBLE
                improvementText.text = feedback.areasForImprovement
            }

            // Behavioral observations
            if (feedback.behavioralObservations.isNullOrEmpty()) {
                behaviorSection.visibility = View.GONE
            } else {
                behaviorSection.visibility = View.VISIBLE
                behaviorText.text = feedback.behavioralObservations
            }

            // Date
            feedbackDate.text = formatFeedbackDate(feedback.createdAt)
        }
    }


    private fun formatFeedbackDate(dateString: String?): String {
        requireNotNull(dateString) { "dateString must not be null" }
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString // Now safe because we checked nullability
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}