package com.example.helpnearby.data.remote.supabase

import android.content.Context
import android.content.Intent
import com.example.helpnearby.core.network.BackendConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the official `supabase-kt` [SupabaseClient] configured with [Auth] (including
 * `helpnearby://auth-callback` OAuth deep-link handling), [Postgrest], and [Realtime].
 *
 * Safely remains `null` when [BackendConfig.isSupabaseConfigured] is `false`.
 */
@Singleton
class SupabaseSdkProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backendConfig: BackendConfig,
    private val okHttpClient: OkHttpClient,
    injectedClient: SupabaseClient?
) {
    constructor(
        context: Context,
        backendConfig: BackendConfig,
        okHttpClient: OkHttpClient
    ) : this(context, backendConfig, okHttpClient, null)

    val client: SupabaseClient? by lazy {
        injectedClient ?: if (!backendConfig.isSupabaseConfigured) {
            null
        } else {
            runCatching {
                createClient(
                    context = context,
                    supabaseUrl = backendConfig.normalizedSupabaseBaseUrl.trimEnd('/'),
                    supabaseAnonKey = backendConfig.supabaseAnonKey,
                    okHttpClient = okHttpClient
                )
            }.getOrNull()
        }
    }

    val isSdkReady: Boolean
        get() = client != null

    val authOrNull: Auth?
        get() = runCatching { client?.auth }.getOrNull()

    val postgrestOrNull: Postgrest?
        get() = runCatching { client?.postgrest }.getOrNull()

    val realtimeOrNull: Realtime?
        get() = runCatching { client?.realtime }.getOrNull()

    val sessionStatus: Flow<SessionStatus>
        get() = authOrNull?.sessionStatus ?: emptyFlow()

    fun handleDeeplinks(intent: Intent?) {
        val sdkClient = client ?: return
        val safeIntent = intent ?: return
        runCatching {
            sdkClient.handleDeeplinks(safeIntent)
        }
    }

    /**
     * Launches Supabase OAuth flow with Google via Custom Tabs and redirects back to
     * `helpnearby://auth-callback`.
     */
    suspend fun signInWithGoogleOAuth(): Result<Unit> {
        val auth = authOrNull
            ?: return Result.failure(IllegalStateException("Supabase is not configured."))
        return runCatching {
            auth.signInWith(Google)
        }
    }

    /**
     * Subscribes to live Postgres changes on `public.help_requests` via Supabase Realtime.
     */
    fun observeHelpRequestsRealtime(): Flow<Unit> {
        val sdkClient = client ?: return emptyFlow()
        return flow {
            val channel = sdkClient.channel("help_requests_public")
            val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "help_requests"
            }.map { Unit }
            channel.subscribe()
            try {
                emitAll(changeFlow)
            } finally {
                runCatching { channel.unsubscribe() }
            }
        }
    }

    private class AndroidPrefsSessionManager(
        context: Context,
        private val json: Json
    ) : SessionManager {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override suspend fun saveSession(session: UserSession) {
            runCatching {
                prefs.edit().putString(KEY_SESSION, json.encodeToString(session)).apply()
            }
        }

        override suspend fun loadSession(): UserSession? {
            val raw = prefs.getString(KEY_SESSION, null) ?: return null
            return runCatching { json.decodeFromString<UserSession>(raw) }.getOrNull()
        }

        override suspend fun deleteSession() {
            prefs.edit().remove(KEY_SESSION).apply()
        }

        private companion object {
            const val PREFS_NAME = "helpnearby_supabase_sdk_session"
            const val KEY_SESSION = "user_session_json"
        }
    }

    companion object {
        const val AUTH_REDIRECT_SCHEME = "helpnearby"
        const val AUTH_REDIRECT_HOST = "auth-callback"

        private val sdkJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun createClient(
            context: Context,
            supabaseUrl: String,
            supabaseAnonKey: String,
            okHttpClient: OkHttpClient
        ): SupabaseClient {
            val realtimeReadyOkHttp = okHttpClient.newBuilder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
            val prefsSessionManager = AndroidPrefsSessionManager(context, sdkJson)

            return createSupabaseClient(
                supabaseUrl = supabaseUrl.trim().trimEnd('/'),
                supabaseKey = supabaseAnonKey.trim()
            ) {
                httpEngine = OkHttp.create {
                    preconfigured = realtimeReadyOkHttp
                }
                install(Auth) {
                    scheme = AUTH_REDIRECT_SCHEME
                    host = AUTH_REDIRECT_HOST
                    defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
                    sessionManager = prefsSessionManager
                    codeVerifierCache = MemoryCodeVerifierCache()
                }
                install(Postgrest)
                install(Realtime)
            }
        }
    }
}
