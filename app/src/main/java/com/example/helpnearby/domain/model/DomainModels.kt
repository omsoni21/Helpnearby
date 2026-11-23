package com.example.helpnearby.domain.model

import kotlinx.serialization.Serializable

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class AppLanguage(val code: String, val displayName: String) {
    EN("en", "English"),
    HI("hi", "हिन्दी (Hindi)")
}

enum class UserRole {
    MEMBER, ADMIN
}

enum class SyncStatus {
    SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE, FAILED, LOCAL_ONLY
}

enum class HelpCategory(val labelEn: String, val labelHi: String) {
    DAILY_ASSISTANCE("Daily Assistance", "दैनिक सहायता"),
    MEDICAL_ASSISTANCE("Medical Assistance", "चिकित्सा सहायता"),
    EDUCATION("Education", "शिक्षा"),
    TRANSPORTATION("Transportation", "परिवहन"),
    FOOD_ASSISTANCE("Food Assistance", "भोजन सहायता"),
    ELDERLY_ASSISTANCE("Elderly Assistance", "बुजुर्ग सहायता"),
    DISABILITY_ASSISTANCE("Disability Assistance", "दिव्यांग सहायता"),
    LOST_AND_FOUND("Lost & Found", "खोया और पाया"),
    COMMUNITY_SUPPORT("Community Support", "सामुदायिक सहयोग"),
    OTHER("Other", "अन्य")
}

enum class UrgencyLevel(val labelEn: String, val labelHi: String) {
    LOW("Flexible", "सामान्य"),
    MEDIUM("Soon (Today)", "आज आवश्यक"),
    HIGH("Urgent", "तत्काल")
}

enum class RequestStatus(val labelEn: String) {
    OPEN("Open"),
    OFFER_RECEIVED("Offer Received"),
    ACCEPTED("Accepted"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired"),
    UNDER_REVIEW("Under Review")
}

enum class OfferStatus {
    PENDING, ACCEPTED, DECLINED, COMPLETED
}

enum class VerificationStatus(val label: String) {
    UNVERIFIED("Unverified Member"),
    VERIFIED_ID("Verified Identity")
}

enum class BloodGroup(val label: String) {
    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-"),
    O_POS("O+"),
    O_NEG("O-")
}

enum class LostFoundType {
    LOST, FOUND
}

enum class LostFoundStatus {
    ACTIVE, CLAIM_PENDING, RECOVERED, CLOSED, UNDER_REVIEW
}

enum class CommunityCategory(val label: String) {
    ANNOUNCEMENT("Announcement"),
    DRIVE("Aid Drive"),
    ALERT("Neighborhood Tip"),
    APPRECIATION("Gratitude"),
    GENERAL("General")
}

enum class NotificationType {
    NEW_HELP_OFFER,
    OFFER_ACCEPTED,
    OFFER_DECLINED,
    NEW_MESSAGE,
    REQUEST_STATUS_CHANGED,
    COMMUNITY_UPDATE,
    DONOR_CONTACT_REQUEST,
    LOST_ITEM_CLAIM_REQUEST
}

enum class ReportTargetType {
    USER, HELP_REQUEST, COMMUNITY_POST, LOST_FOUND_POST, BLOOD_DONOR
}

enum class ReportStatus {
    PENDING, REVIEWED_ACTION_TAKEN, DISMISSED
}

enum class BackendProvider {
    LOCAL_OFFLINE, FIREBASE, SUPABASE
}

@Serializable
data class User(
    val id: String,
    val fullName: String,
    val email: String,
    val photoUrl: String? = null,
    val city: String,
    val state: String,
    val country: String = "India",
    val language: AppLanguage = AppLanguage.EN,
    val interests: List<String> = emptyList(),
    val volunteerAvailable: Boolean = false,
    val emailVerified: Boolean = false,
    val isSuspended: Boolean = false,
    val role: UserRole = UserRole.MEMBER,
    val createdAt: Long,
    val updatedAt: Long
)

data class UserPreferences(
    val userId: String,
    val theme: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.EN,
    val notificationsEnabled: Boolean = true,
    val locationEnabled: Boolean = false,
    val selectedCity: String = "Bengaluru",
    val selectedArea: String = "Indiranagar"
)

data class HelpRequest(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val title: String,
    val description: String,
    val category: HelpCategory,
    val city: String,
    val state: String,
    val approximateArea: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val urgency: UrgencyLevel,
    val status: RequestStatus,
    val photoUrl: String? = null,
    val isBookmarked: Boolean = false,
    val version: Int = 1,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long
)

data class HelpOffer(
    val id: String,
    val requestId: String,
    val volunteerId: String,
    val volunteerName: String,
    val message: String,
    val status: OfferStatus,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val createdAt: Long,
    val updatedAt: Long
)

data class VolunteerProfile(
    val userId: String,
    val fullName: String,
    val photoUrl: String? = null,
    val city: String,
    val bio: String,
    val skills: List<String>,
    val interests: List<String>,
    val availability: Boolean,
    val completedCount: Int,
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val createdAt: Long
)

data class DonorProfile(
    val userId: String,
    val displayName: String,
    val bloodGroup: BloodGroup,
    val city: String,
    val state: String,
    val available: Boolean,
    val consent: Boolean,
    val lastDonationDate: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

data class DonorContactRequest(
    val id: String,
    val donorId: String,
    val requesterId: String,
    val requesterName: String,
    val bloodGroup: BloodGroup,
    val city: String,
    val message: String,
    val status: OfferStatus,
    val createdAt: Long
)

data class LostFoundPost(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val type: LostFoundType,
    val title: String,
    val description: String,
    val category: String,
    val city: String,
    val state: String,
    val approximateArea: String,
    val imageUrl: String? = null,
    val contactPreference: String,
    val status: LostFoundStatus,
    val hiddenVerificationHint: String = "",
    val eventDate: String,
    val createdAt: Long
)

data class LostFoundClaim(
    val id: String,
    val postId: String,
    val claimantId: String,
    val claimantName: String,
    val message: String,
    val verificationAnswer: String,
    val status: OfferStatus,
    val createdAt: Long
)

data class CommunityPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val title: String,
    val content: String,
    val category: CommunityCategory,
    val city: String,
    val status: String = "ACTIVE",
    val likeCount: Int = 0,
    val isLikedByMe: Boolean = false,
    val commentCount: Int = 0,
    val createdAt: Long
)

data class PostComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val content: String,
    val createdAt: Long
)

data class Conversation(
    val id: String,
    val requestId: String,
    val requestTitle: String,
    val ownerId: String,
    val volunteerId: String,
    val ownerName: String,
    val volunteerName: String,
    val status: String,
    val lastMessageText: String,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val createdAt: Long
)

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val createdAt: Long,
    val readAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

data class AppNotification(
    val id: String,
    val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val relatedId: String? = null,
    val readAt: Long? = null,
    val createdAt: Long
)

data class UserReport(
    val id: String,
    val reporterId: String,
    val targetType: ReportTargetType,
    val targetId: String,
    val reason: String,
    val description: String,
    val status: ReportStatus,
    val createdAt: Long
)

data class BlockedUser(
    val id: String,
    val blockerId: String,
    val blockedUserId: String,
    val blockedUserName: String,
    val createdAt: Long
)

data class AdminAuditLog(
    val id: String,
    val adminId: String,
    val action: String,
    val targetId: String,
    val details: String,
    val createdAt: Long
)

data class PendingSyncOperation(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payloadJson: String,
    val status: SyncStatus,
    val retryCount: Int,
    val version: Int,
    val lastAttemptAt: Long?,
    val createdAt: Long
)
