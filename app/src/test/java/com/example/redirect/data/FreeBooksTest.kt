package com.example.redirect.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlinx.coroutines.launch

class FreeBooksTest {

    private val gutendexJson = """
        {"count":3,"next":"https://gutendex.com/books/?page=2&search=x","previous":null,"results":[
          {"id":205,"title":"Walden, and On The Duty Of Civil Disobedience",
           "authors":[{"name":"Thoreau, Henry David","birth_year":1817,"death_year":1862}],
           "summaries":["A book about a pond. (This is an automatically generated summary.)"],
           "subjects":["Solitude","Wilderness areas -- Massachusetts -- Walden Woods"],
           "copyright":false,"media_type":"Text",
           "formats":{"application/epub+zip":"http://www.gutenberg.org/ebooks/205.epub3.images",
                      "image/jpeg":"https://www.gutenberg.org/cache/epub/205/pg205.cover.medium.jpg"},
           "download_count":51422},
          {"id":999,"title":"Still Copyrighted","authors":[],"copyright":true,"media_type":"Text",
           "formats":{"application/epub+zip":"https://example.org/x.epub"}},
          {"id":1000,"title":"Unknown Rights","authors":[],"copyright":null,"media_type":"Text",
           "formats":{"application/epub+zip":"https://example.org/y.epub"}},
          {"id":1001,"title":"Audio Book","authors":[],"copyright":false,"media_type":"Sound","formats":{}},
          {"id":1002,"title":"No Epub","authors":[],"copyright":false,"media_type":"Text",
           "formats":{"text/plain":"https://example.org/z.txt"}}
        ]}
    """.trimIndent()

    @Test fun gutendexKeepsOnlyFreeEpubTextBooks() {
        val page = Gutendex.parse(gutendexJson)
        assertEquals(listOf(205), page.books.map { it.gutenbergId })
        assertEquals("https://gutendex.com/books/?page=2&search=x", page.nextUrl)
    }

    @Test fun gutendexMapsFields() {
        val b = Gutendex.parse(gutendexJson).books.single()
        assertEquals("Henry David Thoreau", b.author)
        assertEquals("A book about a pond.", b.summary)
        assertEquals(listOf("Solitude", "Wilderness areas"), b.subjects)
        assertEquals("https://www.gutenberg.org/ebooks/205.epub3.images", b.epubUrl)
        assertTrue(b.rightsVerified)
        assertEquals("gutenberg-205", b.bookId)
    }

    @Test(expected = IOException::class)
    fun gutendexGarbageIsAnIoError() {
        Gutendex.parse("<html>502 Bad Gateway</html>")
    }

    @Test fun opdsSearchSkipsNavigationEntries() {
        val feed = """
            <feed>
            <entry><id>https://www.gutenberg.org/ebooks/authors/search.opds/?query=walden</id><title>Authors</title></entry>
            <entry>
            <id>https://www.gutenberg.org/ebooks/205.opds</id>
            <title>Walden, and On The Duty Of Civil Disobedience</title>
            <content type="text">Henry David Thoreau</content>
            </entry>
            <entry>
            <id>https://www.gutenberg.org/ebooks/26289.opds</id>
            <title>Walden &amp; Other Writings</title>
            <content type="text">Henry David Thoreau</content>
            </entry>
            </feed>
        """.trimIndent()
        val books = GutenbergOpds.parseSearch(feed)
        assertEquals(listOf(205, 26289), books.map { it.gutenbergId })
        assertEquals("Walden & Other Writings", books[1].title)
        assertEquals("Henry David Thoreau", books[0].author)
        assertFalse("OPDS listings must be rights-checked before download", books[0].rightsVerified)
        assertEquals("https://www.gutenberg.org/ebooks/205.epub3.images", books[0].epubUrl)
    }

    @Test fun opdsDownloadCountIsNotAnAuthor() {
        val feed = "<entry><id>https://www.gutenberg.org/ebooks/37683.opds</id><title>Dictionary</title>" +
            "<content type=\"text\">89341 downloads</content></entry>"
        assertEquals("Unknown author", GutenbergOpds.parseSearch(feed).single().author)
    }

    @Test fun opdsRights() {
        assertTrue(GutenbergOpds.isPublicDomain("<entry><rights>Public domain in the USA.</rights></entry>"))
        assertFalse(GutenbergOpds.isPublicDomain("<entry><rights>Copyrighted. Read the copyright notice inside this book for details.</rights></entry>"))
        assertFalse(GutenbergOpds.isPublicDomain("<entry></entry>"))
    }

    @Test fun authorNames() {
        assertEquals("Jane Austen", displayAuthor("Austen, Jane"))
        assertEquals("Homer", displayAuthor("Homer"))
        assertEquals("Marcus Aurelius", displayAuthor("Marcus Aurelius, Emperor of Rome"))
    }

    @Test fun hugeIllustratedEditionFallsBackToTextOnly() = kotlinx.coroutines.runBlocking {
        val requested = mutableListOf<String>()
        val http = object : Http() {
            override fun download(url: String, dest: java.io.File, maxBytes: Long) {
                requested += url
                if ("images" in url && "noimages" !in url) throw TooLargeException(url)
            }
        }
        val book = GutenbergOpds.parseSearch(
            "<entry><id>https://www.gutenberg.org/ebooks/1184.opds</id><title>Monte Cristo</title></entry>",
        ).single()
        FreeBookCatalog(http).download(book, java.io.File("unused"))
        assertEquals(
            listOf("https://www.gutenberg.org/ebooks/1184.epub3.images", "https://www.gutenberg.org/ebooks/1184.epub.noimages"),
            requested,
        )
    }

