package com.example.peppergptintegration

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.peppergptintegration.databinding.FragmentPerformanceDetailsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray


class PerformanceDetailsFragment : Fragment() {
    private var _binding: FragmentPerformanceDetailsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PerformanceDetailsAdapter
    private var childId: String = ""

    companion object {
        fun newInstance(childId: String) = PerformanceDetailsFragment().apply {
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
        _binding = FragmentPerformanceDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getString("childId") ?: ""

        adapter = PerformanceDetailsAdapter()
        binding.performanceRecyclerView.adapter = adapter
        binding.performanceRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadPerformanceData()
    }

    private fun loadPerformanceData() {
        lifecycleScope.launch {
            try {
                val performanceData = withContext(Dispatchers.IO) {
                    val response = OkHttpClient().newCall(
                        Request.Builder()
                            .url("${BuildConfig.BASE_URL}analytics/children/$childId/performance-details")
                            .addHeader("Authorization", "Bearer ${getAuthToken()}")
                            .addHeader("Accept", "application/json")
                            .build()
                    ).execute()

                    if (response.isSuccessful) {
                        response.body?.string()?.let { parsePerformanceData(it) }
                    } else {
                        null
                    }
                }

                performanceData?.let {
                    withContext(Dispatchers.Main) {
                        adapter.submitList(it)
                    }
                }
            } catch (e: Exception) {
                Log.e("PerformanceDetails", "Error loading data", e)
            }
        }
    }

    private fun parsePerformanceData(json: String): List<PerformanceDetails> {
        val jsonArray = JSONArray(json)
        return (0 until jsonArray.length()).map { i ->
            val item = jsonArray.getJSONObject(i)
            PerformanceDetails(
                category = item.getString("category"),
                overallScore = item.getDouble("overall_score"),
                verbalAccuracy = item.getDouble("verbal_accuracy"),
                selectionAccuracy = item.getDouble("selection_accuracy"),
                lastUpdated = item.getString("last_updated")
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

