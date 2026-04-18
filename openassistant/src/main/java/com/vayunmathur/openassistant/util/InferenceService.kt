package com.vayunmathur.openassistant.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.ai.edge.litertlm.*
import com.vayunmathur.library.util.DatabaseViewModel
import com.vayunmathur.library.util.SecureResultReceiver
import com.vayunmathur.library.util.buildDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import java.io.File
import kotlin.time.Clock
import com.vayunmathur.openassistant.data.AppDatabase
import com.vayunmathur.openassistant.data.Conversation
import com.vayunmathur.openassistant.data.Message

class InferenceService : Service() {

    companion object {
        var newTitle: String? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Keeping the engine and conversation warm in memory
    private var engine: Engine? = null
    private var currentConversation: com.google.ai.edge.litertlm.Conversation? = null
    private var activeIntentToolSet: IntentToolSet? = null
    private var currentConversationId: Long = -1L
    private var resultReceiver: ResultReceiver? = null

    // Simple lock to prevent concurrent generations from crashing the NPU/GPU
    private var isGenerating = false

    val db by lazy { buildDatabase<AppDatabase>() }
    val viewModel by lazy { DatabaseViewModel(db, Conversation::class to db.conversationDao(), Message::class to db.messageDao()) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("InferenceService", "onStartCommand received intent")
        intent?.setExtrasClassLoader(SecureResultReceiver::class.java.classLoader)
        val conversationId = intent?.getLongExtra("conversation_id", -1L) ?: -1L
        val userText = intent?.getStringExtra("user_text") ?: ""

        val imageUris = intent?.getParcelableArrayListExtra<Uri>("image_uris")
        val imagePathsFromUris = imageUris?.map { uri ->
            copyUriToFile(this, uri).absolutePath
        }?.toTypedArray() ?: emptyArray()

        val imagePaths = (intent?.getStringArrayExtra("image_paths") ?: emptyArray()) + imagePathsFromUris

        val audioPath = intent?.getStringExtra("audio_path")
        val schema = intent?.getStringExtra("schema")
        val receiver = intent?.getParcelableExtra<ResultReceiver>("RECEIVER")
        
        if (receiver != null && schema != null) {
            Log.i("InferenceService", "Starting Intent Inference with schema: ${schema.take(50)}...")
            this.resultReceiver = receiver
            processIntentInference(userText, imagePaths, schema)
        } else if (conversationId != -1L) {
            activeIntentToolSet = null
            Log.d("InferenceService", "Starting standard inference for conversation: $conversationId")
            processInference(conversationId, userText, imagePaths, audioPath)
        } else {
            Log.w("InferenceService", "Received intent with no valid target (receiver/schema or conversationId)")
        }

        // START_STICKY ensures the service stays alive to keep the model in RAM
        return START_STICKY
    }

    private fun startForegroundTask() {
        val channelId = "inference_service"
        val channel = NotificationChannel(
            channelId,
            "Inference Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OpenAssistant")
            .setContentText("Processing AI inference...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun stopForegroundTask() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun processIntentInference(
        userText: String,
        imagePaths: Array<String>,
        schema: String
    ) {
        if (isGenerating) {
            Log.w("InferenceService", "Standard inference loop already running, skipping intent inference")
            return
        }
        isGenerating = true
        startForegroundTask()

        serviceScope.launch {
            try {
                Log.d("InferenceService", "Initializing engine for intent inference")
                ensureEngineInitialized()
                setupIntentConversation(schema)
                Log.i("InferenceService", "Running intent inference loop with ${imagePaths.size} images")
                runIntentInferenceLoop(userText, imagePaths)
            } catch (e: Exception) {
                Log.e("InferenceService", "Error during intent inference", e)
                resultReceiver?.send(-1, Bundle().apply { putString("error", e.localizedMessage) })
            } finally {
                isGenerating = false
                stopForegroundTask()
                Log.d("InferenceService", "Intent inference process completed")
            }
        }
    }

    private fun setupIntentConversation(schema: String) {
        val systemPrompt = """
            You are a highly specialized data extraction engine.
            Your sole purpose is to analyze the provided images/text and output a SINGLE valid JSON object that adheres STRICTLY to the provided JSON schema.
            
            EXTREMELY IMPORTANT:
            1. DO NOT respond with any conversational text.
            2. DO NOT include any preamble, explanation, or postscript.
            3. DO NOT wrap the output in any custom keys or objects (like 'vaccination_details' or 'name'). The root of your JSON must be the resource itself.
            4. You MUST call the 'return_intent_result' tool with your final result.
            5. The argument to 'return_intent_result' must be the RAW JSON string, not markdown-formatted.
            6. If multiple immunizations are found, extract only the most recent one as a single object.
            7. If you receive an 'Error' response from 'return_intent_result', you MUST immediately fix the JSON and call the tool AGAIN. 
            8. NEVER respond with text, apologies, or explanations. Your response Turn MUST ALWAYS be a tool call to 'return_intent_result'.
            
            SCHEMA:
            $schema
            
            Start extraction immediately.
            """.trimIndent()

        currentConversation?.close()
        val toolSet = IntentToolSet(resultReceiver, schema)
        activeIntentToolSet = toolSet
        currentConversation = engine?.createConversation(ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            initialMessages = emptyList(),
            tools = listOf(tool(toolSet)),
            automaticToolCalling = true,
        ))
        currentConversationId = -2L // Special ID for intent inference
    }

    private suspend fun runIntentInferenceLoop(
        userText: String,
        imagePaths: Array<String>
    ) {
        val conv = currentConversation ?: return

        // First message contains images
        val initialContents = mutableListOf<Content>()
        imagePaths.forEach { path -> initialContents.add(Content.ImageFile(path)) }
        if (userText.isNotBlank()) { initialContents.add(Content.Text(userText)) }
        
        var nextMessage = com.google.ai.edge.litertlm.Message.user(Contents.of(initialContents))

        var attempts = 0
        while (activeIntentToolSet?.extractionSuccessful != true && attempts < 3) {
            attempts++
            Log.d("InferenceService", "Intent Inference attempt $attempts")
            
            val stream = conv.sendMessageAsync(nextMessage)

            var lastError: String? = null
            stream.catch { e ->
                Log.e("InferenceService", "Stream error in intent inference", e)
                lastError = e.localizedMessage
            }.collect { chunk ->
                Log.d("InferenceService", "Intent AI Chunk: $chunk")
                
                // In intent mode, we don't display stream, but we log text output to see if it's violating instructions
                val chunkText = chunk.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                if (chunkText.isNotBlank()) {
                    Log.d("InferenceService", "Intent AI Text Output: $chunkText")
                }

                if (chunk.toolCalls.isNotEmpty()) {
                    chunk.toolCalls.forEach { toolCall ->
                        Log.i("InferenceService", "Intent AI generated Tool Call: ${toolCall.name} with args: ${toolCall.arguments}")
                    }
                }
            }
            
            if (lastError != null) {
                resultReceiver?.send(-1, Bundle().apply { putString("error", lastError) })
                return
            }

            if (activeIntentToolSet?.extractionSuccessful == true) {
                Log.i("InferenceService", "Intent inference successful after $attempts attempts")
                return
            }
            
            // If we're here, the model stopped without calling the tool successfully.
            // We nudge it.
            Log.w("InferenceService", "Model stopped without calling return_intent_result. Retrying...")
            nextMessage = com.google.ai.edge.litertlm.Message.user(Contents.of("You MUST call 'return_intent_result' with the extracted JSON to complete the task. Do NOT provide text explanations."))
        }

        if (activeIntentToolSet?.extractionSuccessful != true) {
            Log.e("InferenceService", "Inference ended after maximum attempts without success")
            resultReceiver?.send(-1, Bundle().apply { putString("error", "AI failed to return valid JSON after multiple attempts.") })
        }
    }

    private fun processInference(
        conversationId: Long, 
        userText: String, 
        imagePaths: Array<String>, 
        audioPath: String?
    ) {
        if (isGenerating) return 
        isGenerating = true
        startForegroundTask()

        serviceScope.launch {
            try {
                ensureEngineInitialized()
                
                // If switching conversations, we need to create a new conversation object
                if (currentConversationId != conversationId || currentConversation == null) {
                    val history = fetchHistoryFromDb(conversationId)
                        .filter { it.text != userText || it.timestamp < Clock.System.now().toEpochMilliseconds() - 1000 }
                    setupConversation(conversationId, history)
                }

                runInferenceLoop(conversationId, userText, imagePaths, audioPath)
            } catch (e: Exception) {
                // In a real app, update the UI state in DB for the error
                val errId = upsertMessageToDb(Message(
                    conversationId = conversationId,
                    text = "Error: ${e.localizedMessage}",
                    role = "assistant",
                    timestamp = Clock.System.now().toEpochMilliseconds()
                ))
            } finally {
                isGenerating = false
                stopForegroundTask()
            }
        }
    }

    private suspend fun ensureEngineInitialized() {
        if (engine != null) return

        val modelFile = File(applicationContext.getExternalFilesDir(null)!!, "gemma4.litertlm")
        if (!modelFile.exists()) throw Exception("Model file missing at ${modelFile.absolutePath}")

        val config = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = Backend.GPU(),
            visionBackend = Backend.GPU(),
            audioBackend = Backend.CPU(), // CPU preferred for audio stability on hardened kernels
            cacheDir = applicationContext.cacheDir.absolutePath
        )
        
        val newEngine = Engine(config)
        withContext(Dispatchers.IO) {
            newEngine.initialize()
        }
        engine = newEngine
    }

