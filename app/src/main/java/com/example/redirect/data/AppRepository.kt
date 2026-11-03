package com.example.redirect.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.redirect.epub.EpubArchive
import com.example.redirect.epub.EpubException
import com.example.redirect.epub.EpubParser
import com.example.redirect.platform.InstalledApps
import com.example.redirect.platform.RedirectionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface ImportResult {
    data class Success(val book: Book) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/** All reads and writes of user data go through here. */
class AppRepository(
    private val context: Context,
    val store: AppStore,
    val installedApps: InstalledApps,
    private val bridge: RedirectionBridge,
    val catalog: BookCatalog = LocalSampleCatalog(),
    val freeBooks: FreeBookCatalog = FreeBookCatalog(cacheDir = File(context.cacheDir, "discover")),
) {
    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val bundledDir = File(context.filesDir, "bundled").apply { mkdirs() }

    private fun now() = System.currentTimeMillis()
    private fun newId(prefix: String) = "$prefix-${UUID.randomUUID().toString().take(8)}"

    private fun update(syncBridge: Boolean = false, transform: (AppData) -> AppData) {
        store.update(transform)
        if (syncBridge) bridge.onConfigurationChanged(store.current)
    }

    /** Makes sure catalog triggers and default destinations exist (also after app updates). */
    fun ensureSeeded() = update {
        val knownTriggers = it.triggerApps.map { t -> t.id }.toSet()
        val knownDest = it.destinations.map { d -> d.id }.toSet()
        it.copy(
            triggerApps = it.triggerApps + SeedData.triggerCatalog.filter { t -> t.id !in knownTriggers },
            destinations = if (it.preferences.onboardingComplete) it.destinations
            else it.destinations + SeedData.defaultDestinations.filter { d -> d.id !in knownDest },
        )
    }

    // -- Onboarding -----------------------------------------------------------

    fun completeOnboarding(selectedTriggers: List<String>, style: RedirectStyle, starterBookId: String?) {
        val t = now()
        update(syncBridge = true) { data ->
            val existingIds = data.books.map { it.id }.toSet()
            val bundled = SeedData.bundledBooks.filter { it.id !in existingIds }.map { entry ->
                entry.toBook(t).copy(
                    status = if (entry.id == starterBookId) ReadingStatus.READING else ReadingStatus.WANT_TO_READ,
                    lastOpenedAt = if (entry.id == starterBookId) t else null,
                )
            }
            data.copy(
                preferences = data.preferences.copy(
                    onboardingComplete = true,
                    defaultRedirectStyle = style,
                    currentBookId = starterBookId ?: data.preferences.currentBookId,
                ),
                books = data.books + bundled,
                selectedTriggerIds = selectedTriggers,
                rules = data.rules.ifEmpty { SeedData.starterRules(selectedTriggers, style, t) },
            )
        }
    }

    fun resetEverything() {
        booksDir.listFiles()?.forEach { it.delete() }
        coversDir.listFiles()?.forEach { it.delete() }
        store.update { AppData() }
        ensureSeeded()
    }

    // -- Preferences ----------------------------------------------------------

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) =
        update { it.copy(preferences = transform(it.preferences)) }

    fun updateReaderPreferences(transform: (ReaderPreferences) -> ReaderPreferences) =
        updatePreferences { it.copy(reader = transform(it.reader)) }

    // -- Books ----------------------------------------------------------------

    fun book(id: String): Book? = store.current.books.firstOrNull { it.id == id }

    /** Adds a catalog entry (bundled or sample) to the library, or re-shows it if it was removed. */
    fun addCatalogBook(entry: SeedData.CatalogEntry, status: ReadingStatus = ReadingStatus.WANT_TO_READ): String {
        update { data ->
            val existing = data.books.firstOrNull { it.id == entry.id }
            val books = if (existing != null) {
                data.books.map { if (it.id == entry.id) it.copy(inLibrary = true, status = status, updatedAt = now()) else it }
            } else {
                data.books + entry.toBook(now()).copy(status = status)
            }
            data.copy(books = books)
        }
        return entry.id
    }

    fun setStatus(bookId: String, status: ReadingStatus) = updateBook(bookId) {
        it.copy(status = status, progress = if (status == ReadingStatus.FINISHED) 1f else it.progress)
    }

    fun setCurrentBook(bookId: String) = update { data ->
        data.copy(
            preferences = data.preferences.copy(currentBookId = bookId),
            books = data.books.map {
                if (it.id == bookId && it.status != ReadingStatus.READING) it.copy(status = ReadingStatus.READING, updatedAt = now()) else it
            },
        )
    }

    fun markOpened(bookId: String) = update { data ->
        data.copy(
            preferences = data.preferences.copy(currentBookId = bookId),
            books = data.books.map {
                if (it.id != bookId) it
                else it.copy(
                    lastOpenedAt = now(),
                    inLibrary = true,
                    status = if (it.status == ReadingStatus.WANT_TO_READ) ReadingStatus.READING else it.status,
                )
            },
        )
    }

    fun savePosition(bookId: String, position: ReadingPosition, progress: Float) = updateBook(bookId) {
        it.copy(position = position, progress = progress)
    }

    /** Removes a book from the library. Imported files are deleted; bundled books can be re-added. */
    fun removeBook(bookId: String) {
        val book = book(bookId) ?: return
        update { data ->
            val books = when (book.source) {
                BookSource.BUNDLED -> data.books.map {
                    if (it.id == bookId) it.copy(inLibrary = false, status = ReadingStatus.WANT_TO_READ, position = null, progress = 0f) else it
                }
                else -> data.books.filterNot { it.id == bookId }
            }
            data.copy(
                books = books,
                bookmarks = data.bookmarks.filterNot { it.bookId == bookId },
                readingLists = data.readingLists.map { l -> l.copy(bookIds = l.bookIds - bookId) },
                destinations = data.destinations.filterNot { it.type == DestinationType.SPECIFIC_BOOK && it.targetId == bookId },
                preferences = if (data.preferences.currentBookId == bookId) data.preferences.copy(currentBookId = null) else data.preferences,
            )
        }
        if (book.source != BookSource.BUNDLED) {
            book.file?.takeIf { it.scheme == "file" }?.let { File(it.path).delete() }
            book.coverPath?.let { File(it).delete() }
        }
        cleanDanglingDestinations()
    }

    private fun updateBook(bookId: String, transform: (Book) -> Book) = update { data ->
        data.copy(books = data.books.map { if (it.id == bookId) transform(it).copy(updatedAt = now()) else it })
    }

    /** Returns a local file for the book's EPUB, copying bundled assets out on first use. */
    suspend fun bookFile(book: Book): File? = withContext(Dispatchers.IO) {
        val ref = book.file ?: return@withContext null
        when (ref.scheme) {
            "asset" -> {
                val out = File(bundledDir, ref.path.substringAfterLast('/'))
                if (!out.exists() || out.length() == 0L) {
                    val tmp = File(bundledDir, out.name + ".tmp")
                    context.assets.open(ref.path).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    tmp.renameTo(out)
                }
                out
            }
            else -> File(ref.path).takeIf { it.exists() }
        }
    }

    // -- Import ---------------------------------------------------------------

    /** Imports an EPUB as a new book. */
    suspend fun importEpub(uri: Uri): ImportResult = importInto(uri, existing = null)

    /** Attaches an EPUB the user owns to a metadata-only (sample catalog) book. */
    suspend fun attachEpub(bookId: String, uri: Uri): ImportResult {
        val book = book(bookId) ?: return ImportResult.Failure("That book is no longer in your library.")
        return importInto(uri, existing = book)
    }

    private suspend fun importInto(uri: Uri, existing: Book?): ImportResult = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val id = existing?.id ?: newId("imported")
        val tmp = File(context.cacheDir, "import-$id.epub")
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return@withContext ImportResult.Failure("Couldn't open that file.")
            if (copied == 0L) return@withContext ImportResult.Failure("That file is empty.")
            addEpubFile(tmp, id, existing, BookSource.IMPORTED, name)
        } catch (e: SecurityException) {
            ImportResult.Failure("This app doesn't have permission to read that file.")
        } catch (e: java.io.IOException) {
            ImportResult.Failure("Something went wrong while copying the file. Please try again.")
        } finally {
            tmp.delete()
        }
    }

    /**
     * Downloads a public-domain book from Project Gutenberg into the library.
     * Books whose rights weren't confirmed by the listing are checked first.
     */
    suspend fun downloadFreeBook(free: FreeBook): ImportResult = withContext(Dispatchers.IO) {
        book(free.bookId)?.takeIf { it.isReadable }?.let { existing ->
            if (!existing.inLibrary) update { d -> d.copy(books = d.books.map { if (it.id == existing.id) it.copy(inLibrary = true) else it }) }
            return@withContext ImportResult.Success(existing)
        }
        val tmp = File(context.cacheDir, "download-${free.bookId}.epub")
        try {
            if (!free.rightsVerified && !freeBooks.verifyRights(free.gutenbergId)) {
                return@withContext ImportResult.Failure("Project Gutenberg doesn't list this book as public domain, so it can't be downloaded here.")
            }
            freeBooks.download(free, tmp)
            when (val result = addEpubFile(tmp, free.bookId, existing = null, BookSource.DOWNLOADED, null)) {
                is ImportResult.Success -> {
                    var book = result.book
                    if (book.coverPath == null && free.coverUrl != null) {
                        val cover = File(coversDir, "${book.id}.jpg")
                        if (freeBooks.downloadCover(free.coverUrl, cover)) {
                            book = book.copy(coverPath = cover.absolutePath)
                            updateBook(book.id) { it.copy(coverPath = cover.absolutePath) }
                        }
                    }
                    // Catalog listings may lack a summary; the book's own record (already fetched for
                    // the rights check or the details sheet) usually has one.
                    val summary = free.summary.ifBlank { runCatching { freeBooks.details(free.gutenbergId).summary }.getOrDefault("") }
                    if (book.description.isBlank() && summary.isNotBlank()) {
                        book = book.copy(description = summary)
                        updateBook(book.id) { it.copy(description = summary) }
                    }
                    ImportResult.Success(book)
                }
                is ImportResult.Failure -> result
            }
        } catch (e: java.io.IOException) {
            ImportResult.Failure("The download didn't finish. Check your connection and try again.")
        } finally {
            tmp.delete()
        }
    }

    /** Parses [tmp] as an EPUB, moves it into the library and stores the book. */
    private fun addEpubFile(tmp: File, id: String, existing: Book?, source: BookSource, name: String?): ImportResult {
        val parsed = try {
            EpubParser.parse(tmp)
        } catch (e: EpubException) {
            val looksOther = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it != "epub" && it.isNotEmpty() }
            val message = if (looksOther != null && e.reason in setOf(EpubException.Reason.NOT_ZIP, EpubException.Reason.NOT_EPUB)) {
                "“$name” is a .$looksOther file. Only EPUB books can be imported right now."
            } else e.message ?: "That file couldn't be read as an EPUB."
            return ImportResult.Failure(message)
        } catch (e: Exception) {
            return ImportResult.Failure("That file couldn't be read as an EPUB.")
        }

        val dest = File(booksDir, "$id.epub")
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
        }
        val coverFile = parsed.coverPath?.let { path ->
            runCatching {
                EpubArchive(dest).use { archive ->
                    val ext = when (parsed.coverMediaType) {
                        "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg"
                    }
                    val bytes = archive.readBytes(path) ?: return@use null
                    File(coversDir, "$id.$ext").apply { writeBytes(bytes) }
                }
            }.getOrNull()
        }

        val t = now()
        val book = if (existing != null) {
            existing.copy(
                file = BookFile.file(dest.absolutePath),
                coverPath = coverFile?.absolutePath ?: existing.coverPath,
                source = source,
                updatedAt = t,
            )
        } else {
            Book(
                id = id,
                title = parsed.title,
                author = parsed.author ?: "Unknown author",
                description = parsed.description.orEmpty(),
                coverPath = coverFile?.absolutePath,
                coverTint = (parsed.title.hashCode() and 0x7fffffff) % 8,
                file = BookFile.file(dest.absolutePath),
                source = source,
                status = ReadingStatus.WANT_TO_READ,
                createdAt = t,
                updatedAt = t,
            )
        }
        update { data ->
            data.copy(books = if (data.books.any { it.id == book.id }) data.books.map { if (it.id == book.id) book else it } else data.books + book)
        }
        return ImportResult.Success(book)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    // -- Bookmarks & sessions -------------------------------------------------

    fun addBookmark(bookId: String, position: ReadingPosition, label: String, progress: Float) = update {
        it.copy(bookmarks = it.bookmarks + Bookmark(newId("bm"), bookId, position, label, progress, now()))
    }

    fun removeBookmark(id: String) = update { it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id == id }) }

    fun recordSession(bookId: String, startedAt: Long, endedAt: Long, startProgress: Float, endProgress: Float) {
        if (endedAt - startedAt < 5_000) return
        update {
            val session = ReadingSession(newId("session"), bookId, startedAt, endedAt, startProgress, endProgress)
            // Keep the history bounded; only recent sessions are used.
            it.copy(sessions = (it.sessions + session).takeLast(500))
        }
    }

    // -- Reading lists --------------------------------------------------------

    fun createList(name: String, description: String = ""): String {
        val id = newId("list")
        update { it.copy(readingLists = it.readingLists + ReadingList(id, name.trim(), description.trim(), createdAt = now())) }
        return id
    }

    fun deleteList(id: String) = update { it.copy(readingLists = it.readingLists.filterNot { l -> l.id == id }) }

    fun toggleBookInList(listId: String, bookId: String) = update { data ->
        data.copy(readingLists = data.readingLists.map { l ->
            if (l.id != listId) l
            else l.copy(bookIds = if (bookId in l.bookIds) l.bookIds - bookId else l.bookIds + bookId)
        })
    }

    // -- Triggers -------------------------------------------------------------

    fun setTriggerSelected(triggerId: String, selected: Boolean) = update(syncBridge = true) {
        it.copy(selectedTriggerIds = if (selected) (it.selectedTriggerIds + triggerId).distinct() else it.selectedTriggerIds - triggerId)
    }

    /** Adds any installed app as a distracting app. */
    fun addCustomTrigger(packageName: String, label: String) = update(syncBridge = true) { data ->
        val exists = data.triggerApps.any { it.id == packageName }
        data.copy(
            triggerApps = if (exists) data.triggerApps else data.triggerApps + TriggerApp(
                packageName, label, TriggerKind.APP, packageName, support = PlatformSupport.LAUNCH_DETECTION, isCustom = true,
            ),
            selectedTriggerIds = (data.selectedTriggerIds + packageName).distinct(),
        )
    }

    fun setInterruptionsEnabled(enabled: Boolean) = updatePreferences {
        it.copy(interruptionsEnabled = enabled, interruptionsPausedUntil = null)
    }

    fun pauseInterruptions(until: Long) = updatePreferences {
        it.copy(interruptionsEnabled = true, interruptionsPausedUntil = until)
    }

    fun resumeInterruptions() = updatePreferences { it.copy(interruptionsEnabled = true, interruptionsPausedUntil = null) }

    fun startGracePeriod(triggerId: String, minutes: Int) {
        if (minutes <= 0) return
        update { it.copy(graceUntil = it.graceUntil + (triggerId to now() + minutes * 60_000L)) }
    }

    // -- Destinations ---------------------------------------------------------

    fun addDestination(destination: Destination) = update(syncBridge = true) {
        if (it.destinations.any { d -> d.id == destination.id }) it
        else it.copy(destinations = it.destinations + destination.copy(createdAt = now()))
    }

    fun ensureBookDestination(book: Book): String {
        val existing = store.current.destinations.firstOrNull { it.type == DestinationType.SPECIFIC_BOOK && it.targetId == book.id }
        if (existing != null) return existing.id
        val dest = Destination(newId("dest"), DestinationType.SPECIFIC_BOOK, book.id, "Read ${book.title}", "book", createdAt = now())
        addDestination(dest)
        return dest.id
    }

    fun ensureAppDestination(packageName: String, label: String): String {
        val existing = store.current.destinations.firstOrNull { it.type == DestinationType.EXTERNAL_APP && it.targetId == packageName }
        if (existing != null) return existing.id
        val dest = Destination(newId("dest"), DestinationType.EXTERNAL_APP, packageName, label, "app", createdAt = now())
        addDestination(dest)
        return dest.id
    }

    fun addOfflineActivity(name: String, icon: String): String {
        val existing = store.current.destinations.firstOrNull { it.type == DestinationType.OFFLINE_ACTIVITY && it.displayName.equals(name.trim(), true) }
        if (existing != null) return existing.id
        val dest = Destination(newId("dest"), DestinationType.OFFLINE_ACTIVITY, displayName = name.trim(), icon = icon, createdAt = now())
        addDestination(dest)
        return dest.id
    }

    fun removeDestination(id: String) = update(syncBridge = true) { data ->
        data.copy(
            destinations = data.destinations.filterNot { it.id == id },
            rules = data.rules.map { r -> if (id in r.destinationIds) r.copy(destinationIds = r.destinationIds - id) else r },
        )
    }

    private fun cleanDanglingDestinations() = update { data ->
        val ids = data.destinations.map { it.id }.toSet()
        data.copy(rules = data.rules.map { r -> r.copy(destinationIds = r.destinationIds.filter { it in ids }) })
    }

    // -- Rules ----------------------------------------------------------------

    fun upsertRule(rule: RedirectRule) = update(syncBridge = true) { data ->
        val stamped = rule.copy(updatedAt = now())
        data.copy(rules = if (data.rules.any { it.id == rule.id }) data.rules.map { if (it.id == rule.id) stamped else it } else data.rules + stamped)
    }

    fun newRuleId() = newId("rule")

    fun deleteRule(id: String) = update(syncBridge = true) { it.copy(rules = it.rules.filterNot { r -> r.id == id }) }

    fun duplicateRule(id: String) = update(syncBridge = true) { data ->
        val src = data.rules.firstOrNull { it.id == id } ?: return@update data
        val copy = src.copy(id = newId("rule"), name = "${src.name} (copy)", enabled = false, createdAt = now(), updatedAt = now())
        val index = data.rules.indexOf(src)
        data.copy(rules = data.rules.toMutableList().apply { add(index + 1, copy) })
    }

    fun setRuleEnabled(id: String, enabled: Boolean) = updateRule(id) { it.copy(enabled = enabled, pausedUntil = null) }

    fun pauseRule(id: String, until: Long) = updateRule(id) { it.copy(pausedUntil = until, enabled = true) }

    private fun updateRule(id: String, transform: (RedirectRule) -> RedirectRule) = update(syncBridge = true) { data ->
        data.copy(rules = data.rules.map { if (it.id == id) transform(it).copy(updatedAt = now()) else it })
    }
}

fun SeedData.CatalogEntry.toBook(now: Long) = Book(
    id = id,
    title = title,
    author = author,
    description = description,
    coverTint = tint,
    file = assetPath?.let { BookFile.asset(it) },
    source = source,
    createdAt = now,
    updatedAt = now,
)
