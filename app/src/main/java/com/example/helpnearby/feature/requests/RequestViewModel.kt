package com.example.helpnearby.feature.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.location.DetectedLocation
import com.example.helpnearby.core.location.LocationHelper
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequestFormUiState(
    val isLoading: Boolean = false,
    val existingRequest: HelpRequest? = null,
    val currentUser: User? = null,
    val detectedLocation: DetectedLocation? = null,
    val isDetectingLocation: Boolean = false,
    val locationNotice: String? = null,
    val errorMessage: String? = null,
    val savedSuccess: Boolean = false
)

@HiltViewModel
class RequestFormViewModel @Inject constructor(
    private val helpRequestRepository: HelpRequestRepository,
    private val authRepository: AuthRepository,
    private val locationHelper: LocationHelper,
    private val prefsDataStore: PreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestFormUiState())
    val uiState: StateFlow<RequestFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    fun loadForEdit(requestId: String?) {
        if (requestId.isNullOrBlank()) return
        viewModelScope.launch {
            helpRequestRepository.observeRequestById(requestId).first()?.let { req ->
                _uiState.update { it.copy(existingRequest = req) }
            }
        }
    }

    fun detectApproximateLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetectingLocation = true, locationNotice = null) }
            try {
                locationHelper.fetchApproximateLocation().fold(
                    onSuccess = { detected ->
                        prefsDataStore.setUserCoordinates(detected.latitude, detected.longitude)
                        _uiState.update {
                            it.copy(
                                isDetectingLocation = false,
                                detectedLocation = detected,
                                locationNotice = "Approximate location added (~1 km privacy grid)."
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isDetectingLocation = false,
                                locationNotice = err.message ?: "Unable to detect device location."
                            )
                        }
                    }
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isDetectingLocation = false, locationNotice = t.message ?: "Location lookup failed.")
                }
            }
        }
    }

    fun onLocationPermissionDenied() {
        _uiState.update {
            it.copy(locationNotice = "Location permission declined. Enter city and area manually.")
        }
    }

    fun saveRequest(
        requestId: String?,
        title: String,
        description: String,
        category: HelpCategory,
        city: String,
        stateName: String,
        approximateArea: String,
        latitude: Double?,
        longitude: Double?,
        urgency: UrgencyLevel,
        photoUrl: String?,
        expiryDays: Int
    ) {
        val user = _uiState.value.currentUser
        if (user == null) {
            _uiState.update { it.copy(errorMessage = "Please sign in first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val now = System.currentTimeMillis()
            val expiresAt = now + expiryDays.coerceIn(1, 30) * 86_400_000L

            val existing = _uiState.value.existingRequest
            val result = if (requestId != null && existing != null) {
                helpRequestRepository.updateRequest(
                    existing.copy(
                        title = title,
                        description = description,
                        category = category,
                        city = city,
                        state = stateName,
                        approximateArea = approximateArea,
                        latitude = latitude,
                        longitude = longitude,
                        urgency = urgency,
                        photoUrl = photoUrl,
                        expiresAt = expiresAt
                    ),
                    actorId = user.id
                )
            } else {
                helpRequestRepository.createRequest(
                    HelpRequest(
                        id = "",
                        ownerId = user.id,
                        ownerName = user.fullName,
                        title = title,
                        description = description,
                        category = category,
                        city = city,
                        state = stateName,
                        approximateArea = approximateArea,
                        latitude = latitude,
                        longitude = longitude,
                        urgency = urgency,
                        status = RequestStatus.OPEN,
                        photoUrl = photoUrl,
                        createdAt = now,
                        updatedAt = now,
                        expiresAt = expiresAt
                    )
                )
            }

            when (result) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, savedSuccess = true) }
                is AppResult.Failure -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }
}

data class RequestDetailUiState(
    val isLoading: Boolean = true,
    val request: HelpRequest? = null,
    val offers: List<HelpOffer> = emptyList(),
    val currentUser: User? = null,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val openConversationId: String? = null,
    val deletedOrCancelled: Boolean = false
)

