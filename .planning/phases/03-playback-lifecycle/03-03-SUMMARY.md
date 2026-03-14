---
phase: 03-playback-lifecycle
plan: 03
type: execute
status: complete
started: 2026-03-14
completed: 2026-03-14
---

# Plan 03-03 Summary: Device Deploy and E2E Verification

## What Was Done

Deployed APK to Zidoo device and verified full playback lifecycle end-to-end. User testing uncovered several bugs that were iteratively fixed during the session.

## Bug Fixes Applied

| Bug | Root Cause | Fix |
|-----|-----------|-----|
| Progress poller dying immediately | `onStop()` fires when Zidoo player takes foreground | Moved cleanup to `onDestroy()` |
| No episode auto-advance detection | Poller didn't track what file Zidoo was playing | Track `video.path` from getPlayStatus, detect path changes |
| No next-episode flow after watched | `relaunchCallerOrFinish()` just opened Jellyfin home | Added `searchNextEpisode()` using Jellyfin NextUp API |
| Episode completion not marking watched | No handler for Zidoo auto-advance case | Added `handleEpisodeCompleted()` with stop+watched+next flow |
| UI thread violations | `handleEpisodeCompleted()` called from poller thread | Wrapped in `runOnUiThread()` |

## New Code Added

- `JellyfinApi.getNextUp()` — queries `/Shows/NextUp` for next unwatched episode
- `JellyfinApi.NextUpCallback` — interface for NextUp results
- `Play.handleEpisodeCompleted()` — stop/watched/next-episode flow on path change
- `Play.searchNextEpisode()` — opens next episode detail page in Jellyfin client
- `seriesId` field stored from getItem response for NextUp queries
- `lastKnownDurationMs` and `currentPlayingPath` tracking in progress poller

## Key Decisions

- **onStop → onDestroy**: Zidoo player taking foreground triggers onStop, not onDestroy. Poller must survive this.
- **NextUp API over auto-play**: Jellyfin Android TV lacks "Next Up" countdown for external players (confirmed via GitHub issues). We query NextUp ourselves and open the episode detail page.
- **Removed relaunchCallerOrFinish()**: `getLaunchIntentForPackage` always opened Jellyfin home screen, not the episode. Replaced with `finish()` for non-watched exits and `searchNextEpisode()` for watched episodes.
- **Episode detection via video.path**: Zidoo auto-advances to next file in directory. We detect the path change and trigger the episode completion flow.

## Verification Results

All 4 E2E tests passed on real Zidoo device with real Jellyfin server:
1. Now Playing visible on Jellyfin dashboard during playback
2. Resume position saved correctly on stop
3. 90%+ playback marks item as watched
4. After watched, next episode detail page opens in Jellyfin client

## Commits

- `cff9bb3` fix(03-03): device verification bug fixes for playback lifecycle
