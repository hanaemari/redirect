package com.example.redirect.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything the app persists for the local (account-less) user.
 *
 * If accounts are introduced later, one AppData document maps to one account;
 * see [AppStore] for where the file location would become per-user.
 */
@Serializable
data class AppData(
    val schemaVersion: Int = 1,
    val preferences: UserPreferences = UserPreferences(),
    val books: List<Book> = emptyList(),
    val readingLists: List<ReadingList> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val sessions: List<ReadingSession> = emptyList(),
    /** Trigger apps the user has chosen to intervene on (ids into [triggerApps]). */
    val selectedTriggerIds: List<String> = emptyList(),
    /** Catalog triggers plus any installed apps the user added. */
    val triggerApps: List<TriggerApp> = emptyList(),
    /** The user's saved alternatives, referenced by rules. */
    val destinations: List<Destination> = emptyList(),
    val rules: List<RedirectRule> = emptyList(),
    /** triggerId -> epoch millis until which interventions are suspended (grace period). */
    val graceUntil: Map<String, Long> = emptyMap(),
)

// ---------------------------------------------------------------------------
// Preferences
// ---------------------------------------------------------------------------

@Serializable
data class UserPreferences(
    val onboardingComplete: Boolean = false,
    val displayName: String = "",
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val reader: ReaderPreferences = ReaderPreferences(),
    /** Daily reading intention in minutes; null when the user hasn't set one. */
    val readingIntentionMinutes: Int? = 10,
    val defaultRedirectStyle: RedirectStyle = RedirectStyle.AUTOMATIC,
    val currentBookId: String? = null,
    /** Master switch for interrupting distracting apps. */
    val interruptionsEnabled: Boolean = true,
    /** Interruptions are suspended until this time (epoch millis). */
    val interruptionsPausedUntil: Long? = null,
)

@Serializable
enum class AppTheme { SYSTEM, LIGHT, DARK }

@Serializable
data class ReaderPreferences(
    val fontSizePx: Int = 19,
    val lineHeight: Float = 1.6f,
    val theme: ReaderTheme = ReaderTheme.PAPER,
)

@Serializable
enum class ReaderTheme { PAPER, WARM, NIGHT }

/** The onboarding-level choice; individual rules carry their own [RedirectMode]. */
@Serializable
enum class RedirectStyle { AUTOMATIC, CHOICE, SCHEDULED }

// ---------------------------------------------------------------------------
// Books
// ---------------------------------------------------------------------------

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val description: String = "",
    /** Absolute path to an extracted cover image, or null to use a generated cover. */
    val coverPath: String? = null,
    /** Seed for the generated typographic cover. */
    val coverTint: Int = 0,
    /** Absolute path to the EPUB on disk, or an asset reference for bundled books. */
    val file: BookFile? = null,
    val source: BookSource,
    val status: ReadingStatus = ReadingStatus.WANT_TO_READ,
    val position: ReadingPosition? = null,
    /** 0..1 */
    val progress: Float = 0f,
    val inLibrary: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long? = null,
) {
    val isReadable: Boolean get() = file != null
}

@Serializable
data class BookFile(
    /** "asset" for bundled books, "file" for imported books. */
    val scheme: String,
    val path: String,
) {
    companion object {
        fun asset(path: String) = BookFile("asset", path)
        fun file(path: String) = BookFile("file", path)
    }
}

@Serializable
enum class BookSource {
    /** Public-domain EPUB shipped inside the app. */
    BUNDLED,
    /** EPUB the user imported from their device. */
    IMPORTED,
    /** Metadata-only listing from the sample catalog. No text is available. */
    CATALOG_SAMPLE,
    /** Public-domain EPUB downloaded from Project Gutenberg. */
    DOWNLOADED,
}

@Serializable
enum class ReadingStatus { WANT_TO_READ, READING, FINISHED }

@Serializable
data class ReadingPosition(
    val spineIndex: Int,
    /** Scroll fraction within the spine item, 0..1. */
    val fraction: Float,
)

@Serializable
data class Bookmark(
    val id: String,
    val bookId: String,
    val position: ReadingPosition,
    val label: String,
    val progress: Float,
    val createdAt: Long,
)

@Serializable
data class ReadingList(
    val id: String,
    val name: String,
    val description: String = "",
    val bookIds: List<String> = emptyList(),
    val createdAt: Long,
)

