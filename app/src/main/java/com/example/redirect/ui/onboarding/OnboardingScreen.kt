package com.example.redirect.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.redirect.data.AppData
import com.example.redirect.data.RedirectStyle
import com.example.redirect.data.SeedData
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.components.AccessibilityDisclosure
import com.example.redirect.ui.components.AppIcon
import com.example.redirect.ui.components.BookCover
import com.example.redirect.ui.components.Pill
import com.example.redirect.ui.components.PillTone
import com.example.redirect.ui.label
import kotlinx.coroutines.delay

private const val PAGES = 6

@Composable
fun OnboardingScreen(vm: AppViewModel, data: AppData, onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val selectedApps = remember { mutableStateListOf<String>() }
    var style by rememberSaveable { mutableStateOf(RedirectStyle.AUTOMATIC) }
    var starter by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = page > 0) { page-- }

    fun finish() {
        vm.repo.completeOnboarding(selectedApps.toList(), style, starter)
        starter?.let { vm.repo.setCurrentBook(it) }
        onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (page in 1 until PAGES - 1) {
            PageDots(page, Modifier.padding(top = 16.dp, start = 24.dp))
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState > initialState
                (fadeIn(tween(350)) + slideInHorizontally(tween(350)) { if (forward) it / 6 else -it / 6 }) togetherWith
                    (fadeOut(tween(200)) + slideOutHorizontally(tween(250)) { if (forward) -it / 6 else it / 6 })
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { p ->
            when (p) {
                0 -> WelcomePage(onStart = { page = 1 })
                1 -> AppsPage(vm, selectedApps, onNext = { page = 2 }, onBack = { page = 0 })
                2 -> PermissionPage(vm, hasApps = selectedApps.isNotEmpty(), onNext = { page = 3 }, onBack = { page = 1 })
                3 -> StylePage(style, { style = it }, onNext = { page = 4 }, onBack = { page = 2 })
                4 -> LibraryPage(vm, data, starter, { starter = it }, onNext = { page = 5 }, onBack = { page = 3 })
                else -> DonePage(vm, selectedApps.size, style, starter?.let { id -> data.books.firstOrNull { it.id == id }?.title ?: SeedData.bundledBooks.firstOrNull { it.id == id }?.title }, onFinish = ::finish)
            }
        }
    }
}

