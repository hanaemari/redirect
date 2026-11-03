package com.example.redirect.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A public-domain book that can be downloaded from Project Gutenberg. */
@Serializable
data class FreeBook(
    val gutenbergId: Int,
    val title: String,
    val author: String,
    val summary: String = "",
    val subjects: List<String> = emptyList(),
    val coverUrl: String? = null,
    val epubUrl: String,
    val downloads: Int = 0,
    /** True when the source said "not copyrighted" for this book; false means check before downloading. */
    val rightsVerified: Boolean,
) {
    /** Stable library id, so the same book is never downloaded twice. */
    val bookId: String get() = bookIdFor(gutenbergId)

    companion object {
        fun bookIdFor(gutenbergId: Int) = "gutenberg-$gutenbergId"
    }
}

@Serializable
data class FreeBookPage(val books: List<FreeBook>, val nextUrl: String?, val source: String)

/** Extra metadata from a book's own Gutenberg record, fetched when its details are opened. */
data class FreeBookDetails(val summary: String, val subjects: List<String>, val publicDomain: Boolean)

/** A first page of results saved earlier, shown instantly while a refresh runs. */
@Serializable
data class CachedPage(val page: FreeBookPage, val savedAt: Long)

class FreeBookException(message: String) : Exception(message)

class TooLargeException(url: String) : IOException("File is too large: $url")

/**
 * Project Gutenberg discovery.
 *
 * Gutenberg's own OPDS catalog is the primary source: measured at 0.2–0.4 s per request, versus
 * Gutendex at 62–90 s (and HTTP 503s) whenever it hasn't recently served the same query. OPDS search
 * results carry only title/author, so summaries and the public-domain check come from each book's
 * own record ([details]) when it is opened or downloaded. Gutendex stays as the fallback if
 * gutenberg.org is unreachable.
 *
 * First pages are cached in memory and on disk ([cached]) so repeat visits are instant. Requests
 * are cancellable: leaving Discover or typing a new query closes the old connection.
 */
