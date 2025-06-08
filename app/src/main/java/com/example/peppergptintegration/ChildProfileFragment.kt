package com.example.peppergptintegration


import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.peppergptintegration.databinding.FragmentChildProfileBinding
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*


class ChildProfileFragment : Fragment() {
    private var _binding: FragmentChildProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewPagerAdapter: ChildProfilePagerAdapter
    private var childId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChildProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getString("childId")

        setupViewPager()
        loadChildProfile()
        loadProgressData()
    }

    private fun setupViewPager() {
        viewPagerAdapter = ChildProfilePagerAdapter(childId ?: "", requireActivity())
        binding.viewPager.adapter = viewPagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Performance"
                1 -> "Session History"
                2 -> "Progress Trends"
                else -> ""
            }
        }.attach()
    }

    private fun loadChildProfile() {
        childId?.let { id ->
            lifecycleScope.launch {
                try {
                    val child = withContext(Dispatchers.IO) {
                        val response = OkHttpClient().newCall(
                            Request.Builder()
                                .url("${BuildConfig.BASE_URL}children/$id")
                                .addHeader("Authorization", "Bearer ${getAuthToken()}")
                                .addHeader("Accept", "application/json")
                                .build()
                        ).execute()

                        if (response.isSuccessful) {
                            response.body?.string()?.let { parseChildProfile(it) }
                        } else {
                            null
                        }
                    }

                    child?.let {
                        withContext(Dispatchers.Main) {
                            bindChildProfile(it)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChildProfile", "Error loading child profile", e)
                }
            }
        }
    }

    private fun parseChildProfile(json: String): ChildProfile {
        val data = JSONObject(json)
        return ChildProfile(
            id = data.getString("id"),
            name = data.getString("name"),
            age = data.getInt("age"),
            diagnosisDate = data.getString("diagnosis_date"),
            notes = data.getString("notes"),
            createdAt = data.getString("created_at")
        )
    }

    private fun bindChildProfile(profile: ChildProfile) {
        binding.childName.text = profile.name
        binding.childAge.text = "${profile.age} years old"
        binding.diagnosisDate.text = "Diagnosis date: ${formatDate(profile.diagnosisDate)}"
        binding.childNotes.text = profile.notes

        // Pepper can announce the child's name
        (activity as? MainActivity)?.safeSay("Viewing profile for ${profile.name}")
    }

    private fun loadProgressData() {
        childId?.let { id ->
            lifecycleScope.launch {
                try {
                    val progress = withContext(Dispatchers.IO) {
                        val response = OkHttpClient().newCall(
                            Request.Builder()
                                .url("${BuildConfig.BASE_URL}analytics/children/$id/progress")
                                .addHeader("Authorization", "Bearer ${getAuthToken()}")
                                .addHeader("Accept", "application/json")
                                .build()
                        ).execute()

                        if (response.isSuccessful) {
                            response.body?.string()?.let { parseProgressData(it) }
                        } else {
                            null
                        }
                    }

                    progress?.let {
                        withContext(Dispatchers.Main) {
                            bindProgressData(it)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChildProfile", "Error loading progress data", e)
                }
            }
        }
    }

    private fun parseProgressData(json: String): ProgressData {
        val data = JSONObject(json)
        val progress = data.getJSONObject("progress")
        val recommendations = data.getJSONObject("recommendations")

        return ProgressData(
            averageScore = progress.getDouble("average_score"),
            strongestCategory = progress.getString("strongest_category"),
            weakestCategory = progress.getString("weakest_category"),
            practiceMore = recommendations.getJSONArray("practice_more").let { array ->
                (0 until array.length()).map { array.getString(it) }
            },
            nextActivities = recommendations.getJSONArray("next_activities").let { array ->
                (0 until array.length()).map {
                    val item = array.getJSONObject(it)
                    NextActivity(
                        category = item.getString("category"),
                        items = item.getJSONArray("items").let { items ->
                            (0 until items.length()).map { items.getString(it) }
                        }
                    )
                }
            },
            mlRecommendation = recommendations.optString("ml_recommendation").takeIf { it.isNotEmpty() },
            confidenceScore = recommendations.optDouble("confidence_score").takeIf { !recommendations.isNull("confidence_score") },
            encouragement = recommendations.optString("encouragement").takeIf { it.isNotEmpty() }
        )
    }

    private fun bindProgressData(data: ProgressData) {
        binding.averageScore.text = "${(data.averageScore * 100).toInt()}%"
        binding.strongestCategory.text = data.strongestCategory
        binding.weakestCategory.text = data.weakestCategory

        // Practice more
        if (data.practiceMore.isNotEmpty()) {
            binding.practiceMoreText.text = data.practiceMore.joinToString("\n• ", "• ")
            binding.practiceMoreText.visibility = View.VISIBLE
        } else {
            binding.practiceMoreText.visibility = View.GONE
        }

        // Next activities
        if (data.nextActivities.isNotEmpty()) {
            val nextActivitiesText = data.nextActivities.joinToString("\n\n") { activity ->
                "• ${activity.category}:\n   ${activity.items.joinToString("\n   ")}"
            }
            binding.nextActivitiesText.text = nextActivitiesText
            binding.nextActivitiesText.visibility = View.VISIBLE
        } else {
            binding.nextActivitiesText.visibility = View.GONE
        }

        // Handle nullable fields
        data.mlRecommendation?.let { recommendation ->
            binding.mlRecommendationText.text = recommendation
            binding.mlRecommendationText.visibility = View.VISIBLE
        } ?: run {
            binding.mlRecommendationText.visibility = View.GONE
        }

        data.confidenceScore?.let { score ->
            binding.confidenceScoreText.text = "${(score * 100).toInt()}% confidence"
            binding.confidenceScoreText.visibility = View.VISIBLE
        } ?: run {
            binding.confidenceScoreText.visibility = View.GONE
        }

        data.encouragement?.let { encouragement ->
            binding.encouragementText.text = encouragement
            binding.encouragementText.visibility = View.VISIBLE
        } ?: run {
            binding.encouragementText.visibility = View.GONE
        }

        // Load trend data
        loadTrendData()
    }

    private fun loadTrendData() {
        childId?.let { id ->
            lifecycleScope.launch {
                try {
                    val trends = withContext(Dispatchers.IO) {
                        val response = OkHttpClient().newCall(
                            Request.Builder()
                                .url("${BuildConfig.BASE_URL}analytics/children/$id/progress-trends")
                                .addHeader("Authorization", "Bearer ${getAuthToken()}")
                                .addHeader("Accept", "application/json")
                                .build()
                        ).execute()

                        if (response.isSuccessful) {
                            response.body?.string()?.let { parseTrendData(it) }
                        } else {
                            null
                        }
                    }

                    trends?.let { trendData ->
                        withContext(Dispatchers.Main) {
                            updateTrendUI(trendData)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChildProfile", "Error loading trend data", e)
                }
            }
        }
    }

    private fun parseTrendData(json: String): TrendData {
        val data = JSONObject(json)
        val weekly = data.getJSONObject("weekly_trend")
        val monthly = data.getJSONObject("monthly_trend")
        val improvementAreas = data.getJSONArray("improvement_areas")

        return TrendData(
            weeklyTrend = TrendData.Trend(
                trend = weekly.getString("trend"),
                rate = weekly.getDouble("rate"),
                currentScore = weekly.getDouble("current_score"),
                startingScore = weekly.getDouble("starting_score")
            ),
            monthlyTrend = TrendData.Trend(
                trend = monthly.getString("trend"),
                rate = monthly.getDouble("rate"),
                currentScore = monthly.getDouble("current_score"),
                startingScore = monthly.getDouble("starting_score")
            ),
            improvementAreas = (0 until improvementAreas.length()).map { i ->
                val area = improvementAreas.getJSONObject(i)
                TrendData.ImprovementArea(
                    category = area.getString("category"),
                    improvementRate = area.getDouble("improvement_rate")
                )
            }
        )
    }

    private fun updateTrendUI(trendData: TrendData) {
        // Update weekly trend
        binding.trendIndicator.text = when(trendData.weeklyTrend.trend) {
            "improving" -> "↑ Improving"
            "declining" -> "↓ Declining"
            else -> "→ Stable"
        }

        val trendColor = when(trendData.weeklyTrend.trend) {
            "improving" -> ContextCompat.getColor(requireContext(), R.color.success)
            "declining" -> ContextCompat.getColor(requireContext(), R.color.error)
            else -> ContextCompat.getColor(requireContext(), R.color.onSurfaceVariant)
        }
        binding.trendIndicator.setTextColor(trendColor)


    }

    private fun getAuthToken(): String? {
        return activity?.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            ?.getString("auth_token", null)
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}



