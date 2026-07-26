package de.tech2mar.openwebui.data

import de.tech2mar.openwebui.data.model.ChatCompletionChunk
import de.tech2mar.openwebui.data.model.ChatCompletionRequest
import de.tech2mar.openwebui.data.model.ChatMessage
import de.tech2mar.openwebui.data.model.ModelInfo
import de.tech2mar.openwebui.data.model.ModelsResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Streaming events emitted while a chat completion response is being read.
 */
sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data object Done : StreamEvent
    data class Error(val throwable: Throwable) : StreamEvent
}

/**
 * Thin client for the parts of the OpenWebUI REST API this app needs:
 * listing available models and running (streaming) chat completions.
 * Auth is a bearer API key, created in OpenWebUI under Settings > Account > API keys.
 */
class OpenWebUiApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun fetchModels(serverUrl: String, apiKey: String): Result<List<ModelInfo>> = runCatching {
        val request = Request.Builder()
            .url("$serverUrl/api/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val body = executeSuspend(request).use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            response.body?.string().orEmpty()
        }
        json.decodeFromString(ModelsResponse.serializer(), body).data
    }

    fun streamChatCompletion(
        serverUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        onEvent: (StreamEvent) -> Unit
    ): EventSource {
        val requestBody = ChatCompletionRequest(model = model, messages = messages, stream = true)
        val jsonBody = json.encodeToString(ChatCompletionRequest.serializer(), requestBody)

        val request = Request.Builder()
            .url("$serverUrl/api/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    onEvent(StreamEvent.Done)
                    return
                }
                runCatching { json.decodeFromString(ChatCompletionChunk.serializer(), data) }
                    .onSuccess { chunk ->
                        val choice = chunk.choices.firstOrNull()
                        val text = choice?.delta?.content
                        if (!text.isNullOrEmpty()) {
                            onEvent(StreamEvent.Delta(text))
                        }
                        if (choice?.finishReason != null) {
                            onEvent(StreamEvent.Done)
                        }
                    }
                // Malformed or keep-alive chunks are silently skipped; the stream continues.
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val error: Throwable = t ?: IOException(
                    if (response != null) "HTTP ${response.code}: ${response.message}" else "Unknown streaming error"
                )
                onEvent(StreamEvent.Error(error))
            }

            override fun onClosed(eventSource: EventSource) {
                onEvent(StreamEvent.Done)
            }
        }

        return EventSources.createFactory(client).newEventSource(request, listener)
    }

    private suspend fun executeSuspend(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response)
            }
        })
    }
}
