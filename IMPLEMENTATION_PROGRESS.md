# Redirect — Implementation Progress

Android app that interrupts distracting apps (TikTok, Instagram…) and sends the user to
something intentional — by default, the book they're reading.

_Last updated: 2026-10-30 (session 3)_

## How to build / test

```bash
# Use Android Studio's bundled JDK (system JDK 24 is too new for AGP 8.10)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew --offline assembleDebug testDebugUnitTest
```

The project is **not a git repository**. Consider `git init` so future sessions can diff progress.

## Stack (preserve these decisions)

| Area | Choice | Notes |
|---|---|---|
| Language/UI | Kotlin 2.0.21, Jetpack Compose (BOM 2024.12), Material 3, Navigation-Compose | single `MainActivity` |
| Build | AGP 8.10.1, Gradle 8.11.1, compileSdk/targetSdk 35, minSdk 26 | |
| Persistence | Single JSON document (`files/redirect-data.json`) via kotlinx.serialization (`AppStore`) | Chosen instead of Room: tiny data set, atomic writes. **Never rename `@Serializable` enum constants** — an unknown enum value makes the whole document fail to decode and the store falls back to empty data. Add new values or use `@SerialName`. |
| EPUB | Own `EpubParser` (zip + OPF + NCX/nav) + WebView renderer (`EpubWebView`) | Chosen instead of Readium to avoid a large dependency; tested against the 6 bundled Gutenberg EPUBs. |
| Rules | Pure `RuleEngine.decide()` (engine/RuleEngine.kt) | Shared by previews and the real interruption service. |

## Module map

- `data/` — models (`Models.kt`), `AppStore`, `AppRepository` (all writes), `SeedData`, `Selectors`, `BookCatalog`, free-book client
- `epub/` — `EpubParser`, `EpubArchive`
- `engine/` — `RuleEngine` (trigger → rule → destination decision)
- `interrupt/` — foreground-app detection + interruption coordinator (session 3)
- `platform/` — installed apps, launch helpers, capability descriptions
- `ui/` — Compose screens: onboarding, home, library, discover, book detail, reader, rules, rule editor, intervention, profile

## Status checklist

### Priority 0 — Recovery
- [x] Recovered state from repo + previous transcripts (session 1 was cut off by credit limit while installing on emulator; session 2 stopped during recovery)
- [x] Project builds (`assembleDebug`) and all unit tests pass (session 3 start)

### Priority 1 — Reader & local library (done in session 1)
- [x] EPUB import (SAF `OpenDocument`), cover extraction, error messages for non-EPUB files
- [x] EPUB rendering in WebView, chapter list/TOC, font size, line height, themes (Paper / Warm / Night)
- [x] Auto-save position (spine index + scroll fraction), resume, progress %, bookmarks, reading sessions
- [x] Local library with statuses (Want to read / Reading / Finished), reading lists, recents
- [x] 6 bundled public-domain EPUBs (Gutenberg) in `assets/books`

### Priority 2 — Free books (session 3)
- [x] `data/FreeBooks.kt`: Gutendex client (primary, 25 s timeout) + Gutenberg OPDS fallback (Gutendex measured 15–74 s per request on 2026-10-30); in-memory cache of recent first pages
- [x] Discover: included books + "Popular on / From Project Gutenberg", search, details sheet, Download / Read, Show more, error + retry
- [x] Only lists `copyright == false` EPUB text books; OPDS-fallback books have rights verified via `{id}.opds` before download
- [x] `AppRepository.downloadFreeBook` → `BookSource.DOWNLOADED`, id `gutenberg-<id>` (no duplicates), cover from EPUB or Gutenberg cover URL
- [x] Illustrated EPUB only if ≤15 MB, else text-only `.epub.noimages` (Monte Cristo: 84 MB vs 1.3 MB)
- [x] **Verified on emulator**: Popular list with real covers (via OPDS fallback when Gutendex timed out) → Download → "Read" + snackbar → book on Home with cover → reader renders chapters

