---
phase: 04-episode-intelligence
plan: 01
subsystem: api
tags: [tdd, reverse-substitution, jellyfin-api, json-parsing, smb]

requires:
  - phase: 02-core-bridge
    provides: JellyfinApi with parseItemResponse, getNextUp, path substitution
provides:
  - Reverse path substitution (Zidoo SMB path to server path)
  - extractSearchName for Jellyfin item search
  - NextUpDetailResult with full episode metadata parsing
  - parseSearchByPathResponse for item lookup by path
affects: [04-episode-intelligence]

tech-stack:
  added: []
  patterns: [package-private static methods for unit testability, TDD red-green cycle]

key-files:
  created:
    - app/src/test/java/com/jellyfintozidoo/ReverseSubstitutionTest.java
  modified:
    - app/src/main/java/com/jellyfintozidoo/JellyfinApi.java
    - app/src/test/java/com/jellyfintozidoo/JellyfinApiTest.java

key-decisions:
  - "Reverse substitution is a static method taking rule arrays directly, avoiding Android SharedPreferences dependency in tests"
  - "parseSearchByPathResponse checks both root Path and MediaSources[0].Path for each item"

patterns-established:
  - "TDD for pure-logic methods: write failing tests first, then implement"
  - "Package-private static methods with rule arrays for Android-free unit testing"

requirements-completed: [EPIS-01, EPIS-02, EPIS-03, EPIS-04]

duration: 4min
completed: 2026-03-14
---

# Phase 4 Plan 1: Episode Intelligence Core Logic Summary

**TDD-verified reverse path substitution, NextUp detail parsing, and search-by-path response parsing for episode intelligence**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-14T14:17:54Z
- **Completed:** 2026-03-14T14:21:54Z
- **Tasks:** 2 (TDD features, 4 commits total)
- **Files modified:** 3

## Accomplishments
- Reverse path substitution converts Zidoo SMB paths back to Jellyfin server-side paths with credential stripping and URI decoding
- NextUpDetailResult parsing extracts full episode metadata (itemId, seriesName, episodeName, S##E##, seriesId, serverPath)
- Search-by-path parsing finds exact path matches among Jellyfin search results
- 24 new test cases all passing green across both test files

## Task Commits

Each task was committed atomically (TDD red-green):

1. **Feature 1 RED: Reverse substitution tests** - `bb37c93` (test)
2. **Feature 1 GREEN: Reverse substitution implementation** - `00ca2d2` (feat)
3. **Feature 2 RED: NextUp/search parsing tests** - `498938b` (test)
4. **Feature 2 GREEN: NextUp/search parsing implementation** - `095e49a` (feat)

## Files Created/Modified
- `app/src/test/java/com/jellyfintozidoo/ReverseSubstitutionTest.java` - 15 test cases for reverse substitution and extractSearchName
- `app/src/test/java/com/jellyfintozidoo/JellyfinApiTest.java` - Extended with 9 new tests for NextUpDetailResult, parseSearchByPathResponse, and episode SeriesId
- `app/src/main/java/com/jellyfintozidoo/JellyfinApi.java` - Added reverseSubstitution(), extractSearchName(), NextUpDetailResult class, parseNextUpDetailResponse(), parseSearchByPathResponse()

## Decisions Made
- Reverse substitution takes `String[][] rules` directly instead of SharedPreferences, keeping tests Android-free
- parseSearchByPathResponse checks both root Path and MediaSources[0].Path for each search result item (matches existing parseItemResponse fallback pattern)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All pure-logic methods tested and green, ready for Plan 02 to wire into UI layer
- reverseSubstitution() ready for Play.java integration when detecting episode changes
- parseNextUpDetailResponse() ready for Up Next flow
- parseSearchByPathResponse() ready for item-by-path lookup

---
*Phase: 04-episode-intelligence*
*Completed: 2026-03-14*

## Self-Check: PASSED
- All 4 artifact files found
- All 4 commit hashes verified
- ReverseSubstitutionTest.java: 121 lines (min 80)
- JellyfinApiTest.java: 304 lines (min 120)
