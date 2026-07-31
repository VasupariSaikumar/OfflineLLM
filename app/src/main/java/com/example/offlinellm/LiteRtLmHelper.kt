package com.example.offlinellm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LiteRtLmHelper {

    sealed class EngineState {
        object NotLoaded : EngineState()
        object Loading : EngineState()
        object Ready : EngineState()
        data class Error(val message: String) : EngineState()
    }

    sealed class ResponseState {
        object Idle : ResponseState()
        object Generating : ResponseState()
        data class Success(val text: String) : ResponseState()
        data class Error(val message: String) : ResponseState()
    }

    private val _engineState = MutableStateFlow<EngineState>(EngineState.NotLoaded)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _responseState = MutableStateFlow<ResponseState>(ResponseState.Idle)
    val responseState: StateFlow<ResponseState> = _responseState.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun loadModel(modelPath: String) = withContext(Dispatchers.IO) {
        _engineState.value = EngineState.Loading
        try {
            // Limit threads to prevent system starvation and ANRs on high-load devices
            val cpuBackend = Backend.CPU(threadCount = 2)
            val config = EngineConfig(modelPath = modelPath, backend = cpuBackend)
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            conversation = newEngine.createConversation()
            _engineState.value = EngineState.Ready
        } catch (e: Exception) {
            _engineState.value = EngineState.Error(e.message ?: "Failed to load model")
        }
    }

    fun resetConversation() {
        conversation?.close()
        conversation = engine?.createConversation()
        _responseState.value = ResponseState.Idle
    }

  
    suspend fun ask(prompt: String) = withContext(Dispatchers.IO) {
        val activeConversation = conversation
        if (activeConversation == null) {
            _responseState.value = ResponseState.Error("Model not loaded yet")
            return@withContext
        }

        _responseState.value = ResponseState.Generating
        var accumulatedText = ""
        try {
            activeConversation.sendMessageAsync(Message.user(prompt)).collect { messageChunk ->
                val chunkText = messageChunk.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                accumulatedText += chunkText
                _responseState.value = ResponseState.Success(accumulatedText)
            }
        } catch (e: Exception) {
            _responseState.value = ResponseState.Error(e.message ?: "Inference failed")
        }
    }

    fun release() {
        engine?.close()
        engine = null
        conversation = null
        _engineState.value = EngineState.NotLoaded
    }
}