class FreeBookCatalog(
    private val http: Http = Http(),
    /** Where first pages are saved between launches; null disables the disk cache. */
    private val cacheDir: File? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val memory = object : LinkedHashMap<String, CachedPage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedPage>?) = size > MAX_CACHED_QUERIES
    }
    private val detailsCache = java.util.concurrent.ConcurrentHashMap<Int, FreeBookDetails>()

    /** The last saved first page for [query], if any. Doesn't touch the network. */
    suspend fun cached(query: String): CachedPage? = withContext(Dispatchers.IO) {
        val key = keyFor(query)
        synchronized(memory) { memory[key] }?.let { return@withContext it }
        val file = cacheFile(key) ?: return@withContext null
        val saved = runCatching { json.decodeFromString<CachedPage>(file.readText()) }.getOrNull() ?: return@withContext null
        synchronized(memory) { memory[key] = saved }
        saved
    }

    fun isFresh(cached: CachedPage): Boolean = clock() - cached.savedAt in 0 until FRESH_FOR_MS

    /** Fetches the first page for [query] from the network and saves it. */
    suspend fun browse(query: String): FreeBookPage {
        val q = query.trim()
        val page = try {
            fetchOpds(opdsSearchUrl(q))
        } catch (e: IOException) {
            try {
                fetchGutendex(gutendexUrl(q))
            } catch (e2: IOException) {
                throw FreeBookException("Couldn't reach Project Gutenberg. Check your connection and try again.")
            }
        }
        save(keyFor(q), CachedPage(page, clock()))
        return page
    }

    suspend fun nextPage(nextUrl: String): FreeBookPage = try {
        if ("gutendex.com" in nextUrl) fetchGutendex(nextUrl) else fetchOpds(nextUrl)
    } catch (e: IOException) {
        throw FreeBookException("Couldn't load more books. Check your connection and try again.")
    }

    /** Summary, subjects and rights from the book's own Gutenberg record (cached in memory). */
    suspend fun details(gutenbergId: Int): FreeBookDetails {
        detailsCache[gutenbergId]?.let { return it }
        val body = fetchText("https://www.gutenberg.org/ebooks/$gutenbergId.opds", OPDS_TIMEOUT_MS)
        return GutenbergOpds.parseBook(body).also { detailsCache[gutenbergId] = it }
    }

    /** Confirms a book is public domain in the USA according to Project Gutenberg's own record. */
    suspend fun verifyRights(gutenbergId: Int): Boolean = details(gutenbergId).publicDomain

    private suspend fun fetchOpds(url: String): FreeBookPage {
        val body = fetchText(url, OPDS_TIMEOUT_MS)
        return FreeBookPage(GutenbergOpds.parseSearch(body), GutenbergOpds.nextUrl(body), source = "Project Gutenberg")
    }

    private suspend fun fetchGutendex(url: String): FreeBookPage =
        Gutendex.parse(fetchText(url, GUTENDEX_TIMEOUT_MS))

    /**
     * Runs a blocking request off the main thread. Cancelling the coroutine closes the connection;
     * plain coroutine cancellation can't interrupt a blocked HttpURLConnection read.
     */
    private suspend fun fetchText(url: String, timeoutMillis: Int): String = suspendCancellableCoroutine { cont ->
        val call = Http.Call()
        cont.invokeOnCancellation { call.cancel() }
        Dispatchers.IO.asExecutor().execute {
            // Resuming an already-cancelled continuation is ignored.
            cont.resumeWith(runCatching { http.getText(url, timeoutMillis, call) })
        }
    }

    private fun keyFor(query: String) = query.trim().lowercase()

    private fun cacheFile(key: String): File? =
        cacheDir?.let { File(it, "q-${key.hashCode().toUInt()}.json") }?.takeIf { it.exists() }

    private suspend fun save(key: String, cached: CachedPage) = withContext(Dispatchers.IO) {
        synchronized(memory) { memory[key] = cached }
        val dir = cacheDir ?: return@withContext
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "q-${key.hashCode().toUInt()}.json.tmp")
            tmp.writeText(json.encodeToString(CachedPage.serializer(), cached))
            tmp.renameTo(File(dir, "q-${key.hashCode().toUInt()}.json"))
            // Keep the cache small: only the most recent queries.
            dir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_CACHED_QUERIES)?.forEach { it.delete() }
        }
    }

    /**
     * Downloads the EPUB to [dest]. Illustrated editions can be huge (Monte Cristo is 84 MB, its
     * text-only edition 1.3 MB), so the illustrated one is used only when it is reasonably small.
     */
    suspend fun download(book: FreeBook, dest: File) = withContext(Dispatchers.IO) {
        try {
            http.download(book.epubUrl, dest, maxBytes = MAX_ILLUSTRATED_BYTES)
        } catch (e: TooLargeException) {
            http.download(textOnlyUrlFor(book.gutenbergId), dest, maxBytes = MAX_EPUB_BYTES)
        }
    }

    /** Best-effort cover download; Gutenberg EPUBs usually carry their own cover anyway. */
    suspend fun downloadCover(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching { http.download(url, dest, maxBytes = 5L * 1024 * 1024) }.isSuccess
    }

    companion object {
        /** Read timeouts; connecting is capped separately by [Http.CONNECT_TIMEOUT_MS]. */
        const val OPDS_TIMEOUT_MS = 10_000
        const val GUTENDEX_TIMEOUT_MS = 15_000
        /** Cached results younger than this are shown without asking the network again. */
        const val FRESH_FOR_MS = 6 * 60 * 60 * 1000L
        const val MAX_CACHED_QUERIES = 20
        const val MAX_EPUB_BYTES = 100L * 1024 * 1024
        const val MAX_ILLUSTRATED_BYTES = 15L * 1024 * 1024

        fun opdsSearchUrl(q: String) =
            if (q.isEmpty()) "https://www.gutenberg.org/ebooks/search.opds/?sort_order=downloads"
            else "https://www.gutenberg.org/ebooks/search.opds/?query=${URLEncoder.encode(q, "UTF-8")}"

        fun gutendexUrl(q: String) = buildString {
            append("https://gutendex.com/books/?copyright=false&mime_type=application%2Fepub")
            if (q.isNotEmpty()) append("&search=").append(URLEncoder.encode(q, "UTF-8"))
        }

        fun epubUrlFor(id: Int) = "https://www.gutenberg.org/ebooks/$id.epub3.images"
        fun textOnlyUrlFor(id: Int) = "https://www.gutenberg.org/ebooks/$id.epub.noimages"
        fun coverUrlFor(id: Int) = "https://www.gutenberg.org/cache/epub/$id/pg$id.cover.medium.jpg"
    }
}

