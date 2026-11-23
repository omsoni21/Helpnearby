package com.example.helpnearby.domain.repository

import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    val isLoggedIn: Flow<Boolean>
    val backendProvider: BackendProvider

    suspend fun registerWithEmail(
        fullName: String,
        email: String,
        password: String,
        city: String,
        state: String,
        country: String,
        language: AppLanguage,
        interests: List<String>,
        volunteerAvailable: Boolean
    ): AppResult<User>

    suspend fun loginWithEmail(email: String, password: String): AppResult<User>
    suspend fun loginWithGoogleIdToken(idToken: String, nonce: String? = null): AppResult<User>
    suspend fun startGoogleOAuthSignIn(): AppResult<Unit>
    suspend fun completeSupabaseOAuthSession(
        accessToken: String,
        refreshToken: String?,
        expiresAtEpochSec: Long?,
        userId: String,
        email: String?,
        emailConfirmed: Boolean,
        fullName: String?,
        isAdmin: Boolean
    ): AppResult<User>
    suspend fun sendPasswordReset(email: String): AppResult<String>
    suspend fun verifyEmail(code: String): AppResult<User>
    suspend fun logout(): AppResult<Unit>
    suspend fun deleteAccount(userId: String): AppResult<Unit>
}

interface UserRepository {
    fun observeUser(userId: String): Flow<User?>
    fun observePreferences(userId: String): Flow<UserPreferences>
    suspend fun updateProfile(user: User): AppResult<User>
    suspend fun updatePreferences(preferences: UserPreferences): AppResult<Unit>
    suspend fun updateSelectedCityAndArea(city: String, area: String): AppResult<Unit>
    suspend fun updateThemeMode(theme: AppThemeMode): AppResult<Unit>
    suspend fun updateLanguage(language: AppLanguage): AppResult<Unit>
    suspend fun updateLocationEnabled(enabled: Boolean): AppResult<Unit>
    suspend fun updateNotificationsEnabled(enabled: Boolean): AppResult<Unit>
}

interface HelpRequestRepository {
    fun observeRequests(
        cityFilter: String? = null,
        categoryFilter: HelpCategory? = null,
        searchQuery: String = "",
        limit: Int = 50
    ): Flow<List<HelpRequest>>

    fun observeRequestById(requestId: String): Flow<HelpRequest?>
    fun observeMyRequests(ownerId: String): Flow<List<HelpRequest>>
    fun observeBookmarkedRequests(): Flow<List<HelpRequest>>
    fun observeOffersForRequest(requestId: String): Flow<List<HelpOffer>>
    fun observeOffersByVolunteer(volunteerId: String): Flow<List<HelpOffer>>

    suspend fun createRequest(request: HelpRequest): AppResult<HelpRequest>
    suspend fun updateRequest(request: HelpRequest, actorId: String): AppResult<HelpRequest>
    suspend fun cancelOrDeleteRequest(requestId: String, actorId: String): AppResult<Unit>
    suspend fun toggleBookmark(requestId: String): AppResult<Unit>
    suspend fun updateRequestStatus(
        requestId: String,
        newStatus: RequestStatus,
        actorId: String
    ): AppResult<HelpRequest>
}

interface VolunteerRepository {
    fun observeVolunteers(cityFilter: String? = null, query: String = ""): Flow<List<VolunteerProfile>>
    fun observeVolunteerProfile(userId: String): Flow<VolunteerProfile?>
    suspend fun upsertVolunteerProfile(profile: VolunteerProfile): AppResult<VolunteerProfile>
    suspend fun updateAvailability(userId: String, available: Boolean): AppResult<Unit>
    suspend fun sendHelpOffer(offer: HelpOffer): AppResult<HelpOffer>
    suspend fun respondToOffer(
        offerId: String,
        accept: Boolean,
        requestOwnerId: String
    ): AppResult<HelpOffer>
    suspend fun markAssistanceCompleted(
        offerId: String,
        requestId: String,
        volunteerId: String,
        feedbackNote: String
    ): AppResult<Unit>
}

interface BloodDonorRepository {
    fun observeAvailableDonors(
        bloodGroup: BloodGroup? = null,
        cityFilter: String? = null,
        onlyAvailable: Boolean = true
    ): Flow<List<DonorProfile>>

