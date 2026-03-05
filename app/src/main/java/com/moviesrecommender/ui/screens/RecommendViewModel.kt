package com.moviesrecommender.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moviesrecommender.MoviesRecommenderApp
import com.moviesrecommender.data.remote.anthropic.AnthropicResult
import com.moviesrecommender.data.remote.dropbox.DropboxError
import com.moviesrecommender.data.remote.dropbox.DropboxResult
import com.moviesrecommender.data.remote.tmdb.TmdbResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RecommendUiState {
    object Loading : RecommendUiState()
    data class Error(val message: String) : RecommendUiState()
}

class RecommendViewModel : ViewModel() {

    private val app = MoviesRecommenderApp.instance

    private val _uiState = MutableStateFlow<RecommendUiState>(RecommendUiState.Loading)
    val uiState: StateFlow<RecommendUiState> = _uiState.asStateFlow()

    private val _navigateToPreview = MutableSharedFlow<Pair<Int, String>>(extraBufferCapacity = 1)
    val navigateToPreview: SharedFlow<Pair<Int, String>> = _navigateToPreview.asSharedFlow()

    private var hasNavigatedToPreview = false

    init {
        startRecommend()
    }

    fun startRecommend() {
        viewModelScope.launch {
            _uiState.value = RecommendUiState.Loading

            // Use cached list from previous step; download only if not available
            val listContent = app.cachedListContent
                ?: when (val r = app.dropboxService.downloadList()) {
                    is DropboxResult.Success -> r.value.also { app.cachedListContent = it }
                    is DropboxResult.Failure -> {
                        _uiState.value = RecommendUiState.Error(r.error.toMessage())
                        return@launch
                    }
                }

            // Build initial prompt and retry up to 5 times on bad format
            var prompt = "$listContent\n\nrecommend"
            for (attempt in 0 until 5) {
                val result = app.anthropicService.sendRawMessage(prompt)
                if (result is AnthropicResult.Failure) {
                    _uiState.value = RecommendUiState.Error("Claude request failed. Please try again.")
                    return@launch
                }
                val response = (result as AnthropicResult.Success).value
                val parsed = parseTitleYear(response)
                if (parsed == null) {
                    prompt = "$listContent\n\nrecommend\n\n$response\n\nMake sure you provide title and year only!"
                    continue
                }

                when (val tmdbResult = app.tmdbService.fetchMetadata(parsed.first, parsed.second)) {
                    is TmdbResult.Success -> {
                        val t = tmdbResult.value
                        hasNavigatedToPreview = true
                        _navigateToPreview.emit(Pair(t.id, t.mediaType.name))
                        return@launch
                    }
                    is TmdbResult.Failure -> {
                        _uiState.value = RecommendUiState.Error("Search unavailable: Could not reach TMDB.")
                        return@launch
                    }
                }
            }

            _uiState.value = RecommendUiState.Error("Claude request failed. Please try again.")
        }
    }

    /** Called when the screen resumes — re-runs recommend if returning from Preview. */
    fun onScreenResumed() {
        if (hasNavigatedToPreview) {
            hasNavigatedToPreview = false
            startRecommend()
        }
    }

    companion object {
        private val TITLE_YEAR_REGEX = Regex("""^(.+?)\s*\((\d{4})\)\s*$""")

        fun parseTitleYear(response: String): Pair<String, Int>? {
            val match = TITLE_YEAR_REGEX.matchEntire(response.trim()) ?: return null
            val title = match.groupValues[1].trim()
            val year = match.groupValues[2].toIntOrNull() ?: return null
            return Pair(title, year)
        }
    }
}

private fun DropboxError.toMessage(): String = when (this) {
    DropboxError.NoInternet -> "Upload failed: No internet connection."
    DropboxError.TokenExpired -> "Upload failed: Dropbox session expired - please re-authenticate."
    DropboxError.StorageFull -> "Upload failed: Dropbox storage is full."
    DropboxError.RateLimit -> "Upload failed: Too many requests. Try again shortly."
    is DropboxError.Unknown -> "Download failed: $message"
}
