package ru.radionov.mindweaverserver.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}