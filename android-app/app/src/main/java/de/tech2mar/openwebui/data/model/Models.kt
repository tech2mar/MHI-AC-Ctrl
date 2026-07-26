package de.tech2mar.openwebui.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelInfo(
    val id: String,
    val name: String? = null
)

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo> = emptyList()
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true
)

@Serializable
data class DeltaContent(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ChatCompletionChoice(
    val index: Int = 0,
    val delta: DeltaContent? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatCompletionChunk(
    val choices: List<ChatCompletionChoice> = emptyList()
)
