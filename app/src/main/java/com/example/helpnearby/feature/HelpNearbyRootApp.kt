package com.example.helpnearby.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.helpnearby.core.designsystem.EmptyStateView
import com.example.helpnearby.core.designsystem.HelpNearbyTheme
import com.example.helpnearby.core.designsystem.LocalAppStrings
import com.example.helpnearby.core.navigation.HelpNearbyRoute
import com.example.helpnearby.data.local.PreferencesDataStore
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.model.AppThemeMode
import com.example.helpnearby.domain.model.HelpCategory
import com.example.helpnearby.domain.model.LostFoundType
import com.example.helpnearby.domain.repository.AuthRepository
import com.example.helpnearby.domain.repository.MessagingRepository
import com.example.helpnearby.feature.auth.AuthScreen
import com.example.helpnearby.feature.community.CommunityFeedScreen
import com.example.helpnearby.feature.donors.BloodDonorScreen
import com.example.helpnearby.feature.explore.ExploreScreen
import com.example.helpnearby.feature.home.HomeScreen
import com.example.helpnearby.feature.lostfound.CreateLostFoundScreen
import com.example.helpnearby.feature.lostfound.LostFoundScreen
import com.example.helpnearby.feature.messaging.ChatScreen
import com.example.helpnearby.feature.messaging.ConversationsScreen
import com.example.helpnearby.feature.notifications.NotificationsScreen
import com.example.helpnearby.feature.profile.AdminModerationScreen
import com.example.helpnearby.feature.profile.EditProfileScreen
import com.example.helpnearby.feature.profile.ProfileScreen
import com.example.helpnearby.feature.requests.CreateEditHelpRequestScreen
import com.example.helpnearby.feature.requests.HelpRequestDetailScreen
import com.example.helpnearby.feature.safety.EmergencySafetyScreen
import com.example.helpnearby.feature.settings.SettingsScreen
import com.example.helpnearby.feature.volunteers.VolunteerDetailScreen
import com.example.helpnearby.feature.volunteers.VolunteersScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class RootAppChromeState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.EN,
    val unreadMessagesCount: Int = 0,
    val isLoggedIn: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RootAppViewModel @Inject constructor(
    prefsDataStore: PreferencesDataStore,
    authRepository: AuthRepository,
    messagingRepository: MessagingRepository
) : ViewModel() {
    val chromeState: StateFlow<RootAppChromeState> = combine(
        prefsDataStore.themeMode,
        prefsDataStore.language,
        authRepository.currentUser
    ) { theme, lang, user ->
        Triple(theme, lang, user)
    }.flatMapLatest { (theme, lang, user) ->
        if (user == null) {
            flowOf(RootAppChromeState(theme, lang, 0, isLoggedIn = false))
        } else {
            messagingRepository.observeTotalUnreadCount(user.id).map { unread ->
                RootAppChromeState(theme, lang, unread, isLoggedIn = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RootAppChromeState()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpNearbyRootApp(
    rootViewModel: RootAppViewModel = hiltViewModel()
) {
    val chromeState by rootViewModel.chromeState.collectAsStateWithLifecycle()

    HelpNearbyTheme(
        themeMode = chromeState.themeMode,
        language = chromeState.language
    ) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val strings = LocalAppStrings.current

        var showCreateSheet by remember { mutableStateOf(false) }
        var exploreInitialCategory by remember { mutableStateOf<HelpCategory?>(null) }

        fun openAuthScreen() {
            navController.navigate(HelpNearbyRoute.Auth) {
                launchSingleTop = true
            }
        }

        fun requireAuthOrRun(action: () -> Unit) {
            if (chromeState.isLoggedIn) {
                action()
            } else {
                openAuthScreen()
            }
        }

        val showBottomNav = currentDestination?.let { dest ->
            dest.hasRoute<HelpNearbyRoute.Home>() ||
                dest.hasRoute<HelpNearbyRoute.Explore>() ||
                dest.hasRoute<HelpNearbyRoute.Messages>() ||
                dest.hasRoute<HelpNearbyRoute.Profile>()
        } ?: false

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (showBottomNav && !useNavigationRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            NavigationBarItem(
                                selected = currentDestination?.hasRoute<HelpNearbyRoute.Home>() == true,
                                onClick = {
                                    navController.navigate(HelpNearbyRoute.Home) {
                                        popUpTo<HelpNearbyRoute.Home> { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(Icons.Rounded.Home, contentDescription = strings.navHome) },
                                label = { Text(strings.navHome) },
                                modifier = Modifier.testTag("nav_home")
                            )
                            NavigationBarItem(
                                selected = currentDestination?.hasRoute<HelpNearbyRoute.Explore>() == true,
                                onClick = {
                                    navController.navigate(HelpNearbyRoute.Explore) {
                                        popUpTo<HelpNearbyRoute.Home> { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(Icons.Rounded.Explore, contentDescription = strings.navExplore) },
                                label = { Text(strings.navExplore) },
                                modifier = Modifier.testTag("nav_explore")
                            )
                            NavigationBarItem(
                                selected = showCreateSheet,
                                onClick = { showCreateSheet = true },
                                icon = {
                                    Icon(
                                        Icons.Rounded.AddCircle,
                                        contentDescription = strings.navCreate,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                label = { Text(strings.navCreate) },
                                modifier = Modifier.testTag("nav_create")
                            )
                            NavigationBarItem(
                                selected = currentDestination?.hasRoute<HelpNearbyRoute.Messages>() == true,
                                onClick = {
                                    navController.navigate(HelpNearbyRoute.Messages) {
                                        popUpTo<HelpNearbyRoute.Home> { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (chromeState.unreadMessagesCount > 0) {
                                                Badge { Text("${chromeState.unreadMessagesCount}") }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = strings.navMessages)
                                    }
                                },
                                label = { Text(strings.navMessages) },
                                modifier = Modifier.testTag("nav_messages")
                            )
                            NavigationBarItem(
                                selected = currentDestination?.hasRoute<HelpNearbyRoute.Profile>() == true,
                                onClick = {
                                    navController.navigate(HelpNearbyRoute.Profile) {
                                        popUpTo<HelpNearbyRoute.Home> { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(Icons.Rounded.Person, contentDescription = strings.navProfile) },
                                label = { Text(strings.navProfile) },
                                modifier = Modifier.testTag("nav_profile")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showBottomNav && useNavigationRail) {
                        NavigationRail {
                            NavigationRailItem(
                                selected = currentDestination?.hasRoute<HelpNearbyRoute.Home>() == true,
                                onClick = { navController.navigate(HelpNearbyRoute.Home) { launchSingleTop = true } },
                                icon = { Icon(Icons.Rounded.Home, strings.navHome) },
                                label = { Text(strings.navHome) }
                            )
                            NavigationRailItem(
                                selected = currentDestination?.hasRoute<HelpNearbyRoute.Explore>() == true,
                                onClick = { navController.navigate(HelpNearbyRoute.Explore) { launchSingleTop = true } },
                                icon = { Icon(Icons.Rounded.Explore, strings.navExplore) },
                                label = { Text(strings.navExplore) }
                            )
                            NavigationRailItem(
                                selected = showCreateSheet,
                                onClick = { showCreateSheet = true },
                                icon = { Icon(Icons.Rounded.AddCircle, strings.navCreate) },
                                label = { Text(strings.navCreate) }
                            )
                            NavigationRailItem(
                                selected = currentDestination?.hasRoute<HelpNearbyRoute.Messages>() == true,
                                onClick = { navController.navigate(HelpNearbyRoute.Messages) { launchSingleTop = true } },
                                icon = { Icon(Icons.AutoMirrored.Rounded.Chat, strings.navMessages) },
                                label = { Text(strings.navMessages) }
                            )
                            NavigationRailItem(
                                selected = currentDestination?.hasRoute<HelpNearbyRoute.Profile>() == true,
                                onClick = { navController.navigate(HelpNearbyRoute.Profile) { launchSingleTop = true } },
                                icon = { Icon(Icons.Rounded.Person, strings.navProfile) },
                                label = { Text(strings.navProfile) }
                            )
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = HelpNearbyRoute.Home,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable<HelpNearbyRoute.Auth> {
                            AuthScreen(
                                canNavigateBack = navController.previousBackStackEntry != null,
                                onBack = { navController.popBackStack() },
                                onAuthenticated = {
                                    val popped = navController.popBackStack()
                                    if (!popped) {
                                        navController.navigate(HelpNearbyRoute.Home) {
                                            popUpTo<HelpNearbyRoute.Auth> { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable<HelpNearbyRoute.Home> {
                            HomeScreen(
                                onOpenExplore = { category ->
                                    exploreInitialCategory = category
                                    navController.navigate(HelpNearbyRoute.Explore)
                                },
                                onOpenCreateRequest = {
                                    requireAuthOrRun {
                                        navController.navigate(HelpNearbyRoute.CreateEditRequest(null))
                                    }
                                },
                                onOpenRequestDetail = { reqId ->
                                    navController.navigate(HelpNearbyRoute.RequestDetail(reqId))
                                },
                                onOpenVolunteers = { navController.navigate(HelpNearbyRoute.Volunteers) },
                                onOpenBloodDonors = { navController.navigate(HelpNearbyRoute.BloodDonors) },
                                onOpenLostFound = { navController.navigate(HelpNearbyRoute.LostAndFound) },
                                onOpenCommunity = { navController.navigate(HelpNearbyRoute.CommunityFeed) },
                                onOpenNotifications = {
                                    requireAuthOrRun {
                                        navController.navigate(HelpNearbyRoute.Notifications)
                                    }
                                },
                                onOpenSafetyCenter = { navController.navigate(HelpNearbyRoute.EmergencySafety) },
                                isLoggedIn = chromeState.isLoggedIn,
                                onRequireAuth = { openAuthScreen() }
                            )
                        }

                        composable<HelpNearbyRoute.Explore> {
                            ExploreScreen(
                                initialCategory = exploreInitialCategory,
                                onOpenRequestDetail = { reqId ->
                                    navController.navigate(HelpNearbyRoute.RequestDetail(reqId))
                                },
                                onOpenVolunteers = { navController.navigate(HelpNearbyRoute.Volunteers) },
                                onOpenBloodDonors = { navController.navigate(HelpNearbyRoute.BloodDonors) },
                                onOpenLostFound = { navController.navigate(HelpNearbyRoute.LostAndFound) },
                                onOpenCommunity = { navController.navigate(HelpNearbyRoute.CommunityFeed) },
                                isLoggedIn = chromeState.isLoggedIn,
                                onRequireAuth = { openAuthScreen() }
                            )
                        }

                        composable<HelpNearbyRoute.Messages> {
                            ConversationsScreen(
                                onOpenChat = { convId ->
                                    requireAuthOrRun {
                                        navController.navigate(HelpNearbyRoute.ChatThread(convId))
                                    }
                                },
                                isLoggedIn = chromeState.isLoggedIn,
                                onRequireAuth = { openAuthScreen() }
                            )
                        }

                        composable<HelpNearbyRoute.Profile> {
                            ProfileScreen(
                                onEditProfile = {
                                    requireAuthOrRun {
                                        navController.navigate(HelpNearbyRoute.EditProfile)
                                    }
                                },
                                onOpenSettings = { navController.navigate(HelpNearbyRoute.Settings) },
                                onOpenVolunteers = { navController.navigate(HelpNearbyRoute.Volunteers) },
                                onOpenBloodDonors = { navController.navigate(HelpNearbyRoute.BloodDonors) },
                                onOpenSafetyCenter = { navController.navigate(HelpNearbyRoute.EmergencySafety) },
                                onOpenRequestDetail = { reqId ->
                                    navController.navigate(HelpNearbyRoute.RequestDetail(reqId))
                                },
                                onOpenAdminModeration = {
                                    requireAuthOrRun {
                                        navController.navigate(HelpNearbyRoute.AdminModeration)
                                    }
                                },
                                isLoggedIn = chromeState.isLoggedIn,
                                onRequireAuth = { openAuthScreen() }
                            )
                        }

                        composable<HelpNearbyRoute.CreateEditRequest> { entry ->
                            val route = entry.toRoute<HelpNearbyRoute.CreateEditRequest>()
                            ProtectedRouteGuard(
                                isLoggedIn = chromeState.isLoggedIn,
                                title = "Sign In to Post Request",
                                onBack = { navController.popBackStack() },
                                onRequireAuth = { openAuthScreen() }
                            ) {
                                CreateEditHelpRequestScreen(
                                    requestId = route.requestId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        composable<HelpNearbyRoute.RequestDetail> { entry ->
                            val route = entry.toRoute<HelpNearbyRoute.RequestDetail>()
                            HelpRequestDetailScreen(
                                requestId = route.requestId,
                                onBack = { navController.popBackStack() },
                                onEditRequest = { reqId ->
                                    requireAuthOrRun {
                                        navController.navigate(HelpNearbyRoute.CreateEditRequest(reqId))
                                    }
                                },
                                onOpenChat = { convId ->
                                    requireAuthOrRun {
                                        navController.navigate(HelpNearbyRoute.ChatThread(convId))
                                    }
                                },
                                isLoggedIn = chromeState.isLoggedIn,
                                onRequireAuth = { openAuthScreen() }
                            )
                        }

                        composable<HelpNearbyRoute.Volunteers> {
                            VolunteersScreen(
                                onBack = { navController.popBackStack() },
                                onOpenVolunteerDetail = { userId ->
                                    navController.navigate(HelpNearbyRoute.VolunteerDetail(userId))
                                },
                                onDiscoverOpenRequests = {
                                    navController.navigate(HelpNearbyRoute.Explore)
                                },
                                isLoggedIn = chromeState.isLoggedIn,
                                onRequireAuth = { openAuthScreen() }
                            )
                        }

                        composable<HelpNearbyRoute.VolunteerDetail> { entry ->
                            val route = entry.toRoute<HelpNearbyRoute.VolunteerDetail>()
                            VolunteerDetailScreen(
                                userId = route.userId,
                                onBack = { navController.popBackStack() },
                                onDiscoverOpenRequests = { navController.navigate(HelpNearbyRoute.Explore) }
                            )
                        }

                        composable<HelpNearbyRoute.BloodDonors> {
                            BloodDonorScreen(
                                onBack = { navController.popBackStack() },
                                isLoggedIn = chromeState.isLoggedIn,
                                onRequireAuth = { openAuthScreen() }
                            )
                        }

                        composable<HelpNearbyRoute.LostAndFound> {
                            LostFoundScreen(
                                onBack = { navController.popBackStack() },
                                onCreatePost = { type ->
                                    requireAuthOrRun {
                                        navController.navigate(HelpNearbyRoute.CreateLostFound(type.name))
                                    }
                                },
                                isLoggedIn = chromeState.isLoggedIn,
                                onRequireAuth = { openAuthScreen() }
                            )
                        }

                        composable<HelpNearbyRoute.CreateLostFound> { entry ->
                            val route = entry.toRoute<HelpNearbyRoute.CreateLostFound>()
                            val type = runCatching { LostFoundType.valueOf(route.initialType) }
                                .getOrDefault(LostFoundType.LOST)
                            ProtectedRouteGuard(
                                isLoggedIn = chromeState.isLoggedIn,
                                title = "Sign In to Report Item",
                                onBack = { navController.popBackStack() },
                                onRequireAuth = { openAuthScreen() }
                            ) {
                                CreateLostFoundScreen(
                                    initialType = type,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        composable<HelpNearbyRoute.CommunityFeed> {
                            CommunityFeedScreen(
                                onBack = { navController.popBackStack() },
                                isLoggedIn = chromeState.isLoggedIn,
                                onRequireAuth = { openAuthScreen() }
                            )
                        }

                        composable<HelpNearbyRoute.ChatThread> { entry ->
                            val route = entry.toRoute<HelpNearbyRoute.ChatThread>()
                            ProtectedRouteGuard(
                                isLoggedIn = chromeState.isLoggedIn,
                                title = "Sign In for Private Chat",
                                onBack = { navController.popBackStack() },
                                onRequireAuth = { openAuthScreen() }
                            ) {
                                ChatScreen(
                                    conversationId = route.conversationId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        composable<HelpNearbyRoute.Notifications> {
                            ProtectedRouteGuard(
                                isLoggedIn = chromeState.isLoggedIn,
                                title = "Sign In for Notifications",
                                onBack = { navController.popBackStack() },
                                onRequireAuth = { openAuthScreen() }
                            ) {
                                NotificationsScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigateToRequest = { reqId ->
                                        navController.navigate(HelpNearbyRoute.RequestDetail(reqId))
                                    }
                                )
                            }
                        }

                        composable<HelpNearbyRoute.EmergencySafety> {
                            EmergencySafetyScreen(
                                onBack = { navController.popBackStack() },
                                onOpenBlockedUsers = { navController.navigate(HelpNearbyRoute.Profile) }
                            )
                        }

                        composable<HelpNearbyRoute.Settings> {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onSignedOut = {
                                    navController.navigate(HelpNearbyRoute.Home) {
                                        popUpTo<HelpNearbyRoute.Home> { inclusive = true }
                                    }
                                },
                                onRequireAuth = { openAuthScreen() }
                            )
                        }

                        composable<HelpNearbyRoute.EditProfile> {
                            ProtectedRouteGuard(
                                isLoggedIn = chromeState.isLoggedIn,
                                title = "Sign In to Edit Profile",
                                onBack = { navController.popBackStack() },
                                onRequireAuth = { openAuthScreen() }
                            ) {
                                EditProfileScreen(onBack = { navController.popBackStack() })
                            }
                        }

                        composable<HelpNearbyRoute.AdminModeration> {
                            ProtectedRouteGuard(
                                isLoggedIn = chromeState.isLoggedIn,
                                title = "Sign In Required",
                                onBack = { navController.popBackStack() },
                                onRequireAuth = { openAuthScreen() }
                            ) {
                                AdminModerationScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }

            if (showCreateSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showCreateSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (chromeState.isLoggedIn) {
                                "Contribute to HelpNearby"
                            } else {
                                "Contribute to HelpNearby (Sign in required)"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        ListItem(
                            headlineContent = { Text(strings.createRequestAction) },
                            supportingContent = { Text("Ask neighbors for daily, medical, or educational assistance") },
                            leadingContent = { Icon(Icons.Rounded.Handshake, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                showCreateSheet = false
                                requireAuthOrRun {
                                    navController.navigate(HelpNearbyRoute.CreateEditRequest(null))
                                }
                            }.testTag("sheet_create_request")
                        )
                        ListItem(
                            headlineContent = { Text(strings.offerHelpAction) },
                            supportingContent = { Text("Join the volunteer network or update your skills") },
                            leadingContent = { Icon(Icons.Rounded.VolunteerActivism, null, tint = MaterialTheme.colorScheme.secondary) },
                            modifier = Modifier.clickable {
                                showCreateSheet = false
                                requireAuthOrRun {
                                    navController.navigate(HelpNearbyRoute.Volunteers)
                                }
                            }.testTag("sheet_offer_volunteer")
                        )
                        ListItem(
                            headlineContent = { Text(strings.reportLostAction) },
                            supportingContent = { Text("Alert nearby neighbors about a misplaced item") },
                            leadingContent = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.tertiary) },
                            modifier = Modifier.clickable {
                                showCreateSheet = false
                                requireAuthOrRun {
                                    navController.navigate(HelpNearbyRoute.CreateLostFound(LostFoundType.LOST.name))
                                }
                            }.testTag("sheet_report_lost")
                        )
                        ListItem(
                            headlineContent = { Text(strings.reportFoundAction) },
                            supportingContent = { Text("Help return an item safely with private verification") },
                            leadingContent = { Icon(Icons.Rounded.FindInPage, null, tint = MaterialTheme.colorScheme.secondary) },
                            modifier = Modifier.clickable {
                                showCreateSheet = false
                                requireAuthOrRun {
                                    navController.navigate(HelpNearbyRoute.CreateLostFound(LostFoundType.FOUND.name))
                                }
                            }.testTag("sheet_report_found")
                        )
                        ListItem(
                            headlineContent = { Text(strings.createPostAction) },
                            supportingContent = { Text("Share an announcement, aid drive, or local alert") },
                            leadingContent = { Icon(Icons.Rounded.Campaign, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                showCreateSheet = false
                                requireAuthOrRun {
                                    navController.navigate(HelpNearbyRoute.CommunityFeed)
                                }
                            }.testTag("sheet_create_post")
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectedRouteGuard(
    isLoggedIn: Boolean,
    title: String,
    onBack: () -> Unit,
    onRequireAuth: () -> Unit,
    content: @Composable () -> Unit
) {
    if (isLoggedIn) {
        content()
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    icon = Icons.Rounded.LockPerson,
                    title = title,
                    subtitle = "Sign in or create a HelpNearby account to access this feature.",
                    actionLabel = "Sign In / Create Account",
                    onAction = onRequireAuth
                )
            }
        }
    }
}
