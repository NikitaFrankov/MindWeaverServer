package ru.radionov.mindweaverserver

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.radionov.mindweaverserver.aiClients.ChatGPTApiClient
import ru.radionov.mindweaverserver.mcpClient.MCPClient
import ru.radionov.mindweaverserver.models.ChatMessage
import ru.radionov.mindweaverserver.utils.sendToTelegram
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun main(): Unit = runBlocking {
    val config = ApiConfiguration.load()

    val aiGPTApiClient = ChatGPTApiClient(config)
    val client = MCPClient(config)
    client.init()

    launch {
        val owner = config.githubOwnerName
        val repo = config.githubRepoName
        val until = null

        val report = client.summarizeCommits(owner = owner, repo = repo, until = until)
        val text = report?.mapNotNull { it.text }.orEmpty().joinToString { "\n\n" + it }

        val content = "Проведи анализ коммитов и выдай статистику \n$text"
        val message = ChatMessage(
            role = "user",
            content = content,
        )
        val commitsAnalyticsResponse = async { aiGPTApiClient.sendMessage(listOf(message)) }.await()
        commitsAnalyticsResponse.fold(
            onSuccess = { text ->
                sendToTelegram(
                    text = text,
                    token = config.tgToken,
                    chatId = config.tgChatId
                )
            },
            onFailure = ::println
        )
    }
}