package com.example.helpnearby.data.remote.supabase

import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.data.local.PreferencesDataStore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Supabase GoTrue JWT access & refresh tokens stored privately in DataStore,
 * refreshes expiring tokens automatically, and verifies server-side admin claims.
 */
@Singleton
class SupabaseSessionManager @Inject constructor(
    private val backendConfig: BackendConfig,
    private val authApi: SupabaseAuthApi,
    private val prefsDataStore: PreferencesDataStore,
    private val moshi: Moshi
) {
    private val refreshMutex = Mutex()
    private val errorAdapter by lazy { moshi.adapter(SupabaseErrorResponse::class.java) }

    suspend fun saveSession(response: SupabaseAuthResponse) {
        val access = response.accessToken ?: return
        val refresh = response.refreshToken
        val expiresAtMs = when {
            response.expiresAt != null -> response.expiresAt * 1000L
            response.expiresIn != null -> System.currentTimeMillis() + (response.expiresIn * 1000L)
            else -> System.currentTimeMillis() + 3_600_000L
        }
        prefsDataStore.setSupabaseSession(access, refresh, expiresAtMs)
    }

    suspend fun clearSession() {
        prefsDataStore.clearSupabaseSession()
    }

    /**
     * Returns `"Bearer <jwt>"` if an active or refreshable session exists, or null otherwise.
     */
    suspend fun getValidBearerToken(): String? {
        if (!backendConfig.isSupabaseConfigured) return null

        val currentAccess = prefsDataStore.supabaseAccessToken.first()
        val expiresAt = prefsDataStore.supabaseExpiresAt.first()
        val now = System.currentTimeMillis()

        if (!currentAccess.isNullOrBlank() && expiresAt > now + REFRESH_LEEWAY_MS) {
            return "Bearer $currentAccess"
        }

        val refreshToken = prefsDataStore.supabaseRefreshToken.first()
        if (refreshToken.isNullOrBlank()) {
            return currentAccess?.let { "Bearer $it" }
        }

        return refreshMutex.withLock {
            // Re-check inside lock in case another coroutine already refreshed
            val latestAccess = prefsDataStore.supabaseAccessToken.first()
            val latestExpires = prefsDataStore.supabaseExpiresAt.first()
            if (!latestAccess.isNullOrBlank() && latestExpires > System.currentTimeMillis() + REFRESH_LEEWAY_MS) {
                return@withLock "Bearer $latestAccess"
            }

            runCatching {
                val resp = authApi.refreshSession(
                    apiKey = backendConfig.supabaseAnonKey,
                    body = SupabaseRefreshTokenRequest(refreshToken)
                )
                if (resp.isSuccessful) {
                    val body = resp.body()
                    if (body?.accessToken != null) {
                        saveSession(body)
                        return@withLock "Bearer ${body.accessToken}"
                    }
                }
                null
            }.getOrNull()
        }
    }

    /**
     * Checks Supabase GoTrue `/auth/v1/user` to determine whether the signed-in JWT
     * carries a server-assigned `app_metadata.role == "admin"` claim.
     */
    suspend fun isServerVerifiedAdmin(): Boolean {
        if (!backendConfig.isSupabaseConfigured) return false
        val bearer = getValidBearerToken() ?: return false
        return runCatching {
            val resp = authApi.getCurrentUser(backendConfig.supabaseAnonKey, bearer)
            resp.isSuccessful && resp.body()?.isServerVerifiedAdmin == true
        }.getOrDefault(false)
    }

    fun parseError(response: Response<*>, fallback: String): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        if (raw.isNullOrBlank()) return "$fallback (HTTP ${response.code()})"
        val parsed = runCatching { errorAdapter.fromJson(raw) }.getOrNull()
        return parsed?.readableMessage(fallback) ?: "$fallback (HTTP ${response.code()})"
    }

    companion object {
        private const val REFRESH_LEEWAY_MS = 60_000L
    }
}
