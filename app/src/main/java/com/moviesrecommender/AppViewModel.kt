package com.moviesrecommender

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class AppViewModel : ViewModel() {
    private val _dropboxAuthCode = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val dropboxAuthCode: SharedFlow<String> = _dropboxAuthCode

    fun onDropboxAuthCode(code: String) {
        _dropboxAuthCode.tryEmit(code)
    }
}
