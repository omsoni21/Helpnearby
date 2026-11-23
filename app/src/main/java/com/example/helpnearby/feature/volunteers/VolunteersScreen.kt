package com.example.helpnearby.feature.volunteers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.helpnearby.core.designsystem.*
import com.example.helpnearby.domain.model.VerificationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteersScreen(
    onBack: () -> Unit,
    onOpenVolunteerDetail: (String) -> Unit,
    onDiscoverOpenRequests: () -> Unit,
    isLoggedIn: Boolean = true,
    onRequireAuth: () -> Unit = {},
    viewModel: VolunteerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRegisterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Volunteer Network") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onDiscoverOpenRequests) {
                        Text("Open Requests")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            state.message?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            // My Volunteer Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.myProfile != null) "Your Volunteer Status" else "Become a Volunteer",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = state.myProfile?.let {
                                    "${it.completedCount} tasks completed • ${it.verificationStatus.label}"
                                } ?: "Share your skills and help neighbors safely.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (state.myProfile != null && isLoggedIn) {
                            Switch(
                                checked = state.myProfile!!.availability,
                                onCheckedChange = { viewModel.toggleMyAvailability(it) }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            if (isLoggedIn) showRegisterDialog = true else onRequireAuth()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("volunteer_register_btn")
                    ) {
                        Text(
                            when {
                                !isLoggedIn -> "Sign In to Register as Volunteer"
                                state.myProfile != null -> "Edit Volunteer Profile"
                                else -> "Register as Volunteer"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                placeholder = { Text("Search by name, skill, or bio...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            if (state.volunteers.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Rounded.Groups,
                    title = "No volunteers found",
                    subtitle = "Try clearing your filter or register as your neighborhood's first volunteer."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.volunteers, key = { it.userId }) { vol ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenVolunteerDetail(vol.userId) },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(44.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(vol.fullName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(vol.fullName, style = MaterialTheme.typography.titleMedium)
                                            if (vol.verificationStatus == VerificationStatus.VERIFIED_ID) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Rounded.Verified,
                                                    contentDescription = "Verified",
                                                    tint = BrandTeal,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${vol.city} • ${vol.completedCount} helped",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (vol.availability) BrandTealContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (vol.availability) "Available" else "Busy",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (vol.availability) BrandTealOnContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(vol.bio, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                if (vol.skills.isNotEmpty()) {
                                    Text(
                                        text = "Skills: ${vol.skills.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRegisterDialog) {
        var city by remember { mutableStateOf(state.myProfile?.city ?: state.currentUser?.city ?: "Bengaluru") }
        var bio by remember { mutableStateOf(state.myProfile?.bio ?: "") }
        var skills by remember { mutableStateOf(state.myProfile?.skills?.joinToString(", ") ?: "Driving, First Aid, Grocery Pickup") }
        var interests by remember { mutableStateOf(state.myProfile?.interests?.joinToString(", ") ?: "Elderly Assistance, Education") }
        var available by remember { mutableStateOf(state.myProfile?.availability ?: true) }

        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text("Volunteer Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true)
                    OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Short Biography") }, minLines = 2)
                    OutlinedTextField(value = skills, onValueChange = { skills = it }, label = { Text("Skills (comma separated)") })
                    OutlinedTextField(value = interests, onValueChange = { interests = it }, label = { Text("Interests (comma separated)") })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Currently Available")
                        Switch(checked = available, onCheckedChange = { available = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveMyVolunteerProfile(bio, skills, interests, city, available)
                        showRegisterDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerDetailScreen(
    userId: String,
    onBack: () -> Unit,
    onDiscoverOpenRequests: () -> Unit,
    viewModel: VolunteerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(userId) { viewModel.selectVolunteer(userId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Volunteer Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        val vol = state.selectedVolunteer
        if (vol == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                EmptyStateView(Icons.Rounded.PersonOff, "Profile not found", "This volunteer profile is unavailable.")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(vol.fullName, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "${vol.city} • ${vol.verificationStatus.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(vol.bio, style = MaterialTheme.typography.bodyLarge)
                        HorizontalDivider()
                        Text("Completed Assistance: ${vol.completedCount}", style = MaterialTheme.typography.titleSmall)
                        Text("Skills: ${vol.skills.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                        Text("Interests: ${vol.interests.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(
                    onClick = onDiscoverOpenRequests,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    Text("Browse Nearby Help Requests")
                }
            }
        }
    }
}
