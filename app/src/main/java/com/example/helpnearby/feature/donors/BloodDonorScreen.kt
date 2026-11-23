package com.example.helpnearby.feature.donors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.helpnearby.core.designsystem.*
import com.example.helpnearby.domain.model.BloodGroup
import com.example.helpnearby.domain.model.DonorProfile
import com.example.helpnearby.domain.model.OfferStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodDonorScreen(
    onBack: () -> Unit,
    isLoggedIn: Boolean = true,
    onRequireAuth: () -> Unit = {},
    viewModel: BloodDonorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRegisterDialog by remember { mutableStateOf(false) }
    var contactTarget by remember { mutableStateOf<DonorProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voluntary Blood Donor Network") },
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
                    title = "Medical & Privacy Disclaimer",
                    body = stringResource(R.string.donor_disclaimer) +
                        " Phone numbers and exact home locations are never shown publicly.",
                    isUrgent = false
                )
            }

            state.message?.let { msg ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            state.error?.let { err ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(err, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            // My Donor Registration Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val mine = state.myDonorProfile
                        if (mine != null && isLoggedIn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Registered Donor • ${mine.bloodGroup.label}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${mine.city}, ${mine.state}" +
                                            (mine.lastDonationDate?.let { " • Last donated: $it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = mine.available,
                                    onCheckedChange = { viewModel.toggleAvailability(it) }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showRegisterDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Update") }
                                OutlinedButton(
                                    onClick = { viewModel.removeRegistration() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Remove") }
                            }
                        } else {
                            Text("Join Voluntary Blood Donor Directory", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Opt in only with explicit consent. Deactivate anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    if (isLoggedIn) showRegisterDialog = true else onRequireAuth()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("donor_register_btn")
                            ) {
                                Text(if (isLoggedIn) "Register as Voluntary Donor" else "Sign In to Register as Donor")
                            }
                        }
                    }
                }
            }

            // Incoming/Outgoing Private Donor Requests
            if (state.contactRequests.isNotEmpty()) {
                item {
                    Text("Private Contact Requests", style = MaterialTheme.typography.titleMedium)
                }
                items(state.contactRequests, key = { it.id }) { req ->
                    val isDonor = state.currentUser?.id == req.donorId
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isDonor) "From ${req.requesterName}" else "To Donor (${req.bloodGroup.label})",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                AssistChip(onClick = {}, label = { Text(req.status.name) })
                            }
                            Text(req.message, style = MaterialTheme.typography.bodySmall)
                            if (isDonor && req.status == OfferStatus.PENDING) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.respondToRequest(req.id, true) }, modifier = Modifier.weight(1f)) {
                                        Text("Accept")
                                    }
                                    OutlinedButton(onClick = { viewModel.respondToRequest(req.id, false) }, modifier = Modifier.weight(1f)) {
                                        Text("Decline")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search & Filter Controls
            item {
                OutlinedTextField(
                    value = state.cityQuery,
                    onValueChange = { viewModel.setCityQuery(it) },
                    label = { Text("Filter by City") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedBloodGroup == null,
                            onClick = { viewModel.setBloodGroup(null) },
                            label = { Text("All Groups") }
                        )
                    }
                    items(BloodGroup.entries) { bg ->
                        FilterChip(
                            selected = state.selectedBloodGroup == bg,
                            onClick = { viewModel.setBloodGroup(if (state.selectedBloodGroup == bg) null else bg) },
                            label = { Text(bg.label) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show available donors only", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = state.onlyAvailable,
                        onCheckedChange = { viewModel.setOnlyAvailable(it) }
                    )
                }
            }

            if (state.donors.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Rounded.Bloodtype,
                        title = "No matching voluntary donors",
                        subtitle = "Try another city or blood group filter."
                    )
                }
            } else {
                items(state.donors, key = { it.userId }) { donor ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    modifier = Modifier.size(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = BrandUrgentContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = donor.bloodGroup.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = BrandUrgentOnContainer
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(donor.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${donor.city}, ${donor.state}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (donor.available) "Available • Phone private" else "Currently unavailable",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (donor.available) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (donor.userId != state.currentUser?.id && donor.available) {
                                FilledTonalButton(
                                    onClick = {
                                        if (isLoggedIn) contactTarget = donor else onRequireAuth()
                                    },
                                    modifier = Modifier.testTag("request_donor_${donor.userId}")
                                ) {
                                    Text("Request")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRegisterDialog) {
        var selectedGroup by remember { mutableStateOf(state.myDonorProfile?.bloodGroup ?: BloodGroup.O_POS) }
        var city by remember { mutableStateOf(state.myDonorProfile?.city ?: state.currentUser?.city ?: "Bengaluru") }
        var stateName by remember { mutableStateOf(state.myDonorProfile?.state ?: state.currentUser?.state ?: "Karnataka") }
        var lastDate by remember { mutableStateOf(state.myDonorProfile?.lastDonationDate ?: "") }
        var available by remember { mutableStateOf(state.myDonorProfile?.available ?: true) }
        var consent by remember { mutableStateOf(state.myDonorProfile?.consent ?: false) }

        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text("Donor Registration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BloodGroup.entries.chunked(4).forEach { groupRow ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                groupRow.forEach { bg ->
                                    FilterChip(
                                        selected = selectedGroup == bg,
                                        onClick = { selectedGroup = bg },
                                        label = { Text(bg.label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true)
                    OutlinedTextField(value = stateName, onValueChange = { stateName = it }, label = { Text("State") }, singleLine = true)
                    OutlinedTextField(value = lastDate, onValueChange = { lastDate = it }, label = { Text("Last Donation Date (Optional, YYYY-MM-DD)") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it })
                        Text(
                            "I explicitly consent to receive private blood contact requests in-app.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Available to donate", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = available, onCheckedChange = { available = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveDonorProfile(selectedGroup, city, stateName, available, consent, lastDate)
                        showRegisterDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) { Text("Cancel") }
            }
        )
    }

    contactTarget?.let { donor ->
        var msg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { contactTarget = null },
            title = { Text("Private Donor Request (${donor.bloodGroup.label})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your request is sent privately without revealing phone numbers. Confirm medical suitability directly with the hospital.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = msg,
                        onValueChange = { msg = it },
                        label = { Text("Hospital / City Details") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendContactRequest(donor, msg)
                        contactTarget = null
                    }
                ) { Text("Send Request") }
            },
            dismissButton = {
                TextButton(onClick = { contactTarget = null }) { Text("Cancel") }
            }
        )
    }
}
