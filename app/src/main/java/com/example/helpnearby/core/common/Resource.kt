package com.example.helpnearby.core.common

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val retryable: Boolean = true) : UiState<Nothing>
}

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}