@HiltViewModel
class RequestDetailViewModel @Inject constructor(
    private val helpRequestRepository: HelpRequestRepository,
    private val volunteerRepository: VolunteerRepository,
    private val messagingRepository: MessagingRepository,
    private val safetyRepository: SafetyModerationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val requestIdFlow = MutableStateFlow("")
    private val _feedback = MutableStateFlow<Pair<String?, String?>>(null to null)
    private val _openConversation = MutableStateFlow<String?>(null)
    private val _cancelled = MutableStateFlow(false)

    val uiState: StateFlow<RequestDetailUiState> = requestIdFlow
        .filter { it.isNotBlank() }
        .flatMapLatest { reqId ->
            combine(
                helpRequestRepository.observeRequestById(reqId),
                helpRequestRepository.observeOffersForRequest(reqId),
                authRepository.currentUser,
                _feedback,
                _openConversation
            ) { req, offers, user, (info, err), convId ->
                RequestDetailUiState(
                    isLoading = false,
                    request = req,
                    offers = offers,
                    currentUser = user,
                    feedbackMessage = info,
                    errorMessage = err,
                    openConversationId = convId,
                    deletedOrCancelled = _cancelled.value
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RequestDetailUiState()
        )

    fun load(requestId: String) {
        requestIdFlow.value = requestId
    }

    fun clearFeedback() {
        _feedback.value = null to null
    }

    fun clearOpenConversation() {
        _openConversation.value = null
    }

    fun toggleBookmark() {
        val reqId = requestIdFlow.value
        if (reqId.isBlank()) return
        viewModelScope.launch { helpRequestRepository.toggleBookmark(reqId) }
    }

    fun sendVolunteerOffer(message: String) {
        val state = uiState.value
        val req = state.request ?: return
        val user = state.currentUser ?: return
        viewModelScope.launch {
            when (
                val res = volunteerRepository.sendHelpOffer(
                    HelpOffer(
                        id = "",
                        requestId = req.id,
                        volunteerId = user.id,
                        volunteerName = user.fullName,
                        message = message,
                        status = OfferStatus.PENDING,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            ) {
                is AppResult.Success -> _feedback.value = "Offer sent! ${req.ownerName} will review it." to null
                is AppResult.Failure -> _feedback.value = null to res.message
            }
        }
    }

    fun respondToOffer(offer: HelpOffer, accept: Boolean) {
        val ownerId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            when (val res = volunteerRepository.respondToOffer(offer.id, accept, ownerId)) {
                is AppResult.Success -> {
                    _feedback.value = (if (accept) "Offer accepted! Private chat unlocked." else "Offer declined.") to null
                }
                is AppResult.Failure -> _feedback.value = null to res.message
            }
        }
    }

    fun openChatWithVolunteer(volunteerId: String, volunteerName: String) {
        val req = uiState.value.request ?: return
        viewModelScope.launch {
            when (
                val res = messagingRepository.getOrCreateConversationForAcceptedRequest(
                    requestId = req.id,
                    requestTitle = req.title,
                    ownerId = req.ownerId,
                    ownerName = req.ownerName,
                    volunteerId = volunteerId,
                    volunteerName = volunteerName
                )
            ) {
                is AppResult.Success -> _openConversation.value = res.data.id
                is AppResult.Failure -> _feedback.value = null to res.message
            }
        }
    }

    fun markCompleted(offer: HelpOffer, feedbackNote: String) {
        val req = uiState.value.request ?: return
        viewModelScope.launch {
            when (val res = volunteerRepository.markAssistanceCompleted(offer.id, req.id, offer.volunteerId, feedbackNote)) {
                is AppResult.Success -> _feedback.value = "Marked as completed! Thank you for confirming." to null
                is AppResult.Failure -> _feedback.value = null to res.message
            }
        }
    }

    fun cancelRequest() {
        val req = uiState.value.request ?: return
        val actorId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            when (val res = helpRequestRepository.cancelOrDeleteRequest(req.id, actorId)) {
                is AppResult.Success -> _cancelled.value = true
                is AppResult.Failure -> _feedback.value = null to res.message
            }
        }
    }

    fun reportRequest(reason: String, description: String) {
        val req = uiState.value.request ?: return
        val reporterId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            when (
                val res = safetyRepository.submitReport(
                    UserReport(
                        id = "",
                        reporterId = reporterId,
                        targetType = ReportTargetType.HELP_REQUEST,
                        targetId = req.id,
                        reason = reason,
                        description = description,
                        status = ReportStatus.PENDING,
                        createdAt = System.currentTimeMillis()
                    )
                )
            ) {
                is AppResult.Success -> _feedback.value = "Report submitted for moderation review." to null
                is AppResult.Failure -> _feedback.value = null to res.message
            }
        }
    }
}
