package com.example.peppergptintegration

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RawRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.EnforceTabletReachability
import com.aldebaran.qi.sdk.`object`.conversation.ListenResult
import com.aldebaran.qi.sdk.`object`.conversation.Phrase
import com.aldebaran.qi.sdk.builder.*
import com.example.peppergptintegration.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.start
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.util.*

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var qiContext: QiContext
    private var listenFuture: Future<ListenResult>? = null
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognitionListener: RecognitionListener
    private var isListening = false
    private var isListeningContinuously = false
    // Store the EnforceTabletReachability action.
    private var enforceTabletReachability: EnforceTabletReachability? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val speechQueue = mutableListOf<String>()
    private var isSpeaking = false
    fun isPepperSpeaking(): Boolean = isSpeaking

    // Visual Attention Tracking
    private var gazeTrackingManager: GazeTrackingManager? = null


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

        initializeSpeechRecognizer()

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
        gazeTrackingManager = GazeTrackingManager(qiContext)
        enforceTabletReachability = EnforceTabletReachabilityBuilder.with(qiContext).build()
        safeSay("Oh Hello! I'm ready for speech therapy sessions.")
    }

    override fun onRobotFocusLost() {
        Log.d("Pepper", "Robot focus lost")
        gazeTrackingManager?.cleanup()
        gazeTrackingManager = null
        stopContinuousPepperListening()
        enforceTabletReachability?.let {
            it.async().run()
            enforceTabletReachability = null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.child_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.action_logout -> {

                // Navigate back to login and clear stack
                navController.navigate(R.id.loginFragment)

                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }



    fun enableTabletReachability() {
        enforceTabletReachability?.async()?.run()
    }

    fun disableTabletReachability() {
        enforceTabletReachability?.let {
            it.async().run()
            enforceTabletReachability = null
        }
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
                val uri = URI("ws://192.168.100.26:8000/ws")
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
    // Add these helper methods:
    fun runPepperAnimation(@RawRes animationRes: Int, duration: Long, callback: () -> Unit) {
        if (!::qiContext.isInitialized) return
        activityScope.launch {
            try {
                val animation = AnimationBuilder.with(qiContext)
                    .withResources(animationRes)
                    .buildAsync()
                    .get()

                AnimateBuilder.with(qiContext)
                    .withAnimation(animation)
                    .buildAsync()
                    .get()
                    .run()

                delay(duration)
                callback()
            } catch (e: Exception) {
                Log.e("Pepper", "Animation failed", e)
                callback()
            }
        }
    }

    fun speakWithPepper(text: String, callback: () -> Unit = {}) {
        if (!::qiContext.isInitialized) return
        activityScope.launch {
            try {
                SayBuilder.with(qiContext)
                    .withText(text)
                    .buildAsync()
                    .get()
                    .run()
                callback()
            } catch (e: Exception) {
                Log.e("Pepper", "Speech failed", e)
                callback()
            }
        }
    }

    fun startContinuousPepperListening(callback: (String) -> Unit) {
        if (!::qiContext.isInitialized) {
            Toast.makeText(this, "Pepper is not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        isListeningContinuously = true
        listenOnce(callback)
    }

    private fun listenOnce(callback: (String) -> Unit) {
        if (!isListeningContinuously) return

        // Build the PhraseSet asynchronously to avoid NetworkOnMainThreadException
        PhraseSetBuilder.with(qiContext)
            .withTexts("") // Free speech mode
            .buildAsync()
            .andThenConsume { freeSpeechPhraseSet ->
                val listen = ListenBuilder.with(qiContext)
                    .withPhraseSet(freeSpeechPhraseSet)
                    .build()

                listenFuture = listen.async().run()

                listenFuture?.andThenConsume { result ->
                    val heardPhrase = result?.heardPhrase?.text ?: ""
                    runOnUiThread {
                        if (heardPhrase.isNotBlank()) {
                            callback(heardPhrase)
                        }
                        // Keep listening
                        listenOnce(callback)
                    }
                }
            }
    }

    fun stopContinuousPepperListening() {
        isListeningContinuously = false
        listenFuture?.requestCancellation()
    }


    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Listening...", Toast.LENGTH_SHORT).show()
                    // You could also update your UI here (e.g., show a listening animation)
                }
            }

            override fun onBeginningOfSpeech() {
                // Speech has started
            }

            override fun onRmsChanged(rmsdB: Float) {
                // You could use this for a visual volume indicator
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not typically used
            }

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No recognition match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return

                runOnUiThread {
                    // Pass the recognized text back to the fragment
                    currentFragment?.onSpeechRecognized(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Handle partial results if needed
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Not typically used
            }
        }
    }



    fun startPepperListening(callback: (String) -> Unit) {
        if (!::qiContext.isInitialized) {
            Toast.makeText(this, "Pepper is not ready yet", Toast.LENGTH_SHORT).show()
            return
        }

        // Use "free speech" mode by giving Pepper an empty PhraseSet
        val freeSpeechPhraseSet = PhraseSetBuilder.with(qiContext)
            .withTexts(("")) // No restriction
            .build()

        // Create Listen action in free-speech mode
        val listen = ListenBuilder.with(qiContext)
            .withPhraseSet(freeSpeechPhraseSet)
            .build()

        listenFuture = listen.async().run()

        listenFuture?.andThenConsume { result ->
            val heardPhrase = result?.heardPhrase?.text ?: ""
            runOnUiThread {
                if (heardPhrase.isNotBlank()) {
                    callback(heardPhrase)
                } else {
                    Toast.makeText(this, "I didn’t catch that. Could you repeat?", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun stopPepperListening() {
        listenFuture?.requestCancellation()
    }


    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Listening...", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission needed"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        else -> "Unknown error"
                    }
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: return

                    runOnUiThread {
                        currentFragment?.onSpeechRecognized(text)
                        currentSpeechCallback?.invoke(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening(callback: (String) -> Unit) {
        if (!this::speechRecognizer.isInitialized) {
            Toast.makeText(this, "Speech recognizer not ready", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
            currentSpeechCallback = callback
            return
        }

        if (isListening) {
            stopListening()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Pepper...")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer.startListening(intent)
            currentSpeechCallback = callback
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopListening() {
        if (isListening && this::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
            isListening = false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    currentSpeechCallback?.let { startListening(it) }
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Gaze Tracking Delegation Methods
    fun startGazeTracking() {
        // Check if gaze tracking is available
        if (gazeTrackingManager == null) {
            Log.w("MainActivity", "Gaze tracking not available - QiContext not ready")
            return
        }
        gazeTrackingManager?.startGazeTracking()
    }

    fun stopGazeTracking(): GazeTrackingManager.GazeTrackingResult? {
        return gazeTrackingManager?.stopGazeTracking()
    }

    fun isGazeTracking(): Boolean {
        return gazeTrackingManager?.isTracking() ?: false
    }

    fun getCurrentGazeMetrics(): GazeTrackingManager.GazeTrackingResult? {
        return gazeTrackingManager?.getCurrentMetrics()
    }



    override fun onDestroy() {
        activityScope.coroutineContext.cancel()
        webSocketManager.disconnect()
        gazeTrackingManager?.cleanup()
        QiSDK.unregister(this, this)
        stopContinuousPepperListening()
        if (this::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        super.onDestroy()
    }
    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 101
        private var currentSpeechCallback: ((String) -> Unit)? = null
        private var currentFragment: SpeechRecognitionListener? = null

        fun setCurrentFragment(fragment: Fragment?) {
            currentFragment = fragment as? SpeechRecognitionListener
        }
    }

    fun queueSpeech(text: String) {
        speechQueue.add(text)
        processSpeechQueue()
    }

    private fun processSpeechQueue() {
        if (!isSpeaking && speechQueue.isNotEmpty()) {
            val nextSpeech = speechQueue.removeAt(0)
            isSpeaking = true
            speakWithPepper(nextSpeech) {
                isSpeaking = false
                processSpeechQueue()
            }
        }
    }


    interface SpeechRecognitionListener {
        fun onSpeechRecognized(text: String)
    }
}