package com.example.helpnearby.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.helpnearby.core.designsystem.*
import com.example.helpnearby.domain.model.OfferStatus
import com.example.helpnearby.domain.model.RequestStatus
import com.example.helpnearby.domain.model.UserRole

@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVolunteers: () -> Unit,
    onOpenBloodDonors: () -> Unit,
    onOpenSafetyCenter: () -> Unit,
    onOpenRequestDetail: (String) -> Unit,
    onOpenAdminModeration: () -> Unit,
    isLoggedIn: Boolean = true,
    onRequireAuth: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showBlockedDialog by remember { mutableStateOf(false) }

    val completedActivities = remember(state.myRequests, state.myOffers) {
        state.myRequests.filter { it.status == RequestStatus.COMPLETED }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Profile", style = MaterialTheme.typography.headlineSmall)
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("profile_settings_btn")
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }

        if (!isLoggedIn || state.user == null) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.PersonOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Browsing as Guest", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Sign in or create an account to post requests, offer help, claim items, or chat with neighbors.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = onRequireAuth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("guest_profile_signin_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Sign In / Create Account")
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onOpenVolunteers,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.VolunteerActivism, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Volunteers")
                    }
                    OutlinedButton(
                        onClick = onOpenBloodDonors,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Bloodtype, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Blood Donors")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenSafetyCenter,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Safety Center & 112 Helplines")
                }
            }
        } else {
            // User Card
            item {
                val user = state.user
                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(60.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                if (user?.photoUrl != null) {
                                    AsyncImage(
                                        model = user.photoUrl,
                                        contentDescription = user.fullName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = user?.fullName?.take(1)?.uppercase() ?: "H",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user?.fullName ?: "Guest", style = MaterialTheme.typography.titleLarge)
                                Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${user?.city ?: ""}, ${user?.state ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            OutlinedButton(onClick = onEditProfile) {
                                Text("Edit")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = onOpenVolunteers,
                                label = {
                                    Text(
                                        state.volunteerProfile?.let { "Volunteer (${it.completedCount} helped)" }
                                            ?: "Volunteer Profile"
                                    )
                                },
                                leadingIcon = { Icon(Icons.Rounded.VolunteerActivism, null, modifier = Modifier.size(16.dp)) }
                            )
                            AssistChip(
                                onClick = onOpenBloodDonors,
                                label = {
                                    Text(state.donorProfile?.let { "Donor: ${it.bloodGroup.label}" } ?: "Blood Donor")
                                },
                                leadingIcon = { Icon(Icons.Rounded.Bloodtype, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }

            // Privacy & Safety Shortcuts
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showBlockedDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Blocked (${state.blockedUsers.size})")
                    }
                    OutlinedButton(
                        onClick = onOpenSafetyCenter,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Safety & 112")
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onOpenAdminModeration,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.AdminPanelSettings, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Admin Moderation Console (Server-Role Restricted)")
                }
            }

            // Tabs for My Requests / My Offers / Saved / Completed
            item {
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("My Requests (${state.myRequests.size})") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("My Offers (${state.myOffers.size})") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Saved (${state.savedRequests.size})") })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Completed (${completedActivities.size})") })
                }
            }

            when (selectedTab) {
            0 -> {
                if (state.myRequests.isEmpty()) {
                    item { EmptyStateView(Icons.Rounded.PostAdd, "No requests posted", "Requests you create appear here.") }
                } else {
                    items(state.myRequests, key = { it.id }) { req ->
                        HelpRequestCard(
                            request = req,
                            language = state.user?.language ?: com.example.helpnearby.domain.model.AppLanguage.EN,
                            onClick = { onOpenRequestDetail(req.id) },
                            onBookmarkToggle = {}
                        )
                    }
                }
            }
            1 -> {
                if (state.myOffers.isEmpty()) {
                    item { EmptyStateView(Icons.Rounded.VolunteerActivism, "No volunteer offers sent", "Browse requests to lend a hand.") }
                } else {
                    items(state.myOffers, key = { it.id }) { offer ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRequestDetail(offer.requestId) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Offer Status: ${offer.status.name}", style = MaterialTheme.typography.titleSmall)
                                    Text(formatRelativeTime(offer.createdAt), style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(offer.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            2 -> {
                if (state.savedRequests.isEmpty()) {
                    item { EmptyStateView(Icons.Rounded.BookmarkBorder, "No saved requests", "Bookmark requests you want to revisit.") }
                } else {
                    items(state.savedRequests, key = { it.id }) { req ->
                        HelpRequestCard(
                            request = req,
                            language = state.user?.language ?: com.example.helpnearby.domain.model.AppLanguage.EN,
                            onClick = { onOpenRequestDetail(req.id) },
                            onBookmarkToggle = {}
                        )
                    }
                }
            }
            else -> {
                if (completedActivities.isEmpty()) {
                    item { EmptyStateView(Icons.Rounded.TaskAlt, "No completed activities yet", "Completed help tasks are tracked here.") }
                } else {
                    items(completedActivities, key = { it.id }) { req ->
                        HelpRequestCard(
                            request = req,
                            language = state.user?.language ?: com.example.helpnearby.domain.model.AppLanguage.EN,
                            onClick = { onOpenRequestDetail(req.id) },
                            onBookmarkToggle = {}
                        )
                    }
                }
            }
        }
        }
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Blocked Users") },
            text = {
                if (state.blockedUsers.isEmpty()) {
                    Text("You have not blocked any users.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.blockedUsers.forEach { b ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(b.blockedUserName, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { viewModel.unblockUser(b.blockedUserId) }) { Text("Unblock") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val user = state.user

    var name by remember(user) { mutableStateOf(user?.fullName ?: "") }
    var city by remember(user) { mutableStateOf(user?.city ?: "") }
    var stateName by remember(user) { mutableStateOf(user?.state ?: "") }
    var country by remember(user) { mutableStateOf(user?.country ?: "India") }
    var photoUrl by remember(user) { mutableStateOf(user?.photoUrl) }
    var volunteerAvail by remember(user) { mutableStateOf(user?.volunteerAvailable ?: false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUrl = uri.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.message?.let { msg ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(msg, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            state.error?.let { err ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(err, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedButton(
                onClick = {
                    runCatching {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text("Update Profile Photo")
            }

            photoUrl?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "Profile photo preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(CircleShape).align(Alignment.CenterHorizontally)
                )
            }

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = stateName, onValueChange = { stateName = it }, label = { Text("State") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("Country") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Available to Volunteer")
                Switch(checked = volunteerAvail, onCheckedChange = { volunteerAvail = it })
            }

            Button(
                onClick = {
                    viewModel.updateProfile(name, city, stateName, country, photoUrl, volunteerAvail)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) {
                Text("Save Profile")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminModerationScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isServerAdmin = state.user?.role == UserRole.ADMIN

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Moderation Console") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                SafetyDisclaimerCard(
                    title = "Zero-Trust Admin Role Policy",
                    body = "Admin permissions can ONLY be assigned by a trusted backend (Firebase custom claims / Supabase service role). " +
                        "Android clients can never grant themselves admin access. Current role: ${state.user?.role?.name ?: "MEMBER"}.",
                    isUrgent = !isServerAdmin
                )
            }

            state.error?.let { err ->
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(err, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                    }
                }
            }

            item {
                Text("Reported Queue (${state.reports.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (state.reports.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Rounded.VerifiedUser,
                        title = "No pending reports",
                        subtitle = "Flagged users, community posts, blood donor complaints, and suspicious listings appear here."
                    )
                }
            } else {
                items(state.reports, key = { it.id }) { report ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${report.targetType.name} • ${report.status.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(report.reason, style = MaterialTheme.typography.titleSmall)
                            if (report.description.isNotBlank()) {
                                Text(report.description, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.resolveReport(report.id, removeContent = true, suspendAccount = false, notes = "Removed content") },
                                    enabled = isServerAdmin,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Remove") }
                                OutlinedButton(
                                    onClick = { viewModel.resolveReport(report.id, removeContent = false, suspendAccount = false, notes = "Dismissed") },
                                    enabled = isServerAdmin,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }

            item {
                Text("Moderation Audit Log (${state.auditLogs.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(state.auditLogs, key = { it.id }) { log ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${log.action} • ${log.targetId}", style = MaterialTheme.typography.labelMedium)
                        Text(log.details, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
