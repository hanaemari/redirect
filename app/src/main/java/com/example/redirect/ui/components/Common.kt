package com.example.redirect.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Grid3x3
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.redirect.data.Destination
import com.example.redirect.data.DestinationType
import com.example.redirect.ui.theme.CoverPalettes
import com.example.redirect.ui.theme.Serif

/** Rounded card with a hairline border; the basic container used everywhere. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) {
            Column(content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) {
            Column(content = content)
        }
    }
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 24.dp,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = horizontalPadding, end = (horizontalPadding - 8.dp).coerceAtLeast(0.dp), top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** Simple top bar for pushed screens. */
@Composable
fun BackBar(title: String = "", onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

enum class PillTone { Neutral, Accent, Success, Warning, Muted }

@Composable
fun Pill(text: String, tone: PillTone = PillTone.Neutral, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val extra = com.example.redirect.ui.theme.LocalExtraColors.current
    val (bg, fg) = when (tone) {
        PillTone.Neutral -> scheme.secondaryContainer to scheme.onSecondaryContainer
        PillTone.Accent -> scheme.primaryContainer to scheme.onPrimaryContainer
        PillTone.Success -> extra.successContainer to extra.success
        PillTone.Warning -> extra.warningContainer to extra.warning
        PillTone.Muted -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ReadingProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.height(height).clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** Real launcher icon when the app is installed, a calm monogram otherwise. */
@Composable
fun AppIcon(packageName: String?, label: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        packageName?.let { pkg ->
            runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()?.let { d: Drawable ->
                runCatching { d.toBitmap(128, 128).asImageBitmap() }.getOrNull()
            }
        }
    }
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
    } else {
        val palette = CoverPalettes[(label.hashCode() and 0x7fffffff) % CoverPalettes.size]
        Box(
            modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(palette.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.firstOrNull()?.uppercase() ?: "?",
                color = palette.ink,
                fontFamily = Serif,
                fontWeight = FontWeight.Medium,
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}

fun iconFor(key: String): ImageVector = when (key) {
    "book" -> Icons.AutoMirrored.Rounded.MenuBook
    "library" -> Icons.Rounded.LocalLibrary
    "choice" -> Icons.Rounded.Grid3x3
    "walk" -> Icons.Rounded.DirectionsWalk
    "journal" -> Icons.Rounded.EditNote
    "draw" -> Icons.Rounded.Brush
    "stretch" -> Icons.Rounded.SelfImprovement
    "tea" -> Icons.Rounded.Coffee
    "call" -> Icons.Rounded.Call
    "app" -> Icons.Rounded.Apps
    "story" -> Icons.Rounded.AutoStories
    else -> Icons.Rounded.Spa
}

@Composable
fun DestinationIcon(destination: Destination, size: Dp = 40.dp) {
    if (destination.type == DestinationType.EXTERNAL_APP) {
        AppIcon(destination.targetId, destination.displayName, size)
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                iconFor(destination.icon.ifEmpty { if (destination.type == DestinationType.LIBRARY) "library" else "book" }),
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
