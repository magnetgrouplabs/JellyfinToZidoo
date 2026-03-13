---
phase: 02-core-bridge
plan: 03
subsystem: api
tags: [jellyfin, android-intent, okhttp, async-callback, smb]

# Dependency graph
requires:
  - phase: 02-core-bridge/02-01
    provides: "JellyfinApi class (extractItemId, getItem, ticksToMs) and SecureStorage"
  - phase: 02-core-bridge/02-02
    provides: "Settings UI storing jellyfin_server_url and jellyfin_api_key"
provides:
  - "Complete Jellyfin intent -> API call -> path substitution -> Zidoo launch pipeline in Play.java"
  - "Debug screen with Jellyfin Item ID and API Path fields"
affects: [03-playback-reporting, 04-episode-tracking]

# Tech tracking
tech-stack:
  added: []
  patterns: [async-api-callback-to-ui, intent-position-priority-chain]

key-files:
  created: []
  modified:
    - app/src/main/java/com/jellyfintozidoo/Play.java

key-decisions:
  - "Intent position (ms) takes priority over API ticks for resume -- clients may send more accurate position"
  - "Non-Jellyfin URLs fall through to direct substitution preserving backward compatibility"

patterns-established:
  - "Async API pattern: JellyfinApi callback -> update fields -> doSubstitution -> showDebugPageOrSendIntent"
  - "Error display pattern: set message string then call showDebugPageOrSendIntent (matches original PlexToZidoo)"

requirements-completed: [AUTH-04, BRDG-01, BRDG-04, BRDG-05, BRDG-06, DEBG-01, DEBG-02, DEBG-03, DEBG-04]

# Metrics
duration: 2min
completed: 2026-03-13
---

# Phase 2 Plan 3: Jellyfin API Bridge Summary

**Wired Jellyfin intent interception pipeline: URL item ID extraction -> async API resolve -> path substitution -> Zidoo player launch with resume position**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-13T21:56:31Z
- **Completed:** 2026-03-13T21:58:07Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- Complete Jellyfin bridge flow in Play.java replacing Plex TODO placeholder
- Async API call resolves server-side file path from Jellyfin item ID
- Resume position priority chain: intent extras (ms) > API ticks > 0 (start)
- Debug screen shows Jellyfin Item ID, API Path, substitution path, and all existing fields
- Error handling for missing server config and API failures
- ZDMC and direct substitution fallback paths fully preserved

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace Plex placeholder with Jellyfin API bridge flow** - `ccf7f40` (feat)
2. **Task 2: Run full test suite and verify build** - no file changes (verification only)

## Files Created/Modified
- `app/src/main/java/com/jellyfintozidoo/Play.java` - Added Jellyfin bridge pipeline: extractItemId, getItem callback, doSubstitution, resume position, debug screen fields

## Decisions Made
- Intent position (ms) takes priority over API position ticks -- Jellyfin clients may provide more accurate resume position
- Non-Jellyfin URLs fall through to direct substitution, preserving backward compatibility with arbitrary video URLs

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Core bridge pipeline complete -- Jellyfin URL intent -> API resolve -> SMB substitution -> Zidoo player
- Ready for Plan 02-04 (debug overlay / APK deploy) or Phase 3 (playback reporting)
- onActivityResult still has Phase 4 TODO for watch state reporting back to Jellyfin

---
*Phase: 02-core-bridge*
*Completed: 2026-03-13*
