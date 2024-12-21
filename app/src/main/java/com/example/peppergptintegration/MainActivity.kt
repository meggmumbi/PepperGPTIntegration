package com.example.peppergptintegration

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.conversation.Chat
import com.aldebaran.qi.sdk.builder.*
import com.aldebaran.qi.sdk.design.activity.RobotActivity

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    private lateinit var asdList: List<ASDType>
    private var qiContext: QiContext? = null
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        QiSDK.register(this, this)
        setContentView(R.layout.activity_main)

        // Initialize ASD characteristics list
        asdList = listOf(
            ASDType(
                title = "Difficulty with Social Communication",
                description = "Struggling to start or sustain a conversation can be a common experience.",
                imageResId = R.drawable.ic_sensory
            ),
            ASDType(
                title = "Delayed Speech or Language Skills",
                description = "Individuals might express themselves differently through nonverbal cues.",
                imageResId = R.drawable.ic_communication
            ),
            ASDType(
                title = "Lack Of Social Interaction",
                description = "Some may have a deep interest in specific subjects or routines.",
                imageResId = R.drawable.ic_sensory
            ),
            ASDType(
                title = "Repetitive Behaviors",
                description = "Repetitive behaviors like motor movements, speech, or routines",
                imageResId = R.drawable.ic_sensory
            ),
            ASDType(
                title = "Sensory Sensitivities",
                description = "Understanding social rules and customs might be difficult.",
                imageResId = R.drawable.ic_sensory
            ),
            ASDType(
                title = "Difficulty with Transitions",
                description = "Understanding social rules and customs might be difficult.",
                imageResId = R.drawable.ic_sensory
            ),
            ASDType(
                title = "Limited Pretend Play",
                description = "Pretend play may be limited or absent.",
                imageResId = R.drawable.ic_kidrwebpg
            )
        )

        // Setup RecyclerView for tablet interaction
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2) // 2 columns
        recyclerView.adapter = ASDAdapter(asdList) { selectedAsdType ->
            handleASDSelection(selectedAsdType)
        }
    }

    override fun onDestroy() {
        QiSDK.unregister(this, this) // Unregister lifecycle callbacks
        super.onDestroy()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.d("PepperDebug", "Robot focus gained.")
        this.qiContext = qiContext

        // Pepper greets the user
        val sayHello = SayBuilder.with(qiContext)
            .withText("Hello! Select an ASD characteristic or ask me about them.")
            .build()
        sayHello.async().run()

        // Load topic
        val topic = TopicBuilder.with(qiContext)
            .withResource(R.raw.asd_topic) // Ensure the .top file exists in res/raw
            .build()

        // Create QiChatbot
        val qiChatbot = QiChatbotBuilder.with(qiContext)
            .withTopic(topic)
            .build()

        // Create Chat
        chat = ChatBuilder.with(qiContext)
            .withChatbot(qiChatbot)
            .build()

        // React to bookmarks
        val bookmarks = topic.bookmarks
        bookmarks.forEach { name, bookmark ->
            val status = qiChatbot.bookmarkStatus(bookmark)
            status.addOnReachedListener {
                Log.d("PepperDebug", "Bookmark reached: $name")
                val selectedAsdType = asdList.firstOrNull { it.title.contains(name, true) }
                if (selectedAsdType != null) {
                    handleASDSelection(selectedAsdType)
                }
            }
        }

        // Start chat
        chatFuture = chat?.async()?.run()
    }

    override fun onRobotFocusLost() {
        Log.d("PepperDebug", "Robot focus lost.")
        qiContext = null
        stopChat()
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.e("PepperDebug", "Robot focus refused: $reason")
    }

    private fun stopChat() {
        chatFuture?.requestCancellation()
        chatFuture = null
    }

    private fun handleASDSelection(selectedAsdType: ASDType) {
        val context = qiContext
        if (context == null) {
            Log.e("PepperDebug", "QiContext is null! Cannot execute Say or Animate actions.")
            return
        }

        // Display the selection on the tablet
        runOnUiThread {
            Toast.makeText(this, "Selected: ${selectedAsdType.title}", Toast.LENGTH_LONG).show()
        }

        // Create the Say action
        val sayFuture = SayBuilder.with(context)
            .withText("You selected: ${selectedAsdType.title}. ${selectedAsdType.description}")
            .buildAsync()

        // Run the Say action and chain with Animate
        sayFuture.andThenCompose { say ->
            say.async().run() // Execute the Say action
        }.andThenCompose {
            // Create the animation
            AnimationBuilder.with(context)
                .withResources(R.raw.waving_right_b001) // Replace with a valid animation file
                .buildAsync()
        }.andThenCompose { animation ->
            // Build the Animate action
            AnimateBuilder.with(context)
                .withAnimation(animation)
                .buildAsync()
        }.andThenCompose { animate ->
            animate.async().run() // Execute the Animate action
        }.thenConsume {
            // Handle the final result
            if (it.hasError()) {
                Log.e("PepperDebug", "Error during actions: ${it.errorMessage}")
            } else {
                Log.d("PepperDebug", "Actions completed successfully.")
            }
        }
    }
}
