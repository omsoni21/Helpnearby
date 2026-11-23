package com.example.helpnearby.data.remote.supabase

import com.example.helpnearby.core.database.*
import com.example.helpnearby.domain.model.SyncStatus
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal fun Long.toIsoUtc(): String = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toString()

internal fun String?.fromIsoToEpochMs(defaultValue: Long = System.currentTimeMillis()): Long {
    if (this.isNullOrBlank()) return defaultValue
    return runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }
        .getOrElse {
            runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(defaultValue)
        }
}

// ---------------- GoTrue Auth DTOs ----------------

@JsonClass(generateAdapter = true)
data class SupabaseSignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class SupabaseSignInRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class SupabaseIdTokenRequest(
    val provider: String = "google",
    @Json(name = "id_token") val idToken: String,
    val nonce: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseRefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class SupabaseRecoverRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class SupabaseVerifyOtpRequest(
    val type: String = "signup",
    val email: String,
    val token: String
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthUser(
    val id: String,
    val email: String? = null,
    @Json(name = "email_confirmed_at") val emailConfirmedAt: String? = null,
    @Json(name = "confirmed_at") val confirmedAt: String? = null,
    @Json(name = "app_metadata") val appMetadata: Map<String, Any?>? = null,
    @Json(name = "user_metadata") val userMetadata: Map<String, Any?>? = null,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val isEmailConfirmed: Boolean
        get() = !emailConfirmedAt.isNullOrBlank() || !confirmedAt.isNullOrBlank()

    val isServerVerifiedAdmin: Boolean
        get() = (appMetadata?.get("role") as? String)?.equals("admin", ignoreCase = true) == true
}

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "expires_at") val expiresAt: Long? = null,
    val user: SupabaseAuthUser? = null,
    // Top-level GoTrue user fields returned by POST /auth/v1/signup when Confirm Email is ON
    val id: String? = null,
    val email: String? = null,
    @Json(name = "email_confirmed_at") val emailConfirmedAt: String? = null,
    @Json(name = "confirmed_at") val confirmedAt: String? = null,
    @Json(name = "app_metadata") val appMetadata: Map<String, Any?>? = null,
    @Json(name = "user_metadata") val userMetadata: Map<String, Any?>? = null,
    @Json(name = "created_at") val createdAt: String? = null
) {
    fun resolvedUser(): SupabaseAuthUser? = user ?: id?.takeIf { it.isNotBlank() }?.let { uid ->
        SupabaseAuthUser(
            id = uid,
            email = email,
            emailConfirmedAt = emailConfirmedAt,
            confirmedAt = confirmedAt,
            appMetadata = appMetadata,
            userMetadata = userMetadata,
            createdAt = createdAt
        )
    }
}

@JsonClass(generateAdapter = true)
data class SupabaseErrorResponse(
    val code: Int? = null,
    @Json(name = "error_code") val errorCode: String? = null,
    val msg: String? = null,
    val message: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null,
    val error: String? = null
) {
    fun readableMessage(fallback: String): String =
        msg ?: errorDescription ?: message ?: error ?: fallback
}

// ---------------- PostgREST Table DTOs ----------------

@JsonClass(generateAdapter = true)
data class SupabaseUserDto(
    val id: String,
    @Json(name = "full_name") val fullName: String,
    val email: String,
    @Json(name = "photo_url") val photoUrl: String? = null,
    val city: String,
    val state: String,
    val country: String = "India",
    val language: String = "EN",
    val interests: List<String> = emptyList(),
    @Json(name = "volunteer_available") val volunteerAvailable: Boolean = false,
    @Json(name = "email_verified") val emailVerified: Boolean = false,
    val role: String = "MEMBER",
    @Json(name = "is_suspended") val isSuspended: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabasePreferencesDto(
    @Json(name = "user_id") val userId: String,
    val theme: String = "SYSTEM",
    val language: String = "EN",
    @Json(name = "notifications_enabled") val notificationsEnabled: Boolean = true,
    @Json(name = "location_enabled") val locationEnabled: Boolean = false,
    @Json(name = "selected_city") val selectedCity: String = "Bengaluru",
    @Json(name = "selected_area") val selectedArea: String = "Indiranagar"
)

@JsonClass(generateAdapter = true)
data class SupabaseHelpRequestDto(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "owner_name") val ownerName: String = "",
    val title: String,
    val description: String,
    val category: String,
    val city: String,
    val state: String,
    @Json(name = "approximate_area") val approximateArea: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val urgency: String,
    val status: String = "OPEN",
    @Json(name = "photo_url") val photoUrl: String? = null,
    val version: Int = 1,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String
)

