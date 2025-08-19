package ru.radionov.mindweaverserver.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking

fun sendToTelegram(text: String, token: String, chatId: String) {
    val client = HttpClient(CIO) {
        install(Logging) {
            level = LogLevel.ALL   // Логируем все: заголовки, тело запроса и ответа
            logger = Logger.DEFAULT
        }
    }

    runBlocking {
        println("text to telegram - $text")
        try {
            val response: HttpResponse = client.post("https://api.telegram.org/bot$token/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody("""{"chat_id":"$chatId","text":"$text"}""")
            }
        } catch (e: Exception) {
            println("Error sending message to Telegram: ${e.message}")
            e.printStackTrace()
        } finally {
            client.close()
        }
    }
}