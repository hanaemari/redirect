package com.example.redirect.data

/**
 * Static content that ships with the app: bundled public-domain books, the sample
 * discovery catalog, known distracting apps, and starter rules.
 */
object SeedData {

    // -- Books ---------------------------------------------------------------

    data class CatalogEntry(
        val id: String,
        val title: String,
        val author: String,
        val description: String,
        val tint: Int,
        val source: BookSource,
        /** Asset path for bundled books; null for metadata-only sample listings. */
        val assetPath: String? = null,
        val year: String = "",
        val subjects: List<String> = emptyList(),
    )

    /** Public-domain texts from Project Gutenberg, bundled as EPUB in assets/books. */
    val bundledBooks = listOf(
        CatalogEntry(
            id = "pd-meditations", title = "Meditations", author = "Marcus Aurelius",
            description = "Private notes on living well, written by a Roman emperor to himself. Short passages, easy to pick up for a few minutes at a time.",
            tint = 0, source = BookSource.BUNDLED, assetPath = "books/meditations.epub",
            year = "c. 180", subjects = listOf("Philosophy", "Short reads"),
        ),
        CatalogEntry(
            id = "pd-walden", title = "Walden", author = "Henry David Thoreau",
            description = "Two years in a cabin by a pond, and a long look at what a deliberate life might be. Includes the essay On the Duty of Civil Disobedience.",
            tint = 1, source = BookSource.BUNDLED, assetPath = "books/walden.epub",
            year = "1854", subjects = listOf("Nature", "Essays"),
        ),
        CatalogEntry(
            id = "pd-pride-and-prejudice", title = "Pride and Prejudice", author = "Jane Austen",
            description = "Elizabeth Bennet, Mr. Darcy, and a sharp-eyed comedy of manners that still reads as fresh as gossip.",
            tint = 2, source = BookSource.BUNDLED, assetPath = "books/pride-and-prejudice.epub",
            year = "1813", subjects = listOf("Fiction", "Classics"),
        ),
        CatalogEntry(
            id = "pd-the-prophet", title = "The Prophet", author = "Kahlil Gibran",
            description = "Twenty-six prose poems on love, work, joy and sorrow. Each one fits comfortably into a short break.",
            tint = 3, source = BookSource.BUNDLED, assetPath = "books/the-prophet.epub",
            year = "1923", subjects = listOf("Poetry", "Short reads"),
        ),
        CatalogEntry(
            id = "pd-frankenstein", title = "Frankenstein", author = "Mary Shelley",
            description = "The original story of a creator and his creation, told in letters and confessions across the Arctic ice.",
            tint = 4, source = BookSource.BUNDLED, assetPath = "books/frankenstein.epub",
            year = "1818", subjects = listOf("Fiction", "Classics"),
        ),
        CatalogEntry(
            id = "pd-alice", title = "Alice's Adventures in Wonderland", author = "Lewis Carroll",
            description = "Down the rabbit hole. Playful, strange, and quick to read.",
            tint = 5, source = BookSource.BUNDLED, assetPath = "books/alice-in-wonderland.epub",
            year = "1865", subjects = listOf("Fiction", "Classics"),
        ),
    )

    /**
     * Sample listings for the discovery screen. These are metadata only: the app does not
     * have the rights or files to provide their text. A future licensed catalog would
     * replace this list via [com.example.redirect.data.BookCatalog].
     */
    val sampleCatalog = listOf(
        CatalogEntry("sample-four-thousand-weeks", "Four Thousand Weeks", "Oliver Burkeman",
            "On time, finitude, and giving up the fight to get everything done.", 6, BookSource.CATALOG_SAMPLE,
            year = "2021", subjects = listOf("Nonfiction")),
        CatalogEntry("sample-piranesi", "Piranesi", "Susanna Clarke",
            "A man lives in a house of endless halls and tides, and keeps a careful journal.", 7, BookSource.CATALOG_SAMPLE,
            year = "2020", subjects = listOf("Fiction")),
        CatalogEntry("sample-braiding-sweetgrass", "Braiding Sweetgrass", "Robin Wall Kimmerer",
            "Botany, Indigenous knowledge, and the gift economy of the natural world.", 1, BookSource.CATALOG_SAMPLE,
            year = "2013", subjects = listOf("Nature", "Nonfiction")),
        CatalogEntry("sample-stolen-focus", "Stolen Focus", "Johann Hari",
            "An investigation into why it has become so hard to pay attention.", 3, BookSource.CATALOG_SAMPLE,
            year = "2022", subjects = listOf("Nonfiction")),
        CatalogEntry("sample-gentleman-moscow", "A Gentleman in Moscow", "Amor Towles",
            "A count sentenced to house arrest in a grand hotel makes a whole life inside it.", 2, BookSource.CATALOG_SAMPLE,
            year = "2016", subjects = listOf("Fiction")),
        CatalogEntry("sample-klara", "Klara and the Sun", "Kazuo Ishiguro",
            "An Artificial Friend watches the world from a shop window, and then from a family's home.", 0, BookSource.CATALOG_SAMPLE,
            year = "2021", subjects = listOf("Fiction")),
        CatalogEntry("sample-how-to-do-nothing", "How to Do Nothing", "Jenny Odell",
            "Resisting the attention economy by noticing the places and people around you.", 5, BookSource.CATALOG_SAMPLE,
            year = "2019", subjects = listOf("Nonfiction")),
    )

    val allCatalog get() = bundledBooks + sampleCatalog

    // -- Trigger apps ---------------------------------------------------------

    const val TRIGGER_ANY = "trigger:any-selected"

