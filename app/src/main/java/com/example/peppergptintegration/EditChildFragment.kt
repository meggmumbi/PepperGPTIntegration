package com.example.peppergptintegration

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.peppergptintegration.databinding.FragmentEditChildBinding
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
import java.text.SimpleDateFormat
import java.util.*

class EditChildFragment : Fragment() {
    private var _binding: FragmentEditChildBinding? = null
    private val binding get() = _binding!!
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()
    private lateinit var childId: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditChildBinding.inflate(inflater, container, false)
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
        setupDatePicker()
        setupClickListeners()
        loadChildData()

        // Make Pepper announce the screen
        (activity as? MainActivity)?.safeSay("Editing child profile. You can update the details here.")
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupDatePicker() {
        binding.diagnosisDateEditText.setOnClickListener {
            DatePickerUtils.showMaterialDatePicker(
                fragment = this,
                title = "Select Diagnosis Date",
                initialDate = getCurrentDateInMillis(),
                onDateSelected = { timestamp ->
                    val formattedDate = DatePickerUtils.formatDate(timestamp)
                    binding.diagnosisDateEditText.setText(formattedDate)
                }
            )
        }
    }

    private fun getCurrentDateInMillis(): Long? {
        return binding.diagnosisDateEditText.text?.toString()?.let { dateStr ->
            try {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                format.parse(dateStr)?.time
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun setupClickListeners() {
        binding.editPhotoButton.setOnClickListener {
            // Implement image picker logic here
            Toast.makeText(context, "Edit photo functionality", Toast.LENGTH_SHORT).show()
        }

        binding.updateButton.setOnClickListener {
            if (validateInputs()) {
                updateChildProfile()
            }
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun loadChildData() {
        binding.progressIndicator.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = getAuthToken() ?: throw Exception("Not authenticated")
                val child = fetchChildDetails(childId, token)
                withContext(Dispatchers.Main) {
                    populateForm(child)
                    binding.progressIndicator.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressIndicator.visibility = View.GONE
                    showError("Failed to load child data: ${e.message}")
                    Log.e("EditChild", "Error loading child", e)
                }
            }
        }
    }

    private suspend fun fetchChildDetails(childId: String, token: String): Child {
        val url = "http://10.0.2.2:8000/children/$childId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("accept", "application/json")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error details"
            throw Exception("API request failed: ${response.code} - $errorBody")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response body")
        return parseChild(responseBody)
    }

    private fun parseChild(jsonString: String): Child {
        val json = JSONObject(jsonString)
        return Child(
            id = json.getString("id"),
            name = json.getString("name"),
            age = json.getInt("age"),
            diagnosisDate = json.getString("diagnosis_date"),
            notes = json.getString("notes"),
            createdAt = json.optString("created_at", "")
        )
    }

    private fun populateForm(child: Child) {
        binding.nameEditText.setText(child.name)
        binding.ageEditText.setText(child.age.toString())
        binding.diagnosisDateEditText.setText(child.diagnosisDate)
        binding.notesEditText.setText(child.notes)
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

        return isValid
    }

    private fun updateChildProfile() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.updateButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = getAuthToken() ?: throw Exception("Not authenticated")
                val response = performUpdateChild(childId, token)
                withContext(Dispatchers.Main) {
                    handleUpdateResponse(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressIndicator.visibility = View.GONE
                    binding.updateButton.isEnabled = true
                    showError("Failed to update profile: ${e.message}")
                    Log.e("EditChild", "Error updating child", e)
                }
            }
        }
    }

    private suspend fun performUpdateChild(childId: String, token: String): Response {
        val url = "http://10.0.2.2:8000/children/$childId"

        val json = JSONObject().apply {
            put("name", binding.nameEditText.text.toString())
            put("age", binding.ageEditText.text.toString().toInt())
            put("diagnosis_date", binding.diagnosisDateEditText.text.toString())
            put("notes", binding.notesEditText.text.toString())
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .put(json.toString().toRequestBody(JSON))
            .build()

        return client.newCall(request).execute()
    }

    private fun handleUpdateResponse(response: Response) {
        binding.progressIndicator.visibility = View.GONE
        binding.updateButton.isEnabled = true

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error details"
            showError("Server error: ${response.code} - $errorBody")
            return
        }

        Toast.makeText(context, "Child profile updated successfully", Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Child Profile")
            .setMessage("Are you sure you want to delete this child profile? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteChildProfile()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteChildProfile() {
        binding.progressIndicator.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = getAuthToken() ?: throw Exception("Not authenticated")
                val response = performDeleteChild(childId, token)
                withContext(Dispatchers.Main) {
                    handleDeleteResponse(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressIndicator.visibility = View.GONE
                    showError("Failed to delete profile: ${e.message}")
                    Log.e("EditChild", "Error deleting child", e)
                }
            }
        }
    }

    private suspend fun performDeleteChild(childId: String, token: String): Response {
        val url = "http://10.0.2.2:8000/children/$childId"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("accept", "application/json")
            .delete()
            .build()

        return client.newCall(request).execute()
    }

    private fun handleDeleteResponse(response: Response) {
        binding.progressIndicator.visibility = View.GONE

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error details"
            showError("Server error: ${response.code} - $errorBody")
            return
        }

        Toast.makeText(context, "Child profile deleted successfully", Toast.LENGTH_SHORT).show()
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

    data class Child(
        val id: String,
        val name: String,
        val age: Int,
        val diagnosisDate: String,
        val notes: String,
        val createdAt: String
    )
}