@Serializable
data class ReadingSession(
    val id: String,
    val bookId: String,
    val startedAt: Long,
    val endedAt: Long,
    val startProgress: Float,
    val endProgress: Float,
) {
    val durationMillis: Long get() = (endedAt - startedAt).coerceAtLeast(0)
}

// ---------------------------------------------------------------------------
// Redirection
// ---------------------------------------------------------------------------

@Serializable
data class TriggerApp(
    /** Package name for apps, or a synthetic id for features / virtual triggers. */
    val id: String,
    val displayName: String,
    val kind: TriggerKind,
    /** Package that must be installed for this trigger to be relevant. */
    val packageName: String? = null,
    /** Additional package names for the same app (regional variants). */
    val altPackageNames: List<String> = emptyList(),
    val support: PlatformSupport,
    val isCustom: Boolean = false,
)

@Serializable
enum class TriggerKind {
    /** Opening an entire app. */
    APP,
    /** A surface inside another app (Reels, Shorts). */
    IN_APP_FEATURE,
    /** Any of the user's selected apps. */
    ANY_SELECTED,
}

@Serializable
enum class PlatformSupport {
    /** Opening the app is detected by the interruption service. Serial name kept for stored data. */
    @SerialName("LAUNCH_DETECTION_PLANNED")
    LAUNCH_DETECTION,
    /** No validated way to detect this; rules are saved but will never fire. */
    NOT_VALIDATED,
}

@Serializable
data class Destination(
    val id: String,
    val type: DestinationType,
    /** Book id for SPECIFIC_BOOK, package name for EXTERNAL_APP. */
    val targetId: String? = null,
    val displayName: String,
    /** Icon key for built-ins; external apps use the package icon. */
    val icon: String = "",
    /** Extra launch data, e.g. a deep-link URI for an external app. */
    val launchConfig: Map<String, String> = emptyMap(),
    val createdAt: Long = 0,
) {
    val category: DestinationCategory
        get() = when (type) {
            DestinationType.CURRENT_BOOK,
            DestinationType.SPECIFIC_BOOK,
            DestinationType.LIBRARY,
            DestinationType.OFFLINE_ACTIVITY -> DestinationCategory.BUILT_IN
            DestinationType.EXTERNAL_APP -> DestinationCategory.EXTERNAL_APP
            DestinationType.CHOICE_SCREEN -> DestinationCategory.CHOICE
        }
}

@Serializable
enum class DestinationType {
    CURRENT_BOOK,
    SPECIFIC_BOOK,
    LIBRARY,
    EXTERNAL_APP,
    /** A non-app activity, e.g. "Take a short walk", shown as a gentle prompt. */
    OFFLINE_ACTIVITY,
    /** Opens the choice screen with all saved alternatives. */
    CHOICE_SCREEN,
}

enum class DestinationCategory { BUILT_IN, EXTERNAL_APP, CHOICE }

@Serializable
enum class RedirectMode { AUTOMATIC, CHOICE }

@Serializable
data class RedirectRule(
    val id: String,
    val name: String,
    val triggerId: String,
    val mode: RedirectMode,
    /** For AUTOMATIC the first available destination is used; for CHOICE all are offered. */
    val destinationIds: List<String>,
    /** CHOICE mode: also offer every saved alternative. */
    val includeSavedAlternatives: Boolean = false,
    val schedule: Schedule? = null,
    val enabled: Boolean = true,
    /** Temporarily paused until this time (epoch millis). */
    val pausedUntil: Long? = null,
    /** After the user continues past an intervention, stay quiet for this long. */
    val gracePeriodMinutes: Int = 0,
    /** Higher wins when rules overlap. */
    val priority: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class Schedule(
    /** ISO days, 1 = Monday .. 7 = Sunday. */
    val days: Set<Int> = ALL_DAYS,
    /** Minutes from midnight. If start == end, the rule applies all day. */
    val startMinute: Int = 0,
    val endMinute: Int = 0,
) {
    val isAllDay: Boolean get() = startMinute == endMinute

    companion object {
        val ALL_DAYS = setOf(1, 2, 3, 4, 5, 6, 7)
        val WEEKDAYS = setOf(1, 2, 3, 4, 5)
        val WEEKEND = setOf(6, 7)
    }
}
