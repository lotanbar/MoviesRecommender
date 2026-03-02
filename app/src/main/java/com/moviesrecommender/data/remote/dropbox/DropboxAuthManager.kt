package com.moviesrecommender.data.remote.dropbox

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

interface TokenStore {
    operator fun get(key: String): String?
    operator fun set(key: String, value: String?)
}

class DropboxAuthManager(private val store: TokenStore) {

    companion object {
        const val REDIRECT_URI = "moviesrecommender://dropbox/auth"

        private const val KEY_ACCESS_TOKEN = "dropbox_access_token"
        private const val KEY_REFRESH_TOKEN = "dropbox_refresh_token"
        private const val KEY_APP_KEY = "dropbox_app_key"
        private const val KEY_LIST_PATH = "dropbox_list_path"
        private const val KEY_CODE_VERIFIER = "dropbox_code_verifier"
    }

    fun buildAuthUrl(appKey: String): String {
        val verifier = generateCodeVerifier()
        store[KEY_CODE_VERIFIER] = verifier
        store[KEY_APP_KEY] = appKey
        val challenge = generateCodeChallenge(verifier)
        return "https://www.dropbox.com/oauth2/authorize" +
            "?client_id=$appKey" +
            "&response_type=code" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&redirect_uri=$REDIRECT_URI" +
            "&token_access_type=offline"
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        store[KEY_ACCESS_TOKEN] = accessToken
        store[KEY_REFRESH_TOKEN] = refreshToken
        store[KEY_CODE_VERIFIER] = null
    }

    fun updateAccessToken(accessToken: String) {
        store[KEY_ACCESS_TOKEN] = accessToken
    }

    fun getAccessToken(): String? = store[KEY_ACCESS_TOKEN]
    fun getRefreshToken(): String? = store[KEY_REFRESH_TOKEN]
    fun getAppKey(): String? = store[KEY_APP_KEY]
    fun getCodeVerifier(): String? = store[KEY_CODE_VERIFIER]

    fun isAuthenticated(): Boolean = store[KEY_ACCESS_TOKEN] != null

    fun setListPath(path: String) { store[KEY_LIST_PATH] = path }
    fun getListPath(): String? = store[KEY_LIST_PATH]

    fun clearTokens() {
        store[KEY_ACCESS_TOKEN] = null
        store[KEY_REFRESH_TOKEN] = null
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
