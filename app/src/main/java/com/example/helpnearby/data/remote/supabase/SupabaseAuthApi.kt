package com.example.helpnearby.data.remote.supabase

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit contract for Supabase GoTrue Authentication endpoints (`/auth/v1/...`).
 * Base URL and `apikey` are injected at runtime via [com.example.helpnearby.core.network.BackendConfig].
 */
interface SupabaseAuthApi {

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Header("apikey") apiKey: String,
        @Body body: SupabaseSignUpRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(
        @Header("apikey") apiKey: String,
        @Body body: SupabaseSignInRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token?grant_type=id_token")
    suspend fun signInWithIdToken(
        @Header("apikey") apiKey: String,
        @Body body: SupabaseIdTokenRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshSession(
        @Header("apikey") apiKey: String,
        @Body body: SupabaseRefreshTokenRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/recover")
    suspend fun sendPasswordReset(
        @Header("apikey") apiKey: String,
        @Body body: SupabaseRecoverRequest
    ): Response<Unit>

    @POST("auth/v1/verify")
    suspend fun verifyOtp(
        @Header("apikey") apiKey: String,
        @Body body: SupabaseVerifyOtpRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/logout")
    suspend fun logout(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String
    ): Response<Unit>

    @GET("auth/v1/user")
    suspend fun getCurrentUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String
    ): Response<SupabaseAuthUser>
}
