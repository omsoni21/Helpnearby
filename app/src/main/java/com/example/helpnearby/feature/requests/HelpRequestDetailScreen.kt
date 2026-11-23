package com.example.helpnearby.feature.requests

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
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
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.model.OfferStatus
import com.example.helpnearby.domain.model.RequestStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpRequestDetailScreen(
    requestId: String,
    onBack: () -> Unit,
    onEditRequest: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    isLoggedIn: Boolean = true,
    onRequireAuth: () -> Unit = {},
    viewModel: RequestDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(requestId) { viewModel.load(requestId) }
    LaunchedEffect(state.openConversationId) {
        state.openConversationId?.let { convId ->
            viewModel.clearOpenConversation()
            onOpenChat(convId)
        }
    }
    LaunchedEffect(state.deletedOrCancelled) {
        if (state.deletedOrCancelled) onBack()
    }

    var showOfferDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isLoggedIn) viewModel.toggleBookmark() else onRequireAuth()
                    }) {
                        Icon(
                            imageVector = if (state.request?.isBookmarked == true) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = "Save Request"
                        )
                    }
                    IconButton(onClick = {
                        if (isLoggedIn) showReportDialog = true else onRequireAuth()
                    }) {
                        Icon(Icons.Rounded.Flag, contentDescription = "Report Request")
                    }
                }
            )
        }
    ) { innerPadding ->
        val req = state.request
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                SkeletonListPlaceholder(count = 2)
            }
        } else if (req == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                EmptyStateView(
                    icon = Icons.Rounded.SearchOff,
                    title = "Request unavailable",
                    subtitle = "This help request may have been cancelled or removed.",
                    actionLabel = "Go Back",
                    onAction = onBack
                )
            }
        } else {
            val isOwner = state.currentUser?.id == req.ownerId
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                state.feedbackMessage?.let { msg ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                state.errorMessage?.let { err ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                UrgencyPill(urgency = req.urgency, language = AppLanguage.EN)
                                StatusPill(status = req.status)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = formatRelativeTime(req.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(req.title, style = MaterialTheme.typography.headlineSmall)
                            Text(req.description, style = MaterialTheme.typography.bodyLarge)

                            req.photoUrl?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = req.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            }

                            HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Person, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Posted by ${req.ownerName}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${req.approximateArea}, ${req.city}, ${req.state} (exact address kept private)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Owner Controls vs Volunteer Offer Button
                item {
                    if (isOwner) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onEditRequest(req.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("edit_request_btn")
                            ) {
                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit")
                            }
                            OutlinedButton(
                                onClick = { showCancelConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("cancel_request_btn")
                            ) {
                                Icon(Icons.Rounded.Cancel, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cancel")
                            }
                        }
                    } else if (req.status == RequestStatus.OPEN || req.status == RequestStatus.OFFER_RECEIVED) {
                        Button(
                            onClick = {
                                if (isLoggedIn) showOfferDialog = true else onRequireAuth()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("send_offer_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.VolunteerActivism, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isLoggedIn) "Offer to Help" else "Sign In to Offer Help")
                        }
                    }
                }

                // Activity & Offers
                item {
                    Text(
                        text = "Help Offers & Activity (${state.offers.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (state.offers.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No volunteer offers yet. When a nearby volunteer responds, it will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(state.offers, key = { it.id }) { offer ->
                        val isAcceptedParticipant = offer.status == OfferStatus.ACCEPTED &&
                            (isOwner || state.currentUser?.id == offer.volunteerId)
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(offer.volunteerName, style = MaterialTheme.typography.titleSmall)
                                    AssistChip(onClick = {}, label = { Text(offer.status.name) })
                                }
                                Text(offer.message, style = MaterialTheme.typography.bodyMedium)

                                if (isOwner && offer.status == OfferStatus.PENDING) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.respondToOffer(offer, true) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("accept_offer_${offer.id}")
                                        ) {
                                            Text("Accept")
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.respondToOffer(offer, false) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Decline")
                                        }
                                    }
                                }

                                if (isAcceptedParticipant) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.openChatWithVolunteer(offer.volunteerId, offer.volunteerName)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("open_chat_btn")
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.Chat, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Private Chat")
                                        }
                                        if (isOwner && req.status != RequestStatus.COMPLETED) {
                                            OutlinedButton(
                                                onClick = { viewModel.markCompleted(offer, "Helpful and kind!") },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Mark Completed")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOfferDialog) {
        var offerMsg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showOfferDialog = false },
            title = { Text("Offer Volunteer Help") },
            text = {
                OutlinedTextField(
                    value = offerMsg,
                    onValueChange = { offerMsg = it },
                    label = { Text("How can you assist?") },
                    placeholder = { Text("I am nearby and free at 5 PM...") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offer_message_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendVolunteerOffer(offerMsg)
                        showOfferDialog = false
                    }
                ) {
                    Text("Send Offer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOfferDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReportDialog) {
        var reason by remember { mutableStateOf("Suspicious or Unsafe Listing") }
        var details by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Help Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Details") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reportRequest(reason, details)
                        showReportDialog = false
                    }
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel Help Request?") },
            text = { Text("This marks the request cancelled so no new offers arrive.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelRequest()
                        showCancelConfirm = false
                    }
                ) {
                    Text("Cancel Request", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Keep Open") }
            }
        )
    }
}
