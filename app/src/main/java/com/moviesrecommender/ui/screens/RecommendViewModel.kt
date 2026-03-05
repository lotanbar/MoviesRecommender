package com.moviesrecommender.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moviesrecommender.MoviesRecommenderApp
import com.moviesrecommender.data.remote.anthropic.AnthropicError
import com.moviesrecommender.data.remote.anthropic.AnthropicResult
import com.moviesrecommender.data.remote.dropbox.DropboxError
import com.moviesrecommender.data.remote.dropbox.DropboxResult
import com.moviesrecommender.data.remote.tmdb.TmdbError
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
    data class Error(val message: String, val debugInfo: String? = null) : RecommendUiState()
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

            val listContent = app.cachedListContent
                ?: when (val r = app.dropboxService.downloadList()) {
                    is DropboxResult.Success -> r.value.also { app.cachedListContent = it }
                    is DropboxResult.Failure -> {
                        _uiState.value = RecommendUiState.Error(r.error.toMessage())
                        return@launch
                    }
                }

            Log.d("Recommend", "listContent length=${listContent.length}")

            // The file already contains full instructions for Claude.
            // We add only a strict output-format enforcement in the system prompt.
            val systemPrompt = "You MUST respond with ONLY the movie or show title and year, " +
                "in this exact format: Title (Year)\n" +
                "No explanation, no markdown, no other text. Just: Title (Year)"

            var userMessage = "$listContent\n\nrecommend"
            var lastResponse = ""
            for (attempt in 0 until 5) {
                val result = app.anthropicService.sendRawMessage(userMessage, systemPrompt)
                if (result is AnthropicResult.Failure) {
                    _uiState.value = RecommendUiState.Error(result.error.toMessage())
                    return@launch
                }
                lastResponse = (result as AnthropicResult.Success).value
                Log.d("Recommend", "Attempt $attempt response: [$lastResponse]")
                val parsed = parseTitleYear(lastResponse)
                Log.d("Recommend", "Parsed: $parsed")
                if (parsed == null) {
                    userMessage = "$listContent\n\nrecommend\n\n$lastResponse\n\nMake sure you provide title and year only!"
                    continue
                }

                val tmdbResult = app.tmdbService.fetchMetadata(parsed.first, parsed.second)
                Log.d("Recommend", "TMDB result for ${parsed.first} (${parsed.second}): $tmdbResult")
                when (tmdbResult) {
                    is TmdbResult.Success -> {
                        val t = tmdbResult.value
                        hasNavigatedToPreview = true
                        _navigateToPreview.emit(Pair(t.id, t.mediaType.name))
                        return@launch
                    }
                    is TmdbResult.Failure -> {
                        _uiState.value = RecommendUiState.Error(
                            message = tmdbResult.error.toMessage(),
                            debugInfo = "Claude said: \"$lastResponse\"\nParsed: title=\"${parsed.first}\" year=${parsed.second}"
                        )
                        return@launch
                    }
                }
            }

            _uiState.value = RecommendUiState.Error(
                message = "Could not parse a valid title from Claude's response.",
                debugInfo = "Last response: [$lastResponse]"
            )
        }
    }

    fun onScreenResumed() {
        if (hasNavigatedToPreview) {
            hasNavigatedToPreview = false
            startRecommend()
        }
    }

    companion object {
        private val EXACT_REGEX = Regex("""^(.+?)\s*\((\d{4})\)\s*$""")

        fun parseTitleYear(response: String): Pair<String, Int>? {
            val clean = response.trim().replace(Regex("""[*_]+"""), "")
            val match = EXACT_REGEX.matchEntire(clean) ?: return null
            val title = match.groupValues[1].trim()
            val year = match.groupValues[2].toIntOrNull() ?: return null
            return Pair(title, year)
        }
    }
}

private fun AnthropicError.toMessage(): String = when (this) {
    AnthropicError.ApiKeyMissing -> "Claude API key not configured. Please go to Setup."
    AnthropicError.ModelNotSelected -> "Claude model not selected. Please go to Setup."
    AnthropicError.InvalidApiKey -> "Invalid Claude API key. Please go to Setup."
    AnthropicError.NoInternet -> "Claude request failed: No internet connection."
    AnthropicError.NoSonnetModelFound -> "No Claude model found. Please go to Setup."
    is AnthropicError.ApiError -> "Claude request failed: $message"
}

private fun DropboxError.toMessage(): String = when (this) {
    DropboxError.NoInternet -> "Download failed: No internet connection."
    DropboxError.TokenExpired -> "Dropbox session expired - please re-authenticate."
    DropboxError.StorageFull -> "Dropbox storage is full."
    DropboxError.RateLimit -> "Too many requests. Try again shortly."
    is DropboxError.Unknown -> "Download failed: $message"
}

private fun TmdbError.toMessage(): String = when (this) {
    TmdbError.NoInternet -> "Search unavailable: No internet connection."
    TmdbError.InvalidApiKey -> "Invalid TMDB API key. Please go to Setup."
    TmdbError.NotFound -> "Title not found on TMDB."
    is TmdbError.ApiError -> "TMDB error: $message"
}
