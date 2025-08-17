package ru.radionov.mindweaverserver.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val id: String? = null,
    @SerialName("object")
    val chatObject: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
    val error: ErrorDetail? = null
)

@Serializable
data class Choice(
    val index: Int? = null,
    val message: ChatMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null
)

@Serializable
data class ErrorDetail(
    val message: String,
    val type: String? = null,
    val code: String? = null
)