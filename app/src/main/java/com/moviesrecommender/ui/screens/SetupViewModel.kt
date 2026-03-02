package com.moviesrecommender.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moviesrecommender.BuildConfig
import com.moviesrecommender.MoviesRecommenderApp
import com.moviesrecommender.data.remote.anthropic.AnthropicError
import com.moviesrecommender.data.remote.anthropic.AnthropicResult
import com.moviesrecommender.data.remote.dropbox.DropboxResult
import com.moviesrecommender.data.remote.tmdb.TmdbError
import com.moviesrecommender.data.remote.tmdb.TmdbResult
import com.moviesrecommender.util.ToastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SetupViewModel : ViewModel() {

    private val app = MoviesRecommenderApp.instance
    private val dropboxService = app.dropboxService
    private val anthropicService = app.anthropicService
    private val tmdbService = app.tmdbService

    private val _dropboxAuthenticated = MutableStateFlow(dropboxService.isAuthenticated())
    val dropboxAuthenticated = _dropboxAuthenticated.asStateFlow()

    private val _listPath = MutableStateFlow(dropboxService.authManager.getListPath())
    val listPath = _listPath.asStateFlow()

    private val _listPathSet = MutableStateFlow(dropboxService.authManager.getListPath() != null)
    val listPathSet = _listPathSet.asStateFlow()

    private val _tmdbKeySet = MutableStateFlow(tmdbService.isConfigured())
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
        _listPath.value = path.trim().ifBlank { null }
        _listPathSet.value = path.isNotBlank()
    }

    fun saveTmdbKey(key: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = tmdbService.saveApiKeyAndValidate(key.trim())) {
                is TmdbResult.Success -> {
                    _tmdbKeySet.value = true
                    ToastManager.show("TMDB API key verified.")
                }
                is TmdbResult.Failure -> ToastManager.show(
                    when (result.error) {
                        TmdbError.InvalidApiKey -> "Invalid TMDB API key."
                        TmdbError.NoInternet -> "No internet connection."
                        is TmdbError.ApiError -> "TMDB request failed. Please try again."
                    }
                )
            }
            _isLoading.value = false
        }
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

    fun clearDropboxAuth() {
        dropboxService.authManager.clearTokens()
        dropboxService.authManager.clearListPath()
        _dropboxAuthenticated.value = false
        _listPath.value = null
        _listPathSet.value = false
    }

    fun clearListPath() {
        dropboxService.authManager.clearListPath()
        _listPath.value = null
        _listPathSet.value = false
    }

    fun clearTmdbKey() {
        tmdbService.clearApiKey()
        _tmdbKeySet.value = false
    }

    fun clearClaudeKey() {
        anthropicService.authManager.clearCredentials()
        _claudeKeySet.value = false
    }
}