@JsonClass(generateAdapter = true)
data class SupabaseHelpOfferDto(
    val id: String,
    @Json(name = "request_id") val requestId: String,
    @Json(name = "volunteer_id") val volunteerId: String,
    @Json(name = "volunteer_name") val volunteerName: String = "",
    val message: String,
    val status: String = "PENDING",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseVolunteerProfileDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "full_name") val fullName: String = "",
    @Json(name = "photo_url") val photoUrl: String? = null,
    val city: String = "",
    val bio: String = "",
    val skills: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val availability: Boolean = true,
    @Json(name = "completed_count") val completedCount: Int = 0,
    @Json(name = "verification_status") val verificationStatus: String = "UNVERIFIED",
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseDonorProfileDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "display_name") val displayName: String = "",
    @Json(name = "blood_group") val bloodGroup: String,
    val city: String,
    val state: String,
    val available: Boolean = true,
    val consent: Boolean = true,
    @Json(name = "last_donation_date") val lastDonationDate: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseDonorContactRequestDto(
    val id: String,
    @Json(name = "donor_id") val donorId: String,
    @Json(name = "requester_id") val requesterId: String,
    @Json(name = "requester_name") val requesterName: String = "",
    @Json(name = "blood_group") val bloodGroup: String = "O_POS",
    val city: String = "",
    val message: String,
    val status: String = "PENDING",
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseLostFoundPostDto(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "owner_name") val ownerName: String = "",
    val type: String,
    val title: String,
    val description: String,
    val category: String,
    val city: String,
    val state: String,
    @Json(name = "approximate_area") val approximateArea: String,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "contact_preference") val contactPreference: String = "In-app",
    val status: String = "ACTIVE",
    @Json(name = "hidden_verification_hint") val hiddenVerificationHint: String = "",
    @Json(name = "event_date") val eventDate: String = "",
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseLostFoundClaimDto(
    val id: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "claimant_id") val claimantId: String,
    @Json(name = "claimant_name") val claimantName: String = "",
    val message: String,
    @Json(name = "verification_answer") val verificationAnswer: String = "",
    val status: String = "PENDING",
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseCommunityPostDto(
    val id: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "author_name") val authorName: String = "",
    val title: String,
    val content: String,
    val category: String,
    val city: String,
    val status: String = "ACTIVE",
    @Json(name = "like_count") val likeCount: Int = 0,
    @Json(name = "comment_count") val commentCount: Int = 0,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseCommentDto(
    val id: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "author_name") val authorName: String = "",
    val content: String,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseConversationDto(
    val id: String,
    @Json(name = "request_id") val requestId: String,
    @Json(name = "request_title") val requestTitle: String = "",
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "volunteer_id") val volunteerId: String,
    @Json(name = "owner_name") val ownerName: String = "",
    @Json(name = "volunteer_name") val volunteerName: String = "",
    @Json(name = "participant_ids") val participantIds: List<String> = emptyList(),
    val status: String = "OPEN",
    @Json(name = "last_message_text") val lastMessageText: String = "",
    @Json(name = "last_message_at") val lastMessageAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseMessageDto(
    val id: String,
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "sender_id") val senderId: String,
    val text: String,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "read_at") val readAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseNotificationDto(
    val id: String,
    @Json(name = "user_id") val userId: String,
    val type: String,
    val title: String,
    val body: String,
    @Json(name = "related_id") val relatedId: String? = null,
    @Json(name = "read_at") val readAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseUserReportDto(
    val id: String,
    @Json(name = "reporter_id") val reporterId: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    val reason: String,
    val description: String = "",
    val status: String = "PENDING",
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseBlockedUserDto(
    val id: String,
    @Json(name = "blocker_id") val blockerId: String,
    @Json(name = "blocked_user_id") val blockedUserId: String,
    @Json(name = "blocked_user_name") val blockedUserName: String = "",
    @Json(name = "created_at") val createdAt: String? = null
)

// ---------------- Entity <-> DTO Mappers ----------------

fun UserEntity.toSupabaseDto(): SupabaseUserDto = SupabaseUserDto(
    id = id,
    fullName = fullName,
    email = email,
    photoUrl = photoUrl,
    city = city,
    state = state,
    country = country,
    language = language,
    interests = interestsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    volunteerAvailable = volunteerAvailable,
    emailVerified = emailVerified,
    role = role,
    isSuspended = isSuspended,
    createdAt = createdAt.toIsoUtc(),
    updatedAt = updatedAt.toIsoUtc()
)

fun SupabaseUserDto.toEntity(passwordHash: String = "SUPABASE_AUTH"): UserEntity = UserEntity(
    id = id,
    fullName = fullName,
    email = email,
    passwordHash = passwordHash,
    photoUrl = photoUrl,
    city = city,
    state = state,
    country = country,
    language = language,
    interestsCsv = interests.joinToString(","),
    volunteerAvailable = volunteerAvailable,
    emailVerified = emailVerified,
    isSuspended = isSuspended,
    role = role,
    createdAt = createdAt.fromIsoToEpochMs(),
    updatedAt = updatedAt.fromIsoToEpochMs()
)

fun UserPreferencesEntity.toSupabaseDto(): SupabasePreferencesDto = SupabasePreferencesDto(
    userId = userId,
    theme = theme,
    language = language,
    notificationsEnabled = notificationsEnabled,
    locationEnabled = locationEnabled,
    selectedCity = selectedCity,
    selectedArea = selectedArea
)

fun SupabasePreferencesDto.toEntity(): UserPreferencesEntity = UserPreferencesEntity(
    userId = userId,
    theme = theme,
    language = language,
    notificationsEnabled = notificationsEnabled,
    locationEnabled = locationEnabled,
    selectedCity = selectedCity,
    selectedArea = selectedArea
)

fun HelpRequestEntity.toSupabaseDto(): SupabaseHelpRequestDto = SupabaseHelpRequestDto(
    id = id,
    ownerId = ownerId,
    ownerName = ownerName,
    title = title,
    description = description,
    category = category,
    city = city,
    state = state,
    approximateArea = approximateArea,
    latitude = latitude,
    longitude = longitude,
    urgency = urgency,
    status = status,
    photoUrl = photoUrl,
    version = version,
    createdAt = createdAt.toIsoUtc(),
    updatedAt = updatedAt.toIsoUtc(),
    expiresAt = expiresAt.toIsoUtc()
)

fun SupabaseHelpRequestDto.toEntity(isBookmarked: Boolean = false): HelpRequestEntity = HelpRequestEntity(
    id = id,
    ownerId = ownerId,
    ownerName = ownerName,
    title = title,
    description = description,
    category = category,
    city = city,
    state = state,
    approximateArea = approximateArea,
    latitude = latitude,
    longitude = longitude,
    urgency = urgency,
    status = status,
    photoUrl = photoUrl,
    isBookmarked = isBookmarked,
    version = version,
    syncStatus = SyncStatus.SYNCED.name,
    createdAt = createdAt.fromIsoToEpochMs(),
    updatedAt = updatedAt.fromIsoToEpochMs(),
    expiresAt = expiresAt.fromIsoToEpochMs(System.currentTimeMillis() + 7 * 86_400_000L)
)

fun HelpOfferEntity.toSupabaseDto(): SupabaseHelpOfferDto = SupabaseHelpOfferDto(
    id = id,
    requestId = requestId,
    volunteerId = volunteerId,
    volunteerName = volunteerName,
    message = message,
    status = status,
    createdAt = createdAt.toIsoUtc(),
    updatedAt = updatedAt.toIsoUtc()
)

fun SupabaseHelpOfferDto.toEntity(): HelpOfferEntity = HelpOfferEntity(
    id = id,
    requestId = requestId,
    volunteerId = volunteerId,
    volunteerName = volunteerName,
    message = message,
    status = status,
    syncStatus = SyncStatus.SYNCED.name,
    createdAt = createdAt.fromIsoToEpochMs(),
    updatedAt = updatedAt.fromIsoToEpochMs()
)

fun VolunteerProfileEntity.toSupabaseDto(): SupabaseVolunteerProfileDto = SupabaseVolunteerProfileDto(
    userId = userId,
    fullName = fullName,
    photoUrl = photoUrl,
    city = city,
    bio = bio,
    skills = skillsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    interests = interestsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    availability = availability,
    completedCount = completedCount,
    verificationStatus = verificationStatus,
    createdAt = createdAt.toIsoUtc()
)

fun SupabaseVolunteerProfileDto.toEntity(): VolunteerProfileEntity = VolunteerProfileEntity(
    userId = userId,
    fullName = fullName,
    photoUrl = photoUrl,
    city = city,
    bio = bio,
    skillsCsv = skills.joinToString(","),
    interestsCsv = interests.joinToString(","),
    availability = availability,
    completedCount = completedCount,
    verificationStatus = verificationStatus,
    createdAt = createdAt.fromIsoToEpochMs()
)

fun DonorProfileEntity.toSupabaseDto(): SupabaseDonorProfileDto = SupabaseDonorProfileDto(
    userId = userId,
    displayName = displayName,
    bloodGroup = bloodGroup,
    city = city,
    state = state,
    available = available,
    consent = consent,
    lastDonationDate = lastDonationDate,
    createdAt = createdAt.toIsoUtc(),
    updatedAt = updatedAt.toIsoUtc()
)

fun SupabaseDonorProfileDto.toEntity(): DonorProfileEntity = DonorProfileEntity(
    userId = userId,
    displayName = displayName,
    bloodGroup = bloodGroup,
    city = city,
    state = state,
    available = available,
    consent = consent,
    lastDonationDate = lastDonationDate,
    createdAt = createdAt.fromIsoToEpochMs(),
    updatedAt = updatedAt.fromIsoToEpochMs()
)

fun DonorContactRequestEntity.toSupabaseDto(): SupabaseDonorContactRequestDto = SupabaseDonorContactRequestDto(
    id = id,
    donorId = donorId,
    requesterId = requesterId,
    requesterName = requesterName,
    bloodGroup = bloodGroup,
    city = city,
    message = message,
    status = status,
    createdAt = createdAt.toIsoUtc()
)

fun SupabaseDonorContactRequestDto.toEntity(): DonorContactRequestEntity = DonorContactRequestEntity(
    id = id,
    donorId = donorId,
    requesterId = requesterId,
    requesterName = requesterName,
    bloodGroup = bloodGroup,
    city = city,
    message = message,
    status = status,
    createdAt = createdAt.fromIsoToEpochMs()
)

fun LostFoundPostEntity.toSupabaseDto(): SupabaseLostFoundPostDto = SupabaseLostFoundPostDto(
    id = id,
    ownerId = ownerId,
    ownerName = ownerName,
    type = type,
    title = title,
    description = description,
    category = category,
    city = city,
    state = state,
    approximateArea = approximateArea,
    imageUrl = imageUrl,
    contactPreference = contactPreference,
    status = status,
    hiddenVerificationHint = hiddenVerificationHint,
    eventDate = eventDate,
    createdAt = createdAt.toIsoUtc()
)

fun SupabaseLostFoundPostDto.toEntity(): LostFoundPostEntity = LostFoundPostEntity(
    id = id,
    ownerId = ownerId,
    ownerName = ownerName,
    type = type,
    title = title,
    description = description,
    category = category,
    city = city,
    state = state,
    approximateArea = approximateArea,
    imageUrl = imageUrl,
    contactPreference = contactPreference,
    status = status,
    hiddenVerificationHint = hiddenVerificationHint,
    eventDate = eventDate,
    createdAt = createdAt.fromIsoToEpochMs()
)

fun LostFoundClaimEntity.toSupabaseDto(): SupabaseLostFoundClaimDto = SupabaseLostFoundClaimDto(
    id = id,
    postId = postId,
    claimantId = claimantId,
    claimantName = claimantName,
    message = message,
    verificationAnswer = verificationAnswer,
    status = status,
    createdAt = createdAt.toIsoUtc()
)

fun SupabaseLostFoundClaimDto.toEntity(): LostFoundClaimEntity = LostFoundClaimEntity(
    id = id,
    postId = postId,
    claimantId = claimantId,
    claimantName = claimantName,
    message = message,
    verificationAnswer = verificationAnswer,
    status = status,
    createdAt = createdAt.fromIsoToEpochMs()
)

fun CommunityPostEntity.toSupabaseDto(): SupabaseCommunityPostDto = SupabaseCommunityPostDto(
    id = id,
    authorId = authorId,
    authorName = authorName,
    title = title,
    content = content,
    category = category,
    city = city,
    status = status,
    likeCount = likeCount,
    commentCount = commentCount,
    createdAt = createdAt.toIsoUtc()
)

fun SupabaseCommunityPostDto.toEntity(isLikedByMe: Boolean = false): CommunityPostEntity = CommunityPostEntity(
    id = id,
    authorId = authorId,
    authorName = authorName,
    title = title,
    content = content,
    category = category,
    city = city,
    status = status,
    likeCount = likeCount,
    isLikedByMe = isLikedByMe,
    commentCount = commentCount,
    createdAt = createdAt.fromIsoToEpochMs()
)

fun CommentEntity.toSupabaseDto(): SupabaseCommentDto = SupabaseCommentDto(
    id = id,
    postId = postId,
    authorId = authorId,
    authorName = authorName,
    content = content,
    createdAt = createdAt.toIsoUtc()
)

fun SupabaseCommentDto.toEntity(): CommentEntity = CommentEntity(
    id = id,
    postId = postId,
    authorId = authorId,
    authorName = authorName,
    content = content,
    createdAt = createdAt.fromIsoToEpochMs()
)

fun ConversationEntity.toSupabaseDto(): SupabaseConversationDto = SupabaseConversationDto(
    id = id,
    requestId = requestId,
    requestTitle = requestTitle,
    ownerId = ownerId,
    volunteerId = volunteerId,
    ownerName = ownerName,
    volunteerName = volunteerName,
    participantIds = listOf(ownerId, volunteerId),
    status = status,
    lastMessageText = lastMessageText,
    lastMessageAt = lastMessageAt.toIsoUtc(),
    createdAt = createdAt.toIsoUtc()
)

fun SupabaseConversationDto.toEntity(unreadCount: Int = 0): ConversationEntity = ConversationEntity(
    id = id,
    requestId = requestId,
    requestTitle = requestTitle,
    ownerId = ownerId,
    volunteerId = volunteerId,
    ownerName = ownerName,
    volunteerName = volunteerName,
    status = status,
    lastMessageText = lastMessageText,
    lastMessageAt = lastMessageAt.fromIsoToEpochMs(),
    unreadCount = unreadCount,
    createdAt = createdAt.fromIsoToEpochMs()
)

fun MessageEntity.toSupabaseDto(): SupabaseMessageDto = SupabaseMessageDto(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    text = text,
    createdAt = createdAt.toIsoUtc(),
    readAt = readAt?.toIsoUtc()
)

fun SupabaseMessageDto.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    text = text,
    createdAt = createdAt.fromIsoToEpochMs(),
    readAt = readAt?.fromIsoToEpochMs(),
    syncStatus = SyncStatus.SYNCED.name
)

fun NotificationEntity.toSupabaseDto(): SupabaseNotificationDto = SupabaseNotificationDto(
    id = id,
    userId = userId,
    type = type,
    title = title,
    body = body,
    relatedId = relatedId,
    readAt = readAt?.toIsoUtc(),
    createdAt = createdAt.toIsoUtc()
)

fun SupabaseNotificationDto.toEntity(): NotificationEntity = NotificationEntity(
    id = id,
    userId = userId,
    type = type,
    title = title,
    body = body,
    relatedId = relatedId,
    readAt = readAt?.fromIsoToEpochMs(),
    createdAt = createdAt.fromIsoToEpochMs()
)

fun UserReportEntity.toSupabaseDto(): SupabaseUserReportDto = SupabaseUserReportDto(
    id = id,
    reporterId = reporterId,
    targetType = targetType,
    targetId = targetId,
    reason = reason,
    description = description,
    status = status,
    createdAt = createdAt.toIsoUtc()
)

fun BlockedUserEntity.toSupabaseDto(): SupabaseBlockedUserDto = SupabaseBlockedUserDto(
    id = id,
    blockerId = blockerId,
    blockedUserId = blockedUserId,
    blockedUserName = blockedUserName,
    createdAt = createdAt.toIsoUtc()
)

fun SupabaseBlockedUserDto.toEntity(): BlockedUserEntity = BlockedUserEntity(
    id = id,
    blockerId = blockerId,
    blockedUserId = blockedUserId,
    blockedUserName = blockedUserName,
    createdAt = createdAt.fromIsoToEpochMs()
)
