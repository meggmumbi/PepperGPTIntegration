package com.example.peppergptintegration

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.peppergptintegration.databinding.FragmentChildListBinding
import com.example.peppergptintegration.Child
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class ChildListFragment : Fragment() {

    private var _binding: FragmentChildListBinding? = null
    private val binding get() = _binding!!
    private lateinit var childAdapter: ChildAdapter
    private val client = OkHttpClient()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChildListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        fetchChildren()

        binding.addChildFab.setOnClickListener {
            findNavController().navigate(
                ChildListFragmentDirections.actionChildListFragmentToAddChildFragment()
            )
        }



        // Make Pepper announce the screen
        (activity as? MainActivity)?.safeSay("Here is the list of children. Please select a child to begin therapy.")
    }

    private fun setupRecyclerView() {
        childAdapter = ChildAdapter(
            children = emptyList(),
            onItemClick = { child ->
                findNavController().navigate(
                    ChildListFragmentDirections.actionChildListFragmentToEditChildFragment(child.id)
                )
            },
            onTherapyClick = { child ->
                startTherapySession(child)
            },
            onViewProfileClick = { child ->
                navigateToChildDetails(child.id)
            }
        )

        binding.childRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = childAdapter
            setHasFixedSize(true)
        }
    }

    private fun fetchChildren() {
        showLoadingState()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val children = getChildrenFromApi()
                withContext(Dispatchers.Main) {

                    if (children.isNotEmpty()) {
                        showContentState()
                        childAdapter.updateChildren(children)
                    } else {
                        showEmptyState()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorState("Failed to load children: ${e.message}")
                    showError("Failed to load children: ${e.message}")
                    Log.e("ChildList", "Error fetching children", e)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.addChildFab.setOnClickListener {
            findNavController().navigate(
                ChildListFragmentDirections.actionChildListFragmentToAddChildFragment()
            )
        }

        binding.emptyStateView.findViewById<MaterialButton>(R.id.addChildButton).setOnClickListener {
            findNavController().navigate(
                ChildListFragmentDirections.actionChildListFragmentToAddChildFragment()
            )
        }

        binding.errorStateView.findViewById<MaterialButton>(R.id.retryButton).setOnClickListener {
            fetchChildren()
        }
    }

    private suspend fun getChildrenFromApi(): List<Child> {
        val token = getAuthToken() ?: run {
            Log.e("API", "No auth token found")
            throw Exception("Not authenticated")
        }

        val url = "${BuildConfig.BASE_URL}children/"
        Log.d("API", "Attempting to fetch from: $url with token: ${token.take(5)}...")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .build()

        return try {
            val response = client.newCall(request).execute()
            Log.d("API", "Response code: ${response.code}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e("API", "Error response: $errorBody")
                throw Exception("API request failed: ${response.code} - $errorBody")
            }

            val responseBody = response.body?.string()?.also {
                Log.d("API", "Raw response: $it")
            } ?: throw Exception("Empty response body")

            parseChildren(responseBody)
        } catch (e: Exception) {
            Log.e("API", "Network error", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    private fun parseChildren(jsonString: String): List<Child> {
        return try {
            val jsonArray = JSONArray(jsonString)
            List(jsonArray.length()) { i ->
                val childJson = jsonArray.getJSONObject(i)

                // Parse areas of interest
                val areasOfInterestJson = childJson.optJSONArray("areas_of_interest")
                val areasOfInterest = if (areasOfInterestJson != null) {
                    List(areasOfInterestJson.length()) { j ->
                        val areaJson = areasOfInterestJson.getJSONObject(j)
                        CategoryAreas(
                            id = areaJson.getString("id"),
                            name = areaJson.getString("name"),
                            difficultyLevel = "",

                        )
                    }
                } else {
                    emptyList()
                }

                Child(
                    id = childJson.getString("id"),
                    name = childJson.getString("name"),
                    age = childJson.getInt("age"),
                    diagnosisDate = childJson.getString("diagnosis_date"),
                    notes = childJson.optString("notes", ""),
                    therapyGoals = childJson.optString("therapy_goals", ""),
                    areasOfInterest = areasOfInterest,
                    createdAt = childJson.getString("created_at")
                )
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse children: ${e.message}")
        }
    }

    private fun getAuthToken(): String? {
        return activity?.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            ?.getString("auth_token", null)
    }

    private fun navigateToChildDetails(childId: String) {
      findNavController().navigate(
            ChildListFragmentDirections.actionChildListFragmentToChildProfileFragment(childId)
       )
    }

    private fun startTherapySession(child: Child) {
        findNavController().navigate(
            ChildListFragmentDirections.actionChildListFragmentToCategoriesFragment(child.id)
        )
        (activity as? MainActivity)?.safeSay("Starting therapy session with ${child.name}")
    }

    private fun showLoadingState() {
        binding.loadingStateView.visibility = View.VISIBLE
        binding.emptyStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.GONE
        binding.childRecyclerView.visibility = View.GONE
    }

    private fun showContentState() {
        binding.loadingStateView.visibility = View.GONE
        binding.emptyStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.GONE
        binding.childRecyclerView.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        binding.loadingStateView.visibility = View.GONE
        binding.emptyStateView.visibility = View.VISIBLE
        binding.errorStateView.visibility = View.GONE
        binding.childRecyclerView.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        binding.loadingStateView.visibility = View.GONE
        binding.emptyStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.VISIBLE
        binding.childRecyclerView.visibility = View.GONE
        binding.errorTextView.text = message
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        binding.errorTextView.text = message
        binding.errorTextView.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}