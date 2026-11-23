package com.example.helpnearby.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.designsystem.LocalAppStrings
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.model.AppThemeMode
import com.example.helpnearby.domain.repository.AuthRepository
import com.example.helpnearby.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.EN,
    val notificationsEnabled: Boolean = true,
    val locationEnabled: Boolean = false,
    val backendProvider: com.example.helpnearby.domain.model.BackendProvider = com.example.helpnearby.domain.model.BackendProvider.LOCAL_OFFLINE,
    val isLoggedIn: Boolean = false,
    val loggedOutOrDeleted: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val backendConfig: com.example.helpnearby.core.network.BackendConfig,
    prefsDataStore: PreferencesDataStore
) : ViewModel() {

    private val exitedFlow = MutableStateFlow(false)

    private val togglesFlow = combine(
        prefsDataStore.themeMode,
        prefsDataStore.language,
        prefsDataStore.notificationsEnabled,
        prefsDataStore.locationEnabled
    ) { theme, lang, notifs, loc ->
        listOf(theme, lang, notifs, loc)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        togglesFlow,
        authRepository.currentUser,
        exitedFlow
    ) { toggles, user, exited ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            themeMode = toggles[0] as AppThemeMode,
            language = toggles[1] as AppLanguage,
            notificationsEnabled = toggles[2] as Boolean,
            locationEnabled = toggles[3] as Boolean,
            backendProvider = backendConfig.activeProvider,
            isLoggedIn = user != null,
            loggedOutOrDeleted = exited
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(mode: AppThemeMode) {
        viewModelScope.launch { userRepository.updateThemeMode(mode) }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { userRepository.updateLanguage(lang) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { userRepository.updateNotificationsEnabled(enabled) }
    }

    fun setLocation(enabled: Boolean) {
        viewModelScope.launch { userRepository.updateLocationEnabled(enabled) }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            exitedFlow.value = true
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            authRepository.currentUser.first()?.let { user ->
                authRepository.deleteAccount(user.id)
                exitedFlow.value = true
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onRequireAuth: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    var showPolicyDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.loggedOutOrDeleted) {
        if (state.loggedOutOrDeleted) onSignedOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Text("Language / भाषा", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = state.language == lang,
                        onClick = { viewModel.setLanguage(lang) },
                        label = { Text(lang.displayName) },
                        modifier = Modifier.testTag("settings_lang_${lang.name}")
                    )
                }
            }

            HorizontalDivider()

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notifications", style = MaterialTheme.typography.titleSmall)
                    Text("In-app and optional local alerts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.notificationsEnabled, onCheckedChange = { viewModel.setNotifications(it) })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Approximate Location Sorting", style = MaterialTheme.typography.titleSmall)
                    Text("Never required; exact addresses stay private", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.locationEnabled, onCheckedChange = { viewModel.setLocation(it) })
            }

            ListItem(
                headlineContent = { Text("Cloud Sync Status") },
                supportingContent = {
                    val statusDesc = when (state.backendProvider) {
                        com.example.helpnearby.domain.model.BackendProvider.SUPABASE ->
                            "Supabase Cloud Connected (supabase-kt: Auth + PostgREST + Realtime)"
                        com.example.helpnearby.domain.model.BackendProvider.FIREBASE ->
                            "Firebase Cloud Connected"
                        com.example.helpnearby.domain.model.BackendProvider.LOCAL_OFFLINE ->
                            "Offline Storage Mode (configure SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets)"
                    }
                    Text(statusDesc)
                },
                leadingContent = {
                    Icon(
                        imageVector = if (state.backendProvider == com.example.helpnearby.domain.model.BackendProvider.SUPABASE) {
                            Icons.Rounded.CloudDone
                        } else {
                            Icons.Rounded.OfflinePin
                        },
                        contentDescription = "Cloud sync status"
                    )
                }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Privacy Policy") },
                leadingContent = { Icon(Icons.Rounded.PrivacyTip, null) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = {
                        showPolicyDialog = "Privacy Policy" to "HelpNearby collects only the minimum profile details you provide. Phone numbers and exact street addresses are never shown publicly. Voluntary blood donor listings require explicit consent and can be removed at any time."
                    }) { Text("Read") }
                }
            )
            ListItem(
                headlineContent = { Text("Terms of Service") },
                leadingContent = { Icon(Icons.Rounded.Gavel, null) },
                trailingContent = {
                    TextButton(onClick = {
                        showPolicyDialog = "Terms of Service" to "HelpNearby is a community coordination platform and not an emergency, medical, or law-enforcement service. Users must communicate respectfully, avoid sharing sensitive IDs/OTPs, and follow local laws."
                    }) { Text("Read") }
                }
            )
            ListItem(
                headlineContent = { Text("Help & Support") },
                leadingContent = { Icon(Icons.Rounded.SupportAgent, null) },
                trailingContent = {
                    TextButton(onClick = {
                        showPolicyDialog = "Help & Support" to "Need assistance configuring Firebase/Supabase or reporting abuse? See docs/SETUP_BACKEND.md in the repository or use the in-app Report button on any listing."
                    }) { Text("Info") }
                }
            )

            if (state.isLoggedIn) {
                OutlinedButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("settings_logout_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log Out")
                }

                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("settings_delete_account_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.DeleteForever, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Account & Local Data")
                }
            } else {
                Button(
                    onClick = onRequireAuth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("settings_signin_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Person, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In / Create Account")
                }
            }
        }
    }

    showPolicyDialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { showPolicyDialog = null },
            title = { Text(title) },
            text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showPolicyDialog = null }) { Text("Close") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account Permanently?") },
            text = { Text("This deletes your profile, help requests, offers, donor registration, and local session data.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAccount()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
