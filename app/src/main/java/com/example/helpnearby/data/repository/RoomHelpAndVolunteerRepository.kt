package com.example.helpnearby.data.repository

import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.common.InputValidators
import com.example.helpnearby.core.database.*
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.core.security.SecurityGuard
import com.example.helpnearby.core.sync.SyncManager
import com.example.helpnearby.data.local.toDomain
import com.example.helpnearby.data.local.toEntity
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.HelpRequestRepository
import com.example.helpnearby.domain.repository.MessagingRepository
import com.example.helpnearby.domain.repository.NotificationRepository
import com.example.helpnearby.domain.repository.VolunteerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHelpRequestRepository @Inject constructor(
    private val helpRequestDao: HelpRequestDao,
    private val helpOfferDao: HelpOfferDao,
    private val securityGuard: SecurityGuard,
    private val syncManager: SyncManager,
    private val backendConfig: BackendConfig
) : HelpRequestRepository {

    override fun observeRequests(
        cityFilter: String?,
        categoryFilter: HelpCategory?,
        searchQuery: String,
        limit: Int
    ): Flow<List<HelpRequest>> =
        helpRequestDao.observeFilteredRequests(
            city = cityFilter,
            category = categoryFilter?.name,
            query = searchQuery.trim(),
            limit = limit
        ).map { list -> list.map { it.toDomain() } }

    override fun observeRequestById(requestId: String): Flow<HelpRequest?> =
        helpRequestDao.observeById(requestId).map { it?.toDomain() }

    override fun observeMyRequests(ownerId: String): Flow<List<HelpRequest>> =
        helpRequestDao.observeByOwner(ownerId).map { list -> list.map { it.toDomain() } }

    override fun observeBookmarkedRequests(): Flow<List<HelpRequest>> =
        helpRequestDao.observeBookmarked().map { list -> list.map { it.toDomain() } }

    override fun observeOffersForRequest(requestId: String): Flow<List<HelpOffer>> =
        helpOfferDao.observeOffersForRequest(requestId).map { list -> list.map { it.toDomain() } }

    override fun observeOffersByVolunteer(volunteerId: String): Flow<List<HelpOffer>> =
        helpOfferDao.observeOffersByVolunteer(volunteerId).map { list -> list.map { it.toDomain() } }

    override suspend fun createRequest(request: HelpRequest): AppResult<HelpRequest> {
        InputValidators.validateHelpRequest(
            title = request.title,
            description = request.description,
            city = request.city,
            state = request.state,
            approximateArea = request.approximateArea
        )?.let { return AppResult.Failure(it) }

        if (!securityGuard.allowActionWithRateLimit("create_req_${request.ownerId}")) {
            return AppResult.Failure("Please wait a moment before creating another request.")
        }

        val now = System.currentTimeMillis()
        val syncStatus = if (backendConfig.isOfflineLocalOnly) SyncStatus.LOCAL_ONLY else SyncStatus.PENDING_CREATE
        val toSave = request.copy(
            id = request.id.ifBlank { UUID.randomUUID().toString() },
            title = request.title.trim(),
            description = request.description.trim(),
            city = request.city.trim(),
            state = request.state.trim(),
            approximateArea = request.approximateArea.trim(),
            version = 1,
            syncStatus = syncStatus,
            createdAt = now,
            updatedAt = now
        )
        helpRequestDao.upsert(toSave.toEntity())
        syncManager.enqueueOperation(
            entityType = "help_request",
            entityId = toSave.id,
            operationType = "CREATE",
            payloadJson = """{"id":"${toSave.id}"}""",
            version = toSave.version
        )
        return AppResult.Success(toSave)
    }

    override suspend fun updateRequest(request: HelpRequest, actorId: String): AppResult<HelpRequest> {
        val existing = helpRequestDao.getById(request.id)
            ?: return AppResult.Failure("Help request not found.")
        if (!securityGuard.canModifyOwnedResource(actorId, existing.ownerId)) {
            return AppResult.Failure("Only the request owner can edit this request.")
        }

        InputValidators.validateHelpRequest(
            title = request.title,
            description = request.description,
            city = request.city,
            state = request.state,
            approximateArea = request.approximateArea
        )?.let { return AppResult.Failure(it) }

        val updated = request.copy(
            version = existing.version + 1,
            syncStatus = if (backendConfig.isOfflineLocalOnly) SyncStatus.LOCAL_ONLY else SyncStatus.PENDING_UPDATE,
            updatedAt = System.currentTimeMillis()
        )
        helpRequestDao.upsert(updated.toEntity())
        syncManager.enqueueOperation(
            entityType = "help_request",
            entityId = updated.id,
            operationType = "UPDATE",
            payloadJson = """{"id":"${updated.id}","version":${updated.version}}""",
            version = updated.version
        )
        return AppResult.Success(updated)
    }

    override suspend fun cancelOrDeleteRequest(requestId: String, actorId: String): AppResult<Unit> {
        val existing = helpRequestDao.getById(requestId)
            ?: return AppResult.Failure("Help request not found.")
        if (!securityGuard.canModifyOwnedResource(actorId, existing.ownerId)) {
            return AppResult.Failure("Only the request owner can cancel or delete this request.")
        }
        helpRequestDao.updateStatus(requestId, RequestStatus.CANCELLED.name, System.currentTimeMillis())
        syncManager.enqueueOperation(
            entityType = "help_request",
            entityId = requestId,
            operationType = "CANCEL",
            payloadJson = """{"id":"$requestId","status":"CANCELLED"}""",
            version = existing.version + 1
        )
        return AppResult.Success(Unit)
    }

    override suspend fun toggleBookmark(requestId: String): AppResult<Unit> {
        helpRequestDao.toggleBookmark(requestId, System.currentTimeMillis())
        return AppResult.Success(Unit)
    }

    override suspend fun updateRequestStatus(
        requestId: String,
        newStatus: RequestStatus,
        actorId: String
    ): AppResult<HelpRequest> {
        val existing = helpRequestDao.getById(requestId)
            ?: return AppResult.Failure("Help request not found.")
        if (!securityGuard.canModifyOwnedResource(actorId, existing.ownerId)) {
            return AppResult.Failure("Unauthorized status update.")
        }
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            status = newStatus.name,
            version = existing.version + 1,
            syncStatus = if (backendConfig.isOfflineLocalOnly) SyncStatus.LOCAL_ONLY.name else SyncStatus.PENDING_UPDATE.name,
            updatedAt = now
        )
        helpRequestDao.upsert(updated)
        syncManager.enqueueOperation(
            entityType = "help_request",
            entityId = requestId,
            operationType = "UPDATE",
            payloadJson = """{"id":"$requestId","status":"${newStatus.name}"}""",
            version = updated.version
        )
        return AppResult.Success(updated.toDomain())
    }
}

