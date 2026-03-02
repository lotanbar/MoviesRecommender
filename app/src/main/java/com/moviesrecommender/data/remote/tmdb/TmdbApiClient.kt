package com.moviesrecommender.data.remote.tmdb

interface TmdbApiClient {
    suspend fun validateApiKey(apiKey: String)
}
