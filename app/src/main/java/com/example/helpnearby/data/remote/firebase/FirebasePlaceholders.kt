package com.example.helpnearby.data.remote.firebase

import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Firebase implementation placeholders (Spark-plan friendly: Auth + Firestore only, NO Firebase Cloud Storage).
 * These return explicit configuration-missing errors rather than fake successes until the project
 * owner supplies `google-services.json` and enables Firebase Auth + Firestore dependencies in `app/build.gradle.kts`.
 */
class FirebaseBackendUnavailableException : IllegalStateException(
    "Firebase configuration (google-services.json) is not provided."
)

class FirebaseAuthPlaceholder @Inject constructor() : AuthRepository {
    override val currentUser: Flow<User?> = flowOf(null)
    override val isLoggedIn: Flow<Boolean> = flowOf(false)
    override val backendProvider: BackendProvider = BackendProvider.FIREBASE

    override suspend fun registerWithEmail(
        fullName: String,
        email: String,
        password: String,
        city: String,
        state: String,
        country: String,
        language: AppLanguage,
        interests: List<String>,
        volunteerAvailable: Boolean
    ): AppResult<User> = AppResult.Failure(
        "Firebase Auth not configured. Add google-services.json to enable cloud registration.",
        FirebaseBackendUnavailableException()
    )

    override suspend fun loginWithEmail(email: String, password: String): AppResult<User> =
        AppResult.Failure("Firebase Auth not configured.", FirebaseBackendUnavailableException())

    override suspend fun loginWithGoogleIdToken(idToken: String, nonce: String?): AppResult<User> =
        AppResult.Failure("Firebase Google Sign-In not configured.", FirebaseBackendUnavailableException())

    override suspend fun startGoogleOAuthSignIn(): AppResult<Unit> =
        AppResult.Failure("Supabase Google OAuth is not available on FirebaseAuthPlaceholder.")

    override suspend fun completeSupabaseOAuthSession(
        accessToken: String,
        refreshToken: String?,
        expiresAtEpochSec: Long?,
        userId: String,
        email: String?,
        emailConfirmed: Boolean,
        fullName: String?,
        isAdmin: Boolean
    ): AppResult<User> =
        AppResult.Failure("Supabase OAuth is not available on FirebaseAuthPlaceholder.")

    override suspend fun sendPasswordReset(email: String): AppResult<String> =
        AppResult.Failure("Firebase Auth not configured.")

    override suspend fun verifyEmail(code: String): AppResult<User> =
        AppResult.Failure("Firebase Auth not configured.")

    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun deleteAccount(userId: String): AppResult<Unit> =
        AppResult.Failure("Firebase Auth not configured.")
}

class FirebaseSyncGatewayPlaceholder @Inject constructor() {
    suspend fun pushHelpRequest(request: HelpRequest): AppResult<HelpRequest> =
        AppResult.Failure("Firestore not configured. Image upload disabled on Spark plan unless external storage is provided.")

    suspend fun pushMessage(message: Message): AppResult<Message> =
        AppResult.Failure("Firestore not configured.")
}
