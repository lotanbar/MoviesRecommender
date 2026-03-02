package com.moviesrecommender.data.remote.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.net.UnknownHostException

class OkHttpTmdbApiClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) : TmdbApiClient {

    override suspend fun validateApiKey(apiKey: String): Unit = withContext(Dispatchers.IO) {
        executeGet("https://api.themoviedb.org/3/authentication?api_key=$apiKey")
    }

    override suspend fun search(query: String, apiKey: String, page: Int): SearchPage =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val root = JSONObject(
                executeGet("https://api.themoviedb.org/3/search/multi?query=$encoded&api_key=$apiKey&page=$page")
            )
            val totalPages = root.optInt("total_pages", 1)
            val results = root.getJSONArray("results")
            val titles = mutableListOf<Title>()
            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                val mediaType = when (obj.optString("media_type")) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.TV
                    else -> continue
                }
                val title = if (mediaType == MediaType.MOVIE) obj.optString("title")
                            else obj.optString("name")
                val dateStr = if (mediaType == MediaType.MOVIE) obj.optString("release_date")
                              else obj.optString("first_air_date")
                val year = dateStr.take(4).toIntOrNull() ?: continue
                titles.add(
                    Title(
                        id = obj.getInt("id"),
                        title = title,
                        year = year,
                        posterPath = obj.optString("poster_path").takeIf { it.isNotEmpty() },
                        overview = obj.optString("overview").takeIf { it.isNotEmpty() },
                        genres = emptyList(),
                        country = null,
                        director = null,
                        leadActors = emptyList(),
                        productionCompany = null,
                        mediaType = mediaType
                    )
                )
            }
            SearchPage(titles, totalPages)
        }

    override suspend fun fetchDetails(id: Int, mediaType: MediaType, apiKey: String): Title =
        withContext(Dispatchers.IO) {
            val endpoint = if (mediaType == MediaType.MOVIE) "movie" else "tv"
            val body = executeGet(
                "https://api.themoviedb.org/3/$endpoint/$id?api_key=$apiKey&append_to_response=credits"
            )
            val obj = JSONObject(body)

            val title = if (mediaType == MediaType.MOVIE) obj.optString("title")
                        else obj.optString("name")
            val dateStr = if (mediaType == MediaType.MOVIE) obj.optString("release_date")
                          else obj.optString("first_air_date")
            val year = dateStr.take(4).toIntOrNull() ?: 0

            val genres = buildList {
                val arr = obj.optJSONArray("genres") ?: return@buildList
                for (i in 0 until arr.length()) add(arr.getJSONObject(i).getString("name"))
            }

            val country = obj.optJSONArray("origin_country")?.optString(0)?.takeIf { it.isNotEmpty() }

            val productionCompany = obj.optJSONArray("production_companies")
                ?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotEmpty() }

            val credits = obj.optJSONObject("credits")

            val leadActors = buildList {
                val cast = credits?.optJSONArray("cast") ?: return@buildList
                for (i in 0 until minOf(cast.length(), 3)) {
                    cast.getJSONObject(i).optString("name").takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }

            var director: String? = null
            val crew = credits?.optJSONArray("crew")
            if (crew != null) {
                for (i in 0 until crew.length()) {
                    val member = crew.getJSONObject(i)
                    if (member.optString("job") == "Director") {
                        director = member.optString("name").takeIf { it.isNotEmpty() }
                        break
                    }
                }
            }

            Title(
                id = id,
                title = title,
                year = year,
                posterPath = obj.optString("poster_path").takeIf { it.isNotEmpty() },
                overview = obj.optString("overview").takeIf { it.isNotEmpty() },
                genres = genres,
                country = country,
                director = director,
                leadActors = leadActors,
                productionCompany = productionCompany,
                mediaType = mediaType
            )
        }

    private fun executeGet(url: String): String {
        return try {
            val response = httpClient.newCall(Request.Builder().url(url).get().build()).execute()
            val body = response.body?.string() ?: ""
            when {
                response.code in 200..299 -> body
                response.code == 401 -> throw TmdbApiException.Unauthorized()
                else -> throw TmdbApiException.ServerError("HTTP ${response.code}: $body")
            }
        } catch (e: TmdbApiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw TmdbApiException.NoNetwork()
        } catch (e: IOException) {
            throw TmdbApiException.NoNetwork()
        }
    }
}
