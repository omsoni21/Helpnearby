package com.example.helpnearby

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.common.InputValidators
import com.example.helpnearby.core.database.HelpNearbyDatabase
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.core.network.NetworkMonitor
import com.example.helpnearby.core.security.SecurityGuard
import com.example.helpnearby.core.sync.SyncManager
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.data.remote.fcm.PushNotificationTransport
import com.example.helpnearby.data.repository.*
import com.example.helpnearby.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HelpNearbyCoreLogicTest {

    private lateinit var context: Context
    private lateinit var db: HelpNearbyDatabase
    private lateinit var securityGuard: SecurityGuard
    private lateinit var prefs: PreferencesDataStore
    private lateinit var backendConfig: BackendConfig
    private lateinit var syncManager: SyncManager
    private lateinit var authRepo: RoomAuthRepository
    private lateinit var helpRequestRepo: RoomHelpRequestRepository
    private lateinit var volunteerRepo: RoomVolunteerRepository
    private lateinit var donorRepo: RoomBloodDonorRepository
    private lateinit var lostFoundRepo: RoomLostFoundRepository
    private lateinit var messagingRepo: RoomMessagingRepository
    private lateinit var notificationRepo: RoomNotificationRepository
    private lateinit var safetyRepo: RoomSafetyModerationRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HelpNearbyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        securityGuard = SecurityGuard()
        prefs = PreferencesDataStore(context)
        backendConfig = BackendConfig(context)
        val networkMonitor = NetworkMonitor(context)
        syncManager = SyncManager(context, db.syncQueueDao(), networkMonitor, backendConfig)

        val pushTransport = object : PushNotificationTransport {
            override suspend fun registerDeviceTokenIfConfigured() = Result.failure<String>(IllegalStateException("Test"))
            override fun dispatchLocalSystemAlert(notification: AppNotification) {}
        }
        notificationRepo = RoomNotificationRepository(db.notificationDao(), pushTransport)
        safetyRepo = RoomSafetyModerationRepository(
            db.safetyModerationDao(),
            db.userDao(),
            db.helpRequestDao(),
            db.communityDao(),
            db.lostFoundDao(),
            securityGuard,
            backendConfig
        )
        messagingRepo = RoomMessagingRepository(
            db.messagingDao(),
            db.safetyModerationDao(),
            notificationRepo,
            securityGuard,
            syncManager,
            backendConfig
        )
        helpRequestRepo = RoomHelpRequestRepository(
            db.helpRequestDao(),
            db.helpOfferDao(),
            securityGuard,
            syncManager,
            backendConfig,
            notificationRepo,
            db.volunteerDao()
        )
        volunteerRepo = RoomVolunteerRepository(
            db.volunteerDao(),
            db.helpOfferDao(),
            db.helpRequestDao(),
            messagingRepo,
            notificationRepo,
            securityGuard,
            syncManager
        )
        donorRepo = RoomBloodDonorRepository(db.bloodDonorDao(), notificationRepo)
        lostFoundRepo = RoomLostFoundRepository(db.lostFoundDao(), notificationRepo, securityGuard)
        authRepo = RoomAuthRepository(
            db,
            db.userDao(),
            db.volunteerDao(),
            prefs,
            securityGuard,
            backendConfig
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `authentication validation and registration plus account deletion works`() = runTest {
        val badEmail = authRepo.registerWithEmail(
            "Riya Sen", "not-an-email", "Pass1234", "Pune", "Maharashtra", "India",
            AppLanguage.EN, emptyList(), false
        )
        assertTrue(badEmail is AppResult.Failure)

        val weakPass = authRepo.registerWithEmail(
            "Riya Sen", "riya@example.com", "weak", "Pune", "Maharashtra", "India",
            AppLanguage.EN, emptyList(), false
        )
        assertTrue(weakPass is AppResult.Failure)

        val ok = authRepo.registerWithEmail(
            "Riya Sen", "riya@example.com", "StrongPass9", "Pune", "Maharashtra", "India",
            AppLanguage.EN, listOf("Education"), true
        )
        assertTrue(ok is AppResult.Success)
        val user = (ok as AppResult.Success).data
        assertEquals("riya@example.com", user.email)

        val deleteResult = authRepo.deleteAccount(user.id)
        assertTrue(deleteResult is AppResult.Success)
        assertNull(db.userDao().getUserById(user.id))
    }

    @Test
    fun `help request creation prevents phone leaks and enforces owner-only edits`() = runTest {
        val phoneLeak = InputValidators.validateHelpRequest(
            title = "Need medicine pickup",
            description = "Call me at 9876543210 near the main gate",
            city = "Bengaluru",
            state = "Karnataka",
            approximateArea = "Indiranagar"
        )
        assertNotNull(phoneLeak)

        val created = helpRequestRepo.createRequest(
            HelpRequest(
                id = "req_test_1",
                ownerId = "owner_1",
                ownerName = "Owner One",
                title = "Grocery pickup for senior",
                description = "Need fresh vegetables and rice dropped at gate.",
                category = HelpCategory.FOOD_ASSISTANCE,
                city = "Bengaluru",
                state = "Karnataka",
                approximateArea = "Indiranagar",
                urgency = UrgencyLevel.MEDIUM,
                status = RequestStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L,
                expiresAt = 999999999L
            )
        )
        assertTrue(created is AppResult.Success)

        // Non-owner cannot edit or cancel
        val unauthorizedEdit = helpRequestRepo.updateRequest(
            (created as AppResult.Success).data.copy(title = "Hacked title"),
            actorId = "intruder_2"
        )
        assertTrue(unauthorizedEdit is AppResult.Failure)

        val unauthorizedCancel = helpRequestRepo.cancelOrDeleteRequest("req_test_1", "intruder_2")
        assertTrue(unauthorizedCancel is AppResult.Failure)
    }

    @Test
    fun `volunteer offer flow acceptance status transitions and participant-only chat`() = runTest {
        val owner = (authRepo.registerWithEmail(
            "Owner A", "owner@example.com", "Pass1234", "Bengaluru", "Karnataka", "India",
            AppLanguage.EN, emptyList(), false
        ) as AppResult.Success).data

        val volunteer = (authRepo.registerWithEmail(
            "Volunteer B", "vol@example.com", "Pass1234", "Bengaluru", "Karnataka", "India",
            AppLanguage.EN, emptyList(), true
        ) as AppResult.Success).data

        val req = (helpRequestRepo.createRequest(
            HelpRequest(
                id = "req_flow_1",
                ownerId = owner.id,
                ownerName = owner.fullName,
                title = "Pharmacy run",
                description = "Pick up prescription tablets near 100ft road.",
                category = HelpCategory.MEDICAL_ASSISTANCE,
                city = "Bengaluru",
                state = "Karnataka",
                approximateArea = "Indiranagar",
                urgency = UrgencyLevel.HIGH,
                status = RequestStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L,
                expiresAt = 999999999L
            )
        ) as AppResult.Success).data

        val offer = (volunteerRepo.sendHelpOffer(
            HelpOffer(
                id = "off_1",
                requestId = req.id,
                volunteerId = volunteer.id,
                volunteerName = volunteer.fullName,
                message = "I am 5 mins away and can pick it up.",
                status = OfferStatus.PENDING,
                createdAt = 2000L,
                updatedAt = 2000L
            )
        ) as AppResult.Success).data

        assertEquals(RequestStatus.OFFER_RECEIVED, helpRequestRepo.observeRequestById(req.id).first()?.status)

        val accepted = volunteerRepo.respondToOffer(offer.id, accept = true, requestOwnerId = owner.id)
        assertTrue(accepted is AppResult.Success)
        assertEquals(RequestStatus.ACCEPTED, helpRequestRepo.observeRequestById(req.id).first()?.status)

        val conv = messagingRepo.observeConversationsForUser(owner.id).first().first()
        val msgResult = messagingRepo.sendMessage(conv.id, volunteer.id, "On my way to the pharmacy!")
        assertTrue(msgResult is AppResult.Success)

        // Intruder cannot read or send messages in this private conversation
        val outsiderMessages = messagingRepo.observeMessages(conv.id, "outsider_99").first()
        assertTrue(outsiderMessages.isEmpty())
        val outsiderSend = messagingRepo.sendMessage(conv.id, "outsider_99", "Snooping")
        assertTrue(outsiderSend is AppResult.Failure)

        // Blocking prevents further messages
        safetyRepo.blockUser(owner.id, volunteer.id, volunteer.fullName)
        val blockedSend = messagingRepo.sendMessage(conv.id, volunteer.id, "Hello?")
        assertTrue(blockedSend is AppResult.Failure)

        // Completion updates request and volunteer count
        volunteerRepo.markAssistanceCompleted(offer.id, req.id, volunteer.id, "Thank you!")
        assertEquals(RequestStatus.COMPLETED, helpRequestRepo.observeRequestById(req.id).first()?.status)
        assertEquals(1, volunteerRepo.observeVolunteerProfile(volunteer.id).first()?.completedCount)
    }

    @Test
    fun `donor privacy requires explicit consent and lost-found verifies ownership`() = runTest {
        val noConsent = donorRepo.upsertDonorProfile(
            DonorProfile(
                userId = "u1",
                displayName = "Donor U.",
                bloodGroup = BloodGroup.O_POS,
                city = "Mumbai",
                state = "Maharashtra",
                available = true,
                consent = false,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        assertTrue(noConsent is AppResult.Failure)

        val post = (lostFoundRepo.createPost(
            LostFoundPost(
                id = "lf_test_1",
                ownerId = "owner_lf",
                ownerName = "Neha",
                type = LostFoundType.FOUND,
                title = "Found silver watch",
                description = "Found near park bench.",
                category = "Accessories",
                city = "Pune",
                state = "Maharashtra",
                approximateArea = "Kothrud",
                contactPreference = "In-app",
                status = LostFoundStatus.ACTIVE,
                hiddenVerificationHint = "Engraved initials R.M.",
                eventDate = "2026-11-21",
                createdAt = 1000L
            )
        ) as AppResult.Success).data

        val claim = (lostFoundRepo.submitClaim(
            LostFoundClaim(
                id = "clm_1",
                postId = post.id,
                claimantId = "claimant_1",
                claimantName = "Rahul",
                message = "Lost my watch yesterday",
                verificationAnswer = "Engraved initials R.M.",
                status = OfferStatus.PENDING,
                createdAt = 2000L
            )
        ) as AppResult.Success).data

        val approved = lostFoundRepo.respondToClaim(claim.id, "owner_lf", true)
        assertTrue(approved is AppResult.Success)
        assertEquals(LostFoundStatus.RECOVERED, lostFoundRepo.observePostById(post.id).first()?.status)
    }

    @Test
    fun `offline storage sync queue and version conflict resolution work`() = runTest {
        val opId = syncManager.enqueueOperation(
            entityType = "help_request",
            entityId = "req_offline_1",
            operationType = "CREATE",
            payloadJson = """{"id":"req_offline_1"}""",
            version = 1
        )
        assertTrue(opId.startsWith("op_"))
        assertEquals(1, db.syncQueueDao().observePendingCount().first())

        // Conflict resolution: newer remote version must NOT be overwritten by stale local version
        assertFalse(
            syncManager.shouldLocalWin(
                localVersion = 1,
                localUpdatedAt = 5000L,
                remoteVersion = 2,
                remoteUpdatedAt = 4000L
            )
        )
        assertTrue(
            syncManager.shouldLocalWin(
                localVersion = 3,
                localUpdatedAt = 5000L,
                remoteVersion = 2,
                remoteUpdatedAt = 6000L
            )
        )
    }

    @Test
    fun `creating help request notifies nearest matching volunteers`() = runTest {
        val now = System.currentTimeMillis()
        val regRes = authRepo.registerWithEmail(
            fullName = "Aarav Sharma",
            email = "aarav@example.com",
            password = "StrongPassword1",
            city = "Bengaluru",
            state = "Karnataka",
            country = "India",
            language = AppLanguage.EN,
            interests = listOf("Medical Assistance", "Elderly Support"),
            volunteerAvailable = true
        )
        assertTrue(regRes is AppResult.Success)
        val volUser = (regRes as AppResult.Success).data

        val reqRes = helpRequestRepo.createRequest(
            HelpRequest(
                id = "req_notif_test",
                ownerId = "owner_user",
                ownerName = "Pooja",
                title = "Need Medical Assistance",
                description = "Medicine pickup in Indiranagar",
                category = HelpCategory.MEDICAL_ASSISTANCE,
                urgency = UrgencyLevel.HIGH,
                city = "Bengaluru",
                state = "Karnataka",
                approximateArea = "Indiranagar",
                status = RequestStatus.OPEN,
                createdAt = now,
                updatedAt = now,
                expiresAt = now + 86_400_000L
            )
        )

        assertTrue(reqRes is AppResult.Success)
        val notifications = notificationRepo.observeNotifications(volUser.id).first()
        assertEquals(1, notifications.size)
        val notif = notifications.first()
        assertEquals(NotificationType.NEARBY_HELP_REQUEST, notif.type)
        assertTrue(notif.title.contains("Naye Help Request Nearby"))
        assertEquals("req_notif_test", notif.relatedId)
    }
}
