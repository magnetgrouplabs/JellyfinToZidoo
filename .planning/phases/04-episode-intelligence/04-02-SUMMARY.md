---
phase: 04-episode-intelligence
plan: 02
subsystem: ui, api
tags: [glide, blur, countdown, up-next, android-activity, okhttp]

requires:
  - phase: 04-episode-intelligence-01
    provides: NextUpDetailResult, parseNextUpDetailResponse, parseSearchByPathResponse, reverseSubstitution, extractSearchName
provides:
  - UpNextActivity with blurred backdrop, episode info card, 10-second countdown, D-pad buttons
  - getNextUpWithDetails() network method for rich NextUp metadata
  - searchItemByPath() network method for Jellyfin item lookup by server path
  - NextUpDetailCallback and SearchByPathCallback interfaces
affects: [04-episode-intelligence-03]

tech-stack:
  added: [glide 4.16.0, glide-transformations 4.3.0]
  patterns: [BlurTransformation for TV backdrop, CountDownTimer for auto-advance, maxWidth constraints for Zidoo memory]

key-files:
  created:
    - app/src/main/java/com/jellyfintozidoo/UpNextActivity.java
    - app/src/main/res/layout/activity_up_next.xml
  modified:
    - app/build.gradle
    - app/src/main/java/com/jellyfintozidoo/JellyfinApi.java
    - app/src/main/AndroidManifest.xml
    - app/src/main/res/values/strings.xml

key-decisions:
  - "Glide BlurTransformation with sampling=3 and radius=25 for memory-safe backdrop blur on Zidoo Z9X Pro"
  - "Episode thumbnail uses RoundedCorners(16) transform at maxWidth=640 for card appearance"
  - "CountDownTimer canceled in onDestroy and guarded by cancelled flag to prevent zombie launches"

patterns-established:
  - "Glide image loading with maxWidth URL params + .override() for Zidoo memory safety"
  - "Activity result pattern: RESULT_OK with intent extras for episode metadata handoff"

requirements-completed: [EPIS-03, EPIS-04, SETT-04]

duration: 3min
completed: 2026-03-14
---

# Phase 4 Plan 2: Up Next UI & API Wiring Summary

**UpNextActivity with Glide blur backdrop, 10-second countdown, D-pad navigation, plus getNextUpWithDetails and searchItemByPath network methods**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-14T14:25:46Z
- **Completed:** 2026-03-14T14:28:45Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Added Glide 4.16.0 + glide-transformations 4.3.0 for image loading and blur effects
- Created UpNextActivity with blurred series backdrop, episode thumbnail, info card, and 10-second countdown
- Added getNextUpWithDetails() and searchItemByPath() network methods to JellyfinApi
- D-pad navigable Play Now / Cancel buttons with proper focus chain

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Glide dependencies and JellyfinApi network methods** - `cba8bc3` (feat)
2. **Task 2: Create UpNextActivity with blurred backdrop and countdown** - `4de19db` (feat)

## Files Created/Modified
- `app/build.gradle` - Added Glide and glide-transformations dependencies
- `app/src/main/java/com/jellyfintozidoo/JellyfinApi.java` - Added getNextUpWithDetails(), searchItemByPath(), callbacks
- `app/src/main/java/com/jellyfintozidoo/UpNextActivity.java` - Up Next countdown screen Activity
- `app/src/main/res/layout/activity_up_next.xml` - Layout with backdrop, card, countdown, buttons
- `app/src/main/AndroidManifest.xml` - Registered UpNextActivity
- `app/src/main/res/values/strings.xml` - Added Up Next string resources

## Decisions Made
- Glide BlurTransformation with sampling=3 and radius=25 for memory-safe backdrop blur on Zidoo Z9X Pro
- Episode thumbnail uses RoundedCorners(16) transform at maxWidth=640 for card appearance
- CountDownTimer canceled in onDestroy and guarded by cancelled flag to prevent zombie launches
- URL-encoded search terms in searchItemByPath for special characters in filenames

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- UpNextActivity ready for Plan 03 to wire into Play.java flow
- getNextUpWithDetails() and searchItemByPath() ready for episode auto-advance integration
- All unit tests pass, assembleDebug succeeds

---
*Phase: 04-episode-intelligence*
*Completed: 2026-03-14*