    private val opdsPage = "<feed><link rel=\"next\" title=\"Next Page\" href=\"/ebooks/search.opds/?query=frankenstein&amp;start_index=26\"/>" +
        "<entry><id>https://www.gutenberg.org/ebooks/84.opds</id><title>Frankenstein</title><content type=\"text\">Mary Shelley</content></entry></feed>"

    /** Records requested URLs; answers OPDS with [opdsPage] unless told gutenberg.org is down. */
    private class FakeHttp(val opds: String, val gutenbergDown: Boolean = false, val gutendex: String = "") : Http() {
        val requested = mutableListOf<String>()
        override fun getText(url: String, timeoutMillis: Int, call: Call?): String {
            synchronized(requested) { requested += url }
            return when {
                "gutendex" in url -> gutendex.ifEmpty { throw java.net.SocketTimeoutException("slow") }
                gutenbergDown -> throw java.net.UnknownHostException("www.gutenberg.org")
                else -> opds
            }
        }
    }

    @Test fun gutenbergCatalogIsPrimaryAndGutendexIsNotCalled() = kotlinx.coroutines.runBlocking {
        val http = FakeHttp(opdsPage)
        val page = FreeBookCatalog(http).browse("frankenstein")
        assertEquals("Project Gutenberg", page.source)
        assertEquals(84, page.books.single().gutenbergId)
        assertEquals("https://www.gutenberg.org/ebooks/search.opds/?query=frankenstein&start_index=26", page.nextUrl)
        assertEquals(listOf("https://www.gutenberg.org/ebooks/search.opds/?query=frankenstein"), http.requested)
    }

    @Test fun gutendexIsUsedWhenGutenbergIsUnreachable() = kotlinx.coroutines.runBlocking {
        val http = FakeHttp(opdsPage, gutenbergDown = true, gutendex = gutendexJson)
        val page = FreeBookCatalog(http).browse("walden")
        assertEquals("Gutendex", page.source)
        assertEquals(listOf(205), page.books.map { it.gutenbergId })
    }

    @Test(expected = FreeBookException::class)
    fun bothSourcesDownIsAFriendlyError(): Unit = kotlinx.coroutines.runBlocking {
        FreeBookCatalog(FakeHttp(opdsPage, gutenbergDown = true)).browse("x")
        Unit
    }

    @Test fun firstPagesAreCachedOnDiskAcrossInstances() = kotlinx.coroutines.runBlocking {
        val dir = java.nio.file.Files.createTempDirectory("discover").toFile()
        var now = 1_000_000L
        val first = FreeBookCatalog(FakeHttp(opdsPage), dir) { now }
        assertNull(first.cached("Frankenstein"))
        first.browse("Frankenstein")

        // A new instance (e.g. after the app restarts) reads it from disk, case-insensitively.
        val http = FakeHttp(opdsPage)
        val second = FreeBookCatalog(http, dir) { now }
        val cached = second.cached("frankenstein ")!!
        assertEquals(84, cached.page.books.single().gutenbergId)
        assertTrue(second.isFresh(cached))
        assertTrue("cache read must not hit the network", http.requested.isEmpty())

        now += FreeBookCatalog.FRESH_FOR_MS + 1
        assertFalse(second.isFresh(cached))
        dir.deleteRecursively()
        Unit
    }

    @Test fun bookRecordGivesSummarySubjectsAndRights() {
        val record = """
            <entry><content type="xhtml"><div>
            <p>
            Summary:
            "Frankenstein" is a novel &amp; more. (This is an automatically generated summary.)
            </p>
            <p>Subject: Science fiction</p><p>Subject: Monsters -- Fiction</p>
            </div></content>
            <rights>Public domain in the USA.</rights></entry>
        """.trimIndent()
        val d = GutenbergOpds.parseBook(record)
        assertEquals("\"Frankenstein\" is a novel & more.", d.summary)
        assertEquals(listOf("Science fiction", "Monsters"), d.subjects)
        assertTrue(d.publicDomain)
        assertFalse(GutenbergOpds.parseBook("<entry><rights>Copyrighted. Read the copyright notice inside this book for details.</rights></entry>").publicDomain)
    }

    @Test fun lastPageHasNoNextLink() {
        assertNull(GutenbergOpds.nextUrl("<feed><entry></entry></feed>"))
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    @Test fun cancellingABrowseClosesTheConnection() = kotlinx.coroutines.runBlocking {
        val cancelled = java.util.concurrent.CountDownLatch(1)
        val http = object : Http() {
            override fun getText(url: String, timeoutMillis: Int, call: Call?): String {
                // Simulates a read stuck on a slow server until the call is cancelled.
                while (call?.cancelled != true) Thread.sleep(5)
                cancelled.countDown()
                throw java.net.SocketException("closed")
            }
        }
        val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) { runCatching { FreeBookCatalog(http).browse("slow") } }
        kotlinx.coroutines.delay(50)
        job.cancel()
        assertTrue("connection should be closed promptly", cancelled.await(2, java.util.concurrent.TimeUnit.SECONDS))
    }
}
