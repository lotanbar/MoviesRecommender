package com.moviesrecommender.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moviesrecommender.MoviesRecommenderApp
import com.moviesrecommender.data.remote.dropbox.DropboxResult
import com.moviesrecommender.data.remote.tmdb.Title
import com.moviesrecommender.data.remote.tmdb.TmdbResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class TitleWithRating(val title: Title, val rating: Int?)

@OptIn(FlowPreview::class)
class SearchViewModel : ViewModel() {

    private val app = MoviesRecommenderApp.instance
    private val tmdbService = app.tmdbService
    private val dropboxService = app.dropboxService

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<TitleWithRating>>(emptyList())
    val results: StateFlow<List<TitleWithRating>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var listContent: String? = null

    init {
        viewModelScope.launch { downloadList() }
        viewModelScope.launch {
            _query
                .debounce(300)
                .collectLatest { q ->
                    if (q.length >= 2) performSearch(q)
                    else _results.value = emptyList()
                }
        }
    }

    fun onQueryChange(q: String) { _query.value = q }

    private suspend fun downloadList() {
        when (val result = dropboxService.downloadList()) {
            is DropboxResult.Success -> listContent = result.value
            is DropboxResult.Failure -> Unit
        }
    }

    private suspend fun performSearch(query: String) {
        _isSearching.value = true
        when (val result = tmdbService.search(query)) {
            is TmdbResult.Success -> {
                val content = listContent
                _results.value = result.value.map { title ->
                    TitleWithRating(
                        title = title,
                        rating = content?.let { parseRating(it, title.title) }
                    )
                }
            }
            is TmdbResult.Failure -> _results.value = emptyList()
        }
        _isSearching.value = false
    }

    companion object {
        fun parseRating(listContent: String, titleToFind: String): Int? {
            var currentRating: Int? = null
            val normalized = titleToFind.lowercase().trim()
            for (line in listContent.lines()) {
                val trimmed = line.trim()
                val ratingMatch = Regex("^RATING:\\s*(\\d+)$").find(trimmed)
                if (ratingMatch != null) {
                    currentRating = ratingMatch.groupValues[1].toIntOrNull()
                    continue
                }
                if (trimmed.startsWith("- ")) {
                    val titlePart = trimmed.removePrefix("- ").substringBefore("(").trim().lowercase()
                    if (titlePart == normalized) return currentRating
                }
            }
            return null
        }
    }
}