/** Gutendex JSON → [FreeBook]. Pure, so it is unit tested. */
object Gutendex {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Response(val count: Int = 0, val next: String? = null, val results: List<Result> = emptyList())

    @Serializable
    data class Result(
        val id: Int,
        val title: String = "",
        val authors: List<Person> = emptyList(),
        val summaries: List<String> = emptyList(),
        val subjects: List<String> = emptyList(),
        val copyright: Boolean? = null,
        @SerialName("media_type") val mediaType: String = "",
        val formats: Map<String, String> = emptyMap(),
        @SerialName("download_count") val downloadCount: Int = 0,
    )

    @Serializable
    data class Person(val name: String = "")

    fun parse(body: String): FreeBookPage {
        val response = try {
            json.decodeFromString<Response>(body)
        } catch (e: Exception) {
            throw IOException("Unexpected response from Gutendex", e)
        }
        val books = response.results.mapNotNull { r ->
            // copyright == null means "unknown"; only list books known to be free.
            if (r.copyright != false || r.mediaType != "Text") return@mapNotNull null
            val epub = r.formats.entries.firstOrNull { it.key.startsWith("application/epub+zip") }?.value
                ?: return@mapNotNull null
            FreeBook(
                gutenbergId = r.id,
                title = r.title.trim().ifEmpty { "Untitled" },
                author = r.authors.firstOrNull()?.name?.let(::displayAuthor) ?: "Unknown author",
                summary = r.summaries.firstOrNull()?.removeSuffix("(This is an automatically generated summary.)")?.trim().orEmpty(),
                subjects = r.subjects.map { it.substringBefore(" -- ") }.distinct().take(4),
                coverUrl = r.formats["image/jpeg"],
                epubUrl = epub.replace("http://", "https://"),
                downloads = r.downloadCount,
                rightsVerified = true,
            )
        }
        return FreeBookPage(books, response.next, source = "Gutendex")
    }
}

