package com.example.helpnearby.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.common.DistanceUtils
import com.example.helpnearby.core.location.LocationHelper
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.core.sync.SyncManager
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.model.HelpCategory
import com.example.helpnearby.domain.model.HelpRequest
import com.example.helpnearby.domain.repository.AuthRepository
import com.example.helpnearby.domain.repository.HelpRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RequestSortMode {
    NEWEST, APPROXIMATE_DISTANCE
}

data class ExploreUiState(
    val query: String = "",
    val selectedCity: String? = null,
    val selectedCategory: HelpCategory? = null,
    val sortMode: RequestSortMode = RequestSortMode.NEWEST,
    val userLat: Double? = null,
    val userLon: Double? = null,
    val isDetectingLocation: Boolean = false,
    val locationNotice: String? = null,
    val language: AppLanguage = AppLanguage.EN,
    val requests: List<HelpRequest> = emptyList(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val helpRequestRepository: HelpRequestRepository,
    private val authRepository: AuthRepository,
    private val prefsDataStore: PreferencesDataStore,
    private val syncManager: SyncManager,
    private val locationHelper: LocationHelper
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val cityFilterFlow = MutableStateFlow<String?>(null)
    private val categoryFilterFlow = MutableStateFlow<HelpCategory?>(null)
    private val sortModeFlow = MutableStateFlow(RequestSortMode.NEWEST)
    private val overrideCoordsFlow = MutableStateFlow<Pair<Double, Double>?>(null)
    private val locationStatusFlow = MutableStateFlow(false to (null as String?))

    init {
        viewModelScope.launch {
            val city = prefsDataStore.selectedCity.firstOrNull()
            val uid = prefsDataStore.currentUserId.firstOrNull()
            syncManager.refreshFromCloud(city, uid)
        }
        viewModelScope.launch {
            while (true) {
                delay(10_000L)
                val city = prefsDataStore.selectedCity.firstOrNull()
                val uid = prefsDataStore.currentUserId.firstOrNull()
                syncManager.refreshFromCloud(city, uid)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val city = prefsDataStore.selectedCity.firstOrNull()
            val uid = prefsDataStore.currentUserId.firstOrNull()
            syncManager.refreshFromCloud(city, uid)
        }
    }

    private val coordsFlow: Flow<Pair<Double, Double>?> = combine(
        overrideCoordsFlow,
        prefsDataStore.userCoordinates
    ) { override, saved ->
        override ?: saved
    }

    private val filters = combine(
        queryFlow,
        cityFilterFlow,
        categoryFilterFlow,
        sortModeFlow,
        coordsFlow
    ) { q, city, cat, sort, coords ->
        FilterParams(q, city, cat, sort, coords)
    }

    val uiState: StateFlow<ExploreUiState> = combine(
        filters,
        prefsDataStore.language,
        locationStatusFlow
    ) { f, lang, locStatus -> Triple(f, lang, locStatus) }
        .flatMapLatest { (f, lang, locStatus) ->
            helpRequestRepository.observeRequests(
                cityFilter = f.city,
                categoryFilter = f.category,
                searchQuery = f.query,
                limit = 80
            ).map { list ->
                val sorted = if (f.sort == RequestSortMode.APPROXIMATE_DISTANCE && f.coords != null) {
                    val (uLat, uLon) = f.coords
                    list.sortedWith(
                        compareBy<HelpRequest> { req ->
                            if (req.latitude != null && req.longitude != null) {
                                DistanceUtils.haversineKm(uLat, uLon, req.latitude, req.longitude)
                            } else {
                                Double.MAX_VALUE
                            }
                        }.thenByDescending { it.createdAt }
                    )
                } else {
                    list.sortedByDescending { it.createdAt }
                }
                ExploreUiState(
                    query = f.query,
                    selectedCity = f.city,
                    selectedCategory = f.category,
                    sortMode = f.sort,
                    userLat = f.coords?.first,
                    userLon = f.coords?.second,
                    isDetectingLocation = locStatus.first,
                    locationNotice = locStatus.second,
                    language = lang,
                    requests = sorted,
                    isLoading = false
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ExploreUiState()
        )

    fun setQuery(q: String) {
        queryFlow.value = q
    }

    fun setCityFilter(city: String?) {
        cityFilterFlow.value = city?.takeIf { it.isNotBlank() }
    }

    fun setCategoryFilter(category: HelpCategory?) {
        categoryFilterFlow.value = category
    }

    fun setSortMode(mode: RequestSortMode) {
        sortModeFlow.value = mode
    }

    fun setApproximateCoordinates(lat: Double, lon: Double) {
        overrideCoordsFlow.value = lat to lon
        sortModeFlow.value = RequestSortMode.APPROXIMATE_DISTANCE
    }

    fun detectNearbyLocation() {
        viewModelScope.launch {
            locationStatusFlow.value = true to null
            try {
                locationHelper.fetchApproximateLocation().fold(
                    onSuccess = { detected ->
                        overrideCoordsFlow.value = detected.latitude to detected.longitude
                        sortModeFlow.value = RequestSortMode.APPROXIMATE_DISTANCE
                        prefsDataStore.setUserCoordinates(detected.latitude, detected.longitude)
                        prefsDataStore.setLocationEnabled(true)
                        val placeHint = listOfNotNull(detected.area, detected.city).joinToString(", ")
                        locationStatusFlow.value = false to if (placeHint.isNotBlank()) {
                            "Sorted by approximate distance near $placeHint (exact address stays private)."
                        } else {
                            "Sorted by approximate distance (exact address remains hidden)."
                        }
                    },
                    onFailure = { err ->
                        locationStatusFlow.value = false to (err.message ?: "Device location unavailable; showing newest requests.")
                    }
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                locationStatusFlow.value = false to (t.message ?: "Unable to read coarse location; showing newest requests.")
            }
        }
    }

    fun onLocationPermissionDenied() {
        locationStatusFlow.value = false to "Location permission declined. You can still browse and filter by city."
    }

    fun toggleBookmark(requestId: String) {
        viewModelScope.launch {
            helpRequestRepository.toggleBookmark(requestId)
        }
    }

    private data class FilterParams(
        val query: String,
        val city: String?,
        val category: HelpCategory?,
        val sort: RequestSortMode,
        val coords: Pair<Double, Double>?
    )
}
