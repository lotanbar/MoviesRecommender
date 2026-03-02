package com.moviesrecommender.data.remote.tmdb

interface TmdbApiClient {
    suspend fun validateApiKey(apiKey: String)
    suspend fun search(query: String, apiKey: String): List<Title>
    suspend fun fetchDetails(id: Int, mediaType: MediaType, apiKey: String): Title
}
