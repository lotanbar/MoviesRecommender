package com.moviesrecommender.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moviesrecommender.MoviesRecommenderApp
import com.moviesrecommender.data.remote.dropbox.DropboxResult
import com.moviesrecommender.data.remote.tmdb.MediaType
import com.moviesrecommender.data.remote.tmdb.Title
import com.moviesrecommender.data.remote.tmdb.TmdbResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PreviewUiState {
    object Loading : PreviewUiState()
    data class Loaded(
        val title: Title,
        val rating: Int?,
        val wikipediaUrl: String? = null,
        val wikipediaReady: Boolean = false,
        val isUploading: Boolean = false,
        val uploadError: Boolean = false
    ) : PreviewUiState()
    data class Error(val message: String) : PreviewUiState()
}

class PreviewViewModel(
    private val tmdbId: Int,
    private val mediaTypeStr: String
) : ViewModel() {

    private val app = MoviesRecommenderApp.instance
    private val tmdbService = app.tmdbService
    private val dropboxService = app.dropboxService
    private val wikidataApiClient = app.wikidataApiClient
    private val mediaType = if (mediaTypeStr == "TV") MediaType.TV else MediaType.MOVIE

    private var listContent: String? = null

    private val _uiState = MutableStateFlow<PreviewUiState>(PreviewUiState.Loading)
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() = coroutineScope {
        val detailsDeferred = async { tmdbService.fetchDetails(tmdbId, mediaType) }
        val listDeferred = async { dropboxService.downloadList() }
        val listResult = listDeferred.await()
        if (listResult is DropboxResult.Success) listContent = listResult.value
        when (val result = detailsDeferred.await()) {
            is TmdbResult.Success -> {
                val t = result.value
                _uiState.value = PreviewUiState.Loaded(
                    title = t,
                    rating = listContent?.let { SearchViewModel.parseRating(it, t.title) }
                )
                // Fetch Wikipedia URL in background; update state when ready
                launch {
                    val url = wikidataApiClient.getWikipediaUrl(tmdbId, mediaType == MediaType.MOVIE)
                    val current = _uiState.value as? PreviewUiState.Loaded ?: return@launch
                    _uiState.value = current.copy(wikipediaUrl = url, wikipediaReady = true)
                }
            }
            is TmdbResult.Failure -> _uiState.value = PreviewUiState.Error("Failed to load title")
        }
    }

    fun toggleInList() {
        val loaded = _uiState.value as? PreviewUiState.Loaded ?: return
        if (loaded.rating == null) saveRating(loaded, 0) else deleteFromList(loaded)
    }

    fun setRating(stars: Int) {
        val loaded = _uiState.value as? PreviewUiState.Loaded ?: return
        saveRating(loaded, stars)
    }

    private fun saveRating(loaded: PreviewUiState.Loaded, newRating: Int) {
        val t = loaded.title
        val updated = updateListRating(listContent ?: "", t.title, t.year, newRating)
        listContent = updated
        _uiState.value = loaded.copy(rating = newRating, isUploading = true, uploadError = false)
        viewModelScope.launch {
            val success = dropboxService.uploadList(updated) is DropboxResult.Success
            val current = _uiState.value as? PreviewUiState.Loaded ?: return@launch
            _uiState.value = current.copy(isUploading = false, uploadError = !success)
        }
    }

    private fun deleteFromList(loaded: PreviewUiState.Loaded) {
        val t = loaded.title
        val updated = removeEntry(listContent ?: "", t.title, t.year)
        listContent = updated
        _uiState.value = loaded.copy(rating = null, isUploading = true, uploadError = false)
        viewModelScope.launch {
            val success = dropboxService.uploadList(updated) is DropboxResult.Success
            val current = _uiState.value as? PreviewUiState.Loaded ?: return@launch
            _uiState.value = current.copy(isUploading = false, uploadError = !success)
        }
    }

    companion object {
        fun updateListRating(content: String, title: String, year: Int, newRating: Int): String {
            val entry = "- $title ($year)"
            val header = "RATING: $newRating"
            val lines = content.lines().filter { it.trim() != entry }.toMutableList()
            val headerIdx = lines.indexOfFirst { it.trim() == header }
            if (headerIdx >= 0) {
                lines.add(headerIdx + 1, entry)
            } else {
                if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
                lines.add(header)
                lines.add(entry)
            }
            return lines.joinToString("\n")
        }

        fun removeEntry(content: String, title: String, year: Int): String {
            val entry = "- $title ($year)"
            return content.lines().filter { it.trim() != entry }.joinToString("\n")
        }
    }
}

class PreviewViewModelFactory(
    private val tmdbId: Int,
    private val mediaTypeStr: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PreviewViewModel(tmdbId, mediaTypeStr) as T
}
