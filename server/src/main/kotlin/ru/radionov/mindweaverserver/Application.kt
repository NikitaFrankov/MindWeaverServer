package ru.radionov.mindweaverserver

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.radionov.mindweaverserver.aiClients.ChatGPTApiClient
import ru.radionov.mindweaverserver.mcpClient.MCPClient
import ru.radionov.mindweaverserver.models.ChatMessage
import ru.radionov.mindweaverserver.utils.sendToTelegram
import kotlin.time.Clock
import kotlin.time.Duration
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
        val since = Clock.System.now().minus(Duration.parse("24h"))
        val until = Clock.System.now()

        val report = client.summarizeCommits(owner = owner, repo = repo, since = since, until = until)
        val text = report?.mapNotNull { it.text }.orEmpty().joinToString { "\n\n" + it }

        val content = """
            Проведи анализ коммитов и выдай статистику.
            В статистике не должно быть форматирования markdown. 
          
            НЕ ПРИДУМЫВАЙ ДАННЫЕ, ТОЛЬКО АНАЛИЗИРУЙ ПЕРЕДАННЫЙ СПИСОК!!!!
            Список коммитов:$text
            
            ВАЖНО!!! Если список коммитов пустой, вывести только уведомление, что за последние сутки не было коммитов в репозитории $repo. БОЛЬШЕ НИЧЕГО НЕ ВЫВОДИТЬ!!!!
        """.trimIndent()
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