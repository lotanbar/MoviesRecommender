package com.moviesrecommender.data.remote.anthropic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException

class OkHttpAnthropicApiClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) : AnthropicApiClient {

    private val json = "application/json".toMediaType()

    override suspend fun fetchModels(apiKey: String): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/models")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .get()
                .build()
            val body = execute(apiKey, request)
            val data = JSONObject(body).getJSONArray("data")
            (0 until data.length()).map { i ->
                val obj = data.getJSONObject(i)
                ModelInfo(
                    id = obj.getString("id"),
                    createdAt = obj.optString("created_at", "")
                )
            }
        }

    override suspend fun sendMessage(
        apiKey: String,
        modelId: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", 16000)
            put("thinking", JSONObject().apply {
                put("type", "enabled")
                put("budget_tokens", 10000)
            })
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("anthropic-beta", "interleaved-thinking-2025-05-14")
            .post(bodyJson.toRequestBody(json))
            .build()

        val responseBody = execute(apiKey, request)
        val content = JSONObject(responseBody).getJSONArray("content")
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.getString("type") == "text") return@withContext block.getString("text")
        }
        throw AnthropicApiException.ServerError("No text block in response")
    }

    private fun execute(apiKey: String, request: Request): String {
        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            when {
                response.code in 200..299 -> body
                response.code == 401 -> throw AnthropicApiException.Unauthorized()
                else -> throw AnthropicApiException.ServerError("HTTP ${response.code}: $body")
            }
        } catch (e: AnthropicApiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw AnthropicApiException.NoNetwork()
        } catch (e: IOException) {
            throw AnthropicApiException.NoNetwork()
        }
    }
}
