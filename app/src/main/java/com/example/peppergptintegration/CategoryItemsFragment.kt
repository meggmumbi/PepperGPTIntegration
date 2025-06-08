package com.example.peppergptintegration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.peppergptintegration.databinding.FragmentCategoryItemsBinding
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray

class CategoryItemsFragment : Fragment() {

    private var _binding: FragmentCategoryItemsBinding? = null
    private val binding get() = _binding!!
    private val args: CategoryItemsFragmentArgs by navArgs()
    private lateinit var itemsAdapter: CategoryItemsAdapter
    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        fetchCategoryItems()

        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchCategoryItems()
        }

        // Make Pepper announce the screen
        (activity as? MainActivity)?.safeSay("Here are the items in this category. You can view each item by tapping the eye icon.")
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        itemsAdapter = CategoryItemsAdapter(
            items = emptyList(),
            onViewClick = { item ->
                viewItemDetails(item)
            },
            onDeleteClick = { item ->
                showDeleteConfirmationDialog(item)
            }

        )

        binding.itemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = itemsAdapter
            setHasFixedSize(true)
        }
    }
    private fun showDeleteConfirmationDialog(item: CategoryItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${item.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteItem(item)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }
    private fun deleteItem(item: CategoryItem) {
        showLoadingState()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    deleteItemOnApi(item.id)
                }

                if (response.isSuccessful) {
                    showSuccess("Item deleted successfully")
                    fetchCategoryItems() // Refresh the list
                } else {
                    showError("Failed to delete item: ${response.code}")
                }
            } catch (e: Exception) {
                showError("Network error: ${e.message}")
            } finally {
                showContentState()
            }
        }
    }
    private suspend fun deleteItemOnApi(itemId: String): Response {
        val token = getAuthToken() ?: throw Exception("Not authenticated")

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}activities/items/$itemId")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "*/*")
            .delete()
            .build()

        return client.newCall(request).execute()
    }

    private fun setupClickListeners() {
        binding.addItemFab.setOnClickListener {
            // Navigate to create new item screen
            findNavController().navigate(
                CategoryItemsFragmentDirections.actionCategoryItemsFragmentToCreateItemFragment(
                    childId = arguments?.getString("childId") ?: "",
                    args.categoryId
                )
            )
        }

        binding.retryButton.setOnClickListener {
            fetchCategoryItems()
        }
    }

    private fun fetchCategoryItems() {
        showLoadingState()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val items = getCategoryItemsFromApi(args.categoryId)
                withContext(Dispatchers.Main) {
                    if (items.isNotEmpty()) {
                        showContentState()
                        itemsAdapter.updateItems(items)
                    } else {
                        showEmptyState()
                    }
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorState("Failed to load items: ${e.message}")
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private suspend fun getCategoryItemsFromApi(categoryId: String): List<CategoryItem> {
        val token = getAuthToken() ?: throw Exception("Not authenticated")

        val url = "${BuildConfig.BASE_URL}activities/categories/$categoryId/items"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("API request failed: ${response.code}")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response body")
        return parseCategoryItems(responseBody)
    }

    private fun parseCategoryItems(jsonString: String): List<CategoryItem> {
        val jsonArray = JSONArray(jsonString)
        return List(jsonArray.length()) { i ->
            val itemJson = jsonArray.getJSONObject(i)
            CategoryItem(
                id = itemJson.getString("id"),
                name = itemJson.getString("name"),
                categoryId = itemJson.getString("category_id"),
                difficultyLevel = itemJson.getString("difficulty_level"),
                imageBase64 = itemJson.optString("image_url", null)
            )
        }
    }

    private fun viewItemDetails(item: CategoryItem) {
        // Navigate to item details screen
//        findNavController().navigate(
//            CategoryItemsFragmentDirections.actionCategoryItemsFragmentToItemDetailsFragment(
//                item.id
//            )
//        )

        // Optional Pepper announcement
        (activity as? MainActivity)?.safeSay("Showing details for ${item.name}.")
    }

    private fun getAuthToken(): String? {
        return activity?.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            ?.getString("auth_token", null)
    }

    // State management functions
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
    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        (activity as? MainActivity)?.safeSay(message)
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        (activity as? MainActivity)?.safeSay("Error: $message")
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}



