package com.moviesrecommender.data.remote.tmdb

data class SearchPage(val titles: List<Title>, val totalPages: Int)

interface TmdbApiClient {
    suspend fun validateApiKey(apiKey: String)
    suspend fun search(query: String, apiKey: String, page: Int = 1): SearchPage
    suspend fun fetchDetails(id: Int, mediaType: MediaType, apiKey: String): Title
}
