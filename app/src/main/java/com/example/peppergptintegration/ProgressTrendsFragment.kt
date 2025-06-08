package com.example.peppergptintegration

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.peppergptintegration.databinding.FragmentProgressTrendsBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject


class ProgressTrendsFragment : Fragment() {
    private var _binding: FragmentProgressTrendsBinding? = null
    private val binding get() = _binding!!
    private var childId: String = ""

    companion object {
        fun newInstance(childId: String) = ProgressTrendsFragment().apply {
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
        _binding = FragmentProgressTrendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getString("childId") ?: ""

        loadProgressTrends()
    }

    private fun loadProgressTrends() {
        lifecycleScope.launch {
            try {
                val trends = withContext(Dispatchers.IO) {
                    val response = OkHttpClient().newCall(
                        Request.Builder()
                            .url("${BuildConfig.BASE_URL}analytics/children/$childId/progress-trends")
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

                trends?.let {
                    withContext(Dispatchers.Main) {
                        bindTrendData(it)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProgressTrends", "Error loading data", e)
            }
        }
    }

    private fun parseTrendData(json: String): ProgressTrends {
        return try {
            val data = JSONObject(json)

            val weekly = data.optJSONObject("weekly_trend")
            val monthly = data.optJSONObject("monthly_trend")
            val improvementAreas = data.optJSONArray("improvement_areas") ?: JSONArray()

            ProgressTrends(
                weeklyTrend = weekly?.let {
                    ProgressTrends.Trend(
                        trend = it.optString("trend"),
                        rate = it.optDouble("rate").takeIf { !it.isNaN() },
                        currentScore = it.optDouble("current_score").takeIf { !it.isNaN() },
                        startingScore = it.optDouble("starting_score").takeIf { !it.isNaN() }
                    )
                },
                monthlyTrend = monthly?.let {
                    ProgressTrends.Trend(
                        trend = it.optString("trend"),
                        rate = it.optDouble("rate").takeIf { !it.isNaN() },
                        currentScore = it.optDouble("current_score").takeIf { !it.isNaN() },
                        startingScore = it.optDouble("starting_score").takeIf { !it.isNaN() }
                    )
                },
                improvementAreas = (0 until improvementAreas.length()).mapNotNull { i ->
                    improvementAreas.optJSONObject(i)?.let { area ->
                        ProgressTrends.ImprovementArea(
                            category = area.optString("category"),
                            improvementRate = area.optDouble("improvement_rate").takeIf { !area.isNull("improvement_rate") }
                        )
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("ProgressTrends", "Error parsing trend data", e)
            ProgressTrends() // Return empty object on error
        }
    }

    private fun bindTrendData(trends: ProgressTrends) {
        // Weekly trend
        trends.weeklyTrend?.let { weekly ->
            binding.weeklyTrendText.text = when(weekly.trend) {
                "improving" -> "↑ Improving ${weekly.rate?.let { "${(it * 100).toInt()}%" } ?: ""}"
                "declining" -> "↓ Declining ${weekly.rate?.let { "${(it * 100).toInt()}%" } ?: ""}"
                else -> "→ Stable"
            }.trim()

            weekly.currentScore?.let {
                binding.weeklyCurrentScore.text = "${(it * 100).toInt()}%"
                binding.weeklyCurrentScore.visibility = View.VISIBLE
            } ?: run {
                binding.weeklyCurrentScore.visibility = View.GONE
            }

            weekly.startingScore?.let {
                binding.weeklyStartingScore.text = "${(it * 100).toInt()}%"
                binding.weeklyStartingScore.visibility = View.VISIBLE
            } ?: run {
                binding.weeklyStartingScore.visibility = View.GONE
            }
        } ?: run {
            binding.weeklyTrendContainer.visibility = View.GONE
        }

        // Monthly trend
        trends.monthlyTrend?.let { monthly ->
            binding.monthlyTrendText.text = when(monthly.trend) {
                "improving" -> "↑ Improving ${monthly.rate?.let { "${(it * 100).toInt()}%" } ?: ""}"
                "declining" -> "↓ Declining ${monthly.rate?.let { "${(it * 100).toInt()}%" } ?: ""}"
                else -> "→ Stable"
            }.trim()

            // Similar handling for monthly scores...
        } ?: run {
            binding.monthlyTrendContainer.visibility = View.GONE
        }

        // Improvement areas
        binding.improvementAreasGroup.removeAllViews()
        trends.improvementAreas.forEach { area ->
            binding.improvementAreasGroup.addView(
                Chip(requireContext()).apply {
                    text = buildString {
                        append(area.category)
                        area.improvementRate?.let {
                            append(" (${(it * 100).toInt()}%)")
                        }
                    }
                    isCheckable = false
                    setChipBackgroundColorResource(R.color.surfaceContainerHigh)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.onSurface))
                    chipStrokeWidth = 1f
                    chipStrokeColor = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.outline)
                    )
                }
            )
        }
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