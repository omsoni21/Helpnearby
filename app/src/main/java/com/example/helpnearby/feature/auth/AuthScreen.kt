package com.example.helpnearby.feature.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.helpnearby.domain.model.AppLanguage
import com.example.helpnearby.domain.model.HelpCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    canNavigateBack: Boolean = true,
    onBack: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.authenticatedUser) {
        if (state.authenticatedUser != null) {
            onAuthenticated()
        }
    }

    var isRegisterTab by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }

    // Login state
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Registration + Profile setup state
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regCity by remember { mutableStateOf("Bengaluru") }
    var regState by remember { mutableStateOf("Karnataka") }
    var regCountry by remember { mutableStateOf("India") }
    var regLanguage by remember { mutableStateOf(AppLanguage.EN) }
    var regVolunteerAvailable by remember { mutableStateOf(false) }
    var regPhotoUri by remember { mutableStateOf<String?>(null) }
    val selectedInterests = remember { mutableStateListOf("Daily Assistance", "Community Support") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) regPhotoUri = uri.toString()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (canNavigateBack) {
                TopAppBar(
                    title = { Text("Account Required") },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("auth_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to browsing")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "HelpNearby",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Together, We Help.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                if (state.isSupabaseConfigured) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Connected to Supabase Cloud",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Offline local mode active. Add SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets to enable cloud sync and Google Sign-In.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                TabRow(
                    selectedTabIndex = if (isRegisterTab) 1 else 0,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = !isRegisterTab,
                        onClick = {
                            isRegisterTab = false
                            viewModel.clearMessages()
                        },
                        text = { Text("Sign In") },
                        modifier = Modifier.testTag("auth_tab_login")
                    )
                    Tab(
                        selected = isRegisterTab,
                        onClick = {
                            isRegisterTab = true
                            viewModel.clearMessages()
                        },
                        text = { Text("Create Account") },
                        modifier = Modifier.testTag("auth_tab_register")
                    )
                }

                Spacer(Modifier.height(16.dp))

                state.errorMessage?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                state.infoMessage?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (state.pendingVerificationUser != null) {
                    var verificationCode by remember { mutableStateOf("") }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Verify Your Email",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Enter the verification code sent to ${state.pendingVerificationUser?.email} or open the confirmation link in your email.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = verificationCode,
                                onValueChange = { verificationCode = it },
                                label = { Text("Verification Code") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("verify_code_input")
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.verifyEmailCode(verificationCode) },
                                    enabled = !state.isLoading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("verify_email_btn")
                                ) {
                                    if (state.isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Verify")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (state.isSupabaseConfigured) {
                                            viewModel.resetPendingVerification()
                                            isRegisterTab = false
                                        } else {
                                            viewModel.skipVerificationToHome()
                                        }
                                    },
                                    enabled = !state.isLoading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("verify_secondary_btn")
                                ) {
                                    Text(if (state.isSupabaseConfigured) "Back to Sign In" else "Verify Later")
                                }
                            }
                        }
                    }
                } else if (!isRegisterTab) {
                    OutlinedTextField(
                        value = loginEmail,
                        onValueChange = { loginEmail = it },
                        label = { Text("Email address") },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_email_input")
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showForgotDialog = true }) {
                            Text("Forgot password?")
                        }
                    }
                    Button(
                        onClick = { viewModel.loginWithEmail(loginEmail, loginPassword) },
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("login_submit_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Sign In")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.continueWithGoogle(context) },
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("google_signin_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Continue with Google")
                    }
                } else {
                    // Profile Photo Picker (Zero-permission Android Photo Picker)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            if (regPhotoUri != null) {
                                AsyncImage(
                                    model = regPhotoUri,
                                    contentDescription = "Selected profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.AddAPhoto, contentDescription = "Pick profile photo")
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Profile Photo (Optional)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Uses Android Photo Picker — no storage permission needed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = regName,
                        onValueChange = { regName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_name_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = regEmail,
                        onValueChange = { regEmail = it },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_email_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = regPassword,
                        onValueChange = { regPassword = it },
                        label = { Text("Password (8+ chars, letters & digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_password_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = regCity,
                            onValueChange = { regCity = it },
                            label = { Text("City") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("register_city_input")
                        )
                        OutlinedTextField(
                            value = regState,
                            onValueChange = { regState = it },
                            label = { Text("State") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("register_state_input")
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = regCountry,
                        onValueChange = { regCountry = it },
                        label = { Text("Country") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Preferred Language", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = regLanguage == AppLanguage.EN,
                                onClick = { regLanguage = AppLanguage.EN },
                                label = { Text("English") }
                            )
                            FilterChip(
                                selected = regLanguage == AppLanguage.HI,
                                onClick = { regLanguage = AppLanguage.HI },
                                label = { Text("हिन्दी") }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Community Interests",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HelpCategory.entries.take(6).chunked(2).forEach { rowCats ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowCats.forEach { cat ->
                                    val selected = cat.labelEn in selectedInterests
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            if (selected) selectedInterests.remove(cat.labelEn)
                                            else selectedInterests.add(cat.labelEn)
                                        },
                                        label = {
                                            Text(
                                                text = cat.labelEn,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Available to Volunteer", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Appear in your city's volunteer directory",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = regVolunteerAvailable,
                            onCheckedChange = { regVolunteerAvailable = it }
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.registerWithEmail(
                                fullName = regName,
                                email = regEmail,
                                password = regPassword,
                                photoUri = regPhotoUri,
                                city = regCity,
                                state = regState,
                                country = regCountry,
                                language = regLanguage,
                                interests = selectedInterests.toList(),
                                volunteerAvailable = regVolunteerAvailable
                            )
                        },
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("register_submit_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Create Account")
                        }
                    }
                }
            }
        }
    }

    if (showForgotDialog) {
        var resetEmail by remember { mutableStateOf(loginEmail) }
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Reset Password") },
            text = {
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it },
                    label = { Text("Registered Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendPasswordReset(resetEmail)
                        showForgotDialog = false
                    }
                ) {
                    Text("Send Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
