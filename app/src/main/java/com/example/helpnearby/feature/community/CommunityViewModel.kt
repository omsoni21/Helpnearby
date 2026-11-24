package com.example.helpnearby.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.sync.SyncManager
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.AuthRepository
import com.example.helpnearby.domain.repository.CommunityRepository
import com.example.helpnearby.domain.repository.SafetyModerationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommunityUiState(
    val query: String = "",
    val cityFilter: String? = null,
    val selectedCategory: CommunityCategory? = null,
    val currentUser: User? = null,
    val posts: List<CommunityPost> = emptyList(),
    val activeComments: List<PostComment> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val communityRepository: CommunityRepository,
    private val authRepository: AuthRepository,
    private val safetyRepository: SafetyModerationRepository,
    private val prefsDataStore: PreferencesDataStore,
    private val syncManager: SyncManager
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val cityFlow = MutableStateFlow<String?>(null)
    private val categoryFlow = MutableStateFlow<CommunityCategory?>(null)
    private val activePostIdFlow = MutableStateFlow<String?>(null)
    private val feedbackFlow = MutableStateFlow<Pair<String?, String?>>(null to null)

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

    val uiState: StateFlow<CommunityUiState> = combine(
        queryFlow,
        cityFlow,
        categoryFlow,
        authRepository.currentUser,
        activePostIdFlow
    ) { q, city, cat, user, postId ->
        CommunityMeta(q, city, cat, user, postId)
    }.flatMapLatest { meta ->
        combine(
            communityRepository.observePosts(meta.city, meta.cat, meta.query),
            if (!meta.activePostId.isNullOrBlank()) communityRepository.observeComments(meta.activePostId)
            else flowOf(emptyList()),
            feedbackFlow
        ) { posts, comments, fb ->
            CommunityUiState(
                query = meta.query,
                cityFilter = meta.city,
                selectedCategory = meta.cat,
                currentUser = meta.user,
                posts = posts,
                activeComments = comments,
                message = fb.first,
                error = fb.second
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CommunityUiState())

    fun setQuery(q: String) { queryFlow.value = q }
    fun setCityFilter(city: String?) { cityFlow.value = city }
    fun setCategory(cat: CommunityCategory?) { categoryFlow.value = cat }
    fun loadComments(postId: String) { activePostIdFlow.value = postId }

    fun savePost(existingPost: CommunityPost?, title: String, content: String, category: CommunityCategory, city: String) {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            val res = if (existingPost != null) {
                communityRepository.updatePost(
                    existingPost.copy(title = title, content = content, category = category, city = city),
                    user.id
                )
            } else {
                communityRepository.createPost(
                    CommunityPost(
                        id = "",
                        authorId = user.id,
                        authorName = user.fullName,
                        title = title,
                        content = content,
                        category = category,
                        city = city.ifBlank { user.city },
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            when (res) {
                is AppResult.Success -> feedbackFlow.value = "Community post published!" to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    fun deletePost(postId: String) {
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            communityRepository.deletePost(postId, uid)
        }
    }

    fun toggleLike(postId: String) {
        viewModelScope.launch { communityRepository.toggleLike(postId) }
    }

    fun addComment(postId: String, text: String) {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            communityRepository.addComment(
                PostComment(
                    id = "",
                    postId = postId,
                    authorId = user.id,
                    authorName = user.fullName,
                    content = text,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun reportPost(postId: String, reason: String) {
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            safetyRepository.submitReport(
                UserReport(
                    id = "",
                    reporterId = uid,
                    targetType = ReportTargetType.COMMUNITY_POST,
                    targetId = postId,
                    reason = reason,
                    description = "Reported from Community Feed",
                    status = ReportStatus.PENDING,
                    createdAt = System.currentTimeMillis()
                )
            )
            feedbackFlow.value = "Post reported for moderation review." to null
        }
    }

    private data class CommunityMeta(
        val query: String,
        val city: String?,
        val cat: CommunityCategory?,
        val user: User?,
        val activePostId: String?
    )
}
