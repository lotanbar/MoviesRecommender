package com.moviesrecommender.data.remote.tmdb

sealed class TmdbError {
    object InvalidApiKey : TmdbError()
    object NoInternet : TmdbError()
    data class ApiError(val message: String) : TmdbError()
}

sealed class TmdbResult<out T> {
    data class Success<T>(val value: T) : TmdbResult<T>()
    data class Failure(val error: TmdbError) : TmdbResult<Nothing>()
}

sealed class TmdbApiException : Exception() {
    class Unauthorized : TmdbApiException()
    class NoNetwork : TmdbApiException()
    class ServerError(override val message: String?) : TmdbApiException()
}
