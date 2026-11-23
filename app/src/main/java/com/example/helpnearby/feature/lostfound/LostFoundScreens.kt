package com.example.helpnearby.feature.lostfound

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.helpnearby.core.designsystem.EmptyStateView
import com.example.helpnearby.core.designsystem.formatRelativeTime
import com.example.helpnearby.domain.model.LostFoundPost
import com.example.helpnearby.domain.model.LostFoundStatus
import com.example.helpnearby.domain.model.LostFoundType
import com.example.helpnearby.domain.model.OfferStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostFoundScreen(
    onBack: () -> Unit,
    onCreatePost: (LostFoundType) -> Unit,
    isLoggedIn: Boolean = true,
    onRequireAuth: () -> Unit = {},
    viewModel: LostFoundViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var claimTarget by remember { mutableStateOf<LostFoundPost?>(null) }
    var inspectClaimsTarget by remember { mutableStateOf<LostFoundPost?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lost & Found") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isLoggedIn) onCreatePost(state.selectedType) else onRequireAuth()
                },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(if (state.selectedType == LostFoundType.LOST) "Report Lost" else "Report Found") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            TabRow(selectedTabIndex = if (state.selectedType == LostFoundType.LOST) 0 else 1) {
                Tab(
                    selected = state.selectedType == LostFoundType.LOST,
                    onClick = { viewModel.setType(LostFoundType.LOST) },
                    text = { Text("Lost Items") }
                )
                Tab(
                    selected = state.selectedType == LostFoundType.FOUND,
                    onClick = { viewModel.setType(LostFoundType.FOUND) },
                    text = { Text("Found Items") }
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                placeholder = { Text("Search lost or found items...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            state.message?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            if (state.posts.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Rounded.TravelExplore,
                    title = "No ${state.selectedType.name.lowercase()} reports",
                    subtitle = "Items reported by your community appear here without exposing hidden identifying details."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.posts, key = { it.id }) { post ->
                        val isOwner = state.currentUser?.id == post.ownerId
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(onClick = {}, label = { Text("${post.type.name} • ${post.status.name}") })
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(formatRelativeTime(post.createdAt), style = MaterialTheme.typography.labelSmall)
                                        IconButton(onClick = {
                                            if (isLoggedIn) viewModel.reportPost(post.id, "Suspicious listing")
                                            else onRequireAuth()
                                        }) {
                                            Icon(Icons.Rounded.Flag, contentDescription = "Report listing", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                post.imageUrl?.let { uri ->
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = post.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                Text(post.title, style = MaterialTheme.typography.titleMedium)
                                Text(post.description, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${post.approximateArea}, ${post.city} • Date: ${post.eventDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Hidden verification hint ONLY visible to owner!
                                if (isOwner && post.hiddenVerificationHint.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Private owner hint (hidden publicly): ${post.hiddenVerificationHint}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                if (isOwner) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.inspectPostClaims(post.id)
                                                inspectClaimsTarget = post
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Review Claims") }
                                        if (post.status != LostFoundStatus.RECOVERED) {
                                            Button(
                                                onClick = { viewModel.markRecovered(post.id) },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Mark Recovered") }
                                        }
                                    }
                                } else if (post.status == LostFoundStatus.ACTIVE || post.status == LostFoundStatus.CLAIM_PENDING) {
                                    Button(
                                        onClick = {
                                            if (isLoggedIn) claimTarget = post else onRequireAuth()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("claim_item_${post.id}")
                                    ) {
                                        Text(if (isLoggedIn) "Submit Private Claim" else "Sign In to Claim Item")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    claimTarget?.let { post ->
        var msg by remember { mutableStateOf("") }
        var secretAnswer by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { claimTarget = null },
            title = { Text("Verify Ownership / Claim") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Describe a private identifying detail (e.g., charm, sticker, serial code) so ${post.ownerName} can verify.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = secretAnswer, onValueChange = { secretAnswer = it }, label = { Text("Identifying Detail") })
                    OutlinedTextField(value = msg, onValueChange = { msg = it }, label = { Text("Note for Owner") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.submitClaim(post, msg, secretAnswer)
                        claimTarget = null
                    }
                ) { Text("Submit Claim") }
            },
            dismissButton = {
                TextButton(onClick = { claimTarget = null }) { Text("Cancel") }
            }
        )
    }

    inspectClaimsTarget?.let { post ->
        AlertDialog(
            onDismissRequest = { inspectClaimsTarget = null },
            title = { Text("Claims for ${post.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.selectedPostClaims.isEmpty()) {
                        Text("No claims submitted yet.")
                    } else {
                        state.selectedPostClaims.forEach { clm ->
                            Card {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${clm.claimantName} (${clm.status.name})", style = MaterialTheme.typography.titleSmall)
                                    Text("Verification answer: ${clm.verificationAnswer}", style = MaterialTheme.typography.bodySmall)
                                    if (clm.message.isNotBlank()) {
                                        Text(clm.message, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (clm.status == OfferStatus.PENDING) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { viewModel.respondToClaim(clm.id, true) }) { Text("Approve") }
                                            TextButton(onClick = { viewModel.respondToClaim(clm.id, false) }) { Text("Decline") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.closePost(post.id); inspectClaimsTarget = null }) {
                    Text("Close Listing")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectClaimsTarget = null }) { Text("Done") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateLostFoundScreen(
    initialType: LostFoundType,
    onBack: () -> Unit,
    viewModel: LostFoundViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdSuccess) {
        if (state.createdSuccess) onBack()
    }

    var type by remember { mutableStateOf(initialType) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Keys / Wallet / Phone") }
    var city by remember { mutableStateOf(state.currentUser?.city ?: "Bengaluru") }
    var stateName by remember { mutableStateOf(state.currentUser?.state ?: "Karnataka") }
    var area by remember { mutableStateOf("") }
    var hiddenHint by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf("2026-11-22") }
    var imageUrl by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) imageUrl = uri.toString() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Lost or Found Item") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.error?.let { err ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(err, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == LostFoundType.LOST, onClick = { type = LostFoundType.LOST }, label = { Text("Lost Item") })
                FilterChip(selected = type == LostFoundType.FOUND, onClick = { type = LostFoundType.FOUND }, label = { Text("Found Item") })
            }

            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Item Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Public Description (hide secret identifying details)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Item Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = stateName, onValueChange = { stateName = it }, label = { Text("State") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Approximate Area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = eventDate, onValueChange = { eventDate = it }, label = { Text("Date Lost or Found") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = hiddenHint,
                onValueChange = { hiddenHint = it },
                label = { Text("Private Verification Hint (shown only to you)") },
                placeholder = { Text("e.g., Green keychain charm or last 4 digits") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = {
                    runCatching {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, null)
                Spacer(Modifier.width(8.dp))
                Text(if (imageUrl != null) "Photo Attached" else "Attach Item Photo (Optional)")
            }

            Button(
                onClick = {
                    viewModel.createPost(type, title, description, category, city, stateName, area, imageUrl, hiddenHint, eventDate)
                },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) {
                Text("Publish Report")
            }
        }
    }
}
