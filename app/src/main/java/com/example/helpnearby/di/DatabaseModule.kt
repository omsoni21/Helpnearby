package com.example.helpnearby.di

import android.content.Context
import androidx.room.Room
import com.example.helpnearby.core.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideHelpNearbyDatabase(@ApplicationContext context: Context): HelpNearbyDatabase {
        return Room.databaseBuilder(
            context,
            HelpNearbyDatabase::class.java,
            "helpnearby_offline.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides fun provideUserDao(db: HelpNearbyDatabase): UserDao = db.userDao()
    @Provides fun provideHelpRequestDao(db: HelpNearbyDatabase): HelpRequestDao = db.helpRequestDao()
    @Provides fun provideHelpOfferDao(db: HelpNearbyDatabase): HelpOfferDao = db.helpOfferDao()
    @Provides fun provideVolunteerDao(db: HelpNearbyDatabase): VolunteerDao = db.volunteerDao()
    @Provides fun provideBloodDonorDao(db: HelpNearbyDatabase): BloodDonorDao = db.bloodDonorDao()
    @Provides fun provideLostFoundDao(db: HelpNearbyDatabase): LostFoundDao = db.lostFoundDao()
    @Provides fun provideCommunityDao(db: HelpNearbyDatabase): CommunityDao = db.communityDao()
    @Provides fun provideMessagingDao(db: HelpNearbyDatabase): MessagingDao = db.messagingDao()
    @Provides fun provideNotificationDao(db: HelpNearbyDatabase): NotificationDao = db.notificationDao()
    @Provides fun provideSafetyModerationDao(db: HelpNearbyDatabase): SafetyModerationDao = db.safetyModerationDao()
    @Provides fun provideSyncQueueDao(db: HelpNearbyDatabase): SyncQueueDao = db.syncQueueDao()
}
