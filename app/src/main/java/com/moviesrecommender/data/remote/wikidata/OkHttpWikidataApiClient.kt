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
    // P4947 = TMDb movie ID, P4983 = TMDb TV series ID, P166 = award received
    suspend fun getAwards(tmdbId: Int, isMovie: Boolean): List<String> =
        withContext(Dispatchers.IO) {
            val property = if (isMovie) "P4947" else "P4983"
            val query = """
                SELECT DISTINCT ?awardLabel WHERE {
                  ?item wdt:$property "$tmdbId" .
                  ?item wdt:P166 ?award .
                  ?award rdfs:label ?awardLabel .
                  FILTER(LANG(?awardLabel) = "en")
                }
                LIMIT 50
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
                val body = response.body?.string() ?: return@withContext emptyList()
                if (!response.isSuccessful) return@withContext emptyList()
                val bindings = JSONObject(body)
                    .getJSONObject("results")
                    .getJSONArray("bindings")
                buildList {
                    for (i in 0 until bindings.length()) {
                        bindings.getJSONObject(i)
                            .optJSONObject("awardLabel")
                            ?.optString("value")
                            ?.takeIf { it.isNotBlank() && isNotableAward(it) }
                            ?.let { if (!contains(it)) add(it) }
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun getWikipediaUrl(tmdbId: Int, isMovie: Boolean, titleFallback: String? = null): String? =
        withContext(Dispatchers.IO) {
            // Primary: resolve via Wikidata SPARQL using TMDB ID
            val wikidataUrl = fetchWikipediaUrlFromWikidata(tmdbId, isMovie)
            if (wikidataUrl != null) return@withContext wikidataUrl

            // Fallback: ask Wikipedia's REST summary API directly by title
            if (titleFallback != null) fetchWikipediaUrlByTitle(titleFallback) else null
        }

    private fun isNotableAward(label: String): Boolean {
        val l = label.lowercase()
        return "academy award" in l || "oscar" in l ||
               "emmy" in l ||
               "bafta" in l ||
               "golden globe" in l ||
               "annie award" in l ||
               "peabody" in l ||
               "writers guild" in l ||
               "hugo award" in l ||
               "palme d'or" in l || "palme d\u2019or" in l ||
               "golden lion" in l
    }

    private fun fetchWikipediaUrlFromWikidata(tmdbId: Int, isMovie: Boolean): String? {
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
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MoviesRecommenderApp/1.0 (Android)")
                .header("Accept", "application/sparql-results+json")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            if (!response.isSuccessful) return null
            val bindings = JSONObject(body)
                .getJSONObject("results")
                .getJSONArray("bindings")
            if (bindings.length() == 0) null
            else bindings.getJSONObject(0).getJSONObject("article").getString("value")
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchWikipediaUrlByTitle(title: String): String? {
        val encoded = URLEncoder.encode(title, "UTF-8")
        val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MoviesRecommenderApp/1.0 (Android)")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            JSONObject(body)
                .optJSONObject("content_urls")
                ?.optJSONObject("desktop")
                ?.optString("page")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
