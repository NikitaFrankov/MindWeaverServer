@file:OptIn(ExperimentalTime::class)

package ru.radionov.mindweaverserver.mcp.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import ru.radionov.mindweaverserver.ApiConfiguration
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.orEmpty
import kotlin.text.trimIndent
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class GithubMCP(private val config: ApiConfiguration) {
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
    val httpClient = createHttpClient()
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
            name = "get_commits",
            description = "Get list of commits for a GitHub repository. Input is owner (username or organization) and repo name (e.g. octocat, Hello-World)",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("owner") {
                        put("type", "string")
                        put("description", "GitHub username or organization (e.g. octocat)")
                    }
                    putJsonObject("repo") {
                        put("type", "string")
                        put("description", "Repository name (e.g. Hello-World)")
                    }
                    putJsonObject("since") {
                        put("type", "string")
                        put("description", "Only commits after this date will be returned (ISO-8601, e.g. 2025-08-16T00:00:00Z)")
                    }
                    putJsonObject("until") {
                        put("type", "string")
                        put("description", "Only commits before this date will be returned (ISO-8601, e.g. 2025-08-17T00:00:00Z)")
                    }
                },
                required = listOf("owner", "repo")
            )
        ) { request ->
            val owner = request.arguments["owner"]?.jsonPrimitive?.content ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'owner' parameter is required."))
            )
            val repo = request.arguments["repo"]?.jsonPrimitive?.content ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'repo' parameter is required."))
            )
            val since = request.arguments["since"]?.jsonPrimitive?.contentOrNull
            val until = request.arguments["until"]?.jsonPrimitive?.contentOrNull

            val commits = httpClient.getCommits(
                owner = owner,
                repo = repo,
                since = since?.let { Instant.parse(it) },
                until = until?.let { Instant.parse(it) }
            )
            CallToolResult(content = commits.map { TextContent(it) })
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

    suspend fun summarizeCommits(
        owner: String,
        repo: String,
        since: Instant? = null,
        until: Instant? = null,
    ): List<TextContent>? {
        // if server is active

        val arguments = buildJsonObject {
            put("owner", owner)
            put("repo", repo)
            if (since != null) {
                put("since", since.toString())
            }
            if (until != null) {
                put("until", until.toString())
            }
        }

        // Вызов tool
        val request = CallToolRequest(
            name = "get_commits", // Или uri, если используется
            arguments = arguments
        )

        val result = client.callTool(request)

        return result?.content as List<TextContent>?
    }

    private fun createHttpClient() = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                }
            )
        }

        install(Logging) {
            level = LogLevel.INFO
        }

        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = "api.github.com"
            }

            val token = config.githubApiKey

            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
        }
    }
}

private suspend fun HttpClient.getCommits(
    owner: String,
    repo: String,
    since: Instant? = null,
    until: Instant? = null,
): List<String> {
    val uri = "/repos/$owner/$repo/commits"
    val commits = this.get(uri) {
        url {
            since?.let { parameters.append("since", it.toString()) }
            until?.let { parameters.append("until", it.toString()) }
        }
    }.body<List<Commit>>()

    return commits.map { commit ->
        """
            SHA: ${commit.sha}
            Message: ${commit.commit.message}
            Author: ${commit.commit.author.name} (${commit.commit.author.email})
            Date: ${commit.commit.author.date}
        """.trimIndent()
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