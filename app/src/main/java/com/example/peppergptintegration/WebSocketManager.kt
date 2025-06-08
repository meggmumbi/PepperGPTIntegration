import android.content.Context
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class WebSocketManager(
    private val context: Context,
    private val messageHandler: (String) -> Unit
) {
    private var webSocketClient: WebSocketClient? = null
    private var connectionAttempts = 0
    private val maxConnectionAttempts = 5
    private val sharedPref = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun connect() {
        if (connectionAttempts >= maxConnectionAttempts) {
            messageHandler("ERROR: Max connection attempts reached")
            return
        }

        try {
            val token = sharedPref.getString("auth_token", null)
            if (token.isNullOrEmpty()) {
                messageHandler("ERROR: Not authenticated")
                return
            }

            val uri = URI("ws://192.168.100.26:8000/ws")
            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    connectionAttempts = 0
                    Log.d("WebSocket", "Connection opened")

                    // Send authentication message immediately after connection
                    val authMessage = JSONObject().apply {
                        put("type", "authenticate")
                        put("token", token)
                    }.toString()

                    send(authMessage)
                    messageHandler("CONNECTED")
                }

                override fun onMessage(message: String?) {
                    message?.let {
                        Log.d("WebSocket", "Message received: $it")
                        handleIncomingMessage(it)
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

            // Add authentication header
            val headers = HashMap<String, String>()
            headers["Authorization"] = "Bearer $token"
            webSocketClient?.connect()

        } catch (e: Exception) {
            Log.e("WebSocket", "Connection error", e)
            messageHandler("ERROR: ${e.message}")
        }
    }

    private fun handleIncomingMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.getString("type")) {
                "auth_response" -> {
                    if (!json.getBoolean("authenticated")) {
                        if (json.optString("reason") == "token_expired") {
                            messageHandler("ERROR: Token expired")
                            // Trigger token refresh flow
                        } else {
                            messageHandler("ERROR: Authentication failed")
                        }
                    } else {
                        messageHandler("AUTHENTICATED")
                    }
                }
                "error" -> {
                    if (json.optString("code") == "invalid_token") {
                        messageHandler("ERROR: Token invalid")
                    }
                }
                else -> messageHandler(message)
            }
        } catch (e: Exception) {
            messageHandler(message)
        }
    }

    private fun attemptReconnect() {
        Thread {
            Thread.sleep(3000)
            connect()
        }.start()
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