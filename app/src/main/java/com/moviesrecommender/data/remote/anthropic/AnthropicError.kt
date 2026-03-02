package com.moviesrecommender.data.remote.anthropic

sealed class AnthropicError {
    data object ApiKeyMissing : AnthropicError()
    data object ModelNotSelected : AnthropicError()
    data object InvalidApiKey : AnthropicError()
    data object NoInternet : AnthropicError()
    data object NoSonnetModelFound : AnthropicError()
    data class ApiError(val message: String) : AnthropicError()
}

sealed class AnthropicResult<out T> {
    data class Success<out T>(val value: T) : AnthropicResult<T>()
    data class Failure(val error: AnthropicError) : AnthropicResult<Nothing>()
}