    private fun setupConversation(id: Long, history: List<Message>) {
        val systemPrompt = """
            You are a helpful Android assistant.
            On the first request the user sends to you, you MUST define a title for the conversation. You may optionally change the title if the topic of conversation changes sufficiently
            """.trimIndent()

        val initialMessages = history.map { msg ->
            when (msg.role) {
                "user" -> com.google.ai.edge.litertlm.Message.user(Contents.of(msg.text))
                "assistant" -> com.google.ai.edge.litertlm.Message.model(Contents.of(msg.text))
                else -> com.google.ai.edge.litertlm.Message.user(Contents.of(msg.text))
            }
        }

        currentConversation?.close()
        currentConversation = engine?.createConversation(ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            initialMessages = initialMessages,
            tools = listOf(tool(AssistantToolSet(applicationContext))),
            automaticToolCalling = true,
        ))
        currentConversationId = id
    }

    private suspend fun runInferenceLoop(
        conversationId: Long, 
        userText: String, 
        imagePaths: Array<String>, 
        audioPath: String?
    ) {
        val conv = currentConversation ?: return
        
        // Create the AI message record placeholder
        val aiMsgId = upsertMessageToDb(Message(
            conversationId = conversationId,
            text = "...",
            role = "assistant",
            timestamp = Clock.System.now().toEpochMilliseconds()
        ))

        var fullResponseText = ""
        var displayedText = ""

        // On the first loop iteration, build multimodal content
        val stream = run {
            val contents = mutableListOf<Content>()

            // Add images
            imagePaths.forEach { path ->
                contents.add(Content.ImageFile(path))
            }

            // Add audio
            audioPath?.let { path ->
                if (File(path).exists()) {
                    contents.add(Content.AudioFile(path))
                }
            }

            // Add text last
            if (userText.isNotBlank()) {
                contents.add(Content.Text(userText))
            }

            conv.sendMessageAsync(com.google.ai.edge.litertlm.Message.user(Contents.of(contents)))
        }

        stream.catch { e ->
            updateMessageInDb(aiMsgId, "Error: ${e.message}")
        }.collect { chunk ->
            val chunkText = chunk.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
            fullResponseText += chunkText
            displayedText += chunkText

            if(newTitle != null) {
                updateTitleInDb(conversationId, newTitle!!)
                newTitle = null
            }

            if (displayedText.isNotBlank()) {
                updateMessageInDb(aiMsgId, displayedText)
            }
        }
    }

    // Database interaction placeholders - implementation should be provided by your DB layer
    private suspend fun fetchHistoryFromDb(id: Long): List<Message> = viewModel.getAll<Message>().filter { it.conversationId == id }
    private suspend fun upsertMessageToDb(msg: Message): Long = viewModel.upsert(msg)
    private suspend fun updateMessageInDb(id: Long, text: String) {
        val newMsg = viewModel.get<Message>(id).copy(text = text)
        upsertMessageToDb(newMsg)
    }
    private suspend fun updateTitleInDb(id: Long, title: String) {
        val newMsg = viewModel.get<Conversation>(id).copy(title = title)
        viewModel.upsert(newMsg)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        currentConversation?.close()
        engine?.close()
        super.onDestroy()
    }
}
