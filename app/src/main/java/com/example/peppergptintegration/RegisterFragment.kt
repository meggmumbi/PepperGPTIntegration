package com.example.peppergptintegration

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.peppergptintegration.databinding.FragmentRegisterBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.registerButton.setOnClickListener {
            registerCaregiver()
        }
    }

    private fun registerCaregiver() {
        val username = binding.usernameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        if (validateInput(username, email, password)) {
            binding.progressBar.visibility = View.VISIBLE
            binding.registerButton.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = performRegistration(username, email, password)
                    handleRegistrationResponse(response, username, password)
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.registerButton.isEnabled = true
                        showError("Registration failed: ${e.message}")
                    }
                }
            }
        }
    }

    private fun validateInput(username: String, email: String, password: String): Boolean {
        var isValid = true

        if (username.isEmpty()) {
            binding.usernameEditText.error = "Username required"
            isValid = false
        }

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEditText.error = "Valid email required"
            isValid = false
        }

        if (password.length < 6) {
            binding.passwordEditText.error = "Password must be at least 6 characters"
            isValid = false
        }

        return isValid
    }

    private fun performRegistration(username: String, email: String, password: String): String {
        val json = JSONObject().apply {
            put("username", username)
            put("email", email)
            put("password", password)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}auth/register")
            .post(body)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
            response.body?.string() ?: throw Exception("Empty response")
        }
    }

    private fun handleRegistrationResponse(response: String, username: String, password: String) {
        activity?.runOnUiThread {
            binding.progressBar.visibility = View.GONE
            binding.registerButton.isEnabled = true

            try {
                val json = JSONObject(response)
                if (json.has("message")) {
                    // Navigate to login with auto-filled credentials using Safe Args
                    val directions = RegisterFragmentDirections
                        .actionRegisterFragmentToLoginFragment(username, password)
                    findNavController().navigate(directions)

                    // Make Pepper announce the success
                    (activity as? MainActivity)?.safeSay("Registration complete. Logging you in now.")
                } else {
                    showError("Registration failed")
                }
            } catch (e: Exception) {
                showError("Error parsing response")
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Log.e("RegisterFragment", message)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}