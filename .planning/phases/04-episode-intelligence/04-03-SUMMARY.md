---
phase: 04-episode-intelligence
plan: 03
subsystem: integration, lifecycle
tags: [play-activity, up-next, lifecycle-guards, adaptive-polling, episode-chaining]

requires:
  - phase: 04-episode-intelligence-01
    provides: reverseSubstitution, extractSearchName, parseSearchByPathResponse
  - phase: 04-episode-intelligence-02
    provides: UpNextActivity, getNextUpWithDetails, searchItemByPath
provides:
  - End-to-end episode intelligence wired into Play.java
  - Lifecycle guards preventing auto-replay during Up Next flow
  - Adaptive polling (3s near end, 10s normally) for precise Up Next trigger
  - finishWithResult() for binge session episode navigation
  - Redesigned Up Next screen with Jellyfin purple accent

key-files:
  modified:
    - app/src/main/java/com/jellyfintozidoo/Play.java
    - app/src/main/java/com/jellyfintozidoo/UpNextActivity.java
    - app/src/main/AndroidManifest.xml
    - app/src/main/res/layout/activity_up_next.xml
    - app/src/main/res/values/themes.xml
    - app/src/main/res/xml/root_preferences.xml
  created:
    - app/src/main/res/drawable/btn_play_now.xml
    - app/src/main/res/drawable/btn_cancel.xml
    - app/src/main/res/drawable/thumbnail_border.xml

key-decisions:
  - "onStart() guard: handlingPlaybackResult || waitingForUpNext prevents re-init during Up Next flow"
  - "handlingPlaybackResult serves triple duty: stale onActivityResult guard, onStart guard, onRestart guard"
  - "Local variable capture (resolvedSmbPath) for async safety — prevents onStart() overwriting instance var"
  - "Adaptive polling: self-rescheduling with 3s delay in final 60s, 10s otherwise"
  - "finishWithResult() passes current jellyfinItemId to calling Jellyfin client (best-effort)"
  - "Removed per-episode tracking from poller — replaced with stop-before-end approach via finishActivity(98)"

patterns-established:
  - "Lifecycle guard pattern: volatile boolean flags checked at onStart/onRestart/onActivityResult entry"
  - "Stop-before-end: finishActivity(98) at ~30s remaining prevents Zidoo auto-advance race"
  - "Local final var capture before async callbacks for instance variable safety"

requirements-completed: [EPIS-01, EPIS-02, EPIS-03, EPIS-04, SETT-04]

duration: multi-session
completed: 2026-03-14
---

# Phase 4 Plan 3: Integration & Device Verification Summary

**Wired Up Next flow and per-episode tracking into Play.java, fixed 3 critical lifecycle bugs, redesigned Up Next screen**

## Performance

- **Duration:** Multi-session (initial wiring + device debugging)
- **Tasks:** 3 (integration, settings, device verification)
- **Files modified:** 9

## Accomplishments

- Wired Up Next flow into Play.java replacing searchNextEpisode with handleEpisodeCompleted → launchUpNext chain
- Added lifecycle guards (handlingPlaybackResult, waitingForUpNext, upNextTriggered) preventing auto-replay bugs
- Implemented adaptive polling: self-rescheduling at 3s near episode end, 10s normally
- Added stop-before-end trigger: finishActivity(98) at ~30s remaining to prevent Zidoo auto-advance race
- Exposed second substitution rule slot on main settings page
- Redesigned Up Next screen: bottom-aligned layout, Jellyfin purple buttons, fullscreen theme, "Playing in N" countdown

## Task Commits

1. **Task 1: Wire Up Next flow into Play.java** — `1fa8564`
2. **Task 2: Multi-slot substitution settings** — `d8da20a`
3. **Task 3: Device verification & bug fixes** — `9932edb`

## Critical Bugs Fixed (Task 3)

1. **onStart() re-initialization** — When Zidoo player finishes and Play resumes, onStart() ran full init including auto-play, causing episode replay. Fix: guard at top of onStart() checking handlingPlaybackResult/waitingForUpNext.

2. **Stale onActivityResult(98)** — finishActivity(98) could generate duplicate results after handleEpisodeCompleted already ran. Fix: handlingPlaybackResult guard before Zidoo result processing.

3. **directPath race condition** — onStart() overwrites directPath with the HTTP stream URL. Async callbacks from Up Next Play Now handler referenced the instance var. Fix: capture `final String resolvedSmbPath = directPath` before async work.

## Architecture Change: Stop-Before-End vs Poller-Side Tracking

Original plan had the progress poller detect Zidoo auto-advance and do reverse substitution to identify the new episode. The implemented approach stops the player ~30s before end via finishActivity(98), then routes through handleEpisodeCompleted → Up Next. This is simpler and handles both auto-advance and manual stop uniformly.

## Deviations from Plan

- Removed per-episode tracking reset from poller (replaced by stop-before-end approach)
- Added finishWithResult() helper (not in original plan, QoL for binge sessions)
- Redesigned Up Next layout from centered card to bottom-aligned streaming-app style
- OkHttpClient singleton not added (deferred — not causing issues)

## Files Created/Modified

- `Play.java` — lifecycle guards, adaptive polling, stop-before-end trigger, Up Next wiring, finishWithResult
- `UpNextActivity.java` — "Playing in N" countdown text, logging
- `activity_up_next.xml` — Bottom-aligned layout redesign
- `AndroidManifest.xml` — UpNext fullscreen theme
- `themes.xml` — Theme.JellyfinToZidoo.UpNext style
- `btn_play_now.xml` — Jellyfin purple button drawable
- `btn_cancel.xml` — Ghost outline button drawable
- `thumbnail_border.xml` — Thumbnail border drawable
- `root_preferences.xml` — Second substitution rule slot (previous commit)

## Issues Encountered

Three critical lifecycle bugs found during device testing. All resolved — see "Critical Bugs Fixed" above.

## Next Phase Readiness

Phase 4 (Episode Intelligence) is complete. All plans executed:
- 04-01: TDD core logic (reverse substitution, path parsing)
- 04-02: UpNextActivity UI + API methods
- 04-03: Integration + device verification

Ready for Phase 5 or phase verification.

---
*Phase: 04-episode-intelligence*
*Completed: 2026-03-14*
