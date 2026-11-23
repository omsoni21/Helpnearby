package com.example.helpnearby.data.remote.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.common.InputValidators
import com.example.helpnearby.core.database.HelpNearbyDatabase
import com.example.helpnearby.core.database.UserDao
import com.example.helpnearby.core.database.UserEntity
import com.example.helpnearby.core.database.UserPreferencesEntity
import com.example.helpnearby.core.database.VolunteerDao
import com.example.helpnearby.core.database.VolunteerProfileEntity
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.core.security.SecurityGuard
import com.example.helpnearby.core.sync.SyncManager
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.data.local.toDomain
import com.example.helpnearby.data.local.toEntity
import com.example.helpnearby.data.remote.supabase.GoogleCredentialHelper
import com.example.helpnearby.data.remote.supabase.GoogleCredentialResult
import com.example.helpnearby.data.remote.supabase.SupabaseAuthApi
import com.example.helpnearby.data.remote.supabase.SupabaseAuthResponse
import com.example.helpnearby.data.remote.supabase.SupabaseAuthUser
import com.example.helpnearby.data.remote.supabase.SupabasePostgrestApi
import com.example.helpnearby.data.remote.supabase.SupabaseSdkProvider
import com.example.helpnearby.data.remote.supabase.SupabaseSessionManager
import com.example.helpnearby.data.remote.supabase.toEntity
import com.example.helpnearby.data.remote.supabase.toSupabaseDto
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.model.AppThemeMode
import com.example.helpnearby.domain.model.BackendProvider
import com.example.helpnearby.domain.model.User
import com.example.helpnearby.domain.model.UserRole
import com.example.helpnearby.domain.model.VerificationStatus
import com.example.helpnearby.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [AuthRepository] backed by the official `supabase-kt` [SupabaseClient] ([Auth] plugin)
 * and Jetpack Credential Manager for Google authentication, with Room local profile caching and
 * offline local fallback when Supabase secrets are not configured.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val db: HelpNearbyDatabase,
    private val userDao: UserDao,
    private val volunteerDao: VolunteerDao,
    private val prefsDataStore: PreferencesDataStore,
    private val securityGuard: SecurityGuard,
    private val backendConfig: BackendConfig,
    private val supabaseClient: SupabaseClient?,
    private val supabaseSdkProvider: SupabaseSdkProvider?,
    private val supabaseAuthApi: SupabaseAuthApi?,
    private val supabasePostgrestApi: SupabasePostgrestApi?,
    private val supabaseSessionManager: SupabaseSessionManager?,
    private val googleCredentialHelper: GoogleCredentialHelper?,
    private val syncManager: SyncManager?
) : AuthRepository {

    @Volatile
    private var pendingVerificationUserId: String? = null

    private val supabaseAuth: Auth?
        get() = runCatching { supabaseClient?.auth ?: supabaseSdkProvider?.authOrNull }.getOrNull()

    override val backendProvider: BackendProvider
        get() = backendConfig.activeProvider

    override val currentUser: Flow<User?> = prefsDataStore.currentUserId.flatMapLatest { userId ->
        if (userId == null) flowOf(null)
        else userDao.observeUserById(userId).map { it?.toDomain() }
    }

    override val isLoggedIn: Flow<Boolean> = currentUser.map { it != null }

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
    ): AppResult<User> {
        InputValidators.validateFullName(fullName)?.let { return AppResult.Failure(it) }
        InputValidators.validateEmail(email)?.let { return AppResult.Failure(it) }
        InputValidators.validatePassword(password)?.let { return AppResult.Failure(it) }
        InputValidators.validateCityAndArea(city, state)?.let { return AppResult.Failure(it) }

        val cleanName = fullName.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanCity = city.trim()
        val cleanState = state.trim()
        val cleanCountry = country.trim().ifBlank { "India" }
        val now = System.currentTimeMillis()

        if (backendConfig.isSupabaseConfigured && supabaseAuth != null) {
            return registerWithSupabaseClient(
                auth = supabaseAuth!!,
                fullName = cleanName,
                email = cleanEmail,
                password = password,
                city = cleanCity,
                state = cleanState,
                country = cleanCountry,
                language = language,
                interests = interests,
                volunteerAvailable = volunteerAvailable,
                now = now
            )
        }

        // Local offline mode fallback when Supabase Secrets are not yet configured
        return try {
            val existing = userDao.getUserByEmail(cleanEmail)
            if (existing != null) {
                return AppResult.Failure("An account with this email already exists.")
            }

            val userId = UUID.randomUUID().toString()
            val user = User(
                id = userId,
                fullName = cleanName,
                email = cleanEmail,
                city = cleanCity,
                state = cleanState,
                country = cleanCountry,
                language = language,
                interests = interests,
                volunteerAvailable = volunteerAvailable,
                emailVerified = false,
                isSuspended = false,
                role = UserRole.MEMBER,
                createdAt = now,
                updatedAt = now
            )

            persistLocalUserRecords(user, securityGuard.hashPasswordLocal(password), now)
            pendingVerificationUserId = userId
            prefsDataStore.setCurrentUserId(userId)
            prefsDataStore.setLanguage(language)
            prefsDataStore.setSelectedLocation(cleanCity, cleanCity)
            AppResult.Success(user)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AppResult.Failure(t.message ?: "Unable to create local account.")
        }
    }

    private suspend fun registerWithSupabaseClient(
        auth: Auth,
        fullName: String,
        email: String,
        password: String,
        city: String,
        state: String,
        country: String,
        language: AppLanguage,
        interests: List<String>,
        volunteerAvailable: Boolean,
        now: Long
    ): AppResult<User> {
        return try {
            val signedUpUserInfo = auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = buildJsonObject {
                    put("full_name", fullName)
                    put("city", city)
                    put("state", state)
                    put("country", country)
                    put("language", language.name)
                    put("interests", JsonArray(interests.map { JsonPrimitive(it) }))
                    put("volunteer_available", volunteerAvailable)
                }
            }

            val session = auth.currentSessionOrNull()
            val resolvedInfo = session?.user ?: signedUpUserInfo ?: auth.currentUserOrNull()
                ?: return AppResult.Failure("Supabase did not return a user record.")

            val hasAccessToken = session != null && session.accessToken.isNotBlank()
            if (session != null) {
                supabaseSessionManager?.saveSession(session.toLegacyAuthResponse())
            }

            val isAdmin = resolvedInfo.appMetadata?.get("role")?.jsonPrimitive?.contentOrNull
                ?.equals("admin", ignoreCase = true) == true
            val isConfirmed = resolvedInfo.emailConfirmedAt != null ||
                resolvedInfo.confirmedAt != null ||
                hasAccessToken

            val user = User(
                id = resolvedInfo.id,
                fullName = fullName,
                email = email,
                city = city,
                state = state,
                country = country,
                language = language,
                interests = interests,
                volunteerAvailable = volunteerAvailable,
                emailVerified = isConfirmed,
                isSuspended = false,
                role = if (isAdmin) UserRole.ADMIN else UserRole.MEMBER,
                createdAt = now,
                updatedAt = now
            )

            persistLocalUserRecords(user, "SUPABASE_AUTH", now)
            prefsDataStore.setLanguage(language)
            prefsDataStore.setSelectedLocation(city, city)

            if (hasAccessToken && session != null) {
                pendingVerificationUserId = null
                prefsDataStore.setCurrentUserId(user.id)
                syncSignedInProfileAndPreferences(
                    apiKey = backendConfig.supabaseAnonKey,
                    bearer = "Bearer ${session.accessToken}",
                    user = user,
                    city = city,
                    volunteerAvailable = volunteerAvailable
                )
            } else {
                // Confirm Email is ON in Supabase: do not activate a logged-in session without a JWT yet
                pendingVerificationUserId = user.id
            }

            AppResult.Success(user)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AppResult.Failure(readableAuthError(t, "Supabase sign-up failed."))
        }
    }

    override suspend fun loginWithEmail(email: String, password: String): AppResult<User> {
        InputValidators.validateEmail(email)?.let { return AppResult.Failure(it) }
        if (password.isBlank()) return AppResult.Failure("Password is required.")

        val cleanEmail = email.trim().lowercase()
        val auth = supabaseAuth
        if (backendConfig.isSupabaseConfigured && auth != null) {
            return try {
                auth.signInWith(Email) {
                    this.email = cleanEmail
                    this.password = password
                }
                val session = auth.currentSessionOrNull()
                    ?: return AppResult.Failure("Sign-in succeeded, but no active Supabase session was returned.")
                completeSupabaseSignIn(session.toLegacyAuthResponse(fallbackEmail = cleanEmail))
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppResult.Failure(readableAuthError(t, "Invalid email or password."))
            }
        }

        if (backendConfig.isSupabaseConfigured) {
            return AppResult.Failure("Supabase Auth client is unavailable. Check SUPABASE_URL and SUPABASE_ANON_KEY.")
        }

        val entity = userDao.getUserByEmail(cleanEmail)
            ?: return AppResult.Failure("No account found with that email.")

        if (entity.isSuspended) {
            return AppResult.Failure("This account has been suspended due to policy violations.")
        }

        val hash = securityGuard.hashPasswordLocal(password)
        if (entity.passwordHash != hash) {
            return AppResult.Failure("Incorrect password. Please try again.")
        }

        prefsDataStore.setCurrentUserId(entity.id)
        prefsDataStore.setSelectedLocation(entity.city, entity.city)
        return AppResult.Success(entity.toDomain())
    }

    /**
     * Authenticates with Google using Jetpack Credential Manager ([GoogleCredentialHelper]) and
     * exchanges the resulting Google ID token directly with the Supabase Auth client via [IDToken].
     */
    suspend fun signInWithGoogle(activityContext: Context): AppResult<User> {
        if (!backendConfig.isSupabaseConfigured) {
            return AppResult.Failure(
                "Configure SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets to sign in with Google."
            )
        }
        val helper = googleCredentialHelper
            ?: return AppResult.Failure("Google Credential Manager helper is unavailable.")

        return when (val credResult = helper.requestGoogleIdToken(activityContext)) {
            is GoogleCredentialResult.IdToken -> loginWithGoogleIdToken(
                idToken = credResult.idToken,
                nonce = credResult.rawNonce
            )
            is GoogleCredentialResult.Cancelled -> AppResult.Failure("Google sign-in was cancelled.")
            is GoogleCredentialResult.Unavailable -> AppResult.Failure(credResult.reason)
        }
    }

    override suspend fun loginWithGoogleIdToken(idToken: String, nonce: String?): AppResult<User> {
        if (idToken.isBlank()) {
            return AppResult.Failure("No Google ID token received. Please try again.")
        }
        val auth = supabaseAuth
        if (backendConfig.isSupabaseConfigured && auth != null) {
            return try {
                auth.signInWith(IDToken) {
                    this.idToken = idToken
                    this.provider = Google
                    this.nonce = nonce
                }
                val session = auth.currentSessionOrNull()
                    ?: return AppResult.Failure("Google Sign-In completed, but Supabase session is unavailable.")
                completeSupabaseSignIn(session.toLegacyAuthResponse())
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppResult.Failure(readableAuthError(t, "Google Sign-In with Supabase failed."))
            }
        }

        return AppResult.Failure(
            "Configure SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets to complete Google Sign-In."
        )
    }

    override suspend fun startGoogleOAuthSignIn(): AppResult<Unit> {
        if (!backendConfig.isSupabaseConfigured) {
            return AppResult.Failure(
                "Configure SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets to sign in with Google."
            )
        }
        val auth = supabaseAuth
        if (auth != null) {
            return try {
                auth.signInWith(Google)
                AppResult.Success(Unit)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppResult.Failure(readableAuthError(t, "Unable to start Google Sign-In with Supabase."))
            }
        }
        val sdkProvider = supabaseSdkProvider
            ?: return AppResult.Failure("Supabase SDK client is unavailable.")
        return sdkProvider.signInWithGoogleOAuth().fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { err ->
                AppResult.Failure(err.message ?: "Unable to start Google Sign-In with Supabase.")
            }
        )
    }

    override suspend fun completeSupabaseOAuthSession(
        accessToken: String,
        refreshToken: String?,
        expiresAtEpochSec: Long?,
        userId: String,
        email: String?,
        emailConfirmed: Boolean,
        fullName: String?,
        isAdmin: Boolean
    ): AppResult<User> {
        if (!backendConfig.isSupabaseConfigured) {
            return AppResult.Failure("Supabase is not configured.")
        }
        val authBody = SupabaseAuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAtEpochSec,
            user = SupabaseAuthUser(
                id = userId,
                email = email,
                emailConfirmedAt = if (emailConfirmed) "oauth" else null,
                appMetadata = if (isAdmin) mapOf("role" to "admin") else emptyMap(),
                userMetadata = fullName?.let { mapOf("full_name" to it) } ?: emptyMap()
            )
        )
        return try {
            completeSupabaseSignIn(authBody)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AppResult.Failure(t.message ?: "Unable to complete Google OAuth sign-in.")
        }
    }

    override suspend fun sendPasswordReset(email: String): AppResult<String> {
        InputValidators.validateEmail(email)?.let { return AppResult.Failure(it) }
        val cleanEmail = email.trim().lowercase()
        val auth = supabaseAuth

        if (backendConfig.isSupabaseConfigured && auth != null) {
            return try {
                auth.resetPasswordForEmail(email = cleanEmail)
                AppResult.Success("Password reset link dispatched via Supabase Auth to $cleanEmail.")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppResult.Failure(readableAuthError(t, "Unable to send password reset email."))
            }
        }

        return AppResult.Failure(
            "Connect Supabase Auth (SUPABASE_URL & SUPABASE_ANON_KEY) to send password reset emails."
        )
    }

    override suspend fun verifyEmail(code: String): AppResult<User> {
        val currentId = prefsDataStore.currentUserId.first() ?: pendingVerificationUserId
            ?: return AppResult.Failure("No active user session.")
        val entity = userDao.getUserById(currentId)
            ?: return AppResult.Failure("User record missing.")
        val auth = supabaseAuth

        if (backendConfig.isSupabaseConfigured && auth != null) {
            return try {
                val trimmed = code.trim()
                if (trimmed.length >= 4) {
                    auth.verifyEmailOtp(
                        type = OtpType.Email.SIGNUP,
                        email = entity.email,
                        token = trimmed
                    )
                    val session = auth.currentSessionOrNull()
                    if (session != null) {
                        supabaseSessionManager?.saveSession(session.toLegacyAuthResponse(entity.email))
                    }
                    val updated = entity.copy(emailVerified = true, updatedAt = System.currentTimeMillis())
                    userDao.upsertUser(updated)
                    pendingVerificationUserId = null
                    prefsDataStore.setCurrentUserId(updated.id)
                    session?.accessToken?.let { token ->
                        syncSignedInProfileAndPreferences(
                            apiKey = backendConfig.supabaseAnonKey,
                            bearer = "Bearer $token",
                            user = updated.toDomain(),
                            city = updated.city,
                            volunteerAvailable = updated.volunteerAvailable
                        )
                    }
                    return AppResult.Success(updated.toDomain())
                }

                // Fallback: check whether the user clicked the confirmation link in their email
                val existingSession = auth.currentSessionOrNull()
                if (existingSession != null) {
                    val refreshedUser = runCatching { auth.retrieveUserForCurrentSession(updateSession = true) }.getOrNull()
                    val confirmed = refreshedUser?.emailConfirmedAt != null || refreshedUser?.confirmedAt != null
                    if (confirmed) {
                        supabaseSessionManager?.saveSession(existingSession.toLegacyAuthResponse(entity.email))
                        val updated = entity.copy(emailVerified = true, updatedAt = System.currentTimeMillis())
                        userDao.upsertUser(updated)
                        pendingVerificationUserId = null
                        prefsDataStore.setCurrentUserId(updated.id)
                        runCatching {
                            supabasePostgrestApi?.upsertUser(
                                backendConfig.supabaseAnonKey,
                                "Bearer ${existingSession.accessToken}",
                                updated.toSupabaseDto()
                            )
                        }
                        return AppResult.Success(updated.toDomain())
                    }
                }
                AppResult.Failure("Enter the verification code sent to ${entity.email} or click the confirmation link.")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppResult.Failure(readableAuthError(t, "Invalid or expired verification code."))
            }
        }

        if (code.trim().length < 4) {
            return AppResult.Failure("Enter the 4+ digit verification code.")
        }
        val updated = entity.copy(emailVerified = true, updatedAt = System.currentTimeMillis())
        userDao.upsertUser(updated)
        pendingVerificationUserId = null
        return AppResult.Success(updated.toDomain())
    }

    override suspend fun logout(): AppResult<Unit> {
        if (backendConfig.isSupabaseConfigured) {
            runCatching { supabaseAuth?.signOut() }
            runCatching {
                supabaseSessionManager?.getValidBearerToken()?.let { bearer ->
                    supabaseAuthApi?.logout(backendConfig.supabaseAnonKey, bearer)
                }
            }
            supabaseSessionManager?.clearSession()
        }
        pendingVerificationUserId = null
        prefsDataStore.setCurrentUserId(null)
        return AppResult.Success(Unit)
    }

    override suspend fun deleteAccount(userId: String): AppResult<Unit> {
        val existing = userDao.getUserById(userId)
            ?: return AppResult.Failure("Account not found.")
        val postgrest = supabasePostgrestApi
        val sessionMgr = supabaseSessionManager

        if (backendConfig.isSupabaseConfigured && postgrest != null && sessionMgr != null) {
            runCatching {
                sessionMgr.getValidBearerToken()?.let { bearer ->
                    postgrest.deleteUserById(backendConfig.supabaseAnonKey, bearer, "eq.$userId")
                }
            }
            runCatching { supabaseAuth?.signOut() }
            sessionMgr.clearSession()
        }

        db.withTransaction {
            db.helpRequestDao().deleteByOwner(userId)
            db.helpOfferDao().deleteByVolunteer(userId)
            db.bloodDonorDao().deleteByUserId(userId)
            db.lostFoundDao().deleteByOwner(userId)
            db.communityDao().deleteByAuthor(userId)
            db.notificationDao().deleteByUser(userId)
            userDao.deleteUserById(existing.id)
        }
        pendingVerificationUserId = null
        prefsDataStore.setCurrentUserId(null)
        return AppResult.Success(Unit)
    }

    private suspend fun completeSupabaseSignIn(authBody: SupabaseAuthResponse): AppResult<User> {
        val sessionMgr = supabaseSessionManager ?: return AppResult.Failure("Session manager unavailable.")
        val postgrest = supabasePostgrestApi ?: return AppResult.Failure("PostgREST unavailable.")
        val authUser = authBody.resolvedUser() ?: return AppResult.Failure("Missing user info from Supabase.")
        val apiKey = backendConfig.supabaseAnonKey

        sessionMgr.saveSession(authBody)
        val bearer = authBody.accessToken?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
            ?: sessionMgr.getValidBearerToken()
            ?: return AppResult.Failure("Missing access token in Supabase response.")

        val userResp = runCatching { postgrest.fetchUserById(apiKey, bearer, "eq.${authUser.id}") }.getOrNull()
        val remoteDto = if (userResp?.isSuccessful == true) userResp.body()?.firstOrNull() else null
        val now = System.currentTimeMillis()

        val entity = if (remoteDto != null) {
            remoteDto.toEntity("SUPABASE_AUTH").copy(
                emailVerified = authUser.isEmailConfirmed || remoteDto.emailVerified,
                role = if (authUser.isServerVerifiedAdmin) UserRole.ADMIN.name else remoteDto.role
            )
        } else {
            val meta = authUser.userMetadata.orEmpty()
            val city = (meta["city"] as? String)?.takeIf { it.isNotBlank() } ?: "Bengaluru"
            val state = (meta["state"] as? String)?.takeIf { it.isNotBlank() } ?: "Karnataka"
            val country = (meta["country"] as? String)?.takeIf { it.isNotBlank() } ?: "India"
            val fullName = (meta["full_name"] as? String)?.takeIf { it.isNotBlank() }
                ?: (meta["name"] as? String)?.takeIf { it.isNotBlank() }
                ?: authUser.email?.substringBefore('@')?.replaceFirstChar { it.uppercase() }
                ?: "Community Member"
            val lang = (meta["language"] as? String)?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.EN
            val volunteerAvail = meta["volunteer_available"] as? Boolean ?: false

            val created = UserEntity(
                id = authUser.id,
                fullName = fullName,
                email = authUser.email.orEmpty(),
                passwordHash = "SUPABASE_AUTH",
                city = city,
                state = state,
                country = country,
                language = lang.name,
                interestsCsv = "",
                volunteerAvailable = volunteerAvail,
                emailVerified = authUser.isEmailConfirmed,
                isSuspended = false,
                role = if (authUser.isServerVerifiedAdmin) UserRole.ADMIN.name else UserRole.MEMBER.name,
                createdAt = now,
                updatedAt = now
            )
            runCatching { postgrest.upsertUser(apiKey, bearer, created.toSupabaseDto()) }
            created
        }

        if (entity.isSuspended) {
            runCatching { supabaseAuth?.signOut() }
            sessionMgr.clearSession()
            return AppResult.Failure("This account has been suspended due to policy violations.")
        }

        userDao.upsertUser(entity)
        val prefsResp = runCatching { postgrest.fetchPreferences(apiKey, bearer, "eq.${entity.id}") }.getOrNull()
        val remotePrefs = if (prefsResp?.isSuccessful == true) prefsResp.body()?.firstOrNull()?.toEntity() else null
        if (remotePrefs != null) {
            userDao.upsertPreferences(remotePrefs)
            prefsDataStore.setSelectedLocation(remotePrefs.selectedCity, remotePrefs.selectedArea)
        } else {
            val localPrefs = UserPreferencesEntity(
                userId = entity.id,
                selectedCity = entity.city,
                selectedArea = entity.city
            )
            userDao.upsertPreferences(localPrefs)
            runCatching { postgrest.upsertPreferences(apiKey, bearer, localPrefs.toSupabaseDto()) }
            prefsDataStore.setSelectedLocation(entity.city, entity.city)
        }

        prefsDataStore.setCurrentUserId(entity.id)
        runCatching { syncManager?.refreshFromCloud(entity.city, entity.id) }
        return AppResult.Success(entity.toDomain())
    }

    private suspend fun syncSignedInProfileAndPreferences(
        apiKey: String,
        bearer: String,
        user: User,
        city: String,
        volunteerAvailable: Boolean
    ) {
        val postgrest = supabasePostgrestApi ?: return
        runCatching {
            val userUpsertResp = postgrest.upsertUser(apiKey, bearer, user.toEntity("SUPABASE_AUTH").toSupabaseDto())
            if (!userUpsertResp.isSuccessful) {
                syncManager?.enqueueOperation("user_profile", user.id, "UPSERT", """{"id":"${user.id}"}""", 1)
            }
            userDao.getPreferences(user.id)?.let {
                val prefsResp = postgrest.upsertPreferences(apiKey, bearer, it.toSupabaseDto())
                if (!prefsResp.isSuccessful) {
                    syncManager?.enqueueOperation("user_preferences", user.id, "UPSERT", """{"userId":"${user.id}"}""", 1)
                }
            }
            if (volunteerAvailable) {
                volunteerDao.getByUserId(user.id)?.let {
                    val volResp = postgrest.upsertVolunteerProfile(apiKey, bearer, it.toSupabaseDto())
                    if (!volResp.isSuccessful) {
                        syncManager?.enqueueOperation("volunteer_profile", user.id, "UPSERT", """{"userId":"${user.id}"}""", 1)
                    }
                }
            }
            syncManager?.refreshFromCloud(city, user.id)
        }
    }

    private suspend fun persistLocalUserRecords(user: User, passwordHash: String, now: Long) {
        db.withTransaction {
            userDao.upsertUser(user.toEntity(passwordHash))
            userDao.upsertPreferences(
                UserPreferencesEntity(
                    userId = user.id,
                    theme = AppThemeMode.SYSTEM.name,
                    language = user.language.name,
                    notificationsEnabled = true,
                    locationEnabled = false,
                    selectedCity = user.city,
                    selectedArea = user.city
                )
            )
            if (user.volunteerAvailable) {
                volunteerDao.upsert(
                    VolunteerProfileEntity(
                        userId = user.id,
                        fullName = user.fullName,
                        city = user.city,
                        bio = "Community volunteer ready to assist neighbors nearby.",
                        skillsCsv = user.interests.joinToString(","),
                        interestsCsv = user.interests.joinToString(","),
                        availability = true,
                        completedCount = 0,
                        verificationStatus = VerificationStatus.UNVERIFIED.name,
                        createdAt = now
                    )
                )
            }
        }
    }

    private fun UserSession.toLegacyAuthResponse(fallbackEmail: String? = null): SupabaseAuthResponse {
        val authUser = user?.toLegacyAuthUser(fallbackEmail)
        return SupabaseAuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            user = authUser
        )
    }

    private fun UserInfo.toLegacyAuthUser(fallbackEmail: String? = null): SupabaseAuthUser {
        val metaMap = userMetadata?.mapValues { (_, v) ->
            val p = runCatching { v.jsonPrimitive }.getOrNull()
            p?.booleanOrNull ?: p?.contentOrNull
        }
        val appMap = appMetadata?.mapValues { (_, v) ->
            val p = runCatching { v.jsonPrimitive }.getOrNull()
            p?.booleanOrNull ?: p?.contentOrNull
        }
        return SupabaseAuthUser(
            id = id,
            email = email ?: fallbackEmail,
            emailConfirmedAt = emailConfirmedAt?.toString(),
            confirmedAt = confirmedAt?.toString(),
            appMetadata = appMap,
            userMetadata = metaMap
        )
    }

    private fun readableAuthError(t: Throwable, fallback: String): String {
        return when (t) {
            is RestException -> t.error.takeIf { it.isNotBlank() } ?: t.message?.lineSequence()?.firstOrNull() ?: fallback
            else -> t.message?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: fallback
        }
    }
}
