package com.example.helpnearby.core.network

import android.content.Context
import com.example.BuildConfig
import com.example.helpnearby.domain.model.BackendProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inspects Secrets-injected BuildConfig fields (`SUPABASE_URL`, `SUPABASE_ANON_KEY`,
 * `GOOGLE_WEB_CLIENT_ID`) and optional Firebase config without hardcoding credentials.
 *
 * When real Supabase URL + Anon Key are supplied via the AI Studio Secrets panel (or `.env`),
 * [activeProvider] switches to [BackendProvider.SUPABASE] and enables live GoTrue/PostgREST/Realtime sync.
 * Otherwise, the app runs transparently in offline local Room storage ([BackendProvider.LOCAL_OFFLINE]).
 */
@Singleton
class BackendConfig(
    private val context: Context,
    private val rawSupabaseUrl: String,
    private val rawSupabaseAnonKey: String,
    private val rawGoogleWebClientId: String = ""
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context = context,
        rawSupabaseUrl = BuildConfig.SUPABASE_URL,
        rawSupabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
        rawGoogleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    )

    val supabaseUrl: String
        get() = rawSupabaseUrl.trim()

    val supabaseAnonKey: String
        get() = rawSupabaseAnonKey.trim()

    val googleWebClientId: String
        get() {
            val configured = rawGoogleWebClientId.trim()
            if (isValidGoogleWebClientId(configured)) return configured
            return resolveDefaultWebClientIdFromResources()
        }

    val isSupabaseConfigured: Boolean by lazy {
        isValidSupabaseUrl(supabaseUrl) && isValidSupabaseKey(supabaseAnonKey)
    }

    val isGoogleCredentialManagerConfigured: Boolean
        get() = isValidGoogleWebClientId(googleWebClientId)

    private fun resolveDefaultWebClientIdFromResources(): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) return ""
        val value = runCatching { context.getString(resId).trim() }.getOrDefault("")
        return if (isValidGoogleWebClientId(value)) value else ""
    }

    /**
     * Always ends with `/` and parses as an HttpUrl so Retrofit initialization never crashes
     * even before cloud credentials are provided.
     */
    val normalizedSupabaseBaseUrl: String by lazy {
        if (!isSupabaseConfigured) {
            FALLBACK_LOCALHOST_URL
        } else {
            val trimmed = supabaseUrl.trimEnd('/')
            "$trimmed/"
        }
    }

    val activeProvider: BackendProvider by lazy {
        when {
            isSupabaseConfigured -> BackendProvider.SUPABASE
            hasFirebaseConfiguration() -> BackendProvider.FIREBASE
            else -> BackendProvider.LOCAL_OFFLINE
        }
    }

    val isOfflineLocalOnly: Boolean
        get() = activeProvider == BackendProvider.LOCAL_OFFLINE

    private fun hasFirebaseConfiguration(): Boolean {
        val resId = context.resources.getIdentifier("google_app_id", "string", context.packageName)
        if (resId == 0) return false
        val appId = context.getString(resId)
        if (appId.isBlank() || appId.contains("placeholder", ignoreCase = true)) return false
        return runCatching {
            Class.forName("com.google.firebase.auth.FirebaseAuth")
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val FALLBACK_LOCALHOST_URL = "https://localhost/"
        private val PLACEHOLDER_URLS = setOf(
            "https://your-project-ref.supabase.co",
            "http://your-project-ref.supabase.co",
            "https://example.supabase.co"
        )
        private val PLACEHOLDER_KEYS = setOf(
            "your-supabase-anon-key",
            "MY_SUPABASE_ANON_KEY",
            "SUPABASE_ANON_KEY"
        )

        fun isValidSupabaseUrl(url: String): Boolean {
            val clean = url.trim().trimEnd('/')
            if (clean.isEmpty()) return false
            if (clean.lowercase() in PLACEHOLDER_URLS) return false
            if (clean.contains("your-project-ref", ignoreCase = true)) return false
            val parsed = "$clean/".toHttpUrlOrNull() ?: return false
            return parsed.scheme.equals("https", ignoreCase = true) && parsed.host.contains('.')
        }

        fun isValidSupabaseKey(key: String): Boolean {
            val clean = key.trim()
            if (clean.length < 20) return false
            if (clean in PLACEHOLDER_KEYS) return false
            if (clean.startsWith("your-", ignoreCase = true)) return false
            return true
        }

        fun isValidGoogleWebClientId(clientId: String): Boolean {
            val clean = clientId.trim()
            if (clean.length < 20) return false
            if (clean.startsWith("your-", ignoreCase = true)) return false
            if (clean.contains("your-oauth-web-client-id", ignoreCase = true)) return false
            return clean.endsWith(".apps.googleusercontent.com", ignoreCase = true)
        }
    }
}
