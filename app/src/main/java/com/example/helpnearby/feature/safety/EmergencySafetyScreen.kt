package com.example.helpnearby.feature.safety

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.helpnearby.core.designsystem.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySafetyScreen(
    onBack: () -> Unit,
    onOpenBlockedUsers: () -> Unit
) {
    val context = LocalContext.current

    fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        context.startActivity(intent)
    }

    val helplines = remember {
        listOf(
            Triple("112", "National Emergency (Police / Fire / Ambulance)", "24x7 All-India"),
            Triple("108", "Emergency Medical Ambulance", "Medical / Trauma"),
            Triple("100", "Police Control Room", "Law Enforcement"),
            Triple("101", "Fire & Rescue Services", "Fire Emergency"),
            Triple("1091", "Women Helpline", "24x7 Support"),
            Triple("1098", "CHILDLINE India", "Child Protection")
        )
    }

    val safetyChecklist = remember {
        listOf(
            "Meet in well-lit, public neighborhood spots (gates, parks, community centers).",
            "Never share bank OTPs, Aadhaar photos, or personal phone numbers in public listings.",
            "Inform a trusted family member or friend before meeting an unknown neighbor.",
            "Verify Lost & Found ownership using private details before handing over items.",
            "Confirm blood donor compatibility and eligibility only through a licensed hospital or blood bank."
        )
    }

    val checkedStates = remember { mutableStateListOf(false, false, false, false, false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency & Safety Center") },
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
                    title = "Important Emergency Disclaimer",
                    body = stringResource(R.string.emergency_disclaimer),
                    isUrgent = true,
                    actionLabel = "Call 112 Now",
                    onAction = { dialNumber("112") }
                )
            }

            item {
                Text("Official India Emergency Helplines", style = MaterialTheme.typography.titleMedium)
            }

            items(helplines.size) { index ->
                val (number, title, tag) = helplines[index]
                Card(
                    shape = RoundedCornerShape(14.dp),
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
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(
                            onClick = { dialNumber(number) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = BrandUrgentContainer,
                                contentColor = BrandUrgentOnContainer
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dial_$number")
                        ) {
                            Icon(Icons.Rounded.Call, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(number)
                        }
                    }
                }
            }

            item {
                Text("Personal Safety Checklist", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        safetyChecklist.forEachIndexed { idx, tip ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = checkedStates[idx],
                                    onCheckedChange = { checkedStates[idx] = it }
                                )
                                Text(tip, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onOpenBlockedUsers,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Block, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage Blocked Users & Privacy")
                }
            }
        }
    }
}