### Priority 3 — Scroll interruption (session 3)
- [x] `interrupt/InterruptionService` (AccessibilityService, `typeWindowStateChanged` only, `canRetrieveWindowContent=false`)
- [x] `interrupt/InterruptionCoordinator` (pure, tested): package → trigger (incl. regional variants), act only on app *transitions*, ignore shade/keyboard, protected launcher/dialer/settings/installer, pause/off/grace, re-assert up to 3× within 2.5 s when the app's own splash→main activity lands on top of ours
- [x] Service starts `MainActivity` (singleTask) with the trigger → live `InterventionScreen` (countdown → current book, or choice screen); "Continue to app" grants ≥1 min grace so it is never fought
- [x] Global pause (15 min / 1 h / until tomorrow / off) — `InterruptionStatusCard` on Home and Rules
- [x] Onboarding step 2 "Let Redirect notice" with prominent disclosure + "Agree and open settings" + Android 13+ restricted-settings hint
- [x] **Verified on emulator (API 36)**: Home → open Chrome (as distracting app) → Redirect comes to front "Instead of Chrome… Back to Walden" → reader opens

### Priority 4 — Replacement architecture (mostly done in session 1)
- [x] Rule model: trigger, mode (AUTOMATIC / CHOICE), destinations, schedule, priority, pause, grace period
- [x] Destination types: CURRENT_BOOK, SPECIFIC_BOOK, LIBRARY, EXTERNAL_APP, OFFLINE_ACTIVITY, CHOICE_SCREEN
- [x] Rule editor, rules list, previews ("Try it")
- [x] Wire engine to the real interruption service (`AccessibilityBridge` replaces `NotYetAvailableBridge`)

### Priority 5 — Custom app replacement (UI done in session 1)
- [x] Installed launchable app picker (`<queries>` LAUNCHER intent, no QUERY_ALL_PACKAGES)
- [x] External-app destinations; unavailable apps fall back to the choice screen
- [x] Loop prevention: an EXTERNAL_APP destination that is itself on the distracting list is treated as unavailable (`Selectors.isAvailable`)

## Bugs fixed in session 3 (were in session-1 code)
- Reader: `DisposableEffect(load)` read the *new* state in `onDispose` and closed the archive the reader was about to use → "Webpage not available"/blank chapters. Now captures the value.
- `EpubArchive`: reads after close crashed the app (`IllegalStateException: zip file closed` from WebView thread). Reads/close are now synchronized; reads after close return null.

## Known issues / limitations
- In-app surfaces (Reels, Shorts) cannot be detected reliably; those triggers are saved but never fire (by design).
- Android 13+: for sideloaded APKs the Accessibility switch is greyed out until the user taps App info → ⋮ → "Allow restricted settings". Onboarding explains this. Play Store distribution requires the Accessibility API declaration form + the in-app prominent disclosure (implemented).
- Some OEMs (Xiaomi, Huawei, Samsung battery modes) may kill accessibility services; nothing more can be done without user action.
- Gutenberg cover page (section 1) of bundled EPUBs sometimes renders blank in the reader (cosmetic).
- Emulator testing tip: `adb install -r` while the service is bound marks it "crashed"; reset with
  `settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0`, then re-enable.

## Files added/changed in session 3
- added: `data/FreeBooks.kt`, `interrupt/InterruptionCoordinator.kt`, `interrupt/InterruptionService.kt`, `ui/components/InterruptionStatus.kt`, `res/xml/interruption_service.xml`, tests `data/FreeBooksTest.kt`, `interrupt/InterruptionCoordinatorTest.kt`
- changed: `AndroidManifest.xml` (INTERNET, HOME query, service, singleTask), `strings.xml`, `MainActivity.kt`, `RedirectApp.kt`, `data/Models.kt` (DOWNLOADED, pause prefs, `@SerialName` on `LAUNCH_DETECTION`), `data/AppRepository.kt`, `data/Selectors.kt`, `platform/Platform.kt`, `ui/AppViewModel.kt`, `ui/AppNavigation.kt`, `ui/library/DiscoverScreen.kt` (rewritten), `ui/library/BookDetailScreen.kt`, `ui/home/HomeScreen.kt`, `ui/rules/RulesScreen.kt`, `ui/rules/RulePickers.kt`, `ui/rules/InterventionScreen.kt`, `ui/onboarding/OnboardingScreen.kt`, `ui/components/Covers.kt`, `ui/reader/ReaderScreen.kt`, `ui/reader/EpubWebView.kt`, `epub/EpubParser.kt`

