---
phase: 01-fork-setup
plan: 01
subsystem: infra
tags: [android, gradle, fork, package-rename, java]

# Dependency graph
requires: []
provides:
  - Renamed codebase with package com.jellyfintozidoo
  - All Plex code commented with PLEX_REMOVED marker tags
  - Zidoo player launch code preserved intact
  - Upstream remote for cherry-picking PlexToZidoo updates
affects: [01-fork-setup, 02-jellyfin-bridge]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "PLEX_REMOVED_START/END marker tags for commented Plex code"
    - "Empty placeholder classes for commented-out Plex-only files"

key-files:
  created:
    - app/src/main/java/com/jellyfintozidoo/ (all 6 Java files moved here)
  modified:
    - settings.gradle
    - app/build.gradle
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/com/jellyfintozidoo/Play.java
    - app/src/main/java/com/jellyfintozidoo/SettingsActivity.java
    - app/src/main/java/com/jellyfintozidoo/PlexLibraryInfo.java
    - app/src/main/java/com/jellyfintozidoo/PlexLibraryXmlParser.java
    - app/src/main/java/com/jellyfintozidoo/PlexMediaType.java
    - app/src/main/java/com/jellyfintozidoo/PlexXmlParser.java
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values/themes.xml
    - app/src/main/res/values-night/themes.xml
    - app/src/main/res/xml/root_preferences.xml

key-decisions:
  - "Updated compileSdkVersion to 36 and buildToolsVersion to 36.0.0 to match locally installed SDK"
  - "Simplified Play.java debug output to remove Plex-specific fields (ratingKey, partKey, etc.)"
  - "Added fallback doSubstitution+showDebugPageOrSendIntent call for non-ZDMC paths after Plex code removed"

patterns-established:
  - "PLEX_REMOVED_START/END: marker pattern for all commented Plex code, with description in START tag"
  - "Placeholder classes: empty public class in Plex-only files so they compile"

requirements-completed: [FORK-01, FORK-02, FORK-04]

# Metrics
duration: 8min
completed: 2026-03-13
---

# Phase 1 Plan 1: Fork and Rename Summary

**Cloned PlexToZidoo, renamed package to com.jellyfintozidoo, commented out all Plex API code with 27 PLEX_REMOVED marker blocks, and updated all user-facing strings to Jellyfin**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-13T16:24:45Z
- **Completed:** 2026-03-13T16:33:02Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments
- Cloned PlexToZidoo from main_bowlingbeeg branch with full git history preserved
- Renamed package from com.hpn789.plextozidoo to com.jellyfintozidoo across all build config, manifest, and Java files
- Commented out all Plex API code (Volley HTTP calls, XML parsers, library fetching, progress reporting) with PLEX_REMOVED markers
- Updated all user-facing strings from Plex to Jellyfin terminology
- Preserved all Zidoo player launch code, path substitution logic, and ZDMC intent handling intact

## Task Commits

Each task was committed atomically:

1. **Task 1: Clone PlexToZidoo and rename package throughout** - `e941dc9` (feat)
2. **Task 2: Comment out Plex code and update user-facing strings** - `669daca` (feat)

## Files Created/Modified
- `settings.gradle` - rootProject.name changed to JellyfinToZidoo
- `app/build.gradle` - applicationId, compileSdkVersion, buildToolsVersion updated
- `app/src/main/AndroidManifest.xml` - Package and theme references renamed
- `app/src/main/java/com/jellyfintozidoo/Play.java` - Plex API calls commented, Zidoo code preserved
- `app/src/main/java/com/jellyfintozidoo/SettingsActivity.java` - Backup filename updated to JellyfinToZidooSettings.txt
- `app/src/main/java/com/jellyfintozidoo/PlexLibraryInfo.java` - All contents commented, empty placeholder class
- `app/src/main/java/com/jellyfintozidoo/PlexLibraryXmlParser.java` - All contents commented, empty placeholder class
- `app/src/main/java/com/jellyfintozidoo/PlexMediaType.java` - All contents commented, empty placeholder class
- `app/src/main/java/com/jellyfintozidoo/PlexXmlParser.java` - All contents commented, empty placeholder class
- `app/src/main/res/values/strings.xml` - App name and labels changed to Jellyfin
- `app/src/main/res/values/themes.xml` - Theme.PlexToZidoo renamed to Theme.JellyfinToZidoo
- `app/src/main/res/values-night/themes.xml` - Same theme rename
- `app/src/main/res/xml/root_preferences.xml` - Plex library preferences commented out

## Decisions Made
- Updated compileSdkVersion from 31 to 36 and buildToolsVersion from 30.0.3 to 36.0.0 because only SDK 36 and build-tools 36.0.0 are installed locally. This is safe as the app targets API 28 and uses basic AndroidX APIs.
- Simplified the debug output in Play.java's updateDebugPage() to remove Plex-specific fields (ratingKey, partKey, partId, librarySection, mediaType, duration, videoIndex, parentRatingKey) that no longer exist after commenting out Plex code.
- Added a fallback code path in onStart() for non-ZDMC intents: after Plex server communication was commented out, added doSubstitution+showDebugPageOrSendIntent so the app still attempts to play video via path substitution.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added fallback path for non-ZDMC intents in Play.java**
- **Found during:** Task 2 (Comment out Plex code)
- **Issue:** After commenting out all Plex server communication in onStart(), non-ZDMC code paths would reach the end of onStart() without ever calling showDebugPageOrSendIntent(), meaning the app would display nothing and never launch the player.
- **Fix:** Added a fallback block after the commented-out Plex code that calls doSubstitution(directPath) and showDebugPageOrSendIntent() for non-ZDMC paths.
- **Files modified:** app/src/main/java/com/jellyfintozidoo/Play.java
- **Verification:** Code flow analysis confirms all paths through onStart() now reach showDebugPageOrSendIntent()
- **Committed in:** 669daca (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Essential fix to prevent dead code path after Plex removal. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Renamed codebase is ready for Plan 02 (build verification, CI setup, README/LICENSE attribution)
- All PLEX_REMOVED markers in place for easy grep when implementing Jellyfin equivalents in Phase 2
- Upstream remote configured for cherry-picking future PlexToZidoo Zidoo integration fixes

## Self-Check: PASSED

All 14 key files verified present. Both task commits (e941dc9, 669daca) verified in git log.

---
*Phase: 01-fork-setup*
*Completed: 2026-03-13*
