package com.example.helpnearby.data.local

import com.example.helpnearby.core.database.*
import com.example.helpnearby.domain.model.*

fun UserEntity.toDomain(): User = User(
    id = id,
    fullName = fullName,
    email = email,
    photoUrl = photoUrl,
    city = city,
    state = state,
    country = country,
    language = runCatching { AppLanguage.valueOf(language) }.getOrDefault(AppLanguage.EN),
    interests = interestsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    volunteerAvailable = volunteerAvailable,
    emailVerified = emailVerified,
    isSuspended = isSuspended,
    role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.MEMBER),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun User.toEntity(passwordHash: String): UserEntity = UserEntity(
    id = id,
    fullName = fullName,
    email = email,
    passwordHash = passwordHash,
    photoUrl = photoUrl,
    city = city,
    state = state,
    country = country,
    language = language.name,
    interestsCsv = interests.joinToString(","),
    volunteerAvailable = volunteerAvailable,
    emailVerified = emailVerified,
    isSuspended = isSuspended,
    role = role.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun UserPreferencesEntity.toDomain(): UserPreferences = UserPreferences(
    userId = userId,
    theme = runCatching { AppThemeMode.valueOf(theme) }.getOrDefault(AppThemeMode.SYSTEM),
    language = runCatching { AppLanguage.valueOf(language) }.getOrDefault(AppLanguage.EN),
    notificationsEnabled = notificationsEnabled,
    locationEnabled = locationEnabled,
    selectedCity = selectedCity,
    selectedArea = selectedArea
)

fun UserPreferences.toEntity(): UserPreferencesEntity = UserPreferencesEntity(
    userId = userId,
    theme = theme.name,
    language = language.name,
    notificationsEnabled = notificationsEnabled,
    locationEnabled = locationEnabled,
    selectedCity = selectedCity,
    selectedArea = selectedArea
)

fun HelpRequestEntity.toDomain(): HelpRequest = HelpRequest(
    id = id,
    ownerId = ownerId,
    ownerName = ownerName,
    title = title,
    description = description,
    category = runCatching { HelpCategory.valueOf(category) }.getOrDefault(HelpCategory.OTHER),
    city = city,
    state = state,
    approximateArea = approximateArea,
    latitude = latitude,
    longitude = longitude,
    urgency = runCatching { UrgencyLevel.valueOf(urgency) }.getOrDefault(UrgencyLevel.MEDIUM),
    status = runCatching { RequestStatus.valueOf(status) }.getOrDefault(RequestStatus.OPEN),
    photoUrl = photoUrl,
    isBookmarked = isBookmarked,
    version = version,
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.LOCAL_ONLY),
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt
)

fun HelpRequest.toEntity(): HelpRequestEntity = HelpRequestEntity(
    id = id,
    ownerId = ownerId,
    ownerName = ownerName,
    title = title,
    description = description,
    category = category.name,
    city = city,
    state = state,
    approximateArea = approximateArea,
    latitude = latitude,
    longitude = longitude,
    urgency = urgency.name,
    status = status.name,
    photoUrl = photoUrl,
    isBookmarked = isBookmarked,
    version = version,
    syncStatus = syncStatus.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt
)

