package com.example.helpnearby.data.remote.supabase

import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit contract for Supabase PostgREST endpoints (`/rest/v1/...`) matching `/docs/SUPABASE_SCHEMA.sql`.
 */
interface SupabasePostgrestApi {

    // ---- Users & Preferences ----
    @GET("rest/v1/users")
    suspend fun fetchUserById(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String
    ): Response<List<SupabaseUserDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/users")
    suspend fun upsertUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseUserDto
    ): Response<List<SupabaseUserDto>>

    @DELETE("rest/v1/users")
    suspend fun deleteUserById(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String
    ): Response<Unit>

    @GET("rest/v1/user_preferences")
    suspend fun fetchPreferences(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("user_id") userIdEq: String
    ): Response<List<SupabasePreferencesDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/user_preferences")
    suspend fun upsertPreferences(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabasePreferencesDto
    ): Response<List<SupabasePreferencesDto>>

    // ---- Help Requests & Offers ----
    @GET("rest/v1/help_requests")
    suspend fun fetchHelpRequests(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("city") cityEq: String? = null,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): Response<List<SupabaseHelpRequestDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/help_requests")
    suspend fun upsertHelpRequest(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseHelpRequestDto
    ): Response<List<SupabaseHelpRequestDto>>

    @GET("rest/v1/help_offers")
    suspend fun fetchHelpOffers(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("request_id") requestIdEq: String? = null,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<SupabaseHelpOfferDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/help_offers")
    suspend fun upsertHelpOffer(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseHelpOfferDto
    ): Response<List<SupabaseHelpOfferDto>>

    // ---- Volunteers ----
    @GET("rest/v1/volunteer_profiles")
    suspend fun fetchVolunteerProfiles(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("city") cityEq: String? = null,
        @Query("order") order: String = "completed_count.desc"
    ): Response<List<SupabaseVolunteerProfileDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/volunteer_profiles")
    suspend fun upsertVolunteerProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseVolunteerProfileDto
    ): Response<List<SupabaseVolunteerProfileDto>>

    // ---- Blood Donors ----
    @GET("rest/v1/donor_profiles")
    suspend fun fetchPublicDonors(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("consent") consentEq: String = "eq.true",
        @Query("order") order: String = "updated_at.desc"
    ): Response<List<SupabaseDonorProfileDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/donor_profiles")
    suspend fun upsertDonorProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseDonorProfileDto
    ): Response<List<SupabaseDonorProfileDto>>

    @DELETE("rest/v1/donor_profiles")
    suspend fun deleteDonorProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("user_id") userIdEq: String
    ): Response<Unit>

    @GET("rest/v1/donor_contact_requests")
    suspend fun fetchDonorContactRequests(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<SupabaseDonorContactRequestDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/donor_contact_requests")
    suspend fun upsertDonorContactRequest(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseDonorContactRequestDto
    ): Response<List<SupabaseDonorContactRequestDto>>

    // ---- Lost & Found ----
    @GET("rest/v1/lost_found_posts")
    suspend fun fetchLostFoundPosts(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): Response<List<SupabaseLostFoundPostDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/lost_found_posts")
    suspend fun upsertLostFoundPost(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseLostFoundPostDto
    ): Response<List<SupabaseLostFoundPostDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/lost_found_claims")
    suspend fun upsertLostFoundClaim(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseLostFoundClaimDto
    ): Response<List<SupabaseLostFoundClaimDto>>

    // ---- Community & Comments ----
    @GET("rest/v1/community_posts")
    suspend fun fetchCommunityPosts(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("status") statusEq: String = "eq.ACTIVE",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): Response<List<SupabaseCommunityPostDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/community_posts")
    suspend fun upsertCommunityPost(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseCommunityPostDto
    ): Response<List<SupabaseCommunityPostDto>>

    @DELETE("rest/v1/community_posts")
    suspend fun deleteCommunityPost(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String
    ): Response<Unit>

    @GET("rest/v1/comments")
    suspend fun fetchComments(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("post_id") postIdEq: String,
        @Query("order") order: String = "created_at.asc"
    ): Response<List<SupabaseCommentDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/comments")
    suspend fun upsertComment(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseCommentDto
    ): Response<List<SupabaseCommentDto>>

    // ---- Messaging & Notifications ----
    @GET("rest/v1/conversations")
    suspend fun fetchConversations(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("order") order: String = "last_message_at.desc"
    ): Response<List<SupabaseConversationDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/conversations")
    suspend fun upsertConversation(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseConversationDto
    ): Response<List<SupabaseConversationDto>>

    @GET("rest/v1/messages")
    suspend fun fetchMessages(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("conversation_id") conversationIdEq: String,
        @Query("order") order: String = "created_at.asc"
    ): Response<List<SupabaseMessageDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/messages")
    suspend fun upsertMessage(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseMessageDto
    ): Response<List<SupabaseMessageDto>>

    @GET("rest/v1/notifications")
    suspend fun fetchNotifications(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("user_id") userIdEq: String,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<SupabaseNotificationDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/notifications")
    suspend fun upsertNotification(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseNotificationDto
    ): Response<List<SupabaseNotificationDto>>

    // ---- Safety & Moderation ----
    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/user_reports")
    suspend fun insertReport(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseUserReportDto
    ): Response<List<SupabaseUserReportDto>>

    @GET("rest/v1/blocked_users")
    suspend fun fetchBlockedUsers(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("blocker_id") blockerIdEq: String
    ): Response<List<SupabaseBlockedUserDto>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/blocked_users")
    suspend fun upsertBlockedUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body payload: SupabaseBlockedUserDto
    ): Response<List<SupabaseBlockedUserDto>>

    @DELETE("rest/v1/blocked_users")
    suspend fun deleteBlockedUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("blocker_id") blockerIdEq: String,
        @Query("blocked_user_id") blockedUserIdEq: String
    ): Response<Unit>
}
