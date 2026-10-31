package com.example.redirect.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun epub(files: Map<String, String>): File {
        val f = tmp.newFile("book.epub")
        ZipOutputStream(f.outputStream()).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return f
    }

    private val container = """<?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>"""

    private fun chapter(title: String) = """<?xml version="1.0" encoding="utf-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>$title</title></head><body><p>$title text</p></body></html>"""

    @Test fun parsesEpub3WithNavAndCover() {
        val opf = """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>The Quiet Hour</dc:title><dc:creator>A. Writer</dc:creator>
                <dc:description>&lt;p&gt;A calm book.&lt;/p&gt;</dc:description>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="c1" href="text/ch%201.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                <item id="img" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
              </manifest>
              <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
            </package>"""
        val nav = """<?xml version="1.0"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
              <nav epub:type="toc"><ol>
                <li><a href="text/ch%201.xhtml">One</a></li>
                <li><a href="text/ch2.xhtml#part">Two</a><ol><li><a href="text/ch2.xhtml#sub">Two, sub</a></li></ol></li>
              </ol></nav></body></html>"""
        val book = EpubParser.parse(
            epub(
                mapOf(
                    "mimetype" to "application/epub+zip",
                    "META-INF/container.xml" to container,
                    "OEBPS/content.opf" to opf,
                    "OEBPS/nav.xhtml" to nav,
                    "OEBPS/text/ch 1.xhtml" to chapter("One"),
                    "OEBPS/text/ch2.xhtml" to chapter("Two"),
                    "OEBPS/images/cover.jpg" to "jpg",
                )
            )
        )
        assertEquals("The Quiet Hour", book.title)
        assertEquals("A. Writer", book.author)
        assertEquals("A calm book.", book.description)
        assertEquals(listOf("OEBPS/text/ch 1.xhtml", "OEBPS/text/ch2.xhtml"), book.spine.map { it.path })
        assertEquals("OEBPS/images/cover.jpg", book.coverPath)
        assertEquals(listOf("One", "Two", "Two, sub"), book.toc.map { it.title })
        assertEquals(listOf(0, 1, 1), book.toc.map { it.spineIndex })
        assertEquals("part", book.toc[1].fragment)
        assertEquals(1, book.toc[2].depth)
        assertEquals("Two", book.chapterTitleFor(1))
    }

    @Test fun parsesEpub2WithNcx() {
        val opf = """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Old Book</dc:title><meta name="cover" content="cov"/></metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="c1" href="c1.html" media-type="application/xhtml+xml"/>
                <item id="notes" href="notes.html" media-type="application/xhtml+xml"/>
                <item id="cov" href="cover.png" media-type="image/png"/>
              </manifest>
              <spine toc="ncx"><itemref idref="c1"/><itemref idref="notes" linear="no"/></spine>
            </package>"""
        val ncx = """<?xml version="1.0"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
              <navPoint id="p1"><navLabel><text>Chapter  One</text></navLabel><content src="c1.html"/></navPoint>
            </navMap></ncx>"""
        val book = EpubParser.parse(
            epub(
                mapOf(
                    "META-INF/container.xml" to container,
                    "OEBPS/content.opf" to opf,
                    "OEBPS/toc.ncx" to ncx,
                    "OEBPS/c1.html" to chapter("One"),
                    "OEBPS/notes.html" to chapter("Notes"),
                    "OEBPS/cover.png" to "png",
                )
            )
        )
        assertEquals("Old Book", book.title)
        assertNull(book.author)
        assertEquals(1, book.spine.size)
        assertEquals("OEBPS/cover.png", book.coverPath)
        assertEquals("Chapter One", book.toc.single().title)
    }

    @Test fun rejectsNonEpubFiles() {
        val notZip = tmp.newFile("x.epub").apply { writeText("hello") }
        expect(EpubException.Reason.NOT_ZIP) { EpubParser.parse(notZip) }
        val zipNoContainer = epub(mapOf("readme.txt" to "hi"))
        expect(EpubException.Reason.NOT_EPUB) { EpubParser.parse(zipNoContainer) }
    }

    @Test fun rejectsDrm() {
        val f = epub(
            mapOf(
                "META-INF/container.xml" to container,
                "META-INF/encryption.xml" to """<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
                    <enc:EncryptedData><enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/></enc:EncryptedData></encryption>""",
            )
        )
        expect(EpubException.Reason.DRM_PROTECTED) { EpubParser.parse(f) }
    }

    @Test fun progressIsWeightedByChapterSize() {
        val book = EpubBook("t", null, null, listOf(SpineItem("a", "x", 100), SpineItem("b", "x", 300)), emptyList(), null, null)
        assertEquals(0f, book.progressOf(0, 0f), 0.001f)
        assertEquals(0.25f, book.progressOf(1, 0f), 0.001f)
        assertEquals(1f, book.progressOf(1, 1f), 0.001f)
    }

    @Test fun resolvesRelativePaths() {
        assertEquals("OEBPS/images/a.png", EpubParser.resolve("OEBPS/text", "../images/a.png"))
        assertEquals("a b.html", EpubParser.resolve("", "a%20b.html"))
        assertTrue(EpubParser.resolve("x", "./y.html") == "x/y.html")
    }

    private fun expect(reason: EpubException.Reason, block: () -> Unit) {
        try {
            block()
            fail("Expected $reason")
        } catch (e: EpubException) {
            assertEquals(reason, e.reason)
        }
    }
}
