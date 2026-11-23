package com.example.helpnearby.feature.messaging

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.helpnearby.core.designsystem.EmptyStateView
import com.example.helpnearby.core.designsystem.formatRelativeTime

@Composable
fun ConversationsScreen(
    onOpenChat: (String) -> Unit,
    isLoggedIn: Boolean = true,
    onRequireAuth: () -> Unit = {},
    viewModel: ConversationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Private Messages",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Conversations open only between a request owner and an accepted volunteer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (!isLoggedIn) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text("Sign In for Private Messages", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Sign in or create an account to chat privately with neighbors and accepted volunteers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRequireAuth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("guest_messages_signin_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Sign In / Create Account")
                    }
                }
            }
        } else if (state.conversations.isEmpty()) {
            EmptyStateView(
                icon = Icons.AutoMirrored.Rounded.Chat,
                title = "No active conversations",
                subtitle = "When you accept a volunteer offer—or your offer is accepted—private request chat unlocks here."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.conversations, key = { it.id }) { conv ->
                    val otherName = if (conv.ownerId == state.currentUser?.id) conv.volunteerName else conv.ownerName
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(conv.id) },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(otherName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = conv.requestTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = conv.lastMessageText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatRelativeTime(conv.lastMessageAt),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (conv.unreadCount > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Badge { Text("${conv.unreadCount}") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(conversationId) { viewModel.load(conversationId) }

    var input by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val conv = state.conversation
                    val otherName = if (conv != null && conv.ownerId == state.currentUser?.id) {
                        conv.volunteerName
                    } else {
                        conv?.ownerName ?: "Private Chat"
                    }
                    Column {
                        Text(otherName, style = MaterialTheme.typography.titleMedium)
                        conv?.let {
                            Text(
                                text = "${it.requestTitle} (${it.status})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Safety options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Report User") },
                                leadingIcon = { Icon(Icons.Rounded.Flag, null) },
                                onClick = {
                                    viewModel.reportOtherUser("Inappropriate or unsafe chat")
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block User") },
                                leadingIcon = { Icon(Icons.Rounded.Block, null) },
                                onClick = {
                                    viewModel.blockOtherUser()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close Conversation") },
                                leadingIcon = { Icon(Icons.Rounded.Close, null) },
                                onClick = {
                                    viewModel.closeConversation()
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            state.info?.let { info ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(info, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
            }
            state.error?.let { err ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(err, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
            }

            if (state.conversation == null) {
                EmptyStateView(
                    icon = Icons.Rounded.Lock,
                    title = "Private Conversation Restricted",
                    subtitle = "Only the request owner and accepted volunteer can access this thread."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        val isMine = msg.senderId == state.currentUser?.id
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 290.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = msg.text,
                                        color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = buildString {
                                            append(formatRelativeTime(msg.createdAt))
                                            if (isMine) {
                                                append(if (msg.readAt != null) " • Read" else " • ${msg.syncStatus.name}")
                                            }
                                        },
                                        color = (if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.78f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.conversation?.status != "CLOSED") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("Type a message...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input"),
                            shape = RoundedCornerShape(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                if (input.isNotBlank()) {
                                    viewModel.sendMessage(input)
                                    input = ""
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_send_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send message")
                        }
                    }
                }
            }
        }
    }
}
