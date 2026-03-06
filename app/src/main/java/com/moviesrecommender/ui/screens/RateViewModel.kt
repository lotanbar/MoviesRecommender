package com.moviesrecommender.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moviesrecommender.MoviesRecommenderApp
import com.moviesrecommender.data.remote.anthropic.AnthropicError
import com.moviesrecommender.data.remote.anthropic.AnthropicResult
import com.moviesrecommender.data.remote.dropbox.DropboxError
import com.moviesrecommender.data.remote.dropbox.DropboxResult
import com.moviesrecommender.data.remote.tmdb.TmdbResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RateUiState {
    object Loading : RateUiState()
    object Uploading : RateUiState()
    data class Error(val message: String, val debugInfo: String? = null) : RateUiState()
}

class RateViewModel : ViewModel() {

    private val app = MoviesRecommenderApp.instance

    private val _uiState = MutableStateFlow<RateUiState>(RateUiState.Loading)
    val uiState: StateFlow<RateUiState> = _uiState.asStateFlow()

    private val _navigateToPreview = MutableSharedFlow<Pair<Int, String>>(extraBufferCapacity = 1)
    val navigateToPreview: SharedFlow<Pair<Int, String>> = _navigateToPreview.asSharedFlow()

    private val _navigateToActions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToActions: SharedFlow<Unit> = _navigateToActions.asSharedFlow()

    // Queue of (tmdbId, mediaType) for the current batch of 5
    private var queue: List<Pair<Int, String>> = emptyList()
    private var currentIndex = 0
    private var hasNavigatedToPreview = false
    private var isBusy = false

    init {
        app.rateMode = true
        startBatch()
    }

    override fun onCleared() {
        super.onCleared()
        app.rateMode = false
    }

    fun startBatch() {
        viewModelScope.launch {
            _uiState.value = RateUiState.Loading
            queue = emptyList()
            currentIndex = 0

            val listContent = app.cachedListContent
                ?: when (val r = app.dropboxService.downloadList()) {
                    is DropboxResult.Success -> r.value.also { app.cachedListContent = it }
                    is DropboxResult.Failure -> {
                        _uiState.value = RateUiState.Error(r.error.toDownloadMessage())
                        return@launch
                    }
                }

            Log.d("Rate", "listContent length=${listContent.length}")

            val listBody = listContent
                .substringAfter("================================================================================")
                .trimStart('\r', '\n')

            val systemPrompt =
                "You MUST respond with EXACTLY 5 movie or show titles and years, one per line, " +
                "numbered 1–5, in this exact format:\n" +
                "1. Title (Year)\n2. Title (Year)\n3. Title (Year)\n4. Title (Year)\n5. Title (Year)\n" +
                "No explanation, no markdown, no other text. Just 5 numbered lines."

            val messages = mutableListOf("user" to "$listBody\n\nrate")
            var lastResponse = ""
            var titles: List<Pair<String, Int>> = emptyList()

            for (attempt in 0 until 5) {
                val result = app.anthropicService.sendMessages(messages, systemPrompt)
                if (result is AnthropicResult.Failure) {
                    _uiState.value = RateUiState.Error(result.error.toMessage())
                    return@launch
                }
                lastResponse = (result as AnthropicResult.Success).value
                Log.d("Rate", "Attempt $attempt response: [$lastResponse]")
                messages.add("assistant" to lastResponse)

                titles = parseRateTitles(lastResponse)
                if (titles.size == 5) break
                messages.add("user" to "Please provide exactly 5 titles, each on its own line:\n1. Title (Year)\n2. Title (Year)\n3. Title (Year)\n4. Title (Year)\n5. Title (Year)")
            }

            if (titles.isEmpty()) {
                _uiState.value = RateUiState.Error(
                    "Could not get 5 titles from Claude.",
                    "Last response: [$lastResponse]"
                )
                return@launch
            }

            // TMDB lookup for all titles in parallel
            val tmdbResults = titles.map { (title, year) ->
                async { app.tmdbService.fetchMetadata(title, year) }
            }.awaitAll()

            queue = tmdbResults.mapIndexedNotNull { i, result ->
                when (result) {
                    is TmdbResult.Success -> Pair(result.value.id, result.value.mediaType.name)
                    is TmdbResult.Failure -> {
                        Log.w("Rate", "TMDB failed for ${titles[i]}: ${result.error}")
                        null
                    }
                }
            }

            if (queue.isEmpty()) {
                _uiState.value = RateUiState.Error(
                    "Could not find any of the suggested titles on TMDB.",
                    "Claude suggested: ${titles.joinToString { "${it.first} (${it.second})" }}"
                )
                return@launch
            }

            hasNavigatedToPreview = true
            _navigateToPreview.emit(queue[0])
        }
    }

    fun onResumedFromPreview() {
        if (!hasNavigatedToPreview) return
        hasNavigatedToPreview = false
        currentIndex++
        if (currentIndex < queue.size) {
            hasNavigatedToPreview = true
            viewModelScope.launch { _navigateToPreview.emit(queue[currentIndex]) }
        } else {
            uploadAndRepeat()
        }
    }

    fun onBackPressed() {
        if (isBusy) return
        isBusy = true
        viewModelScope.launch {
            _uiState.value = RateUiState.Uploading
            val content = app.cachedListContent
            if (content != null) {
                app.dropboxService.uploadList(content)
            }
            app.rateMode = false
            app.cachedListContent = null
            isBusy = false
            _navigateToActions.emit(Unit)
        }
    }

    private fun uploadAndRepeat() {
        viewModelScope.launch {
            _uiState.value = RateUiState.Uploading
            val content = app.cachedListContent
            if (content != null) {
                when (val r = app.dropboxService.uploadList(content)) {
                    is DropboxResult.Failure -> {
                        _uiState.value = RateUiState.Error(r.error.toUploadMessage())
                        return@launch
                    }
                    else -> {}
                }
            }
            // Cache already matches what was just uploaded — reuse it for the next batch
            startBatch()
        }
    }

    companion object {
        private val NUMBERED_REGEX = Regex("""^\d+\.\s+(.+?)\s*\((\d{4})\)\s*$""")

        fun parseRateTitles(response: String): List<Pair<String, Int>> =
            response.lines().mapNotNull { line ->
                val clean = line.trim().replace(Regex("""[*_]+"""), "")
                val match = NUMBERED_REGEX.matchEntire(clean) ?: return@mapNotNull null
                val title = match.groupValues[1].trim()
                val year = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                Pair(title, year)
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

private fun DropboxError.toDownloadMessage(): String = when (this) {
    DropboxError.NoInternet -> "Download failed: No internet connection."
    DropboxError.TokenExpired -> "Dropbox session expired - please re-authenticate."
    DropboxError.StorageFull -> "Dropbox storage is full."
    DropboxError.RateLimit -> "Too many requests. Try again shortly."
    is DropboxError.Unknown -> "Download failed: $message"
}

private fun DropboxError.toUploadMessage(): String = when (this) {
    DropboxError.NoInternet -> "Upload failed: No internet connection."
    DropboxError.TokenExpired -> "Upload failed: Dropbox session expired - please re-authenticate."
    DropboxError.StorageFull -> "Upload failed: Dropbox storage is full."
    DropboxError.RateLimit -> "Upload failed: Too many requests. Try again shortly."
    is DropboxError.Unknown -> "Upload failed: $message"
}
