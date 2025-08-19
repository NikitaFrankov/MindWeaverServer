@file:OptIn(ExperimentalTime::class)

package ru.radionov.mindweaverserver.mcp.ai

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import ru.radionov.mindweaverserver.aiClients.OpenAiClient
import ru.radionov.mindweaverserver.models.ChatMessage
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.orEmpty
import kotlin.text.trimIndent
import kotlin.time.ExperimentalTime

class AiAnalyzerMCP(aiClient: OpenAiClient) {
    private val clientOut = PipedOutputStream()
    private val serverIn = PipedInputStream(clientOut)
    private val serverOut = PipedOutputStream()
    private val clientIn = PipedInputStream(serverOut)
    private val clientTransport = StdioClientTransport(
        input = clientIn.asSource().buffered(),
        output = clientOut.asSink().buffered()
    )
    private val serverTransport = StdioServerTransport(
        inputStream = serverIn.asSource().buffered(),
        outputStream = serverOut.asSink().buffered()
    )
    private val server = Server(
        serverInfo = Implementation(
            name = "example-server",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(
                    subscribe = true,
                    listChanged = true
                ),
                tools = ServerCapabilities.Tools(true),
            )
        )
    )
    private val client = Client(
        clientInfo = Implementation(
            name = "example-client",
            version = "1.0.0"
        )
    )
    private var tools: List<Tool> = emptyList()

    init {
        server.addTool(
            name = "analyze_commits_with_ai",
            description = "Send commit messages as a single text string to an LLM for analysis and get back a human-readable summary.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("commitsText") {
                        put("type", "string")
                        put("description", "A single string containing all commit messages with details (e.g. author, date, message).")
                    }
                },
                required = listOf("commitsText")
            )
        ) { request ->
            val commitsText = request.arguments["commitsText"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("The 'commitsText' parameter is required."))
                )

            val content = """
                ОЧЕНЬ ВАЖНО Никакого форматирования Markdown, только простой текст, но красиво составленный!!!!!!!!!!!!!.
                Ты — ассистент для анализа Git-коммитов. 
                Ни при каких условиях ты не имеешь права придумывать или добавлять информацию, которой нет во входных данных. 
                Твои правила:
                ОЧЕНЬ ВАЖНО Никакого форматирования Markdown, только простой текст, но красиво составленный!!!!!!!!!!!!!.
                
                1. Используй ТОЛЬКО предоставленный список коммитов. 
                   - Если данных нет, не выдумывай ничего. 
                   - Если в коммитах отсутствуют авторы или даты — не добавляй их сам.
                   - Если тип коммита определить невозможно, укажи «неизвестно».  
        
                2. Если список пустой или состоит только из пробелов/пустых строк — верни СТРОГО эту фразу (без изменений, без добавления чего-либо ещё):  
                   "За последние 24 часа коммитов не производилось."
        
                3. Если коммиты есть, выведи отчёт в таком виде:
                   - Общее количество коммитов.
                   - Список всех авторов.
                   - Краткое описание коммита (прочитай message коммита и сделай вывод на русском языке что там было произведено)
                   - Краткое текстовое резюме того, что произошло в проекте.  
                   - Никакого форматирования Markdown
        
                4. В отчёте не используй догадки, прогнозы, внешние данные или вымышленные факты.  
                
                5. ОЧЕНЬ ВАЖНО Никакого форматирования Markdown, только простой текст, но красиво составленный!!!!!!!!!!!!!.
        
                Входные данные (список коммитов):
                $commitsText
                ОЧЕНЬ ВАЖНО Никакого форматирования Markdown, только простой текст, но красиво составленный!!!!!!!!!!!!!.
             """.trimIndent()

            val message = ChatMessage(
                role = "user",
                content = content,
            )

            val commitsAnalyticsResponse = aiClient.sendMessage(listOf(message))

            commitsAnalyticsResponse.fold(
                onSuccess = { text ->
                    CallToolResult(
                        content = listOf(TextContent(text))
                    )
                },
                onFailure = {
                    CallToolResult(
                        content = listOf(TextContent(it.message)),
                        isError = true
                    )
                }
            )
        }

    }

    suspend fun init() {
        server.connect(serverTransport)
        client.connect(clientTransport)

        val toolsResult = client.listTools(request = ListToolsRequest())
        tools = toolsResult?.tools.orEmpty()
    }

    suspend fun getTools(): List<Tool> {
        if (tools.isNotEmpty()) return tools

        val toolsResult = client.listTools(request = ListToolsRequest())
        tools = toolsResult?.tools.orEmpty()
        return tools
    }

    suspend fun callTool(call: ToolCall): List<TextContent>? {
        val arguments = buildJsonObject {
            call.params.forEach {
                put(it.key, it.value)
            }
        }

        // Вызов tool
        val request = CallToolRequest(
            name = call.tool,
            arguments = arguments
        )

        val result = client.callTool(request)

        return result?.content as List<TextContent>?
    }

    suspend fun analyzeCommits(data: String): String {
        val arguments = buildJsonObject {
            put("commitsText", data)
        }

        val request = CallToolRequest(
            name = "analyze_commits_with_ai",
            arguments = arguments
        )

        val toolResult = client.callTool(request)
        if (toolResult?.isError == true) {
            error((toolResult.content.first() as TextContent).text.orEmpty())
        }
        val textContent = toolResult?.content?.firstOrNull() as? TextContent
        val analyzedText = textContent?.text
        if (textContent == null) {
            error("Empty response from AI")
        }
        if (analyzedText == null) {
            error("Empty response from AI")
        }

        return analyzedText
    }
}

// Data classes для GitHub API (как в сэмпле)
@Serializable
data class Commit(
    val sha: String,
    val commit: CommitDetails
)

@Serializable
data class CommitDetails(
    val author: Author,
    val message: String
)

@Serializable
data class Author(
    val name: String,
    val email: String,
    val date: String
)

@Serializable
data class ToolCall(
    val action: String,
    val tool: String,
    val params: Map<String, String> = emptyMap()
)