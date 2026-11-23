package com.example.helpnearby.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helpnearby.domain.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun HelpCategory.icon(): ImageVector = when (this) {
    HelpCategory.DAILY_ASSISTANCE -> Icons.Rounded.Handshake
    HelpCategory.MEDICAL_ASSISTANCE -> Icons.Rounded.MedicalServices
    HelpCategory.EDUCATION -> Icons.Rounded.MenuBook
    HelpCategory.TRANSPORTATION -> Icons.Rounded.DirectionsCar
    HelpCategory.FOOD_ASSISTANCE -> Icons.Rounded.Restaurant
    HelpCategory.ELDERLY_ASSISTANCE -> Icons.Rounded.Elderly
    HelpCategory.DISABILITY_ASSISTANCE -> Icons.Rounded.Accessible
    HelpCategory.LOST_AND_FOUND -> Icons.Rounded.TravelExplore
    HelpCategory.COMMUNITY_SUPPORT -> Icons.Rounded.Groups
    HelpCategory.OTHER -> Icons.AutoMirrored.Rounded.HelpOutline
}

@Composable
fun HelpCategory.accentColor(): Color = when (this) {
    HelpCategory.DAILY_ASSISTANCE -> MaterialTheme.colorScheme.primary
    HelpCategory.MEDICAL_ASSISTANCE -> BrandUrgent
    HelpCategory.EDUCATION -> Color(0xFF3B82F6)
    HelpCategory.TRANSPORTATION -> BrandAmber
    HelpCategory.FOOD_ASSISTANCE -> BrandTeal
    HelpCategory.ELDERLY_ASSISTANCE -> Color(0xFF8B5CF6)
    HelpCategory.DISABILITY_ASSISTANCE -> Color(0xFF06B6D4)
    HelpCategory.LOST_AND_FOUND -> Color(0xFFF97316)
    HelpCategory.COMMUNITY_SUPPORT -> BrandTeal
    HelpCategory.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun HelpCategory.localizedLabel(language: AppLanguage): String =
    if (language == AppLanguage.HI) labelHi else labelEn

fun UrgencyLevel.localizedLabel(language: AppLanguage): String =
    if (language == AppLanguage.HI) labelHi else labelEn

fun formatRelativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        minutes < 2 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun CloudAndSyncStatusStrip(
    isCloudConnected: Boolean,
    isOnline: Boolean,
    pendingSyncCount: Int,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloud_sync_status_strip"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) BrandTeal else BrandAmber)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isCloudConnected) "Cloud Connected" else strings.offlineBannerTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(if (isOnline) strings.onlineLabel else strings.offlineLabel)
                            if (pendingSyncCount > 0) {
                                append(" • ")
                                append(String.format(strings.pendingSyncFormat, pendingSyncCount))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (pendingSyncCount > 0) {
                TextButton(
                    onClick = onSyncClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = strings.syncNow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(strings.syncNow, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun UrgencyPill(
    urgency: UrgencyLevel,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (urgency) {
        UrgencyLevel.HIGH -> BrandUrgentContainer to BrandUrgentOnContainer
        UrgencyLevel.MEDIUM -> BrandAmberContainer to BrandAmberOnContainer
        UrgencyLevel.LOW -> BrandTealContainer to BrandTealOnContainer
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f))
    ) {
        Text(
            text = urgency.localizedLabel(language),
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun StatusPill(
    status: RequestStatus,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (status) {
        RequestStatus.OPEN -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        RequestStatus.OFFER_RECEIVED -> BrandAmberContainer to BrandAmberOnContainer
        RequestStatus.ACCEPTED, RequestStatus.IN_PROGRESS -> BrandTealContainer to BrandTealOnContainer
        RequestStatus.COMPLETED -> Color(0xFFDCFCE7) to Color(0xFF14532D)
        RequestStatus.CANCELLED, RequestStatus.EXPIRED, RequestStatus.UNDER_REVIEW ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.2f))
    ) {
        Text(
            text = status.labelEn,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun HelpRequestCard(
    request: HelpRequest,
    language: AppLanguage,
    distanceText: String? = null,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val catColor = request.category.accentColor()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("help_request_card_${request.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(catColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = request.category.icon(),
                            contentDescription = request.category.localizedLabel(language),
                            tint = catColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = request.category.localizedLabel(language),
                            style = MaterialTheme.typography.labelMedium,
                            color = catColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${request.approximateArea}, ${request.city}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onBookmarkToggle,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bookmark_btn_${request.id}")
                ) {
                    Icon(
                        imageVector = if (request.isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = "Save request",
                        tint = if (request.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = request.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UrgencyPill(urgency = request.urgency, language = language)
                StatusPill(status = request.status)
                Spacer(Modifier.weight(1f))
                Text(
                    text = distanceText ?: formatRelativeTime(request.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    EmptyStateView(
        icon = Icons.Rounded.ErrorOutline,
        title = "Unable to load content",
        subtitle = message,
        actionLabel = strings.retry,
        onAction = onRetry,
        modifier = modifier
    )
}

@Composable
fun SkeletonListPlaceholder(
    count: Int = 3,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
            ) {}
        }
    }
}

@Composable
fun SafetyDisclaimerCard(
    title: String,
    body: String,
    isUrgent: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bg = if (isUrgent) BrandUrgentContainer.copy(alpha = 0.75f)
    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
    val fg = if (isUrgent) BrandUrgentOnContainer else MaterialTheme.colorScheme.onTertiaryContainer

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isUrgent) Icons.Rounded.Emergency else Icons.Rounded.Shield,
                    contentDescription = title,
                    tint = fg,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = fg,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = fg
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAction,
                    border = BorderStroke(1.dp, fg),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = fg),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
