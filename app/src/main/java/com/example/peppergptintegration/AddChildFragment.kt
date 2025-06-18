package com.example.peppergptintegration

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.peppergptintegration.databinding.FragmentAddChildBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

import android.view.LayoutInflater
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*


class AddChildFragment : Fragment() {
    private var _binding: FragmentAddChildBinding? = null
    private val binding get() = _binding!!
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()
    private var categories = listOf<Category>()
    private var selectedCategoryIds = mutableListOf<String>()

    data class Category(
        val id: String,
        val name: String,
        val difficultyLevel: String = ""
    )


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddChildBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        setupToolbar()
        setupDatePicker()
        setupClickListeners()
        fetchCategories()

        // Make Pepper announce the screen
        (activity as? MainActivity)?.safeSay("Let's create a new child profile. Please provide the child's details.")
    }

//    private fun setupToolbar() {
//        binding.toolbar.setNavigationOnClickListener {
//            findNavController().navigateUp()
//        }
//    }

    private fun setupDatePicker() {
        binding.diagnosisDateEditText.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        DatePickerUtils.showMaterialDatePicker(
            fragment = this,
            title = "Select Diagnosis Date",
            initialDate = null, // or set initial date if needed
            onDateSelected = { timestamp ->
                val formattedDate = DatePickerUtils.formatDate(timestamp)
                binding.diagnosisDateEditText.setText(formattedDate)
            }
        )
    }

    private fun formatDate(year: Int, month: Int, day: Int): String {
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day)
    }

    private fun setupClickListeners() {
        binding.diagnosisDateEditText.setOnClickListener {
            showDatePicker()
        }

        binding.addPhotoButton.setOnClickListener {
            // Implement image picker logic here
            Toast.makeText(context, "Add photo functionality", Toast.LENGTH_SHORT).show()
        }

        binding.submitButton.setOnClickListener {
            if (validateInputs()) {
                createChildProfile()
            }
        }
        binding.interestsDropdown.setOnClickListener {
            showMultiSelectDialog()
        }
    }

    private fun fetchCategories() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = getAuthToken() ?: throw Exception("Not authenticated")
                val url = "${BuildConfig.BASE_URL}activities/categories/"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonArray = JSONArray(responseBody)

                    categories = (0 until jsonArray.length()).map { i ->
                        val category = jsonArray.getJSONObject(i)
                        Category(
                            id = category.getString("id"),
                            name = category.getString("name"),
                            difficultyLevel = category.getString("difficulty_level"),

                        )
                    }

                    withContext(Dispatchers.Main) {
                        updateInterestsDropdown()
                    }
                }
            } catch (e: Exception) {
                Log.e("AddChild", "Error fetching categories", e)
            }
        }
    }

    private fun updateInterestsDropdown() {
        if (selectedCategoryIds.isEmpty()) {
            binding.interestsDropdown.setText("Select areas of interest")
        } else {
            val selectedNames = categories
                .filter { selectedCategoryIds.contains(it.id) }
                .joinToString(", ") { it.name }
            binding.interestsDropdown.setText(selectedNames)
        }
    }

    private fun showMultiSelectDialog() {
        val items = categories.map {
            "${it.name} (${it.difficultyLevel})"
        }.toTypedArray()

        val checkedItems = categories.map {
            selectedCategoryIds.contains(it.id)
        }.toBooleanArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Areas of Interest")
            .setMultiChoiceItems(items, checkedItems) { dialog, which, isChecked ->
                val category = categories[which]
                if (isChecked) {
                    selectedCategoryIds.add(category.id)
                } else {
                    selectedCategoryIds.remove(category.id)
                }
                updateInterestsDropdown()
            }
            .setPositiveButton("OK") { dialog, which ->
                updateInterestsDropdown()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun validateInputs(): Boolean {
        var isValid = true

        if (binding.nameEditText.text.isNullOrEmpty()) {
            binding.nameInputLayout.error = "Name is required"
            isValid = false
        } else {
            binding.nameInputLayout.error = null
        }

        if (binding.ageEditText.text.isNullOrEmpty()) {
            binding.ageInputLayout.error = "Age is required"
            isValid = false
        } else {
            binding.ageInputLayout.error = null
        }

        if (binding.diagnosisDateEditText.text.isNullOrEmpty()) {
            binding.diagnosisDateInputLayout.error = "Diagnosis date is required"
            isValid = false
        } else {
            binding.diagnosisDateInputLayout.error = null
        }

        if (binding.goalsEditText.text.isNullOrEmpty()) {
            binding.goalsInputLayout.error = "Therapy goals are required"
            isValid = false
        } else {
            binding.goalsInputLayout.error = null
        }

        return isValid
    }

    private fun createChildProfile() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.submitButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = getAuthToken() ?: throw Exception("Not authenticated")
                val response = performCreateChild(token)
                withContext(Dispatchers.Main) {
                    handleCreateResponse(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressIndicator.visibility = View.GONE
                    binding.submitButton.isEnabled = true
                    showError("Failed to create profile: ${e.message}")
                    Log.e("AddChild", "Error creating child", e)
                }
            }
        }
    }

    private suspend fun performCreateChild(token: String): Response {
        val url = "${BuildConfig.BASE_URL}children/"

        val json = JSONObject().apply {
            put("name", binding.nameEditText.text.toString())
            put("age", binding.ageEditText.text.toString().toInt())
            put("diagnosis_date", binding.diagnosisDateEditText.text.toString())
            put("notes", binding.notesEditText.text.toString())
            put("therapy_goals", binding.goalsEditText.text.toString())
            put("areas_of_interest_ids", JSONArray(selectedCategoryIds))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody(JSON))
            .build()

        return client.newCall(request).execute()
    }

    private fun handleCreateResponse(response: Response) {
        binding.progressIndicator.visibility = View.GONE
        binding.submitButton.isEnabled = true

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error details"
            showError("Server error: ${response.code} - $errorBody")
            return
        }

        Toast.makeText(context, "Child profile created successfully", Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    private fun getAuthToken(): String? {
        return activity?.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            ?.getString("auth_token", null)
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}