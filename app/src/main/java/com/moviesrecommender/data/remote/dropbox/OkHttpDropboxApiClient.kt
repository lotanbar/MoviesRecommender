package com.moviesrecommender.data.remote.dropbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException

class OkHttpDropboxApiClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) : DropboxApiClient {

    private val octetStream = "application/octet-stream".toMediaType()
    private val formEncoded = "application/x-www-form-urlencoded".toMediaType()

    override suspend fun download(path: String, accessToken: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/download")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Dropbox-API-Arg", """{"path":"$path"}""")
                .post("".toRequestBody(octetStream))
                .build()
            execute(request)
        }

    override suspend fun upload(path: String, content: String, accessToken: String) =
        withContext(Dispatchers.IO) {
            val arg = """{"path":"$path","mode":"overwrite","autorename":false}"""
            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/upload")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Dropbox-API-Arg", arg)
                .post(content.toRequestBody(octetStream))
                .build()
            execute(request)
            Unit
        }

    override suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
        appKey: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val body = "code=$code" +
            "&grant_type=authorization_code" +
            "&code_verifier=$codeVerifier" +
            "&client_id=$appKey" +
            "&redirect_uri=${DropboxAuthManager.REDIRECT_URI}"
        val request = Request.Builder()
            .url("https://api.dropboxapi.com/oauth2/token")
            .post(body.toRequestBody(formEncoded))
            .build()
        val json = JSONObject(execute(request))
        Pair(json.getString("access_token"), json.getString("refresh_token"))
    }

    override suspend fun refreshToken(refreshToken: String, appKey: String): String =
        withContext(Dispatchers.IO) {
            val body = "refresh_token=$refreshToken&grant_type=refresh_token&client_id=$appKey"
            val request = Request.Builder()
                .url("https://api.dropboxapi.com/oauth2/token")
                .post(body.toRequestBody(formEncoded))
                .build()
            JSONObject(execute(request)).getString("access_token")
        }

    private fun execute(request: Request): String {
        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            when {
                response.code in 200..299 -> body
                response.code == 401 -> throw DropboxApiException.Unauthorized()
                response.code == 429 -> throw DropboxApiException.RateLimited()
                response.code == 507 -> throw DropboxApiException.InsufficientStorage()
                else -> throw DropboxApiException.ServerError("HTTP ${response.code}: $body")
            }
        } catch (e: DropboxApiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw DropboxApiException.NoNetwork()
        } catch (e: IOException) {
            throw DropboxApiException.NoNetwork()
        }
    }
}
