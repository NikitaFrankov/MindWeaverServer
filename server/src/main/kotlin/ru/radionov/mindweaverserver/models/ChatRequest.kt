package ru.radionov.mindweaverserver.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 1000,
    val stream: Boolean = false
)