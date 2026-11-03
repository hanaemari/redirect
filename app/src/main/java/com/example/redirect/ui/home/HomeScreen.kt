package com.example.redirect.ui.home

import com.example.redirect.ui.components.AccessibilityBanner
import com.example.redirect.ui.components.InterruptionStatusCard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.redirect.data.AppData
import com.example.redirect.data.Book
import com.example.redirect.data.ReadingStatus
import com.example.redirect.data.currentBook
import com.example.redirect.data.describe
import com.example.redirect.data.destination
import com.example.redirect.data.libraryBooks
import com.example.redirect.data.minutesReadOn
import com.example.redirect.data.recentBooks
import com.example.redirect.data.trigger
import com.example.redirect.engine.RuleEngine
import com.example.redirect.ui.AppActions
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.Routes
import com.example.redirect.ui.components.AppIcon
import com.example.redirect.ui.components.BookCover
import com.example.redirect.ui.components.EmptyState
import com.example.redirect.ui.components.Pill
import com.example.redirect.ui.components.PillTone
import com.example.redirect.ui.components.ReadingProgress
import com.example.redirect.ui.components.SectionHeader
import com.example.redirect.ui.components.SoftCard
import com.example.redirect.ui.label
import com.example.redirect.ui.percent
import java.time.LocalDate
import java.time.LocalTime

private val encouragements = listOf(
    "A few pages is a lovely way to spend a few minutes.",
    "Your book is right where you left it.",
    "Small moments add up to whole chapters.",
    "Nothing to catch up on. Just something to enjoy.",
    "Read a little, look up, read a little more.",
    "The best part of a quiet minute is what you fill it with.",
    "One page at a time is still reading.",
)

@Composable
fun HomeScreen(vm: AppViewModel, data: AppData, actions: AppActions) {
    val importing by vm.importing.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importEpub(uri)
    }
    val launchImport = { picker.launch(arrayOf("application/epub+zip", "application/octet-stream")) }
    val current = data.currentBook
    val library = data.libraryBooks

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // One item: if the banner were its own item, appearing while the list is open would insert it
        // above the visible first item, and the list would keep that item anchored, leaving the banner off-screen.
        item {
            Column {
                AccessibilityBanner(vm, data, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
                Greeting(data)
            }
        }

        if (library.isEmpty()) {
            item {
                SoftCard(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = "Your shelf is waiting",
                        body = "Find a free classic to start with, or bring in an EPUB you already own.",
                    ) {
                        Button(onClick = actions::discover) { Text("Discover books") }
                        OutlinedButton(onClick = launchImport, enabled = !importing) { Text(if (importing) "Importing…" else "Import an EPUB") }
                    }
                }
            }
        } else if (current != null) {
            item { ContinueReadingCard(current, onContinue = { actions.read(current.id) }, onDetails = { actions.openBook(current.id) }) }
        } else {
            item { ChooseNextCard(library, actions) }
        }

        item {
            Spacer(Modifier.height(12.dp))
            InterruptionStatusCard(vm, data, Modifier.padding(horizontal = 20.dp))
        }

        data.preferences.readingIntentionMinutes?.let { goal ->
            item { IntentionCard(vm, goal, data.minutesReadOn(LocalDate.now())) }
        }

        val recent = data.recentBooks.filter { it.id != current?.id }
        val shelf = recent.ifEmpty { library.filter { it.status == ReadingStatus.WANT_TO_READ && it.id != current?.id } }
        if (shelf.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader(if (recent.isNotEmpty()) "Recently read" else "Saved for later", action = "Library", onAction = { actions.tab(Routes.LIBRARY) })
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(shelf.take(10), key = { it.id }) { book ->
                        Column(Modifier.width(104.dp).clickable { actions.openBook(book.id) }) {
                            BookCover(book, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (book.progress > 0f && book.status != ReadingStatus.FINISHED) {
                                Spacer(Modifier.height(6.dp))
                                ReadingProgress(book.progress, Modifier.fillMaxWidth(), height = 3.dp)
                            } else {
                                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            RedirectionOverview(data, actions)
        }

        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(
                    Icons.Rounded.FileUpload, if (importing) "Importing…" else "Import a book", "EPUB from your device",
                    Modifier.weight(1f), enabled = !importing, loading = importing, onClick = launchImport,
                )
                QuickAction(Icons.Outlined.Explore, "Discover", "Free classics & more", Modifier.weight(1f), onClick = actions::discover)
            }
        }
    }
}

