package com.example.redirect.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Derived values shared by several screens. */

val AppData.libraryBooks: List<Book> get() = books.filter { it.inLibrary }

/** The book "Continue reading" and CURRENT_BOOK destinations point at. */
val AppData.currentBook: Book?
    get() {
        val explicit = preferences.currentBookId?.let { id -> books.firstOrNull { it.id == id && it.inLibrary && it.isReadable } }
        return explicit
            ?: libraryBooks.filter { it.status == ReadingStatus.READING && it.isReadable }.maxByOrNull { it.lastOpenedAt ?: 0 }
    }

val AppData.recentBooks: List<Book>
    get() = libraryBooks.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt }

fun AppData.trigger(id: String): TriggerApp? = triggerApps.firstOrNull { it.id == id }
    ?: SeedData.triggerCatalog.firstOrNull { it.id == id }

fun AppData.destination(id: String): Destination? = destinations.firstOrNull { it.id == id }

fun AppData.bookmarksFor(bookId: String) = bookmarks.filter { it.bookId == bookId }.sortedBy { it.progress }

fun AppData.minutesReadOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
    val ms = sessions.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == date }
        .sumOf { it.durationMillis.coerceAtMost(3 * 60 * 60_000L) }
    return (ms / 60_000L).toInt()
}

fun AppData.isAvailable(d: Destination, isInstalled: (String) -> Boolean): Boolean = when (d.type) {
    DestinationType.CURRENT_BOOK -> currentBook != null
    DestinationType.SPECIFIC_BOOK -> books.firstOrNull { it.id == d.targetId }?.let { it.isReadable && it.inLibrary } == true
    DestinationType.LIBRARY, DestinationType.OFFLINE_ACTIVITY, DestinationType.CHOICE_SCREEN -> true
    // An app that is itself on the distracting list would just be interrupted again: never redirect to it.
    DestinationType.EXTERNAL_APP -> d.targetId?.let { it in distractingPackages() || !isInstalled(it) } == false
}

/** Every package (including regional variants) of the apps the user chose to interrupt. */
fun AppData.distractingPackages(): Set<String> =
    selectedTriggerIds.mapNotNull { trigger(it) }
        .filter { it.kind == TriggerKind.APP }
        .flatMap { listOfNotNull(it.packageName) + it.altPackageNames }
        .toSet()

/** Human-readable label for a destination, resolving "current book" to its title. */
fun AppData.describe(d: Destination): String = when (d.type) {
    DestinationType.CURRENT_BOOK -> currentBook?.let { "Continue ${it.title}" } ?: "Continue my current book"
    DestinationType.SPECIFIC_BOOK -> books.firstOrNull { it.id == d.targetId }?.let { "Read ${it.title}" } ?: d.displayName
    else -> d.displayName
}
