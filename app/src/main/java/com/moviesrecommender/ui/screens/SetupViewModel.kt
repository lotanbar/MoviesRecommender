package com.moviesrecommender.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moviesrecommender.BuildConfig
import com.moviesrecommender.MoviesRecommenderApp
import com.moviesrecommender.data.remote.anthropic.AnthropicError
import com.moviesrecommender.data.remote.anthropic.AnthropicResult
import com.moviesrecommender.data.remote.dropbox.DropboxResult
import com.moviesrecommender.util.ToastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SetupViewModel : ViewModel() {

    private val app = MoviesRecommenderApp.instance
    private val dropboxService = app.dropboxService
    private val anthropicService = app.anthropicService
    private val tokenStore = app.tokenStore

    private val _dropboxAuthenticated = MutableStateFlow(dropboxService.isAuthenticated())
    val dropboxAuthenticated = _dropboxAuthenticated.asStateFlow()

    private val _listPathSet = MutableStateFlow(dropboxService.authManager.getListPath() != null)
    val listPathSet = _listPathSet.asStateFlow()

    private val _tmdbKeySet = MutableStateFlow(tokenStore[KEY_TMDB] != null)
    val tmdbKeySet = _tmdbKeySet.asStateFlow()

    private val _claudeKeySet = MutableStateFlow(anthropicService.isConfigured())
    val claudeKeySet = _claudeKeySet.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun getDropboxAuthUrl(): String = dropboxService.getAuthUrl(BuildConfig.DROPBOX_APP_KEY)

    fun handleDropboxCallback(code: String) {
        viewModelScope.launch {
            when (val result = dropboxService.handleAuthCallback(code)) {
                is DropboxResult.Success -> _dropboxAuthenticated.value = true
                is DropboxResult.Failure -> ToastManager.show(
                    "Dropbox auth failed: ${result.error}"
                )
            }
        }
    }

    fun saveListPath(path: String) {
        dropboxService.authManager.setListPath(path.trim())
        _listPathSet.value = path.isNotBlank()
    }

    fun saveTmdbKey(key: String) {
        tokenStore[KEY_TMDB] = key.trim()
        _tmdbKeySet.value = key.isNotBlank()
    }

    fun saveClaudeKey(key: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = anthropicService.saveApiKeyAndSelectModel(key.trim())) {
                is AnthropicResult.Success -> {
                    _claudeKeySet.value = true
                    ToastManager.show("Model selected: ${result.value}")
                }
                is AnthropicResult.Failure -> ToastManager.show(
                    when (result.error) {
                        AnthropicError.InvalidApiKey -> "Claude request failed. Please try again."
                        AnthropicError.NoSonnetModelFound -> "No Sonnet Thinking model found."
                        AnthropicError.NoInternet -> "No internet connection."
                        else -> "Claude request failed. Please try again."
                    }
                )
            }
            _isLoading.value = false
        }
    }

    companion object {
        const val KEY_TMDB = "tmdb_api_key"
    }
}
