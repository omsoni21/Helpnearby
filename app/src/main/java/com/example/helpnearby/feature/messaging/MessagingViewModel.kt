package com.example.helpnearby.feature.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.AuthRepository
import com.example.helpnearby.domain.repository.MessagingRepository
import com.example.helpnearby.domain.repository.SafetyModerationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationsUiState(
    val currentUser: User? = null,
    val conversations: List<Conversation> = emptyList(),
    val totalUnread: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val messagingRepository: MessagingRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val uiState: StateFlow<ConversationsUiState> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(ConversationsUiState())
            } else {
                combine(
                    messagingRepository.observeConversationsForUser(user.id),
                    messagingRepository.observeTotalUnreadCount(user.id)
                ) { convs, unread ->
                    ConversationsUiState(
                        currentUser = user,
                        conversations = convs,
                        totalUnread = unread
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConversationsUiState())
}

data class ChatUiState(
    val currentUser: User? = null,
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val error: String? = null,
    val info: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messagingRepository: MessagingRepository,
    private val safetyRepository: SafetyModerationRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private val conversationIdFlow = MutableStateFlow("")
    private val feedbackFlow = MutableStateFlow<Pair<String?, String?>>(null to null)

    val uiState: StateFlow<ChatUiState> = combine(
        conversationIdFlow.filter { it.isNotBlank() },
        authRepository.currentUser
    ) { convId, user -> convId to user }
        .flatMapLatest { (convId, user) ->
            val uid = user?.id ?: ""
            combine(
                messagingRepository.observeConversation(convId, uid),
                messagingRepository.observeMessages(convId, uid),
                feedbackFlow
            ) { conv, msgs, (info, err) ->
                ChatUiState(
                    currentUser = user,
                    conversation = conv,
                    messages = msgs,
                    info = info,
                    error = err
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    fun load(conversationId: String) {
        conversationIdFlow.value = conversationId
        val uid = uiState.value.currentUser?.id
        if (uid != null) {
            viewModelScope.launch { messagingRepository.markConversationRead(conversationId, uid) }
        }
    }

    fun sendMessage(text: String) {
        val convId = conversationIdFlow.value
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            when (val res = messagingRepository.sendMessage(convId, uid, text)) {
                is AppResult.Success -> feedbackFlow.value = null to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    fun closeConversation() {
        val convId = conversationIdFlow.value
        val uid = uiState.value.currentUser?.id ?: return
        viewModelScope.launch {
            when (val res = messagingRepository.closeConversation(convId, uid)) {
                is AppResult.Success -> feedbackFlow.value = "Conversation closed." to null
                is AppResult.Failure -> feedbackFlow.value = null to res.message
            }
        }
    }

    fun blockOtherUser() {
        val conv = uiState.value.conversation ?: return
        val me = uiState.value.currentUser ?: return
        val otherId = if (conv.ownerId == me.id) conv.volunteerId else conv.ownerId
        val otherName = if (conv.ownerId == me.id) conv.volunteerName else conv.ownerName
        viewModelScope.launch {
            safetyRepository.blockUser(me.id, otherId, otherName)
            feedbackFlow.value = "$otherName has been blocked." to null
        }
    }

    fun reportOtherUser(reason: String) {
        val conv = uiState.value.conversation ?: return
        val me = uiState.value.currentUser ?: return
        val otherId = if (conv.ownerId == me.id) conv.volunteerId else conv.ownerId
        viewModelScope.launch {
            safetyRepository.submitReport(
                UserReport(
                    id = "",
                    reporterId = me.id,
                    targetType = ReportTargetType.USER,
                    targetId = otherId,
                    reason = reason,
                    description = "Reported from request chat ${conv.requestId}",
                    status = ReportStatus.PENDING,
                    createdAt = System.currentTimeMillis()
                )
            )
            feedbackFlow.value = "User reported for safety review." to null
        }
    }
}