    val triggerCatalog = listOf(
        TriggerApp("com.zhiliaoapp.musically", "TikTok", TriggerKind.APP, "com.zhiliaoapp.musically",
            altPackageNames = listOf("com.ss.android.ugc.trill"), support = PlatformSupport.LAUNCH_DETECTION),
        TriggerApp("com.instagram.android", "Instagram", TriggerKind.APP, "com.instagram.android",
            support = PlatformSupport.LAUNCH_DETECTION),
        TriggerApp("com.google.android.youtube", "YouTube", TriggerKind.APP, "com.google.android.youtube",
            support = PlatformSupport.LAUNCH_DETECTION),
        TriggerApp("com.reddit.frontpage", "Reddit", TriggerKind.APP, "com.reddit.frontpage",
            support = PlatformSupport.LAUNCH_DETECTION),
        TriggerApp("com.twitter.android", "X", TriggerKind.APP, "com.twitter.android",
            support = PlatformSupport.LAUNCH_DETECTION),
        TriggerApp("com.facebook.katana", "Facebook", TriggerKind.APP, "com.facebook.katana",
            support = PlatformSupport.LAUNCH_DETECTION),
        TriggerApp("com.snapchat.android", "Snapchat", TriggerKind.APP, "com.snapchat.android",
            support = PlatformSupport.LAUNCH_DETECTION),
        TriggerApp("com.pinterest", "Pinterest", TriggerKind.APP, "com.pinterest",
            support = PlatformSupport.LAUNCH_DETECTION),
        TriggerApp("feature:instagram-reels", "Instagram Reels", TriggerKind.IN_APP_FEATURE, "com.instagram.android",
            support = PlatformSupport.NOT_VALIDATED),
        TriggerApp("feature:youtube-shorts", "YouTube Shorts", TriggerKind.IN_APP_FEATURE, "com.google.android.youtube",
            support = PlatformSupport.NOT_VALIDATED),
        TriggerApp(TRIGGER_ANY, "Any distracting app", TriggerKind.ANY_SELECTED,
            support = PlatformSupport.LAUNCH_DETECTION),
    )

    /** Shown during onboarding. */
    val onboardingTriggerIds = listOf(
        "com.zhiliaoapp.musically", "com.instagram.android", "com.google.android.youtube",
        "com.reddit.frontpage", "com.twitter.android", "com.facebook.katana",
        "com.snapchat.android", "com.pinterest",
    )

    // -- Destinations ---------------------------------------------------------

    const val DEST_CURRENT_BOOK = "dest:current-book"
    const val DEST_LIBRARY = "dest:library"
    const val DEST_CHOICE = "dest:choice"

    val defaultDestinations = listOf(
        Destination(DEST_CURRENT_BOOK, DestinationType.CURRENT_BOOK, displayName = "Continue my current book", icon = "book"),
        Destination(DEST_LIBRARY, DestinationType.LIBRARY, displayName = "Browse my library", icon = "library"),
        Destination(DEST_CHOICE, DestinationType.CHOICE_SCREEN, displayName = "Let me choose", icon = "choice"),
        Destination("dest:walk", DestinationType.OFFLINE_ACTIVITY, displayName = "Take a short walk", icon = "walk"),
    )

    /** Offline activity suggestions offered when adding an alternative. */
    val offlineActivityIdeas = listOf(
        "Take a short walk" to "walk",
        "Write in my journal" to "journal",
        "Sketch something" to "draw",
        "Stretch for five minutes" to "stretch",
        "Make a cup of tea" to "tea",
        "Call someone I love" to "call",
    )

    // -- Starter rules --------------------------------------------------------

    fun starterRules(selected: List<String>, style: RedirectStyle, now: Long): List<RedirectRule> {
        val rules = mutableListOf<RedirectRule>()
        var n = 0
        fun id() = "rule-${now}-${n++}"
        val name = { id: String -> triggerCatalog.firstOrNull { it.id == id }?.displayName ?: id }

        for (trigger in selected) {
            when (style) {
                RedirectStyle.AUTOMATIC -> rules += RedirectRule(
                    id(), "${name(trigger)} → my book", trigger, RedirectMode.AUTOMATIC,
                    listOf(DEST_CURRENT_BOOK), createdAt = now, updatedAt = now,
                )
                RedirectStyle.CHOICE -> rules += RedirectRule(
                    id(), "${name(trigger)} → choose", trigger, RedirectMode.CHOICE,
                    listOf(DEST_CURRENT_BOOK, DEST_LIBRARY), includeSavedAlternatives = true,
                    createdAt = now, updatedAt = now,
                )
                RedirectStyle.SCHEDULED -> {
                    rules += RedirectRule(
                        id(), "${name(trigger)} on weekdays", trigger, RedirectMode.AUTOMATIC,
                        listOf(DEST_CURRENT_BOOK), schedule = Schedule(Schedule.WEEKDAYS),
                        priority = 1, createdAt = now, updatedAt = now,
                    )
                    rules += RedirectRule(
                        id(), "${name(trigger)} on weekends", trigger, RedirectMode.CHOICE,
                        listOf(DEST_CURRENT_BOOK, DEST_LIBRARY), includeSavedAlternatives = true,
                        schedule = Schedule(Schedule.WEEKEND), priority = 1, createdAt = now, updatedAt = now,
                    )
                }
            }
        }
        // A broader example: evenings are for reading, whatever app is opened.
        rules += RedirectRule(
            id(), "Evening wind-down", TRIGGER_ANY, RedirectMode.AUTOMATIC, listOf(DEST_CURRENT_BOOK),
            schedule = Schedule(Schedule.ALL_DAYS, startMinute = 21 * 60, endMinute = 23 * 60 + 30),
            priority = 2, enabled = selected.isNotEmpty(), createdAt = now, updatedAt = now,
        )
        return rules
    }
}
