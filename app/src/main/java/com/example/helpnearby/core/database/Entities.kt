package com.example.helpnearby.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    val email: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String? = null,
    val city: String,
    val state: String,
    val country: String = "India",
    val language: String = "EN",
    @ColumnInfo(name = "interests_csv") val interestsCsv: String = "",
    @ColumnInfo(name = "volunteer_available") val volunteerAvailable: Boolean = false,
    @ColumnInfo(name = "email_verified") val emailVerified: Boolean = false,
    @ColumnInfo(name = "is_suspended") val isSuspended: Boolean = false,
    val role: String = "MEMBER",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "user_preferences",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserPreferencesEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    val theme: String = "SYSTEM",
    val language: String = "EN",
    @ColumnInfo(name = "notifications_enabled") val notificationsEnabled: Boolean = true,
    @ColumnInfo(name = "location_enabled") val locationEnabled: Boolean = false,
    @ColumnInfo(name = "selected_city") val selectedCity: String = "Bengaluru",
    @ColumnInfo(name = "selected_area") val selectedArea: String = "Indiranagar"
)

@Entity(
    tableName = "help_requests",
    indices = [
        Index(value = ["owner_id"]),
        Index(value = ["city", "status"]),
        Index(value = ["category"])
    ]
)
data class HelpRequestEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "owner_name") val ownerName: String,
    val title: String,
    val description: String,
    val category: String,
    val city: String,
    val state: String,
    @ColumnInfo(name = "approximate_area") val approximateArea: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val urgency: String,
    val status: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String? = null,
    @ColumnInfo(name = "is_bookmarked") val isBookmarked: Boolean = false,
    val version: Int = 1,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "LOCAL_ONLY",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long
)

@Entity(
    tableName = "help_offers",
    foreignKeys = [
        ForeignKey(
            entity = HelpRequestEntity::class,
            parentColumns = ["id"],
            childColumns = ["request_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["request_id"]),
        Index(value = ["volunteer_id"]),
        Index(value = ["request_id", "volunteer_id"], unique = true)
    ]
)
data class HelpOfferEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "volunteer_id") val volunteerId: String,
    @ColumnInfo(name = "volunteer_name") val volunteerName: String,
    val message: String,
    val status: String,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "LOCAL_ONLY",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "volunteer_profiles",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VolunteerProfileEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String? = null,
    val city: String,
    val bio: String,
    @ColumnInfo(name = "skills_csv") val skillsCsv: String,
    @ColumnInfo(name = "interests_csv") val interestsCsv: String,
    val availability: Boolean,
    @ColumnInfo(name = "completed_count") val completedCount: Int = 0,
    @ColumnInfo(name = "verification_status") val verificationStatus: String = "UNVERIFIED",
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "donor_profiles",
    indices = [Index(value = ["blood_group", "city", "available"])]
)
data class DonorProfileEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "blood_group") val bloodGroup: String,
    val city: String,
    val state: String,
    val available: Boolean,
    val consent: Boolean,
    @ColumnInfo(name = "last_donation_date") val lastDonationDate: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "donor_contact_requests",
    indices = [Index(value = ["donor_id"]), Index(value = ["requester_id"])]
)
data class DonorContactRequestEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "donor_id") val donorId: String,
    @ColumnInfo(name = "requester_id") val requesterId: String,
    @ColumnInfo(name = "requester_name") val requesterName: String,
    @ColumnInfo(name = "blood_group") val bloodGroup: String,
    val city: String,
    val message: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "lost_found_posts",
    indices = [Index(value = ["type", "city", "status"])]
)
data class LostFoundPostEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "owner_name") val ownerName: String,
    val type: String,
    val title: String,
    val description: String,
    val category: String,
    val city: String,
    val state: String,
    @ColumnInfo(name = "approximate_area") val approximateArea: String,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "contact_preference") val contactPreference: String,
    val status: String,
    @ColumnInfo(name = "hidden_verification_hint") val hiddenVerificationHint: String = "",
    @ColumnInfo(name = "event_date") val eventDate: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "lost_found_claims",
    foreignKeys = [
        ForeignKey(
            entity = LostFoundPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["post_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["post_id"]), Index(value = ["claimant_id"])]
)
data class LostFoundClaimEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "post_id") val postId: String,
    @ColumnInfo(name = "claimant_id") val claimantId: String,
    @ColumnInfo(name = "claimant_name") val claimantName: String,
    val message: String,
    @ColumnInfo(name = "verification_answer") val verificationAnswer: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "community_posts",
    indices = [Index(value = ["city", "category", "status"])]
)
data class CommunityPostEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "author_id") val authorId: String,
    @ColumnInfo(name = "author_name") val authorName: String,
    val title: String,
    val content: String,
    val category: String,
    val city: String,
    val status: String = "ACTIVE",
    @ColumnInfo(name = "like_count") val likeCount: Int = 0,
    @ColumnInfo(name = "is_liked_by_me") val isLikedByMe: Boolean = false,
    @ColumnInfo(name = "comment_count") val commentCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(
            entity = CommunityPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["post_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["post_id"])]
)
data class CommentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "post_id") val postId: String,
    @ColumnInfo(name = "author_id") val authorId: String,
    @ColumnInfo(name = "author_name") val authorName: String,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["request_id", "volunteer_id"], unique = true),
        Index(value = ["owner_id"]),
        Index(value = ["volunteer_id"])
    ]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "request_title") val requestTitle: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "volunteer_id") val volunteerId: String,
    @ColumnInfo(name = "owner_name") val ownerName: String,
    @ColumnInfo(name = "volunteer_name") val volunteerName: String,
    val status: String = "OPEN",
    @ColumnInfo(name = "last_message_text") val lastMessageText: String = "",
    @ColumnInfo(name = "last_message_at") val lastMessageAt: Long,
    @ColumnInfo(name = "unread_count") val unreadCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversation_id", "created_at"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    val text: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "read_at") val readAt: Long? = null,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "LOCAL_ONLY"
)

@Entity(
    tableName = "notifications",
    indices = [Index(value = ["user_id", "created_at"])]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val type: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "related_id") val relatedId: String? = null,
    @ColumnInfo(name = "read_at") val readAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "user_reports",
    indices = [Index(value = ["status", "created_at"])]
)
data class UserReportEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "reporter_id") val reporterId: String,
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    val reason: String,
    val description: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "blocked_users",
    indices = [Index(value = ["blocker_id", "blocked_user_id"], unique = true)]
)
data class BlockedUserEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "blocker_id") val blockerId: String,
    @ColumnInfo(name = "blocked_user_id") val blockedUserId: String,
    @ColumnInfo(name = "blocked_user_name") val blockedUserName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "admin_audit_logs",
    indices = [Index(value = ["created_at"])]
)
data class AdminAuditLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "admin_id") val adminId: String,
    val action: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    val details: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "pending_sync_operations",
    indices = [Index(value = ["status", "created_at"])]
)
data class PendingSyncOperationEntity(
    @PrimaryKey @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "operation_type") val operationType: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val status: String,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    val version: Int = 1,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
