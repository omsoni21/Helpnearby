package com.example.helpnearby.feature.lostfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.AuthRepository
import com.example.helpnearby.domain.repository.LostFoundRepository
import com.example.helpnearby.domain.repository.SafetyModerationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LostFoundUiState(
    val selectedType: LostFoundType = LostFoundType.LOST,
    val query: String = "",
    val cityFilter: String? = null,
    val currentUser: User? = null,
    val posts: List<LostFoundPost> = emptyList(),
    val selectedPostClaims: List<LostFoundClaim> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val createdSuccess: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LostFoundViewModel @Inject constructor(
    private val lostFoundRepository: LostFoundRepository,
    private val authRepository: AuthRepository,
    private val safetyRepository: SafetyModerationRepository
) : ViewModel() {

    private val typeFlow = MutableStateFlow(LostFoundType.LOST)
    private val queryFlow = MutableStateFlow("")
    private val cityFlow = MutableStateFlow<String?>(null)
    private val inspectedPostIdFlow = MutableStateFlow<String?>(null)
    private val feedbackFlow = MutableStateFlow<Pair<String?, String?>>(null to null)
    private val createdFlow = MutableStateFlow(false)

    val uiState: StateFlow<LostFoundUiState> = combine(
        typeFlow,
        queryFlow,
        cityFlow,
        authRepository.currentUser,
        inspectedPostIdFlow
    ) { type, q, city, user, postId ->
        LFMete(type, q, city, user, postId)
    }.flatMapLatest { meta ->
        combine(
            lostFoundRepository.observePosts(meta.type, meta.city, meta.query),
            if (!meta.inspectedPostId.isNullOrBlank()) lostFoundRepository.observeClaimsForPost(meta.inspectedPostId)
            else flowOf(emptyList()),
            feedbackFlow,
            createdFlow
        ) { posts, claims, fb, created ->
            LostFoundUiState(
                selectedType = meta.type,
                query = meta.query,
                cityFilter = meta.city,
                currentUser = meta.user,
                posts = posts,
                selectedPostClaims = claims,
                message = fb.first,
                error = fb.second,
                createdSuccess = created
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LostFoundUiState())

    fun setType(type: LostFoundType) { typeFlow.value = type }
    fun setQuery(q: String) { queryFlow.value = q }
    fun inspectPostClaims(postId: String) { inspectedPostIdFlow.value = postId }

    fun createPost(
        type: LostFoundType,
        title: String,
        description: String,
        category: String,
        city: String,
        stateName: String,
        area: String,
        imageUrl: String?,
        hiddenHint: String,
        eventDate: String
    ) {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            val res = lostFoundRepository.createPost(
                LostFoundPost(
                    id = "",
                    ownerId = user.id,
                    ownerName = user.fullName,
                    type = type,
                    title = title,
                    description = description,
                    category = category,
                    city = city,
                    state = stateName,
                    approximateArea = area,
                    imageUrl = imageUrl,
                    contactPreference = "In-app Claim Verification",
                    status = LostFoundStatus.ACTIVE,
                    hiddenVerificationHint = hiddenHint,
                    eventDate = eventDate,
                    createdAt = System.currentTimeMillis()
                )
            )
            when (res) {
                is AppResult.Success -> createdFlow.value = true
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    fun submitClaim(post: LostFoundPost, message: String, verificationAnswer: String) {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            when (
                val res = lostFoundRepository.submitClaim(
                    LostFoundClaim(
                        id = "",
                        postId = post.id,
                        claimantId = user.id,
                        claimantName = user.fullName,
                        message = message,
                        verificationAnswer = verificationAnswer,
                        status = OfferStatus.PENDING,
                        createdAt = System.currentTimeMillis()
                    )
                )
            ) {
                is AppResult.Success -> feedbackFlow.value = "Claim submitted privately to ${post.ownerName}." to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    fun respondToClaim(claimId: String, accept: Boolean) {
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            when (val res = lostFoundRepository.respondToClaim(claimId, uid, accept)) {
                is AppResult.Success -> feedbackFlow.value = (if (accept) "Claim verified & item marked recovered!" else "Claim declined.") to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    fun markRecovered(postId: String) {
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch { lostFoundRepository.markRecovered(postId, uid) }
    }

    fun closePost(postId: String) {
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch { lostFoundRepository.closePost(postId, uid) }
    }

    fun reportPost(postId: String, reason: String) {
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            safetyRepository.submitReport(
                UserReport(
                    id = "",
                    reporterId = uid,
                    targetType = ReportTargetType.LOST_FOUND_POST,
                    targetId = postId,
                    reason = reason,
                    description = "Flagged from Lost & Found",
                    status = ReportStatus.PENDING,
                    createdAt = System.currentTimeMillis()
                )
            )
            feedbackFlow.value = "Suspicious listing reported." to null
        }
    }

    private data class LFMete(
        val type: LostFoundType,
        val query: String,
        val city: String?,
        val user: User?,
        val inspectedPostId: String?
    )
}
