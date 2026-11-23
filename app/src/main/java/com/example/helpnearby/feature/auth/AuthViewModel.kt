package com.example.helpnearby.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.data.remote.supabase.GoogleCredentialHelper
import com.example.helpnearby.data.remote.supabase.GoogleCredentialResult
import com.example.helpnearby.data.remote.supabase.SupabaseSdkProvider
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.model.User
import com.example.helpnearby.domain.repository.AuthRepository
import com.example.helpnearby.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSupabaseConfigured: Boolean = false,
    val isGoogleSignInReady: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingVerificationUser: User? = null,
    val authenticatedUser: User? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val backendConfig: BackendConfig,
    private val googleCredentialHelper: GoogleCredentialHelper,
    private val supabaseSdkProvider: SupabaseSdkProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isSupabaseConfigured = backendConfig.isSupabaseConfigured,
            isGoogleSignInReady = backendConfig.isSupabaseConfigured ||
                backendConfig.isGoogleCredentialManagerConfigured
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        if (backendConfig.isSupabaseConfigured) {
            viewModelScope.launch {
                supabaseSdkProvider.sessionStatus
                    .catch { /* Keep email/password & manual sign-in working if SDK listener fails */ }
                    .collect { status ->
                        try {
                            if (status is SessionStatus.Authenticated &&
                                !_uiState.value.isLoading &&
                                _uiState.value.authenticatedUser == null
                            ) {
                                val session = status.session
                                val authUser = session.user ?: return@collect
                                val fullName = authUser.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                                    ?: authUser.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                                val isAdmin = authUser.appMetadata?.get("role")?.jsonPrimitive?.contentOrNull
                                    ?.equals("admin", ignoreCase = true) == true
                                val expiresAtEpochSec = (System.currentTimeMillis() / 1000L) + session.expiresIn

                                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                                when (
                                    val res = authRepository.completeSupabaseOAuthSession(
                                        accessToken = session.accessToken,
                                        refreshToken = session.refreshToken,
                                        expiresAtEpochSec = expiresAtEpochSec,
                                        userId = authUser.id,
                                        email = authUser.email,
                                        emailConfirmed = authUser.emailConfirmedAt != null,
                                        fullName = fullName,
                                        isAdmin = isAdmin
                                    )
                                ) {
                                    is AppResult.Success -> _uiState.update {
                                        it.copy(isLoading = false, authenticatedUser = res.data)
                                    }
                                    is AppResult.Failure -> _uiState.update {
                                        it.copy(isLoading = false, errorMessage = res.message)
                                    }
                                }
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = t.message ?: "Session sync failed.")
                            }
                        }
                    }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun resetPendingVerification() {
        _uiState.update {
            it.copy(
                pendingVerificationUser = null,
                errorMessage = null,
                infoMessage = "After confirming your email, sign in with your email and password."
            )
        }
    }

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                when (val res = authRepository.loginWithEmail(email, password)) {
                    is AppResult.Success -> _uiState.update {
                        it.copy(isLoading = false, authenticatedUser = res.data)
                    }
                    is AppResult.Failure -> _uiState.update {
                        it.copy(isLoading = false, errorMessage = res.message)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: "Unable to sign in.")
                }
            }
        }
    }

    fun registerWithEmail(
        fullName: String,
        email: String,
        password: String,
        photoUri: String?,
        city: String,
        state: String,
        country: String,
        language: AppLanguage,
        interests: List<String>,
        volunteerAvailable: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                when (
                    val res = authRepository.registerWithEmail(
                        fullName = fullName,
                        email = email,
                        password = password,
                        city = city,
                        state = state,
                        country = country,
                        language = language,
                        interests = interests,
                        volunteerAvailable = volunteerAvailable
                    )
                ) {
                    is AppResult.Success -> {
                        val userWithPhoto = if (!photoUri.isNullOrBlank()) {
                            runCatching {
                                userRepository.updateProfile(res.data.copy(photoUrl = photoUri))
                            }
                            res.data.copy(photoUrl = photoUri)
                        } else {
                            res.data
                        }

                        if (userWithPhoto.emailVerified) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    pendingVerificationUser = null,
                                    authenticatedUser = userWithPhoto
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    pendingVerificationUser = userWithPhoto,
                                    infoMessage = if (backendConfig.isSupabaseConfigured) {
                                        "Account created! Check your email for a confirmation link or verification code."
                                    } else {
                                        "Account created! Complete email verification or continue."
                                    }
                                )
                            }
                        }
                    }
                    is AppResult.Failure -> _uiState.update {
                        it.copy(isLoading = false, errorMessage = res.message)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: "Unable to create account.")
                }
            }
        }
    }

    fun verifyEmailCode(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                when (val res = authRepository.verifyEmail(code)) {
                    is AppResult.Success -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingVerificationUser = null,
                            authenticatedUser = res.data
                        )
                    }
                    is AppResult.Failure -> _uiState.update {
                        it.copy(isLoading = false, errorMessage = res.message)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: "Verification failed.")
                }
            }
        }
    }

    fun skipVerificationToHome() {
        val pending = _uiState.value.pendingVerificationUser ?: return
        _uiState.update { it.copy(pendingVerificationUser = null, authenticatedUser = pending) }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                when (val res = authRepository.sendPasswordReset(email)) {
                    is AppResult.Success -> _uiState.update {
                        it.copy(isLoading = false, infoMessage = res.data)
                    }
                    is AppResult.Failure -> _uiState.update {
                        it.copy(isLoading = false, errorMessage = res.message)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: "Unable to send password reset.")
                }
            }
        }
    }

    /**
     * Continues with Google using Jetpack Credential Manager when `GOOGLE_WEB_CLIENT_ID` is
     * configured, or automatically falls back to Supabase Google OAuth via Custom Tabs
     * (`helpnearby://auth-callback`) when Supabase is configured.
     */
    fun continueWithGoogle(activityContext: Context) {
        if (!backendConfig.isSupabaseConfigured) {
            _uiState.update {
                it.copy(
                    errorMessage = "Configure SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets to sign in with Google."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                if (backendConfig.isGoogleCredentialManagerConfigured) {
                    when (val credResult = googleCredentialHelper.requestGoogleIdToken(activityContext)) {
                        is GoogleCredentialResult.IdToken -> {
                            when (val res = authRepository.loginWithGoogleIdToken(credResult.idToken, credResult.rawNonce)) {
                                is AppResult.Success -> _uiState.update {
                                    it.copy(isLoading = false, authenticatedUser = res.data)
                                }
                                is AppResult.Failure -> _uiState.update {
                                    it.copy(isLoading = false, errorMessage = res.message)
                                }
                            }
                            return@launch
                        }
                        is GoogleCredentialResult.Cancelled -> {
                            _uiState.update { it.copy(isLoading = false) }
                            return@launch
                        }
                        is GoogleCredentialResult.Unavailable -> {
                            // Fall through to Supabase Browser OAuth flow if native Credential Manager has no credentials on device
                        }
                    }
                }

                when (val oauthResult = authRepository.startGoogleOAuthSignIn()) {
                    is AppResult.Success -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            infoMessage = "Complete Google Sign-In in the browser window to return to HelpNearby."
                        )
                    }
                    is AppResult.Failure -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = oauthResult.message
                        )
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: "Google Sign-In failed.")
                }
            }
        }
    }
}
