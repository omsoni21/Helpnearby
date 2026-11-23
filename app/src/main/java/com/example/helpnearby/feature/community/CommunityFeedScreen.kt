package com.example.helpnearby.feature.community

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.helpnearby.core.designsystem.EmptyStateView
import com.example.helpnearby.core.designsystem.formatRelativeTime
import com.example.helpnearby.domain.model.CommunityCategory
import com.example.helpnearby.domain.model.CommunityPost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityFeedScreen(
    onBack: () -> Unit,
    openComposerImmediately: Boolean = false,
    isLoggedIn: Boolean = true,
    onRequireAuth: () -> Unit = {},
    viewModel: CommunityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(openComposerImmediately) }
    var editingPost by remember { mutableStateOf<CommunityPost?>(null) }
    var commentsPost by remember { mutableStateOf<CommunityPost?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community Feed") },
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
                    if (isLoggedIn) {
                        editingPost = null
                        showCreateDialog = true
                    } else {
                        onRequireAuth()
                    }
                },
                icon = { Icon(Icons.Rounded.Edit, null) },
                text = { Text("Post Update") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                placeholder = { Text("Search announcements or aid drives...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.setCategory(null) },
                        label = { Text("All") }
                    )
                }
                items(CommunityCategory.entries) { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick = { viewModel.setCategory(if (state.selectedCategory == cat) null else cat) },
                        label = { Text(cat.label) }
                    )
                }
            }

            state.message?.let { msg ->
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            if (state.posts.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Rounded.Forum,
                    title = "No community posts yet",
                    subtitle = "Share local neighborhood announcements, blood camps, or safety tips."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.posts, key = { it.id }) { post ->
                        val isOwner = state.currentUser?.id == post.authorId
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
                                    Column {
                                        Text(post.authorName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${post.city} • ${post.category.label} • ${formatRelativeTime(post.createdAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row {
                                        if (isOwner) {
                                            IconButton(onClick = { editingPost = post; showCreateDialog = true }) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Edit post", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = { viewModel.deletePost(post.id) }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete post", modifier = Modifier.size(18.dp))
                                            }
                                        } else {
                                            IconButton(onClick = {
                                                if (isLoggedIn) viewModel.reportPost(post.id, "Inappropriate content")
                                                else onRequireAuth()
                                            }) {
                                                Icon(Icons.Rounded.Flag, contentDescription = "Report post", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                Text(post.title, style = MaterialTheme.typography.titleMedium)
                                Text(post.content, style = MaterialTheme.typography.bodyMedium)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = {
                                        if (isLoggedIn) viewModel.toggleLike(post.id) else onRequireAuth()
                                    }) {
                                        Icon(
                                            imageVector = if (post.isLikedByMe) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = "Like",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("${post.likeCount}")
                                    }
                                    TextButton(onClick = {
                                        viewModel.loadComments(post.id)
                                        commentsPost = post
                                    }) {
                                        Icon(Icons.Rounded.Comment, contentDescription = "Comments", modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("${post.commentCount} Comments")
                                    }
                                    TextButton(onClick = {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, post.title)
                                            putExtra(Intent.EXTRA_TEXT, "${post.title}\n\n${post.content}\n\n— Shared via HelpNearby (${post.city})")
                                        }
                                        context.startActivity(Intent.createChooser(share, "Share Community Update"))
                                    }) {
                                        Icon(Icons.Rounded.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Share")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by remember(editingPost) { mutableStateOf(editingPost?.title ?: "") }
        var content by remember(editingPost) { mutableStateOf(editingPost?.content ?: "") }
        var city by remember(editingPost) { mutableStateOf(editingPost?.city ?: state.currentUser?.city ?: "Bengaluru") }
        var cat by remember(editingPost) { mutableStateOf(editingPost?.category ?: CommunityCategory.ANNOUNCEMENT) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (editingPost == null) "New Community Post" else "Edit Post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Headline") }, singleLine = true)
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Details") }, minLines = 3)
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(CommunityCategory.entries) { c ->
                            FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c.label) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.savePost(editingPost, title, content, cat, city)
                    showCreateDialog = false
                }) { Text("Publish") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    commentsPost?.let { post ->
        var commentText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { commentsPost = null },
            title = { Text("Comments • ${post.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.activeComments.isEmpty()) {
                        Text("No comments yet. Start the conversation respectfully.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        state.activeComments.forEach { c ->
                            Column {
                                Text("${c.authorName} • ${formatRelativeTime(c.createdAt)}", style = MaterialTheme.typography.labelSmall)
                                Text(c.content, style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    if (isLoggedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                placeholder = { Text("Write a comment...") },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (commentText.isNotBlank()) {
                                    viewModel.addComment(post.id, commentText)
                                    commentText = ""
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Post comment")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                commentsPost = null
                                onRequireAuth()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign In to Comment")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { commentsPost = null }) { Text("Close") }
            }
        )
    }
}
