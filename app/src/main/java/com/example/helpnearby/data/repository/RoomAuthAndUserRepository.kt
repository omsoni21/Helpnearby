package com.example.helpnearby.data.repository

import androidx.room.withTransaction
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.common.InputValidators
import com.example.helpnearby.core.database.*
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.core.security.SecurityGuard
import com.example.helpnearby.core.sync.SyncManager
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.data.local.toDomain
import com.example.helpnearby.data.local.toEntity
import com.example.helpnearby.data.remote.supabase.*
import com.example.helpnearby.domain.model.*
import com.example.helpnearby.domain.repository.AuthRepository
import com.example.helpnearby.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RoomAuthRepository @Inject constructor(
    private val db: HelpNearbyDatabase,
    private val userDao: UserDao,
    private val volunteerDao: VolunteerDao,
    private val prefsDataStore: PreferencesDataStore,
    private val securityGuard: SecurityGuard,
    private val backendConfig: BackendConfig,
    private val supabaseAuthApi: SupabaseAuthApi?,
    private val supabasePostgrestApi: SupabasePostgrestApi?,
    private val supabaseSessionManager: SupabaseSessionManager?,
    private val supabaseSdkProvider: SupabaseSdkProvider?,
    private val syncManager: SyncManager?
) : AuthRepository {

    constructor(
        db: HelpNearbyDatabase,
        userDao: UserDao,
        volunteerDao: VolunteerDao,
        prefsDataStore: PreferencesDataStore,
        securityGuard: SecurityGuard,
        backendConfig: BackendConfig
    ) : this(
        db, userDao, volunteerDao, prefsDataStore, securityGuard,
        backendConfig, null, null, null, null, null
    )

    @Volatile
    private var pendingVerificationUserId: String? = null

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

        val cleanEmail = email.trim().lowercase()
        val now = System.currentTimeMillis()

        if (backendConfig.isSupabaseConfigured && supabaseAuthApi != null && supabaseSessionManager != null) {
            return registerWithSupabase(
                fullName = fullName.trim(),
                email = cleanEmail,
                password = password,
                city = city.trim(),
                state = state.trim(),
                country = country.trim().ifBlank { "India" },
                language = language,
                interests = interests,
                volunteerAvailable = volunteerAvailable,
                now = now
            )
        }

        return runCatching {
            val existing = userDao.getUserByEmail(cleanEmail)
            if (existing != null) {
                return AppResult.Failure("An account with this email already exists.")
            }

            val userId = UUID.randomUUID().toString()
            val user = User(
                id = userId,
                fullName = fullName.trim(),
                email = cleanEmail,
                city = city.trim(),
                state = state.trim(),
                country = country.trim().ifBlank { "India" },
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
            prefsDataStore.setSelectedLocation(city.trim(), city.trim())
            AppResult.Success(user)
        }.getOrElse { err ->
            AppResult.Failure(err.message ?: "Unable to create local account.")
        }
    }

    private suspend fun registerWithSupabase(
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
        val authApi = supabaseAuthApi ?: return AppResult.Failure("Supabase Auth client unavailable.")
        val sessionMgr = supabaseSessionManager ?: return AppResult.Failure("Session manager unavailable.")
        val postgrest = supabasePostgrestApi ?: return AppResult.Failure("Supabase PostgREST client unavailable.")
        val apiKey = backendConfig.supabaseAnonKey

        return runCatching {
            val signUpResp = authApi.signUp(
                apiKey = apiKey,
                body = SupabaseSignUpRequest(
                    email = email,
                    password = password,
                    data = mapOf(
                        "full_name" to fullName,
                        "city" to city,
                        "state" to state,
                        "country" to country,
                        "language" to language.name,
                        "interests" to interests.toList(),
                        "volunteer_available" to volunteerAvailable
                    )
                )
            )
            if (!signUpResp.isSuccessful) {
                return AppResult.Failure(sessionMgr.parseError(signUpResp, "Supabase sign-up failed."))
            }
            val authBody = signUpResp.body()
            val authUser = authBody?.resolvedUser()
                ?: return AppResult.Failure("Supabase did not return a user record.")

            val hasAccessToken = !authBody.accessToken.isNullOrBlank()
            if (hasAccessToken) {
                sessionMgr.saveSession(authBody)
            }

            val user = User(
                id = authUser.id,
                fullName = fullName,
                email = email,
                city = city,
                state = state,
                country = country,
                language = language,
                interests = interests,
                volunteerAvailable = volunteerAvailable,
                emailVerified = authUser.isEmailConfirmed || hasAccessToken,
                isSuspended = false,
                role = if (authUser.isServerVerifiedAdmin) UserRole.ADMIN else UserRole.MEMBER,
                createdAt = now,
                updatedAt = now
            )
            persistLocalUserRecords(user, "SUPABASE_AUTH", now)
            prefsDataStore.setLanguage(language)
            prefsDataStore.setSelectedLocation(city, city)

            if (hasAccessToken) {
                pendingVerificationUserId = null
                prefsDataStore.setCurrentUserId(user.id)
                val bearer = "Bearer ${authBody.accessToken}"
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
            } else {
                // Confirm Email is ON in Supabase: do not activate a logged-in session without a JWT yet
                pendingVerificationUserId = user.id
            }

            AppResult.Success(user)
        }.getOrElse { err ->
            AppResult.Failure(err.message ?: "Unable to connect to Supabase Auth.")
        }
    }

    override suspend fun loginWithEmail(email: String, password: String): AppResult<User> {
        InputValidators.validateEmail(email)?.let { return AppResult.Failure(it) }
        if (password.isBlank()) return AppResult.Failure("Password is required.")

        val cleanEmail = email.trim().lowercase()
        if (backendConfig.isSupabaseConfigured && supabaseAuthApi != null && supabaseSessionManager != null) {
            return loginWithSupabaseEmail(cleanEmail, password)
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

    private suspend fun loginWithSupabaseEmail(email: String, password: String): AppResult<User> {
        val authApi = supabaseAuthApi ?: return AppResult.Failure("Supabase Auth client unavailable.")
        val sessionMgr = supabaseSessionManager ?: return AppResult.Failure("Session manager unavailable.")
        val apiKey = backendConfig.supabaseAnonKey

        return runCatching {
            val resp = authApi.signInWithPassword(
                apiKey = apiKey,
                body = SupabaseSignInRequest(email = email, password = password)
            )
            if (!resp.isSuccessful) {
                return AppResult.Failure(sessionMgr.parseError(resp, "Invalid email or password."))
            }
            val body = resp.body() ?: return AppResult.Failure("Empty authentication response.")
            completeSupabaseSignIn(body)
        }.getOrElse { err ->
            AppResult.Failure(err.message ?: "Unable to reach Supabase server.")
        }
    }

    override suspend fun loginWithGoogleIdToken(idToken: String, nonce: String?): AppResult<User> {
        if (idToken.isBlank()) {
            return AppResult.Failure("No Google ID token received. Please try again.")
        }
        val authApi = supabaseAuthApi
        val sessionMgr = supabaseSessionManager
        if (backendConfig.isSupabaseConfigured && authApi != null && sessionMgr != null) {
            val apiKey = backendConfig.supabaseAnonKey
            return runCatching {
                val resp = authApi.signInWithIdToken(
                    apiKey = apiKey,
                    body = SupabaseIdTokenRequest(
                        provider = "google",
                        idToken = idToken,
                        nonce = nonce
                    )
                )
                if (!resp.isSuccessful) {
                    return AppResult.Failure(sessionMgr.parseError(resp, "Google Sign-In with Supabase failed."))
                }
                val body = resp.body() ?: return AppResult.Failure("Empty authentication response.")
                completeSupabaseSignIn(body)
            }.getOrElse { err ->
                AppResult.Failure(err.message ?: "Unable to complete Google Sign-In.")
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
        return runCatching {
            completeSupabaseSignIn(authBody)
        }.getOrElse { err ->
            AppResult.Failure(err.message ?: "Unable to complete Google OAuth sign-in.")
        }
    }

    private suspend fun completeSupabaseSignIn(authBody: SupabaseAuthResponse): AppResult<User> {
        val sessionMgr = supabaseSessionManager ?: return AppResult.Failure("Session manager unavailable.")
        val postgrest = supabasePostgrestApi ?: return AppResult.Failure("PostgREST unavailable.")
        val authUser = authBody.resolvedUser() ?: return AppResult.Failure("Missing user info from Supabase.")
        val apiKey = backendConfig.supabaseAnonKey

        sessionMgr.saveSession(authBody)
        val bearer = authBody.accessToken?.let { "Bearer $it" }
            ?: sessionMgr.getValidBearerToken()
            ?: return AppResult.Failure("Missing access token in Supabase response.")

        val userResp = postgrest.fetchUserById(apiKey, bearer, "eq.${authUser.id}")
        val remoteDto = if (userResp.isSuccessful) userResp.body()?.firstOrNull() else null
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
            postgrest.upsertUser(apiKey, bearer, created.toSupabaseDto())
            created
        }

        if (entity.isSuspended) {
            sessionMgr.clearSession()
            return AppResult.Failure("This account has been suspended due to policy violations.")
        }

        userDao.upsertUser(entity)
        val prefsResp = postgrest.fetchPreferences(apiKey, bearer, "eq.${entity.id}")
        val remotePrefs = if (prefsResp.isSuccessful) prefsResp.body()?.firstOrNull()?.toEntity() else null
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
            postgrest.upsertPreferences(apiKey, bearer, localPrefs.toSupabaseDto())
            prefsDataStore.setSelectedLocation(entity.city, entity.city)
        }

        prefsDataStore.setCurrentUserId(entity.id)
        syncManager?.refreshFromCloud(entity.city, entity.id)
        return AppResult.Success(entity.toDomain())
    }

    override suspend fun sendPasswordReset(email: String): AppResult<String> {
        InputValidators.validateEmail(email)?.let { return AppResult.Failure(it) }
        val cleanEmail = email.trim().lowercase()
        val authApi = supabaseAuthApi
        val sessionMgr = supabaseSessionManager

        if (backendConfig.isSupabaseConfigured && authApi != null && sessionMgr != null) {
            return runCatching {
                val resp = authApi.sendPasswordReset(
                    apiKey = backendConfig.supabaseAnonKey,
                    body = SupabaseRecoverRequest(cleanEmail)
                )
                if (resp.isSuccessful) {
                    AppResult.Success("Password reset link dispatched via Supabase Auth to $cleanEmail.")
                } else {
                    AppResult.Failure(sessionMgr.parseError(resp, "Unable to send password reset email."))
                }
            }.getOrElse { err ->
                AppResult.Failure(err.message ?: "Unable to reach Supabase Auth.")
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
        val authApi = supabaseAuthApi
        val sessionMgr = supabaseSessionManager

        if (backendConfig.isSupabaseConfigured && authApi != null && sessionMgr != null) {
            return runCatching {
                val apiKey = backendConfig.supabaseAnonKey
                val verifyResp = if (code.trim().length >= 4) {
                    authApi.verifyOtp(
                        apiKey = apiKey,
                        body = SupabaseVerifyOtpRequest(type = "signup", email = entity.email, token = code.trim())
                    )
                } else null

                if (verifyResp != null && verifyResp.isSuccessful) {
                    verifyResp.body()?.let { sessionMgr.saveSession(it) }
                    val updated = entity.copy(emailVerified = true, updatedAt = System.currentTimeMillis())
                    userDao.upsertUser(updated)
                    pendingVerificationUserId = null
                    prefsDataStore.setCurrentUserId(updated.id)
                    sessionMgr.getValidBearerToken()?.let { bearer ->
                        runCatching {
                            supabasePostgrestApi?.upsertUser(apiKey, bearer, updated.toSupabaseDto())
                            userDao.getPreferences(updated.id)?.let {
                                supabasePostgrestApi?.upsertPreferences(apiKey, bearer, it.toSupabaseDto())
                            }
                            if (updated.volunteerAvailable) {
                                volunteerDao.getByUserId(updated.id)?.let {
                                    supabasePostgrestApi?.upsertVolunteerProfile(apiKey, bearer, it.toSupabaseDto())
                                }
                            }
                            syncManager?.refreshFromCloud(updated.city, updated.id)
                        }
                    }
                    return AppResult.Success(updated.toDomain())
                }

                // Fallback: check whether user clicked the confirmation email link
                val bearer = sessionMgr.getValidBearerToken()
                if (bearer != null) {
                    val userCheck = authApi.getCurrentUser(apiKey, bearer)
                    if (userCheck.isSuccessful && userCheck.body()?.isEmailConfirmed == true) {
                        val updated = entity.copy(emailVerified = true, updatedAt = System.currentTimeMillis())
                        userDao.upsertUser(updated)
                        pendingVerificationUserId = null
                        prefsDataStore.setCurrentUserId(updated.id)
                        runCatching {
                            supabasePostgrestApi?.upsertUser(apiKey, bearer, updated.toSupabaseDto())
                        }
                        return AppResult.Success(updated.toDomain())
                    }
                }
                val errMsg = verifyResp?.let {
                    sessionMgr.parseError(it, "Invalid or expired verification code.")
                } ?: "Enter the verification code sent to ${entity.email} or click the confirmation link."
                AppResult.Failure(errMsg)
            }.getOrElse { err ->
                AppResult.Failure(err.message ?: "Verification failed.")
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
        val authApi = supabaseAuthApi
        val sessionMgr = supabaseSessionManager
        if (backendConfig.isSupabaseConfigured && authApi != null && sessionMgr != null) {
            runCatching {
                sessionMgr.getValidBearerToken()?.let { bearer ->
                    authApi.logout(backendConfig.supabaseAnonKey, bearer)
                }
                supabaseSdkProvider?.authOrNull?.signOut()
            }
            sessionMgr.clearSession()
        }
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
            sessionMgr.clearSession()
        }

        db.helpRequestDao().deleteByOwner(userId)
        db.helpOfferDao().deleteByVolunteer(userId)
        db.bloodDonorDao().deleteByUserId(userId)
        db.lostFoundDao().deleteByOwner(userId)
        db.communityDao().deleteByAuthor(userId)
        db.notificationDao().deleteByUser(userId)
        userDao.deleteUserById(existing.id)
        prefsDataStore.setCurrentUserId(null)
        return AppResult.Success(Unit)
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
}

@Singleton
class RoomUserRepository @Inject constructor(
    private val userDao: UserDao,
    private val prefsDataStore: PreferencesDataStore,
    private val backendConfig: BackendConfig?,
    private val syncManager: SyncManager?
) : UserRepository {

    constructor(
        userDao: UserDao,
        prefsDataStore: PreferencesDataStore
    ) : this(userDao, prefsDataStore, null, null)

    override fun observeUser(userId: String): Flow<User?> =
        userDao.observeUserById(userId).map { it?.toDomain() }

    override fun observePreferences(userId: String): Flow<UserPreferences> =
        combine(
            userDao.observePreferences(userId),
            prefsDataStore.themeMode,
            prefsDataStore.language,
            prefsDataStore.selectedCity,
            prefsDataStore.selectedArea
        ) { entity, theme, lang, city, area ->
            entity?.toDomain()?.copy(
                theme = theme,
                language = lang,
                selectedCity = city,
                selectedArea = area
            ) ?: UserPreferences(
                userId = userId,
                theme = theme,
                language = lang,
                selectedCity = city,
                selectedArea = area
            )
        }

    override suspend fun updateProfile(user: User): AppResult<User> {
        InputValidators.validateFullName(user.fullName)?.let { return AppResult.Failure(it) }
        InputValidators.validateCityAndArea(user.city, user.state)?.let { return AppResult.Failure(it) }
        val existing = userDao.getUserById(user.id)
            ?: return AppResult.Failure("User not found.")
        val updated = user.copy(
            role = runCatching { UserRole.valueOf(existing.role) }.getOrDefault(UserRole.MEMBER),
            updatedAt = System.currentTimeMillis()
        )
        userDao.upsertUser(updated.toEntity(existing.passwordHash))
        prefsDataStore.setSelectedLocation(updated.city, updated.city)
        if (backendConfig?.isOfflineLocalOnly == false) {
            syncManager?.enqueueOperation(
                entityType = "user_profile",
                entityId = updated.id,
                operationType = "UPSERT",
                payloadJson = """{"id":"${updated.id}"}""",
                version = 1
            )
        }
        return AppResult.Success(updated)
    }

    override suspend fun updatePreferences(preferences: UserPreferences): AppResult<Unit> {
        userDao.upsertPreferences(preferences.toEntity())
        prefsDataStore.setThemeMode(preferences.theme)
        prefsDataStore.setLanguage(preferences.language)
        prefsDataStore.setSelectedLocation(preferences.selectedCity, preferences.selectedArea)
        prefsDataStore.setLocationEnabled(preferences.locationEnabled)
        prefsDataStore.setNotificationsEnabled(preferences.notificationsEnabled)
        if (backendConfig?.isOfflineLocalOnly == false) {
            syncManager?.enqueueOperation(
                entityType = "user_preferences",
                entityId = preferences.userId,
                operationType = "UPSERT",
                payloadJson = """{"userId":"${preferences.userId}"}""",
                version = 1
            )
        }
        return AppResult.Success(Unit)
    }

    override suspend fun updateSelectedCityAndArea(city: String, area: String): AppResult<Unit> {
        prefsDataStore.setSelectedLocation(city, area)
        val uid = prefsDataStore.currentUserId.first()
        if (uid != null) {
            val current = userDao.getPreferences(uid) ?: UserPreferencesEntity(userId = uid)
            userDao.upsertPreferences(current.copy(selectedCity = city, selectedArea = area))
            if (backendConfig?.isOfflineLocalOnly == false) {
                syncManager?.enqueueOperation(
                    entityType = "user_preferences",
                    entityId = uid,
                    operationType = "UPSERT",
                    payloadJson = """{"userId":"$uid"}""",
                    version = 1
                )
            }
        }
        return AppResult.Success(Unit)
    }

    override suspend fun updateThemeMode(theme: AppThemeMode): AppResult<Unit> {
        prefsDataStore.setThemeMode(theme)
        return AppResult.Success(Unit)
    }

    override suspend fun updateLanguage(language: AppLanguage): AppResult<Unit> {
        prefsDataStore.setLanguage(language)
        return AppResult.Success(Unit)
    }

    override suspend fun updateLocationEnabled(enabled: Boolean): AppResult<Unit> {
        prefsDataStore.setLocationEnabled(enabled)
        return AppResult.Success(Unit)
    }

    override suspend fun updateNotificationsEnabled(enabled: Boolean): AppResult<Unit> {
        prefsDataStore.setNotificationsEnabled(enabled)
        return AppResult.Success(Unit)
    }
}
