package com.example.helpnearby.feature.volunteers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.domain.model.User
import com.example.helpnearby.domain.model.VerificationStatus
import com.example.helpnearby.domain.model.VolunteerProfile
import com.example.helpnearby.domain.repository.AuthRepository
import com.example.helpnearby.domain.repository.VolunteerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VolunteerUiState(
    val query: String = "",
    val cityFilter: String? = null,
    val currentUser: User? = null,
    val myProfile: VolunteerProfile? = null,
    val volunteers: List<VolunteerProfile> = emptyList(),
    val selectedVolunteer: VolunteerProfile? = null,
    val message: String? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VolunteerViewModel @Inject constructor(
    private val volunteerRepository: VolunteerRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val cityFlow = MutableStateFlow<String?>(null)
    private val selectedIdFlow = MutableStateFlow<String?>(null)
    private val feedbackFlow = MutableStateFlow<Pair<String?, String?>>(null to null)

    val uiState: StateFlow<VolunteerUiState> = combine(
        queryFlow,
        cityFlow,
        authRepository.currentUser,
        selectedIdFlow,
        feedbackFlow
    ) { q, city, user, selectedId, fb ->
        VolunteerMeta(q, city, user, selectedId, fb)
    }.flatMapLatest { meta ->
        val myUid = meta.user?.id ?: ""
        combine(
            volunteerRepository.observeVolunteers(meta.city, meta.query),
            if (myUid.isNotEmpty()) volunteerRepository.observeVolunteerProfile(myUid) else flowOf(null),
            if (!meta.selectedId.isNullOrBlank()) volunteerRepository.observeVolunteerProfile(meta.selectedId) else flowOf(null)
        ) { list, mine, selected ->
            VolunteerUiState(
                query = meta.query,
                cityFilter = meta.city,
                currentUser = meta.user,
                myProfile = mine,
                volunteers = list,
                selectedVolunteer = selected,
                message = meta.feedback.first,
                error = meta.feedback.second
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VolunteerUiState())

    fun setQuery(q: String) { queryFlow.value = q }
    fun setCityFilter(c: String?) { cityFlow.value = c }
    fun selectVolunteer(userId: String) { selectedIdFlow.value = userId }

    fun saveMyVolunteerProfile(bio: String, skillsCsv: String, interestsCsv: String, city: String, available: Boolean) {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            val profile = VolunteerProfile(
                userId = user.id,
                fullName = user.fullName,
                photoUrl = user.photoUrl,
                city = city.trim().ifBlank { user.city },
                bio = bio.trim(),
                skills = skillsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                interests = interestsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                availability = available,
                completedCount = uiState.value.myProfile?.completedCount ?: 0,
                verificationStatus = uiState.value.myProfile?.verificationStatus ?: VerificationStatus.UNVERIFIED,
                createdAt = uiState.value.myProfile?.createdAt ?: System.currentTimeMillis()
            )
            when (val res = volunteerRepository.upsertVolunteerProfile(profile)) {
                is AppResult.Success -> feedbackFlow.value = "Volunteer profile updated!" to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    fun toggleMyAvailability(available: Boolean) {
        val userId = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            volunteerRepository.updateAvailability(userId, available)
        }
    }

    private data class VolunteerMeta(
        val query: String,
        val city: String?,
        val user: User?,
        val selectedId: String?,
        val feedback: Pair<String?, String?>
    )
}
