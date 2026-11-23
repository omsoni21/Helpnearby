package com.example.helpnearby.di

import com.example.helpnearby.data.remote.fcm.LocalAndFcmNotificationTransport
import com.example.helpnearby.data.remote.fcm.PushNotificationTransport
import com.example.helpnearby.data.remote.repository.AuthRepositoryImpl
import com.example.helpnearby.data.repository.*
import com.example.helpnearby.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindUserRepository(impl: RoomUserRepository): UserRepository

    @Binds @Singleton
    abstract fun bindHelpRequestRepository(impl: RoomHelpRequestRepository): HelpRequestRepository

    @Binds @Singleton
    abstract fun bindVolunteerRepository(impl: RoomVolunteerRepository): VolunteerRepository

    @Binds @Singleton
    abstract fun bindBloodDonorRepository(impl: RoomBloodDonorRepository): BloodDonorRepository

    @Binds @Singleton
    abstract fun bindLostFoundRepository(impl: RoomLostFoundRepository): LostFoundRepository

    @Binds @Singleton
    abstract fun bindCommunityRepository(impl: RoomCommunityRepository): CommunityRepository

    @Binds @Singleton
    abstract fun bindMessagingRepository(impl: RoomMessagingRepository): MessagingRepository

    @Binds @Singleton
    abstract fun bindNotificationRepository(impl: RoomNotificationRepository): NotificationRepository

    @Binds @Singleton
    abstract fun bindSafetyModerationRepository(impl: RoomSafetyModerationRepository): SafetyModerationRepository

    @Binds @Singleton
    abstract fun bindPushTransport(impl: LocalAndFcmNotificationTransport): PushNotificationTransport
}
