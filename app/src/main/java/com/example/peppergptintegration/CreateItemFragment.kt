package com.example.peppergptintegration

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.peppergptintegration.R
import com.example.peppergptintegration.databinding.FragmentCreateItemBinding
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

class CreateItemFragment : Fragment() {

    private var _binding: FragmentCreateItemBinding? = null
    private val binding get() = _binding!!
    private val args: CreateItemFragmentArgs by navArgs()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Increased timeout
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val difficultyLevels = listOf("Easy", "Medium", "Advanced")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupDifficultyDropdown()
        setupCreateButton()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (!binding.createButton.isEnabled) {
                showSnackbar("Please wait while we create your item")
                return@setNavigationOnClickListener
            }
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
        binding.difficultyAutoCompleteTextView.setOnItemClickListener { _, _, _, _ ->
            binding.difficultyInputLayout.error = null
        }
    }

    private fun setupCreateButton() {
        binding.createButton.setOnClickListener {
            if (validateInputs()) {
                createItem()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        if (binding.nameEditText.text.isNullOrBlank()) {
            binding.nameInputLayout.error = "Item name is required"
            isValid = false
        } else {
            binding.nameInputLayout.error = null
        }

        if (binding.difficultyAutoCompleteTextView.text.isNullOrBlank()) {
            binding.difficultyInputLayout.error = "Please select difficulty level"
            isValid = false
        } else {
            binding.difficultyInputLayout.error = null
        }

        return isValid
    }

    private fun createItem() {
        val name = binding.nameEditText.text.toString().trim()
        val difficultyLevel = binding.difficultyAutoCompleteTextView.text.toString().trim().lowercase()
        val generateImage = binding.generateImageCheckBox.isChecked

        disableUI()
        showLoading(true)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    createItemOnApi(name, difficultyLevel, generateImage)
                }

                if (response.isSuccessful) {
                    showSuccessAndNavigateBack()
                } else {
                    val errorBody = response.body?.string() ?: "No error message"
                    showError("Failed to create item: ${response.code} - $errorBody")
                }
            } catch (e: Exception) {
                showError("Network error: ${e.message}")
            } finally {
                enableUI()
                showLoading(false)
            }
        }
    }

    private suspend fun createItemOnApi(
        name: String,
        difficultyLevel: String,
        generateImage: Boolean
    ): Response {
        val token = getAuthToken() ?: throw Exception("Not authenticated")

        val json = JSONObject().apply {
            put("name", name)
            put("category_id", args.categoryId)
            put("difficulty_level", difficultyLevel)
            put("generate_image", generateImage)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        return client.newCall(
            Request.Builder()
                .url("${BuildConfig.BASE_URL}activities/items/")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()
        ).execute()
    }

    private fun showSuccessAndNavigateBack() {
        // Show success message on UI thread
        activity?.runOnUiThread {
            showSnackbar("Item created successfully")
            (activity as? MainActivity)?.safeSay("Item created successfully.")

            // Navigate back after a short delay to ensure message is seen
            binding.root.postDelayed({
                findNavController().navigateUp()
            }, 1500)
        }
    }

    private fun showError(message: String) {
        activity?.runOnUiThread {
            showSnackbar(message)
            (activity as? MainActivity)?.safeSay("Error: $message")
        }
    }

    private fun showLoading(show: Boolean) {
        activity?.runOnUiThread {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
            binding.createButton.text = if (show) "Creating..." else "Create Item"
        }
    }

    private fun disableUI() {
        activity?.runOnUiThread {
            binding.nameEditText.isEnabled = false
            binding.difficultyAutoCompleteTextView.isEnabled = false
            binding.generateImageCheckBox.isEnabled = false
            binding.createButton.isEnabled = false
            binding.toolbar.navigationIcon = null
        }
    }

    private fun enableUI() {
        activity?.runOnUiThread {
            binding.nameEditText.isEnabled = true
            binding.difficultyAutoCompleteTextView.isEnabled = true
            binding.generateImageCheckBox.isEnabled = true
            binding.createButton.isEnabled = true
            binding.toolbar.setNavigationIcon(R.drawable.ic_back)
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
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