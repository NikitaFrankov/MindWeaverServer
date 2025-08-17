package ru.radionov.mindweaverserver.utils

import java.net.HttpURLConnection
import java.net.URI

fun sendToTelegram(text: String, token: String, chatId: String) {
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