package com.example.redirect.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.redirect.data.AppData
import com.example.redirect.data.Book
import com.example.redirect.data.ReaderPreferences
import com.example.redirect.data.ReaderTheme
import com.example.redirect.data.ReadingPosition
import com.example.redirect.data.ReadingStatus
import com.example.redirect.data.bookmarksFor
import com.example.redirect.epub.EpubArchive
import com.example.redirect.epub.EpubBook
import com.example.redirect.epub.EpubException
import com.example.redirect.epub.EpubParser
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.percent
import com.example.redirect.ui.theme.Serif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.abs

private sealed interface ReaderLoad {
    data object Loading : ReaderLoad
    data class Ready(val archive: EpubArchive, val epub: EpubBook) : ReaderLoad
    data class Failed(val message: String) : ReaderLoad
}

@Composable
fun ReaderScreen(vm: AppViewModel, data: AppData, bookId: String, onExit: () -> Unit) {
    val book = data.books.firstOrNull { it.id == bookId }
    val prefs = data.preferences.reader
    val palette = prefs.theme.palette()

    val load by produceState<ReaderLoad>(ReaderLoad.Loading, bookId) {
        val b = vm.repo.book(bookId)
        if (b == null || !b.isReadable) {
            value = ReaderLoad.Failed("This book doesn't have a readable file.")
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            try {
                val file = vm.repo.bookFile(b) ?: return@withContext ReaderLoad.Failed("The book file is missing. Try importing it again.")
                val archive = EpubArchive(file)
                try {
                    ReaderLoad.Ready(archive, EpubParser.parse(archive))
                } catch (e: Exception) {
                    archive.close()
                    throw e
                }
            } catch (e: EpubException) {
                ReaderLoad.Failed(e.message ?: "This book couldn't be opened.")
            } catch (e: Exception) {
                ReaderLoad.Failed("This book couldn't be opened.")
            }
        }
    }
    DisposableEffect(load) {
        // Capture this effect's value: `load` read inside onDispose would be the *new* state,
        // which closed the archive the reader was about to use.
        val opened = (load as? ReaderLoad.Ready)?.archive
        onDispose { opened?.close() }
    }
    LaunchedEffect(bookId) { vm.repo.markOpened(bookId) }

    Box(Modifier.fillMaxSize().background(Color(palette.background))) {
        when (val state = load) {
            ReaderLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color(palette.accent), strokeWidth = 2.dp)
            is ReaderLoad.Failed -> ReaderError(state.message, palette, onExit)
            is ReaderLoad.Ready -> if (book != null) ReaderContent(vm, data, book, state.archive, state.epub, prefs, onExit)
        }
    }
}

@Composable
private fun ReaderError(message: String, palette: ReaderPalette, onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Couldn't open this book", fontFamily = Serif, fontSize = 24.sp, color = Color(palette.text), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(message, color = Color(palette.muted), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onExit) { Text("Go back", color = Color(palette.text)) }
    }
}