/** Minimal reader for Project Gutenberg's OPDS (Atom) feeds. */
object GutenbergOpds {
    private val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val idRegex = Regex("<id>https?://www\\.gutenberg\\.org/ebooks/(\\d+)\\.opds</id>")
    private val titleRegex = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val contentRegex = Regex("<content type=\"text\">(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
    private val downloadsRegex = Regex("""[\d,]+ downloads?""")
    private val rightsRegex = Regex("<rights>(.*?)</rights>", RegexOption.DOT_MATCHES_ALL)
    private val nextRegex = Regex("<link[^>]*rel=\"next\"[^>]*href=\"([^\"]+)\"")
    private val summaryRegex = Regex("<p>\\s*Summary:\\s*(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
    private val subjectRegex = Regex("<p>Subject: (.*?)</p>")

    fun parseSearch(body: String): List<FreeBook> = entryRegex.findAll(body).mapNotNull { m ->
        val entry = m.groupValues[1]
        // Non-book entries ("Authors", "Subjects", "Sort by…") have other ids.
        val id = idRegex.find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
        FreeBook(
            gutenbergId = id,
            title = titleRegex.find(entry)?.groupValues?.get(1)?.let(::unescape)?.trim().orEmpty().ifEmpty { "Untitled" },
            // Books without an author carry their download count here instead ("89341 downloads").
            author = contentRegex.find(entry)?.groupValues?.get(1)?.let(::unescape)?.trim().orEmpty()
                .takeUnless { it.isEmpty() || downloadsRegex.matches(it) } ?: "Unknown author",
            coverUrl = FreeBookCatalog.coverUrlFor(id),
            epubUrl = FreeBookCatalog.epubUrlFor(id),
            rightsVerified = false,
        )
    }.distinctBy { it.gutenbergId }.toList()

    /** Absolute URL of the next results page, or null on the last page. */
    fun nextUrl(body: String): String? = nextRegex.find(body)?.groupValues?.get(1)?.let { href ->
        URL(URL("https://www.gutenberg.org/"), unescape(href)).toString()
    }

    /** Fields of a single book's record (`/ebooks/{id}.opds`). */
    fun parseBook(body: String) = FreeBookDetails(
        summary = summaryRegex.find(body)?.groupValues?.get(1)?.let(::unescape)
            ?.replace(Regex("\\s+"), " ")?.trim()?.removeSuffix("(This is an automatically generated summary.)")?.trim().orEmpty(),
        subjects = subjectRegex.findAll(body).map { unescape(it.groupValues[1]).substringBefore(" -- ") }.distinct().take(4).toList(),
        publicDomain = isPublicDomain(body),
    )

    fun isPublicDomain(body: String): Boolean =
        rightsRegex.find(body)?.groupValues?.get(1)?.contains("public domain", ignoreCase = true) == true

    private fun unescape(s: String) = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
}

/** "Thoreau, Henry David" → "Henry David Thoreau"; "Marcus Aurelius, Emperor of Rome" stays as is. */
fun displayAuthor(name: String): String {
    val parts = name.split(", ", limit = 2)
    // A multi-word part before the comma is a full name followed by a title, not "Surname, Given".
    if (parts.size < 2 || parts[0].trim().contains(' ')) return parts[0].trim()
    return "${parts[1].trim()} ${parts[0].trim()}"
}

/** Tiny HTTP helper over HttpURLConnection, to avoid pulling in an HTTP library. */
open class Http {
    /** Lets another thread abort a request by closing its connection. */
    class Call {
        @Volatile private var conn: HttpURLConnection? = null
        @Volatile var cancelled = false
            private set

        fun cancel() {
            cancelled = true
            conn?.disconnect()
        }

        internal fun attach(c: HttpURLConnection) {
            conn = c
            if (cancelled) c.disconnect()
        }
    }

    open fun getText(url: String, timeoutMillis: Int, call: Call? = null): String {
        val conn = open(url, timeoutMillis, call)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    open fun download(url: String, dest: File, maxBytes: Long) {
        val conn = open(url, 30_000)
        val tmp = File(dest.parentFile, dest.name + ".part")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            if (conn.contentLengthLong > maxBytes) throw TooLargeException(url)
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw TooLargeException(url)
                        out.write(buf, 0, n)
                    }
                }
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } finally {
            tmp.delete()
            conn.disconnect()
        }
    }

    private fun open(url: String, timeoutMillis: Int, call: Call? = null): HttpURLConnection {
        var current = URL(url)
        // HttpURLConnection doesn't follow http→https redirects, so follow a few by hand.
        repeat(5) {
            if (call?.cancelled == true) throw IOException("Cancelled")
            val conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = timeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Redirect-Android/0.1 (personal reading app)")
            }
            call?.attach(conn)
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: return conn
                conn.disconnect()
                current = URL(current, location)
            } else {
                return conn
            }
        }
        throw IOException("Too many redirects for $url")
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
    }
}
