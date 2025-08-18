package ru.radionov.mindweaverserver

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.radionov.mindweaverserver.aiClients.OpenAiClient
import ru.radionov.mindweaverserver.mcp.ai.AiAnalyzerMCP
import ru.radionov.mindweaverserver.mcp.github.GithubMCP
import ru.radionov.mindweaverserver.utils.sendToTelegram
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun main(): Unit = runBlocking {
    val config = ApiConfiguration.load()

    val aiGPTApiClient = OpenAiClient(config)
    val githubClient = GithubMCP(config)
    val aiAnalyzerClient = AiAnalyzerMCP(aiGPTApiClient)
    githubClient.init()
    aiAnalyzerClient.init()

    launch {
        val owner = config.githubOwnerName
        val repo = config.githubRepoName
        val since = Clock.System.now().minus(Duration.parse("24h"))
        val until = Clock.System.now()

        val report = githubClient.summarizeCommits(owner = owner, repo = repo, since = since, until = until)
        val commitsText = report?.mapNotNull { it.text }.orEmpty().joinToString { "\n\n" + it }
        val analyzedCommits = aiAnalyzerClient.analyzeCommits(commitsText)

        sendToTelegram(
            text = analyzedCommits,
            token = config.tgToken,
            chatId = config.tgChatId
        )
    }
}