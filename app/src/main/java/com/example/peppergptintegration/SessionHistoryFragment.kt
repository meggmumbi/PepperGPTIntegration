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
import com.example.peppergptintegration.databinding.FragmentSessionHistoryBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class SessionHistoryFragment : Fragment() {
    private var _binding: FragmentSessionHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SessionHistoryAdapter
    private var childId: String = ""

    companion object {
        fun newInstance(childId: String) = SessionHistoryFragment().apply {
            arguments = Bundle().apply {
                putString("childId", childId)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSessionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getString("childId") ?: ""

        setupRecyclerView()
        setupEmptyState()
        loadSessionHistory()
    }

    private fun setupRecyclerView() {
        adapter = SessionHistoryAdapter { session ->
            showFeedbackBottomSheet(session)
        }

        binding.sessionHistoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SessionHistoryFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL).apply {
                setDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.divider)!!)
            })
        }
    }

    private fun setupEmptyState() {
        binding.emptyState.apply {
            root.visibility = View.GONE
            emptyIcon.setImageResource(R.drawable.ic_history)
            emptyTitle.text = getString(R.string.no_session_history)
            emptySubtitle.text = getString(R.string.no_session_history_subtitle)
        }
    }

    private fun loadSessionHistory() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyState.root.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    val response = OkHttpClient().newCall(
                        Request.Builder()
                            .url("${BuildConfig.BASE_URL}analytics/children/$childId/session-history")
                            .addHeader("Authorization", "Bearer ${getAuthToken()}")
                            .addHeader("Accept", "application/json")
                            .build()
                    ).execute()

                    if (response.isSuccessful) {
                        response.body?.string()?.let { parseSessionHistory(it) }
                    } else {
                        null
                    }
                }

                history?.let {
                    withContext(Dispatchers.Main) {
                        adapter.submitList(it)
                        binding.progressBar.visibility = View.GONE
                        binding.emptyState.root.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e("SessionHistory", "Error loading data", e)
                binding.progressBar.visibility = View.GONE
                showErrorSnackbar(getString(R.string.error_loading_session_history))
            }
        }
    }

    private fun parseSessionHistory(json: String): List<SessionHistoryItem> {
        val jsonArray = JSONArray(json)
        return (0 until jsonArray.length()).map { i ->
            val item = jsonArray.getJSONObject(i)
            val feedbackObj = item.optJSONObject("feedback")
            val feedback = feedbackObj?.let {
                SessionFeedback(
                    rating = it.optInt("rating"),
                    comments = it.optString("comments"),
                    progressAchievements = it.optString("progress_achievements"),
                    areasForImprovement = it.optString("areas_for_improvement"),
                    behavioralObservations = it.optString("behavioral_observations"),
                    feedbackType = it.optString("feedback_type"),
                    createdAt = it.optString("created_at")
                )
            }

            SessionHistoryItem(
                id = item.getString("id"),
                date = item.getString("date"),
                category = item.getString("category"),
                durationMinutes = item.optDouble("duration_minutes").takeIf { !item.isNull("duration_minutes") },
                score = item.optDouble("score").takeIf { !item.isNull("score") },
                feedback = feedback
            )
        }
    }

    private fun showFeedbackBottomSheet(session: SessionHistoryItem) {
        try {
            val activity = activity ?: return
            if (!isAdded || activity.isFinishing) return

            session.feedback?.let { feedback ->
                FeedbackBottomSheet.newInstance(feedback).apply {
                    show(activity.supportFragmentManager, "FeedbackBottomSheet")
                }
            } ?: run {
                showInfoSnackbar(getString(R.string.no_feedback_available))
            }
        } catch (e: Exception) {
            Log.e("FeedbackBottomSheet", "Error showing bottom sheet", e)
        }
    }


    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.errorContainer))
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.onErrorContainer))
            .show()
    }

    private fun showInfoSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.surfaceContainerHigh))
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.onSurface))
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

