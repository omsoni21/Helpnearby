package com.example.helpnearby.data.remote.supabase

import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.database.HelpNearbyDatabase
import com.example.helpnearby.core.database.PendingSyncOperationEntity
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.domain.model.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bidirectional Supabase synchronization gateway bridging Room local cache and
 * Supabase PostgREST (`/rest/v1/...`) endpoints when [BackendConfig.isSupabaseConfigured] is true.
 */
@Singleton
class SupabaseRemoteGateway @Inject constructor(
    private val backendConfig: BackendConfig,
    private val postgrestApi: SupabasePostgrestApi,
    private val sessionManager: SupabaseSessionManager,
    private val db: HelpNearbyDatabase
) {
    /**
     * Dispatches a single queued [PendingSyncOperationEntity] to Supabase PostgREST.
     * Returns true if the remote server acknowledged the mutation; false otherwise.
     */
    suspend fun dispatchSyncOperation(op: PendingSyncOperationEntity): Boolean {
        if (!backendConfig.isSupabaseConfigured) return false
        val apiKey = backendConfig.supabaseAnonKey
        val bearer = sessionManager.getValidBearerToken() ?: return false

        return runCatching {
            when (op.entityType) {
                "help_request" -> {
                    val entity = db.helpRequestDao().getById(op.entityId) ?: return true
                    val resp = postgrestApi.upsertHelpRequest(apiKey, bearer, entity.toSupabaseDto())
                    if (resp.isSuccessful) {
                        db.helpRequestDao().updateSyncStatus(entity.id, SyncStatus.SYNCED.name)
                        true
                    } else false
                }

                "help_offer" -> {
                    val entity = db.helpOfferDao().getById(op.entityId) ?: return true
                    val resp = postgrestApi.upsertHelpOffer(apiKey, bearer, entity.toSupabaseDto())
                    if (resp.isSuccessful) {
                        db.helpOfferDao().updateSyncStatus(entity.id, SyncStatus.SYNCED.name)
                        true
                    } else false
                }

                "volunteer_profile" -> {
                    val entity = db.volunteerDao().getByUserId(op.entityId) ?: return true
                    postgrestApi.upsertVolunteerProfile(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                }

                "donor_profile" -> {
                    if (op.operationType == "DELETE") {
                        postgrestApi.deleteDonorProfile(apiKey, bearer, "eq.${op.entityId}").isSuccessful
                    } else {
                        val entity = db.bloodDonorDao().getByUserId(op.entityId) ?: return true
                        postgrestApi.upsertDonorProfile(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                    }
                }

                "donor_contact_request" -> {
                    val entity = db.bloodDonorDao().getContactRequestById(op.entityId) ?: return true
                    postgrestApi.upsertDonorContactRequest(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                }

                "lost_found_post" -> {
                    val entity = db.lostFoundDao().getById(op.entityId) ?: return true
                    postgrestApi.upsertLostFoundPost(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                }

                "lost_found_claim" -> {
                    val entity = db.lostFoundDao().getClaimById(op.entityId) ?: return true
                    postgrestApi.upsertLostFoundClaim(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                }

                "community_post" -> {
                    if (op.operationType == "DELETE") {
                        postgrestApi.deleteCommunityPost(apiKey, bearer, "eq.${op.entityId}").isSuccessful
                    } else {
                        val entity = db.communityDao().getById(op.entityId) ?: return true
                        postgrestApi.upsertCommunityPost(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                    }
                }

                "comment" -> {
                    val entity = db.communityDao().getCommentById(op.entityId) ?: return true
                    postgrestApi.upsertComment(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                }

                "conversation" -> {
                    val entity = db.messagingDao().getConversationById(op.entityId) ?: return true
                    postgrestApi.upsertConversation(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                }

                "message" -> {
                    val entity = db.messagingDao().getMessageById(op.entityId) ?: return true
                    val resp = postgrestApi.upsertMessage(apiKey, bearer, entity.toSupabaseDto())
                    if (resp.isSuccessful) {
                        db.messagingDao().updateMessageSyncStatus(entity.id, SyncStatus.SYNCED.name)
                        true
                    } else false
                }

                "notification" -> {
                    val entity = db.notificationDao().getById(op.entityId) ?: return true
                    postgrestApi.upsertNotification(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                }

                "user_report" -> {
                    val entity = db.safetyModerationDao().getReportById(op.entityId) ?: return true
                    postgrestApi.insertReport(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                }

                "blocked_user" -> {
                    if (op.operationType == "DELETE") {
                        val parts = op.entityId.split("|")
                        if (parts.size == 2) {
                            postgrestApi.deleteBlockedUser(apiKey, bearer, "eq.${parts[0]}", "eq.${parts[1]}").isSuccessful
                        } else true
                    } else {
                        val entity = db.safetyModerationDao().getBlockedById(op.entityId) ?: return true
                        postgrestApi.upsertBlockedUser(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                    }
                }

                "user_profile" -> {
                    val entity = db.userDao().getUserById(op.entityId) ?: return true
                    postgrestApi.upsertUser(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                }

                "user_preferences" -> {
                    val entity = db.userDao().getPreferences(op.entityId) ?: return true
                    postgrestApi.upsertPreferences(apiKey, bearer, entity.toSupabaseDto()).isSuccessful
                }

                else -> false
            }
        }.getOrDefault(false)
    }

    /**
     * Pulls latest community data + user's private records from Supabase into Room cache,
     * applying version + timestamp conflict resolution so unsynced local edits are never clobbered.
     */
    suspend fun refreshFromCloud(city: String?, userId: String?): AppResult<Unit> {
        if (!backendConfig.isSupabaseConfigured) return AppResult.Success(Unit)
        val apiKey = backendConfig.supabaseAnonKey
        val userBearer = sessionManager.getValidBearerToken()
        val publicBearer = userBearer ?: "Bearer $apiKey"

        return runCatching {
            val cityFilter = city?.takeIf { it.isNotBlank() }?.let { "eq.$it" }

            // 1. Help Requests (public non-PII listings)
            val reqResp = postgrestApi.fetchHelpRequests(apiKey, publicBearer, cityEq = cityFilter)
            if (reqResp.isSuccessful) {
                reqResp.body().orEmpty().forEach { remote ->
                    val local = db.helpRequestDao().getById(remote.id)
                    val remoteUpdatedAt = remote.updatedAt.fromIsoToEpochMs()
                    val shouldApplyRemote = local == null ||
                        local.syncStatus == SyncStatus.SYNCED.name ||
                        remote.version > local.version ||
                        (remote.version == local.version && remoteUpdatedAt >= local.updatedAt)
                    if (shouldApplyRemote) {
                        db.helpRequestDao().upsert(remote.toEntity(isBookmarked = local?.isBookmarked ?: false))
                    }
                }
            }

            // 2. Volunteer Profiles
            val volResp = postgrestApi.fetchVolunteerProfiles(apiKey, publicBearer, cityEq = cityFilter)
            if (volResp.isSuccessful) {
                volResp.body().orEmpty().forEach { dto ->
                    db.volunteerDao().upsert(dto.toEntity())
                }
            }

            // 3. Public Consented Blood Donors
            val donorResp = postgrestApi.fetchPublicDonors(apiKey, publicBearer)
            if (donorResp.isSuccessful) {
                donorResp.body().orEmpty().forEach { dto ->
                    db.bloodDonorDao().upsert(dto.toEntity())
                }
            }

            // 4. Lost & Found
            val lfResp = postgrestApi.fetchLostFoundPosts(apiKey, publicBearer)
            if (lfResp.isSuccessful) {
                lfResp.body().orEmpty().forEach { dto ->
                    db.lostFoundDao().upsertPost(dto.toEntity())
                }
            }

            // 5. Community Posts
            val commResp = postgrestApi.fetchCommunityPosts(apiKey, publicBearer)
            if (commResp.isSuccessful) {
                commResp.body().orEmpty().forEach { dto ->
                    val existing = db.communityDao().getById(dto.id)
                    db.communityDao().upsertPost(dto.toEntity(isLikedByMe = existing?.isLikedByMe ?: false))
                }
            }

            // 6. Authenticated User-Private Data
            if (userBearer != null && !userId.isNullOrBlank()) {
                val offersResp = postgrestApi.fetchHelpOffers(apiKey, userBearer)
                if (offersResp.isSuccessful) {
                    offersResp.body().orEmpty().forEach { db.helpOfferDao().upsert(it.toEntity()) }
                }

                val donorReqResp = postgrestApi.fetchDonorContactRequests(apiKey, userBearer)
                if (donorReqResp.isSuccessful) {
                    donorReqResp.body().orEmpty().forEach { db.bloodDonorDao().upsertContactRequest(it.toEntity()) }
                }

                val convResp = postgrestApi.fetchConversations(apiKey, userBearer)
                if (convResp.isSuccessful) {
                    convResp.body().orEmpty().forEach { convDto ->
                        val localConv = db.messagingDao().getConversationById(convDto.id)
                        db.messagingDao().upsertConversation(convDto.toEntity(unreadCount = localConv?.unreadCount ?: 0))
                        val msgResp = postgrestApi.fetchMessages(apiKey, userBearer, conversationIdEq = "eq.${convDto.id}")
                        if (msgResp.isSuccessful) {
                            msgResp.body().orEmpty().forEach { db.messagingDao().insertMessage(it.toEntity()) }
                        }
                    }
                }

                val notifResp = postgrestApi.fetchNotifications(apiKey, userBearer, userIdEq = "eq.$userId")
                if (notifResp.isSuccessful) {
                    notifResp.body().orEmpty().forEach { db.notificationDao().insert(it.toEntity()) }
                }

                val blockedResp = postgrestApi.fetchBlockedUsers(apiKey, userBearer, blockerIdEq = "eq.$userId")
                if (blockedResp.isSuccessful) {
                    blockedResp.body().orEmpty().forEach { db.safetyModerationDao().insertBlockedUser(it.toEntity()) }
                }
            }

            AppResult.Success(Unit)
        }.getOrElse { err ->
            AppResult.Failure(err.message ?: "Unable to refresh from Supabase.")
        }
    }
}
