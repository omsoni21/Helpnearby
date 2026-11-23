package com.example.helpnearby.core.navigation

import kotlinx.serialization.Serializable

sealed interface HelpNearbyRoute {
    @Serializable data object Splash : HelpNearbyRoute
    @Serializable data object Onboarding : HelpNearbyRoute
    @Serializable data object Auth : HelpNearbyRoute

    // Top-level bottom navigation tabs
    @Serializable data object Home : HelpNearbyRoute
    @Serializable data object Explore : HelpNearbyRoute
    @Serializable data object Messages : HelpNearbyRoute
    @Serializable data object Profile : HelpNearbyRoute

    // Secondary feature screens
    @Serializable data class CreateEditRequest(val requestId: String? = null) : HelpNearbyRoute
    @Serializable data class RequestDetail(val requestId: String) : HelpNearbyRoute
    @Serializable data object Volunteers : HelpNearbyRoute
    @Serializable data class VolunteerDetail(val userId: String) : HelpNearbyRoute
    @Serializable data object BloodDonors : HelpNearbyRoute
    @Serializable data object LostAndFound : HelpNearbyRoute
    @Serializable data class CreateLostFound(val initialType: String = "LOST") : HelpNearbyRoute
    @Serializable data object CommunityFeed : HelpNearbyRoute
    @Serializable data class ChatThread(val conversationId: String) : HelpNearbyRoute
    @Serializable data object Notifications : HelpNearbyRoute
    @Serializable data object EmergencySafety : HelpNearbyRoute
    @Serializable data object Settings : HelpNearbyRoute
    @Serializable data object EditProfile : HelpNearbyRoute
    @Serializable data object AdminModeration : HelpNearbyRoute
}
