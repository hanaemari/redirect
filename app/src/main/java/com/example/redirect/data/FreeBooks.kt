package com.example.redirect.data

import kotlinx.coroutines.Dispatchers
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

data class FreeBookPage(val books: List<FreeBook>, val nextUrl: String?, val source: String)

class FreeBookException(message: String) : Exception(message)

class TooLargeException(url: String) : IOException("File is too large: $url")

/**
 * Project Gutenberg discovery. Gutendex (https://gutendex.com) is the primary source because it
 * returns rights, summaries and formats as JSON. It is a free community service and was measured
 * at 15–74 s per request, so on timeout or error we fall back to Gutenberg's own OPDS catalog,
 * which is fast but sparse; books found that way have their rights checked before download.
 */
class FreeBookCatalog(
    private val http: Http = Http(),
) {
    /** Recent first pages, so returning to Discover doesn't wait on the network again. */
    private val cache = object : LinkedHashMap<String, FreeBookPage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FreeBookPage>?) = size > 12
    }

    suspend fun browse(query: String): FreeBookPage = withContext(Dispatchers.IO) {
        val q = query.trim()
        synchronized(cache) { cache[q.lowercase()] }?.let { return@withContext it }
        val url = buildString {
            append("https://gutendex.com/books/?copyright=false&mime_type=application%2Fepub")
            if (q.isNotEmpty()) append("&search=").append(URLEncoder.encode(q, "UTF-8"))
        }
        val page = try {
            fetchGutendex(url)
        } catch (e: IOException) {
            fallbackSearch(q)
        }
        synchronized(cache) { cache[q.lowercase()] = page }
        page
    }

    suspend fun nextPage(nextUrl: String): FreeBookPage = withContext(Dispatchers.IO) {
        try {
            fetchGutendex(nextUrl)
        } catch (e: IOException) {
            throw FreeBookException("Couldn't load more books. Check your connection and try again.")
        }
    }

    private fun fetchGutendex(url: String): FreeBookPage {
        val body = http.getText(url, timeoutMillis = GUTENDEX_TIMEOUT_MS)
        return Gutendex.parse(body)
    }

    private fun fallbackSearch(q: String): FreeBookPage {
        val url = if (q.isEmpty()) "https://www.gutenberg.org/ebooks/search.opds/?sort_order=downloads"
        else "https://www.gutenberg.org/ebooks/search.opds/?query=${URLEncoder.encode(q, "UTF-8")}"
        val body = try {
            http.getText(url, timeoutMillis = OPDS_TIMEOUT_MS)
        } catch (e: IOException) {
            throw FreeBookException("Couldn't reach Project Gutenberg. Check your connection and try again.")
        }
        return FreeBookPage(GutenbergOpds.parseSearch(body), nextUrl = null, source = "Project Gutenberg")
    }

    /** Confirms a book is public domain in the USA according to Project Gutenberg's own record. */
    suspend fun verifyRights(gutenbergId: Int): Boolean = withContext(Dispatchers.IO) {
        val body = http.getText("https://www.gutenberg.org/ebooks/$gutenbergId.opds", timeoutMillis = OPDS_TIMEOUT_MS)
        GutenbergOpds.isPublicDomain(body)
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
        const val GUTENDEX_TIMEOUT_MS = 25_000
        const val OPDS_TIMEOUT_MS = 15_000
        const val MAX_EPUB_BYTES = 100L * 1024 * 1024
        const val MAX_ILLUSTRATED_BYTES = 15L * 1024 * 1024

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
    open fun getText(url: String, timeoutMillis: Int): String {
        val conn = open(url, timeoutMillis)
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

    private fun open(url: String, timeoutMillis: Int): HttpURLConnection {
        var current = URL(url)
        // HttpURLConnection doesn't follow http→https redirects, so follow a few by hand.
        repeat(5) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = timeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Redirect-Android/0.1 (personal reading app)")
            }
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
}
