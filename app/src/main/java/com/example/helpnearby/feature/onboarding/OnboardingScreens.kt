package com.example.helpnearby.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.helpnearby.core.designsystem.BrandIndigo
import com.example.helpnearby.core.designsystem.BrandTeal
import com.example.helpnearby.domain.model.AppLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    startupState: StartupState,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "splash_scale"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1100L)
    }

    LaunchedEffect(startupState.isReady, visible) {
        if (startupState.isReady && visible) {
            delay(650L)
            when {
                startupState.isLoggedIn -> onNavigateToHome()
                startupState.onboardingCompleted -> onNavigateToAuth()
                else -> onNavigateToOnboarding()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(BrandIndigo, Color(0xFF4B3CC9), BrandTeal.copy(alpha = 0.85f))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale)
                .padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(104.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.16f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_launcher_symbol),
                    contentDescription = "HelpNearby Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(22.dp))
                )
            }
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { it / 3 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "HelpNearby",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (startupState.language == AppLanguage.HI) {
                            "साथ मिलकर, हम मदद करते हैं।"
                        } else {
                            "Together, We Help."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

private data class OnboardingPage(
    val imageRes: Int,
    val titleEn: String,
    val titleHi: String,
    val bodyEn: String,
    val bodyHi: String,
    val badgeEn: String,
    val badgeHi: String
)

@Composable
fun OnboardingScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onComplete: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                imageRes = R.drawable.img_onboard_connect,
                titleEn = "Neighborhood Help, Close to Home",
                titleHi = "पड़ोस में तुरंत सहायता",
                bodyEn = "Ask for daily essentials, medicine pickups, tutoring, or elder care from trusted neighbors in your city.",
                bodyHi = "अपने शहर में भरोसेमंद पड़ोसियों से दवा लाने, किराना, पढ़ाई या बुजुर्गों की देखभाल में मदद पाएं।",
                badgeEn = "Community First",
                badgeHi = "समुदाय प्रथम"
            ),
            OnboardingPage(
                imageRes = R.drawable.img_onboard_volunteer,
                titleEn = "Volunteer, Donate Blood & Recover Items",
                titleHi = "स्वयंसेवा, रक्तदान और खोया-पाया",
                bodyEn = "Offer your skills nearby, join the voluntary blood donor network with strict privacy, or reunite lost belongings.",
                bodyHi = "आस-पास स्वयंसेवा करें, गोपनीयता के साथ रक्तदाता नेटवर्क से जुड़ें या खोई वस्तुएं वापस लौटाएं।",
                badgeEn = "Works Offline Too",
                badgeHi = "ऑफ़लाइन भी काम करे"
            ),
            OnboardingPage(
                imageRes = R.drawable.img_onboard_safety,
                titleEn = "Privacy & Safety at Every Step",
                titleHi = "हर कदम पर गोपनीयता और सुरक्षा",
                bodyEn = "Exact addresses and phone numbers stay private. Chat only after accepting help, with built-in reporting and 112 safety access.",
                bodyHi = "सटीक पता और फ़ोन नंबर निजी रहते हैं। मदद स्वीकार करने के बाद ही चैट करें, 112 सुरक्षा केंद्र के साथ।",
                badgeEn = "Safe & Transparent",
                badgeHi = "सुरक्षित और पारदर्शी"
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onLanguageChange(
                            if (language == AppLanguage.EN) AppLanguage.HI else AppLanguage.EN
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_lang_toggle"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = "Switch Language",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == AppLanguage.EN) "हिन्दी" else "English")
                }

                if (!isLastPage) {
                    TextButton(
                        onClick = onComplete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("onboarding_skip_btn")
                    ) {
                        Text(if (language == AppLanguage.HI) "छोड़ें" else "Skip")
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = if (language == AppLanguage.HI) page.badgeHi else page.badgeEn,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Image(
                        painter = painterResource(id = page.imageRes),
                        contentDescription = page.titleEn,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(24.dp))
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = if (language == AppLanguage.HI) page.titleHi else page.titleEn,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (language == AppLanguage.HI) page.bodyHi else page.bodyEn,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pages.size) { index ->
                        val active = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (active) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isLastPage) {
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_next_btn"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        if (isLastPage) {
                            if (language == AppLanguage.HI) "शुरू करें" else "Get Started"
                        } else {
                            if (language == AppLanguage.HI) "आगे" else "Next"
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