@Composable
private fun PageDots(page: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1 until PAGES - 1) {
            val width by animateDpAsState(if (i == page) 22.dp else 6.dp, label = "dot")
            Box(
                Modifier.height(6.dp).width(width).clip(CircleShape)
                    .background(if (i <= page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@Composable
private fun StepScaffold(
    eyebrow: String,
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    onBack: (() -> Unit)?,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            if (secondary != null) TextButton(onClick = onSecondary) { Text(secondary) }
            Button(onClick = onPrimary, contentPadding = ButtonDefaults.ContentPadding) { Text(primary) }
        }
    }
}

// -- 1. Welcome -----------------------------------------------------------------

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 1..4) { delay(220); stage = i }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.8f))
        AnimatedVisibility(stage >= 1, enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 5 }) {
            FannedBooks()
        }
        Spacer(Modifier.height(44.dp))
        AnimatedVisibility(stage >= 2, enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 3 }) {
            Text(
                "Less scrolling.\nMore of what you love.",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(stage >= 3, enter = fadeIn(tween(700))) {
            Text(
                "Turn the moments you usually spend scrolling into moments spent reading, creating, playing, or doing something meaningful to you.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(stage >= 4, enter = fadeIn(tween(600))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).height(54.dp)) {
                    Text("Get started", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "No account needed. Everything stays on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun FannedBooks() {
    val books = SeedData.bundledBooks
    Box(Modifier.height(190.dp).width(260.dp), contentAlignment = Alignment.Center) {
        BookCover(books[1].title, books[1].author, books[1].tint, modifier = Modifier.width(104.dp).offset(x = (-70).dp, y = 10.dp).rotate(-9f))
        BookCover(books[3].title, books[3].author, books[3].tint, modifier = Modifier.width(104.dp).offset(x = 70.dp, y = 10.dp).rotate(9f))
        BookCover(books[0].title, books[0].author, books[0].tint, modifier = Modifier.width(118.dp))
    }
}

// -- 2. Distracting apps ----------------------------------------------------------

@Composable
private fun AppsPage(vm: AppViewModel, selected: MutableList<String>, onNext: () -> Unit, onBack: () -> Unit) {
    val options = SeedData.onboardingTriggerIds.mapNotNull { id -> SeedData.triggerCatalog.firstOrNull { it.id == id } }
    StepScaffold(
        eyebrow = "Step 1 of 4",
        title = "Which apps pull you in?",
        body = "Pick the apps you'd like a gentle nudge away from. You can change these any time.",
        primary = if (selected.isEmpty()) "Skip for now" else "Continue",
        onPrimary = onNext,
        onBack = onBack,
    ) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    val isSelected = app.id in selected
                    val installedPkg = remember(app.id) { vm.repo.installedApps.installedPackageFor(app) }
                    SelectableTile(
                        selected = isSelected,
                        onClick = { if (isSelected) selected.remove(app.id) else selected.add(app.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(installedPkg, app.displayName, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (installedPkg != null) "Installed" else "Not installed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
        NoteCard(
            "Other apps",
            "You can add any installed app later in Rules. System apps like Phone and Settings are never interrupted.",
        )
    }
}

@Composable
private fun SelectableTile(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainerLowest,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Box(Modifier.padding(14.dp)) {
            content()
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle, null, tint = scheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun NoteCard(title: String, body: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// -- 2b. Permission ---------------------------------------------------------------

@Composable
private fun PermissionPage(vm: AppViewModel, hasApps: Boolean, onNext: () -> Unit, onBack: () -> Unit) {
    val enabled by vm.serviceEnabled.collectAsStateWithLifecycle()
    StepScaffold(
        eyebrow = "Step 2 of 4",
        title = "Let Redirect notice",
        body = if (hasApps) "To step in when you open one of those apps, Redirect needs Android's Accessibility permission. Here's exactly what that means."
        else "When you pick distracting apps, Redirect needs Android's Accessibility permission to notice them. You can also do this later.",
        primary = if (enabled) "Continue" else "Skip for now",
        onPrimary = onNext,
        onBack = onBack,
    ) {
        AccessibilityDisclosure(vm)
    }
}

// -- 3. Redirect style ------------------------------------------------------------

@Composable
private fun StylePage(style: RedirectStyle, onSelect: (RedirectStyle) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    StepScaffold(
        eyebrow = "Step 3 of 4",
        title = "When you reach for one, what should happen?",
        body = "Start simple. You can fine-tune each app later in Rules.",
        primary = "Continue",
        onPrimary = onNext,
        onBack = onBack,
    ) {
        StyleOption(
            RedirectStyle.AUTOMATIC, style, onSelect, Icons.Rounded.AltRoute,
            "Go straight to my book", "Open what you're reading right away. The smoothest way to switch.", recommended = true,
        )
        Spacer(Modifier.height(12.dp))
        StyleOption(
            RedirectStyle.CHOICE, style, onSelect, Icons.Rounded.TouchApp,
            "Show me a few options", "A calm screen with your book and other alternatives, so you pick.",
        )
        Spacer(Modifier.height(12.dp))
        StyleOption(
            RedirectStyle.SCHEDULED, style, onSelect, Icons.Rounded.Schedule,
            "Depends on the day", "Go straight to reading on weekdays, show options on weekends. Adjust times later.",
        )
    }
}

@Composable
private fun StyleOption(
    value: RedirectStyle, current: RedirectStyle, onSelect: (RedirectStyle) -> Unit,
    icon: ImageVector, title: String, body: String, recommended: Boolean = false,
) {
    SelectableTile(selected = value == current, onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (recommended) {
                        Spacer(Modifier.width(8.dp))
                        Pill("Default", PillTone.Accent)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(value.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

// -- 4. Library -------------------------------------------------------------------

@Composable
private fun LibraryPage(
    vm: AppViewModel, data: AppData, starter: String?, onSelect: (String?) -> Unit, onNext: () -> Unit, onBack: () -> Unit,
) {
    val importing by vm.importing.collectAsStateWithLifecycle()
    val importedBooks = data.books.filter { it.source == com.example.redirect.data.BookSource.IMPORTED }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importEpub(uri) { onSelect(it.id) }
    }
    StepScaffold(
        eyebrow = "Step 4 of 4",
        title = "Pick something to read",
        body = "These classics are included free and ready to open. Choose one to start with, or bring your own EPUB.",
        primary = if (starter == null) "Choose later" else "Continue",
        onPrimary = onNext,
        onBack = onBack,
    ) {
        val entries = importedBooks.map { Triple(it.id, it, null) } + SeedData.bundledBooks.map { Triple(it.id, null, it) }
        entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (id, book, entry) ->
                    val isSelected = starter == id
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .let { m -> m },
                    ) {
                        Box {
                            Surface(
                                onClick = { onSelect(if (isSelected) null else id) },
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            ) {
                                Box(Modifier.padding(if (isSelected) 4.dp else 0.dp)) {
                                    if (book != null) BookCover(book, Modifier.fillMaxWidth())
                                    else BookCover(entry!!.title, entry.author, entry.tint, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            if (isSelected) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(book?.title ?: entry!!.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(
                            book?.author ?: entry!!.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(18.dp))
        }
        Surface(
            onClick = { if (!importing) picker.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (importing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.FileUpload, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(if (importing) "Importing…" else "Import an EPUB", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "DRM-free .epub files from your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Included books are public-domain texts from Project Gutenberg.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// -- 5. Done ----------------------------------------------------------------------

@Composable
private fun DonePage(vm: AppViewModel, appCount: Int, style: RedirectStyle, starterTitle: String?, onFinish: () -> Unit) {
    val serviceEnabled by vm.serviceEnabled.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(shown, enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 4 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp)) }
                Spacer(Modifier.height(24.dp))
                Text("You're all set", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                SummaryLine(
                    when (appCount) {
                        0 -> "No apps chosen yet. Add some any time in Rules."
                        1 -> "1 app will get a gentle redirect"
                        else -> "$appCount apps will get a gentle redirect"
                    }
                )
                SummaryLine("Style: ${style.label()}")
                if (appCount > 0 && !serviceEnabled) SummaryLine("Interruptions start once you enable Accessibility (Home → Enable Accessibility)")
                SummaryLine(starterTitle?.let { "Starting with $it" } ?: "Your library is ready when you are")
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).height(54.dp)) {
            Text("Go to my home", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SummaryLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}
