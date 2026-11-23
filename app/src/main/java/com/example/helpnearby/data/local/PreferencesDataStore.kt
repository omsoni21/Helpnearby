package com.example.helpnearby.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.model.AppThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.helpNearbyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "helpnearby_session_prefs"
)

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyCurrentUserId = stringPreferencesKey("current_user_id")
    private val keyOnboardingDone = booleanPreferencesKey("onboarding_done")
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyLanguage = stringPreferencesKey("app_language")
    private val keySelectedCity = stringPreferencesKey("selected_city")
    private val keySelectedArea = stringPreferencesKey("selected_area")
    private val keyLocationEnabled = booleanPreferencesKey("location_enabled")
    private val keyNotificationsEnabled = booleanPreferencesKey("notifications_enabled")
    private val keyUserLat = doublePreferencesKey("user_lat")
    private val keyUserLon = doublePreferencesKey("user_lon")

    private val keySupabaseAccessToken = stringPreferencesKey("supabase_access_token")
    private val keySupabaseRefreshToken = stringPreferencesKey("supabase_refresh_token")
    private val keySupabaseExpiresAt = longPreferencesKey("supabase_expires_at")

    val currentUserId: Flow<String?> = context.helpNearbyDataStore.data.map { prefs ->
        prefs[keyCurrentUserId]?.takeIf { it.isNotBlank() }
    }

    val onboardingCompleted: Flow<Boolean> = context.helpNearbyDataStore.data.map { prefs ->
        prefs[keyOnboardingDone] ?: false
    }

    val themeMode: Flow<AppThemeMode> = context.helpNearbyDataStore.data.map { prefs ->
        runCatching { AppThemeMode.valueOf(prefs[keyThemeMode] ?: "SYSTEM") }
            .getOrDefault(AppThemeMode.SYSTEM)
    }

    val language: Flow<AppLanguage> = context.helpNearbyDataStore.data.map { prefs ->
        runCatching { AppLanguage.valueOf(prefs[keyLanguage] ?: "EN") }
            .getOrDefault(AppLanguage.EN)
    }

    val selectedCity: Flow<String> = context.helpNearbyDataStore.data.map { prefs ->
        prefs[keySelectedCity] ?: "Bengaluru"
    }

    val selectedArea: Flow<String> = context.helpNearbyDataStore.data.map { prefs ->
        prefs[keySelectedArea] ?: "Indiranagar"
    }

    val locationEnabled: Flow<Boolean> = context.helpNearbyDataStore.data.map { prefs ->
        prefs[keyLocationEnabled] ?: false
    }

    val userCoordinates: Flow<Pair<Double, Double>?> = context.helpNearbyDataStore.data.map { prefs ->
        val lat = prefs[keyUserLat]
        val lon = prefs[keyUserLon]
        if (lat != null && lon != null) lat to lon else null
    }

    val notificationsEnabled: Flow<Boolean> = context.helpNearbyDataStore.data.map { prefs ->
        prefs[keyNotificationsEnabled] ?: true
    }

    val supabaseAccessToken: Flow<String?> = context.helpNearbyDataStore.data.map { prefs ->
        prefs[keySupabaseAccessToken]?.takeIf { it.isNotBlank() }
    }

    val supabaseRefreshToken: Flow<String?> = context.helpNearbyDataStore.data.map { prefs ->
        prefs[keySupabaseRefreshToken]?.takeIf { it.isNotBlank() }
    }

    val supabaseExpiresAt: Flow<Long> = context.helpNearbyDataStore.data.map { prefs ->
        prefs[keySupabaseExpiresAt] ?: 0L
    }

    suspend fun setCurrentUserId(userId: String?) {
        context.helpNearbyDataStore.edit { prefs ->
            if (userId == null) prefs.remove(keyCurrentUserId)
            else prefs[keyCurrentUserId] = userId
        }
    }

    suspend fun setSupabaseSession(accessToken: String?, refreshToken: String?, expiresAtMs: Long) {
        context.helpNearbyDataStore.edit { prefs ->
            if (accessToken.isNullOrBlank()) prefs.remove(keySupabaseAccessToken)
            else prefs[keySupabaseAccessToken] = accessToken
            if (refreshToken.isNullOrBlank()) prefs.remove(keySupabaseRefreshToken)
            else prefs[keySupabaseRefreshToken] = refreshToken
            prefs[keySupabaseExpiresAt] = expiresAtMs
        }
    }

    suspend fun clearSupabaseSession() {
        context.helpNearbyDataStore.edit { prefs ->
            prefs.remove(keySupabaseAccessToken)
            prefs.remove(keySupabaseRefreshToken)
            prefs.remove(keySupabaseExpiresAt)
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.helpNearbyDataStore.edit { it[keyOnboardingDone] = completed }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.helpNearbyDataStore.edit { it[keyThemeMode] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.helpNearbyDataStore.edit { it[keyLanguage] = language.name }
    }

    suspend fun setSelectedLocation(city: String, area: String) {
        context.helpNearbyDataStore.edit {
            it[keySelectedCity] = city
            it[keySelectedArea] = area
        }
    }

    suspend fun setLocationEnabled(enabled: Boolean) {
        context.helpNearbyDataStore.edit { it[keyLocationEnabled] = enabled }
    }

    suspend fun setUserCoordinates(lat: Double?, lon: Double?) {
        context.helpNearbyDataStore.edit { prefs ->
            if (lat == null || lon == null) {
                prefs.remove(keyUserLat)
                prefs.remove(keyUserLon)
            } else {
                prefs[keyUserLat] = lat
                prefs[keyUserLon] = lon
            }
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.helpNearbyDataStore.edit { it[keyNotificationsEnabled] = enabled }
    }
}
