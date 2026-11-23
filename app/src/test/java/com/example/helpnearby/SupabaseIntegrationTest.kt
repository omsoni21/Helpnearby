package com.example.helpnearby

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.database.HelpNearbyDatabase
import com.example.helpnearby.core.database.HelpRequestEntity
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.core.network.NetworkMonitor
import com.example.helpnearby.core.security.SecurityGuard
import com.example.helpnearby.core.sync.SyncManager
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.data.remote.supabase.*
import com.example.helpnearby.data.remote.repository.AuthRepositoryImpl
import com.example.helpnearby.data.repository.RoomAuthRepository
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.model.BackendProvider
import com.example.helpnearby.domain.model.SyncStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: HelpNearbyDatabase
    private lateinit var prefs: PreferencesDataStore
    private lateinit var moshi: Moshi
    private lateinit var securityGuard: SecurityGuard

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HelpNearbyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = PreferencesDataStore(context)
        kotlinx.coroutines.runBlocking {
            prefs.setCurrentUserId(null)
            prefs.clearSupabaseSession()
        }
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        securityGuard = SecurityGuard()
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.runBlocking {
            prefs.setCurrentUserId(null)
            prefs.clearSupabaseSession()
        }
        db.close()
    }

    @Test
    fun `BackendConfig rejects placeholders and activates Supabase with valid https url and key`() {
        val placeholderConfig = BackendConfig(
            context = context,
            rawSupabaseUrl = "https://your-project-ref.supabase.co",
            rawSupabaseAnonKey = "your-supabase-anon-key"
        )
        assertFalse(placeholderConfig.isSupabaseConfigured)
        assertTrue(
            placeholderConfig.activeProvider == BackendProvider.LOCAL_OFFLINE ||
                placeholderConfig.activeProvider == BackendProvider.FIREBASE
        )
        assertFalse(placeholderConfig.isGoogleCredentialManagerConfigured)

        val httpConfig = BackendConfig(
            context = context,
            rawSupabaseUrl = "http://insecure.supabase.co",
            rawSupabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.testkey",
            rawGoogleWebClientId = "your-oauth-web-client-id.apps.googleusercontent.com"
        )
        assertFalse(httpConfig.isSupabaseConfigured)
        assertFalse(httpConfig.isGoogleCredentialManagerConfigured)

        val validConfig = BackendConfig(
            context = context,
            rawSupabaseUrl = "https://abcdefg.supabase.co",
            rawSupabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid_anon_key_payload",
            rawGoogleWebClientId = "1234567890-abcdef123456.apps.googleusercontent.com"
        )
        assertTrue(validConfig.isSupabaseConfigured)
        assertEquals(BackendProvider.SUPABASE, validConfig.activeProvider)
        assertEquals("https://abcdefg.supabase.co/", validConfig.normalizedSupabaseBaseUrl)
        assertTrue(validConfig.isGoogleCredentialManagerConfigured)
    }

    @Test
    fun `SupabaseSdkProvider initializes official SupabaseClient only when configured`() {
        val okHttp = OkHttpClient()

        val placeholderConfig = BackendConfig(
            context = context,
            rawSupabaseUrl = "https://your-project-ref.supabase.co",
            rawSupabaseAnonKey = "your-supabase-anon-key"
        )
        val unconfiguredProvider = SupabaseSdkProvider(context, placeholderConfig, okHttp)
        assertFalse(unconfiguredProvider.isSdkReady)
        assertNull(unconfiguredProvider.client)

        val validConfig = BackendConfig(
            context = context,
            rawSupabaseUrl = "https://abcdefg.supabase.co",
            rawSupabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid_anon_key_payload"
        )
        val configuredProvider = SupabaseSdkProvider(context, validConfig, okHttp)
        assertTrue(configuredProvider.isSdkReady)
        assertNotNull(configuredProvider.client)
        assertNotNull(configuredProvider.authOrNull)
        assertNotNull(configuredProvider.postgrestOrNull)
        assertNotNull(configuredProvider.realtimeOrNull)
        assertEquals("abcdefg.supabase.co", configuredProvider.client?.supabaseUrl)
        configuredProvider.client?.let { kotlinx.coroutines.runBlocking { it.close() } }
    }

    @Test
    fun `Supabase login stores JWT session and syncs profile plus pending queue operations`() = runTest {
        val testUserId = UUID.randomUUID().toString()
        val testReqId = UUID.randomUUID().toString()
        val recordedRequests = mutableListOf<String>()
        var failHelpRequestsOnce = true

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath + (req.url.encodedQuery?.let { "?$it" } ?: "")
                recordedRequests.add("${req.method} $path")
                assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid_anon_key_payload", req.header("apikey"))

                val (code, json) = when {
                    path.startsWith("/auth/v1/token") && path.contains("grant_type=password") -> 200 to """
                        {
                          "access_token": "jwt_access_123",
                          "refresh_token": "jwt_refresh_123",
                          "expires_in": 3600,
                          "user": {
                            "id": "$testUserId",
                            "email": "priya@example.com",
                            "email_confirmed_at": "2026-11-22T10:00:00Z",
                            "app_metadata": {"role": "member"},
                            "user_metadata": {"full_name": "Priya Nair", "city": "Bengaluru", "state": "Karnataka"}
                          }
                        }
                    """.trimIndent()
                    path.startsWith("/auth/v1/token") && path.contains("grant_type=refresh_token") -> 200 to """
                        {
                          "access_token": "jwt_access_refreshed",
                          "refresh_token": "jwt_refresh_456",
                          "expires_in": 3600
                        }
                    """.trimIndent()
                    req.method == "GET" && path.startsWith("/rest/v1/users") -> 200 to """
                        [{
                          "id": "$testUserId",
                          "full_name": "Priya Nair",
                          "email": "priya@example.com",
                          "city": "Bengaluru",
                          "state": "Karnataka",
                          "country": "India",
                          "language": "EN",
                          "interests": ["Education"],
                          "volunteer_available": true,
                          "email_verified": true,
                          "role": "MEMBER",
                          "is_suspended": false
                        }]
                    """.trimIndent()
                    req.method == "GET" && path.startsWith("/rest/v1/user_preferences") -> 200 to "[]"
                    req.method == "POST" && path.startsWith("/rest/v1/user_preferences") -> 201 to "[]"
                    req.method == "GET" && path.startsWith("/rest/v1/") -> 200 to "[]"
                    req.method == "POST" && path.startsWith("/rest/v1/help_requests") -> {
                        if (failHelpRequestsOnce) {
                            failHelpRequestsOnce = false
                            500 to """{"message":"Temporary server error"}"""
                        } else {
                            201 to "[]"
                        }
                    }
                    else -> 200 to "[]"
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code in 200..299) "OK" else "Error")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val capabilities = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        org.robolectric.Shadows.shadowOf(capabilities).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        org.robolectric.Shadows.shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, capabilities)

        val config = BackendConfig(
            context = context,
            rawSupabaseUrl = "https://testproj.supabase.co",
            rawSupabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid_anon_key_payload"
        )
        val retrofit = Retrofit.Builder()
            .baseUrl(config.normalizedSupabaseBaseUrl)
            .client(fakeClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val authApi = retrofit.create(SupabaseAuthApi::class.java)
        val postgrestApi = retrofit.create(SupabasePostgrestApi::class.java)
        val sessionMgr = SupabaseSessionManager(config, authApi, prefs, moshi)
        val gateway = SupabaseRemoteGateway(config, postgrestApi, sessionMgr, db)
        val networkMonitor = NetworkMonitor(context)
        assertTrue(networkMonitor.isOnline.value)
        val syncManager = SyncManager(context, db.syncQueueDao(), networkMonitor, config, gateway)

        val authRepo = RoomAuthRepository(
            db = db,
            userDao = db.userDao(),
            volunteerDao = db.volunteerDao(),
            prefsDataStore = prefs,
            securityGuard = securityGuard,
            backendConfig = config,
            supabaseAuthApi = authApi,
            supabasePostgrestApi = postgrestApi,
            supabaseSessionManager = sessionMgr,
            supabaseSdkProvider = null,
            syncManager = syncManager
        )

        val blankGoogleResult = authRepo.loginWithGoogleIdToken("")
        assertTrue(blankGoogleResult is AppResult.Failure)

        val loginResult = authRepo.loginWithEmail("priya@example.com", "StrongPass1")
        assertTrue(loginResult is AppResult.Success)
        val loggedInUser = (loginResult as AppResult.Success).data
        assertEquals("Priya Nair", loggedInUser.fullName)
        assertEquals("jwt_access_123", prefs.supabaseAccessToken.first())

        // Test refresh when access token is expired
        prefs.setSupabaseSession("expired_token", "jwt_refresh_123", System.currentTimeMillis() - 10_000L)
        val refreshedBearer = sessionMgr.getValidBearerToken()
        assertEquals("Bearer jwt_access_refreshed", refreshedBearer)

        // Queue a help request for sync: first attempt hits 500 -> stays queued with retryCount=1,
        // second attempt succeeds -> marked SYNCED and removed from queue.
        val now = System.currentTimeMillis()
        db.helpRequestDao().upsert(
            HelpRequestEntity(
                id = testReqId,
                ownerId = testUserId,
                ownerName = "Priya Nair",
                title = "Need wheel chair ramp help",
                description = "Need assistance near Indiranagar metro station tomorrow morning.",
                category = "DISABILITY_ASSISTANCE",
                city = "Bengaluru",
                state = "Karnataka",
                approximateArea = "Indiranagar",
                urgency = "MEDIUM",
                status = "OPEN",
                version = 1,
                syncStatus = SyncStatus.PENDING_CREATE.name,
                createdAt = now,
                updatedAt = now,
                expiresAt = now + 86_400_000L
            )
        )
        syncManager.enqueueOperation("help_request", testReqId, "CREATE", """{"id":"$testReqId"}""", 1)

        val firstSummary = syncManager.processPendingQueueOnce()
        assertEquals(1, firstSummary.failed)
        assertEquals(1, db.syncQueueDao().observePendingCount().first())

        val secondSummary = syncManager.processPendingQueueOnce()
        assertEquals(1, secondSummary.succeeded)
        assertEquals(0, db.syncQueueDao().observePendingCount().first())
        assertEquals(SyncStatus.SYNCED.name, db.helpRequestDao().getById(testReqId)?.syncStatus)
        assertTrue(recordedRequests.any { it.contains("/auth/v1/token?grant_type=password") })
        assertTrue(recordedRequests.any { it.contains("/rest/v1/help_requests") })
    }

    @Test
    fun `Supabase registerWithEmail handles Confirm Email ON response without crash and completes OTP verification`() = runTest {
        val testUserId = UUID.randomUUID().toString()
        val recordedRequests = mutableListOf<String>()

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath + (req.url.encodedQuery?.let { "?$it" } ?: "")
                recordedRequests.add("${req.method} $path")

                val (code, json) = when {
                    path == "/auth/v1/signup" -> 200 to """
                        {
                          "id": "$testUserId",
                          "aud": "authenticated",
                          "role": "authenticated",
                          "email": "ananya@example.com",
                          "created_at": "2026-11-23T00:00:00Z"
                        }
                    """.trimIndent()
                    path == "/auth/v1/verify" -> 200 to """
                        {
                          "access_token": "jwt_verified_123",
                          "refresh_token": "refresh_verified_123",
                          "expires_in": 3600,
                          "user": {
                            "id": "$testUserId",
                            "email": "ananya@example.com",
                            "email_confirmed_at": "2026-11-23T00:01:00Z"
                          }
                        }
                    """.trimIndent()
                    req.method == "POST" && path.startsWith("/rest/v1/") -> 201 to "[]"
                    else -> 200 to "[]"
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val config = BackendConfig(
            context = context,
            rawSupabaseUrl = "https://testproj.supabase.co",
            rawSupabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid_anon_key_payload"
        )
        val retrofit = Retrofit.Builder()
            .baseUrl(config.normalizedSupabaseBaseUrl)
            .client(fakeClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val authApi = retrofit.create(SupabaseAuthApi::class.java)
        val postgrestApi = retrofit.create(SupabasePostgrestApi::class.java)
        val sessionMgr = SupabaseSessionManager(config, authApi, prefs, moshi)
        val gateway = SupabaseRemoteGateway(config, postgrestApi, sessionMgr, db)
        val syncManager = SyncManager(context, db.syncQueueDao(), NetworkMonitor(context), config, gateway)

        val authRepo = RoomAuthRepository(
            db = db,
            userDao = db.userDao(),
            volunteerDao = db.volunteerDao(),
            prefsDataStore = prefs,
            securityGuard = securityGuard,
            backendConfig = config,
            supabaseAuthApi = authApi,
            supabasePostgrestApi = postgrestApi,
            supabaseSessionManager = sessionMgr,
            supabaseSdkProvider = null,
            syncManager = syncManager
        )

        val signupRes = authRepo.registerWithEmail(
            fullName = "Ananya Verma",
            email = "ananya@example.com",
            password = "StrongPass1",
            city = "Bengaluru",
            state = "Karnataka",
            country = "India",
            language = com.example.helpnearby.domain.model.AppLanguage.EN,
            interests = listOf("Education"),
            volunteerAvailable = true
        )
        assertTrue(signupRes is AppResult.Success)
        val pendingUser = (signupRes as AppResult.Success).data
        assertEquals(testUserId, pendingUser.id)
        assertFalse(pendingUser.emailVerified)
        assertNull(prefs.currentUserId.first())

        val verifyRes = authRepo.verifyEmail("123456")
        assertTrue(verifyRes is AppResult.Success)
        val verifiedUser = (verifyRes as AppResult.Success).data
        assertTrue(verifiedUser.emailVerified)
        assertEquals(testUserId, prefs.currentUserId.first())
        assertEquals("jwt_verified_123", prefs.supabaseAccessToken.first())
        assertTrue(recordedRequests.contains("POST /auth/v1/signup"))
        assertTrue(recordedRequests.contains("POST /auth/v1/verify"))
        assertTrue(recordedRequests.any { it.startsWith("POST /rest/v1/users") })
    }

    @Test
    fun `Supabase registerWithEmail handles Confirm Email OFF immediate session response`() = runTest {
        val testUserId = UUID.randomUUID().toString()
        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                val (code, json) = when {
                    path == "/auth/v1/signup" -> 200 to """
                        {
                          "access_token": "jwt_immediate_1",
                          "refresh_token": "refresh_immediate_1",
                          "expires_in": 3600,
                          "user": {
                            "id": "$testUserId",
                            "email": "rohan@example.com",
                            "email_confirmed_at": "2026-11-23T00:00:00Z"
                          }
                        }
                    """.trimIndent()
                    req.method == "POST" && path.startsWith("/rest/v1/") -> 201 to "[]"
                    else -> 200 to "[]"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val config = BackendConfig(
            context = context,
            rawSupabaseUrl = "https://testproj.supabase.co",
            rawSupabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid_anon_key_payload"
        )
        val retrofit = Retrofit.Builder()
            .baseUrl(config.normalizedSupabaseBaseUrl)
            .client(fakeClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val authApi = retrofit.create(SupabaseAuthApi::class.java)
        val postgrestApi = retrofit.create(SupabasePostgrestApi::class.java)
        val sessionMgr = SupabaseSessionManager(config, authApi, prefs, moshi)
        val gateway = SupabaseRemoteGateway(config, postgrestApi, sessionMgr, db)
        val syncManager = SyncManager(context, db.syncQueueDao(), NetworkMonitor(context), config, gateway)

        val authRepo = RoomAuthRepository(
            db = db,
            userDao = db.userDao(),
            volunteerDao = db.volunteerDao(),
            prefsDataStore = prefs,
            securityGuard = securityGuard,
            backendConfig = config,
            supabaseAuthApi = authApi,
            supabasePostgrestApi = postgrestApi,
            supabaseSessionManager = sessionMgr,
            supabaseSdkProvider = null,
            syncManager = syncManager
        )

        val signupRes = authRepo.registerWithEmail(
            fullName = "Rohan Mehta",
            email = "rohan@example.com",
            password = "StrongPass1",
            city = "Pune",
            state = "Maharashtra",
            country = "India",
            language = com.example.helpnearby.domain.model.AppLanguage.EN,
            interests = listOf("Daily Assistance"),
            volunteerAvailable = false
        )
        assertTrue(signupRes is AppResult.Success)
        val signedInUser = (signupRes as AppResult.Success).data
        assertTrue(signedInUser.emailVerified)
        assertEquals(testUserId, prefs.currentUserId.first())
        assertEquals("jwt_immediate_1", prefs.supabaseAccessToken.first())
    }

    @Test
    fun `SupabaseRemoteGateway guest refreshFromCloud fetches public listings with anon key and skips private endpoints`() = runTest {
        val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid_anon_key_payload"
        val requestedPaths = mutableListOf<String>()
        val publicReqId = UUID.randomUUID().toString()
        val ownerId = UUID.randomUUID().toString()

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                requestedPaths.add(path)
                assertEquals("Bearer $anonKey", req.header("Authorization"))

                val json = if (path == "/rest/v1/help_requests") {
                    """
                    [{
                      "id": "$publicReqId",
                      "owner_id": "$ownerId",
                      "owner_name": "Meera K.",
                      "title": "Groceries needed nearby",
                      "description": "Looking for someone to drop milk and bread.",
                      "category": "FOOD_ASSISTANCE",
                      "city": "Bengaluru",
                      "state": "Karnataka",
                      "approximate_area": "Indiranagar",
                      "latitude": 12.97,
                      "longitude": 77.64,
                      "urgency": "HIGH",
                      "status": "OPEN",
                      "version": 1,
                      "created_at": "2026-11-23T00:00:00Z",
                      "updated_at": "2026-11-23T00:00:00Z",
                      "expires_at": "2026-11-25T00:00:00Z"
                    }]
                    """.trimIndent()
                } else "[]"

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val config = BackendConfig(
            context = context,
            rawSupabaseUrl = "https://testproj.supabase.co",
            rawSupabaseAnonKey = anonKey
        )
        val retrofit = Retrofit.Builder()
            .baseUrl(config.normalizedSupabaseBaseUrl)
            .client(fakeClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val authApi = retrofit.create(SupabaseAuthApi::class.java)
        val postgrestApi = retrofit.create(SupabasePostgrestApi::class.java)
        val sessionMgr = SupabaseSessionManager(config, authApi, prefs, moshi)
        val gateway = SupabaseRemoteGateway(config, postgrestApi, sessionMgr, db)

        val result = gateway.refreshFromCloud("Bengaluru", userId = null)
        assertTrue(result is AppResult.Success)
        assertNotNull(db.helpRequestDao().getById(publicReqId))
        assertTrue(requestedPaths.contains("/rest/v1/help_requests"))
        assertTrue(requestedPaths.contains("/rest/v1/volunteer_profiles"))
        assertTrue(requestedPaths.contains("/rest/v1/donor_profiles"))
        assertTrue(requestedPaths.contains("/rest/v1/lost_found_posts"))
        assertTrue(requestedPaths.contains("/rest/v1/community_posts"))
        assertFalse(requestedPaths.contains("/rest/v1/help_offers"))
        assertFalse(requestedPaths.contains("/rest/v1/conversations"))
        assertFalse(requestedPaths.contains("/rest/v1/messages"))
        assertFalse(requestedPaths.contains("/rest/v1/notifications"))
    }

    @Test
    fun `AuthRepositoryImpl authenticates via SupabaseClient for email signup signin and Google ID token`() = runTest {
        val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid_anon_key_payload"
        val testUserId = UUID.randomUUID().toString()
        val recorded = mutableListOf<String>()

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath + (req.url.encodedQuery?.let { "?$it" } ?: "")
                recorded.add("${req.method} $path")

                val (code, json) = when {
                    path.startsWith("/auth/v1/signup") -> 200 to """
                        {
                          "id": "$testUserId",
                          "aud": "authenticated",
                          "role": "authenticated",
                          "email": "sara@example.com",
                          "created_at": "2026-11-23T00:00:00Z"
                        }
                    """.trimIndent()
                    path.startsWith("/auth/v1/token") && path.contains("grant_type=password") -> 200 to """
                        {
                          "access_token": "sdk_jwt_password",
                          "token_type": "bearer",
                          "expires_in": 3600,
                          "refresh_token": "sdk_refresh_password",
                          "user": {
                            "id": "$testUserId",
                            "aud": "authenticated",
                            "role": "authenticated",
                            "email": "sara@example.com",
                            "email_confirmed_at": "2026-11-23T00:00:00Z",
                            "app_metadata": {"provider": "email"},
                            "user_metadata": {"full_name": "Sara Khan", "city": "Mumbai", "state": "Maharashtra"},
                            "created_at": "2026-11-23T00:00:00Z"
                          }
                        }
                    """.trimIndent()
                    path.startsWith("/auth/v1/token") && path.contains("grant_type=id_token") -> 200 to """
                        {
                          "access_token": "sdk_jwt_google",
                          "token_type": "bearer",
                          "expires_in": 3600,
                          "refresh_token": "sdk_refresh_google",
                          "user": {
                            "id": "$testUserId",
                            "aud": "authenticated",
                            "role": "authenticated",
                            "email": "sara@example.com",
                            "email_confirmed_at": "2026-11-23T00:00:00Z",
                            "app_metadata": {"provider": "google"},
                            "user_metadata": {"full_name": "Sara Khan"},
                            "created_at": "2026-11-23T00:00:00Z"
                          }
                        }
                    """.trimIndent()
                    req.method == "POST" && path.startsWith("/rest/v1/") -> 201 to "[]"
                    else -> 200 to "[]"
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .header("Content-Type", "application/json")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val config = BackendConfig(
            context = context,
            rawSupabaseUrl = "https://testproj.supabase.co",
            rawSupabaseAnonKey = anonKey
        )
        val retrofit = Retrofit.Builder()
            .baseUrl(config.normalizedSupabaseBaseUrl)
            .client(fakeClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val authApi = retrofit.create(SupabaseAuthApi::class.java)
        val postgrestApi = retrofit.create(SupabasePostgrestApi::class.java)
        val sessionMgr = SupabaseSessionManager(config, authApi, prefs, moshi)
        val gateway = SupabaseRemoteGateway(config, postgrestApi, sessionMgr, db)
        val syncManager = SyncManager(context, db.syncQueueDao(), NetworkMonitor(context), config, gateway)
        val supabaseClient = SupabaseSdkProvider.createClient(
            context = context,
            supabaseUrl = config.normalizedSupabaseBaseUrl.trimEnd('/'),
            supabaseAnonKey = anonKey,
            okHttpClient = fakeClient
        )

        try {
            val sdkProvider = SupabaseSdkProvider(context, config, fakeClient, supabaseClient)
            val repo = AuthRepositoryImpl(
                db = db,
                userDao = db.userDao(),
                volunteerDao = db.volunteerDao(),
                prefsDataStore = prefs,
                securityGuard = securityGuard,
                backendConfig = config,
                supabaseClient = supabaseClient,
                supabaseSdkProvider = sdkProvider,
                supabaseAuthApi = authApi,
                supabasePostgrestApi = postgrestApi,
                supabaseSessionManager = sessionMgr,
                googleCredentialHelper = GoogleCredentialHelper(config),
                syncManager = syncManager
            )

            val regRes = repo.registerWithEmail(
                fullName = "Sara Khan",
                email = "sara@example.com",
                password = "StrongPass1",
                city = "Mumbai",
                state = "Maharashtra",
                country = "India",
                language = AppLanguage.EN,
                interests = listOf("Education"),
                volunteerAvailable = false
            )
            assertTrue("regRes failed: $regRes | recorded=$recorded", regRes is AppResult.Success)
            assertEquals(testUserId, (regRes as AppResult.Success).data.id)
            assertFalse(regRes.data.emailVerified)

            val loginRes = repo.loginWithEmail("sara@example.com", "StrongPass1")
            assertTrue("loginRes failed: $loginRes | recorded=$recorded", loginRes is AppResult.Success)
            assertEquals("Sara Khan", (loginRes as AppResult.Success).data.fullName)
            assertEquals("sdk_jwt_password", prefs.supabaseAccessToken.first())

            val googleRes = repo.loginWithGoogleIdToken("fake_google_id_token", "raw_nonce_123")
            assertTrue("googleRes failed: $googleRes | recorded=$recorded", googleRes is AppResult.Success)
            assertEquals("sdk_jwt_google", prefs.supabaseAccessToken.first())

            assertTrue(recorded.any { it.contains("/auth/v1/signup") })
            assertTrue(recorded.any { it.contains("grant_type=password") })
            assertTrue(recorded.any { it.contains("grant_type=id_token") })
        } finally {
            kotlinx.coroutines.runBlocking { supabaseClient.close() }
            context.getSharedPreferences("helpnearby_supabase_sdk_session", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
