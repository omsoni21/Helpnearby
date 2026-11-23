package com.example.helpnearby.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.common.DistanceUtils
import com.example.helpnearby.core.location.LocationHelper
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.core.sync.SyncManager
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.data.remote.supabase.SupabaseSdkProvider
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val currentUser: User? = null,
    val selectedCity: String = "Bengaluru",
    val selectedArea: String = "Indiranagar",
    val userLat: Double? = null,
    val userLon: Double? = null,
    val isDetectingLocation: Boolean = false,
    val locationNotice: String? = null,
    val language: AppLanguage = AppLanguage.EN,
    val isCloudConnected: Boolean = false,
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0,
    val unreadNotificationCount: Int = 0,
    val nearbyRequests: List<HelpRequest> = emptyList(),
    val recentPosts: List<CommunityPost> = emptyList(),
    val volunteerHighlights: List<VolunteerProfile> = emptyList(),
    val myRecentActivity: List<HelpRequest> = emptyList(),
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val helpRequestRepository: HelpRequestRepository,
    private val volunteerRepository: VolunteerRepository,
    private val communityRepository: CommunityRepository,
    private val notificationRepository: NotificationRepository,
    private val prefsDataStore: PreferencesDataStore,
    private val syncManager: SyncManager,
    private val backendConfig: BackendConfig,
    private val supabaseSdkProvider: SupabaseSdkProvider,
    private val locationHelper: LocationHelper
) : ViewModel() {

    private val _locationUiState = MutableStateFlow(false to (null as String?))

    private val locationMetaFlow = combine(
        prefsDataStore.selectedCity,
        prefsDataStore.selectedArea,
        prefsDataStore.userCoordinates,
        prefsDataStore.locationEnabled
    ) { city, area, coords, locEnabled ->
        LocationMeta(city, area, coords, locEnabled)
    }

    private val dataFlow = combine(
        authRepository.currentUser,
        locationMetaFlow,
        prefsDataStore.language,
        syncManager.isOnline,
        syncManager.pendingCount
    ) { user, locMeta, lang, online, pending ->
        HomeMeta(user, locMeta.city, locMeta.area, locMeta.coords, locMeta.enabled, lang, online, pending)
    }

    init {
        if (backendConfig.isSupabaseConfigured) {
            triggerSyncNow()
            viewModelScope.launch {
                supabaseSdkProvider.observeHelpRequestsRealtime()
                    .catch { /* Keep offline-first fallback active if realtime channel drops */ }
                    .collect {
                        val city = prefsDataStore.selectedCity.first()
                        val uid = prefsDataStore.currentUserId.first()
                        syncManager.refreshFromCloud(city, uid)
                    }
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(dataFlow, _locationUiState) { meta, locState ->
        meta to locState
    }.flatMapLatest { (meta, locState) ->
        val uid = meta.user?.id ?: ""
        combine(
            helpRequestRepository.observeRequests(cityFilter = meta.city, limit = 20),
            communityRepository.observePosts(cityFilter = meta.city),
            volunteerRepository.observeVolunteers(cityFilter = meta.city),
            if (uid.isNotEmpty()) helpRequestRepository.observeMyRequests(uid) else flowOf(emptyList()),
            if (uid.isNotEmpty()) notificationRepository.observeUnreadCount(uid) else flowOf(0)
        ) { requests, posts, volunteers, myReqs, unreadNotifs ->
            val coords = meta.coords
            val sortedRequests = if (coords != null) {
                val (uLat, uLon) = coords
                requests.sortedWith(
                    compareBy<HelpRequest> { req ->
                        if (req.latitude != null && req.longitude != null) {
                            DistanceUtils.haversineKm(uLat, uLon, req.latitude, req.longitude)
                        } else {
                            Double.MAX_VALUE
                        }
                    }.thenByDescending { it.createdAt }
                ).take(10)
            } else {
                requests.take(10)
            }

            HomeUiState(
                isLoading = false,
                currentUser = meta.user,
                selectedCity = meta.city,
                selectedArea = meta.area,
                userLat = coords?.first,
                userLon = coords?.second,
                isDetectingLocation = locState.first,
                locationNotice = locState.second,
                language = meta.lang,
                isCloudConnected = !backendConfig.isOfflineLocalOnly,
                isOnline = meta.online,
                pendingSyncCount = meta.pending,
                unreadNotificationCount = unreadNotifs,
                nearbyRequests = sortedRequests,
                recentPosts = posts.take(3),
                volunteerHighlights = volunteers.take(5),
                myRecentActivity = myReqs.take(3)
            )
        }
    }.catch { err ->
        emit(HomeUiState(isLoading = false, errorMessage = err.message ?: "Failed to load dashboard."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun updateCityAndArea(city: String, area: String) {
        viewModelScope.launch {
            userRepository.updateSelectedCityAndArea(city.trim(), area.trim())
            if (backendConfig.isSupabaseConfigured) {
                val uid = prefsDataStore.currentUserId.first()
                syncManager.refreshFromCloud(city.trim(), uid)
            }
        }
    }

    fun detectNearbyLocation() {
        viewModelScope.launch {
            _locationUiState.value = true to null
            try {
                locationHelper.fetchApproximateLocation().fold(
                    onSuccess = { detected ->
                        prefsDataStore.setUserCoordinates(detected.latitude, detected.longitude)
                        prefsDataStore.setLocationEnabled(true)
                        val resolvedCity = detected.city ?: prefsDataStore.selectedCity.first()
                        val resolvedArea = detected.area ?: prefsDataStore.selectedArea.first()
                        if (!detected.city.isNullOrBlank()) {
                            userRepository.updateSelectedCityAndArea(resolvedCity, resolvedArea)
                        }
                        if (backendConfig.isSupabaseConfigured) {
                            val uid = prefsDataStore.currentUserId.first()
                            syncManager.refreshFromCloud(resolvedCity, uid)
                        }
                        _locationUiState.value = false to "Nearby location updated ($resolvedArea, $resolvedCity)."
                    },
                    onFailure = { err ->
                        _locationUiState.value = false to (err.message ?: "Unable to read location.")
                    }
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _locationUiState.value = false to (t.message ?: "Unable to detect nearby location.")
            }
        }
    }

    fun onLocationPermissionDenied() {
        _locationUiState.value = false to "Location permission declined. You can still select your city manually."
    }

    fun clearLocationNotice() {
        _locationUiState.value = _locationUiState.value.first to null
    }

    fun toggleBookmark(requestId: String) {
        viewModelScope.launch {
            helpRequestRepository.toggleBookmark(requestId)
        }
    }

    fun triggerSyncNow() {
        viewModelScope.launch {
            syncManager.processPendingQueueOnce()
            if (backendConfig.isSupabaseConfigured) {
                val city = prefsDataStore.selectedCity.first()
                val uid = prefsDataStore.currentUserId.first()
                syncManager.refreshFromCloud(city, uid)
            }
        }
    }

    private data class LocationMeta(
        val city: String,
        val area: String,
        val coords: Pair<Double, Double>?,
        val enabled: Boolean
    )

    private data class HomeMeta(
        val user: User?,
        val city: String,
        val area: String,
        val coords: Pair<Double, Double>?,
        val locationEnabled: Boolean,
        val lang: AppLanguage,
        val online: Boolean,
        val pending: Int
    )
}
