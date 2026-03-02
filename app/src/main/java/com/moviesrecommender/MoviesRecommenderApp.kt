package com.moviesrecommender

import android.app.Application
import com.moviesrecommender.data.local.SharedPrefsTokenStore
import com.moviesrecommender.data.remote.anthropic.AnthropicAuthManager
import com.moviesrecommender.data.remote.anthropic.AnthropicService
import com.moviesrecommender.data.remote.anthropic.OkHttpAnthropicApiClient
import com.moviesrecommender.data.remote.dropbox.DropboxAuthManager
import com.moviesrecommender.data.remote.dropbox.DropboxService
import com.moviesrecommender.data.remote.dropbox.OkHttpDropboxApiClient
import com.moviesrecommender.data.remote.dropbox.TokenStore
import com.moviesrecommender.data.remote.tmdb.OkHttpTmdbApiClient
import com.moviesrecommender.data.remote.tmdb.TmdbService
import com.moviesrecommender.util.ToastManager

class MoviesRecommenderApp : Application() {

    val tokenStore: TokenStore by lazy {
        SharedPrefsTokenStore(getSharedPreferences("app_prefs", MODE_PRIVATE))
    }

    val dropboxService: DropboxService by lazy {
        DropboxService(DropboxAuthManager(tokenStore), OkHttpDropboxApiClient())
    }

    val anthropicService: AnthropicService by lazy {
        AnthropicService(AnthropicAuthManager(tokenStore), OkHttpAnthropicApiClient())
    }

    val tmdbService: TmdbService by lazy {
        TmdbService(tokenStore, OkHttpTmdbApiClient())
    }

    companion object {
        lateinit var instance: MoviesRecommenderApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ToastManager.init(this)
    }
}
