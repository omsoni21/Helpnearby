package com.example.helpnearby.feature.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.helpnearby.core.common.DistanceUtils
import com.example.helpnearby.core.designsystem.*
import com.example.helpnearby.domain.model.HelpCategory
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpenExplore: (HelpCategory?) -> Unit,
    onOpenCreateRequest: () -> Unit,
    onOpenRequestDetail: (String) -> Unit,
    onOpenVolunteers: () -> Unit,
    onOpenBloodDonors: () -> Unit,
    onOpenLostFound: () -> Unit,
    onOpenCommunity: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSafetyCenter: () -> Unit,
    isLoggedIn: Boolean = true,
    onRequireAuth: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var showLocationDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.detectNearbyLocation()
        } else {
            viewModel.onLocationPermissionDenied()
        }
    }

    fun requestOrDetectLocation() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.detectNearbyLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val greeting = remember(strings) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> strings.greetingMorning
            hour < 17 -> strings.greetingAfternoon
            else -> strings.greetingEvening
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + City Selector + Notification Bell
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting, ${state.currentUser?.fullName?.split(" ")?.firstOrNull() ?: "Neighbor"}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier
                                .clickable { showLocationDialog = true }
                                .testTag("home_city_selector")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = "Selected area",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${state.selectedArea}, ${state.selectedCity}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Change city",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                            modifier = Modifier
                                .clickable(enabled = !state.isDetectingLocation) { requestOrDetectLocation() }
                                .testTag("home_use_gps_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (state.isDetectingLocation) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.MyLocation,
                                        contentDescription = "Use current GPS location",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (state.userLat != null) "GPS Active" else "Near Me",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_notifications_btn")
                ) {
                    BadgedBox(
                        badge = {
                            if (state.unreadNotificationCount > 0) {
                                Badge {
                                    Text("${state.unreadNotificationCount}")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = strings.notificationsTitle,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        state.locationNotice?.let { notice ->
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearLocationNotice() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss location notice", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Cloud + Offline/Pending Sync Status Strip
        item {
            CloudAndSyncStatusStrip(
                isCloudConnected = state.isCloudConnected,
                isOnline = state.isOnline,
                pendingSyncCount = state.pendingSyncCount,
                onSyncClick = { viewModel.triggerSyncNow() }
            )
        }

        // Search Shortcut Bar
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenExplore(null) }
                    .testTag("home_search_bar"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.searchHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Community Hero Card + Emergency Shortcut
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(136.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_community_hero),
                            contentDescription = "HelpNearby Community Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = strings.tagline,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Verified community assistance in ${state.selectedCity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BrandUrgentContainer.copy(alpha = 0.65f))
                            .clickable(onClick = onOpenSafetyCenter)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("home_emergency_shortcut"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Emergency,
                                contentDescription = strings.emergencyBannerTitle,
                                tint = BrandUrgent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = strings.emergencyBannerTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = BrandUrgentOnContainer
                                )
                                Text(
                                    text = strings.emergencyBannerSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandUrgentOnContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = strings.call112,
                            tint = BrandUrgentOnContainer
                        )
                    }
                }
            }
        }

        // Quick Action Buttons
        item {
            Text(
                text = strings.quickActionsTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    icon = Icons.Rounded.AddCircle,
                    label = "Ask Help",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onOpenCreateRequest,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_ask_help")
                )
                QuickActionTile(
                    icon = Icons.Rounded.VolunteerActivism,
                    label = "Volunteers",
                    color = BrandTeal,
                    onClick = onOpenVolunteers,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_volunteers")
                )
                QuickActionTile(
                    icon = Icons.Rounded.Bloodtype,
                    label = "Blood Donors",
                    color = BrandUrgent,
                    onClick = onOpenBloodDonors,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_blood_donors")
                )
                QuickActionTile(
                    icon = Icons.Rounded.FindInPage,
                    label = "Lost & Found",
                    color = BrandAmber,
                    onClick = onOpenLostFound,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_lost_found")
                )
            }
        }

        // 10 Help Categories Grid (2 rows x 5 scrollable or compact 2-column / LazyRow chips)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.categoriesTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { onOpenExplore(null) }) {
                    Text("See All")
                }
            }
            Spacer(Modifier.height(8.dp))
            val categories = HelpCategory.entries
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { category ->
                            val accent = category.accentColor()
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (category == HelpCategory.LOST_AND_FOUND) onOpenLostFound()
                                        else onOpenExplore(category)
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(accent.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = category.icon(),
                                            contentDescription = category.localizedLabel(state.language),
                                            tint = accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = category.localizedLabel(state.language),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Nearby Help Requests
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.nearbyRequestsTitle} (${state.nearbyRequests.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { onOpenExplore(null) }) {
                    Text("Browse")
                }
            }
        }

        if (state.isLoading) {
            item { SkeletonListPlaceholder(count = 2) }
        } else if (state.errorMessage != null) {
            item {
                ErrorStateView(
                    message = state.errorMessage ?: "Error",
                    onRetry = { viewModel.triggerSyncNow() }
                )
            }
        } else if (state.nearbyRequests.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    EmptyStateView(
                        icon = Icons.Rounded.LocationCity,
                        title = strings.noNearbyRequestsTitle,
                        subtitle = strings.noNearbyRequestsBody,
                        actionLabel = strings.createRequestAction,
                        onAction = onOpenCreateRequest
                    )
                }
            }
        } else {
            items(state.nearbyRequests, key = { it.id }) { req ->
                val dist = if (state.userLat != null && state.userLon != null &&
                    req.latitude != null && req.longitude != null
                ) {
                    DistanceUtils.formatApproximateDistance(
                        DistanceUtils.haversineKm(
                            state.userLat!!,
                            state.userLon!!,
                            req.latitude,
                            req.longitude
                        )
                    )
                } else null

                HelpRequestCard(
                    request = req,
                    language = state.language,
                    distanceText = dist,
                    onClick = { onOpenRequestDetail(req.id) },
                    onBookmarkToggle = {
                        if (isLoggedIn) viewModel.toggleBookmark(req.id) else onRequireAuth()
                    }
                )
            }
        }

        // Volunteer Highlights
        if (state.volunteerHighlights.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.volunteerHighlightsTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onOpenVolunteers) {
                        Text("Directory")
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.volunteerHighlights, key = { it.userId }) { vol ->
                        Card(
                            modifier = Modifier
                                .width(220.dp)
                                .clickable(onClick = onOpenVolunteers),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(38.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = vol.fullName.take(1).uppercase(),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = vol.fullName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${vol.completedCount} completed • ${vol.city}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = vol.skills.take(3).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Community Updates
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.recentCommunityTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onOpenCommunity) {
                    Text("Community Feed")
                }
            }
            if (state.recentPosts.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenCommunity),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "No community announcements in ${state.selectedCity} yet. Tap to open Community Feed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recentPosts.forEach { post ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenCommunity),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = post.category.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = formatRelativeTime(post.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(post.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = post.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Activity
        if (state.myRecentActivity.isNotEmpty()) {
            item {
                Text(
                    text = strings.recentActivityTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.myRecentActivity.forEach { act ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRequestDetail(act.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(act.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${act.approximateArea}, ${act.city}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(status = act.status)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showLocationDialog) {
        val indianCities = listOf(
            "Bengaluru" to "Indiranagar",
            "Mumbai" to "Andheri West",
            "New Delhi" to "Hauz Khas",
            "Pune" to "Kothrud",
            "Hyderabad" to "Jubilee Hills",
            "Chennai" to "Adyar",
            "Jaipur" to "Malviya Nagar"
        )
        var customCity by remember { mutableStateOf(state.selectedCity) }
        var customArea by remember { mutableStateOf(state.selectedArea) }

        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Select City & Area") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showLocationDialog = false
                            requestOrDetectLocation()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Use Current Approximate Location")
                    }
                    Text("Quick select:", style = MaterialTheme.typography.labelMedium)
                    indianCities.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (city, area) ->
                                FilterChip(
                                    selected = customCity.equals(city, ignoreCase = true),
                                    onClick = {
                                        customCity = city
                                        customArea = area
                                    },
                                    label = { Text(city, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customCity,
                        onValueChange = { customCity = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customArea,
                        onValueChange = { customArea = it },
                        label = { Text("Approximate Area / Neighborhood") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customCity.isNotBlank()) {
                            viewModel.updateCityAndArea(customCity, customArea.ifBlank { customCity })
                        }
                        showLocationDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
