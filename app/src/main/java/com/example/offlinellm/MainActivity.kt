package com.example.offlinellm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch


private const val MODEL_PATH = "/data/local/tmp/llm/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"

class OfflineAiViewModel : ViewModel() {
    val helper = LiteRtLmHelper()

    init {
        // loadModel is a suspend function, so it runs inside a coroutine
        // tied to this ViewModel's lifecycle.
        viewModelScope.launch {
            helper.loadModel(MODEL_PATH)
        }
    }

    fun sendPrompt(prompt: String) {
        viewModelScope.launch {
            helper.ask(prompt)
        }
    }

    fun resetChat() {
        helper.resetConversation()
    }

    override fun onCleared() {
        viewModelScope.launch {
            helper.release()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OfflineAiScreen()
                }
            }
        }
    }
}

@Composable
fun OfflineAiScreen(viewModel: OfflineAiViewModel = viewModel()) {
    val engineState by viewModel.helper.engineState.collectAsStateWithLifecycle()
    val responseState by viewModel.helper.responseState.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Engine: ${engineState::class.simpleName}",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when (val state = responseState) {
                is LiteRtLmHelper.ResponseState.Idle ->
                    Text("Ask something below — this runs fully offline.")
                is LiteRtLmHelper.ResponseState.Generating ->
                    Text("Thinking... (CPU inference on mid-range hardware, give it a moment)")
                is LiteRtLmHelper.ResponseState.Success ->
                    Text(state.text)
                is LiteRtLmHelper.ResponseState.Error ->
                    Text("Error: ${state.message}")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    viewModel.resetChat()
                    prompt = ""
                },
                enabled = engineState is LiteRtLmHelper.EngineState.Ready,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Reset")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                label = { Text("Prompt") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.sendPrompt(prompt) },
                enabled = engineState is LiteRtLmHelper.EngineState.Ready
            ) {
                Text("Send")
            }
        }
    }
}