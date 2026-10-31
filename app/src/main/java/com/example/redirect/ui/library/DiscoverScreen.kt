package com.example.redirect.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.redirect.data.AppData
import com.example.redirect.data.BookSource
import com.example.redirect.data.FreeBook
import com.example.redirect.data.FreeBookException
import com.example.redirect.data.SeedData
import com.example.redirect.ui.AppActions
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.components.BackBar
import com.example.redirect.ui.components.BookCover
import com.example.redirect.ui.components.EmptyState
import com.example.redirect.ui.components.Pill
import com.example.redirect.ui.components.PillTone
import com.example.redirect.ui.components.rememberRemoteCoverPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Online results: null while loading. */
private sealed interface OnlineState {
    data object Loading : OnlineState
    data class Loaded(val books: List<FreeBook>, val nextUrl: String?, val source: String) : OnlineState
    data class Failed(val message: String) : OnlineState
}

@Composable
fun DiscoverScreen(vm: AppViewModel, data: AppData, actions: AppActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var online by remember { mutableStateOf<OnlineState>(OnlineState.Loading) }
    var loadingMore by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<FreeBook?>(null) }
    val scope = rememberCoroutineScope()
    val downloading by vm.downloading.collectAsStateWithLifecycle()

    val included by produceState(SeedData.bundledBooks, query) {
        value = vm.repo.catalog.search(query).filter { it.source == BookSource.BUNDLED }
    }
    LaunchedEffect(query, retry) {
        online = OnlineState.Loading
        if (query.isNotBlank()) delay(500)
        online = try {
            val page = vm.repo.freeBooks.browse(query)
            OnlineState.Loaded(page.books, page.nextUrl, page.source)
        } catch (e: FreeBookException) {
            OnlineState.Failed(e.message ?: "Couldn't load books.")
        }
    }

    val inLibrary = data.books.filter { it.inLibrary && it.isReadable }.map { it.id }.toSet()
    fun openOrDownload(book: FreeBook) {
        if (book.bookId in inLibrary) actions.read(book.bookId)
        else vm.downloadFreeBook(book)
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Discover", onBack = { actions.back() })
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text("Find your next book", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Title or author") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (included.isNotEmpty()) {
                item { SectionLabel("Included with the app", "Public-domain classics, ready offline") }
                items(included, key = { it.id }) { entry ->
                    IncludedRow(entry, entry.id in inLibrary, onOpen = { actions.openBook(entry.id) }) {
                        vm.repo.addCatalogBook(entry)
                        vm.toast("Added “${entry.title}” to your library")
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionLabel(
                    if (query.isBlank()) "Popular on Project Gutenberg" else "From Project Gutenberg",
                    "Free public-domain ebooks. Downloaded books work offline.",
                )
            }
            when (val state = online) {
                OnlineState.Loading -> item {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Searching the catalog. This can take a little while.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is OnlineState.Failed -> item {
                    EmptyState(Icons.Rounded.CloudOff, "Couldn't load free books", state.message) {
                        OutlinedButton(onClick = { retry++ }) { Text("Try again") }
                    }
                }
                is OnlineState.Loaded -> {
                    if (state.books.isEmpty()) {
                        item { EmptyState(Icons.Rounded.SearchOff, "No free books found", "Try another title or author.") }
                    }
                    items(state.books, key = { "g${it.gutenbergId}" }) { book ->
                        FreeBookRow(
                            book,
                            added = book.bookId in inLibrary,
                            downloading = book.gutenbergId in downloading,
                            onOpen = { details = book },
                            onAction = { openOrDownload(book) },
                        )
                    }
                    if (state.nextUrl != null) {
                        item {
                            TextButton(
                                onClick = {
                                    loadingMore = true
                                    scope.launch {
                                        try {
                                            val page = vm.repo.freeBooks.nextPage(state.nextUrl)
                                            online = state.copy(books = (state.books + page.books).distinctBy { it.gutenbergId }, nextUrl = page.nextUrl)
                                        } catch (e: FreeBookException) {
                                            vm.toast(e.message ?: "Couldn't load more books.")
                                        } finally {
                                            loadingMore = false
                                        }
                                    }
                                },
                                enabled = !loadingMore,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) { Text(if (loadingMore) "Loading…" else "Show more") }
                        }
                    }
                }
            }
        }
    }

    details?.let { book ->
        FreeBookSheet(
            book,
            added = book.bookId in inLibrary,
            downloading = book.gutenbergId in downloading,
            onDismiss = { details = null },
            onAction = { openOrDownload(book); if (book.bookId in inLibrary) details = null },
        )
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IncludedRow(entry: SeedData.CatalogEntry, added: Boolean, onOpen: () -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BookCover(entry.title, entry.author, entry.tint, modifier = Modifier.width(72.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(entry.author, entry.year).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                entry.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (added) {
                    OutlinedButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("In library")
                    }
                } else {
                    FilledTonalButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add to library")
                    }
                }
                Spacer(Modifier.width(10.dp))
                Pill("Free", PillTone.Success)
            }
        }
    }
}

@Composable
private fun FreeBookRow(book: FreeBook, added: Boolean, downloading: Boolean, onOpen: () -> Unit, onAction: () -> Unit) {
    val cover = rememberRemoteCoverPath(book.coverUrl)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BookCover(book.title, book.author, book.gutenbergId % 8, cover, modifier = Modifier.width(72.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (book.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    book.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FreeBookButton(added, downloading, onAction)
                Spacer(Modifier.width(10.dp))
                Pill("Public domain", PillTone.Success)
            }
        }
    }
}

@Composable
private fun FreeBookButton(added: Boolean, downloading: Boolean, onAction: () -> Unit, modifier: Modifier = Modifier) {
    when {
        downloading -> OutlinedButton(onClick = {}, enabled = false, contentPadding = PaddingValues(horizontal = 14.dp), modifier = modifier) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Downloading…")
        }
        added -> OutlinedButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 14.dp), modifier = modifier) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Read")
        }
        else -> FilledTonalButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 14.dp), modifier = modifier) {
            Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Download")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreeBookSheet(book: FreeBook, added: Boolean, downloading: Boolean, onDismiss: () -> Unit, onAction: () -> Unit) {
    val cover = rememberRemoteCoverPath(book.coverUrl)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
        ) {
            Row {
                BookCover(book.title, book.author, book.gutenbergId % 8, cover, modifier = Modifier.width(96.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Pill(if (book.rightsVerified) "Public domain in the USA" else "Rights checked before download", PillTone.Success)
                }
            }
            if (book.subjects.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(book.subjects.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (book.summary.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(book.summary, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            if (downloading || added) {
                FreeBookButton(added, downloading, onAction, Modifier.fillMaxWidth().height(48.dp))
            } else {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download free EPUB")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "From Project Gutenberg (gutenberg.org). Public-domain status can differ outside the USA.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