@Composable
private fun Greeting(data: AppData) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello, night owl"
    }
    val name = data.preferences.displayName.trim()
    val message = remember { encouragements[LocalDate.now().dayOfYear % encouragements.size] }
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp)) {
        Text(
            if (name.isNotEmpty()) "$greeting, $name" else greeting,
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContinueReadingCard(book: Book, onContinue: () -> Unit, onDetails: () -> Unit) {
    SoftCard(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(), onClick = onDetails) {
        Column(Modifier.padding(20.dp)) {
            Row {
                BookCover(book, Modifier.width(112.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    Text("CONTINUE READING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    ReadingProgress(book.progress, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (book.progress <= 0f) "Not started yet" else "${percent(book.progress)} read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (book.progress <= 0f) "Start reading" else "Continue reading", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ChooseNextCard(library: List<Book>, actions: AppActions) {
    val picks = library.filter { it.isReadable && it.status != ReadingStatus.FINISHED }.take(3)
    SoftCard(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("WHAT'S NEXT?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Choose a book to come back to", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Whatever you pick becomes the book your redirects open.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                picks.forEach { book ->
                    Column(Modifier.weight(1f).clickable { actions.openBook(book.id) }) {
                        BookCover(book, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(book.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - picks.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { actions.tab(Routes.LIBRARY) }, modifier = Modifier.fillMaxWidth()) { Text("Browse my library") }
        }
    }
}

@Composable
private fun IntentionCard(vm: AppViewModel, goal: Int, minutesToday: Int) {
    var editing by remember { mutableStateOf(false) }
    val done = minutesToday >= goal
    SoftCard(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(),
        onClick = { editing = true },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (done) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                else CircularProgressIndicator(
                    progress = { (minutesToday / goal.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    gapSize = 0.dp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Today's intention", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Read for $goal minutes", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                if (done) "Done" else "$minutesToday of $goal min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (editing) {
        IntentionDialog(goal, onDismiss = { editing = false }) { minutes ->
            vm.repo.updatePreferences { it.copy(readingIntentionMinutes = minutes) }
            editing = false
        }
    }
}

@Composable
fun IntentionDialog(current: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily reading intention") },
        text = {
            Column {
                Text(
                    "A gentle aim, not a streak. Nothing happens if you miss a day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20, 30).forEach { m ->
                        FilterChip(selected = selected == m, onClick = { selected = m }, label = { Text("$m min") })
                    }
                }
                Spacer(Modifier.height(4.dp))
                FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("No intention") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RedirectionOverview(data: AppData, actions: AppActions) {
    val now = System.currentTimeMillis()
    val active = data.rules.filter { RuleEngine.isActive(it, now) }
    SectionHeader("Your redirects", action = "Manage", onAction = { actions.tab(Routes.RULES) })
    SoftCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), onClick = { actions.tab(Routes.RULES) }) {
        Column(Modifier.padding(18.dp)) {
            if (active.isEmpty()) {
                Text("No active rules", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Choose an app and where you'd rather go instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                active.take(3).forEachIndexed { i, rule ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    val trigger = data.trigger(rule.triggerId)
                    val dest = rule.destinationIds.firstOrNull()?.let { data.destination(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(trigger?.packageName, trigger?.displayName ?: "?", 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(trigger?.displayName ?: "Unknown app", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                if (rule.mode == com.example.redirect.data.RedirectMode.CHOICE) "Show my options"
                                else dest?.let { data.describe(it) } ?: "No destination",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Pill(rule.mode.label(), PillTone.Muted)
                    }
                }
                if (active.size > 3) {
                    Spacer(Modifier.height(10.dp))
                    Text("+${active.size - 3} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    SoftCard(modifier, onClick = if (enabled) onClick else null) {
        Column(Modifier.padding(16.dp).widthIn(min = 100.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
