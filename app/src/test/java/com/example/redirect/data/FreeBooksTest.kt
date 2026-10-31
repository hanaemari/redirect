package com.example.redirect.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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

    @Test fun catalogFallsBackToOpdsWhenGutendexFails() = kotlinx.coroutines.runBlocking {
        val http = object : Http() {
            override fun getText(url: String, timeoutMillis: Int): String =
                if ("gutendex" in url) throw java.net.SocketTimeoutException("slow")
                else "<entry><id>https://www.gutenberg.org/ebooks/84.opds</id><title>Frankenstein</title><content type=\"text\">Mary Shelley</content></entry>"
        }
        val page = FreeBookCatalog(http).browse("frankenstein")
        assertEquals("Project Gutenberg", page.source)
        assertEquals(84, page.books.single().gutenbergId)
        assertNull(page.nextUrl)
    }
}
