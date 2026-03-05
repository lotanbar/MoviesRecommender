package com.moviesrecommender.data.remote.tmdb

import com.moviesrecommender.data.remote.dropbox.TokenStore

class TmdbService(
    private val store: TokenStore,
    private val apiClient: TmdbApiClient
) {
    companion object {
        const val KEY_TMDB = "tmdb_api_key"
    }

    fun getApiKey(): String? = store[KEY_TMDB]
    fun isConfigured(): Boolean = store[KEY_TMDB] != null
    fun clearApiKey() { store[KEY_TMDB] = null }

    suspend fun saveApiKeyAndValidate(key: String): TmdbResult<Unit> {
        return try {
            apiClient.validateApiKey(key)
            store[KEY_TMDB] = key
            TmdbResult.Success(Unit)
        } catch (e: TmdbApiException.Unauthorized) {
            TmdbResult.Failure(TmdbError.InvalidApiKey)
        } catch (e: TmdbApiException.NoNetwork) {
            TmdbResult.Failure(TmdbError.NoInternet)
        } catch (e: TmdbApiException.ServerError) {
            TmdbResult.Failure(TmdbError.ApiError(e.message ?: "Unknown error"))
        }
    }

    suspend fun search(query: String, page: Int = 1): TmdbResult<SearchPage> {
        val apiKey = store[KEY_TMDB] ?: return TmdbResult.Failure(TmdbError.InvalidApiKey)
        return try {
            TmdbResult.Success(apiClient.search(query, apiKey, page))
        } catch (e: TmdbApiException.Unauthorized) {
            TmdbResult.Failure(TmdbError.InvalidApiKey)
        } catch (e: TmdbApiException.NoNetwork) {
            TmdbResult.Failure(TmdbError.NoInternet)
        } catch (e: TmdbApiException.ServerError) {
            TmdbResult.Failure(TmdbError.ApiError(e.message ?: "Unknown error"))
        }
    }

    suspend fun fetchDetails(id: Int, mediaType: MediaType): TmdbResult<Title> {
        val apiKey = store[KEY_TMDB] ?: return TmdbResult.Failure(TmdbError.InvalidApiKey)
        return try {
            TmdbResult.Success(apiClient.fetchDetails(id, mediaType, apiKey))
        } catch (e: TmdbApiException.Unauthorized) {
            TmdbResult.Failure(TmdbError.InvalidApiKey)
        } catch (e: TmdbApiException.NoNetwork) {
            TmdbResult.Failure(TmdbError.NoInternet)
        } catch (e: TmdbApiException.ServerError) {
            TmdbResult.Failure(TmdbError.ApiError(e.message ?: "Unknown error"))
        }
    }

    suspend fun fetchMetadata(title: String, year: Int): TmdbResult<Title> {
        val apiKey = store[KEY_TMDB] ?: return TmdbResult.Failure(TmdbError.InvalidApiKey)
        return try {
            val candidates = apiClient.search(title, apiKey, 1).titles
            val match = candidates.firstOrNull { it.year == year } ?: candidates.firstOrNull()
                ?: return TmdbResult.Failure(TmdbError.NotFound)
            TmdbResult.Success(apiClient.fetchDetails(match.id, match.mediaType, apiKey))
        } catch (e: TmdbApiException.Unauthorized) {
            TmdbResult.Failure(TmdbError.InvalidApiKey)
        } catch (e: TmdbApiException.NoNetwork) {
            TmdbResult.Failure(TmdbError.NoInternet)
        } catch (e: TmdbApiException.ServerError) {
            TmdbResult.Failure(TmdbError.ApiError(e.message ?: "Unknown error"))
        }
    }
}
