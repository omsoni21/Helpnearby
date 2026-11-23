package com.example.helpnearby.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        UserPreferencesEntity::class,
        HelpRequestEntity::class,
        HelpOfferEntity::class,
        VolunteerProfileEntity::class,
        DonorProfileEntity::class,
        DonorContactRequestEntity::class,
        LostFoundPostEntity::class,
        LostFoundClaimEntity::class,
        CommunityPostEntity::class,
        CommentEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        NotificationEntity::class,
        UserReportEntity::class,
        BlockedUserEntity::class,
        AdminAuditLogEntity::class,
        PendingSyncOperationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HelpNearbyDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun helpRequestDao(): HelpRequestDao
    abstract fun helpOfferDao(): HelpOfferDao
    abstract fun volunteerDao(): VolunteerDao
    abstract fun bloodDonorDao(): BloodDonorDao
    abstract fun lostFoundDao(): LostFoundDao
    abstract fun communityDao(): CommunityDao
    abstract fun messagingDao(): MessagingDao
    abstract fun notificationDao(): NotificationDao
    abstract fun safetyModerationDao(): SafetyModerationDao
    abstract fun syncQueueDao(): SyncQueueDao
}
