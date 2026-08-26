package com.example.peppergptintegration

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.peppergptintegration.databinding.FragmentPepperChatBinding
import com.example.peppergptintegration.databinding.ItemConversationBinding
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class PepperChatFragment : Fragment(), MainActivity.SpeechRecognitionListener {
    private var _binding: FragmentPepperChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var conversationList: MutableList<ConversationItem>
    private lateinit var conversationAdapter: ConversationAdapter
    private val openAIClient = OpenAIClient()

    // Speech recognition
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private var isListening = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPepperChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        conversationList = mutableListOf()
        conversationAdapter = ConversationAdapter(conversationList)
        MainActivity.setCurrentFragment(this)
        setupRecyclerView()
        setupListeners()
        setupSpeechRecognition()


        // Make Pepper greet the therapist
        (activity as? MainActivity)?.enableTabletReachability()
        (activity as? MainActivity)?.safeSay("Hello! I'm Pepper. Let's talk about Autism Spectrum Disorder. What would you like to discuss?")
        addMessage("Pepper", "Hello! I'm Pepper. Let's talk about Autism Spectrum Disorder. What would you like to discuss?")
    }
    override fun onSpeechRecognized(text: String) {
        binding.messageInput.setText(text)
        sendMessageToPepper(text)
    }


    private fun setupRecyclerView() {
        conversationAdapter = ConversationAdapter(conversationList)
        binding.conversationRecyclerView.apply {
            adapter = conversationAdapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupSpeechRecognition() {
        // Check if speech recognition is available
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            showError("Speech recognition is not available on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())

        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                updateListeningUI(true)
                addMessage("System", "Listening...")
            }

            override fun onBeginningOfSpeech() {
                binding.listeningStatus.text = "Speak now..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Optional: Add visual feedback for audio level
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not needed for most cases
            }

            override fun onEndOfSpeech() {
                binding.listeningStatus.text = "Processing..."
            }

            override fun onError(error: Int) {
                isListening = false
                updateListeningUI(false)
                binding.listeningStatus.text = "Tap microphone to speak"

                when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> showError("Audio recording error")
                    SpeechRecognizer.ERROR_CLIENT -> return // Usually when user cancels
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> showError("Insufficient permissions")
                    SpeechRecognizer.ERROR_NETWORK -> showError("Network error")
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> showError("Network timeout")
                    SpeechRecognizer.ERROR_NO_MATCH -> showError("No speech recognized")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> showError("Recognition service busy")
                    SpeechRecognizer.ERROR_SERVER -> showError("Server error")
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> showError("No speech input")
                    else -> showError("Unknown error: $error")
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                updateListeningUI(false)
                binding.listeningStatus.text = "Tap microphone to speak"

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    binding.messageInput.setText(recognizedText)
                    sendMessageToPepper(recognizedText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    binding.messageInput.setText(partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Not commonly used
            }
        })
    }

    private fun showError(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateListeningUI(listening: Boolean) {
        binding.listenButton.isEnabled = !listening
        binding.stopListenButton.isEnabled = listening

        if (listening) {
            binding.listenButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.asd_primary_text))
        } else {
            binding.listenButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.asd_success_text))
        }
    }

    private fun setupListeners() {
        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString()
            if (message.isNotBlank()) {
                sendMessageToPepper(message)
                binding.messageInput.text?.clear()
            }
        }

        binding.listenButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }

        binding.stopListenButton.setOnClickListener {
            stopListening()
        }
    }

    private fun startListening() {
        try {
            speechRecognizer.startListening(speechRecognizerIntent)
        } catch (e: Exception) {
            showError("Failed to start speech recognition: ${e.message}")
        }
    }

    private fun stopListening() {
        try {
            speechRecognizer.stopListening()
            isListening = false
            updateListeningUI(false)
            binding.listeningStatus.text = "Tap microphone to speak"
        } catch (e: Exception) {
            showError("Error stopping speech recognition: ${e.message}")
        }
    }

    private fun sendMessageToPepper(message: String) {
        // Add user message
        addMessage("Therapist", message)

        // Add temporary "Thinking..." message
        val thinkingPosition = conversationList.size
        conversationList.add(ConversationItem("Pepper", "Thinking..."))
        conversationAdapter.notifyItemInserted(thinkingPosition)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = openAIClient.getAIResponse(message)

                withContext(Dispatchers.Main) {
                    // Remove "Thinking..." and add actual response
                    conversationList.removeAt(thinkingPosition)
                    conversationAdapter.notifyItemRemoved(thinkingPosition)

                    conversationList.add(ConversationItem("Pepper", response))
                    conversationAdapter.notifyItemInserted(conversationList.size - 1)
                    (activity as? MainActivity)?.enableTabletReachability()
                    (activity as? MainActivity)?.safeSay(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Remove "Thinking..." and add error message
                    conversationList.removeAt(thinkingPosition)
                    conversationAdapter.notifyItemRemoved(thinkingPosition)

                    val errorMessage = "Sorry, I encountered an error. Please try again."
                    conversationList.add(ConversationItem("Pepper", errorMessage))
                    conversationAdapter.notifyItemInserted(conversationList.size - 1)
                    (activity as? MainActivity)?.enableTabletReachability()
                    (activity as? MainActivity)?.safeSay(errorMessage)
                }
            }
        }
    }

    private fun addMessage(speaker: String, message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            // Safely add to the list on main thread
            conversationList.add(ConversationItem(speaker, message))

            // Notify adapter of the insert
            conversationAdapter.notifyItemInserted(conversationList.size - 1)

            // Scroll to bottom
            binding.conversationRecyclerView.smoothScrollToPosition(conversationList.size - 1)
        }
    }

    override fun onDestroyView() {
        MainActivity.setCurrentFragment(null)
        super.onDestroyView()
        _binding = null
    }
}

data class ConversationItem(val speaker: String, val message: String)

class ConversationAdapter(private val items: List<ConversationItem>) :
    RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.speakerTextView.text = item.speaker
        holder.binding.messageTextView.text = item.message

        // Style differently based on speaker
        if (item.speaker == "Pepper") {
            holder.binding.messageCardView.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.pepper_message_bg)
            )
        } else {
            holder.binding.messageCardView.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.therapist_message_bg)
            )
        }
    }



    override fun getItemCount() = items.size
}