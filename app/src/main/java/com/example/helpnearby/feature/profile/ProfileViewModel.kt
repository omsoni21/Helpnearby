package com.example.helpnearby.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val volunteerProfile: VolunteerProfile? = null,
    val donorProfile: DonorProfile? = null,
    val myRequests: List<HelpRequest> = emptyList(),
    val myOffers: List<HelpOffer> = emptyList(),
    val savedRequests: List<HelpRequest> = emptyList(),
    val blockedUsers: List<BlockedUser> = emptyList(),
    val reports: List<UserReport> = emptyList(),
    val auditLogs: List<AdminAuditLog> = emptyList(),
    val isCloudConnected: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val helpRequestRepository: HelpRequestRepository,
    private val volunteerRepository: VolunteerRepository,
    private val bloodDonorRepository: BloodDonorRepository,
    private val safetyRepository: SafetyModerationRepository,
    private val backendConfig: BackendConfig
) : ViewModel() {

    private val feedbackFlow = MutableStateFlow<Pair<String?, String?>>(null to null)

    val uiState: StateFlow<ProfileUiState> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(ProfileUiState(isCloudConnected = !backendConfig.isOfflineLocalOnly))
            } else {
                val baseFlows = combine(
                    volunteerRepository.observeVolunteerProfile(user.id),
                    bloodDonorRepository.observeMyDonorProfile(user.id),
                    helpRequestRepository.observeMyRequests(user.id),
                    helpRequestRepository.observeOffersByVolunteer(user.id),
                    helpRequestRepository.observeBookmarkedRequests()
                ) { vol, donor, myReqs, myOffers, saved ->
                    ProfileCore(vol, donor, myReqs, myOffers, saved)
                }

                combine(
                    baseFlows,
                    safetyRepository.observeBlockedUsers(user.id),
                    safetyRepository.observeReports(),
                    safetyRepository.observeAuditLogs(),
                    feedbackFlow
                ) { core, blocked, reports, logs, (msg, err) ->
                    ProfileUiState(
                        user = user,
                        volunteerProfile = core.vol,
                        donorProfile = core.donor,
                        myRequests = core.myReqs,
                        myOffers = core.myOffers,
                        savedRequests = core.saved,
                        blockedUsers = blocked,
                        reports = reports,
                        auditLogs = logs,
                        isCloudConnected = !backendConfig.isOfflineLocalOnly,
                        message = msg,
                        error = err
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun updateProfile(fullName: String, city: String, stateName: String, country: String, photoUrl: String?, volunteerAvailable: Boolean) {
        val current = uiState.value.user ?: return
        viewModelScope.launch {
            when (
                val res = userRepository.updateProfile(
                    current.copy(
                        fullName = fullName.trim(),
                        city = city.trim(),
                        state = stateName.trim(),
                        country = country.trim(),
                        photoUrl = photoUrl,
                        volunteerAvailable = volunteerAvailable
                    )
                )
            ) {
                is AppResult.Success -> feedbackFlow.value = "Profile updated!" to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    fun unblockUser(blockedUserId: String) {
        val uid = uiState.value.user?.id ?: return
        viewModelScope.launch {
            safetyRepository.unblockUser(uid, blockedUserId)
        }
    }

    fun resolveReport(reportId: String, removeContent: Boolean, suspendAccount: Boolean, notes: String) {
        val actor = uiState.value.user ?: return
        viewModelScope.launch {
            when (
                val res = safetyRepository.adminResolveReport(
                    adminActor = actor,
                    reportId = reportId,
                    removeTargetContent = removeContent,
                    suspendTargetAccount = suspendAccount,
                    notes = notes
                )
            ) {
                is AppResult.Success -> feedbackFlow.value = "Moderation action logged." to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    private data class ProfileCore(
        val vol: VolunteerProfile?,
        val donor: DonorProfile?,
        val myReqs: List<HelpRequest>,
        val myOffers: List<HelpOffer>,
        val saved: List<HelpRequest>
    )
}
