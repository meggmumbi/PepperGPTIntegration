package com.example.peppergptintegration

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.peppergptintegration.databinding.FragmentCategoriesBinding
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    private lateinit var categoriesAdapter: CategoriesAdapter
    private val client = OkHttpClient()
    private var currentPage = 1
    private var isLoading = false
    private var isLastPage = false
    private lateinit var childId: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        childId = arguments?.getString("childId") ?: run {
            showSnackbar("Child ID not provided")
            findNavController().navigateUp()
            return
        }

        setupRecyclerView()
        setupClickListeners()
        fetchCategories()

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshCategories()
        }
        // When navigating to CreateCategoryFragment
        binding.addCategoryFab.setOnClickListener {
            findNavController().navigate(
                CategoriesFragmentDirections.actionCategoriesFragmentToCreateCategoryFragment(childId)
            )
        }
        // Make Pepper announce the screen
        (activity as? MainActivity)?.safeSay("Here are the therapy categories. Please select a category to view activities.")
    }

    private fun setupRecyclerView() {
        categoriesAdapter = CategoriesAdapter(
            categories = emptyList(),
            onItemClick = { category ->
                findNavController().navigate(
                    CategoriesFragmentDirections.actionCategoriesFragmentToActivitiesFragment(
                        childId = arguments?.getString("childId") ?: "",
                        categoryId = category.id,
                        difficultyLevel = category.difficultyLevel.lowercase()
                    )
                )
            },
            onViewClick = { category ->
                findNavController().navigate(
                    CategoriesFragmentDirections.actionCategoriesFragmentToCategoryItemsFragment(
                        childId = arguments?.getString("childId") ?: "",
                        categoryId = category.id
                    )
                )
            },

        )

        binding.categoriesRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = categoriesAdapter
            setHasFixedSize(true)

            // Add pagination scroll listener
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && !isLastPage) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= PAGE_SIZE) {
                            loadMoreCategories()
                        }
                    }
                }
            })
        }
    }

    private fun setupClickListeners() {
        binding.emptyStateView.findViewById<MaterialButton>(R.id.retryButton).setOnClickListener {
            fetchCategories()
        }

        binding.errorStateView.findViewById<MaterialButton>(R.id.errorRetryButton).setOnClickListener {
            fetchCategories()
        }
    }

    private fun fetchCategories(page: Int = 1) {
        if (page == 1) {
            showLoadingState()
        } else {
            binding.paginationProgressBar.visibility = View.VISIBLE
        }

        isLoading = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val categories = getCategoriesFromApi(page)
                withContext(Dispatchers.Main) {
                    if (page == 1) {
                        categoriesAdapter.updateCategories(categories)
                        if (categories.isEmpty()) {
                            showEmptyState()
                        } else {
                            showContentState()
                        }
                    } else {
                        categoriesAdapter.addCategories(categories)
                        binding.paginationProgressBar.visibility = View.GONE
                    }

                    // Check if this is the last page
                    isLastPage = categories.size < PAGE_SIZE
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (page == 1) {
                        showErrorState("Failed to load categories: ${e.message}")
                    } else {
                        showSnackbar("Failed to load more categories")
                        binding.paginationProgressBar.visibility = View.GONE
                    }
                    isLoading = false
                    Log.e("Categories", "Error fetching categories", e)
                }
            }
        }
    }

    private fun refreshCategories() {
        currentPage = 1
        isLastPage = false
        fetchCategories(currentPage)
        binding.swipeRefreshLayout.isRefreshing = false
    }

    private fun loadMoreCategories() {
        currentPage++
        fetchCategories(currentPage)
    }

    private suspend fun getCategoriesFromApi(page: Int): List<Category> {
        val token = getAuthToken() ?: run {
            Log.e("API", "No auth token found")
            throw Exception("Not authenticated")
        }

        val url = "${BuildConfig.BASE_URL}activities/categories/?page=$page"
        Log.d("API", "Fetching categories from: $url")

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

            parseCategories(responseBody)
        } catch (e: Exception) {
            Log.e("API", "Network error", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    private fun parseCategories(jsonString: String): List<Category> {
        return try {
            val jsonArray = JSONArray(jsonString)
            List(jsonArray.length()) { i ->
                val categoryJson = jsonArray.getJSONObject(i)
                Category(
                    id = categoryJson.getString("id"),
                    name = categoryJson.getString("name"),
                    description = categoryJson.getString("description"),
                    difficultyLevel = categoryJson.getString("difficulty_level")
                )
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse categories: ${e.message}")
        }
    }

    private fun getAuthToken(): String? {
        return activity?.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            ?.getString("auth_token", null)
    }

    private fun navigateToActivities(category: Category) {
        // Navigate to activities list for this category
//        findNavController().navigate(
//            CategoriesFragmentDirections.actionCategoriesFragmentToActivitiesFragment(category.id)
//        )

        (activity as? MainActivity)?.safeSay("You selected ${category.name} category.")
    }

    private fun showLoadingState() {
        binding.loadingStateView.visibility = View.VISIBLE
        binding.emptyStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.GONE
        binding.swipeRefreshLayout.visibility = View.GONE
    }

    private fun showContentState() {
        binding.loadingStateView.visibility = View.GONE
        binding.emptyStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.GONE
        binding.swipeRefreshLayout.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        binding.loadingStateView.visibility = View.GONE
        binding.emptyStateView.visibility = View.VISIBLE
        binding.errorStateView.visibility = View.GONE
        binding.swipeRefreshLayout.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        binding.loadingStateView.visibility = View.GONE
        binding.emptyStateView.visibility = View.GONE
        binding.errorStateView.visibility = View.VISIBLE
        binding.swipeRefreshLayout.visibility = View.GONE
        binding.errorTextView.text = message
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PAGE_SIZE = 10 // Adjust based on your API's page size
    }
}

