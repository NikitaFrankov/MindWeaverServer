package ru.radionov.mindweaverserver

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val client = MCPClient()
    client.init()

    launch {
        while (isActive) {
            val report = client.summarizeCommits("NikitaFrankov", "MindWeaverStudio")
            val text = report?.mapNotNull { it.text }.orEmpty().joinToString { "\n\n" + it }
            sendToTelegram(text)
        }
    }
}