package ru.radionov.mindweaverserver.utils

import java.util.Properties

data class ApiConfiguration(
    val openAiApiKey: String,
    val tgToken: String,
    val tgChatId: String,
    val githubRepoName: String,
    val githubOwnerName: String,
    val githubApiKey: String,
) {
    companion object {
        fun load(): ApiConfiguration {
            val properties = Properties()

            // Try to load from config file first
            try {
                val configStream = ApiConfiguration::class.java.classLoader
                    .getResourceAsStream("api-config.properties")
                if (configStream != null) {
                    properties.load(configStream)
                }
            } catch (e: Exception) {
                // Config file not found, will use environment variables
            }

            return ApiConfiguration(
                openAiApiKey = properties.getProperty("openai.api.key").orEmpty(),
                tgToken = properties.getProperty("tg.bot.token").orEmpty(),
                tgChatId = properties.getProperty("tg.chat.id").orEmpty(),
                githubRepoName = properties.getProperty("github.repo.name").orEmpty(),
                githubOwnerName = properties.getProperty("github.owner.name").orEmpty(),
                githubApiKey = properties.getProperty("github.api.key").orEmpty(),
            )
        }
    }
}