package com.example.helpnearby.feature.requests

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.helpnearby.domain.model.HelpCategory
import com.example.helpnearby.domain.model.UrgencyLevel

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditHelpRequestScreen(
    requestId: String? = null,
    onBack: () -> Unit,
    viewModel: RequestFormViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(requestId) {
        viewModel.loadForEdit(requestId)
    }

    LaunchedEffect(state.savedSuccess) {
        if (state.savedSuccess) onBack()
    }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(HelpCategory.DAILY_ASSISTANCE) }
    var city by remember { mutableStateOf("Bengaluru") }
    var stateName by remember { mutableStateOf("Karnataka") }
    var approximateArea by remember { mutableStateOf("") }
    var urgency by remember { mutableStateOf(UrgencyLevel.MEDIUM) }
    var expiryDays by remember { mutableIntStateOf(3) }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var initializedFromExisting by remember { mutableStateOf(false) }

    LaunchedEffect(state.existingRequest, state.currentUser) {
        if (!initializedFromExisting) {
            state.existingRequest?.let { req ->
                title = req.title
                description = req.description
                category = req.category
                city = req.city
                stateName = req.state
                approximateArea = req.approximateArea
                urgency = req.urgency
                photoUri = req.photoUrl
                latitude = req.latitude
                longitude = req.longitude
                initializedFromExisting = true
            } ?: state.currentUser?.let { u ->
                city = u.city
                stateName = u.state
                initializedFromExisting = true
            }
        }
    }

    LaunchedEffect(state.detectedLocation) {
        state.detectedLocation?.let { detected ->
            latitude = detected.latitude
            longitude = detected.longitude
            if (!detected.city.isNullOrBlank()) city = detected.city
            if (!detected.stateName.isNullOrBlank()) stateName = detected.stateName
            if (approximateArea.isBlank() && !detected.area.isNullOrBlank()) {
                approximateArea = detected.area
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) photoUri = uri.toString()
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.detectApproximateLocation()
        else viewModel.onLocationPermissionDenied()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (requestId == null) "Create Help Request" else "Edit Help Request") },
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
            state.errorMessage?.let { err ->
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

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Request Title") },
                placeholder = { Text("e.g., Pharmacy pickup for senior neighbor") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("req_title_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (no phone numbers or exact address)") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("req_description_input")
            )

            Text("Category", style = MaterialTheme.typography.titleSmall)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(HelpCategory.entries) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.labelEn, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("req_city_input")
                )
                OutlinedTextField(
                    value = stateName,
                    onValueChange = { stateName = it },
                    label = { Text("State") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("req_state_input")
                )
            }

            OutlinedTextField(
                value = approximateArea,
                onValueChange = { approximateArea = it },
                label = { Text("Approximate Area / Landmark") },
                placeholder = { Text("e.g., Indiranagar 12th Main (never house number)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("req_area_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.detectApproximateLocation()
                        else locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    enabled = !state.isDetectingLocation,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isDetectingLocation) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            state.isDetectingLocation -> "Locating..."
                            latitude != null -> "Approx. Location Added"
                            else -> "Add Approx. Coords"
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (photoUri != null) "Photo Attached" else "Attach Photo")
                }
            }

            state.locationNotice?.let { notice ->
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            photoUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "Attached request photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            Text("Urgency", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UrgencyLevel.entries.forEach { level ->
                    FilterChip(
                        selected = urgency == level,
                        onClick = { urgency = level },
                        label = { Text(level.labelEn) }
                    )
                }
            }

            Text("Expires In: $expiryDays days", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = expiryDays.toFloat(),
                onValueChange = { expiryDays = it.toInt() },
                valueRange = 1f..14f,
                steps = 12
            )

            Button(
                onClick = {
                    viewModel.saveRequest(
                        requestId = requestId,
                        title = title,
                        description = description,
                        category = category,
                        city = city,
                        stateName = stateName,
                        approximateArea = approximateArea,
                        latitude = latitude,
                        longitude = longitude,
                        urgency = urgency,
                        photoUrl = photoUri,
                        expiryDays = expiryDays
                    )
                },
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("req_submit_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (requestId == null) "Publish Help Request" else "Save Changes")
            }
        }
    }
}
