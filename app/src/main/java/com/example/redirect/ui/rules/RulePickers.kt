package com.example.redirect.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.redirect.data.AppData
import com.example.redirect.data.PlatformSupport
import com.example.redirect.data.SeedData
import com.example.redirect.data.TriggerApp
import com.example.redirect.data.TriggerKind
import com.example.redirect.data.libraryBooks
import com.example.redirect.platform.InstalledApp
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.components.AppIcon
import com.example.redirect.ui.components.BookCover
import com.example.redirect.ui.components.Pill
import com.example.redirect.ui.components.PillTone
import com.example.redirect.ui.components.iconFor

/** Searchable list of launchable apps installed on the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppSheet(
    vm: AppViewModel,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onPick: (InstalledApp) -> Unit,
) {
    LaunchedEffect(Unit) { vm.loadInstalledApps() }
    val apps by vm.installedApps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            OutlinedTextField(
                query, { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            val list = apps
            if (list == null) {
                CircularProgressIndicator(Modifier.padding(32.dp).size(28.dp).align(Alignment.CenterHorizontally))
            } else {
                val filtered = list.filter { query.isBlank() || it.label.contains(query, true) }
                LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (filtered.isEmpty()) {
                        item { Text("No apps found", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(app) }.padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.packageName, app.label, 36.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Choose which apps count as distracting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistractingAppsSheet(vm: AppViewModel, data: AppData, onDismiss: () -> Unit) {
    var addingInstalled by remember { mutableStateOf(false) }
    if (addingInstalled) {
        InstalledAppSheet(
            vm, "Add an installed app", "It will be added to your distracting apps.",
            onDismiss = { addingInstalled = false },
        ) { app ->
            vm.repo.addCustomTrigger(app.packageName, app.label)
            addingInstalled = false
        }
        return
    }
    val apps = data.triggerApps.filter { it.kind == TriggerKind.APP }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Distracting apps", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "Rules for “Any distracting app” cover everything selected here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(apps, key = { it.id }) { app ->
                    val selected = app.id in data.selectedTriggerIds
                    val pkg = remember(app.id) { vm.repo.installedApps.installedPackageFor(app) }
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.repo.setTriggerSelected(app.id, !selected) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(pkg, app.displayName, 36.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (pkg != null) "Installed" else "Not installed on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            if (selected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(onClick = { addingInstalled = true }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add another installed app")
            }
        }
    }
}

/** Add a new alternative destination: a book, an installed app, or an offline activity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlternativeSheet(vm: AppViewModel, data: AppData, onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    var mode by remember { mutableStateOf(0) } // 0 menu, 1 book, 2 app, 3 offline
    var custom by remember { mutableStateOf("") }
    if (mode == 2) {
        InstalledAppSheet(
            vm, "Choose an app", "Games, drawing, music, journaling… anything you'd rather be doing.",
            onDismiss = { mode = 0 },
        ) { app -> onAdded(vm.repo.ensureAppDestination(app.packageName, app.label)) }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                if (mode != 0) IconButton(onClick = { mode = 0 }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                else Spacer(Modifier.width(12.dp))
                Text(
                    when (mode) { 1 -> "Choose a book"; 3 -> "Something offline"; else -> "Add an alternative" },
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            when (mode) {
                0 -> {
                    MenuRow(Icons.AutoMirrored.Rounded.MenuBook, "A specific book", "Always open the same book") { mode = 1 }
                    MenuRow(Icons.Rounded.Apps, "An app on this device", "Like a game, drawing or music app") { mode = 2 }
                    MenuRow(Icons.Rounded.Spa, "Something offline", "A walk, a stretch, a cup of tea") { mode = 3 }
                }
                1 -> {
                    val readable = data.libraryBooks.filter { it.isReadable }
                    if (readable.isEmpty()) {
                        Text("Add a readable book to your library first.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(readable, key = { it.id }) { book ->
                            Column(Modifier.width(96.dp).clickable { onAdded(vm.repo.ensureBookDestination(book)) }) {
                                BookCover(book, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                Text(book.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                3 -> {
                    SeedData.offlineActivityIdeas.forEach { (name, icon) ->
                        MenuRow(iconFor(icon), name, null) { onAdded(vm.repo.addOfflineActivity(name, icon)) }
                    }
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            custom, { custom = it },
                            placeholder = { Text("Or write your own") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onAdded(vm.repo.addOfflineActivity(custom, "spa")) }, enabled = custom.isNotBlank()) { Text("Add") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Short, honest description of whether a trigger can fire on this platform. */
@Composable
fun SupportPill(trigger: TriggerApp?) {
    when {
        trigger == null -> Pill("Unknown app", PillTone.Muted)
        trigger.support == PlatformSupport.NOT_VALIDATED -> Pill("Can't be detected", PillTone.Warning)
        else -> Pill("Detected on open", PillTone.Success)
    }
}

fun supportExplanation(trigger: TriggerApp?): String = when {
    trigger == null -> ""
    trigger.support == PlatformSupport.NOT_VALIDATED ->
        "Android doesn't let apps see which screen you're on inside another app, so ${trigger.displayName} can't be detected reliably. The rule is saved, but it won't fire."
    trigger.kind == TriggerKind.ANY_SELECTED -> "Covers every app in your distracting list."
    else -> "Redirect notices when ${trigger.displayName} opens, as long as interruptions are set up and on."
}
