package com.example.peppergptintegration

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.peppergptintegration.databinding.FragmentLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val args: LoginFragmentArgs by navArgs()
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Auto-fill credentials if coming from registration
        args.username.takeIf { it.isNotEmpty() }?.let { username ->
            binding.usernameEditText.setText(username)
            args.password.takeIf { it.isNotEmpty() }?.let { password ->
                binding.passwordEditText.setText(password)
                // Make Pepper announce auto-login attempt
                (activity as? MainActivity)?.safeSay("Welcome $username! Attempting to log you in.")
                attemptLogin(username, password)
            }
        }

        setupUi()
    }

    private fun setupUi() {
        binding.loginButton.setOnClickListener {
            val username = binding.usernameEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (validateInput(username, password)) {
                attemptLogin(username, password)
            }
        }

        binding.registerLinkText.setOnClickListener {
            findNavController().navigate(
                LoginFragmentDirections.actionLoginFragmentToRegisterFragment()
            )
        }
    }

    private fun validateInput(username: String, password: String): Boolean {
        var isValid = true

        if (username.isEmpty()) {
            binding.usernameEditText.error = "Username required"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.passwordEditText.error = "Password required"
            isValid = false
        } else if (password.length < 8) {
            binding.passwordEditText.error = "Password must be at least 8 characters"
            isValid = false
        }

        return isValid
    }

    private fun attemptLogin(username: String, password: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.loginButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tokenResponse = performLogin(username, password)
                handleLoginResponse(tokenResponse)
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true
                    showError("Login failed: ${e.message}")
                    Log.e("Login", "Error: ${e.message}", e)
                }
            }
        }
    }

    private fun performLogin(username: String, password: String): TokenResponse {
        val url = "http://10.0.2.2:8000/auth/login?username=$username&password=$password"

        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody(JSON))  // Empty body as per your curl example
            .addHeader("accept", "application/json")  // Required header
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        return parseTokenResponse(responseBody)
    }

    private fun parseTokenResponse(jsonString: String): TokenResponse {
        val json = JSONObject(jsonString)
        return TokenResponse(
            accessToken = json.getString("access_token"),
            tokenType = json.getString("token_type")
        )
    }

    private fun handleLoginResponse(tokenResponse: TokenResponse) {
        activity?.runOnUiThread {
            binding.progressBar.visibility = View.GONE
            binding.loginButton.isEnabled = true

            saveAuthToken(tokenResponse.accessToken)

            // Make Pepper announce success
            (activity as? MainActivity)?.safeSay("Login successful! Welcome back.")

            // Navigate to main screen
            findNavController().navigate(
                LoginFragmentDirections.actionLoginFragmentToChildListFragment()
            )
        }
    }

    private fun saveAuthToken(token: String) {
        // Use the same preferences as WebSocketManager
        activity?.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)?.edit()?.apply {
            putString("auth_token", token)
            putString("token_type", "bearer")
            apply()
        }
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Log.e("LoginFragment", message)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Data class to hold token response
    data class TokenResponse(
        val accessToken: String,
        val tokenType: String
    )
}