    fun observeMyDonorProfile(userId: String): Flow<DonorProfile?>
    fun observeDonorContactRequests(userId: String): Flow<List<DonorContactRequest>>

    suspend fun upsertDonorProfile(profile: DonorProfile): AppResult<DonorProfile>
    suspend fun updateAvailability(userId: String, available: Boolean): AppResult<Unit>
    suspend fun removeDonorRegistration(userId: String): AppResult<Unit>
    suspend fun sendContactRequest(request: DonorContactRequest): AppResult<DonorContactRequest>
    suspend fun respondToContactRequest(
        requestId: String,
        donorId: String,
        accept: Boolean
    ): AppResult<DonorContactRequest>
}

interface LostFoundRepository {
    fun observePosts(
        type: LostFoundType? = null,
        cityFilter: String? = null,
        query: String = ""
    ): Flow<List<LostFoundPost>>

    fun observePostById(postId: String): Flow<LostFoundPost?>
    fun observeClaimsForPost(postId: String): Flow<List<LostFoundClaim>>

    suspend fun createPost(post: LostFoundPost): AppResult<LostFoundPost>
    suspend fun submitClaim(claim: LostFoundClaim): AppResult<LostFoundClaim>
    suspend fun respondToClaim(
        claimId: String,
        ownerId: String,
        accept: Boolean
    ): AppResult<LostFoundClaim>
    suspend fun markRecovered(postId: String, ownerId: String): AppResult<Unit>
    suspend fun closePost(postId: String, ownerId: String): AppResult<Unit>
}

interface CommunityRepository {
    fun observePosts(
        cityFilter: String? = null,
        category: CommunityCategory? = null,
        query: String = ""
    ): Flow<List<CommunityPost>>

    fun observeComments(postId: String): Flow<List<PostComment>>
    suspend fun createPost(post: CommunityPost): AppResult<CommunityPost>
    suspend fun updatePost(post: CommunityPost, actorId: String): AppResult<CommunityPost>
    suspend fun deletePost(postId: String, actorId: String): AppResult<Unit>
    suspend fun toggleLike(postId: String): AppResult<Unit>
    suspend fun addComment(comment: PostComment): AppResult<PostComment>
}

interface MessagingRepository {
    fun observeConversationsForUser(userId: String): Flow<List<Conversation>>
    fun observeConversation(conversationId: String, userId: String): Flow<Conversation?>
    fun observeMessages(conversationId: String, userId: String): Flow<List<Message>>
    fun observeTotalUnreadCount(userId: String): Flow<Int>

    suspend fun getOrCreateConversationForAcceptedRequest(
        requestId: String,
        requestTitle: String,
        ownerId: String,
        ownerName: String,
        volunteerId: String,
        volunteerName: String
    ): AppResult<Conversation>

    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        text: String
    ): AppResult<Message>

    suspend fun markConversationRead(conversationId: String, userId: String): AppResult<Unit>
    suspend fun closeConversation(conversationId: String, userId: String): AppResult<Unit>
}

interface NotificationRepository {
    fun observeNotifications(userId: String): Flow<List<AppNotification>>
    fun observeUnreadCount(userId: String): Flow<Int>
    suspend fun addNotification(notification: AppNotification): AppResult<Unit>
    suspend fun markAsRead(notificationId: String): AppResult<Unit>
    suspend fun markAllAsRead(userId: String): AppResult<Unit>
}

interface SafetyModerationRepository {
    fun observeBlockedUsers(blockerId: String): Flow<List<BlockedUser>>
    fun observeReports(): Flow<List<UserReport>>
    fun observeAuditLogs(): Flow<List<AdminAuditLog>>

    suspend fun submitReport(report: UserReport): AppResult<UserReport>
    suspend fun blockUser(blockerId: String, blockedUserId: String, blockedUserName: String): AppResult<Unit>
    suspend fun unblockUser(blockerId: String, blockedUserId: String): AppResult<Unit>
    suspend fun adminResolveReport(
        adminActor: User,
        reportId: String,
        removeTargetContent: Boolean,
        suspendTargetAccount: Boolean,
        notes: String
    ): AppResult<Unit>
}
