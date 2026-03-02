package com.moviesrecommender.data.remote.dropbox

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DropboxServiceTest {

    private lateinit var tokenStore: FakeTokenStore
    private lateinit var authManager: DropboxAuthManager
    private lateinit var apiClient: FakeDropboxApiClient
    private lateinit var service: DropboxService

    @Before
    fun setUp() {
        tokenStore = FakeTokenStore()
        authManager = DropboxAuthManager(tokenStore)
        apiClient = FakeDropboxApiClient()
        service = DropboxService(authManager, apiClient)

        tokenStore["dropbox_access_token"] = "access123"
        tokenStore["dropbox_refresh_token"] = "refresh123"
        tokenStore["dropbox_app_key"] = "appkey123"
        tokenStore["dropbox_list_path"] = "/Lists"
    }

    @Test
    fun `downloadList returns content when authenticated`() = runTest {
        apiClient.downloadResponse = "MOVIE LIST CONTENT"
        val result = service.downloadList()
        assertTrue(result is DropboxResult.Success)
        assertEquals("MOVIE LIST CONTENT", (result as DropboxResult.Success).value)
    }

    @Test
    fun `downloadList returns NoInternet on network error`() = runTest {
        apiClient.downloadException = DropboxApiException.NoNetwork()
        val result = service.downloadList()
        assertTrue(result is DropboxResult.Failure)
        assertEquals(DropboxError.NoInternet, (result as DropboxResult.Failure).error)
    }

    @Test
    fun `uploadList returns success`() = runTest {
        val result = service.uploadList("MOVIE CONTENT")
        assertTrue(result is DropboxResult.Success)
    }

    @Test
    fun `uploadList returns StorageFull when quota exceeded`() = runTest {
        apiClient.uploadException = DropboxApiException.InsufficientStorage()
        val result = service.uploadList("CONTENT")
        assertTrue(result is DropboxResult.Failure)
        assertEquals(DropboxError.StorageFull, (result as DropboxResult.Failure).error)
    }

    @Test
    fun `uploadList returns RateLimit when rate limited`() = runTest {
        apiClient.uploadException = DropboxApiException.RateLimited()
        val result = service.uploadList("CONTENT")
        assertTrue(result is DropboxResult.Failure)
        assertEquals(DropboxError.RateLimit, (result as DropboxResult.Failure).error)
    }

    @Test
    fun `download refreshes token and retries on Unauthorized`() = runTest {
        apiClient.downloadUnauthorizedOnFirstCall = true
        apiClient.downloadResponse = "CONTENT AFTER REFRESH"
        apiClient.refreshTokenResponse = "new_access_token"
        val result = service.downloadList()
        assertTrue(result is DropboxResult.Success)
        assertEquals("CONTENT AFTER REFRESH", (result as DropboxResult.Success).value)
        assertEquals("new_access_token", tokenStore["dropbox_access_token"])
    }

    @Test
    fun `download returns TokenExpired when refresh also fails`() = runTest {
        apiClient.downloadException = DropboxApiException.Unauthorized()
        apiClient.refreshException = DropboxApiException.Unauthorized()
        val result = service.downloadList()
        assertTrue(result is DropboxResult.Failure)
        assertEquals(DropboxError.TokenExpired, (result as DropboxResult.Failure).error)
    }
}

// ─── Fakes ───────────────────────────────────────────────────────────────────

class FakeTokenStore : TokenStore {
    private val map = mutableMapOf<String, String?>()
    override fun get(key: String): String? = map[key]
    override fun set(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

class FakeDropboxApiClient : DropboxApiClient {

    var downloadResponse: String = ""
    var downloadException: DropboxApiException? = null
    var downloadUnauthorizedOnFirstCall = false
    private var downloadCallCount = 0

    var uploadException: DropboxApiException? = null

    var refreshTokenResponse: String = "refreshed_token"
    var refreshException: DropboxApiException? = null

    override suspend fun listEntries(path: String, accessToken: String): DropboxEntries =
        DropboxEntries(emptyList(), emptyList())

    override suspend fun download(path: String, accessToken: String): String {
        downloadCallCount++
        if (downloadUnauthorizedOnFirstCall && downloadCallCount == 1) throw DropboxApiException.Unauthorized()
        downloadException?.let { throw it }
        return downloadResponse
    }

    override suspend fun upload(path: String, content: String, accessToken: String) {
        uploadException?.let { throw it }
    }

    override suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
        appKey: String
    ): Pair<String, String> = Pair("access_token", "refresh_token")

    override suspend fun refreshToken(refreshToken: String, appKey: String): String {
        refreshException?.let { throw it }
        return refreshTokenResponse
    }
}
