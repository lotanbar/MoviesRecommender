package com.moviesrecommender.data.remote.tmdb

import com.moviesrecommender.data.remote.dropbox.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TmdbServiceTest {

    private lateinit var tokenStore: FakeTmdbTokenStore
    private lateinit var apiClient: FakeTmdbApiClient
    private lateinit var service: TmdbService

    @Before
    fun setUp() {
        tokenStore = FakeTmdbTokenStore()
        apiClient = FakeTmdbApiClient()
        service = TmdbService(tokenStore, apiClient)
        tokenStore[TmdbService.KEY_TMDB] = "valid_api_key"
    }

    // ── search ──────────────────────────────────────────────────────────────

    @Test
    fun `search returns list of titles on success`() = runTest {
        apiClient.searchResponse = listOf(
            fakeTitle(id = 1, title = "The Matrix", year = 1999),
            fakeTitle(id = 2, title = "The Matrix Reloaded", year = 2003)
        )
        val result = service.search("Matrix")
        assertTrue(result is TmdbResult.Success)
        assertEquals(2, (result as TmdbResult.Success).value.size)
        assertEquals("The Matrix", result.value[0].title)
    }

    @Test
    fun `search returns NoInternet on network error`() = runTest {
        apiClient.searchException = TmdbApiException.NoNetwork()
        val result = service.search("Matrix")
        assertTrue(result is TmdbResult.Failure)
        assertEquals(TmdbError.NoInternet, (result as TmdbResult.Failure).error)
    }

    @Test
    fun `search returns InvalidApiKey when unauthorized`() = runTest {
        apiClient.searchException = TmdbApiException.Unauthorized()
        val result = service.search("Matrix")
        assertTrue(result is TmdbResult.Failure)
        assertEquals(TmdbError.InvalidApiKey, (result as TmdbResult.Failure).error)
    }

    @Test
    fun `search returns InvalidApiKey when no api key stored`() = runTest {
        tokenStore[TmdbService.KEY_TMDB] = null
        val result = service.search("Matrix")
        assertTrue(result is TmdbResult.Failure)
        assertEquals(TmdbError.InvalidApiKey, (result as TmdbResult.Failure).error)
    }

    // ── fetchMetadata ────────────────────────────────────────────────────────

    @Test
    fun `fetchMetadata returns full title details on success`() = runTest {
        val searchCandidate = fakeTitle(id = 278, title = "The Shawshank Redemption", year = 1994)
        val fullTitle = fakeTitle(
            id = 278,
            title = "The Shawshank Redemption",
            year = 1994,
            director = "Frank Darabont",
            leadActors = listOf("Tim Robbins", "Morgan Freeman"),
            productionCompany = "Castle Rock Entertainment"
        )
        apiClient.searchResponse = listOf(searchCandidate)
        apiClient.detailsResponse = fullTitle

        val result = service.fetchMetadata("The Shawshank Redemption", 1994)
        assertTrue(result is TmdbResult.Success)
        val title = (result as TmdbResult.Success).value
        assertEquals("Frank Darabont", title.director)
        assertEquals(listOf("Tim Robbins", "Morgan Freeman"), title.leadActors)
        assertEquals("Castle Rock Entertainment", title.productionCompany)
    }

    @Test
    fun `fetchMetadata prefers exact year match over first result`() = runTest {
        val wrongYear = fakeTitle(id = 1, title = "Dune", year = 1984)
        val rightYear = fakeTitle(id = 2, title = "Dune", year = 2021)
        apiClient.searchResponse = listOf(wrongYear, rightYear)
        apiClient.detailsResponse = rightYear

        val result = service.fetchMetadata("Dune", 2021)
        assertTrue(result is TmdbResult.Success)
        assertEquals(2021, (result as TmdbResult.Success).value.year)
        assertEquals(2, apiClient.lastFetchedId)
    }

    @Test
    fun `fetchMetadata returns NotFound when search yields no results`() = runTest {
        apiClient.searchResponse = emptyList()
        val result = service.fetchMetadata("Nonexistent Title", 2025)
        assertTrue(result is TmdbResult.Failure)
        assertEquals(TmdbError.NotFound, (result as TmdbResult.Failure).error)
    }

    @Test
    fun `fetchMetadata returns NoInternet on network failure`() = runTest {
        apiClient.searchException = TmdbApiException.NoNetwork()
        val result = service.fetchMetadata("The Matrix", 1999)
        assertTrue(result is TmdbResult.Failure)
        assertEquals(TmdbError.NoInternet, (result as TmdbResult.Failure).error)
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun fakeTitle(
    id: Int = 1,
    title: String = "Test Title",
    year: Int = 2000,
    director: String? = null,
    leadActors: List<String> = emptyList(),
    productionCompany: String? = null
) = Title(
    id = id,
    title = title,
    year = year,
    posterPath = null,
    overview = null,
    genres = emptyList(),
    country = null,
    director = director,
    leadActors = leadActors,
    productionCompany = productionCompany,
    mediaType = MediaType.MOVIE
)

// ─── Fakes ────────────────────────────────────────────────────────────────────

class FakeTmdbTokenStore : TokenStore {
    private val map = mutableMapOf<String, String?>()
    override fun get(key: String): String? = map[key]
    override fun set(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

class FakeTmdbApiClient : TmdbApiClient {

    var searchResponse: List<Title> = emptyList()
    var searchException: TmdbApiException? = null

    var detailsResponse: Title = fakeTitle()
    var detailsException: TmdbApiException? = null
    var lastFetchedId: Int = -1

    override suspend fun validateApiKey(apiKey: String) {}

    override suspend fun search(query: String, apiKey: String): List<Title> {
        searchException?.let { throw it }
        return searchResponse
    }

    override suspend fun fetchDetails(id: Int, mediaType: MediaType, apiKey: String): Title {
        detailsException?.let { throw it }
        lastFetchedId = id
        return detailsResponse
    }
}
