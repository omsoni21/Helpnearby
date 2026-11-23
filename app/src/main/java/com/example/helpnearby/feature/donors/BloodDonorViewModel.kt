package com.example.helpnearby.feature.donors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.AuthRepository
import com.example.helpnearby.domain.repository.BloodDonorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BloodDonorUiState(
    val selectedBloodGroup: BloodGroup? = null,
    val cityQuery: String = "",
    val onlyAvailable: Boolean = true,
    val currentUser: User? = null,
    val myDonorProfile: DonorProfile? = null,
    val donors: List<DonorProfile> = emptyList(),
    val contactRequests: List<DonorContactRequest> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BloodDonorViewModel @Inject constructor(
    private val bloodDonorRepository: BloodDonorRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val bloodGroupFlow = MutableStateFlow<BloodGroup?>(null)
    private val cityFlow = MutableStateFlow("")
    private val onlyAvailableFlow = MutableStateFlow(true)
    private val feedbackFlow = MutableStateFlow<Pair<String?, String?>>(null to null)

    val uiState: StateFlow<BloodDonorUiState> = combine(
        bloodGroupFlow,
        cityFlow,
        onlyAvailableFlow,
        authRepository.currentUser,
        feedbackFlow
    ) { bg, city, avail, user, fb ->
        DonorMeta(bg, city, avail, user, fb)
    }.flatMapLatest { meta ->
        val uid = meta.user?.id ?: ""
        combine(
            bloodDonorRepository.observeAvailableDonors(
                bloodGroup = meta.bloodGroup,
                cityFilter = meta.city.trim().takeIf { it.isNotEmpty() },
                onlyAvailable = meta.onlyAvailable
            ),
            if (uid.isNotEmpty()) bloodDonorRepository.observeMyDonorProfile(uid) else flowOf(null),
            if (uid.isNotEmpty()) bloodDonorRepository.observeDonorContactRequests(uid) else flowOf(emptyList())
        ) { donors, mine, reqs ->
            BloodDonorUiState(
                selectedBloodGroup = meta.bloodGroup,
                cityQuery = meta.city,
                onlyAvailable = meta.onlyAvailable,
                currentUser = meta.user,
                myDonorProfile = mine,
                donors = donors,
                contactRequests = reqs,
                message = meta.feedback.first,
                error = meta.feedback.second
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BloodDonorUiState())

    fun setBloodGroup(group: BloodGroup?) { bloodGroupFlow.value = group }
    fun setCityQuery(city: String) { cityFlow.value = city }
    fun setOnlyAvailable(avail: Boolean) { onlyAvailableFlow.value = avail }

    fun saveDonorProfile(
        bloodGroup: BloodGroup,
        city: String,
        stateName: String,
        available: Boolean,
        consent: Boolean,
        lastDonationDate: String
    ) {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val displayInitials = user.fullName.split(" ").let { parts ->
                if (parts.size > 1) "${parts.first()} ${parts.last().take(1)}." else parts.first()
            }
            val profile = DonorProfile(
                userId = user.id,
                displayName = displayInitials,
                bloodGroup = bloodGroup,
                city = city.trim(),
                state = stateName.trim(),
                available = available,
                consent = consent,
                lastDonationDate = lastDonationDate.trim().takeIf { it.isNotEmpty() },
                createdAt = uiState.value.myDonorProfile?.createdAt ?: now,
                updatedAt = now
            )
            when (val res = bloodDonorRepository.upsertDonorProfile(profile)) {
                is AppResult.Success -> feedbackFlow.value = "Donor profile saved with your consent." to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    fun toggleAvailability(available: Boolean) {
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch { bloodDonorRepository.updateAvailability(uid, available) }
    }

    fun removeRegistration() {
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            bloodDonorRepository.removeDonorRegistration(uid)
            feedbackFlow.value = "Donor registration removed from directory." to null
        }
    }

    fun sendContactRequest(donor: DonorProfile, message: String) {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            when (
                val res = bloodDonorRepository.sendContactRequest(
                    DonorContactRequest(
                        id = "",
                        donorId = donor.userId,
                        requesterId = user.id,
                        requesterName = user.fullName,
                        bloodGroup = donor.bloodGroup,
                        city = donor.city,
                        message = message,
                        status = OfferStatus.PENDING,
                        createdAt = System.currentTimeMillis()
                    )
                )
            ) {
                is AppResult.Success -> feedbackFlow.value = "Private contact request sent to ${donor.displayName}." to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    fun respondToRequest(requestId: String, accept: Boolean) {
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            when (val res = bloodDonorRepository.respondToContactRequest(requestId, uid, accept)) {
                is AppResult.Success -> feedbackFlow.value = (if (accept) "Request accepted." else "Request declined.") to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    private data class DonorMeta(
        val bloodGroup: BloodGroup?,
        val city: String,
        val onlyAvailable: Boolean,
        val user: User?,
        val feedback: Pair<String?, String?>
    )
}
