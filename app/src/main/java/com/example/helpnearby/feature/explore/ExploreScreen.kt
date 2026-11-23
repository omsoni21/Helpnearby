package com.example.helpnearby.feature.explore

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.helpnearby.core.common.DistanceUtils
import com.example.helpnearby.core.designsystem.*
import com.example.helpnearby.domain.model.HelpCategory

@Composable
fun ExploreScreen(
    initialCategory: HelpCategory? = null,
    onOpenRequestDetail: (String) -> Unit,
    onOpenVolunteers: () -> Unit,
    onOpenBloodDonors: () -> Unit,
    onOpenLostFound: () -> Unit,
    onOpenCommunity: () -> Unit,
    isLoggedIn: Boolean = true,
    onRequireAuth: () -> Unit = {},
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(initialCategory) {
        if (initialCategory != null) {
            viewModel.setCategoryFilter(initialCategory)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.detectNearbyLocation()
        } else {
            viewModel.onLocationPermissionDenied()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Explore HelpNearby",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.setQuery(it) },
            placeholder = { Text("Search keywords, area, or category...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("explore_search_input")
        )

        Spacer(Modifier.height(10.dp))

        // Module Shortcuts Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AssistChip(
                    onClick = onOpenVolunteers,
                    label = { Text("Volunteers") },
                    leadingIcon = { Icon(Icons.Rounded.VolunteerActivism, null, modifier = Modifier.size(16.dp)) }
                )
            }
            item {
                AssistChip(
                    onClick = onOpenBloodDonors,
                    label = { Text("Blood Donors") },
                    leadingIcon = { Icon(Icons.Rounded.Bloodtype, null, modifier = Modifier.size(16.dp)) }
                )
            }
            item {
                AssistChip(
                    onClick = onOpenLostFound,
                    label = { Text("Lost & Found") },
                    leadingIcon = { Icon(Icons.Rounded.TravelExplore, null, modifier = Modifier.size(16.dp)) }
                )
            }
            item {
                AssistChip(
                    onClick = onOpenCommunity,
                    label = { Text("Community Feed") },
                    leadingIcon = { Icon(Icons.Rounded.Forum, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // City Filter & Sort Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cities = listOf<String?>(null, "Bengaluru", "Mumbai", "New Delhi", "Pune", "Hyderabad")
            items(cities) { city ->
                FilterChip(
                    selected = state.selectedCity == city,
                    onClick = { viewModel.setCityFilter(city) },
                    label = { Text(city ?: "All Cities") }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Category Filter Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.selectedCategory == null,
                    onClick = { viewModel.setCategoryFilter(null) },
                    label = { Text("All Categories") }
                )
            }
            items(HelpCategory.entries) { cat ->
                FilterChip(
                    selected = state.selectedCategory == cat,
                    onClick = {
                        viewModel.setCategoryFilter(if (state.selectedCategory == cat) null else cat)
                    },
                    label = { Text(cat.localizedLabel(state.language)) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${state.requests.size} requests",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sortMode == RequestSortMode.NEWEST,
                    onClick = { viewModel.setSortMode(RequestSortMode.NEWEST) },
                    label = { Text("Newest") }
                )
                FilterChip(
                    selected = state.sortMode == RequestSortMode.APPROXIMATE_DISTANCE,
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.detectNearbyLocation()
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    label = { Text(if (state.isDetectingLocation) "Locating..." else "Near Me") },
                    leadingIcon = {
                        if (state.isDetectingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.NearMe, null, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.testTag("explore_near_me_chip")
                )
            }
        }

        state.locationNotice?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        if (state.isLoading) {
            SkeletonListPlaceholder(count = 3)
        } else if (state.requests.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.SearchOff,
                title = "No matching help requests",
                subtitle = "Try clearing your search keyword or switching to All Cities."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.requests, key = { it.id }) { req ->
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
        }
    }
}