fun HelpOfferEntity.toDomain(): HelpOffer = HelpOffer(
    id = id,
    requestId = requestId,
    volunteerId = volunteerId,
    volunteerName = volunteerName,
    message = message,
    status = runCatching { OfferStatus.valueOf(status) }.getOrDefault(OfferStatus.PENDING),
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.LOCAL_ONLY),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun HelpOffer.toEntity(): HelpOfferEntity = HelpOfferEntity(
    id = id,
    requestId = requestId,
    volunteerId = volunteerId,
    volunteerName = volunteerName,
    message = message,
    status = status.name,
    syncStatus = syncStatus.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun VolunteerProfileEntity.toDomain(): VolunteerProfile = VolunteerProfile(
    userId = userId,
    fullName = fullName,
    photoUrl = photoUrl,
    city = city,
    bio = bio,
    skills = skillsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    interests = interestsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    availability = availability,
    completedCount = completedCount,
    verificationStatus = runCatching { VerificationStatus.valueOf(verificationStatus) }
        .getOrDefault(VerificationStatus.UNVERIFIED),
    createdAt = createdAt
)

fun VolunteerProfile.toEntity(): VolunteerProfileEntity = VolunteerProfileEntity(
    userId = userId,
    fullName = fullName,
    photoUrl = photoUrl,
    city = city,
    bio = bio,
    skillsCsv = skills.joinToString(","),
    interestsCsv = interests.joinToString(","),
    availability = availability,
    completedCount = completedCount,
    verificationStatus = verificationStatus.name,
    createdAt = createdAt
)

fun DonorProfileEntity.toDomain(): DonorProfile = DonorProfile(
    userId = userId,
    displayName = displayName,
    bloodGroup = runCatching { BloodGroup.valueOf(bloodGroup) }.getOrDefault(BloodGroup.O_POS),
    city = city,
    state = state,
    available = available,
    consent = consent,
    lastDonationDate = lastDonationDate,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DonorProfile.toEntity(): DonorProfileEntity = DonorProfileEntity(
    userId = userId,
    displayName = displayName,
    bloodGroup = bloodGroup.name,
    city = city,
    state = state,
    available = available,
    consent = consent,
    lastDonationDate = lastDonationDate,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DonorContactRequestEntity.toDomain(): DonorContactRequest = DonorContactRequest(
    id = id,
    donorId = donorId,
    requesterId = requesterId,
    requesterName = requesterName,
    bloodGroup = runCatching { BloodGroup.valueOf(bloodGroup) }.getOrDefault(BloodGroup.O_POS),
    city = city,
    message = message,
    status = runCatching { OfferStatus.valueOf(status) }.getOrDefault(OfferStatus.PENDING),
    createdAt = createdAt
)

fun DonorContactRequest.toEntity(): DonorContactRequestEntity = DonorContactRequestEntity(
    id = id,
    donorId = donorId,
    requesterId = requesterId,
    requesterName = requesterName,
    bloodGroup = bloodGroup.name,
    city = city,
    message = message,
    status = status.name,
    createdAt = createdAt
)

fun LostFoundPostEntity.toDomain(): LostFoundPost = LostFoundPost(
    id = id,
    ownerId = ownerId,
    ownerName = ownerName,
    type = runCatching { LostFoundType.valueOf(type) }.getOrDefault(LostFoundType.LOST),
    title = title,
    description = description,
    category = category,
    city = city,
    state = state,
    approximateArea = approximateArea,
    imageUrl = imageUrl,
    contactPreference = contactPreference,
    status = runCatching { LostFoundStatus.valueOf(status) }.getOrDefault(LostFoundStatus.ACTIVE),
    hiddenVerificationHint = hiddenVerificationHint,
    eventDate = eventDate,
    createdAt = createdAt
)

fun LostFoundPost.toEntity(): LostFoundPostEntity = LostFoundPostEntity(
    id = id,
    ownerId = ownerId,
    ownerName = ownerName,
    type = type.name,
    title = title,
    description = description,
    category = category,
    city = city,
    state = state,
    approximateArea = approximateArea,
    imageUrl = imageUrl,
    contactPreference = contactPreference,
    status = status.name,
    hiddenVerificationHint = hiddenVerificationHint,
    eventDate = eventDate,
    createdAt = createdAt
)

fun LostFoundClaimEntity.toDomain(): LostFoundClaim = LostFoundClaim(
    id = id,
    postId = postId,
    claimantId = claimantId,
    claimantName = claimantName,
    message = message,
    verificationAnswer = verificationAnswer,
    status = runCatching { OfferStatus.valueOf(status) }.getOrDefault(OfferStatus.PENDING),
    createdAt = createdAt
)

fun LostFoundClaim.toEntity(): LostFoundClaimEntity = LostFoundClaimEntity(
    id = id,
    postId = postId,
    claimantId = claimantId,
    claimantName = claimantName,
    message = message,
    verificationAnswer = verificationAnswer,
    status = status.name,
    createdAt = createdAt
)

fun CommunityPostEntity.toDomain(): CommunityPost = CommunityPost(
    id = id,
    authorId = authorId,
    authorName = authorName,
    title = title,
    content = content,
    category = runCatching { CommunityCategory.valueOf(category) }.getOrDefault(CommunityCategory.GENERAL),
    city = city,
    status = status,
    likeCount = likeCount,
    isLikedByMe = isLikedByMe,
    commentCount = commentCount,
    createdAt = createdAt
)

fun CommunityPost.toEntity(): CommunityPostEntity = CommunityPostEntity(
    id = id,
    authorId = authorId,
    authorName = authorName,
    title = title,
    content = content,
    category = category.name,
    city = city,
    status = status,
    likeCount = likeCount,
    isLikedByMe = isLikedByMe,
    commentCount = commentCount,
    createdAt = createdAt
)

fun CommentEntity.toDomain(): PostComment = PostComment(
    id = id,
    postId = postId,
    authorId = authorId,
    authorName = authorName,
    content = content,
    createdAt = createdAt
)

fun PostComment.toEntity(): CommentEntity = CommentEntity(
    id = id,
    postId = postId,
    authorId = authorId,
    authorName = authorName,
    content = content,
    createdAt = createdAt
)

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    requestId = requestId,
    requestTitle = requestTitle,
    ownerId = ownerId,
    volunteerId = volunteerId,
    ownerName = ownerName,
    volunteerName = volunteerName,
    status = status,
    lastMessageText = lastMessageText,
    lastMessageAt = lastMessageAt,
    unreadCount = unreadCount,
    createdAt = createdAt
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    text = text,
    createdAt = createdAt,
    readAt = readAt,
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.LOCAL_ONLY)
)

fun NotificationEntity.toDomain(): AppNotification = AppNotification(
    id = id,
    userId = userId,
    type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.COMMUNITY_UPDATE),
    title = title,
    body = body,
    relatedId = relatedId,
    readAt = readAt,
    createdAt = createdAt
)

fun AppNotification.toEntity(): NotificationEntity = NotificationEntity(
    id = id,
    userId = userId,
    type = type.name,
    title = title,
    body = body,
    relatedId = relatedId,
    readAt = readAt,
    createdAt = createdAt
)

fun UserReportEntity.toDomain(): UserReport = UserReport(
    id = id,
    reporterId = reporterId,
    targetType = runCatching { ReportTargetType.valueOf(targetType) }.getOrDefault(ReportTargetType.COMMUNITY_POST),
    targetId = targetId,
    reason = reason,
    description = description,
    status = runCatching { ReportStatus.valueOf(status) }.getOrDefault(ReportStatus.PENDING),
    createdAt = createdAt
)

fun BlockedUserEntity.toDomain(): BlockedUser = BlockedUser(
    id = id,
    blockerId = blockerId,
    blockedUserId = blockedUserId,
    blockedUserName = blockedUserName,
    createdAt = createdAt
)

fun AdminAuditLogEntity.toDomain(): AdminAuditLog = AdminAuditLog(
    id = id,
    adminId = adminId,
    action = action,
    targetId = targetId,
    details = details,
    createdAt = createdAt
)
