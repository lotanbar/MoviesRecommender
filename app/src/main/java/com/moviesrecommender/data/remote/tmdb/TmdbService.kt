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
}