private data class LoadRequest(val index: Int, val fraction: Float?, val fragment: String?, val nonce: Long = System.nanoTime())

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    vm: AppViewModel,
    data: AppData,
    book: Book,
    archive: EpubArchive,
    epub: EpubBook,
    prefs: ReaderPreferences,
    onExit: () -> Unit,
) {
    val palette = prefs.theme.palette()
    val bg = Color(palette.background)
    val fg = Color(palette.text)
    val muted = Color(palette.muted)
    val accent = Color(palette.accent)
    val chrome = Color(palette.chrome)

    val start = book.position?.takeIf { it.spineIndex in epub.spine.indices }
    var spineIndex by remember { mutableIntStateOf(start?.spineIndex ?: 0) }
    var fraction by remember { mutableFloatStateOf(start?.fraction ?: 0f) }
    var request by remember { mutableStateOf(LoadRequest(spineIndex, fraction, null)) }
    var restoring by remember { mutableStateOf(true) }
    var chromeVisible by remember { mutableStateOf(true) }
    var showContents by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var sessionStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sessionStartProgress by remember { mutableFloatStateOf(book.progress) }

    val progress = epub.progressOf(spineIndex, fraction)
    val chapterTitle = epub.chapterTitleFor(spineIndex)
    val atChapterEnd = !restoring && fraction >= 0.985f
    val isLastChapter = spineIndex == epub.spine.lastIndex
    val marks = data.bookmarksFor(book.id)
    val markHere = marks.firstOrNull { it.position.spineIndex == spineIndex && abs(it.position.fraction - fraction) < 0.03f }

    val currentProgress by rememberUpdatedState(progress)
    val currentPos by rememberUpdatedState(ReadingPosition(spineIndex, fraction))

    fun goTo(index: Int, atFraction: Float? = 0f, fragment: String? = null) {
        if (index !in epub.spine.indices) return
        restoring = true
        spineIndex = index
        fraction = atFraction ?: 0f
        request = LoadRequest(index, atFraction, fragment)
    }

    fun persist() {
        if (!restoring) vm.repo.savePosition(book.id, currentPos, currentProgress)
    }

    fun endSession() {
        persist()
        vm.repo.recordSession(book.id, sessionStart, System.currentTimeMillis(), sessionStartProgress, currentProgress)
    }

    // Immersive reading: hide system bars together with the reader chrome.
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(chromeVisible, prefs.theme) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) } ?: return@LaunchedEffect
        controller.isAppearanceLightStatusBars = prefs.theme != ReaderTheme.NIGHT
        controller.isAppearanceLightNavigationBars = prefs.theme != ReaderTheme.NIGHT
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    // Session tracking survives the app going to the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> endSession()
                Lifecycle.Event.ON_START -> {
                    sessionStart = System.currentTimeMillis()
                    sessionStartProgress = currentProgress
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            endSession()
        }
    }

    // Save the position shortly after scrolling settles.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(spineIndex, fraction, restoring) }
            .debounce(700)
            .distinctUntilChanged()
            .collect { (_, _, isRestoring) -> if (!isRestoring) persist() }
    }

    val chapterPaths = remember(epub) { epub.spine.map { it.path }.toSet() }
    val webView = remember { mutableStateOf<EpubWebView?>(null) }
    val context = LocalContext.current
    val client = remember(archive) {
        EpubClient(
            archive = archive,
            chapterPaths = chapterPaths,
            onInternalLink = { path, fragment ->
                val idx = epub.spineIndexOf(path)
                if (idx >= 0) goTo(idx, atFraction = if (fragment == null) 0f else null, fragment = fragment)
            },
            onExternalLink = { vm.toast("Links to the web are turned off while reading") },
            onLoaded = {
                val wv = webView.value ?: return@EpubClient
                val target = request.fraction
                if (request.fragment == null && target != null && target > 0f) {
                    wv.restoreFraction(target)
                } else {
                    wv.postDelayed({ restoring = false; fraction = wv.currentFraction }, 120)
                }
            },
        )
    }

    // (Re)load the requested chapter, and reload in place when reading settings change.
    LaunchedEffect(request, webView.value) {
        val wv = webView.value ?: return@LaunchedEffect
        client.css = buildReaderCss(prefs)
        wv.setBackgroundColor(bg.toArgb())
        wv.loadUrl(EpubWebView.urlFor(epub.spine[request.index].path, request.fragment))
    }
    var lastPrefs by remember { mutableStateOf(prefs) }
    LaunchedEffect(prefs) {
        if (prefs != lastPrefs) {
            lastPrefs = prefs
            goTo(spineIndex, fraction)
        }
    }

    BackHandler {
        if (showContents || showSettings) { showContents = false; showSettings = false } else onExit()
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        AndroidView(
            factory = { ctx ->
                EpubWebView(ctx).apply {
                    webViewClient = client
                    setBackgroundColor(bg.toArgb())
                    onTap = { chromeVisible = !chromeVisible }
                    onScrollFraction = { f ->
                        if (!restoring) {
                            fraction = f
                            if (chromeVisible && f > 0.02f) chromeVisible = false
                        }
                    }
                    onPageReady = { restoring = false; fraction = currentFraction }
                    webView.value = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.stopLoading(); it.destroy() },
        )

        // Quiet "next chapter" affordance when the end of a chapter is reached.
        AnimatedVisibility(
            atChapterEnd && !chromeVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            EndOfChapter(isLastChapter, book.status == ReadingStatus.FINISHED, accent, chrome, fg,
                onNext = { goTo(spineIndex + 1) },
                onFinish = {
                    vm.repo.setStatus(book.id, ReadingStatus.FINISHED)
                    vm.toast("Finished “${book.title}”. Lovely.")
                },
            )
        }

        // Top chrome
        AnimatedVisibility(
            chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(color = chrome.copy(alpha = 0.97f), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onExit) { Icon(Icons.Rounded.Close, "Close book", tint = fg) }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(book.title, color = fg, fontFamily = Serif, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (chapterTitle != null) {
                            Text(chapterTitle, color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = {
                        if (markHere != null) {
                            vm.repo.removeBookmark(markHere.id)
                            vm.toast("Bookmark removed")
                        } else {
                            val label = (chapterTitle ?: "Section ${spineIndex + 1}") + " · " + percent(progress)
                            vm.repo.addBookmark(book.id, ReadingPosition(spineIndex, fraction), label, progress)
                            vm.toast("Bookmarked")
                        }
                    }) {
                        Icon(
                            if (markHere != null) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                            if (markHere != null) "Remove bookmark" else "Add bookmark",
                            tint = if (markHere != null) accent else fg,
                        )
                    }
                    IconButton(onClick = { showContents = true }) { Icon(Icons.AutoMirrored.Rounded.List, "Contents and bookmarks", tint = fg) }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.FormatSize, "Reading settings", tint = fg) }
                }
            }
        }

        // Bottom chrome
        AnimatedVisibility(
            chromeVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(color = chrome.copy(alpha = 0.97f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { goTo(spineIndex - 1) }, enabled = spineIndex > 0) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Previous chapter", tint = if (spineIndex > 0) fg else muted.copy(alpha = 0.4f))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                                color = accent,
                                trackColor = Color(palette.border),
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${percent(progress)} · Section ${spineIndex + 1} of ${epub.spine.size}",
                                color = muted, fontSize = 12.sp,
                            )
                        }
                        IconButton(onClick = { goTo(spineIndex + 1) }, enabled = !isLastChapter) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Next chapter", tint = if (!isLastChapter) fg else muted.copy(alpha = 0.4f))
                        }
                    }
                    if (isLastChapter && atChapterEnd && book.status != ReadingStatus.FINISHED) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.repo.setStatus(book.id, ReadingStatus.FINISHED); vm.toast("Finished “${book.title}”. Lovely.") },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = bg),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Mark as finished") }
                    }
                }
            }
        }
    }

    if (showContents) {
        ModalBottomSheet(
            onDismissRequest = { showContents = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = chrome,
            contentColor = fg,
        ) {
            ContentsSheet(
                epub, spineIndex, marks, fg, muted, accent,
                onChapter = { entry -> showContents = false; goTo(entry.spineIndex, if (entry.fragment == null) 0f else null, entry.fragment) },
                onSection = { idx -> showContents = false; goTo(idx) },
                onBookmark = { bm -> showContents = false; goTo(bm.position.spineIndex, bm.position.fraction) },
                onDeleteBookmark = { vm.repo.removeBookmark(it.id) },
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = chrome, contentColor = fg) {
            ReaderSettingsSheet(prefs, fg, muted, accent) { transform -> vm.repo.updateReaderPreferences(transform) }
        }
    }
}

@Composable
private fun EndOfChapter(
    isLast: Boolean, finished: Boolean, accent: Color, chrome: Color, fg: Color, onNext: () -> Unit, onFinish: () -> Unit,
) {
    if (isLast) {
        if (finished) return
        Surface(onClick = onFinish, shape = CircleShape, color = chrome, border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mark as finished", color = fg, fontWeight = FontWeight.Medium)
            }
        }
    } else {
        Surface(onClick = onNext, shape = CircleShape, color = chrome, border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Next chapter", color = fg, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ContentsSheet(
    epub: EpubBook,
    spineIndex: Int,
    marks: List<com.example.redirect.data.Bookmark>,
    fg: Color, muted: Color, accent: Color,
    onChapter: (com.example.redirect.epub.TocEntry) -> Unit,
    onSection: (Int) -> Unit,
    onBookmark: (com.example.redirect.data.Bookmark) -> Unit,
    onDeleteBookmark: (com.example.redirect.data.Bookmark) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = fg,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = accent)
            },
            divider = {},
        ) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Contents") })
            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Bookmarks · ${marks.size}") })
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) {
            val current = epub.toc.indexOfLast { it.spineIndex <= spineIndex }
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.height(480.dp)) {
                if (epub.toc.isEmpty()) {
                    items(epub.spine.indices.toList()) { i ->
                        TocRow("Section ${i + 1}", 0, i == spineIndex, fg, accent) { onSection(i) }
                    }
                } else {
                    items(epub.toc.indices.toList()) { i ->
                        val entry = epub.toc[i]
                        TocRow(entry.title, entry.depth, i == current, fg, accent) { onChapter(entry) }
                    }
                }
            }
        } else {
            if (marks.isEmpty()) {
                Text(
                    "No bookmarks yet. Tap the bookmark icon while reading to save your place.",
                    color = muted, modifier = Modifier.padding(24.dp),
                )
                Spacer(Modifier.height(80.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.height(420.dp)) {
                    items(marks, key = { it.id }) { bm ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onBookmark(bm) }.padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Bookmark, null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(bm.label, color = fg, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { onDeleteBookmark(bm) }) { Icon(Icons.Rounded.Delete, "Delete bookmark", tint = muted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TocRow(title: String, depth: Int, current: Boolean, fg: Color, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = (24 + depth * 16).dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = if (current) accent else fg,
            fontFamily = Serif,
            fontSize = if (depth == 0) 16.sp else 15.sp,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (current) Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    prefs: ReaderPreferences, fg: Color, muted: Color, accent: Color,
    update: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        Text("Reading settings", fontFamily = Serif, fontSize = 22.sp, color = fg)
        Spacer(Modifier.height(20.dp))

        Text("Text size", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { update { it.copy(fontSizePx = (it.fontSizePx - 1).coerceAtLeast(14)) } },
                enabled = prefs.fontSizePx > 14,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
            ) { Icon(Icons.Rounded.Remove, "Smaller text", tint = fg) }
            Text(
                "Aa", fontFamily = Serif, fontSize = (prefs.fontSizePx * 1.1f).sp, color = fg,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { update { it.copy(fontSizePx = (it.fontSizePx + 1).coerceAtMost(30)) } },
                enabled = prefs.fontSizePx < 30,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
            ) { Icon(Icons.Rounded.Add, "Larger text", tint = fg) }
        }
        Text("${prefs.fontSizePx}px", color = muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        Spacer(Modifier.height(20.dp))
        Text("Line spacing", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        val spacings = listOf(1.4f to "Compact", 1.6f to "Comfortable", 1.9f to "Airy")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            spacings.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = abs(prefs.lineHeight - value) < 0.05f,
                    onClick = { update { it.copy(lineHeight = value) } },
                    shape = SegmentedButtonDefaults.itemShape(i, spacings.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = accent.copy(alpha = 0.18f),
                        activeContentColor = fg,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = fg,
                        activeBorderColor = muted.copy(alpha = 0.5f),
                        inactiveBorderColor = muted.copy(alpha = 0.5f),
                    ),
                    label = { Text(label, maxLines = 1) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Page", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderTheme.entries.forEach { theme ->
                val p = theme.palette()
                val selected = prefs.theme == theme
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { update { it.copy(theme = theme) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(p.background))
                            .border(if (selected) 2.dp else 1.dp, if (selected) accent else Color(p.border), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Aa", color = Color(p.text), fontFamily = Serif, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (theme) { ReaderTheme.PAPER -> "Light"; ReaderTheme.WARM -> "Warm paper"; ReaderTheme.NIGHT -> "Dark" },
                        color = if (selected) fg else muted, fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { update { ReaderPreferences(theme = it.theme) } }) { Text("Reset size and spacing", color = accent) }
    }
}
