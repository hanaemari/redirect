package com.example.redirect.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.redirect.data.AppData
import com.example.redirect.data.Book
import com.example.redirect.data.BookSource
import com.example.redirect.data.ReadingStatus
import com.example.redirect.data.libraryBooks
import com.example.redirect.ui.AppActions
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.components.BookCover
import com.example.redirect.ui.components.EmptyState
import com.example.redirect.ui.components.Pill
import com.example.redirect.ui.components.PillTone
import com.example.redirect.ui.components.ReadingProgress
import com.example.redirect.ui.components.ScreenTitle
import com.example.redirect.ui.label
import com.example.redirect.ui.percent

enum class SortOrder(val label: String) {
    RECENT("Recently opened"), ADDED("Recently added"), TITLE("Title"), AUTHOR("Author"), PROGRESS("Progress")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: AppViewModel, data: AppData, actions: AppActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<ReadingStatus?>(null) }
    var listFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.RECENT) }
    var gridView by rememberSaveable { mutableStateOf(true) }
    var showSort by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showNewList by remember { mutableStateOf(false) }
    var confirmDeleteList by remember { mutableStateOf<String?>(null) }

    val importing by vm.importing.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importEpub(uri)
    }

    val all = data.libraryBooks
    val selectedList = data.readingLists.firstOrNull { it.id == listFilter }
    val books = all
        .filter { statusFilter == null || it.status == statusFilter }
        .filter { selectedList == null || it.id in selectedList.bookIds }
        .filter { query.isBlank() || it.title.contains(query.trim(), true) || it.author.contains(query.trim(), true) }
        .let { list ->
            when (sort) {
                SortOrder.RECENT -> list.sortedWith(compareByDescending<Book> { it.lastOpenedAt ?: 0 }.thenByDescending { it.createdAt })
                SortOrder.ADDED -> list.sortedByDescending { it.createdAt }
                SortOrder.TITLE -> list.sortedBy { it.title.removePrefix("The ").lowercase() }
                SortOrder.AUTHOR -> list.sortedBy { it.author.substringAfterLast(' ').lowercase() }
                SortOrder.PROGRESS -> list.sortedByDescending { it.progress }
            }
        }

    LazyVerticalGrid(
        columns = if (gridView) GridCells.Adaptive(104.dp) else GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (gridView) 22.dp else 4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenTitle(
                    "Library",
                    subtitle = "${all.size} ${if (all.size == 1) "book" else "books"}",
                    horizontalPadding = 4.dp,
                ) {
                    FilledTonalIconButton(onClick = { showAdd = true }) { Icon(Icons.Rounded.Add, "Add a book") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search title or author") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        LibraryChip("All", statusFilter == null && listFilter == null) { statusFilter = null; listFilter = null }
                    }
                    items(ReadingStatus.entries) { s ->
                        val count = all.count { it.status == s }
                        LibraryChip("${s.label()} · $count", statusFilter == s) {
                            statusFilter = if (statusFilter == s) null else s
                        }
                    }
                    items(data.readingLists, key = { it.id }) { list ->
                        LibraryChip(list.name, listFilter == list.id) { listFilter = if (listFilter == list.id) null else list.id }
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = { showNewList = true },
                            label = { Text("New list") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selectedList != null) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedList.name, style = MaterialTheme.typography.titleLarge)
                            if (selectedList.description.isNotBlank()) {
                                Text(selectedList.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { confirmDeleteList = selectedList.id }) { Text("Delete list") }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Box {
                        TextButton(onClick = { showSort = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(sort.label)
                        }
                        DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(text = { Text(order.label) }, onClick = { sort = order; showSort = false })
                            }
                        }
                    }
                    IconButton(onClick = { gridView = !gridView }) {
                        Icon(if (gridView) Icons.Rounded.ViewList else Icons.Rounded.GridView, if (gridView) "Show as list" else "Show as grid")
                    }
                }
            }
        }

        if (all.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    Icons.Outlined.Explore,
                    "Nothing on the shelf yet",
                    "Add a free classic from Discover, or import an EPUB you own.",
                ) {
                    Button(onClick = actions::discover) { Text("Discover books") }
                    OutlinedButton(onClick = { picker.launch(EPUB_TYPES) }) { Text("Import an EPUB") }
                }
            }
        } else if (books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    Icons.Rounded.SearchOff,
                    if (selectedList != null && query.isBlank()) "This list is empty" else "No matches",
                    if (selectedList != null && query.isBlank()) "Open a book and choose “Add to list” to collect it here."
                    else "Try a different search or filter.",
                )
            }
        } else {
            items(books, key = { it.id }) { book ->
                if (gridView) GridBook(book) { actions.openBook(book.id) }
                else ListBook(book) { actions.openBook(book.id) }
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Add a book", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                SheetAction(Icons.Outlined.Explore, "Discover books", "Free classics and sample listings") {
                    showAdd = false; actions.discover()
                }
                SheetAction(Icons.Rounded.FileUpload, if (importing) "Importing…" else "Import an EPUB", "A DRM-free .epub file from your device") {
                    showAdd = false; picker.launch(EPUB_TYPES)
                }
                SheetAction(Icons.Rounded.PlaylistAdd, "New reading list", "Group books however you like") {
                    showAdd = false; showNewList = true
                }
            }
        }
    }

    if (showNewList) {
        NewListDialog(onDismiss = { showNewList = false }) { name, desc ->
            listFilter = vm.repo.createList(name, desc)
            statusFilter = null
            showNewList = false
        }
    }

    confirmDeleteList?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteList = null },
            title = { Text("Delete this list?") },
            text = { Text("The books stay in your library. Only the list is removed.") },
            confirmButton = {
                TextButton(onClick = { vm.repo.deleteList(id); listFilter = null; confirmDeleteList = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteList = null }) { Text("Cancel") } },
        )
    }
}

val EPUB_TYPES = arrayOf("application/epub+zip", "application/octet-stream")

@Composable
private fun LibraryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun GridBook(book: Book, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box {
            BookCover(book, Modifier.fillMaxWidth())
            if (book.source == BookSource.CATALOG_SAMPLE && !book.isReadable) {
                Pill("Sample", PillTone.Muted, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        when {
            book.status == ReadingStatus.FINISHED -> Text("Finished", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            book.progress > 0f -> ReadingProgress(book.progress, Modifier.fillMaxWidth(), height = 3.dp)
            else -> Text(book.status.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ListBook(book: Book, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book, Modifier.width(56.dp), elevated = false)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    book.status.label(),
                    when (book.status) {
                        ReadingStatus.READING -> PillTone.Accent
                        ReadingStatus.FINISHED -> PillTone.Success
                        ReadingStatus.WANT_TO_READ -> PillTone.Muted
                    },
                )
                if (book.progress > 0f && book.status == ReadingStatus.READING) {
                    Spacer(Modifier.width(10.dp))
                    Text(percent(book.progress), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (book.source == BookSource.CATALOG_SAMPLE && !book.isReadable) {
                    Spacer(Modifier.width(8.dp))
                    Pill("Sample listing", PillTone.Muted)
                }
            }
        }
    }
}

@Composable
fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
fun NewListDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New reading list") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(desc, { desc = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, desc) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
