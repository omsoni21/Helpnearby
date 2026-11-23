package com.example.helpnearby.data.repository

import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.common.InputValidators
import com.example.helpnearby.core.database.*
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.core.security.SecurityGuard
import com.example.helpnearby.core.sync.SyncManager
import com.example.helpnearby.data.local.toDomain
import com.example.helpnearby.data.local.toEntity
import com.example.helpnearby.data.remote.fcm.PushNotificationTransport
import com.example.helpnearby.data.remote.supabase.SupabaseSessionManager
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBloodDonorRepository @Inject constructor(
    private val bloodDonorDao: BloodDonorDao,
    private val notificationRepository: NotificationRepository,
    private val syncManager: SyncManager?
) : BloodDonorRepository {

    constructor(
        bloodDonorDao: BloodDonorDao,
        notificationRepository: NotificationRepository
    ) : this(bloodDonorDao, notificationRepository, null)

    override fun observeAvailableDonors(
        bloodGroup: BloodGroup?,
        cityFilter: String?,
        onlyAvailable: Boolean
    ): Flow<List<DonorProfile>> =
        bloodDonorDao.observeDonors(bloodGroup?.name, cityFilter, onlyAvailable)
            .map { list -> list.map { it.toDomain() } }

    override fun observeMyDonorProfile(userId: String): Flow<DonorProfile?> =
        bloodDonorDao.observeByUserId(userId).map { it?.toDomain() }

    override fun observeDonorContactRequests(userId: String): Flow<List<DonorContactRequest>> =
        bloodDonorDao.observeContactRequestsForUser(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun upsertDonorProfile(profile: DonorProfile): AppResult<DonorProfile> {
        if (!profile.consent) {
            return AppResult.Failure("Explicit consent is required before appearing in the Blood Donor directory.")
        }
        InputValidators.validateCityAndArea(profile.city, profile.state)?.let { return AppResult.Failure(it) }
        val now = System.currentTimeMillis()
        val saved = profile.copy(updatedAt = now)
        bloodDonorDao.upsert(saved.toEntity())
        syncManager?.enqueueOperation(
            entityType = "donor_profile",
            entityId = saved.userId,
            operationType = "UPSERT",
            payloadJson = """{"userId":"${saved.userId}"}""",
            version = 1
        )
        return AppResult.Success(saved)
    }

    override suspend fun updateAvailability(userId: String, available: Boolean): AppResult<Unit> {
        bloodDonorDao.updateAvailability(userId, available, System.currentTimeMillis())
        syncManager?.enqueueOperation(
            entityType = "donor_profile",
            entityId = userId,
            operationType = "UPDATE",
            payloadJson = """{"userId":"$userId","available":$available}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }

    override suspend fun removeDonorRegistration(userId: String): AppResult<Unit> {
        bloodDonorDao.deleteByUserId(userId)
        syncManager?.enqueueOperation(
            entityType = "donor_profile",
            entityId = userId,
            operationType = "DELETE",
            payloadJson = """{"userId":"$userId"}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }

    override suspend fun sendContactRequest(request: DonorContactRequest): AppResult<DonorContactRequest> {
        if (request.donorId == request.requesterId) {
            return AppResult.Failure("Cannot send a donor request to yourself.")
        }
        if (request.message.trim().length < 10) {
            return AppResult.Failure("Please provide hospital/medical facility context (at least 10 characters).")
        }
        val now = System.currentTimeMillis()
        val saved = request.copy(
            id = request.id.ifBlank { UUID.randomUUID().toString() },
            status = OfferStatus.PENDING,
            createdAt = now
        )
        bloodDonorDao.upsertContactRequest(saved.toEntity())
        syncManager?.enqueueOperation(
            entityType = "donor_contact_request",
            entityId = saved.id,
            operationType = "CREATE",
            payloadJson = """{"id":"${saved.id}"}""",
            version = 1
        )
        notificationRepository.addNotification(
            AppNotification(
                id = UUID.randomUUID().toString(),
                userId = saved.donorId,
                type = NotificationType.DONOR_CONTACT_REQUEST,
                title = "Voluntary Blood Donor Request (${saved.bloodGroup.label})",
                body = "${saved.requesterName} in ${saved.city} sent a private contact request.",
                relatedId = saved.id,
                createdAt = now
            )
        )
        return AppResult.Success(saved)
    }

    override suspend fun respondToContactRequest(
        requestId: String,
        donorId: String,
        accept: Boolean
    ): AppResult<DonorContactRequest> {
        val existing = bloodDonorDao.getContactRequestById(requestId)
            ?: return AppResult.Failure("Contact request not found.")
        if (existing.donorId != donorId) {
            return AppResult.Failure("Only the donor can accept or decline this request.")
        }
        val updated = existing.copy(
            status = if (accept) OfferStatus.ACCEPTED.name else OfferStatus.DECLINED.name
        )
        bloodDonorDao.upsertContactRequest(updated)
        syncManager?.enqueueOperation(
            entityType = "donor_contact_request",
            entityId = updated.id,
            operationType = "UPDATE",
            payloadJson = """{"id":"${updated.id}","status":"${updated.status}"}""",
            version = 1
        )
        return AppResult.Success(updated.toDomain())
    }
}

@Singleton
class RoomLostFoundRepository @Inject constructor(
    private val lostFoundDao: LostFoundDao,
    private val notificationRepository: NotificationRepository,
    private val securityGuard: SecurityGuard,
    private val syncManager: SyncManager?
) : LostFoundRepository {

    constructor(
        lostFoundDao: LostFoundDao,
        notificationRepository: NotificationRepository,
        securityGuard: SecurityGuard
    ) : this(lostFoundDao, notificationRepository, securityGuard, null)

    override fun observePosts(type: LostFoundType?, cityFilter: String?, query: String): Flow<List<LostFoundPost>> =
        lostFoundDao.observePosts(type?.name, cityFilter, query.trim()).map { list ->
            list.map { it.toDomain() }
        }

    override fun observePostById(postId: String): Flow<LostFoundPost?> =
        lostFoundDao.observeById(postId).map { it?.toDomain() }

    override fun observeClaimsForPost(postId: String): Flow<List<LostFoundClaim>> =
        lostFoundDao.observeClaimsForPost(postId).map { list -> list.map { it.toDomain() } }

    override suspend fun createPost(post: LostFoundPost): AppResult<LostFoundPost> {
        if (post.title.trim().length < 4 || post.description.trim().length < 10) {
            return AppResult.Failure("Please provide item title and description.")
        }
        if (InputValidators.containsPrivatePhoneNumber(post.description)) {
            return AppResult.Failure("Do not put personal phone numbers in public Lost & Found listings.")
        }
        val saved = post.copy(
            id = post.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = System.currentTimeMillis()
        )
        lostFoundDao.upsertPost(saved.toEntity())
        syncManager?.enqueueOperation(
            entityType = "lost_found_post",
            entityId = saved.id,
            operationType = "CREATE",
            payloadJson = """{"id":"${saved.id}"}""",
            version = 1
        )
        return AppResult.Success(saved)
    }

    override suspend fun submitClaim(claim: LostFoundClaim): AppResult<LostFoundClaim> {
        val post = lostFoundDao.getById(claim.postId)
            ?: return AppResult.Failure("Listing not found.")
        if (post.ownerId == claim.claimantId) {
            return AppResult.Failure("You cannot claim your own listing.")
        }
        if (claim.verificationAnswer.trim().length < 3) {
            return AppResult.Failure("Provide identifying details to verify ownership.")
        }
        val now = System.currentTimeMillis()
        val saved = claim.copy(
            id = claim.id.ifBlank { UUID.randomUUID().toString() },
            status = OfferStatus.PENDING,
            createdAt = now
        )
        lostFoundDao.upsertClaim(saved.toEntity())
        lostFoundDao.updateStatus(post.id, LostFoundStatus.CLAIM_PENDING.name)
        syncManager?.enqueueOperation(
            entityType = "lost_found_claim",
            entityId = saved.id,
            operationType = "CREATE",
            payloadJson = """{"id":"${saved.id}"}""",
            version = 1
        )
        notificationRepository.addNotification(
            AppNotification(
                id = UUID.randomUUID().toString(),
                userId = post.ownerId,
                type = NotificationType.LOST_ITEM_CLAIM_REQUEST,
                title = "Claim Request on ${post.title}",
                body = "${saved.claimantName} submitted verification details.",
                relatedId = post.id,
                createdAt = now
            )
        )
        return AppResult.Success(saved)
    }

    override suspend fun respondToClaim(
        claimId: String,
        ownerId: String,
        accept: Boolean
    ): AppResult<LostFoundClaim> {
        val claim = lostFoundDao.getClaimById(claimId)
            ?: return AppResult.Failure("Claim not found.")
        val post = lostFoundDao.getById(claim.postId)
            ?: return AppResult.Failure("Listing not found.")
        if (!securityGuard.canModifyOwnedResource(ownerId, post.ownerId)) {
            return AppResult.Failure("Only the listing owner can approve or reject claims.")
        }
        val updated = claim.copy(status = if (accept) OfferStatus.ACCEPTED.name else OfferStatus.DECLINED.name)
        lostFoundDao.upsertClaim(updated)
        syncManager?.enqueueOperation(
            entityType = "lost_found_claim",
            entityId = updated.id,
            operationType = "UPDATE",
            payloadJson = """{"id":"${updated.id}","status":"${updated.status}"}""",
            version = 1
        )
        if (accept) {
            lostFoundDao.updateStatus(post.id, LostFoundStatus.RECOVERED.name)
            syncManager?.enqueueOperation(
                entityType = "lost_found_post",
                entityId = post.id,
                operationType = "UPDATE",
                payloadJson = """{"id":"${post.id}","status":"RECOVERED"}""",
                version = 1
            )
        }
        return AppResult.Success(updated.toDomain())
    }

    override suspend fun markRecovered(postId: String, ownerId: String): AppResult<Unit> {
        val post = lostFoundDao.getById(postId) ?: return AppResult.Failure("Post not found.")
        if (!securityGuard.canModifyOwnedResource(ownerId, post.ownerId)) {
            return AppResult.Failure("Only the owner can mark this item recovered.")
        }
        lostFoundDao.updateStatus(postId, LostFoundStatus.RECOVERED.name)
        syncManager?.enqueueOperation(
            entityType = "lost_found_post",
            entityId = postId,
            operationType = "UPDATE",
            payloadJson = """{"id":"$postId","status":"RECOVERED"}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }

    override suspend fun closePost(postId: String, ownerId: String): AppResult<Unit> {
        val post = lostFoundDao.getById(postId) ?: return AppResult.Failure("Post not found.")
        if (!securityGuard.canModifyOwnedResource(ownerId, post.ownerId)) {
            return AppResult.Failure("Only the owner can close this report.")
        }
        lostFoundDao.updateStatus(postId, LostFoundStatus.CLOSED.name)
        syncManager?.enqueueOperation(
            entityType = "lost_found_post",
            entityId = postId,
            operationType = "UPDATE",
            payloadJson = """{"id":"$postId","status":"CLOSED"}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }
}

@Singleton
class RoomCommunityRepository @Inject constructor(
    private val communityDao: CommunityDao,
    private val securityGuard: SecurityGuard,
    private val syncManager: SyncManager?
) : CommunityRepository {

    constructor(
        communityDao: CommunityDao,
        securityGuard: SecurityGuard
    ) : this(communityDao, securityGuard, null)

    override fun observePosts(
        cityFilter: String?,
        category: CommunityCategory?,
        query: String
    ): Flow<List<CommunityPost>> =
        communityDao.observePosts(cityFilter, category?.name, query.trim())
            .map { list -> list.map { it.toDomain() } }

    override fun observeComments(postId: String): Flow<List<PostComment>> =
        communityDao.observeComments(postId).map { list -> list.map { it.toDomain() } }

    override suspend fun createPost(post: CommunityPost): AppResult<CommunityPost> {
        if (post.title.trim().length < 4 || post.content.trim().length < 10) {
            return AppResult.Failure("Please write a title and meaningful post content.")
        }
        val saved = post.copy(
            id = post.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = System.currentTimeMillis()
        )
        communityDao.upsertPost(saved.toEntity())
        syncManager?.enqueueOperation(
            entityType = "community_post",
            entityId = saved.id,
            operationType = "CREATE",
            payloadJson = """{"id":"${saved.id}"}""",
            version = 1
        )
        return AppResult.Success(saved)
    }

    override suspend fun updatePost(post: CommunityPost, actorId: String): AppResult<CommunityPost> {
        val existing = communityDao.getById(post.id) ?: return AppResult.Failure("Post not found.")
        if (!securityGuard.canModifyOwnedResource(actorId, existing.authorId)) {
            return AppResult.Failure("Only the author can edit this post.")
        }
        communityDao.upsertPost(post.toEntity())
        syncManager?.enqueueOperation(
            entityType = "community_post",
            entityId = post.id,
            operationType = "UPDATE",
            payloadJson = """{"id":"${post.id}"}""",
            version = 1
        )
        return AppResult.Success(post)
    }

    override suspend fun deletePost(postId: String, actorId: String): AppResult<Unit> {
        val existing = communityDao.getById(postId) ?: return AppResult.Failure("Post not found.")
        if (!securityGuard.canModifyOwnedResource(actorId, existing.authorId)) {
            return AppResult.Failure("Only the author can delete this post.")
        }
        communityDao.deleteById(postId)
        syncManager?.enqueueOperation(
            entityType = "community_post",
            entityId = postId,
            operationType = "DELETE",
            payloadJson = """{"id":"$postId"}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }

    override suspend fun toggleLike(postId: String): AppResult<Unit> {
        val existing = communityDao.getById(postId) ?: return AppResult.Failure("Post not found.")
        val nowLiked = !existing.isLikedByMe
        val nextCount = if (nowLiked) existing.likeCount + 1 else (existing.likeCount - 1).coerceAtLeast(0)
        communityDao.upsertPost(existing.copy(isLikedByMe = nowLiked, likeCount = nextCount))
        syncManager?.enqueueOperation(
            entityType = "community_post",
            entityId = postId,
            operationType = "UPDATE",
            payloadJson = """{"id":"$postId","likeCount":$nextCount}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }

    override suspend fun addComment(comment: PostComment): AppResult<PostComment> {
        if (comment.content.trim().isEmpty()) {
            return AppResult.Failure("Comment cannot be empty.")
        }
        val saved = comment.copy(
            id = comment.id.ifBlank { UUID.randomUUID().toString() },
            content = comment.content.trim(),
            createdAt = System.currentTimeMillis()
        )
        communityDao.insertComment(saved.toEntity())
        communityDao.incrementCommentCount(saved.postId)
        syncManager?.enqueueOperation(
            entityType = "comment",
            entityId = saved.id,
            operationType = "CREATE",
            payloadJson = """{"id":"${saved.id}"}""",
            version = 1
        )
        return AppResult.Success(saved)
    }
}

@Singleton
class RoomMessagingRepository @Inject constructor(
    private val messagingDao: MessagingDao,
    private val safetyDao: SafetyModerationDao,
    private val notificationRepository: NotificationRepository,
    private val securityGuard: SecurityGuard,
    private val syncManager: SyncManager,
    private val backendConfig: BackendConfig
) : MessagingRepository {

    override fun observeConversationsForUser(userId: String): Flow<List<Conversation>> =
        messagingDao.observeConversationsForUser(userId).map { list -> list.map { it.toDomain() } }

    override fun observeConversation(conversationId: String, userId: String): Flow<Conversation?> =
        messagingDao.observeConversation(conversationId, userId).map { it?.toDomain() }

    override fun observeMessages(conversationId: String, userId: String): Flow<List<Message>> =
        kotlinx.coroutines.flow.combine(
            messagingDao.observeConversation(conversationId, userId),
            messagingDao.observeMessages(conversationId)
        ) { convEntity, list ->
            val conv = convEntity?.toDomain()
            if (conv != null && securityGuard.canAccessConversation(userId, conv)) {
                list.map { it.toDomain() }
            } else {
                emptyList()
            }
        }

    override fun observeTotalUnreadCount(userId: String): Flow<Int> =
        messagingDao.observeTotalUnread(userId)

    override suspend fun getOrCreateConversationForAcceptedRequest(
        requestId: String,
        requestTitle: String,
        ownerId: String,
        ownerName: String,
        volunteerId: String,
        volunteerName: String
    ): AppResult<Conversation> {
        val existing = messagingDao.getByRequestAndVolunteer(requestId, volunteerId)
        if (existing != null) return AppResult.Success(existing.toDomain())

        val now = System.currentTimeMillis()
        val created = ConversationEntity(
            id = UUID.randomUUID().toString(),
            requestId = requestId,
            requestTitle = requestTitle,
            ownerId = ownerId,
            volunteerId = volunteerId,
            ownerName = ownerName,
            volunteerName = volunteerName,
            status = "OPEN",
            lastMessageText = "Help offer accepted. Coordinate safely here.",
            lastMessageAt = now,
            unreadCount = 0,
            createdAt = now
        )
        messagingDao.upsertConversation(created)
        syncManager.enqueueOperation(
            entityType = "conversation",
            entityId = created.id,
            operationType = "CREATE",
            payloadJson = """{"id":"${created.id}"}""",
            version = 1
        )
        return AppResult.Success(created.toDomain())
    }

    override suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        text: String
    ): AppResult<Message> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return AppResult.Failure("Message cannot be empty.")

        val convEntity = messagingDao.getConversationById(conversationId)
            ?: return AppResult.Failure("Conversation not found.")
        val conv = convEntity.toDomain()

        if (!securityGuard.canAccessConversation(senderId, conv)) {
            return AppResult.Failure("Access denied: only the request owner and accepted volunteer may message here.")
        }
        if (conv.status == "CLOSED") {
            return AppResult.Failure("This conversation is closed.")
        }

        val otherUserId = if (conv.ownerId == senderId) conv.volunteerId else conv.ownerId
        val blockedByMe = safetyDao.observeBlockedUserIds(senderId).first()
        val blockedByOther = safetyDao.observeBlockedUserIds(otherUserId).first()
        if (otherUserId in blockedByMe || senderId in blockedByOther) {
            return AppResult.Failure("Messaging is unavailable because one participant has blocked the other.")
        }

        val now = System.currentTimeMillis()
        val syncStatus = if (backendConfig.isOfflineLocalOnly) SyncStatus.LOCAL_ONLY else SyncStatus.PENDING_CREATE
        val msg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            text = trimmed,
            createdAt = now,
            readAt = null,
            syncStatus = syncStatus.name
        )
        messagingDao.insertMessage(msg)
        messagingDao.upsertConversation(
            convEntity.copy(
                lastMessageText = trimmed,
                lastMessageAt = now,
                unreadCount = convEntity.unreadCount + 1
            )
        )
        notificationRepository.addNotification(
            AppNotification(
                id = UUID.randomUUID().toString(),
                userId = otherUserId,
                type = NotificationType.NEW_MESSAGE,
                title = "New message on ${conv.requestTitle}",
                body = trimmed.take(90),
                relatedId = conversationId,
                createdAt = now
            )
        )
        syncManager.enqueueOperation(
            entityType = "conversation",
            entityId = conversationId,
            operationType = "UPDATE",
            payloadJson = """{"id":"$conversationId"}""",
            version = 1
        )
        syncManager.enqueueOperation(
            entityType = "message",
            entityId = msg.id,
            operationType = "SEND",
            payloadJson = """{"id":"${msg.id}","conversationId":"$conversationId"}""",
            version = 1
        )
        return AppResult.Success(msg.toDomain())
    }

    override suspend fun markConversationRead(conversationId: String, userId: String): AppResult<Unit> {
        val conv = messagingDao.getConversationById(conversationId)?.toDomain()
            ?: return AppResult.Failure("Conversation not found.")
        if (!securityGuard.canAccessConversation(userId, conv)) {
            return AppResult.Failure("Unauthorized.")
        }
        messagingDao.markIncomingMessagesRead(conversationId, userId, System.currentTimeMillis())
        messagingDao.clearUnreadCount(conversationId)
        return AppResult.Success(Unit)
    }

    override suspend fun closeConversation(conversationId: String, userId: String): AppResult<Unit> {
        val conv = messagingDao.getConversationById(conversationId)?.toDomain()
            ?: return AppResult.Failure("Conversation not found.")
        if (!securityGuard.canAccessConversation(userId, conv)) {
            return AppResult.Failure("Unauthorized.")
        }
        messagingDao.closeConversation(conversationId)
        syncManager.enqueueOperation(
            entityType = "conversation",
            entityId = conversationId,
            operationType = "UPDATE",
            payloadJson = """{"id":"$conversationId","status":"CLOSED"}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }
}

@Singleton
class RoomNotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao,
    private val pushTransport: PushNotificationTransport,
    private val syncManager: SyncManager?
) : NotificationRepository {

    constructor(
        notificationDao: NotificationDao,
        pushTransport: PushNotificationTransport
    ) : this(notificationDao, pushTransport, null)

    override fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        notificationDao.observeNotifications(userId).map { list -> list.map { it.toDomain() } }

    override fun observeUnreadCount(userId: String): Flow<Int> =
        notificationDao.observeUnreadCount(userId)

    override suspend fun addNotification(notification: AppNotification): AppResult<Unit> {
        val saved = notification.copy(id = notification.id.ifBlank { UUID.randomUUID().toString() })
        notificationDao.insert(saved.toEntity())
        pushTransport.dispatchLocalSystemAlert(saved)
        syncManager?.enqueueOperation(
            entityType = "notification",
            entityId = saved.id,
            operationType = "CREATE",
            payloadJson = """{"id":"${saved.id}"}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }

    override suspend fun markAsRead(notificationId: String): AppResult<Unit> {
        notificationDao.markRead(notificationId, System.currentTimeMillis())
        return AppResult.Success(Unit)
    }

    override suspend fun markAllAsRead(userId: String): AppResult<Unit> {
        notificationDao.markAllRead(userId, System.currentTimeMillis())
        return AppResult.Success(Unit)
    }
}

@Singleton
class RoomSafetyModerationRepository @Inject constructor(
    private val safetyDao: SafetyModerationDao,
    private val userDao: UserDao,
    private val helpRequestDao: HelpRequestDao,
    private val communityDao: CommunityDao,
    private val lostFoundDao: LostFoundDao,
    private val securityGuard: SecurityGuard,
    private val backendConfig: BackendConfig,
    private val syncManager: SyncManager?,
    private val supabaseSessionManager: SupabaseSessionManager?
) : SafetyModerationRepository {

    constructor(
        safetyDao: SafetyModerationDao,
        userDao: UserDao,
        helpRequestDao: HelpRequestDao,
        communityDao: CommunityDao,
        lostFoundDao: LostFoundDao,
        securityGuard: SecurityGuard,
        backendConfig: BackendConfig
    ) : this(
        safetyDao, userDao, helpRequestDao, communityDao, lostFoundDao,
        securityGuard, backendConfig, null, null
    )

    override fun observeBlockedUsers(blockerId: String): Flow<List<BlockedUser>> =
        safetyDao.observeBlockedUsers(blockerId).map { list -> list.map { it.toDomain() } }

    override fun observeReports(): Flow<List<UserReport>> =
        safetyDao.observeReports().map { list -> list.map { it.toDomain() } }

    override fun observeAuditLogs(): Flow<List<AdminAuditLog>> =
        safetyDao.observeAuditLogs().map { list -> list.map { it.toDomain() } }

    override suspend fun submitReport(report: UserReport): AppResult<UserReport> {
        if (report.reason.isBlank()) return AppResult.Failure("Please select a report reason.")
        val saved = report.copy(
            id = report.id.ifBlank { UUID.randomUUID().toString() },
            status = ReportStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        safetyDao.upsertReport(
            UserReportEntity(
                id = saved.id,
                reporterId = saved.reporterId,
                targetType = saved.targetType.name,
                targetId = saved.targetId,
                reason = saved.reason,
                description = saved.description.trim(),
                status = saved.status.name,
                createdAt = saved.createdAt
            )
        )
        syncManager?.enqueueOperation(
            entityType = "user_report",
            entityId = saved.id,
            operationType = "CREATE",
            payloadJson = """{"id":"${saved.id}"}""",
            version = 1
        )
        return AppResult.Success(saved)
    }

    override suspend fun blockUser(
        blockerId: String,
        blockedUserId: String,
        blockedUserName: String
    ): AppResult<Unit> {
        if (blockerId == blockedUserId) return AppResult.Failure("You cannot block yourself.")
        val id = UUID.randomUUID().toString()
        safetyDao.insertBlockedUser(
            BlockedUserEntity(
                id = id,
                blockerId = blockerId,
                blockedUserId = blockedUserId,
                blockedUserName = blockedUserName,
                createdAt = System.currentTimeMillis()
            )
        )
        syncManager?.enqueueOperation(
            entityType = "blocked_user",
            entityId = id,
            operationType = "CREATE",
            payloadJson = """{"id":"$id"}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }

    override suspend fun unblockUser(blockerId: String, blockedUserId: String): AppResult<Unit> {
        safetyDao.removeBlockedUser(blockerId, blockedUserId)
        syncManager?.enqueueOperation(
            entityType = "blocked_user",
            entityId = "$blockerId|$blockedUserId",
            operationType = "DELETE",
            payloadJson = """{"blockerId":"$blockerId","blockedUserId":"$blockedUserId"}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }

    override suspend fun adminResolveReport(
        adminActor: User,
        reportId: String,
        removeTargetContent: Boolean,
        suspendTargetAccount: Boolean,
        notes: String
    ): AppResult<Unit> {
        // Production rule: never trust client-supplied admin role; require trusted server verification.
        val serverVerified = when {
            backendConfig.isSupabaseConfigured -> supabaseSessionManager?.isServerVerifiedAdmin() == true
            else -> false
        }
        if (!securityGuard.canPerformAdminModeration(adminActor, serverVerified)) {
            return AppResult.Failure(
                "Admin privileges require trusted server-side role verification."
            )
        }

        val report = safetyDao.getReportById(reportId)
            ?: return AppResult.Failure("Report not found.")
        val now = System.currentTimeMillis()

        val nextStatus = if (removeTargetContent || suspendTargetAccount) {
            ReportStatus.REVIEWED_ACTION_TAKEN
        } else {
            ReportStatus.DISMISSED
        }
        safetyDao.upsertReport(report.copy(status = nextStatus.name))

        if (removeTargetContent) {
            when (report.targetType) {
                ReportTargetType.HELP_REQUEST.name ->
                    helpRequestDao.updateStatus(report.targetId, RequestStatus.UNDER_REVIEW.name, now)
                ReportTargetType.COMMUNITY_POST.name ->
                    communityDao.updateStatus(report.targetId, "REMOVED")
                ReportTargetType.LOST_FOUND_POST.name ->
                    lostFoundDao.updateStatus(report.targetId, LostFoundStatus.UNDER_REVIEW.name)
            }
        }
        if (suspendTargetAccount && report.targetType == ReportTargetType.USER.name) {
            userDao.setUserSuspended(report.targetId, true, now)
        }

        safetyDao.insertAuditLog(
            AdminAuditLogEntity(
                id = UUID.randomUUID().toString(),
                adminId = adminActor.id,
                action = if (removeTargetContent) "REMOVE_CONTENT" else if (suspendTargetAccount) "SUSPEND_USER" else "DISMISS_REPORT",
                targetId = report.targetId,
                details = notes.ifBlank { "Reviewed report ${report.id}" },
                createdAt = now
            )
        )
        return AppResult.Success(Unit)
    }
}
