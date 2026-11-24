package com.example.helpnearby

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.helpnearby.core.designsystem.HelpNearbyTheme
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.feature.onboarding.OnboardingScreen
import com.example.helpnearby.feature.safety.EmergencySafetyScreen
import android.content.Context
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.helpnearby.core.common.DistanceUtils
import com.example.helpnearby.core.database.HelpNearbyDatabase
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.core.network.NetworkMonitor
import com.example.helpnearby.core.security.SecurityGuard
import com.example.helpnearby.core.sync.SyncManager
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.data.remote.fcm.PushNotificationTransport
import com.example.helpnearby.data.remote.supabase.GoogleCredentialHelper
import com.example.helpnearby.data.remote.supabase.SupabaseSdkProvider
import com.example.helpnearby.data.repository.RoomAuthRepository
import com.example.helpnearby.data.repository.RoomMessagingRepository
import com.example.helpnearby.data.repository.RoomNotificationRepository
import com.example.helpnearby.data.repository.RoomSafetyModerationRepository
import com.example.helpnearby.data.repository.RoomUserRepository
import com.example.helpnearby.domain.model.AppNotification
import com.example.helpnearby.feature.auth.AuthScreen
import com.example.helpnearby.feature.auth.AuthViewModel
import com.example.helpnearby.feature.messaging.ConversationsScreen
import com.example.helpnearby.feature.messaging.ConversationsViewModel
import com.example.helpnearby.feature.settings.SettingsScreen
import com.example.helpnearby.feature.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HelpNearbyUiJourneyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `onboarding language toggle and skip flow work`() {
        var currentLanguage by mutableStateOf(AppLanguage.EN)
        var completed = false

        composeRule.setContent {
            HelpNearbyTheme(language = currentLanguage) {
                OnboardingScreen(
                    language = currentLanguage,
                    onLanguageChange = { currentLanguage = it },
                    onComplete = { completed = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Neighborhood Help, Close to Home").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_lang_toggle").performClick()
        composeRule.waitForIdle()
        assertEquals(AppLanguage.HI, currentLanguage)
        composeRule.onNodeWithText("पड़ोस में तुरंत सहायता").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("onboarding_skip_btn").performClick()
        composeRule.waitForIdle()
        assertTrue(completed)
    }

    @Test
    fun `emergency safety center displays 112 disclaimer and helplines`() {
        composeRule.setContent {
            HelpNearbyTheme {
                EmergencySafetyScreen(
                    onBack = {},
                    onOpenBlockedUsers = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Important Emergency Disclaimer").assertIsDisplayed()
        composeRule.onNodeWithTag("dial_112").assertIsDisplayed()
        composeRule.onNodeWithTag("dial_108").assertIsDisplayed()
    }

    @Test
    fun `auth screen create account tab submits cleanly without crash and supports back navigation`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, HelpNearbyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = PreferencesDataStore(context)
            kotlinx.coroutines.runBlocking {
                prefs.setCurrentUserId(null)
                prefs.clearSupabaseSession()
            }
            val config = BackendConfig(context)
            val authRepo = RoomAuthRepository(db, db.userDao(), db.volunteerDao(), prefs, SecurityGuard(), config)
            val userRepo = RoomUserRepository(db.userDao(), prefs)
            val vm = AuthViewModel(
                authRepository = authRepo,
                userRepository = userRepo,
                backendConfig = config,
                googleCredentialHelper = GoogleCredentialHelper(config),
                supabaseSdkProvider = SupabaseSdkProvider(context, config, OkHttpClient())
            )
            var backClicked = false

            composeRule.setContent {
                HelpNearbyTheme {
                    AuthScreen(
                        onAuthenticated = {},
                        canNavigateBack = true,
                        onBack = { backClicked = true },
                        viewModel = vm
                    )
                }
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithTag("auth_tab_register").performClick()
            composeRule.onNodeWithTag("register_name_input").performScrollTo().performTextInput("Kavita Rao")
            composeRule.onNodeWithTag("register_email_input").performScrollTo().performTextInput("kavita@example.com")
            composeRule.onNodeWithTag("register_password_input").performScrollTo().performTextInput("StrongPass1")
            composeRule.onNodeWithTag("register_submit_btn").performScrollTo().performClick()

            composeRule.waitUntil(timeoutMillis = 5000L) {
                runCatching {
                    composeRule.onNodeWithTag("verify_code_input").fetchSemanticsNode()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("verify_code_input").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("verify_email_btn").performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithTag("auth_back_btn").performClick()
            assertTrue(backClicked)
        } finally {
            db.close()
        }
    }

    @Test
    fun `guest screens gate private messages and account actions behind sign in button`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, HelpNearbyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = PreferencesDataStore(context)
            kotlinx.coroutines.runBlocking {
                prefs.setCurrentUserId(null)
                prefs.clearSupabaseSession()
            }
            val config = BackendConfig(context)
            val securityGuard = SecurityGuard()
            val syncManager = SyncManager(context, db.syncQueueDao(), NetworkMonitor(context), config)
            val push = object : PushNotificationTransport {
                override suspend fun registerDeviceTokenIfConfigured() = Result.failure<String>(IllegalStateException("Test"))
                override fun dispatchLocalSystemAlert(notification: AppNotification) {}
            }
            val notifRepo = RoomNotificationRepository(db.notificationDao(), push)
            val msgRepo = RoomMessagingRepository(db.messagingDao(), db.safetyModerationDao(), notifRepo, securityGuard, syncManager, config)
            val authRepo = RoomAuthRepository(db, db.userDao(), db.volunteerDao(), prefs, securityGuard, config)
            val userRepo = RoomUserRepository(db.userDao(), prefs)

            val convVm = ConversationsViewModel(msgRepo, syncManager, authRepo)
            val settingsVm = SettingsViewModel(authRepo, userRepo, config, prefs)
            var msgSignInClicked = false
            var settingsSignInClicked = false
            var showMessages by mutableStateOf(true)

            composeRule.setContent {
                HelpNearbyTheme {
                    if (showMessages) {
                        ConversationsScreen(
                            onOpenChat = {},
                            isLoggedIn = false,
                            onRequireAuth = { msgSignInClicked = true },
                            viewModel = convVm
                        )
                    } else {
                        SettingsScreen(
                            onBack = {},
                            onSignedOut = {},
                            onRequireAuth = { settingsSignInClicked = true },
                            viewModel = settingsVm
                        )
                    }
                }
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithTag("guest_messages_signin_btn").assertIsDisplayed().performClick()
            assertTrue(msgSignInClicked)

            showMessages = false
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("settings_signin_btn").performScrollTo().assertIsDisplayed().performClick()
            assertTrue(settingsSignInClicked)
        } finally {
            db.close()
        }
    }

    @Test
    fun `preferences persist coordinates and DistanceUtils formats nearby distances`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = PreferencesDataStore(context)
        prefs.setUserCoordinates(12.97, 77.64)
        assertEquals(12.97 to 77.64, prefs.userCoordinates.first())

        val sameSpot = DistanceUtils.haversineKm(12.97, 77.64, 12.972, 77.642)
        assertEquals("< 1 km away", DistanceUtils.formatApproximateDistance(sameSpot))

        val mediumDist = DistanceUtils.haversineKm(12.97, 77.64, 13.00, 77.67)
        assertTrue(DistanceUtils.formatApproximateDistance(mediumDist).contains("km away"))
    }
}
