package com.example.redirect.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.redirect.data.AppData
import com.example.redirect.data.Book
import com.example.redirect.data.BookSource
import com.example.redirect.data.ReadingStatus
import com.example.redirect.data.SeedData
import com.example.redirect.data.bookmarksFor
import com.example.redirect.data.currentBook
import com.example.redirect.data.toBook
import com.example.redirect.ui.AppActions
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.components.BackBar
import com.example.redirect.ui.components.BookCover
import com.example.redirect.ui.components.Pill
import com.example.redirect.ui.components.PillTone
import com.example.redirect.ui.components.ReadingProgress
import com.example.redirect.ui.components.SoftCard
import com.example.redirect.ui.label
import com.example.redirect.ui.percent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(vm: AppViewModel, data: AppData, actions: AppActions, bookId: String) {
    val stored = data.books.firstOrNull { it.id == bookId }
    val entry = remember(bookId) { SeedData.allCatalog.firstOrNull { it.id == bookId } }
    val book: Book = stored ?: entry?.toBook(0L) ?: run {
        Column(Modifier.fillMaxSize()) {
            BackBar(onBack = { actions.back() })
            Text("This book is no longer available.", Modifier.padding(24.dp))
        }
        return
    }
    val inLibrary = stored?.inLibrary == true
    val isCurrent = data.currentBook?.id == book.id
    val description = book.description.ifBlank { entry?.description.orEmpty() }

    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var showLists by remember { mutableStateOf(false) }
    var showNewList by remember { mutableStateOf(false) }
    val importing by vm.importing.collectAsStateWithLifecycle()
    val attachPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importEpub(uri, attachTo = book.id)
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack = { actions.back() }) {
            if (inLibrary) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Add to list") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                            onClick = { menu = false; showLists = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (book.source == BookSource.IMPORTED) "Delete book" else "Remove from library") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; confirmRemove = true },
                        )
                    }
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            BookCover(book, Modifier.width(168.dp))
            Spacer(Modifier.height(24.dp))
            Text(book.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(book.author, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    book.source == BookSource.BUNDLED -> Pill("Included · Public domain", PillTone.Success)
                    book.source == BookSource.IMPORTED -> Pill("Imported EPUB", PillTone.Neutral)
                    book.source == BookSource.CATALOG_SAMPLE -> Pill("Sample listing · text not available", PillTone.Muted)
                    book.source == BookSource.DOWNLOADED -> Pill("Project Gutenberg · Public domain", PillTone.Success)
                }
                if (isCurrent) Pill("Current book", PillTone.Accent, icon = Icons.Rounded.Star)
            }

            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                if (inLibrary && book.isReadable && book.progress > 0f) {
                    Spacer(Modifier.height(24.dp))
                    ReadingProgress(book.progress, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("${percent(book.progress)} read", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))

                // Primary action
                when {
                    book.isReadable -> Button(
                        onClick = { actions.read(book.id) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(if (book.progress > 0f) "Continue reading" else "Start reading", style = MaterialTheme.typography.titleMedium)
                    }
                    !inLibrary -> Button(
                        onClick = {
                            entry?.let { vm.repo.addCatalogBook(it) }
                            vm.toast("Saved to Want to Read")
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Save to Want to Read", style = MaterialTheme.typography.titleMedium) }
                    else -> SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Not readable here yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "This is a sample listing, so there's no text to open. If you own a DRM-free EPUB of this book, attach it to read it here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { attachPicker.launch(EPUB_TYPES) }, enabled = !importing) {
                                Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (importing) "Importing…" else "Attach an EPUB")
                            }
                        }
                    }
                }

                if (book.isReadable && !inLibrary) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { entry?.let { vm.repo.addCatalogBook(it) }; vm.toast("Added to your library") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add to library")
                    }
                }

                if (inLibrary) {
                    Spacer(Modifier.height(24.dp))
                    Text("Status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ReadingStatus.entries.forEachIndexed { i, s ->
                            SegmentedButton(
                                selected = book.status == s,
                                onClick = { vm.repo.setStatus(book.id, s) },
                                shape = SegmentedButtonDefaults.itemShape(i, ReadingStatus.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ),
                                label = { Text(s.label(), maxLines = 1) },
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (book.isReadable && !isCurrent) {
                            OutlinedButton(onClick = {
                                vm.repo.setCurrentBook(book.id)
                                vm.toast("Redirects will now open “${book.title}”")
                            }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.AltRoute, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Make current", maxLines = 1)
                            }
                        }
                        OutlinedButton(onClick = { showLists = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add to list", maxLines = 1)
                        }
                    }
                    if (book.isReadable && !isCurrent) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Your current book is what “Continue reading” and redirects open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (description.isNotBlank()) {
                    Spacer(Modifier.height(28.dp))
                    Text("About this book", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val marks = data.bookmarksFor(book.id)
                if (marks.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Text("Bookmarks", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    marks.forEach { bm ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.repo.savePosition(book.id, bm.position, bm.progress)
                                actions.read(book.id)
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(bm.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(percent(bm.progress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (book.source == BookSource.BUNDLED || book.source == BookSource.DOWNLOADED) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "This edition comes from Project Gutenberg and is in the public domain in the USA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showLists) {
        ModalBottomSheet(onDismissRequest = { showLists = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Add to a list", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                if (data.readingLists.isEmpty()) {
                    Text(
                        "You don't have any lists yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                data.readingLists.forEach { list ->
                    val checked = book.id in list.bookIds
                    SheetAction(
                        if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                        list.name,
                        "${list.bookIds.size} ${if (list.bookIds.size == 1) "book" else "books"}",
                    ) { vm.repo.toggleBookInList(list.id, book.id) }
                }
                SheetAction(Icons.Rounded.Add, "New list", "Create a list and add this book") { showNewList = true }
            }
        }
    }

    if (showNewList) {
        NewListDialog(onDismiss = { showNewList = false }) { name, desc ->
            val id = vm.repo.createList(name, desc)
            vm.repo.toggleBookInList(id, book.id)
            showNewList = false
            showLists = false
            vm.toast("Added to “${name.trim()}”")
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (book.source == BookSource.IMPORTED || book.source == BookSource.DOWNLOADED) "Delete this book?" else "Remove from library?") },
            text = {
                Text(
                    when (book.source) {
                        BookSource.IMPORTED -> "The imported file, your progress and bookmarks will be deleted from this device."
                        BookSource.BUNDLED -> "Your progress and bookmarks will be cleared. You can add it again from Discover."
                        BookSource.CATALOG_SAMPLE -> "It will be removed from your library and lists."
                        BookSource.DOWNLOADED -> "The downloaded file, your progress and bookmarks will be deleted. You can download it again from Discover."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    vm.repo.removeBook(book.id)
                    vm.toast("Removed “${book.title}”")
                    actions.back()
                }) { Text(if (book.source == BookSource.IMPORTED) "Delete" else "Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}
