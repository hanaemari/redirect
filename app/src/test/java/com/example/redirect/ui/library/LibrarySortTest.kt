package com.example.redirect.ui.library

import com.example.redirect.data.Book
import com.example.redirect.data.BookSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {

    private fun book(id: String, lastOpenedAt: Long?, createdAt: Long = 0) = Book(
        id = id, title = id, author = "A", source = BookSource.BUNDLED,
        createdAt = createdAt, updatedAt = 0, lastOpenedAt = lastOpenedAt,
    )

    private val mixed = listOf(
        book("never", null, createdAt = 2),
        book("old", 1_000L),
        book("recent", 5_000_000_000L),
        book("also-never", null, createdAt = 1),
    )

    /** Regression: this threw ClassCastException (Long vs Integer) and crashed the Library tab. */
    @Test fun recentSortHandlesOpenedAndUnopenedBooks() {
        assertEquals(listOf("recent", "old", "never", "also-never"), sortBooks(mixed, SortOrder.RECENT).map { it.id })
    }

    @Test fun everySortOrderWorksOnAMixedLibrary() {
        SortOrder.entries.forEach { sortBooks(mixed, it) }
    }

    @Test fun emptyLibrary() {
        SortOrder.entries.forEach { assertEquals(emptyList<Book>(), sortBooks(emptyList(), it)) }
    }
}
