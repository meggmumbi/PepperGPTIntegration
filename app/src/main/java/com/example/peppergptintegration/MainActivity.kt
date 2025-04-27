package com.example.peppergptintegration

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.example.peppergptintegration.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var qiContext: QiContext
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Navigation Component
        setupNavigation()

        // Initialize WebSocket
        webSocketManager = WebSocketManager { message ->
            runOnUiThread {
                handleWebSocketMessage(message)
            }
        }

        // Register Pepper QiSDK
        QiSDK.register(this, this)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Setup ActionBar with NavController
        setupActionBarWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    // Pepper Robot Lifecycle Callbacks
    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
        webSocketManager.connect()
        safeSay("Hello! I'm ready for speech therapy sessions.")
    }

    override fun onRobotFocusLost() {
        Log.d("Pepper", "Robot focus lost")
    }

    override fun onRobotFocusRefused(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Robot unavailable: $reason", Toast.LENGTH_LONG).show()
        }
    }

    // WebSocket Management
    inner class WebSocketManager(private val messageHandler: (String) -> Unit) {
        private var webSocketClient: WebSocketClient? = null
        private var connectionAttempts = 0
        private val maxConnectionAttempts = 5

        fun connect() {
            if (connectionAttempts >= maxConnectionAttempts) {
                messageHandler("ERROR: Max connection attempts reached")
                return
            }

            try {
                val uri = URI("ws://10.0.2.2:8000/ws")
                webSocketClient = object : WebSocketClient(uri) {
                    override fun onOpen(handshakedata: ServerHandshake?) {
                        connectionAttempts = 0
                        messageHandler("CONNECTED")
                        Log.d("WebSocket", "Connection opened")
                    }

                    override fun onMessage(message: String?) {
                        message?.let {
                            Log.d("WebSocket", "Message received: $it")
                            messageHandler(it)
                        }
                    }

                    override fun onClose(code: Int, reason: String?, remote: Boolean) {
                        Log.d("WebSocket", "Connection closed: $reason")
                        messageHandler("DISCONNECTED: $reason")
                        attemptReconnect()
                    }

                    override fun onError(ex: Exception?) {
                        Log.e("WebSocket", "Error: ${ex?.message}")
                        messageHandler("ERROR: ${ex?.message}")
                        attemptReconnect()
                    }
                }
                connectionAttempts++
                webSocketClient?.connect()
            } catch (e: Exception) {
                Log.e("WebSocket", "Connection error", e)
                messageHandler("ERROR: ${e.message}")
            }
        }

        private fun attemptReconnect() {
            activityScope.launch {
                kotlinx.coroutines.delay(3000)
                connect()
            }
        }

        fun sendMessage(message: String) {
            try {
                if (webSocketClient?.isOpen == true) {
                    webSocketClient?.send(message)
                } else {
                    Log.e("WebSocket", "Cannot send message - connection not open")
                }
            } catch (e: Exception) {
                Log.e("WebSocket", "Send message error", e)
            }
        }

        fun disconnect() {
            try {
                webSocketClient?.close()
            } catch (e: Exception) {
                Log.e("WebSocket", "Disconnection error", e)
            }
        }

        fun isConnected(): Boolean {
            return webSocketClient?.isOpen == true
        }
    }

    private fun handleWebSocketMessage(message: String) {
        when {
            message == "CONNECTED" -> {
                safeSay("Connected to therapy server")
            }
            message.startsWith("DISCONNECTED") -> {
                val reason = message.substringAfter("DISCONNECTED: ")
                safeSay("Lost connection to server: ${reason.take(20)}")
            }
            message.startsWith("ERROR") -> {
                val error = message.substringAfter("ERROR: ")
                safeSay("Network error occurred: ${error.take(20)}")
            }
            message.startsWith("{") -> handleJsonMessage(message)
            else -> Log.d("WebSocket", "Unknown message: $message")
        }
    }

    private fun handleJsonMessage(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            when (json.getString("type")) {
                "session_initialized" -> {
                    val sessionId = json.getString("session_id")
                    safeSay("New therapy session ready. Session ID ${sessionId.take(8)}")
                }
                "therapy_update" -> {
                    val childName = json.optString("child_name", "")
                    val progress = json.optString("progress", "")
                    safeSay("Update for $childName: $progress")
                }
            }
        } catch (e: Exception) {
            Log.e("JSON", "Parse error", e)
        }
    }

    fun safeSay(text: String, maxRetries: Int = 3) {
        if (!::qiContext.isInitialized) return

        activityScope.launch {
            var retryCount = 0
            while (retryCount < maxRetries) {
                try {
                    withContext(Dispatchers.IO) {
                        SayBuilder.with(qiContext)
                            .withText(text)
                            .build()
                            .run()
                    }
                    break
                } catch (e: Exception) {
                    retryCount++
                    if (retryCount >= maxRetries) {
                        Log.e("Pepper", "Final speech attempt failed", e)
                    } else {
                        delay(1000)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        activityScope.coroutineContext.cancel()
        webSocketManager.disconnect()
        QiSDK.unregister(this, this)
        super.onDestroy()
    }
}