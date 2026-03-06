package com.moviesrecommender.data.remote.anthropic

data class ModelInfo(val id: String, val createdAt: String)

interface AnthropicApiClient {
    suspend fun fetchModels(apiKey: String): List<ModelInfo>
    suspend fun sendMessage(
        apiKey: String,
        modelId: String,
        prompt: String,
        system: String? = null
    ): String
    suspend fun sendMessages(
        apiKey: String,
        modelId: String,
        messages: List<Pair<String, String>>,
        system: String? = null
    ): String
}

sealed class AnthropicApiException(message: String) : Exception(message) {
    class Unauthorized : AnthropicApiException("Invalid API key")
    class NoNetwork : AnthropicApiException("No network")
    class ServerError(message: String) : AnthropicApiException(message)
}
