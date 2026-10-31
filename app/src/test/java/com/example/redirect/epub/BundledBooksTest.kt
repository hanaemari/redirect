package com.example.redirect.epub

import com.example.redirect.data.SeedData
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every bundled public-domain EPUB must open with chapters and a table of contents. */
class BundledBooksTest {
    @Test fun allBundledBooksParse() {
        for (entry in SeedData.bundledBooks) {
            val file = File("src/main/assets/${entry.assetPath}")
            assertTrue("missing ${file.path}", file.exists())
            val book = EpubParser.parse(file)
            assertTrue("${entry.id} has no chapters", book.spine.size > 1)
            assertTrue("${entry.id} has no table of contents", book.toc.isNotEmpty())
            println("${entry.id}: ${book.title} — ${book.spine.size} sections, ${book.toc.size} toc entries")
        }
    }
}
