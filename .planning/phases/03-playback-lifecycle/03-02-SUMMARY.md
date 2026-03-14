---
phase: 03-playback-lifecycle
plan: 02
subsystem: playback
tags: [zidoo, jellyfin, polling, okhttp, playback-reporting, activity-lifecycle]

# Dependency graph
requires:
  - phase: 03-playback-lifecycle plan 01
    provides: JellyfinApi reporting methods (reportPlaybackStart, reportPlaybackProgress, reportPlaybackStopped, markAsWatched, isWatched)
provides:
  - Full playback lifecycle wiring in Play.java
  - Zidoo progress polling via localhost:9529
  - Caller package capture and relaunch
  - Automatic watched marking at 90% threshold
affects: [04-testing, 05-polish]

# Tech tracking
tech-stack:
  added: []
  patterns: [ScheduledExecutorService for periodic polling, caller capture via getCallingPackage/getReferrer, onActivityResult chained async callbacks]

key-files:
  created: []
  modified:
    - app/src/main/java/com/jellyfintozidoo/JellyfinApi.java
    - app/src/main/java/com/jellyfintozidoo/Play.java

key-decisions:
  - "Callback interface updated to include durationTicks for watched threshold calculation"
  - "Progress poller uses 3s initial delay and 10s interval to balance responsiveness with overhead"
  - "Kept onRestart->finish() pattern from PlexToZidoo for non-result navigation cases"
  - "Poller shut down in both onActivityResult and onStop as safety net against thread leaks"

patterns-established:
  - "Zidoo local API polling: GET http://127.0.0.1:9529/ZidooVideoPlay/getPlayStatus for position tracking"
  - "Chained async callbacks: reportPlaybackStopped -> markAsWatched -> relaunchCallerOrFinish"
  - "Caller detection: getCallingPackage first, fall back to getReferrer with android-app scheme"

requirements-completed: [PLAY-01, PLAY-02, PLAY-03, PLAY-04, PLAY-05, PLAY-06]

# Metrics
duration: 4min
completed: 2026-03-14
---

# Phase 3 Plan 2: Playback Lifecycle Wiring Summary

**Zidoo playback lifecycle in Play.java: caller capture, progress polling via localhost:9529, stop/watched/relaunch flow in onActivityResult**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-14T01:31:09Z
- **Completed:** 2026-03-14T01:34:39Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Wired full playback reporting pipeline: start -> progress -> stop -> watched -> relaunch
- Zidoo progress poller polls localhost:9529 every 10 seconds with 3-second initial delay
- Caller package detection and relaunch after playback reporting completes
- 90% watched threshold triggers automatic markAsWatched call

## Task Commits

Each task was committed atomically:

1. **Task 1: Update Callback interface and add caller capture + playback start reporting** - `bdde723` (feat)
2. **Task 2: Implement Zidoo progress poller and onActivityResult stop/watched/relaunch flow** - `0f72030` (feat)

## Files Created/Modified
- `app/src/main/java/com/jellyfintozidoo/JellyfinApi.java` - Updated Callback interface to include durationTicks, updated getItem to pass durationTicks
- `app/src/main/java/com/jellyfintozidoo/Play.java` - Added caller capture, session state fields, playback start reporting, progress poller, onActivityResult with stop/watched/relaunch flow

## Decisions Made
- Updated Callback interface to include durationTicks parameter so Play.java can calculate watched threshold
- Progress poller uses 3s initial delay (Zidoo player needs startup time) and 10s polling interval
- Kept onRestart->finish() from PlexToZidoo -- handles case where user navigates back from launcher rather than via onActivityResult
- Poller shut down in both onActivityResult and onStop to prevent thread leaks

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Task 1 needed stub methods for startProgressPoller/stopProgressPoller to compile independently since the full implementation was planned for Task 2. Added stubs and filled them in Task 2.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Full playback lifecycle is now wired: Jellyfin shows Now Playing, progress updates, resume position saved, watched marking
- Ready for Phase 4 (testing) or Phase 5 (polish)

---
*Phase: 03-playback-lifecycle*
*Completed: 2026-03-14*
