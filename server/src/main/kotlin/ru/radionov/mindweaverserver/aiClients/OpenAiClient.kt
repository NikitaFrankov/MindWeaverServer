package ru.radionov.mindweaverserver.aiClients

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import ru.radionov.mindweaverserver.utils.ApiConfiguration
import ru.radionov.mindweaverserver.models.ChatMessage
import ru.radionov.mindweaverserver.models.ChatRequest
import ru.radionov.mindweaverserver.models.ChatResponse

class OpenAiClient(private val config: ApiConfiguration) {
    val baseUrl: String = "https://openrouter.ai/api/v1"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(this@OpenAiClient.json)
        }
        install(Logging) {
            logger = Logger.Companion.DEFAULT
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 1500000
            connectTimeoutMillis = 500000
            socketTimeoutMillis = 1000000
        }
    }

    suspend fun sendMessage(
        messages: List<ChatMessage>
    ): Result<String> {
        val request = ChatRequest(
            model = "qwen/qwen2.5-vl-32b-instruct:free",
            messages = messages,
            temperature = 0.7,
            maxTokens = 1000
        )

        return createChatCompletion(request).fold(
            onSuccess = { response ->
                response.error?.let { error ->
                    return Result.failure(Exception("ChatGPT API Error: ${error.message}"))
                }

                val content = response.choices?.firstOrNull()?.message?.content
                    ?: return Result.failure(Exception("No response content available"))

                Result.success(content)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    private suspend fun createChatCompletion(request: ChatRequest): Result<ChatResponse> {
        val apiKey = config.openAiApiKey

        return try {
            val response = client.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    append("Content-Type", "application/json")
                }
                setBody(request)
            }

            val rawResponse = response.bodyAsText()
            println("ChatGPT API Response: $rawResponse")

            val jsonResponse = json.decodeFromString<ChatResponse>(rawResponse)
            Result.success(jsonResponse)
        } catch (e: Exception) {
            println("ChatGPT API Error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}