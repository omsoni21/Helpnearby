package com.example.helpnearby.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StartupState(
    val isReady: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val isLoggedIn: Boolean = false,
    val language: AppLanguage = AppLanguage.EN
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefsDataStore: PreferencesDataStore,
    authRepository: AuthRepository
) : ViewModel() {

    val startupState: StateFlow<StartupState> = combine(
        prefsDataStore.onboardingCompleted,
        authRepository.isLoggedIn,
        prefsDataStore.language
    ) { onboardingDone, loggedIn, lang ->
        StartupState(
            isReady = true,
            onboardingCompleted = onboardingDone,
            isLoggedIn = loggedIn,
            language = lang
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StartupState()
    )

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            prefsDataStore.setLanguage(language)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            prefsDataStore.setOnboardingCompleted(true)
        }
    }
}
