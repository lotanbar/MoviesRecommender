package com.moviesrecommender.data.local

import android.content.SharedPreferences
import com.moviesrecommender.data.remote.dropbox.TokenStore

class SharedPrefsTokenStore(private val prefs: SharedPreferences) : TokenStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun set(key: String, value: String?) {
        prefs.edit().run {
            if (value == null) remove(key) else putString(key, value)
            apply()
        }
    }
}