## Build / test results
- 2026-10-30: `assembleDebug` OK; unit tests 43/43 passing (RuleEngine 12, EpubParser 6, BundledBooks 1, FreeBooks 9, InterruptionCoordinator 15)
- Emulator (Medium_Phone_API_36.0) verified: interruption → intervention → reader; free-book download + read; onboarding permission step → Accessibility settings.

## Remaining tasks (in priority order)
1. Manual pass on a **real phone** with TikTok/Instagram installed (emulator used Chrome as the stand-in distracting app).
2. Show download size / progress in Discover (downloads currently show only a spinner).
3. Consider making the live intervention countdown configurable (currently 4 s, then opens the destination).
4. Reader: cover-only first section of Gutenberg EPUBs sometimes blank (cosmetic).
5. Phase 2 polish: rule editor already supports EXTERNAL_APP; add a "replacement app" quick-pick in onboarding (optional).
6. `git init` + first commit (not done — awaiting user's go-ahead).

## Next step to resume
Item 1 above (real-device check), then item 2.

---

## Session 4 (2026-11-02) — stabilization (uncommitted; see `git status`)

### Library tab crash — FIXED
- Cause (logcat): `ClassCastException: Long cannot be cast to Integer` at `LibraryScreen.kt:114`. "Recently opened" sort used `lastOpenedAt ?: 0` — `Long` for opened books, `Int` 0 for unopened — so any library with both crashed. The crash also made Android mark the Accessibility service "crashed" (looked like a permission reset).
- Fix: `?: 0L`; sort moved to `sortBooks()` in the same file. Regression test `ui/library/LibrarySortTest` fails on the old code, passes now.

### Discovery speed — DONE
- Gutenberg OPDS is primary; Gutendex is the fallback. Summary/subjects/rights come from the book's `{id}.opds` record (details sheet + rights check before download). List rows say "Public domain" only when verified.
- Disk + memory cache of first pages (`cacheDir/discover`, 6 h fresh, 20 queries); cached results show instantly. Requests are cancellable (connection closed on cancel). Timeouts: connect 8 s, OPDS read 10 s, Gutendex read 15 s.
- Measured (curl): Gutendex 62 s / 89 s (503) / 60 s timeouts on uncached queries; OPDS 0.2–0.9 s. App code path (`DiscoveryLatencyProbe`, run with `-Pprobe`): before 0.07–25.4 s (uncached searches 15.6 s, 25.3 s); after 0.08–0.64 s.

### Accessibility banner — DONE
- `AccessibilityBanner` at top of Home and Rules while the service is OFF or STOPPED (enabled in Settings but not running, e.g. after a crash; re-checked after 3 s). Live via resume, a Settings ContentObserver, and the service's `runningState`.
- "Enable Accessibility" opens Accessibility settings with Redirect highlighted (`:settings:fragment_args_key`; the per-service details action is system-only). The disclosure sheet shows first unless already accepted (`accessibilityDisclosureAccepted`).
- Fixed in verification: on Home the banner was inserted as a separate list item and stayed scrolled off-screen when it appeared while open; now it shares the first item with the greeting.

### Verified on emulator (API 36)
- Library with opened + unopened books opens; grid/list, all sort orders, 40+ tab switches, reader and Discover round trips: no crash.
- Banner: shown when off → disclosure → settings (Redirect highlighted) → enable via system dialog → banner gone; revoked while app open → banner back.
- Discover loads from Gutenberg OPDS.
- Tests: 52 passed, 1 skipped (network probe).

### Not tested / remaining
- Not re-run this session: interruption flow, continue/bypass, pause options, EPUB import, downloads end to end, custom/external-app rules (code paths unchanged).
- STOPPED banner state not reproduced on the emulator.
- Library "Add a book" sheet subtitle still says "sample listings" (stale copy).
- Emulator under host memory pressure caused system-wide ANRs (not Redirect-specific).

### Next step
Phone checks for the items above, then commit. After that: scroll-aware / link-aware interruption (not started).
