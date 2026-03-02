package com.moviesrecommender.data.remote.wikidata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class OkHttpWikidataApiClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    // P4947 = TMDb movie ID, P4983 = TMDb TV series ID
    suspend fun getWikipediaUrl(tmdbId: Int, isMovie: Boolean): String? =
        withContext(Dispatchers.IO) {
            val property = if (isMovie) "P4947" else "P4983"
            val query = """
                SELECT ?article WHERE {
                  ?item wdt:$property "$tmdbId" .
                  ?article schema:about ?item ;
                           schema:inLanguage "en" ;
                           schema:isPartOf <https://en.wikipedia.org/> .
                }
                LIMIT 1
            """.trimIndent()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://query.wikidata.org/sparql?query=$encoded&format=json"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MoviesRecommenderApp/1.0 (Android)")
                    .header("Accept", "application/sparql-results+json")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null
                val bindings = JSONObject(body)
                    .getJSONObject("results")
                    .getJSONArray("bindings")
                if (bindings.length() == 0) null
                else bindings.getJSONObject(0).getJSONObject("article").getString("value")
            } catch (e: Exception) {
                null
            }
        }
}