@Singleton
class RoomVolunteerRepository @Inject constructor(
    private val volunteerDao: VolunteerDao,
    private val helpOfferDao: HelpOfferDao,
    private val helpRequestDao: HelpRequestDao,
    private val messagingRepository: MessagingRepository,
    private val notificationRepository: NotificationRepository,
    private val securityGuard: SecurityGuard,
    private val syncManager: SyncManager
) : VolunteerRepository {

    override fun observeVolunteers(cityFilter: String?, query: String): Flow<List<VolunteerProfile>> =
        volunteerDao.observeVolunteers(cityFilter, query.trim()).map { list -> list.map { it.toDomain() } }

    override fun observeVolunteerProfile(userId: String): Flow<VolunteerProfile?> =
        volunteerDao.observeByUserId(userId).map { it?.toDomain() }

    override suspend fun upsertVolunteerProfile(profile: VolunteerProfile): AppResult<VolunteerProfile> {
        if (profile.fullName.isBlank() || profile.city.isBlank()) {
            return AppResult.Failure("Name and city are required for volunteer profile.")
        }
        // Never allow client to self-assign verified status
        val existing = volunteerDao.getByUserId(profile.userId)
        val safeProfile = profile.copy(
            verificationStatus = existing?.let {
                runCatching { VerificationStatus.valueOf(it.verificationStatus) }
                    .getOrDefault(VerificationStatus.UNVERIFIED)
            } ?: VerificationStatus.UNVERIFIED
        )
        volunteerDao.upsert(safeProfile.toEntity())
        syncManager.enqueueOperation(
            entityType = "volunteer_profile",
            entityId = safeProfile.userId,
            operationType = "UPSERT",
            payloadJson = """{"userId":"${safeProfile.userId}"}""",
            version = 1
        )
        return AppResult.Success(safeProfile)
    }

    override suspend fun updateAvailability(userId: String, available: Boolean): AppResult<Unit> {
        volunteerDao.updateAvailability(userId, available)
        syncManager.enqueueOperation(
            entityType = "volunteer_profile",
            entityId = userId,
            operationType = "UPDATE",
            payloadJson = """{"userId":"$userId","available":$available}""",
            version = 1
        )
        return AppResult.Success(Unit)
    }

    override suspend fun sendHelpOffer(offer: HelpOffer): AppResult<HelpOffer> {
        val request = helpRequestDao.getById(offer.requestId)
            ?: return AppResult.Failure("Help request not found.")
        if (request.ownerId == offer.volunteerId) {
            return AppResult.Failure("You cannot send a volunteer offer on your own request.")
        }
        if (request.status != RequestStatus.OPEN.name && request.status != RequestStatus.OFFER_RECEIVED.name) {
            return AppResult.Failure("This request is no longer accepting offers.")
        }
        if (offer.message.trim().length < 5) {
            return AppResult.Failure("Please write a brief note explaining how you can help.")
        }

        val now = System.currentTimeMillis()
        val saved = offer.copy(
            id = offer.id.ifBlank { UUID.randomUUID().toString() },
            message = offer.message.trim(),
            status = OfferStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        helpOfferDao.upsert(saved.toEntity())
        if (request.status == RequestStatus.OPEN.name) {
            helpRequestDao.updateStatus(request.id, RequestStatus.OFFER_RECEIVED.name, now)
            syncManager.enqueueOperation(
                entityType = "help_request",
                entityId = request.id,
                operationType = "UPDATE",
                payloadJson = """{"id":"${request.id}","status":"OFFER_RECEIVED"}""",
                version = request.version + 1
            )
        }
        notificationRepository.addNotification(
            AppNotification(
                id = UUID.randomUUID().toString(),
                userId = request.ownerId,
                type = NotificationType.NEW_HELP_OFFER,
                title = "New Help Offer from ${saved.volunteerName}",
                body = "\"${saved.message}\" for ${request.title}",
                relatedId = request.id,
                createdAt = now
            )
        )
        syncManager.enqueueOperation(
            entityType = "help_offer",
            entityId = saved.id,
            operationType = "CREATE",
            payloadJson = """{"id":"${saved.id}","requestId":"${saved.requestId}"}""",
            version = 1
        )
        return AppResult.Success(saved)
    }

    override suspend fun respondToOffer(
        offerId: String,
        accept: Boolean,
        requestOwnerId: String
    ): AppResult<HelpOffer> {
        val offerEntity = helpOfferDao.getById(offerId)
            ?: return AppResult.Failure("Offer not found.")
        val request = helpRequestDao.getById(offerEntity.requestId)
            ?: return AppResult.Failure("Associated request not found.")

        if (!securityGuard.canModifyOwnedResource(requestOwnerId, request.ownerId)) {
            return AppResult.Failure("Only the request owner can accept or decline offers.")
        }

        val now = System.currentTimeMillis()
        val newStatus = if (accept) OfferStatus.ACCEPTED else OfferStatus.DECLINED
        val updated = offerEntity.copy(status = newStatus.name, updatedAt = now)
        helpOfferDao.upsert(updated)
        syncManager.enqueueOperation(
            entityType = "help_offer",
            entityId = updated.id,
            operationType = "UPDATE",
            payloadJson = """{"id":"${updated.id}","status":"${newStatus.name}"}""",
            version = 1
        )

        if (accept) {
            helpRequestDao.updateStatus(request.id, RequestStatus.ACCEPTED.name, now)
            syncManager.enqueueOperation(
                entityType = "help_request",
                entityId = request.id,
                operationType = "UPDATE",
                payloadJson = """{"id":"${request.id}","status":"ACCEPTED"}""",
                version = request.version + 1
            )
            messagingRepository.getOrCreateConversationForAcceptedRequest(
                requestId = request.id,
                requestTitle = request.title,
                ownerId = request.ownerId,
                ownerName = request.ownerName,
                volunteerId = offerEntity.volunteerId,
                volunteerName = offerEntity.volunteerName
            )
            notificationRepository.addNotification(
                AppNotification(
                    id = UUID.randomUUID().toString(),
                    userId = offerEntity.volunteerId,
                    type = NotificationType.OFFER_ACCEPTED,
                    title = "Your Help Offer Was Accepted!",
                    body = "${request.ownerName} accepted your offer for \"${request.title}\". You can now chat privately.",
                    relatedId = request.id,
                    createdAt = now
                )
            )
        } else {
            notificationRepository.addNotification(
                AppNotification(
                    id = UUID.randomUUID().toString(),
                    userId = offerEntity.volunteerId,
                    type = NotificationType.OFFER_DECLINED,
                    title = "Offer Update",
                    body = "${request.ownerName} declined your offer for \"${request.title}\".",
                    relatedId = request.id,
                    createdAt = now
                )
            )
        }
        return AppResult.Success(updated.toDomain())
    }

    override suspend fun markAssistanceCompleted(
        offerId: String,
        requestId: String,
        volunteerId: String,
        feedbackNote: String
    ): AppResult<Unit> {
        val offer = helpOfferDao.getById(offerId)
            ?: return AppResult.Failure("Offer not found.")
        val request = helpRequestDao.getById(requestId)
            ?: return AppResult.Failure("Request not found.")
        val now = System.currentTimeMillis()
        helpOfferDao.upsert(offer.copy(status = OfferStatus.COMPLETED.name, updatedAt = now))
        helpRequestDao.updateStatus(request.id, RequestStatus.COMPLETED.name, now)
        volunteerDao.incrementCompletedCount(volunteerId)

        syncManager.enqueueOperation(
            entityType = "help_offer",
            entityId = offer.id,
            operationType = "UPDATE",
            payloadJson = """{"id":"${offer.id}","status":"COMPLETED"}""",
            version = 1
        )
        syncManager.enqueueOperation(
            entityType = "help_request",
            entityId = request.id,
            operationType = "UPDATE",
            payloadJson = """{"id":"${request.id}","status":"COMPLETED"}""",
            version = request.version + 1
        )
        syncManager.enqueueOperation(
            entityType = "volunteer_profile",
            entityId = volunteerId,
            operationType = "UPDATE",
            payloadJson = """{"userId":"$volunteerId"}""",
            version = 1
        )
        notificationRepository.addNotification(
            AppNotification(
                id = UUID.randomUUID().toString(),
                userId = volunteerId,
                type = NotificationType.REQUEST_STATUS_CHANGED,
                title = "Assistance Marked Completed",
                body = "Thank you for helping with \"${request.title}\"! ${feedbackNote.ifBlank { "" }}".trim(),
                relatedId = request.id,
                createdAt = now
            )
        )
        return AppResult.Success(Unit)
    }
}
