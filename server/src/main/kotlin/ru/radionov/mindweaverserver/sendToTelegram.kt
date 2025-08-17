package ru.radionov.mindweaverserver

import java.net.HttpURLConnection
import java.net.URI

fun sendToTelegram(text: String) {
    val token = System.getenv("TG_BOT_TOKEN") ?: error("TG_BOT_TOKEN not set")
    val chatId = System.getenv("TG_CHAT_ID") ?: error("TG_CHAT_ID not set")

    val url = URI("https://api.telegram.org/bot$token/sendMessage").toURL()
    val body = """{"chat_id":"$chatId","text":"$text"}"""

    with(url.openConnection() as HttpURLConnection) {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        outputStream.use { it.write(body.toByteArray()) }
        inputStream.bufferedReader().use { println(it.readText()) }
    }
}