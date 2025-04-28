package com.example.peppergptintegration

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.peppergptintegration.databinding.FragmentCreateCategoryBinding
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

class CreateCategoryFragment : Fragment() {

    private var _binding: FragmentCreateCategoryBinding? = null
    private val binding get() = _binding!!
    private val client = OkHttpClient()
    private val difficultyLevels = listOf("Easy", "Medium", "Advanced")
    private lateinit var childId: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getString("childId") ?: run {
            showError("Child ID not provided")
            findNavController().navigateUp()
            return
        }

        setupToolbar()
        setupDifficultyDropdown()
        setupCreateButton()

        // Make Pepper announce the screen
        (activity as? MainActivity)?.safeSay("You are creating a new therapy category. Please provide the name, description, and select difficulty level.")
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupDifficultyDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_menu_item,
            difficultyLevels
        )

        binding.difficultyAutoCompleteTextView.setAdapter(adapter)

        // Customize dropdown appearance
        binding.difficultyAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            binding.difficultyInputLayout.error = null
        }
    }

    private fun setupCreateButton() {
        binding.createButton.setOnClickListener {
            if (validateInputs()) {
                createCategory()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Validate name
        if (binding.nameEditText.text.isNullOrBlank()) {
            binding.nameInputLayout.error = "Category name is required"
            isValid = false
        } else {
            binding.nameInputLayout.error = null
        }

        // Validate description
        if (binding.descriptionEditText.text.isNullOrBlank()) {
            binding.descriptionInputLayout.error = "Description is required"
            isValid = false
        } else {
            binding.descriptionInputLayout.error = null
        }

        // Validate difficulty level
        if (binding.difficultyAutoCompleteTextView.text.isNullOrBlank()) {
            binding.difficultyInputLayout.error = "Please select difficulty level"
            isValid = false
        } else {
            binding.difficultyInputLayout.error = null
        }

        return isValid
    }

    private fun createCategory() {
        val name = binding.nameEditText.text.toString().trim()
        val description = binding.descriptionEditText.text.toString().trim()
        val difficultyLevel = binding.difficultyAutoCompleteTextView.text.toString().trim()

        showLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = createCategoryOnApi(name, description, difficultyLevel)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    if (response.isSuccessful) {
                        showSuccess("Category created successfully")
                        findNavController().navigateUp()
                    } else {
                        showError("Failed to create category: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showError("Error creating category: ${e.message}")
                }
            }
        }
    }

    private suspend fun createCategoryOnApi(
        name: String,
        description: String,
        difficultyLevel: String
    ): Response {
        val token = getAuthToken() ?: throw Exception("Not authenticated")

        val json = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("difficulty_level", difficultyLevel.lowercase())
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("http://10.0.2.2:8000/activities/categories/")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        return client.newCall(request).execute()
    }

    private fun getAuthToken(): String? {
        return activity?.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            ?.getString("auth_token", null)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.createButton.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        (activity as? MainActivity)?.safeSay("Category created successfully.")
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        (activity as? MainActivity)?.safeSay("Error creating category. Please try again.")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}