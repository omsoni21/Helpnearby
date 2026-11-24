package com.example.helpnearby.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observeUserById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Query("UPDATE users SET is_suspended = :suspended, updated_at = :updatedAt WHERE id = :userId")
    suspend fun setUserSuspended(userId: String, suspended: Boolean, updatedAt: Long)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId LIMIT 1")
    fun observePreferences(userId: String): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId LIMIT 1")
    suspend fun getPreferences(userId: String): UserPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(prefs: UserPreferencesEntity)
}

@Dao
interface HelpRequestDao {
    @Query("""
        SELECT * FROM help_requests
        WHERE status != 'CANCELLED' AND status != 'UNDER_REVIEW'
          AND (:city IS NULL OR :city = '' OR LOWER(city) = LOWER(:city))
          AND (:category IS NULL OR category = :category)
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR approximate_area LIKE '%' || :query || '%')
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun observeFilteredRequests(
        city: String?,
        category: String?,
        query: String,
        limit: Int
    ): Flow<List<HelpRequestEntity>>

    @Query("SELECT * FROM help_requests WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<HelpRequestEntity?>

    @Query("SELECT * FROM help_requests WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HelpRequestEntity?

    @Query("SELECT * FROM help_requests WHERE owner_id = :ownerId ORDER BY created_at DESC")
    fun observeByOwner(ownerId: String): Flow<List<HelpRequestEntity>>

    @Query("SELECT * FROM help_requests WHERE is_bookmarked = 1 ORDER BY updated_at DESC")
    fun observeBookmarked(): Flow<List<HelpRequestEntity>>

    @Upsert
    suspend fun upsert(request: HelpRequestEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(requests: List<HelpRequestEntity>)

    @Query("UPDATE help_requests SET is_bookmarked = NOT is_bookmarked, updated_at = :now WHERE id = :requestId")
    suspend fun toggleBookmark(requestId: String, now: Long)

    @Query("UPDATE help_requests SET status = :status, version = version + 1, updated_at = :now WHERE id = :requestId")
    suspend fun updateStatus(requestId: String, status: String, now: Long)

    @Query("UPDATE help_requests SET sync_status = :syncStatus WHERE id = :requestId")
    suspend fun updateSyncStatus(requestId: String, syncStatus: String)

    @Query("DELETE FROM help_requests WHERE id = :requestId")
    suspend fun deleteById(requestId: String)

    @Query("DELETE FROM help_requests WHERE owner_id = :ownerId")
    suspend fun deleteByOwner(ownerId: String)

    @Query("SELECT COUNT(*) FROM help_requests")
    suspend fun countAll(): Int
}

@Dao
interface HelpOfferDao {
    @Query("SELECT * FROM help_offers WHERE request_id = :requestId ORDER BY created_at DESC")
    fun observeOffersForRequest(requestId: String): Flow<List<HelpOfferEntity>>

    @Query("SELECT * FROM help_offers WHERE volunteer_id = :volunteerId ORDER BY created_at DESC")
    fun observeOffersByVolunteer(volunteerId: String): Flow<List<HelpOfferEntity>>

    @Query("SELECT * FROM help_offers WHERE id = :offerId LIMIT 1")
    suspend fun getById(offerId: String): HelpOfferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(offer: HelpOfferEntity)

    @Query("UPDATE help_offers SET sync_status = :syncStatus WHERE id = :offerId")
    suspend fun updateSyncStatus(offerId: String, syncStatus: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(offers: List<HelpOfferEntity>)

    @Query("DELETE FROM help_offers WHERE volunteer_id = :volunteerId")
    suspend fun deleteByVolunteer(volunteerId: String)
}

@Dao
interface VolunteerDao {
    @Query("""
        SELECT * FROM volunteer_profiles
        WHERE (:city IS NULL OR :city = '' OR LOWER(city) = LOWER(:city))
          AND (:query = '' OR full_name LIKE '%' || :query || '%' OR skills_csv LIKE '%' || :query || '%' OR bio LIKE '%' || :query || '%')
        ORDER BY availability DESC, completed_count DESC
    """)
    fun observeVolunteers(city: String?, query: String): Flow<List<VolunteerProfileEntity>>

    @Query("SELECT * FROM volunteer_profiles WHERE user_id = :userId LIMIT 1")
    fun observeByUserId(userId: String): Flow<VolunteerProfileEntity?>

    @Query("SELECT * FROM volunteer_profiles WHERE user_id = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): VolunteerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: VolunteerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(profiles: List<VolunteerProfileEntity>)

    @Query("SELECT * FROM volunteer_profiles WHERE availability = 1")
    suspend fun getAllAvailableVolunteers(): List<VolunteerProfileEntity>

    @Query("UPDATE volunteer_profiles SET availability = :available WHERE user_id = :userId")
    suspend fun updateAvailability(userId: String, available: Boolean)

    @Query("UPDATE volunteer_profiles SET completed_count = completed_count + 1 WHERE user_id = :userId")
    suspend fun incrementCompletedCount(userId: String)
}

@Dao
interface BloodDonorDao {
    @Query("""
        SELECT * FROM donor_profiles
        WHERE consent = 1
          AND (:bloodGroup IS NULL OR blood_group = :bloodGroup)
          AND (:city IS NULL OR :city = '' OR LOWER(city) = LOWER(:city))
          AND (:onlyAvailable = 0 OR available = 1)
        ORDER BY available DESC, updated_at DESC
    """)
    fun observeDonors(
        bloodGroup: String?,
        city: String?,
        onlyAvailable: Boolean
    ): Flow<List<DonorProfileEntity>>

    @Query("SELECT * FROM donor_profiles WHERE user_id = :userId LIMIT 1")
    fun observeByUserId(userId: String): Flow<DonorProfileEntity?>

    @Query("SELECT * FROM donor_profiles WHERE user_id = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): DonorProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: DonorProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(profiles: List<DonorProfileEntity>)

    @Query("UPDATE donor_profiles SET available = :available, updated_at = :now WHERE user_id = :userId")
    suspend fun updateAvailability(userId: String, available: Boolean, now: Long)

    @Query("DELETE FROM donor_profiles WHERE user_id = :userId")
    suspend fun deleteByUserId(userId: String)

    @Query("SELECT * FROM donor_contact_requests WHERE donor_id = :userId OR requester_id = :userId ORDER BY created_at DESC")
    fun observeContactRequestsForUser(userId: String): Flow<List<DonorContactRequestEntity>>

    @Query("SELECT * FROM donor_contact_requests WHERE id = :id LIMIT 1")
    suspend fun getContactRequestById(id: String): DonorContactRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContactRequest(req: DonorContactRequestEntity)
}

@Dao
interface LostFoundDao {
    @Query("""
        SELECT * FROM lost_found_posts
        WHERE status != 'UNDER_REVIEW'
          AND (:type IS NULL OR type = :type)
          AND (:city IS NULL OR :city = '' OR LOWER(city) = LOWER(:city))
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR approximate_area LIKE '%' || :query || '%')
        ORDER BY created_at DESC
    """)
    fun observePosts(type: String?, city: String?, query: String): Flow<List<LostFoundPostEntity>>

    @Query("SELECT * FROM lost_found_posts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<LostFoundPostEntity?>

    @Query("SELECT * FROM lost_found_posts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LostFoundPostEntity?

    @Upsert
    suspend fun upsertPost(post: LostFoundPostEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(posts: List<LostFoundPostEntity>)

    @Query("UPDATE lost_found_posts SET status = :status WHERE id = :postId")
    suspend fun updateStatus(postId: String, status: String)

    @Query("DELETE FROM lost_found_posts WHERE owner_id = :ownerId")
    suspend fun deleteByOwner(ownerId: String)

    @Query("SELECT * FROM lost_found_claims WHERE post_id = :postId ORDER BY created_at DESC")
    fun observeClaimsForPost(postId: String): Flow<List<LostFoundClaimEntity>>

    @Query("SELECT * FROM lost_found_claims WHERE id = :claimId LIMIT 1")
    suspend fun getClaimById(claimId: String): LostFoundClaimEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaim(claim: LostFoundClaimEntity)
}

@Dao
interface CommunityDao {
    @Query("""
        SELECT * FROM community_posts
        WHERE status = 'ACTIVE'
          AND (:city IS NULL OR :city = '' OR LOWER(city) = LOWER(:city))
          AND (:category IS NULL OR category = :category)
          AND (:query = '' OR title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY created_at DESC
    """)
    fun observePosts(city: String?, category: String?, query: String): Flow<List<CommunityPostEntity>>

    @Query("SELECT * FROM community_posts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CommunityPostEntity?

    @Upsert
    suspend fun upsertPost(post: CommunityPostEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(posts: List<CommunityPostEntity>)

    @Query("DELETE FROM community_posts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM community_posts WHERE author_id = :authorId")
    suspend fun deleteByAuthor(authorId: String)

    @Query("UPDATE community_posts SET status = :status WHERE id = :postId")
    suspend fun updateStatus(postId: String, status: String)

    @Query("SELECT * FROM comments WHERE post_id = :postId ORDER BY created_at ASC")
    fun observeComments(postId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE id = :commentId LIMIT 1")
    suspend fun getCommentById(commentId: String): CommentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Query("UPDATE community_posts SET comment_count = comment_count + 1 WHERE id = :postId")
    suspend fun incrementCommentCount(postId: String)
}

@Dao
interface MessagingDao {
    @Query("""
        SELECT * FROM conversations
        WHERE owner_id = :userId OR volunteer_id = :userId
        ORDER BY last_message_at DESC
    """)
    fun observeConversationsForUser(userId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId AND (owner_id = :userId OR volunteer_id = :userId) LIMIT 1")
    fun observeConversation(conversationId: String, userId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE request_id = :requestId AND volunteer_id = :volunteerId LIMIT 1")
    suspend fun getByRequestAndVolunteer(requestId: String, volunteerId: String): ConversationEntity?

    @Upsert
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("SELECT COALESCE(SUM(unread_count), 0) FROM conversations WHERE owner_id = :userId OR volunteer_id = :userId")
    fun observeTotalUnread(userId: String): Flow<Int>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET sync_status = :syncStatus WHERE id = :messageId")
    suspend fun updateMessageSyncStatus(messageId: String, syncStatus: String)

    @Query("UPDATE messages SET read_at = :readAt WHERE conversation_id = :conversationId AND sender_id != :readerId AND read_at IS NULL")
    suspend fun markIncomingMessagesRead(conversationId: String, readerId: String, readAt: Long)

    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :conversationId")
    suspend fun clearUnreadCount(conversationId: String)

    @Query("UPDATE conversations SET status = 'CLOSED' WHERE id = :conversationId")
    suspend fun closeConversation(conversationId: String)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE user_id = :userId ORDER BY created_at DESC")
    fun observeNotifications(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NotificationEntity?

    @Query("SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND read_at IS NULL")
    fun observeUnreadCount(userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET read_at = :readAt WHERE id = :notificationId")
    suspend fun markRead(notificationId: String, readAt: Long)

    @Query("UPDATE notifications SET read_at = :readAt WHERE user_id = :userId AND read_at IS NULL")
    suspend fun markAllRead(userId: String, readAt: Long)

    @Query("DELETE FROM notifications WHERE user_id = :userId")
    suspend fun deleteByUser(userId: String)
}

@Dao
interface SafetyModerationDao {
    @Query("SELECT * FROM blocked_users WHERE blocker_id = :blockerId ORDER BY created_at DESC")
    fun observeBlockedUsers(blockerId: String): Flow<List<BlockedUserEntity>>

    @Query("SELECT * FROM blocked_users WHERE id = :id LIMIT 1")
    suspend fun getBlockedById(id: String): BlockedUserEntity?

    @Query("SELECT blocked_user_id FROM blocked_users WHERE blocker_id = :blockerId")
    fun observeBlockedUserIds(blockerId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedUser(entry: BlockedUserEntity)

    @Query("DELETE FROM blocked_users WHERE blocker_id = :blockerId AND blocked_user_id = :blockedUserId")
    suspend fun removeBlockedUser(blockerId: String, blockedUserId: String)

    @Query("SELECT * FROM user_reports ORDER BY created_at DESC")
    fun observeReports(): Flow<List<UserReportEntity>>

    @Query("SELECT * FROM user_reports WHERE id = :id LIMIT 1")
    suspend fun getReportById(id: String): UserReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(report: UserReportEntity)

    @Query("SELECT * FROM admin_audit_logs ORDER BY created_at DESC")
    fun observeAuditLogs(): Flow<List<AdminAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AdminAuditLogEntity)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM pending_sync_operations WHERE status != 'SYNCED' ORDER BY created_at ASC")
    fun observePendingOperations(): Flow<List<PendingSyncOperationEntity>>

    @Query("SELECT COUNT(*) FROM pending_sync_operations WHERE status != 'SYNCED'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_sync_operations WHERE status != 'SYNCED' ORDER BY created_at ASC")
    suspend fun getPendingOperations(): List<PendingSyncOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: PendingSyncOperationEntity)

    @Query("UPDATE pending_sync_operations SET status = :status, retry_count = :retryCount, last_attempt_at = :attemptAt WHERE operation_id = :operationId")
    suspend fun updateAttempt(operationId: String, status: String, retryCount: Int, attemptAt: Long)

    @Query("DELETE FROM pending_sync_operations WHERE operation_id = :operationId")
    suspend fun removeOperation(operationId: String)
}
