package com.moviesrecommender.data.remote.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException

class OkHttpTmdbApiClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) : TmdbApiClient {

    override suspend fun validateApiKey(apiKey: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/authentication?api_key=$apiKey")
            .get()
            .build()
        try {
            val response = httpClient.newCall(request).execute()
            when {
                response.code in 200..299 -> Unit
                response.code == 401 -> throw TmdbApiException.Unauthorized()
                else -> throw TmdbApiException.ServerError("HTTP ${response.code}")
            }
        } catch (e: TmdbApiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw TmdbApiException.NoNetwork()
        } catch (e: IOException) {
            throw TmdbApiException.NoNetwork()
        }
    }
}
